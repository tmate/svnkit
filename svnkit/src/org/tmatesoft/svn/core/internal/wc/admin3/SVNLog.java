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
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.HashMap;
import java.util.Map;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.util.SVNDate;
import org.tmatesoft.svn.core.internal.util.SVNXMLUtil;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNFileType;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;


/**
 * @version 1.1.2
 * @author  TMate Software Ltd.
 */
public class SVNLog {
    
    public static final int COPY = 0;
    public static final int COPY_AND_TRANSLATE = 1;
    public static final int COPY_AND_TRANSLATE_SPECIAL_ONLY = 2;
    public static final int COPY_AND_DETRANSLATE = 3;
    
    public static final String APPEND_TAG = "append";
    public static final String COMMITTED_TAG = "committed";
    public static final String RM_TAG = "rm";
    public static final String MV_TAG = "mv";
    public static final String CP_TAG = "cp";
    public static final String CP_AND_TRANSALTE_TAG = "cp-and-translate";
    public static final String CP_AND_DETRANSLATE_TAG = "cp-and-detranslate";
    public static final String DELETE_ENTRY_TAG = "delete-entry";
    public static final String DELETE_LOCK_TAG = "delete-lock";
    public static final String DELETE_CHANGELIST_TAG = "delete-changelist";
    public static final String MODIFY_ENTRY_TAG = "modify-entry";
    public static final String READONLY_TAG = "readonly";
    public static final String MAYBE_READONLY_TAG = "maybe-readonly";
    public static final String MAYBE_EXECUTABLE_TAG = "maybe-executable";
    public static final String SET_TIMESTAMP_TAG = "set-timestamp";
    public static final String MODIFY_WCPROP_TAG = "modify-wcprop";
    public static final String UPGRADE_FORMAT_TAG = "upgrade-format";
    
    public static final String NAME_ATTR = "name";
    public static final String DEST_ATTR = "dest";
    public static final String REVISION_ATTR = "revision";
    public static final String TIMESTAMP_ATTR = "timestamp";
    public static final String PROPNAME_ATTR = "propname";
    public static final String PROPVAL_ATTR = "propval";
    public static final String FORCE_ATTR = "force";
    public static final String FORMAT_ATTR = "format";

    public static final String ARG1_ATTR = "arg1";
    public static final String ARG2_ATTR = "arg2";
    
    // obsolete
    public static final String MERGE_TAG = "merge";
    
    private static final String[] COPY_COMMANDS = {CP_TAG, CP_AND_TRANSALTE_TAG, CP_AND_TRANSALTE_TAG, CP_AND_DETRANSLATE_TAG};
    
    private static final String WC_TIMESTAMP = "working";
    private static final String WC_WORKING_SIZE = "working";
    
    private SVNAdminLayout myAdminLayout;
    private StringBuffer myBuffer;
    private Map myAttributes;
    private SVNWCAccess myWCAccess;

    public SVNLog(SVNWCAccess wcAccess) {
        myAdminLayout = wcAccess.getAdminArea().getLayout();
        myWCAccess = wcAccess;
        myBuffer = new StringBuffer();
        myAttributes = new HashMap();
    }
    
    public void append(String srcPath, String dstPath) {
        myAttributes.clear();
        myAttributes.put(NAME_ATTR, getLogPath(srcPath));
        myAttributes.put(DEST_ATTR, getLogPath(dstPath));
        writeCommand(APPEND_TAG);
    }
    
    public void committed(String path, long revision) {
        myAttributes.clear();
        myAttributes.put(NAME_ATTR, getLogPath(path));
        myAttributes.put(REVISION_ATTR, Long.toString(revision));
        writeCommand(COMMITTED_TAG);
    }
    
    public boolean copy(String srcPath, String dstPath, boolean removeDstIfNoDst, int type) {
        return copy(COPY_COMMANDS[type], srcPath, dstPath, type == COPY_AND_TRANSLATE_SPECIAL_ONLY, removeDstIfNoDst);
    }
    
    public void translate(String srcPath, String dstPath, String workingPath) {
        myAttributes.clear();
        myAttributes.put(NAME_ATTR, getLogPath(srcPath));
        myAttributes.put(DEST_ATTR, getLogPath(dstPath));
        myAttributes.put(ARG2_ATTR, getLogPath(workingPath));
        writeCommand(CP_AND_TRANSALTE_TAG);
    }

    public void deleteEntry(String path) {
        myAttributes.clear();
        myAttributes.put(NAME_ATTR, getLogPath(path));
        writeCommand(DELETE_ENTRY_TAG);
    }

    public void deleteLock(String path) {
        myAttributes.clear();
        myAttributes.put(NAME_ATTR, getLogPath(path));
        writeCommand(DELETE_LOCK_TAG);
    }

    public void deleteChangelist(String path) {
        myAttributes.clear();
        myAttributes.put(NAME_ATTR, getLogPath(path));
        writeCommand(DELETE_CHANGELIST_TAG);
    }
    
    public void modifyEntry(SVNEntry entry, String path, long flags) {
        if (flags == 0) {
            return;
        }
        myAttributes.clear();

        writeEntryAttribute(flags, SVNEntry.FLAG_REVISION, SVNEntry.REVISION, Long.toString(entry.getRevision()));
        writeEntryAttribute(flags, SVNEntry.FLAG_URL, SVNEntry.URL, entry.getURL());
        writeEntryAttribute(flags, SVNEntry.FLAG_REPOS, SVNEntry.REPOS, entry.getRepositoryURL());
        writeEntryAttribute(flags, SVNEntry.FLAG_UUID, SVNEntry.UUID, entry.getRepositoryUUID());
        writeEntryAttribute(flags, SVNEntry.FLAG_KIND, SVNEntry.KIND, entry.getKind().toString());
        writeEntryAttribute(flags, SVNEntry.FLAG_SCHEDULE, SVNEntry.SCHEDULE, entry.getSchedule());
        writeEntryAttribute(flags, SVNEntry.FLAG_COPIED, SVNEntry.COPIED, Boolean.toString(entry.isCopied()));
        writeEntryAttribute(flags, SVNEntry.FLAG_DELETED, SVNEntry.DELETED, Boolean.toString(entry.isDeleted()));
        writeEntryAttribute(flags, SVNEntry.FLAG_ABSENT, SVNEntry.ABSENT, Boolean.toString(entry.isAbsent()));
        writeEntryAttribute(flags, SVNEntry.FLAG_INCOMPLETE, SVNEntry.INCOMPLETE, Boolean.toString(entry.isIncomplete()));
        writeEntryAttribute(flags, SVNEntry.FLAG_COPYFROM_URL, SVNEntry.COPYFROM_URL, entry.getCopyFromURL());
        writeEntryAttribute(flags, SVNEntry.FLAG_COPYFROM_REV, SVNEntry.COPYFROM_REV, Long.toString(entry.getCopyFromRevision()));
        
        writeEntryAttribute(flags, SVNEntry.FLAG_CONFLICT_OLD, SVNEntry.CONFLICT_OLD, entry.getConflictOld() == null ? "" : entry.getConflictOld());
        writeEntryAttribute(flags, SVNEntry.FLAG_CONFLICT_NEW, SVNEntry.CONFLICT_NEW, entry.getConflictNew() == null ? "" : entry.getConflictNew());
        writeEntryAttribute(flags, SVNEntry.FLAG_CONFLICT_WRK, SVNEntry.CONFLICT_WRK, entry.getConflictWorking() == null ? "" : entry.getConflictWorking());
        writeEntryAttribute(flags, SVNEntry.FLAG_PREJFILE, SVNEntry.PREJFILE, entry.getPropReject() == null ? "" : entry.getPropReject());

        writeEntryAttribute(flags, SVNEntry.FLAG_TEXT_TIME, SVNEntry.TEXT_TIME, SVNDate.formatDate(entry.getTextTime()));
        writeEntryAttribute(flags, SVNEntry.FLAG_PROP_TIME, SVNEntry.PROP_TIME, SVNDate.formatDate(entry.getPropTime()));
        writeEntryAttribute(flags, SVNEntry.FLAG_CHECKSUM, SVNEntry.CHECKSUM, entry.getChecksum());

        writeEntryAttribute(flags, SVNEntry.FLAG_CMT_REV, SVNEntry.CMT_REV, Long.toString(entry.getCommitedRevision()));
        writeEntryAttribute(flags, SVNEntry.FLAG_CMT_AUTHOR, SVNEntry.CMT_AUTHOR, entry.getCommitAuthor());
        writeEntryAttribute(flags, SVNEntry.FLAG_CMT_DATE, SVNEntry.CMT_DATE, SVNDate.formatDate(entry.getCommittedDate()));
        
        writeEntryAttribute(flags, SVNEntry.FLAG_LOCK_TOKEN, SVNEntry.LOCK_TOKEN, entry.getLockToken());
        writeEntryAttribute(flags, SVNEntry.FLAG_LOCK_OWNER, SVNEntry.LOCK_OWNER, entry.getLockOwner());
        writeEntryAttribute(flags, SVNEntry.FLAG_LOCK_COMMENT, SVNEntry.LOCK_COMMENT, entry.getLockComment());
        writeEntryAttribute(flags, SVNEntry.FLAG_LOCK_CREATION_DATE, SVNEntry.LOCK_CREATION_DATE, SVNDate.formatDate(entry.getLockCreationDate()));
        
        writeEntryAttribute(flags, SVNEntry.FLAG_HAS_PROPS, SVNEntry.HAS_PROPS, Boolean.toString(entry.hasProperties()));
        writeEntryAttribute(flags, SVNEntry.FLAG_HAS_PROP_MODS, SVNEntry.HAS_PROP_MODS, Boolean.toString(entry.isPropertiesModified()));
        writeEntryAttribute(flags, SVNEntry.FLAG_CACHABLE_PROPS, SVNEntry.CACHABLE_PROPS, entry.getCachableProperties());
        writeEntryAttribute(flags, SVNEntry.FLAG_PRESENT_PROPS, SVNEntry.PRESENT_PROPS, entry.getPresentProperties());

        writeEntryAttribute(flags, SVNEntry.FLAG_WORKING_SIZE, SVNEntry.WORKING_SIZE, Long.toString(entry.getWorkingSize()));
        writeEntryAttribute(flags, SVNEntry.FLAG_FORCE, FORCE_ATTR, Boolean.TRUE.toString());
        
        if (myAttributes.isEmpty()) {
            return;
        }
        myAttributes.put(NAME_ATTR, getLogPath(path));
        writeCommand(MODIFY_ENTRY_TAG);
    }
    
    private void writeEntryAttribute(long flags, long mask, String name, String value) {
        if ((flags & mask) != 0) {
            myAttributes.put(name, value);
        }
    }

    public void modifyWCProperty(String path, String name, String value) {
        myAttributes.clear();
        myAttributes.put(NAME_ATTR, getLogPath(path));
        myAttributes.put(PROPNAME_ATTR, name);
        myAttributes.put(PROPVAL_ATTR, value);
        writeCommand(MODIFY_WCPROP_TAG);
    }
    
    public boolean move(String srcPath, String dstPath, boolean removeDstIfNoSrc) {
        return copy(MV_TAG, srcPath, dstPath, false, removeDstIfNoSrc);
    }

    public void maybeSetExecutable(String path) {
        myAttributes.clear();
        myAttributes.put(NAME_ATTR, getLogPath(path));
        writeCommand(MAYBE_EXECUTABLE_TAG);
    }

    public void maybeSetReadonly(String path) {
        myAttributes.clear();
        myAttributes.put(NAME_ATTR, getLogPath(path));
        writeCommand(MAYBE_READONLY_TAG);
    }
    
    public void entryTimestampFromWC(String path, String timeProperty) {
        myAttributes.clear();
        myAttributes.put(NAME_ATTR, getLogPath(path));
        myAttributes.put(timeProperty, WC_TIMESTAMP);
        writeCommand(MODIFY_ENTRY_TAG);
    }

    public void entryWorkingSizeFromWC(String path) {
        myAttributes.clear();
        myAttributes.put(NAME_ATTR, getLogPath(path));
        myAttributes.put(SVNEntry.WORKING_SIZE, WC_WORKING_SIZE);
        writeCommand(MODIFY_ENTRY_TAG);
    }

    public void readonly(String path) {
        myAttributes.clear();
        myAttributes.put(NAME_ATTR, getLogPath(path));
        writeCommand(READONLY_TAG);
    }

    public void timestamp(String path, String time) {
        myAttributes.clear();
        myAttributes.put(NAME_ATTR, getLogPath(path));
        myAttributes.put(TIMESTAMP_ATTR, time);
        writeCommand(SET_TIMESTAMP_TAG);
    }
    
    public void remove(String path) {
        myAttributes.clear();
        myAttributes.put(NAME_ATTR, getLogPath(path));
        writeCommand(RM_TAG);
    }

    public void upgradeFormat(String path, int format) {
        myAttributes.clear();
        myAttributes.put(NAME_ATTR, getLogPath(path));
        myAttributes.put(FORMAT_ATTR, Integer.toString(format));
        writeCommand(UPGRADE_FORMAT_TAG);
    }
    
    public void save(int logNumber) throws SVNException {
        OutputStream os = null;
        String extension = logNumber > 0 ? "." + logNumber : null;
        try {
            os = myAdminLayout.write(myWCAccess, null, SVNAdminLayout.FILE_LOG, extension, true);
            OutputStreamWriter writer = new OutputStreamWriter(os, "UTF-8");
            writer.write(myBuffer.toString());
            writer.flush();
            myAdminLayout.close(myWCAccess, null, SVNAdminLayout.FILE_LOG, extension, os, true);
            os = null;
        } catch (IOException e) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR);
            SVNErrorManager.error(err, e);
        } finally {
            SVNFileUtil.closeFile(os);
        }
    }
    
    public String toString() {
        return myBuffer.toString();
    }

    private boolean copy(String commandName, String srcPath, String dstPath, boolean specialOnly, boolean removeDstIfNoSrc) {
        File src = new File(srcPath);
        // src path could refer below admin area.
        // and its type should be fetched through layout.
        SVNFileType srcType = SVNFileType.getType(src);
        
        if (srcType != SVNFileType.NONE) {
            myAttributes.clear();
            myAttributes.put(NAME_ATTR, getLogPath(srcPath));
            myAttributes.put(DEST_ATTR, getLogPath(dstPath));
            if (specialOnly) {
                myAttributes.put(ARG1_ATTR, Boolean.TRUE.toString());
            }
            writeCommand(commandName);
            return true;
        } else if (srcType == SVNFileType.NONE && removeDstIfNoSrc) {
            remove(dstPath);
            return true;
        }
        return false;
    }
    
    private void writeCommand(String name) {
        SVNXMLUtil.openXMLTag(null, name, SVNXMLUtil.XML_STYLE_ATTRIBUTE_BREAKS_LINE | SVNXMLUtil.XML_STYLE_SELF_CLOSING, myAttributes, myBuffer);
    }
    
    private String getLogPath(String path) {
        return myAdminLayout.getLogPath(myWCAccess, path);
    }

}
