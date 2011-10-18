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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.diff.SVNDeltaProcessor;
import org.tmatesoft.svn.core.io.diff.SVNDiffWindow;
import org.tmatesoft.svn.core.wc.ISVNDiffGenerator;

/**
 * @version 1.0
 * @author TMate Software Ltd.
 */
public class SVNDiffEditor implements ISVNEditor {

    private SVNWCAccess myWCAccess;
    private ISVNDiffGenerator myDiffGenerator;
    private boolean myUseAncestry;
    private boolean myIsReverseDiff;
    private boolean myIsCompareToBase;
    private OutputStream myResult;
    private boolean myIsRootOpen;
    private long myTargetRevision;
    private SVNDirectoryInfo myCurrentDirectory;
    private SVNFileInfo myCurrentFile;
    private SVNDeltaProcessor myDeltaProcessor;

    public SVNDiffEditor(SVNWCAccess wcAccess, ISVNDiffGenerator diffGenerator,
            boolean useAncestry, boolean reverseDiff, boolean compareToBase, OutputStream result) {
        myWCAccess = wcAccess;
        myDiffGenerator = diffGenerator;
        myUseAncestry = useAncestry;
        myIsReverseDiff = reverseDiff;
        myResult = result;
        myIsCompareToBase = compareToBase;
        myDeltaProcessor = new SVNDeltaProcessor();
    }

    public void targetRevision(long revision) throws SVNException {
        myTargetRevision = revision;
    }

    public void openRoot(long revision) throws SVNException {
        myIsRootOpen = true;
        myCurrentDirectory = createDirInfo(null, "", false);
    }

    public void deleteEntry(String path, long revision) throws SVNException {
        SVNDirectory dir = myWCAccess.getDirectory(myCurrentDirectory.myPath);
        String name = SVNPathUtil.tail(path);
        SVNEntry entry = dir.getEntries().getEntry(name, true);
        String displayPath = dir.getFile(name).getAbsolutePath().replace(File.separatorChar, '/');
        if (entry != null && entry.isFile()) {
            SVNProperties baseProps = dir.getBaseProperties(name, false);
            SVNProperties wcProps = dir.getProperties(name, false);
            String baseMimeType = baseProps.getPropertyValue(SVNProperty.MIME_TYPE);
            String wcMimeType = wcProps.getPropertyValue(SVNProperty.MIME_TYPE);

            boolean deleted = entry.isScheduledForDeletion();
            if (myIsReverseDiff) {
                // deleted
                File baseFile = deleted ? null : dir.getBaseFile(name, false);
                String revStr = "(revision " + myTargetRevision + ")";
                myDiffGenerator.displayFileDiff(displayPath, baseFile, null,
                        revStr, null, baseMimeType, wcMimeType, myResult);
            } else {
                // added (compare agains wc file).
                File baseFile = deleted ? null : dir.getFile(name);
                File emptyFile = null;
                String revStr = "(revision " + entry.getRevision() + ")";
                myDiffGenerator.displayFileDiff(displayPath, emptyFile,
                        baseFile, "(revision 0)", revStr, wcMimeType,
                        baseMimeType, myResult);
            }
        } else if (entry != null && entry.isDirectory()) {
            SVNDirectoryInfo info = createDirInfo(myCurrentDirectory, path,
                    true);
            localDirectoryDiff(info, true, myResult);
        }
        myCurrentDirectory.myComparedEntries.add(name);
    }

    public void addDir(String path, String copyFromPath, long copyFromRevision)
            throws SVNException {
        myCurrentDirectory = createDirInfo(myCurrentDirectory, path, true);
    }

    public void openDir(String path, long revision) throws SVNException {
        myCurrentDirectory = createDirInfo(myCurrentDirectory, path, false);
    }

    public void changeDirProperty(String name, String value)
            throws SVNException {
        if (name.startsWith(SVNProperty.SVN_WC_PREFIX)
                || name.startsWith(SVNProperty.SVN_ENTRY_PREFIX)) {
            return;
        }
        if (myCurrentDirectory.myPropertyDiff == null) {
            myCurrentDirectory.myPropertyDiff = new HashMap();
        }
        myCurrentDirectory.myPropertyDiff.put(name, value);
        if (myCurrentDirectory.myBaseProperties == null) {
            SVNDirectory dir = myWCAccess
                    .getDirectory(myCurrentDirectory.myPath);
            if (dir != null) {
                myCurrentDirectory.myBaseProperties = dir.getBaseProperties("",
                        false).asMap();
            } else {
                myCurrentDirectory.myBaseProperties = new HashMap();
            }
        }
    }

    public void closeDir() throws SVNException {
        if (!myCurrentDirectory.myIsAdded) {
            localDirectoryDiff(myCurrentDirectory, false, myResult);
        }
        // display dir prop changes.
        Map diff = myCurrentDirectory.myPropertyDiff;
        Map base = myCurrentDirectory.myBaseProperties;
        if (diff != null && !diff.isEmpty()) {
            // reverse changes
            if (!myIsReverseDiff) {
                reversePropChanges(base, diff);
            }
            String displayPath = new File(myWCAccess.getAnchor().getRoot(), myCurrentDirectory.myPath).getAbsolutePath();
            displayPath = displayPath.replace(File.separatorChar, '/');
            myDiffGenerator.displayPropDiff(displayPath, base, diff, myResult);
        }
        String name = SVNPathUtil.tail(myCurrentDirectory.myPath);
        myCurrentDirectory = myCurrentDirectory.myParent;
        if (myCurrentDirectory != null) {
            myCurrentDirectory.myComparedEntries.add(name);
        }
    }

    public void addFile(String path, String copyFromPath, long copyFromRevision)
            throws SVNException {
        String name = SVNPathUtil.tail(path);
        myCurrentFile = createFileInfo(path, true);
        myCurrentDirectory.myComparedEntries.add(name);
    }

    public void openFile(String path, long revision) throws SVNException {
        String name = SVNPathUtil.tail(path);
        myCurrentFile = createFileInfo(path, false);
        myCurrentDirectory.myComparedEntries.add(name);
    }

    public void changeFileProperty(String path, String name, String value)
            throws SVNException {
        if (name.startsWith(SVNProperty.SVN_WC_PREFIX)
                || name.startsWith(SVNProperty.SVN_ENTRY_PREFIX)) {
            return;
        }
        if (myCurrentFile.myPropertyDiff == null) {
            myCurrentFile.myPropertyDiff = new HashMap();
        }
        myCurrentFile.myPropertyDiff.put(name, value);
        if (myCurrentFile.myBaseProperties == null) {
            SVNDirectory dir = myWCAccess
                    .getDirectory(myCurrentDirectory.myPath);
            String fileName = SVNPathUtil.tail(myCurrentFile.myPath);
            if (dir != null) {
                myCurrentFile.myBaseProperties = dir.getBaseProperties(
                        fileName, false).asMap();
            } else {
                myCurrentFile.myBaseProperties = new HashMap();
            }
        }
    }

    public void applyTextDelta(String path, String baseChecksum) throws SVNException {
        SVNDirectory dir = myWCAccess.getDirectory(myCurrentDirectory.myPath);
        String fileName = SVNPathUtil.tail(myCurrentFile.myPath);
        if (dir != null) {
            SVNEntry entry = dir.getEntries().getEntry(fileName, true);
            if (entry != null && entry.getCopyFromURL() != null) {
                myCurrentFile.myIsAdded = false;
            }
            if (entry != null && entry.isScheduledForDeletion()) {
                myCurrentFile.myIsScheduledForDeletion = true;
            }
        }
        File tmpFile = null;
        if (!myCurrentFile.myIsAdded) {
            tmpFile = dir.getBaseFile(fileName, true);
            myCurrentFile.myBaseFile = dir.getBaseFile(fileName, false);
        } else {
            // iterate till existing dir and get tmp file in it.
            SVNDirectoryInfo info = myCurrentDirectory.myParent;
            while (info != null) {
                SVNDirectory parentDir = myWCAccess.getDirectory(info.myPath);
                if (parentDir != null) {
                    tmpFile = SVNFileUtil.createUniqueFile(parentDir.getAdminFile("tmp/text-base"), fileName, ".tmp");
                    myCurrentFile.myBaseFile = parentDir.getAdminFile("empty-file");
                    if (myCurrentFile.myBaseFile.exists()) {
                        break;
                    }
                }
                info = info.myParent;
            }
        }
        // it will be repos file.
        myCurrentFile.myFile = tmpFile;
        myDeltaProcessor.applyTextDelta(myCurrentFile.myBaseFile, myCurrentFile.myFile, false);
    }

    public OutputStream textDeltaChunk(String path, SVNDiffWindow diffWindow) throws SVNException {
        return myDeltaProcessor.textDeltaChunk(diffWindow);
    }

    public void textDeltaEnd(String path) throws SVNException {
        myDeltaProcessor.textDeltaEnd();
    }

    public void closeFile(String commitPath, String textChecksum) throws SVNException {
        String reposMimeType = (String) (myCurrentFile.myPropertyDiff != null ?  myCurrentFile.myPropertyDiff.get(SVNProperty.MIME_TYPE) : null);
        String fileName = SVNPathUtil.tail(myCurrentFile.myPath);
        SVNDirectory dir = myWCAccess.getDirectory(myCurrentDirectory.myPath);
        if (reposMimeType == null) {
            if (myCurrentFile.myBaseProperties == null) {
                myCurrentFile.myBaseProperties = dir != null ? dir.getBaseProperties(fileName, false).asMap() : new HashMap();
            }
            reposMimeType = (String) myCurrentFile.myBaseProperties.get(SVNProperty.MIME_TYPE);
        }
        SVNEntry entry = null;
        if (dir != null) {
            entry = dir.getEntries().getEntry(fileName, true);
        }
        String displayPath = new File(myWCAccess.getAnchor().getRoot(), myCurrentFile.myPath).getAbsolutePath().replace(File.separatorChar, '/');
        if (myCurrentFile.myIsAdded) {
            if (myIsReverseDiff) {
                // empty->repos
                String revStr = entry != null ? "(revision " + entry.getRevision() + ")" : null;
                myDiffGenerator.displayFileDiff(displayPath,
                        myCurrentFile.myBaseFile, myCurrentFile.myFile,
                        "(revision 0)", revStr, null, reposMimeType, myResult);
            } else {
                // repos->empty
                String revStr = "(revision " + myTargetRevision + ")";
                myDiffGenerator.displayFileDiff(displayPath,
                        myCurrentFile.myFile, null, revStr, null,
                        reposMimeType, null, myResult);
            }
        } else {
            if (myCurrentFile.myFile != null) {
                String wcMimeType = dir.getProperties(fileName, false).getPropertyValue(SVNProperty.MIME_TYPE);
                if (!myIsCompareToBase && myCurrentFile.myIsScheduledForDeletion) {
                    myCurrentFile.myBaseFile = null;
                } else if (!myIsCompareToBase) {
                    File wcTmpFile = SVNFileUtil.createUniqueFile(myCurrentFile.myFile.getParentFile(), fileName,  ".tmp");
                    String path = SVNFileUtil.getBasePath(wcTmpFile);
                    SVNTranslator.translate(dir, fileName, fileName, path, true, false);
                    myCurrentFile.myBaseFile = wcTmpFile;
                }
                String revStr = "(revision " + myTargetRevision + ")";
                if (myIsReverseDiff) {
                    myDiffGenerator.displayFileDiff(displayPath,
                            myCurrentFile.myBaseFile, myCurrentFile.myFile,
                            null, revStr, wcMimeType, reposMimeType, myResult);
                } else {
                    myDiffGenerator.displayFileDiff(displayPath,
                            myCurrentFile.myFile, myCurrentFile.myBaseFile,
                            revStr, null, reposMimeType, wcMimeType, myResult);
                }
                if (myCurrentFile.myBaseFile != null
                        && !myCurrentFile.myIsScheduledForDeletion
                        && !myIsCompareToBase
                        && !fileName.equals(SVNFileUtil.getBasePath(myCurrentFile.myBaseFile))) {
                    myCurrentFile.myBaseFile.delete();
                }
                myCurrentFile.myFile.delete();
            }
            if (myCurrentFile.myPropertyDiff != null  && !myCurrentFile.myPropertyDiff.isEmpty()) {
                Map base = myCurrentFile.myBaseProperties;
                Map diff = myCurrentFile.myPropertyDiff;
                if (!myIsReverseDiff) {
                    reversePropChanges(base, diff);
                }
                myDiffGenerator.displayPropDiff(displayPath, base, diff, myResult);
            }
        }
        if (myCurrentFile.myFile != null) {
            myCurrentFile.myFile.delete();
        }
    }

    public SVNCommitInfo closeEdit() throws SVNException {
        if (!myIsRootOpen) {
            localDirectoryDiff(createDirInfo(null, "", false), false, myResult);
        }
        return null;
    }

    public void abortEdit() throws SVNException {
    }

    public void absentDir(String path) throws SVNException {
    }

    public void absentFile(String path) throws SVNException {
    }

    private void localDirectoryDiff(SVNDirectoryInfo info, boolean isAdded,  OutputStream result) throws SVNException {
        if (myIsCompareToBase) {
            return;
        }
        SVNDirectory dir = myWCAccess.getDirectory(info.myPath);
        boolean anchor = !"".equals(myWCAccess.getTargetName())
                && dir == myWCAccess.getAnchor();

        if (!anchor) {
            // generate prop diff for dir.
            if (dir.hasPropModifications("")) {
                SVNProperties baseProps = dir.getBaseProperties("", false);
                Map propDiff = baseProps
                        .compareTo(dir.getProperties("", false));
                String displayPath = dir.getRoot().getAbsolutePath().replace(File.separatorChar, '/');
                myDiffGenerator.displayPropDiff(displayPath, baseProps.asMap(),  propDiff, result);
            }
        }
        Set processedFiles = null;
        if (myDiffGenerator.isDiffUnversioned()) {
            processedFiles = new HashSet();
        }
        SVNEntries svnEntries = dir.getEntries();
        for (Iterator entries = svnEntries.entries(false); entries.hasNext();) {
            SVNEntry entry = (SVNEntry) entries.next();
            if (processedFiles != null && !"".equals(entry.getName())) {
                processedFiles.add(entry.getName());
            }
            if (anchor && !myWCAccess.getTargetName().equals(entry.getName())) {
                continue;
            }
            if ("".equals(entry.getName())) {
                continue;
            }
            if (info.myComparedEntries.contains(entry.getName())) {
                continue;
            }
            info.myComparedEntries.add(entry.getName());
            if (entry.isDirectory()) {
                // recurse here.
                SVNDirectoryInfo childInfo = createDirInfo(info, SVNPathUtil
                        .append(info.myPath, entry.getName()), false);
                SVNDirectory childDir = myWCAccess
                        .getDirectory(childInfo.myPath);
                if (childDir != null) {
                    localDirectoryDiff(childInfo, isAdded, myResult);
                }
                continue;
            }
            String name = entry.getName();
            boolean added = entry.isScheduledForAddition() || isAdded;
            boolean replaced = entry.isScheduledForReplacement();
            boolean deleted = entry.isScheduledForDeletion();
            boolean copied = entry.isCopied();
            if (copied) {
                added = false;
                deleted = false;
                replaced = false;
            }
            if (replaced && !myUseAncestry) {
                replaced = false;
            }
            SVNProperties props = dir.getProperties(name, false);
            String fullPath = dir.getFile(name).getAbsolutePath().replace(File.separatorChar, '/');
            Map baseProps = dir.getBaseProperties(name, false).asMap();
            Map propDiff = null;
            if (!deleted && dir.hasPropModifications(name)) {
                propDiff = dir.getBaseProperties(name, false).compareTo(
                        dir.getProperties(name, false));
            }
            if (deleted || replaced) {
                // display text diff for deleted file.
                String mimeType1 = (String) baseProps.get(SVNProperty.MIME_TYPE);
                String rev1 = "(revision " + Long.toString(entry.getRevision()) + ")";
                myDiffGenerator.displayFileDiff(fullPath, dir.getBaseFile(name,
                        false), null, rev1, null, mimeType1, null, result);
                if (deleted) {
                    continue;
                }
            }

            File tmpFile = null;
            try {
                if (added || replaced) {
                    tmpFile = dir.getBaseFile(name, true);
                    SVNTranslator.translate(dir, name, name, SVNFileUtil.getBasePath(tmpFile), false, false);
                    // display text diff for added file.

                    String mimeType1 = null;
                    String mimeType2 = props.getPropertyValue(SVNProperty.MIME_TYPE);
                    String rev2 = "(revision " + Long.toString(entry.getRevision()) + ")";
                    String rev1 = "(revision 0)";

                    myDiffGenerator.displayFileDiff(fullPath, null, tmpFile,
                            rev1, rev2, mimeType1, mimeType2, result);
                    if (propDiff != null && propDiff.size() > 0) {
                        // display prop diff.
                        myDiffGenerator.displayPropDiff(fullPath, baseProps, propDiff, result);
                    }
                    continue;
                }
                boolean isTextModified = dir.hasTextModifications(name, false);
                if (isTextModified) {
                    tmpFile = dir.getBaseFile(name, true);
                    SVNTranslator.translate(dir, name, name, SVNFileUtil.getBasePath(tmpFile), false, false);

                    String mimeType1 = (String) baseProps.get(SVNProperty.MIME_TYPE);
                    String mimeType2 = props.getPropertyValue(SVNProperty.MIME_TYPE);
                    String rev1 = "(revision " + Long.toString(entry.getRevision()) + ")";
                    myDiffGenerator.displayFileDiff(fullPath, dir.getBaseFile(
                            name, false), tmpFile, rev1, null, mimeType1,
                            mimeType2, result);
                }
	            if (propDiff != null && propDiff.size() > 0) {
	                myDiffGenerator.displayPropDiff(fullPath, baseProps,
	                        propDiff, result);
	            }
            } finally {
                if (tmpFile != null) {
                    tmpFile.delete();
                }
            }
        }
        if (myDiffGenerator.isDiffUnversioned()) {
            diffUnversioned(result, dir.getRoot(), dir, anchor, processedFiles);
        }
    }

    private void diffUnversioned(OutputStream result, File root, SVNDirectory dir, boolean anchor, Set processedFiles) throws SVNException {
        File[] allFiles = root.listFiles();
        for (int i = 0; allFiles != null && i < allFiles.length; i++) {
            File file = allFiles[i];
            if (SVNFileUtil.getAdminDirectoryName().equals(file.getName())) {
                continue;
            }
            if (processedFiles != null && processedFiles.contains(file.getName())) {
                continue;
            }
            if (anchor && !myWCAccess.getTargetName().equals(file.getName())) {
                continue;
            } else if (dir != null && dir.isIgnored(file.getName())) {
                continue;
            }
            // generate patch as for added file.
            SVNFileType fileType = SVNFileType.getType(file);
            if (fileType == SVNFileType.DIRECTORY) {
                diffUnversioned(result, file, null, false, null);
            } else if (fileType == SVNFileType.FILE) {
                String mimeType1 = null;
                String mimeType2 = SVNFileUtil.detectMimeType(file);
                String rev1 = "";
                String rev2 = "";

                String fullPath = file.getAbsolutePath().replace(File.separatorChar, '/');
                myDiffGenerator.displayFileDiff(fullPath, null, file, rev1, rev2, mimeType1, mimeType2, result);
            }
        }
    }

    private SVNDirectoryInfo createDirInfo(SVNDirectoryInfo parent,
            String path, boolean added) {
        SVNDirectoryInfo info = new SVNDirectoryInfo();
        info.myParent = parent;
        info.myPath = path;
        info.myIsAdded = added;
        return info;
    }

    private SVNFileInfo createFileInfo(String path, boolean added) {
        SVNFileInfo info = new SVNFileInfo();
        info.myPath = path;
        info.myIsAdded = added;
        return info;
    }

    private static class SVNDirectoryInfo {

        private boolean myIsAdded;

        private String myPath;

        private Map myBaseProperties;

        private Map myPropertyDiff;

        private SVNDirectoryInfo myParent;

        private Set myComparedEntries = new HashSet();
    }

    private static class SVNFileInfo {

        private boolean myIsAdded;
        private String myPath;
        private File myFile;
        private File myBaseFile;
        private Map myBaseProperties;
        private Map myPropertyDiff;
        private boolean myIsScheduledForDeletion;
    }

    private static void reversePropChanges(Map base, Map diff) {
        Collection namesList = new ArrayList(diff.keySet());
        for (Iterator names = namesList.iterator(); names.hasNext();) {
            String name = (String) names.next();
            String newValue = (String) diff.get(name);
            String oldValue = (String) base.get(name);
            if (oldValue == null && newValue != null) {
                base.put(name, newValue);
                diff.put(name, null);
            } else if (oldValue != null && newValue == null) {
                base.put(name, null);
                diff.put(name, oldValue);
            } else if (oldValue != null && newValue != null) {
                base.put(name, newValue);
                diff.put(name, oldValue);
            }
        }
    }
}