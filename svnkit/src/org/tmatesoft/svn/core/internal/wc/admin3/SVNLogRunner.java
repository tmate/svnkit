/*
 * ====================================================================
 * Copyright (c) 2004-2008 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.wc.admin3;

import java.io.File;
import java.util.Map;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;


/**
 * @version 1.1.2
 * @author  TMate Software Ltd.
 */
public class SVNLogRunner {
    
    private SVNWCAccess myWCAccess;
    
    public SVNLogRunner(SVNWCAccess wcAccess) {
        myWCAccess = wcAccess;
    }

    public void runCommand(String commandName, Map attrs) throws SVNException {
        if (!attrs.containsKey(SVNLog.NAME_ATTR) && !SVNLog.UPGRADE_FORMAT_TAG.equals(commandName)) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNKNOWN, 
                    "Log entry missing 'name' attribute (entry ''{0}'' for directory ''{1}'')", 
                    new Object[] {commandName, new File(myWCAccess.getPath())});
            SVNErrorManager.error(err);
        }
        try {
            if (SVNLog.MODIFY_ENTRY_TAG.equals(commandName)) {
                
            } else if (SVNLog.DELETE_CHANGELIST_TAG.equals(commandName)) {
                
            } else if (SVNLog.DELETE_ENTRY_TAG.equals(commandName)) {
                
            } else if (SVNLog.COMMITTED_TAG.equals(commandName)) {
                
            } else if (SVNLog.MODIFY_WCPROP_TAG.equals(commandName)) {
                
            } else if (SVNLog.RM_TAG.equals(commandName)) {
                
            } else if (SVNLog.MERGE_TAG.equals(commandName)) {
                
            } else if (SVNLog.MV_TAG.equals(commandName)) {
                
            } else if (SVNLog.CP_TAG.equals(commandName)) {
                
            } else if (SVNLog.CP_AND_TRANSALTE_TAG.equals(commandName)) {
                
            } else if (SVNLog.CP_AND_DETRANSLATE_TAG.equals(commandName)) {
                
            } else if (SVNLog.APPEND_TAG.equals(commandName)) {
                
            } else if (SVNLog.READONLY_TAG.equals(commandName)) {
                
            } else if (SVNLog.MAYBE_READONLY_TAG.equals(commandName)) {
                
            } else if (SVNLog.MAYBE_EXECUTABLE_TAG.equals(commandName)) {
                
            } else if (SVNLog.SET_TIMESTAMP_TAG.equals(commandName)) {
                
            } else if (SVNLog.UPGRADE_FORMAT_TAG.equals(commandName)) {
                
            } else {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNKNOWN, 
                        "Unrecognized logfile element ''{0}'' in ''{1}''", 
                        new Object[] {commandName, new File(myWCAccess.getPath())});
                SVNErrorManager.error(err);
            }
        } catch (SVNException e) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNKNOWN, 
                    "Error processing command ''{0}'' in ''{1}''", 
                    new Object[] {commandName, new File(myWCAccess.getPath())});
            SVNErrorManager.error(err, e.getErrorMessage());            
        }
    }

}
