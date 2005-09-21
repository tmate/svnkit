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
import org.tmatesoft.svn.core.internal.util.SVNTimeUtil;
import org.tmatesoft.svn.core.internal.wc.SVNTranslator;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNRevisionProperty;
import org.tmatesoft.svn.core.internal.wc.SVNProperties;


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
    private String SVN_REPOS_FS_FORMAT = "format";
    private int    SVN_FS_FORMAT_NUMBER = 1;
    private String SVN_FS_TYPE_FILENAME = "fs-type";
    private String SVN_REPOS_UUID_FILE = "uuid";
    private String SVN_REPOS_REVPROPS_DIR = "revprops";
    private String SVN_REPOS_REVS_DIR = "revs";

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

    //to mean the end of a file 
    private long FILE_END_POS = -1;
    
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
    
    private byte[] readBytesFromFile(long pos, long offset, byte[] buffer, long bytesToRead, File file) throws SVNException{
        RandomAccessFile revRAF = null;
        try{
            revRAF = new RandomAccessFile(file, "r");
        }catch(FileNotFoundException fnfe){
            throw new SVNException("svn: Can't open file '" + file.getAbsolutePath() + "': " + fnfe.getMessage());
        }
        
        long fileLength = -1;
        try{
            fileLength = revRAF.length();
        }catch(IOException ioe){
            throw new SVNException("svn: Can't open file '" + file.getAbsolutePath() + "': " + ioe.getMessage());
        }
        
        if(pos == FILE_END_POS){
            pos = fileLength - 1 + offset;
        }else{
            pos = pos + offset;
        }
        if(bytesToRead > buffer.length || bytesToRead < 0){
            bytesToRead = buffer.length;
        }
        
        try {
            while (true) {
                int l = revRAF.read(buffer, (int)(pos + 1), (int)bytesToRead);
                if (l <= 0) {
                    break;
                }
            }
        } catch (IOException ioe) {
            throw new SVNException("svn: Can't read length line in file '" + file.getAbsolutePath() + "'", ioe);
        } finally {
            if(revRAF!=null){
                try {
                    revRAF.close();
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
            throw new SVNException("svn: Can't open file '" + formatFile.getAbsolutePath() + "'", fnfe);
        }

        String firstLine=null;

        try{
            firstLine = br.readLine();
        }catch(IOException ioe){
            throw new SVNException("svn: Can't read file '" + formatFile.getAbsolutePath() + "'", ioe);
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
            throw new SVNException("svn: Error opening db lockfile" + SVNTranslator.getEOL(SVNProperty.EOL_STYLE_NATIVE) + "svn: Can't open file '" + dbLockFile.getAbsolutePath() + "'");
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
            throw new SVNException("svn: Error opening db lockfile" + SVNTranslator.getEOL(SVNProperty.EOL_STYLE_NATIVE) + "svn: Can't open file '" + dbLockFile.getAbsolutePath() + "'");
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
            throw new SVNException("svn: Error opening db lockfile" + SVNTranslator.getEOL(SVNProperty.EOL_STYLE_NATIVE) + "svn: Can't get shared lock on file '" + dbLockFile.getAbsolutePath() + "'");
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
        
        String eolBytes = new String(SVNTranslator.getEOL(SVNProperty.EOL_STYLE_NATIVE));
        
        String errorMessage = "svn: Unable to open an ra_local session to URL" + eolBytes + "svn: Unable to open repository '" + getLocation() + "'";
        
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
            throw new SVNException(errorMessage + eolBytes + svne.getMessage());
        }
    
        //3. Lock 'db.lock' file non-exclusively, blocking, for reading only
        try{
            lockDBFile(myReposRootPath);
        }catch(SVNException svne){
            throw new SVNException(errorMessage + eolBytes + svne.getMessage());
        }
            
        //4. Check FS type for 'fsfs'
        try{
            checkFSType(myReposRootPath);
        }catch(SVNException svne){
            throw new SVNException(errorMessage + eolBytes + svne.getMessage());
        }
            
        //5. Attempt to open the 'current' file of this repository
        File dbCurrentFile = new File(new File(myReposRootPath, SVN_REPOS_DB_DIR), SVN_REPOS_DB_CURRENT);
        FileInputStream fis = null;
        try{
            fis = new FileInputStream(dbCurrentFile);
        }catch(FileNotFoundException fnfe){
            throw new SVNException(errorMessage + eolBytes + "svn: Can't open file '" + dbCurrentFile.getAbsolutePath() + "'");
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
            throw new SVNException(errorMessage + eolBytes + svne.getMessage());
        }
            
        //7. Read and cache repository UUID
        String uuid=null;
        try{
            uuid = readReposUUID(myReposRootPath);
        }catch(SVNException svne){
            throw new SVNException(errorMessage + eolBytes + svne.getMessage());
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
            
        BufferedReader br=null;
        try{
            br = new BufferedReader(new InputStreamReader(new FileInputStream(uuidFile)));
        }catch(FileNotFoundException fnfe){
            if(br!=null){
                try{
                    br.close();
                }catch(IOException ioe){
                    //
                }
            }
            throw new SVNException("svn: Can't open file '" + uuidFile.getAbsolutePath() + "'", fnfe);
        }
    
        String uuidLine=null;

        //svn makes synchronization
        synchronized(myMutex){
            try{
                uuidLine = br.readLine();
            }catch(IOException ioe){
                throw new SVNException("svn: Can't read file '" + uuidFile.getAbsolutePath() + "'", ioe);
            }finally{
                if(br!=null){
                    try{
                        br.close();
                    }catch(IOException iioe){
                        //
                    }
                }
            }
        }
       
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
            throw new SVNException("svn: Can't open file '" + fsTypeFile.getAbsolutePath() + "'", fnfe);
        }

        String fsType=null;

        try{
            fsType = br.readLine();
        }catch(IOException ioe){
            throw new SVNException("svn: Can't read file '" + fsTypeFile.getAbsolutePath() + "'", ioe);
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
            throw new SVNException("svn: Can't read file '" + fsTypeFile.getAbsolutePath() + "': End of file found");
        }
        
        if(!fsType.equals(SVN_REPOS_FSFS_FORMAT)){
            throw new SVNException("svn: Unknown FS type '" + fsType + "'");
        }
    }
    
    private File getRevpropsDir(){
        return new File(new File(myReposRootPath, SVN_REPOS_DB_DIR), SVN_REPOS_REVPROPS_DIR);
    }
    
    private long getYoungestRev(String reposRootPath) throws SVNException{
        File dbCurrentFile = new File(new File(reposRootPath, SVN_REPOS_DB_DIR), SVN_REPOS_DB_CURRENT);
    
        BufferedReader br=null;
        try{
            br = new BufferedReader(new InputStreamReader(new FileInputStream(dbCurrentFile)));
        }catch(FileNotFoundException fnfe){
            if(br!=null){
                try{
                    br.close();
                }catch(IOException ioe){
                    //
                }
            }
            throw new SVNException("svn: Can't open file '" + dbCurrentFile.getAbsolutePath() + "'", fnfe);
        }
    
        String firstLine = null;

        //svn makes synchronization
        synchronized(myMutex){
            try{
                firstLine = br.readLine();
            }catch(IOException ioe){
                throw new SVNException("svn: Can't read file '" + dbCurrentFile.getAbsolutePath() + "'", ioe);
            }finally{
                if(br!=null){
                    try{
                        br.close();
                    }catch(IOException iioe){
                        //
                    }
                }
            }
        }

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
    
    private Date getTime(long revision) throws SVNException{
        File revPropFile = new File(getRevpropsDir(), String.valueOf(revision));
        SVNProperties revProps = new SVNProperties(revPropFile, null);
        String timeString=null;
        
        synchronized(myMutex){
            timeString =  revProps.getPropertyValue(SVNRevisionProperty.DATE);
            if(timeString==null){
                throw new SVNException("svn: Failed to find time on revision " + revision);
            }
        }

        Date date=null;
        date = SVNTimeUtil.parseDate(timeString);//dateFormatter.parse(timeString);
        if(date==null){
            throw new SVNException("svn: Can't parse date on revision " + revision);
        }
        return date;
    }
    
    private long getDatedRev(String reposRootPath, Date date) throws SVNException{
        long latestRev = getYoungestRev(reposRootPath); 
        long topRev = latestRev;
        long botRev = 0;
        long midRev;
        Date curTime=null;
        
        while(botRev <= topRev){
            midRev = (topRev + botRev)/2;
            curTime = getTime(midRev);
            
            if(curTime.compareTo(date)>0){//overshot
                if((midRev - 1) < 0){
                    return 0;
                }
                Date prevTime = getTime(midRev-1);
                // see if time falls between midRev and midRev-1: 
                if(prevTime.compareTo(date)<0){
                    return midRev - 1;
                }
                topRev = midRev - 1;
            } else if (curTime.compareTo(date)<0){//undershot
                if((midRev + 1) > latestRev){
                    return latestRev;
                }
                Date nextTime = getTime(midRev+1);
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
     * 
     * @param path
     * @param revision
     * @param includeCommitMessages
     * @param entries
     * @return
     * @throws SVNException
     */
    public SVNDirEntry getDir(String path, long revision, boolean includeCommitMessages, Collection entries) throws SVNException{
        return null;
    }

    private void getRootChangesOffset(String reposRootPath, long revision, long rootOffset, long changesOffset) throws SVNException{
        String eolBytes = new String(SVNTranslator.getEOL(SVNProperty.EOL_STYLE_NATIVE));
        
        File revsDir = new File(new File(reposRootPath, SVN_REPOS_DB_DIR), SVN_REPOS_REVS_DIR);
        File revFile = new File(revsDir, String.valueOf(revision));
        byte[] buffer = new byte[64];
        
        try{
            /* svn: We will assume that the last line containing the two offsets
             * will never be longer than 64 characters.
             * Read in this last block, from which we will identify the last line. 
             */
            readBytesFromFile(FILE_END_POS, -64, buffer, 64, revFile);
        }catch(SVNException svne){
            throw new SVNException(svne.getMessage() + eolBytes + "svn: No such revision " + revision);
        }
        
        
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
        
        try{
            openRepository();
            
            path = getRepositoryPath(path);
            if(!super.isValidRevision(revision)){
                revision = getYoungestRev(myReposRootPath);
            }
            
        }finally{
            closeRepository();
        }
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
