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
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.internal.util.SVNDate;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;


/**
 * @version 1.1.2
 * @author  TMate Software Ltd.
 */
public class SVNLogRunner {
    
    private static final int XFER_CP                    = 0;
    private static final int XFER_MV                    = 1;
    private static final int XFER_APPEND                = 2;
    private static final int XFER_CP_AND_TRANSLATE      = 3;
    private static final int XFER_CP_AND_DETRANSLATE    = 4;
    
    private SVNWCAccess myWCAccess;
    private boolean myIsEntriesModified;
    
    public SVNLogRunner(SVNWCAccess wcAccess) {
        myWCAccess = wcAccess;
    }
    
    public boolean isEntriesModified() {
        return myIsEntriesModified;
    }

    public void runCommand(String commandName, Map attrs) throws SVNException {
        String name = (String) attrs.get(SVNLog.NAME_ATTR);
        if (name == null && !SVNLog.UPGRADE_FORMAT_TAG.equals(commandName)) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_BAD_ADM_LOG,  
                    "Log entry missing 'name' attribute (entry ''{0}'' for directory ''{1}'')", 
                    new Object[] {commandName, new File(myWCAccess.getPath())});
            SVNErrorManager.error(err);
        }
        try {
            if (SVNLog.MODIFY_ENTRY_TAG.equals(commandName)) {
                modifyEntry(name, attrs);
            } else if (SVNLog.DELETE_CHANGELIST_TAG.equals(commandName)) {
                
            } else if (SVNLog.DELETE_ENTRY_TAG.equals(commandName)) {
                
            } else if (SVNLog.COMMITTED_TAG.equals(commandName)) {
                
            } else if (SVNLog.MODIFY_WCPROP_TAG.equals(commandName)) {
                
            } else if (SVNLog.RM_TAG.equals(commandName)) {
                
            } else if (SVNLog.MERGE_TAG.equals(commandName)) {
                
            } else if (SVNLog.MV_TAG.equals(commandName)) {
                xfer(name, attrs, XFER_MV);
            } else if (SVNLog.CP_TAG.equals(commandName)) {
                xfer(name, attrs, XFER_CP);
            } else if (SVNLog.CP_AND_TRANSALTE_TAG.equals(commandName)) {
                xfer(name, attrs, XFER_CP_AND_TRANSLATE);
            } else if (SVNLog.CP_AND_DETRANSLATE_TAG.equals(commandName)) {
                xfer(name, attrs, XFER_CP_AND_DETRANSLATE);
            } else if (SVNLog.APPEND_TAG.equals(commandName)) {
                xfer(name, attrs, XFER_APPEND);
            } else if (SVNLog.READONLY_TAG.equals(commandName)) {
                readonly(name);
            } else if (SVNLog.MAYBE_READONLY_TAG.equals(commandName)) {
                
            } else if (SVNLog.MAYBE_EXECUTABLE_TAG.equals(commandName)) {
                
            } else if (SVNLog.SET_TIMESTAMP_TAG.equals(commandName)) {
                
            } else if (SVNLog.UPGRADE_FORMAT_TAG.equals(commandName)) {
                
            } else {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_BAD_ADM_LOG, 
                        "Unrecognized logfile element ''{0}'' in ''{1}''", 
                        new Object[] {commandName, new File(myWCAccess.getPath())});
                SVNErrorManager.error(err);
            }
        } catch (SVNException e) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_BAD_ADM_LOG, 
                    "Error processing command ''{0}'' in ''{1}''", 
                    new Object[] {commandName, new File(myWCAccess.getPath())});
            SVNErrorManager.error(err, e.getErrorMessage());            
        }
    }
    
    protected void modifyEntry(String entryName, Map attributes) throws SVNException {
        SVNEntry entry = new SVNEntry();
        String thisDirName = myWCAccess.getAdminArea().getThisDirName(myWCAccess);
        long flags = SVNEntry.loadFromMap(entry, thisDirName, attributes);
        
        String filePath = thisDirName.equals(entryName) ? 
                myWCAccess.getPath() : SVNPathUtil.append(myWCAccess.getPath(), entryName);
        
        String timeStr = (String) attributes.get(SVNEntry.TEXT_TIME);
        if ((flags & SVNEntry.FLAG_TEXT_TIME) != 0 && SVNLog.WC_TIMESTAMP.equals(timeStr)) {
            long time = new File(filePath).lastModified();
            entry.myTextTime = new SVNDate(time, 0);
        }
        timeStr = (String) attributes.get(SVNEntry.PROP_TIME);
        if ((flags & SVNEntry.FLAG_PROP_TIME) != 0 && SVNLog.WC_TIMESTAMP.equals(timeStr)) {
            SVNNodeKind kind = myWCAccess.getPath().equals(filePath) ? SVNNodeKind.DIR : SVNNodeKind.FILE;
            long time =
                myWCAccess.getAdminArea().propertiesLastModified(myWCAccess, filePath, kind, SVNAdminArea.WORKING_PROPERTIES, false);
            entry.myPropTime = new SVNDate(time, 0);
        }
        String size = (String) attributes.get(SVNEntry.WORKING_SIZE);
        if ((flags & SVNEntry.FLAG_WORKING_SIZE) != 0 && SVNLog.WC_WORKING_SIZE.equals(size)) {
            SVNEntry fileEntry = myWCAccess.getEntry(filePath, false);
            if (fileEntry == null) {
                return;
            }
            fileEntry.myWorkingSize = new File(filePath).length();
            if (fileEntry.myWorkingSize < 0) {
                fileEntry.myWorkingSize = 0;
            }
        }
        if (Boolean.TRUE.toString().equals(attributes.get(SVNLog.FORCE_ATTR))) {
            flags |= SVNEntry.FLAG_FORCE;
        }
        try {
            myWCAccess.modifyEntry(entryName, entry, flags, false);
        } catch (SVNException e) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_BAD_ADM_LOG, "Error modifying entry for ''{0}''", entryName);
            SVNErrorManager.error(err, e.getErrorMessage());
        }
        myIsEntriesModified = true;
    }
    
    protected void readonly(String name) {
        String path = getRealPath(name);
        SVNFileUtil.setReadonly(new File(path), true);
    }
    
    protected void xfer(String name, Map attributes, int action) throws SVNException {
        String dst = (String) attributes.get(SVNLog.DEST_ATTR);
        if (dst == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_BAD_ADM_LOG, "Missing ''dest'' attribute in ''{0}''", new File(getWCAccess().getPath()));
            SVNErrorManager.error(err);
        }
        boolean special = attributes.get(SVNLog.ARG1_ATTR) != null;
        String versioned = (String) attributes.get(SVNLog.ARG2_ATTR);
        xfer(name, dst, versioned, action, special);
    }
    
    protected SVNWCAccess getWCAccess() {
        return myWCAccess;
    }
    
    protected String getRealPath(String logPath) {
        return getWCAccess().getAdminArea().getLayout().getRealLogPath(getWCAccess(), logPath);
    }
    
    private void xfer(String name, String dst, String versioned, int action, boolean specialOnly) throws SVNException {
        String fullSrc = getRealPath(name);
        String fullDst = getRealPath(dst);
        String fullVersioned = versioned != null ? getRealPath(versioned) : null;
        
        switch (action) {
            case XFER_MV:
                SVNFileUtil.rename(new File(fullSrc), new File(fullDst));
                break;

            case XFER_CP:
            case XFER_APPEND:
            case XFER_CP_AND_TRANSLATE:
            case XFER_CP_AND_DETRANSLATE:
            default:
                break;
        }
    }

}
