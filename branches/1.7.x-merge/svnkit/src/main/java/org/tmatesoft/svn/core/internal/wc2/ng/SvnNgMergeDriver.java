package org.tmatesoft.svn.core.internal.wc2.ng;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNMergeRange;
import org.tmatesoft.svn.core.SVNMergeRangeList;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.SVNCancellableEditor;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc.admin.SVNAdminArea;
import org.tmatesoft.svn.core.internal.wc17.SVNWCContext;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.ISVNReporter;
import org.tmatesoft.svn.core.io.ISVNReporterBaton;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.wc.SVNDiffOptions;
import org.tmatesoft.svn.core.wc2.SvnMerge;
import org.tmatesoft.svn.util.SVNDebugLog;

public class SvnNgMergeDriver {
    
    private boolean dryRun;
    private File targetAbsPath;
    private SVNWCContext context;
    
    private Collection<File> dryRunDeletions;
    private Collection<File> dryRunAdditions;
    
    private Collection<File> pathsWithDeletedMergeInfo;
    private Collection<File> pathsWithAddedMergeInfo;

    private boolean recordOnly;
    private boolean sourcesAncestral;
    
    private long mergeSource2Rev;
    private SVNURL mergeSource2Url;
    private long mergeSource1Rev;
    private SVNURL mergeSource1Url;
    
    private boolean sameRepos;
    private boolean ignoreAncestry;
    private boolean mergeinfoCapable;
    private boolean reintegrateMerge;
    
    private SVNRepository repos1;
    private SVNRepository repos2;
    private SVNDiffOptions diffOptions;
    private Collection<File> conflictedPaths;
    private File addedPath;
    private SVNURL reposRootUrl;
    private boolean force;
    
    private SvnMerge operation;

    public ISVNEditor driveMergeReportEditor(File targetWCPath, SVNURL url1, long revision1,
            SVNURL url2, final long revision2, final List<MergePath> childrenWithMergeInfo, final boolean isRollBack, 
            SVNDepth depth, SVNAdminArea adminArea, SvnNgMergeCallback mergeCallback) throws SVNException {
        final boolean honorMergeInfo = isHonorMergeInfo();
        long targetStart = revision1;
        
        if (honorMergeInfo) {
            targetStart = revision2;
            if (childrenWithMergeInfo != null && !childrenWithMergeInfo.isEmpty()) {                
                MergePath targetMergePath = (MergePath) childrenWithMergeInfo.get(0);
                SVNMergeRangeList remainingRanges = targetMergePath.myRemainingRanges; 
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

        SvnNgRemoteDiffEditor editor = SvnNgRemoteDiffEditor.createEditor(context, targetAbsPath, depth, repos2, revision1, false, dryRun, mergeCallback);

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
                                   if (childMergePath == null || childMergePath.myIsAbsent) {
                                       continue;
                                   }
                                   Object[] childrenWithMergeInfoArray = childrenWithMergeInfo.toArray();
                                   int parentIndex = findNearestAncestor(childrenWithMergeInfoArray, false, childMergePath.myPath);
                                   if (parentIndex >= 0 && parentIndex < childrenWithMergeInfoArray.length) {
                                       parent = (MergePath) childrenWithMergeInfoArray[parentIndex];
                                   }
                                   
                                   SVNMergeRange range = null;
                                   if (childMergePath.myRemainingRanges != null && 
                                           !childMergePath.myRemainingRanges.isEmpty()) {
                                       SVNMergeRangeList remainingRangesList = childMergePath.myRemainingRanges; 
                                       SVNMergeRange[] remainingRanges = remainingRangesList.getRanges();
                                       range = remainingRanges[0];
                                       
                                       if ((!isRollBack && range.getStartRevision() > revision2) ||
                                               (isRollBack && range.getStartRevision() < revision2)) {
                                           continue;
                                       } else if (parent.myRemainingRanges != null && !parent.myRemainingRanges.isEmpty()) {
                                           SVNMergeRange parentRange = parent.myRemainingRanges.getRanges()[0];
                                           SVNMergeRange childRange = childMergePath.myRemainingRanges.getRanges()[0];
                                           if (parentRange.getStartRevision() == childRange.getStartRevision()) {
                                               continue;
                                           }
                                       }
                                   } else {
                                       if (parent.myRemainingRanges == null || parent.myRemainingRanges.isEmpty()) {
                                           continue;
                                       }
                                   }
                                     
                                   String childPath = childMergePath.myPath.getAbsolutePath();
                                   childPath = childPath.replace(File.separatorChar, '/');
                                   String relChildPath = childPath.substring(targetPath.length());
                                   if (relChildPath.startsWith("/")) {
                                       relChildPath = relChildPath.substring(1);
                                   }
                                   
                                   if (childMergePath.myRemainingRanges == null || 
                                           childMergePath.myRemainingRanges.isEmpty() ||
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
            String childPath = child.myPath.getAbsolutePath().replace(File.separatorChar, '/');
            String pathStr = path.getAbsolutePath().replace(File.separatorChar, '/');
            if (SVNPathUtil.isAncestor(childPath, pathStr) && (!childPath.equals(pathStr) || pathIsOwnAncestor)) {
                ancestorIndex = i;
            }
        }
        return ancestorIndex;
    }
    
    protected class MergePath implements Comparable {
        protected File myPath;
        protected boolean myHasMissingChildren;
        protected boolean myIsSwitched;
        protected boolean myHasNonInheritableMergeInfo;
        protected boolean myIsAbsent;
        protected boolean myIsIndirectMergeInfo;
        protected boolean myIsScheduledForDeletion;
        public SVNMergeRangeList myRemainingRanges;
        protected Map myPreMergeMergeInfo;
        protected Map myImplicitMergeInfo;
        
        public MergePath() {
        }

        public MergePath(File path) {
            myPath = path;
        }
        
        public int compareTo(Object obj) {
            if (obj == null || obj.getClass() != MergePath.class) {
                return -1;
            }
            MergePath mergePath = (MergePath) obj; 
            if (this == mergePath) {
                return 0;
            }
            return myPath.compareTo(mergePath.myPath);
        }
        
        public boolean equals(Object obj) {
            return compareTo(obj) == 0;
        }
        
        public String toString() {
            return myPath.toString();
        }
    }
}
