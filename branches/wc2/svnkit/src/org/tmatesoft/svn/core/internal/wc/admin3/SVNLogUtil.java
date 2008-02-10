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
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.util.SVNEncodingUtil;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc.admin.SVNLog;


/**
 * @version 1.1.2
 * @author  TMate Software Ltd.
 */
public class SVNLogUtil {
    
    public static void run(InputStream log, SVNLogRunner runner) throws SVNException {
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
                    runner.runCommand(commandName, attrs);
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
}
