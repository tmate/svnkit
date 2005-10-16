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
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.ISVNFileRevisionHandler;
import org.tmatesoft.svn.core.io.ISVNLocationEntryHandler;
import org.tmatesoft.svn.core.io.ISVNLockHandler;
import org.tmatesoft.svn.core.io.ISVNReporterBaton;
import org.tmatesoft.svn.core.io.ISVNSession;
import org.tmatesoft.svn.core.io.ISVNWorkspaceMediator;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.util.SVNTimeUtil;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNRevisionProperty;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;

/**
 * @version 1.0
 * @author TMate Software Ltd.
 */
public class FSRepository extends SVNRepository {
    private FileLock myDBSharedLock;
    private File myReposRootDir;
    // db.lock file representation for synchronizing
    private RandomAccessFile myDBLockFile;

    
    protected FSRepository(SVNURL location, ISVNSession options) {
        super(location, options);
    }

    public void testConnection() throws SVNException {
        // try to open and close a repository
        try {
            openRepository();
        } finally {
            closeRepository();
        }
    }

    private void lockDBFile(File reposRootDir) throws SVNException {
        // 1. open db.lock for shared reading (?? just like in the svn code)
        File dbLockFile = FSRepositoryUtil.getDBLockFile(reposRootDir);

        if (!dbLockFile.exists()) {
            SVNErrorManager.error("svn: Error opening db lockfile" + SVNFileUtil.getNativeEOLMarker() + "svn: Can't open file '" + dbLockFile.getAbsolutePath() + "'");
        }

        myDBLockFile = null;
        try {
            myDBLockFile = new RandomAccessFile(dbLockFile, "r");
        } catch (FileNotFoundException fnfe) {
            SVNFileUtil.closeFile(myDBLockFile);
            SVNErrorManager.error("svn: Error opening db lockfile" + SVNFileUtil.getNativeEOLMarker() + "svn: Can't open file '" + dbLockFile.getAbsolutePath() + "': " + fnfe.getMessage());
        }

        // 2. lock db.lock blocking, not exclusively
        FileChannel fch = myDBLockFile.getChannel();
        try {
            myDBSharedLock = fch.lock(0, Long.MAX_VALUE, true);
        } catch (IOException ioe) {
            SVNFileUtil.closeFile(myDBLockFile);
            if (myDBSharedLock != null) {
                try {
                    myDBSharedLock.release();
                } catch (IOException ioex) {
                    //
                }
            }
            SVNErrorManager.error("svn: Error opening db lockfile" + SVNFileUtil.getNativeEOLMarker() + "svn: Can't get shared lock on file '" + dbLockFile.getAbsolutePath() + "': " + ioe.getMessage());
        }
    }

    private void unlockDBFile() throws SVNException {
        // 1. release the shared lock
        if (myDBSharedLock != null) {
            try {
                myDBSharedLock.release();
            } catch (IOException ioe) {
                File dbLockFile = FSRepositoryUtil.getDBLockFile(myReposRootDir);
                SVNErrorManager.error("svn: Can't unlock file '" + dbLockFile.getAbsoluteFile() + "': " + ioe.getMessage());
            } finally {
                // 2. close 'db.lock' file
                SVNFileUtil.closeFile(myDBLockFile);
            }
        }
    }

    private void openRepository() throws SVNException {
        lock();

        String eol = SVNFileUtil.getNativeEOLMarker();

        String errorMessage = "svn: Unable to open an ra_local session to URL" + eol + "svn: Unable to open repository '" + getLocation() + "'";

        // Perform steps similar to svn's ones
        // 1. Find repos root
        if (myReposRootDir == null) {
            try {
                myReposRootDir = FSRepositoryUtil.findRepositoryRoot(new File(getLocation().getPath()).getCanonicalFile());//findRepositoryRoot(getLocation().getPath());
            } catch (SVNException svne) {
                SVNErrorManager.error(errorMessage);
            }catch(IOException ioe){
                SVNErrorManager.error(errorMessage + ": " + ioe.getMessage());
            }
        }

        // 2. Check repos format (the format file must exist!)
        try {
            FSRepositoryUtil.checkRepositoryFormat(myReposRootDir);//checkReposFormat(myReposRootPath);
        } catch (SVNException svne) {
            SVNErrorManager.error(errorMessage + eol + svne.getMessage());
        }

        // 3. Lock 'db.lock' file non-exclusively, blocking, for reading only
        try {
            lockDBFile(myReposRootDir);
        } catch (SVNException svne) {
            SVNErrorManager.error(errorMessage + eol + svne.getMessage());
        }

        // 4. Check FS type for 'fsfs'
        try {
            FSRepositoryUtil.checkFSType(myReposRootDir);
        } catch (SVNException svne) {
            SVNErrorManager.error(errorMessage + eol + svne.getMessage());
        }

        // 5. Attempt to open the 'current' file of this repository
        File dbCurrentFile = FSRepositoryUtil.getFSCurrentFile(myReposRootDir);
        FileInputStream fis = null;
        try {
            fis = new FileInputStream(dbCurrentFile);
        } catch (FileNotFoundException fnfe) {
            SVNErrorManager.error(errorMessage + eol + "svn: Can't open file '" + dbCurrentFile.getAbsolutePath() + "' " + fnfe.getMessage());
        } finally {
            SVNFileUtil.closeFile(fis);
        }

        /*
         * 6. Check the FS format number (db/format). Treat an absent format
         * file as format 1. Do not try to create the format file on the fly,
         * because the repository might be read-only for us, or we might have a
         * umask such that even if we did create the format file, subsequent
         * users would not be able to read it. See thread starting at
         * http://subversion.tigris.org/servlets/ReadMsg?list=dev&msgNo=97600
         * for more.
         */
        try {
            FSRepositoryUtil.checkFSFormat(myReposRootDir);
        } catch (SVNException svne) {
            SVNErrorManager.error(errorMessage + eol + svne.getMessage());
        }

        // 7. Read and cache repository UUID
        if(super.getRepositoryUUID() == null){
            String uuid = null;
            try {
                uuid = FSRepositoryUtil.getRepositoryUUID(myReposRootDir);
            } catch (SVNException svne) {
                SVNErrorManager.error(errorMessage + eol + svne.getMessage());
            }
    
            String rootDir = null;
            try{
                rootDir = myReposRootDir.getCanonicalPath();
            }catch(IOException ioe){
                rootDir = myReposRootDir.getAbsolutePath();
            }
            rootDir = rootDir.replace(File.separatorChar, '/');
            if(!rootDir.startsWith("/")){
                rootDir = "/" + rootDir;
            }
            setRepositoryCredentials(uuid, SVNURL.parseURIEncoded(getLocation().getProtocol() + "://" + rootDir));
        }
    }

    private void closeRepository() throws SVNException {
        try{
            unlockDBFile();
        }finally{
            unlock();
        }
    }

    public File getRepositoryRootDir(){
        return myReposRootDir;
    }
    
    private long getYoungestRev(File reposRootDir) throws SVNException {
        File dbCurrentFile = FSRepositoryUtil.getFSCurrentFile(reposRootDir);//new File(new File(reposRootPath, SVN_REPOS_DB_DIR), SVN_REPOS_DB_CURRENT);

        String firstLine = FSReader.readSingleLine(dbCurrentFile);

        if (firstLine == null) {
            SVNErrorManager.error("svn: Can't read file '" + dbCurrentFile.getAbsolutePath() + "': End of file found");
        }

        String splittedLine[] = firstLine.split(" ");
        long latestRev = -1;
        try {
            latestRev = Long.parseLong(splittedLine[0]);
        } catch (NumberFormatException nfe) {
            // svn 1.2 will not report an error if there are no any digit bytes
            // but we decided to introduce this restriction
            SVNErrorManager.error("svn: Can't parse revision number in file '" + dbCurrentFile.getAbsolutePath() + "'");
        }

        return latestRev;
    }

    public long getLatestRevision() throws SVNException {
        try {
            openRepository();
            return getYoungestRev(myReposRootDir);
        } finally {
            closeRepository();
        }
    }

    private Date getTime(File reposRootDir, long revision) throws SVNException {
        String timeString = null;

        timeString = FSRepositoryUtil.getRevisionProperty(reposRootDir, revision, SVNRevisionProperty.DATE);//getRevProperty(reposRootPath, revision, SVNRevisionProperty.DATE);

        if (timeString == null) {
            SVNErrorManager.error("svn: Failed to find time on revision " + revision);
        }

        Date date = null;
        date = SVNTimeUtil.parseDate(timeString);
        if (date == null) {
            SVNErrorManager.error("svn: Can't parse date on revision " + revision);
        }
        return date;
    }

    private long getDatedRev(File reposRootDir, Date date) throws SVNException {
        long latestRev = getYoungestRev(reposRootDir);
        long topRev = latestRev;
        long botRev = 0;
        long midRev;
        Date curTime = null;

        while (botRev <= topRev) {
            midRev = (topRev + botRev) / 2;
            curTime = getTime(reposRootDir, midRev);

            if (curTime.compareTo(date) > 0) {// overshot
                if ((midRev - 1) < 0) {
                    return 0;
                }
                Date prevTime = getTime(reposRootDir, midRev - 1);
                // see if time falls between midRev and midRev-1:
                if (prevTime.compareTo(date) < 0) {
                    return midRev - 1;
                }
                topRev = midRev - 1;
            } else if (curTime.compareTo(date) < 0) {// undershot
                if ((midRev + 1) > latestRev) {
                    return latestRev;
                }
                Date nextTime = getTime(reposRootDir, midRev + 1);
                // see if time falls between midRev and midRev+1:
                if (nextTime.compareTo(date) > 0) {
                    return midRev + 1;
                }
                botRev = midRev + 1;
            } else {
                return midRev;// exact match!
            }
        }
        return 0;
    }

    
    
    public long getDatedRevision(Date date) throws SVNException {
        if (date == null) {
            date = new Date(System.currentTimeMillis());
        }

        try {
            openRepository();
            return getDatedRev(myReposRootDir, date);
        } finally {
            closeRepository();
        }
    }

    public Map getRevisionProperties(long revision, Map properties) throws SVNException {
        assertValidRevision(revision);

        try {
            openRepository();
            Map revProps = FSRepositoryUtil.getRevisionProperties(myReposRootDir, revision);
            if (properties == null) {
                properties = revProps;
            }
        } finally {
            closeRepository();
        }
        return properties;
    }

    public void setRevisionPropertyValue(long revision, String propertyName, String propertyValue) throws SVNException {
        assertValidRevision(revision);
        try {
            openRepository();

            if (!SVNProperty.isRegularProperty(propertyName)) {
                SVNErrorManager.error("svn: Storage of non-regular property '" + propertyName + "' is disallowed through the repository interface, and could indicate a bug in your client");
            }
            String userName = System.getProperty("user.name");
            String oldValue = FSRepositoryUtil.getRevisionProperty(myReposRootDir, revision, propertyName);
            String action = null;
            if(propertyValue == null){//delete
                action = FSHooks.REVPROP_DELETE;
            }else if(oldValue == null){//add
                action = FSHooks.REVPROP_ADD;
            }else{//modify
                action = FSHooks.REVPROP_MODIFY;
            }
            
            FSHooks.runPreRevPropChangeHook(myReposRootDir, propertyName, propertyValue, super.getRepositoryPath(""), userName, revision, action);
            FSRepositoryUtil.setRevisionProperty(myReposRootDir, revision, propertyName, propertyValue, oldValue, super.getRepositoryPath(""), userName, action);
            FSHooks.runPostRevPropChangeHook(myReposRootDir, propertyName, oldValue, super.getRepositoryPath(""), userName, revision, action);
        } finally {
            closeRepository();
        }
    }

    public String getRevisionPropertyValue(long revision, String propertyName) throws SVNException {
        assertValidRevision(revision);
        if (propertyName == null) {
            return null;
        }
        try {
            openRepository();
            return FSRepositoryUtil.getRevisionProperty(myReposRootDir, revision, propertyName);
        } finally {
            closeRepository();
        }
    }

    public SVNNodeKind checkPath(String path, long revision) throws SVNException {
        return null;
    }

    public long getFile(String path, long revision, Map properties, OutputStream contents) throws SVNException {
        try {
            openRepository();
            if (!SVNRepository.isValidRevision(revision)) {
                revision = getYoungestRev(myReposRootDir);
            }
            String parentPath = SVNPathUtil.removeTail(path);
            FSRevisionNode parent = getParentNode(myReposRootDir, parentPath, revision);
            if(parent == null){
                SVNErrorManager.error("svn: Attempted to open non-existent child node '" + path + "'" + SVNFileUtil.getNativeEOLMarker() + "svn: File not found: revision "
                        + revision + ", path '" + getRepositoryPath(path) + "'");
            }
            String childName = SVNPathUtil.tail(path);
            FSRevisionNode childNode = FSReader.getChildDirNode(childName, parent, myReposRootDir);
            if(childNode == null){
                SVNErrorManager.error("svn: Attempted to open non-existent child node '" + childName + "'");
            }else if(childNode.getType() != SVNNodeKind.FILE){
                SVNErrorManager.error("svn: Path at '" + path + "' is not a file, but " + childNode.getType());
            }
            FSReader.readDeltaRepresentation(childNode.getTextRepresentation(), contents, myReposRootDir);
            if(properties != null){
                properties.putAll(collectProperties(childNode, myReposRootDir));
            }
            return revision;
        } finally {
            closeRepository();
        }
    }

    // path is relative to this FSRepository's location
    private Collection getDirEntries(FSRevisionNode parent, File reposRootDir, Map parentDirProps, boolean includeLogs) throws SVNException {
        Map entries = FSReader.getDirEntries(parent, reposRootDir);
        Set keys = entries.keySet();
        Iterator dirEntries = keys.iterator();
        Collection dirEntriesList = new LinkedList();

        while (dirEntries.hasNext()) {
            String name = (String) dirEntries.next();
            FSRepresentationEntry repEntry = (FSRepresentationEntry) entries.get(name);
            if (repEntry != null) {
                dirEntriesList.add(buildDirEntry(repEntry, null, reposRootDir, includeLogs));
            }
        }

        if (parentDirProps != null) {
            parentDirProps.putAll(collectProperties(parent, reposRootDir));
        }

        return dirEntriesList;
    }
    
    private Map collectProperties(FSRevisionNode revNode, File reposRootDir) throws SVNException {
        Map properties = new HashMap();
        // first fetch out user props
        Map versionedProps = FSReader.getProperties(revNode, reposRootDir);
        if (versionedProps != null && versionedProps.size() > 0) {
            properties.putAll(versionedProps);
        }
        // now add special non-tweakable metadata props
        Map metaprops = null;
        try {
            metaprops = FSRepositoryUtil.getMetaProps(reposRootDir, revNode.getRevNodeID().getRevision(), this);
        } catch (SVNException svne) {
            //
        }
        if (metaprops != null && metaprops.size() > 0) {
            properties.putAll(metaprops);
        }
        return properties;
    }

    private FSRevisionNode getParentNode(File reposRootDir, String path, long revision) throws SVNException {
        String absPath = super.getRepositoryPath(path);

        String nextPathComponent = null;
        FSRevisionNode parent = FSReader.getRootRevNode(reposRootDir, revision);
        FSRevisionNode child = null;
        if (absPath.indexOf(':') != -1 || absPath.indexOf('|') != -1) {
            absPath = (absPath.indexOf('/') != -1) ? absPath.substring(absPath.indexOf('/')) : "";
        }

        while (true) {
            nextPathComponent = SVNPathUtil.head(absPath);
            absPath = SVNPathUtil.removeHead(absPath);

            if (nextPathComponent.length() == 0) {
                child = parent;
            } else {
                child = FSReader.getChildDirNode(nextPathComponent, parent, reposRootDir);
                if (child == null) {
                    return null;
                }

            }
            parent = child;

            if ("".equals(absPath)) {
                break;
            }
        }
        return parent;
    }

    private SVNDirEntry buildDirEntry(FSRepresentationEntry repEntry, FSRevisionNode revNode, File reposRootDir, boolean includeLogs) throws SVNException {
        FSRevisionNode entryNode = revNode == null ? FSReader.getRevNode(reposRootDir, repEntry.getId()) : revNode;

        // dir size is equated to 0
        long size = 0;

        if (entryNode.getType() == SVNNodeKind.FILE) {
            size = entryNode.getTextRepresentation().getExpandedSize();
        }

        Map props = null;
        props = FSReader.getProperties(entryNode, reposRootDir);
        boolean hasProps = (props == null || props.size() == 0) ? false : true;

        // should it be an exception if getting a rev property is impossible,
        // hmmm?
        Map revProps = null;
        try {
            revProps = FSRepositoryUtil.getRevisionProperties(reposRootDir, repEntry.getId().getRevision());
        } catch (SVNException svne) {
            //
        }

        String lastAuthor = null;
        String log = null;
        if (revProps != null && revProps.size() > 0) {
            lastAuthor = (String) revProps.get(SVNRevisionProperty.AUTHOR);
            log = (String) revProps.get(SVNRevisionProperty.LOG);
        }

        Date lastCommitDate = null;
        try {
            lastCommitDate = getTime(reposRootDir, repEntry.getId().getRevision());
        } catch (SVNException svne) {
            //
        }

        return new SVNDirEntry(repEntry.getName(), repEntry.getType(), size, hasProps, repEntry.getId().getRevision(), lastCommitDate, lastAuthor, includeLogs ? log : null);
    }

    public long getDir(String path, long revision, Map properties, ISVNDirEntryHandler handler) throws SVNException {
        try {
            openRepository();
            if (!SVNRepository.isValidRevision(revision)) {
                revision = getYoungestRev(myReposRootDir);
            }
            FSRevisionNode parent = getParentNode(myReposRootDir, path, revision);
            if(parent == null){
                SVNErrorManager.error("svn: Attempted to open non-existent child node '" + path + "'" + SVNFileUtil.getNativeEOLMarker() + "svn: File not found: revision "
                        + revision + ", path '" + getRepositoryPath(path) + "'");
            }
            Collection entriesCollection = getDirEntries(parent, myReposRootDir, properties, false);
            Iterator entries = entriesCollection.iterator();
            while (entries.hasNext()) {
                SVNDirEntry entry = (SVNDirEntry) entries.next();
                handler.handleDirEntry(entry);
            }

            return revision;
        } finally {
            closeRepository();
        }
    }

    public SVNDirEntry getDir(String path, long revision, boolean includeCommitMessages, Collection entries) throws SVNException {
        try {
            openRepository();
            if (!SVNRepository.isValidRevision(revision)) {
                revision = getYoungestRev(myReposRootDir);
            }
            FSRevisionNode parent = getParentNode(myReposRootDir, path, revision);
            if(parent == null){
                SVNErrorManager.error("svn: Attempted to open non-existent child node '" + path + "'" + SVNFileUtil.getNativeEOLMarker() + "svn: File not found: revision "
                        + revision + ", path '" + getRepositoryPath(path) + "'");
            }
            entries.addAll(getDirEntries(parent, myReposRootDir, null, includeCommitMessages));
            SVNDirEntry parentDirEntry = buildDirEntry(new FSRepresentationEntry(parent.getRevNodeID(), parent.getType(), ""), parent, myReposRootDir, false);
            parentDirEntry.setPath(parent.getCreatedPath());
            return parentDirEntry;
        } finally {
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
