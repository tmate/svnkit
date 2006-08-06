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
package org.tmatesoft.svn.core.internal.wc.admin;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.internal.util.SVNEncodingUtil;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.util.SVNTimeUtil;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc.SVNProperties;
import org.tmatesoft.svn.core.internal.wc.SVNWCAccess;

/**
 * @version 1.0
 * @author TMate Software Ltd.
 */
public class SVNXMLEntries extends SVNAdminArea {
    public static final int WC_FORMAT = 4;
    private static final String THIS_DIR = "";

    public SVNXMLEntries(SVNWCAccess wcAccess, String path, File dir) {
        super(wcAccess, path, dir);
    }

    protected void saveProperties(boolean close) throws SVNException {
        Map propsCache = getPropertiesStorage(false);
        if (propsCache == null) {
            return;
        }
        
        for(Iterator entries = propsCache.keySet().iterator(); entries.hasNext();) {
            String name = (String)entries.next();
            ISVNProperties props = (ISVNProperties)propsCache.get(name);
            if (props.isModified()) {
                String dstPath = getThisDirName().equals(name) ? "dir-props" : "props/" + name + ".svn-work";
                File dstFile = getAdminFile(dstPath);
                if (props.isEmpty()) {
                    SVNFileUtil.deleteFile(dstFile);
                } else {
                    String tmpPath = "tmp/";
                    tmpPath += getThisDirName().equals(name) ? "dir-props" : "props/" + name + ".svn-work";
                    File tmpFile = getAdminFile(tmpPath);
                    SVNProperties.setProperties(props.asMap(), dstFile, tmpFile, SVNProperties.SVN_HASH_TERMINATOR);
                }
            }
        }
        if (close) {
            propsCache.clear();
        }
    }
    
    protected void saveBaseProperties(boolean close) throws SVNException {
        Map basePropsCache = getBasePropertiesStorage(false);
        if (basePropsCache == null) { 
            return;
        }
        
        for(Iterator entries = basePropsCache.keySet().iterator(); entries.hasNext();) {
            String name = (String)entries.next();
            ISVNProperties props = (ISVNProperties)basePropsCache.get(name);
            if (props.isModified()) {
                String dstPath = getThisDirName().equals(name) ? "dir-prop-base" : "prop-base/" + name + ".svn-base";
                File dstFile = getAdminFile(dstPath);
                if (props.isEmpty()) {
                    SVNFileUtil.deleteFile(dstFile);
                } else {
                    String tmpPath = "tmp/";
                    tmpPath += getThisDirName().equals(name) ? "dir-prop-base" : "prop-base/" + name + ".svn-base";
                    File tmpFile = getAdminFile(tmpPath);
                    SVNProperties.setProperties(props.asMap(), dstFile, tmpFile, SVNProperties.SVN_HASH_TERMINATOR);
                }
            }
        }
        if (close) {
            basePropsCache.clear();
        }
    }
    
    protected void saveWCProperties(boolean close) throws SVNException {
        Map wcPropsCache = getWCPropertiesStorage(false);
        if (wcPropsCache == null) {
            return;
        }
        
        for(Iterator entries = wcPropsCache.keySet().iterator(); entries.hasNext();) {
            String name = (String)entries.next();
            ISVNProperties props = (ISVNProperties)wcPropsCache.get(name);
            if (props.isModified()) {
                String dstPath = getThisDirName().equals(name) ? "dir-wcprops" : "wcprops/" + name + ".svn-work";
                File dstFile = getAdminFile(dstPath);
                if (props.isEmpty()) {
                    SVNFileUtil.deleteFile(dstFile);
                } else {
                    String tmpPath = "tmp/";
                    tmpPath += getThisDirName().equals(name) ? "dir-wcprops" : "wcprops/" + name + ".svn-work";
                    File tmpFile = getAdminFile(tmpPath);
                    SVNProperties.setProperties(props.asMap(), dstFile, tmpFile, SVNProperties.SVN_HASH_TERMINATOR);
                }
            }
        }
        if (close) {
            wcPropsCache.clear();
        }
    }
    
    public ISVNProperties getBaseProperties(String name) throws SVNException {
        Map basePropsCache = getBasePropertiesStorage(true);
        ISVNProperties props = (ISVNProperties)basePropsCache.get(name); 
        if (props != null) {
            return props;
        }
        
        Map baseProps = null;
        try {
            baseProps = readBaseProperties(name);
        } catch (SVNException svne) {
            SVNErrorMessage err = svne.getErrorMessage().wrap("Failed to load properties from disk");
            SVNErrorManager.error(err);
        }

        props = new SVNProperties13(baseProps);
        basePropsCache.put(name, props);
        return props;
    }

    public ISVNProperties getProperties(String name) throws SVNException {
        Map propsCache = getPropertiesStorage(true);
        ISVNProperties props = (ISVNProperties)propsCache.get(name); 
        if (props != null) {
            return props;
        }
        
        Map properties = null;
        try {
            properties = readProperties(name);
        } catch (SVNException svne) {
            SVNErrorMessage err = svne.getErrorMessage().wrap("Failed to load properties from disk");
            SVNErrorManager.error(err);
        }
        
        props = new SVNProperties13(properties);
        propsCache.put(name, props);
        return props;
    }

    public ISVNProperties getWCProperties(String name) throws SVNException {
        Map wcPropsCache = getWCPropertiesStorage(true);
        ISVNProperties props = (ISVNProperties)wcPropsCache.get(name); 
        if (props != null) {
            return props;
        }
        
        Map properties = null;
        try {
            properties = readWCProperties(name);
        } catch (SVNException svne) {
            SVNErrorMessage err = svne.getErrorMessage().wrap("Failed to load properties from disk");
            SVNErrorManager.error(err);
        }

        props = new SVNProperties13(properties);
        wcPropsCache.put(name, props);
        return props;
    }
    
    private Map readProperties(String name) throws SVNException {
        //String path = !tmp ? "" : "tmp/";
        String path = getThisDirName().equals(name) ? "dir-props" : "props/" + name + ".svn-work";
        File propertiesFile = getAdminFile(path);
        SVNProperties props = new SVNProperties(propertiesFile, getAdminDirectory().getName() + "/" + path);
        return props.asMap();
    }

    private Map readBaseProperties(String name) throws SVNException {
        //String path = !tmp ? "" : "tmp/";
        String path = getThisDirName().equals(name) ? "dir-prop-base" : "prop-base/" + name + ".svn-base";
        File propertiesFile = getAdminFile(path);
        SVNProperties props = new SVNProperties(propertiesFile, getAdminDirectory().getName() + "/" + path);
        return props.asMap();
    }

    private Map readWCProperties(String name) throws SVNException {
        String path = getThisDirName().equals(name) ? "dir-wcprops" : "wcprops/" + name + ".svn-work";
        File propertiesFile = getAdminFile(path);
        SVNProperties props = new SVNProperties(propertiesFile, getAdminDirectory().getName() + "/" + path);
        return props.asMap();
    }

    protected void fetchEntries() throws IOException, SVNException {
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new InputStreamReader(SVNFileUtil.openFileForReading(myEntriesFile), "UTF-8"));
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
                            return;
                        }
                        myEntries.putEntry(entryName, entry);
                        if (!getThisDirName().equals(entryName)) {
                            Map rootEntry = myEntries.getEntryMap(getThisDirName());
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
        Map rootEntry = myEntries.getEntryMap(getThisDirName());

        buffer.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n");
        buffer.append("<wc-entries\n");
        buffer.append("   xmlns=\"svn:\">\n");
        for (Iterator entriesIter = myEntries.entries(true); entriesIter.hasNext();) {
            SVNEntry entry = (SVNEntry)entriesIter.next();
            String name = entry.getName();
            Map entryMap = myEntries.getEntryMap(name);
            buffer.append("<entry");
            for (Iterator names = entryMap.keySet().iterator(); names.hasNext();) {
                String propName = (String) names.next();
                String propValue = (String) entryMap.get(propName);
                if (propValue == null) {
                    continue;
                }
                if (BOOLEAN_PROPERTIES.contains(propName) && !Boolean.TRUE.toString().equals(propValue)) {
                    continue;
                }
                if (!getThisDirName().equals(name)) {
                    Object expectedValue;
                    if (SVNProperty.KIND_DIR.equals(entryMap.get(SVNProperty.KIND))) {
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

    public boolean hasPropModifications(String name) throws SVNException {
        File propFile;
        File baseFile;
        if ("".equals(name)) {
            propFile = getAdminFile("dir-props");
            baseFile = getAdminFile("dir-prop-base");
        } else {
            propFile = getAdminFile("props/" + name + ".svn-work");
            baseFile = getAdminFile("prop-base/" + name + ".svn-base");
        }
        SVNEntry entry = getEntries().getEntry(name, true);
        long propLength = propFile.length();
        boolean propEmtpy = propLength <= 4;
        if (entry.isScheduledForReplacement()) {
            return !propEmtpy;
        }
        if (propEmtpy) {
            boolean baseEmtpy = baseFile.length() <= 4;
            if (baseEmtpy) {
                return !propEmtpy;
            }
            return true;
        }
        if (propLength != baseFile.length()) {
            return true;
        }
        String realTimestamp = SVNTimeUtil.formatDate(new Date(propFile.lastModified()));
        String fullRealTimestamp = realTimestamp;
        realTimestamp = realTimestamp.substring(0, 23);
        String timeStamp = entry.getPropTime();
        if (timeStamp != null) {
            timeStamp = timeStamp.substring(0, 23);
            if (realTimestamp.equals(timeStamp)) {
                return false;
            }
        }
        ISVNProperties m1 = getProperties(name);
        ISVNProperties m2 = getBaseProperties(name);
        if (m1.equals(m2)) {
            if (isLocked()) {
                entry.setPropTime(fullRealTimestamp);
                saveEntries(false);
            }
            return false;
        }
        return true;
    }

    protected int getFormatNumber() {
        return WC_FORMAT;
    }
}
