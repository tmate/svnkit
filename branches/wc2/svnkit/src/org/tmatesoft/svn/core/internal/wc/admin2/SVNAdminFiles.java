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
package org.tmatesoft.svn.core.internal.wc.admin2;

import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;


/**
 * @version 1.1.2
 * @author  TMate Software Ltd.
 */
public class SVNAdminFiles {
    
    private static final String TMP_EXTENSION = ".tmp";
    private static final String PROP_REJECT_EXTENSION = ".prej";
    private static final String BASE_EXTENSION = ".svn-base";
    private static final String WORK_EXTENSION = ".svn-work";
    private static final String REVERT_EXTENSION = ".svn-revert";

    private static final String ADM_FORMAT = "format";
    public static final String ADM_ENTRIES = "entries";
    public static final String ADM_LOCK = "lock";
    private static final String ADM_TMP = "tmp";
    private static final String ADM_TEXT_BASE = "text-base";
    private static final String ADM_PROPS = "props";
    private static final String ADM_PROP_BASE = "prop-base";
    private static final String ADM_DIR_PROPS = "dir-props";
    private static final String ADM_DIR_PROP_BASE = "dir-prop-base";
    private static final String ADM_DIR_PROP_REVERT = "dir-prop-revert";
    private static final String ADM_WCPROPS = "wcprops";
    private static final String ADM_DIR_WCPROPS = "dir-wcprops";
    public static final String ADM_ALL_WCPROPS = "all-wcprops";
    private static final String ADM_LOG = "log";
    private static final String ADM_KILLME = "KILLME";
    private static final String ADM_README = "README.txt";
    private static final String ADM_EMPTY_FILE = "empty-file";
    
    static final int MINIMUM_WC_FORMAT = 2;
    static final int MAXIMUM_WC_FORMAT = 9;
    
    private static String composeAdminPath(String path, String name, String extension, boolean tmp) {
        path = SVNPathUtil.append(path, SVNFileUtil.getAdminDirectoryName());
        if (tmp) {
            path = SVNPathUtil.append(path, ADM_TMP);
        }
        if (name != null) {
            path = SVNPathUtil.append(path, name);
            if (extension != null) {
                path = SVNPathUtil.append(path, extension);
            }
        }
        return path;
    }
    
    public static File getAdminFile(String path, String name, boolean tmp) {
        path = composeAdminPath(path, name, null, tmp);
        return new File(path);
    }   
    
    public static File createAdminFile(SVNWCAccess2 wcAccess, String name, SVNNodeKind kind, boolean tmp) throws SVNException {
        wcAccess.assertWritable();

        String path = composeAdminPath(wcAccess.getPath(), name, null, tmp);
        File file = new File(path);
        if (kind == SVNNodeKind.DIR) {
            file.mkdir();
        } else if (kind == SVNNodeKind.FILE) {
            SVNFileUtil.createEmptyFile(file);
        }
        return file;
    }

    public static void removeAdminFile(SVNWCAccess2 wcAccess, String name, boolean tmp) throws SVNException {
        String path = composeAdminPath(wcAccess.getPath(), name, null, tmp);
        File file = new File(path);
        SVNFileUtil.deleteFile(file);
    }
    
    public static boolean adminFileExists(SVNWCAccess2 wcAccess, String name, boolean tmp) {
        String path = composeAdminPath(wcAccess.getPath(), name, null, tmp);
        File file = new File(path);
        return file.exists();
    }
    
    public static int readVersionFile(File file) throws SVNException {
        InputStream in = null;
        byte[] buffer = new byte[80];
        int read = 0;
        try {
            in = SVNFileUtil.openFileForReading(file);
            read = in.read(buffer);
        } catch (EOFException e) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.STREAM_UNEXPECTED_EOF, "Reading ''{0}''", file);
            SVNErrorManager.error(err);
        } catch (IOException e) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, "Reading ''{0}''", file);
            SVNErrorManager.error(err);
        } finally {
            SVNFileUtil.closeFile(in);
        }
        if (read <= 0) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.STREAM_UNEXPECTED_EOF, "Reading ''{0}''", file);
            SVNErrorManager.error(err);
        }
        // get first line and get int from it.        
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < read; i++) {
            char ch = (char) buffer[i];
            if (ch == '\n' || ch == '\r') {
                break;
            }
            if (!Character.isDigit(ch)) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.BAD_VERSION_FILE_FORMAT, "First line of ''{0}'' contains non-digit", file);
                SVNErrorManager.error(err);
            }
            sb.append(ch);
        }
        try {
            return Integer.parseInt(sb.toString());
        } catch (NumberFormatException nfe) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.BAD_VERSION_FILE_FORMAT, "First line of ''{0}'' contains non-digit", file);
            SVNErrorManager.error(err);
        }
        return 0;
    }
}

