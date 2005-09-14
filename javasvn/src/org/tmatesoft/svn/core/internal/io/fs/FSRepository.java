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
import java.io.InputStream;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.io.FileInputStream;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.io.RandomAccessFile;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.OutputStream;
import java.io.File;

import java.util.Collection;
import java.util.Date;
import java.util.Map;

import org.tmatesoft.svn.core.ISVNDirEntryHandler;
import org.tmatesoft.svn.core.ISVNLogEntryHandler;
import org.tmatesoft.svn.core.SVNDirEntry;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLock;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.wc.SVNFileType;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.ISVNFileRevisionHandler;
import org.tmatesoft.svn.core.io.ISVNLocationEntryHandler;
import org.tmatesoft.svn.core.io.ISVNLockHandler;
import org.tmatesoft.svn.core.io.ISVNReporterBaton;
import org.tmatesoft.svn.core.io.ISVNSession;
import org.tmatesoft.svn.core.io.ISVNWorkspaceMediator;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.util.SVNEncodingUtil;
import org.tmatesoft.svn.core.internal.wc.SVNTranslator;
import org.tmatesoft.svn.core.SVNProperty;


/**
 * @version 1.0
 * @author  TMate Software Ltd.
 */
public class FSRepository extends SVNRepository {
    private String SVN_REPOS_README = "README.txt";
    private String SVN_REPOS_DB_DIR = "db";
    private String SVN_REPOS_DAV_DIR = "dav";
    private String SVN_REPOS_LOCKS_DIR = "locks";
    private String SVN_REPOS_HOOKS_DIR = "hooks";
    private String SVN_REPOS_CONF_DIR = "conf";
    private String SVN_REPOS_DB_LOCKFILE = "db.lock";
    private String SVN_REPOS_DB_LOGS_LCOKFILE = "db-logs.lock";
    private String SVN_REPOS_HOOK_START_COMMIT = "start-commit";
    private String SVN_REPOS_HOOK_PRE_COMMIT = "pre-commit";
    private String SVN_REPOS_HOOK_POST_COMMIT = "post-commit";
    private String SVN_REPOS_HOOK_READ_SENTINEL = "read-sentinels";
    private String SVN_REPOS_HOOK_WRITE_SENTINEL = "write-sentinels";
    private String SVN_REPOS_HOOK_PRE_REVPROP_CHANGE = "pre-revprop-change";
    private String SVN_REPOS_HOOK_POST_REVPROP_CHANGE = "post-revprop-change";
    private String SVN_REPOS_HOOK_PRE_LOCK = "pre-lock";
    private String SVN_REPOS_HOOK_POST_LOCK = "post-lock";
    private String SVN_REPOS_HOOK_PRE_UNLOCK = "pre-unlock";
    private String SVN_REPOS_HOOK_POST_UNLOCK = "post-unlock";
    private String SVN_REPOS_HOOK_DESC_EXT = ".tmpl";
    private String SVN_REPOS_CONF_SVNSERVE_CONF = "svnserve.conf";
    private String SVN_REPOS_CONF_PASSWD = "passwd";
    private String SVN_REPOS_FSFS_FORMAT = "fsfs";
    private String SVN_REPOS_DB_CURRENT = "current";
    private String SVN_REPOS_FORMAT = "format";
    private int    SVN_REPOS_FORMAT_NUMBER = 3;
    private String SVN_FS_FORMAT = "format";
    private int    SVN_FS_FORMAT_NUMBER = 1;
    private String SVN_FS_TYPE_FILENAME = "fs-type";
    
    private FileLock myDBSharedLock;

    protected FSRepository(SVNURL location, ISVNSession options) {
        super(location, options);
    }

    public void testConnection() throws SVNException {
    }
    
    private String findRepositoryRoot(String path) throws SVNException{
        if(path==null){
            path = "";
        }
        String rootPath = path;
        while(!isReposRoot(rootPath)){
            rootPath = SVNPathUtil.removeTail(rootPath);
            if(rootPath.equals("") && !isReposRoot(rootPath)){
                throw new SVNException();
            }
        }
        return rootPath;
    }

    private boolean isReposRoot(String candidatePath){
        File formatFile = new File(candidatePath, SVN_REPOS_FORMAT);
        SVNFileType fileType = SVNFileType.getType(formatFile);
        if(fileType!=SVNFileType.FILE){
            return false;
        }
        File dbFile = new File(candidatePath, SVN_REPOS_DB_DIR);
        fileType = SVNFileType.getType(dbFile);
        if(fileType!=SVNFileType.DIRECTORY && fileType!=SVNFileType.SYMLINK){
            return false;
        }
        return true;
    }
    
    private byte[] readBytesFromFile(byte[] buffer, File file) throws SVNException{
        InputStream is=null;
        try{
            is = new BufferedInputStream(new FileInputStream(file));
        }catch(FileNotFoundException fnfe){
            if(is!=null){
                try {
                    is.close();
                } catch (IOException e) {
                    //
                }
            }            
            throw new SVNException("svn: Can't open file '" + file + "'", fnfe);
        }

        try {
            while (true) {
                int l = is.read(buffer);
                if (l <= 0) {
                    break;
                }
            }
        } catch (IOException ioe) {
            throw new SVNException("svn: Can't read length line in file '" + file + "'", ioe);
        } finally {
            if(is!=null){
                try {
                    is.close();
                } catch (IOException e) {
                    //
                }
            }
        }
        return buffer;
    }
    
    private void checkReposFormat(String reposRootPath) throws SVNException{
        int formatNumber =  getFormat(reposRootPath, SVN_REPOS_FORMAT);
        if(formatNumber!=SVN_REPOS_FORMAT_NUMBER){
            throw new SVNException("svn: Expected format '" + SVN_REPOS_FORMAT_NUMBER + "' of repository; found format '" + formatNumber + "'");
        }
    }

    private void checkFSFormat(String reposRootPath) throws SVNException{
        int formatNumber =  getFormat(reposRootPath, SVN_FS_FORMAT);
        if(formatNumber!=SVN_FS_FORMAT_NUMBER){
            throw new SVNException("svn: Expected FS format '" + SVN_FS_FORMAT_NUMBER + "'; found format '" + formatNumber + "'");
        }
    }
    
    private int getFormat(String reposRootPath, String format) throws SVNException {
        File formatFile = new File(reposRootPath, format);
        
        BufferedReader br=null;
        try{
            br = new BufferedReader(new InputStreamReader(new FileInputStream(formatFile)));
        }catch(FileNotFoundException fnfe){
            if(br!=null){
                try{
                    br.close();
                }catch(IOException ioe){
                    //
                }
            }
            throw new SVNException("svn: Can't open file '" + formatFile + "'", fnfe);
        }

        String firstLine=null;

        try{
            firstLine = br.readLine();
        }catch(IOException ioe){
            throw new SVNException("svn: Can't read file '" + formatFile + "'", ioe);
        }finally{
            if(br!=null){
                try{
                    br.close();
                }catch(IOException iioe){
                    //
                }
            }
        }
        
        if(firstLine==null){
            throw new SVNException("svn: Can't read file '" + formatFile + "': End of file found");
        }
        
        //checking for non-digits 
        for(int i=0; i < firstLine.length(); i++){
            if(!Character.isDigit(firstLine.charAt(i))){
                throw new SVNException("First line of '" + formatFile +  "' contains non-digit");
            }
        }
        return Integer.parseInt(firstLine);
    }
    
    private void lockDBFile(String reposRootPath) throws SVNException{
        //1. open db.lock for shared reading & writing (?? just like in the svn code)
        File locksDir = new File(reposRootPath, SVN_REPOS_LOCKS_DIR);
        File dbLockFile = new File(locksDir, SVN_REPOS_DB_LOCKFILE);
        
        if(!dbLockFile.exists()){
            throw new SVNException("svn: Error opening db lockfile" + SVNTranslator.getEOL(SVNProperty.EOL_STYLE_NATIVE) + "svn: Can't open file '" + dbLockFile.getAbsolutePath() + "'");
        }
        
        RandomAccessFile rafile = null;
        try{
            rafile = new RandomAccessFile(dbLockFile, "rw");
        }catch(FileNotFoundException fnfe){
            throw new SVNException("svn: Error opening db lockfile" + SVNTranslator.getEOL(SVNProperty.EOL_STYLE_NATIVE) + "svn: Can't open file '" + dbLockFile.getAbsolutePath() + "'");
        }
        
        //2. lock db.lock blocking, not exclusively 
        FileChannel fch = rafile.getChannel();
        try{
            myDBSharedLock = fch.lock(0, Long.MAX_VALUE, true);
        }catch(IOException ioe){
            throw new SVNException("svn: Error opening db lockfile" + SVNTranslator.getEOL(SVNProperty.EOL_STYLE_NATIVE) + "svn: Can't get shared lock on file '" + dbLockFile + "'");
        }
    }
    
    private void openRepository() throws SVNException {
        String eolBytes = new String(SVNTranslator.getEOL(SVNProperty.EOL_STYLE_NATIVE));
        String errorMessage = "svn: Unable to open an ra_local session to URL" + eolBytes + "svn: Unable to open repository '" + getLocation() + "'";
        
        //1. find repos root 
        String reposRoot=null;
        try{
            reposRoot = findRepositoryRoot(SVNEncodingUtil.uriDecode(getLocation().getPath()));
        }catch(SVNException svne){
            throw new SVNException(errorMessage);
        }
        
        //2. check repos format (the format file must exist!)
        try{
            checkReposFormat(reposRoot);
        }catch(SVNException svne){
            throw new SVNException(errorMessage + eolBytes + svne.getMessage());
        }

        //3. lock 'db.lock' file non-exclusively, blocking, for reading only
        try{
            lockDBFile(reposRoot);
        }catch(SVNException svne){
            throw new SVNException(errorMessage + eolBytes + svne.getMessage());
        }
        
        //4. check FS type for 'fsfs'
        try{
            checkFSType(reposRoot);
        }catch(SVNException svne){
            throw new SVNException(errorMessage + eolBytes + svne.getMessage());
        }
    }
    
    private void checkFSType(String reposRootPath) throws SVNException{
        File fsTypeFile = new File(new File(reposRootPath, SVN_REPOS_DB_DIR), SVN_FS_TYPE_FILENAME);
        BufferedReader br=null;
        try{
            br = new BufferedReader(new InputStreamReader(new FileInputStream(fsTypeFile)));
        }catch(FileNotFoundException fnfe){
            if(br!=null){
                try{
                    br.close();
                }catch(IOException ioe){
                    //
                }
            }
            throw new SVNException("svn: Can't open file '" + fsTypeFile + "'", fnfe);
        }

        String fsType=null;

        try{
            fsType = br.readLine();
        }catch(IOException ioe){
            throw new SVNException("svn: Can't read file '" + fsTypeFile + "'", ioe);
        }finally{
            if(br!=null){
                try{
                    br.close();
                }catch(IOException iioe){
                    //
                }
            }
        }
       
        if(fsType==null){
            throw new SVNException("svn: Can't read file '" + fsTypeFile + "': End of file found");
        }
        
        if(!fsType.equals(SVN_REPOS_FSFS_FORMAT)){
            throw new SVNException("svn: Unknown FS type '" + fsType + "'");
        }
    }
    
    public long getLatestRevision() throws SVNException {
        openRepository();
        
        String reposRoot = findRepositoryRoot(getLocation().getPath());
        File dbDir = new File(reposRoot, SVN_REPOS_DB_DIR);
        File dbCurrentFile = new File(dbDir, SVN_REPOS_DB_CURRENT);
        byte[] buffer = new byte[256];
        String[] result = new String(readBytesFromFile(buffer, dbCurrentFile)).split(" ");
        return Long.parseLong(result[0]);
    }

    public long getDatedRevision(Date date) throws SVNException {
        return 0;
    }

    /**
     * @param revision
     * @param properties
     * @return
     * @throws SVNException
     */
    public Map getRevisionProperties(long revision, Map properties) throws SVNException {
        return null;
    }

    /**
     * @param revision
     * @param propertyName
     * @param propertyValue
     * @throws SVNException
     */
    public void setRevisionPropertyValue(long revision, String propertyName, String propertyValue) throws SVNException {
    }

    /**
     * @param revision
     * @param propertyName
     * @return
     * @throws SVNException
     */
    public String getRevisionPropertyValue(long revision, String propertyName) throws SVNException {
        return null;
    }
    
    public SVNDirEntry getDir(String path, long revision, boolean includeCommitMessages, Collection entries) throws SVNException{
        return null;
    }
    
    /**
     * @param path
     * @param revision
     * @return
     * @throws SVNException
     */
    public SVNNodeKind checkPath(String path, long revision) throws SVNException {
        return null;
    }

    /**
     * @param path
     * @param revision
     * @param properties
     * @param contents
     * @return
     * @throws SVNException
     */
    public long getFile(String path, long revision, Map properties, OutputStream contents) throws SVNException {
        return 0;
    }

    /**
     * @param path
     * @param revision
     * @param properties
     * @param handler
     * @return
     * @throws SVNException
     */
    public long getDir(String path, long revision, Map properties, ISVNDirEntryHandler handler) throws SVNException {
        return 0;
    }

    /**
     * @param path
     * @param startRevision
     * @param endRevision
     * @param handler
     * @return
     * @throws SVNException
     */
    public int getFileRevisions(String path, long startRevision, long endRevision, ISVNFileRevisionHandler handler) throws SVNException {
        return 0;
    }

    /**
     * @param targetPaths
     * @param startRevision
     * @param endRevision
     * @param changedPath
     * @param strictNode
     * @param limit
     * @param handler
     * @return
     * @throws SVNException
     */
    public long log(String[] targetPaths, long startRevision, long endRevision, boolean changedPath, boolean strictNode, long limit, ISVNLogEntryHandler handler) throws SVNException {
        return 0;
    }

    /**
     * @param path
     * @param pegRevision
     * @param revisions
     * @param handler
     * @return
     * @throws SVNException
     */
    public int getLocations(String path, long pegRevision, long[] revisions, ISVNLocationEntryHandler handler) throws SVNException {
        return 0;
    }

    /**
     * @param path
     * @param revision
     * @return
     * @throws SVNException
     */
    public Collection getDir(String path, long revision) throws SVNException {
        return null;
    }

    /**
     * @param url
     * @param revision
     * @param target
     * @param ignoreAncestry
     * @param recursive
     * @param reporter
     * @param editor
     * @throws SVNException
     */
    public void diff(SVNURL url, long revision, String target, boolean ignoreAncestry, boolean recursive, ISVNReporterBaton reporter, ISVNEditor editor) throws SVNException {
    }

    /**
     * @param url
     * @param targetRevision
     * @param revision
     * @param target
     * @param ignoreAncestry
     * @param recursive
     * @param reporter
     * @param editor
     * @throws SVNException
     */
    public void diff(SVNURL url, long targetRevision, long revision, String target, boolean ignoreAncestry, boolean recursive, ISVNReporterBaton reporter, ISVNEditor editor) throws SVNException {
    }

    /**
     * @param revision
     * @param target
     * @param recursive
     * @param reporter
     * @param editor
     * @throws SVNException
     */
    public void update(long revision, String target, boolean recursive, ISVNReporterBaton reporter, ISVNEditor editor) throws SVNException {
    }

    /**
     * @param revision
     * @param target
     * @param recursive
     * @param reporter
     * @param editor
     * @throws SVNException
     */
    public void status(long revision, String target, boolean recursive, ISVNReporterBaton reporter, ISVNEditor editor) throws SVNException {
    }

    /**
     * @param url
     * @param revision
     * @param target
     * @param recursive
     * @param reporter
     * @param editor
     * @throws SVNException
     */
    public void update(SVNURL url, long revision, String target, boolean recursive, ISVNReporterBaton reporter, ISVNEditor editor) throws SVNException {
    }

    /**
     * @param path
     * @param revision
     * @return
     * @throws SVNException
     */
    public SVNDirEntry info(String path, long revision) throws SVNException {
        return null;
    }

    /**
     * @param logMessage
     * @param locks
     * @param keepLocks
     * @param mediator
     * @return
     * @throws SVNException
     */
    public ISVNEditor getCommitEditor(String logMessage, Map locks, boolean keepLocks, ISVNWorkspaceMediator mediator) throws SVNException {
        return null;
    }

    /**
     * @param path
     * @return
     * @throws SVNException
     */
    public SVNLock getLock(String path) throws SVNException {
        return null;
    }

    /**
     * @param path
     * @return
     * @throws SVNException
     */
    public SVNLock[] getLocks(String path) throws SVNException {
        return null;
    }

    /**
     * @param pathsToRevisions
     * @param comment
     * @param force
     * @param handler
     * @throws SVNException
     */
    public void lock(Map pathsToRevisions, String comment, boolean force, ISVNLockHandler handler) throws SVNException {
    }

    /**
     * @param pathToTokens
     * @param force
     * @param handler
     * @throws SVNException
     */
    public void unlock(Map pathToTokens, boolean force, ISVNLockHandler handler) throws SVNException {
    }

    /**
     * @throws SVNException
     */
    public void closeSession() throws SVNException {
    }

}
