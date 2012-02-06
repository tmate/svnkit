package org.tmatesoft.svn.core.internal.wc2.ng;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

import org.tmatesoft.svn.core.ISVNLogEntryHandler;
import org.tmatesoft.svn.core.SVNCancelException;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLogEntry;
import org.tmatesoft.svn.core.SVNMergeInfoInheritance;
import org.tmatesoft.svn.core.SVNMergeRange;
import org.tmatesoft.svn.core.SVNMergeRangeList;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.util.SVNMergeInfoUtil;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.SVNCancellableEditor;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNEventFactory;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc17.SVNStatusEditor17;
import org.tmatesoft.svn.core.internal.wc17.SVNWCContext;
import org.tmatesoft.svn.core.internal.wc17.SVNWCUtils;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.SVNWCDbKind;
import org.tmatesoft.svn.core.internal.wc17.db.SVNWCDb;
import org.tmatesoft.svn.core.internal.wc17.db.Structure;
import org.tmatesoft.svn.core.internal.wc17.db.StructureFields.ExternalNodeInfo;
import org.tmatesoft.svn.core.internal.wc17.db.StructureFields.NodeOriginInfo;
import org.tmatesoft.svn.core.internal.wc17.db.SvnWcDbExternals;
import org.tmatesoft.svn.core.internal.wc17.db.SvnWcDbReader;
import org.tmatesoft.svn.core.internal.wc2.SvnRepositoryAccess;
import org.tmatesoft.svn.core.internal.wc2.SvnRepositoryAccess.LocationsInfo;
import org.tmatesoft.svn.core.internal.wc2.ng.SvnNgMergeinfoUtil.SvnMergeInfoInfo;
import org.tmatesoft.svn.core.io.ISVNReporter;
import org.tmatesoft.svn.core.io.ISVNReporterBaton;
import org.tmatesoft.svn.core.io.SVNLocationSegment;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.wc.ISVNEventHandler;
import org.tmatesoft.svn.core.wc.SVNDiffOptions;
import org.tmatesoft.svn.core.wc.SVNEvent;
import org.tmatesoft.svn.core.wc.SVNEventAction;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNStatusType;
import org.tmatesoft.svn.core.wc2.ISvnObjectReceiver;
import org.tmatesoft.svn.core.wc2.SvnGetProperties;
import org.tmatesoft.svn.core.wc2.SvnMerge;
import org.tmatesoft.svn.core.wc2.SvnStatus;
import org.tmatesoft.svn.core.wc2.SvnTarget;
import org.tmatesoft.svn.util.SVNDebugLog;
import org.tmatesoft.svn.util.SVNLogType;

public class SvnNgMergeDriver implements ISVNEventHandler {
    boolean force;

    boolean dryRun;
    boolean recordOnly;
    boolean sourcesAncestral;
    boolean sameRepos;
    private boolean mergeinfoCapable;
    private boolean ignoreAncestry;
    private boolean targetMissingChild;
    boolean reintegrateMerge;
    
    File targetAbsPath;
    File addedPath;
    SVNURL reposRootUrl;
    
    long mergeSource2Rev;
    SVNURL mergeSource2Url;
    long mergeSource1Rev;
    SVNURL mergeSource1Url;
    
    private SVNMergeRangeList implicitSrcGap;
    SVNWCContext context;
    
    private boolean addNecessiatedMerge;
    
    Collection<File> dryRunDeletions;
    Collection<File> dryRunAdded;
    private Collection<File> conflictedPaths;
    
    Collection<File> pathsWithNewMergeInfo;
    Collection<File> pathsWithDeletedMergeInfo;

    SVNDiffOptions diffOptions;
    
    SVNRepository repos1;
    SVNRepository repos2;
    
    private SvnMerge operation;
    SvnRepositoryAccess repositoryAccess; 
    
    private Map<File, MergePath> childrenWithMergeInfo;

    private int currentAncestorIndex;
    private boolean singleFileMerge;

    protected void doMergeInfoUnawareDirectoryMerge(File targetPath, SVNURL url1, long revision1, SVNURL url2, long revision2, SVNDepth depth) throws SVNException {
        boolean isRollBack = revision1 > revision2;

        MergePath item = new MergePath(targetPath);
        SVNMergeRange itemRange = new SVNMergeRange(revision1, revision2, true);
        item.remainingRanges = new SVNMergeRangeList(itemRange);
        
        Map<File, MergePath> childrenWithMergeInfo = new TreeMap<File, SvnNgMergeDriver.MergePath>();
        childrenWithMergeInfo.put(targetPath, item);

        SvnNgMergeCallback mergeCallback = new SvnNgMergeCallback(this);
        driveMergeReportEditor(targetPath, url1, revision1, url2, revision2, childrenWithMergeInfo, isRollBack,  depth, mergeCallback);
    }


    protected void doDirectoryMerge(File targetPath, Map resultCatalog, SVNURL url1, long revision1, SVNURL url2, long revision2, SVNDepth depth, boolean abortOnConflicts) throws SVNException {        
        boolean isRollBack = revision1 > revision2;
        SVNURL primaryURL = isRollBack ? url1 : url2;
        boolean honorMergeInfo = isHonorMergeInfo();
        boolean recordMergeInfo = isRecordMergeInfo();
        boolean sameURLs = url1.equals(url2);

        SvnNgMergeCallback mergeCallback = new SvnNgMergeCallback(this);
        
        Map<File, MergePath> childrenWithMergeInfo = new TreeMap<File, SvnNgMergeDriver.MergePath>();
        if (!honorMergeInfo) {
            doMergeInfoUnawareDirectoryMerge(targetPath, url1, revision1, url2, revision2, depth);
            return;
        }
        
        SVNRepository repository = isRollBack ? repos1 : repos2;
        SVNURL sourceRootURL = repository.getRepositoryRoot(true);
        String mergeInfoPath = getPathRelativeToRoot(primaryURL, sourceRootURL, null);
        childrenWithMergeInfo = getMergeInfoPaths(childrenWithMergeInfo, mergeInfoPath, sourceRootURL, url1, url2, revision1, revision2, repository, depth);
        this.childrenWithMergeInfo = childrenWithMergeInfo;

        MergePath targetMergePath = childrenWithMergeInfo.get(0);
        targetMissingChild = targetMergePath.missingChild;
        populateRemainingRanges(childrenWithMergeInfo, sourceRootURL, url1, revision1, url2, revision2, honorMergeInfo, repository, mergeInfoPath);
        
        SVNMergeRange range = new SVNMergeRange(revision1, revision2, true);
        SVNErrorMessage err = null;
        if (honorMergeInfo && !recordOnly) {
            long newRangeStart = getMostInclusiveStartRevision(childrenWithMergeInfo, isRollBack);
            if (SVNRevision.isValidRevisionNumber(newRangeStart)) {
                range.setStartRevision(newRangeStart);
            }
            if (!isRollBack) {
                removeNoOpSubtreeRanges(url1, revision1, url2, revision2, repository);
            }
            fixDeletedSubtreeRanges(url1, revision1, url2, revision2, repository);
            long startRev = getMostInclusiveStartRevision(childrenWithMergeInfo, isRollBack);
            
            if (SVNRevision.isValidRevisionNumber(startRev)) {
                long endRev = getMostInclusiveEndRevision(childrenWithMergeInfo, isRollBack);
                while (SVNRevision.isValidRevisionNumber(endRev)) {
                    SVNMergeRange firstTargetRange = targetMergePath.remainingRanges != null && !targetMergePath.remainingRanges.isEmpty() ? targetMergePath.remainingRanges.getRanges()[0] : null;
                    if (firstTargetRange != null && startRev != firstTargetRange.getStartRevision()) {
                        if (isRollBack) {
                            if (endRev < firstTargetRange.getStartRevision()) {
                                endRev = firstTargetRange.getStartRevision();
                            }
                        } else {
                            if (endRev > firstTargetRange.getStartRevision()) {
                                endRev = firstTargetRange.getStartRevision();
                            }
                        }
                    }
                    sliceRemainingRanges(childrenWithMergeInfo, isRollBack, endRev);
                    currentAncestorIndex = -1;
                     
                    SVNURL realURL1 = url1;
                    SVNURL realURL2 = url2;
                    SVNURL oldURL1 = null;
                    SVNURL oldURL2 = null;
                    long nextEndRev = SVNRepository.INVALID_REVISION;
                    
                    if (!sameURLs) {
                        if (isRollBack && endRev != revision2) {
                            realURL2 = url1;
                            oldURL2 = ensureSessionURL(repos2, realURL2);
                        }
                        if (!isRollBack && startRev != revision1) {
                            realURL1 = url2;
                            oldURL1 = ensureSessionURL(repos1, realURL1);
                        }
                    }
                    
                    try {
                        driveMergeReportEditor(this.targetAbsPath, realURL1, startRev, realURL2, endRev, 
                                childrenWithMergeInfo, isRollBack, depth, mergeCallback);
                    } finally {
                        if (oldURL1 != null) {
                            repos1.setLocation(oldURL1, false);
                        }
                        if (oldURL2 != null) {
                            repos2.setLocation(oldURL2, false);
                        }
                    }
                    
                    processChildrenWithNewMergeInfo(childrenWithMergeInfo);
                    processChildrenWithDeletedMergeInfo(childrenWithMergeInfo);
                    
                    removeFirstRangeFromRemainingRanges(endRev, childrenWithMergeInfo);
                    nextEndRev = getMostInclusiveEndRevision(childrenWithMergeInfo, isRollBack);

                    if ((nextEndRev != -1 || abortOnConflicts) && (conflictedPaths != null && !conflictedPaths.isEmpty())) {
                        SVNMergeRange conflictedRange = new SVNMergeRange(startRev, endRev, false);
                        err = makeMergeConflictError(targetAbsPath, conflictedRange);
                        range.setEndRevision(endRev);
                        break;
                    }
                    startRev = getMostInclusiveStartRevision(childrenWithMergeInfo, isRollBack);
                    endRev = nextEndRev;
                }
            }
        } else {
            if (!recordOnly) {
                currentAncestorIndex = -1;
                driveMergeReportEditor(targetAbsPath, url1, revision1, url2, revision2, null, isRollBack, 
                        depth, mergeCallback);
            }
        }

        if (recordMergeInfo) {
//            recordMergeInfoForDirectoryMerge(resultCatalog, range, mergeInfoPath, depth);
            if (range.getStartRevision() < range.getEndRevision()) {
//                recordMergeInfoForAddedSubtrees(range, mergeInfoPath, depth);
            }
        }
        
        if (err != null) {
            SVNErrorManager.error(err, SVNLogType.WC);
        }
    }

    private void removeNoOpSubtreeRanges(SVNURL url1, long revision1, SVNURL url2, long revision2, SVNRepository repository) throws SVNException {
        if (revision1 > revision2) {
            return;
        }
        if (childrenWithMergeInfo.size() < 2) {
            return;
        }
        Iterator<MergePath> mps = childrenWithMergeInfo.values().iterator();
        MergePath rootPath = mps.next();
        SVNMergeRangeList requestedRanges = new SVNMergeRangeList(
                new SVNMergeRange(Math.min(revision1, revision2), 
                        Math.max(revision1, revision2), true));
        SVNMergeRangeList subtreeGapRanges = rootPath.remainingRanges.remove(requestedRanges, false);
        if (subtreeGapRanges.isEmpty()) {
            return;
        }
        SVNMergeRangeList subtreeRemainingRanges = new SVNMergeRangeList(new SVNMergeRange[0]);
        while(mps.hasNext()) {
            MergePath child = mps.next();
            if (child.remainingRanges != null && !child.remainingRanges.isEmpty()) {
                subtreeRemainingRanges = subtreeRemainingRanges.merge(child.remainingRanges);
            }
        }
        if (subtreeRemainingRanges.isEmpty()) {
            return;
        }
        subtreeGapRanges = subtreeGapRanges.intersect(subtreeRemainingRanges, false);
        if (subtreeGapRanges.isEmpty()) {
            return;
        }
        SVNMergeRange oldestGapRev = subtreeGapRanges.getRanges()[0];
        SVNMergeRange youngestRev = subtreeGapRanges.getRanges()[subtreeGapRanges.getSize() - 1];
        SVNURL reposRootURL = context.getNodeReposInfo(targetAbsPath).reposRootUrl;
        NoopLogHandler logHandler = new NoopLogHandler();
        logHandler.sourceReposAbsPath = getPathRelativeToRoot(url2, reposRootURL, null);
        logHandler.mergedRanges = new SVNMergeRangeList(new SVNMergeRange[0]);
        logHandler.operativeRanges = new SVNMergeRangeList(new SVNMergeRange[0]);
        
        repository.log(new String[] {""}, oldestGapRev.getStartRevision() + 1, youngestRev.getEndRevision(), true, true, -1, false, null, logHandler);
        
        SVNMergeRangeList inoperativeRanges = new SVNMergeRangeList(oldestGapRev.getEndRevision(), youngestRev.getStartRevision(), true);
        inoperativeRanges = inoperativeRanges.remove(logHandler.operativeRanges, false);
        logHandler.mergedRanges = logHandler.mergedRanges.merge(inoperativeRanges);
        
        for (MergePath child : childrenWithMergeInfo.values()) {
            if (child.remainingRanges != null && !child.remainingRanges.isEmpty()) {
                child.remainingRanges = child.remainingRanges.remove(logHandler.mergedRanges, false);
            }
        }
    }
    


    public SvnNgRemoteDiffEditor driveMergeReportEditor(File targetWCPath, SVNURL url1, long revision1,
            SVNURL url2, final long revision2, final Map<File, MergePath> childrenWithMergeInfo, final boolean isRollBack, 
            SVNDepth depth, SvnNgMergeCallback mergeCallback) throws SVNException {
        final boolean honorMergeInfo = isHonorMergeInfo();
        long targetStart = revision1;
        
        if (honorMergeInfo) {
            targetStart = revision2;
            if (childrenWithMergeInfo != null && !childrenWithMergeInfo.isEmpty()) {                
                MergePath targetMergePath = (MergePath) childrenWithMergeInfo.get(0);
                SVNMergeRangeList remainingRanges = targetMergePath.remainingRanges; 
                if (remainingRanges != null && !remainingRanges.isEmpty()) {
                    SVNMergeRange[] ranges = remainingRanges.getRanges();
                    SVNMergeRange range = ranges[0];
                    if ((!isRollBack && range.getStartRevision() > revision2) ||
                            (isRollBack && range.getStartRevision() < revision2)) {
                        targetStart = revision2;
                    } else {
                        targetStart = range.getStartRevision();
                    }
                }
            }
        }

        SvnNgRemoteDiffEditor editor = SvnNgRemoteDiffEditor.createEditor(context, targetAbsPath, depth, repos2, revision1, false, dryRun, mergeCallback, this);

        SVNURL oldURL = ensureSessionURL(repos2, url1);
        try {
            final SVNDepth reportDepth = depth;
            final long reportStart = targetStart;
            final String targetPath = targetWCPath.getAbsolutePath().replace(File.separatorChar, '/');

            repos1.diff(url2, revision2, revision2, null, ignoreAncestry, depth, true,
                    new ISVNReporterBaton() {
                        public void report(ISVNReporter reporter) throws SVNException {
                            
                            reporter.setPath("", null, reportStart, reportDepth, false);

                            if (honorMergeInfo && childrenWithMergeInfo != null) {
                                for (int i = 1; i < childrenWithMergeInfo.size(); i++) {
                                   MergePath childMergePath = (MergePath) childrenWithMergeInfo.get(i);
                                   MergePath parent = null;
                                   if (childMergePath == null || childMergePath.absent) {
                                       continue;
                                   }
                                   //
                                   Object[] childrenWithMergeInfoArray = childrenWithMergeInfo.values().toArray();
                                   int parentIndex = findNearestAncestor(childrenWithMergeInfoArray, false, childMergePath.absPath);
                                   if (parentIndex >= 0 && parentIndex < childrenWithMergeInfoArray.length) {
                                       parent = (MergePath) childrenWithMergeInfoArray[parentIndex];
                                   }
                                   
                                   SVNMergeRange range = null;
                                   if (childMergePath.remainingRanges != null && 
                                           !childMergePath.remainingRanges.isEmpty()) {
                                       SVNMergeRangeList remainingRangesList = childMergePath.remainingRanges; 
                                       SVNMergeRange[] remainingRanges = remainingRangesList.getRanges();
                                       range = remainingRanges[0];
                                       
                                       if ((!isRollBack && range.getStartRevision() > revision2) ||
                                               (isRollBack && range.getStartRevision() < revision2)) {
                                           continue;
                                       } else if (parent.remainingRanges != null && !parent.remainingRanges.isEmpty()) {
                                           SVNMergeRange parentRange = parent.remainingRanges.getRanges()[0];
                                           SVNMergeRange childRange = childMergePath.remainingRanges.getRanges()[0];
                                           if (parentRange.getStartRevision() == childRange.getStartRevision()) {
                                               continue;
                                           }
                                       }
                                   } else {
                                       if (parent.remainingRanges == null || parent.remainingRanges.isEmpty()) {
                                           continue;
                                       }
                                   }
                                     
                                   String childPath = childMergePath.absPath.getAbsolutePath();
                                   childPath = childPath.replace(File.separatorChar, '/');
                                   String relChildPath = childPath.substring(targetPath.length());
                                   if (relChildPath.startsWith("/")) {
                                       relChildPath = relChildPath.substring(1);
                                   }
                                   
                                   if (childMergePath.remainingRanges == null || 
                                           childMergePath.remainingRanges.isEmpty() ||
                                           (isRollBack && range.getStartRevision() < revision2) ||
                                           (!isRollBack && range.getStartRevision() > revision2)) {
                                       reporter.setPath(relChildPath, null, revision2, reportDepth, false);
                                   } else {
                                       reporter.setPath(relChildPath, null, range.getStartRevision(), reportDepth, false);
                                   }
                                }
                            }
                            reporter.finishReport();
                        }
                    }, 
                    SVNCancellableEditor.newInstance(editor, operation.getCanceller(), SVNDebugLog.getDefaultLog()));
        } finally {
            if (oldURL != null) {
                repos2.setLocation(oldURL, false);
            }
            editor.cleanup();
        }
        
        SVNFileUtil.sleepForTimestamp();
        if (conflictedPaths == null) {
            conflictedPaths = mergeCallback.getConflictedPaths();
        }
        return editor;
    }

    protected boolean isHonorMergeInfo() {
        return sourcesAncestral && sameRepos && !ignoreAncestry && mergeinfoCapable;
    }

    public boolean isRecordMergeInfo() {
        return !dryRun && isHonorMergeInfo();
    }

    protected SVNURL ensureSessionURL(SVNRepository repository, SVNURL url) throws SVNException {
        SVNURL oldURL = repository.getLocation();
        if (url == null) {
            url = repository.getRepositoryRoot(true);
        }
        if (!url.equals(oldURL)) {
            repository.setLocation(url, false);
            return oldURL;
        }
        return null;
    }

    private int findNearestAncestor(Object[] childrenWithMergeInfoArray, boolean pathIsOwnAncestor, File path) {
        if (childrenWithMergeInfoArray == null) {
            return 0;
        }

        int ancestorIndex = 0;
        for (int i = 0; i < childrenWithMergeInfoArray.length; i++) {
            MergePath child = (MergePath) childrenWithMergeInfoArray[i];
            String childPath = child.absPath.getAbsolutePath().replace(File.separatorChar, '/');
            String pathStr = path.getAbsolutePath().replace(File.separatorChar, '/');
            if (SVNPathUtil.isAncestor(childPath, pathStr) && (!childPath.equals(pathStr) || pathIsOwnAncestor)) {
                ancestorIndex = i;
            }
        }
        return ancestorIndex;
    }

    private Map<File, MergePath> getMergeInfoPaths(
            final Map<File, MergePath> children, 
            final String mergeSrcPath, 
            final SVNURL sourceRootURL,
            final SVNURL url1,
            final SVNURL url2,
            final long revision1, 
            final long revision2, 
            final SVNRepository repository, 
            final SVNDepth depth) throws SVNException {

        final Map<File, MergePath> childrenWithMergeInfo = children == null ? new TreeMap<File, MergePath>() : children;
        
        final SvnGetProperties pg = operation.getOperationFactory().createGetProperties();
        final Map<File, String> subtreesWithMergeinfo = new TreeMap<File, String>();
        pg.setDepth(depth);
        pg.setSingleTarget(SvnTarget.fromFile(targetAbsPath, SVNRevision.WORKING));
        pg.setRevision(SVNRevision.WORKING);
        pg.setReceiver(new ISvnObjectReceiver<SVNProperties>() {
            public void receive(SvnTarget target, SVNProperties object) throws SVNException {
                String value = object.getStringValue(SVNProperty.MERGE_INFO);
                if (value != null) {
                    subtreesWithMergeinfo.put(target.getFile(), value);
                }
            }
        });
        pg.run();
        
        if (!subtreesWithMergeinfo.isEmpty()) {
            for (File wcPath : subtreesWithMergeinfo.keySet()) {
                String value = subtreesWithMergeinfo.get(wcPath);
                MergePath mp = new MergePath(wcPath);
                Map<String, SVNMergeRangeList> mi;
                try {
                    mi = SVNMergeInfoUtil.parseMergeInfo(new StringBuffer(value), null);
                } catch (SVNException e) {
                    if (e.getErrorMessage().getErrorCode() == SVNErrorCode.MERGE_INFO_PARSE_ERROR) {
                        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_INVALID_MERGEINFO_NO_MERGETRACKING,
                                "Invalid mergeinfo detected on ''{0}'', mergetracking not possible");
                        SVNErrorManager.error(err, SVNLogType.WC);
                    }
                    throw e;
                }
                mp.preMergeMergeInfo = mi;
                mp.hasNonInheritable = value != null && value.indexOf(SVNMergeRangeList.MERGE_INFO_NONINHERITABLE_STRING) >= 0;
                childrenWithMergeInfo.put(mp.absPath, mp);
            }
        }
        
        final Map<File, SVNDepth> shallowSubtrees = new HashMap<File, SVNDepth>();
        final Collection<File> missingSubtrees = new HashSet<File>();
        final Collection<File> switchedSubtrees = new HashSet<File>();
        
        SVNStatusEditor17 statusEditor = new SVNStatusEditor17(targetAbsPath, context, operation.getOptions(), true, true, depth, new ISvnObjectReceiver<SvnStatus>() {
            public void receive(SvnTarget target, SvnStatus status) throws SVNException {
                boolean fileExternal = false;
                if (status.isVersioned() && status.isSwitched() && status.getKind() == SVNNodeKind.FILE) {
                    Structure<ExternalNodeInfo> info = SvnWcDbExternals.readExternal(context, status.getPath(), targetAbsPath, ExternalNodeInfo.kind);
                    fileExternal = info.get(ExternalNodeInfo.kind) == SVNWCDbKind.File;
                }
                if (status.isSwitched() && !fileExternal) {
                    switchedSubtrees.add(status.getPath());
                }
                if (status.getDepth() == SVNDepth.EMPTY || status.getDepth() == SVNDepth.FILES) {
                    shallowSubtrees.put(status.getPath(), status.getDepth());
                }
                if (status.getNodeStatus() == SVNStatusType.STATUS_MISSING) {
                    boolean parentPresent = false;
                    for (File missingRoot : switchedSubtrees) {
                        if (SVNWCUtils.isAncestor(missingRoot, status.getPath())) {
                            parentPresent = true;
                            break;
                        }
                    }
                    if (!parentPresent) {
                        missingSubtrees.add(status.getPath());
                    }
                }
            }
        });
        statusEditor.walkStatus(targetAbsPath, depth, true, true, true, null);
        
        if (!missingSubtrees.isEmpty()) {
            final StringBuffer errorMessage = new StringBuffer("Merge tracking not allowed with missing "
                    + "subtrees; try restoring these items "
                    + "first:\n");
            final Object[] values = new Object[missingSubtrees.size()];
            int index = 0;
            for(File missingPath : missingSubtrees) {
                values[index] = missingPath;
                errorMessage.append("''{" + index + "}''\n");
                index++;
            }
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_NOT_READY_TO_MERGE, errorMessage.toString(), values);
            SVNErrorManager.error(err, SVNLogType.WC);
        }
        
        if (!switchedSubtrees.isEmpty()) {
            for (File switchedPath : switchedSubtrees) {
                MergePath child = getChildWithMergeinfo(childrenWithMergeInfo, switchedPath);
                if (child != null) {
                    child.switched = true;
                } else {
                    child = new MergePath(switchedPath);
                    child.switched = true;
                    childrenWithMergeInfo.put(child.absPath, child);
                }
            }
        }
        
        if (!shallowSubtrees.isEmpty()) {
            for (File shallowPath : shallowSubtrees.keySet()) {
                MergePath child = getChildWithMergeinfo(childrenWithMergeInfo, shallowPath);
                SVNDepth childDepth = shallowSubtrees.get(shallowPath);
                boolean newChild = false;
                if (child != null) {
                    if (childDepth == SVNDepth.EMPTY || childDepth == SVNDepth.FILES) {
                        child.missingChild = true;
                    }
                } else {
                    newChild = true;
                    child = new MergePath(shallowPath);
                    if (childDepth == SVNDepth.EMPTY || childDepth == SVNDepth.FILES) {
                        child.missingChild = true;
                    }
                }
                if (!child.hasNonInheritable && (childDepth == SVNDepth.EMPTY || childDepth == SVNDepth.FILES)) {
                    child.hasNonInheritable = true;
                }
                if (newChild) {
                    childrenWithMergeInfo.put(child.absPath, child);
                }
            }
        }

        Collection<File> excludedTrees = SvnWcDbReader.getServerExcludedNodes((SVNWCDb) context.getDb(), targetAbsPath);
        if (excludedTrees != null && !excludedTrees.isEmpty()) {
            for (File excludedTree : excludedTrees) {
                MergePath mp = getChildWithMergeinfo(childrenWithMergeInfo, excludedTree);
                if (mp != null) {
                    mp.absent = true;
                } else {
                    mp = new MergePath(excludedTree);
                    mp.absent = true;
                    childrenWithMergeInfo.put(mp.absPath, mp);
                }
            }
        }
        
        if (getChildWithMergeinfo(childrenWithMergeInfo, targetAbsPath) == null) {
            childrenWithMergeInfo.put(targetAbsPath, new MergePath(targetAbsPath));
        }
        if (depth == SVNDepth.IMMEDIATES || depth == SVNDepth.FILES) {
            final List<File> immediateChildren = context.getChildrenOfWorkingNode(targetAbsPath, false);
            for (final File immeidateChild : immediateChildren) {
                SVNNodeKind childKind = context.readKind(immeidateChild, false);
                if ((childKind == SVNNodeKind.DIR && depth == SVNDepth.IMMEDIATES) ||
                        (childKind == SVNNodeKind.FILE && depth == SVNDepth.FILES)) {
                    if (getChildWithMergeinfo(childrenWithMergeInfo, immeidateChild) == null) {
                        MergePath mp = new MergePath(immeidateChild);
                        if (childKind == SVNNodeKind.DIR && depth == SVNDepth.IMMEDIATES) {
                            mp.immediateChildDir = true;
                        }
                        childrenWithMergeInfo.put(mp.absPath, mp);
                    }
                }
            }
        }
        
        if (depth.compareTo(SVNDepth.EMPTY) <= 0) {
            return childrenWithMergeInfo;
        }
        
        for (File childPath : new TreeSet<File>(childrenWithMergeInfo.keySet())) {
            MergePath child = childrenWithMergeInfo.get(childPath);
            if (child.hasNonInheritable) {
                List<File> childrenOfNonInheritable = context.getNodeChildren(child.absPath, false);
                for (File childOfNonInheritable : childrenOfNonInheritable) {
                    MergePath mpOfNonInheritable = getChildWithMergeinfo(childrenWithMergeInfo, childOfNonInheritable);
                    if (mpOfNonInheritable == null) {
                        if (depth == SVNDepth.FILES) {
                            SVNNodeKind childKind = context.readKind(childOfNonInheritable, false);
                            if (childKind  != SVNNodeKind.FILE) {
                                continue;
                            }
                        }
                        mpOfNonInheritable = new MergePath(childOfNonInheritable);
                        mpOfNonInheritable.childOfNonInheritable = true;
                        childrenWithMergeInfo.put(mpOfNonInheritable.absPath, mpOfNonInheritable);
                        if (!dryRun && sameRepos) {
                            SvnMergeInfoInfo info = SvnNgMergeinfoUtil.getWCMergeInfo(context, mpOfNonInheritable.absPath, targetAbsPath, SVNMergeInfoInheritance.NEAREST_ANCESTOR, false);
                            String value = SVNMergeInfoUtil.formatMergeInfoToString(info.mergeinfo, null);
                            SvnNgPropertiesManager.setProperty(context, childOfNonInheritable, SVNProperty.MERGE_INFO, SVNPropertyValue.create(value), SVNDepth.EMPTY, true, null, null);
                        }
                    }
                } 
            }
            insertParentAndSiblingsOfAbsentDelSubtree(childrenWithMergeInfo, child, depth);
        }
        return childrenWithMergeInfo;
    }
    
    private void insertParentAndSiblingsOfAbsentDelSubtree(Map<File, MergePath> childrenWithMergeInfo, MergePath child, SVNDepth depth) throws SVNException {
        if (!(child.absent || (child.switched && !targetAbsPath.equals(child.absPath)))) {
            return;
        }
        File parentPath = SVNFileUtil.getParentFile(child.absPath);
        MergePath parentMp = getChildWithMergeinfo(childrenWithMergeInfo, parentPath);
        if (parentMp != null) {
            parentMp.missingChild = true;
        } else {
            parentMp = new MergePath(parentPath);
            parentMp.missingChild = true;
            childrenWithMergeInfo.put(parentPath, parentMp);
        }
        List<File> files = context.getNodeChildren(parentPath, false);
        for (File file : files) {
            MergePath siblingMp = getChildWithMergeinfo(childrenWithMergeInfo, file);
            if (siblingMp == null) {
                if (depth == SVNDepth.FILES) {
                    if (context.readKind(file, false) != SVNNodeKind.FILE) {
                        continue;
                    }
                }
                childrenWithMergeInfo.put(file, siblingMp);
            }
        }
    }

    private MergePath getChildWithMergeinfo(Map<File, MergePath> childrenWithMergeInfo, File path) {
        return childrenWithMergeInfo.get(path);
    }

    private void populateRemainingRanges(Map<File, MergePath> childrenWithMergeInfo, SVNURL sourceRootURL, 
            SVNURL url1, long revision1, SVNURL url2, long revision2, 
            boolean honorMergeInfo, SVNRepository repository, String parentMergeSrcCanonPath) throws SVNException {

        if (!honorMergeInfo || recordOnly) {
            int index = 0;
            Object[] childrenWithMergeInfoArray = childrenWithMergeInfo.values().toArray();
            for (Iterator<MergePath> childrenIter = childrenWithMergeInfo.values().iterator(); childrenIter.hasNext();) {
                MergePath child = childrenIter.next();
                SVNMergeRange range = new SVNMergeRange(revision1, revision2, true);
                child.remainingRanges = new SVNMergeRangeList(range);
                if (index == 0) {
                    boolean indirect[] = { false };
                    Map[] mergeInfo = getFullMergeInfo(false, true, indirect, SVNMergeInfoInheritance.INHERITED, repository, 
                            child.absPath, Math.max(revision1, revision2), Math.min(revision1, revision2));
                    child.implicitMergeInfo = mergeInfo[1];
                } else {
                    int parentIndex = findNearestAncestor(childrenWithMergeInfoArray, false, child.absPath);
                    MergePath parent = (MergePath) childrenWithMergeInfoArray[parentIndex];
                    boolean childInheritsImplicit = parent != null && !child.switched;
                    ensureImplicitMergeinfo(parent, child, childInheritsImplicit, revision1, revision2, repository);
                }
                index++;
            }           
            return;
        }
        
        long[] gap = new long[2];
        findGapsInMergeSourceHistory(gap, parentMergeSrcCanonPath, url1, revision1, url2, revision2, repository);
        if (gap[0] >= 0 && gap[1] >= 0) {
            implicitSrcGap = new SVNMergeRangeList(gap[0], gap[1], true);
        }
        int index = 0;
        
        for (Iterator<MergePath> childrenIter = childrenWithMergeInfo.values().iterator(); childrenIter.hasNext();) {
            MergePath child = childrenIter.next();
            if (child == null || child.absent) {
                index++;
                continue;
            }
            
            String childRelativePath = null;
            if (targetAbsPath.equals(child.absPath)) {
                childRelativePath = "";
            } else {
                childRelativePath = SVNPathUtil.getRelativePath(targetAbsPath.getAbsolutePath(), child.absPath.getAbsolutePath());
            }
            MergePath parent = null;
            SVNURL childURL1 = url1.appendPath(childRelativePath, false);
            SVNURL childURL2 = url2.appendPath(childRelativePath, false);
            
            boolean indirect[] = { false };
            Map mergeInfo[] = getFullMergeInfo(true, index == 0, indirect, SVNMergeInfoInheritance.INHERITED, 
                    repository, child.absPath, Math.max(revision1, revision2), Math.min(revision1, revision2));
        
            child.preMergeMergeInfo = mergeInfo[0];
            if (index == 0) {
                child.implicitMergeInfo = mergeInfo[1];
            }
            child.inheritedMergeInfo = indirect[0];

            if (index > 0) {
                Object[] childrenWithMergeInfoArray = childrenWithMergeInfo.values().toArray();
                int parentIndex = findNearestAncestor(childrenWithMergeInfoArray, false, child.absPath);
                if (parentIndex >= 0 && parentIndex < childrenWithMergeInfoArray.length) {
                    parent = (MergePath) childrenWithMergeInfoArray[parentIndex];
                }                
            }
            boolean childInheritsImplicit = parent != null && !child.switched;
            calculateRemainingRanges(parent, child, sourceRootURL, childURL1, revision1, 
                    childURL2, revision2, child.preMergeMergeInfo, implicitSrcGap, 
                    index > 0, childInheritsImplicit, repository);

            if (child.remainingRanges.getSize() > 0  && implicitSrcGap != null) {
                long start, end;
                boolean properSubset = false;
                boolean equals = false;
                boolean overlapsOrAdjoins = false;
                
                if (revision1 > revision2) {
                    child.remainingRanges.reverse();
                }
                for(int j = 0; j < child.remainingRanges.getSize(); j++) {
                    start = child.remainingRanges.getRanges()[j].getStartRevision();
                    end = child.remainingRanges.getRanges()[j].getEndRevision();
                    
                    if ((start <= gap[0] && gap[1] < end) || (start < gap[0] && gap[1] <= end)) {
                        properSubset = true;
                        break;
                    } else if (gap[0] == start && gap[1] == end) {
                        equals = true;
                        break;
                    } else if (gap[0] <= end && start <= gap[1]) {
                        overlapsOrAdjoins = true;
                        break;
                    }
                }
                if (!properSubset) {
                    if (overlapsOrAdjoins) {
                        child.remainingRanges = child.remainingRanges.merge(implicitSrcGap);
                    } else if (equals) {
                        child.remainingRanges = child.remainingRanges.diff(implicitSrcGap, false);
                    }
                }
                if (revision1 > revision2) {
                    child.remainingRanges.reverse();
                }
            }
            index++;
        }
    }
    
    protected Map[] getFullMergeInfo(boolean getRecorded, boolean getImplicit, boolean[] inherited, SVNMergeInfoInheritance inherit, SVNRepository repos, File target, long start, long end) throws SVNException {
        Map[] result = new Map[2];
        SVNErrorManager.assertionFailure(SVNRevision.isValidRevisionNumber(start) && SVNRevision.isValidRevisionNumber(end) && start > end, null, SVNLogType.WC);
        
        if (getRecorded) {
            result[0] = SvnNgMergeinfoUtil.getWCOrReposMergeInfo(context, target, repos, false, inherit);
            // TODO track inherited info.
        }

        if (getImplicit) {
            File reposRelPath = null;
            SVNURL reposRootUrl = null;
            long targetRevision = -1;
            Structure<NodeOriginInfo> originInfo = context.getNodeOrigin(target, false, NodeOriginInfo.revision, NodeOriginInfo.reposRelpath, NodeOriginInfo.reposRootUrl);
            reposRelPath = originInfo.get(NodeOriginInfo.reposRelpath);
            reposRootUrl = originInfo.get(NodeOriginInfo.reposRootUrl);
            targetRevision = originInfo.lng(NodeOriginInfo.revision);
            if (reposRelPath == null) {
                result[1] = new TreeMap<String, SVNMergeRangeList>();
            } else if (targetRevision <= end) {
                result[1] = new TreeMap<String, SVNMergeRangeList>();
            } else {
                SVNURL url = SVNWCUtils.join(reposRootUrl, reposRelPath);
                SVNURL sessionUrl = repos.getLocation();
                ensureSessionURL(repos, url);
                if (targetRevision < start) {
                    start = targetRevision;
                }
                result[1] = repositoryAccess.getHistoryAsMergeInfo(repos, SvnTarget.fromURL(url, SVNRevision.create(targetRevision)), start, end);
                ensureSessionURL(repos, sessionUrl);
            }
        }
        return result;
    }

    public Map calculateImplicitMergeInfo(SVNRepository repos, SVNURL url, long[] targetRev, long start, long end) throws SVNException {
        Map implicitMergeInfo = null;
        boolean closeSession = false;
        SVNURL sessionURL = null;
        try {
            if (repos != null) {
                sessionURL = ensureSessionURL(repos, url);
            } else {
                repos = repositoryAccess.createRepository(url, null, false);
                closeSession = true;
            }

            if (targetRev[0] < start) {
                repositoryAccess.getLocations(repos, SvnTarget.fromURL(url), SVNRevision.create(targetRev[0]), SVNRevision.create(start), SVNRevision.UNDEFINED);
                targetRev[0] = start;
            }
            implicitMergeInfo = repositoryAccess.getHistoryAsMergeInfo(repos, SvnTarget.fromURL(url, SVNRevision.create(targetRev[0])), start, end);
            if (sessionURL != null) {
                repos.setLocation(sessionURL, false);
            }
        } finally {
            if (closeSession) {
                repos.closeSession();
            }
        }
        return implicitMergeInfo;
    }

    private void inheritImplicitMergeinfoFromParent(MergePath parent, MergePath child, long revision1, long revision2, SVNRepository repository) throws SVNException {
        if (parent.implicitMergeInfo == null) {
            Map[] mergeinfo = getFullMergeInfo(false, true, null, SVNMergeInfoInheritance.INHERITED, repository, parent.absPath, 
                    Math.max(revision1, revision2), Math.min(revision1, revision2));
            parent.implicitMergeInfo = mergeinfo[1];
        }
        child.implicitMergeInfo = new TreeMap<String, SVNMergeRangeList>();
        
        String ancestorPath = SVNPathUtil.getCommonPathAncestor(parent.absPath.getAbsolutePath().replace(File.separatorChar, '/'), child.absPath.getAbsolutePath().replace(File.separatorChar, '/')); 
        String childPath = SVNPathUtil.getPathAsChild(ancestorPath, child.absPath.getAbsolutePath().replace(File.separatorChar, '/'));
        if (childPath.startsWith("/")) {
            childPath = childPath.substring(1);
        }
        SVNMergeInfoUtil.adjustMergeInfoSourcePaths(child.implicitMergeInfo, childPath, parent.implicitMergeInfo);
    }
    
    
    private void ensureImplicitMergeinfo(MergePath parent, MergePath child, boolean childInheritsParent, long revision1, long revision2, SVNRepository repository) throws SVNException {
        if (child.implicitMergeInfo != null) {
            return;
        }
        if (childInheritsParent) {
            inheritImplicitMergeinfoFromParent(parent, child, revision1, revision2, repository);
        } else {
            boolean[] indirect = {false};
            Map[] mergeinfo = getFullMergeInfo(false, true, indirect, SVNMergeInfoInheritance.INHERITED, repository, child.absPath, Math.max(revision1, revision2), Math.min(revision1, revision2));
            child.implicitMergeInfo = mergeinfo[1];
        }
    }
    protected void findGapsInMergeSourceHistory(long[] gap, String mergeSrcCanonPath, SVNURL url1, long rev1, SVNURL url2, long rev2, SVNRepository repos) throws SVNException {
        long youngRev = Math.max(rev1, rev2);
        long oldRev = Math.min(rev1, rev2);
        SVNURL url = rev2 < rev1 ? url1 : url2;
        gap[0] = gap[1] = -1;
        SVNRevision pegRevision = SVNRevision.create(youngRev);
        
        SVNURL oldURL = null;
        if (repos != null) {
            oldURL = ensureSessionURL(repos, url);            
        }
        Map implicitSrcMergeInfo = null;
        try {
           implicitSrcMergeInfo = repositoryAccess.getHistoryAsMergeInfo(repos, SvnTarget.fromURL(url, pegRevision), youngRev, oldRev);
        } finally {
            if (repos != null && oldURL != null) {
                repos.setLocation(oldURL, false);
            }
        }
        SVNMergeRangeList rangelist = (SVNMergeRangeList) implicitSrcMergeInfo.get(mergeSrcCanonPath);
        if (rangelist != null) {
            if (rangelist.getSize() > 1) {
                gap[0] = Math.min(rev1, rev2);
                gap[1] = rangelist.getRanges()[rangelist.getSize() - 1].getStartRevision();
            } else if (implicitSrcMergeInfo.size() > 1) {
                SVNMergeRangeList implicitMergeRangeList = new SVNMergeRangeList(new SVNMergeRange[0]);
                SVNMergeRangeList requestedMergeRangeList = new SVNMergeRangeList(Math.min(rev1, rev2), Math.max(rev1, rev2), true);
                for(Iterator paths = implicitSrcMergeInfo.keySet().iterator(); paths.hasNext();) {
                    String path = (String) paths.next();
                    rangelist = (SVNMergeRangeList) implicitSrcMergeInfo.get(path);
                    implicitMergeRangeList = implicitMergeRangeList != null ? implicitMergeRangeList.merge(rangelist) : rangelist;
                }
                SVNMergeRangeList gapRangeList = requestedMergeRangeList.diff(implicitMergeRangeList, false);
                if (gapRangeList.getSize() > 0) {
                    gap[0] = gapRangeList.getRanges()[0].getStartRevision();
                    gap[1] = gapRangeList.getRanges()[0].getEndRevision();
                }
            }
        }
    }
    
    public void calculateRemainingRanges(MergePath parent, MergePath child, SVNURL sourceRootURL, SVNURL url1, long revision1, 
            SVNURL url2, long revision2, Map targetMergeInfo, SVNMergeRangeList implicitSrcGap, 
            boolean isSubtree, boolean childInheritsImplicit, SVNRepository repository) throws SVNException {
        SVNURL primaryURL = revision1 < revision2 ? url2 : url1;
        Map adjustedTargetMergeInfo = null;

        String mergeInfoPath = getPathRelativeToRoot(primaryURL, sourceRootURL, repository);
        if (implicitSrcGap != null && child.preMergeMergeInfo != null) {
            SVNMergeRangeList explicitMergeInfoGapRanges = (SVNMergeRangeList) child.preMergeMergeInfo.get(mergeInfoPath);
            if (explicitMergeInfoGapRanges != null) {
                Map gapMergeInfo = new TreeMap();
                gapMergeInfo.put(mergeInfoPath, implicitSrcGap);
                adjustedTargetMergeInfo = SVNMergeInfoUtil.removeMergeInfo(gapMergeInfo, targetMergeInfo, false);
            }
        } else {
            adjustedTargetMergeInfo = targetMergeInfo;
        }
        
        filterMergedRevisions(parent, child, repository, mergeInfoPath, 
                adjustedTargetMergeInfo, revision1, revision2, childInheritsImplicit); 
        
        if (isSubtree) {
            boolean isRollback = revision2 < revision1;
            if (isRollback) {
                child.remainingRanges.reverse();
                parent.remainingRanges.reverse();
            }
            SVNMergeRangeList[] rangeListDiff = SVNMergeInfoUtil.diffMergeRangeLists(child.remainingRanges, parent.remainingRanges, true);
            SVNMergeRangeList deletedRangeList = rangeListDiff[0];
            SVNMergeRangeList addedRangeList = rangeListDiff[1];
            if (isRollback) {
                child.remainingRanges.reverse();
                parent.remainingRanges.reverse();
            }
            if (!deletedRangeList.isEmpty() || !addedRangeList.isEmpty()) {
                adjustDeletedSubTreeRanges(child, parent, revision1, revision2, primaryURL, repository);
            }
        }

        long childBaseRevision = context.getNodeBaseRev(child.absPath);
        if (childBaseRevision >= 0 &&
                (child.remainingRanges == null || child.remainingRanges.isEmpty()) &&
                (revision2 < revision1) &&
                (childBaseRevision <= revision2)) {
            try {
                Structure<LocationsInfo> locations = repositoryAccess.getLocations(repository, SvnTarget.fromURL(url1), SVNRevision.create(revision1), SVNRevision.create(childBaseRevision), SVNRevision.UNDEFINED);
                SVNURL startURL = locations.get(LocationsInfo.startUrl);
                if (startURL.equals(context.getNodeUrl(child.absPath))) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_NOT_READY_TO_MERGE, 
                            "Cannot reverse-merge a range from a path's own future history; try updating first");
                    SVNErrorManager.error(err, SVNLogType.DEFAULT);
                }
            } catch (SVNException svne) {
                SVNErrorCode code = svne.getErrorMessage().getErrorCode();
                if (!(code == SVNErrorCode.FS_NOT_FOUND || code == SVNErrorCode.RA_DAV_PATH_NOT_FOUND || 
                        code == SVNErrorCode.CLIENT_UNRELATED_RESOURCES)) {
                    throw svne;
                }
            }
        }
    }
    private void adjustDeletedSubTreeRanges(MergePath child, MergePath parent, long revision1, long revision2, 
            SVNURL primaryURL, SVNRepository repository) throws SVNException {

        SVNErrorManager.assertionFailure(parent.remainingRanges != null, "parent must already have non-null remaining ranges set", SVNLogType.WC);

        String relativePath = getPathRelativeToRoot(primaryURL, repository.getLocation(), repository);
        if (relativePath.startsWith("/")) {
            relativePath = relativePath.substring(1);
        }
        
        boolean isRollback = revision2 < revision1;
        long pegRev = isRollback ? revision1 : revision2;
        long youngerRev = pegRev;
        long olderRev = isRollback ? revision2 : revision1;
        
        List locationSegments = null;
        try {
            locationSegments = repository.getLocationSegments(relativePath, pegRev, youngerRev, olderRev);
        } catch (SVNException e) {
            SVNErrorCode errCode = e.getErrorMessage().getErrorCode();
            if (errCode == SVNErrorCode.FS_NOT_FOUND || errCode == SVNErrorCode.RA_DAV_REQUEST_FAILED) {
                SVNNodeKind kind = repository.checkPath(relativePath, olderRev);
                if (kind == SVNNodeKind.NONE) {
                    child.remainingRanges = parent.remainingRanges.dup();
                } else {
                    long primaryURLDeletedRevision = repository.getDeletedRevision(relativePath, olderRev, youngerRev);
                    SVNErrorManager.assertionFailure(SVNRevision.isValidRevisionNumber(primaryURLDeletedRevision), "deleted revision must exist", SVNLogType.WC);
                    if (isRollback) {
                        child.remainingRanges = child.remainingRanges.reverse();
                        parent.remainingRanges = parent.remainingRanges.reverse();
                    }
                    
                    SVNMergeRangeList existingRangeList = new SVNMergeRangeList(new SVNMergeRange(olderRev, primaryURLDeletedRevision - 1, true));
                    child.remainingRanges = child.remainingRanges.intersect(existingRangeList, false);
                    
                    SVNMergeRangeList deletedRangeList = new SVNMergeRangeList(new SVNMergeRange(primaryURLDeletedRevision - 1, pegRev, true));
                    deletedRangeList = parent.remainingRanges.intersect(deletedRangeList, false);
                    child.remainingRanges = child.remainingRanges.merge(deletedRangeList);
                    
                    if (isRollback) {
                        child.remainingRanges = child.remainingRanges.reverse();
                        parent.remainingRanges = parent.remainingRanges.reverse();
                    }
                }
            } else {
                throw e;            
            }
        }
        
        if (locationSegments != null && !locationSegments.isEmpty()) {
            SVNLocationSegment segment = (SVNLocationSegment) locationSegments.get(locationSegments.size() - 1);
            if (segment.getStartRevision() == olderRev) {
                return;
            }
            if (isRollback) {
                child.remainingRanges = child.remainingRanges.reverse();
                parent.remainingRanges = parent.remainingRanges.reverse();
            }
            
            SVNMergeRangeList existingRangeList = new SVNMergeRangeList(new SVNMergeRange(segment.getStartRevision(), pegRev, true));
            child.remainingRanges = child.remainingRanges.intersect(existingRangeList, false);
            SVNMergeRangeList nonExistentRangeList = new SVNMergeRangeList(new SVNMergeRange(olderRev, segment.getStartRevision(), true));
            nonExistentRangeList = parent.remainingRanges.intersect(nonExistentRangeList, false);
            child.remainingRanges = child.remainingRanges.merge(nonExistentRangeList);

            if (isRollback) {
                child.remainingRanges = child.remainingRanges.reverse();
                parent.remainingRanges = parent.remainingRanges.reverse();
            }
        }
    }

    private void filterMergedRevisions(MergePath parent, MergePath child, SVNRepository repository, String mergeInfoPath, Map targetMergeInfo,
            long rev1, long rev2, boolean childInheritsImplicit) throws SVNException {
        SVNMergeRangeList targetRangeList = null;        
        SVNMergeRangeList targetImplicitRangeList = null;        
        SVNMergeRangeList explicitRangeList = null;        
        SVNMergeRangeList requestedMergeRangeList = new SVNMergeRangeList(new SVNMergeRange(rev1, rev2, true));


        if (rev1 > rev2) {
            requestedMergeRangeList = requestedMergeRangeList.reverse();
            if (targetMergeInfo != null) {
                targetRangeList = (SVNMergeRangeList) targetMergeInfo.get(mergeInfoPath);
            } else {
                targetRangeList = null;
            }

            if (targetRangeList != null) {
                explicitRangeList = targetRangeList.intersect(requestedMergeRangeList, false);
            } else {
                explicitRangeList = new SVNMergeRangeList(new SVNMergeRange[0]);
            }

            SVNMergeRangeList[] diff = SVNMergeInfoUtil.diffMergeRangeLists(requestedMergeRangeList, explicitRangeList, false);
            SVNMergeRangeList deletedRangeList = diff[0];
            
            if (deletedRangeList == null || deletedRangeList.isEmpty()) {
                requestedMergeRangeList = requestedMergeRangeList.reverse();
                child.remainingRanges = requestedMergeRangeList.dup();
            } else {
                SVNMergeRangeList implicitRangeList = null;
                ensureImplicitMergeinfo(parent, child, childInheritsImplicit, rev1, rev2, repository);
                targetImplicitRangeList = (SVNMergeRangeList) child.implicitMergeInfo.get(mergeInfoPath);
                
                if (targetImplicitRangeList != null) {
                    implicitRangeList = targetImplicitRangeList.intersect(requestedMergeRangeList, false);
                } else {
                    implicitRangeList = new  SVNMergeRangeList(new SVNMergeRange[0]);
                }
                implicitRangeList = implicitRangeList.merge(explicitRangeList);
                implicitRangeList = implicitRangeList.reverse();
                child.remainingRanges = implicitRangeList.dup();
            }
            
        } else {
            if (targetMergeInfo != null) {
                targetRangeList = (SVNMergeRangeList) targetMergeInfo.get(mergeInfoPath);
            } else {
                targetRangeList = null;
            }
            if (targetRangeList != null) {
                explicitRangeList = requestedMergeRangeList.remove(targetRangeList, false);
            } else {
                explicitRangeList = requestedMergeRangeList.dup();
            }
            if (explicitRangeList == null || explicitRangeList.isEmpty()) {
                child.remainingRanges = new SVNMergeRangeList(new SVNMergeRange[0]);
            } else {
                if (false /*TODO: diffOptions.isAllowAllForwardMergesFromSelf()*/) {
                    child.remainingRanges = explicitRangeList.dup();                
                } else {
                    ensureImplicitMergeinfo(parent, child, childInheritsImplicit, rev1, rev2, repository);
                    targetImplicitRangeList = (SVNMergeRangeList) child.implicitMergeInfo.get(mergeInfoPath);
                    if (targetImplicitRangeList != null) {
                        child.remainingRanges = explicitRangeList.remove(targetImplicitRangeList, false);
                    } else {
                        child.remainingRanges = explicitRangeList.dup();
                    }
                }
            }
        }
    }
    protected String getPathRelativeToRoot(SVNURL url, SVNURL reposRootURL, SVNRepository repos) throws SVNException {
        if (reposRootURL == null) {
            reposRootURL = repos.getRepositoryRoot(true);
        }
        String reposRootPath = reposRootURL.getPath();
        String absPath = url.getPath();
        if (!absPath.startsWith(reposRootPath)) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_UNRELATED_RESOURCES, "URL ''{0}'' is not a child of repository root URL ''{1}''", new Object[] {
                    url, reposRootURL
            });
            SVNErrorManager.error(err, SVNLogType.WC);
        }
        absPath = absPath.substring(reposRootPath.length());
        if (!absPath.startsWith("/")) {
            absPath = "/" + absPath;
        }
        return absPath;
    }

    private void sliceRemainingRanges(Map<File, MergePath> childrenWithMergeInfo, boolean isRollBack, long endRevision) {
        for (MergePath child:  childrenWithMergeInfo.values()) {
            if (child == null || child.absent) {
                continue;
            }
            
            if (!child.remainingRanges.isEmpty()) {
                SVNMergeRange[] originalRemainingRanges = child.remainingRanges.getRanges();
                SVNMergeRange range = originalRemainingRanges[0];
                if ((isRollBack && range.getStartRevision() > endRevision && 
                        range.getEndRevision() < endRevision) ||
                        (!isRollBack && range.getStartRevision() < endRevision && 
                                range.getEndRevision() > endRevision)) {
                    SVNMergeRange splitRange1 = new SVNMergeRange(range.getStartRevision(), endRevision, 
                            range.isInheritable());
                    SVNMergeRange splitRange2 = new SVNMergeRange(endRevision, range.getEndRevision(), 
                            range.isInheritable());
                    SVNMergeRange[] remainingRanges = new SVNMergeRange[originalRemainingRanges.length + 1];
                    remainingRanges[0] = splitRange1;
                    remainingRanges[1] = splitRange2;
                    System.arraycopy(originalRemainingRanges, 1, remainingRanges, 2, 
                            originalRemainingRanges.length - 1);
                    child.remainingRanges = new SVNMergeRangeList(remainingRanges);
                }
            }
        }
    }
    
    private long getMostInclusiveEndRevision(Map<File, MergePath> childrenWithMergeInfo, boolean isRollBack) {
        long endRev = SVNRepository.INVALID_REVISION;
        for (MergePath child : childrenWithMergeInfo.values()) {
            if (child == null || child.absent) {
                continue;
            }
            if (child.remainingRanges.getSize() > 0) {
                SVNMergeRange ranges[] = child.remainingRanges.getRanges();
                SVNMergeRange range = ranges[0];
                if (!SVNRevision.isValidRevisionNumber(endRev) || 
                        (isRollBack && range.getEndRevision() > endRev) ||
                        (!isRollBack && range.getEndRevision() < endRev)) {
                    endRev = range.getEndRevision();
                }
            }
        }
        return endRev;
    }

    private long getMostInclusiveStartRevision(Map<File, MergePath> childrenWithMergeInfo, boolean isRollBack) {
        long startRev = SVNRepository.INVALID_REVISION;
        boolean first = true;
        for (MergePath child: childrenWithMergeInfo.values()) {
            if (child == null || child.absent) {
                first = false;
                continue;
            }
            if (child.remainingRanges.isEmpty()) {
                first = false;
                continue;
            }
            SVNMergeRange ranges[] = child.remainingRanges.getRanges();
            SVNMergeRange range = ranges[0];
            if (first && range.getStartRevision() == range.getEndRevision()) {
                first = false;
                continue;
            }
            if (!SVNRevision.isValidRevisionNumber(startRev) || 
                    (isRollBack && range.getStartRevision() > startRev) ||
                    (!isRollBack && range.getStartRevision() < startRev)) {
                startRev = range.getStartRevision();
            }
            first = false;
        }
        return startRev;
    }

    private void processChildrenWithNewMergeInfo(Map<File, MergePath> childrenWithMergeInfo) throws SVNException {
        if (pathsWithNewMergeInfo != null && !dryRun) {
            for (Iterator<File> pathsIter = pathsWithNewMergeInfo.iterator(); pathsIter.hasNext();) {
                File pathWithNewMergeInfo = pathsIter.next();
                SvnMergeInfoInfo pathExplicitMergeInfo = SvnNgMergeinfoUtil.getWCMergeInfo(context, pathWithNewMergeInfo, targetAbsPath, SVNMergeInfoInheritance.EXPLICIT, false);
                
                SVNURL oldURL = null;
                if (pathExplicitMergeInfo != null) {
                    oldURL = ensureSessionURL(repos2, context.getNodeUrl(pathWithNewMergeInfo));
                    Map<String, SVNMergeRangeList> pathInheritedMergeInfo = SvnNgMergeinfoUtil.getWCOrReposMergeInfo(context, pathWithNewMergeInfo, repos2, false, SVNMergeInfoInheritance.NEAREST_ANCESTOR);
                    if (pathInheritedMergeInfo != null) {
                        pathExplicitMergeInfo.mergeinfo = SVNMergeInfoUtil.mergeMergeInfos(pathExplicitMergeInfo.mergeinfo, pathInheritedMergeInfo);
                        String value = SVNMergeInfoUtil.formatMergeInfoToString(pathExplicitMergeInfo.mergeinfo, "");
                        SvnNgPropertiesManager.setProperty(context, pathWithNewMergeInfo, SVNProperty.MERGE_INFO, SVNPropertyValue.create(value), SVNDepth.EMPTY, true, null, null);
                    }
                
                    MergePath newChild = new MergePath(pathWithNewMergeInfo);
                    if (!childrenWithMergeInfo.containsKey(newChild.absPath)) {
                        Object[] childrenWithMergeInfoArray = childrenWithMergeInfo.values().toArray();
                        int parentIndex = findNearestAncestor(childrenWithMergeInfoArray, false, pathWithNewMergeInfo);
                        MergePath parent = (MergePath) childrenWithMergeInfoArray[parentIndex];
                        newChild.remainingRanges = parent.remainingRanges.dup();
                        childrenWithMergeInfo.put(newChild.absPath, newChild);
                    }
                }
                
                if (oldURL != null) {
                    repos2.setLocation(oldURL, false);
                }
            }
        }
    }
    
    private void processChildrenWithDeletedMergeInfo(Map<File, MergePath> childrenWithMergeInfo) {
        if (pathsWithDeletedMergeInfo != null && !dryRun) {
            Iterator<MergePath> children = childrenWithMergeInfo.values().iterator();
            children.next(); // skip first.
            while(children.hasNext()) {
                MergePath path = children.next();
                if (path != null && pathsWithDeletedMergeInfo.contains(path.absPath)) {
                    children.remove();             
                }
            }
        }
    }
    
    private void removeFirstRangeFromRemainingRanges(long endRevision, Map<File, MergePath> childrenWithMergeInfo) {
        for (Iterator<MergePath> children = childrenWithMergeInfo.values().iterator(); children.hasNext();) {
            MergePath child = (MergePath) children.next();
            if (child == null || child.absent) {
                continue;
            }
            if (!child.remainingRanges.isEmpty()) {
                SVNMergeRange[] originalRemainingRanges = child.remainingRanges.getRanges();
                SVNMergeRange firstRange = originalRemainingRanges[0]; 
                if (firstRange.getEndRevision() == endRevision) {
                    SVNMergeRange[] remainingRanges = new SVNMergeRange[originalRemainingRanges.length - 1];
                    System.arraycopy(originalRemainingRanges, 1, remainingRanges, 0, 
                            originalRemainingRanges.length - 1);
                    child.remainingRanges = new SVNMergeRangeList(remainingRanges);
                }
            }
        }
    }

    private SVNErrorMessage makeMergeConflictError(File targetPath, SVNMergeRange range) {
        SVNErrorMessage error = SVNErrorMessage.create(SVNErrorCode.WC_FOUND_CONFLICT, 
                "One or more conflicts were produced while merging r{0}:{1} into\n" + 
                "''{2}'' --\n" +
                "resolve all conflicts and rerun the merge to apply the remaining\n" + 
                "unmerged revisions", new Object[] { Long.toString(range.getStartRevision()), 
                Long.toString(range.getEndRevision()), targetPath} );
        return error;
    }
/*
    protected void recordMergeInfoForDirectoryMerge(Map resultCatalog, SVNMergeRange range, String mergeInfoPath, SVNDepth depth) throws SVNException {
        
        boolean isRollBack = range.getStartRevision() > range.getEndRevision();
        removeAbsentChildren(myTarget, myChildrenWithMergeInfo);
  
        for (int i = 0; i < myChildrenWithMergeInfo.size(); i++) {
            MergePath child = (MergePath) myChildrenWithMergeInfo.get(i);
            if (child == null || child.myIsAbsent) {
                continue;
            }
            
            String childReposPath = null;
            if (child.myPath.equals(myTarget)) {
                childReposPath = ""; 
            } else {
                childReposPath = SVNPathUtil.getRelativePath(myTarget.getAbsolutePath(), 
                        child.myPath.getAbsolutePath());
            }
            
            SVNEntry childEntry = myWCAccess.getVersionedEntry(child.myPath, false);
            String childMergeSourcePath = SVNPathUtil.getAbsolutePath(SVNPathUtil.append(mergeInfoPath, 
                    childReposPath));
            
            SVNMergeRangeList childMergeRangeList = filterNaturalHistoryFromMergeInfo(childMergeSourcePath, 
                    child.myImplicitMergeInfo, range);
            if (childMergeRangeList.isEmpty()) {
                continue;
            } 
            if (i == 0) {
                recordSkips(mergeInfoPath, targetEntry, isRollBack);
            }
            calculateMergeInheritance(childMergeRangeList, childEntry, i == 0, child.myHasMissingChildren, depth);
            if (child.myIsIndirectMergeInfo) {
                SVNPropertiesManager.recordWCMergeInfo(child.myPath, child.myPreMergeMergeInfo, 
                        myWCAccess);
            }
            if (myImplicitSrcGap != null) {
                if (isRollBack) {
                    childMergeRangeList.reverse();
                }
                childMergeRangeList = childMergeRangeList.diff(myImplicitSrcGap, false);
                if (isRollBack) {
                    childMergeRangeList.reverse();
                }
            }
            Map childMerges = new TreeMap();
            childMerges.put(child.myPath, childMergeRangeList);
            updateWCMergeInfo(resultCatalog, child.myPath, childMergeSourcePath, childEntry, childMerges, isRollBack);

            if (i > 0) {
                boolean isInSwitchedSubTree = false;
                if (child.myIsSwitched) {
                    isInSwitchedSubTree = true;
                } else if (i > 1) {
                    for (int j = i - 1; j > 0; j--) {
                        MergePath parent = (MergePath) myChildrenWithMergeInfo.get(j);
                        if (parent != null && parent.myIsSwitched && 
                                SVNPathUtil.isAncestor(parent.myPath.getAbsolutePath().replace(File.separatorChar, '/'), 
                                child.myPath.getAbsolutePath().replace(File.separatorChar, '/'))) {
                            isInSwitchedSubTree = true;
                            break;
                        }
                    }
                }
                
                elideMergeInfo(myWCAccess, child.myPath, childEntry, isInSwitchedSubTree ? null : myTarget);
            }
        }
    }
    */
/*
    protected void recordMergeInfoForAddedSubtrees(SVNMergeRange range, String mergeInfoPath, SVNDepth depth) throws SVNException {
        if (myAddedPaths != null) {
            for (Iterator addedPathsIter = myAddedPaths.iterator(); addedPathsIter.hasNext();) {
                File addedPath = (File) addedPathsIter.next();
                SVNPropertyValue addedPathParentPropValue = SVNPropertiesManager.getProperty(myWCAccess, 
                        addedPath.getParentFile(), SVNProperty.MERGE_INFO);
                String addedPathParentPropValueStr = addedPathParentPropValue != null ? 
                        addedPathParentPropValue.getString() : null;
                if (addedPathParentPropValueStr != null && 
                        addedPathParentPropValueStr.indexOf(SVNMergeRangeList.MERGE_INFO_NONINHERITABLE_STRING) != -1) {
                    SVNEntry entry = myWCAccess.getVersionedEntry(addedPath, false);
                    Map mergeMergeInfo = new TreeMap();
                    MergePath targetMergePath = (MergePath) myChildrenWithMergeInfo.get(0);
                    
                    SVNMergeRange rng = range.dup();
                    if (entry.isFile()) {
                        rng.setInheritable(true);
                    } else {
                        rng.setInheritable(!(depth == SVNDepth.INFINITY || depth == SVNDepth.IMMEDIATES));
                    }

                    String addedPathStr = SVNPathUtil.validateFilePath(addedPath.getAbsolutePath());
                    String targetMergePathStr = SVNPathUtil.validateFilePath(targetMergePath.myPath.getAbsolutePath());
                    String commonAncestorPath = SVNPathUtil.getCommonPathAncestor(addedPathStr, targetMergePathStr);
                    String relativeAddedPath = SVNPathUtil.getRelativePath(commonAncestorPath, addedPathStr);
                    if (relativeAddedPath.startsWith("/")) {
                        relativeAddedPath = relativeAddedPath.substring(1);
                    }
                    
                    SVNMergeRangeList rangeList = new SVNMergeRangeList(rng);
                    mergeMergeInfo.put(SVNPathUtil.getAbsolutePath(SVNPathUtil.append(mergeInfoPath, 
                            relativeAddedPath)), rangeList);
                    boolean[] inherited = { false };
                    Map addedPathMergeInfo = getWCMergeInfo(addedPath, entry, null, 
                            SVNMergeInfoInheritance.EXPLICIT, false, inherited);
                    if (addedPathMergeInfo != null) {
                        mergeMergeInfo = SVNMergeInfoUtil.mergeMergeInfos(mergeMergeInfo, addedPathMergeInfo);
                    }
                    SVNPropertiesManager.recordWCMergeInfo(addedPath, mergeMergeInfo, myWCAccess);
                }
            }
        }
    }
*/
    private SVNMergeRangeList removeNoOpMergeRanges(SVNRepository repository, SVNMergeRangeList ranges) throws SVNException {
        long oldestRev = SVNRepository.INVALID_REVISION;
        long youngestRev = SVNRepository.INVALID_REVISION;
        
        SVNMergeRange[] mergeRanges = ranges.getRanges();
        for (int i = 0; i < ranges.getSize(); i++) {
            SVNMergeRange range = mergeRanges[i];
            long maxRev = Math.max(range.getStartRevision(), range.getEndRevision());
            long minRev = Math.min(range.getStartRevision(), range.getEndRevision());
            if (!SVNRevision.isValidRevisionNumber(youngestRev) || maxRev > youngestRev) {
                youngestRev = maxRev;
            }
            if (!SVNRevision.isValidRevisionNumber(oldestRev) || minRev < oldestRev) {
                oldestRev = minRev;
            }
        }
        
        final List changedRevs = new LinkedList();
        repository.log(new String[] { "" }, youngestRev, oldestRev, false, false, 0, false, new String[0], 
                new ISVNLogEntryHandler() {
            public void handleLogEntry(SVNLogEntry logEntry) throws SVNException {
                changedRevs.add(new Long(logEntry.getRevision()));
            }
        });
        
        long youngestChangedRevision = SVNRepository.INVALID_REVISION;
        long oldestChangedRevision = SVNRepository.INVALID_REVISION;
        if (changedRevs.size() > 0) {
            youngestChangedRevision = ((Long) changedRevs.get(0)).longValue();
            oldestChangedRevision = ((Long) changedRevs.get(changedRevs.size() - 1)).longValue();
        }
        
        List operativeRanges = new LinkedList();
        for (int i = 0; i < ranges.getSize(); i++) {
            SVNMergeRange range = mergeRanges[i];
            long rangeMinRev = Math.min(range.getStartRevision(), range.getEndRevision()) + 1;
            long rangeMaxRev = Math.max(range.getStartRevision(), range.getEndRevision());
            if (rangeMinRev > youngestChangedRevision || rangeMaxRev < oldestChangedRevision) {
                continue;
            }
            for (Iterator changedRevsIter = changedRevs.iterator(); changedRevsIter.hasNext();) {
                long changedRev = ((Long) changedRevsIter.next()).longValue();
                if (changedRev >= rangeMinRev && changedRev <= rangeMaxRev) {
                    operativeRanges.add(range);
                    break;
                }
            }
        }
        return SVNMergeRangeList.fromCollection(operativeRanges);
    }
    
    private void fixDeletedSubtreeRanges(SVNURL url1, long revision1, SVNURL url2, long revision2, SVNRepository repository) throws SVNException {
        boolean isRollback = revision2 < revision1;
        SVNURL sourceRootUrl = repository.getRepositoryRoot(true);
        Object[] array= childrenWithMergeInfo.values().toArray();
        
        for (MergePath child : childrenWithMergeInfo.values()) {
            if (child.absent) {
                continue;
            }
            int parentIndex = findNearestAncestor(array, false, child.absPath);
            MergePath parent = (MergePath) array[parentIndex];
            if (isRollback) {
                child.remainingRanges = child.remainingRanges.reverse();
                parent.remainingRanges = parent.remainingRanges.reverse();
            }
            
            SVNMergeRangeList added = child.remainingRanges.diff(parent.remainingRanges, true);
            SVNMergeRangeList deleted = parent.remainingRanges.diff(child.remainingRanges, true);
            
            if (isRollback) {
                child.remainingRanges = child.remainingRanges.reverse();
                parent.remainingRanges = parent.remainingRanges.reverse();
            }
            
            if (!added.isEmpty() || !deleted.isEmpty()) {
                String childReposSrcPath = SVNWCUtils.getPathAsChild(targetAbsPath, child.absPath);
                SVNURL childPrimarySrcUrl = revision1 < revision2 ? url2 : url1;
                childPrimarySrcUrl = childPrimarySrcUrl.appendPath(childReposSrcPath, false);
                adjustDeletedSubTreeRanges(child, parent, revision1, revision2, childPrimarySrcUrl, repository);
            }
        }
    }

    protected class MergePath implements Comparable {

        protected File absPath;
        protected boolean missingChild;
        protected boolean switched;
        protected boolean hasNonInheritable;
        protected boolean absent;
        protected boolean childOfNonInheritable;

        protected SVNMergeRangeList remainingRanges;
        protected Map<String, SVNMergeRangeList> preMergeMergeInfo;
        protected Map<String, SVNMergeRangeList> implicitMergeInfo;

        protected boolean inheritedMergeInfo;
        protected boolean scheduledForDeletion;
        protected boolean immediateChildDir;
        
        public MergePath() {
        }

        public MergePath(File path) {
            absPath = path;
        }
        
        public int compareTo(Object obj) {
            if (obj == null || obj.getClass() != MergePath.class) {
                return -1;
            }
            MergePath mergePath = (MergePath) obj; 
            if (this == mergePath) {
                return 0;
            }
            return absPath.compareTo(mergePath.absPath);
        }
        
        public boolean equals(Object obj) {
            return compareTo(obj) == 0;
        }
        
        public String toString() {
            return absPath.toString();
        }
    }
    
    private class NoopLogHandler implements ISVNLogEntryHandler {
        
        private SVNMergeRangeList operativeRanges;
        private SVNMergeRangeList mergedRanges;

        private String sourceReposAbsPath;

        public void handleLogEntry(SVNLogEntry logEntry) throws SVNException {
            operativeRanges = operativeRanges.mergeRevision(logEntry.getRevision());
            boolean logEntryRevisionRequired = false;
            long revision = logEntry.getRevision();
            
            for (String changedPath : logEntry.getChangedPaths().keySet()) {
                String relativePath = SVNPathUtil.getRelativePath(changedPath, changedPath);
                if (relativePath == null) {
                    continue;
                }
                File cwmiPath = SVNFileUtil.createFilePath(targetAbsPath, relativePath);
                SVNMergeRangeList pathExclplicitRangeList = null;
                boolean mergeInfoInherited = false;
                while(!logEntryRevisionRequired) {
                    MergePath child = childrenWithMergeInfo.get(cwmiPath);
                    if (child != null && child.preMergeMergeInfo != null) {
                        pathExclplicitRangeList = child.preMergeMergeInfo.get(changedPath);
                        break;
                    }
                    if (cwmiPath == null || cwmiPath.equals(targetAbsPath)) {
                        break;
                    }
                    cwmiPath = SVNFileUtil.getParentFile(cwmiPath);
                    changedPath = SVNPathUtil.removeTail(changedPath);
                    mergeInfoInherited = true;
                }
                if (pathExclplicitRangeList != null) {
                    SVNMergeRangeList rl = new SVNMergeRangeList(new SVNMergeRange(revision - 1, revision, true));
                    SVNMergeRangeList intersection = pathExclplicitRangeList.intersect(rl, true);
                    if (intersection.getSize() == 0) {
                        logEntryRevisionRequired = true;
                    }
                } else {
                    logEntryRevisionRequired = true;
                }
            }
            
            if (!logEntryRevisionRequired) {
                mergedRanges.mergeRevision(revision);
            }
        }
        
    }

    public void checkCancelled() throws SVNCancelException {
    }

    private int operativeNotifications;
    private int notifications;
    
    private Collection<File> addedPaths;
    private Collection<File> mergedPaths;
    private Collection<File> skippedPaths;
    private Collection<File> treeConflictedPaths;
    
    private static boolean isOperativeNotification(SVNEvent event) {
        if (event.getContentsStatus() == SVNStatusType.CONFLICTED
                || event.getContentsStatus() == SVNStatusType.MERGED 
                || event.getContentsStatus() == SVNStatusType.CHANGED
                || event.getPropertiesStatus() == SVNStatusType.CONFLICTED
                || event.getPropertiesStatus() == SVNStatusType.MERGED
                || event.getPropertiesStatus() == SVNStatusType.CHANGED
                || event.getAction() == SVNEventAction.UPDATE_ADD
                || event.getAction() == SVNEventAction.TREE_CONFLICT) {
            return true;
        }
        return false;
                
    }

    public void handleEvent(SVNEvent event, double progress) throws SVNException {
        if (recordOnly &&
                (event.getAction() != SVNEventAction.UPDATE_UPDATE && event.getAction() != SVNEventAction.RECORD_MERGE_BEGIN)) {
            return;
        }
        boolean operative = false;
        if (isOperativeNotification(event)) {
            operativeNotifications++;
            operative = true;
        }
        if (sourcesAncestral || reintegrateMerge) {
            if (event.getContentsStatus() == SVNStatusType.MERGED ||
                    event.getContentsStatus() == SVNStatusType.CHANGED ||
                    event.getPropertiesStatus() == SVNStatusType.MERGED ||
                    event.getPropertiesStatus() == SVNStatusType.CHANGED ||
                    event.getAction() == SVNEventAction.UPDATE_ADD) {
                if (mergedPaths == null) {
                    mergedPaths = new ArrayList<File>();
                }
                mergedPaths.add(event.getFile());
            }
            if (event.getAction() == SVNEventAction.SKIP) {
                if (skippedPaths == null) {
                    skippedPaths = new ArrayList<File>();
                }
                skippedPaths.add(event.getFile());
            }
            if (event.getAction() == SVNEventAction.TREE_CONFLICT) {
                if (treeConflictedPaths == null) {
                    treeConflictedPaths = new ArrayList<File>();
                }
                treeConflictedPaths.add(event.getFile());
            }
            if (event.getAction() == SVNEventAction.UPDATE_ADD) {
                boolean subtreeRoot;
                if (addedPaths == null) {
                    treeConflictedPaths = new ArrayList<File>();
                    subtreeRoot = true;
                } else {
                    subtreeRoot = !addedPaths.contains(SVNFileUtil.getFileDir(event.getFile()));
                }
                if (subtreeRoot) {
                    addedPaths.add(event.getFile());
                }
            }
        }
        
        if (sourcesAncestral) {
            notifications++;
            if (!singleFileMerge && operative) {
                Object[] array = childrenWithMergeInfo.values().toArray();
                int index = findNearestAncestor(array, event.getAction() != SVNEventAction.UPDATE_DELETE, event.getFile());
                if (index != currentAncestorIndex) {
                    MergePath child = (MergePath) array[index];
                    currentAncestorIndex = index;
                    if (!child.absent && !child.remainingRanges.isEmpty() && !(index == 0 && child.remainingRanges == null)) {
                        SVNEvent mergeBeginEvent = SVNEventFactory.createSVNEvent(child.absPath, 
                                SVNNodeKind.NONE, 
                                null, -1, sameRepos ? SVNEventAction.MERGE_BEGIN : SVNEventAction.FOREIGN_MERGE_BEGIN, 
                                        null, null, child.remainingRanges.getRanges()[0]);
                        context.getEventHandler().handleEvent(mergeBeginEvent, -1);
                    }
                }
            }
        } else if (!singleFileMerge && operativeNotifications == 1 && operative) {
            SVNEvent mergeBeginEvent = SVNEventFactory.createSVNEvent(targetAbsPath, 
                    SVNNodeKind.NONE, 
                    null, -1, sameRepos ? SVNEventAction.MERGE_BEGIN : SVNEventAction.FOREIGN_MERGE_BEGIN, 
                            null, null, null);
            context.getEventHandler().handleEvent(mergeBeginEvent, -1);
            
        }
        context.getEventHandler().handleEvent(event, -1);
    }
}
