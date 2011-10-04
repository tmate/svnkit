/*
 * ====================================================================
 * Copyright (c) 2004-2006 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://tmate.org/svn/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.wc;

import java.io.File;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.tmatesoft.svn.core.ISVNDirEntryHandler;
import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.diff.SVNDeltaProcessor;
import org.tmatesoft.svn.core.io.diff.SVNDiffWindow;
import org.tmatesoft.svn.core.wc.ISVNEventHandler;
import org.tmatesoft.svn.core.wc.SVNEvent;
import org.tmatesoft.svn.core.wc.SVNEventAction;
import org.tmatesoft.svn.core.wc.SVNStatusType;
import org.tmatesoft.svn.util.SVNDebugLog;

/**
 * @version 1.0
 * @author TMate Software Ltd.
 */
public class SVNMergeEditor implements ISVNEditor {

    private SVNRepository myRepos;
    private long myRevision1;
    private long myRevision2;
    private SVNWCAccess myWCAccess;
    private String myTarget;
    private SVNDirectoryInfo myCurrentDirectory;
    private SVNFileInfo myCurrentFile;
    private SVNMerger myMerger;
    private SVNDeltaProcessor myDeltaProcessor;
    private ISVNEventHandler myEventHandler;

    public SVNMergeEditor(SVNWCAccess wcAccess, SVNRepository repos,
            long revision1, long revision2, SVNMerger merger, ISVNEventHandler handler) {
        myRepos = repos;
        myRevision1 = revision1;
        myRevision2 = revision2;
        myWCAccess = wcAccess;
        myEventHandler = handler;
        myMerger = merger;
        myTarget = "".equals(myWCAccess.getTargetName()) ? null : myWCAccess.getTargetName();
        myDeltaProcessor = new SVNDeltaProcessor();
    }

    public void targetRevision(long revision) throws SVNException {
    }

    public void openRoot(long revision) throws SVNException {
        myCurrentDirectory = new SVNDirectoryInfo(null, "", false);
        myCurrentDirectory.myWCPath = myTarget != null ? myTarget : "";

        myCurrentDirectory.myBaseProperties = new HashMap();
        myCurrentDirectory.loadFromRepository(myRepos, myRevision1);
    }

    public void deleteEntry(String path, long revision) throws SVNException {
        SVNNodeKind nodeKind = myRepos.checkPath(path, myRevision1);
        SVNEventAction action = SVNEventAction.SKIP;
        SVNStatusType mergeResult = null;

        path = SVNPathUtil.append(myTarget, path);

        if (nodeKind == SVNNodeKind.FILE) {
            mergeResult = myMerger.fileDeleted(path);
        } else if (nodeKind == SVNNodeKind.DIR) {
            mergeResult = myMerger.directoryDeleted(path);
        }
        SVNEventAction expectedAction = null; 
        if (mergeResult != SVNStatusType.OBSTRUCTED
                && mergeResult != SVNStatusType.MISSING) {
            action = SVNEventAction.UPDATE_DELETE;
        } else {
            expectedAction = SVNEventAction.UPDATE_DELETE;
        }
        SVNEvent event = SVNEventFactory.createMergeEvent(myWCAccess, path, action, expectedAction, null, null, null);
        myWCAccess.handleEvent(event, ISVNEventHandler.UNKNOWN);
    }

    public void addDir(String path, String copyFromPath, long copyFromRevision)
            throws SVNException {
        myCurrentDirectory = new SVNDirectoryInfo(myCurrentDirectory, path,
                true);
        myCurrentDirectory.myBaseProperties = Collections.EMPTY_MAP;

        String wcPath = SVNPathUtil.append(myTarget, path);
        myCurrentDirectory.myWCPath = wcPath;

        // merge dir added.
        SVNEventAction action = SVNEventAction.UPDATE_ADD;
        SVNStatusType mergeResult = myMerger.directoryAdded(
                myCurrentDirectory.myWCPath, myCurrentDirectory.myEntryProps);
        SVNEventAction expectedAction = null;
        if (mergeResult == SVNStatusType.MISSING || mergeResult == SVNStatusType.OBSTRUCTED) {
            action = SVNEventAction.SKIP;
            expectedAction = SVNEventAction.UPDATE_ADD;
        }
        SVNEvent event = SVNEventFactory.createMergeEvent(myWCAccess,
                myCurrentDirectory.myWCPath, action, expectedAction, null, null, SVNNodeKind.DIR);
        myWCAccess.handleEvent(event, ISVNEventHandler.UNKNOWN);
    }

    public void openDir(String path, long revision) throws SVNException {
        myCurrentDirectory = new SVNDirectoryInfo(myCurrentDirectory, path,
                false);

        myCurrentDirectory.myBaseProperties = new HashMap();
        String wcPath = SVNPathUtil.append(myTarget, path);
        myCurrentDirectory.myWCPath = wcPath;
        myCurrentDirectory.loadFromRepository(myRepos, myRevision1);
    }

    public void changeDirProperty(String name, String value)
            throws SVNException {
        if (name != null && myCurrentDirectory.myIsAdded
                && name.startsWith(SVNProperty.SVN_ENTRY_PREFIX)) {
            myCurrentDirectory.myEntryProps.put(name, value);
            return;
        }
        if (name == null || name.startsWith(SVNProperty.SVN_WC_PREFIX)
                || name.startsWith(SVNProperty.SVN_ENTRY_PREFIX)) {
            return;
        }
        if (myCurrentDirectory.myPropertyDiff == null) {
            myCurrentDirectory.myPropertyDiff = new HashMap();
        }
        myCurrentDirectory.myPropertyDiff.put(name, value);
    }

    public void closeDir() throws SVNException {
        SVNStatusType propStatus = SVNStatusType.UNCHANGED;
        if (myCurrentDirectory.myPropertyDiff != null) {
            SVNDirectory dir = myWCAccess.getDirectory(myCurrentDirectory.myWCPath);
            if (dir == null && !myMerger.isDryRun()) {
                SVNEventAction expectedAction = myCurrentDirectory.myIsAdded ? SVNEventAction.UPDATE_ADD : SVNEventAction.UPDATE_UPDATE;
                SVNEvent event = SVNEventFactory.createMergeEvent(myWCAccess,
                        myCurrentDirectory.myWCPath, SVNEventAction.SKIP, expectedAction, null,
                        null, SVNNodeKind.DIR);
                myWCAccess.handleEvent(event, ISVNEventHandler.UNKNOWN);
                myCurrentDirectory = myCurrentDirectory.myParent;
                return;
            }
            if (!myMerger.isDryRun() || dir != null) {
                propStatus = myMerger.directoryPropertiesChanged(
                        myCurrentDirectory.myWCPath,
                        myCurrentDirectory.myBaseProperties,
                        myCurrentDirectory.myPropertyDiff);
            }
        }
        if (!myCurrentDirectory.myIsAdded && propStatus != SVNStatusType.UNCHANGED) {
            SVNEvent event = SVNEventFactory.createMergeEvent(myWCAccess,
                    myCurrentDirectory.myWCPath, SVNEventAction.UPDATE_UPDATE,
                    null, propStatus, SVNNodeKind.DIR);
            myWCAccess.handleEvent(event, ISVNEventHandler.UNKNOWN);
        }
        myCurrentDirectory = myCurrentDirectory.myParent;
    }

    public void addFile(String path, String copyFromPath, long copyFromRevision)
            throws SVNException {
        myCurrentFile = new SVNFileInfo(myCurrentDirectory, path, true);
        myCurrentFile.myBaseProperties = new HashMap();
    }

    public void openFile(String path, long revision) throws SVNException {
        myCurrentFile = new SVNFileInfo(myCurrentDirectory, path, false);
        // props only
        myCurrentFile.loadFromRepository(myCurrentFile.myBaseFile, myRepos, myRevision1, myEventHandler);
    }

    public void changeFileProperty(String commitPath, String name, String value)
            throws SVNException {
        if (name != null && myCurrentFile.myIsAdded
                && name.startsWith(SVNProperty.SVN_ENTRY_PREFIX)) {
            myCurrentFile.myEntryProps.put(name, value);
            return;
        }
        if (name == null || name.startsWith(SVNProperty.SVN_WC_PREFIX)
                || name.startsWith(SVNProperty.SVN_ENTRY_PREFIX)) {
            return;
        }
        if (myCurrentFile.myPropertyDiff == null) {
            myCurrentFile.myPropertyDiff = new HashMap();
        }
        myCurrentFile.myPropertyDiff.put(name, value);
    }

    public void applyTextDelta(String commitPath, String baseChecksum) throws SVNException {
        myCurrentFile.myBaseFile = myMerger.getFile(myCurrentFile.myWCPath, true);

        if (myCurrentFile.myIsAdded) {
            SVNFileUtil.createEmptyFile(myCurrentFile.myBaseFile);
        } else {
            myCurrentFile.loadFromRepository(myCurrentFile.myBaseFile, myRepos, myRevision1, myEventHandler);
        }
        myCurrentFile.myFile = myMerger.getFile(myCurrentFile.myWCPath, false);
        myDeltaProcessor.applyTextDelta(myCurrentFile.myBaseFile, myCurrentFile.myFile, false);
    }

    public OutputStream textDeltaChunk(String commitPath, SVNDiffWindow diffWindow) throws SVNException {
        return myDeltaProcessor.textDeltaChunk(diffWindow);
    }

    public void textDeltaEnd(String commitPath) throws SVNException {
        myDeltaProcessor.textDeltaEnd();
    }

    public void closeFile(String commitPath, String textChecksum) throws SVNException {
//        myDeltaProcessor.close();
        SVNDirectory dir = myWCAccess.getDirectory(myCurrentDirectory.myWCPath);
        if (dir == null && !myMerger.isDryRun()) {
            // not for dry run?
            SVNEventAction expectedAction = myCurrentFile.myIsAdded ? SVNEventAction.UPDATE_ADD : SVNEventAction.UPDATE_UPDATE;
            SVNEvent event = SVNEventFactory.createMergeEvent(myWCAccess,
                    myCurrentFile.myWCPath, SVNEventAction.SKIP,  expectedAction, null, null, SVNNodeKind.FILE);
            myWCAccess.handleEvent(event, ISVNEventHandler.UNKNOWN);
        } else {
            SVNStatusType contents = SVNStatusType.UNCHANGED;
            SVNStatusType props = SVNStatusType.UNCHANGED;
            if (myCurrentFile.myPropertyDiff != null
                    || myCurrentFile.myFile != null) {
                String mimeType1 = (String) myCurrentFile.myBaseProperties
                        .get(SVNProperty.MIME_TYPE);
                String mimeType2 = myCurrentFile.myPropertyDiff != null ? (String) myCurrentFile.myPropertyDiff
                        .get(SVNProperty.MIME_TYPE)
                        : null;
                if (mimeType2 == null) {
                    mimeType2 = mimeType1;
                }
                if (myCurrentFile.myPropertyDiff == null) {
                    myCurrentFile.myPropertyDiff = new HashMap();
                }
                SVNStatusType[] result = null;
                if (myCurrentFile.myIsAdded) {
                    try {
                        result = myMerger
                                .fileAdded(
                                        myCurrentFile.myWCPath,
                                        myCurrentFile.myFile != null ? myCurrentFile.myBaseFile
                                                : null, myCurrentFile.myFile,
                                        myRevision2, 0, mimeType1, mimeType2,
                                        myCurrentFile.myBaseProperties,
                                        myCurrentFile.myPropertyDiff,
                                        myCurrentFile.myEntryProps);
                    } catch (Throwable th) {
                        SVNDebugLog.logInfo(th);
                    }
                } else {
                    try {
                        result = myMerger.fileChanged(myCurrentFile.myWCPath,
                                myCurrentFile.myBaseFile, myCurrentFile.myFile,
                                myRevision1, myRevision2, mimeType1, mimeType2,
                                myCurrentFile.myBaseProperties,
                                myCurrentFile.myPropertyDiff);
                    } catch (Throwable th) {
                        SVNDebugLog.logInfo(th);
                    }
                }
                if (result != null) {
                    contents = result[0];
                    props = result[1];
                }
            }
            SVNEventAction action;
            SVNEventAction expectedAction = null;
            if (contents == SVNStatusType.MISSING || contents == SVNStatusType.OBSTRUCTED) {
                action = SVNEventAction.SKIP;
                expectedAction = myCurrentFile.myIsAdded ? SVNEventAction.UPDATE_ADD : SVNEventAction.UPDATE_UPDATE;
            } else if (myCurrentFile.myIsAdded) {
                action = SVNEventAction.UPDATE_ADD;
            } else {
                action = SVNEventAction.UPDATE_UPDATE;
            }
            SVNEvent event = SVNEventFactory.createMergeEvent(myWCAccess,
                    myCurrentFile.myWCPath, action, expectedAction, contents, props, SVNNodeKind.FILE);
            myWCAccess.handleEvent(event, ISVNEventHandler.UNKNOWN);
        }
        if (myCurrentFile.myFile != null) {
            myCurrentFile.myFile.delete();
        }
        if (myCurrentFile.myBaseFile != null) {
            myCurrentFile.myBaseFile.delete();
        }
    }

    public SVNCommitInfo closeEdit() throws SVNException {
        return null;
    }

    public void abortEdit() throws SVNException {
    }

    public void absentDir(String path) throws SVNException {
    }

    public void absentFile(String path) throws SVNException {
    }

    private static class SVNDirectoryInfo {

        public SVNDirectoryInfo(SVNDirectoryInfo parent, String path,
                boolean added) {
            myParent = parent;
            myPath = path;
            myIsAdded = added;
            if (added) {
                myEntryProps = new HashMap();
            }
        }

        private boolean myIsAdded;
        private String myPath;
        private String myWCPath;
        private Map myBaseProperties;
        private Map myPropertyDiff;
        private Map myEntryProps;
        private SVNDirectoryInfo myParent;

        public void loadFromRepository(SVNRepository repos, long revision)
                throws SVNException {
            myBaseProperties = new HashMap();
            repos.getDir(myPath, revision, myBaseProperties,
                    (ISVNDirEntryHandler) null);
            Collection names = new ArrayList(myBaseProperties.keySet());
            for (Iterator propNames = names.iterator(); propNames.hasNext();) {
                String propName = (String) propNames.next();
                if (propName.startsWith(SVNProperty.SVN_ENTRY_PREFIX)
                        || propName.startsWith(SVNProperty.SVN_WC_PREFIX)) {
                    myBaseProperties.remove(propName);
                }
            }
        }
    }

    private static class SVNFileInfo {

        public SVNFileInfo(SVNDirectoryInfo parent, String path, boolean added) {
            myPath = path;
            myWCPath = SVNPathUtil.append(parent.myWCPath, SVNPathUtil.tail(path));
            myIsAdded = added;
            if (added) {
                myEntryProps = new HashMap();
            }
        }

        public void loadFromRepository(File dst, SVNRepository repos, long revision, ISVNEventHandler handler) throws SVNException {
            OutputStream os = null;
            myBaseProperties = new HashMap();
            try {
                os = dst == null ? null : SVNFileUtil.openFileForWriting(dst);
                repos.getFile(myPath, revision, myBaseProperties, new SVNCancellableOutputStream(os, handler));
            } finally {
                SVNFileUtil.closeFile(os);
            }
            Collection names = new ArrayList(myBaseProperties.keySet());
            for (Iterator propNames = names.iterator(); propNames.hasNext();) {
                String propName = (String) propNames.next();
                if (propName.startsWith(SVNProperty.SVN_ENTRY_PREFIX)
                        || propName.startsWith(SVNProperty.SVN_WC_PREFIX)) {
                    myBaseProperties.remove(propName);
                }
            }
        }

        private boolean myIsAdded;
        private String myWCPath;
        private String myPath;
        private File myFile;
        private File myBaseFile;
        private Map myBaseProperties;
        private Map myPropertyDiff;
        private Map myEntryProps;
    }
}