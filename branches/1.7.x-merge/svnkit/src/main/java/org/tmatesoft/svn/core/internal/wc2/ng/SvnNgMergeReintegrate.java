package org.tmatesoft.svn.core.internal.wc2.ng;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNMergeRangeList;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.util.SVNURLUtil;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNFileType;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc17.SVNWCContext;
import org.tmatesoft.svn.core.internal.wc17.db.Structure;
import org.tmatesoft.svn.core.internal.wc2.SvnRepositoryAccess.RepositoryInfo;
import org.tmatesoft.svn.core.io.SVNLocationSegment;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.wc2.ISvnObjectReceiver;
import org.tmatesoft.svn.core.wc2.SvnGetProperties;
import org.tmatesoft.svn.core.wc2.SvnMerge;
import org.tmatesoft.svn.core.wc2.SvnTarget;
import org.tmatesoft.svn.util.SVNLogType;

public class SvnNgMergeReintegrate extends SvnNgOperationRunner<Void, SvnMerge>{

    @Override
    protected Void run(SVNWCContext context) throws SVNException {
        return null;
    }
    
    private void merge(SVNWCContext context, SvnTarget mergeSource, File mergeTarget, boolean dryRun, MergeOptions options) throws SVNException {
        SVNFileType targetKind = SVNFileType.getType(mergeTarget);
        if (targetKind == SVNFileType.NONE) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_PATH_NOT_FOUND, "Path ''{0}'' does not exist", mergeTarget);
            SVNErrorManager.error(err, SVNLogType.WC);
        }     
        
        SVNURL url2 = getRepositoryAccess().getTargetURL(mergeSource);
        if (url2 == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_MISSING_URL, "''{0}'' has no URL", mergeTarget);
            SVNErrorManager.error(err, SVNLogType.WC);
        }
        
        SVNURL wcReposRoot = context.getNodeReposInfo(mergeTarget).reposRootUrl;
        Structure<RepositoryInfo> sourceReposInfo = getRepositoryAccess().createRepositoryFor(mergeSource, mergeSource.getPegRevision(), mergeSource.getPegRevision(), null);
        SVNURL sourceReposRoot = ((SVNRepository) sourceReposInfo.get(RepositoryInfo.repository)).getRepositoryRoot(true);
        
        if (!wcReposRoot.equals(sourceReposRoot)) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_UNRELATED_RESOURCES, 
                    "''{0}'' must be from the same repositor as ''{1}''", mergeSource.getURL(), mergeTarget);
            SVNErrorManager.error(err, SVNLogType.WC);
        }
        // TODO ensure wc is single-revision
        long targetBaseRev = context.getNodeBaseRev(mergeTarget);
        long rev1 = targetBaseRev;
        File sourceReposRelPath = new File(SVNURLUtil.getRelativeURL(wcReposRoot, url2));
        File targetReposRelPath = context.getNodeReposRelPath(mergeTarget);
        
        if ("".equals(sourceReposRelPath.getPath()) || "".equals(targetReposRelPath.getPath())) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_NOT_READY_TO_MERGE, 
                    "Neither reintegrate source nor target can be the root of repository");
            SVNErrorManager.error(err, SVNLogType.WC);
        }
        final Map<File, String> explicitMergeInfo = new HashMap<File, String>();
        SvnGetProperties pg = getOperation().getOperationFactory().createGetProperties();
        pg.setDepth(SVNDepth.INFINITY);
        pg.setSingleTarget(SvnTarget.fromFile(mergeTarget));
        pg.setReceiver(new ISvnObjectReceiver<SVNProperties>() {
            public void receive(SvnTarget target, SVNProperties props) throws SVNException {
                final String value = props.getStringValue(SVNProperty.MERGE_INFO);
                if (value != null) {
                    explicitMergeInfo.put(target.getFile(), value);
                }
            }
        });
        
        sourceReposInfo = getRepositoryAccess().createRepositoryFor(SvnTarget.fromURL(url2), null, mergeSource.getPegRevision(), null);
        SVNRepository sourceRepository = sourceReposInfo.get(RepositoryInfo.repository);
        long rev2 = sourceReposInfo.lng(RepositoryInfo.revision);
        url2 = sourceReposInfo.get(RepositoryInfo.url);
        sourceReposInfo.release();
        
        SVNURL targetUrl = context.getNodeUrl(mergeTarget);
        SVNRepository targetRepository = getRepositoryAccess().createRepository(targetUrl, null, false);
        //
        
        SvnTarget url1 = calculateLeftHandSide(context,
                new HashMap<String, Map<String,SVNMergeRangeList>>(),
                new HashMap<String, Map<String,SVNMergeRangeList>>(), 
                mergeTarget,
                targetReposRelPath,
                explicitMergeInfo,
                targetBaseRev,
                sourceReposRelPath,
                sourceReposRoot,
                wcReposRoot,
                rev2,
                sourceRepository,
                targetRepository);
        
        if (url1 == null) {
            return;
        }
        
        if (!url1.equals(targetUrl)) {
            targetRepository.setLocation(url1.getURL(), false);
        }
        rev1 = url1.getPegRevision().getNumber();
        SVNLocationSegment yc = getRepositoryAccess().getYoungestCommonAncestor(url2, rev2, url1.getURL(), rev1);
        
        if (yc == null || !(yc.getPath() != null && yc.getStartRevision() >= 0)) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_NOT_READY_TO_MERGE, 
                    "'{0}'@'{1}' must be ancestrally related to '{2}'@'{3}'", url1, new Long(rev1), url2, new Long(rev2));
            SVNErrorManager.error(err, SVNLogType.WC);
        }
        
        if (rev1 > yc.getStartRevision()) {
            // TODO check already merged revs for continuosity.
        }
        
        // TODO real merge of cousins, supplement mergeinfo.
        sleepForTimestamp();
        
    }
    
    private SvnTarget calculateLeftHandSide(SVNWCContext context,
            Map<String, Map<String, SVNMergeRangeList>>  mergedToSourceCatalog,
            Map<String, Map<String, SVNMergeRangeList>>  unmergedToSourceCatalog,
            File targetAbsPath,
            File targetReposRelPath,
            Map<File, String> subtreesWithMergeInfo,
            long targetRev,
            File sourceReposRelPath,
            SVNURL sourceReposRoot,
            SVNURL targetReposRoot,
            long sourceRev,
            SVNRepository sourceRepository,
            SVNRepository targetRepository)  throws SVNException {
        return null;
    }

    private static class MergeOptions {
        
    }

}
