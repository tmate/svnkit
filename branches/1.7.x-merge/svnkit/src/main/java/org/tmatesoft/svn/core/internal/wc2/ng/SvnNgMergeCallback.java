package org.tmatesoft.svn.core.internal.wc2.ng;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNMergeRange;
import org.tmatesoft.svn.core.SVNMergeRangeList;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.util.SVNMergeInfoUtil;
import org.tmatesoft.svn.core.internal.wc.SVNFileType;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc17.SVNWCContext;
import org.tmatesoft.svn.core.internal.wc17.SVNWCContext.ConflictInfo;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.SVNWCDbKind;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.SVNWCDbStatus;
import org.tmatesoft.svn.core.internal.wc17.db.Structure;
import org.tmatesoft.svn.core.internal.wc17.db.StructureFields.NodeInfo;
import org.tmatesoft.svn.core.internal.wc2.SvnRepositoryAccess;
import org.tmatesoft.svn.core.internal.wc2.SvnRepositoryAccess.LocationsInfo;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNStatusType;
import org.tmatesoft.svn.core.wc2.SvnTarget;

public class SvnNgMergeCallback implements ISvnDiffCallback {
    
    private boolean dryRun;
    private File targetAbsPath;
    private SVNWCContext context;
    
    private Collection<File> dryRunDeletions;
    private Collection<File> dryRunAdditions;
    
    private boolean recordOnly;
    private boolean sourcesAncestral;
    private long mergeSource2Rev;
    private long mergeSource1Rev;
    private boolean sameRepos;
    private boolean ignoreAncestry;
    private boolean mergeinfoCapable;
    private boolean reintegrateMerge;
    
    private SVNRepository repos2;

    public void fileOpened(SvnDiffCallbackResult result, File path, long revision) throws SVNException {
        // do nothing
    }

    public void fileChanged(SvnDiffCallbackResult result, File path,
            File tmpFile1, File tmpFile2, long rev1, long rev2,
            String mimetype1, String mimeType2, SVNProperties propChanges,
            SVNProperties originalProperties) throws SVNException {
        ObstructionState os = performObstructionCheck(path, SVNNodeKind.UNKNOWN);
        if (os.obstructionState != SVNStatusType.INAPPLICABLE) {
            result.contentState = os.obstructionState;
            if (os.obstructionState == SVNStatusType.MISSING) {
                result.propState = SVNStatusType.MISSING;
            }
            return;
        }
        SVNNodeKind wcKind = os.kind;
        boolean isDeleted = os.deleted;
        
        if (wcKind != SVNNodeKind.FILE  || isDeleted) {
            if (wcKind == SVNNodeKind.NONE) {
                SVNDepth parentDepth = context.getNodeDepth(SVNFileUtil.getParentFile(path));
                if (parentDepth != SVNDepth.UNKNOWN && parentDepth.compareTo(SVNDepth.FILES) < 0) {
                    result.contentState = SVNStatusType.MISSING;
                    result.propState = SVNStatusType.MISSING;
                    return;
                }
            }
            // TODO create tree conflict
            result.treeConflicted = true;
            result.contentState = SVNStatusType.MISSING;
            result.propState = SVNStatusType.MISSING;
            return;
        }
        
        if (!propChanges.isEmpty()) {
            boolean treeConflicted2 = false;
            mergePropChanges(path, propChanges, originalProperties);
//            merge
        }
    }

    public void fileAdded(SvnDiffCallbackResult result, File path,
            File tmpFile1, File tmpFile2, long rev1, long rev2,
            String mimetype1, String mimeType2, File copyFromPath,
            long copyFromRevision, SVNProperties propChanges,
            SVNProperties originalProperties) throws SVNException {
    }

    public void fileDeleted(SvnDiffCallbackResult result, File path,
            File tmpFile1, File tmpFile2, String mimetype1, String mimeType2,
            SVNProperties originalProperties) throws SVNException {
    }

    public void dirDeleted(SvnDiffCallbackResult result, File path)
            throws SVNException {
    }

    public void dirOpened(SvnDiffCallbackResult result, File path, long revision)
            throws SVNException {
    }

    public void dirAdded(SvnDiffCallbackResult result, File path,
            long revision, String copyFromPath, long copyFromRevision)
            throws SVNException {
    }

    public void dirPropsChanged(SvnDiffCallbackResult result, File path,
            boolean isAdded, SVNProperties propChanges,
            SVNProperties originalProperties) throws SVNException {
    }

    public void dirClosed(SvnDiffCallbackResult result, File path, boolean isAdded) throws SVNException {
    }
    
    private boolean isDryRunAddition(File path) {
        return dryRun && dryRunAdditions != null && dryRunAdditions.contains(path);
    }
    
    private boolean isDryRunDeletion(File path) {
        return dryRun && dryRunDeletions != null && dryRunDeletions.contains(path);
    }
    
    private void mergePropChanges(File localAbsPath, SVNProperties propChanges, SVNProperties originalProperties) throws SVNException {
        SVNProperties props = new SVNProperties();
        SvnNgPropertiesManager.categorizeProperties(propChanges, props, null, null);
        
        if (recordOnly && !props.isEmpty()) {
            SVNProperties mergeinfoProps = new SVNProperties();
            if (props.containsName(SVNProperty.MERGE_INFO)) {
                mergeinfoProps.put(SVNProperty.MERGE_INFO, props.getStringValue(SVNProperty.MERGE_INFO));
            }
            props = mergeinfoProps;
        }
        
        if (!props.isEmpty()) {
            if (mergeSource1Rev < mergeSource2Rev || !sourcesAncestral) {
                props = filterSelfReferentialMergeInfo(props, localAbsPath, isHonorMergeInfo(), sameRepos, reintegrateMerge, repos2);
            }
        }
    }
    
    private SVNProperties filterSelfReferentialMergeInfo(SVNProperties props, File localAbsPath, boolean honorMergeInfo, boolean sameRepos,
            boolean reintegrateMerge, SVNRepository repos) throws SVNException {
        if (!sameRepos) {
            return omitMergeInfoChanges(props);
        }
        if (!honorMergeInfo && !reintegrateMerge) {
            return props;
        }
        
        boolean isAdded = context.isNodeAdded(localAbsPath);
        if (isAdded) {
            return props;
        }
        long baseRevision = context.getNodeBaseRev(localAbsPath);        
        SVNProperties adjustedProps = new SVNProperties();
        
        for (String propName : props.nameSet()) {
            if (!SVNProperty.MERGE_INFO.equals(propName) || props.getSVNPropertyValue(propName) == null || "".equals(props.getSVNPropertyValue(propName))) {
                adjustedProps.put(propName, props.getSVNPropertyValue(propName));
                continue;
            }
            SVNURL targetUrl = context.getUrlFromPath(localAbsPath);
            SVNURL oldUrl = repos.getLocation();
            repos.setLocation(targetUrl, false);
            String mi = props.getStringValue(propName);
            
            Map<String, SVNMergeRangeList> mergeinfo = null;
            Map<String, SVNMergeRangeList> filteredYoungerMergeinfo = null;
            Map<String, SVNMergeRangeList> filteredMergeinfo = null;
            
            try {
                mergeinfo = SVNMergeInfoUtil.parseMergeInfo(new StringBuffer(mi), null);
            } catch (SVNException e) {
                adjustedProps.put(propName, props.getSVNPropertyValue(propName));
                if (e.getErrorMessage().getErrorCode() == SVNErrorCode.MERGE_INFO_PARSE_ERROR) {
                    repos.setLocation(oldUrl, false);
                    continue;
                }
                throw e;
            }
            Map<String, SVNMergeRangeList>[] splitted = splitMergeInfoOnRevision(mergeinfo, baseRevision);
            Map<String, SVNMergeRangeList> youngerMergeInfo = splitted[0];
            mergeinfo = splitted[1];
            
            if (youngerMergeInfo != null) {
                SVNURL mergeSourceRootUrl = repos.getRepositoryRoot(true);
                
                for (Iterator<String> youngerMergeInfoIter = youngerMergeInfo.keySet().iterator(); youngerMergeInfoIter.hasNext();) {
                    String sourcePath = youngerMergeInfoIter.next();
                    SVNMergeRangeList rangeList = (SVNMergeRangeList) youngerMergeInfo.get(sourcePath);
                    SVNMergeRange ranges[] = rangeList.getRanges();
                    List<SVNMergeRange> adjustedRanges = new ArrayList<SVNMergeRange>();
                    
                    SVNURL mergeSourceURL = mergeSourceRootUrl.appendPath(sourcePath, false);
                    for (int i = 0; i < ranges.length; i++) {
                        SVNMergeRange range = ranges[i];
                        Structure<LocationsInfo> locations = null;
                        try {
                            locations = new SvnNgRepositoryAccess(null, context).getLocations(
                                    repos, 
                                    SvnTarget.fromURL(targetUrl), 
                                    SVNRevision.create(baseRevision), 
                                    SVNRevision.create(range.getStartRevision() + 1), 
                                    SVNRevision.UNDEFINED);
                            SVNURL startURL = locations.get(LocationsInfo.startUrl);
                            if (!mergeSourceURL.equals(startURL)) {
                                adjustedRanges.add(range);
                            }
                            locations.release();
                        } catch (SVNException svne) {
                            SVNErrorCode code = svne.getErrorMessage().getErrorCode();
                            if (code == SVNErrorCode.CLIENT_UNRELATED_RESOURCES || 
                                    code == SVNErrorCode.RA_DAV_PATH_NOT_FOUND ||
                                    code == SVNErrorCode.FS_NOT_FOUND ||
                                    code == SVNErrorCode.FS_NO_SUCH_REVISION) {
                                adjustedRanges.add(range);
                            } else {
                                throw svne;
                            }
                        }
                    }

                    if (!adjustedRanges.isEmpty()) {
                        if (filteredYoungerMergeinfo == null) {
                            filteredYoungerMergeinfo = new TreeMap<String, SVNMergeRangeList>();
                        }
                        SVNMergeRangeList adjustedRangeList = SVNMergeRangeList.fromCollection(adjustedRanges); 
                        filteredYoungerMergeinfo.put(sourcePath, adjustedRangeList);
                    }
                }
            }
            if (mergeinfo != null && !mergeinfo.isEmpty()) {
                
                Map<String, SVNMergeRangeList> implicitMergeInfo = 
                        new SvnNgRepositoryAccess(null, context).getHistoryAsMergeInfo(repos2, SvnTarget.fromFile(localAbsPath),  
                                baseRevision, -1);                         
                filteredMergeinfo = SVNMergeInfoUtil.removeMergeInfo(implicitMergeInfo, mergeinfo, true);
            }
            
            if (oldUrl != null) {
                repos.setLocation(oldUrl, false);
            }
            
            if (filteredMergeinfo != null && filteredYoungerMergeinfo != null) {
                filteredMergeinfo = SVNMergeInfoUtil.mergeMergeInfos(filteredMergeinfo, filteredYoungerMergeinfo);
            } else if (filteredYoungerMergeinfo != null) {
                filteredMergeinfo = filteredYoungerMergeinfo;
            }

            if (filteredMergeinfo != null && !filteredMergeinfo.isEmpty()) {
                String filteredMergeInfoStr = SVNMergeInfoUtil.formatMergeInfoToString(filteredMergeinfo, null);
                adjustedProps.put(SVNProperty.MERGE_INFO, filteredMergeInfoStr);
            }
        }
        
        return adjustedProps;
    }

    private Map<String, SVNMergeRangeList>[] splitMergeInfoOnRevision(Map<String, SVNMergeRangeList> mergeinfo, long revision) {
        Map<String, SVNMergeRangeList> youngerMergeinfo = null;
        for (String path : new HashSet<String>(mergeinfo.keySet())) {
            SVNMergeRangeList rl = mergeinfo.get(path);
            for (int i = 0; i < rl.getSize(); i++) {
                SVNMergeRange r = rl.getRanges()[i];
                if (r.getEndRevision() <= revision) {
                    continue;
                } else {
                    SVNMergeRangeList youngerRl = new SVNMergeRangeList(new SVNMergeRange[0]);
                    for (int j = 0; j < rl.getSize(); j++) {
                        SVNMergeRange r2 = rl.getRanges()[j];
                        SVNMergeRange youngerRange = r2.dup();
                        if (i == j && r.getStartRevision() + 1 <= revision) {
                            youngerRange.setStartRevision(revision);
                            r.setEndRevision(revision);
                        }
                        youngerRl.pushRange(youngerRange.getStartRevision(), youngerRange.getEndRevision(), youngerRange.isInheritable());
                    }
                    
                    if (youngerMergeinfo == null) {
                        youngerMergeinfo = new TreeMap<String, SVNMergeRangeList>();
                    }
                    youngerMergeinfo.put(path, youngerRl);
                    mergeinfo = SVNMergeInfoUtil.removeMergeInfo(youngerMergeinfo, mergeinfo, true);
                    break;
                }
            }
        }
        return new Map[] {youngerMergeinfo, mergeinfo};
    }

    private SVNProperties omitMergeInfoChanges(SVNProperties props) {
        SVNProperties result = new SVNProperties();
        for (String name : props.nameSet()) {
            if (SVNProperty.MERGE_INFO.equals(name)) {
                continue;
            }
            SVNPropertyValue pv = props.getSVNPropertyValue(name);
            result.put(name, pv);
        }
        return result;
    }

    private boolean isHonorMergeInfo() {
        return sourcesAncestral && sameRepos && !ignoreAncestry && mergeinfoCapable;
    }
    
    private ObstructionState performObstructionCheck(File localAbsPath, SVNNodeKind expectedKind) throws SVNException {
        ObstructionState result = new ObstructionState();
        result.obstructionState = SVNStatusType.INAPPLICABLE;
        result.kind = SVNNodeKind.NONE;

        if (dryRun) {
            if (isDryRunDeletion(localAbsPath)) {
                result.deleted = true;
                if (expectedKind != SVNNodeKind.UNKNOWN &&
                        expectedKind != SVNNodeKind.NONE) {
                    result.obstructionState = SVNStatusType.OBSTRUCTED;
                }
                return result;
            } else if (isDryRunAddition(localAbsPath)) {
                result.added = true;
                result.kind = SVNNodeKind.DIR;
                return result;
            }
        }
        
        boolean checkRoot = !localAbsPath.equals(targetAbsPath);
        checkWcForObstruction(result, localAbsPath, checkRoot);
        if (result.obstructionState == SVNStatusType.INAPPLICABLE &&
                expectedKind != SVNNodeKind.UNKNOWN &&
                result.kind != expectedKind) {
            result.obstructionState = SVNStatusType.OBSTRUCTED;
        }
        return result;
    }
    
    private void checkWcForObstruction(ObstructionState result, File localAbsPath, boolean noWcRootCheck) throws SVNException {
        result.kind = SVNNodeKind.NONE;
        result.obstructionState = SVNStatusType.INAPPLICABLE;
        SVNFileType diskKind = SVNFileType.getType(localAbsPath);
        SVNWCDbStatus status = null;
        SVNWCDbKind dbKind = null;
        boolean conflicted = false;
        try {
            Structure<NodeInfo> info = context.getDb().readInfo(localAbsPath, NodeInfo.status, NodeInfo.kind, NodeInfo.conflicted);
            status = info.get(NodeInfo.status);
            dbKind = info.get(NodeInfo.kind);
            conflicted = info.is(NodeInfo.conflicted);
            
            info.release();
        } catch (SVNException e) {
            if (e.getErrorMessage().getErrorCode() != SVNErrorCode.WC_PATH_NOT_FOUND) {
                throw e;
            }
            if (diskKind != SVNFileType.NONE) {
                result.obstructionState = SVNStatusType.OBSTRUCTED;
                return;
            }
            
            try {
                Structure<NodeInfo> parentInfo = context.getDb().readInfo(SVNFileUtil.getParentFile(localAbsPath), NodeInfo.status, NodeInfo.kind);
                ISVNWCDb.SVNWCDbStatus parentStatus = parentInfo.get(NodeInfo.status);
                ISVNWCDb.SVNWCDbKind parentDbKind = parentInfo.get(NodeInfo.kind);
                if (parentDbKind != SVNWCDbKind.Dir ||
                        (parentStatus != SVNWCDbStatus.Normal &&
                        parentStatus != SVNWCDbStatus.Added)) {
                    result.obstructionState = SVNStatusType.OBSTRUCTED;
                }
                parentInfo.release();
            } catch (SVNException e2) {
                if (e2.getErrorMessage().getErrorCode() == SVNErrorCode.WC_PATH_NOT_FOUND) {
                    result.obstructionState = SVNStatusType.OBSTRUCTED;
                    return;
                }
                throw e;
            }
            return;
        }
        if (!noWcRootCheck && dbKind == SVNWCDbKind.Dir && status == SVNWCDbStatus.Normal) {
            boolean isRoot = context.getDb().isWCRoot(localAbsPath);
            if (isRoot) {
                result.obstructionState = SVNStatusType.OBSTRUCTED;
                return;
            }
        }
        result.kind = dbKind.toNodeKind();
        switch (status) {
        case Deleted:
            result.deleted = true;
        case NotPresent:
            if (diskKind != SVNFileType.NONE) {
                result.obstructionState = SVNStatusType.OBSTRUCTED;
            }
            break;
        case Excluded:
        case ServerExcluded:
        case Incomplete:
            result.obstructionState = SVNStatusType.MISSING;            
            break;
        case Added:
            result.added = true;
        case Normal:
            if (diskKind == SVNFileType.NONE) {
                result.obstructionState = SVNStatusType.MISSING;
            } else {
                SVNNodeKind expectedKind = dbKind.toNodeKind();
                if (SVNFileType.getNodeKind(diskKind) != expectedKind) {
                    result.obstructionState = SVNStatusType.OBSTRUCTED;            
                }
            }
        }
        
        if (conflicted) {
            ConflictInfo ci = context.getConflicted(localAbsPath, true, true, true);
            result.conflicted = ci != null && (ci.propConflicted || ci.textConflicted || ci.treeConflicted);
        }
    }
    
    private static class ObstructionState {
        SVNStatusType obstructionState;
        boolean added;
        boolean deleted;
        boolean conflicted;
        SVNNodeKind kind;
    }
    
}
