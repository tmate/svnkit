package org.tmatesoft.svn.core.internal.wc.v17;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.tmatesoft.svn.core.ISVNCanceller;
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
import org.tmatesoft.svn.core.internal.util.SVNHashMap;
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
import org.tmatesoft.svn.core.wc.DefaultSVNRepositoryPool;
import org.tmatesoft.svn.core.wc.ISVNEventHandler;
import org.tmatesoft.svn.core.wc.ISVNOptions;
import org.tmatesoft.svn.core.wc.ISVNRepositoryPool;
import org.tmatesoft.svn.core.wc.SVNBasicClient;
import org.tmatesoft.svn.core.wc.SVNBasicClient;
import org.tmatesoft.svn.core.wc.SVNCommitItem;
import org.tmatesoft.svn.core.wc.SVNEvent;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.util.ISVNDebugLog;
import org.tmatesoft.svn.util.SVNDebugLog;
import org.tmatesoft.svn.util.SVNLogType;

public class SVNBasicClient17 extends SVNBasicClient implements
		ISVNEventHandler {
	protected SVNBasicClient dispatcher;

	protected SVNBasicClient17(SVNBasicClient from) {
		super(from);
		this.dispatcher = dispatcher;
	}

	public static SVNBasicClient17 delegate(SVNBasicClient dispatcher) {
		SVNBasicClient17 delegate = new SVNBasicClient17(dispatcher);
		return delegate;
	}

	/** 
	 * @from org.tmatesoft.svn.core.wc.SVNBasicClient
	 */
	public void dispatchEvent(SVNEvent event, double progress)
			throws SVNException {
		SVNErrorMessage err = SVNErrorMessage
				.create(SVNErrorCode.VERSION_MISMATCH);
		SVNErrorManager.error(err, SVNLogType.CLIENT);
	}

	/** 
	 * @from org.tmatesoft.svn.core.wc.SVNBasicClient
	 */
	public SVNURL deriveLocation(File path, SVNURL url,
			long[] pegRevisionNumber, SVNRevision pegRevision,
			SVNRepository repos, SVNWCAccess access) throws SVNException {
		SVNErrorMessage err = SVNErrorMessage
				.create(SVNErrorCode.VERSION_MISMATCH);
		SVNErrorManager.error(err, SVNLogType.CLIENT);
		return null;
	}

	/** 
	 * @from org.tmatesoft.svn.core.wc.SVNBasicClient
	 */
	public SVNURL getEntryLocation(File path, SVNEntry entry, long[] revNum,
			SVNRevision pegRevision) throws SVNException {
		SVNErrorMessage err = SVNErrorMessage
				.create(SVNErrorCode.VERSION_MISMATCH);
		SVNErrorManager.error(err, SVNLogType.CLIENT);
		return null;
	}

	/** 
	 * @from org.tmatesoft.svn.core.wc.SVNBasicClient
	 */
	static public String getPreviousLogPath(String path, SVNLogEntry logEntry,
			SVNNodeKind kind) throws SVNException {
		SVNErrorMessage err = SVNErrorMessage
				.create(SVNErrorCode.VERSION_MISMATCH);
		SVNErrorManager.error(err, SVNLogType.CLIENT);
		return null;
	}

	/** 
	 * Dispatches events to the registered event handler (if any). 
	 * @param event       the current event
	 * @param progress    progress state (from 0 to 1)
	 * @throws SVNException
	 * @from org.tmatesoft.svn.core.wc.SVNBasicClient
	 */
	public void handleEvent(SVNEvent event, double progress)
			throws SVNException {
		SVNErrorMessage err = SVNErrorMessage
				.create(SVNErrorCode.VERSION_MISMATCH);
		SVNErrorManager.error(err, SVNLogType.CLIENT);
	}

	/** 
	 * @from org.tmatesoft.svn.core.wc.SVNBasicClient
	 */
	public void dispatchEvent(SVNEvent event) throws SVNException {
		SVNErrorMessage err = SVNErrorMessage
				.create(SVNErrorCode.VERSION_MISMATCH);
		SVNErrorManager.error(err, SVNLogType.CLIENT);
	}

	/** 
	 * Sets externals definitions to be ignored or not during
	 * operations.
	 * <p>
	 * For example, if external definitions are set to be ignored
	 * then a checkout operation won't fetch them into a Working Copy.
	 * @param ignore  <span class="javakeyword">true</span> to ignore
	 * externals definitions, <span class="javakeyword">false</span> - 
	 * not to
	 * @see #isIgnoreExternals()
	 * @from org.tmatesoft.svn.core.wc.SVNBasicClient
	 */
	public void setIgnoreExternals(boolean ignore) {
	}

	/** 
	 * Returns the debug logger currently in use.  
	 * <p>
	 * If no debug logger has been specified by the time this call occurs, 
	 * a default one (returned by <code>org.tmatesoft.svn.util.SVNDebugLog.getDefaultLog()</code>) 
	 * will be created and used.
	 * @return a debug logger
	 * @from org.tmatesoft.svn.core.wc.SVNBasicClient
	 */
	public ISVNDebugLog getDebugLog() {
		return null;
	}

	/** 
	 * @from org.tmatesoft.svn.core.wc.SVNBasicClient
	 */
	public SVNRevision[] resolveRevisions(SVNRevision pegRevision,
			SVNRevision revision, boolean isURL,
			boolean noticeLocalModifications) {
		return null;
	}

	/** 
	 * Returns the root of the repository. 
	 * <p/>
	 * If <code>path</code> is not <span class="javakeyword">null</span> and <code>pegRevision</code> is 
	 * either {@link SVNRevision#WORKING} or {@link SVNRevision#BASE}, then attempts to fetch the repository 
	 * root from the working copy represented by <code>path</code>. If these conditions are not met or if the 
	 * repository root is not recorded in the working copy, then a repository connection is established 
	 * and the repository root is fetched from the session. 
	 * <p/>
	 * When fetching the repository root from the working copy and if <code>access</code> is 
	 * <span class="javakeyword">null</span>, a new working copy access will be created and the working copy 
	 * will be opened non-recursively for reading only. 
	 * <p/>
	 * All necessary cleanup (session or|and working copy close) will be performed automatically as the routine 
	 * finishes. 
	 * @param path           working copy path
	 * @param url            repository url
	 * @param pegRevision    revision in which the target is valid
	 * @param adminArea      working copy administrative area object
	 * @param access         working copy access object
	 * @return                repository root url
	 * @throws SVNException 
	 * @since                 1.2.0         
	 * @from org.tmatesoft.svn.core.wc.SVNBasicClient
	 */
	public SVNURL getReposRoot(File path, SVNURL url, SVNRevision pegRevision,
			SVNAdminArea adminArea, SVNWCAccess access) throws SVNException {
		SVNErrorMessage err = SVNErrorMessage
				.create(SVNErrorCode.VERSION_MISMATCH);
		SVNErrorManager.error(err, SVNLogType.CLIENT);
		return null;
	}

	/** 
	 * @from org.tmatesoft.svn.core.wc.SVNBasicClient
	 */
	public SVNURL ensureSessionURL(SVNRepository repository, SVNURL url)
			throws SVNException {
		SVNErrorMessage err = SVNErrorMessage
				.create(SVNErrorCode.VERSION_MISMATCH);
		SVNErrorManager.error(err, SVNLogType.CLIENT);
		return null;
	}

	/** 
	 * Gets run-time configuration options used by this object.
	 * @return the run-time options being in use
	 * @from org.tmatesoft.svn.core.wc.SVNBasicClient
	 */
	public ISVNOptions getOptions() {
		return null;
	}

	/** 
	 * @from org.tmatesoft.svn.core.wc.SVNBasicClient
	 */
	public String getPathRelativeToRoot(File path, SVNURL url,
			SVNURL reposRootURL, SVNWCAccess wcAccess, SVNRepository repos)
			throws SVNException {
		SVNErrorMessage err = SVNErrorMessage
				.create(SVNErrorCode.VERSION_MISMATCH);
		SVNErrorManager.error(err, SVNLogType.CLIENT);
		return null;
	}

	/** 
	 * @from org.tmatesoft.svn.core.wc.SVNBasicClient
	 */
	public SVNRepository createRepository(SVNURL url, File path,
			SVNAdminArea area, SVNRevision pegRevision, SVNRevision revision,
			long[] pegRev) throws SVNException {
		SVNErrorMessage err = SVNErrorMessage
				.create(SVNErrorCode.VERSION_MISMATCH);
		SVNErrorManager.error(err, SVNLogType.CLIENT);
		return null;
	}

	/** 
	 * Removes or adds a path prefix. This method is not intended for 
	 * users (from an API point of view). 
	 * @param prefix a path prefix
	 * @from org.tmatesoft.svn.core.wc.SVNBasicClient
	 */
	public void setEventPathPrefix(String prefix) {
	}

	/** 
	 * @from org.tmatesoft.svn.core.wc.SVNBasicClient
	 */
	public SVNRepository createRepository(SVNURL url, File path,
			SVNWCAccess access, boolean mayReuse) throws SVNException {
		SVNErrorMessage err = SVNErrorMessage
				.create(SVNErrorCode.VERSION_MISMATCH);
		SVNErrorManager.error(err, SVNLogType.CLIENT);
		return null;
	}

	/** 
	 * @from org.tmatesoft.svn.core.wc.SVNBasicClient
	 */
	public SVNRepositoryLocation[] getLocations(SVNURL url, File path,
			SVNRepository repository, SVNRevision revision, SVNRevision start,
			SVNRevision end) throws SVNException {
		SVNErrorMessage err = SVNErrorMessage
				.create(SVNErrorCode.VERSION_MISMATCH);
		SVNErrorManager.error(err, SVNLogType.CLIENT);
		return null;
	}

	/** 
	 * @from org.tmatesoft.svn.core.wc.SVNBasicClient
	 */
	public void elideMergeInfo(SVNWCAccess access, File path, SVNEntry entry,
			File wcElisionLimitPath) throws SVNException {
		SVNErrorMessage err = SVNErrorMessage
				.create(SVNErrorCode.VERSION_MISMATCH);
		SVNErrorManager.error(err, SVNLogType.CLIENT);
	}

	/** 
	 * @from org.tmatesoft.svn.core.wc.SVNBasicClient
	 */
	public int getLevelsToLockFromDepth(SVNDepth depth) {
		return 0;
	}

	/** 
	 * @from org.tmatesoft.svn.core.wc.SVNBasicClient
	 */
	public SVNWCAccess createWCAccess() {
		return null;
	}

	/** 
	 * @from org.tmatesoft.svn.core.wc.SVNBasicClient
	 */
	public SVNRepository createRepository(SVNURL url, String uuid,
			boolean mayReuse) throws SVNException {
		SVNErrorMessage err = SVNErrorMessage
				.create(SVNErrorCode.VERSION_MISMATCH);
		SVNErrorManager.error(err, SVNLogType.CLIENT);
		return null;
	}

	/** 
	 * @from org.tmatesoft.svn.core.wc.SVNBasicClient
	 */
	public long getRevisionNumber(SVNRevision revision,
			long[] latestRevisionNumber, SVNRepository repository, File path)
			throws SVNException {
		SVNErrorMessage err = SVNErrorMessage
				.create(SVNErrorCode.VERSION_MISMATCH);
		SVNErrorManager.error(err, SVNLogType.CLIENT);
		return 0;
	}

	/** 
	 * @from org.tmatesoft.svn.core.wc.SVNBasicClient
	 */
	public long getRevisionNumber(SVNRevision revision,
			SVNRepository repository, File path) throws SVNException {
		SVNErrorMessage err = SVNErrorMessage
				.create(SVNErrorCode.VERSION_MISMATCH);
		SVNErrorManager.error(err, SVNLogType.CLIENT);
		return 0;
	}

	/** 
	 * @from org.tmatesoft.svn.core.wc.SVNBasicClient
	 */
	public Map getLocations10(SVNRepository repos, final long pegRevision,
			final long startRevision, final long endRevision)
			throws SVNException {
		SVNErrorMessage err = SVNErrorMessage
				.create(SVNErrorCode.VERSION_MISMATCH);
		SVNErrorManager.error(err, SVNLogType.CLIENT);
		return null;
	}

	/** 
	 * @from org.tmatesoft.svn.core.wc.SVNBasicClient
	 */
	public Map getWCOrRepositoryMergeInfo(File path, SVNEntry entry,
			SVNMergeInfoInheritance inherit, boolean[] indirect,
			boolean reposOnly, SVNRepository repository) throws SVNException {
		SVNErrorMessage err = SVNErrorMessage
				.create(SVNErrorCode.VERSION_MISMATCH);
		SVNErrorManager.error(err, SVNLogType.CLIENT);
		return null;
	}

	/** 
	 * Sets run-time global configuration options to this object.
	 * @param options  the run-time configuration options 
	 * @from org.tmatesoft.svn.core.wc.SVNBasicClient
	 */
	public void setOptions(ISVNOptions options) {
	}

	/** 
	 * Determines if externals definitions are ignored.
	 * @return <span class="javakeyword">true</span> if ignored,
	 * otherwise <span class="javakeyword">false</span>
	 * @see #setIgnoreExternals(boolean)
	 * @from org.tmatesoft.svn.core.wc.SVNBasicClient
	 */
	public boolean isIgnoreExternals() {
		return false;
	}

	/** 
	 * @from org.tmatesoft.svn.core.wc.SVNBasicClient
	 */
	public ISVNEventHandler getEventDispatcher() {
		return null;
	}

	/** 
	 * @from org.tmatesoft.svn.core.wc.SVNBasicClient
	 */
	public SVNURL getURL(File path) throws SVNException {
		SVNErrorMessage err = SVNErrorMessage
				.create(SVNErrorCode.VERSION_MISMATCH);
		SVNErrorManager.error(err, SVNLogType.CLIENT);
		return null;
	}

	/** 
	 * @param path path relative to the repository location.
	 * @from org.tmatesoft.svn.core.wc.SVNBasicClient
	 */
	public Map getReposMergeInfo(SVNRepository repository, String path,
			long revision, SVNMergeInfoInheritance inheritance,
			boolean squelchIncapable) throws SVNException {
		SVNErrorMessage err = SVNErrorMessage
				.create(SVNErrorCode.VERSION_MISMATCH);
		SVNErrorManager.error(err, SVNLogType.CLIENT);
		return null;
	}

	/** 
	 * @from org.tmatesoft.svn.core.wc.SVNBasicClient
	 */
	public void setCommitItemAccess(SVNCommitItem item, SVNWCAccess access) {
	}

	/** 
	 * @from org.tmatesoft.svn.core.wc.SVNBasicClient
	 */
	public SVNWCAccess createWCAccess(final String pathPrefix) {
		return null;
	}

	/** 
	 * Redirects this call to the registered event handler (if any).
	 * @throws SVNCancelException  if the current operation
	 * was cancelled
	 * @from org.tmatesoft.svn.core.wc.SVNBasicClient
	 */
	public void checkCancelled() throws SVNCancelException {
	}

	/** 
	 * mergeInfo must not be null!
	 * @from org.tmatesoft.svn.core.wc.SVNBasicClient
	 */
	public Map getWCMergeInfo(File path, SVNEntry entry, File limitPath,
			SVNMergeInfoInheritance inherit, boolean base, boolean[] inherited)
			throws SVNException {
		SVNErrorMessage err = SVNErrorMessage
				.create(SVNErrorCode.VERSION_MISMATCH);
		SVNErrorManager.error(err, SVNLogType.CLIENT);
		return null;
	}

	/** 
	 * @from org.tmatesoft.svn.core.wc.SVNBasicClient
	 */
	public void sleepForTimeStamp() {
	}

	/** 
	 * @from org.tmatesoft.svn.core.wc.SVNBasicClient
	 */
	public ISVNRepositoryPool getRepositoryPool() {
		return null;
	}

	/** 
	 * Sets an event handler for this object. This event handler
	 * will be dispatched {@link SVNEvent} objects to provide 
	 * detailed information about actions and progress state 
	 * of version control operations performed by <b>do</b>*<b>()</b>
	 * methods of <b>SVN</b>*<b>Client</b> classes.
	 * @param dispatcher an event handler
	 * @from org.tmatesoft.svn.core.wc.SVNBasicClient
	 */
	public void setEventHandler(ISVNEventHandler dispatcher) {
	}

	/** 
	 * @from org.tmatesoft.svn.core.wc.SVNBasicClient
	 */
	public String getPathRelativeToSession(SVNURL url, SVNURL sessionURL,
			SVNRepository repos) throws SVNException {
		SVNErrorMessage err = SVNErrorMessage
				.create(SVNErrorCode.VERSION_MISMATCH);
		SVNErrorManager.error(err, SVNLogType.CLIENT);
		return null;
	}

	private static final class LocationsLogEntryHandler implements
			ISVNLogEntryHandler {
		private String myCurrentPath = null;
		private String myStartPath = null;
		private String myEndPath = null;
		private String myPegPath = null;
		private long myStartRevision;
		private long myEndRevision;
		private long myPegRevision;
		private SVNNodeKind myKind;
		private ISVNEventHandler myEventHandler;

		private LocationsLogEntryHandler(String path, long startRevision,
				long endRevision, long pegRevision, SVNNodeKind kind,
				ISVNEventHandler eventHandler) {
			myCurrentPath = path;
			myStartRevision = startRevision;
			myEndRevision = endRevision;
			myPegRevision = pegRevision;
			myEventHandler = eventHandler;
			myKind = kind;
		}

		public void handleLogEntry(SVNLogEntry logEntry) throws SVNException {
			if (myEventHandler != null) {
				myEventHandler.checkCancelled();
			}
			if (logEntry.getChangedPaths() == null) {
				return;
			}
			if (myCurrentPath == null) {
				return;
			}
			if (myStartPath == null
					&& logEntry.getRevision() <= myStartRevision) {
				myStartPath = myCurrentPath;
			}
			if (myEndPath == null && logEntry.getRevision() <= myEndRevision) {
				myEndPath = myCurrentPath;
			}
			if (myPegPath == null && logEntry.getRevision() <= myPegRevision) {
				myPegPath = myCurrentPath;
			}
			myCurrentPath = getPreviousLogPath(myCurrentPath, logEntry, myKind);
		}
	}
}
