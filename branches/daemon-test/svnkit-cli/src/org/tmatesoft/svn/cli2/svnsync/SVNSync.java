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
package org.tmatesoft.svn.cli2.svnsync;

import org.tmatesoft.svn.cli2.AbstractSVNCommandEnvironment;
import org.tmatesoft.svn.cli2.AbstractSVNLauncher;
import org.tmatesoft.svn.cli2.SVNCommandLine;


/**
 * @version 1.1.2
 * @author  TMate Software Ltd.
 */
public class SVNSync extends AbstractSVNLauncher {

    public static void main(String[] args) {
        new SVNSync().run(args);
    }

    protected AbstractSVNCommandEnvironment createCommandEnvironment() {
        return new SVNSyncCommandEnvironment(getProgramName(), System.out, System.err, System.in);
    }

    protected String getProgramName() {
        return "jsvnsync";
    }

    protected boolean needArgs() {
        return true;
    }

    protected boolean needCommand() {
        return true;
    }

    protected void registerCommands() {
    }

    protected void registerOptions() {
        SVNCommandLine.registerOption(SVNSyncOption.HELP);
        SVNCommandLine.registerOption(SVNSyncOption.QUESTION);
        SVNCommandLine.registerOption(SVNSyncOption.VERSION);
        SVNCommandLine.registerOption(SVNSyncOption.CONFIG_DIR);
        SVNCommandLine.registerOption(SVNSyncOption.SYNC_PASSWORD);
        SVNCommandLine.registerOption(SVNSyncOption.SYNC_USERNAME);
        SVNCommandLine.registerOption(SVNSyncOption.SOURCE_PASSWORD);
        SVNCommandLine.registerOption(SVNSyncOption.SOURCE_USERNAME);
        SVNCommandLine.registerOption(SVNSyncOption.USERNAME);
        SVNCommandLine.registerOption(SVNSyncOption.PASSWORD);
        SVNCommandLine.registerOption(SVNSyncOption.NO_AUTH_CACHE);
        SVNCommandLine.registerOption(SVNSyncOption.NON_INTERACTIVE);
    }
}