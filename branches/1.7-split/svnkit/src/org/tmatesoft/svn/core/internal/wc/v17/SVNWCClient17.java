package org.tmatesoft.svn.core.internal.wc.v17;

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

public class SVNWCClient17 extends SVNWCClient {
	protected SVNWCClient dispatcher;

	protected SVNWCClient17(SVNWCClient from) {
		super(from);
		this.dispatcher = dispatcher;
	}

	public static SVNWCClient17 delegate(SVNWCClient dispatcher) {
		SVNWCClient17 delegate = new SVNWCClient17(dispatcher);
		return delegate;
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
		SVNErrorMessage err = SVNErrorMessage
				.create(SVNErrorCode.VERSION_MISMATCH);
		SVNErrorManager.error(err, SVNLogType.CLIENT);
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
		SVNErrorMessage err = SVNErrorMessage
				.create(SVNErrorCode.VERSION_MISMATCH);
		SVNErrorManager.error(err, SVNLogType.CLIENT);
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
		SVNErrorMessage err = SVNErrorMessage
				.create(SVNErrorCode.VERSION_MISMATCH);
		SVNErrorManager.error(err, SVNLogType.CLIENT);
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
		SVNErrorMessage err = SVNErrorMessage
				.create(SVNErrorCode.VERSION_MISMATCH);
		SVNErrorManager.error(err, SVNLogType.CLIENT);
	}

	/** 
	 * @from org.tmatesoft.svn.core.wc.SVNWCClient
	 */
	public boolean revert(SVNAdminArea dir, String name, SVNEntry entry,
			boolean useCommitTime) throws SVNException {
		SVNErrorMessage err = SVNErrorMessage
				.create(SVNErrorCode.VERSION_MISMATCH);
		SVNErrorManager.error(err, SVNLogType.CLIENT);
		return false;
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
		SVNErrorMessage err = SVNErrorMessage
				.create(SVNErrorCode.VERSION_MISMATCH);
		SVNErrorManager.error(err, SVNLogType.CLIENT);
	}

	/** 
	 * @from org.tmatesoft.svn.core.wc.SVNWCClient
	 */
	public void pushDirInfo(SVNRepository repos, SVNRevision rev, String path,
			SVNURL root, String uuid, SVNURL url, Map locks, SVNDepth depth,
			ISVNInfoHandler handler) throws SVNException {
		SVNErrorMessage err = SVNErrorMessage
				.create(SVNErrorCode.VERSION_MISMATCH);
		SVNErrorManager.error(err, SVNLogType.CLIENT);
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
		SVNErrorMessage err = SVNErrorMessage
				.create(SVNErrorCode.VERSION_MISMATCH);
		SVNErrorManager.error(err, SVNLogType.CLIENT);
	}

	/** 
	 * @from org.tmatesoft.svn.core.wc.SVNWCClient
	 */
	public void doGetRemoteProperty(SVNURL url, String path,
			SVNRepository repos, String propName, SVNRevision rev,
			SVNDepth depth, ISVNPropertyHandler handler) throws SVNException {
		SVNErrorMessage err = SVNErrorMessage
				.create(SVNErrorCode.VERSION_MISMATCH);
		SVNErrorManager.error(err, SVNLogType.CLIENT);
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
		SVNErrorMessage err = SVNErrorMessage
				.create(SVNErrorCode.VERSION_MISMATCH);
		SVNErrorManager.error(err, SVNLogType.CLIENT);
	}

	/** 
	 * @from org.tmatesoft.svn.core.wc.SVNWCClient
	 */
	public SVNURL collectLockInfo(SVNWCAccess wcAccess, File[] files,
			Map lockInfo, Map lockPaths, boolean lock, boolean stealLock)
			throws SVNException {
		SVNErrorMessage err = SVNErrorMessage
				.create(SVNErrorCode.VERSION_MISMATCH);
		SVNErrorManager.error(err, SVNLogType.CLIENT);
		return null;
	}

	/** 
	 * @from org.tmatesoft.svn.core.wc.SVNWCClient
	 */
	public SVNAdminArea addParentDirectories(SVNWCAccess wcAccess, File path)
			throws SVNException {
		SVNErrorMessage err = SVNErrorMessage
				.create(SVNErrorCode.VERSION_MISMATCH);
		SVNErrorManager.error(err, SVNLogType.CLIENT);
		return null;
	}

	/** 
	 * @from org.tmatesoft.svn.core.wc.SVNWCClient
	 */
	public boolean doRevert(File path, SVNAdminArea parent, SVNDepth depth,
			boolean useCommitTimes, Collection changeLists) throws SVNException {
		SVNErrorMessage err = SVNErrorMessage
				.create(SVNErrorCode.VERSION_MISMATCH);
		SVNErrorManager.error(err, SVNLogType.CLIENT);
		return false;
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
		SVNErrorMessage err = SVNErrorMessage
				.create(SVNErrorCode.VERSION_MISMATCH);
		SVNErrorManager.error(err, SVNLogType.CLIENT);
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
		SVNErrorMessage err = SVNErrorMessage
				.create(SVNErrorCode.VERSION_MISMATCH);
		SVNErrorManager.error(err, SVNLogType.CLIENT);
	}

	/** 
	 * @from org.tmatesoft.svn.core.wc.SVNWCClient
	 */
	public void setLocalProperties(File path, SVNEntry entry,
			SVNAdminArea adminArea, boolean force,
			ISVNPropertyValueProvider propertyValueProvider,
			ISVNPropertyHandler handler) throws SVNException {
		SVNErrorMessage err = SVNErrorMessage
				.create(SVNErrorCode.VERSION_MISMATCH);
		SVNErrorManager.error(err, SVNLogType.CLIENT);
	}

	/** 
	 * @from org.tmatesoft.svn.core.wc.SVNWCClient
	 */
	public void addFile(File path, SVNFileType type, SVNAdminArea dir)
			throws SVNException {
		SVNErrorMessage err = SVNErrorMessage
				.create(SVNErrorCode.VERSION_MISMATCH);
		SVNErrorManager.error(err, SVNLogType.CLIENT);
	}

	/** 
	 * @from org.tmatesoft.svn.core.wc.SVNWCClient
	 */
	public void doGetLocalProperty(SVNEntry entry, SVNAdminArea area,
			String propName, boolean base, ISVNPropertyHandler handler,
			SVNDepth depth, Collection changeLists) throws SVNException {
		SVNErrorMessage err = SVNErrorMessage
				.create(SVNErrorCode.VERSION_MISMATCH);
		SVNErrorManager.error(err, SVNLogType.CLIENT);
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
		SVNErrorMessage err = SVNErrorMessage
				.create(SVNErrorCode.VERSION_MISMATCH);
		SVNErrorManager.error(err, SVNLogType.CLIENT);
		return null;
	}

	/** 
	 * @from org.tmatesoft.svn.core.wc.SVNWCClient
	 */
	public void reportEntry(File path, SVNEntry entry, ISVNInfoHandler handler)
			throws SVNException {
		SVNErrorMessage err = SVNErrorMessage
				.create(SVNErrorCode.VERSION_MISMATCH);
		SVNErrorManager.error(err, SVNLogType.CLIENT);
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
		SVNErrorMessage err = SVNErrorMessage
				.create(SVNErrorCode.VERSION_MISMATCH);
		SVNErrorManager.error(err, SVNLogType.CLIENT);
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
		SVNErrorMessage err = SVNErrorMessage
				.create(SVNErrorCode.VERSION_MISMATCH);
		SVNErrorManager.error(err, SVNLogType.CLIENT);
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
		SVNErrorMessage err = SVNErrorMessage
				.create(SVNErrorCode.VERSION_MISMATCH);
		SVNErrorManager.error(err, SVNLogType.CLIENT);
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
		SVNErrorMessage err = SVNErrorMessage
				.create(SVNErrorCode.VERSION_MISMATCH);
		SVNErrorManager.error(err, SVNLogType.CLIENT);
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
		SVNErrorMessage err = SVNErrorMessage
				.create(SVNErrorCode.VERSION_MISMATCH);
		SVNErrorManager.error(err, SVNLogType.CLIENT);
	}

	/** 
	 * @from org.tmatesoft.svn.core.wc.SVNWCClient
	 */
	public void crawlEntries(File path, SVNDepth depth,
			final Collection changeLists, final ISVNInfoHandler handler)
			throws SVNException {
		SVNErrorMessage err = SVNErrorMessage
				.create(SVNErrorCode.VERSION_MISMATCH);
		SVNErrorManager.error(err, SVNLogType.CLIENT);
	}

	/** 
	 * @from org.tmatesoft.svn.core.wc.SVNWCClient
	 */
	public void doGetLocalFileContents(File path, OutputStream dst,
			SVNRevision revision, boolean expandKeywords) throws SVNException {
		SVNErrorMessage err = SVNErrorMessage
				.create(SVNErrorCode.VERSION_MISMATCH);
		SVNErrorManager.error(err, SVNLogType.CLIENT);
	}

	/** 
	 * @from org.tmatesoft.svn.core.wc.SVNWCClient
	 */
	public void setWCFormat(SVNAdminAreaInfo info, SVNAdminArea area, int format)
			throws SVNException {
		SVNErrorMessage err = SVNErrorMessage
				.create(SVNErrorCode.VERSION_MISMATCH);
		SVNErrorManager.error(err, SVNLogType.CLIENT);
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
		SVNErrorMessage err = SVNErrorMessage
				.create(SVNErrorCode.VERSION_MISMATCH);
		SVNErrorManager.error(err, SVNLogType.CLIENT);
	}

	/** 
	 * @from org.tmatesoft.svn.core.wc.SVNWCClient
	 */
	public void addDirectory(File wcRoot, File path, SVNAdminArea parentDir,
			boolean force, boolean noIgnore, SVNDepth depth, boolean setDepth)
			throws SVNException {
		SVNErrorMessage err = SVNErrorMessage
				.create(SVNErrorCode.VERSION_MISMATCH);
		SVNErrorManager.error(err, SVNLogType.CLIENT);
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
		SVNErrorMessage err = SVNErrorMessage
				.create(SVNErrorCode.VERSION_MISMATCH);
		SVNErrorManager.error(err, SVNLogType.CLIENT);
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
		SVNErrorMessage err = SVNErrorMessage
				.create(SVNErrorCode.VERSION_MISMATCH);
		SVNErrorManager.error(err, SVNLogType.CLIENT);
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
		return null;
	}

	/** 
	 * @from org.tmatesoft.svn.core.wc.SVNWCClient
	 */
	public boolean isRevertMissingDirectories() {
		return false;
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
