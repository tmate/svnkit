/*
 * ====================================================================
 * Copyright (c) 2004 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://tmate.org/svn/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */

package org.tmatesoft.svn.core.io;

import java.util.Date;
import java.util.Map;

/**
 * <p>
 * The class <code>SVNLogEntry</code> incapsulates log information provided for every
 * commit operation. This information includes:
 * <ul>
 * <li>revision number the repository was committed to;
 * <li>person who made the commit;
 * <li>date (generally moment in time) when the commit was performed;
 * <li>client's log message that accompanied the commit;
 * <li>map collection that contains all the paths of the entries which were
 * changed at the commit. Map keys are the paths themselves and values are
 * <code>SVNLogEntryPath</code> instances. 
 * </ul>
 * </p>
 * <p>
 * Instances of <code>SVNLogEntry</code> are passed to
 * <code>ISVNLogEntryHandler</code> during the progress of the 
 * {@link SVNRepository#log(String[], long, long, boolean, boolean, ISVNLogEntryHandler)
 * log} operation. {@link ISVNLogEntryHandler#handleLogEntry(SVNLogEntry)
 * ISVNLogEntryHandlerhandleLogEntry(SVNLogEntry)} then performs handling
 * the passed log entry.
 * </p>
 * <p>
 * NOTE: if the <code>changedPath</code> flag is <code>false</code> in 
 * {@link SVNRepository#log(String[], long, long, boolean, boolean, ISVNLogEntryHandler)
 * SVNRepository.log(...)} a call to {@link #getChangedPaths()}
 * will return an empty map. 
 * </p>
 * @version 1.0
 * @author TMate Software Ltd.
 * @see SVNLogEntryPath
 * @see SVNRepository
 */
public class SVNLogEntry {
    
    private long myRevision;
    private String myAuthor;
    private Date myDate;
    private String myMessage;
    private Map myChangedPaths;
    /**
     * <p>
     * Constructs a <code>SVNLogEntry</code> object. 
     * </p>
     * @param changedPaths a map collection which keys should be
     * all the paths of the entries that were changed in <code>revision</code>.
     * And values are <code>SVNLogEntryPath</code> instances.
     * @param a revision revision number.
     * @param author the person who committed the repository to <code>revision</code>.
     * @param date the moment in time when changes were committed to the 
     * repository.
     * @param message an obligatory log message provided for committing.
     * @see SVNLogEntryPath
     */
    public SVNLogEntry(Map changedPaths, long revision, String author, Date date, String message) {
        myRevision = revision;
        myAuthor = author;
        myDate = date;
        myMessage = message;
        myChangedPaths = changedPaths;
    }
    /**
     * <p>
     * Gets a map collection containing all the paths of the entries that
     * were changed in the revision. It can be empty, see the class 
     * description for details.
     * </p>
     * @return a <code>Map</code> instance which keys are all the paths 
     * of the entries that were changed and values are <code>SVNLogEntryPath</code>
     * instances.
     */
    public Map getChangedPaths() {
        return myChangedPaths;
    }
    /**
     * <p>
     * Gets the commit author name.
     * </p>
     * @return author name.
     */
    public String getAuthor() {
        return myAuthor;
    }
    /**
     * <p>
     * Gets the moment in time when the commit was performed.
     * </p>
     * @return a <code>Date</code> instance.
     */
    public Date getDate() {
        return myDate;
    }
    /**
     * <p>
     * Gets the log message attached to the commit.
     * </p>
     * @return log message.
     */
    public String getMessage() {
        return myMessage;
    }
    /**
     * <p>
     * Gets the revision number of the repository.
     * </p>
     * @return revision number.
     */
    public long getRevision() {
        return myRevision;
    }
}
