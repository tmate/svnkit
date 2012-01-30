package org.tmatesoft.svn.core.internal.wc2.ng;

import java.io.File;
import java.io.OutputStream;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.tmatesoft.svn.core.ISVNDirEntryHandler;
import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNDirEntry;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.SVNEventFactory;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc.admin.SVNChecksumOutputStream;
import org.tmatesoft.svn.core.internal.wc17.SVNWCContext;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.diff.SVNDeltaProcessor;
import org.tmatesoft.svn.core.io.diff.SVNDiffWindow;
import org.tmatesoft.svn.core.wc.SVNEvent;
import org.tmatesoft.svn.core.wc.SVNEventAction;
import org.tmatesoft.svn.core.wc.SVNStatusType;
import org.tmatesoft.svn.core.wc2.SvnChecksum;

public class SvnNgRemoteDiffEditor implements ISVNEditor {
    
    private SVNWCContext context;
    private File target;
    private SVNDepth depth;
    private SVNRepository repository;
    private long revision;
    private boolean walkDeletedDirs;
    private boolean dryRun;
    private ISvnDiffCallback diffCallback;
    
    private long targetRevision;
    private File emptyFile;
    private Map<File, DeletedPath> deletedPaths;

    private DirBaton currentDir;
    private SvnDiffCallbackResult currentResult;
    private FileBaton currentFile;

    public static ISVNEditor createEditor(SVNWCContext context, File target, SVNDepth depth, SVNRepository repository, long revision, 
            boolean walkDeletedDirs, boolean dryRun, ISvnDiffCallback diffCallback) {
        SvnNgRemoteDiffEditor editor = new SvnNgRemoteDiffEditor();
        
        editor.context = context;
        editor.target = target;
        editor.depth = depth;
        editor.repository = repository;
        editor.revision = revision;
        editor.walkDeletedDirs = walkDeletedDirs;
        editor.dryRun = dryRun;
        editor.diffCallback = diffCallback;
        
        editor.deletedPaths = new HashMap<File, DeletedPath>();
        editor.currentResult = new SvnDiffCallbackResult();
        
        return editor;
    }
    
    private static class DeletedPath {
        SVNNodeKind kind;
        SVNEventAction action;
        SVNStatusType state;
        boolean isTreeConflicted;
    }
    
    private static class DirBaton {
        boolean added;
        boolean treeConflicted;
        boolean skip;
        boolean skipChildren;
        String repoPath;
        File wcPath;
        DirBaton parent;
        SVNProperties propChanges;
        SVNProperties pristineProperties;
        
        public void loadProperties(SVNRepository repos, long revision) throws SVNException {
            repos.getDir("", revision, pristineProperties, 0, (ISVNDirEntryHandler) null);
        }
    }

    private static class FileBaton {
        boolean added;
        boolean treeConflicted;
        boolean skip;
        String repoPath;
        File wcPath;
        
        File startRevisionFile;
        File endRevisionFile;
        SVNProperties pristineProps;
        long baseRevision;
        
        SvnChecksum startMd5Checksum;
        SvnChecksum resultMd5Checksum;
        
        SVNProperties propChanges;
        public SVNDeltaProcessor deltaProcessor;
        
        public void loadFile(SVNWCContext context, SVNRepository repos, boolean propsOnly) throws SVNException {
            if (!propsOnly) {
                File tmpDir = context.getDb().getWCRootTempDir(wcPath);
                startRevisionFile = SVNFileUtil.createUniqueFile(tmpDir, "diff.", ".tmp", false);
                OutputStream os = null;
                try {
                    os = SVNFileUtil.openFileForWriting(startRevisionFile);
                    os = new SVNChecksumOutputStream(os, "MD5", true);
                    repos.getFile(this.repoPath, this.baseRevision, this.pristineProps, os);
                } finally {
                    SVNFileUtil.closeFile(os);
                }
            } else {
                repos.getFile(this.repoPath, this.baseRevision, this.pristineProps, null);
            }
                
        }
        
        public String[] getMimeTypes() {
            String[] r = new String[2];
            if (pristineProps != null) {
                r[0] = pristineProps.getStringValue(SVNProperty.MIME_TYPE);
                r[1] = r[0];
            }
            if (propChanges != null) {
                r[1] = propChanges.getStringValue(SVNProperty.MIME_TYPE);
            }
            return r;
        }
    }
    
    private DirBaton makeDirBaton(String path, DirBaton parent, boolean added) {
        final DirBaton baton = new DirBaton();
        baton.parent = parent;
        baton.added = added;
        baton.repoPath = path;
        baton.wcPath = SVNFileUtil.createFilePath(target, path);
        baton.propChanges = new SVNProperties();
        return baton;
    }

    private FileBaton makeFileBaton(String path, boolean added) {
        final FileBaton baton = new FileBaton();
        baton.added = added;
        baton.repoPath = path;
        baton.wcPath = SVNFileUtil.createFilePath(target, path);
        baton.propChanges = new SVNProperties();
        baton.baseRevision = this.revision;
        return baton;
    }

    public void applyTextDelta(String path, String baseChecksum) throws SVNException {
        // get base version
        if (currentFile.skip) {
            return;
        }
        currentFile.deltaProcessor = new SVNDeltaProcessor();
        if (!currentFile.added) {
            currentFile.loadFile(context, repository, false);
        } else {
            currentFile.startRevisionFile = getEmptyFile();
        }
        File tmpDir = context.getDb().getWCRootTempDir(target);
        currentFile.endRevisionFile = SVNFileUtil.createUniqueFile(tmpDir, SVNPathUtil.tail(path), ".tmp", false);
        currentFile.deltaProcessor.applyTextDelta(currentFile.startRevisionFile, currentFile.endRevisionFile, true);
    }

    public OutputStream textDeltaChunk(String path, SVNDiffWindow diffWindow) throws SVNException {
        if (currentFile.deltaProcessor != null) {
            return currentFile.deltaProcessor.textDeltaChunk(diffWindow);
        }
        return SVNFileUtil.DUMMY_OUT;
    }

    public void textDeltaEnd(String path) throws SVNException {
        if (currentFile.deltaProcessor != null) {
            String checksum = currentFile.deltaProcessor.textDeltaEnd();
            currentFile.resultMd5Checksum = SvnChecksum.fromString("$md5 $" + checksum);
        }
    }

    public void targetRevision(long revision) throws SVNException {
        this.targetRevision = revision;
    }

    public void openRoot(long revision) throws SVNException {
        DirBaton baton = makeDirBaton("", null, false);
        baton.wcPath = target;
        
        baton.loadProperties(repository, revision);
        
        this.currentDir = baton;
        
    }

    public void deleteEntry(String path, long revision) throws SVNException {
        if (currentDir.skip || currentDir.skipChildren || currentDir.treeConflicted) {
            return;
        }
        SVNNodeKind kind = repository.checkPath(path, revision);
        SVNEventAction action = SVNEventAction.SKIP;
        currentResult.contentState = SVNStatusType.INAPPLICABLE;
        
        if (kind == SVNNodeKind.FILE) {
            FileBaton b = makeFileBaton(path, false);
            b.loadFile(context, repository, false);
            b.endRevisionFile = getEmptyFile();
            String[] mTypes = b.getMimeTypes();
            
            diffCallback.fileDeleted(currentResult, path, b.startRevisionFile, 
                    b.endRevisionFile, mTypes[0], mTypes[1], b.pristineProps);
        } else if (kind == SVNNodeKind.DIR) {
            diffCallback.dirDeleted(currentResult, path);
            if (walkDeletedDirs) {
                diffDeletedDir(path, this.revision, repository);
            }
        }
        if (currentResult.contentState != SVNStatusType.MISSING && currentResult.contentState != SVNStatusType.OBSTRUCTED && !currentResult.treeConflicted) {
            action = SVNEventAction.UPDATE_DELETE;
        }
        if (context.getEventHandler() != null) {
            final DeletedPath dp = new DeletedPath();
            dp.action = currentResult.treeConflicted ? SVNEventAction.TREE_CONFLICT : action;
            dp.kind = kind;
            dp.state = currentResult.contentState;
            dp.isTreeConflicted = currentResult.treeConflicted;
            deletedPaths.put(SVNFileUtil.createFilePath(target, path), dp);
        }
    }

    private void diffDeletedDir(String path, long revision, SVNRepository repository) throws SVNException {
        context.checkCancelled();
        Collection<SVNDirEntry> entries = repository.getDir(path, revision, null, 0, (Collection<SVNDirEntry>) null);
        for (SVNDirEntry entry : entries) {
            if (entry.getName() == null || "".equals(entry.getName())) {
                continue;
            }
            String entryPath = SVNPathUtil.append(path, entry.getName());
            if (entry.getKind() == SVNNodeKind.FILE) {
                FileBaton fb = makeFileBaton(entryPath, false);
                fb.loadFile(context, repository, false);
                File emptyFile = getEmptyFile();
                String[] mTypes = fb.getMimeTypes();
                diffCallback.fileDeleted(null, entryPath, 
                        fb.startRevisionFile, emptyFile, 
                        mTypes[0], mTypes[1], 
                        fb.pristineProps);
            } else if (entry.getKind() == SVNNodeKind.DIR) {
                diffDeletedDir(entryPath, revision, repository);
            }
        }
    }

    private File getEmptyFile() throws SVNException {
        if (emptyFile == null) {
            emptyFile = context.getDb().getWCRootTempDir(target);
            emptyFile = SVNFileUtil.createUniqueFile(emptyFile, "empty.", ".tmp", false);
            SVNFileUtil.createEmptyFile(emptyFile);
        }
        return emptyFile;
    }

    public void absentDir(String path) throws SVNException {
        if (context.getEventHandler() == null) {
            return;
        }
        File file = SVNFileUtil.createFilePath(currentDir.wcPath, SVNPathUtil.tail(path));
        SVNEvent event = SVNEventFactory.createSVNEvent(file, SVNNodeKind.DIR, null, -1, 
                SVNStatusType.MISSING, 
                SVNStatusType.MISSING, 
                SVNStatusType.INAPPLICABLE, 
                SVNEventAction.SKIP, 
                SVNEventAction.SKIP, null, null, null);
        context.getEventHandler().handleEvent(event, -1);
    }

    public void absentFile(String path) throws SVNException {
        if (context.getEventHandler() == null) {
            return;
        }
        File file = SVNFileUtil.createFilePath(currentDir.wcPath, SVNPathUtil.tail(path));
        SVNEvent event = SVNEventFactory.createSVNEvent(file, SVNNodeKind.FILE, null, -1, 
                SVNStatusType.MISSING, 
                SVNStatusType.MISSING, 
                SVNStatusType.INAPPLICABLE, 
                SVNEventAction.SKIP, 
                SVNEventAction.SKIP, null, null, null);
        context.getEventHandler().handleEvent(event, -1);
    }

    public void addDir(String path, String copyFromPath, long copyFromRevision) throws SVNException {
        DirBaton db = makeDirBaton(path, currentDir, true);
        db.pristineProperties = new SVNProperties();
        DirBaton pb = currentDir;
        currentDir = db;
        
        if (pb.skip || pb.skipChildren || pb.treeConflicted) {
            currentDir.skip = true;
            return;
        }
        
        diffCallback.dirAdded(currentResult.reset(), path, targetRevision, copyFromPath, copyFromRevision);
        db.skip = currentResult.skip;
        db.skipChildren = currentResult.skipChildren;
        db.treeConflicted = currentResult.treeConflicted;
        
        if (context.getEventHandler() != null) {
            SVNNodeKind kind = SVNNodeKind.DIR;
            SVNEventAction action = null;
            DeletedPath dp = deletedPaths.get(db.wcPath);
            if (dp != null) {
                currentResult.contentState = dp.state;
                kind = dp.kind;
            }
            if (db.treeConflicted) {
                action = SVNEventAction.TREE_CONFLICT;
            } else if (dp != null) {
                if (dp.action == SVNEventAction.UPDATE_DELETE) {
                    action = SVNEventAction.UPDATE_REPLACE;
                } else {
                    action = dp.action;
                }
            } else if (currentResult.contentState == SVNStatusType.MISSING || 
                    currentResult.contentState == SVNStatusType.OBSTRUCTED) {
                action = SVNEventAction.SKIP;
            } else {
                action = SVNEventAction.UPDATE_ADD;
            }
            SVNEvent event = SVNEventFactory.createSVNEvent(db.wcPath, 
                    kind, null, -1, 
                    currentResult.contentState, 
                    currentResult.contentState, 
                    SVNStatusType.INAPPLICABLE, 
                    action, 
                    action, null, null, null);
            context.getEventHandler().handleEvent(event, -1);
        }
    }

    public void openDir(String path, long revision) throws SVNException {
        DirBaton db = makeDirBaton(path, currentDir, true);
        db.pristineProperties = new SVNProperties();
        DirBaton pb = currentDir;
        currentDir = db;
        
        if (pb.skip || pb.skipChildren || pb.treeConflicted) {
            currentDir.skip = true;
            return;
        }
        db.loadProperties(repository, revision);
        diffCallback.dirOpened(currentResult.reset(), path, revision);
        db.skip = currentResult.skip;
        db.skipChildren = currentResult.skipChildren;
        db.treeConflicted = currentResult.treeConflicted;
    }

    public void changeDirProperty(String name, SVNPropertyValue value) throws SVNException {
        if (currentDir.skip) {
            return;
        }
        currentDir.propChanges.put(name, value);
    }

    public void closeDir() throws SVNException {
    }

    public void addFile(String path, String copyFromPath, long copyFromRevision) throws SVNException {
        FileBaton fb = makeFileBaton(path, true);
        currentFile = fb;
        if (currentDir.skip || currentDir.skipChildren || currentDir.treeConflicted) {
            fb.skip = true;
            return;
        }
        fb.pristineProps = new SVNProperties();
    }

    public void openFile(String path, long revision) throws SVNException {
        FileBaton fb = makeFileBaton(path, false);
        currentFile = fb;
        if (currentDir.skip || currentDir.skipChildren || currentDir.treeConflicted) {
            fb.skip = true;
            return;
        }
        fb.baseRevision = revision;
        diffCallback.fileOpened(currentResult.reset(), fb.wcPath, revision);
        fb.treeConflicted = currentResult.treeConflicted;
        fb.skip = currentResult.skip;
    }

    public void changeFileProperty(String path, String propertyName, SVNPropertyValue propertyValue) throws SVNException {
        if (currentFile.skip) {
            return;
        }
        currentFile.propChanges.put(propertyName, propertyValue);
    }

    public void closeFile(String path, String textChecksum) throws SVNException {
        if (currentFile.skip) {
            currentFile = null;
            return;
        }
        FileBaton b = currentFile;
        if (!b.added && b.propChanges.size() > 0) {
            if (b.pristineProps == null) {
                b.loadFile(context, repository, true);
            }
            removeNonPropChanges(b.pristineProps, b.propChanges);
        }
        if (b.endRevisionFile != null || b.added) {
            String[] mTypes = b.getMimeTypes();
            if (b.added) {
                diffCallback.fileAdded(currentResult.reset(),
                        path,
                        b.endRevisionFile != null ? b.startRevisionFile : null, 
                        b.endRevisionFile, 
                        0, 
                        targetRevision, 
                        mTypes[0], 
                        mTypes[1], 
                        null, 
                        -1, 
                        b.propChanges, b.pristineProps);
                b.treeConflicted = currentResult.treeConflicted;
            } else {
                diffCallback.fileChanged(currentResult.reset(),
                        path,
                        b.endRevisionFile != null ? b.startRevisionFile : null, 
                        b.endRevisionFile, 
                        0, 
                        targetRevision, 
                        mTypes[0], 
                        mTypes[1], 
                        b.propChanges, b.pristineProps);
                b.treeConflicted = currentResult.treeConflicted;
            }
        }
        if (context.getEventHandler() != null) {
            SVNNodeKind kind = SVNNodeKind.FILE;
            SVNEventAction action = null;
            DeletedPath dp = deletedPaths.get(b.wcPath);
            if (dp != null) {
                deletedPaths.remove(b.wcPath);
            }
        }
    }

    private void removeNonPropChanges(SVNProperties pristineProps, SVNProperties propChanges) {
        Set<String> removed = new HashSet<String>();
        for (String propertyName : propChanges.nameSet()) {
            SVNPropertyValue newValue = propChanges.getSVNPropertyValue(propertyName);
            SVNPropertyValue oldValue = pristineProps.getSVNPropertyValue(propertyName);
            if (oldValue == newValue || (oldValue != null && oldValue.equals(newValue))) {
                removed.add(propertyName);
            }
        }
        for (String name : removed) {
            propChanges.remove(name);
        }
    }

    public SVNCommitInfo closeEdit() throws SVNException {
        return null;
    }

    public void abortEdit() throws SVNException {
    }

}
