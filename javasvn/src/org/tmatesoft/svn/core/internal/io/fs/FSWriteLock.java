/*
 * ====================================================================
 * Copyright (c) 2004 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://tmate.org/svn/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.io.fs;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileLock;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

import org.tmatesoft.svn.core.internal.wc.SVNFileType;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.SVNException;

/**
 * @version 1.0
 * @author  TMate Software Ltd.
 */
public class FSWriteLock {
    private static final Map ourThreadLocksCache = new HashMap();

    private RandomAccessFile myLockFile;
    private FileLock myLock;
    private int myReferencesCount = 0;
    private String myReposRootPath; 
    private File myReposRootDir;
   
    private FSWriteLock(String reposRootPath){
        myReposRootPath = reposRootPath;
        myReposRootDir = new File(myReposRootPath);
    }
    
    public static synchronized FSWriteLock getWriteLock(File reposRootDir) throws SVNException {
        String reposRootPath = null;
        try{
            reposRootPath = reposRootDir.getCanonicalPath();
        }catch(IOException ioe){
            SVNErrorManager.error("Can't get exclusive write lock to the repository '" + reposRootDir.getAbsolutePath() + "': " + ioe.getMessage());
        }
        FSWriteLock lock = (FSWriteLock)ourThreadLocksCache.get(reposRootPath);
        if(lock == null){
            lock = new FSWriteLock(reposRootPath);
            ourThreadLocksCache.put(reposRootPath, lock);
        }
        lock.myReferencesCount++;
        return lock;
    }
    
    public synchronized void lock() throws SVNException {
        try{
            /* svn 1.1.1 and earlier deferred lock file creation to the first
             * commit.  So in case the repository was created by an earlier
             * version of svn, check the lock file here. 
             */            
            File writeLockFile = FSRepositoryUtil.getWriteLockFile(myReposRootDir);
            SVNFileType type = SVNFileType.getType(writeLockFile);
            if(type == SVNFileType.UNKNOWN || type == SVNFileType.NONE){
                SVNFileUtil.createEmptyFile(writeLockFile);
            }
            myLockFile = new RandomAccessFile(writeLockFile, "rw");
            myLock = myLockFile.getChannel().lock(0, Long.MAX_VALUE, false);            
        }catch(IOException ioe){
            unlock();
            SVNErrorManager.error("Can't get exclusive write lock to the repository '" + myReposRootPath + "': " + ioe.getMessage());
        }
    }
    
    public static synchronized void realease(FSWriteLock lock){
        if(lock == null){
            return;
        }
        if((--lock.myReferencesCount) == 0){
            ourThreadLocksCache.remove(lock);
        }
    }
    
    public synchronized void unlock(){
        if (myLock != null) {
            try {
                myLock.release();
            } catch (IOException ioex) {
                //
            }
            myLock = null; 
        }
        SVNFileUtil.closeFile(myLockFile);
        myLockFile = null;
    }
}
