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
import java.io.FileInputStream;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.io.RandomAccessFile;
import java.io.FileNotFoundException;
import java.io.OutputStream;
import java.io.File;
import java.util.Collection;
import java.util.Date;
import java.util.Map;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Iterator;
import java.util.Set;

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
import org.tmatesoft.svn.core.internal.util.SVNTimeUtil;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNRevisionProperty;
import org.tmatesoft.svn.core.internal.wc.SVNProperties;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;


/**
 * @version 1.0
 * @author  TMate Software Ltd.
 */
public class FSRepository extends SVNRepository {
    static String SVN_REPOS_README = "README.txt";
    static String SVN_REPOS_DB_DIR = "db";
    static String SVN_REPOS_DAV_DIR = "dav";
    static String SVN_REPOS_LOCKS_DIR = "locks";
    static String SVN_REPOS_HOOKS_DIR = "hooks";
    static String SVN_REPOS_CONF_DIR = "conf";
    static String SVN_REPOS_DB_LOCKFILE = "db.lock";
    static String SVN_REPOS_DB_LOGS_LCOKFILE = "db-logs.lock";
    static String SVN_REPOS_HOOK_START_COMMIT = "start-commit";
    static String SVN_REPOS_HOOK_PRE_COMMIT = "pre-commit";
    static String SVN_REPOS_HOOK_POST_COMMIT = "post-commit";
    static String SVN_REPOS_HOOK_READ_SENTINEL = "read-sentinels";
    static String SVN_REPOS_HOOK_WRITE_SENTINEL = "write-sentinels";
    static String SVN_REPOS_HOOK_PRE_REVPROP_CHANGE = "pre-revprop-change";
    static String SVN_REPOS_HOOK_POST_REVPROP_CHANGE = "post-revprop-change";
    static String SVN_REPOS_HOOK_PRE_LOCK = "pre-lock";
    static String SVN_REPOS_HOOK_POST_LOCK = "post-lock";
    static String SVN_REPOS_HOOK_PRE_UNLOCK = "pre-unlock";
    static String SVN_REPOS_HOOK_POST_UNLOCK = "post-unlock";
    static String SVN_REPOS_HOOK_DESC_EXT = ".tmpl";
    static String SVN_REPOS_CONF_SVNSERVE_CONF = "svnserve.conf";
    static String SVN_REPOS_CONF_PASSWD = "passwd";
    static String SVN_REPOS_FSFS_FORMAT = "fsfs";
    static String SVN_REPOS_DB_CURRENT = "current";
    static String SVN_REPOS_FORMAT = "format";
    private int    SVN_REPOS_FORMAT_NUMBER = 3;
    static String SVN_REPOS_FS_FORMAT = "format";
    private int    SVN_FS_FORMAT_NUMBER = 1;
    static String SVN_FS_TYPE_FILENAME = "fs-type";
    static String SVN_REPOS_UUID_FILE = "uuid";
    static String SVN_REPOS_REVPROPS_DIR = "revprops";
    static String SVN_REPOS_REVS_DIR = "revs";

    //uuid format - 36 symbols
    private int SVN_UUID_FILE_LENGTH = 36;
    //if > max svn 1.2 stops working
    private int SVN_UUID_FILE_MAX_LENGTH = SVN_UUID_FILE_LENGTH + 1;
    
    private FileLock myDBSharedLock;
    
    /* svn gets the mutex for buffered i/o (locks a file that can potentially
     * be changed by another thread of the same process during reading from it)
     */  
    private static Object myMutex = new Object();

    private String myReposRootPath;
    //db.lock file representation for synchronizing
    private RandomAccessFile myDBLockFile;

    protected FSRepository(SVNURL location, ISVNSession options) {
        super(location, options);
    }

    public void testConnection() throws SVNException {
        //try to open and close a repository
        try{
            openRepository();
        }finally{
            closeRepository();
        }
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
    
    private void checkReposFormat(String reposRootPath) throws SVNException{
        int formatNumber =  getFormat(reposRootPath, SVN_REPOS_FORMAT);
        if(formatNumber!=SVN_REPOS_FORMAT_NUMBER){
            throw new SVNException("svn: Expected format '" + SVN_REPOS_FORMAT_NUMBER + "' of repository; found format '" + formatNumber + "'");
        }
    }

    private void checkFSFormat(String reposRootPath) throws SVNException{
        File dbFSFormat = new File(reposRootPath, SVN_REPOS_DB_DIR);
        int formatNumber = -1;
        try{
            formatNumber =  getFormat(dbFSFormat.getAbsolutePath(), SVN_REPOS_FS_FORMAT);
        }catch(SVNException svne){
            if(svne.getCause() instanceof FileNotFoundException){
                formatNumber = SVN_FS_FORMAT_NUMBER;
            }
        }
        
        if(formatNumber!=SVN_FS_FORMAT_NUMBER){
            throw new SVNException("svn: Expected FS format '" + SVN_FS_FORMAT_NUMBER + "'; found format '" + formatNumber + "'");
        }
    }
    
    private int getFormat(String reposPath, String format) throws SVNException {
        File formatFile = new File(reposPath, format);
        
        String firstLine = SVNFSReader.readSingleLine(formatFile);

        if(firstLine == null){
            throw new SVNException("svn: Can't read file '" + formatFile.getAbsolutePath() + "': End of file found");
        }
        
        //checking for non-digits 
        for(int i=0; i < firstLine.length(); i++){
            if(!Character.isDigit(firstLine.charAt(i))){
                throw new SVNException("First line of '" + formatFile.getAbsolutePath() +  "' contains non-digit");
            }
        }
        return Integer.parseInt(firstLine);
    }
    
    private void lockDBFile(String reposRootPath) throws SVNException{
        //1. open db.lock for shared reading (?? just like in the svn code)
        File dbLockFile = new File(new File(reposRootPath, SVN_REPOS_LOCKS_DIR), SVN_REPOS_DB_LOCKFILE);
        
        if(!dbLockFile.exists()){
            throw new SVNException("svn: Error opening db lockfile" + SVNFileUtil.getNativeEOLMarker() + "svn: Can't open file '" + dbLockFile.getAbsolutePath() + "'");
        }
        
        myDBLockFile = null;
        try{
            myDBLockFile = new RandomAccessFile(dbLockFile, "r");
        }catch(FileNotFoundException fnfe){
            if(myDBLockFile!=null){
                try{
                    myDBLockFile.close();
                }catch(IOException ioe){
                    //
                }
            }
            throw new SVNException("svn: Error opening db lockfile" + SVNFileUtil.getNativeEOLMarker() + "svn: Can't open file '" + dbLockFile.getAbsolutePath() + "'");
        }
        
        //2. lock db.lock blocking, not exclusively 
        FileChannel fch = myDBLockFile.getChannel();
        try{
            myDBSharedLock = fch.lock(0, Long.MAX_VALUE, true);
        }catch(IOException ioe){
            if(myDBLockFile!=null){
                try{
                    myDBLockFile.close();
                }catch(IOException ioex){
                    //
                }
            }
            if(myDBSharedLock!=null){
                try{
                    myDBSharedLock.release();
                }catch(IOException ioex){
                    //
                }
            }
            throw new SVNException("svn: Error opening db lockfile" + SVNFileUtil.getNativeEOLMarker() + "svn: Can't get shared lock on file '" + dbLockFile.getAbsolutePath() + "'");
        }
    }
    
    private void unlockDBFile() throws SVNException{
        //1. release the shared lock
        if(myDBSharedLock!=null){
            try{
                myDBSharedLock.release();
            }catch(IOException ioex){
                File dbLockFile = new File(new File(myReposRootPath, SVN_REPOS_LOCKS_DIR), SVN_REPOS_DB_LOCKFILE);
                throw new SVNException("svn: Can't unlock file '" + dbLockFile.getAbsoluteFile() + "'"); 
            }finally{
                //2. close 'db.lock' file
                if(myDBLockFile!=null){
                    try{
                        myDBLockFile.close();
                    }catch(IOException ioex){
                        //
                    }
                    return;
                }
            }
        }

        //2. close 'db.lock' file
        if(myDBLockFile!=null){
            try{
                myDBLockFile.close();
            }catch(IOException ioex){
                File dbLockFile = new File(new File(myReposRootPath, SVN_REPOS_LOCKS_DIR), SVN_REPOS_DB_LOCKFILE);
                throw new SVNException("svn: Can't close file '" + dbLockFile.getAbsoluteFile() + "'"); 
            }
        }
    }
    
    private void openRepository() throws SVNException {
        lock();
        
        String eol = SVNFileUtil.getNativeEOLMarker();
        
        String errorMessage = "svn: Unable to open an ra_local session to URL" + eol + "svn: Unable to open repository '" + getLocation() + "'";
        
        //Perform steps similar to svn's ones
        //1. Find repos root 
        if(myReposRootPath==null){
            try{
                myReposRootPath = findRepositoryRoot(getLocation().getPath());
            }catch(SVNException svne){
                throw new SVNException(errorMessage);
            }
        }
            
        //2. Check repos format (the format file must exist!)
        try{
            checkReposFormat(myReposRootPath);
        }catch(SVNException svne){
            throw new SVNException(errorMessage + eol + svne.getMessage());
        }
    
        //3. Lock 'db.lock' file non-exclusively, blocking, for reading only
        try{
            lockDBFile(myReposRootPath);
        }catch(SVNException svne){
            throw new SVNException(errorMessage + eol + svne.getMessage());
        }
            
        //4. Check FS type for 'fsfs'
        try{
            checkFSType(myReposRootPath);
        }catch(SVNException svne){
            throw new SVNException(errorMessage + eol + svne.getMessage());
        }
            
        //5. Attempt to open the 'current' file of this repository
        File dbCurrentFile = new File(new File(myReposRootPath, SVN_REPOS_DB_DIR), SVN_REPOS_DB_CURRENT);
        FileInputStream fis = null;
        try{
            fis = new FileInputStream(dbCurrentFile);
        }catch(FileNotFoundException fnfe){
            throw new SVNException(errorMessage + eol + "svn: Can't open file '" + dbCurrentFile.getAbsolutePath() + "'");
        }finally{
            if(fis!=null){
                try{
                    fis.close();
                }catch(IOException ioe){
                    //
                }
            }
        }
            
        /*
         * 6. Check the FS format number (db/format). Treat an absent
         * format file as format 1. Do not try to create the format file 
         * on the fly, because the repository might be read-only for us, 
         * or we might have a umask such that even if we did create the 
         * format file, subsequent users would not be able to read it. See 
         * thread starting at http://subversion.tigris.org/servlets/ReadMsg?list=dev&msgNo=97600
         * for more.
         */
        try{
            checkFSFormat(myReposRootPath);
        }catch(SVNException svne){
            throw new SVNException(errorMessage + eol + svne.getMessage());
        }
            
        //7. Read and cache repository UUID
        String uuid=null;
        try{
            uuid = readReposUUID(myReposRootPath);
        }catch(SVNException svne){
            throw new SVNException(errorMessage + eol + svne.getMessage());
        }
        
        String decodedURL = SVNEncodingUtil.uriDecode(getLocation().toString());
        String rootURL = decodedURL.substring(0, decodedURL.indexOf(myReposRootPath) + myReposRootPath.length());    
        setRepositoryCredentials(uuid, SVNURL.parseURIEncoded(rootURL));
    }
    
    private void closeRepository() throws SVNException{
        unlockDBFile();
        unlock();
    }
    
    private String readReposUUID(String reposRootPath) throws SVNException{
        File uuidFile = new File(new File(reposRootPath, SVN_REPOS_DB_DIR), SVN_REPOS_UUID_FILE);
            
        String uuidLine = SVNFSReader.readSingleLine(uuidFile);

        if(uuidLine==null){
            throw new SVNException("svn: Can't read file '" + uuidFile.getAbsolutePath() + "': End of file found");
        }
        
        if(uuidLine.length() > SVN_UUID_FILE_MAX_LENGTH){
            throw new SVNException("svn: Can't read length line in file '" + uuidFile.getAbsolutePath() + "'");
        }
        return uuidLine;
    }
    
    private void checkFSType(String reposRootPath) throws SVNException{
        File fsTypeFile = new File(new File(reposRootPath, SVN_REPOS_DB_DIR), SVN_FS_TYPE_FILENAME);

        String fsType = SVNFSReader.readSingleLine(fsTypeFile);

        if(fsType==null){
            throw new SVNException("svn: Can't read file '" + fsTypeFile.getAbsolutePath() + "': End of file found");
        }
        
        if(!fsType.equals(SVN_REPOS_FSFS_FORMAT)){
            throw new SVNException("svn: Unknown FS type '" + fsType + "'");
        }
    }
    
    static File getRevPropsFile(String reposRootPath, long revision) throws SVNException{
        File revPropsFile = new File(getRevPropsDir(reposRootPath), String.valueOf(revision)); 
        if(!revPropsFile.exists()){
            throw new SVNException("svn: No such revision: " + revision);
        }
        return revPropsFile;
    }
    
    static File getRevPropsDir(String reposRootPath){
        return new File(new File(reposRootPath, SVN_REPOS_DB_DIR), SVN_REPOS_REVPROPS_DIR);
    }
    
    static File getRevsDir(String reposRootPath) {
        return new File(new File(reposRootPath, SVN_REPOS_DB_DIR), SVN_REPOS_REVS_DIR);
    }

    static File getRevFile(String reposRootPath, long revision) throws SVNException{
        File revFile = new File(getRevsDir(reposRootPath), String.valueOf(revision));
        if(!revFile.exists()){
            throw new SVNException("svn: No such revision " + revision);
        }
        return revFile;
    }
    
    private long getYoungestRev(String reposRootPath) throws SVNException{
        File dbCurrentFile = new File(new File(reposRootPath, SVN_REPOS_DB_DIR), SVN_REPOS_DB_CURRENT);
    
        String firstLine = SVNFSReader.readSingleLine(dbCurrentFile);

        if(firstLine==null){
            throw new SVNException("svn: Can't read file '" + dbCurrentFile.getAbsolutePath() + "': End of file found");
        }
        
        String splittedLine[] = firstLine.split(" ");
        long latestRev = -1;
        try{
            latestRev = Long.parseLong(splittedLine[0]);
        }catch(NumberFormatException nfe){
            //svn 1.2 will not report an error if there are no any digit bytes
            //but we decided to introduce this restriction
            throw new SVNException("svn: Can't parse revision number in file '" + dbCurrentFile.getAbsolutePath() + "'");
        }
        
        return latestRev;
    }
    
    public long getLatestRevision() throws SVNException {
        try{
            openRepository();
            return getYoungestRev(myReposRootPath);
        }finally{
            closeRepository();
        }
    }
    
    private Date getTime(String reposRootPath, long revision) throws SVNException{
        String timeString = null;

        timeString = getRevProperty(reposRootPath, revision, SVNRevisionProperty.DATE);
        
        if(timeString==null){
            throw new SVNException("svn: Failed to find time on revision " + revision);
        }

        Date date=null;
        date = SVNTimeUtil.parseDate(timeString);
        if(date==null){
            throw new SVNException("svn: Can't parse date on revision " + revision);
        }
        return date;
    }
    
    private String getRevProperty(String reposRootPath, long revision, String revName) throws SVNException{
        File revPropFile = getRevPropsFile(reposRootPath, revision);
        SVNProperties revProps = new SVNProperties(revPropFile, null);
        return revProps.getPropertyValue(revName);
    }
    
    private long getDatedRev(String reposRootPath, Date date) throws SVNException{
        long latestRev = getYoungestRev(reposRootPath); 
        long topRev = latestRev;
        long botRev = 0;
        long midRev;
        Date curTime=null;
        
        while(botRev <= topRev){
            midRev = (topRev + botRev)/2;
            curTime = getTime(reposRootPath, midRev);
            
            if(curTime.compareTo(date)>0){//overshot
                if((midRev - 1) < 0){
                    return 0;
                }
                Date prevTime = getTime(reposRootPath, midRev-1);
                // see if time falls between midRev and midRev-1: 
                if(prevTime.compareTo(date)<0){
                    return midRev - 1;
                }
                topRev = midRev - 1;
            } else if (curTime.compareTo(date)<0){//undershot
                if((midRev + 1) > latestRev){
                    return latestRev;
                }
                Date nextTime = getTime(reposRootPath, midRev+1);
                // see if time falls between midRev and midRev+1:
                if(nextTime.compareTo(date)>0){
                    return midRev+1;
                }
                botRev = midRev + 1;
            } else {
                return midRev;//exact match!
            }
        }
        return 0;
    }
    
    public long getDatedRevision(Date date) throws SVNException {
        if (date == null) {
            date = new Date(System.currentTimeMillis());
        }

        try{
            openRepository();
            return getDatedRev(myReposRootPath, date);
        }finally{
            closeRepository();
        }
    }

    public Map getRevisionProperties(long revision, Map properties) throws SVNException {
        return null;
    }

    public void setRevisionPropertyValue(long revision, String propertyName, String propertyValue) throws SVNException {
    }

    public String getRevisionPropertyValue(long revision, String propertyName) throws SVNException {
        return null;
    }
    
    public SVNNodeKind checkPath(String path, long revision) throws SVNException {
        return null;
    }

    public long getFile(String path, long revision, Map properties, OutputStream contents) throws SVNException {
        return 0;
    }
    
    //path is relative to this FSRepository's location
    private Collection getDirEntries(SVNRevisionNode parent, String reposRootPath, Map parentDirProps, boolean includeLogs) throws SVNException {
        SVNFSReader fsReader = SVNFSReader.getInstance(reposRootPath);
        Map entries = fsReader.getDirEntries(parent);
        Set keys = entries.keySet();
        Iterator iter = keys.iterator();
        Collection dirEntries = new LinkedList();
        
        
        while(iter.hasNext()){
            String name = (String)iter.next();
            SVNRepEntry repEntry = (SVNRepEntry)entries.get(name);
            if(repEntry != null){
                dirEntries.add(buildDirEntry(repEntry, null, reposRootPath, includeLogs));
            }
        }

        if(parentDirProps != null){
            //first fetch out user props
            Map dirProps = fsReader.getProperties(parent);
            if(dirProps != null && dirProps.size() > 0){
                parentDirProps.putAll(dirProps);
            }
            //now add special non-tweakable metadata props
            Map metaprops = null;
            try{
                metaprops = getMetaProps(reposRootPath, parent.getRevNodeID().getRevision());
            }catch(SVNException svne){
                //
            }
            if(metaprops != null && metaprops.size() > 0){
                parentDirProps.putAll(metaprops);
            }
        }
        
        return dirEntries;
    }
    
    private SVNRevisionNode getParentNode(String reposRootPath, String path, long revision) throws SVNException{
        String absPath = getRepositoryPath(path);
        SVNFSReader fsReader = SVNFSReader.getInstance(reposRootPath);
        
        String nextPathComponent = null;
        SVNRevisionNode parent = fsReader.getRootRevNode(revision);
        SVNRevisionNode child = null;
        if(absPath.indexOf(':') != -1 || absPath.indexOf('|') != -1){
            absPath = (absPath.indexOf('/') != -1) ? absPath.substring(absPath.indexOf('/')) : "";
        }
        
        while(true){
            nextPathComponent = SVNPathUtil.head(absPath);
            absPath = SVNPathUtil.removeHead(absPath);
            
            if(nextPathComponent.length() == 0){
                child = parent;
            }else{
                child = fsReader.getChildDirNode(nextPathComponent, parent);
                if(child == null){
                    throw new SVNException("svn: Attempted to open non-existent child node '" + nextPathComponent + "'" + SVNFileUtil.getNativeEOLMarker() + "svn: File not found: revision " + revision + ", path '" + getRepositoryPath(path) + "'");
                }
                
            }
            parent = child;
            
            if("".equals(absPath)){
                break;
            }
        }
        return parent;
    }
    
    private SVNDirEntry buildDirEntry(SVNRepEntry repEntry, SVNRevisionNode revNode, String reposRootPath, boolean includeLogs) throws SVNException {
        SVNFSReader fsReader = SVNFSReader.getInstance(reposRootPath);
        SVNRevisionNode entryNode = revNode == null ? fsReader.getRevNode(repEntry.getId()) : revNode;

        //dir size is equated to 0
        long size = 0;
        
        if(entryNode.getType() == SVNNodeKind.FILE){
            size = entryNode.getTextRepresentation().getExpandedSize();
        }
        
        Map props = null;
        props = fsReader.getProperties(entryNode);
        boolean hasProps = (props == null || props.size() == 0) ? false : true;
        
        //should it be an exception if getting a rev property is impossible, hmmm?
        Map revProps = null;
        try{
            revProps = getAllRevProps(reposRootPath, repEntry.getId().getRevision());
        }catch(SVNException svne){
            //
        }
        
        String lastAuthor = null;
        String log = null;
        if(revProps != null && revProps.size() > 0){
            lastAuthor = (String)revProps.get(SVNRevisionProperty.AUTHOR);
            log = (String)revProps.get(SVNRevisionProperty.LOG);
        }
        log = log == null || !includeLogs ? "" : log;
        
        Date lastCommitDate = null;
        try{
            lastCommitDate = getTime(reposRootPath, repEntry.getId().getRevision());
        }catch(SVNException svne){
            //
        }
        
        return new SVNDirEntry(repEntry.getName(), repEntry.getType(), size , hasProps, repEntry.getId().getRevision(), lastCommitDate, lastAuthor, log);
    }
    
    private Map getMetaProps(String reposRootPath, long revision) throws SVNException {
        Map metaProps = new HashMap();
        Map revProps = null;
        revProps = getAllRevProps(reposRootPath, revision);
        String author = (String)revProps.get(SVNRevisionProperty.AUTHOR);
        String date = (String)revProps.get(SVNRevisionProperty.DATE);
        String uuid = super.getRepositoryUUID();
        String rev = String.valueOf(revision);
        
        metaProps.put(SVNProperty.LAST_AUTHOR, author != null ? author : "");
        metaProps.put(SVNProperty.COMMITTED_DATE, date != null ? date : "");
        metaProps.put(SVNProperty.COMMITTED_REVISION, rev);
        metaProps.put(SVNProperty.UUID, uuid != null ? uuid : "");
        return metaProps;
    }
    
    private Map getAllRevProps(String reposRootPath, long revision) throws SVNException {
        Map allProps = new HashMap();
        String author = getRevProperty(reposRootPath, revision, SVNRevisionProperty.AUTHOR);
        String date = getRevProperty(reposRootPath, revision, SVNRevisionProperty.DATE);
        String log = getRevProperty(reposRootPath, revision, SVNRevisionProperty.LOG);
        
        allProps.put(SVNRevisionProperty.AUTHOR, author);
        allProps.put(SVNRevisionProperty.DATE, date);
        allProps.put(SVNRevisionProperty.LOG, log);
        
        return allProps;
    }
    
    public long getDir(String path, long revision, Map properties, ISVNDirEntryHandler handler) throws SVNException {
        try{
            openRepository();
            if(!super.isValidRevision(revision)){
                revision = getYoungestRev(myReposRootPath);
            }
            SVNRevisionNode parent = getParentNode(myReposRootPath, path, revision); 
            Collection entries = getDirEntries(parent, myReposRootPath, properties, false);
            Iterator iterator = entries.iterator();
            while(iterator.hasNext()){
                SVNDirEntry entry = (SVNDirEntry)iterator.next();
                handler.handleDirEntry(entry);
            }
            
            return revision;
        }finally{
            closeRepository();
        }
    }
    
    public SVNDirEntry getDir(String path, long revision, boolean includeCommitMessages, Collection entries) throws SVNException{
        try{
            openRepository();
            if(!super.isValidRevision(revision)){
                revision = getYoungestRev(myReposRootPath);
            }
            SVNRevisionNode parent = getParentNode(myReposRootPath, path, revision);
            entries.addAll(getDirEntries(parent, myReposRootPath, null, includeCommitMessages));
            SVNDirEntry parentDirEntry = null;
            String parentName = SVNPathUtil.tail(parent.getCreatedPath());
            parentName = parentName.length() > 0 ? parentName : "/"; 
            parentDirEntry = buildDirEntry(new SVNRepEntry(parent.getRevNodeID(), parent.getType(), parentName), parent, myReposRootPath, includeCommitMessages);
            return parentDirEntry;
        }finally{
            closeRepository();
        }
    }
    
    public int getFileRevisions(String path, long startRevision, long endRevision, ISVNFileRevisionHandler handler) throws SVNException {
        return 0;
    }

    public long log(String[] targetPaths, long startRevision, long endRevision, boolean changedPath, boolean strictNode, long limit, ISVNLogEntryHandler handler) throws SVNException {
        return 0;
    }

    public int getLocations(String path, long pegRevision, long[] revisions, ISVNLocationEntryHandler handler) throws SVNException {
        return 0;
    }

    public void diff(SVNURL url, long revision, String target, boolean ignoreAncestry, boolean recursive, ISVNReporterBaton reporter, ISVNEditor editor) throws SVNException {
    }

    public void diff(SVNURL url, long targetRevision, long revision, String target, boolean ignoreAncestry, boolean recursive, ISVNReporterBaton reporter, ISVNEditor editor) throws SVNException {
    }

    public void update(long revision, String target, boolean recursive, ISVNReporterBaton reporter, ISVNEditor editor) throws SVNException {
    }

    public void status(long revision, String target, boolean recursive, ISVNReporterBaton reporter, ISVNEditor editor) throws SVNException {
    }

    public void update(SVNURL url, long revision, String target, boolean recursive, ISVNReporterBaton reporter, ISVNEditor editor) throws SVNException {
    }

    public SVNDirEntry info(String path, long revision) throws SVNException {
        return null;
    }

    public ISVNEditor getCommitEditor(String logMessage, Map locks, boolean keepLocks, ISVNWorkspaceMediator mediator) throws SVNException {
        return null;
    }

    public SVNLock getLock(String path) throws SVNException {
        return null;
    }

    public SVNLock[] getLocks(String path) throws SVNException {
        return null;
    }

    public void lock(Map pathsToRevisions, String comment, boolean force, ISVNLockHandler handler) throws SVNException {
    }

    public void unlock(Map pathToTokens, boolean force, ISVNLockHandler handler) throws SVNException {
    }

    public void closeSession() throws SVNException {
    }
}
