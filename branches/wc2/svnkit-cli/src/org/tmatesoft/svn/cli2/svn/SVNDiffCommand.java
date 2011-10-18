/*
 * ====================================================================
 * Copyright (c) 2004-2007 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.cli2.svn;

import java.io.File;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import org.tmatesoft.svn.cli2.SVNCommandUtil;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.SVNPath;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.wc.DefaultSVNDiffGenerator;
import org.tmatesoft.svn.core.wc.ISVNDiffStatusHandler;
import org.tmatesoft.svn.core.wc.SVNChangelistClient;
import org.tmatesoft.svn.core.wc.SVNDiffClient;
import org.tmatesoft.svn.core.wc.SVNDiffStatus;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNStatusType;


/**
 * @version 1.1.2
 * @author  TMate Software Ltd.
 */
public class SVNDiffCommand extends SVNCommand implements ISVNDiffStatusHandler {

    public SVNDiffCommand() {
        super("diff", new String[] {"di"});
    }
    
    public boolean acceptsRevisionRange() {
        return true;
    }

    protected Collection createSupportedOptions() {
        Collection options = new LinkedList();
        options.add(SVNOption.REVISION);
        options.add(SVNOption.CHANGE);
        options.add(SVNOption.OLD);
        options.add(SVNOption.NEW);
        options.add(SVNOption.NON_RECURSIVE);
        options.add(SVNOption.DEPTH);
        options.add(SVNOption.EXTENSIONS);
        options.add(SVNOption.NO_DIFF_DELETED);
        options.add(SVNOption.NOTICE_ANCESTRY);
        options.add(SVNOption.SUMMARIZE);
        options.add(SVNOption.CHANGELIST);
        options.add(SVNOption.FORCE);
        options = SVNOption.addAuthOptions(options);
        options.add(SVNOption.CONFIG_DIR);
        return options;
    }

    public void run() throws SVNException {
        List targets = new ArrayList(); 
        if (getSVNEnvironment().getChangelist() != null) {
            SVNPath target = new SVNPath("");
            SVNChangelistClient changelistClient = getSVNEnvironment().getClientManager().getChangelistClient();
            changelistClient.getChangelist(target.getFile(), getSVNEnvironment().getChangelist(), targets);
            if (targets.isEmpty()) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNKNOWN_CHANGELIST, 
                        "Unknown changelist ''{0}''", getSVNEnvironment().getChangelist());
                SVNErrorManager.error(err);
            }
        }
        if (getSVNEnvironment().getTargets() != null) {
            targets.addAll(getSVNEnvironment().getTargets());
        }
        targets = getSVNEnvironment().combineTargets(targets);
        
        SVNPath oldTarget = null;
        SVNPath newTarget = null;
        SVNRevision start = getSVNEnvironment().getStartRevision();
        SVNRevision end = getSVNEnvironment().getEndRevision();
        boolean peggedDiff = false;
        
        if (targets.size() == 2 && 
                getSVNEnvironment().getOldTarget() == null && 
                getSVNEnvironment().getNewTarget() == null &&
                SVNCommandUtil.isURL((String) targets.get(0)) && 
                SVNCommandUtil.isURL((String) targets.get(1)) &&
                getSVNEnvironment().getStartRevision() == SVNRevision.UNDEFINED &&
                getSVNEnvironment().getEndRevision() == SVNRevision.UNDEFINED) {
            oldTarget = new SVNPath((String) targets.get(0), true);
            newTarget = new SVNPath((String) targets.get(1), true);
            start = oldTarget.getPegRevision();
            end = newTarget.getPegRevision();
            targets.clear();
            if (start == SVNRevision.UNDEFINED) {
                start = SVNRevision.HEAD;
            }
            if (end == SVNRevision.UNDEFINED) {
                end = SVNRevision.HEAD;
            }
        } else if (getSVNEnvironment().getOldTarget() != null) {
            targets.clear();
            targets.add(getSVNEnvironment().getOldTarget());
            targets.add(getSVNEnvironment().getNewTarget() != null ? getSVNEnvironment().getNewTarget() : getSVNEnvironment().getOldTarget());
            
            oldTarget = new SVNPath((String) targets.get(0), true);
            newTarget = new SVNPath((String) targets.get(1), true);
            start = getSVNEnvironment().getStartRevision();
            end = getSVNEnvironment().getEndRevision();
            if (oldTarget.getPegRevision() != SVNRevision.UNDEFINED) {
                start = oldTarget.getPegRevision();
            }
            if (newTarget.getPegRevision() != SVNRevision.UNDEFINED) {
                end = newTarget.getPegRevision();
            }
            if (start == SVNRevision.UNDEFINED) {
                start = oldTarget.isURL() ? SVNRevision.HEAD : SVNRevision.BASE;
            }
            if (end == SVNRevision.UNDEFINED) {
                end = newTarget.isURL() ? SVNRevision.HEAD : SVNRevision.WORKING;
            }
            targets = getSVNEnvironment().combineTargets(null);
        } else if (getSVNEnvironment().getNewTarget() != null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CL_ARG_PARSING_ERROR, 
                    "'--new' option only valid with '--old' option");
            SVNErrorManager.error(err);
        } else {
            if (targets.isEmpty()) {
                targets.add("");
            }
            oldTarget = new SVNPath("");
            newTarget = new SVNPath("");
            boolean hasURLs = false;
            boolean hasWCs = false;
            
            for(int i = 0; i < targets.size(); i++) {
                SVNPath target = new SVNPath((String) targets.get(i));
                hasURLs |= target.isURL();
                hasWCs |= target.isFile();
            }
            
            if (hasURLs && hasWCs) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNSUPPORTED_FEATURE, "Target lists to diff may not contain both working copy paths and URLs");
                SVNErrorManager.error(err);
            }
            start = getSVNEnvironment().getStartRevision();
            end = getSVNEnvironment().getEndRevision();
            if (start == SVNRevision.UNDEFINED && hasWCs) {
                start = SVNRevision.BASE;
            }
            if (end == SVNRevision.UNDEFINED) {
                end = hasWCs ? SVNRevision.WORKING : SVNRevision.HEAD;
            }
            peggedDiff = (start != SVNRevision.BASE && start != SVNRevision.WORKING) || (end != SVNRevision.BASE && end != SVNRevision.WORKING);
        }
        if (targets.isEmpty()) {
            targets.add("");
        }

        SVNDiffClient client = getSVNEnvironment().getClientManager().getDiffClient();
        DefaultSVNDiffGenerator diffGenerator = new DefaultSVNDiffGenerator();
        diffGenerator.setDiffOptions(getSVNEnvironment().getDiffOptions());
        diffGenerator.setDiffDeleted(!getSVNEnvironment().isNoDiffDeleted());
        diffGenerator.setForcedBinaryDiff(getSVNEnvironment().isForce());
        diffGenerator.setBasePath(new File("").getAbsoluteFile());
        client.setDiffGenerator(diffGenerator);
        
        PrintStream ps = getSVNEnvironment().getOut();
        for(int i = 0; i < targets.size(); i++) {
            String targetName = (String) targets.get(i);
            if (!peggedDiff) {
                SVNPath target1 = new SVNPath(SVNPathUtil.append(oldTarget.getTarget(), targetName));
                SVNPath target2 = new SVNPath(SVNPathUtil.append(newTarget.getTarget(), targetName));
                if (getSVNEnvironment().isSummarize()) {
                    if (target1.isURL() && target2.isURL()) {
                        client.doDiffStatus(target1.getURL(), start, target2.getURL(), end, getSVNEnvironment().getDepth(), getSVNEnvironment().isNoticeAncestry(), this);
                    } else if (target1.isURL()) {
                        client.doDiffStatus(target1.getURL(), start, target2.getFile(), end, getSVNEnvironment().getDepth(), getSVNEnvironment().isNoticeAncestry(), this);
                    } else if (target2.isURL()) {
                        client.doDiffStatus(target1.getFile(), start, target2.getURL(), end, getSVNEnvironment().getDepth(), getSVNEnvironment().isNoticeAncestry(), this);
                    } else {
                        client.doDiffStatus(target1.getFile(), start, target2.getFile(), end, getSVNEnvironment().getDepth(), getSVNEnvironment().isNoticeAncestry(), this);
                    }
                } else {
                    if (target1.isURL() && target2.isURL()) {
                        client.doDiff(target1.getURL(), start, target2.getURL(), end, getSVNEnvironment().getDepth(), getSVNEnvironment().isNoticeAncestry(), ps);
                    } else if (target1.isURL()) {
                        client.doDiff(target1.getURL(), start, target2.getFile(), end, getSVNEnvironment().getDepth(), getSVNEnvironment().isNoticeAncestry(), ps);
                    } else if (target2.isURL()) {
                        client.doDiff(target1.getFile(), start, target2.getURL(), end, getSVNEnvironment().getDepth(), getSVNEnvironment().isNoticeAncestry(), ps);
                    } else {
                        client.doDiff(target1.getFile(), start, target2.getFile(), end, getSVNEnvironment().getDepth(), getSVNEnvironment().isNoticeAncestry(), ps);
                    }
                }
            } else {
                SVNPath target = new SVNPath(targetName, true);
                SVNRevision pegRevision = target.getPegRevision();
                if (pegRevision == SVNRevision.UNDEFINED) {
                    pegRevision = target.isURL() ? SVNRevision.HEAD : SVNRevision.WORKING;
                }
                if (getSVNEnvironment().isSummarize()) {
                    if (target.isURL()) {
                        client.doDiffStatus(target.getURL(), start, end, pegRevision, getSVNEnvironment().getDepth(), getSVNEnvironment().isNoticeAncestry(), this);
                    } else {
                        client.doDiffStatus(target.getFile(), start, end, pegRevision, getSVNEnvironment().getDepth(), getSVNEnvironment().isNoticeAncestry(), this);
                    }
                } else {
                    if (target.isURL()) {
                        client.doDiff(target.getURL(), pegRevision, start, end, getSVNEnvironment().getDepth(), getSVNEnvironment().isNoticeAncestry(), ps);
                    } else {
                        client.doDiff(target.getFile(), pegRevision, start, end, getSVNEnvironment().getDepth(), getSVNEnvironment().isNoticeAncestry(), ps);
                    }
                }
            }
        }
    }

    public void handleDiffStatus(SVNDiffStatus diffStatus) throws SVNException {
        if (diffStatus.getModificationType() == SVNStatusType.STATUS_NONE &&
                !diffStatus.isPropertiesModified()) {
            return;
        }
        String path = diffStatus.getPath();
        if (!SVNCommandUtil.isURL(path)) {
            if (diffStatus.getFile() != null) {
                path = getSVNEnvironment().getRelativePath(diffStatus.getFile());
            }
            path = SVNCommandUtil.getLocalPath(path);
        }
        getSVNEnvironment().getOut().print(diffStatus.getModificationType().getCode() + (diffStatus.isPropertiesModified() ? "M" : " ") + "     " + path + "\n");
        getSVNEnvironment().getOut().flush();
    }

}