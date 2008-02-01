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

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;

import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.internal.util.SVNEncodingUtil;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc.admin.SVNEntry;


/**
 * @version 1.1.2
 * @author  TMate Software Ltd.
 */
public class SVNEntries {
    
    protected static final String ATTRIBUTE_COPIED = "copied";
    protected static final String ATTRIBUTE_DELETED = "deleted";
    protected static final String ATTRIBUTE_ABSENT = "absent";
    protected static final String ATTRIBUTE_INCOMPLETE = "incomplete";
    protected static final String ATTRIBUTE_HAS_PROPS = "has-props";
    protected static final String ATTRIBUTE_HAS_PROP_MODS = "has-prop-mods";
    protected static final String KILL_ADM_ONLY = "adm-only";
    protected static final String ATTRIBUTE_KEEP_LOCAL = "keep-local";
    protected static final String THIS_DIR = "";


    public static String getThisDirName() {
        return THIS_DIR;
    }

    public static void readEntries(File file, Map entries, Map hiddenEntries) throws SVNException {
        
        InputStream is = null;
        BufferedReader reader = null;
        try {
            is = SVNFileUtil.openFileForReading(file);
            reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
            //skip format line
            reader.readLine();
            int entryNumber = 1;
            while(true){
                try {
                    SVNEntry entry = readEntry(reader, entryNumber); 
                    if (entry == null) {
                        break;
                    } 
                    if (entries != null && !entry.isHidden()) {
                        entries.put(entry.getName(), entry);
                    }
                    if (hiddenEntries != null) {
                        hiddenEntries.put(entry.getName(), entry);
                    }
                } catch (SVNException svne) {
                    SVNErrorMessage err = svne.getErrorMessage().wrap("Error at entry {0,number,integer} in entries file for ''{1}'':", new Object[]{new Integer(entryNumber), file.getParentFile()});
                    SVNErrorManager.error(err, svne);
                }
                ++entryNumber;
            }
        } catch (IOException e) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, "Cannot read entries file ''{0}'': {1}", new Object[] {file, e.getMessage()});
            SVNErrorManager.error(err, e);
        } finally {
            SVNFileUtil.closeFile(is);
            SVNFileUtil.closeFile(reader);
        }

        SVNEntry defaultEntry = (SVNEntry) entries.get(getThisDirName());
        if (defaultEntry == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_NOT_FOUND, "Missing default entry");
            SVNErrorManager.error(err);
        }
        
        Map defaultEntryAttrs = defaultEntry.asMap();
        if (defaultEntryAttrs.get(SVNProperty.REVISION) == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_MISSING_REVISION, "Default entry has no revision number");
            SVNErrorManager.error(err);
        }

        if (defaultEntryAttrs.get(SVNProperty.URL) == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_MISSING_URL, "Default entry is missing URL");
            SVNErrorManager.error(err);
        }

        for (Iterator entriesIter = entries.keySet().iterator(); entriesIter.hasNext();) {
            String name = (String)entriesIter.next();
            SVNEntry entry = (SVNEntry)entries.get(name);
            if (getThisDirName().equals(name)) {
                continue;
            }
            
            Map entryAttributes = entry.asMap();
            SVNNodeKind kind = SVNNodeKind.parseKind((String)entryAttributes.get(SVNProperty.KIND));
            if (kind == SVNNodeKind.FILE) {
                if (entryAttributes.get(SVNProperty.REVISION) == null || Long.parseLong((String) entryAttributes.get(SVNProperty.REVISION), 10) < 0) {
                    entryAttributes.put(SVNProperty.REVISION, defaultEntryAttrs.get(SVNProperty.REVISION));
                }
                if (entryAttributes.get(SVNProperty.URL) == null) {
                    String rootURL = (String)defaultEntryAttrs.get(SVNProperty.URL);
                    String url = SVNPathUtil.append(rootURL, SVNEncodingUtil.uriEncode(name));
                    entryAttributes.put(SVNProperty.URL, url);
                }
                if (entryAttributes.get(SVNProperty.REPOS) == null) {
                    entryAttributes.put(SVNProperty.REPOS, defaultEntryAttrs.get(SVNProperty.REPOS));
                }
                if (entryAttributes.get(SVNProperty.UUID) == null) {
                    String schedule = (String)entryAttributes.get(SVNProperty.SCHEDULE);
                    if (!(SVNProperty.SCHEDULE_ADD.equals(schedule) || SVNProperty.SCHEDULE_REPLACE.equals(schedule))) {
                        entryAttributes.put(SVNProperty.UUID, defaultEntryAttrs.get(SVNProperty.UUID));
                    }
                }
                if (entryAttributes.get(SVNProperty.CACHABLE_PROPS) == null) {
                    entryAttributes.put(SVNProperty.CACHABLE_PROPS, defaultEntryAttrs.get(SVNProperty.CACHABLE_PROPS));
                }
            }
        }
    }

    protected static SVNEntry readEntry(BufferedReader reader, int entryNumber) throws IOException, SVNException {
        String line = reader.readLine();
        if (line == null && entryNumber > 1) {
            return null;
        }

        String name = parseString(line);
        name = name != null ? name : getThisDirName();

        Map entryAttrs = new HashMap();
        entryAttrs.put(SVNProperty.NAME, name);
        SVNEntry entry = new SVNEntry(entryAttrs, null, name);
        entry.setDepth(SVNDepth.INFINITY);
        
        line = reader.readLine();
        String kind = parseValue(line);
        if (kind != null) {
            SVNNodeKind parsedKind = SVNNodeKind.parseKind(kind); 
            if (parsedKind != SVNNodeKind.UNKNOWN && parsedKind != SVNNodeKind.NONE) {
                entryAttrs.put(SVNProperty.KIND, kind);
            } else {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.NODE_UNKNOWN_KIND, "Entry ''{0}'' has invalid node kind", name);
                SVNErrorManager.error(err);
            }
        } else {
            entryAttrs.put(SVNProperty.KIND, SVNNodeKind.NONE.toString());
        }
        
        line = reader.readLine();
        if (isEntryFinished(line)) {
            return entry;
        }
        String revision = parseValue(line);
        if (revision != null) {
            entryAttrs.put(SVNProperty.REVISION, revision);
        }
        
        line = reader.readLine();
        if (isEntryFinished(line)) {
            return entry;
        }
        String url = parseString(line);
        if (url != null) {
            entryAttrs.put(SVNProperty.URL, url);
        }
        
        line = reader.readLine();
        if (isEntryFinished(line)) {
            return entry;
        }
        String reposRoot = parseString(line);
        if (reposRoot != null && url != null && !SVNPathUtil.isAncestor(reposRoot, url)) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_CORRUPT, "Entry for ''{0}'' has invalid repository root", name);
            SVNErrorManager.error(err);
        } else if (reposRoot != null) {
            entryAttrs.put(SVNProperty.REPOS, reposRoot);
        }
        
        line = reader.readLine();
        if (isEntryFinished(line)) {
            return entry;
        }
        String schedule = parseValue(line);
        if (schedule != null) {
            if (SVNProperty.SCHEDULE_ADD.equals(schedule) || SVNProperty.SCHEDULE_DELETE.equals(schedule) || SVNProperty.SCHEDULE_REPLACE.equals(schedule)) {
                entryAttrs.put(SVNProperty.SCHEDULE, schedule);
            } else {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_ATTRIBUTE_INVALID, "Entry ''{0}'' has invalid ''{1}'' value", new Object[]{name, SVNProperty.SCHEDULE});
                SVNErrorManager.error(err);
            }
        }
        
        line = reader.readLine();
        if (isEntryFinished(line)) {
            return entry;
        }
        String timestamp = parseValue(line);
        if (timestamp != null) {
            entryAttrs.put(SVNProperty.TEXT_TIME, timestamp);
        }
        
        line = reader.readLine();
        if (isEntryFinished(line)) {
            return entry;
        }
        String checksum = parseString(line);
        if (checksum != null) {
            entryAttrs.put(SVNProperty.CHECKSUM, checksum);
        }

        line = reader.readLine();
        if (isEntryFinished(line)) {
            return entry;
        }
        String committedDate = parseValue(line);
        if (committedDate != null) {
            entryAttrs.put(SVNProperty.COMMITTED_DATE, committedDate);
        }

        line = reader.readLine();
        if (isEntryFinished(line)) {
            return entry;
        }
        String committedRevision = parseValue(line);
        if (committedRevision != null) {
            entryAttrs.put(SVNProperty.COMMITTED_REVISION, committedRevision);
        }
        
        line = reader.readLine();
        if (isEntryFinished(line)) {
            return entry;
        }
        String committedAuthor = parseString(line);
        if (committedAuthor != null) {
            entryAttrs.put(SVNProperty.LAST_AUTHOR, committedAuthor);
        }
        
        line = reader.readLine();
        if (isEntryFinished(line)) {
            return entry;
        }
        boolean hasProps = parseBoolean(line, ATTRIBUTE_HAS_PROPS);
        if (hasProps) {
            entryAttrs.put(SVNProperty.HAS_PROPS, SVNProperty.toString(hasProps));
        }

        line = reader.readLine();
        if (isEntryFinished(line)) {
            return entry;
        }
        boolean hasPropMods = parseBoolean(line, ATTRIBUTE_HAS_PROP_MODS);
        if (hasPropMods) {
            entryAttrs.put(SVNProperty.HAS_PROP_MODS, SVNProperty.toString(hasPropMods));
        }

        line = reader.readLine();
        if (isEntryFinished(line)) {
            return entry;
        }
        String cachablePropsStr = parseValue(line);
        if (cachablePropsStr != null) {
            String[] cachableProps = fromString(cachablePropsStr, " ");
            entryAttrs.put(SVNProperty.CACHABLE_PROPS, cachableProps);
        }
        
        line = reader.readLine();
        if (isEntryFinished(line)) {
            return entry;
        }
        String presentPropsStr = parseValue(line);
        if (presentPropsStr != null) {
            String[] presentProps = fromString(presentPropsStr, " ");
            entryAttrs.put(SVNProperty.PRESENT_PROPS, presentProps);
        }
        
        line = reader.readLine();
        if (isEntryFinished(line)) {
            return entry;
        }
        String prejFile = parseString(line);
        if (prejFile != null) {
            entryAttrs.put(SVNProperty.PROP_REJECT_FILE, prejFile);
        }

        line = reader.readLine();
        if (isEntryFinished(line)) {
            return entry;
        }
        String conflictOldFile = parseString(line);
        if (conflictOldFile != null) {
            entryAttrs.put(SVNProperty.CONFLICT_OLD, conflictOldFile);
        }

        line = reader.readLine();
        if (isEntryFinished(line)) {
            return entry;
        }
        String conflictNewFile = parseString(line);
        if (conflictNewFile != null) {
            entryAttrs.put(SVNProperty.CONFLICT_NEW, conflictNewFile);
        }

        line = reader.readLine();
        if (isEntryFinished(line)) {
            return entry;
        }
        String conflictWorkFile = parseString(line);
        if (conflictWorkFile != null) {
            entryAttrs.put(SVNProperty.CONFLICT_WRK, conflictWorkFile);
        }

        line = reader.readLine();
        if (isEntryFinished(line)) {
            return entry;
        }
        boolean isCopied = parseBoolean(line, ATTRIBUTE_COPIED);
        if (isCopied) {
            entryAttrs.put(SVNProperty.COPIED, SVNProperty.toString(isCopied));
        }

        line = reader.readLine();
        if (isEntryFinished(line)) {
            return entry;
        }
        String copyfromURL = parseString(line);
        if (copyfromURL != null) {
            entryAttrs.put(SVNProperty.COPYFROM_URL, copyfromURL);
        }
        
        line = reader.readLine();
        if (isEntryFinished(line)) {
            return entry;
        }
        String copyfromRevision = parseValue(line);
        if (copyfromRevision != null) {
            entryAttrs.put(SVNProperty.COPYFROM_REVISION, copyfromRevision);
        }
        
        line = reader.readLine();
        if (isEntryFinished(line)) {
            return entry;
        }
        boolean isDeleted = parseBoolean(line, ATTRIBUTE_DELETED);
        if (isDeleted) {
            entryAttrs.put(SVNProperty.DELETED, SVNProperty.toString(isDeleted));
        }

        line = reader.readLine();
        if (isEntryFinished(line)) {
            return entry;
        }
        boolean isAbsent = parseBoolean(line, ATTRIBUTE_ABSENT);
        if (isAbsent) {
            entryAttrs.put(SVNProperty.ABSENT, SVNProperty.toString(isAbsent));
        }

        line = reader.readLine();
        if (isEntryFinished(line)) {
            return entry;
        }
        boolean isIncomplete = parseBoolean(line, ATTRIBUTE_INCOMPLETE);
        if (isIncomplete) {
            entryAttrs.put(SVNProperty.INCOMPLETE, SVNProperty.toString(isIncomplete));
        }

        line = reader.readLine();
        if (isEntryFinished(line)) {
            return entry;
        }
        String uuid = parseString(line);
        if (uuid != null) {
            entryAttrs.put(SVNProperty.UUID, uuid);
        }
        
        line = reader.readLine();
        if (isEntryFinished(line)) {
            return entry;
        }
        String lockToken = parseString(line);
        if (lockToken != null) {
            entryAttrs.put(SVNProperty.LOCK_TOKEN, lockToken);
        }
        
        line = reader.readLine();
        if (isEntryFinished(line)) {
            return entry;
        }
        String lockOwner = parseString(line);
        if (lockOwner != null) {
            entryAttrs.put(SVNProperty.LOCK_OWNER, lockOwner);
        }
        
        line = reader.readLine();
        if (isEntryFinished(line)) {
            return entry;
        }
        String lockComment = parseString(line);
        if (lockComment != null) {
            entryAttrs.put(SVNProperty.LOCK_COMMENT, lockComment);
        }
        
        line = reader.readLine();
        if (isEntryFinished(line)) {
            return entry;
        }
        String lockCreationDate = parseValue(line);
        if (lockCreationDate != null) {
            entryAttrs.put(SVNProperty.LOCK_CREATION_DATE, lockCreationDate);
        }

        if (readExtraOptions(reader, entryAttrs)) {
            return entry;
        }
        
        do {
            line = reader.readLine();
            if (line == null) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_CORRUPT, "Missing entry terminator");
                SVNErrorManager.error(err);
            } else if (line.length() == 1 && line.charAt(0) == '\f') {
                break;
            }
        } while (line != null);

        return entry;
    }

    protected static boolean readExtraOptions(BufferedReader reader, Map entryAttrs) throws SVNException, IOException {
        String line = reader.readLine();
        if (isEntryFinished(line)) {
            return true;
        }
        String changelist = parseString(line);
        if (changelist != null) {
            entryAttrs.put(SVNProperty.CHANGELIST, changelist);
        }

        line = reader.readLine();
        if (isEntryFinished(line)) {
            return true;
        }
        boolean keepLocal = parseBoolean(line, ATTRIBUTE_KEEP_LOCAL);
        if (keepLocal) {
            entryAttrs.put(SVNProperty.KEEP_LOCAL, SVNProperty.toString(keepLocal));
        }
        
        line = reader.readLine();
        if (isEntryFinished(line)) {
            return true;
        }
        String workingSize = parseString(line);
        if (workingSize != null) {
            entryAttrs.put(SVNProperty.WORKING_SIZE, workingSize);
        }
        
        line = reader.readLine();
        if (isEntryFinished(line)) {
            return true;
        }
        String depthStr = parseValue(line);
        if (depthStr == null) {
            entryAttrs.put(SVNProperty.DEPTH, SVNDepth.INFINITY.getName());
        } else {
            entryAttrs.put(SVNProperty.DEPTH, depthStr);
        }
        return false;
    }

    
    protected static boolean isEntryFinished(String line) {
        return line != null && line.length() > 0 && line.charAt(0) == '\f';
    }
    
    protected static boolean parseBoolean(String line, String field) throws SVNException {
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
    
    protected static String parseValue(String line) throws SVNException {
        if (line == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_CORRUPT, "Unexpected end of entry");
            SVNErrorManager.error(err);
        } else if ("".equals(line)) {
            return null;
        }
        return line;
    }
    
    protected static String parseString(String line) throws SVNException {
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

    protected static String[] fromString(String str, String delimiter) {
        if (str == null) {
            return new String[0];
        }
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

}
