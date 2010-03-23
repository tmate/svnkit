package org.tmatesoft.svn.core.internal.wc.v16;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import org.tmatesoft.svn.core.SVNCancelException;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNDirEntry;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLock;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.SVNRevisionProperty;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.internal.util.SVNDate;
import org.tmatesoft.svn.core.internal.util.SVNEncodingUtil;
import org.tmatesoft.svn.core.internal.util.SVNHashMap;
import org.tmatesoft.svn.core.internal.util.SVNHashSet;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.util.SVNURLUtil;
import org.tmatesoft.svn.core.internal.wc.IOExceptionWrapper;
import org.tmatesoft.svn.core.internal.wc.SVNAdminUtil;
import org.tmatesoft.svn.core.internal.wc.SVNCancellableOutputStream;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNEventFactory;
import org.tmatesoft.svn.core.internal.wc.SVNExternal;
import org.tmatesoft.svn.core.internal.wc.SVNFileListUtil;
import org.tmatesoft.svn.core.internal.wc.SVNFileType;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc.SVNPropertiesManager;
import org.tmatesoft.svn.core.internal.wc.SVNStatusEditor;
import org.tmatesoft.svn.core.internal.wc.SVNTreeConflictUtil;
import org.tmatesoft.svn.core.internal.wc.SVNWCManager;
import org.tmatesoft.svn.core.internal.wc.admin.ISVNEntryHandler;
import org.tmatesoft.svn.core.internal.wc.admin.SVNAdminArea;
import org.tmatesoft.svn.core.internal.wc.admin.SVNAdminAreaFactory;
import org.tmatesoft.svn.core.internal.wc.admin.SVNAdminAreaInfo;
import org.tmatesoft.svn.core.internal.wc.admin.SVNEntry;
import org.tmatesoft.svn.core.internal.wc.admin.SVNLog;
import org.tmatesoft.svn.core.internal.wc.admin.SVNTranslator;
import org.tmatesoft.svn.core.internal.wc.admin.SVNVersionedProperties;
import org.tmatesoft.svn.core.internal.wc.admin.SVNWCAccess;
import org.tmatesoft.svn.core.io.ISVNLockHandler;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.wc.ISVNAddParameters;
import org.tmatesoft.svn.core.wc.ISVNEventHandler;
import org.tmatesoft.svn.core.wc.ISVNInfoHandler;
import org.tmatesoft.svn.core.wc.ISVNOptions;
import org.tmatesoft.svn.core.wc.ISVNPropertyHandler;
import org.tmatesoft.svn.core.wc.ISVNPropertyValueProvider;
import org.tmatesoft.svn.core.wc.ISVNRepositoryPool;
import org.tmatesoft.svn.core.wc.ISVNStatusHandler;
import org.tmatesoft.svn.core.wc.SVNConflictChoice;
import org.tmatesoft.svn.core.wc.SVNConflictDescription;
import org.tmatesoft.svn.core.wc.SVNEvent;
import org.tmatesoft.svn.core.wc.SVNEventAction;
import org.tmatesoft.svn.core.wc.SVNInfo;
import org.tmatesoft.svn.core.wc.SVNPropertyData;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNStatus;
import org.tmatesoft.svn.core.wc.SVNStatusType;
import org.tmatesoft.svn.core.wc.SVNTreeConflictDescription;
import org.tmatesoft.svn.core.wc.SVNWCClient;
import org.tmatesoft.svn.core.wc.SVNWCClient;
import org.tmatesoft.svn.util.ISVNDebugLog;
import org.tmatesoft.svn.util.SVNDebugLog;
import org.tmatesoft.svn.util.SVNLogType;

public class SVNWCClient16 extends SVNWCClient {
	protected SVNWCClient dispatcher;

	protected SVNWCClient16(SVNWCClient from) {
		super(from);
		this.dispatcher = dispatcher;
	}

	public static SVNWCClient16 delegate(SVNWCClient dispatcher) {
		SVNWCClient16 delegate = new SVNWCClient16(dispatcher);
		return delegate;
	}

	/** 
	 * Constructs and initializes an <b>SVNWCClient</b> object
	 * with the specified run-time configuration and repository pool object.
	 * <p/>
	 * <p/>
	 * If <code>options</code> is <span class="javakeyword">null</span>,
	 * then this <b>SVNWCClient</b> will be using a default run-time
	 * configuration driver  which takes client-side settings from the
	 * default SVN's run-time configuration area but is not able to
	 * change those settings (read more on {@link ISVNOptions} and {@link SVNWCUtil}).
	 * <p/>
	 * <p/>
	 * If <code>repositoryPool</code> is <span class="javakeyword">null</span>,
	 * then {@link org.tmatesoft.svn.core.io.SVNRepositoryFactory} will be used to create {@link SVNRepository repository access objects}.
	 * @param repositoryPool   a repository pool object
	 * @param options          a run-time configuration options driver
	 * @from org.tmatesoft.svn.core.wc.SVNWCClient
	 */
	public SVNWCClient16(ISVNRepositoryPool repositoryPool, ISVNOptions options) {
		super(repositoryPool, options);
	}

	/** 
	 * Constructs and initializes an <b>SVNWCClient</b> object
	 * with the specified run-time configuration and authentication
	 * drivers.
	 * <p/>
	 * <p/>
	 * If <code>options</code> is <span class="javakeyword">null</span>,
	 * then this <b>SVNWCClient</b> will be using a default run-time
	 * configuration driver  which takes client-side settings from the
	 * default SVN's run-time configuration area but is not able to
	 * change those settings (read more on {@link ISVNOptions} and {@link SVNWCUtil}).
	 * <p/>
	 * <p/>
	 * If <code>authManager</code> is <span class="javakeyword">null</span>,
	 * then this <b>SVNWCClient</b> will be using a default authentication
	 * and network layers driver (see {@link SVNWCUtil#createDefaultAuthenticationManager()})
	 * which uses server-side settings and auth storage from the
	 * default SVN's run-time configuration area (or system properties
	 * if that area is not found).
	 * @param authManager an authentication and network layers driver
	 * @param options     a run-time configuration options driver
	 * @from org.tmatesoft.svn.core.wc.SVNWCClient
	 */
	public SVNWCClient16(ISVNAuthenticationManager authManager,
			ISVNOptions options) {
		super(authManager, options);
	}

	/** 
	 * Schedules a working copy <code>path</code> for addition to the repository.
	 * <p/>
	 * If <code>depth</code> is {@link SVNDepth#EMPTY}, adds just <code>path</code> and nothing
	 * below it. If {@link SVNDepth#FILES}, adds <code>path</code> and any file
	 * children of <code>path</code>. If {@link SVNDepth#IMMEDIATES}, adds <code>path</code>, any
	 * file children, and any immediate subdirectories (but nothing
	 * underneath those subdirectories). If {@link SVNDepth#INFINITY}, adds
	 * <code>path</code> and everything under it fully recursively.
	 * <p/>
	 * <code>path</code>'s parent must be under revision control already (unless
	 * <code>makeParents</code> is <span class="javakeyword">true</span>), but <code>path</code> is not.  
	 * <p/>
	 * If <code>force</code> is set, <code>path</code> is a directory, <code>depth</code> is {@link SVNDepth#INFINITY}, then schedules for addition unversioned files and directories
	 * scattered deep within a versioned tree.
	 * <p/>
	 * If <code>includeIgnored</code> is <span class="javakeyword">false</span>, doesn't add files or 
	 * directories that match ignore patterns.
	 * <p/>
	 * If <code>makeParents</code> is <span class="javakeyword">true</span>, recurse up <code>path</code>'s 
	 * directory and look for a versioned directory. If found, add all intermediate paths between it
	 * and <code>path</code>. 
	 * <p/>
	 * Important: this is a *scheduling* operation.  No changes will happen to the repository until a commit 
	 * occurs. This scheduling can be removed with a call to {@link #doRevert(File[],SVNDepth,Collection)}.
	 * @param path                      working copy path
	 * @param force                     if <span class="javakeyword">true</span>, this method does not throw exceptions 
	 * on already-versioned items 
	 * @param mkdir                     if <span class="javakeyword">true</span>, create a directory also at <code>path</code>
	 * @param climbUnversionedParents   not used; make use of <code>makeParents</code> instead
	 * @param depth                     tree depth
	 * @param includeIgnored            if <span class="javakeyword">true</span>, does not apply ignore patterns 
	 * to paths being added
	 * @param makeParents               if <span class="javakeyword">true</span>, climb upper and schedule also
	 * all unversioned paths in the way
	 * @throws SVNException             <ul>
	 * <li/>exception with {@link SVNErrorCode#ENTRY_EXISTS} error code -  
	 * if <code>force</code> is not set and <code>path</code> is already 
	 * under version
	 * <li/>exception with {@link SVNErrorCode#CLIENT_NO_VERSIONED_PARENT} 
	 * error code - if <code>makeParents</code> is 
	 * <span class="javakeyword">true</span> but no unversioned paths stepping 
	 * upper from <code>path</code> are found 
	 * @since 1.2, SVN 1.5
	 * @from org.tmatesoft.svn.core.wc.SVNWCClient
	 */
	public void doAdd(File path, boolean force, boolean mkdir,
			boolean climbUnversionedParents, SVNDepth depth,
			boolean includeIgnored, boolean makeParents) throws SVNException {
		doAdd(path, force, mkdir, climbUnversionedParents, depth, false,
				includeIgnored, makeParents);
	}

	/** 
	 * Invokes <code>handler</code> to return information
	 * about <code>url</code> in <code>revision</code>. The information returned is
	 * system-generated metadata, not the sort of "property" metadata
	 * created by users. See {@link SVNInfo}.
	 * <p/>
	 * If <code>revision</code> argument is either <span class="javakeyword">null</span> or {@link SVNRevision#isValid() invalid}, it defaults to {@link SVNRevision#HEAD}.
	 * If <code>revision</code> is {@link SVNRevision#PREVIOUS} (or some other kind that requires
	 * a local path), an error will be returned, because the desired
	 * revision cannot be determined.
	 * If <code>pegRevision</code> argument is either <span class="javakeyword">null</span> or {@link SVNRevision#isValid() invalid}, it defaults to <code>revision</code>.
	 * <p/>
	 * Information will be pulled from the repository. The actual node revision selected is determined by 
	 * the <code>url</code> as it exists in <code>pegRevision</code>. If <code>pegRevision</code> is {@link SVNRevision#UNDEFINED}, then it defaults to {@link SVNRevision#WORKING}.
	 * <p/>
	 * If <code>url</code> is a file, just invokes <code>handler</code> on it. If it
	 * is a directory, then descends according to <code>depth</code>. If <code>depth</code> is{@link SVNDepth#EMPTY}, invokes <code>handler</code> on <code>url</code> and
	 * nothing else; if {@link SVNDepth#FILES}, on <code>url</code> and its
	 * immediate file children; if {@link SVNDepth#IMMEDIATES}, the preceding
	 * plus on each immediate subdirectory; if {@link SVNDepth#INFINITY}, then
	 * recurses fully, invoking <code>handler</code> on <code>url</code> and
	 * everything beneath it.
	 * @param url            versioned item url
	 * @param pegRevision    revision in which <code>path</code> is first
	 * looked up
	 * @param revision       target revision
	 * @param depth          tree depth
	 * @param handler        caller's info handler
	 * @throws SVNException 
	 * @since 1.2, SVN 1.5
	 * @from org.tmatesoft.svn.core.wc.SVNWCClient
	 */
	public void doInfo(SVNURL url, SVNRevision pegRevision,
			SVNRevision revision, SVNDepth depth, ISVNInfoHandler handler)
			throws SVNException {
		depth = depth == null ? SVNDepth.UNKNOWN : depth;
		long[] revNum = { SVNRepository.INVALID_REVISION };
		SVNRepository repos = createRepository(url, null, null, pegRevision,
				revision, revNum);
		url = repos.getLocation();
		SVNDirEntry rootEntry = null;
		SVNURL reposRoot = repos.getRepositoryRoot(true);
		String reposUUID = repos.getRepositoryUUID(true);
		String baseName = SVNPathUtil.tail(url.getPath());
		try {
			rootEntry = repos.info("", revNum[0]);
		} catch (SVNException e) {
			if (e.getErrorMessage() != null
					&& e.getErrorMessage().getErrorCode() == SVNErrorCode.RA_NOT_IMPLEMENTED) {
				if (url.equals(reposRoot)) {
					if (depth.compareTo(SVNDepth.EMPTY) > 0) {
						SVNLock[] locks = null;
						if (pegRevision == SVNRevision.HEAD) {
							try {
								locks = repos.getLocks("");
							} catch (SVNException svne) {
								SVNErrorCode code = svne.getErrorMessage()
										.getErrorCode();
								if (code == SVNErrorCode.RA_NOT_IMPLEMENTED
										|| code == SVNErrorCode.UNSUPPORTED_FEATURE) {
									locks = new SVNLock[0];
								} else {
									throw svne;
								}
							}
						} else {
							locks = new SVNLock[0];
						}
						locks = locks == null ? new SVNLock[0] : locks;
						Map locksMap = new SVNHashMap();
						for (int i = 0; i < locks.length; i++) {
							SVNLock lock = locks[i];
							locksMap.put(lock.getPath(), lock);
						}
						pushDirInfo(repos, SVNRevision.create(revNum[0]), "",
								repos.getRepositoryRoot(true), reposUUID, url,
								locksMap, depth, handler);
						return;
					}
					SVNErrorMessage err = SVNErrorMessage
							.create(SVNErrorCode.UNSUPPORTED_FEATURE,
									"Server does not support retrieving information about the repository root");
					SVNErrorManager.error(err, SVNLogType.WC);
				}
				SVNNodeKind urlKind = repos.checkPath("", revNum[0]);
				if (urlKind == SVNNodeKind.NONE) {
					SVNErrorMessage err = SVNErrorMessage.create(
							SVNErrorCode.RA_ILLEGAL_URL,
							"URL ''{0}'' non-existent in revision {1}",
							new Object[] { url, new Long(revNum[0]) });
					SVNErrorManager.error(err, SVNLogType.WC);
				}
				SVNRepository parentRepos = createRepository(url
						.removePathTail(), null, null, false);
				Collection dirEntries = parentRepos.getDir("", revNum[0], null,
						SVNDirEntry.DIRENT_KIND
								| SVNDirEntry.DIRENT_CREATED_REVISION
								| SVNDirEntry.DIRENT_TIME
								| SVNDirEntry.DIRENT_LAST_AUTHOR,
						(Collection) null);
				for (Iterator ents = dirEntries.iterator(); ents.hasNext();) {
					SVNDirEntry dirEntry = (SVNDirEntry) ents.next();
					if (baseName.equals(dirEntry.getName())) {
						rootEntry = dirEntry;
						break;
					}
				}
				if (rootEntry == null) {
					SVNErrorMessage err = SVNErrorMessage.create(
							SVNErrorCode.RA_ILLEGAL_URL,
							"URL ''{0}'' non-existent in revision {1}",
							new Object[] { url, new Long(revNum[0]) });
					SVNErrorManager.error(err, SVNLogType.WC);
				}
			} else {
				throw e;
			}
		}
		if (rootEntry == null || rootEntry.getKind() == SVNNodeKind.NONE) {
			SVNErrorMessage err = SVNErrorMessage.create(
					SVNErrorCode.RA_ILLEGAL_URL,
					"URL ''{0}'' non-existent in revision ''{1}''",
					new Object[] { url, new Long(revNum[0]) });
			SVNErrorManager.error(err, SVNLogType.WC);
		}
		SVNLock lock = null;
		if (rootEntry.getKind() == SVNNodeKind.FILE) {
			try {
				SVNRepositoryLocation[] locations = getLocations(url, null,
						null, SVNRevision.create(revNum[0]), SVNRevision.HEAD,
						SVNRevision.UNDEFINED);
				if (locations != null && locations.length > 0) {
					SVNURL headURL = locations[0].getURL();
					if (headURL.equals(url)) {
						try {
							lock = repos.getLock("");
						} catch (SVNException e) {
							if (!(e.getErrorMessage() != null && e
									.getErrorMessage().getErrorCode() == SVNErrorCode.RA_NOT_IMPLEMENTED)) {
								throw e;
							}
						}
					}
				}
			} catch (SVNException e) {
				SVNErrorCode code = e.getErrorMessage().getErrorCode();
				if (code != SVNErrorCode.FS_NOT_FOUND
						&& code != SVNErrorCode.CLIENT_UNRELATED_RESOURCES) {
					throw e;
				}
			}
		}
		SVNInfo info = SVNInfo.createInfo(baseName, reposRoot, reposUUID, url,
				SVNRevision.create(revNum[0]), rootEntry, lock);
		handler.handleInfo(info);
		if (depth.compareTo(SVNDepth.EMPTY) > 0
				&& rootEntry.getKind() == SVNNodeKind.DIR) {
			SVNLock[] locks = null;
			if (pegRevision == SVNRevision.HEAD) {
				try {
					locks = repos.getLocks("");
				} catch (SVNException svne) {
					SVNErrorCode code = svne.getErrorMessage().getErrorCode();
					if (code == SVNErrorCode.RA_NOT_IMPLEMENTED
							|| code == SVNErrorCode.UNSUPPORTED_FEATURE) {
						locks = new SVNLock[0];
					} else {
						throw svne;
					}
				}
			} else {
				locks = new SVNLock[0];
			}
			locks = locks == null ? new SVNLock[0] : locks;
			Map locksMap = new SVNHashMap();
			for (int i = 0; i < locks.length; i++) {
				lock = locks[i];
				locksMap.put(lock.getPath(), lock);
			}
			pushDirInfo(repos, SVNRevision.create(revNum[0]), "", repos
					.getRepositoryRoot(true), reposUUID, url, locksMap, depth,
					handler);
		}
	}

	/** 
	 * Invokes <code>handler</code> on paths covered by <code>depth</code> starting with 
	 * the specified <code>path</code>.
	 * <p/>
	 * If both <vode>revision</code> and <code>pegRevision</code> are ones of:
	 * <ul>
	 * <li>{@link SVNRevision#BASE BASE}<li>{@link SVNRevision#WORKING WORKING}<li>{@link SVNRevision#COMMITTED COMMITTED}<li>{@link SVNRevision#UNDEFINED}</ul>
	 * then this method gets properties from the working copy without connecting to the repository. 
	 * Otherwise properties are taken from the repository (using the item's URL).
	 * <p/>
	 * The actual node revision selected is determined by the path as it exists in <code>pegRevision</code>.
	 * If <code>pegRevision</code> is {@link SVNRevision#UNDEFINED}, then it defaults to {@link SVNRevision#WORKING}.
	 * <p/>
	 * If <code>depth</code> is {@link SVNDepth#EMPTY}, fetch the property from <code>path</code> only; 
	 * if {@link SVNDepth#FILES}, fetch from <code>path</code> and its file children (if any); 
	 * if {@link SVNDepth#IMMEDIATES}, from <code>path</code> and all of its immediate children (both files and 
	 * directories); if {@link SVNDepth#INFINITY}, from <code>path</code> and everything beneath it.
	 * <p/>
	 * <code>changeLists</code> is a collection of <code>String</tt> changelist
	 * names, used as a restrictive filter on items whose properties are
	 * set; that is, don't set properties on any item unless it's a member
	 * of one of those changelists.  If <code>changeLists</code> is empty (or
	 * <span class="javakeyword">null</span>), no changelist filtering occurs.
	 * @param path          a WC item's path
	 * @param propName      an item's property name; if it's
	 * <span class="javakeyword">null</span> then
	 * all the item's properties will be retrieved
	 * and passed to <code>handler</code> for
	 * processing
	 * @param pegRevision   a revision in which the item is first looked up
	 * @param revision      a target revision
	 * @param depth         tree depth 
	 * @param handler       a caller's property handler
	 * @param changeLists   collection of changelist names
	 * @throws SVNException if one of the following is true:
	 * <ul>
	 * <li><code>propName</code> starts
	 * with the {@link org.tmatesoft.svn.core.SVNProperty#SVN_WC_PREFIX} prefix
	 * <li><code>path</code> is not under version control
	 * </ul>
	 * @since               1.2, SVN 1.5
	 * @from org.tmatesoft.svn.core.wc.SVNWCClient
	 */
	public void doGetProperty(File path, String propName,
			SVNRevision pegRevision, SVNRevision revision, SVNDepth depth,
			ISVNPropertyHandler handler, Collection changeLists)
			throws SVNException {
		if (SVNProperty.isWorkingCopyProperty(propName)) {
			SVNErrorMessage err = SVNErrorMessage.create(
					SVNErrorCode.CLIENT_PROPERTY_NAME,
					"''{0}'' is a wcprop, thus not accessible to clients",
					propName);
			SVNErrorManager.error(err, SVNLogType.WC);
		}
		if (SVNProperty.isEntryProperty(propName)) {
			SVNErrorMessage err = SVNErrorMessage.create(
					SVNErrorCode.CLIENT_PROPERTY_NAME,
					"Property ''{0}'' is an entry property", propName);
			SVNErrorManager.error(err, SVNLogType.WC);
		}
		if (depth == null || depth == SVNDepth.UNKNOWN) {
			depth = SVNDepth.EMPTY;
		}
		if ((revision != SVNRevision.WORKING && revision != SVNRevision.BASE
				&& revision != SVNRevision.COMMITTED && revision != SVNRevision.UNDEFINED)
				|| (pegRevision != SVNRevision.WORKING
						&& pegRevision != SVNRevision.BASE
						&& pegRevision != SVNRevision.COMMITTED && pegRevision != SVNRevision.UNDEFINED)) {
			long[] revNum = { SVNRepository.INVALID_REVISION };
			SVNRepository repository = createRepository(null, path, null,
					pegRevision, revision, revNum);
			revision = SVNRevision.create(revNum[0]);
			doGetRemoteProperty(repository.getLocation(), "", repository,
					propName, revision, depth, handler);
		} else {
			SVNWCAccess wcAccess = createWCAccess();
			try {
				int admDepth = getLevelsToLockFromDepth(depth);
				SVNAdminArea area = wcAccess.probeOpen(path, false, admDepth);
				SVNEntry entry = wcAccess.getVersionedEntry(path, false);
				boolean base = revision == SVNRevision.BASE
						|| revision == SVNRevision.COMMITTED;
				doGetLocalProperty(entry, area, propName, base, handler, depth,
						changeLists);
			} finally {
				wcAccess.close();
			}
		}
	}

	/** 
	 * Recursively removes all DAV-specific <span class="javakeyword">"svn:wc:"</span> properties
	 * from the <code>directory</code> and beneath. 
	 * <p>
	 * This method does not connect to a repository, it's a local operation only. Nor does it change any user's 
	 * versioned data. Changes are made only in administrative version control files.
	 * @param directory     working copy path
	 * @throws SVNException
	 * @since  1.2
	 * @from org.tmatesoft.svn.core.wc.SVNWCClient
	 */
	public void doCleanupWCProperties(File directory) throws SVNException {
		SVNWCAccess wcAccess = SVNWCAccess.newInstance(this);
		try {
			SVNAdminArea dir = wcAccess.open(directory, true, true, -1);
			if (dir != null) {
				SVNPropertiesManager.deleteWCProperties(dir, null, true);
			}
		} finally {
			wcAccess.close();
		}
	}

	/** 
	 * @from org.tmatesoft.svn.core.wc.SVNWCClient
	 */
	public boolean revert(SVNAdminArea dir, String name, SVNEntry entry,
			boolean useCommitTime) throws SVNException {
		SVNLog log = dir.getLog();
		boolean reverted = false;
		SVNVersionedProperties baseProperties = null;
		SVNProperties command = new SVNProperties();
		boolean revertBase = false;
		if (entry.isScheduledForReplacement()) {
			revertBase = true;
			baseProperties = dir.getRevertProperties(name);
			String propRevertPath = SVNAdminUtil.getPropRevertPath(name, entry
					.getKind(), false);
			command.put(SVNLog.NAME_ATTR, propRevertPath);
			log.addCommand(SVNLog.DELETE, command, false);
			command.clear();
			reverted = true;
		}
		boolean reinstallWorkingFile = false;
		if (baseProperties == null) {
			if (dir.hasPropModifications(name)) {
				baseProperties = dir.getBaseProperties(name);
				SVNVersionedProperties propDiff = dir.getProperties(name)
						.compareTo(baseProperties);
				Collection propNames = propDiff.getPropertyNames(null);
				reinstallWorkingFile = propNames
						.contains(SVNProperty.EXECUTABLE)
						|| propNames.contains(SVNProperty.KEYWORDS)
						|| propNames.contains(SVNProperty.EOL_STYLE)
						|| propNames.contains(SVNProperty.CHARSET)
						|| propNames.contains(SVNProperty.SPECIAL)
						|| propNames.contains(SVNProperty.NEEDS_LOCK);
			}
		}
		if (baseProperties != null) {
			SVNProperties newProperties = baseProperties.asMap();
			SVNVersionedProperties originalBaseProperties = dir
					.getBaseProperties(name);
			SVNVersionedProperties workProperties = dir.getProperties(name);
			if (revertBase) {
				originalBaseProperties.removeAll();
			}
			workProperties.removeAll();
			for (Iterator names = newProperties.nameSet().iterator(); names
					.hasNext();) {
				String propName = (String) names.next();
				if (revertBase) {
					originalBaseProperties.setPropertyValue(propName,
							newProperties.getSVNPropertyValue(propName));
				}
				workProperties.setPropertyValue(propName, newProperties
						.getSVNPropertyValue(propName));
			}
			dir.saveVersionedProperties(log, false);
			reverted = true;
		}
		SVNProperties newEntryProperties = new SVNProperties();
		if (entry.getKind() == SVNNodeKind.FILE) {
			String basePath = SVNAdminUtil.getTextBasePath(name, false);
			String revertBasePath = SVNAdminUtil.getTextRevertPath(name, false);
			if (!reinstallWorkingFile) {
				SVNFileType fileType = SVNFileType.getType(dir.getFile(name));
				if (fileType == SVNFileType.NONE) {
					reinstallWorkingFile = true;
				}
			}
			if (dir.getFile(revertBasePath).isFile()) {
				reinstallWorkingFile = true;
			} else {
				if (!dir.getFile(basePath).isFile()) {
					SVNErrorMessage err = SVNErrorMessage.create(
							SVNErrorCode.IO_ERROR,
							"Error restoring text for ''{0}''", dir
									.getFile(name));
					SVNErrorManager.error(err, SVNLogType.WC);
				}
				revertBasePath = null;
			}
			if (revertBasePath != null) {
				command.put(SVNLog.NAME_ATTR, revertBasePath);
				command.put(SVNLog.DEST_ATTR, name);
				log.addCommand(SVNLog.COPY_AND_TRANSLATE, command, false);
				command.clear();
				command.put(SVNLog.NAME_ATTR, revertBasePath);
				command.put(SVNLog.DEST_ATTR, basePath);
				log.addCommand(SVNLog.MOVE, command, false);
				reverted = true;
			} else {
				if (!reinstallWorkingFile) {
					reinstallWorkingFile = dir.hasTextModifications(name,
							false, false, false);
				}
				if (reinstallWorkingFile) {
					command.put(SVNLog.NAME_ATTR, SVNAdminUtil.getTextBasePath(
							name, false));
					command.put(SVNLog.DEST_ATTR, name);
					log.addCommand(SVNLog.COPY_AND_TRANSLATE, command, false);
					command.clear();
					if (useCommitTime && entry.getCommittedDate() != null) {
						command.put(SVNLog.NAME_ATTR, name);
						command.put(SVNLog.TIMESTAMP_ATTR, entry
								.getCommittedDate());
						log.addCommand(SVNLog.SET_TIMESTAMP, command, false);
						command.clear();
					} else {
						command.put(SVNLog.NAME_ATTR, name);
						command.put(SVNLog.TIMESTAMP_ATTR,
								SVNDate.formatDate(new Date(System
										.currentTimeMillis())));
						log.addCommand(SVNLog.SET_TIMESTAMP, command, false);
						command.clear();
					}
					command.put(SVNLog.NAME_ATTR, name);
					command.put(SVNProperty
							.shortPropertyName(SVNProperty.TEXT_TIME),
							SVNLog.WC_TIMESTAMP);
					log.addCommand(SVNLog.MODIFY_ENTRY, command, false);
					command.clear();
					command.put(SVNLog.NAME_ATTR, name);
					command.put(SVNProperty
							.shortPropertyName(SVNProperty.WORKING_SIZE),
							SVNLog.WC_WORKING_SIZE);
					log.addCommand(SVNLog.MODIFY_ENTRY, command, false);
					command.clear();
				}
				reverted |= reinstallWorkingFile;
			}
		}
		if (entry.getConflictNew() != null) {
			command.put(SVNLog.NAME_ATTR, entry.getConflictNew());
			log.addCommand(SVNLog.DELETE, command, false);
			command.clear();
			newEntryProperties
					.put(SVNProperty
							.shortPropertyName(SVNProperty.CONFLICT_NEW),
							(String) null);
			if (!reverted) {
				reverted |= dir.getFile(entry.getConflictNew()).exists();
			}
		}
		if (entry.getConflictOld() != null) {
			command.put(SVNLog.NAME_ATTR, entry.getConflictOld());
			log.addCommand(SVNLog.DELETE, command, false);
			command.clear();
			newEntryProperties
					.put(SVNProperty
							.shortPropertyName(SVNProperty.CONFLICT_OLD),
							(String) null);
			if (!reverted) {
				reverted |= dir.getFile(entry.getConflictOld()).exists();
			}
		}
		if (entry.getConflictWorking() != null) {
			command.put(SVNLog.NAME_ATTR, entry.getConflictWorking());
			log.addCommand(SVNLog.DELETE, command, false);
			command.clear();
			newEntryProperties
					.put(SVNProperty
							.shortPropertyName(SVNProperty.CONFLICT_WRK),
							(String) null);
			if (!reverted) {
				reverted |= dir.getFile(entry.getConflictWorking()).exists();
			}
		}
		if (entry.getPropRejectFile() != null) {
			command.put(SVNLog.NAME_ATTR, entry.getPropRejectFile());
			log.addCommand(SVNLog.DELETE, command, false);
			command.clear();
			newEntryProperties.put(SVNProperty
					.shortPropertyName(SVNProperty.PROP_REJECT_FILE),
					(String) null);
			if (!reverted) {
				reverted |= dir.getFile(entry.getPropRejectFile()).exists();
			}
		}
		if (entry.isScheduledForReplacement()) {
			newEntryProperties.put(SVNProperty
					.shortPropertyName(SVNProperty.COPIED), SVNProperty
					.toString(false));
			newEntryProperties
					.put(SVNProperty
							.shortPropertyName(SVNProperty.COPYFROM_URL),
							(String) null);
			newEntryProperties.put(SVNProperty
					.shortPropertyName(SVNProperty.COPYFROM_REVISION),
					SVNProperty.toString(SVNRepository.INVALID_REVISION));
			if (entry.isFile() && entry.getCopyFromURL() != null) {
				String basePath = SVNAdminUtil.getTextRevertPath(name, false);
				File baseFile = dir.getFile(basePath);
				String digest = SVNFileUtil.computeChecksum(baseFile);
				newEntryProperties.put(SVNProperty
						.shortPropertyName(SVNProperty.CHECKSUM), digest);
			}
		}
		if (entry.getSchedule() != null) {
			newEntryProperties.put(SVNProperty
					.shortPropertyName(SVNProperty.SCHEDULE), (String) null);
			reverted = true;
		}
		if (!newEntryProperties.isEmpty()) {
			newEntryProperties.put(SVNLog.NAME_ATTR, name);
			log.addCommand(SVNLog.MODIFY_ENTRY, newEntryProperties, false);
		}
		log.save();
		dir.runLogs();
		return reverted;
	}

	/** 
	 * Changes working copy format. This method may be used to upgrade\downgrade working copy formats.
	 * <p>
	 * If externals are not {@link SVNBasicClient#isIgnoreExternals() ignored} then external working copies 
	 * are also converted to the new working copy <code>format</code>.
	 * <p>
	 * This method does not connect to a repository, it's a local operation only. Nor does it change any user's 
	 * versioned data. Changes are made only in administrative version control files.
	 * @param directory    working copy directory
	 * @param format       format to set, supported formats are: 9 (SVN 1.5), 8 (SVN 1.4) and 4 (SVN 1.2)
	 * @throws SVNException 
	 * @since  1.2
	 * @from org.tmatesoft.svn.core.wc.SVNWCClient
	 */
	public void doSetWCFormat(File directory, int format) throws SVNException {
		SVNAdminAreaInfo info = null;
		SVNWCAccess wcAccess = SVNWCAccess.newInstance(this);
		try {
			info = wcAccess.openAnchor(directory, false, -1);
			setWCFormat(info, info.getTarget(), format);
		} finally {
			wcAccess.close();
		}
		if (!isIgnoreExternals() && info != null) {
			Collection processedDirs = new SVNHashSet();
			Map externals = info.getOldExternals();
			for (Iterator paths = externals.keySet().iterator(); paths
					.hasNext();) {
				String path = (String) paths.next();
				String value = (String) externals.get(path);
				if (value == null) {
					continue;
				}
				SVNExternal[] externalDefs = SVNExternal.parseExternals("",
						value);
				for (int i = 0; i < externalDefs.length; i++) {
					String externalPath = externalDefs[i].getPath();
					File externalDir = new File(info.getAnchor().getRoot(),
							SVNPathUtil.append(path, externalPath));
					if (processedDirs.add(externalDir)) {
						try {
							wcAccess.open(externalDir, false, 0);
						} catch (SVNException svne) {
							if (svne.getErrorMessage().getErrorCode() == SVNErrorCode.WC_NOT_DIRECTORY) {
								continue;
							}
							throw svne;
						} finally {
							wcAccess.close();
						}
						try {
							doSetWCFormat(externalDir, format);
						} catch (SVNException e) {
							if (e.getErrorMessage().getErrorCode() == SVNErrorCode.WC_NOT_DIRECTORY) {
								continue;
							}
							throw e;
						}
					}
				}
			}
			externals = info.getNewExternals();
			for (Iterator paths = externals.keySet().iterator(); paths
					.hasNext();) {
				String path = (String) paths.next();
				String value = (String) externals.get(path);
				SVNExternal[] externalDefs = SVNExternal.parseExternals("",
						value);
				for (int i = 0; i < externalDefs.length; i++) {
					String externalPath = externalDefs[i].getPath();
					File externalDir = new File(info.getAnchor().getRoot(),
							SVNPathUtil.append(path, externalPath));
					if (processedDirs.add(externalDir)) {
						try {
							wcAccess.open(externalDir, false, 0);
						} catch (SVNException svne) {
							if (svne.getErrorMessage().getErrorCode() == SVNErrorCode.WC_NOT_DIRECTORY) {
								continue;
							}
							throw svne;
						} finally {
							wcAccess.close();
						}
						try {
							doSetWCFormat(externalDir, format);
						} catch (SVNException e) {
							if (e.getErrorMessage().getErrorCode() == SVNErrorCode.WC_NOT_DIRECTORY) {
								continue;
							}
							throw e;
						}
					}
				}
			}
		}
	}

	/** 
	 * @from org.tmatesoft.svn.core.wc.SVNWCClient
	 */
	public void pushDirInfo(SVNRepository repos, SVNRevision rev, String path,
			SVNURL root, String uuid, SVNURL url, Map locks, SVNDepth depth,
			ISVNInfoHandler handler) throws SVNException {
		Collection children = repos.getDir(path, rev.getNumber(), null,
				SVNDirEntry.DIRENT_KIND | SVNDirEntry.DIRENT_CREATED_REVISION
						| SVNDirEntry.DIRENT_TIME
						| SVNDirEntry.DIRENT_LAST_AUTHOR, new ArrayList());
		for (Iterator ents = children.iterator(); ents.hasNext();) {
			checkCancelled();
			SVNDirEntry child = (SVNDirEntry) ents.next();
			SVNURL childURL = url.appendPath(child.getName(), false);
			String childPath = SVNPathUtil.append(path, child.getName());
			String displayPath = repos.getFullPath(childPath);
			displayPath = displayPath.substring(repos.getLocation().getPath()
					.length());
			if (displayPath.startsWith("/")) {
				displayPath = displayPath.substring(1);
			}
			if ("".equals(displayPath)) {
				displayPath = path;
			}
			SVNLock lock = (SVNLock) locks.get(path);
			SVNInfo info = SVNInfo.createInfo(displayPath, root, uuid, url,
					rev, child, lock);
			if (depth.compareTo(SVNDepth.IMMEDIATES) >= 0
					|| (depth == SVNDepth.FILES && child.getKind() == SVNNodeKind.FILE)) {
				handler.handleInfo(info);
			}
			if (depth == SVNDepth.INFINITY
					&& child.getKind() == SVNNodeKind.DIR) {
				pushDirInfo(repos, rev, SVNPathUtil.append(path, child
						.getName()), root, uuid, childURL, locks, depth,
						handler);
			}
		}
	}

	/** 
	 * Schedules a working copy <code>path</code> for addition to the repository.
	 * <p/>
	 * If <code>depth</code> is {@link SVNDepth#EMPTY}, adds just <code>path</code> and nothing
	 * below it. If {@link SVNDepth#FILES}, adds <code>path</code> and any file
	 * children of <code>path</code>. If {@link SVNDepth#IMMEDIATES}, adds <code>path</code>, any
	 * file children, and any immediate subdirectories (but nothing
	 * underneath those subdirectories). If {@link SVNDepth#INFINITY}, adds
	 * <code>path</code> and everything under it fully recursively.
	 * <p/>
	 * <code>path</code>'s parent must be under revision control already (unless
	 * <code>makeParents</code> is <span class="javakeyword">true</span>), but <code>path</code> is not.  
	 * <p/>
	 * If <code>force</code> is set, <code>path</code> is a directory, <code>depth</code> is {@link SVNDepth#INFINITY}, then schedules for addition unversioned files and directories
	 * scattered deep within a versioned tree.
	 * <p/>
	 * If <code>includeIgnored</code> is <span class="javakeyword">false</span>, doesn't add files or 
	 * directories that match ignore patterns.
	 * <p/>
	 * If <code>makeParents</code> is <span class="javakeyword">true</span>, recurse up <code>path</code>'s 
	 * directory and look for a versioned directory. If found, add all intermediate paths between it
	 * and <code>path</code>. 
	 * <p/>
	 * Important: this is a *scheduling* operation.  No changes will happen to the repository until a commit 
	 * occurs. This scheduling can be removed with a call to {@link #doRevert(File[],SVNDepth,Collection)}.
	 * @param path                      working copy path
	 * @param force                     if <span class="javakeyword">true</span>, this method does not throw exceptions on already-versioned items 
	 * @param mkdir                     if <span class="javakeyword">true</span>, create a directory also at <code>path</code>
	 * @param climbUnversionedParents   not used; make use of <code>makeParents</code> instead
	 * @param depth                     tree depth
	 * @param depthIsSticky             if depth should be recorded to the working copy
	 * @param includeIgnored            if <span class="javakeyword">true</span>, does not apply ignore patterns 
	 * to paths being added
	 * @param makeParents               if <span class="javakeyword">true</span>, climb upper and schedule also
	 * all unversioned paths in the way
	 * @throws SVNException             <ul>
	 * <li/>exception with {@link SVNErrorCode#ENTRY_EXISTS} error code -  
	 * if <code>force</code> is not set and <code>path</code> is already 
	 * under version
	 * <li/>exception with {@link SVNErrorCode#CLIENT_NO_VERSIONED_PARENT} 
	 * error code - if <code>makeParents</code> is 
	 * <span class="javakeyword">true</span> but no unversioned paths stepping 
	 * upper from <code>path</code> are found 
	 * @since 1.3
	 * @from org.tmatesoft.svn.core.wc.SVNWCClient
	 */
	public void doAdd(File path, boolean force, boolean mkdir,
			boolean climbUnversionedParents, SVNDepth depth,
			boolean depthIsSticky, boolean includeIgnored, boolean makeParents)
			throws SVNException {
		depth = depth == null ? SVNDepth.UNKNOWN : depth;
		path = path.getAbsoluteFile();
		if (!mkdir && makeParents && path.getParentFile() != null) {
			SVNWCAccess wcAccess = createWCAccess();
			try {
				addParentDirectories(wcAccess, path.getParentFile());
			} finally {
				wcAccess.close();
			}
		}
		SVNFileType kind = SVNFileType.getType(path);
		if (force && mkdir && kind == SVNFileType.DIRECTORY) {
			doAdd(path, force, false, true, SVNDepth.EMPTY, depthIsSticky,
					true, makeParents);
			return;
		} else if (mkdir) {
			File parent = path;
			File firstCreated = path;
			while (parent != null
					&& SVNFileType.getType(parent) == SVNFileType.NONE) {
				if (!parent.equals(path) && !makeParents) {
					SVNErrorMessage err = SVNErrorMessage
							.create(
									SVNErrorCode.IO_ERROR,
									"Cannot create directoy ''{0}'' with non-existent parents",
									path);
					SVNErrorManager.error(err, SVNLogType.WC);
				}
				firstCreated = parent;
				parent = parent.getParentFile();
			}
			boolean created = path.mkdirs();
			if (!created) {
				SVNErrorMessage err = SVNErrorMessage.create(
						SVNErrorCode.IO_ERROR,
						"Cannot create new directory ''{0}''", path);
				while (parent == null ? path != null : !path.equals(parent)) {
					SVNFileUtil.deleteAll(path, true);
					path = path.getParentFile();
				}
				SVNErrorManager.error(err, SVNLogType.WC);
			}
			try {
				doAdd(firstCreated, false, false, climbUnversionedParents,
						depth, depthIsSticky, true, makeParents);
			} catch (SVNException e) {
				SVNFileUtil.deleteAll(firstCreated, true);
				throw e;
			}
			return;
		}
		SVNWCAccess wcAccess = createWCAccess();
		try {
			SVNAdminArea dir = null;
			SVNFileType fileType = SVNFileType.getType(path);
			if (fileType == SVNFileType.DIRECTORY) {
				dir = wcAccess.open(SVNWCUtil16.isVersionedDirectory(path
						.getParentFile()) ? path.getParentFile() : path, true,
						0);
			} else {
				dir = wcAccess.open(path.getParentFile(), true, 0);
			}
			if (fileType == SVNFileType.DIRECTORY
					&& depth.compareTo(SVNDepth.FILES) >= 0) {
				File wcRoot = SVNWCUtil16.getWorkingCopyRoot(dir.getRoot(),
						true);
				addDirectory(wcRoot, path, dir, force, includeIgnored, depth,
						depthIsSticky);
			} else if (fileType == SVNFileType.FILE
					|| fileType == SVNFileType.SYMLINK) {
				addFile(path, fileType, dir);
			} else {
				SVNWCManager.add(path, dir, null, SVNRevision.UNDEFINED,
						depthIsSticky ? depth : null);
			}
		} catch (SVNException e) {
			if (!(force && e.getErrorMessage().getErrorCode() == SVNErrorCode.ENTRY_EXISTS)) {
				throw e;
			}
		} finally {
			wcAccess.close();
		}
	}

	/** 
	 * @from org.tmatesoft.svn.core.wc.SVNWCClient
	 */
	public void doGetRemoteProperty(SVNURL url, String path,
			SVNRepository repos, String propName, SVNRevision rev,
			SVNDepth depth, ISVNPropertyHandler handler) throws SVNException {
		checkCancelled();
		long revNumber = getRevisionNumber(rev, repos, null);
		SVNNodeKind kind = repos.checkPath(path, revNumber);
		SVNProperties props = new SVNProperties();
		if (kind == SVNNodeKind.DIR) {
			Collection children = repos.getDir(path, revNumber, props,
					SVNDirEntry.DIRENT_KIND,
					SVNDepth.FILES.compareTo(depth) <= 0 ? new ArrayList()
							: null);
			if (propName != null) {
				SVNPropertyValue value = props.getSVNPropertyValue(propName);
				if (value != null) {
					handler.handleProperty(url, new SVNPropertyData(propName,
							value, getOptions()));
				}
			} else {
				for (Iterator names = props.nameSet().iterator(); names
						.hasNext();) {
					String name = (String) names.next();
					if (name.startsWith(SVNProperty.SVN_ENTRY_PREFIX)
							|| name.startsWith(SVNProperty.SVN_WC_PREFIX)) {
						continue;
					}
					SVNPropertyValue value = props.getSVNPropertyValue(name);
					handler.handleProperty(url, new SVNPropertyData(name,
							value, getOptions()));
				}
			}
			if (SVNDepth.FILES.compareTo(depth) <= 0) {
				checkCancelled();
				for (Iterator entries = children.iterator(); entries.hasNext();) {
					SVNDirEntry child = (SVNDirEntry) entries.next();
					SVNURL childURL = url.appendPath(child.getName(), false);
					String childPath = "".equals(path) ? child.getName()
							: SVNPathUtil.append(path, child.getName());
					SVNDepth depthBelowHere = depth;
					if (child.getKind() == SVNNodeKind.DIR
							&& depth == SVNDepth.FILES) {
						continue;
					}
					if (depth == SVNDepth.FILES || depth == SVNDepth.IMMEDIATES) {
						depthBelowHere = SVNDepth.EMPTY;
					}
					doGetRemoteProperty(childURL, childPath, repos, propName,
							rev, depthBelowHere, handler);
				}
			}
		} else if (kind == SVNNodeKind.FILE) {
			repos.getFile(path, revNumber, props, null);
			if (propName != null) {
				SVNPropertyValue value = props.getSVNPropertyValue(propName);
				if (value != null) {
					handler.handleProperty(url, new SVNPropertyData(propName,
							value, getOptions()));
				}
			} else {
				for (Iterator names = props.nameSet().iterator(); names
						.hasNext();) {
					String name = (String) names.next();
					if (name.startsWith(SVNProperty.SVN_ENTRY_PREFIX)
							|| name.startsWith(SVNProperty.SVN_WC_PREFIX)) {
						continue;
					}
					SVNPropertyValue value = props.getSVNPropertyValue(name);
					handler.handleProperty(url, new SVNPropertyData(name,
							value, getOptions()));
				}
			}
		} else if (kind == SVNNodeKind.NONE) {
			SVNErrorMessage err = SVNErrorMessage.create(
					SVNErrorCode.ENTRY_NOT_FOUND,
					"''{0}'' does not exist in revision {1}", new Object[] {
							path, String.valueOf(revNumber) });
			SVNErrorManager.error(err, SVNLogType.WC);
		} else {
			SVNErrorMessage err = SVNErrorMessage.create(
					SVNErrorCode.NODE_UNKNOWN_KIND,
					"Unknown node kind for ''{0}''", path);
			SVNErrorManager.error(err, SVNLogType.WC);
		}
	}

	/** 
	 * Invokes <code>handler</code> to return information
	 * about <code>path</code> in <code>revision</code>. The information returned is
	 * system-generated metadata, not the sort of "property" metadata
	 * created by users. See {@link SVNInfo}.
	 * <p/>
	 * If both revision arguments are either <span class="javakeyword">null</span> or {@link SVNRevision#isLocal() local}, or {@link SVNRevision#isValid() invalid}, then information 
	 * will be pulled solely from the working copy; no network connections will be
	 * made.
	 * <p/>
	 * Otherwise, information will be pulled from a repository. The
	 * actual node revision selected is determined by the <code>path</code>
	 * as it exists in <code>pegRevision</code>. If <code>pegRevision</code> is {@link SVNRevision#UNDEFINED}, then it defaults to {@link SVNRevision#WORKING}.
	 * <p/>
	 * If <code>path</code> is a file, just invokes <code>handler</code> on it. If it
	 * is a directory, then descends according to <code>depth</code>.  If <code>depth</code> is{@link SVNDepth#EMPTY}, invokes <code>handler</code> on <code>path</code> and
	 * nothing else; if {@link SVNDepth#FILES}, on <code>path</code> and its
	 * immediate file children; if {@link SVNDepth#IMMEDIATES}, the preceding
	 * plus on each immediate subdirectory; if {@link SVNDepth#INFINITY}, then
	 * recurses fully, invoking <code>handler</code> on <code>path</code> and
	 * everything beneath it.
	 * <p/>
	 * <code>changeLists</code> is a collection of <code>String</code> changelist
	 * names, used as a restrictive filter on items whose info is
	 * reported; that is, doesn't report info about any item unless
	 * it's a member of one of those changelists.  If <code>changeLists</code> is
	 * empty (or <span class="javakeyword">null</span>), no changelist filtering occurs.
	 * @param path           a WC item on which info should be obtained
	 * @param pegRevision    a revision in which <code>path</code> is first
	 * looked up
	 * @param revision       a target revision
	 * @param depth          tree depth
	 * @param changeLists    collection changelist names
	 * @param handler        caller's info handler
	 * @throws SVNException 
	 * @since 1.2, SVN 1.5
	 * @from org.tmatesoft.svn.core.wc.SVNWCClient
	 */
	public void doInfo(File path, SVNRevision pegRevision,
			SVNRevision revision, SVNDepth depth, Collection changeLists,
			ISVNInfoHandler handler) throws SVNException {
		if (handler == null) {
			return;
		}
		boolean local = (revision == null || !revision.isValid() || revision
				.isLocal())
				&& (pegRevision == null || !pegRevision.isValid() || pegRevision
						.isLocal());
		if (!local) {
			SVNWCAccess wcAccess = createWCAccess();
			SVNRevision wcRevision = null;
			SVNURL url = null;
			try {
				wcAccess.probeOpen(path, false, 0);
				SVNEntry entry = wcAccess.getVersionedEntry(path, false);
				url = entry.getSVNURL();
				if (url == null) {
					SVNErrorMessage err = SVNErrorMessage.create(
							SVNErrorCode.ENTRY_MISSING_URL,
							"''{0}'' has no URL", path);
					SVNErrorManager.error(err, SVNLogType.WC);
				}
				wcRevision = SVNRevision.create(entry.getRevision());
			} finally {
				wcAccess.close();
			}
			doInfo(url, pegRevision == null || !pegRevision.isValid()
					|| pegRevision.isLocal() ? wcRevision : pegRevision,
					revision, depth, handler);
			return;
		}
		Collection changelistsSet = null;
		if (changeLists != null) {
			changelistsSet = new SVNHashSet();
			for (Iterator changeListsIter = changeLists.iterator(); changeListsIter
					.hasNext();) {
				String changeList = (String) changeListsIter.next();
				changelistsSet.add(changeList);
			}
		}
		crawlEntries(path, depth, changelistsSet, handler);
	}

	/** 
	 * @from org.tmatesoft.svn.core.wc.SVNWCClient
	 */
	public SVNURL collectLockInfo(SVNWCAccess wcAccess, File[] files,
			Map lockInfo, Map lockPaths, boolean lock, boolean stealLock)
			throws SVNException {
		String[] paths = new String[files.length];
		for (int i = 0; i < files.length; i++) {
			paths[i] = files[i].getAbsolutePath();
			paths[i] = paths[i].replace(File.separatorChar, '/');
		}
		Collection condencedPaths = new ArrayList();
		String commonParentPath = SVNPathUtil.condencePaths(paths,
				condencedPaths, false);
		if (condencedPaths.isEmpty()) {
			condencedPaths.add(SVNPathUtil.tail(commonParentPath));
			commonParentPath = SVNPathUtil.removeTail(commonParentPath);
		}
		if (commonParentPath == null || "".equals(commonParentPath)) {
			SVNErrorMessage err = SVNErrorMessage
					.create(SVNErrorCode.UNSUPPORTED_FEATURE,
							"No common parent found, unable to operate on dijoint arguments");
			SVNErrorManager.error(err, SVNLogType.WC);
		}
		paths = (String[]) condencedPaths.toArray(new String[condencedPaths
				.size()]);
		int depth = 0;
		for (int i = 0; i < paths.length; i++) {
			int segments = SVNPathUtil.getSegmentsCount(paths[i]);
			if (depth < segments) {
				depth = segments;
			}
		}
		wcAccess.probeOpen(new File(commonParentPath).getAbsoluteFile(), true,
				depth);
		for (int i = 0; i < paths.length; i++) {
			File file = new File(commonParentPath, paths[i]);
			SVNEntry entry = wcAccess.getVersionedEntry(file, false);
			if (entry.getURL() == null) {
				SVNErrorMessage err = SVNErrorMessage.create(
						SVNErrorCode.ENTRY_MISSING_URL, "''{0}'' has no URL",
						file);
				SVNErrorManager.error(err, SVNLogType.WC);
			}
			if (lock) {
				SVNRevision revision = stealLock ? SVNRevision.UNDEFINED
						: SVNRevision.create(entry.getRevision());
				lockInfo.put(entry.getSVNURL(), new LockInfo(file, revision));
			} else {
				if (!stealLock && entry.getLockToken() == null) {
					SVNErrorMessage err = SVNErrorMessage.create(
							SVNErrorCode.CLIENT_MISSING_LOCK_TOKEN,
							"''{0}'' is not locked in this working copy", file);
					SVNErrorManager.error(err, SVNLogType.WC);
				}
				lockInfo.put(entry.getSVNURL(), new LockInfo(file, entry
						.getLockToken()));
			}
		}
		checkCancelled();
		SVNURL[] urls = (SVNURL[]) lockInfo.keySet().toArray(
				new SVNURL[lockInfo.size()]);
		Collection urlPaths = new SVNHashSet();
		final SVNURL topURL = SVNURLUtil.condenceURLs(urls, urlPaths, false);
		if (urlPaths.isEmpty()) {
			urlPaths.add("");
		}
		if (topURL == null) {
			SVNErrorMessage err = SVNErrorMessage.create(
					SVNErrorCode.UNSUPPORTED_FEATURE,
					"Unable to lock/unlock across multiple repositories");
			SVNErrorManager.error(err, SVNLogType.WC);
		}
		for (Iterator encodedPaths = urlPaths.iterator(); encodedPaths
				.hasNext();) {
			String encodedPath = (String) encodedPaths.next();
			SVNURL fullURL = topURL.appendPath(encodedPath, true);
			LockInfo info = (LockInfo) lockInfo.get(fullURL);
			encodedPath = SVNEncodingUtil.uriDecode(encodedPath);
			if (lock) {
				if (info.myRevision == SVNRevision.UNDEFINED) {
					lockPaths.put(encodedPath, null);
				} else {
					lockPaths.put(encodedPath, new Long(info.myRevision
							.getNumber()));
				}
			} else {
				lockPaths.put(encodedPath, info.myToken);
			}
		}
		return topURL;
	}

	/** 
	 * @from org.tmatesoft.svn.core.wc.SVNWCClient
	 */
	public SVNAdminArea addParentDirectories(SVNWCAccess wcAccess, File path)
			throws SVNException {
		try {
			return wcAccess.open(path, true, 0);
		} catch (SVNException e) {
			if (e.getErrorMessage().getErrorCode() == SVNErrorCode.WC_NOT_DIRECTORY) {
				if (path.getParentFile() == null) {
					SVNErrorMessage err = SVNErrorMessage
							.create(SVNErrorCode.CLIENT_NO_VERSIONED_PARENT);
					SVNErrorManager.error(err, SVNLogType.WC);
				} else if (SVNFileUtil.getAdminDirectoryName().equals(
						path.getName())) {
					SVNErrorMessage err = SVNErrorMessage.create(
							SVNErrorCode.RESERVED_FILENAME_SPECIFIED,
							"''{0}'' ends in a reserved name", path);
					SVNErrorManager.error(err, SVNLogType.WC);
				} else {
					File parentPath = path.getParentFile();
					SVNAdminArea parentDir = addParentDirectories(wcAccess,
							parentPath);
					SVNWCManager.add(path, parentDir, null,
							SVNRevision.UNDEFINED, SVNDepth.INFINITY);
					return wcAccess.getAdminArea(path);
				}
			}
			throw e;
		}
	}

	/** 
	 * @from org.tmatesoft.svn.core.wc.SVNWCClient
	 */
	public boolean doRevert(File path, SVNAdminArea parent, SVNDepth depth,
			boolean useCommitTimes, Collection changeLists) throws SVNException {
		checkCancelled();
		SVNWCAccess wcAccess = parent.getWCAccess();
		SVNAdminArea dir = wcAccess.probeRetrieve(path);
		SVNEntry entry = wcAccess.getEntry(path, false);
		SVNTreeConflictDescription treeConflict = wcAccess
				.getTreeConflict(path);
		if (entry == null && treeConflict == null) {
			SVNErrorMessage err = SVNErrorMessage.create(
					SVNErrorCode.UNVERSIONED_RESOURCE,
					"Cannot revert unversioned item ''{0}''", path);
			SVNErrorManager.error(err, SVNLogType.WC);
		}
		if (entry != null && entry.getKind() == SVNNodeKind.DIR) {
			SVNFileType fileType = SVNFileType.getType(path);
			if (fileType != SVNFileType.DIRECTORY
					&& !entry.isScheduledForAddition()) {
				if (isRevertMissingDirectories() && entry.getSchedule() != null
						&& !entry.isThisDir()) {
					boolean reverted = revert(parent, entry.getName(), entry,
							useCommitTimes);
					if (reverted) {
						SVNEvent event = SVNEventFactory.createSVNEvent(dir
								.getFile(entry.getName()), entry.getKind(),
								null, entry.getRevision(),
								SVNEventAction.REVERT, null, null, null);
						dispatchEvent(event);
					}
					return reverted;
				}
				SVNEvent event = SVNEventFactory.createSVNEvent(dir
						.getFile(entry.getName()), entry.getKind(), null, entry
						.getRevision(), SVNEventAction.FAILED_REVERT, null,
						null, null);
				dispatchEvent(event);
				return false;
			}
		}
		if (entry != null && entry.getKind() != SVNNodeKind.DIR
				&& entry.getKind() != SVNNodeKind.FILE) {
			SVNErrorMessage err = SVNErrorMessage.create(
					SVNErrorCode.UNSUPPORTED_FEATURE,
					"Cannot revert ''{0}'': unsupported entry node kind", path);
			SVNErrorManager.error(err, SVNLogType.WC);
		}
		SVNFileType fileType = SVNFileType.getType(path);
		if (fileType == SVNFileType.UNKNOWN) {
			SVNErrorMessage err = SVNErrorMessage
					.create(
							SVNErrorCode.UNSUPPORTED_FEATURE,
							"Cannot revert ''{0}'': unsupported node kind in working copy",
							path);
			SVNErrorManager.error(err, SVNLogType.WC);
		}
		boolean reverted = false;
		if (SVNWCAccess.matchesChangeList(changeLists, entry)) {
			if (treeConflict != null) {
				parent.deleteTreeConflict(path.getName());
				reverted = true;
			}
			if (entry != null) {
				if (entry.isScheduledForAddition()) {
					boolean wasDeleted = false;
					if (entry.getKind() == SVNNodeKind.FILE) {
						wasDeleted = entry.isDeleted();
						parent.removeFromRevisionControl(path.getName(), false,
								false);
					} else if (entry.getKind() == SVNNodeKind.DIR) {
						SVNEntry entryInParent = parent.getEntry(
								path.getName(), true);
						if (entryInParent != null) {
							wasDeleted = entryInParent.isDeleted();
						}
						if (fileType == SVNFileType.NONE
								|| wcAccess.isMissing(path)) {
							parent.deleteEntry(path.getName());
							parent.saveEntries(false);
						} else {
							dir.removeFromRevisionControl("", false, false);
						}
					}
					reverted = true;
					depth = SVNDepth.EMPTY;
					if (wasDeleted) {
						Map attributes = new SVNHashMap();
						attributes.put(SVNProperty.KIND, entry.getKind()
								.toString());
						attributes.put(SVNProperty.DELETED, Boolean.TRUE
								.toString());
						parent.modifyEntry(path.getName(), attributes, true,
								false);
					}
				} else if (entry.getSchedule() == null
						|| entry.isScheduledForDeletion()
						|| entry.isScheduledForReplacement()) {
					if (entry.getKind() == SVNNodeKind.FILE) {
						reverted = revert(parent, entry.getName(), entry,
								useCommitTimes);
					} else if (entry.getKind() == SVNNodeKind.DIR) {
						reverted = revert(dir, dir.getThisDirName(), entry,
								useCommitTimes);
						if (reverted && parent != dir) {
							SVNEntry entryInParent = parent.getEntry(path
									.getName(), false);
							revert(parent, path.getName(), entryInParent,
									useCommitTimes);
						}
						if (entry.isScheduledForReplacement()) {
							depth = SVNDepth.INFINITY;
						}
					}
				}
			}
			if (reverted) {
				SVNEvent event = null;
				if (entry != null) {
					event = SVNEventFactory.createSVNEvent(dir.getFile(entry
							.getName()), entry.getKind(), null, entry
							.getRevision(), SVNEventAction.REVERT, null, null,
							null);
				} else {
					event = SVNEventFactory.createSVNEvent(path,
							SVNNodeKind.UNKNOWN, null,
							SVNRepository.INVALID_REVISION,
							SVNEventAction.REVERT, null, null, null);
				}
				dispatchEvent(event);
			}
		}
		if (entry != null && entry.getKind() == SVNNodeKind.DIR
				&& depth.compareTo(SVNDepth.EMPTY) > 0) {
			SVNDepth depthBelowHere = depth;
			if (depth == SVNDepth.FILES || depth == SVNDepth.IMMEDIATES) {
				depthBelowHere = SVNDepth.EMPTY;
			}
			for (Iterator entries = dir.entries(false); entries.hasNext();) {
				SVNEntry childEntry = (SVNEntry) entries.next();
				if (dir.getThisDirName().equals(childEntry.getName())) {
					continue;
				}
				if (depth == SVNDepth.FILES && !childEntry.isFile()) {
					continue;
				}
				File childPath = new File(path, childEntry.getName());
				reverted |= doRevert(childPath, dir, depthBelowHere,
						useCommitTimes, changeLists);
			}
			Map conflicts = SVNTreeConflictUtil.readTreeConflicts(path, entry
					.getTreeConflictData());
			for (Iterator conflictsIter = conflicts.keySet().iterator(); conflictsIter
					.hasNext();) {
				File conflictedPath = (File) conflictsIter.next();
				if (dir.getEntry(conflictedPath.getName(), false) == null) {
					reverted |= doRevert(conflictedPath, dir, SVNDepth.EMPTY,
							useCommitTimes, changeLists);
				}
			}
		}
		return reverted;
	}

	/** 
	 * Schedules a Working Copy item for deletion. This method allows to
	 * choose - whether file item(s) are to be deleted from the filesystem or
	 * not. Another version of the {@link #doDelete(File,boolean,boolean) doDelete()}method is similar to the corresponding SVN client's command - <code>'svn delete'</code>
	 * as it always deletes files from the filesystem.
	 * <p/>
	 * This method deletes only local working copy paths without connecting to the repository.
	 * @param path        a WC item to be deleted
	 * @param force       <span class="javakeyword">true</span> to
	 * force the operation to run
	 * @param deleteFiles if <span class="javakeyword">true</span> then
	 * files will be scheduled for deletion as well as
	 * deleted from the filesystem, otherwise files will
	 * be only scheduled for addition and still be present
	 * in the filesystem
	 * @param dryRun      <span class="javakeyword">true</span> only to
	 * try the delete operation without actual deleting
	 * @throws SVNException if one of the following is true:
	 * <ul>
	 * <li><code>path</code> is not under version control
	 * <li>can not delete <code>path</code> without forcing
	 * </ul>
	 * @from org.tmatesoft.svn.core.wc.SVNWCClient
	 */
	public void doDelete(File path, boolean force, boolean deleteFiles,
			boolean dryRun) throws SVNException {
		SVNWCAccess wcAccess = createWCAccess();
		path = path.getAbsoluteFile();
		try {
			if (!force && deleteFiles) {
				SVNWCManager.canDelete(path, getOptions(), this);
			}
			SVNAdminArea root = wcAccess.open(path.getParentFile(), true, 0);
			if (!dryRun) {
				SVNWCManager.delete(wcAccess, root, path, deleteFiles, true);
			}
		} finally {
			wcAccess.close();
		}
	}

	/** 
	 * Unlocks file items in a Working Copy as well as in a repository.
	 * @param paths     an array of local WC file paths that should be unlocked
	 * @param breakLock if <span class="javakeyword">true</span> and there are locks
	 * that belong to different users then those locks will be also
	 * unlocked - that is "broken"
	 * @throws SVNException if one of the following is true:
	 * <ul>
	 * <li>a path is not under version control
	 * <li>can not obtain a URL of a local path to unlock it in
	 * the repository - there's no such entry
	 * <li>if a path is not locked in the Working Copy
	 * and <code>breakLock</code> is <span class="javakeyword">false</span>
	 * <li><code>paths</code> to be unlocked belong to different repositories
	 * </ul>
	 * @see #doUnlock(SVNURL[],boolean)
	 * @from org.tmatesoft.svn.core.wc.SVNWCClient
	 */
	public void doUnlock(File[] paths, boolean breakLock) throws SVNException {
		if (paths == null || paths.length == 0) {
			return;
		}
		final Map entriesMap = new SVNHashMap();
		Map pathsTokensMap = new SVNHashMap();
		final SVNWCAccess wcAccess = createWCAccess();
		try {
			final SVNURL topURL = collectLockInfo(wcAccess, paths, entriesMap,
					pathsTokensMap, false, breakLock);
			checkCancelled();
			SVNRepository repository = createRepository(topURL, paths[0],
					wcAccess, true);
			final SVNURL rootURL = repository.getRepositoryRoot(true);
			repository.unlock(pathsTokensMap, breakLock, new ISVNLockHandler() {
				public void handleLock(String path, SVNLock lock,
						SVNErrorMessage error) throws SVNException {
				}

				public void handleUnlock(String path, SVNLock lock,
						SVNErrorMessage error) throws SVNException {
					SVNURL fullURL = rootURL.appendPath(path, false);
					LockInfo lockInfo = (LockInfo) entriesMap.get(fullURL);
					SVNEventAction action = null;
					SVNAdminArea dir = wcAccess.probeRetrieve(lockInfo.myFile);
					if (error == null
							|| (error != null && error.getErrorCode() != SVNErrorCode.FS_LOCK_OWNER_MISMATCH)) {
						SVNEntry entry = wcAccess.getVersionedEntry(
								lockInfo.myFile, false);
						entry.setLockToken(null);
						entry.setLockComment(null);
						entry.setLockOwner(null);
						entry.setLockCreationDate(null);
						SVNVersionedProperties props = dir.getProperties(entry
								.getName());
						if (props.getPropertyValue(SVNProperty.NEEDS_LOCK) != null) {
							SVNFileUtil.setReadonly(dir
									.getFile(entry.getName()), true);
						}
						dir.saveEntries(false);
						action = SVNEventAction.UNLOCKED;
					}
					if (error != null) {
						action = SVNEventAction.UNLOCK_FAILED;
					}
					if (action != null) {
						handleEvent(SVNEventFactory.createLockEvent(dir
								.getFile(lockInfo.myFile.getName()), action,
								lock, error), ISVNEventHandler.UNKNOWN);
					}
				}
			});
		} finally {
			wcAccess.close();
		}
	}

	/** 
	 * @from org.tmatesoft.svn.core.wc.SVNWCClient
	 */
	public void setLocalProperties(File path, SVNEntry entry,
			SVNAdminArea adminArea, boolean force,
			ISVNPropertyValueProvider propertyValueProvider,
			ISVNPropertyHandler handler) throws SVNException {
		SVNVersionedProperties entryProperties = adminArea.getProperties(entry
				.getName());
		SVNProperties properties = entryProperties.asMap();
		SVNProperties unmodifiableProperties = SVNProperties
				.unmodifiableProperties(properties);
		SVNProperties changedProperties = propertyValueProvider
				.providePropertyValues(path, unmodifiableProperties);
		SVNProperties propDiff = properties.compareTo(changedProperties);
		for (Iterator iterator = propDiff.nameSet().iterator(); iterator
				.hasNext();) {
			String propName = (String) iterator.next();
			SVNPropertyValue propValue = propDiff.getSVNPropertyValue(propName);
			if (propValue != null
					&& !SVNPropertiesManager.isValidPropertyName(propName)) {
				SVNErrorMessage err = SVNErrorMessage.create(
						SVNErrorCode.CLIENT_PROPERTY_NAME,
						"Bad property name ''{0}''", propName);
				SVNErrorManager.error(err, SVNLogType.WC);
			}
			if (SVNRevisionProperty.isRevisionProperty(propName)) {
				SVNErrorMessage err = SVNErrorMessage
						.create(
								SVNErrorCode.CLIENT_PROPERTY_NAME,
								"Revision property ''{0}'' not allowed in this context",
								propName);
				SVNErrorManager.error(err, SVNLogType.WC);
			} else if (SVNProperty.isWorkingCopyProperty(propName)) {
				SVNErrorMessage err = SVNErrorMessage.create(
						SVNErrorCode.CLIENT_PROPERTY_NAME,
						"''{0}'' is a wcprop, thus not accessible to clients",
						propName);
				SVNErrorManager.error(err, SVNLogType.WC);
			} else if (SVNProperty.isEntryProperty(propName)) {
				SVNErrorMessage err = SVNErrorMessage.create(
						SVNErrorCode.CLIENT_PROPERTY_NAME,
						"Property ''{0}'' is an entry property", propName);
				SVNErrorManager.error(err, SVNLogType.WC);
			}
			try {
				boolean modified = SVNPropertiesManager.setProperty(adminArea
						.getWCAccess(), path, propName, propValue, force);
				if (modified && handler != null) {
					handler.handleProperty(path, new SVNPropertyData(propName,
							propValue, getOptions()));
				}
			} catch (SVNException svne) {
				if (svne.getErrorMessage().getErrorCode() != SVNErrorCode.ILLEGAL_TARGET) {
					throw svne;
				}
			}
		}
	}

	/** 
	 * @from org.tmatesoft.svn.core.wc.SVNWCClient
	 */
	public void addFile(File path, SVNFileType type, SVNAdminArea dir)
			throws SVNException {
		ISVNEventHandler handler = dir.getWCAccess().getEventHandler();
		dir.getWCAccess().setEventHandler(null);
		SVNWCManager.add(path, dir, null, SVNRevision.UNDEFINED, null);
		dir.getWCAccess().setEventHandler(handler);
		String mimeType = null;
		if (type == SVNFileType.SYMLINK) {
			SVNPropertiesManager.setProperty(dir.getWCAccess(), path,
					SVNProperty.SPECIAL, SVNProperty
							.getValueOfBooleanProperty(SVNProperty.SPECIAL),
					false);
		} else {
			Map props = SVNPropertiesManager.computeAutoProperties(
					getOptions(), path, null);
			for (Iterator names = props.keySet().iterator(); names.hasNext();) {
				String propName = (String) names.next();
				String propValue = (String) props.get(propName);
				try {
					SVNPropertiesManager
							.setProperty(dir.getWCAccess(), path, propName,
									SVNPropertyValue.create(propValue), false);
				} catch (SVNException e) {
					if (SVNProperty.EOL_STYLE.equals(propName)
							&& e.getErrorMessage().getErrorCode() == SVNErrorCode.ILLEGAL_TARGET
							&& e.getErrorMessage().getMessage().indexOf(
									"newlines") >= 0) {
						ISVNAddParameters.Action action = getAddParameters()
								.onInconsistentEOLs(path);
						if (action == ISVNAddParameters.REPORT_ERROR) {
							ISVNEventHandler eventHandler = getEventDispatcher();
							try {
								setEventHandler(null);
								doRevert(path, dir, SVNDepth.EMPTY, false, null);
							} catch (SVNException svne) {
							} finally {
								setEventHandler(eventHandler);
							}
							throw e;
						} else if (action == ISVNAddParameters.ADD_AS_IS) {
							SVNPropertiesManager.setProperty(dir.getWCAccess(),
									path, propName, null, false);
						} else if (action == ISVNAddParameters.ADD_AS_BINARY) {
							SVNPropertiesManager.setProperty(dir.getWCAccess(),
									path, propName, null, false);
							mimeType = SVNFileUtil.BINARY_MIME_TYPE;
						}
					} else {
						ISVNEventHandler eventHandler = getEventDispatcher();
						try {
							setEventHandler(null);
							doRevert(path, dir, SVNDepth.EMPTY, false, null);
						} catch (SVNException svne) {
						} finally {
							setEventHandler(eventHandler);
						}
						throw e;
					}
				}
			}
			if (mimeType != null) {
				SVNPropertiesManager.setProperty(dir.getWCAccess(), path,
						SVNProperty.MIME_TYPE, SVNPropertyValue
								.create(mimeType), false);
			} else {
				mimeType = (String) props.get(SVNProperty.MIME_TYPE);
			}
		}
		SVNEvent event = SVNEventFactory.createSVNEvent(dir.getFile(path
				.getName()), SVNNodeKind.FILE, mimeType,
				SVNRepository.INVALID_REVISION, SVNEventAction.ADD, null, null,
				null);
		dispatchEvent(event);
	}

	/** 
	 * @from org.tmatesoft.svn.core.wc.SVNWCClient
	 */
	public void doGetLocalProperty(SVNEntry entry, SVNAdminArea area,
			String propName, boolean base, ISVNPropertyHandler handler,
			SVNDepth depth, Collection changeLists) throws SVNException {
		if (depth == null || depth == SVNDepth.UNKNOWN) {
			depth = SVNDepth.EMPTY;
		}
		File target = area.getFile(entry.getName());
		SVNWCAccess wcAccess = area.getWCAccess();
		ISVNEntryHandler propGetHandler = new PropFetchHandler(changeLists,
				propName, handler, base);
		if (SVNDepth.FILES.compareTo(depth) <= 0 && entry.isDirectory()) {
			wcAccess.walkEntries(target, propGetHandler, false, depth);
		} else if (SVNWCAccess.matchesChangeList(changeLists, entry)) {
			if (propName == null) {
				SVNVersionedProperties properties = base ? area
						.getBaseProperties(entry.getName()) : area
						.getProperties(entry.getName());
				if (propName != null) {
					SVNPropertyValue propValue = properties
							.getPropertyValue(propName);
					if (propValue != null) {
						handler.handleProperty(target, new SVNPropertyData(
								propName, propValue, getOptions()));
					}
				} else {
					SVNProperties allProps = properties.asMap();
					for (Iterator names = allProps.nameSet().iterator(); names
							.hasNext();) {
						String name = (String) names.next();
						SVNPropertyValue val = allProps
								.getSVNPropertyValue(name);
						handler.handleProperty(area.getFile(entry.getName()),
								new SVNPropertyData(name, val, getOptions()));
					}
				}
			} else {
				propGetHandler.handleEntry(target, entry);
			}
		}
	}

	/** 
	 * Returns the current Working Copy min- and max- revisions as well as
	 * changes and switch status within a single string.
	 * <p/>
	 * <p/>
	 * A return string has a form of <code>"minR[:maxR][M][S]"</code> where:
	 * <ul>
	 * <li><code>minR</code> - is the smallest revision number met in the
	 * Working Copy
	 * <li><code>maxR</code> - is the biggest revision number met in the
	 * Working Copy; appears only if there are different revision in the
	 * Working Copy
	 * <li><code>M</code> - appears only if there're local edits to the
	 * Working Copy - that means 'Modified'
	 * <li><code>S</code> - appears only if the Working Copy is switched
	 * against a different URL
	 * </ul>
	 * If <code>path</code> is a directory - this method recursively descends
	 * into the Working Copy, collects and processes local information.
	 * <p/>
	 * This method operates on local working copies only without accessing a repository.
	 * @param path          a local path
	 * @param trailURL      optional: if not <span class="javakeyword">null</span>
	 * specifies the name of the item that should be met
	 * in the URL corresponding to the repository location
	 * of the <code>path</code>; if that URL ends with something
	 * different than this optional parameter - the Working
	 * Copy will be considered "switched"
	 * @param committed     if <span class="javakeyword">true</span> committed (last chaned) 
	 * revisions instead of working copy ones are reported
	 * @return              brief info on the Working Copy or the string
	 * "exported" if <code>path</code> is a clean directory
	 * @throws SVNException if <code>path</code> is neither versioned nor
	 * even exported
	 * @since  1.2
	 * @from org.tmatesoft.svn.core.wc.SVNWCClient
	 */
	public String doGetWorkingCopyID(final File path, String trailURL,
			final boolean committed) throws SVNException {
		SVNWCAccess wcAccess = createWCAccess();
		try {
			wcAccess.open(path, false, 0);
		} catch (SVNException e) {
			SVNFileType pathType = SVNFileType.getType(path);
			if (pathType == SVNFileType.DIRECTORY) {
				return "exported";
			} else if (pathType == SVNFileType.NONE) {
				throw e;
			}
			return "'" + path + "' is not versioned and not exported";
		} finally {
			wcAccess.close();
		}
		SVNStatusClient16 statusClient = new SVNStatusClient16(
				(ISVNAuthenticationManager) null, getOptions());
		statusClient.setIgnoreExternals(true);
		final long[] maxRevision = new long[1];
		final long[] minRevision = new long[] { -1 };
		final boolean[] switched = new boolean[3];
		final String[] wcURL = new String[1];
		statusClient.doStatus(path, SVNRevision.WORKING, SVNDepth.INFINITY,
				false, true, false, false, new ISVNStatusHandler() {
					public void handleStatus(SVNStatus status) {
						if (status.getEntryProperties() == null
								|| status.getEntryProperties().isEmpty()) {
							return;
						}
						if (status.getContentsStatus() != SVNStatusType.STATUS_ADDED) {
							SVNRevision revision = committed ? status
									.getCommittedRevision() : status
									.getRevision();
							if (revision != null) {
								if (minRevision[0] < 0
										|| minRevision[0] > revision
												.getNumber()) {
									minRevision[0] = revision.getNumber();
								}
								maxRevision[0] = Math.max(maxRevision[0],
										revision.getNumber());
							}
						}
						switched[0] |= status.isSwitched();
						switched[1] |= status.getContentsStatus() != SVNStatusType.STATUS_NORMAL;
						switched[1] |= status.getPropertiesStatus() != SVNStatusType.STATUS_NORMAL
								&& status.getPropertiesStatus() != SVNStatusType.STATUS_NONE;
						switched[2] |= status.getEntry() != null
								&& status.getEntry().getDepth() != SVNDepth.INFINITY;
						if (wcURL[0] == null && status.getFile() != null
								&& status.getFile().equals(path)
								&& status.getURL() != null) {
							wcURL[0] = status.getURL().toString();
						}
					}
				}, null);
		if (!switched[0] && trailURL != null) {
			if (wcURL[0] == null) {
				switched[0] = true;
			} else {
				switched[0] = !wcURL[0].endsWith(trailURL);
			}
		}
		StringBuffer id = new StringBuffer();
		id.append(minRevision[0]);
		if (minRevision[0] != maxRevision[0]) {
			id.append(":").append(maxRevision[0]);
		}
		if (switched[1]) {
			id.append("M");
		}
		if (switched[0]) {
			id.append("S");
		}
		if (switched[2]) {
			id.append("P");
		}
		return id.toString();
	}

	/** 
	 * @from org.tmatesoft.svn.core.wc.SVNWCClient
	 */
	public void reportEntry(File path, SVNEntry entry, ISVNInfoHandler handler)
			throws SVNException {
		if (entry.isDirectory() && !"".equals(entry.getName())) {
			return;
		}
		handler.handleInfo(SVNInfo.createInfo(path, entry));
	}

	/** 
	 * Locks file items in a Working Copy as well as in a repository so that
	 * no other user can commit changes to them.
	 * @param paths       an array of local WC file paths that should be locked
	 * @param stealLock   if <span class="javakeyword">true</span> then all existing
	 * locks on the specified <code>paths</code> will be "stolen"
	 * @param lockMessage an optional lock comment
	 * @throws SVNException if one of the following is true:
	 * <ul>
	 * <li>a path to be locked is not under version control
	 * <li>can not obtain a URL of a local path to lock it in
	 * the repository - there's no such entry
	 * <li><code>paths</code> to be locked belong to different repositories
	 * </ul>
	 * @see #doLock(SVNURL[],boolean,String)
	 * @from org.tmatesoft.svn.core.wc.SVNWCClient
	 */
	public void doLock(File[] paths, boolean stealLock, String lockMessage)
			throws SVNException {
		if (paths == null || paths.length == 0) {
			return;
		}
		final Map entriesMap = new SVNHashMap();
		Map pathsRevisionsMap = new SVNHashMap();
		final SVNWCAccess wcAccess = createWCAccess();
		try {
			final SVNURL topURL = collectLockInfo(wcAccess, paths, entriesMap,
					pathsRevisionsMap, true, stealLock);
			SVNRepository repository = createRepository(topURL, paths[0],
					wcAccess, true);
			final SVNURL rootURL = repository.getRepositoryRoot(true);
			repository.lock(pathsRevisionsMap, lockMessage, stealLock,
					new ISVNLockHandler() {
						public void handleLock(String path, SVNLock lock,
								SVNErrorMessage error) throws SVNException {
							SVNURL fullURL = rootURL.appendPath(path, false);
							LockInfo lockInfo = (LockInfo) entriesMap
									.get(fullURL);
							SVNAdminArea dir = wcAccess
									.probeRetrieve(lockInfo.myFile);
							if (error == null) {
								SVNEntry entry = wcAccess.getVersionedEntry(
										lockInfo.myFile, false);
								entry.setLockToken(lock.getID());
								entry.setLockComment(lock.getComment());
								entry.setLockOwner(lock.getOwner());
								entry.setLockCreationDate(SVNDate
										.formatDate(lock.getCreationDate()));
								SVNVersionedProperties props = dir
										.getProperties(entry.getName());
								if (props
										.getPropertyValue(SVNProperty.NEEDS_LOCK) != null) {
									SVNFileUtil.setReadonly(dir.getFile(entry
											.getName()), false);
								}
								SVNFileUtil
										.setExecutable(
												dir.getFile(entry.getName()),
												props
														.getPropertyValue(SVNProperty.EXECUTABLE) != null);
								dir.saveEntries(false);
								handleEvent(SVNEventFactory.createLockEvent(dir
										.getFile(entry.getName()),
										SVNEventAction.LOCKED, lock, null),
										ISVNEventHandler.UNKNOWN);
							} else {
								handleEvent(SVNEventFactory
										.createLockEvent(dir
												.getFile(lockInfo.myFile
														.getName()),
												SVNEventAction.LOCK_FAILED,
												lock, error),
										ISVNEventHandler.UNKNOWN);
							}
						}

						public void handleUnlock(String path, SVNLock lock,
								SVNErrorMessage error) {
						}
					});
		} finally {
			wcAccess.close();
		}
	}

	/** 
	 * Recursively cleans up the working copy, removing locks and resuming
	 * unfinished operations.
	 * <p/>
	 * If you ever get a "working copy locked" error, use this method
	 * to remove stale locks and get your working copy into a usable
	 * state again.
	 * <p>
	 * This method operates only on working copies and does not open any network connection.
	 * @param path                 a WC path to start a cleanup from
	 * @param deleteWCProperties   if <span class="javakeyword">true</span>, removes DAV specific 
	 * <span class="javastring">"svn:wc:"</span> properties from the working copy 
	 * @throws SVNException         if one of the following is true:
	 * <ul>
	 * <li><code>path</code> does not exist
	 * <li><code>path</code>'s parent directory
	 * is not under version control
	 * </ul>
	 * @from org.tmatesoft.svn.core.wc.SVNWCClient
	 */
	public void doCleanup(File path, boolean deleteWCProperties)
			throws SVNException {
		SVNFileType fType = SVNFileType.getType(path);
		if (fType == SVNFileType.NONE) {
			SVNErrorMessage err = SVNErrorMessage.create(
					SVNErrorCode.WC_PATH_NOT_FOUND, "''{0}'' does not exist",
					path);
			SVNErrorManager.error(err, SVNLogType.WC);
		} else if (fType == SVNFileType.FILE) {
			path = path.getParentFile();
		} else if (fType == SVNFileType.SYMLINK) {
			path = SVNFileUtil.resolveSymlink(path);
			if (SVNFileType.getType(path) == SVNFileType.FILE) {
				path = path.getParentFile();
			}
		}
		SVNWCAccess wcAccess = createWCAccess();
		try {
			SVNAdminArea adminArea = wcAccess.open(path, true, true, 0);
			adminArea.cleanup();
			if (deleteWCProperties) {
				SVNPropertiesManager.deleteWCProperties(adminArea, null, true);
			}
		} catch (SVNException e) {
			if (e instanceof SVNCancelException) {
				throw e;
			} else if (!SVNAdminArea.isSafeCleanup()) {
				throw e;
			}
			SVNDebugLog.getDefaultLog().logFine(SVNLogType.WC,
					"CLEANUP FAILED for " + path);
			SVNDebugLog.getDefaultLog().logFine(SVNLogType.WC, e);
		} finally {
			wcAccess.close();
			sleepForTimeStamp();
		}
	}

	/** 
	 * Sets <code>propName</code> to <code>propValue</code> on <code>path</code>.
	 * A <code>propValue</code> of <span class="javakeyword">null</span> will delete 
	 * the property.
	 * <p/>
	 * If <code>depth</code> is {@link org.tmatesoft.svn.core.SVNDepth#EMPTY}, set the property on <code>path</code>
	 * only; if {@link SVNDepth#FILES}, set it on <code>path</code> and its file
	 * children (if any); if {@link SVNDepth#IMMEDIATES}, on <code>path</code> and all
	 * of its immediate children (both files and directories); if{@link SVNDepth#INFINITY}, on <code>path</code> and everything beneath it.
	 * <p/>
	 * If <code>propName</code> is an svn-controlled property (i.e. prefixed with
	 * <span class="javastring">"svn:"</span>), then the caller is responsible for ensuring that
	 * the value uses LF line-endings.
	 * <p/>
	 * If <code>skipChecks</code> is <span class="javakeyword">true</span>, this method does no validity 
	 * checking.  But if <code>skipChecks</code> is <span class="javakeyword">false</span>, 
	 * and <code>propName</code> is not a valid property for <code>path</code>, it throws an exception, 
	 * either with an error code {@link org.tmatesoft.svn.core.SVNErrorCode#ILLEGAL_TARGET} 
	 * (if the property is not appropriate for <code>path</code>), or with {@link org.tmatesoft.svn.core.SVNErrorCode#BAD_MIME_TYPE} (if <code>propName</code> is 
	 * <span class="javastring">"svn:mime-type"</span>, but <code>propVal</code> is not a valid mime-type).
	 * <p/>
	 * <code>changeLists</code> is a collection of <code>String</code> changelist
	 * names, used as a restrictive filter on items whose properties are
	 * set; that is, don't set properties on any item unless it's a member
	 * of one of those changelists.  If <code>changelists</code> is empty (or
	 * <span class="javakeyword">null</span>), no changelist filtering occurs.
	 * <p>
	 * This method operates only on working copies and does not open any network connection.
	 * @param path          working copy path
	 * @param propName      property name
	 * @param propValue     property value
	 * @param skipChecks    <span class="javakeyword">true</span> to
	 * force the operation to run without validity checking 
	 * @param depth         working copy tree depth to process   
	 * @param handler       a caller's property handler
	 * @param changeLists   changelist names
	 * @throws SVNException <ul>
	 * <li><code>path</code> does not exist
	 * <li>exception with {@link SVNErrorCode#CLIENT_PROPERTY_NAME} error code - 
	 * if <code>propName</code> is a revision property name or not a valid property name or 
	 * not a regular property name (one starting with 
	 * a <span class="javastring">"svn:entry"</span> or 
	 * <span class="javastring">"svn:wc"</span> prefix)
	 * </ul>
	 * @see #doSetProperty(SVNURL,String,SVNPropertyValue,SVNRevision,String,SVNProperties,boolean,ISVNPropertyHandler)
	 * @since 1.2, SVN 1.5
	 * @from org.tmatesoft.svn.core.wc.SVNWCClient
	 */
	public void doSetProperty(File path, String propName,
			SVNPropertyValue propValue, boolean skipChecks, SVNDepth depth,
			ISVNPropertyHandler handler, Collection changeLists)
			throws SVNException {
		depth = depth == null ? SVNDepth.UNKNOWN : depth;
		int admLockLevel = SVNWCAccess.INFINITE_DEPTH;
		if (depth == SVNDepth.EMPTY || depth == SVNDepth.FILES) {
			admLockLevel = 0;
		}
		if (propValue != null
				&& !SVNPropertiesManager.isValidPropertyName(propName)) {
			SVNErrorMessage err = SVNErrorMessage.create(
					SVNErrorCode.CLIENT_PROPERTY_NAME,
					"Bad property name ''{0}''", propName);
			SVNErrorManager.error(err, SVNLogType.WC);
		}
		if (SVNRevisionProperty.isRevisionProperty(propName)) {
			SVNErrorMessage err = SVNErrorMessage.create(
					SVNErrorCode.CLIENT_PROPERTY_NAME,
					"Revision property ''{0}'' not allowed in this context",
					propName);
			SVNErrorManager.error(err, SVNLogType.WC);
		} else if (SVNProperty.isWorkingCopyProperty(propName)) {
			SVNErrorMessage err = SVNErrorMessage.create(
					SVNErrorCode.CLIENT_PROPERTY_NAME,
					"''{0}'' is a wcprop, thus not accessible to clients",
					propName);
			SVNErrorManager.error(err, SVNLogType.WC);
		} else if (SVNProperty.isEntryProperty(propName)) {
			SVNErrorMessage err = SVNErrorMessage.create(
					SVNErrorCode.CLIENT_PROPERTY_NAME,
					"Property ''{0}'' is an entry property", propName);
			SVNErrorManager.error(err, SVNLogType.WC);
		}
		SVNWCAccess wcAccess = createWCAccess();
		try {
			wcAccess.probeOpen(path, true, admLockLevel);
			SVNEntry entry = wcAccess.getVersionedEntry(path, false);
			if (SVNDepth.FILES.compareTo(depth) <= 0 && entry.isDirectory()) {
				PropSetHandler entryHandler = new PropSetHandler(skipChecks,
						propName, propValue, handler, changeLists);
				wcAccess.walkEntries(path, entryHandler, false, depth);
			} else if (SVNWCAccess.matchesChangeList(changeLists, entry)) {
				boolean modified = SVNPropertiesManager.setProperty(wcAccess,
						path, propName, propValue, skipChecks);
				if (modified && handler != null) {
					handler.handleProperty(path, new SVNPropertyData(propName,
							propValue, getOptions()));
				}
			}
		} finally {
			wcAccess.close();
		}
	}

	/** 
	 * Schedules <code>path</code> as being replaced.
	 * This method does not perform any deletion\addition in the filesysem nor does it require a connection to 
	 * the repository. It just marks the current <code>path</code> item as being replaced.  
	 * @param path working copy path to mark as
	 * @throws SVNException
	 * @since 1.2 
	 * @from org.tmatesoft.svn.core.wc.SVNWCClient
	 */
	public void doMarkReplaced(File path) throws SVNException {
		SVNWCAccess wcAccess = createWCAccess();
		path = path.getAbsoluteFile();
		try {
			SVNAdminAreaInfo areaInfo = wcAccess.openAnchor(path, true,
					SVNWCAccess.INFINITE_DEPTH);
			SVNAdminArea anchor = areaInfo.getAnchor();
			if (path.equals(anchor.getRoot().getAbsoluteFile())) {
				SVNWCManager.markTree(anchor, SVNProperty.SCHEDULE_REPLACE,
						false, false, SVNWCManager.SCHEDULE);
			} else {
				SVNEntry entry = anchor.getEntry(path.getName(), false);
				SVNWCManager.markEntry(anchor, entry,
						SVNProperty.SCHEDULE_REPLACE, false, false,
						SVNWCManager.SCHEDULE);
			}
			anchor.saveEntries(false);
		} finally {
			wcAccess.close();
		}
	}

	/** 
	 * Schedules a Working Copy item for deletion.
	 * This method is equivalent to <code>doDelete(path, force, true, dryRun)</code>.
	 * @param path          a WC item to be deleted
	 * @param force         <span class="javakeyword">true</span> to
	 * force the operation to run
	 * @param dryRun        <span class="javakeyword">true</span> only to
	 * try the delete operation without actual deleting
	 * @throws SVNException if one of the following is true:
	 * <ul>
	 * <li><code>path</code> is not under version control
	 * <li>can not delete <code>path</code> without forcing
	 * </ul>
	 * @see #doDelete(File,boolean,boolean,boolean)
	 * @from org.tmatesoft.svn.core.wc.SVNWCClient
	 */
	public void doDelete(File path, boolean force, boolean dryRun)
			throws SVNException {
		doDelete(path, force, true, dryRun);
	}

	/** 
	 * @from org.tmatesoft.svn.core.wc.SVNWCClient
	 */
	public void crawlEntries(File path, SVNDepth depth,
			final Collection changeLists, final ISVNInfoHandler handler)
			throws SVNException {
		final SVNWCAccess wcAccess = createWCAccess();
		int admLockLevel = getLevelsToLockFromDepth(depth);
		try {
			wcAccess.probeOpen(path, false, admLockLevel);
			wcAccess.walkEntries(path, new ISVNEntryHandler() {
				public void handleEntry(File path, SVNEntry entry)
						throws SVNException {
					if (entry.isDirectory() && !entry.isThisDir()) {
						return;
					}
					if (SVNWCAccess.matchesChangeList(changeLists, entry)) {
						reportEntry(path, entry, handler);
					}
				}

				public void handleError(File path, SVNErrorMessage error)
						throws SVNException {
					if (error != null
							&& error.getErrorCode() == SVNErrorCode.UNVERSIONED_RESOURCE) {
						SVNAdminArea dir = wcAccess.probeTry(path
								.getParentFile(), false, 0);
						SVNTreeConflictDescription tc = dir
								.getTreeConflict(path.getName());
						if (tc != null) {
							SVNInfo info = SVNInfo.createInfo(path, tc);
							handler.handleInfo(info);
							return;
						}
					}
					SVNErrorManager.error(error, SVNLogType.WC);
				}
			}, false, depth);
		} finally {
			wcAccess.close();
		}
	}

	/** 
	 * @from org.tmatesoft.svn.core.wc.SVNWCClient
	 */
	public void doGetLocalFileContents(File path, OutputStream dst,
			SVNRevision revision, boolean expandKeywords) throws SVNException {
		SVNWCAccess wcAccess = createWCAccess();
		InputStream input = null;
		boolean hasMods = false;
		SVNVersionedProperties properties = null;
		try {
			SVNAdminArea area = wcAccess.open(path.getParentFile(), false, 0);
			SVNEntry entry = wcAccess.getVersionedEntry(path, false);
			if (entry.getKind() != SVNNodeKind.FILE) {
				SVNErrorMessage err = SVNErrorMessage.create(
						SVNErrorCode.UNVERSIONED_RESOURCE,
						"''{0}'' refers to a directory", path);
				SVNErrorManager.error(err, SVNLogType.WC);
			}
			String name = path.getName();
			if (revision != SVNRevision.WORKING) {
				input = area.getBaseFileForReading(name, false);
				properties = area.getBaseProperties(name);
			} else {
				input = SVNFileUtil.openFileForReading(area.getFile(path
						.getName()), SVNLogType.WC);
				hasMods = area.hasPropModifications(name)
						|| area.hasTextModifications(name, true);
				properties = area.getProperties(name);
			}
			String charsetProp = properties
					.getStringPropertyValue(SVNProperty.CHARSET);
			String eolStyle = properties
					.getStringPropertyValue(SVNProperty.EOL_STYLE);
			String keywords = properties
					.getStringPropertyValue(SVNProperty.KEYWORDS);
			boolean special = properties.getPropertyValue(SVNProperty.SPECIAL) != null;
			byte[] eols = null;
			Map keywordsMap = null;
			String time = null;
			String charset = SVNTranslator.getCharset(charsetProp, path
					.getPath(), getOptions());
			eols = SVNTranslator.getEOL(eolStyle, getOptions());
			if (hasMods && !special) {
				time = SVNDate.formatDate(new Date(path.lastModified()));
			} else {
				time = entry.getCommittedDate();
			}
			if (keywords != null) {
				String url = entry.getURL();
				String author = hasMods ? "(local)" : entry.getAuthor();
				String rev = hasMods ? entry.getCommittedRevision() + "M"
						: entry.getCommittedRevision() + "";
				keywordsMap = SVNTranslator.computeKeywords(keywords,
						expandKeywords ? url : null, author, time, rev,
						getOptions());
			}
			OutputStream translatingStream = charset != null || eols != null
					|| keywordsMap != null ? SVNTranslator
					.getTranslatingOutputStream(dst, charset, eols, false,
							keywordsMap, expandKeywords) : dst;
			try {
				SVNTranslator.copy(input, new SVNCancellableOutputStream(
						translatingStream, getEventDispatcher()));
				if (translatingStream != dst) {
					SVNFileUtil.closeFile(translatingStream);
				}
				dst.flush();
			} catch (IOExceptionWrapper ioew) {
				throw ioew.getOriginalException();
			} catch (IOException e) {
				if (e instanceof SVNCancellableOutputStream.IOCancelException) {
					SVNErrorManager.cancel(e.getMessage(), SVNLogType.NETWORK);
				}
				SVNErrorManager.error(SVNErrorMessage.create(
						SVNErrorCode.IO_ERROR, e.getMessage()), SVNLogType.WC);
			}
		} finally {
			SVNFileUtil.closeFile(input);
			wcAccess.close();
		}
	}

	/** 
	 * @from org.tmatesoft.svn.core.wc.SVNWCClient
	 */
	public void setWCFormat(SVNAdminAreaInfo info, SVNAdminArea area, int format)
			throws SVNException {
		if (!isIgnoreExternals()) {
			SVNVersionedProperties props = area.getProperties(area
					.getThisDirName());
			SVNVersionedProperties baseProps = area.getBaseProperties(area
					.getThisDirName());
			SVNPropertyValue property = props
					.getPropertyValue(SVNProperty.EXTERNALS);
			SVNPropertyValue baseProperty = baseProps
					.getPropertyValue(SVNProperty.EXTERNALS);
			if (property != null || baseProperty != null) {
				String areaPath = area.getRelativePath(info.getAnchor());
				info.addExternal(areaPath, property != null ? property
						.getString() : null,
						baseProperty != null ? baseProperty.getString() : null);
			}
		}
		area.getWCAccess().closeAdminArea(area.getRoot());
		area = area.getWCAccess().open(area.getRoot(), true, false, false, 0,
				Level.FINE);
		SVNAdminArea newArea = SVNAdminAreaFactory.changeWCFormat(area, format);
		for (Iterator entries = newArea.entries(false); entries.hasNext();) {
			SVNEntry entry = (SVNEntry) entries.next();
			if (entry.isThisDir() || entry.isFile()) {
				continue;
			}
			File childDir = new File(newArea.getRoot(), entry.getName());
			SVNAdminArea childArea = newArea.getWCAccess().getAdminArea(
					childDir);
			if (childArea != null) {
				setWCFormat(info, childArea, format);
			}
		}
	}

	/** 
	 * Performs automatic conflict resolution on a working copy <code>path</code>.
	 * <p/> 
	 * If <code>depth</code> is {@link SVNDepth#EMPTY}, acts only on <code>path</code>; if{@link SVNDepth#FILES}, resolves <code>path</code> and its conflicted file
	 * children (if any); if {@link SVNDepth#IMMEDIATES}, resolves <code>path</code> and
	 * all its immediate conflicted children (both files and directories,
	 * if any); if {@link SVNDepth#INFINITY}, resolves <code>path</code> and every
	 * conflicted file or directory anywhere beneath it.
	 * <p/>
	 * If <code>conflictChoice</code> is {@link SVNConflictChoice#BASE}, resolves the
	 * conflict with the old file contents; if {@link SVNConflictChoice#MINE_FULL}, uses the original 
	 * working contents; if {@link SVNConflictChoice#THEIRS_FULL}, the new contents; and if{@link SVNConflictChoice#MERGED}, doesn't change the contents at all, just removes the conflict status, 
	 * which is the pre-1.2 (pre-SVN 1.5) behavior.
	 * <p/>{@link SVNConflictChoice#THEIRS_CONFLICT} and {@link SVNConflictChoice#MINE_CONFLICT} are not legal for 
	 * binary files or properties.
	 * <p/>
	 * If <code>path</code> is not in a state of conflict to begin with, does nothing. If 
	 * <code>path</code>'s conflict state is removed and caller's {@link ISVNEntryHandler} is not 
	 * <span class="javakeyword">null</span>, then an {@link SVNEventAction#RESOLVED} event is 
	 * dispatched to the handler.
	 * @param path               working copy path
	 * @param depth              tree depth
	 * @param resolveContents    resolve content conflict
	 * @param resolveProperties  resolve property conflict
	 * @param resolveTree n      resolve any tree conlicts
	 * @param conflictChoice     choice object for making decision while resolving
	 * @throws SVNException  
	 * @since 1.3, SVN 1.6
	 * @from org.tmatesoft.svn.core.wc.SVNWCClient
	 */
	public void doResolve(File path, SVNDepth depth,
			final boolean resolveContents, final boolean resolveProperties,
			final boolean resolveTree, SVNConflictChoice conflictChoice)
			throws SVNException {
		final SVNConflictChoice choice = conflictChoice == null ? SVNConflictChoice.MERGED
				: conflictChoice;
		path = path.getAbsoluteFile();
		final SVNWCAccess wcAccess = createWCAccess();
		int admLockLevel = SVNWCAccess.INFINITE_DEPTH;
		if (depth == SVNDepth.EMPTY || depth == SVNDepth.FILES) {
			admLockLevel = 0;
		}
		try {
			wcAccess.probeOpen(path, true, admLockLevel);
			if (!wcAccess.isWCRoot(path)) {
				wcAccess.close();
				if (admLockLevel >= 0) {
					admLockLevel++;
				}
				wcAccess.probeOpen(path.getParentFile(), true, admLockLevel);
			}
			ISVNEntryHandler resolveEntryHandler = new ISVNEntryHandler() {
				public void handleEntry(File path, SVNEntry entry)
						throws SVNException {
					if (entry != null && entry.isDirectory()
							&& !"".equals(entry.getName())) {
						return;
					}
					SVNNodeKind kind = SVNNodeKind.UNKNOWN;
					long revision = -1;
					boolean wcRoot = false;
					boolean resolved = false;
					if (entry != null && entry.isDirectory()) {
						wcRoot = wcAccess.isWCRoot(path);
					}
					if (resolveTree && !wcRoot) {
						File parentDir = path.getParentFile();
						SVNAdminArea parentArea = wcAccess
								.probeRetrieve(parentDir);
						SVNTreeConflictDescription tc = parentArea
								.getTreeConflict(path.getName());
						if (tc != null) {
							if (choice != SVNConflictChoice.MERGED) {
								SVNErrorMessage err = SVNErrorMessage
										.create(
												SVNErrorCode.WC_CONFLICT_RESOLVER_FAILURE,
												"Tree conflicts can only be resolved to ''working'' state; ''{0}'' not resolved",
												path);
								SVNErrorManager.error(err, SVNLogType.WC);
							}
							parentArea.deleteTreeConflict(path.getName());
							kind = tc.getNodeKind();
							resolved = true;
						}
					}
					if (entry != null && (resolveContents || resolveProperties)) {
						kind = entry.getKind();
						revision = entry.getRevision();
						File conflictDir = entry.isDirectory() ? path : path
								.getParentFile();
						SVNAdminArea conflictArea = wcAccess
								.retrieve(conflictDir);
						resolved |= conflictArea.markResolved(entry.getName(),
								resolveContents, resolveProperties, choice);
					}
					if (resolved) {
						SVNEvent event = SVNEventFactory.createSVNEvent(path,
								kind, null, revision, SVNEventAction.RESOLVED,
								null, null, null);
						dispatchEvent(event);
					}
				}

				public void handleError(File path, SVNErrorMessage error)
						throws SVNException {
					SVNErrorManager.error(error, SVNLogType.WC);
				}
			};
			if (depth == SVNDepth.EMPTY) {
				SVNEntry entry = wcAccess.getEntry(path, false);
				if (entry != null) {
					resolveEntryHandler.handleEntry(path, entry);
				} else {
					SVNTreeConflictDescription tc = wcAccess
							.getTreeConflict(path);
					if (tc != null) {
						resolveEntryHandler.handleEntry(path, null);
					} else {
						SVNErrorMessage err = SVNErrorMessage.create(
								SVNErrorCode.ENTRY_NOT_FOUND,
								"''{0}'' is not under version control", path);
						SVNErrorManager.error(err, SVNLogType.WC);
					}
				}
			} else {
				wcAccess.walkEntries(path, resolveEntryHandler, false, true,
						depth);
			}
		} finally {
			wcAccess.close();
		}
	}

	/** 
	 * @from org.tmatesoft.svn.core.wc.SVNWCClient
	 */
	public void addDirectory(File wcRoot, File path, SVNAdminArea parentDir,
			boolean force, boolean noIgnore, SVNDepth depth, boolean setDepth)
			throws SVNException {
		checkCancelled();
		try {
			SVNWCManager.add(path, parentDir, null, SVNRevision.UNDEFINED,
					setDepth ? SVNDepth.INFINITY : null);
		} catch (SVNException e) {
			if (!(force && e.getErrorMessage().getErrorCode() == SVNErrorCode.ENTRY_EXISTS)) {
				throw e;
			}
		}
		SVNWCAccess access = parentDir.getWCAccess();
		SVNAdminArea dir = access.retrieve(path);
		Collection ignores = Collections.EMPTY_SET;
		if (!noIgnore) {
			ignores = SVNStatusEditor.getIgnorePatterns(dir, SVNStatusEditor
					.getGlobalIgnores(getOptions()));
		}
		String relativePath = SVNPathUtil.getRelativePath(wcRoot
				.getAbsolutePath().replace(File.separatorChar, '/'), dir
				.getRoot().getAbsolutePath().replace(File.separatorChar, '/'));
		relativePath = relativePath != null ? "/" + relativePath : null;
		File[] children = SVNFileListUtil.listFiles(dir.getRoot());
		for (int i = 0; children != null && i < children.length; i++) {
			checkCancelled();
			if (SVNFileUtil.getAdminDirectoryName().equals(
					children[i].getName())) {
				continue;
			}
			if (!noIgnore) {
				String rootRelativePath = relativePath != null ? SVNPathUtil
						.append(relativePath, children[i].getName()) : null;
				if (SVNStatusEditor.isIgnored(ignores, children[i],
						rootRelativePath)) {
					continue;
				}
			}
			SVNFileType childType = SVNFileType.getType(children[i]);
			if (childType == SVNFileType.DIRECTORY
					&& depth.compareTo(SVNDepth.IMMEDIATES) >= 0) {
				SVNDepth depthBelowHere = depth;
				if (depth == SVNDepth.IMMEDIATES) {
					depthBelowHere = SVNDepth.EMPTY;
				}
				addDirectory(wcRoot, children[i], dir, force, noIgnore,
						depthBelowHere, setDepth);
			} else if (childType != SVNFileType.UNKNOWN
					&& childType != SVNFileType.DIRECTORY
					&& depth.compareTo(SVNDepth.FILES) >= 0) {
				try {
					addFile(children[i], childType, dir);
				} catch (SVNException e) {
					if (force
							&& e.getErrorMessage().getErrorCode() == SVNErrorCode.ENTRY_EXISTS) {
						continue;
					}
					throw e;
				}
			}
		}
	}

	/** 
	 * Crawls the working copy at <code>path</code> and calls {@link ISVNPropertyValueProvider#providePropertyValues(java.io.File,org.tmatesoft.svn.core.SVNProperties)}to get properties to be change on each path being traversed
	 * <p/>
	 * If <code>depth</code> is {@link org.tmatesoft.svn.core.SVNDepth#EMPTY}, change the properties on <code>path</code>
	 * only; if {@link SVNDepth#FILES}, change the properties on <code>path</code> and its file
	 * children (if any); if {@link SVNDepth#IMMEDIATES}, on <code>path</code> and all
	 * of its immediate children (both files and directories); if{@link SVNDepth#INFINITY}, on <code>path</code> and everything beneath it.
	 * <p/>
	 * If <code>skipChecks</code> is <span class="javakeyword">true</span>, this method does no validity
	 * checking of changed properties.  But if <code>skipChecks</code> is <span class="javakeyword">false</span>,
	 * and changed property name is not a valid property for <code>path</code>, it throws an exception,
	 * either with an error code {@link org.tmatesoft.svn.core.SVNErrorCode#ILLEGAL_TARGET}(if the property is not appropriate for <code>path</code>), or with{@link org.tmatesoft.svn.core.SVNErrorCode#BAD_MIME_TYPE} (if changed propery name is
	 * <span class="javastring">"svn:mime-type"</span>, but changed property value is not a valid mime-type).
	 * <p/>
	 * <code>changeLists</code> is a collection of <code>String</code> changelist
	 * names, used as a restrictive filter on items whose properties are
	 * set; that is, don't set properties on any item unless it's a member
	 * of one of those changelists.  If <code>changelists</code> is empty (or
	 * <span class="javakeyword">null</span>), no changelist filtering occurs.
	 * <p>
	 * This method operates only on working copies and does not open any network connection.
	 * @param path                         working copy path
	 * @param propertyValueProvider        changed properties provider
	 * @param skipChecks                   <span class="javakeyword">true</span> to
	 * force the operation to run without validity checking
	 * @param depth                        working copy tree depth to process
	 * @param handler                      a caller's property handler
	 * @param changeLists                  changelist names
	 * @throws SVNException                <ul>
	 * <li><code>path</code> does not exist
	 * <li>exception with {@link SVNErrorCode#CLIENT_PROPERTY_NAME} error code -
	 * if changed property name is a revision property name or not a valid property name or
	 * not a regular property name (one starting with
	 * a <span class="javastring">"svn:entry"</span> or
	 * <span class="javastring">"svn:wc"</span> prefix)
	 * </ul>
	 * @see #doSetProperty(java.io.File,String,org.tmatesoft.svn.core.SVNPropertyValue,boolean,org.tmatesoft.svn.core.SVNDepth,ISVNPropertyHandler,java.util.Collection) 
	 * @since 1.2, SVN 1.5
	 * @from org.tmatesoft.svn.core.wc.SVNWCClient
	 */
	public void doSetProperty(File path,
			ISVNPropertyValueProvider propertyValueProvider,
			boolean skipChecks, SVNDepth depth, ISVNPropertyHandler handler,
			Collection changeLists) throws SVNException {
		depth = depth == null ? SVNDepth.UNKNOWN : depth;
		int admLockLevel = SVNWCAccess.INFINITE_DEPTH;
		if (depth == SVNDepth.EMPTY || depth == SVNDepth.FILES) {
			admLockLevel = 0;
		}
		SVNWCAccess wcAccess = createWCAccess();
		try {
			wcAccess.probeOpen(path, true, admLockLevel);
			SVNEntry entry = wcAccess.getVersionedEntry(path, false);
			if (SVNDepth.FILES.compareTo(depth) <= 0 && entry.isDirectory()) {
				PropSetHandlerExt entryHandler = new PropSetHandlerExt(
						skipChecks, propertyValueProvider, handler, changeLists);
				wcAccess.walkEntries(path, entryHandler, false, depth);
			} else if (SVNWCAccess.matchesChangeList(changeLists, entry)) {
				SVNAdminArea adminArea = entry.getAdminArea();
				setLocalProperties(path, entry, adminArea, skipChecks,
						propertyValueProvider, handler);
			}
		} finally {
			wcAccess.close();
		}
	}

	/** 
	 * Restores the pristine version of working copy <code>paths</code>,
	 * effectively undoing any local mods. For each path in <code>paths</code>,
	 * reverts it if it is a file. Else if it is a directory, reverts
	 * according to <code>depth</code>:
	 * <p/>
	 * If </code>depth</code> is {@link SVNDepth#EMPTY}, reverts just the properties on
	 * the directory; else if {@link SVNDepth#FILES}, reverts the properties
	 * and any files immediately under the directory; else if{@link SVNDepth#IMMEDIATES}, reverts all of the preceding plus
	 * properties on immediate subdirectories; else if {@link SVNDepth#INFINITY},
	 * reverts path and everything under it fully recursively.
	 * <p/>
	 * <code>changeLists</code> is a collection of <code>String</code> changelist
	 * names, used as a restrictive filter on items reverted; that is,
	 * doesn't revert any item unless it's a member of one of those
	 * changelists.  If <code>changeLists</code> is empty (or <span class="javakeyword">null</span>),
	 * no changelist filtering occurs.
	 * <p/>
	 * If an item specified for reversion is not under version control,
	 * then does not fail with an exception, just invokes {@link ISVNEventHandler} 
	 * using notification code {@link SVNEventAction#SKIP}.
	 * @param paths           working copy paths to revert
	 * @param depth           tree depth
	 * @param changeLists     collection with changelist names
	 * @throws SVNException 
	 * @since 1.2, SVN 1.5
	 * @from org.tmatesoft.svn.core.wc.SVNWCClient
	 */
	public void doRevert(File[] paths, SVNDepth depth, Collection changeLists)
			throws SVNException {
		boolean reverted = false;
		try {
			for (int i = 0; i < paths.length; i++) {
				File path = paths[i];
				path = path.getAbsoluteFile();
				SVNWCAccess wcAccess = createWCAccess();
				try {
					int admLockLevel = getLevelsToLockFromDepth(depth);
					SVNAdminAreaInfo info = wcAccess.openAnchor(path, true,
							admLockLevel);
					SVNEntry entry = wcAccess.getEntry(path, false);
					if (entry != null && entry.isDirectory()
							&& entry.isScheduledForAddition()) {
						if (depth != SVNDepth.INFINITY) {
							getDebugLog().logFine(
									SVNLogType.WC,
									"Forcing revert on path '" + path
											+ "' to recurse");
							depth = SVNDepth.INFINITY;
							wcAccess.close();
							info = wcAccess.openAnchor(path, true,
									SVNWCAccess.INFINITE_DEPTH);
						}
					}
					boolean useCommitTimes = getOptions().isUseCommitTimes();
					reverted |= doRevert(path, info.getAnchor(), depth,
							useCommitTimes, changeLists);
				} catch (SVNException e) {
					reverted |= true;
					SVNErrorCode code = e.getErrorMessage().getErrorCode();
					if (code == SVNErrorCode.ENTRY_NOT_FOUND
							|| code == SVNErrorCode.UNVERSIONED_RESOURCE) {
						SVNEvent event = SVNEventFactory.createSVNEvent(path,
								SVNNodeKind.UNKNOWN, null,
								SVNRepository.INVALID_REVISION,
								SVNEventAction.SKIP, SVNEventAction.REVERT,
								null, null);
						dispatchEvent(event);
						continue;
					}
					throw e;
				} finally {
					wcAccess.close();
				}
			}
		} finally {
			if (reverted) {
				sleepForTimeStamp();
			}
		}
	}

	/** 
	 * Returns the add parameters object used by this object.
	 * If no custom object was specified through a call to {@link #setAddParameters(ISVNAddParameters)} 
	 * then {@link #DEFAULT_ADD_PARAMETERS} is returned.
	 * @return add parameters object
	 * @since 1.2
	 * @from org.tmatesoft.svn.core.wc.SVNWCClient
	 */
	public ISVNAddParameters getAddParameters() {
		if (myAddParameters == null) {
			return DEFAULT_ADD_PARAMETERS;
		}
		return myAddParameters;
	}

	/** 
	 * @from org.tmatesoft.svn.core.wc.SVNWCClient
	 */
	public boolean isRevertMissingDirectories() {
		return myIsRevertMissingDirectories;
	}

	private class PropFetchHandler implements ISVNEntryHandler {
		private Collection myChangeLists;
		private boolean myIsPristine;
		private String myPropName;
		private ISVNPropertyHandler myPropHandler;

		public PropFetchHandler(Collection changeLists, String propName,
				ISVNPropertyHandler handler, boolean pristine) {
			myChangeLists = changeLists;
			myIsPristine = pristine;
			myPropName = propName;
			myPropHandler = handler;
		}

		public void handleEntry(File path, SVNEntry entry) throws SVNException {
			SVNAdminArea adminArea = entry.getAdminArea();
			if (entry.isDirectory() && !entry.isThisDir()) {
				return;
			}
			if ((myIsPristine && entry.isScheduledForAddition())
					|| (!myIsPristine && entry.isScheduledForDeletion())) {
				return;
			}
			if (!SVNWCAccess.matchesChangeList(myChangeLists, entry)) {
				return;
			}
			SVNVersionedProperties properties = myIsPristine ? adminArea
					.getBaseProperties(entry.getName()) : adminArea
					.getProperties(entry.getName());
			if (myPropName != null) {
				SVNPropertyValue propValue = properties
						.getPropertyValue(myPropName);
				if (propValue != null) {
					myPropHandler.handleProperty(path, new SVNPropertyData(
							myPropName, propValue, getOptions()));
				}
			} else {
				SVNProperties allProps = properties.asMap();
				for (Iterator names = allProps.nameSet().iterator(); names
						.hasNext();) {
					String name = (String) names.next();
					SVNPropertyValue val = allProps.getSVNPropertyValue(name);
					myPropHandler.handleProperty(path, new SVNPropertyData(
							name, val, getOptions()));
				}
			}
		}

		public void handleError(File path, SVNErrorMessage error)
				throws SVNException {
			SVNErrorManager.error(error, SVNLogType.WC);
		}
	}

	private static class LockInfo {
		public LockInfo(File file, SVNRevision rev) {
			myFile = file;
			myRevision = rev;
		}

		public LockInfo(File file, String token) {
			myFile = file;
			myToken = token;
		}

		private File myFile;
		private SVNRevision myRevision;
		private String myToken;
	}

	private class PropSetHandler implements ISVNEntryHandler {
		private boolean myIsForce;
		private String myPropName;
		private SVNPropertyValue myPropValue;
		private ISVNPropertyHandler myPropHandler;
		private Collection myChangeLists;

		public PropSetHandler(boolean isForce, String propName,
				SVNPropertyValue propValue, ISVNPropertyHandler handler,
				Collection changeLists) {
			myIsForce = isForce;
			myPropName = propName;
			myPropValue = propValue;
			myPropHandler = handler;
			myChangeLists = changeLists;
		}

		public void handleEntry(File path, SVNEntry entry) throws SVNException {
			SVNAdminArea adminArea = entry.getAdminArea();
			if (entry.isDirectory()
					&& !adminArea.getThisDirName().equals(entry.getName())) {
				return;
			}
			if (entry.isScheduledForDeletion()) {
				return;
			}
			if (!SVNWCAccess.matchesChangeList(myChangeLists, entry)) {
				return;
			}
			try {
				boolean modified = SVNPropertiesManager.setProperty(adminArea
						.getWCAccess(), path, myPropName, myPropValue,
						myIsForce);
				if (modified && myPropHandler != null) {
					myPropHandler.handleProperty(path, new SVNPropertyData(
							myPropName, myPropValue, getOptions()));
				}
			} catch (SVNException svne) {
				if (svne.getErrorMessage().getErrorCode() != SVNErrorCode.ILLEGAL_TARGET) {
					throw svne;
				}
			}
		}

		public void handleError(File path, SVNErrorMessage error)
				throws SVNException {
			SVNErrorManager.error(error, SVNLogType.WC);
		}
	}

	private class PropSetHandlerExt implements ISVNEntryHandler {
		private boolean myIsForce;
		private ISVNPropertyValueProvider myPropValueProvider;
		private ISVNPropertyHandler myPropHandler;
		private Collection myChangeLists;

		public PropSetHandlerExt(boolean isForce,
				ISVNPropertyValueProvider propertyValueProvider,
				ISVNPropertyHandler handler, Collection changeLists) {
			myIsForce = isForce;
			myPropValueProvider = propertyValueProvider;
			myPropHandler = handler;
			myChangeLists = changeLists;
		}

		public void handleEntry(File path, SVNEntry entry) throws SVNException {
			SVNAdminArea adminArea = entry.getAdminArea();
			if (entry.isDirectory()
					&& !adminArea.getThisDirName().equals(entry.getName())) {
				return;
			}
			if (entry.isScheduledForDeletion()) {
				return;
			}
			if (!SVNWCAccess.matchesChangeList(myChangeLists, entry)) {
				return;
			}
			setLocalProperties(path, entry, adminArea, myIsForce,
					myPropValueProvider, myPropHandler);
		}

		public void handleError(File path, SVNErrorMessage error)
				throws SVNException {
			SVNErrorManager.error(error, SVNLogType.WC);
		}
	}
}
