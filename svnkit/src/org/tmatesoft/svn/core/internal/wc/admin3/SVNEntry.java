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

import java.util.Map;
import java.util.StringTokenizer;

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
 * @version 1.1.2
 * @author  TMate Software Ltd.
 */
public class SVNEntry {
    
    public static final String NAME              = "name";
    public static final String REVISION          = "revision";
    public static final String URL               = "url";
    public static final String REPOS             = "repos";
    public static final String KIND              = "kind";
    public static final String TEXT_TIME         = "text-time";
    public static final String PROP_TIME         = "prop-time";
    public static final String CHECKSUM          = "checksum";
    public static final String SCHEDULE          = "schedule";
    public static final String COPIED            = "copied";
    public static final String DELETED           = "deleted";
    public static final String ABSENT            = "absent";
    public static final String COPYFROM_URL      = "copyfrom-url";
    public static final String COPYFROM_REV      = "copyfrom-rev";
    public static final String CONFLICT_OLD      = "conflict-old"; /* saved old file */
    public static final String CONFLICT_NEW      = "conflict-new"; /* saved new file */
    public static final String CONFLICT_WRK      = "conflict-wrk"; /* saved wrk file */
    public static final String PREJFILE          = "prop-reject-file";
    public static final String CMT_REV           = "committed-rev";
    public static final String CMT_DATE          = "committed-date";
    public static final String CMT_AUTHOR        = "last-author";
    public static final String UUID              = "uuid";
    public static final String INCOMPLETE        = "incomplete";
    public static final String LOCK_TOKEN        = "lock-token";
    public static final String LOCK_OWNER        = "lock-owner";
    public static final String LOCK_COMMENT      = "lock-comment";
    public static final String LOCK_CREATION_DATE= "lock-creation-date";
    public static final String HAS_PROPS         = "has-props";
    public static final String HAS_PROP_MODS     = "has-prop-mods";
    public static final String CACHABLE_PROPS    = "cachable-props";
    public static final String PRESENT_PROPS     = "present-props";
    public static final String CHANGELIST        = "changelist";
    public static final String KEEP_LOCAL        = "keep-local";
    public static final String WORKING_SIZE      = "working-size";
    
    public static final String SCHEDULE_ADD = "add";
    public static final String SCHEDULE_DELETE = "delete";
    public static final String SCHEDULE_REPLACE = "replace";
    
    public static final long FLAG_REVISION           = 0x0000000000000001l;
    public static final long FLAG_URL                = 0x0000000000000002l;
    public static final long FLAG_REPOS              = 0x0000000000000004l;
    public static final long FLAG_KIND               = 0x0000000000000008l;
    public static final long FLAG_TEXT_TIME          = 0x0000000000000010l;
    public static final long FLAG_PROP_TIME          = 0x0000000000000020l;
    public static final long FLAG_CHECKSUM           = 0x0000000000000040l;
    public static final long FLAG_SCHEDULE           = 0x0000000000000080l;
    public static final long FLAG_COPIED             = 0x0000000000000100l;
    public static final long FLAG_DELETED            = 0x0000000000000200l;
    public static final long FLAG_COPYFROM_URL       = 0x0000000000000400l;
    public static final long FLAG_COPYFROM_REV       = 0x0000000000000800l;
    public static final long FLAG_CONFLICT_OLD       = 0x0000000000001000l;
    public static final long FLAG_CONFLICT_NEW       = 0x0000000000002000l;
    public static final long FLAG_CONFLICT_WRK       = 0x0000000000004000l;
    public static final long FLAG_PREJFILE           = 0x0000000000008000l;
    public static final long FLAG_CMT_REV            = 0x0000000000010000l;
    public static final long FLAG_CMT_DATE           = 0x0000000000020000l;
    public static final long FLAG_CMT_AUTHOR         = 0x0000000000040000l;
    public static final long FLAG_UUID               = 0x0000000000080000l;
    public static final long FLAG_INCOMPLETE         = 0x0000000000100000l;
    public static final long FLAG_ABSENT             = 0x0000000000200000l;
    public static final long FLAG_LOCK_TOKEN         = 0x0000000000400000l;
    public static final long FLAG_LOCK_OWNER         = 0x0000000000800000l;
    public static final long FLAG_LOCK_COMMENT       = 0x0000000001000000l;
    public static final long FLAG_LOCK_CREATION_DATE = 0x0000000002000000l;
    public static final long FLAG_HAS_PROPS          = 0x0000000004000000l;
    public static final long FLAG_HAS_PROP_MODS      = 0x0000000008000000l;
    public static final long FLAG_CACHABLE_PROPS     = 0x0000000010000000l;
    public static final long FLAG_PRESENT_PROPS      = 0x0000000020000000l;
    public static final long FLAG_CHANGELIST         = 0x0000000040000000l;
    public static final long FLAG_KEEP_LOCAL         = 0x0000000080000000l;
    public static final long FLAG_WORKING_SIZE       = 0x0000000100000000l;
    public static final long FLAG_FORCE              = 0x4000000000000000l;
    
    protected String myName;
    protected long myRevision;
    protected String myURL;
    protected String myRepositoryURL;
    protected String myRepositoryUUID;
    protected SVNNodeKind myKind;
    protected String mySchedule;
    protected boolean myIsCopied;
    protected boolean myIsDeleted;
    protected boolean myIsAbsent;
    protected boolean myIsIncomplete;
    protected String myCopyFromURL;
    protected long myCopyFromRevision;
    protected String myConflictOld;
    protected String myConflictNew;
    protected String myConflictWorking;
    protected String myPropReject;
    protected SVNDate myTextTime;
    protected SVNDate myPropTime;
    protected String myChecksum;
    protected long myCommitedRevision;
    protected SVNDate myCommittedDate;
    protected String myCommitAuthor;
    protected String myLockToken;
    protected String myLockOwner;
    protected String myLockComment;
    protected SVNDate myLockCreationDate;    
    protected boolean myHasProperties;
    protected boolean myIsPropertiesModified;
    protected String myCachableProperties;
    protected String myPresentProperties;
    protected String myChangelist;
    protected long myWorkingSize;
    protected boolean myIsKeepLocal;
    protected SVNDepth myDepth;

    public static long loadFromMap(SVNEntry entry, String thisDirName, Map attributes) throws SVNException {
        long flags = 0;
        String name = (String) attributes.get(SVNEntry.NAME);
        entry.myName = name == null ? thisDirName : name;
        //
        String revisionStr = (String) attributes.get(SVNEntry.REVISION);
        if (revisionStr != null) {
            entry.myRevision = Long.parseLong(revisionStr);
            flags |= FLAG_REVISION;
        } else {
            entry.myRevision = -1;
        }
        //
        entry.myURL = (String) attributes.get(SVNEntry.URL);
        if (entry.myURL != null) {
            flags |= FLAG_URL;
        }
        //
        entry.myRepositoryURL = (String) attributes.get(SVNEntry.REPOS);
        if (entry.myRepositoryURL != null) {
            if (entry.myURL != null && !SVNPathUtil.isAncestor(entry.myRepositoryURL, entry.myURL)) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_CORRUPT, "Entry for ''{0}'' has invalid repository root", entry.getName());
                SVNErrorManager.error(err);
            }
            flags |= FLAG_REPOS;
        }
        //
        String kind = (String) attributes.get(SVNEntry.KIND);
        entry.myKind = SVNNodeKind.NONE;
        if (kind != null) {
            if (SVNNodeKind.FILE.toString().equals(kind)) {
                entry.myKind = SVNNodeKind.FILE;
            } else if (SVNNodeKind.DIR.toString().equals(kind)) {
                entry.myKind = SVNNodeKind.DIR;
            } else {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_CORRUPT, "Entry for ''{0}'' has invalid node kind", entry.getName());
                SVNErrorManager.error(err);
            }
            flags |= FLAG_KIND;
        }
        //
        String schedule = (String) attributes.get(SVNEntry.SCHEDULE);
        if (schedule != null) {
            if (SVNEntry.SCHEDULE_ADD.equals(schedule)) {
                entry.mySchedule = SVNEntry.SCHEDULE_ADD;                 
            } else if (SVNEntry.SCHEDULE_DELETE.equals(schedule)) {
                entry.mySchedule = SVNEntry.SCHEDULE_DELETE;                 
            } else if (SVNEntry.SCHEDULE_REPLACE.equals(schedule)) {
                entry.mySchedule = SVNEntry.SCHEDULE_REPLACE;                 
            } else {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_CORRUPT, "Entry for ''{0}'' has invalid ''{1}'' value", 
                        new Object[] {entry.getName(), SVNEntry.SCHEDULE});
                SVNErrorManager.error(err);
            }
            flags |= FLAG_SCHEDULE;
        }
        //
        entry.myPropReject = (String) attributes.get(SVNEntry.PREJFILE);
        if (attributes.containsKey(SVNEntry.PREJFILE)) {
            flags |= FLAG_PREJFILE;
        }
        entry.myConflictOld = (String) attributes.get(SVNEntry.CONFLICT_OLD);
        if (attributes.containsKey(SVNEntry.CONFLICT_OLD)) {
            flags |= FLAG_CONFLICT_OLD;
        }
        entry.myConflictNew = (String) attributes.get(SVNEntry.CONFLICT_NEW);
        if (attributes.containsKey(SVNEntry.CONFLICT_NEW)) {
            flags |= FLAG_CONFLICT_NEW;
        }
        entry.myConflictWorking = (String) attributes.get(SVNEntry.CONFLICT_WRK);
        if (attributes.containsKey(SVNEntry.CONFLICT_WRK)) {
            flags |= FLAG_CONFLICT_WRK;
        }
        entry.myIsCopied = Boolean.TRUE.toString().equals(attributes.get(SVNEntry.COPIED));
        if (attributes.containsKey(SVNEntry.COPIED)) {
            flags |= FLAG_COPIED;
        }
        //
        entry.myCopyFromURL = (String) attributes.get(SVNEntry.COPYFROM_URL);
        if (entry.myCopyFromURL != null) {
            flags |= SVNEntry.FLAG_COPYFROM_URL;
        }
        revisionStr = (String) attributes.get(SVNEntry.COPYFROM_REV);
        if (revisionStr != null) {
            entry.myCopyFromRevision = Long.parseLong(revisionStr);
            flags |= SVNEntry.FLAG_COPIED;
        }
        //
        entry.myIsDeleted = Boolean.TRUE.toString().equals(attributes.get(SVNEntry.DELETED));
        if (attributes.containsKey(SVNEntry.DELETED)) {
            flags |= FLAG_DELETED;
        }
        //
        entry.myIsAbsent = Boolean.TRUE.toString().equals(attributes.get(SVNEntry.ABSENT));
        if (attributes.containsKey(SVNEntry.ABSENT)) {
            flags |= FLAG_ABSENT;
        }
        //
        entry.myIsIncomplete = Boolean.TRUE.toString().equals(attributes.get(SVNEntry.INCOMPLETE));
        if (attributes.containsKey(SVNEntry.INCOMPLETE)) {
            flags |= FLAG_INCOMPLETE;
        }
        //
        entry.myIsKeepLocal = Boolean.TRUE.toString().equals(attributes.get(SVNEntry.KEEP_LOCAL));
        if (attributes.containsKey(SVNEntry.KEEP_LOCAL)) {
            flags |= FLAG_KEEP_LOCAL;
        }
        //
        String timeStr = (String) attributes.get(SVNEntry.TEXT_TIME);
        if (timeStr != null) {
            if (!timeStr.equals(SVNLog.WC_TIMESTAMP)) {
                entry.myTextTime = SVNDate.parseDate(timeStr);                
            }
            flags |= SVNEntry.FLAG_TEXT_TIME;
        }
        timeStr = (String) attributes.get(SVNEntry.PROP_TIME);
        if (timeStr != null) {
            if (!timeStr.equals(SVNLog.WC_TIMESTAMP)) {
                entry.myPropTime = SVNDate.parseDate(timeStr);                
            }
            flags |= SVNEntry.FLAG_PROP_TIME;
        }
        //
        entry.myChecksum = (String) attributes.get(SVNEntry.CHECKSUM);
        if (entry.myChecksum != null) {
            flags |= SVNEntry.FLAG_CHECKSUM;
        }
        //
        entry.myRepositoryUUID = (String) attributes.get(SVNEntry.UUID);
        if (entry.myRepositoryUUID != null) {
            flags |= SVNEntry.FLAG_UUID;
        }
        //
        timeStr = (String) attributes.get(SVNEntry.CMT_DATE);
        if (timeStr != null) {
            entry.myCommittedDate = SVNDate.parseDate(timeStr);                
            flags |= SVNEntry.FLAG_CMT_DATE;
        } else {
            entry.myCommittedDate = SVNDate.NULL;
        }
        revisionStr = (String) attributes.get(SVNEntry.CMT_REV);
        if (revisionStr != null) {
            entry.myCommitedRevision = Long.parseLong(revisionStr);
            flags |= SVNEntry.FLAG_CMT_REV;
        } else {
            entry.myCommitedRevision = -1;
        }
        entry.myCommitAuthor = (String) attributes.get(SVNEntry.CMT_AUTHOR);
        if (entry.myCommitAuthor != null) {
            flags |= SVNEntry.FLAG_CMT_AUTHOR;
        }
        //
        entry.myLockToken = (String) attributes.get(SVNEntry.LOCK_TOKEN);
        if (entry.myLockToken != null) {
            flags |= SVNEntry.FLAG_LOCK_TOKEN;
        }
        entry.myLockOwner = (String) attributes.get(SVNEntry.LOCK_OWNER);
        if (entry.myLockOwner != null) {
            flags |= SVNEntry.FLAG_LOCK_OWNER;
        }
        entry.myLockComment = (String) attributes.get(SVNEntry.LOCK_COMMENT);
        if (entry.myLockComment != null) {
            flags |= SVNEntry.FLAG_LOCK_COMMENT;
        }
        timeStr = (String) attributes.get(SVNEntry.LOCK_CREATION_DATE);
        if (timeStr != null) {
            entry.myLockCreationDate = SVNDate.parseDate(timeStr);                
            flags |= SVNEntry.FLAG_LOCK_CREATION_DATE;
        } 
        //
        entry.myChangelist = (String) attributes.get(SVNEntry.CHANGELIST);
        if (entry.myChangelist != null) {
            flags |= SVNEntry.FLAG_CHANGELIST;
        }
        //
        entry.myHasProperties = Boolean.TRUE.toString().equals(attributes.get(SVNEntry.HAS_PROPS));
        if (attributes.containsKey(SVNEntry.HAS_PROPS)) {
            flags |= SVNEntry.FLAG_HAS_PROP_MODS;
        }
        String hasPropMods = (String) attributes.get(SVNEntry.HAS_PROP_MODS);
        if (hasPropMods != null) {
            if (Boolean.TRUE.toString().equals(hasPropMods)) {
                entry.myIsPropertiesModified = true;
            } else if (!Boolean.FALSE.toString().equals(hasPropMods)) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_CORRUPT, "Entry for ''{0}'' has invalid ''{1}'' value", 
                        new Object[] {entry.getName(), SVNEntry.HAS_PROP_MODS});
                SVNErrorManager.error(err);
            }
            flags |= SVNEntry.FLAG_HAS_PROP_MODS;
        }
        //
        entry.myCachableProperties = (String) attributes.get(SVNEntry.CACHABLE_PROPS);
        if (entry.myCachableProperties != null) {
            flags |= SVNEntry.FLAG_CACHABLE_PROPS;
        }
        entry.myPresentProperties = (String) attributes.get(SVNEntry.PRESENT_PROPS);
        if (entry.myPresentProperties != null) {
            flags |= SVNEntry.FLAG_PRESENT_PROPS;
        }
        //
        String size = (String) attributes.get(SVNEntry.WORKING_SIZE);
        if (size != null) {
            if (!SVNLog.WC_WORKING_SIZE.equals(size)) {
                entry.myWorkingSize = Long.parseLong(size);
            }
            flags |= SVNEntry.FLAG_WORKING_SIZE;
        }

        return flags;
    }
    
    protected SVNEntry() {
        myRevision = -1;
        myWorkingSize = -1;
        myCopyFromRevision = -1;
        myCommitedRevision = -1;
        myDepth = SVNDepth.INFINITY;
        myKind = SVNNodeKind.NONE;
    }
    
    public String getName() {
        return myName;
    }
    
    public long getRevision() {
        return myRevision;
    }
    
    public String getURL() {
        return myURL;
    }
    
    public String getRepositoryURL() {
        return myRepositoryURL;
    }
    
    public String getRepositoryUUID() {
        return myRepositoryUUID;
    }
    
    public SVNNodeKind getKind() {
        return myKind;
    }
    
    public String getSchedule() {
        return mySchedule;
    }
    
    public boolean isCopied() {
        return myIsCopied;
    }
    
    public boolean isDeleted() {
        return myIsDeleted;
    }
    
    public boolean isAbsent() {
        return myIsAbsent;
    }
    
    public boolean isIncomplete() {
        return myIsIncomplete;
    }
    
    public String getCopyFromURL() {
        return myCopyFromURL;
    }
    
    public long getCopyFromRevision() {
        return myCopyFromRevision;
    }
    
    public String getConflictOld() {
        return myConflictOld;
    }
    
    public String getConflictNew() {
        return myConflictNew;
    }
    
    public String getConflictWorking() {
        return myConflictWorking;
    }
    
    public String getPropReject() {
        return myPropReject;
    }
    
    public SVNDate getTextTime() {
        return myTextTime;
    }
    
    public SVNDate getPropTime() {
        return myPropTime;
    }
    
    public String getChecksum() {
        return myChecksum;
    }
    
    public long getCommitedRevision() {
        return myCommitedRevision;
    }
    
    public SVNDate getCommittedDate() {
        return myCommittedDate;
    }
    
    public String getCommitAuthor() {
        return myCommitAuthor;
    }
    
    public String getLockToken() {
        return myLockToken;
    }
    
    public String getLockOwner() {
        return myLockOwner;
    }
    
    public String getLockComment() {
        return myLockComment;
    }
    
    public SVNDate getLockCreationDate() {
        return myLockCreationDate;
    }
    
    public boolean hasProperties() {
        return myHasProperties;
    }

    public boolean isPropertiesModified() {
        return myIsPropertiesModified;
    }

    public String getCachableProperties() {
        return myCachableProperties;
    }

    public String getPresentProperties() {
        return myPresentProperties;
    }

    public String getChangelist() {
        return myChangelist;
    }
    
    public long getWorkingSize() {
        return myWorkingSize;
    }
    
    public boolean isKeepLocal() {
        return myIsKeepLocal;
    }
    
    public SVNDepth getDepth() {
        return myDepth;
    }

    public boolean isHidden() {
        return (isDeleted() || isAbsent()) && !SCHEDULE_ADD.equals(getSchedule())  && !SCHEDULE_REPLACE.equals(getSchedule());
    }
    
    public boolean isDirectory() {
        return getKind() == SVNNodeKind.DIR;
    }
    
    public boolean isFile() {
        return getKind() == SVNNodeKind.FILE;
    }
    
    public void takeFrom(SVNEntry src) {
        if (myRevision < 0 && myKind != SVNNodeKind.DIR) {
            myRevision = src.getRevision();
        }
        if (myURL == null) {
            myURL = SVNPathUtil.append(src.getURL(), SVNEncodingUtil.uriEncode(myName));
        }
        if (myRepositoryURL == null) {
            myRepositoryURL = src.getRepositoryURL();
        }
        if (myRepositoryUUID == null && !(SCHEDULE_ADD.equals(mySchedule) || SCHEDULE_REPLACE.equals(mySchedule))) {
            myRepositoryUUID = src.getRepositoryUUID();
        }
        if (myCachableProperties == null) {
            myCachableProperties = src.getCachableProperties();
        }
    }

    public void buildPresentProperties(Map properties) {
        StringBuffer present = new StringBuffer();
        for(StringTokenizer props = new StringTokenizer(myCachableProperties, " "); props.hasMoreTokens();) {
            String propName = props.nextToken();
            if (properties.get(propName) != null) {
                if (present.length() > 0) {
                    present.append(' ');
                }
                present.append(propName);
            }
        }
        myPresentProperties = present.toString();
    }

    public boolean isCachableProperty(String propertyName) {
        return contains(myCachableProperties, propertyName);
    }

    public boolean isPresentProperty(String propertyName) {
        return contains(myCachableProperties, propertyName);
    }
    
    private static boolean contains(String properties, String name) {
        if (properties == null) {
            return false;
        }
        for(StringTokenizer props = new StringTokenizer(properties, " "); props.hasMoreTokens();) {
            String propName = props.nextToken();
            if (propName.equals(name)) {
                return true;
            }
        }
        return false;
    }
}
