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
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.internal.util.SVNDate;
import org.tmatesoft.svn.core.internal.util.SVNEncodingUtil;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;


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
}
