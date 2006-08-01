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

import java.util.Iterator;
import java.util.Map;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.util.SVNEncodingUtil;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;

/**
 * @version 1.0
 * @author TMate Software Ltd.
 */
public class SVNEntry implements Comparable {

    private SVNAdminArea myAdminArea;

    private String myName;

    public SVNEntry(SVNAdminArea adminArea, String name) {
        myAdminArea = adminArea;
        myName = name;
    }

    public boolean equals(Object obj) {
        if (obj == null || obj.getClass() != SVNEntry.class) {
            return false;
        }
        SVNEntry entry = (SVNEntry) obj;
        return entry.myAdminArea == myAdminArea && entry.myName.equals(myName);
    }

    public int hashCode() {
        return myAdminArea.hashCode() + 17 * myName.hashCode();
    }

    public int compareTo(Object obj) {
        if (obj == null || obj.getClass() != SVNEntry.class) {
            return 1;
        }
        return myName.compareTo(((SVNEntry) obj).myName);
    }

    public String getURL() {
        String url = myAdminArea.getAttributeValue(myName, SVNProperty.URL);
        if (url == null && !"".equals(myName)) {
            url = myAdminArea.getAttributeValue("", SVNProperty.URL);
            url = SVNPathUtil.append(url, SVNEncodingUtil.uriEncode(myName));
        }
        return url;
    }
    
    public SVNURL getSVNURL() throws SVNException {
        String url = getURL();
        if (url != null) {
            return SVNURL.parseURIEncoded(url);
        }
        return null;
    }

    public String getName() {
        return myName;
    }

    public boolean isDirectory() {
        return SVNProperty.KIND_DIR.equals(myAdminArea.getAttributeValue(myName, SVNProperty.KIND));
    }

    public long getRevision() {
        String revStr = myAdminArea.getAttributeValue(myName, SVNProperty.REVISION);
        if (revStr == null && !"".equals(myName)) {
            revStr = myAdminArea.getAttributeValue("", SVNProperty.REVISION);
        }
        if (revStr == null) {
            return -1;
        }
        return Long.parseLong(revStr);
    }

    public boolean isScheduledForAddition() {
        return SVNProperty.SCHEDULE_ADD.equals(myAdminArea.getAttributeValue(
                myName, SVNProperty.SCHEDULE));
    }

    public boolean isScheduledForDeletion() {
        return SVNProperty.SCHEDULE_DELETE.equals(myAdminArea.getAttributeValue(
                myName, SVNProperty.SCHEDULE));
    }

    public boolean isScheduledForReplacement() {
        return SVNProperty.SCHEDULE_REPLACE.equals(myAdminArea.getAttributeValue(
                myName, SVNProperty.SCHEDULE));
    }

    public boolean isHidden() {
        return (isDeleted() || isAbsent()) && !isScheduledForAddition()
                && !isScheduledForReplacement();
    }

    public boolean isFile() {
        return SVNProperty.KIND_FILE.equals(myAdminArea.getAttributeValue(myName,
                SVNProperty.KIND));
    }

    public String getLockToken() {
        return myAdminArea.getAttributeValue(myName, SVNProperty.LOCK_TOKEN);
    }

    public boolean isDeleted() {
        return Boolean.TRUE.toString().equals(myAdminArea.getAttributeValue(myName, SVNProperty.DELETED));
    }

    public boolean isAbsent() {
        return Boolean.TRUE.toString().equals(
                myAdminArea.getAttributeValue(myName, SVNProperty.ABSENT));
    }

    public String toString() {
        return myName;
    }

    public void setCachableProperties(String[] cachableProps) {
        myAdminArea.setCachableProperties(myName, cachableProps);
    }

    public String[] getCachableProperties() {
        return myAdminArea.getCachableProperties(myName);
    }

    public void setPresentProperties(String[] presentProps) {
        myAdminArea.setPresentProperties(myName, presentProps);
    }

    public String[] getPresentProperties() {
        return myAdminArea.getPresentProperties(myName);
    }

    public boolean setRevision(long revision) {
        return myAdminArea.setAttributeValue(myName, SVNProperty.REVISION, Long
                .toString(revision));
    }

    public boolean setURL(String url) {
        return myAdminArea.setAttributeValue(myName, SVNProperty.URL, url);
    }

    public void setIncomplete(boolean incomplete) {
        myAdminArea.setAttributeValue(myName, SVNProperty.INCOMPLETE,
                incomplete ? Boolean.TRUE.toString() : null);
    }

    public boolean isIncomplete() {
        return Boolean.TRUE.toString().equals(
                myAdminArea.getAttributeValue(myName, SVNProperty.INCOMPLETE));
    }

    public String getConflictOld() {
        return myAdminArea.getAttributeValue(myName, SVNProperty.CONFLICT_OLD);
    }

    public void setConflictOld(String name) {
        myAdminArea.setAttributeValue(myName, SVNProperty.CONFLICT_OLD, name);
    }

    public String getConflictNew() {
        return myAdminArea.getAttributeValue(myName, SVNProperty.CONFLICT_NEW);
    }

    public void setConflictNew(String name) {
        myAdminArea.setAttributeValue(myName, SVNProperty.CONFLICT_NEW, name);
    }

    public String getConflictWorking() {
        return myAdminArea.getAttributeValue(myName, SVNProperty.CONFLICT_WRK);
    }

    public void setConflictWorking(String name) {
        myAdminArea.setAttributeValue(myName, SVNProperty.CONFLICT_WRK, name);
    }

    public String getPropRejectFile() {
        return myAdminArea.getAttributeValue(myName, SVNProperty.PROP_REJECT_FILE);
    }

    public void setPropRejectFile(String name) {
        myAdminArea.setAttributeValue(myName, SVNProperty.PROP_REJECT_FILE, name);
    }

    public String getAuthor() {
        return myAdminArea.getAttributeValue(myName, SVNProperty.LAST_AUTHOR);
    }

    public String getCommittedDate() {
        return myAdminArea.getAttributeValue(myName, SVNProperty.COMMITTED_DATE);
    }

    public long getCommittedRevision() {
        String rev = myAdminArea.getAttributeValue(myName,
                SVNProperty.COMMITTED_REVISION);
        if (rev == null) {
            return -1;
        }
        return Long.parseLong(rev);
    }

    public void setTextTime(String time) {
        myAdminArea.setAttributeValue(myName, SVNProperty.TEXT_TIME, time);
    }

    public void setKind(SVNNodeKind kind) {
        String kindStr = kind == SVNNodeKind.DIR ? SVNProperty.KIND_DIR : (kind == SVNNodeKind.FILE ? SVNProperty.KIND_FILE : null);
        myAdminArea.setAttributeValue(myName, SVNProperty.KIND, kindStr);
    }

    public void setAbsent(boolean absent) {
        myAdminArea.setAttributeValue(myName, SVNProperty.ABSENT,
                absent ? Boolean.TRUE.toString() : null);
    }

    public void setDeleted(boolean deleted) {
        myAdminArea.setAttributeValue(myName, SVNProperty.DELETED,
                deleted ? Boolean.TRUE.toString() : null);
    }

    public SVNNodeKind getKind() {
        String kind = myAdminArea.getAttributeValue(myName, SVNProperty.KIND);
        return SVNNodeKind.parseKind(kind);
    }
    
    public String getTextTime() {
        return myAdminArea.getAttributeValue(myName, SVNProperty.TEXT_TIME);
    }

    public String getChecksum() {
        return myAdminArea.getAttributeValue(myName, SVNProperty.CHECKSUM);
    }

    public void setLockComment(String comment) {
        myAdminArea.setAttributeValue(myName, SVNProperty.LOCK_COMMENT, comment);
    }

    public void setLockOwner(String owner) {
        myAdminArea.setAttributeValue(myName, SVNProperty.LOCK_OWNER, owner);
    }

    public void setLockCreationDate(String date) {
        myAdminArea.setAttributeValue(myName, SVNProperty.LOCK_CREATION_DATE, date);
    }

    public void setLockToken(String token) {
        myAdminArea.setAttributeValue(myName, SVNProperty.LOCK_TOKEN, token);
    }

    public void setUUID(String uuid) {
        myAdminArea.setAttributeValue(myName, SVNProperty.UUID, uuid);
    }

    public void unschedule() {
        myAdminArea.setAttributeValue(myName, SVNProperty.SCHEDULE, null);
    }

    public void scheduleForAddition() {
        myAdminArea.setAttributeValue(myName, SVNProperty.SCHEDULE,
                SVNProperty.SCHEDULE_ADD);
    }

    public void scheduleForDeletion() {
        myAdminArea.setAttributeValue(myName, SVNProperty.SCHEDULE,
                SVNProperty.SCHEDULE_DELETE);
    }

    public void scheduleForReplacement() {
        myAdminArea.setAttributeValue(myName, SVNProperty.SCHEDULE,
                SVNProperty.SCHEDULE_REPLACE);
    }

    public void setCopyFromRevision(long revision) {
        myAdminArea.setAttributeValue(myName, SVNProperty.COPYFROM_REVISION,
                revision >= 0 ? Long.toString(revision) : null);
    }

    public boolean setCopyFromURL(String url) {
        return myAdminArea.setAttributeValue(myName, SVNProperty.COPYFROM_URL, url);
    }

    public void setCopied(boolean copied) {
        myAdminArea.setAttributeValue(myName, SVNProperty.COPIED, copied ? Boolean.TRUE.toString() : null);
    }

    public String getCopyFromURL() {
        return myAdminArea.getAttributeValue(myName, SVNProperty.COPYFROM_URL);
    }

    public SVNURL getCopyFromSVNURL() throws SVNException {
        String url = getCopyFromURL();
        if (url != null) {
            return SVNURL.parseURIEncoded(url);
        }
        return null;
    }

    public long getCopyFromRevision() {
        String rev = myAdminArea.getAttributeValue(myName,
                SVNProperty.COPYFROM_REVISION);
        if (rev == null) {
            return -1;
        }
        return Long.parseLong(rev);
    }

    public String getPropTime() {
        return myAdminArea.getAttributeValue(myName, SVNProperty.PROP_TIME);
    }

    public void setPropTime(String time) {
        myAdminArea.setAttributeValue(myName, SVNProperty.PROP_TIME, time);
    }

    public boolean isCopied() {
        return Boolean.TRUE.toString().equals(
                myAdminArea.getAttributeValue(myName, SVNProperty.COPIED));
    }

    public String getUUID() {
        return myAdminArea.getAttributeValue(myName, SVNProperty.UUID);
    }

    public String getRepositoryRoot() {
        return myAdminArea.getAttributeValue(myName, SVNProperty.REPOS);
    }

    public SVNURL getRepositoryRootURL() throws SVNException {
        String url = getRepositoryRoot();
        if (url != null) {
            return SVNURL.parseURIEncoded(url);
        }
        return null;
    }
    
    public boolean setRepositoryRoot(String url) {
        return myAdminArea.setAttributeValue(myName, SVNProperty.REPOS, url);
    }

    public boolean setRepositoryRootURL(SVNURL url) {
        return setRepositoryRoot(url == null ? null : url.toString());
    }

    public void loadProperties(Map entryProps) {
        if (entryProps == null) {
            return;
        }
        for (Iterator propNames = entryProps.keySet().iterator(); propNames.hasNext();) {
            String propName = (String) propNames.next();
            myAdminArea.setAttributeValue(myName, propName, (String) entryProps.get(propName));
        }
    }

    public String getLockOwner() {
        return myAdminArea.getAttributeValue(myName, SVNProperty.LOCK_OWNER);
    }

    public String getLockComment() {
        return myAdminArea.getAttributeValue(myName, SVNProperty.LOCK_COMMENT);
    }

    public String getLockCreationDate() {
        return myAdminArea.getAttributeValue(myName,
                SVNProperty.LOCK_CREATION_DATE);
    }

    public String getSchedule() {
        return myAdminArea.getAttributeValue(myName, SVNProperty.SCHEDULE);
    }

    public Map asMap() {
        return myAdminArea.getEntryMap(myName);
    }
}