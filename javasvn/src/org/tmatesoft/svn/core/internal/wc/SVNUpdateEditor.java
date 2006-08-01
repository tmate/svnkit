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
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.internal.util.SVNEncodingUtil;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.diff.SVNDeltaProcessor;
import org.tmatesoft.svn.core.io.diff.SVNDiffWindow;
import org.tmatesoft.svn.core.wc.SVNEventAction;
import org.tmatesoft.svn.core.wc.SVNStatusType;
import org.tmatesoft.svn.util.SVNDebugLog;

/**
 * @version 1.0
 * @author TMate Software Ltd.
 */
public class SVNUpdateEditor implements ISVNEditor {

    private String mySwitchURL;
    private String myTarget;
    private String myTargetURL;
    private String myRootURL;
    private boolean myIsRecursive;
    private SVNWCAccess myWCAccess;
    private SVNDirectoryInfo myCurrentDirectory;
    private SVNFileInfo myCurrentFile;
    private long myTargetRevision;
    private boolean myIsRootOpen;
    private boolean myIsTargetDeleted;
    private boolean myIsLeaveConflicts;
    
    private SVNDeltaProcessor myDeltaProcessor;

    public SVNUpdateEditor(SVNWCAccess wcAccess, String switchURL, boolean recursive, boolean leaveConflicts) throws SVNException {
        myWCAccess = wcAccess;
        myIsRecursive = recursive;
        myTarget = wcAccess.getTargetName();
        mySwitchURL = switchURL;
        myTargetRevision = -1;
        myIsLeaveConflicts = leaveConflicts;
        myDeltaProcessor = new SVNDeltaProcessor();

        SVNEntry entry = wcAccess.getAnchor().getAdminArea(false).getEntry("", true);
        myTargetURL = entry.getURL();
        myRootURL = entry.getRepositoryRoot();
        if (myTarget != null) {
            myTargetURL = SVNPathUtil.append(myTargetURL, SVNEncodingUtil.uriEncode(myTarget));
        }
        if (mySwitchURL != null && entry != null && entry.getRepositoryRoot() != null) {
            if (!mySwitchURL.startsWith(entry.getRepositoryRoot() + "/") && !mySwitchURL.equals(entry.getRepositoryRoot())) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_INVALID_SWITCH, "''{0}''\nis not the same repository as\n''{1}''",
                        new Object[] {mySwitchURL, entry.getRepositoryRoot()});
                SVNErrorManager.error(err);
            }
        }
        wcAccess.getTarget().getAdminArea(false).close();

        if ("".equals(myTarget)) {
            myTarget = null;
        }
    }

    public void targetRevision(long revision) throws SVNException {
        myTargetRevision = revision;
    }

    public long getTargetRevision() {
        return myTargetRevision;
    }

    public void openRoot(long revision) throws SVNException {
        myIsRootOpen = true;
        myCurrentDirectory = createDirectoryInfo(null, "", false);
        if (myTarget == null) {
            SVNAdminArea adminArea = myCurrentDirectory.getDirectory().getAdminArea(false);
            SVNEntry entry = adminArea.getEntry("", true);
            entry.setRevision(myTargetRevision);
            entry.setURL(myCurrentDirectory.URL);
            entry.setIncomplete(true);
            if (mySwitchURL != null) {
                clearWCProperty(myCurrentDirectory.getDirectory());
            }
            adminArea.save(true);
        }
    }

    public void deleteEntry(String path, long revision) throws SVNException {
        String name = SVNPathUtil.tail(path);

        SVNEntry entry = myCurrentDirectory.getDirectory().getAdminArea(false).getEntry(name, true);
        if (entry == null) {
            return;
        }

        SVNLog log = myCurrentDirectory.getLog(true);
        Map attributes = new HashMap();

        attributes.put(SVNLog.NAME_ATTR, name);
        log.addCommand(SVNLog.DELETE_ENTRY, attributes, false);
        SVNNodeKind kind = entry.getKind();
        boolean isDeleted = entry.isDeleted();
        if (path.equals(myTarget)) {
            attributes.put(SVNLog.NAME_ATTR, name);
            attributes.put(SVNProperty.shortPropertyName(SVNProperty.KIND), kind == SVNNodeKind.DIR ? SVNProperty.KIND_DIR : SVNProperty.KIND_FILE);
            attributes.put(SVNProperty.shortPropertyName(SVNProperty.REVISION), Long.toString(myTargetRevision));
            attributes.put(SVNProperty.shortPropertyName(SVNProperty.DELETED), Boolean.TRUE.toString());
            log.addCommand(SVNLog.MODIFY_ENTRY, attributes, false);
            myIsTargetDeleted = true;
        }
        if (mySwitchURL != null && kind == SVNNodeKind.DIR) {
            myCurrentDirectory.getDirectory().destroy(name, true);
        }
        log.save();
        myCurrentDirectory.runLogs();
        if (isDeleted) {
            // entry was deleted, but it was already deleted, no need to make a
            // notification.
            return;
        }
        myWCAccess.handleEvent(SVNEventFactory.createUpdateDeleteEvent(myWCAccess, myCurrentDirectory.getDirectory(), kind, name));
    }

    public void addDir(String path, String copyFromPath, long copyFromRevision) throws SVNException {
        SVNDirectory parentDir = myCurrentDirectory.getDirectory();
        myCurrentDirectory = createDirectoryInfo(myCurrentDirectory, path, true);

        String name = SVNPathUtil.tail(path);
        File file = parentDir.getFile(name);
        if (file.exists()) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_OBSTRUCTED_UPDATE, "Failed to add directory ''{0}'': object of the same name already exists", path);
            SVNErrorManager.error(err);
        } else if (SVNFileUtil.getAdminDirectoryName().equals(name)) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_OBSTRUCTED_UPDATE, "Failed to add directory ''{0}'':  object of the same name as the administrative directory", path);
            SVNErrorManager.error(err);
        }
        SVNEntry entry = parentDir.getAdminArea(false).getEntry(name, true);
        if (entry != null) {
            if (entry.isScheduledForAddition()) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_OBSTRUCTED_UPDATE, "Failed to add directory ''{0}'': object of the same name already exists", path);
                SVNErrorManager.error(err);
            }
        } else {
            entry = parentDir.getAdminArea(false).addEntry(name);
        }
        entry.setKind(SVNNodeKind.DIR);
        entry.setAbsent(false);
        entry.setDeleted(false);
        parentDir.getAdminArea(false).save(true);

        SVNDirectory dir = parentDir.createChildDirectory(name, myCurrentDirectory.URL, null, myTargetRevision);
        if (dir == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_NOT_LOCKED, "Working copy ''{0}'' is missing or not locked", parentDir.getFile(name));
            SVNErrorManager.error(err);
        } else {
            dir.getAdminArea(false).getEntry("", false).setIncomplete(true);
            dir.getAdminArea(false).save(true);
            dir.lock(0);
        }
        myWCAccess.handleEvent(SVNEventFactory.createUpdateAddEvent(myWCAccess, parentDir, SVNNodeKind.DIR, entry));
    }

    public void openDir(String path, long revision) throws SVNException {
        myCurrentDirectory = createDirectoryInfo(myCurrentDirectory, path, false);
        if (myCurrentDirectory.getDirectory() == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_NOT_LOCKED, "Working copy ''{0}'' is missing or not locked", path);
            SVNErrorManager.error(err);
        }
        SVNAdminArea adminArea = myCurrentDirectory.getDirectory().getAdminArea(false);
        SVNEntry entry = adminArea.getEntry("", true);
        entry.setRevision(myTargetRevision);
        entry.setURL(myCurrentDirectory.URL);
        entry.setIncomplete(true);
        if (mySwitchURL != null) {
            clearWCProperty(myCurrentDirectory.getDirectory());
        }
        adminArea.save(true);
    }

    public void absentDir(String path) throws SVNException {
        absentEntry(path, SVNNodeKind.DIR);
    }

    public void absentFile(String path) throws SVNException {
        absentEntry(path, SVNNodeKind.FILE);
    }

    private void absentEntry(String path, SVNNodeKind kind) throws SVNException {
        String name = SVNPathUtil.tail(path);
        SVNAdminArea adminArea = myCurrentDirectory.getDirectory().getAdminArea(false);
        SVNEntry entry = adminArea.getEntry(name, true);
        if (entry != null && entry.isScheduledForAddition()) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_OBSTRUCTED_UPDATE, "Failed to mark ''{0}'' absent: item of the same name is already scheduled for addition");
            SVNErrorManager.error(err);
        }
        if (entry == null) {
            entry = adminArea.addEntry(name);
        }
        if (entry != null) {
            entry.setKind(kind);
            entry.setDeleted(false);
            entry.setRevision(myTargetRevision);
            entry.setAbsent(true);
        }
        adminArea.save(true);

    }

    public void changeDirProperty(String name, String value) throws SVNException {
        myCurrentDirectory.propertyChanged(name, value);
    }

    private void clearWCProperty(SVNDirectory dir) throws SVNException {
        if (dir == null) {
            return;
        }
        SVNAdminArea adminArea = dir.getAdminArea(false);
        for (Iterator ents = adminArea.entries(false); ents.hasNext();) {
            SVNEntry entry = (SVNEntry) ents.next();
            if (entry.isFile() || "".equals(entry.getName())) {
//                SVNProperties props = dir.getWCProperties(entry.getName());
//                props.setPropertyValue(SVNProperty.WC_URL, null);
                adminArea.setWCPropertyValue(entry.getName(), SVNProperty.WC_URL, null);
            } else {
                clearWCProperty(dir.getChildDirectory(entry.getName()));
            }
        }
    }

    public void closeDir() throws SVNException {
        Map modifiedWCProps = myCurrentDirectory.getChangedWCProperties();
        Map modifiedEntryProps = myCurrentDirectory.getChangedEntryProperties();
        Map modifiedProps = myCurrentDirectory.getChangedProperties();

        SVNStatusType propStatus = SVNStatusType.UNCHANGED;
        SVNDirectory dir = myCurrentDirectory.getDirectory();
        if (modifiedWCProps != null || modifiedEntryProps != null || modifiedProps != null) {
            SVNLog log = myCurrentDirectory.getLog(true);

            if (modifiedProps != null && !modifiedProps.isEmpty()) {
                myWCAccess.addExternals(dir, (String) modifiedProps.get(SVNProperty.EXTERNALS));

                SVNAdminArea adminArea = dir.getAdminArea(false); 
                Map oldBaseProps = adminArea.getBaseProperties(adminArea.getThisDirName(), false);//dir.getBaseProperties("", false).asMap();
                propStatus = dir.mergeProperties("", oldBaseProps, modifiedProps, true, log);
                if (myCurrentDirectory.IsAdded && !dir.hasPropModifications("")) {
                    Map command = new HashMap();
                    command.put(SVNLog.NAME_ATTR, "");
                    command.put(SVNProperty.shortPropertyName(SVNProperty.PROP_TIME), SVNLog.WC_TIMESTAMP);
                    log.addCommand(SVNLog.MODIFY_ENTRY, command, false);
                }
            }
            log.logChangedEntryProperties("", modifiedEntryProps);
            log.logChangedWCProperties("", modifiedWCProps);
            log.save();
        }
        myCurrentDirectory.runLogs();
        completeDirectory(myCurrentDirectory);
        if (!myCurrentDirectory.IsAdded && propStatus != SVNStatusType.UNCHANGED) {
            myWCAccess.handleEvent(SVNEventFactory.createUpdateModifiedEvent(myWCAccess, dir, "", SVNNodeKind.DIR, SVNEventAction.UPDATE_UPDATE, null, SVNStatusType.UNCHANGED, propStatus, null));
        }
        myCurrentDirectory = myCurrentDirectory.Parent;
    }

    public SVNCommitInfo closeEdit() throws SVNException {
        if (myTarget != null && !myWCAccess.getAnchor().getFile(myTarget).exists()) {
            myCurrentDirectory = createDirectoryInfo(null, "", false);
            deleteEntry(myTarget, myTargetRevision);
        }
        if (!myIsRootOpen) {
            completeDirectory(myCurrentDirectory);
        }
        if (!myIsTargetDeleted) {
            bumpDirectories();
        }
        return null;
    }

    public void addFile(String path, String copyFromPath, long copyFromRevision) throws SVNException {
        myCurrentFile = createFileInfo(myCurrentDirectory, path, true);
    }

    public void openFile(String path, long revision) throws SVNException {
        myCurrentFile = createFileInfo(myCurrentDirectory, path, false);
    }

    public void changeFileProperty(String commitPath, String name, String value) throws SVNException {
        myCurrentFile.propertyChanged(name, value);
        if (myWCAccess.getOptions().isUseCommitTimes() && SVNProperty.COMMITTED_DATE.equals(name)) {
            myCurrentFile.CommitTime = value;
        }
    }

    public void applyTextDelta(String commitPath, String baseChecksum) throws SVNException {
        SVNDirectory dir = myCurrentFile.getDirectory();
        SVNAdminArea adminArea = dir.getAdminArea(false);
        SVNEntry entry = adminArea.getEntry(myCurrentFile.Name, true);
        File baseFile = dir.getBaseFile(myCurrentFile.Name, false);
        if (entry != null && entry.getChecksum() != null) {
            if (baseChecksum == null) {
                baseChecksum = entry.getChecksum();
            }
            String realChecksum = SVNFileUtil.computeChecksum(baseFile);
            if (baseChecksum != null && (realChecksum == null || !realChecksum.equals(baseChecksum))) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_CORRUPT_TEXT_BASE, "Checksum mismatch for ''{0}''; expected: ''{1}'', actual: ''{2}''",
                        new Object[] {myCurrentFile.getPath(), baseChecksum, realChecksum}); 
                SVNErrorManager.error(err);
            }
        }
        File baseTmpFile = dir.getBaseFile(myCurrentFile.Name, true);
        myCurrentFile.TextUpdated = true;
        myDeltaProcessor.applyTextDelta(baseFile, baseTmpFile, true);
    }

    public OutputStream textDeltaChunk(String commitPath, SVNDiffWindow diffWindow) throws SVNException {
        return myDeltaProcessor.textDeltaChunk(diffWindow);
    }

    public void textDeltaEnd(String commitPath) throws SVNException {
        myCurrentFile.Checksum = myDeltaProcessor.textDeltaEnd();
    }

    public void closeFile(String commitPath, String textChecksum) throws SVNException {
//        myDeltaProcessor.close();
        // check checksum.
        String checksum = null;
        if (textChecksum != null && myCurrentFile.TextUpdated) {            
            File baseTmpFile = myCurrentFile.getDirectory().getBaseFile(myCurrentFile.Name, true);
            checksum = myCurrentFile.Checksum != null ? myCurrentFile.Checksum : SVNFileUtil.computeChecksum(baseTmpFile);            
            if (!textChecksum.equals(checksum)) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CHECKSUM_MISMATCH, "Checksum mismatch for ''{0}''; expected: ''{1}'', actual: ''{2}''",
                        new Object[] {myCurrentFile.getPath(), textChecksum, checksum}); 
                SVNErrorManager.error(err);
            }
            checksum = textChecksum;
        }
        SVNDirectory dir = myCurrentFile.getDirectory();
        SVNLog log = myCurrentDirectory.getLog(true);

        // merge props.
        Map modifiedWCProps = myCurrentFile.getChangedWCProperties();
        Map modifiedEntryProps = myCurrentFile.getChangedEntryProperties();
        Map modifiedProps = myCurrentFile.getChangedProperties();
        String name = myCurrentFile.Name;
        String commitTime = myCurrentFile.CommitTime;

        Map command = new HashMap();

        SVNStatusType textStatus = SVNStatusType.UNCHANGED;
        SVNStatusType lockStatus = SVNStatusType.LOCK_UNCHANGED;

        boolean magicPropsChanged = false;
        if (modifiedProps != null && !modifiedProps.isEmpty()) {
            magicPropsChanged = modifiedProps.containsKey(SVNProperty.EXECUTABLE) || 
            modifiedProps.containsKey(SVNProperty.NEEDS_LOCK) || 
            modifiedProps.containsKey(SVNProperty.KEYWORDS) || 
            modifiedProps.containsKey(SVNProperty.EOL_STYLE) || 
            modifiedProps.containsKey(SVNProperty.SPECIAL);
        }
        
        SVNAdminArea adminArea = dir.getAdminArea(false);
//        Map oldBaseProps = dir.getBaseProperties(name, false).asMap();
        Map oldBaseProps = adminArea.getBaseProperties(name, false);

        boolean isLocalPropsModified = !myCurrentFile.IsAdded && dir.hasPropModifications(name);
        SVNStatusType propStatus = dir.mergeProperties(name, oldBaseProps, modifiedProps, true, log);
        if (modifiedEntryProps != null) {
            lockStatus = log.logChangedEntryProperties(name, modifiedEntryProps);
        }

        // merge contents.
        File textTmpBase = dir.getBaseFile(name, true);
        String adminDir = SVNFileUtil.getAdminDirectoryName();

        String tmpPath = adminDir + "/tmp/text-base/" + name + ".svn-base";
        String basePath = adminDir + "/text-base/" + name + ".svn-base";
        File workingFile = dir.getFile(name);

        if (!myCurrentFile.TextUpdated && magicPropsChanged && workingFile.exists()) {
            // only props were changed, but we have to retranslate file.
            // only if wc file exists (may be locally deleted), otherwise no
            // need to retranslate...
            command.put(SVNLog.NAME_ATTR, name);
            command.put(SVNLog.DEST_ATTR, tmpPath);
            log.addCommand(SVNLog.COPY_AND_DETRANSLATE, command, false);
            command.clear();
            command.put(SVNLog.NAME_ATTR, tmpPath);
            command.put(SVNLog.DEST_ATTR, name);
            log.addCommand(SVNLog.COPY_AND_TRANSLATE, command, false);
            command.clear();
        }
        // update entry.
        command.put(SVNLog.NAME_ATTR, name);
        command.put(SVNProperty.shortPropertyName(SVNProperty.KIND), SVNProperty.KIND_FILE);
        command.put(SVNProperty.shortPropertyName(SVNProperty.REVISION), Long.toString(myTargetRevision));
        command.put(SVNProperty.shortPropertyName(SVNProperty.DELETED), Boolean.FALSE.toString());
        command.put(SVNProperty.shortPropertyName(SVNProperty.ABSENT), Boolean.FALSE.toString());
        log.addCommand(SVNLog.MODIFY_ENTRY, command, false);
        command.clear();

        command.put(SVNLog.NAME_ATTR, name);
        command.put(SVNProperty.shortPropertyName(SVNProperty.URL), myCurrentFile.URL);
        log.addCommand(SVNLog.MODIFY_ENTRY, command, false);
        command.clear();

        boolean isLocallyModified = !myCurrentFile.IsAdded && dir.hasTextModifications(name, false);
        if (myCurrentFile.TextUpdated && textTmpBase.exists()) {
            textStatus = SVNStatusType.CHANGED;
            // there is a text replace working copy with.
            if (!isLocallyModified || !workingFile.exists()) {
                command.put(SVNLog.NAME_ATTR, tmpPath);
                command.put(SVNLog.DEST_ATTR, name);
                log.addCommand(SVNLog.COPY_AND_TRANSLATE, command, false);
                command.clear();
            } else {
                // do test merge.
                String oldEolStyle = null;
                String oldKeywords = null;
//                SVNProperties props = dir.getProperties(myCurrentFile.Name, false);
                try {
                    if (magicPropsChanged && 
                            (modifiedProps.containsKey(SVNProperty.EOL_STYLE) || modifiedProps.containsKey(SVNProperty.KEYWORDS))) {
                        // use new valuse to let dry-run merge use the same input as real merge will use.
//                        oldKeywords = props.getPropertyValue(SVNProperty.KEYWORDS);
                        oldKeywords = adminArea.getPropertyValue(myCurrentFile.Name, false, SVNProperty.KEYWORDS);

//                        oldEolStyle = props.getPropertyValue(SVNProperty.EOL_STYLE);
                        oldEolStyle = adminArea.getPropertyValue(myCurrentFile.Name, false, SVNProperty.EOL_STYLE);
//                        props.setPropertyValue(SVNProperty.EOL_STYLE, (String) modifiedProps.get(SVNProperty.EOL_STYLE));
                        adminArea.setPropertyValue(myCurrentFile.Name, false, SVNProperty.EOL_STYLE, (String) modifiedProps.get(SVNProperty.EOL_STYLE));
  
//                        props.setPropertyValue(SVNProperty.KEYWORDS, (String) modifiedProps.get(SVNProperty.KEYWORDS));
                        adminArea.setPropertyValue(myCurrentFile.Name, false, SVNProperty.KEYWORDS, (String) modifiedProps.get(SVNProperty.KEYWORDS));
  
                    }
                    textStatus = dir.mergeText(name, basePath, tmpPath, "", "", "", myIsLeaveConflicts, true);
                } finally {
                    if (magicPropsChanged && 
                            (modifiedProps.containsKey(SVNProperty.EOL_STYLE) || modifiedProps.containsKey(SVNProperty.KEYWORDS))) {
                        // restore original values.
//                        props.setPropertyValue(SVNProperty.EOL_STYLE, oldEolStyle);
                        adminArea.setPropertyValue(myCurrentFile.Name, false, SVNProperty.EOL_STYLE, oldEolStyle);
//                        props.setPropertyValue(SVNProperty.KEYWORDS, oldKeywords);
                        adminArea.setPropertyValue(myCurrentFile.Name, false, SVNProperty.KEYWORDS, oldKeywords);
                        
                    }
                }
                if (textStatus == SVNStatusType.UNCHANGED) {
                    textStatus = SVNStatusType.MERGED;
                }

                SVNEntry entry = adminArea.getEntry(name, true);
                String oldRevisionStr = ".r" + entry.getRevision();
                String newRevisionStr = ".r" + myTargetRevision;
                adminArea.close();

                command.put(SVNLog.NAME_ATTR, name);
                command.put(SVNLog.ATTR1, basePath);
                command.put(SVNLog.ATTR2, tmpPath);
                command.put(SVNLog.ATTR3, oldRevisionStr);
                command.put(SVNLog.ATTR4, newRevisionStr);
                command.put(SVNLog.ATTR5, ".mine");
                if (textStatus == SVNStatusType.CONFLICTED_UNRESOLVED) {
                    command.put(SVNLog.ATTR6, Boolean.TRUE.toString());
                }
                log.addCommand(SVNLog.MERGE, command, false);
                command.clear();
            }
        } else if (lockStatus == SVNStatusType.LOCK_UNLOCKED) {
            command.put(SVNLog.NAME_ATTR, name);
            log.addCommand(SVNLog.MAYBE_READONLY, command, false);
            command.clear();
        }
        if (!isLocalPropsModified) {
            command.put(SVNLog.NAME_ATTR, name);
            command.put(SVNProperty.shortPropertyName(SVNProperty.PROP_TIME), SVNLog.WC_TIMESTAMP);
            log.addCommand(SVNLog.MODIFY_ENTRY, command, false);
            command.clear();
        }
        if (myCurrentFile.TextUpdated && textTmpBase.exists()) {
            command.put(SVNLog.NAME_ATTR, tmpPath);
            command.put(SVNLog.DEST_ATTR, basePath);
            log.addCommand(SVNLog.MOVE, command, false);
            command.clear();
            command.put(SVNLog.NAME_ATTR, basePath);
            log.addCommand(SVNLog.READONLY, command, false);
            command.clear();
            command.put(SVNLog.NAME_ATTR, name);
            command.put(SVNProperty.shortPropertyName(SVNProperty.CHECKSUM), checksum);
            log.addCommand(SVNLog.MODIFY_ENTRY, command, false);
            command.clear();
        }
        if (modifiedWCProps != null) {
            log.logChangedWCProperties(name, modifiedWCProps);
        }
        if (!isLocallyModified) {
            if (commitTime != null) {
                command.put(SVNLog.NAME_ATTR, name);
                command.put(SVNLog.TIMESTAMP_ATTR, commitTime);
                log.addCommand(SVNLog.SET_TIMESTAMP, command, false);
                command.clear();
            }
            if ((myCurrentFile.TextUpdated && textTmpBase.exists()) || magicPropsChanged) {
                command.put(SVNLog.NAME_ATTR, name);
                command.put(SVNProperty.shortPropertyName(SVNProperty.TEXT_TIME), SVNLog.WC_TIMESTAMP);
                log.addCommand(SVNLog.MODIFY_ENTRY, command, false);
                command.clear();
            }
        }
        // bump.
        log.save();
        myCurrentFile.TextUpdated = false;
        completeDirectory(myCurrentDirectory);

        // notify.
        if (!myCurrentFile.IsAdded && textStatus == SVNStatusType.UNCHANGED && propStatus == SVNStatusType.UNCHANGED && lockStatus == SVNStatusType.LOCK_UNCHANGED) {
            // no changes, probably just wcurl switch.
            myCurrentFile = null;
            return;
        }
        SVNEventAction action = myCurrentFile.IsAdded ? SVNEventAction.UPDATE_ADD : SVNEventAction.UPDATE_UPDATE;
        myWCAccess.handleEvent(SVNEventFactory.createUpdateModifiedEvent(myWCAccess, dir, myCurrentFile.Name, SVNNodeKind.FILE, action, null, textStatus, propStatus, lockStatus));
        myCurrentFile = null;
    }

    public void abortEdit() throws SVNException {
    }

    private void bumpDirectories() throws SVNException {
        SVNDirectory dir = myWCAccess.getAnchor();
        if (myTarget != null) {
            if (dir.getChildDirectory(myTarget) == null) {
                SVNEntry entry = dir.getAdminArea(false).getEntry(myTarget, true);
                boolean save = bumpEntry(dir.getAdminArea(false), entry, mySwitchURL, myRootURL, myTargetRevision, false);
                if (save) {
                    dir.getAdminArea(false).save(true);
                } else {
                    dir.getAdminArea(false).close();
                }
                return;
            }
            dir = dir.getChildDirectory(myTarget);
        }
        bumpDirectory(dir, mySwitchURL, myRootURL);
    }

    private void bumpDirectory(SVNDirectory dir, String url, String rootURL) throws SVNException {
        SVNAdminArea adminArea = dir.getAdminArea(false);
        boolean save = bumpEntry(adminArea, adminArea.getEntry("", true), url, rootURL, myTargetRevision, false);
        Map childDirectories = new HashMap();
        for (Iterator ents = adminArea.entries(true); ents.hasNext();) {
            SVNEntry entry = (SVNEntry) ents.next();
            if ("".equals(entry.getName())) {
                continue;
            }
            String childURL = url != null ? SVNPathUtil.append(url, SVNEncodingUtil.uriEncode(entry.getName())) : null;
            if (entry.getKind() == SVNNodeKind.FILE) {
                save |= bumpEntry(adminArea, entry, childURL, rootURL, myTargetRevision, true);
            } else if (myIsRecursive && entry.getKind() == SVNNodeKind.DIR) {
                SVNDirectory childDirectory = dir.getChildDirectory(entry.getName());
                if (!entry.isScheduledForAddition() && (childDirectory == null || !childDirectory.isVersioned())) {
                    myWCAccess.handleEvent(SVNEventFactory.createUpdateDeleteEvent(myWCAccess, dir, entry));
                    adminArea.deleteEntry(entry.getName());
                    save = true;
                } else {
                    // schedule for recursion, map of dir->url
                    childDirectories.put(childDirectory, childURL);
                }
            }
        }
        if (save) {
            adminArea.save(true);
        }
        for (Iterator children = childDirectories.keySet().iterator(); children.hasNext();) {
            SVNDirectory child = (SVNDirectory) children.next();
            String childURL = (String) childDirectories.get(child);
            if (child != null) {
                bumpDirectory(child, childURL, rootURL);
            } else {
                SVNDebugLog.logInfo("svn: Directory object is null in bump directories method");
            }
        }
    }

    private static boolean bumpEntry(SVNAdminArea adminArea, SVNEntry entry, String url, String rootURL, long revision, boolean delete) {
        if (entry == null) {
            return false;
        }
        boolean save = false;
        if (url != null) {
            save = entry.setURL(url);
        }
        save |= entry.setRepositoryRoot(rootURL);
        if (revision >= 0 && !entry.isScheduledForAddition() && !entry.isScheduledForReplacement()) {
            save |= entry.setRevision(revision);
        }
        if (delete && (entry.isDeleted() || (entry.isAbsent() && entry.getRevision() != revision))) {
            adminArea.deleteEntry(entry.getName());
            save = true;
        }
        return save;
    }

    private void completeDirectory(SVNDirectoryInfo info) throws SVNException {
        while (info != null) {
            info.RefCount--;
            if (info.RefCount > 0) {
                return;
            }
            if (info.Parent == null && myTarget != null) {
                return;
            }
            SVNAdminArea adminArea = info.getDirectory().getAdminArea(false);
            if (adminArea.getEntry("", true) == null) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_NOT_FOUND, "No root entry found in ''{0}''", info.getDirectory().getPath());
                SVNErrorManager.error(err);
            }
            for (Iterator ents = adminArea.entries(true); ents.hasNext();) {
                SVNEntry entry = (SVNEntry) ents.next();
                if ("".equals(entry.getName())) {
                    entry.setIncomplete(false);
                    continue;
                }
                if (entry.isDeleted()) {
                    if (!entry.isScheduledForAddition()) {
                        adminArea.deleteEntry(entry.getName());
                    } else {
                        entry.setDeleted(false);
                    }
                } else if (entry.isAbsent() && entry.getRevision() != myTargetRevision) {
                    adminArea.deleteEntry(entry.getName());
                } else if (entry.getKind() == SVNNodeKind.DIR) {
                    SVNDirectory childDirectory = info.getDirectory().getChildDirectory(entry.getName());
                    if (myIsRecursive && (childDirectory == null || !childDirectory.isVersioned()) && !entry.isAbsent() && !entry.isScheduledForAddition()) {
                        myWCAccess.handleEvent(SVNEventFactory.createUpdateDeleteEvent(myWCAccess, info.getDirectory(), entry));
                        adminArea.deleteEntry(entry.getName());
                    }
                }
            }
            adminArea.save(true);
            info = info.Parent;
        }
    }

    private SVNFileInfo createFileInfo(SVNDirectoryInfo parent, String path, boolean added) throws SVNException {
        SVNFileInfo info = new SVNFileInfo(parent, path);
        info.IsAdded = added;
        info.Name = SVNPathUtil.tail(path);
        SVNDirectory dir = parent.getDirectory();
        if (added && dir.getFile(info.Name).exists()) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_OBSTRUCTED_UPDATE, "Failed to add file ''{0}'': object of the same name already exists", path);
            SVNErrorManager.error(err);
        }
        SVNAdminArea adminArea = null;
        try {
            adminArea = dir.getAdminArea(false);
            SVNEntry entry = adminArea.getEntry(info.Name, true);
            if (added && entry != null && entry.isScheduledForAddition()) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_OBSTRUCTED_UPDATE, "Failed to add file ''{0}'': object of the same name already exists and scheduled for addition", path);
                SVNErrorManager.error(err);
            }
            if (!added && entry == null) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNVERSIONED_RESOURCE, "File ''{0}'' in directory ''{1}'' is not a versioned resource", 
                        new Object[] {info.Name, dir.getRoot()});
                SVNErrorManager.error(err);
            }
            if (mySwitchURL != null || entry == null) {
                info.URL = SVNPathUtil.append(parent.URL, SVNEncodingUtil.uriEncode(info.Name));
            } else {
                info.URL = entry.getURL();
            }
        } finally {
            if (adminArea != null) {
                adminArea.close();
            }
        }
        parent.RefCount++;
        return info;
    }

    private SVNDirectoryInfo createDirectoryInfo(SVNDirectoryInfo parent, String path, boolean added) throws SVNException {
        SVNDirectoryInfo info = new SVNDirectoryInfo(path);
        info.Parent = parent;
        info.IsAdded = added;
        String name = path != null ? SVNPathUtil.tail(path) : "";

        if (mySwitchURL == null) {
            SVNDirectory dir = added ? null : info.getDirectory();
            if (dir != null && dir.getAdminArea(false).getEntry("", true) != null) {
                info.URL = dir.getAdminArea(false).getEntry("", true).getURL();
            }
            if (info.URL == null && parent != null) {
                info.URL = SVNPathUtil.append(parent.URL, SVNEncodingUtil.uriEncode(name));
            } else if (info.URL == null && parent == null) {
                info.URL = myTargetURL;
            }
        } else {
            if (parent == null) {
                info.URL = myTarget == null ? mySwitchURL : SVNPathUtil.removeTail(mySwitchURL);
            } else {
                if (myTarget != null && parent.Parent == null) {
                    info.URL = mySwitchURL;
                } else {
                    info.URL = SVNPathUtil.append(parent.URL, SVNEncodingUtil.uriEncode(name));
                }
            }
        }
        info.RefCount = 1;
        if (info.Parent != null) {
            info.Parent.RefCount++;
        }
        return info;
    }

    private class SVNEntryInfo {

        public String URL;
        public boolean IsAdded;
        public SVNDirectoryInfo Parent;
        private String myPath;
        private Map myChangedProperties;
        private Map myChangedEntryProperties;
        private Map myChangedWCProperties;

        protected SVNEntryInfo(String path) {
            myPath = path;
        }

        protected String getPath() {
            return myPath;
        }

        public void propertyChanged(String name, String value) {
            if (name.startsWith(SVNProperty.SVN_ENTRY_PREFIX)) {
                myChangedEntryProperties = myChangedEntryProperties == null ? new HashMap() : myChangedEntryProperties;
                myChangedEntryProperties.put(name.substring(SVNProperty.SVN_ENTRY_PREFIX.length()), value);
            } else if (name.startsWith(SVNProperty.SVN_WC_PREFIX)) {
                myChangedWCProperties = myChangedWCProperties == null ? new HashMap() : myChangedWCProperties;
                myChangedWCProperties.put(name, value);
            } else {
                myChangedProperties = myChangedProperties == null ? new HashMap() : myChangedProperties;
                myChangedProperties.put(name, value);
            }
        }

        public Map getChangedWCProperties() {
            return myChangedWCProperties;
        }

        public Map getChangedEntryProperties() {
            return myChangedEntryProperties;
        }

        public Map getChangedProperties() {
            return myChangedProperties;
        }
    }

    private class SVNFileInfo extends SVNEntryInfo {

        public String Name;
        public String CommitTime;
        public boolean TextUpdated;
        public String Checksum;

        public SVNFileInfo(SVNDirectoryInfo parent, String path) {
            super(path);
            this.Parent = parent;
        }

        public SVNDirectory getDirectory() {
            return Parent.getDirectory();
        }
    }

    private class SVNDirectoryInfo extends SVNEntryInfo {

        public int RefCount;
        private int myLogCount;

        public SVNDirectoryInfo(String path) {
            super(path);
        }

        public SVNDirectory getDirectory() {
            return myWCAccess.getDirectory(getPath());
        }

        public SVNLog getLog(boolean increment) {
            SVNLog log = getDirectory().getLog(myLogCount);
            if (increment) {
                myLogCount++;
            }
            return log;
        }

        public void runLogs() throws SVNException {
            getDirectory().runLogs();
            myLogCount = 0;
        }
    }
}
