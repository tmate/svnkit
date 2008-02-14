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

import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;


/**
 * This class wraps access to the information stored in administrative area.
 * 
 * Its methods accept wcAccess object and description of the unit (path) and 
 * returns objects to access that very unit. Default implementation is straightforward
 * and treats descriptions as paths in the filesystem relative to wcAccess path.   
 * 
 * @version 1.2.0
 * @author  TMate Software Ltd.
 */
public class SVNAdminLayout {

    public static String EXTENSION_TMP = ".tmp";
    public static String EXTENSION_PROP_REJECT = ".prej";
    public static String EXTENSION_BASE = ".svn-base";
    public static String EXTENSION_WORK = ".svn-work";
    public static String EXTENSION_REVERT = ".svn-revert";
    
    public static String DIR_TMP = "tmp";
    public static String DIR_TEXT_BASE = "text-base";
    public static String DIR_PROPS = "props";
    public static String DIR_PROP_BASE = "prop-base";
    public static String DIR_WCPROPS = "wcprops";
    
    public static String FILE_DIR_PROPS = "dir-props";
    public static String FILE_DIR_PROP_BASE = "dir-prop-base";
    public static String FILE_DIR_PROP_REVERT = "dir-prop-revert";
    public static String FILE_DIR_WCPROPS = "dir-wcprops";
    public static String FILE_ALL_WCPROPS = "all-wcprops";

    public static String FILE_LOG = "log";
    public static String FILE_KILLME = "KILLME";
    public static String FILE_README = "README.txt";
    public static String FILE_EMPTY_FILE = "empty-file";
    public static String FILE_FORMAT = "format";
    public static String FILE_ENTRIES = "entries";
    public static String FILE_LOCK = "lock";
    
    private static SVNAdminLayout ourInstance;
    
    public static SVNAdminLayout getInstance() {
        if (ourInstance == null) {
            ourInstance = new SVNAdminLayout();
        }
        return ourInstance;
    }
    
    protected static void setInstance(SVNAdminLayout instance) {
        if (ourInstance != null) {
            ourInstance = instance;
        }
    }
    
    protected SVNAdminLayout() {
        
    }

    public void create(SVNWCAccess wcAccess, String dir, String name, String extension, SVNNodeKind kind, boolean tmp) throws SVNException {
        String fullPath = composeAdminPath(wcAccess.getPath(), dir, name, extension, tmp);
        File file = new File(fullPath);
        if (kind == SVNNodeKind.FILE) {
            OutputStream os = null;
            try {
                os = SVNFileUtil.openFileForWriting(file);
            } finally {
                SVNFileUtil.closeFile(os);
            }
        } else if (kind == SVNNodeKind.DIR) {
            file.mkdirs();
        }
    }

    public boolean exists(SVNWCAccess wcAccess, String dir, String name, String extension, boolean tmp) {
        String fullPath = composeAdminPath(wcAccess.getPath(), dir, name, extension, tmp);
        File file = new File(fullPath);
        return file.exists();
    }

    public void delete(SVNWCAccess wcAccess, String dir, String name, String extension, boolean tmp) throws SVNException {
        String fullPath = composeAdminPath(wcAccess.getPath(), dir, name, extension, tmp);
        File file = new File(fullPath);
        SVNFileUtil.deleteFile(file);
    }

    public void sync(SVNWCAccess wcAccess, String dir, String name, String extension) throws SVNException {
        String tmpPath = composeAdminPath(wcAccess.getPath(), dir, name, extension, true);
        String path = composeAdminPath(wcAccess.getPath(), dir, name, extension, false);
        
        File file = new File(path);
        SVNFileUtil.rename(new File(tmpPath), file);
    }

    public InputStream read(SVNWCAccess wcAccess, String dir, String name, String extension, boolean tmp) throws SVNException {
        String path = composeAdminPath(wcAccess.getPath(), dir, name, extension, tmp);
        return SVNFileUtil.openFileForReading(new File(path));
    }

    public OutputStream write(SVNWCAccess wcAccess, String dir, String name, String extension, boolean synced) throws SVNException {
        String tmpPath = composeAdminPath(wcAccess.getPath(), dir, name, extension, synced);
        return SVNFileUtil.openFileForWriting(new File(tmpPath));
    }

    public void close(SVNWCAccess wcAccess, String dir, String name, String extension, OutputStream os, boolean sync) throws SVNException {
        SVNFileUtil.closeFile(os);
        if (sync) {
            sync(wcAccess, dir, name, extension);
        }
    }

    public void close(InputStream is) {
        SVNFileUtil.closeFile(is);
    }
    
    public boolean adminAreaExists(String path) {
        path = composeAdminPath(path, null, null, null, false);
        return new File(path).isDirectory();
    }
    
    public long lastModified(SVNWCAccess wcAccess, String dir, String name, String extension, boolean tmp) {
        String fullPath = composeAdminPath(wcAccess.getPath(), dir, name, extension, tmp);
        File file = new File(fullPath);
        return file.lastModified();
    }
    
    public int readVersion(String wcAccessPath) throws SVNException {
        SVNErrorMessage err = null;
        int wcFormat = 0;
        String formatFilePath = composeAdminPath(wcAccessPath, null, FILE_FORMAT, null, false);
        InputStream is = null;
        File formatFile = null;
        try {
            is = SVNFileUtil.openFileForReading(new File(formatFilePath));
            wcFormat = readVersion(is, formatFile);
        } catch (SVNException e) {
            err = e.getErrorMessage();
        } finally {
            close(is);
        }
        if (err != null && err.getErrorCode() == SVNErrorCode.BAD_VERSION_FILE_FORMAT) {
            try {
                err = null;
                formatFilePath = composeAdminPath(wcAccessPath, null, FILE_ENTRIES, null, false);
                formatFile = new File(formatFilePath);
                is = SVNFileUtil.openFileForReading(formatFile);
                wcFormat = readVersion(is, formatFile);
            } catch (SVNException e) {
                err = e.getErrorMessage();  
            } finally {
                close(is);
            }
        }
        if (err != null && !formatFile.exists()) {
            File wcFile = new File(wcAccessPath);
            if (!wcFile.exists()) {
                err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, "''{0}'' does not exist", wcFile);
                SVNErrorManager.error(err);
            }
            wcFormat = 0;
        } else if (err != null) {
            SVNErrorManager.error(err);
        }
        return wcFormat;
    }
    
    protected int readVersion(InputStream is, File file) throws SVNException {
        byte[] data = new byte[10];
        int version = 0;
        try {
            int read = is.read(data);
            if (read < 1) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.STREAM_UNEXPECTED_EOF, "Reading ''{0}''", file);
                SVNErrorManager.error(err);
            }
            for (int i = 0; i < read; i++) {
                if (data[i] == '\n' || data[i] == '\r') {
                    break;
                }
                if (!Character.isDigit((char) data[i])) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.BAD_VERSION_FILE_FORMAT, "First line of ''{0}'' contains non-digit", file);
                    SVNErrorManager.error(err);
                }
                version += i == 0 ? data[i] - '0' : i*10*(data[i] - '0');
            }
        } catch (EOFException e) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.STREAM_UNEXPECTED_EOF, "Reading ''{0}''", file);
            SVNErrorManager.error(err);
        } catch (IOException e) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, "Reading ''{0}''", file);
            SVNErrorManager.error(err);
        }
        return version;
    }

    /**
     * Returns absolute path of admin file to write to the log file.
     * This path later will be fed to the getLogPath method.
     */
    public String getAbsolutePath(SVNWCAccess wcAccess, String dir, String name, String extension, boolean tmp) {
        return composeAdminPath(wcAccess.getPath(), dir, name, extension, tmp);
    }

    protected String composeAdminPath(String wcAccessPath, String dir, String name, String extension, boolean tmp) {
        StringBuffer sb = new StringBuffer();
        sb.append(wcAccessPath);
        sb.append('/');
        sb.append(SVNFileUtil.getAdminDirectoryName());
        if (tmp) {
            sb.append('/');
            sb.append(DIR_TMP);
        }
        if (dir != null) {
            sb.append('/');
            sb.append(dir);
        }
        if (name != null) {
            sb.append('/');
            sb.append(name);
            if (extension != null) {
                sb.append(extension);
            }
        }
        return sb.toString();
    }

    public String getRealLogPath(SVNWCAccess access, String logPath) {
        return SVNPathUtil.append(access.getPath(), logPath);
    }

}
