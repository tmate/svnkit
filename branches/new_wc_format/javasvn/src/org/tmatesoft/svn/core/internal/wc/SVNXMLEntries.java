/*
 * ====================================================================
 * Copyright (c) 2004-2006 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://tmate.org/svn/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.wc;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.internal.util.SVNEncodingUtil;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.util.SVNDebugLog;

/**
 * @version 1.0
 * @author TMate Software Ltd.
 */
public class SVNXMLEntries extends SVNAdminArea{
    public static final int WC_FORMAT = 4;
    private static final String THIS_DIR = "";

    public SVNXMLEntries(SVNDirectory parent) {
        super(parent);
    }

    public SVNProperties getBaseProperties(String name, boolean tmp) {
        String path = !tmp ? "" : "tmp/";
        path += "".equals(name) ? "dir-prop-base" : "prop-base/" + name + ".svn-base";
        File propertiesFile = getParent().getAdminFile(path);
        return new SVNProperties(propertiesFile, getParent().getAdminDirectory().getName() + "/" + path);
    }

    public SVNProperties getWCProperties(String name) {
        String path = "".equals(name) ? "dir-wcprops" : "wcprops/" + name + ".svn-work";
        File propertiesFile = getParent().getAdminFile(path);
        return new SVNProperties(propertiesFile, getParent().getAdminDirectory().getName() + "/" + path);
    }

    public SVNProperties getProperties(String name, boolean tmp) {
        String path = !tmp ? "" : "tmp/";
        path += "".equals(name) ? "dir-props" : "props/" + name + ".svn-work";
        File propertiesFile = getParent().getAdminFile(path);
        return new SVNProperties(propertiesFile, getParent().getAdminDirectory().getName() + "/" + path);
    }

    protected void fetchEntries() throws IOException, SVNException {
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new InputStreamReader(SVNFileUtil.openFileForReading(getParent().getEntriesFile()), "UTF-8"));
            String line;
            Map entry = null;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.equals("<entry")) {
                    entry = new HashMap();
                    continue;
                }
                if (entry != null) {
                    if (line.indexOf('=') <= 0 || line.indexOf('\"') <= 0 || 
                            line.indexOf('\"') == line.lastIndexOf('\"')) {
                        continue;
                    }
                    String name = line.substring(0, line.indexOf('='));
                    String value = line.substring(line.indexOf('\"') + 1, 
                            line.lastIndexOf('\"'));
                    value = SVNEncodingUtil.xmlDecode(value);
                    entry.put(SVNProperty.SVN_ENTRY_PREFIX + name, value);
                    if (line.charAt(line.length() - 1) == '>') {
                        String entryName = (String) entry.get(SVNProperty.NAME);
                        if (entryName == null) {
                            SVNDebugLog.logInfo("svn: '" + getParent().getEntriesFile() + "' file includes invalid entry with missing 'name' attribute");
                            myData.clear();
                            myEntries.clear();
                            return;
                        }
                        myData.put(entryName, entry);
                        myEntries.add(new SVNEntry(this, entryName));
                        if (!"".equals(entryName)) {
                            Map rootEntry = (Map) myData.get("");
                            if (rootEntry != null) {
                                if (entry.get(SVNProperty.REVISION) == null) {
                                    entry.put(SVNProperty.REVISION, rootEntry.get(SVNProperty.REVISION));
                                }
                                if (entry.get(SVNProperty.URL) == null) {
                                    String url = (String) rootEntry.get(SVNProperty.URL);
                                    if (url != null) {
                                        url = SVNPathUtil.append(url, SVNEncodingUtil.uriEncode(entryName));
                                    }
                                    entry.put(SVNProperty.URL, url);
                                }
                                if (entry.get(SVNProperty.UUID) == null) {
                                    entry.put(SVNProperty.UUID, rootEntry.get(SVNProperty.UUID));
                                }
                                if (entry.get(SVNProperty.REPOS) == null && rootEntry.get(SVNProperty.REPOS) != null) {
                                    entry.put(SVNProperty.REPOS, rootEntry.get(SVNProperty.REPOS));
                                }
                            }
                        }
                        entry = null;
                    }
                }
            }
        } finally {
            SVNFileUtil.closeFile(reader);
        }
    }

    public String getThisDirName() {
        return THIS_DIR;
    }

    protected String formatEntries() {
        StringBuffer buffer = new StringBuffer();
        Map rootEntry = (Map) myData.get(getThisDirName());

        buffer.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n");
        buffer.append("<wc-entries\n");
        buffer.append("   xmlns=\"svn:\">\n");
        for (Iterator entries = myData.keySet().iterator(); entries.hasNext();) {
            String name = (String) entries.next();
            Map entry = (Map) myData.get(name);
            buffer.append("<entry");
            for (Iterator names = entry.keySet().iterator(); names.hasNext();) {
                String propName = (String) names.next();
                String propValue = (String) entry.get(propName);
                if (propValue == null) {
                    continue;
                }
                if (BOOLEAN_PROPERTIES.contains(propName) && !Boolean.TRUE.toString().equals(propValue)) {
                    continue;
                }
                if (!getThisDirName().equals(name)) {
                    Object expectedValue;
                    if (SVNProperty.KIND_DIR.equals(entry.get(SVNProperty.KIND))) {
                        if (SVNProperty.UUID.equals(propName)
                                || SVNProperty.REVISION.equals(propName)
                                || SVNProperty.URL.equals(propName) 
                                || SVNProperty.REPOS.equals(propName)) {
                            continue;
                        }
                    } else {
                        if (SVNProperty.URL.equals(propName)) {
                            expectedValue = SVNPathUtil.append((String) rootEntry.get(propName), SVNEncodingUtil.uriEncode(name));
                        } else if (SVNProperty.UUID.equals(propName) || SVNProperty.REVISION.equals(propName)) {
                            expectedValue = rootEntry.get(propName);
                        } else if (SVNProperty.REPOS.equals(propName)) {
                            expectedValue = rootEntry.get(propName);
                        } else {
                            expectedValue = null;
                        }
                        if (propValue.equals(expectedValue)) {
                            continue;
                        }
                    }
                }
                propName = propName.substring(SVNProperty.SVN_ENTRY_PREFIX.length());
                propValue = SVNEncodingUtil.xmlEncodeAttr(propValue);
                buffer.append("\n   ");
                buffer.append(propName);
                buffer.append("=\"");
                buffer.append(propValue);
                buffer.append("\"");
            }
            buffer.append("/>\n");
        }
        buffer.append("</wc-entries>\n");
        return buffer.toString();
    }

    public void close() {
        super.close();
    }

    public void setCachableProperties(String name, String[] cachableProps) {
    }

    protected int getFormatNumber() {
        return WC_FORMAT;
    }
}
