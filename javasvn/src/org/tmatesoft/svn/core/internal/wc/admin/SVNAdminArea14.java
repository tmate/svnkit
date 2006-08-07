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
import java.io.OutputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.internal.io.fs.FSFile;
import org.tmatesoft.svn.core.internal.util.SVNEncodingUtil;
import org.tmatesoft.svn.core.internal.util.SVNFormatUtil;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc.SVNProperties;
import org.tmatesoft.svn.core.internal.wc.SVNWCAccess;


public class SVNAdminArea14 extends SVNAdminArea {
    private static final String[] ourCachableProperties = new String[] {
        SVNProperty.SPECIAL,
        SVNProperty.EXTERNALS, 
        SVNProperty.NEEDS_LOCK
    };

    public static final int WC_FORMAT = 8;
    
    private static final String ATTRIBUTE_COPIED = "copied";
    private static final String ATTRIBUTE_DELETED = "deleted";
    private static final String ATTRIBUTE_ABSENT = "absent";
    private static final String ATTRIBUTE_INCOMPLETE = "incomplete";
    private static final String THIS_DIR = "";

    public SVNAdminArea14(SVNWCAccess wcAccess, String path, File dir) {
        super(wcAccess, path, dir);
    }

    public static String[] getCachableProperties() {
        return ourCachableProperties;
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
                String tmpPath = "tmp/";
                tmpPath += getThisDirName().equals(name) ? "dir-props" : "props/" + name + ".svn-work";
                File tmpFile = getAdminFile(tmpPath);
                File dstFile = getAdminFile(dstPath);
                String terminator = props.isEmpty() ? null : SVNProperties.SVN_HASH_TERMINATOR; 
                SVNProperties.setProperties(props.asMap(), dstFile, tmpFile, terminator);
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
        
        boolean hasAnyProps = false;
        File dstFile = getAdminFile("all-wcprops");
        File tmpFile = getAdminFile("tmp/all-wcprops");

        for(Iterator entries = wcPropsCache.keySet().iterator(); entries.hasNext();) {
            String name = (String)entries.next();
            ISVNProperties props = (ISVNProperties)wcPropsCache.get(name);
            if (!props.isEmpty()) {
                hasAnyProps = true;
                break;
            }
        }

        if (hasAnyProps) {
            OutputStream target = null;
            try {
                target = SVNFileUtil.openFileForWriting(tmpFile);
                ISVNProperties props = (ISVNProperties)wcPropsCache.get(getThisDirName());
                if (props != null && !props.isEmpty()) {
                    SVNProperties.setProperties(props.asMap(), target, SVNProperties.SVN_HASH_TERMINATOR);
                } else {
                    SVNProperties.setProperties(Collections.EMPTY_MAP, target, SVNProperties.SVN_HASH_TERMINATOR);
                }
    
                for(Iterator entries = wcPropsCache.keySet().iterator(); entries.hasNext();) {
                    String name = (String)entries.next();
                    if (getThisDirName().equals(name)) {
                        continue;
                    }
                    props = (ISVNProperties)wcPropsCache.get(name);
                    if (!props.isEmpty()) {
                        target.write(name.getBytes("UTF-8"));
                        target.write('\n');
                        SVNProperties.setProperties(props.asMap(), target, SVNProperties.SVN_HASH_TERMINATOR);
                    }
                }
            } catch (IOException ioe) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, ioe.getLocalizedMessage());
                SVNErrorManager.error(err, ioe);
            } finally {
                SVNFileUtil.closeFile(target);
            }
            SVNFileUtil.rename(tmpFile, dstFile);
            SVNFileUtil.setReadonly(dstFile, true);
        } else {
            SVNFileUtil.deleteFile(dstFile);
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
        
        final String entryName = name;
        props =  new SVNProperties14(this, name){

            protected Map loadProperties() throws SVNException {
                if (myProperties == null) {
                    try {
                        myProperties = readProperties(entryName);
                    } catch (SVNException svne) {
                        SVNErrorMessage err = svne.getErrorMessage().wrap("Failed to load properties from disk");
                        SVNErrorManager.error(err);
                    }
                    myProperties = myProperties != null ? myProperties : new HashMap();
                }
                return myProperties;
            }
        };

        propsCache.put(name, props);
        return props;
    }

    public ISVNProperties getWCProperties(String entryName) throws SVNException {
        Map wcPropsCache = getWCPropertiesStorage(true);
        ISVNProperties props = (ISVNProperties)wcPropsCache.get(entryName); 
        if (props != null) {
            return props;
        }
        
        SVNEntry entry = getEntries().getEntry(entryName, false);
        if (entry == null) {
            props = new SVNProperties13(new HashMap());
            wcPropsCache.put(entryName, props);
        } else {
            File propertiesFile = getAdminFile("all-wcprops");
            if (!propertiesFile.exists()) {
                props = new SVNProperties13(new HashMap());
                wcPropsCache.put(entryName, props);
            } else {
                FSFile wcpropsFile = null;
                try {
                    wcpropsFile = new FSFile(propertiesFile);
                    Map wcProps = wcpropsFile.readProperties(false);
                    ISVNProperties entryWCProps = new SVNProperties13(wcProps); 
                    wcPropsCache.put(getThisDirName(), entryWCProps);
                        
                    String name = null;
                    StringBuffer buffer = new StringBuffer();
                    while(true) {
                        try {
                            name = wcpropsFile.readLine(buffer);
                        } catch (SVNException e) {
                            if (e.getErrorMessage().getErrorCode() == SVNErrorCode.STREAM_UNEXPECTED_EOF && buffer.length() > 0) {
                                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_CORRUPT, "Missing end of line in wcprops file for ''{0}''", getRoot());
                                SVNErrorManager.error(err, e);
                            }
                            break;
                        }
                        wcProps = wcpropsFile.readProperties(false);
                        entryWCProps = new SVNProperties13(wcProps);
                        wcPropsCache.put(name, entryWCProps);
                        buffer.delete(0, buffer.length());
                    }
                } catch (SVNException svne) {
                    SVNErrorMessage err = svne.getErrorMessage().wrap("Failed to load properties from disk");
                    SVNErrorManager.error(err);
                } finally {
                    wcpropsFile.close();
                }
                props = (ISVNProperties)wcPropsCache.get(entryName); 
                if (props == null) {
                    props = new SVNProperties13(new HashMap());
                    wcPropsCache.put(entryName, props);
                }
            }
        }
        return props;
    }
    
    private Map readBaseProperties(String name) throws SVNException {
        //String path = !tmp ? "" : "tmp/";
        String path = getThisDirName().equals(name) ? "dir-prop-base" : "prop-base/" + name + ".svn-base";
        File propertiesFile = getAdminFile(path);
        SVNProperties props = new SVNProperties(propertiesFile, getAdminDirectory().getName() + "/" + path);
        return props.asMap();
    }
    
    private Map readProperties(String name) throws SVNException {
        if (hasPropModifications(name)) {
            //String path = !tmp ? "" : "tmp/";
            String path = getThisDirName().equals(name) ? "dir-props" : "props/" + name + ".svn-work";
            File propertiesFile = getAdminFile(path);
            SVNProperties props = new SVNProperties(propertiesFile, getAdminDirectory().getName() + "/" + path);
            return props.asMap();
        }
        return new HashMap();
    }


    protected void fetchEntries() throws IOException, SVNException {
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new InputStreamReader(SVNFileUtil.openFileForReading(myEntriesFile), "UTF-8"));
            //skip format line
            reader.readLine();
            int entryNumber = 1;
            while(true){
                try {
                    if (readEntry(reader, entryNumber) == null) {
                        break;
                    }
                } catch (SVNException svne) {
                    SVNErrorMessage err = svne.getErrorMessage().wrap("Error at entry {0,number,integer} in entries file for ''{1}'':", new Object[]{new Integer(entryNumber), getRoot()});
                    SVNErrorManager.error(err);
                }
                ++entryNumber;
            }
        } finally {
            SVNFileUtil.closeFile(reader);
        }
        resolveToDefaults();
    }

    private void resolveToDefaults() throws SVNException {
        Map defaultEntry = myEntries.getEntryMap(getThisDirName());
        if (defaultEntry == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_NOT_FOUND, "Missing default entry");
            SVNErrorManager.error(err);
        }
        
        if (defaultEntry.get(SVNProperty.REVISION) == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_MISSING_REVISION, "Default entry has no revision number");
            SVNErrorManager.error(err);
        }

        if (defaultEntry.get(SVNProperty.URL) == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_MISSING_URL, "Default entry is missing URL");
            SVNErrorManager.error(err);
        }

        for (Iterator entries = myEntries.entries(true); entries.hasNext();) {
            SVNEntry entry = (SVNEntry)entries.next();
            String name = entry.getName();
            if (getThisDirName().equals(name)) {
                continue;
            }
            
            Map entryAttributes = myEntries.getEntryMap(name);
            SVNNodeKind kind = SVNNodeKind.parseKind((String)entryAttributes.get(SVNProperty.KIND));
            if (kind == SVNNodeKind.FILE) {
                if (entryAttributes.get(SVNProperty.REVISION) == null) {
                    entryAttributes.put(SVNProperty.REVISION, defaultEntry.get(SVNProperty.REVISION));
                }
                if (entryAttributes.get(SVNProperty.URL) == null) {
                    String rootURL = (String)defaultEntry.get(SVNProperty.URL);
                    String url = SVNPathUtil.append(rootURL, SVNEncodingUtil.uriEncode(name));
                    entryAttributes.put(SVNProperty.URL, url);
                }
                if (entryAttributes.get(SVNProperty.REPOS) == null) {
                    entryAttributes.put(SVNProperty.REPOS, defaultEntry.get(SVNProperty.REPOS));
                }
                if (entryAttributes.get(SVNProperty.UUID) == null) {
                    String schedule = (String)entryAttributes.get(SVNProperty.SCHEDULE);
                    if (!(SVNProperty.SCHEDULE_ADD.equals(schedule) || SVNProperty.SCHEDULE_REPLACE.equals(schedule))) {
                        entryAttributes.put(SVNProperty.UUID, defaultEntry.get(SVNProperty.UUID));
                    }
                }
                if (entryAttributes.get(SVNProperty.CACHABLE_PROPS) == null) {
                    entryAttributes.put(SVNProperty.CACHABLE_PROPS, defaultEntry.get(SVNProperty.CACHABLE_PROPS));
                }
            }
        }
    }
    
    private Map readEntry(BufferedReader reader, int entryNumber) throws IOException, SVNException {
        String line = reader.readLine();
        if (line == null && entryNumber > 1) {
            return null;
        }

        String name = parseString(line);
        name = name != null ? name : getThisDirName();

        Map entry = new HashMap();
        entry.put(SVNProperty.NAME, name);

        line = reader.readLine();
        String kind = parseValue(line);
        if (kind != null) {
            SVNNodeKind parsedKind = SVNNodeKind.parseKind(kind); 
            if (parsedKind != SVNNodeKind.UNKNOWN && parsedKind != SVNNodeKind.NONE) {
                entry.put(SVNProperty.KIND, kind);
            } else {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.NODE_UNKNOWN_KIND, "Entry ''{0}'' has invalid node kind", name);
                SVNErrorManager.error(err);
            }
        } else {
            entry.put(SVNProperty.KIND, SVNNodeKind.NONE.toString());
        }
        
        line = reader.readLine();
        if (isEntryFinished(line)) {
            myEntries.putEntry(name, entry);
            return entry;
        }
        String revision = parseValue(line);
        if (revision != null) {
            entry.put(SVNProperty.REVISION, revision);
        }
        
        line = reader.readLine();
        if (isEntryFinished(line)) {
            myEntries.putEntry(name, entry);
            return entry;
        }
        String url = parseString(line);
        if (url == null) {
            entry.put(SVNProperty.URL, url);
        }
        
        line = reader.readLine();
        if (isEntryFinished(line)) {
            myEntries.putEntry(name, entry);
            return entry;
        }
        String reposRoot = parseString(line);
        if (reposRoot != null && url != null && !SVNPathUtil.isAncestor(reposRoot, url)) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_CORRUPT, "Entry for ''{0}'' has invalid repository root", name);
            SVNErrorManager.error(err);
        } else if (reposRoot != null) {
            entry.put(SVNProperty.REPOS, reposRoot);
        }
        
        line = reader.readLine();
        if (isEntryFinished(line)) {
            myEntries.putEntry(name, entry);
            return entry;
        }
        String schedule = parseValue(line);
        if (schedule != null) {
            if (SVNProperty.SCHEDULE_ADD.equals(schedule) || SVNProperty.SCHEDULE_DELETE.equals(schedule) || SVNProperty.SCHEDULE_REPLACE.equals(schedule)) {
                entry.put(SVNProperty.SCHEDULE, schedule);
            } else {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_ATTRIBUTE_INVALID, "Entry ''{0}'' has invalid ''{1}'' value", new Object[]{name, SVNProperty.SCHEDULE});
                SVNErrorManager.error(err);
            }
        }
        
        line = reader.readLine();
        if (isEntryFinished(line)) {
            myEntries.putEntry(name, entry);
            return entry;
        }
        String timestamp = parseValue(line);
        if (timestamp != null) {
            entry.put(SVNProperty.TEXT_TIME, timestamp);
        }
        
        line = reader.readLine();
        if (isEntryFinished(line)) {
            myEntries.putEntry(name, entry);
            return entry;
        }
        String checksum = parseString(line);
        if (checksum != null) {
            entry.put(SVNProperty.CHECKSUM, checksum);
        }

        line = reader.readLine();
        if (isEntryFinished(line)) {
            myEntries.putEntry(name, entry);
            return entry;
        }
        String committedDate = parseValue(line);
        if (committedDate != null) {
            entry.put(SVNProperty.COMMITTED_DATE, committedDate);
        }

        line = reader.readLine();
        if (isEntryFinished(line)) {
            myEntries.putEntry(name, entry);
            return entry;
        }
        String committedRevision = parseValue(line);
        if (committedRevision != null) {
            entry.put(SVNProperty.COMMITTED_REVISION, committedRevision);
        }
        
        line = reader.readLine();
        if (isEntryFinished(line)) {
            myEntries.putEntry(name, entry);
            return entry;
        }
        String committedAuthor = parseString(line);
        if (committedAuthor != null) {
            entry.put(SVNProperty.LAST_AUTHOR, checksum);
        }
        
        line = reader.readLine();
        if (isEntryFinished(line)) {
            myEntries.putEntry(name, entry);
            return entry;
        }
        boolean hasProps = parseBoolean(line, SVNProperty.HAS_PROPS);
        if (hasProps) {
            entry.put(SVNProperty.HAS_PROPS, SVNProperty.toString(hasProps));
        }

        line = reader.readLine();
        if (isEntryFinished(line)) {
            myEntries.putEntry(name, entry);
            return entry;
        }
        boolean hasPropMods = parseBoolean(line, SVNProperty.HAS_PROP_MODS);
        if (hasPropMods) {
            entry.put(SVNProperty.HAS_PROP_MODS, SVNProperty.toString(hasPropMods));
        }

        line = reader.readLine();
        if (isEntryFinished(line)) {
            myEntries.putEntry(name, entry);
            return entry;
        }
        String cachablePropsStr = parseValue(line);
        if (cachablePropsStr != null) {
            String[] cachableProps = fromString(cachablePropsStr, " ");
            entry.put(SVNProperty.CACHABLE_PROPS, cachableProps);
        }
        
        line = reader.readLine();
        if (isEntryFinished(line)) {
            myEntries.putEntry(name, entry);
            return entry;
        }
        String presentPropsStr = parseValue(line);
        if (presentPropsStr != null) {
            String[] presentProps = fromString(presentPropsStr, " ");
            entry.put(SVNProperty.PRESENT_PROPS, presentProps);
        }
        
        line = reader.readLine();
        if (isEntryFinished(line)) {
            myEntries.putEntry(name, entry);
            return entry;
        }
        String prejFile = parseString(line);
        if (prejFile != null) {
            entry.put(SVNProperty.PROP_REJECT_FILE, prejFile);
        }

        line = reader.readLine();
        if (isEntryFinished(line)) {
            myEntries.putEntry(name, entry);
            return entry;
        }
        String conflictOldFile = parseString(line);
        if (conflictOldFile != null) {
            entry.put(SVNProperty.CONFLICT_OLD, conflictOldFile);
        }

        line = reader.readLine();
        if (isEntryFinished(line)) {
            myEntries.putEntry(name, entry);
            return entry;
        }
        String conflictNewFile = parseString(line);
        if (conflictNewFile != null) {
            entry.put(SVNProperty.CONFLICT_NEW, conflictNewFile);
        }

        line = reader.readLine();
        if (isEntryFinished(line)) {
            myEntries.putEntry(name, entry);
            return entry;
        }
        String conflictWorkFile = parseString(line);
        if (conflictWorkFile != null) {
            entry.put(SVNProperty.CONFLICT_WRK, conflictWorkFile);
        }

        line = reader.readLine();
        if (isEntryFinished(line)) {
            myEntries.putEntry(name, entry);
            return entry;
        }
        boolean isCopied = parseBoolean(line, ATTRIBUTE_COPIED);
        if (isCopied) {
            entry.put(SVNProperty.COPIED, SVNProperty.toString(isCopied));
        }

        line = reader.readLine();
        if (isEntryFinished(line)) {
            myEntries.putEntry(name, entry);
            return entry;
        }
        String copyfromURL = parseString(line);
        if (copyfromURL != null) {
            entry.put(SVNProperty.COPYFROM_URL, copyfromURL);
        }
        
        line = reader.readLine();
        if (isEntryFinished(line)) {
            myEntries.putEntry(name, entry);
            return entry;
        }
        String copyfromRevision = parseValue(line);
        if (copyfromRevision != null) {
            entry.put(SVNProperty.COPYFROM_REVISION, copyfromRevision);
        }
        
        line = reader.readLine();
        if (isEntryFinished(line)) {
            myEntries.putEntry(name, entry);
            return entry;
        }
        boolean isDeleted = parseBoolean(line, ATTRIBUTE_DELETED);
        if (isDeleted) {
            entry.put(SVNProperty.DELETED, SVNProperty.toString(isDeleted));
        }

        line = reader.readLine();
        if (isEntryFinished(line)) {
            myEntries.putEntry(name, entry);
            return entry;
        }
        boolean isAbsent = parseBoolean(line, ATTRIBUTE_ABSENT);
        if (isAbsent) {
            entry.put(SVNProperty.ABSENT, SVNProperty.toString(isAbsent));
        }

        line = reader.readLine();
        if (isEntryFinished(line)) {
            myEntries.putEntry(name, entry);
            return entry;
        }
        boolean isIncomplete = parseBoolean(line, ATTRIBUTE_INCOMPLETE);
        if (isIncomplete) {
            entry.put(SVNProperty.INCOMPLETE, SVNProperty.toString(isIncomplete));
        }

        line = reader.readLine();
        if (isEntryFinished(line)) {
            myEntries.putEntry(name, entry);
            return entry;
        }
        String uuid = parseString(line);
        if (uuid != null) {
            entry.put(SVNProperty.UUID, uuid);
        }
        
        line = reader.readLine();
        if (isEntryFinished(line)) {
            myEntries.putEntry(name, entry);
            return entry;
        }
        String lockToken = parseString(line);
        if (lockToken != null) {
            entry.put(SVNProperty.LOCK_TOKEN, lockToken);
        }
        
        line = reader.readLine();
        if (isEntryFinished(line)) {
            myEntries.putEntry(name, entry);
            return entry;
        }
        String lockOwner = parseString(line);
        if (lockOwner != null) {
            entry.put(SVNProperty.LOCK_OWNER, lockOwner);
        }
        
        line = reader.readLine();
        if (isEntryFinished(line)) {
            myEntries.putEntry(name, entry);
            return entry;
        }
        String lockComment = parseString(line);
        if (lockComment != null) {
            entry.put(SVNProperty.LOCK_COMMENT, lockComment);
        }
        
        line = reader.readLine();
        if (isEntryFinished(line)) {
            myEntries.putEntry(name, entry);
            return entry;
        }
        String lockCreationDate = parseValue(line);
        if (lockCreationDate != null) {
            entry.put(SVNProperty.LOCK_CREATION_DATE, lockCreationDate);
        }

        myEntries.putEntry(name, entry);
        line = reader.readLine();
        if (line == null || line.length() != 1) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_CORRUPT, "Missing entry terminator");
            SVNErrorManager.error(err);
        } else if (line.length() == 1 && line.charAt(0) != '\f') {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_CORRUPT, "Invalid entry terminator");
            SVNErrorManager.error(err);
        }
        return entry;
    }
    
    private boolean isEntryFinished(String line) {
        return line != null && line.charAt(0) == '\f';
    }
    
    private boolean parseBoolean(String line, String field) throws SVNException {
        line = parseValue(line);
        if (line != null) {
            if (!line.equals(field)) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_CORRUPT, "Invalid value for field ''{0}''", field);
                SVNErrorManager.error(err);
            }
            return true;
        }
        return false;
    }
    
    private String parseString(String line) throws SVNException {
        if (line == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_CORRUPT, "Unexpected end of entry");
            SVNErrorManager.error(err);
        } else if ("".equals(line)) {
            return null;
        }
        
        int fromIndex = 0;
        int ind = -1;
        StringBuffer buffer = null;
        String escapedString = null;
        while ((ind = line.indexOf('\\', fromIndex)) != -1) {
            if (line.length() < ind + 4 || line.charAt(ind + 1) != 'x' || !SVNEncodingUtil.isHexDigit(line.charAt(ind + 2)) || !SVNEncodingUtil.isHexDigit(line.charAt(ind + 3))) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_CORRUPT, "Invalid escape sequence");
                SVNErrorManager.error(err);
            }
            if (buffer == null) {
                buffer = new StringBuffer();
            }

            escapedString = line.substring(ind + 2, ind + 4);  
            int escapedByte = Integer.parseInt(escapedString, 16);
            
            if (ind > fromIndex) {
                buffer.append(line.substring(fromIndex, ind));
                buffer.append((char)(escapedByte & 0xFF));
            } else {
                buffer.append((char)(escapedByte & 0xFF));
            }
            fromIndex = ind + 4;
        }
        
        if (buffer != null) {
            if (fromIndex < line.length()) {
                buffer.append(line.substring(fromIndex));
            }
            return buffer.toString();
        }   
        return line;
    }
    
    private String parseValue(String line) throws SVNException {
        if (line == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_CORRUPT, "Unexpected end of entry");
            SVNErrorManager.error(err);
        } else if ("".equals(line)) {
            return null;
        }
        return line;
    }
    
    public String getThisDirName() {
        return THIS_DIR;
    }
    
    protected String formatEntries() {
        StringBuffer buffer = new StringBuffer();
        Map rootEntry = myEntries.getEntryMap(getThisDirName());

        buffer.append(getFormatNumber());
        buffer.append('\n');
        writeEntry(buffer, getThisDirName(), rootEntry, null);

        for (Iterator entries = myEntries.entries(true); entries.hasNext();) {
            SVNEntry entry = (SVNEntry)entries.next();
            String name = entry.getName();
            if (getThisDirName().equals(name)) {
                continue;
            }
            Map entryMap = myEntries.getEntryMap(name);
            writeEntry(buffer, name, entryMap, rootEntry);
        }
        return buffer.toString();
    }

    private void writeEntry(StringBuffer buffer, String name, Map entry, Map rootEntry) {
        boolean isThisDir = getThisDirName().equals(name);
        boolean isSubDir = !isThisDir && SVNProperty.KIND_DIR.equals(entry.get(SVNProperty.KIND)); 
        int emptyFields = 0;
        writeString(buffer, name, emptyFields);
        
        String kind = (String)entry.get(SVNProperty.KIND);
        if (writeValue(buffer, kind, emptyFields)){
            emptyFields = 0;
        } else {
            ++emptyFields;
        }

        String revision = null;
        if (isThisDir){ 
            revision = (String)entry.get(SVNProperty.REVISION);
        } else if (!isSubDir){
            revision = (String)entry.get(SVNProperty.REVISION);
            if (revision != null && revision.equals(rootEntry.get(SVNProperty.REVISION))) {
                revision = null;
            }
        }
        if (writeRevision(buffer, revision, emptyFields)) {
            emptyFields = 0;
        } else {
            ++emptyFields;
        }
        
        String url = null;
        if (isThisDir) {
            url = (String)entry.get(SVNProperty.URL);
        } else if (!isSubDir) {
            url = (String)entry.get(SVNProperty.URL);
            String expectedURL = SVNPathUtil.append((String)rootEntry.get(SVNProperty.URL), name);
            if (url != null && url.equals(expectedURL)) {
                url = null;
            }
        }
        if (writeString(buffer, url, emptyFields)) {
            emptyFields = 0;
        } else {
            ++emptyFields;
        }
        
        String root = null;
        if (isThisDir) {
            root = (String)entry.get(SVNProperty.REPOS);
        } else if (!isSubDir) {
            String thisDirRoot = (String)rootEntry.get(SVNProperty.REPOS);
            root = (String)entry.get(SVNProperty.REPOS);
            if (root != null && root.equals(thisDirRoot)) {
                root = null;
            }
        }
        if (writeString(buffer, root, emptyFields)) {
            emptyFields = 0;
        } else {
            ++emptyFields;
        }
        
        String schedule = (String)entry.get(SVNProperty.SCHEDULE);
        if (schedule != null && (!SVNProperty.SCHEDULE_ADD.equals(schedule) && !SVNProperty.SCHEDULE_DELETE.equals(schedule) && !SVNProperty.SCHEDULE_REPLACE.equals(schedule))) {
            schedule = null;
        }
        if (writeValue(buffer, schedule, emptyFields)) {
            emptyFields = 0;
        } else {
            ++emptyFields;
        }
        
        String textTime = (String)entry.get(SVNProperty.TEXT_TIME);
        if (writeValue(buffer, textTime, emptyFields)) {
            emptyFields = 0;
        } else {
            ++emptyFields;
        }
        
        String checksum = (String)entry.get(SVNProperty.CHECKSUM);
        if (writeValue(buffer, checksum, emptyFields)) {
            emptyFields = 0;
        } else {
            ++emptyFields;
        }
        
        String committedDate = (String)entry.get(SVNProperty.COMMITTED_DATE);
        if (writeValue(buffer, committedDate, emptyFields)) {
            emptyFields = 0;
        } else {
            ++emptyFields;
        }

        String committedRevision = (String)entry.get(SVNProperty.COMMITTED_REVISION);
        if (writeRevision(buffer, committedRevision, emptyFields)) {
            emptyFields = 0;
        } else {
            ++emptyFields;
        }
        
        String committedAuthor = (String)entry.get(SVNProperty.LAST_AUTHOR);
        if (writeString(buffer, committedAuthor, emptyFields)) {
            emptyFields = 0;
        } else {
            ++emptyFields;
        }
        
        String hasProps = (String)entry.get(SVNProperty.HAS_PROPS);
        if (writeValue(buffer, hasProps != null && SVNProperty.booleanValue(hasProps) ? SVNProperty.HAS_PROPS : null, emptyFields)) {
            emptyFields = 0;
        } else {
            ++emptyFields;
        }

        String hasPropMods = (String)entry.get(SVNProperty.HAS_PROP_MODS);
        if (writeValue(buffer, hasPropMods != null && SVNProperty.booleanValue(hasPropMods)? SVNProperty.HAS_PROP_MODS : null, emptyFields)) {
            emptyFields = 0;
        } else {
            ++emptyFields;
        }
        
        String cachableProps = asString((String[])entry.get(SVNProperty.CACHABLE_PROPS), " ");
        if (!isThisDir) {             
            String thisDirCachableProps = asString((String[])rootEntry.get(SVNProperty.CACHABLE_PROPS), " ");
            if (thisDirCachableProps != null && cachableProps != null && thisDirCachableProps.equals(cachableProps)) {
                cachableProps = null;
            }
        }
        if (writeValue(buffer, cachableProps, emptyFields)) {
            emptyFields = 0;
        } else {
            ++emptyFields;
        }

        String presentProps = asString((String[])entry.get(SVNProperty.PRESENT_PROPS), " ");
        if (writeValue(buffer, presentProps, emptyFields)) {
            emptyFields = 0;
        } else {
            ++emptyFields;
        }
        
        String propRejectFile = (String)entry.get(SVNProperty.PROP_REJECT_FILE);
        if (writeString(buffer, propRejectFile, emptyFields)) {
            emptyFields = 0;
        } else {
            ++emptyFields;
        }
        
        String conflictOldFile = (String)entry.get(SVNProperty.CONFLICT_OLD);
        if (writeString(buffer, conflictOldFile, emptyFields)) {
            emptyFields = 0;
        } else {
            ++emptyFields;
        }

        String conflictNewFile = (String)entry.get(SVNProperty.CONFLICT_NEW);
        if (writeString(buffer, conflictNewFile, emptyFields)) {
            emptyFields = 0;
        } else {
            ++emptyFields;
        }
    
        String conflictWrkFile = (String)entry.get(SVNProperty.CONFLICT_WRK);
        if (writeString(buffer, conflictWrkFile, emptyFields)) {
            emptyFields = 0;
        } else {
            ++emptyFields;
        }

        String copiedAttr = (String)entry.get(SVNProperty.COPIED);
        if (writeValue(buffer, copiedAttr != null ? ATTRIBUTE_COPIED : null, emptyFields)) {
            emptyFields = 0;
        } else {
            ++emptyFields;
        }
        
        String copyfromURL = (String)entry.get(SVNProperty.COPYFROM_URL);
        if (writeString(buffer, copyfromURL, emptyFields)) {
            emptyFields = 0;
        } else {
            ++emptyFields;
        }
        
        String copyfromRevision = (String)entry.get(SVNProperty.COPYFROM_REVISION);
        if (writeRevision(buffer, copyfromRevision, emptyFields)) {
            emptyFields = 0;
        } else {
            ++emptyFields;
        }
        
        String deletedAttr = (String)entry.get(SVNProperty.DELETED);
        if (writeValue(buffer, deletedAttr != null ? ATTRIBUTE_DELETED : null, emptyFields)) {
            emptyFields = 0;
        } else {
            ++emptyFields;
        }
        
        String absentAttr = (String)entry.get(SVNProperty.ABSENT);
        if (writeValue(buffer, absentAttr != null ? ATTRIBUTE_ABSENT : null, emptyFields)) {
            emptyFields = 0;
        } else {
            ++emptyFields;
        }

        String incompleteAttr = (String)entry.get(SVNProperty.INCOMPLETE);
        if (writeValue(buffer, incompleteAttr != null ? ATTRIBUTE_INCOMPLETE : null, emptyFields)) {
            emptyFields = 0;
        } else {
            ++emptyFields;
        }
        
        String uuid = (String)entry.get(SVNProperty.UUID);
        if (!isThisDir) {             
            String thisDirUUID = (String)rootEntry.get(SVNProperty.UUID);
            if (thisDirUUID != null && uuid != null && thisDirUUID.equals(uuid)) {
                uuid = null;
            }
        }
        if (writeValue(buffer, uuid, emptyFields)) {
            emptyFields = 0;
        } else {
            ++emptyFields;
        }
        
        String lockToken = (String)entry.get(SVNProperty.LOCK_TOKEN);
        if (writeString(buffer, lockToken, emptyFields)) {
            emptyFields = 0;
        } else {
            ++emptyFields;
        }
        
        String lockOwner = (String)entry.get(SVNProperty.LOCK_OWNER);
        if (writeString(buffer, lockOwner, emptyFields)) {
            emptyFields = 0;
        } else {
            ++emptyFields;
        }

        String lockComment = (String)entry.get(SVNProperty.LOCK_COMMENT);
        if (writeString(buffer, lockComment, emptyFields)) {
            emptyFields = 0;
        } else {
            ++emptyFields;
        }
        
        String lockCreationDate = (String)entry.get(SVNProperty.LOCK_CREATION_DATE);
        writeValue(buffer, lockCreationDate, emptyFields);

        buffer.append("\f\n");
    }
    
    private String asString(String[] array, String delimiter) {
        String str = null;
        if (array != null) {
            str = "";
            for (int i = 0; i < array.length; i++) {
                str += array[i];
                if (i < array.length - 1) {
                    str += delimiter;
                }
            }
        }
        return str;
    }
    
    private String[] fromString(String str, String delimiter) {
        LinkedList list = new LinkedList(); 
        int startInd = 0;
        int ind = -1;
        while ((ind = str.indexOf(delimiter, startInd)) != -1) {
            list.add(str.substring(startInd, ind));
            startInd = ind;
            while (startInd < str.length() && str.charAt(startInd) == ' '){
                startInd++;
            }
        }
        if (startInd < str.length()) {
            list.add(str.substring(startInd));
        }
        return (String[])list.toArray(new String[list.size()]);
    }

    private boolean writeString(StringBuffer buffer, String str, int emptyFields) {
        if (str != null && str.length() > 0) {
            for (int i = 0; i < emptyFields; i++) {
                buffer.append('\n');
            }
            for (int i = 0; i < str.length(); i++) {
                char ch = str.charAt(i);
                if (SVNEncodingUtil.isASCIIControlChar(ch) || ch == '\\') {
                    buffer.append("\\x");
                    buffer.append(SVNFormatUtil.getHexNumberFromByte((byte)ch));
                } else {
                    buffer.append(ch);
                }
            }
            buffer.append('\n');
            return true;
        }
        return false;
    }
    
    private boolean writeValue(StringBuffer buffer, String val, int emptyFields) {
        if (val != null && val.length() > 0) {
            for (int i = 0; i < emptyFields; i++) {
                buffer.append('\n');
            }
            buffer.append(val);
            buffer.append('\n');
            return true;
        }
        return false;
    }
    
    private boolean writeRevision(StringBuffer buffer, String rev, int emptyFields) {
        if (rev != null && rev.length() > 0 && Long.parseLong(rev) >= 0) {
            for (int i = 0; i < emptyFields; i++) {
                buffer.append('\n');
            }
            buffer.append(rev);
            buffer.append('\n');
            return true;
        }
        return false;
    }
    
    public void setCachableProperties(String name, String[] cachableProps) throws SVNException {
        Map entry = getEntries().getEntryMap(name);
        if (entry != null) {
            entry.put(SVNProperty.CACHABLE_PROPS, cachableProps);
        }
    }

    public String[] getCachableProperties(String entryName) throws SVNException {
        Map entry = getEntries().getEntryMap(entryName);
        if (entry != null) {
            return (String[])entry.get(SVNProperty.CACHABLE_PROPS);
        }
        return null;
    }

    public void setPresentProperties(String name, String[] presentProps) throws SVNException {
        Map entry = getEntries().getEntryMap(name);
        if (entry != null) {
            entry.put(SVNProperty.PRESENT_PROPS, presentProps);
        }
    }

    public String[] getPresentProperties(String entryName) throws SVNException {
        Map entry = getEntries().getEntryMap(entryName);
        if (entry != null) {
            return (String[])entry.get(SVNProperty.PRESENT_PROPS);
        }
        return null;
    }

    public boolean hasPropModifications(String name) throws SVNException {
        Map entry = getEntries().getEntryMap(name);
        if (entry != null) {
            return SVNProperty.booleanValue((String)entry.get(SVNProperty.HAS_PROP_MODS));
        }
        return false;
    }
    
    public boolean hasProperties(String name) throws SVNException {
        Map entry = getEntries().getEntryMap(name);
        if (entry != null) {
            return SVNProperty.booleanValue((String)entry.get(SVNProperty.HAS_PROPS));
        }
        return false;
    }

    protected int getFormatNumber() {
        return WC_FORMAT;
    }
}
