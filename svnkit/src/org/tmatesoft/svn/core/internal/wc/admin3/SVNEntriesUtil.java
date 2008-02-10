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
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.internal.util.SVNDate;
import org.tmatesoft.svn.core.internal.util.SVNEncodingUtil;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;


/**
 * Utility class to read and write SVNEntry structures.
 * 
 * @version 1.2
 * @author  TMate Software Ltd.
 */
public class SVNEntriesUtil {
    
    public static Map readEntries(InputStream is, String path, String thisDirName) throws SVNException {
        BufferedReader reader = null;
        Map entries = new HashMap();
        try {
            reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
            //skip format line
            String version = reader.readLine();
            if (version == null) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, "Invalid version line in entries file of ''{0}''", new File(path));
                SVNErrorManager.error(err);
            }
            while(true){
                SVNEntry entry = readEntry(reader, thisDirName); 
                if (entry == null) {
                    break;
                } 
                entries.put(entry.getName(), entry);
            }
        } catch (IOException e) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, "Cannot read entries file ''{0}'': {1}", new Object[] {new File(path), e.getMessage()});
            SVNErrorManager.error(err, e);
        } 
        resolveToDefaults(entries, thisDirName);
        return entries;
    }
    
    private static SVNEntry readEntry(BufferedReader reader, String thisDirName) throws SVNException, IOException {        
        String line = reader.readLine();
        if (line == null) {
            return null;
        } 
        SVNEntry entry = new SVNEntry();
        String name = readString(line);
        entry.myName = name == null ? thisDirName : name;
        line = readValue(reader.readLine());
        if (line != null) {
            if (SVNNodeKind.DIR.toString().equals(line)) {
                entry.myKind = SVNNodeKind.DIR;
            } else if (SVNNodeKind.FILE.toString().equals(line)) {
                entry.myKind = SVNNodeKind.FILE;
            } else {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.NODE_UNKNOWN_KIND, "Entry ''{0}'' has invalid node kind", entry.getName());
                SVNErrorManager.error(err);
            }
        } else {
            entry.myKind = SVNNodeKind.NONE;
        }
        //
        line = reader.readLine();
        if (isEntryFinished(line)) {
            return entry;
        }
        entry.myRevision = readRevision(line);
        //
        line = reader.readLine();
        if (isEntryFinished(line)) {
            return entry;
        }
        entry.myURL = readString(line);
        //
        line = reader.readLine();
        if (isEntryFinished(line)) {
            return entry;
        }
        entry.myRepositoryURL = readString(line);
        if (entry.myRepositoryURL != null && entry.myURL != null && !SVNPathUtil.isAncestor(entry.myRepositoryURL, entry.myURL)) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_CORRUPT, "Entry for ''{0}'' has invalid repository root", entry.getName());
            SVNErrorManager.error(err);
        }
        //
        line = reader.readLine();
        if (isEntryFinished(line)) {
            return entry;
        }
        String schedule = readValue(line);
        if (schedule != null) {
            if (!SVNEntry.SCHEDULE_ADD.equals(schedule) && !SVNEntry.SCHEDULE_DELETE.equals(schedule) && !SVNEntry.SCHEDULE_REPLACE.equals(schedule)) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_CORRUPT, "Entry ''{0}'' has invalid ''{1}'' value", new Object[] {entry.getName(), SVNEntry.SCHEDULE});
                SVNErrorManager.error(err);
            }
            entry.mySchedule = schedule;
        }
        //
        line = reader.readLine();
        if (isEntryFinished(line)) {
            return entry;
        }
        entry.myTextTime = readDate(line);
        //
        line = reader.readLine();
        if (isEntryFinished(line)) {
            return entry;
        }
        entry.myChecksum = readString(line);
        //
        line = reader.readLine();
        if (isEntryFinished(line)) {
            return entry;
        }
        entry.myCommittedDate = readDate(line);
        //
        line = reader.readLine();
        if (isEntryFinished(line)) {
            return entry;
        }
        entry.myCommitedRevision = readRevision(line);
        //
        line = reader.readLine();
        if (isEntryFinished(line)) {
            return entry;
        }
        entry.myCommitAuthor = readString(line);
        //
        line = reader.readLine();
        if (isEntryFinished(line)) {
            return entry;
        }
        entry.myHasProperties = readBoolean(line, SVNEntry.HAS_PROPS);
        //
        line = reader.readLine();
        if (isEntryFinished(line)) {
            return entry;
        }
        entry.myIsPropertiesModified = readBoolean(line, SVNEntry.HAS_PROP_MODS);
        //
        line = reader.readLine();
        if (isEntryFinished(line)) {
            return entry;
        }
        entry.myCachableProperties = readValue(line);
        //
        line = reader.readLine();
        if (isEntryFinished(line)) {
            return entry;
        }
        entry.myPresentProperties = readValue(line);
        //
        line = reader.readLine();
        if (isEntryFinished(line)) {
            return entry;
        }
        entry.myPropReject = readString(line);
        //
        line = reader.readLine();
        if (isEntryFinished(line)) {
            return entry;
        }
        entry.myConflictOld = readString(line);
        //
        line = reader.readLine();
        if (isEntryFinished(line)) {
            return entry;
        }
        entry.myConflictNew = readString(line);
        //
        line = reader.readLine();
        if (isEntryFinished(line)) {
            return entry;
        }
        entry.myConflictWorking= readString(line);
        //
        line = reader.readLine();
        if (isEntryFinished(line)) {
            return entry;
        }
        entry.myIsCopied = readBoolean(line, SVNEntry.COPIED);
        //
        line = reader.readLine();
        if (isEntryFinished(line)) {
            return entry;
        }
        entry.myCopyFromURL = readString(line);
        //
        line = reader.readLine();
        if (isEntryFinished(line)) {
            return entry;
        }
        entry.myCopyFromRevision = readRevision(line);
        //
        line = reader.readLine();
        if (isEntryFinished(line)) {
            return entry;
        }
        entry.myIsDeleted = readBoolean(line, SVNEntry.DELETED);
        //
        line = reader.readLine();
        if (isEntryFinished(line)) {
            return entry;
        }
        entry.myIsAbsent = readBoolean(line, SVNEntry.ABSENT);
        //
        line = reader.readLine();
        if (isEntryFinished(line)) {
            return entry;
        }
        entry.myIsIncomplete = readBoolean(line, SVNEntry.INCOMPLETE);
        //
        line = reader.readLine();
        if (isEntryFinished(line)) {
            return entry;
        }
        entry.myRepositoryUUID = readString(line);
        //
        line = reader.readLine();
        if (isEntryFinished(line)) {
            return entry;
        }
        entry.myLockToken = readString(line);
        //
        line = reader.readLine();
        if (isEntryFinished(line)) {
            return entry;
        }
        entry.myLockOwner = readString(line);
        //
        line = reader.readLine();
        if (isEntryFinished(line)) {
            return entry;
        }
        entry.myLockComment = readString(line);
        //
        line = reader.readLine();
        if (isEntryFinished(line)) {
            return entry;
        }
        entry.myLockCreationDate = readDate(line);
        //
        line = reader.readLine();
        if (isEntryFinished(line)) {
            return entry;
        }
        entry.myChangelist = readString(line);
        //
        line = reader.readLine();
        if (isEntryFinished(line)) {
            return entry;
        }
        entry.myIsKeepLocal = readBoolean(line, SVNEntry.KEEP_LOCAL);
        //
        line = reader.readLine();
        if (isEntryFinished(line)) {
            return entry;
        }
        String size = readValue(line);
        if (size != null) {
            try {
                entry.myWorkingSize = Long.parseLong(size);
            } catch (NumberFormatException nfe) {
                entry.myWorkingSize = -1;
            }
        }
        //
        line = reader.readLine();
        if (isEntryFinished(line)) {
            return entry;
        }
        String depth = readValue(line);
        if (depth != null) {
            entry.myDepth = SVNDepth.fromString(depth);
        } else {
            entry.myDepth = SVNDepth.INFINITY;
        }
        //
        line = reader.readLine();
        if (isEntryFinished(line)) {
            return entry;
        }
        return entry;
    }
    
    private static boolean isEntryFinished(String line) {
        return line != null && line.length() > 0 && line.charAt(0) == '\f';
    }
    
    private static void resolveToDefaults(Map entriesMap, String thisDirName) throws SVNException {
        SVNEntry defaultEntry = (SVNEntry) entriesMap.get(thisDirName);
        if (defaultEntry == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_NOT_FOUND, "Missing default entry");
            SVNErrorManager.error(err);
        }
        
        if (defaultEntry.getRevision() < 0) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_MISSING_REVISION, "Default entry has no revision number");
            SVNErrorManager.error(err);
        }

        if (defaultEntry.getURL() == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_MISSING_URL, "Default entry is missing URL");
            SVNErrorManager.error(err);
        }

        for (Iterator entries = entriesMap.values().iterator(); entries.hasNext();) {
            SVNEntry entry = (SVNEntry) entries.next();
            if (entry == defaultEntry) {
                continue;
            } else if (entry.getKind() == SVNNodeKind.DIR) {
                continue;
            } else if (entry.getKind() == SVNNodeKind.FILE) {
                entry.takeFrom(defaultEntry);
            }
        }
    }

    private static String readString(String line) throws SVNException {
        if (line == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_CORRUPT, "Unexpected end of entry");
            SVNErrorManager.error(err);
        } else if ("".equals(line)) {
            return null;
        }
        
        StringBuffer buffer = null;
        for(int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '\\') {
                if (line.length() < i + 4) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_CORRUPT, "Invalid escape sequence");
                    SVNErrorManager.error(err);
                }
                char x = line.charAt(i + 1);
                char first = line.charAt(i + 2);
                char second = line.charAt(i + 3);
                if (x != 'x' || !SVNEncodingUtil.isHexDigit(first) || !SVNEncodingUtil.isHexDigit(second)) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_CORRUPT, "Invalid escape sequence");
                    SVNErrorManager.error(err);
                }
                char value = (char) (SVNEncodingUtil.hexValue(first) * 16 + SVNEncodingUtil.hexValue(second));
                if (buffer == null) {
                    buffer = new StringBuffer();
                }
                buffer.append(value);
                i += 3;
            } else if (buffer != null) {
                buffer.append(ch);
            }
        }
        if (buffer != null) {
            return buffer.toString();
        }
        return line;
    }

    private static String readValue(String line) throws SVNException {
        if (line == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_CORRUPT, "Unexpected end of entry");
            SVNErrorManager.error(err);
        } else if ("".equals(line)) {
            return null;
        }
        return line;
    }

    private static boolean readBoolean(String line, String fieldName) throws SVNException {
        String value = readValue(line);
        if (value == null) {
            return false;
        }
        if (!value.equals(fieldName)) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_CORRUPT, "Invalid value for field ''{0}''", fieldName);
            SVNErrorManager.error(err);
        }
        return true;
    }
    
    private static long readRevision(String line) throws SVNException {
        String value = readValue(line);
        if (value == null) {
            return -1;
        }
        long revision = -1;
        try {
            revision = Long.parseLong(value);
        } catch (NumberFormatException nfe) {
            revision = -1;
        }
        return revision;
    }
    
    private static SVNDate readDate(String line) throws SVNException {
        String value = readValue(line);
        return SVNDate.parseDate(value);
    }
}
