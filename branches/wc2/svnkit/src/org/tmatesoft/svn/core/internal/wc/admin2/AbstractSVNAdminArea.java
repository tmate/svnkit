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

import java.io.InputStream;
import java.io.OutputStream;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;


/**
 * @version 1.1.2
 * @author  TMate Software Ltd.
 */
public abstract class AbstractSVNAdminArea {
    
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

    public abstract boolean exists(String area, String dir, String name, String extension, boolean tmp);
    
    public abstract void create(String area, String dir, String name, String extension, SVNNodeKind kind, boolean tmp) throws SVNException;
    
    public abstract void delete(String area, String dir, String name, String extension, boolean tmp) throws SVNException;

    public abstract OutputStream write(String area, String dir, String name, String extension, boolean synced) throws SVNException;
    
    public abstract InputStream read(String area, String dir, String name, String extension, boolean tmp) throws SVNException;
    
    public abstract void close(String area, String dir, String name, String extension, OutputStream os, boolean sync) throws SVNException;
    
    public abstract void close(InputStream is);
    
    public abstract void sync(String area, String dir, String name, String extension) throws SVNException;

    protected abstract String composeAdminPath(String area, String dir, String name, String extension, boolean tmp);
    
    public abstract String getLogPath(String area, String absolutePath);
}
