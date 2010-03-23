/*
 * ====================================================================
 * Copyright (c) 2004-2009 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.wc;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import org.tmatesoft.svn.core.internal.util.SVNHashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.tmatesoft.svn.core.ISVNLogEntryHandler;
import org.tmatesoft.svn.core.SVNCancelException;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLogEntry;
import org.tmatesoft.svn.core.SVNLogEntryPath;
import org.tmatesoft.svn.core.SVNMergeInfo;
import org.tmatesoft.svn.core.SVNMergeInfoInheritance;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.internal.util.SVNEncodingUtil;
import org.tmatesoft.svn.core.internal.util.SVNMergeInfoUtil;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc.SVNPropertiesManager;
import org.tmatesoft.svn.core.internal.wc.SVNWCManager;
import org.tmatesoft.svn.core.internal.wc.admin.SVNAdminArea;
import org.tmatesoft.svn.core.internal.wc.admin.SVNEntry;
import org.tmatesoft.svn.core.internal.wc.admin.SVNWCAccess;
import org.tmatesoft.svn.core.io.SVNLocationEntry;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;
import org.tmatesoft.svn.util.ISVNDebugLog;
import org.tmatesoft.svn.util.SVNDebugLog;
import org.tmatesoft.svn.util.SVNLogType;
import org.tmatesoft.svn.core.internal.wc.v17.SVNBasicClient17;
import org.tmatesoft.svn.core.internal.wc.v16.SVNBasicClient16;


/**
 * The <b>SVNBasicClient</b> is the base class of all 
 * <b>SVN</b>*<b>Client</b> classes that provides a common interface
 * and realization.
 * 
 * <p>
 * All of <b>SVN</b>*<b>Client</b> classes use inherited methods of
 * <b>SVNBasicClient</b> to access Working Copies metadata, to create 
 * a driver object to access a repository if it's necessary, etc. In addition
 * <b>SVNBasicClient</b> provides some interface methods  - such as those
 * that allow you to set your {@link ISVNEventHandler event handler}, 
 * obtain run-time configuration options, and others. 
 * 
 * @version 1.3
 * @author  TMate Software Ltd.
 * @since   1.2
 */
public class SVNBasicClient implements ISVNEventHandler {

    protected ISVNRepositoryPool myRepositoryPool;
    protected ISVNOptions myOptions;
    protected ISVNEventHandler myEventDispatcher;
    protected List myPathPrefixesStack;
    protected boolean myIsIgnoreExternals;
    protected boolean myIsLeaveConflictsUnresolved;
    protected ISVNDebugLog myDebugLog;
    protected ISVNPathListHandler myPathListHandler;

    protected SVNBasicClient(SVNBasicClient from) {
        this.myRepositoryPool = from.myRepositoryPool;
        this.myOptions = from.myOptions;
        this.myEventDispatcher = from.myEventDispatcher;
        this.myPathPrefixesStack = from.myPathPrefixesStack;
        this.myIsIgnoreExternals = from.myIsIgnoreExternals;
        this.myIsLeaveConflictsUnresolved = from.myIsLeaveConflictsUnresolved;
        this.myDebugLog = from.myDebugLog;
        this.myPathListHandler = from.myPathListHandler;
    }

    protected SVNBasicClient(final ISVNAuthenticationManager authManager, ISVNOptions options) {
        this(new DefaultSVNRepositoryPool(authManager == null ? SVNWCUtil.createDefaultAuthenticationManager() : authManager, options, 0, false), options);
    }

    protected SVNBasicClient(ISVNRepositoryPool repositoryPool, ISVNOptions options) {
        myRepositoryPool = repositoryPool;
        setOptions(options);
        myPathPrefixesStack = new LinkedList();
    }
    
    /**
     * Gets run-time configuration options used by this object.
     * 
     * @return the run-time options being in use
     */
    public ISVNOptions getOptions() {
        return myOptions;
    }
    
    /**
     * Sets run-time global configuration options to this object.
     * 
     * @param options  the run-time configuration options 
     */
    public void setOptions(ISVNOptions options) {
        myOptions = options;
        if (myOptions == null) {
            myOptions = SVNWCUtil.createDefaultOptions(true);
        }
    }
    
    /**
     * Sets externals definitions to be ignored or not during
     * operations.
     * 
     * <p>
     * For example, if external definitions are set to be ignored
     * then a checkout operation won't fetch them into a Working Copy.
     * 
     * @param ignore  <span class="javakeyword">true</span> to ignore
     *                externals definitions, <span class="javakeyword">false</span> - 
     *                not to
     * @see           #isIgnoreExternals()
     */
    public void setIgnoreExternals(boolean ignore) {
        myIsIgnoreExternals = ignore;
    }
    
    /**
     * Determines if externals definitions are ignored.
     * 
     * @return <span class="javakeyword">true</span> if ignored,
     *         otherwise <span class="javakeyword">false</span>
     * @see    #setIgnoreExternals(boolean)
     */
    public boolean isIgnoreExternals() {
        return myIsIgnoreExternals;
    }
    /**
     * Sets (or unsets) all conflicted working files to be untouched
     * by update and merge operations.
     * 
     * <p>
     * By default when a file receives changes from the repository 
     * that are in conflict with local edits, an update operation places
     * two sections for each conflicting snatch into the working file 
     * one of which is a user's local edit and the second is the one just 
     * received from the repository. Like this:
     * <pre class="javacode">
     * <<<<<<< .mine
     * user's text
     * =======
     * received text
     * >>>>>>> .r2</pre><br /> 
     * Also the operation creates three temporary files that appear in the 
     * same directory as the working file. Now if you call this method with 
     * <code>leave</code> set to <span class="javakeyword">true</span>,
     * an update will still create temporary files but won't place those two
     * sections into your working file. And this behaviour also concerns
     * merge operations: any merging to a conflicted file will be prevented. 
     * In addition if there is any registered event
     * handler for an <b>SVNDiffClient</b> or <b>SVNUpdateClient</b> 
     * instance then the handler will be dispatched an event with 
     * the status type set to {@link SVNStatusType#CONFLICTED_UNRESOLVED}. 
     * 
     * <p>
     * The default value is <span class="javakeyword">false</span> until
     * a caller explicitly changes it calling this method. 
     * 
     * @param leave  <span class="javakeyword">true</span> to prevent 
     *               conflicted files from merging (all merging operations 
     *               will be skipped), otherwise <span class="javakeyword">false</span>
     * @see          #isLeaveConflictsUnresolved()              
     * @see          SVNUpdateClient
     * @see          SVNDiffClient
     * @see          ISVNEventHandler
     * @deprecated   this method should not be used anymore
     */
    public void setLeaveConflictsUnresolved(boolean leave) {
        myIsLeaveConflictsUnresolved = leave;
    }
    
    /**
     * Determines if conflicted files should be left unresolved
     * preventing from merging their contents during update and merge 
     * operations.
     *  
     * @return     <span class="javakeyword">true</span> if conflicted files
     *             are set to be prevented from merging, <span class="javakeyword">false</span>
     *             if there's no such restriction
     * @see        #setLeaveConflictsUnresolved(boolean)
     * @deprecated this method should not be used anymore
     */
    public boolean isLeaveConflictsUnresolved() {
        return myIsLeaveConflictsUnresolved;
    }
    
    /**
     * Sets an event handler for this object. This event handler
     * will be dispatched {@link SVNEvent} objects to provide 
     * detailed information about actions and progress state 
     * of version control operations performed by <b>do</b>*<b>()</b>
     * methods of <b>SVN</b>*<b>Client</b> classes.
     * 
     * @param dispatcher an event handler
     */
    public void setEventHandler(ISVNEventHandler dispatcher) {
        myEventDispatcher = dispatcher;
    }

    /**
     * Sets a path list handler implementation to this object.
     * @param handler  handler implementation
     * @since          1.2.0
     */
    public void setPathListHandler(ISVNPathListHandler handler) {
        myPathListHandler = handler;
    }
    
    /**
     * Sets a logger to write debug log information to.
     * 
     * @param log a debug logger
     */
    public void setDebugLog(ISVNDebugLog log) {
        myDebugLog = log;
    }
    
    /**
     * Returns the debug logger currently in use.  
     * 
     * <p>
     * If no debug logger has been specified by the time this call occurs, 
     * a default one (returned by <code>org.tmatesoft.svn.util.SVNDebugLog.getDefaultLog()</code>) 
     * will be created and used.
     * 
     * @return a debug logger
     */
    public ISVNDebugLog getDebugLog() {
        if (myDebugLog == null) {
            return SVNDebugLog.getDefaultLog();
        }
        return myDebugLog;
    }
    
    /**
     * Returns the root of the repository. 
     * 
     * <p/>
     * If <code>path</code> is not <span class="javakeyword">null</span> and <code>pegRevision</code> is 
     * either {@link SVNRevision#WORKING} or {@link SVNRevision#BASE}, then attempts to fetch the repository 
     * root from the working copy represented by <code>path</code>. If these conditions are not met or if the 
     * repository root is not recorded in the working copy, then a repository connection is established 
     * and the repository root is fetched from the session. 
     * 
     * <p/>
     * When fetching the repository root from the working copy and if <code>access</code> is 
     * <span class="javakeyword">null</span>, a new working copy access will be created and the working copy 
     * will be opened non-recursively for reading only. 
     * 
     * <p/>
     * All necessary cleanup (session or|and working copy close) will be performed automatically as the routine 
     * finishes. 
     * 
     * @param  path           working copy path
     * @param  url            repository url
     * @param  pegRevision    revision in which the target is valid
     * @param  adminArea      working copy administrative area object
     * @param  access         working copy access object
     * @return                repository root url
     * @throws SVNException 
     * @since                 1.2.0         
     */
    public SVNURL getReposRoot(File path, SVNURL url, SVNRevision pegRevision, SVNAdminArea adminArea, 
            SVNWCAccess access) throws SVNException {
                try {
                    return SVNBasicClient17.delegate(this).getReposRoot(path, url, pegRevision, adminArea, access);
                } catch (SVNException e) {
                    return SVNBasicClient16.delegate(this).getReposRoot(path, url, pegRevision, adminArea, access);
                }
            }
    
    protected void sleepForTimeStamp() {
        if (myPathPrefixesStack == null || myPathPrefixesStack.isEmpty()) {
            SVNFileUtil.sleepForTimestamp();
        }
    }

    protected SVNRepository createRepository(SVNURL url, File path, SVNWCAccess access, boolean mayReuse) throws SVNException {
        try {
            return SVNBasicClient17.delegate(this).createRepository(url, path, access, mayReuse);
        } catch (SVNException e) {
            return SVNBasicClient16.delegate(this).createRepository(url, path, access, mayReuse);
        }
    }
    
    protected SVNRepository createRepository(SVNURL url, String uuid, boolean mayReuse) throws SVNException {
        try {
            return SVNBasicClient17.delegate(this).createRepository(url, uuid, mayReuse);
        } catch (SVNException e) {
            return SVNBasicClient16.delegate(this).createRepository(url, uuid, mayReuse);
        }
    }
    
    protected ISVNRepositoryPool getRepositoryPool() {
        return myRepositoryPool;
    }

    protected void dispatchEvent(SVNEvent event) throws SVNException {
        try {
            SVNBasicClient17.delegate(this).dispatchEvent(event);
        } catch (SVNException e) {
            SVNBasicClient16.delegate(this).dispatchEvent(event);
        }
    }

    protected void dispatchEvent(SVNEvent event, double progress) throws SVNException {
        try {
            SVNBasicClient17.delegate(this).dispatchEvent(event, progress);
        } catch (SVNException e) {
            SVNBasicClient16.delegate(this).dispatchEvent(event, progress);
        }
    }
    
    /**
     * Removes or adds a path prefix. This method is not intended for 
     * users (from an API point of view). 
     * 
     * @param prefix a path prefix
     */
    public void setEventPathPrefix(String prefix) {
        if (prefix == null && !myPathPrefixesStack.isEmpty()) {
            myPathPrefixesStack.remove(myPathPrefixesStack.size() - 1);
        } else if (prefix != null) {
            myPathPrefixesStack.add(prefix);
        }
    }

    protected ISVNEventHandler getEventDispatcher() {
        return myEventDispatcher;
    }

    protected SVNWCAccess createWCAccess() {
        return createWCAccess(null);
    }

    protected SVNWCAccess createWCAccess(final String pathPrefix) {
        ISVNEventHandler eventHandler = null;
        if (pathPrefix != null) {
            eventHandler = new ISVNEventHandler() {
                public void handleEvent(SVNEvent event, double progress) throws SVNException {
                    dispatchEvent(event, progress);
                }

                public void checkCancelled() throws SVNCancelException {
                    SVNBasicClient.this.checkCancelled();
                }
            };
        } else {
            eventHandler = this;
        }
        SVNWCAccess access = SVNWCAccess.newInstance(eventHandler);
        access.setOptions(myOptions);
        return access;
    }

    /**
     * Dispatches events to the registered event handler (if any). 
     * 
     * @param event       the current event
     * @param progress    progress state (from 0 to 1)
     * @throws SVNException
     */
    public void handleEvent(SVNEvent event, double progress) throws SVNException {
        try {
            SVNBasicClient17.delegate(this).handleEvent(event, progress);
        } catch (SVNException e) {
            SVNBasicClient16.delegate(this).handleEvent(event, progress);
        }
    }
    
    /**
     * Handles a next working copy path with the {@link ISVNPathListHandler path list handler} 
     * if any was provided to this object through {@link #setPathListHandler(ISVNPathListHandler)}.
     * 
     * <p/>
     * Note: used by <code>SVNKit</code> internals.
     * 
     * @param  path            working copy path 
     * @throws SVNException 
     * @since                  1.2.0
     */
    public void handlePathListItem(File path) throws SVNException {
        if (myPathListHandler != null && path != null) {
            myPathListHandler.handlePathListItem(path);
        }
    }

    
    /**
     * Redirects this call to the registered event handler (if any).
     * 
     * @throws SVNCancelException  if the current operation
     *                             was cancelled
     */
    public void checkCancelled() throws SVNCancelException {
        if (myEventDispatcher != null) {
            myEventDispatcher.checkCancelled();
        }
    }
    
    protected long getRevisionNumber(SVNRevision revision, SVNRepository repository, File path) throws SVNException {
        try {
            return SVNBasicClient17.delegate(this).getRevisionNumber(revision, repository, path);
        } catch (SVNException e) {
            return SVNBasicClient16.delegate(this).getRevisionNumber(revision, repository, path);
        }
    }

    protected long getRevisionNumber(SVNRevision revision, long[] latestRevisionNumber, SVNRepository repository, 
            File path) throws SVNException {
                try {
                    return SVNBasicClient17.delegate(this).getRevisionNumber(revision, latestRevisionNumber, repository, path);
                } catch (SVNException e) {
                    return SVNBasicClient16.delegate(this).getRevisionNumber(revision, latestRevisionNumber, repository, path);
                }
            }

    protected SVNRepository createRepository(SVNURL url, File path, SVNAdminArea area, SVNRevision pegRevision, 
            SVNRevision revision, long[] pegRev) throws SVNException {
                try {
                    return SVNBasicClient17.delegate(this).createRepository(url, path, area, pegRevision, revision, pegRev);
                } catch (SVNException e) {
                    return SVNBasicClient16.delegate(this).createRepository(url, path, area, pegRevision, revision, pegRev);
                }
            }

    protected SVNRevision[] resolveRevisions(SVNRevision pegRevision, SVNRevision revision, boolean isURL,
            boolean noticeLocalModifications) {
        if (!pegRevision.isValid()) {
            if (isURL) {
                pegRevision = SVNRevision.HEAD;
            } else {
                if (noticeLocalModifications) {
                    pegRevision = SVNRevision.WORKING;
                } else {
                    pegRevision = SVNRevision.BASE;
                }
            }
        }

        if (!revision.isValid()) {
            revision = pegRevision;
        }
        return new SVNRevision[] { pegRevision, revision };
    }
    
    protected void elideMergeInfo(SVNWCAccess access, File path, SVNEntry entry,
            File wcElisionLimitPath) throws SVNException {
                try {
                    SVNBasicClient17.delegate(this).elideMergeInfo(access, path, entry, wcElisionLimitPath);
                } catch (SVNException e) {
                    SVNBasicClient16.delegate(this).elideMergeInfo(access, path, entry, wcElisionLimitPath);
                }
            }

    /**
     * @param path path relative to the repository location.
     */
    protected Map getReposMergeInfo(SVNRepository repository, String path, long revision, 
    		SVNMergeInfoInheritance inheritance, boolean squelchIncapable) throws SVNException {
                try {
                    return SVNBasicClient17.delegate(this).getReposMergeInfo(repository, path, revision, inheritance, squelchIncapable);
                } catch (SVNException e) {
                    return SVNBasicClient16.delegate(this).getReposMergeInfo(repository, path, revision, inheritance, squelchIncapable);
                }
            }
    
    protected Map getWCOrRepositoryMergeInfo(File path, SVNEntry entry, 
            SVNMergeInfoInheritance inherit, boolean[] indirect, boolean reposOnly, 
            SVNRepository repository) throws SVNException {
                try {
                    return SVNBasicClient17.delegate(this).getWCOrRepositoryMergeInfo(path, entry, inherit, indirect, reposOnly, repository);
                } catch (SVNException e) {
                    return SVNBasicClient16.delegate(this).getWCOrRepositoryMergeInfo(path, entry, inherit, indirect, reposOnly, repository);
                }
            }
    
    /**
     * mergeInfo must not be null!
     */
    protected Map getWCMergeInfo(File path, SVNEntry entry, File limitPath, SVNMergeInfoInheritance inherit, 
            boolean base, boolean[] inherited) throws SVNException {
                try {
                    return SVNBasicClient17.delegate(this).getWCMergeInfo(path, entry, limitPath, inherit, base, inherited);
                } catch (SVNException e) {
                    return SVNBasicClient16.delegate(this).getWCMergeInfo(path, entry, limitPath, inherit, base, inherited);
                }
            }

    protected long getPathLastChangeRevision(String relPath, long revision, SVNRepository repository) throws SVNException {
        final long[] rev = new long[1];
        rev[0] = SVNRepository.INVALID_REVISION;

            repository.log(new String[] { relPath }, 1, revision, false, true, 1, false, null, 
                    new ISVNLogEntryHandler() {
                public void handleLogEntry(SVNLogEntry logEntry) throws SVNException {
                    rev[0] = logEntry.getRevision();
                }
            });
        return rev[0];
    }
    
    protected String getPathRelativeToRoot(File path, SVNURL url, SVNURL reposRootURL, SVNWCAccess wcAccess,
            SVNRepository repos) throws SVNException {
                try {
                    return SVNBasicClient17.delegate(this).getPathRelativeToRoot(path, url, reposRootURL, wcAccess, repos);
                } catch (SVNException e) {
                    return SVNBasicClient16.delegate(this).getPathRelativeToRoot(path, url, reposRootURL, wcAccess, repos);
                }
            }

    protected String getPathRelativeToSession(SVNURL url, SVNURL sessionURL, SVNRepository repos) throws SVNException {
        try {
            return SVNBasicClient17.delegate(this).getPathRelativeToSession(url, sessionURL, repos);
        } catch (SVNException e) {
            return SVNBasicClient16.delegate(this).getPathRelativeToSession(url, sessionURL, repos);
        }
    }
    
    protected SVNRepositoryLocation[] getLocations(SVNURL url, File path, SVNRepository repository, 
    		SVNRevision revision, SVNRevision start, SVNRevision end) throws SVNException {
                try {
                    return SVNBasicClient17.delegate(this).getLocations(url, path, repository, revision, start, end);
                } catch (SVNException e) {
                    return SVNBasicClient16.delegate(this).getLocations(url, path, repository, revision, start, end);
                }
            }
    
    protected SVNURL getURL(File path) throws SVNException {
        try {
            return SVNBasicClient17.delegate(this).getURL(path);
        } catch (SVNException e) {
            return SVNBasicClient16.delegate(this).getURL(path);
        }
    }
    
    protected SVNURL deriveLocation(File path, SVNURL url, long[] pegRevisionNumber, SVNRevision pegRevision, 
            SVNRepository repos, SVNWCAccess access) throws SVNException {
                try {
                    return SVNBasicClient17.delegate(this).deriveLocation(path, url, pegRevisionNumber, pegRevision, repos, access);
                } catch (SVNException e) {
                    return SVNBasicClient16.delegate(this).deriveLocation(path, url, pegRevisionNumber, pegRevision, repos, access);
                }
            }
    
    protected SVNURL getEntryLocation(File path, SVNEntry entry, long[] revNum, SVNRevision pegRevision) throws SVNException {
        try {
            return SVNBasicClient17.delegate(this).getEntryLocation(path, entry, revNum, pegRevision);
        } catch (SVNException e) {
            return SVNBasicClient16.delegate(this).getEntryLocation(path, entry, revNum, pegRevision);
        }
    }
    
    protected SVNURL ensureSessionURL(SVNRepository repository, SVNURL url) throws SVNException {
        try {
            return SVNBasicClient17.delegate(this).ensureSessionURL(repository, url);
        } catch (SVNException e) {
            return SVNBasicClient16.delegate(this).ensureSessionURL(repository, url);
        }
    }
    
    protected int getLevelsToLockFromDepth(SVNDepth depth) {
        return  depth == SVNDepth.EMPTY || depth == SVNDepth.FILES ? 0 : 
            (depth == SVNDepth.IMMEDIATES ? 1 : SVNWCAccess.INFINITE_DEPTH);  
    }

    protected void setCommitItemAccess(SVNCommitItem item, SVNWCAccess access) {
        item.setWCAccess(access);
    }

    protected void setCommitItemProperty(SVNCommitItem item, String name, SVNPropertyValue value) {
        item.setProperty(name, value);
    }

    protected void setCommitItemFlags(SVNCommitItem item, boolean contentModified, boolean propertiesModified) {
        item.setContentsModified(contentModified);
        item.setPropertiesModified(propertiesModified);
    }
    
    protected static class RepositoryReference {

        public RepositoryReference(String url, long rev) {
            URL = url;
            Revision = rev;
        }

        public String URL;

        public long Revision;
    }

    protected static class SVNRepositoryLocation {

        private SVNURL myURL;
        private long myRevision;

        public SVNRepositoryLocation(SVNURL url, long rev) {
            myURL = url;
            myRevision = rev;
        }
        public long getRevisionNumber() {
            return myRevision;
        }
        public SVNURL getURL() {
            return myURL;
        }
    }

}