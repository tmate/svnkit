/*
 * ====================================================================
 * Copyright (c) 2004 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://tmate.org/svn/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */

package org.tmatesoft.svn.cli.command;

import java.io.File;
import java.io.PrintStream;

import org.tmatesoft.svn.cli.SVNArgument;
import org.tmatesoft.svn.cli.SVNCommand;
import org.tmatesoft.svn.core.io.SVNException;
import org.tmatesoft.svn.core.io.SVNNodeKind;
import org.tmatesoft.svn.core.wc.ISVNEventListener;
import org.tmatesoft.svn.core.wc.SVNEvent;
import org.tmatesoft.svn.core.wc.SVNEventAction;
import org.tmatesoft.svn.core.wc.SVNEventStatus;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNUpdateClient;
import org.tmatesoft.svn.util.DebugLog;

/**
 * @author TMate Software Ltd.
 */
public class SwitchCommand extends SVNCommand {

    public void run(final PrintStream out, final PrintStream err) throws SVNException {
        String url = getCommandLine().getURL(0);
        String absolutePath = getCommandLine().getPathAt(0);

        long revNumber = parseRevision(getCommandLine(), null, null);
        SVNRevision revision = SVNRevision.HEAD;
        if (revNumber >= 0) {
            revision = SVNRevision.create(revNumber);
        }
        SVNUpdateClient updater = new SVNUpdateClient(getCredentialsProvider(), new ISVNEventListener() {
            private boolean isExternal = false;
            private boolean isChanged = false;
            private boolean isExternalChanged = false;
            
            public void svnEvent(SVNEvent event) {
                if (event.getAction() == SVNEventAction.UPDATE_ADD) {
                    if (isExternal) {
                        isExternalChanged = true;
                    } else {
                        isChanged = true;
                    }
                    println(out, "A    " + getPath(event.getFile()));
                } else if (event.getAction() == SVNEventAction.UPDATE_DELETE) {
                    if (isExternal) {
                        isExternalChanged = true;
                    } else {
                        isChanged = true;
                    }
                    println(out, "D    " + getPath(event.getFile()));
                } else if (event.getAction() == SVNEventAction.UPDATE_UPDATE) {
                    StringBuffer sb = new StringBuffer();
                    if (event.getNodeKind() != SVNNodeKind.DIR) {
                        if (event.getContentsStatus() == SVNEventStatus.CHANGED) {
                            sb.append("U");
                        } else if (event.getContentsStatus() == SVNEventStatus.CONFLICTED) {
                            sb.append("C");
                        } else if (event.getContentsStatus() == SVNEventStatus.MERGED) {
                            sb.append("G");
                        } else {
                            sb.append(" ");
                        }
                    } else {
                        sb.append(' ');
                    }
                    if (event.getPropertiesStatus() == SVNEventStatus.CHANGED) {
                        sb.append("U");
                    } else if (event.getPropertiesStatus() == SVNEventStatus.CONFLICTED) {
                        sb.append("C");
                    } else if (event.getPropertiesStatus() == SVNEventStatus.CONFLICTED) {
                        sb.append("M");
                    } else {
                        sb.append(" ");
                    }
                    if (sb.toString().trim().length() != 0) {
                        if (isExternal) {
                            isExternalChanged = true;
                        } else {
                            isChanged = true;
                        }
                    }
                    if (event.getLockStatus() == SVNEventStatus.LOCK_UNLOCKED) {
                        sb.append("B");
                    } else {
                        sb.append(" ");
                    } 
                    println(out, sb.toString() + " " + getPath(event.getFile()));
                } else if (event.getAction() == SVNEventAction.UPDATE_COMPLETED) {                    
                    if (!isExternal) {
                        if (isChanged) {
                            println(out, "Updated to revision " + event.getRevision() + ".");
                        } else {
                            println(out, "At revision " + event.getRevision() + ".");
                        }
                    } else {
                        if (isExternalChanged) {
                            println(out, "Updated external to revision " + event.getRevision() + ".");
                        } else {
                            println(out, "External at revision " + event.getRevision() + ".");
                        }
                        isExternalChanged = false;
                        isExternal = false;
                    }
                    println(out);
                } else if (event.getAction() == SVNEventAction.UPDATE_EXTERNAL) {
                    println(out);
                    println(out, "Updating external item at '" + event.getPath() + "'");
                    isExternal = true;
                }
            }
        });
        try {
            if (getCommandLine().hasArgument(SVNArgument.RELOCATE)) {
                updater.doRelocate(new File(absolutePath).getAbsoluteFile(), url, getCommandLine().getURL(1), !getCommandLine().hasArgument(SVNArgument.NON_RECURSIVE));
            } else {
                updater.doSwitch(new File(absolutePath).getAbsoluteFile(), url, revision, !getCommandLine().hasArgument(SVNArgument.NON_RECURSIVE));
            }
        } catch (Throwable th) {
            DebugLog.error(th);
            println(err, th.getMessage());
            println(err);
            System.exit(1);
        }
    }
}
