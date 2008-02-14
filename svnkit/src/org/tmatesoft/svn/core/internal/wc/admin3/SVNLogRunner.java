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

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.HashMap;
import java.util.Map;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.internal.util.SVNDate;
import org.tmatesoft.svn.core.internal.util.SVNEncodingUtil;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.IOExceptionWrapper;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc.admin.SVNTranslator;
import org.tmatesoft.svn.core.internal.wc.admin.SVNTranslatorOutputStream;


/**
 * @version 1.2
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
    
    public void run(InputStream log) throws SVNException {
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new InputStreamReader(log, "UTF-8"));
            String line;
            Map attrs = new HashMap();
            String commandName = null;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.startsWith("<")) {
                    commandName = line.substring(1);
                    continue;
                }
                int index = line.indexOf('=');
                if (index > 0) {
                    String attrName = line.substring(0, index).trim();
                    String value = line.substring(index + 1).trim();
                    if (value.endsWith("/>")) {
                        value = value.substring(0, value.length() - "/>".length());
                    }
                    if (value.startsWith("\"")) {
                        value = value.substring(1);
                    }
                    if (value.endsWith("\"")) {
                        value = value.substring(0, value.length() - 1);
                    }
                    value = SVNEncodingUtil.xmlDecode(value);
                    if ("".equals(value) && !SVNLog.NAME_ATTR.equals(attrName)) {
                        value = null;
                    }
                    attrs.put(attrName, value);
                }
                if (line.endsWith("/>") && commandName != null) {
                    // run command
                    runCommand(commandName, attrs);
                    attrs.clear();
                    commandName = null;
                }
            }
        } catch (IOException e) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, "Cannot read log file");
            SVNErrorManager.error(err, e);
        } finally {
            SVNFileUtil.closeFile(reader);
        }
    }


    protected void runCommand(String commandName, Map attrs) throws SVNException {
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
        
        String timeStr = (String) attributes.get(SVNEntry.TEXT_TIME);
        if ((flags & SVNEntry.FLAG_TEXT_TIME) != 0 && SVNLog.WC_TIMESTAMP.equals(timeStr)) {
            long time = lastModified(entryName);
            entry.myTextTime = new SVNDate(time, 0);
        }
        timeStr = (String) attributes.get(SVNEntry.PROP_TIME);
        // this path is not 'absolute' but a path in a WC tree, it would never refer 
        // to the file below admin area.
        String filePath = SVNPathUtil.append(getWCAccess().getPath(), thisDirName.equals(entryName) ? "" : entryName);
        if ((flags & SVNEntry.FLAG_PROP_TIME) != 0 && SVNLog.WC_TIMESTAMP.equals(timeStr)) {
            SVNNodeKind kind = thisDirName.equals(entryName) ? SVNNodeKind.DIR : SVNNodeKind.FILE;
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
            fileEntry.myWorkingSize = length(entryName);
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
        setReadonly(name, true);
    }
    
    protected void xfer(String name, Map attributes, int action) throws SVNException {
        String dst = (String) attributes.get(SVNLog.DEST_ATTR);
        if (dst == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_BAD_ADM_LOG, "Missing ''dest'' attribute in ''{0}''", new File(getWCAccess().getPath()));
            SVNErrorManager.error(err);
        }
        String versioned = (String) attributes.get(SVNLog.ARG2_ATTR);
        xfer(name, dst, versioned, action);
    }
    
    private void xfer(String name, String dst, String versioned, int action) throws SVNException {
        InputStream is = null;
        OutputStream os = null;

        switch (action) {
            case XFER_MV:
                rename(name, dst);
                break;
            case XFER_CP:
                copy(name, dst);
                break;
            case XFER_APPEND:
                try {
                    is = read(name);
                    os = write(dst);
                    while(true) {
                        int r = is.read();
                        if (r < 0) {
                            break;
                        }
                        os.write(r);                        
                    }
                } catch (IOException e) {
                    if (exists(name)) {
                        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, "Cannot append ''{0}'' to ''{1}''", new Object[] {name, dst});
                        SVNErrorManager.error(err, e);
                    }
                } finally {
                    close(is);
                    close(os);
                }
                break;
            case XFER_CP_AND_TRANSLATE:
                try {
                    if (versioned == null) {
                        versioned = dst;
                    }
                    // TODO 
                    // this should be done by wc-translator, that accepts:
                    // inputStream for detranslate file and File for
                    // destination, plus wcAccess for options and versioned name.
                    
                    // 1) setup eols and keywords and special from versioned.
                    // 2) call loghelper to translate - it will also create
                    //    link if needed.
                    // 3) log helper should be initialized with ISVNContext - to get custom charset and/or eol.
                    
                    is = read(name);
                    os = new SVNTranslatorOutputStream(write(dst), null, false, null, true);
                    byte[] buffer = new byte[8192];
                    while(true) {
                        int r = is.read(buffer);
                        if (r <= 0) {
                            break;
                        }
                        os.write(buffer, 0, r);
                    }
                } catch (IOExceptionWrapper wrapper) {
                    throw wrapper.getOriginalException();
                } catch (IOException e) {
                    if (exists(name)) {
                        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, "Cannot translate ''{0}'' to ''{1}''", new Object[] {name, dst});
                        SVNErrorManager.error(err, e);
                    }
                } finally {
                    close(is);
                    close(os);
                } 
                break;
            case XFER_CP_AND_DETRANSLATE:
            default:
                break;
        }
    }
    
    protected SVNWCAccess getWCAccess() {
        return myWCAccess;
    }
    
    /*
     * TODO move to SVNLogHelper, these methods providers low-level file ops
     * using 'short' paths.
     * 
     * SVNLogHelper should be provided by SVNAdminArea or SVNAdminLayout.
     */
    
    protected void rename(String src, String dst) throws SVNException {
        SVNFileUtil.rename(new File(getRealPath(src)), new File(getRealPath(dst)));
    }

    protected void copy(String src, String dst) throws SVNException {
        SVNFileUtil.copyFile(new File(getRealPath(src)), new File(getRealPath(dst)), true);
    }
    
    protected InputStream read(String path) throws SVNException {
        return SVNFileUtil.openFileForReading(new File(getRealPath(path)));
    }
    
    protected OutputStream write(String path) throws SVNException {
        return SVNFileUtil.openFileForWriting(new File(getRealPath(path)));
    }
    
    protected void close(OutputStream os) {
        SVNFileUtil.closeFile(os);
    }
    
    protected void close(InputStream is) {
        SVNFileUtil.closeFile(is);
    }
    
    protected boolean exists(String path) {
        return new File(getRealPath(path)).exists();
    }
    
    protected void setReadonly(String path, boolean readonly) {
        SVNFileUtil.setReadonly(new File(getRealPath(path)), readonly);
    }
    
    protected long lastModified(String path) {
        return new File(getRealPath(path)).lastModified();
    }
    
    protected long length(String path) {
        return new File(getRealPath(path)).length();
    }
    
    private String getRealPath(String logPath) {
        return SVNPathUtil.append(getWCAccess().getPath(), logPath);
    }

}
