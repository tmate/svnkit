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

import java.io.File;
import java.io.IOException;
import java.io.FileOutputStream;
import java.io.FileNotFoundException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.HashMap;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLock;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc.SVNProperties;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.util.SVNTimeUtil;

/**
 * @version 1.0
 * @author  TMate Software Ltd.
 */
public class FSWriter {
    private static File ourTmpDir;

    public static void writePathInfoToReportFile(OutputStream tmpFileOS, String target, String path, String linkPath, String lockToken, long revision, boolean startEmpty) throws IOException {
        String anchorRelativePath = SVNPathUtil.append(target, path);
        String linkPathRep = linkPath != null ? "+" + linkPath.length() + ":" + linkPath : "-";
        String revisionRep = FSRepository.isValidRevision(revision) ? "+" + revision + ":" : "-";
        String lockTokenRep = lockToken != null ? "+" + lockToken.length() + ":" + lockToken : "-";
        String startEmptyRep = startEmpty ? "+" : "-";
        String fullRepresentation = "+" + anchorRelativePath.length() + ":" + anchorRelativePath + linkPathRep + revisionRep + startEmptyRep + lockTokenRep;
        tmpFileOS.write(fullRepresentation.getBytes());
    }
    
    /* Delete LOCK from FS in the actual OS filesystem. */
    public static void deleteLock(SVNLock lock, File reposRootDir) throws SVNException {
        String reposPath = lock.getPath();
        String childToKill = null;
        Collection children = new ArrayList();;
        while(true){
            FSReader.fetchLock(reposPath, children, reposRootDir);
            if(childToKill != null){
                children.remove(childToKill);
            }
            
            /* Delete the lock.*/
            if(children.size() == 0){
                /* Special case:  no goodz, no file.  And remember to nix
                 * the entry for it in its parent. 
                 */
                childToKill = FSRepositoryUtil.getDigestFromRepositoryPath(reposPath);
                File digestFile = FSRepositoryUtil.getDigestFileFromRepositoryPath(reposPath, reposRootDir);
                SVNFileUtil.deleteFile(digestFile);
            }else{
                FSWriter.writeDigestLockFile(null, children, reposPath, reposRootDir);
                childToKill = null;
                /* ? Why should we go upper rewriting files where nothing is changed?
                 * For now i guess we should break here, maybe later i'll figure out
                 * the reason of non-stopping   
                 */
                break;
            }
            /* Prep for next iteration, or bail if we're done. */
            if("/".equals(reposPath)){
                break;
            }
            reposPath = SVNPathUtil.removeTail(reposPath);
            if("".equals(reposPath)){
                reposPath = "/";
            }
            children.clear(); 
        }
    }
    
    public static void writeDigestLockFile(SVNLock lock, Collection children, String repositoryPath, File reposRootDir) throws SVNException {
        if(!ensureDirExists(FSRepositoryUtil.getDBLocksDir(reposRootDir), true)){
            SVNErrorManager.error("svn: Can't create a directory at '" + FSRepositoryUtil.getDBLocksDir(reposRootDir).getAbsolutePath() + "'");
        }
        File digestLockFile = FSRepositoryUtil.getDigestFileFromRepositoryPath(repositoryPath, reposRootDir);
        File lockDigestSubdir = FSRepositoryUtil.getLockDigestSubdirectory(FSRepositoryUtil.getDigestFromRepositoryPath(repositoryPath), reposRootDir);
        if(!ensureDirExists(lockDigestSubdir, true)){
            SVNErrorManager.error("svn: Can't create a directory at '" + FSRepositoryUtil.getDBLocksDir(reposRootDir).getAbsolutePath() + "'");
        }
        Map props = new HashMap();
        if(lock != null){
            props.put(FSConstants.PATH_LOCK_KEY, lock.getPath());
            props.put(FSConstants.OWNER_LOCK_KEY, lock.getOwner());
            props.put(FSConstants.TOKEN_LOCK_KEY, lock.getID());
            props.put(FSConstants.IS_DAV_COMMENT_LOCK_KEY, "0");
            if(lock.getComment() != null){
                props.put(FSConstants.COMMENT_LOCK_KEY, lock.getComment());
            }
            if(lock.getCreationDate() != null){
                props.put(FSConstants.CREATION_DATE_LOCK_KEY, SVNTimeUtil.formatDate(lock.getCreationDate()));
            }
            if(lock.getExpirationDate() != null){
                props.put(FSConstants.EXPIRATION_DATE_LOCK_KEY, SVNTimeUtil.formatDate(lock.getExpirationDate()));
            }
        }
        if(children != null && children.size() > 0){
            Object[] digests = children.toArray();
            StringBuffer value = new StringBuffer();
            for(int i = 0; i < digests.length; i++){
                value.append(digests[i]);
                value.append('\n');
            }
            props.put(FSConstants.CHILDREN_LOCK_KEY, value.toString());
        }
        
        try{
            SVNProperties.setProperties(props, digestLockFile);
        }catch(SVNException svne){
            SVNErrorManager.error("svn: Cannot write lock/entries hashfile '" + digestLockFile.getAbsolutePath() + "': " + svne.getMessage());
        }
    }
    
    public static boolean ensureDirExists(File dir, boolean create){
        if(!dir.exists() && create == true){
            return dir.mkdirs();
        }else if(!dir.exists()){
            return false;
        }
        return true;
    }
    
    public static File createUniqueTemporaryFile(String name, String suffix) throws SVNException {
        File tmpDir = getTmpDir();
        if (tmpDir == null) {
            SVNErrorManager.error("svn: Can't get a temporary directory");
        }
        File tmpFile = null;
        try {
            tmpFile = SVNFileUtil.createUniqueFile(tmpDir, name, suffix);
            tmpFile.createNewFile();
            tmpFile.deleteOnExit();
        } catch (IOException ioe) {
            SVNErrorManager.error("svn: Can't create a temporary file");
        }
        return tmpFile;
    }

    public static File getTmpDir() {
        if (ourTmpDir == null) {
            ourTmpDir = FSWriter.testTempDir(new File(""));
        }
        return ourTmpDir;
    }

    public static File testTempDir(File tmpDir){
        File tmpFile = null;
        FileOutputStream fos = null;
        for(int i = 0; i < 2; i++){
            try{
                tmpFile = File.createTempFile("javasvn-test", ".tmp", i == 0 ? null : tmpDir);
                fos = new FileOutputStream(tmpFile);
                fos.write('!');
                fos.close();
                return tmpFile.getParentFile();
            }catch(FileNotFoundException fnfe){
                continue;
            }catch(IOException ioe){
                continue;
            }catch(SecurityException se){
                continue;
            }finally{
                SVNFileUtil.closeFile(fos);
                /* it should not be a fatal error that a security
                 * exception may occur?
                 */
                try{
                    SVNFileUtil.deleteFile(tmpFile);
                }catch(SecurityException se){
                }
            }
        }
        return null;
    }
    
}
