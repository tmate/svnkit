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
import org.tmatesoft.svn.core.io.diff.SVNRAFileData;
import org.tmatesoft.svn.core.io.diff.SVNSequenceDeltaGenerator;
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
            path = path == null ? "" : path;
            String repositoryPath = getRepositoryPath(path);
            return checkNodeKind(repositoryPath, null, revision);
        } finally {
            closeRepository();
        }
    }

    private SVNNodeKind checkNodeKind(String repositoryPath, FSRevisionNode root, long revision) throws SVNException {
        FSRevisionNode revNode = FSReader.getRevisionNode(myReposRootDir, repositoryPath, root, revision);
        return revNode == null ? SVNNodeKind.NONE : revNode.getType();
    }

    public long getFile(String path, long revision, Map properties, OutputStream contents) throws SVNException {
        try {
            openRepository();
            if (!SVNRepository.isValidRevision(revision)) {
                revision = getYoungestRev(myReposRootDir);
            }
            path = path == null ? "" : path;
            String repositoryPath = getRepositoryPath(path);
            FSRevisionNode revNode = FSReader.getRevisionNode(myReposRootDir, repositoryPath, null, revision);
            if (revNode == null) {
                SVNErrorManager.error("svn: Attempted to open non-existent child node '" + path + "'");
            } else if (revNode.getType() != SVNNodeKind.FILE) {
                SVNErrorManager.error("svn: Path at '" + path + "' is not a file, but " + revNode.getType());
            }
            getFileContents(revNode, contents);
            if (properties != null) {
                properties.putAll(collectProperties(revNode, myReposRootDir));
            }
            return revision;
        } finally {
            closeRepository();
        }
    }
    
    private void getFileContents(FSRevisionNode revNode, OutputStream contents) throws SVNException {
        if(revNode == null){
            return;
        }
        if (revNode.getType() != SVNNodeKind.FILE) {
            SVNErrorManager.error("svn: Attempted to get textual contents of a *non*-file node");
        }
        FSReader.readDeltaRepresentation(revNode.getTextRepresentation(), contents, myReposRootDir);
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
            	//commented because of svn update made conflict
           //     child = FSReader.getChildDirNode(nextPathComponent, parent, reposRootDir);
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
            String repositoryPath = getRepositoryPath(path);
            FSRevisionNode parent = FSReader.getRevisionNode(myReposRootDir, repositoryPath, null, revision);
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

            FSRevisionNode parent = FSReader.getRevisionNode(myReposRootDir, repositoryPath, null, revision);
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
    	try{
    		openRepository();
    		if (!SVNRepository.isValidRevision(pegRevision)){
    			//Bad situation: how to react at bad revisoin????????
    			return -1;    		
    		}
    		//Get absolute path of file
    		path = path == null ? "" : path;
    		String repositoryPath = super.getRepositoryPath(path);
            String parentPath = SVNPathUtil.removeTail(repositoryPath);
            
    	}finally{
    		closeRepository();
    	}
        return 0;
    }

    public void diff(SVNURL url, long revision, String target, boolean ignoreAncestry, boolean recursive, ISVNReporterBaton reporter, ISVNEditor editor) throws SVNException {
        try {
            openRepository();
            File tmpFile = FSWriter.createUniqueTemporaryFile("report", ".tmp");
            makeReporterContext(revision, tmpFile, target, url, recursive, ignoreAncestry, true, editor);
            reporter.report(this);
        } finally {
            closeRepository();
        }
    }

    public void diff(SVNURL url, long targetRevision, long revision, String target, boolean ignoreAncestry, boolean recursive, ISVNReporterBaton reporter, ISVNEditor editor) throws SVNException {
        try {
            openRepository();
            File tmpFile = FSWriter.createUniqueTemporaryFile("report", ".tmp");
            makeReporterContext(targetRevision, tmpFile, target, url, recursive, ignoreAncestry, true, editor);
            reporter.report(this);
        } finally {
            closeRepository();
        }
    }

    public void update(long revision, String target, boolean recursive, ISVNReporterBaton reporter, ISVNEditor editor) throws SVNException {
        try {
            openRepository();
            File tmpFile = FSWriter.createUniqueTemporaryFile("report", ".tmp");
            makeReporterContext(revision, tmpFile, target, null, recursive, false, true, editor);
            reporter.report(this);
        } finally {
            closeRepository();
        }
    }

    public void status(long revision, String target, boolean recursive, ISVNReporterBaton reporter, ISVNEditor editor) throws SVNException {
        try {
            openRepository();

          //!!!!commented because of bad situation
          //  File tmpFile = FSWriter.createUniqueTemporaryFile("report", ".tmp");
          //  myReporterContext = new ReporterContext(revision, tmpFile, target, recursive, editor);

            File tmpFile = FSWriter.createUniqueTemporaryFile("report", ".tmp");
            makeReporterContext(revision, tmpFile, target, null, recursive, false, false, editor);

            reporter.report(this);
        } finally {
            closeRepository();
        }
    }
    
    private void makeReporterContext(long targetRevision, File reportFile, String target, SVNURL switchURL, boolean recursive, boolean ignoreAncestry, boolean textDeltas, ISVNEditor editor) throws SVNException{
        target = target == null ? "" : target;
        if (!isValidRevision(targetRevision)) {
            targetRevision = getYoungestRev(myReposRootDir);
        }
        /* If switchURL was provided, validate it and convert it into a
         * regular filesystem path. 
         */
        String switchPath = null;
        if(switchURL != null){
            /* Sanity check:  the switchURL better be in the same repository 
             * as the original session url! 
             */
            SVNURL reposRootURL = getRepositoryRoot();
            if(switchURL.toString().indexOf(reposRootURL.toString()) == -1){
                SVNErrorManager.error("'" + switchURL + "'" + SVNFileUtil.getNativeEOLMarker() + "is not the same repository as" + SVNFileUtil.getNativeEOLMarker() + "'" + getRepositoryRoot() + "'");
            }
            switchPath = switchURL.toString().substring(reposRootURL.toString().length());
        }
        String anchor = getRepositoryPath("");
        String fullTargetPath = switchPath != null ? switchPath : "/".equals(anchor) ? anchor + target: SVNPathUtil.append(anchor, target);
        myReporterContext = new ReporterContext(targetRevision, reportFile, target, fullTargetPath, switchURL == null ? false : true, recursive, ignoreAncestry, textDeltas, editor);
    }
    
    public void update(SVNURL url, long revision, String target, boolean recursive, ISVNReporterBaton reporter, ISVNEditor editor) throws SVNException {
        try {
            openRepository();
            File tmpFile = FSWriter.createUniqueTemporaryFile("report", ".tmp");
            makeReporterContext(revision, tmpFile, target, url, recursive, true, true, editor);
            reporter.report(this);
        } finally {
            closeRepository();
        }
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
            FSWriter.writePathInfoToReportFile(myReporterContext.getReportFileForWriting(), myReporterContext.getReportTarget(), path, null, lockToken, revision, startEmpty);
        } catch (IOException ioe) {
            SVNErrorManager.error("svn: Can't write path info: " + ioe.getMessage());
        }
    }

    public void deletePath(String path) throws SVNException {
        try {
            FSWriter.writePathInfoToReportFile(myReporterContext.getReportFileForWriting(), myReporterContext.getReportTarget(), path, null, null, -1, false);
        } catch (IOException ioe) {
            SVNErrorManager.error("svn: Can't write path info: " + ioe.getMessage());
        }
    }

    public void linkPath(SVNURL url, String path, String lockToken, long revision, boolean startEmpty) throws SVNException {
        assertValidRevision(revision);
        SVNURL reposRootURL = getRepositoryRoot();
        if(url.toString().indexOf(reposRootURL.toString()) == -1){
            SVNErrorManager.error("'" + url + "'" + SVNFileUtil.getNativeEOLMarker() + "is not the same repository as" + SVNFileUtil.getNativeEOLMarker() + "'" + reposRootURL + "'");
        }
        String reposLinkPath = url.toString().substring(reposRootURL.toString().length());
        try {
            FSWriter.writePathInfoToReportFile(myReporterContext.getReportFileForWriting(), myReporterContext.getReportTarget(), path, reposLinkPath, lockToken, revision, startEmpty);
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
        SVNFileUtil.closeFile(myReporterContext.getReportFileForWriting());
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

        String fullTargetPath = myReporterContext.getReportTargetPath(); 
        String anchor = getRepositoryPath("");
        String fullSourcePath = "/".equals(anchor) ? anchor + myReporterContext.getReportTarget(): SVNPathUtil.append(getRepositoryPath(""), myReporterContext.getReportTarget());
        FSRepresentationEntry targetEntry = fakeDirEntry(fullTargetPath, myReporterContext.getTargetRoot(myReposRootDir), myReporterContext.getTargetRevision());
        FSRepresentationEntry sourceEntry = fakeDirEntry(fullSourcePath, null, sourceRevision);
        
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
            sourceEntries = FSReader.getDirEntries(FSReader.getRevisionNode(myReposRootDir, sourcePath, null, sourceRevision), myReposRootDir);
        }
        Map targetEntries = FSReader.getDirEntries(FSReader.getRevisionNode(myReposRootDir, targetPath, myReporterContext.getTargetRoot(myReposRootDir), myReporterContext.getTargetRevision()), myReposRootDir);
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

    /* Makes the appropriate edits to change file (represented by 
     * editPath) contents and properties from those in 
     * sourceRevision/sourcePath to those in 
     * myReporterContext.getTargetRevision()/targetPath,
     * possibly using lockToken to determine if the client's lock on 
     * the file is defunct. 
     */
    private void diffFiles(long sourceRevision, String sourcePath, String targetPath, String editPath, String lockToken) throws SVNException {
        /* Compare the files' property lists.  */
        diffProplists(sourceRevision, sourcePath, editPath, targetPath, lockToken, false);
        String sourceHexDigest = null;
        if(sourcePath != null){
            FSRevisionNode sourceRoot = FSReader.getRootRevNode(myReposRootDir, sourceRevision);
            /* Is this delta calculation worth our time?  If we are ignoring
             * ancestry, then our editor implementor isn't concerned by the
             * theoretical differences between "has contents which have not
             * changed with respect to" and "has the same actual contents
             * as".  We'll do everything we can to avoid transmitting even
             * an empty text-delta in that case.  
             */
            boolean changed = false;
            if(myReporterContext.isIgnoreAncestry()){
                changed = checkFilesDifferent(sourceRoot, sourcePath, myReporterContext.getTargetRoot(myReposRootDir), targetPath); 
            }else{
                changed = areFileContentsChanged(sourceRoot, sourcePath, myReporterContext.getTargetRoot(myReposRootDir), targetPath);
            }
            if(!changed){
                return;
            }
            FSRevisionNode sourceNode = FSReader.getRevisionNode(myReposRootDir, sourcePath, sourceRoot, -1);
            sourceHexDigest = getFileChecksum(sourceNode);//sourceNode.getTextRepresentation().getHexDigest();
        }
        /* Sends the delta stream if desired, or just calls 
         * the editor's textDeltaEnd() if not. 
         */
        myReporterContext.getEditor().applyTextDelta(editPath, sourceHexDigest);
        if(myReporterContext.isSendTextDeltas()){
            File srcFile = FSWriter.createUniqueTemporaryFile("source", ".tmp");
            File tgtFile = FSWriter.createUniqueTemporaryFile("target", ".tmp");
            OutputStream file1OS = SVNFileUtil.openFileForWriting(srcFile);
            OutputStream file2OS = SVNFileUtil.openFileForWriting(tgtFile);
            FSRevisionNode sourceNode = FSReader.getRevisionNode(myReposRootDir, sourcePath, null, sourceRevision);
            FSRevisionNode targetNode = FSReader.getRevisionNode(myReposRootDir, targetPath, myReporterContext.getTargetRoot(myReposRootDir), -1);
            getFileContents(sourceNode, file1OS);
            getFileContents(targetNode, file2OS);
            SVNFileUtil.closeFile(file1OS);
            SVNFileUtil.closeFile(file2OS);
            SVNRAFileData srcRAFile = new SVNRAFileData(srcFile, true);
            SVNRAFileData tgtRAFile = new SVNRAFileData(tgtFile, true);
            SVNSequenceDeltaGenerator generator = new SVNSequenceDeltaGenerator(FSWriter.getTmpDir());
            generator.generateDiffWindow(editPath, myReporterContext.getEditor(), tgtRAFile, srcRAFile);
        }else{
            myReporterContext.getEditor().textDeltaEnd(editPath);
        }
    }    
    
    /*
     * Returns true - if files are really different, their contents are
     * different. Otherwise return false - they are the same file 
     */
    private boolean checkFilesDifferent(FSRevisionNode root1, String path1, FSRevisionNode root2, String path2) throws SVNException {
        boolean changed = areFileContentsChanged(root1, path1, root2, path2);
        /* If the filesystem claims the things haven't changed, then 
         * they haven't changed. 
         */
        if(!changed){
            return false;
        }
        /* From this point on, assume things haven't changed. */
        /* So, things have changed.  But we need to know if the two sets 
         * of file contents are actually different. If they have differing
         * sizes, then we know they differ. 
         */
        FSRevisionNode revNode1 = FSReader.getRevisionNode(myReposRootDir, path1, root1, -1);
        FSRevisionNode revNode2 = FSReader.getRevisionNode(myReposRootDir, path2, root2, -1);
        if(getFileLength(revNode1) != getFileLength(revNode2)){
            return true;
        }
        /* Same sizes? Well, if their checksums differ, we know 
         * they differ. 
         */
        if(!getFileChecksum(revNode1).equals(getFileChecksum(revNode2))){
            return true;
        }
        /* Same sizes, same checksums. Chances are really good that 
         * files don't differ, but to be absolute sure, we need to 
         * compare bytes. 
         */
//        OutputStream file1 = new ByteArrayOutputStream();
//        OutputStream file2 = new ByteArrayOutputStream();
        File file1 = FSWriter.createUniqueTemporaryFile("source", ".tmp");
        File file2 = FSWriter.createUniqueTemporaryFile("target", ".tmp");
        OutputStream file1OS = SVNFileUtil.openFileForWriting(file1);
        OutputStream file2OS = SVNFileUtil.openFileForWriting(file2);
        getFileContents(revNode1, file1OS);
        getFileContents(revNode2, file2OS);
        SVNFileUtil.closeFile(file1OS);
        SVNFileUtil.closeFile(file2OS);
        
//        byte[] buf1 = file1.toByteArray();
//        byte[] buf2 = file2.toByteArray();
//        if(buf1.length != buf2.length){
//            return true;
//        }
        InputStream file1IS = SVNFileUtil.openFileForReading(file1);
        InputStream file2IS = SVNFileUtil.openFileForReading(file2);
        int r1 = -1;
        int r2 = -1;
        while(true){
            try{
                r1 = file1IS.read();
                r2 = file2IS.read();
            }catch(IOException ioe){
                SVNFileUtil.closeFile(file1IS);
                SVNFileUtil.closeFile(file2IS);
                SVNErrorManager.error("svn: Can't read temporary file: "+ioe.getMessage());
            }
            if(r1 != r2){
                SVNFileUtil.closeFile(file1IS);
                SVNFileUtil.closeFile(file2IS);
                return true;
            }
            if(r1 == -1){
                break;
            }
        }
/*        for(int i = 0; i < buf1.length; i++){
            if(buf1[i] != buf2[i]){
                return true;
            }
        }*/
        return false;
    }
    
    private long getFileLength(FSRevisionNode revNode) throws SVNException {
        if(revNode.getType() != SVNNodeKind.FILE){
            SVNErrorManager.error("svn: Attempted to get length of a *non*-file node");
        }
        return revNode.getTextRepresentation() != null ? revNode.getTextRepresentation().getExpandedSize() : 0;
    }

    private String getFileChecksum(FSRevisionNode revNode) throws SVNException {
        if(revNode.getType() != SVNNodeKind.FILE){
            SVNErrorManager.error("svn: Attempted to get checksum of a *non*-file node");
        }
        return revNode.getTextRepresentation() != null ? revNode.getTextRepresentation().getHexDigest() : "";
    }
    
    /*
     * Returns true if nodes' representations are different.
     */
    private boolean areFileContentsChanged(FSRevisionNode root1, String path1, FSRevisionNode root2, String path2) throws SVNException {
        /* Is there a need to check here that both roots 
         *  Check that both paths are files. 
         */
        if(checkNodeKind(path1, root1, -1) != SVNNodeKind.FILE){
            SVNErrorManager.error("svn: '" + path1 + "' is not a file");
        }
        if(checkNodeKind(path2, root2, -1) != SVNNodeKind.FILE){
            SVNErrorManager.error("svn: '" + path2 + "' is not a file");
        }
        FSRevisionNode revNode1 = FSReader.getRevisionNode(myReposRootDir, path1, root1, -1);
        FSRevisionNode revNode2 = FSReader.getRevisionNode(myReposRootDir, path2, root2, -1);
        return !FSRepositoryUtil.areContentsEqual(revNode1, revNode2);
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
        /* For non-switch operations, follow link path in the target. */
        if(pathInfo != null && pathInfo.getLinkPath() != null && !myReporterContext.isSwitch()){
            targetPath = pathInfo.getLinkPath();
            targetEntry = fakeDirEntry(targetPath, myReporterContext.getTargetRoot(myReposRootDir), myReporterContext.getTargetRevision());
        }
        if(pathInfo != null && isInvalidRevision(pathInfo.getRevision())){
            /* Delete this entry in the source. */
            sourcePath = null;
            sourceEntry = null;
        }else if(pathInfo != null && sourcePath != null){
            /* Follow the rev and possibly path in this entry. */
            sourcePath = pathInfo.getLinkPath() != null ? pathInfo.getLinkPath() : sourcePath;
            sourceRevision = pathInfo.getRevision();
            sourceEntry = fakeDirEntry(sourcePath, null, sourceRevision);
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
            if(related){
                myReporterContext.getEditor().openFile(editPath, sourceRevision);
            }else{
                myReporterContext.getEditor().addFile(editPath, null, -1);
            }
            diffFiles(sourceRevision, sourcePath, targetPath, editPath, pathInfo != null ? pathInfo.getLockToken() : null);
            FSRevisionNode targetNode = FSReader.getRevisionNode(myReposRootDir, targetPath, myReporterContext.getTargetRoot(myReposRootDir), -1);
            String targetHexDigest = getFileChecksum(targetNode);
            myReporterContext.getEditor().closeFile(editPath, targetHexDigest);
        }
    }
    
    private FSRepresentationEntry fakeDirEntry(String reposPath, FSRevisionNode root, long revision) throws SVNException {
        FSRevisionNode node = FSReader.getRevisionNode(myReposRootDir, reposPath, root, revision);
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

    /* Generate the appropriate property editing calls to turn the
     * properties of sourceRevision/sourcePath into those of 
     * myReporterContext.getTargetRevision()/targetPath. If 
     * sourcePath is null, this is an add, so assume the target 
     * starts with no properties. 
     */
    private void diffProplists(long sourceRevision, String sourcePath, String editPath, String targetPath, String lockToken, boolean isDir) throws SVNException {
        FSRevisionNode targetNode = FSReader.getRevisionNode(myReposRootDir, targetPath, myReporterContext.getTargetRoot(myReposRootDir), myReporterContext.getTargetRevision());
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
            FSRevisionNode sourceNode = FSReader.getRevisionNode(myReposRootDir, sourcePath, null, sourceRevision);
            if(sourcePath == null){
                SVNErrorManager.error("svn: File not found: revision " + sourceRevision + ", path '"
                        + sourcePath + "'");
            }
            boolean propsChanged = !FSRepositoryUtil.arePropertiesEqual(sourceNode, targetNode);
            if(!propsChanged){
                return;
            }
            /* If so, go ahead and get the source path's properties. */
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
        return SVNRepository.isInvalidRevision(revision);
    }    
    
    public static boolean isValidRevision(long revision) {
        return SVNRepository.isValidRevision(revision);
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
        private boolean sendTextDeltas;
        private String myTargetPath;
        private boolean isSwitch;
        private FSRevisionNode myTargetRoot;
        
        public ReporterContext(long revision, File tmpFile, String target, String targetPath, boolean isSwitch, boolean recursive, boolean ignoreAncestry, boolean textDeltas, ISVNEditor editor) {
            myTargetRevision = revision;
            myReportFile = tmpFile;
            myTarget = target;
            myEditor = editor;
            isRecursive = recursive;
            this.ignoreAncestry = ignoreAncestry;
            sendTextDeltas = textDeltas;
            myTargetPath = targetPath;
            this.isSwitch = isSwitch;
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

        public boolean isSwitch(){
            return isSwitch;
        }

        public boolean isSendTextDeltas(){
            return sendTextDeltas;
        }
        
        public String getReportTarget() {
            return myTarget;
        }

        public String getReportTargetPath() {
            return myTargetPath;
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
        
        public FSRevisionNode getTargetRoot(File reposRootDir) throws SVNException {
            if(myTargetRoot == null){
                myTargetRoot = FSReader.getRootRevNode(reposRootDir, myTargetRevision); 
            }
            return myTargetRoot; 
        }
    }
}
