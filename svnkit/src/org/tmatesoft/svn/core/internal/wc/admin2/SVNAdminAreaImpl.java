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

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;


/**
 * @version 1.1.2
 * @author  TMate Software Ltd.
 */
public class SVNAdminAreaImpl extends AbstractSVNAdminArea {

    public void create(String area, String dir, String name, String extension, SVNNodeKind kind, boolean tmp) throws SVNException {
        String fullPath = composeAdminPath(area, dir, name, extension, tmp);
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

    public boolean exists(String area, String dir, String name, String extension, boolean tmp) {
        String fullPath = composeAdminPath(area, dir, name, extension, tmp);
        File file = new File(fullPath);
        return file.exists();
    }

    public void delete(String area, String dir, String name, String extension, boolean tmp) throws SVNException {
        String fullPath = composeAdminPath(area, dir, name, extension, tmp);
        File file = new File(fullPath);
        SVNFileUtil.deleteFile(file);
    }

    public void sync(String area, String dir, String name, String extension) throws SVNException {
        String tmpPath = composeAdminPath(area, dir, name, extension, true);
        String path = composeAdminPath(area, dir, name, extension, false);
        
        File file = new File(path);
        SVNFileUtil.rename(new File(tmpPath), file);
    }

    public InputStream read(String area, String dir, String name, String extension, boolean tmp) throws SVNException {
        String path = composeAdminPath(area, dir, name, extension, tmp);
        return SVNFileUtil.openFileForReading(new File(path));
    }

    public OutputStream write(String area, String dir, String name, String extension, boolean synced) throws SVNException {
        String tmpPath = composeAdminPath(area, dir, name, extension, synced);
        return SVNFileUtil.openFileForWriting(new File(tmpPath));
    }

    public void close(String area, String dir, String name, String extension, OutputStream os, boolean sync) throws SVNException {
        SVNFileUtil.closeFile(os);
        if (sync) {
            sync(area, dir, name, extension);
        }
    }

    public void close(InputStream is) {
        SVNFileUtil.closeFile(is);
    }
    
    public String getLogPath(String area, String absolutePath) {
        String localPath = SVNPathUtil.getPathAsChild(area, absolutePath);
        if (localPath == null || area.equals(absolutePath)) {
            return SVNEntries.THIS_DIR;
        }
        return localPath;
    }

    protected String composeAdminPath(String area, String dir, String name, String extension, boolean tmp) {
        StringBuffer sb = new StringBuffer();
        sb.append(area);
        sb.append('/');
        sb.append(SVNFileUtil.getAdminDirectoryName());
        if (tmp) {
            sb.append('/');
            sb.append(AbstractSVNAdminArea.DIR_TMP);
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
}
