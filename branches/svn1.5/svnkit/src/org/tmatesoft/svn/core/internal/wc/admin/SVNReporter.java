/*
 * ====================================================================
 * Copyright (c) 2004-2007 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.wc.admin;

import java.io.File;
import java.util.Iterator;

import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.util.SVNEncodingUtil;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNEventFactory;
import org.tmatesoft.svn.core.internal.wc.SVNExternalInfo;
import org.tmatesoft.svn.core.internal.wc.SVNFileType;
import org.tmatesoft.svn.core.io.ISVNReporter;
import org.tmatesoft.svn.core.io.ISVNReporterBaton;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.util.ISVNDebugLog;


/**
 * @version 1.1.1
 * @author  TMate Software Ltd.
 */
public class SVNReporter implements ISVNReporterBaton {

    private SVNAdminAreaInfo myInfo;
    private SVNDepth myDepth;
    private boolean myIsRestore;
    private File myTarget;
    private ISVNDebugLog myLog;

    public SVNReporter(SVNAdminAreaInfo info, File file, boolean restoreFiles, SVNDepth depth, ISVNDebugLog log) {
        myInfo = info;
        myDepth = depth;
        myIsRestore = restoreFiles;
        myLog = log;
        myTarget = file;
    }

    public void report(ISVNReporter reporter) throws SVNException {
        try {
            SVNAdminArea targetArea = myInfo.getTarget();
            SVNWCAccess wcAccess = myInfo.getWCAccess();
            SVNEntry targetEntry = wcAccess.getEntry(myTarget, false);
            if (targetEntry == null || (targetEntry.isDirectory() && targetEntry.isScheduledForAddition())) {
                SVNEntry parentEntry = wcAccess.getVersionedEntry(myTarget.getParentFile(), false);
                long revision = parentEntry.getRevision();
                if (myDepth == SVNDepth.DEPTH_UNKNOWN) {
                    myDepth = parentEntry.getDepth();
                }
                reporter.setPath("", null, revision, myDepth, targetEntry != null ? targetEntry.isIncomplete() : true);
                reporter.deletePath("");
                reporter.finishReport();
                return;
            }
            
            SVNEntry parentEntry = null;
            long revision = targetEntry.getRevision();
            if (!SVNRevision.isValidRevisionNumber(revision)) {
                parentEntry = wcAccess.getVersionedEntry(myTarget.getParentFile(), false);
                revision = parentEntry.getRevision();
            }
            if (myDepth == SVNDepth.DEPTH_UNKNOWN) {
                myDepth = targetEntry.getDepth();
            }
            reporter.setPath("", null, revision, myDepth, targetEntry.isIncomplete());
            
            SVNFileType fileType = SVNFileType.getType(myTarget);
            boolean missing = !targetEntry.isScheduledForDeletion() && fileType == SVNFileType.NONE;
            
            if (targetEntry.isDirectory()) {
                if (missing) {
                    reporter.deletePath("");
                } else {
                    /* TODO(sd): svn dev says: "Just passing depth here is not enough.  There
                     * can be circumstances where the root is depth 0 or 1,
                     * but some child directories are present at depth
                     * infinity.  We need to detect them and recurse into
                     * them *unless* there is a passed-in depth that is not
                     * infinity." - unfortunately, I don't understand why depth is not enough
                     */
                    reportEntries(reporter, targetArea, "", revision, targetEntry.isIncomplete(), myDepth);
                }
            } else if (targetEntry.isFile()) {
                if (missing) {
                    restoreFile(targetArea, targetEntry.getName());
                }
                // report either linked path or entry path
                parentEntry = parentEntry == null ? wcAccess.getEntry(myTarget.getParentFile(), false) : parentEntry;
                String url = targetEntry.getURL();
                String parentURL = parentEntry.getURL();
                String expectedURL = SVNPathUtil.append(parentURL, SVNEncodingUtil.uriEncode(targetEntry.getName()));
                if (parentEntry != null && !expectedURL.equals(url)) {
                    SVNURL svnURL = SVNURL.parseURIEncoded(url);
                    reporter.linkPath(svnURL, "", targetEntry.getLockToken(), targetEntry.getRevision(), targetEntry.getDepth(), false);
                } else if (targetEntry.getRevision() != revision || targetEntry.getLockToken() != null) {
                    reporter.setPath("", targetEntry.getLockToken(), targetEntry.getRevision(), targetEntry.getDepth(), false);
                }
            }
            reporter.finishReport();
        } catch (SVNException e) {
            try {
                reporter.abortReport();
            } catch (SVNException inner) {
                myLog.info(inner);
            }
            throw e;
        } catch (Throwable th) {
            myLog.info(th);
            try {
                reporter.abortReport();
            } catch (SVNException e) {
                myLog.info(e);
            }
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNKNOWN, "WC report failed: {0}", th.getMessage());
            SVNErrorManager.error(err, th);
        }
    }

    private void reportEntries(ISVNReporter reporter, SVNAdminArea adminArea, String dirPath, long dirRevision, boolean reportAll, SVNDepth depth) throws SVNException {
        SVNWCAccess wcAccess = myInfo.getWCAccess();
        SVNExternalInfo[] externals = myInfo.addExternals(adminArea, adminArea.getProperties(adminArea.getThisDirName()).getPropertyValue(SVNProperty.EXTERNALS));
        for (int i = 0; externals != null && i < externals.length; i++) {
            externals[i].setOldExternal(externals[i].getNewURL(), externals[i].getNewRevision());
        }

        String parentURL = adminArea.getEntry(adminArea.getThisDirName(), true).getURL();
        for (Iterator e = adminArea.entries(true); e.hasNext();) {
            SVNEntry entry = (SVNEntry) e.next();
            if (adminArea.getThisDirName().equals(entry.getName())) {
                continue;
            }
            String path = "".equals(dirPath) ? entry.getName() : SVNPathUtil.append(dirPath, entry.getName());
            if (entry.isDeleted() || entry.isAbsent()) {
                if (!reportAll) {
                    reporter.deletePath(path);
                }
                continue;
            }
            if (entry.isScheduledForAddition()) {
                continue;
            }
            File file = adminArea.getFile(entry.getName());
            SVNFileType fileType = SVNFileType.getType(file);
            boolean missing = fileType == SVNFileType.NONE;
            String expectedURL = SVNPathUtil.append(parentURL, SVNEncodingUtil.uriEncode(entry.getName()));

            if (entry.isFile()) {
                if (missing && !entry.isScheduledForDeletion() && !entry.isScheduledForReplacement()) {
                    restoreFile(adminArea, entry.getName());
                }
                String url = entry.getURL();
                if (reportAll) {
                    if (!url.equals(expectedURL)) {
                        SVNURL svnURL = SVNURL.parseURIEncoded(url);
                        reporter.linkPath(svnURL, path, entry.getLockToken(), entry.getRevision(), entry.getDepth(), false);
                    } else {
                        reporter.setPath(path, entry.getLockToken(), entry.getRevision(), entry.getDepth(), false);
                    }
                } else if (!entry.isScheduledForAddition() && !entry.isScheduledForReplacement() && !url.equals(expectedURL)) {
                    // link path
                    SVNURL svnURL = SVNURL.parseURIEncoded(url);
                    reporter.linkPath(svnURL, path, entry.getLockToken(), entry.getRevision(), entry.getDepth(), false);
                } else if (entry.getRevision() != dirRevision || entry.getDepth() != depth ||entry.getLockToken() != null) {
                    reporter.setPath(path, entry.getLockToken(), entry.getRevision(), entry.getDepth(), false);
                }
                /* TODO(sd): svn devs think "...it's correct to check whether
                 * 'depth == svn_depth_infinity' above.  If the
                 * specified depth is not infinity, then we don't want
                 * to recurse at all.  If it is, then we want recursion
                 * to be dependent on the subdirs' entries, right?..."
                 */ 
            } else if (entry.isDirectory() && depth == SVNDepth.DEPTH_INFINITY) {
                if (missing) {
                    if (!reportAll) {
                        reporter.deletePath(path);
                    }
                    continue;
                }
                SVNAdminArea childArea = wcAccess.retrieve(adminArea.getFile(entry.getName()));
                SVNEntry childEntry = childArea.getEntry(childArea.getThisDirName(), true);
                String url = childEntry.getURL();
                if (reportAll) {
                    if (!url.equals(expectedURL)) {
                        SVNURL svnURL = SVNURL.parseURIEncoded(url);
                        reporter.linkPath(svnURL, path, childEntry.getLockToken(), childEntry.getRevision(), childEntry.getDepth(), childEntry.isIncomplete());
                    } else {
                        reporter.setPath(path, childEntry.getLockToken(), childEntry.getRevision(), childEntry.getDepth(), childEntry.isIncomplete());
                    }
                } else if (!url.equals(expectedURL)) {
                    SVNURL svnURL = SVNURL.parseURIEncoded(url);
                    reporter.linkPath(svnURL, path, childEntry.getLockToken(), childEntry.getRevision(), childEntry.getDepth(), childEntry.isIncomplete());
                } else if (childEntry.getLockToken() != null || childEntry.getRevision() != dirRevision || childEntry.isIncomplete()) {
                    reporter.setPath(path, childEntry.getLockToken(), childEntry.getRevision(), childEntry.getDepth(), childEntry.isIncomplete());
                }

                //TODO(sd): review it later, seems to be ok. 
                if (depth == SVNDepth.DEPTH_INFINITY) {
                    reportEntries(reporter, childArea, path, childEntry.getRevision(), childEntry.isIncomplete(), depth);
                }
            }
        }
    }
    
    private void restoreFile(SVNAdminArea adminArea, String name) throws SVNException {
        if (!myIsRestore) {
            return;
        }
        adminArea.restoreFile(name);
        SVNEntry entry = adminArea.getEntry(name, true);
        myInfo.getWCAccess().handleEvent(SVNEventFactory.createRestoredEvent(myInfo, adminArea, entry));
    }
    
}
