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
import java.io.UnsupportedEncodingException;
import java.util.Map;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.internal.util.SVNDate;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc.admin.SVNTranslator;
import org.tmatesoft.svn.core.internal.wc.admin.SVNTranslatorOutputStream;
import org.tmatesoft.svn.core.wc.ISVNOptions;


/**
 * @version 1.2
 * @author  TMate Software Ltd.
 */
public class SVNAdminTranslator {
    
    public static void expand(SVNWCAccess wcAccess, ISVNOptions options, String path, InputStream src, File dst, long flags) throws SVNException {
        boolean special = isSpecial(wcAccess, path);
        if (special) {
            // only create symlink!
            BufferedReader reader = null;
            String linkName = null;
            try {
                reader = new BufferedReader(new InputStreamReader(src, "UTF-8"));
                linkName = reader.readLine();
            } catch (IOException e) {
                e.printStackTrace();
            }
            if (linkName != null && linkName.startsWith("link")) {
                linkName = linkName.substring("link".length());
                linkName = linkName.trim();
            }
            SVNFileUtil.createSymlink(dst, linkName);
            return;
        }
        byte[] eols = getEOLBytes(wcAccess, path, options);
        Map keywords = getKeywords(wcAccess, path, options);
        
        OutputStream os = SVNFileUtil.openFileForWriting(dst);
        os = new SVNTranslatorOutputStream(os, eols, false, keywords, true);
        // copy from is to os.
    }

    public static void unexpand(SVNWCAccess wcAccess, String path, File src, OutputStream dst, long flags) throws SVNException {
        
    }

    private static byte[] getEOLBytes(SVNWCAccess wcAccess, String path, ISVNOptions options) throws SVNException {
        byte[] styleBytes = wcAccess.getPropertyValue(path, SVNProperty.EOL_STYLE);
        String style = null;
        try {
            style = new String(styleBytes, "UTF-8");
        } catch (UnsupportedEncodingException e) {
        }
        if ("LF".equals(style)) {
            return new byte[] {'\n'};
        } else if ("CR".equals(style)) {
            return new byte[] {'\r'};
        } else if ("CRLF".equals(style)) {
            return new byte[] {'\r', '\n'};
        } else if ("native".equals(style)) {            
            byte[] eols = options.getNativeEOLBytes();
            if (eols == null) {
                eols = System.getProperty("line.separator").getBytes();
            }
            return eols;
        }
        return null;
    }
    
    private static boolean isSpecial(SVNWCAccess wcAccess, String path) throws SVNException {
        return wcAccess.getPropertyValue(path, SVNProperty.SPECIAL) != null;
    }
    
    private static Map getKeywords(SVNWCAccess wcAccess, String path, ISVNOptions options) throws SVNException {
        byte[] keywordsBytes = wcAccess.getPropertyValue(path, SVNProperty.KEYWORDS);
        if (keywordsBytes == null) {
            return null;
        }
        String keywords = null;
        try {
            keywords = new String(keywordsBytes, "UTF-8");
        } catch (UnsupportedEncodingException e) {
        }
        SVNEntry entry = wcAccess.getVersionedEntry(path, false);
        String date = entry.getCommittedDate() != null ? SVNDate.formatHumanDate(entry.getCommittedDate(), options) : null;
        String revision = entry.getCommitedRevision() >= 0 ? Long.toString(entry.getCommitedRevision()) : null;
        
        return SVNTranslator.computeKeywords(keywords, entry.getURL(), entry.getCommitAuthor(), date, revision, options);
    }
}
