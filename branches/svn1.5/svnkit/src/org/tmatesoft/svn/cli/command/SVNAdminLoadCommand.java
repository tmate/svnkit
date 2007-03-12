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
package org.tmatesoft.svn.cli.command;

import java.io.File;
import java.io.InputStream;
import java.io.PrintStream;

import org.tmatesoft.svn.cli.SVNArgument;
import org.tmatesoft.svn.cli.SVNCommand;
import org.tmatesoft.svn.core.SVNCancelException;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.wc.SVNEvent;
import org.tmatesoft.svn.core.wc.admin.ISVNAdminEventHandler;
import org.tmatesoft.svn.core.wc.admin.SVNAdminClient;
import org.tmatesoft.svn.core.wc.admin.SVNAdminEvent;
import org.tmatesoft.svn.core.wc.admin.SVNAdminEventAction;
import org.tmatesoft.svn.core.wc.admin.SVNUUIDAction;


/**
 * @version 1.1.1
 * @author  TMate Software Ltd.
 * @since   1.1.1
 */
public class SVNAdminLoadCommand extends SVNCommand implements ISVNAdminEventHandler {
    private boolean myIsQuiet;
    private PrintStream myOut;
    private boolean myIsNodeOpened;
    
    public void run(PrintStream out, PrintStream err) throws SVNException {
        run(System.in, out, err);
    }

    public void run(InputStream in, PrintStream out, PrintStream err) throws SVNException {
        if (!getCommandLine().hasPaths()) {
            SVNCommand.println(out, "jsvnadmin: Repository argument required");
            System.exit(1);
        }
        File reposRoot = new File(getCommandLine().getPathAt(0));  

        boolean ignoreUUID = getCommandLine().hasArgument(SVNArgument.IGNORE_UUID);
        boolean forceUUID = getCommandLine().hasArgument(SVNArgument.FORCE_UUID);
        SVNUUIDAction uuidAction = null;
        if (!ignoreUUID && !forceUUID) {
            uuidAction = SVNUUIDAction.DEFAULT;
        } else if (ignoreUUID) {
            uuidAction = SVNUUIDAction.IGNORE_UUID;
        } else {
            uuidAction = SVNUUIDAction.FORCE_UUID;
        }
        
        boolean usePreCommitHook = getCommandLine().hasArgument(SVNArgument.USE_PRECOMMIT_HOOK);
        boolean usePostCommitHook = getCommandLine().hasArgument(SVNArgument.USE_POSTCOMMIT_HOOK);
        String parentDir = (String) getCommandLine().getArgumentValue(SVNArgument.PARENT_DIR);

        myIsQuiet = getCommandLine().hasArgument(SVNArgument.QUIET); 
        myOut = out;

        SVNAdminClient adminClient = getClientManager().getAdminClient();
        adminClient.setEventHandler(this);
        adminClient.doLoad(reposRoot, in, usePreCommitHook, usePostCommitHook, uuidAction, parentDir);
    }

    public void handleAdminEvent(SVNAdminEvent event, double progress) throws SVNException {
        if (!myIsQuiet && event != null) {
            if (event.getAction() == SVNAdminEventAction.REVISION_LOAD) {
                myOut.println("<<< Started new transaction, based on original revision " + event.getOriginalRevision());
            } else if (event.getAction() == SVNAdminEventAction.REVISION_LOAD_EDIT_PATH) {
                if (myIsNodeOpened) {
                    myOut.println(" done.");
                }

                String path = event.getPath().startsWith("/") ? event.getPath().substring(1) : event.getPath(); 
                myOut.print("     * editing path : " + path + " ...");
                myIsNodeOpened = true;
            } else if (event.getAction() == SVNAdminEventAction.REVISION_LOAD_ADD_PATH) {
                if (myIsNodeOpened) {
                    myOut.println(" done.");
                }

                String path = event.getPath().startsWith("/") ? event.getPath().substring(1) : event.getPath(); 
                myOut.print("     * adding path : " + path + " ...");
                myIsNodeOpened = true;
            } else if (event.getAction() == SVNAdminEventAction.REVISION_LOAD_DELETE_PATH) {
                if (myIsNodeOpened) {
                    myOut.println(" done.");
                }
                
                String path = event.getPath().startsWith("/") ? event.getPath().substring(1) : event.getPath(); 
                myOut.print("     * deleting path : " + path + " ...");
                myIsNodeOpened = true;
            } else if (event.getAction() == SVNAdminEventAction.REVISION_LOAD_REPLACE_PATH) {
                if (myIsNodeOpened) {
                    myOut.println(" done.");
                }
                
                String path = event.getPath().startsWith("/") ? event.getPath().substring(1) : event.getPath(); 
                myOut.print("     * replacing path : " + path + " ...");
                myIsNodeOpened = true;
            } else if (event.getAction() == SVNAdminEventAction.REVISION_LOADED) {
                if (myIsNodeOpened) {
                    myOut.println(" done.");
                    myIsNodeOpened = false;
                }

                long rev = event.getRevision();
                long originalRev = event.getOriginalRevision();
                if (rev == originalRev) {
                    myOut.println("");
                    myOut.println("------- Committed revision " + rev + " >>>");
                    myOut.println("");
                } else {
                    myOut.println("");
                    myOut.println("------- Committed new rev " + rev + " (loaded from original rev " + originalRev + ") >>>");
                    myOut.println("");
                }
            }
        }
    }
    
    public void handleEvent(SVNEvent event, double progress) throws SVNException {
    }    
    
    public void checkCancelled() throws SVNCancelException {
    }
    
}
