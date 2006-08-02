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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.TreeMap;
import java.util.TreeSet;

import org.tmatesoft.svn.core.SVNCancelException;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.util.SVNEncodingUtil;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.wc.ISVNCommitParameters;
import org.tmatesoft.svn.core.wc.ISVNEventHandler;
import org.tmatesoft.svn.core.wc.SVNCommitItem;
import org.tmatesoft.svn.core.wc.SVNEvent;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNStatus;
import org.tmatesoft.svn.core.wc.SVNStatusClient;
import org.tmatesoft.svn.core.wc.SVNStatusType;
import org.tmatesoft.svn.core.wc.SVNWCUtil;

/**
 * @version 1.0
 * @author TMate Software Ltd.
 */
public class SVNCommitUtil {

    public static void driveCommitEditor(ISVNCommitPathHandler handler, Collection paths, ISVNEditor editor, long revision) throws SVNException {
        if (paths == null || paths.isEmpty() || handler == null || editor == null) {
            return;
        }
        String[] pathsArray = (String[]) paths.toArray(new String[paths.size()]);
        Arrays.sort(pathsArray, SVNPathUtil.PATH_COMPARATOR);
        int index = 0;
        String lastPath = null;
        if ("".equals(pathsArray[index])) {
            handler.handleCommitPath("", editor);
            lastPath = pathsArray[index];
            index++;
        } else {
            editor.openRoot(revision);
        }
        for (; index < pathsArray.length; index++) {
            String commitPath = pathsArray[index];
            String commonAncestor = lastPath == null || "".equals(lastPath) ? 
                    "" : SVNPathUtil.getCommonPathAncestor(commitPath, lastPath);
            if (lastPath != null) {
                while (!lastPath.equals(commonAncestor)) {
                    editor.closeDir();
                    if (lastPath.lastIndexOf('/') >= 0) {
                        lastPath = lastPath.substring(0, lastPath.lastIndexOf('/'));
                    } else {
                        lastPath = "";
                    }
                }
            }
            String relativeCommitPath = commitPath.substring(commonAncestor.length());
            if (relativeCommitPath.startsWith("/")) {
                relativeCommitPath = relativeCommitPath.substring(1);
            }

            for (StringTokenizer tokens = new StringTokenizer(
                    relativeCommitPath, "/"); tokens.hasMoreTokens();) {
                String token = tokens.nextToken();
                commonAncestor = "".equals(commonAncestor) ? token : commonAncestor + "/" + token;
                if (!commonAncestor.equals(commitPath)) {
                    editor.openDir(commonAncestor, revision);
                } else {
                    break;
                }
            }
            boolean closeDir = handler.handleCommitPath(commitPath, editor);
            if (closeDir) {
                lastPath = commitPath;
            } else {
                lastPath = SVNPathUtil.removeTail(commitPath);
            }
        }
        while (lastPath != null && !"".equals(lastPath)) {
            editor.closeDir();
            lastPath = lastPath.lastIndexOf('/') >= 0 ? lastPath.substring(0, lastPath.lastIndexOf('/')) : "";
        }
    }

    public static SVNWCAccess createCommitWCAccess(File[] paths, boolean recursive, boolean force, Collection relativePaths, final SVNStatusClient statusClient) throws SVNException {
        File wcRoot = null;
        Map localRootsCache = new HashMap();
        for (int i = 0; i < paths.length; i++) {
            statusClient.checkCancelled();
            File path = paths[i];           
            File rootPath = path;
            if (rootPath.isFile()) {
                rootPath = rootPath.getParentFile();
            }
            File newWCRoot = localRootsCache.containsKey(rootPath) ? (File) localRootsCache.get(rootPath) : SVNWCUtil.getWorkingCopyRoot(rootPath, true);
            localRootsCache.put(rootPath, newWCRoot);
            if (wcRoot != null && !wcRoot.equals(newWCRoot)) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNSUPPORTED_FEATURE, "Commit targets should belong to the same working copy");
                SVNErrorManager.error(err);
            }
            wcRoot = newWCRoot;
        }
        String[] validatedPaths = new String[paths.length];
        for (int i = 0; i < paths.length; i++) {
            statusClient.checkCancelled();
            File file = paths[i];
            validatedPaths[i] = SVNPathUtil.validateFilePath(file.getAbsolutePath());
        }

        String rootPath = SVNPathUtil.condencePaths(validatedPaths, relativePaths, recursive);
        if (rootPath == null) {
            return null;
        }
        File baseDir = new File(rootPath);
        Collection dirsToLock = new TreeSet(); // relative paths to lock.
        Collection dirsToLockRecursively = new TreeSet(); 
        boolean lockAll = false;
        if (relativePaths.isEmpty()) {
            statusClient.checkCancelled();
            String target = getTargetName(baseDir);
            if (!"".equals(target)) {
                // we will have to lock target as well, not only base dir.
                SVNFileType targetType = SVNFileType.getType(new File(rootPath));
                relativePaths.add(target);
                if (targetType == SVNFileType.DIRECTORY) {
                    // lock recursively if forced and copied...
                    if (recursive || (force && isRecursiveCommitForced(baseDir))) {
                        // dir is copied, include children
                        dirsToLockRecursively.add(target);
                    } else {
                        dirsToLock.add(target);
                    }  
                }
                baseDir = baseDir.getParentFile();
            } else {
                lockAll = true;
            }
        } else {
            baseDir = adjustRelativePaths(baseDir, relativePaths);
            // there are multiple paths.
            for (Iterator targets = relativePaths.iterator(); targets.hasNext();) {
                statusClient.checkCancelled();
                String targetPath = (String) targets.next();
                File targetFile = new File(baseDir, targetPath);
                String target = getTargetName(targetFile);
                if (!"".equals(target)) {
                    SVNFileType targetType = SVNFileType.getType(targetFile);
                    if (targetType == SVNFileType.DIRECTORY) {
                        if (recursive || (force && isRecursiveCommitForced(targetFile))) {
                            dirsToLockRecursively.add(targetPath);
                        } else {
                            dirsToLock.add(targetPath);
                        }
                    }
                }
                // now lock all dirs from anchor to base dir (non-recursive).
                targetFile = targetFile.getParentFile();
                targetPath = SVNPathUtil.removeTail(targetPath);
                while (targetFile != null && !baseDir.equals(targetFile) && !"".equals(targetPath) && !dirsToLock.contains(targetPath)) {
                    dirsToLock.add(targetPath);
                    targetFile = targetFile.getParentFile();
                    targetPath = SVNPathUtil.removeTail(targetPath);
                }
            }
        }
        SVNDirectory anchor = new SVNDirectory(null, "", baseDir);
        SVNWCAccess baseAccess = new SVNWCAccess(anchor, anchor, "");
        baseAccess.setEventDispatcher(new ISVNEventHandler() {
            public void handleEvent(SVNEvent event, double progress) throws SVNException {
            }
            public void checkCancelled() throws SVNCancelException {
                statusClient.checkCancelled();
            }
        });
        if (!recursive && !force) {
            for (Iterator targets = relativePaths.iterator(); targets.hasNext();) {
                statusClient.checkCancelled();
                String targetPath = (String) targets.next();
                File targetFile = new File(baseDir, targetPath);
                if (SVNFileType.getType(targetFile) == SVNFileType.DIRECTORY) {
                    SVNStatus status = statusClient.doStatus(targetFile, false);
                    if (status != null && (status.getContentsStatus() == SVNStatusType.STATUS_DELETED || status.getContentsStatus() == SVNStatusType.STATUS_REPLACED)) {
                        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNSUPPORTED_FEATURE, "Cannot non-recursively commit a directory deletion");
                        SVNErrorManager.error(err);
                    }
                }
            }
        }
        try {
            statusClient.checkCancelled();
            if (lockAll) {
                baseAccess.open(true, recursive);
            } else {
                baseAccess.open(true, false);
                removeRedundantPaths(dirsToLockRecursively, dirsToLock);
                for (Iterator nonRecusivePaths = dirsToLock.iterator(); nonRecusivePaths.hasNext();) {
                    statusClient.checkCancelled();
                    String path = (String) nonRecusivePaths.next();
                    File pathFile = new File(baseDir, path);
                    baseAccess.addDirectory(path, pathFile, false, true, true);
                }
                for (Iterator recusivePaths = dirsToLockRecursively.iterator(); recusivePaths.hasNext();) {
                    statusClient.checkCancelled();
                    String path = (String) recusivePaths.next();
                    File pathFile = new File(baseDir, path);
                    baseAccess.addDirectory(path, pathFile, true, true, true);
                }
            }
            // if commit is non-recursive and forced, remove those child dirs 
            // that were not explicitly added but are explicitly copied. ufff.
            if (!recursive && force) {
                SVNDirectory[] lockedDirs = baseAccess.getAllDirectories();
                for (int i = 0; i < lockedDirs.length; i++) {
                    statusClient.checkCancelled();
                    SVNDirectory dir = lockedDirs[i];
                    SVNEntry rootEntry = dir.getEntries().getEntry("", true);
                    if (rootEntry.getCopyFromURL() != null) {
                        File dirRoot = dir.getRoot();
                        boolean keep = false;
                        for (int j = 0; j < paths.length; j++) {
                            if (dirRoot.equals(paths[j])) {
                                keep = true;
                                break;
                            }
                        }
                        if (!keep) {
                            baseAccess.removeDirectory(dir.getPath(), true);
                        }
                    }
                }
            }
        } catch (SVNException e) {
            baseAccess.close(true);
            throw e;
        }

        return baseAccess;
    }

    public static SVNWCAccess[] createCommitWCAccess2(File[] paths, boolean recursive, boolean force, Map relativePathsMap, SVNStatusClient statusClient) throws SVNException {
        Map rootsMap = new HashMap(); // wc root file -> paths to be committed (paths).
        Map localRootsCache = new HashMap();
        for (int i = 0; i < paths.length; i++) {
            statusClient.checkCancelled();
            File path = paths[i];
            File rootPath = path;
            if (rootPath.isFile()) {
                rootPath = rootPath.getParentFile();
            }
            File wcRoot = localRootsCache.containsKey(rootPath) ? (File) localRootsCache.get(rootPath) : SVNWCUtil.getWorkingCopyRoot(rootPath, true);
            localRootsCache.put(path, wcRoot);
            if (!rootsMap.containsKey(wcRoot)) {
                rootsMap.put(wcRoot, new ArrayList());
            }
            Collection wcPaths = (Collection) rootsMap.get(wcRoot);
            wcPaths.add(path);
        }
        Collection result = new ArrayList();
        try {
            for (Iterator roots = rootsMap.keySet().iterator(); roots.hasNext();) {
                statusClient.checkCancelled();
                File root = (File) roots.next();
                Collection filesList = (Collection) rootsMap.get(root);
                File[] filesArray = (File[]) filesList.toArray(new File[filesList.size()]);
                Collection relativePaths = new TreeSet();
                SVNWCAccess wcAccess = createCommitWCAccess(filesArray, recursive, force, relativePaths, statusClient);
                relativePathsMap.put(wcAccess, relativePaths);
                result.add(wcAccess);
            }
        } catch (SVNException e) {
            for (Iterator wcAccesses = result.iterator(); wcAccesses.hasNext();) {
                SVNWCAccess wcAccess = (SVNWCAccess) wcAccesses.next();
                wcAccess.close(true);
            }
            throw e;
        }
        return (SVNWCAccess[]) result.toArray(new SVNWCAccess[result.size()]);
    }

    public static SVNCommitItem[] harvestCommitables(SVNWCAccess baseAccess,
            Collection paths, Map lockTokens, boolean justLocked,
            boolean recursive, boolean force, ISVNCommitParameters params) throws SVNException {
        Map commitables = new TreeMap();
        Collection danglers = new HashSet();
        Iterator targets = paths.iterator();
        
        boolean isRecursionForced = false;

        do {
            baseAccess.checkCancelled();
            String target = targets.hasNext() ? (String) targets.next() : "";
            // get entry for target
            File targetFile = new File(baseAccess.getAnchor().getRoot(), target);
            String targetName = "".equals(target) ? "" : SVNPathUtil.tail(target);
            String parentPath = SVNPathUtil.removeTail(target);
            SVNDirectory dir = baseAccess.getDirectory(parentPath);
            SVNEntry entry = dir == null ? null : dir.getEntries().getEntry(
                    targetName, false);
            String url = null;
            if (entry == null) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_NOT_FOUND, "''{0}'' is not under version control", targetFile);
                SVNErrorManager.error(err);
            } else if (entry.getURL() == null) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_MISSING_URL, "''{0}'' has no URL", targetFile);
                SVNErrorManager.error(err);
            } else {
                url = entry.getURL();
            }
            if (entry != null
                    && (entry.isScheduledForAddition() || entry
                            .isScheduledForReplacement())) {
                // get parent (for file or dir-> get ""), otherwise open parent
                // dir and get "".
                SVNEntry parentEntry;
                if (!"".equals(targetName)) {
                    parentEntry = dir.getEntries().getEntry("", false);
                } else {
                    File parentFile = targetFile.getParentFile();
                    SVNWCAccess parentAccess = SVNWCAccess.create(parentFile);
                    parentEntry = parentAccess.getTarget().getEntries()
                            .getEntry("", false);
                }
                if (parentEntry == null) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_CORRUPT, 
                            "''{0}'' is scheduled for addition within unversioned parent", targetFile);
                    SVNErrorManager.error(err);
                } else if (parentEntry.isScheduledForAddition()
                        || parentEntry.isScheduledForReplacement()) {
                    danglers.add(targetFile.getParentFile());
                }
            }
            boolean recurse = recursive;
            if (entry != null && entry.isCopied() && entry.getSchedule() == null) {
                // if commit is forced => we could collect this entry, assuming
                // that its parent is already included into commit
                // it will be later removed from commit anyway.
                if (!force) {
                    SVNErrorMessage err =  SVNErrorMessage.create(SVNErrorCode.ILLEGAL_TARGET, 
                            "Entry for ''{0}''"
                                    + " is marked as 'copied' but is not itself scheduled\n"
                                    + "for addition.  Perhaps you're committing a target that is\n"
                                    + "inside an unversioned (or not-yet-versioned) directory?", targetFile);
                    SVNErrorManager.error(err);
                } else {
                    // just do not process this item as in case of recursive
                    // commit.
                    continue;
                }
            } else if (entry != null && entry.isCopied() && entry.isScheduledForAddition()) {
                if (force) {
                    isRecursionForced = !recursive;
                    recurse = true;
                }
            } else if (entry != null && entry.isScheduledForDeletion()) {
                if (force && !recursive) {
                    // if parent is also deleted -> skip this entry
                    SVNEntry parentEntry;
                    if (!"".equals(targetName)) {
                        parentEntry = dir.getEntries().getEntry("", false);
                    } else {
                        File parentFile = targetFile.getParentFile();
                        SVNWCAccess parentAccess = SVNWCAccess.create(parentFile);
                        parentEntry = parentAccess.getTarget().getEntries()
                                .getEntry("", false);
                    }
                    if (parentEntry != null && parentEntry.isScheduledForDeletion() &&
                            paths.contains(parentPath)) {
                        continue;
                    }
                    // this recursion is not considered as "forced", all children should be 
                    // deleted anyway.
                    recurse = true;
                }
            }
            harvestCommitables(commitables, dir, targetFile, null, entry, url, null, false, false, justLocked, lockTokens, recurse, isRecursionForced, params);
        } while (targets.hasNext());

        for (Iterator ds = danglers.iterator(); ds.hasNext();) {
            baseAccess.checkCancelled();
            File file = (File) ds.next();
            if (!commitables.containsKey(file)) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ILLEGAL_TARGET, 
                        "''{0}'' is not under version control\n"
                        + "and is not part of the commit, \n"
                        + "yet its child is part of the commit", file);
                SVNErrorManager.error(err);
            }
        }
        if (isRecursionForced) {
            // if commit is non-recursive and forced and there are elements included into commit 
            // that not only 'copied' but also has local mods (modified or deleted), remove those items?
            // or not?
            for (Iterator items = commitables.values().iterator(); items.hasNext();) {
                baseAccess.checkCancelled();
                SVNCommitItem item = (SVNCommitItem) items.next();
                if (item.isDeleted()) {
                    // to detect deleted copied items.
                    File file = item.getFile();
                    if (item.getKind() == SVNNodeKind.DIR) {
                        if (!file.exists()) {
                            continue;
                        } 
                    } else {
                        String path = SVNPathUtil.removeTail(item.getPath());
                        String name = SVNPathUtil.tail(item.getPath());
                        SVNDirectory dir = baseAccess.getDirectory(path);
                        if (!dir.getBaseFile(name, false).exists()) {
                            continue;
                        }
                    }
                }
                if (item.isContentsModified() || item.isDeleted() || item.isPropertiesModified()) {
                    // if item was not explicitly included into commit, then just make it 'added'
                    // but do not remove that are marked as 'deleted'
                    String itemPath = item.getPath();
                    if (!paths.contains(itemPath)) {
                        items.remove(); 
                    }
                }
            }
        }
        return (SVNCommitItem[]) commitables.values().toArray(new SVNCommitItem[commitables.values().size()]);
    }

    public static String translateCommitables(SVNCommitItem[] items,
            Map decodedPaths) throws SVNException {
        Map itemsMap = new TreeMap();
        for (int i = 0; i < items.length; i++) {
            SVNCommitItem item = items[i];
            if (itemsMap.containsKey(item.getURL().toString())) {
                SVNCommitItem oldItem = (SVNCommitItem) itemsMap.get(item.getURL().toString());
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_DUPLICATE_COMMIT_URL, 
                        "Cannot commit both ''{0}'' and ''{1}'' as they refer to the same URL",
                        new Object[] {item.getFile(), oldItem.getFile()});
                SVNErrorManager.error(err);
            }
            itemsMap.put(item.getURL().toString(), item);
        }

        Iterator urls = itemsMap.keySet().iterator();
        String baseURL = (String) urls.next();
        while (urls.hasNext()) {
            String url = (String) urls.next();
            baseURL = SVNPathUtil.getCommonURLAncestor(baseURL, url);
        }
        if (itemsMap.containsKey(baseURL)) {
            SVNCommitItem root = (SVNCommitItem) itemsMap.get(baseURL);
            if (root.getKind() != SVNNodeKind.DIR) {
                baseURL = SVNPathUtil.removeTail(baseURL);
            } else if (root.getKind() == SVNNodeKind.DIR
                    && (root.isAdded() || root.isDeleted() || root.isCopied() || root
                            .isLocked())) {
                baseURL = SVNPathUtil.removeTail(baseURL);
            }
        }
        urls = itemsMap.keySet().iterator();
        while (urls.hasNext()) {
            String url = (String) urls.next();
            SVNCommitItem item = (SVNCommitItem) itemsMap.get(url);
            String realPath = url.equals(baseURL) ? "" : url.substring(baseURL.length() + 1);
            decodedPaths.put(SVNEncodingUtil.uriDecode(realPath), item);
        }
        return baseURL;
    }

    public static Map translateLockTokens(Map lockTokens, String baseURL) {
        Map translatedLocks = new TreeMap();
        for (Iterator urls = lockTokens.keySet().iterator(); urls.hasNext();) {
            String url = (String) urls.next();
            String token = (String) lockTokens.get(url);
            url = url.equals(baseURL) ? "" : url.substring(baseURL.length() + 1);
            translatedLocks.put(SVNEncodingUtil.uriDecode(url), token);
        }
        lockTokens.clear();
        lockTokens.putAll(translatedLocks);
        return lockTokens;
    }

    public static void harvestCommitables(Map commitables, SVNDirectory dir,
            File path, SVNEntry parentEntry, SVNEntry entry, String url,
            String copyFromURL, boolean copyMode, boolean addsOnly,
            boolean justLocked, Map lockTokens, boolean recursive, boolean forcedRecursion, ISVNCommitParameters params)
            throws SVNException {
        if (commitables.containsKey(path)) {
            return;
        }
        if (dir != null && dir.getWCAccess() != null) {
            dir.getWCAccess().checkCancelled();
        }
        long cfRevision = entry.getCopyFromRevision();
        String cfURL = null;
        if (entry.getKind() != SVNNodeKind.DIR
                && entry.getKind() != SVNNodeKind.FILE) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.NODE_UNKNOWN_KIND, "Unknown entry kind for ''{0}''", path);                    
            SVNErrorManager.error(err);
        }
        SVNFileType fileType = SVNFileType.getType(path);
        if (fileType == SVNFileType.UNKNOWN) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.NODE_UNKNOWN_KIND, "Unknown entry kind for ''{0}''", path);                    
            SVNErrorManager.error(err);
        }
        String specialPropertyValue = dir.getProperties(entry.getName(), false).getPropertyValue(SVNProperty.SPECIAL);
        boolean specialFile = fileType == SVNFileType.SYMLINK;
        if (SVNFileType.isSymlinkSupportEnabled()) {
            if (((specialPropertyValue == null && specialFile) || (!SVNFileUtil.isWindows && specialPropertyValue != null && !specialFile)) 
                    && fileType != SVNFileType.NONE) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.NODE_UNEXPECTED_KIND, "Entry ''{0}'' has unexpectedly changed special status", path);                    
                SVNErrorManager.error(err);
            }
        }
        boolean propConflicts;
        boolean textConflicts = false;
        SVNEntries entries = null;
        if (entry.getKind() == SVNNodeKind.DIR) {
            SVNDirectory childDir = dir.getChildDirectory(entry.getName());
            if (childDir != null && childDir.getEntries() != null) {
                entries = childDir.getEntries();
                if (entries.getEntry("", false) != null) {
                    entry = entries.getEntry("", false);
                    dir = childDir;
                }
                propConflicts = entry.getPropRejectFile() != null;
            } else {
                propConflicts = entry.getPropRejectFile() != null;
            }
        } else {
            propConflicts = entry.getPropRejectFile() != null;
            textConflicts = entry.getConflictOld() != null
                    || entry.getConflictNew() != null
                    || entry.getConflictWorking() != null;
        }
        if (propConflicts || textConflicts) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_FOUND_CONFLICT, "Aborting commit: ''{0}'' remains in conflict", path);                    
            SVNErrorManager.error(err);
        }
        if (entry.getURL() != null && !copyMode) {
            url = entry.getURL();
        }
        boolean commitDeletion = !addsOnly
                && ((entry.isDeleted() && entry.getSchedule() == null) || entry.isScheduledForDeletion() || entry.isScheduledForReplacement());
        if (!addsOnly && !commitDeletion && fileType == SVNFileType.NONE && params != null) {
            ISVNCommitParameters.Action action = 
                entry.getKind() == SVNNodeKind.DIR ? params.onMissingDirectory(path) : params.onMissingFile(path);
            if (action == ISVNCommitParameters.ERROR) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_NOT_LOCKED, "Working copy file ''{0}'' is missing", path);
                SVNErrorManager.error(err);
            } else if (action == ISVNCommitParameters.DELETE) {
                commitDeletion = true;
                entry.scheduleForDeletion();
                dir.getEntries().save(false);
            }
        }
        boolean commitAddition = false;
        boolean commitCopy = false;
        if (entry.isScheduledForAddition() || entry.isScheduledForReplacement()) {
            commitAddition = true;
            if (entry.getCopyFromURL() != null) {
                cfURL = entry.getCopyFromURL();
                addsOnly = false;
                commitCopy = true;
            } else {
                addsOnly = true;
            }
        }
        if ((entry.isCopied() || copyMode) && !entry.isDeleted() && entry.getSchedule() == null) {
            long parentRevision = entry.getRevision() - 1;
            if (!SVNWCUtil.isWorkingCopyRoot(path, true)) {
                if (parentEntry != null) {
                    parentRevision = parentEntry.getRevision();
                }

            } else if (!copyMode) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_CORRUPT, "Did not expect ''{0}'' to be a working copy root", path);                    
                SVNErrorManager.error(err);
            }
            if (parentRevision != entry.getRevision()) {
                commitAddition = true;
                commitCopy = true;
                addsOnly = false;
                cfRevision = entry.getRevision();
                if (copyMode) {
                    cfURL = entry.getURL();
                } else if (copyFromURL != null) {
                    cfURL = copyFromURL;
                } else {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.BAD_URL, "Commit item ''{0}'' has copy flag but no copyfrom URL", path);                    
                    SVNErrorManager.error(err);
                }
            }
        }
        boolean textModified = false;
        boolean propsModified = false;
        boolean commitLock;

        if (commitAddition) {
            SVNProperties props = dir.getProperties(entry.getName(), false);
            SVNProperties baseProps = dir.getBaseProperties(entry.getName(), false);            
            Map propDiff = null;
            if (entry.isScheduledForReplacement()) {
                propDiff = props.asMap();
            } else {
                propDiff = baseProps.compareTo(props);
            }
            boolean eolChanged = textModified = propDiff != null && propDiff.containsKey(SVNProperty.EOL_STYLE);
            if (entry.getKind() == SVNNodeKind.FILE) {
                if (commitCopy) {
                    textModified = propDiff != null && propDiff.containsKey(SVNProperty.EOL_STYLE);
                    if (!textModified) {
                        textModified = dir.hasTextModifications(entry.getName(), eolChanged);
                    }
                } else {
                    textModified = true;
                }
            }
            propsModified = propDiff != null && !propDiff.isEmpty();
        } else if (!commitDeletion) {
            SVNProperties props = dir.getProperties(entry.getName(), false);
            SVNProperties baseProps = dir.getBaseProperties(entry.getName(),
                    false);
            Map propDiff = baseProps.compareTo(props);
            boolean eolChanged = textModified = propDiff != null
                    && propDiff.containsKey(SVNProperty.EOL_STYLE);
            propsModified = propDiff != null && !propDiff.isEmpty();
            if (entry.getKind() == SVNNodeKind.FILE) {
                textModified = dir.hasTextModifications(entry.getName(),  eolChanged);
            }
        }

        commitLock = entry.getLockToken() != null
                && (justLocked || textModified || propsModified
                        || commitDeletion || commitAddition || commitCopy);

        if (commitAddition || commitDeletion || textModified || propsModified
                || commitCopy || commitLock) {
            SVNCommitItem item = new SVNCommitItem(path, 
                    SVNURL.parseURIEncoded(url), cfURL != null ? SVNURL.parseURIEncoded(cfURL) : null, entry.getKind(), 
                    cfURL != null ? SVNRevision.create(cfRevision) : SVNRevision.create(entry.getRevision()), 
                    commitAddition, commitDeletion, propsModified, textModified, commitCopy,
                    commitLock);
            String itemPath = dir.getPath();
            if ("".equals(itemPath)) {
                itemPath += entry.getName();
            } else if (!"".equals(entry.getName())) {
                itemPath += "/" + entry.getName();
            }
            item.setPath(itemPath);
            commitables.put(path, item);
            if (lockTokens != null && entry.getLockToken() != null) {
                lockTokens.put(url, entry.getLockToken());
            }
        }
        if (entries != null && recursive && (commitAddition || !commitDeletion)) {
            // recurse.
            for (Iterator ents = entries.entries(copyMode); ents.hasNext();) {
                if (dir != null && dir.getWCAccess() != null) {
                    dir.getWCAccess().checkCancelled();
                }
                SVNEntry currentEntry = (SVNEntry) ents.next();
                if ("".equals(currentEntry.getName())) {
                    continue;
                }
                // if recursion is forced and entry is explicitly copied, skip it.
                if (forcedRecursion && currentEntry.isCopied() && currentEntry.getCopyFromURL() != null) {
                    continue;
                }
                String currentCFURL = cfURL != null ? cfURL : copyFromURL;
                if (currentCFURL != null) {
                    currentCFURL = SVNPathUtil.append(currentCFURL, SVNEncodingUtil.uriEncode(currentEntry.getName()));
                }
                String currentURL = currentEntry.getURL();
                if (copyMode || entry.getURL() == null) {
                    currentURL = SVNPathUtil.append(url, SVNEncodingUtil.uriEncode(currentEntry.getName()));
                }
                File currentFile = dir.getFile(currentEntry.getName());
                SVNDirectory childDir;
                if (currentEntry.getKind() == SVNNodeKind.DIR) {
                    childDir = dir.getChildDirectory(currentEntry.getName());
                    if (childDir == null) {
                        SVNFileType currentType = SVNFileType.getType(currentFile);
                        if (currentType == SVNFileType.NONE && currentEntry.isScheduledForDeletion()) {
                            SVNCommitItem item = new SVNCommitItem(currentFile,
                                    SVNURL.parseURIEncoded(currentURL), null, currentEntry.getKind(),
                                    SVNRevision.UNDEFINED, false, true, false,
                                    false, false, false);
                            item.setPath(SVNPathUtil.append(dir.getPath(), currentEntry.getName()));
                            commitables.put(currentFile, item);
                            continue;
                        } else if (currentType != SVNFileType.NONE) {
                            // directory is not missing, but obstructed, 
                            // or no special params are specified.
                            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_NOT_LOCKED, "Working copy ''{0}'' is missing or not locked", currentFile);
                            SVNErrorManager.error(err);
                        } else { 
                            ISVNCommitParameters.Action action = 
                                params != null ? params.onMissingDirectory(dir.getFile(currentEntry.getName())) : ISVNCommitParameters.ERROR;
                            if (action == ISVNCommitParameters.DELETE) {
                                SVNCommitItem item = new SVNCommitItem(currentFile,
                                        SVNURL.parseURIEncoded(currentURL), null, currentEntry.getKind(),
                                        SVNRevision.UNDEFINED, false, true, false,
                                        false, false, false);
                                item.setPath(SVNPathUtil.append(dir.getPath(), currentEntry.getName()));
                                commitables.put(currentFile, item);
                                currentEntry.scheduleForDeletion();
                                entries.save(false);
                                continue;
                            } else if (action != ISVNCommitParameters.SKIP) {
                                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_NOT_LOCKED, "Working copy ''{0}'' is missing or not locked", currentFile);
                                SVNErrorManager.error(err);
                            }
                        }
                    }
                }
                harvestCommitables(commitables, dir, currentFile, entry,
                        currentEntry, currentURL, currentCFURL, copyMode,
                        addsOnly, justLocked, lockTokens, true, forcedRecursion, params);

            }
        }
        if (lockTokens != null && entry.getKind() == SVNNodeKind.DIR
                && commitDeletion) {
            // harvest lock tokens for deleted items.
            collectLocks(dir, lockTokens);
        }
    }

    private static void collectLocks(SVNDirectory dir, Map lockTokens)
            throws SVNException {
        SVNEntries entries = dir.getEntries();
        if (entries == null) {
            return;
        }
        for (Iterator ents = entries.entries(false); ents.hasNext();) {
            SVNEntry entry = (SVNEntry) ents.next();
            if (entry.getURL() != null && entry.getLockToken() != null) {
                lockTokens.put(entry.getURL(), entry.getLockToken());
            }
            if (!"".equals(entry.getName()) && entry.isDirectory()) {
                SVNDirectory child = dir.getChildDirectory(entry.getName());
                if (child != null) {
                    collectLocks(child, lockTokens);
                }
            }
        }
        entries.close();
    }

    private static void removeRedundantPaths(Collection dirsToLockRecursively,
            Collection dirsToLock) {
        for (Iterator paths = dirsToLock.iterator(); paths.hasNext();) {
            String path = (String) paths.next();
            if (dirsToLockRecursively.contains(path)) {
                paths.remove();
            } else {
                for (Iterator recPaths = dirsToLockRecursively.iterator(); recPaths
                        .hasNext();) {
                    String existingPath = (String) recPaths.next();
                    if (path.startsWith(existingPath + "/")) {
                        paths.remove();
                        break;
                    }
                }
            }
        }
    }

    private static File adjustRelativePaths(File rootFile,
            Collection relativePaths) throws SVNException {
        if (relativePaths.contains("")) {
            String targetName = getTargetName(rootFile);
            if (!"".equals(targetName) && rootFile.getParentFile() != null) {
                // there is a versioned parent.
                rootFile = rootFile.getParentFile();
                Collection result = new TreeSet();
                for (Iterator paths = relativePaths.iterator(); paths.hasNext();) {
                    String path = (String) paths.next();
                    path = "".equals(path) ? targetName : SVNPathUtil.append(targetName, path);
                    result.add(path);
                }
                relativePaths.clear();
                relativePaths.addAll(result);
            }
        }
        return rootFile;
    }

    private static String getTargetName(File file) throws SVNException {
        SVNWCAccess wcAccess = SVNWCAccess.create(file);
        return wcAccess.getTargetName();
    }
    
    private static boolean isRecursiveCommitForced(File directory) throws SVNException {
        SVNWCAccess wcAccess = SVNWCAccess.create(directory);
        SVNEntry targetEntry = wcAccess.getTargetEntry();
        if (targetEntry != null) {
            return targetEntry.isCopied() || targetEntry.isScheduledForDeletion() || targetEntry.isScheduledForReplacement();
        }
        return false;
    }
}
