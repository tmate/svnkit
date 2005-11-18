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
import java.io.InputStream;
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
import org.tmatesoft.svn.core.io.ISVNReporter;
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
public class FSRepository extends SVNRepository implements ISVNReporter {

    private FileLock myDBSharedLock;
    private File myReposRootDir;
    // db.lock file representation for synchronizing
    private RandomAccessFile myDBLockFile;

    private ReporterContext myReporterContext;// for reporter

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
            SVNErrorManager.error("svn: Error opening db lockfile" + SVNFileUtil.getNativeEOLMarker() + "svn: Can't get shared lock on file '" + dbLockFile.getAbsolutePath() + "': "
                    + ioe.getMessage());
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
        try {
            myReposRootDir = FSRepositoryUtil.findRepositoryRoot(new File(getLocation().getPath()).getCanonicalFile());// findRepositoryRoot(getLocation().getPath());
        } catch (SVNException svne) {
            SVNErrorManager.error(errorMessage);
        } catch (IOException ioe) {
            SVNErrorManager.error(errorMessage + ": " + ioe.getMessage());
        }

        // 2. Check repos format (the format file must exist!)
        try {
            FSRepositoryUtil.checkRepositoryFormat(myReposRootDir);// checkReposFormat(myReposRootPath);
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
        String uuid = null;
        try {
            uuid = FSRepositoryUtil.getRepositoryUUID(myReposRootDir);
        } catch (SVNException svne) {
            SVNErrorManager.error(errorMessage + eol + svne.getMessage());
        }
        String rootDir = null;
        try {
            rootDir = myReposRootDir.getCanonicalPath();
        } catch (IOException ioe) {
            rootDir = myReposRootDir.getAbsolutePath();
        }
        rootDir = rootDir.replace(File.separatorChar, '/');
        if (!rootDir.startsWith("/")) {
            rootDir = "/" + rootDir;
        }
        setRepositoryCredentials(uuid, SVNURL.parseURIEncoded(getLocation().getProtocol() + "://" + rootDir));
    }

    private void closeRepository() throws SVNException {
        try {
            unlockDBFile();
        } finally {
            unlock();
        }
    }

    public File getRepositoryRootDir() {
        return myReposRootDir;
    }

    private long getYoungestRev(File reposRootDir) throws SVNException {
        File dbCurrentFile = FSRepositoryUtil.getFSCurrentFile(reposRootDir);// new
                                                                                // File(new
                                                                                // File(reposRootPath,
                                                                                // SVN_REPOS_DB_DIR),
                                                                                // SVN_REPOS_DB_CURRENT);

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

        timeString = FSRepositoryUtil.getRevisionProperty(reposRootDir, revision, SVNRevisionProperty.DATE);// getRevProperty(reposRootPath,
                                                                                                            // revision,
                                                                                                            // SVNRevisionProperty.DATE);

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
            if (propertyValue == null) {// delete
                action = FSHooks.REVPROP_DELETE;
            } else if (oldValue == null) {// add
                action = FSHooks.REVPROP_ADD;
            } else {// modify
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
        try {
            openRepository();
            if (!SVNRepository.isValidRevision(revision)) {
                revision = getYoungestRev(myReposRootDir);
            }
            return checkNodeKind(path, revision);
        } finally {
            closeRepository();
        }
    }

    private SVNNodeKind checkNodeKind(String path, long revision) throws SVNException {
        path = path == null ? "" : path;
        String repositoryPath = super.getRepositoryPath(path);
        String parent = SVNPathUtil.removeTail(repositoryPath);
        String child = SVNPathUtil.tail(repositoryPath);
        child = child.startsWith("/") ? child.substring(child.indexOf("/") + 1) : child;

        FSRevisionNode rootRevNode = FSReader.getRevisionNode(myReposRootDir, parent, revision);
        if (rootRevNode == null) {
            return SVNNodeKind.NONE;
        }
        if ("".equals(child)) {
            return rootRevNode.getType();
        }

        FSRevisionNode childRevNode = FSReader.getChildDirNode(child, rootRevNode, myReposRootDir);
        return childRevNode == null ? SVNNodeKind.NONE : childRevNode.getType();
    }

    public long getFile(String path, long revision, Map properties, OutputStream contents) throws SVNException {
        try {
            openRepository();
            if (!SVNRepository.isValidRevision(revision)) {
                revision = getYoungestRev(myReposRootDir);
            }
            path = path == null ? "" : path;
            String repositoryPath = super.getRepositoryPath(path);
            String parentPath = SVNPathUtil.removeTail(repositoryPath);

            FSRevisionNode parent = FSReader.getRevisionNode(myReposRootDir, parentPath, revision);
            if (parent == null) {
                SVNErrorManager.error("svn: Attempted to open non-existent child node '" + path + "'" + SVNFileUtil.getNativeEOLMarker() + "svn: File not found: revision " + revision + ", path '"
                        + getRepositoryPath(path) + "'");
            }
            String childName = SVNPathUtil.tail(repositoryPath);
            FSRevisionNode childNode = FSReader.getChildDirNode(childName, parent, myReposRootDir);

            if (childNode == null) {
                SVNErrorManager.error("svn: Attempted to open non-existent child node '" + childName + "'");
            } else if (childNode.getType() != SVNNodeKind.FILE) {
                SVNErrorManager.error("svn: Path at '" + path + "' is not a file, but " + childNode.getType());
            }
            FSReader.readDeltaRepresentation(childNode.getTextRepresentation(), contents, myReposRootDir);
            if (properties != null) {
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
/*
    private FSRevisionNode getRevisionNode(File reposRootDir, String repositoryPath, long revision) throws SVNException {
        String absPath = repositoryPath;// super.getRepositoryPath(path);

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
*/
    private SVNDirEntry buildDirEntry(FSRepresentationEntry repEntry, FSRevisionNode revNode, File reposRootDir, boolean includeLogs) throws SVNException {
        FSRevisionNode entryNode = revNode == null ? FSReader.getRevNodeFromID(reposRootDir, repEntry.getId()) : revNode;

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
            path = path == null ? "" : path;
            String repositoryPath = super.getRepositoryPath(path);
            FSRevisionNode parent = FSReader.getRevisionNode(myReposRootDir, repositoryPath, revision);
            if (parent == null) {
                SVNErrorManager.error("svn: Attempted to open non-existent child node '" + path + "'" + SVNFileUtil.getNativeEOLMarker() + "svn: File not found: revision " + revision + ", path '"
                        + getRepositoryPath(path) + "'");
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
            path = path == null ? "" : path;
            String repositoryPath = getRepositoryPath(path);

            FSRevisionNode parent = FSReader.getRevisionNode(myReposRootDir, repositoryPath, revision);
            if (parent == null) {
                SVNErrorManager.error("svn: Attempted to open non-existent child node '" + path + "'" + SVNFileUtil.getNativeEOLMarker() + "svn: File not found: revision " + revision + ", path '"
                        + getRepositoryPath(path) + "'");
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
        try {
            openRepository();

            target = target == null ? "" : target;
            if (!SVNRepository.isValidRevision(revision)) {
                revision = getYoungestRev(myReposRootDir);
            }
            File tmpFile = FSWriter.createUniqueTemporaryFile("report", ".tmp");
            myReporterContext = new ReporterContext(revision, tmpFile, target, recursive, false, editor);
            reporter.report(this);
        } finally {
            closeRepository();
        }

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

    public void setPath(String path, String lockToken, long revision, boolean startEmpty) throws SVNException {
        assertValidRevision(revision);
        try {
            FSWriter.writePathInfo(myReporterContext.getReportFileForWriting(), myReporterContext.getReportTarget(), path, null, lockToken, revision, startEmpty);
        } catch (IOException ioe) {
            SVNErrorManager.error("svn: Can't write path info: " + ioe.getMessage());
        }
    }

    public void deletePath(String path) throws SVNException {
        try {
            FSWriter.writePathInfo(myReporterContext.getReportFileForWriting(), myReporterContext.getReportTarget(), path, null, null, -1, false);
        } catch (IOException ioe) {
            SVNErrorManager.error("svn: Can't write path info: " + ioe.getMessage());
        }
    }

    public void linkPath(SVNURL url, String path, String lockToken, long revision, boolean startEmpty) throws SVNException {
        assertValidRevision(revision);
        String reposRootPath = null;
        String linkPath = url.getPath();
        try{    
            reposRootPath = myReposRootDir.getCanonicalPath();
        }catch(IOException ioe){
            reposRootPath = myReposRootDir.getAbsolutePath();
        }
        
        if(linkPath.indexOf(reposRootPath) == -1){
            SVNErrorManager.error("'" + url + "'" + SVNFileUtil.getNativeEOLMarker() + "is not the same repository as" + SVNFileUtil.getNativeEOLMarker() + "'" + getRepositoryRoot() + "'");
        }
        String reposLinkPath = linkPath.substring(reposRootPath.length());
        try {
            FSWriter.writePathInfo(myReporterContext.getReportFileForWriting(), myReporterContext.getReportTarget(), path, reposLinkPath, lockToken, revision, startEmpty);
        } catch (IOException ioe) {
            SVNErrorManager.error("svn: Can't write path info: " + ioe.getMessage());
        }
    }

    public void finishReport() throws SVNException {
        OutputStream tmpFile = myReporterContext.getReportFileForWriting();
        try {
            tmpFile.write('-');
        } catch (IOException ioe) {
            SVNErrorManager.error("svn: Can't finish report: " + ioe.getMessage());
        }

        /*
         * Read the first pathinfo from the report and verify that it is a
         * top-level set_path entry.
         */
        PathInfo info = null;
        try {
            info = myReporterContext.getFirstPathInfo();
        } catch (IOException ioe) {
            SVNErrorManager.error("svn: Can't read report file: " + ioe.getMessage());
        }
        if (info == null || !info.getPath().equals(myReporterContext.getReportTarget()) || info.getLinkPath() != null || isInvalidRevision(info.getRevision())) {
            SVNErrorManager.error("svn: Invalid report for top level of working copy");
        }
        
        long sourceRevision = info.getRevision();
        
        /* Initialize the lookahead pathinfo. */
        PathInfo lookahead = null;
        try {
            lookahead = myReporterContext.getNextPathInfo();
        } catch (IOException ioe) {
            SVNErrorManager.error("svn: Can't read report file: " + ioe.getMessage());
        }
        
        if(lookahead != null && lookahead.getPath().equals(myReporterContext.getReportTarget())){
            if("".equals(myReporterContext.getReportTarget())){
                SVNErrorManager.error("svn: Two top-level reports with no target");
            }
            /* If the operand of the wc operation is switched or deleted,
             * then info above is just a place-holder, and the only thing we
             * have to do is pass the revision it contains to open_root.
             * The next pathinfo actually describes the target. 
             */
            info = lookahead;
            try{
                myReporterContext.getNextPathInfo();
            }catch(IOException ioe){
                SVNErrorManager.error("svn: Can't read report file: " + ioe.getMessage());
            }
        }
       
        myReporterContext.getEditor().targetRevision(myReporterContext.getTargetRevision());

        String fullTargetPath = SVNPathUtil.append(getRepositoryPath(""), myReporterContext.getReportTarget()); 
        String fullSourcePath = SVNPathUtil.append(getRepositoryPath(""), myReporterContext.getReportTarget());
        FSRepresentationEntry targetEntry = fakeDirEntry(fullTargetPath, myReporterContext.getTargetRevision());
        FSRepresentationEntry sourceEntry = fakeDirEntry(fullSourcePath, sourceRevision);

        /* 
         * If the operand is a locally added file or directory, it won't
         * exist in the source, so accept that. 
         */
        if(isValidRevision(info.getRevision()) && info.getLinkPath() == null && sourceEntry == null){
            fullSourcePath = null;
        }
        
        /* If the anchor is the operand, the source and target must be dirs.
         * Check this before opening the root to avoid modifying the wc. 
         */
        if("".equals(myReporterContext.getReportTarget()) && (sourceEntry == null || sourceEntry.getType() != SVNNodeKind.DIR || targetEntry == null || targetEntry.getType() != SVNNodeKind.DIR)){
            SVNErrorManager.error("svn: Cannot replace a directory from within");
        }
        
        myReporterContext.getEditor().openRoot(sourceRevision);
        
        /* If the anchor is the operand, diff the two directories; otherwise
         * update the operand within the anchor directory. 
         */
        if("".equals(myReporterContext.getReportTarget())){
            diffDirs(sourceRevision, fullSourcePath, fullTargetPath, "", info.isStartEmpty());
        }else{
            //update entry
            updateEntry(sourceRevision, fullSourcePath, sourceEntry, fullTargetPath, targetEntry, myReporterContext.getReportTarget(), info, true);
        }

        myReporterContext.getEditor().closeDir();
        myReporterContext.getEditor().closeEdit();
        
        disposeReporterContext();
    }

    public void abortReport() throws SVNException {
        disposeReporterContext();
    }


    /* Emit edits within directory (with corresponding path editPath) with 
     * the changes from the directory sourceRevision/sourcePath to the
     * directory myReporterContext.getTargetRevision()/targetPath.  
     * sourcePath may be null if the entry does not exist in the source. 
     */
    private void diffDirs(long sourceRevision, String sourcePath, String targetPath, String editPath, boolean startEmpty) throws SVNException {
        /* Compare the property lists.  If we're starting empty, pass a null
         * source path so that we add all the properties. When we support 
         * directory locks, we must pass the lock token here. */
        diffProplists(sourceRevision, startEmpty == true ? null : sourcePath, editPath, targetPath, null, true);
        /* Get the list of entries in each of source and target. */
        Map sourceEntries = null;
        if(sourcePath != null && !startEmpty){
            sourceEntries = FSReader.getDirEntries(FSReader.getRevisionNode(myReposRootDir, sourcePath, sourceRevision), myReposRootDir);
        }
        Map targetEntries = FSReader.getDirEntries(FSReader.getRevisionNode(myReposRootDir, targetPath, myReporterContext.getTargetRevision()), myReposRootDir);
        /* Iterate over the report information for this directory. */
        while(true){
            Object[] nextInfo = fetchPathInfo(editPath);
            String entryName = (String)nextInfo[0];
            if(entryName == null){
                break;
            }
            PathInfo pathInfo = (PathInfo)nextInfo[1];
            if(pathInfo != null && isInvalidRevision(pathInfo.getRevision())){
                /* We want to perform deletes before non-replacement adds,
                 * for graceful handling of case-only renames on
                 * case-insensitive client filesystems.  So, if the report
                 * item is a delete, remove the entry from the source hash,
                 * but don't update the entry yet. 
                 */
                if(sourceEntries != null){
                    sourceEntries.put(entryName, null);
                }
                continue;
            }
            
            String entryEditPath = SVNPathUtil.append(editPath, entryName);
            String entryTargetPath = SVNPathUtil.append(targetPath, entryName);
            FSRepresentationEntry targetEntry = (FSRepresentationEntry)targetEntries.get(entryName);
            String entrySourcePath = sourcePath != null ? SVNPathUtil.append(sourcePath, entryName) : null;
            FSRepresentationEntry sourceEntry = sourceEntries != null ? (FSRepresentationEntry)sourceEntries.get(entryName) : null;
            updateEntry(sourceRevision, entrySourcePath, sourceEntry, entryTargetPath, targetEntry, entryEditPath, pathInfo, myReporterContext.isRecursive());
            /* Don't revisit this entryName in the target or source entries. */
            targetEntries.put(entryName, null);
            if(sourceEntries != null){
                sourceEntries.put(entryName, null);
            }
        }
        
        /* Remove any deleted entries. Do this before processing the
         * target, for graceful handling of case-only renames. 
         */
        if(sourceEntries != null){
            Object[] names = sourceEntries.keySet().toArray();
            for(int i = 0; i < names.length; i++){
                FSRepresentationEntry srcEntry = (FSRepresentationEntry)sourceEntries.get(names[i]);
                if(targetEntries.get(srcEntry.getName()) == null){
                    /* There is no corresponding target entry, so delete. */
                    String entryEditPath = SVNPathUtil.append(editPath, srcEntry.getName());
                    if(myReporterContext.isRecursive() || srcEntry.getType() != SVNNodeKind.DIR){
                        myReporterContext.getEditor().deleteEntry(entryEditPath, -1);
                    }
                }
            }
        }
        /* Loop over the dirents in the target. */
        Object[] names = targetEntries.keySet().toArray();
        for(int i = 0; i < names.length; i++){
            FSRepresentationEntry tgtEntry = (FSRepresentationEntry)targetEntries.get(names[i]);
            /* Compose the report, editor, and target paths for this entry. */
            String entryEditPath = SVNPathUtil.append(editPath, tgtEntry.getName());
            String entryTargetPath = SVNPathUtil.append(targetPath, tgtEntry.getName());
            /* Look for an entry with the same name in the source dirents. */
            FSRepresentationEntry srcEntry = sourceEntries != null ? (FSRepresentationEntry)sourceEntries.get(tgtEntry.getName()) : null;
            String entrySourcePath = srcEntry != null ? SVNPathUtil.append(sourcePath, tgtEntry.getName()) : null;
            updateEntry(sourceRevision, entrySourcePath, srcEntry, entryTargetPath, tgtEntry, entryEditPath, null, myReporterContext.isRecursive());
        }        
    }

    /* Emits a series of editing operations to transform a source entry to
     * a target entry.
     * 
     * sourceRevision and sourcePath specify the source entry.  sourceEntry 
     * contains the already-looked-up information about the node-revision 
     * existing at that location. sourcePath and sourceEntry may be null if 
     * the entry does not exist in the source.  spurcePath may be non-null 
     * and sourceEntry may be null if the caller expects pathInfo to modify 
     * the source to an existing location.
     *
     * targetPath specify the target entry.  targetEntry contains
     * the already-looked-up information about the node-revision existing
     * at that location. targetPath and targetEntry may be null if the entry 
     * does not exist in the target.
     *
     * editPath should be passed to the editor calls as the pathname. 
     * editPath is the anchor-relative working copy pathname, which may 
     * differ from the source and target pathnames if the report contains a 
     * link_path.
     *
     * pathInfo contains the report information for this working copy path, 
     * or null if there is none.  This method will internally modify the
     * source and target entries as appropriate based on the report
     * information.
     * 
     * If recursive is false, avoids operating on directories.  (Normally
     * recursive is simply taken from myReporterContext.isRecursive(), but 
     * finishReport() needs to force us to recurse into the target even if 
     * that flag is not set.) 
     */
    private void updateEntry(long sourceRevision, String sourcePath, FSRepresentationEntry sourceEntry, String targetPath, FSRepresentationEntry targetEntry, String editPath, PathInfo pathInfo, boolean recursive) throws SVNException {
        /*Follow link path in the target. */
        if(pathInfo != null && pathInfo.getLinkPath() != null){
            targetPath = pathInfo.getLinkPath();
            targetEntry = fakeDirEntry(targetPath, myReporterContext.getTargetRevision());
        }
        if(pathInfo != null && isInvalidRevision(pathInfo.getRevision())){
            /* Delete this entry in the source. */
            sourcePath = null;
            sourceEntry = null;
        }else if(pathInfo != null && sourcePath != null){
            /* Follow the rev and possibly path in this entry. */
            sourcePath = pathInfo.getLinkPath() != null ? pathInfo.getLinkPath() : sourcePath;
            sourceRevision = pathInfo.getRevision();
            sourceEntry = fakeDirEntry(sourcePath, sourceRevision);
        }
        /* Don't let the report carry us somewhere nonexistent. */
        if(sourcePath != null && sourceEntry == null){
            SVNErrorManager.error("svn: Working copy path '" + editPath + "' does not exist in repository");
        }
        if(!recursive && ((sourceEntry != null && sourceEntry.getType() == SVNNodeKind.DIR) || (targetEntry != null && targetEntry.getType() == SVNNodeKind.DIR))){
            skipPathInfo(editPath);
            return;
        }
        /* If the source and target both exist and are of the same kind,
         * then find out whether they're related.  If they're exactly the
         * same, then we don't have to do anything (unless the report has
         * changes to the source).  If we're ignoring ancestry, then any two
         * nodes of the same type are related enough for us. 
         */
        boolean related = false;
        if(sourceEntry != null && targetEntry != null && sourceEntry.getType() == targetEntry.getType()){
            int distance = FSID.compareIds(sourceEntry.getId(), targetEntry.getId());
            if(distance == 0 && !PathInfo.isRelevant(myReporterContext.getCurrentPathInfo(), editPath) && (pathInfo != null || (!pathInfo.isStartEmpty() && pathInfo.getLinkPath() == null))){
                return;
            }else if(distance != -1 || myReporterContext.isIgnoreAncestry()){
                related = true;
            }
        }
        /* If there's a source and it's not related to the target, nuke it. */
        if(sourceEntry != null && !related){
            myReporterContext.getEditor().deleteEntry(editPath, -1);
            sourcePath = null;
        }
        /* If there's no target, we have nothing more to do. */
        if(targetEntry == null){
            skipPathInfo(editPath);
            return;
        }
        if(targetEntry.getType() == SVNNodeKind.DIR){
            if(related){
                myReporterContext.getEditor().openDir(editPath, sourceRevision);
            }else{
                myReporterContext.getEditor().addDir(editPath, null, -1);
            }
            diffDirs(sourceRevision, sourcePath, targetPath, editPath, pathInfo != null ? pathInfo.isStartEmpty() : false);
            myReporterContext.getEditor().closeDir();
        }else{
            
        }
    }
    
    private FSRepresentationEntry fakeDirEntry(String reposPath, long revision) throws SVNException {
        FSRevisionNode node = FSReader.getRevisionNode(myReposRootDir, reposPath, revision);
        FSRepresentationEntry dirEntry = null;
        if(node != null){
            dirEntry = new FSRepresentationEntry(node.getRevNodeID(), node.getType(), SVNPathUtil.tail(node.getCreatedPath()));
        }
        return dirEntry;
    }

    /* Skip all path info entries relevant to prefix.  Called when the
     * editor drive skips a directory. 
     */
    private void skipPathInfo(String prefix) throws SVNException {
        while(PathInfo.isRelevant(myReporterContext.getCurrentPathInfo(), prefix)){
            try{
                myReporterContext.getNextPathInfo();
            }catch(IOException ioe){
                SVNErrorManager.error("svn: Can't read report file: " + ioe.getMessage());
            }
        }

    }
    
    /* Fetch the next pathinfo from the report file for a descendent of
     * prefix.  If the next pathinfo is for an immediate child of prefix,
     * sets Object[0] to the path component of the report information and
     * Object[1] to the path information for that entry.  If the next pathinfo
     * is for a grandchild or other more remote descendent of prefix, sets
     * Object[0] to the immediate child corresponding to that descendent and
     * sets Object[1] to null.  If the next pathinfo is not for a descendent of
     * prefix, or if we reach the end of the report, sets both Object[0] and 
     * Object[1] to null.
     *
     * At all times, myReporterContext.getCurrentPathInfo() is presumed to be 
     * the next pathinfo not yet returned as an immediate child, or null if we 
     * have reached the end of the report. 
     */
    private Object[] fetchPathInfo(String prefix) throws SVNException{
        Object[] result = new Object[2];
        PathInfo pathInfo = myReporterContext.getCurrentPathInfo(); 
        if(!PathInfo.isRelevant(pathInfo, prefix)){
            /* No more entries relevant to prefix. */
            result[0] = null;
            result[1] = null;
        }else{
            /* Take a look at the prefix-relative part of the path. */
            String relPath = "".equals(prefix) ? pathInfo.getPath() : pathInfo.getPath().substring(prefix.length() + 1);
            if(relPath.indexOf('/') != -1){
                /* Return the immediate child part; do not advance. */
                result[0] = relPath.substring(0, relPath.indexOf('/'));
                result[1] = null;
            }else{
                /* This is an immediate child; return it and advance. */
                result[0] = relPath;
                result[1] = pathInfo;
                try{
                    myReporterContext.getNextPathInfo();
                }catch(IOException ioe){
                    SVNErrorManager.error("svn: Can't read report file: " + ioe.getMessage());
                }
            }
        }
        return result;
    }
    
    private void diffProplists(long sourceRevision, String sourcePath, String editPath, String targetPath, String lockToken, boolean isDir) throws SVNException {
        FSRevisionNode targetNode = FSReader.getRevisionNode(myReposRootDir, targetPath, myReporterContext.getTargetRevision());
        if(targetNode == null){
            SVNErrorManager.error("svn: File not found: revision " + myReporterContext.getTargetRevision() + ", path '" + targetPath + "'");
        }
        long createdRevision = targetNode != null ? targetNode.getRevNodeID().getRevision() : -1;  
        //why are we checking the created revision fetched from the rev-file? may the file be malformed - is this the reason...
        if(isValidRevision(createdRevision)){
            Map entryProps = FSRepositoryUtil.getMetaProps(myReposRootDir, createdRevision, this);
            /* Transmit the committed-rev. */
            changeProperty(editPath, SVNProperty.COMMITTED_REVISION, (String)entryProps.get(SVNProperty.COMMITTED_REVISION), isDir);
            /* Transmit the committed-date. */
            String committedDate = (String)entryProps.get(SVNProperty.COMMITTED_DATE);
            if(committedDate != null || sourcePath != null){
                changeProperty(editPath, SVNProperty.COMMITTED_DATE, committedDate, isDir);
            }
            /* Transmit the last-author. */
            String lastAuthor = (String)entryProps.get(SVNProperty.LAST_AUTHOR);
            if(lastAuthor != null || sourcePath != null){
                changeProperty(editPath, SVNProperty.LAST_AUTHOR, lastAuthor, isDir);
            }
            /* Transmit the UUID. */
            String uuid = (String)entryProps.get(SVNProperty.UUID);
            if(uuid != null || sourcePath != null){
                changeProperty(editPath, SVNProperty.UUID, uuid, isDir);
            }
        }
        /* Update lock properties. */
        if(lockToken != null){
            SVNLock lock = FSReader.getLock(targetPath, false, null, myReposRootDir);
            /* Delete a defunct lock. */
            if(lock == null || !lockToken.equals(lock.getID())){
                changeProperty(editPath, SVNProperty.LOCK_TOKEN, null, isDir);
            }
        }
        Map sourceProps = null;
        if(sourcePath != null){
            boolean propsChanged = FSRepositoryUtil.arePropsChanged(sourcePath, sourceRevision, targetPath, myReporterContext.getTargetRevision(), myReposRootDir);
            if(!propsChanged){
                return;
            }
            /* If so, go ahead and get the source path's properties. */
            FSRevisionNode sourceNode = FSReader.getRevisionNode(myReposRootDir, sourcePath, sourceRevision);
            sourceProps = FSReader.getProperties(sourceNode, myReposRootDir);
        }else{
            sourceProps = new HashMap();
        }
        /* Get the target path's properties */
        Map targetProps = FSReader.getProperties(targetNode, myReposRootDir);
        /* Now transmit the differences. */
        Map propsDiffs = FSRepositoryUtil.getPropsDiffs(sourceProps, targetProps);
        Object[] names = propsDiffs.keySet().toArray();
        for(int i = 0; i < names.length; i++){
            String propName = (String)names[i];
            changeProperty(editPath, propName, (String)propsDiffs.get(propName), isDir);
        }
    }
    
    private void changeProperty(String path, String name, String value, boolean isDir) throws SVNException{
        if(isDir){
            myReporterContext.getEditor().changeDirProperty(name, value);
        }else{
            myReporterContext.getEditor().changeFileProperty(path, name, value);
        }
    }
    
    private void disposeReporterContext(){
        if(myReporterContext != null){
            myReporterContext.disposeContext();
            myReporterContext = null;
        }
    }
    
    public static boolean isInvalidRevision(long revision) {
        return isInvalidRevision(revision);
    }    
    
    public static boolean isValidRevision(long revision) {
        return isValidRevision(revision);
    }
    
    private class ReporterContext {

        private File myReportFile;
        private String myTarget;
        private OutputStream myReportOS;
        private InputStream myReportIS;
        private ISVNEditor myEditor;
        private long myTargetRevision;
        private boolean isRecursive;
        private PathInfo myCurrentPathInfo;
        private boolean ignoreAncestry;
        
        public ReporterContext(long revision, File tmpFile, String target, boolean recursive, boolean ignoreAncestry, ISVNEditor editor) {
            myTargetRevision = revision;
            myReportFile = tmpFile;
            myTarget = target;
            myEditor = editor;
            isRecursive = recursive;
            this.ignoreAncestry = ignoreAncestry;
        }

        public OutputStream getReportFileForWriting() throws SVNException {
            if (myReportOS == null) {
                myReportOS = SVNFileUtil.openFileForWriting(myReportFile);
            }
            return myReportOS;
        }

/*        public InputStream getReportFileForReading() throws SVNException {
            if (myReportIS == null) {
                myReportIS = SVNFileUtil.openFileForReading(myReportFile);
            }
            return myReportIS;
        }
*/
        public boolean isIgnoreAncestry(){
            return ignoreAncestry;
        }
        
        public String getReportTarget() {
            return myTarget;
        }

        public void disposeContext() {
            SVNFileUtil.closeFile(myReportOS);
            SVNFileUtil.closeFile(myReportIS);
        }

        public ISVNEditor getEditor() {
            return myEditor;
        }

        public boolean isRecursive() {
            return isRecursive;
        }

        public long getTargetRevision() {
            return myTargetRevision;
        }
        
        public PathInfo getFirstPathInfo() throws IOException, SVNException {
            SVNFileUtil.closeFile(myReportIS);
            myReportIS = SVNFileUtil.openFileForReading(myReportFile);
            myCurrentPathInfo = FSReader.readPathInfoFromReportFile(myReportIS);
            return myCurrentPathInfo;
        }
        
        public PathInfo getNextPathInfo() throws IOException {
            myCurrentPathInfo = FSReader.readPathInfoFromReportFile(myReportIS);
            return myCurrentPathInfo;
        }

        public PathInfo getCurrentPathInfo() {
            return myCurrentPathInfo;
        }
        
/*        public FSRevisionNode getTargetRoot(File reposRootDir) throws SVNException {
            return FSReader.getRootRevNode(reposRootDir, myTargetRevision);
        }*/
    }
}
