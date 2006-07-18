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
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.internal.util.SVNEncodingUtil;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;


public class SVNPostXMLEntries extends SVNAdminArea {

    public SVNPostXMLEntries(SVNDirectory parent) {
        super(parent);
    }

    public SVNEntry addEntry(String name) {
        return null;
    }

    public void close() {
    }

    public void deleteEntry(String name) {
    }

    public Iterator entries(boolean hidden) {
        return null;
    }

    public SVNProperties getBaseProperties(String name, boolean tmp) {
        return null;
    }

    public SVNEntry getEntry(String name, boolean hidden) {
        return null;
    }

    protected Map getEntryMap(String name) {
        return null;
    }

    public SVNProperties getProperties(String name, boolean tmp) {
        return null;
    }

    public String getPropertyValue(String name, String propertyName) {
        return null;
    }

    public SVNProperties getWCProperties(String name) {
        return null;
    }

    protected void fetchEntries() throws IOException, SVNException {
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new InputStreamReader(SVNFileUtil.openFileForReading(getParent().getEntriesFile()), "UTF-8"));
            //skip format line
            reader.readLine();
            int entryNumber = 1;
            while(true){
                try {
                    if (readEntry(reader, entryNumber) == null) {
                        break;
                    }
                } catch (SVNException svne) {
                    SVNErrorMessage err = svne.getErrorMessage().wrap("Error at entry {0,number, integer} in entries file for ''{0}'':", new Object[]{new Integer(entryNumber), getParent().getRoot()});
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
        Map defaultEntry = (Map)myData.get("");
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

        for (Iterator entries = myData.keySet().iterator(); entries.hasNext();) {
            String name = (String)entries.next();
            if ("".equals(name)) {
                continue;
            }
            
            Map entry = (Map)myData.get(name);
            SVNNodeKind kind = SVNNodeKind.parseKind((String)entry.get(SVNProperty.KIND));
            if (kind == SVNNodeKind.FILE) {
                if (entry.get(SVNProperty.REVISION) == null) {
                    entry.put(SVNProperty.REVISION, defaultEntry.get(SVNProperty.REVISION));
                }
                if (entry.get(SVNProperty.URL) == null) {
                    String rootURL = (String)defaultEntry.get(SVNProperty.URL);
                    String url = SVNPathUtil.append(rootURL, SVNEncodingUtil.uriEncode(name));
                    entry.put(SVNProperty.URL, url);
                }
                if (entry.get(SVNProperty.REPOS) == null) {
                    entry.put(SVNProperty.REPOS, defaultEntry.get(SVNProperty.REPOS));
                }
                if (entry.get(SVNProperty.UUID) == null) {
                    String schedule = (String)entry.get(SVNProperty.SCHEDULE);
                    if (!(SVNProperty.SCHEDULE_ADD.equals(schedule) || SVNProperty.SCHEDULE_REPLACE.equals(schedule))) {
                        entry.put(SVNProperty.UUID, defaultEntry.get(SVNProperty.UUID));
                    }
                }
                if (entry.get(SVNProperty.CACHABLE_PROPS) == null) {
                    entry.put(SVNProperty.CACHABLE_PROPS, defaultEntry.get(SVNProperty.CACHABLE_PROPS));
                }
            }
        }
    }
    
    private Map readEntry(BufferedReader reader, int entryNumber) throws IOException, SVNException {
        String line = reader.readLine();
        if (line == null && entryNumber > 1) {
            return null;
        }

        Map entry = new HashMap();
        String name = parseString(line);
        name = name != null ? name : "";
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
            myData.put(name, entry);
            return entry;
        }
        String revision = parseValue(line);
        if (revision != null) {
            entry.put(SVNProperty.REVISION, revision);
        }
        
        line = reader.readLine();
        if (isEntryFinished(line)) {
            myData.put(name, entry);
            return entry;
        }
        String url = parseString(line);
        if (url == null) {
            entry.put(SVNProperty.URL, url);
        }
        
        line = reader.readLine();
        if (isEntryFinished(line)) {
            myData.put(name, entry);
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
            myData.put(name, entry);
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
            myData.put(name, entry);
            return entry;
        }
        String timestamp = parseValue(line);
        if (timestamp != null) {
            entry.put(SVNProperty.TEXT_TIME, timestamp);
        }
        
        line = reader.readLine();
        if (isEntryFinished(line)) {
            myData.put(name, entry);
            return entry;
        }
        String checksum = parseString(line);
        if (checksum != null) {
            entry.put(SVNProperty.CHECKSUM, checksum);
        }

        line = reader.readLine();
        if (isEntryFinished(line)) {
            myData.put(name, entry);
            return entry;
        }
        String committedDate = parseValue(line);
        if (committedDate != null) {
            entry.put(SVNProperty.COMMITTED_DATE, committedDate);
        }

        line = reader.readLine();
        if (isEntryFinished(line)) {
            myData.put(name, entry);
            return entry;
        }
        String committedRevision = parseValue(line);
        if (committedRevision != null) {
            entry.put(SVNProperty.COMMITTED_REVISION, committedRevision);
        }
        
        line = reader.readLine();
        if (isEntryFinished(line)) {
            myData.put(name, entry);
            return entry;
        }
        String committedAuthor = parseString(line);
        if (committedAuthor != null) {
            entry.put(SVNProperty.LAST_AUTHOR, checksum);
        }
        
        line = reader.readLine();
        if (isEntryFinished(line)) {
            myData.put(name, entry);
            return entry;
        }
        boolean hasProps = parseBoolean(line, SVNProperty.HAS_PROPS);
        if (hasProps) {
            entry.put(SVNProperty.HAS_PROPS, SVNProperty.toString(hasProps));
        }

        line = reader.readLine();
        if (isEntryFinished(line)) {
            myData.put(name, entry);
            return entry;
        }
        boolean hasPropMods = parseBoolean(line, SVNProperty.HAS_PROP_MODS);
        if (hasPropMods) {
            entry.put(SVNProperty.HAS_PROP_MODS, SVNProperty.toString(hasPropMods));
        }

        line = reader.readLine();
        if (isEntryFinished(line)) {
            myData.put(name, entry);
            return entry;
        }
        String cachableProps = parseValue(line);
        if (cachableProps != null) {
            entry.put(SVNProperty.CACHABLE_PROPS, cachableProps);
        }
        
        line = reader.readLine();
        if (isEntryFinished(line)) {
            myData.put(name, entry);
            return entry;
        }
        String presentProps = parseValue(line);
        if (presentProps != null) {
            entry.put(SVNProperty.PRESENT_PROPS, presentProps);
        }
        
        line = reader.readLine();
        if (isEntryFinished(line)) {
            myData.put(name, entry);
            return entry;
        }
        String prejFile = parseString(line);
        if (prejFile != null) {
            entry.put(SVNProperty.PROP_REJECT_FILE, prejFile);
        }

        line = reader.readLine();
        if (isEntryFinished(line)) {
            myData.put(name, entry);
            return entry;
        }
        String conflictOldFile = parseString(line);
        if (conflictOldFile != null) {
            entry.put(SVNProperty.CONFLICT_OLD, conflictOldFile);
        }

        line = reader.readLine();
        if (isEntryFinished(line)) {
            myData.put(name, entry);
            return entry;
        }
        String conflictNewFile = parseString(line);
        if (conflictNewFile != null) {
            entry.put(SVNProperty.CONFLICT_NEW, conflictNewFile);
        }

        line = reader.readLine();
        if (isEntryFinished(line)) {
            myData.put(name, entry);
            return entry;
        }
        String conflictWorkFile = parseString(line);
        if (conflictWorkFile != null) {
            entry.put(SVNProperty.CONFLICT_WRK, conflictWorkFile);
        }

        line = reader.readLine();
        if (isEntryFinished(line)) {
            myData.put(name, entry);
            return entry;
        }
        boolean isCopied = parseBoolean(line, "copied");
        if (isCopied) {
            entry.put(SVNProperty.COPIED, SVNProperty.toString(isCopied));
        }

        line = reader.readLine();
        if (isEntryFinished(line)) {
            myData.put(name, entry);
            return entry;
        }
        String copyfromURL = parseString(line);
        if (copyfromURL != null) {
            entry.put(SVNProperty.COPYFROM_URL, copyfromURL);
        }
        
        line = reader.readLine();
        if (isEntryFinished(line)) {
            myData.put(name, entry);
            return entry;
        }
        String copyfromRevision = parseValue(line);
        if (copyfromRevision != null) {
            entry.put(SVNProperty.COPYFROM_REVISION, copyfromRevision);
        }
        
        line = reader.readLine();
        if (isEntryFinished(line)) {
            myData.put(name, entry);
            return entry;
        }
        boolean isDeleted = parseBoolean(line, "deleted");
        if (isDeleted) {
            entry.put(SVNProperty.DELETED, SVNProperty.toString(isDeleted));
        }

        line = reader.readLine();
        if (isEntryFinished(line)) {
            myData.put(name, entry);
            return entry;
        }
        boolean isAbsent = parseBoolean(line, "absent");
        if (isAbsent) {
            entry.put(SVNProperty.ABSENT, SVNProperty.toString(isAbsent));
        }

        line = reader.readLine();
        if (isEntryFinished(line)) {
            myData.put(name, entry);
            return entry;
        }
        boolean isIncomplete = parseBoolean(line, "incomplete");
        if (isIncomplete) {
            entry.put(SVNProperty.INCOMPLETE, SVNProperty.toString(isIncomplete));
        }

        line = reader.readLine();
        if (isEntryFinished(line)) {
            myData.put(name, entry);
            return entry;
        }
        String uuid = parseString(line);
        if (uuid != null) {
            entry.put(SVNProperty.UUID, uuid);
        }
        
        line = reader.readLine();
        if (isEntryFinished(line)) {
            myData.put(name, entry);
            return entry;
        }
        String lockToken = parseString(line);
        if (lockToken != null) {
            entry.put(SVNProperty.LOCK_TOKEN, lockToken);
        }
        
        line = reader.readLine();
        if (isEntryFinished(line)) {
            myData.put(name, entry);
            return entry;
        }
        String lockOwner = parseString(line);
        if (lockOwner != null) {
            entry.put(SVNProperty.LOCK_OWNER, lockOwner);
        }
        
        line = reader.readLine();
        if (isEntryFinished(line)) {
            myData.put(name, entry);
            return entry;
        }
        String lockComment = parseString(line);
        if (lockComment != null) {
            entry.put(SVNProperty.LOCK_COMMENT, lockComment);
        }
        
        line = reader.readLine();
        if (isEntryFinished(line)) {
            myData.put(name, entry);
            return entry;
        }
        String lockCreationDate = parseValue(line);
        if (lockCreationDate != null) {
            entry.put(SVNProperty.LOCK_CREATION_DATE, lockCreationDate);
        }

        myData.put(name, entry);
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
    
    public void save(boolean close) throws SVNException {
    }

    public boolean setPropertyValue(String name, String propertyName, String propertyValue) {
        return false;
    }

}
