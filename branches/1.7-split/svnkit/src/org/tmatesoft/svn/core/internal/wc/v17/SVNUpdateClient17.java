package org.tmatesoft.svn.core.internal.wc.v17;

import java.io.File;
import java.io.OutputStream;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import org.tmatesoft.svn.core.SVNCancelException;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.internal.util.SVNDate;
import org.tmatesoft.svn.core.internal.util.SVNEncodingUtil;
import org.tmatesoft.svn.core.internal.util.SVNHashMap;
import org.tmatesoft.svn.core.internal.util.SVNHashSet;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.ISVNFileFetcher;
import org.tmatesoft.svn.core.internal.wc.ISVNUpdateEditor;
import org.tmatesoft.svn.core.internal.wc.SVNAmbientDepthFilterEditor;
import org.tmatesoft.svn.core.internal.wc.SVNCancellableEditor;
import org.tmatesoft.svn.core.internal.wc.SVNCancellableOutputStream;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNEventFactory;
import org.tmatesoft.svn.core.internal.wc.SVNExportEditor;
import org.tmatesoft.svn.core.internal.wc.SVNExternal;
import org.tmatesoft.svn.core.internal.wc.SVNFileType;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc.SVNPropertiesManager;
import org.tmatesoft.svn.core.internal.wc.SVNWCManager;
import org.tmatesoft.svn.core.internal.wc.admin.SVNAdminArea;
import org.tmatesoft.svn.core.internal.wc.admin.SVNAdminAreaFactory;
import org.tmatesoft.svn.core.internal.wc.admin.SVNAdminAreaInfo;
import org.tmatesoft.svn.core.internal.wc.admin.SVNEntry;
import org.tmatesoft.svn.core.internal.wc.admin.SVNReporter;
import org.tmatesoft.svn.core.internal.wc.admin.SVNTranslator;
import org.tmatesoft.svn.core.internal.wc.admin.SVNVersionedProperties;
import org.tmatesoft.svn.core.internal.wc.admin.SVNWCAccess;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.ISVNReporter;
import org.tmatesoft.svn.core.io.ISVNReporterBaton;
import org.tmatesoft.svn.core.io.SVNCapability;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.wc.ISVNEventHandler;
import org.tmatesoft.svn.core.wc.ISVNExternalsHandler;
import org.tmatesoft.svn.core.wc.ISVNOptions;
import org.tmatesoft.svn.core.wc.ISVNRepositoryPool;
import org.tmatesoft.svn.core.wc.SVNEvent;
import org.tmatesoft.svn.core.wc.SVNEventAction;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNUpdateClient;
import org.tmatesoft.svn.core.wc.SVNUpdateClient;
import org.tmatesoft.svn.util.ISVNDebugLog;
import org.tmatesoft.svn.util.SVNDebugLog;
import org.tmatesoft.svn.util.SVNLogType;

public class SVNUpdateClient17 extends SVNUpdateClient {
	protected SVNUpdateClient dispatcher;

	protected SVNUpdateClient17(SVNUpdateClient from) {
		super(from);
		this.dispatcher = dispatcher;
	}

	public static SVNUpdateClient17 delegate(SVNUpdateClient dispatcher) {
		SVNUpdateClient17 delegate = new SVNUpdateClient17(dispatcher);
		return delegate;
	}

	/** 
	 * @from org.tmatesoft.svn.core.wc.SVNUpdateClient
	 */
	static public boolean canonicalizeEntry(SVNEntry entry,
			boolean omitDefaultPort) throws SVNException {
		SVNErrorMessage err = SVNErrorMessage
				.create(SVNErrorCode.VERSION_MISMATCH);
		SVNErrorManager.error(err, SVNLogType.CLIENT);
		return false;
	}

	/** 
	 * @from org.tmatesoft.svn.core.wc.SVNUpdateClient
	 */
	public void doCanonicalizeURLs(SVNAdminAreaInfo adminAreaInfo,
			SVNAdminArea adminArea, String name, boolean omitDefaultPort,
			boolean recursive) throws SVNException {
		SVNErrorMessage err = SVNErrorMessage
				.create(SVNErrorCode.VERSION_MISMATCH);
		SVNErrorManager.error(err, SVNLogType.CLIENT);
	}

	/** 
	 * @from org.tmatesoft.svn.core.wc.SVNUpdateClient
	 */
	public SVNURL getOwnerURL(File root) {
		return null;
	}

	/** 
	 * @from org.tmatesoft.svn.core.wc.SVNUpdateClient
	 */
	static public String canonicalizeExtenrals(String externals,
			boolean omitDefaultPort) throws SVNException {
		SVNErrorMessage err = SVNErrorMessage
				.create(SVNErrorCode.VERSION_MISMATCH);
		SVNErrorManager.error(err, SVNLogType.CLIENT);
		return null;
	}

	/** 
	 * @from org.tmatesoft.svn.core.wc.SVNUpdateClient
	 */
	public void handleExternals(SVNWCAccess wcAccess, File root,
			Map oldExternals, Map newExternals, Map depths, SVNURL fromURL,
			SVNURL rootURL, SVNDepth requestedDepth, boolean isExport,
			boolean updateUnchanged) throws SVNException {
		SVNErrorMessage err = SVNErrorMessage
				.create(SVNErrorCode.VERSION_MISMATCH);
		SVNErrorManager.error(err, SVNLogType.CLIENT);
	}

	/** 
	 * @from org.tmatesoft.svn.core.wc.SVNUpdateClient
	 */
	public void copyVersionedFile(File dstPath, SVNAdminArea adminArea,
			String fileName, SVNRevision revision, String eol)
			throws SVNException {
		SVNErrorMessage err = SVNErrorMessage
				.create(SVNErrorCode.VERSION_MISMATCH);
		SVNErrorManager.error(err, SVNLogType.CLIENT);
	}

	/** 
	 * @from org.tmatesoft.svn.core.wc.SVNUpdateClient
	 */
	public long doSwitchImpl(SVNWCAccess wcAccess, File path, SVNURL url,
			SVNRevision pegRevision, SVNRevision revision, SVNDepth depth,
			boolean allowUnversionedObstructions, boolean depthIsSticky)
			throws SVNException {
		SVNErrorMessage err = SVNErrorMessage
				.create(SVNErrorCode.VERSION_MISMATCH);
		SVNErrorManager.error(err, SVNLogType.CLIENT);
		return 0;
	}

	/** 
	 * Says whether the entire working copy should be locked while updating or not.
	 * <p/>
	 * If this method returns <span class="javakeyword">false</span>, then the working copy will be 
	 * closed for all paths involved in the update. Otherwise only those working copy subdirectories 
	 * will be locked, which will be either changed by the update or which contain deleted files
	 * that should be restored during the update; all other versioned subdirectories than won't be 
	 * touched by the update will remain opened for read only access without locking. 
	 * <p/>
	 * Locking working copies on demand is intended to improve update performance for large working 
	 * copies because even a no-op update on a huge working copy always locks the entire tree by default.
	 * And locking a working copy tree means opening special lock files for privileged access for all 
	 * subdirectories involved. This makes an update process work slower. Locking wc on demand 
	 * feature suggests such a workaround to enhance update performance.
	 * @return  <span class="javakeyword">true</span> when locking wc on demand
	 * @from org.tmatesoft.svn.core.wc.SVNUpdateClient
	 */
	public boolean isUpdateLocksOnDemand() {
		return false;
	}

	/** 
	 * @from org.tmatesoft.svn.core.wc.SVNUpdateClient
	 */
	public long doRemoteExport(SVNRepository repository, final long revNumber,
			File dstPath, String eolStyle, boolean force, SVNDepth depth)
			throws SVNException {
		SVNErrorMessage err = SVNErrorMessage
				.create(SVNErrorCode.VERSION_MISMATCH);
		SVNErrorManager.error(err, SVNLogType.CLIENT);
		return 0;
	}

	/** 
	 * @from org.tmatesoft.svn.core.wc.SVNUpdateClient
	 */
	static public SVNURL canonicalizeURL(SVNURL url, boolean omitDefaultPort)
			throws SVNException {
		SVNErrorMessage err = SVNErrorMessage
				.create(SVNErrorCode.VERSION_MISMATCH);
		SVNErrorManager.error(err, SVNLogType.CLIENT);
		return null;
	}

	/** 
	 * Substitutes the beginning part of a Working Copy's URL with a new one.
	 * <p> 
	 * When a repository root location or a URL schema is changed the old URL of the 
	 * Working Copy which starts with <code>oldURL</code> should be substituted for a
	 * new URL beginning - <code>newURL</code>.
	 * @param dst				a Working Copy item's path 
	 * @param oldURL			the old beginning part of the repository's URL that should
	 * be overwritten  
	 * @param newURL			a new beginning part for the repository location that
	 * will overwrite <code>oldURL</code> 
	 * @param recursive		if <span class="javakeyword">true</span> and <code>dst</code> is
	 * a directory then the entire tree will be relocated, otherwise if 
	 * <span class="javakeyword">false</span> - only <code>dst</code> itself
	 * @throws SVNException
	 * @from org.tmatesoft.svn.core.wc.SVNUpdateClient
	 */
	public void doRelocate(File dst, SVNURL oldURL, SVNURL newURL,
			boolean recursive) throws SVNException {
		SVNErrorMessage err = SVNErrorMessage
				.create(SVNErrorCode.VERSION_MISMATCH);
		SVNErrorManager.error(err, SVNLogType.CLIENT);
	}

	/** 
	 * @from org.tmatesoft.svn.core.wc.SVNUpdateClient
	 */
	public Map validateRelocateTargetURL(SVNURL targetURL, String expectedUUID,
			Map validatedURLs, boolean isRoot) throws SVNException {
		SVNErrorMessage err = SVNErrorMessage
				.create(SVNErrorCode.VERSION_MISMATCH);
		SVNErrorManager.error(err, SVNLogType.CLIENT);
		return null;
	}

	/** 
	 * @from org.tmatesoft.svn.core.wc.SVNUpdateClient
	 */
	public void copyVersionedDir(File from, File to, SVNRevision revision,
			String eolStyle, boolean force, SVNDepth depth) throws SVNException {
		SVNErrorMessage err = SVNErrorMessage
				.create(SVNErrorCode.VERSION_MISMATCH);
		SVNErrorManager.error(err, SVNLogType.CLIENT);
	}

	/** 
	 * @from org.tmatesoft.svn.core.wc.SVNUpdateClient
	 */
	public void deleteExternal(File external) throws SVNException {
		SVNErrorMessage err = SVNErrorMessage
				.create(SVNErrorCode.VERSION_MISMATCH);
		SVNErrorManager.error(err, SVNLogType.CLIENT);
	}

	/** 
	 * Switches working tree <code>path</code> to <code>url</code>\<code>pegRevision</code> at 
	 * <code>revision</code>. 
	 * <p/>
	 * Summary of purpose: this is normally used to switch a working
	 * directory over to another line of development, such as a branch or
	 * a tag.  Switching an existing working directory is more efficient
	 * than checking out <code>url</code> from scratch.
	 * <p/>
	 * <code>revision</code> must represent a valid revision number ({@link SVNRevision#getNumber()} >= 0),
	 * or date ({@link SVNRevision#getDate()} != <span class="javakeyword">true</span>), or be equal to {@link SVNRevision#HEAD}. If <code>revision</code> does not meet these requirements, an exception with 
	 * the error code {@link SVNErrorCode#CLIENT_BAD_REVISION} is thrown.
	 * <p/>
	 * If <code>depth</code> is {@link SVNDepth#INFINITY}, switches fully recursively.
	 * Else if it is {@link SVNDepth#IMMEDIATES}, switches <code>path</code> and its file
	 * children (if any), and switches subdirectories but does not update
	 * them.  Else if {@link SVNDepth#FILES}, switches just file children,
	 * ignoring subdirectories completely. Else if {@link SVNDepth#EMPTY},
	 * switches just <code>path</code> and touches nothing underneath it.
	 * <p/>
	 * If <code>depthIsSticky</code> is set and <code>depth</code> is not {@link SVNDepth#UNKNOWN}, then in addition to switching <code>path</code>, also sets
	 * its sticky ambient depth value to <code>depth</code>.
	 * <p/>
	 * If externals are {@link #isIgnoreExternals() ignored}, doesn't process externals definitions
	 * as part of this operation.
	 * <p/>
	 * If <code>allowUnversionedObstructions</code> is <span class="javakeyword">true</span> then the switch 
	 * tolerates existing unversioned items that obstruct added paths. Only
	 * obstructions of the same type (file or dir) as the added item are
	 * tolerated. The text of obstructing files is left as-is, effectively
	 * treating it as a user modification after the switch. Working
	 * properties of obstructing items are set equal to the base properties.
	 * If <code>allowUnversionedObstructions</code> is <span class="javakeyword">false</span> then the switch 
	 * will abort if there are any unversioned obstructing items.
	 * <p/>
	 * If the caller's {@link ISVNEventHandler} is non-<span class="javakeyword">null</span>, it is invoked for 
	 * paths affected by the switch, and also for files restored from text-base. Also {@link ISVNEventHandler#checkCancelled()} will be used at various places during the switch to check 
	 * whether the caller wants to stop the switch.
	 * <p/>
	 * This operation requires repository access (in case the repository is not on the same machine, network
	 * connection is established).
	 * @param path                           the Working copy item to be switched
	 * @param url                            the repository location as a target against which the item will 
	 * be switched
	 * @param pegRevision                    a revision in which <code>path</code> is first looked up
	 * in the repository
	 * @param revision                       the desired revision of the repository target   
	 * @param depth                          tree depth to update
	 * @param allowUnversionedObstructions   flag that allows tollerating unversioned items 
	 * during update
	 * @param depthIsSticky                  flag that controls whether the requested depth 
	 * should be written into the working copy
	 * @return                                value of the revision to which the working copy was actually switched
	 * @throws SVNException 
	 * @since  1.2, SVN 1.5
	 * @from org.tmatesoft.svn.core.wc.SVNUpdateClient
	 */
	public long doSwitch(File path, SVNURL url, SVNRevision pegRevision,
			SVNRevision revision, SVNDepth depth,
			boolean allowUnversionedObstructions, boolean depthIsSticky)
			throws SVNException {
		SVNErrorMessage err = SVNErrorMessage
				.create(SVNErrorCode.VERSION_MISMATCH);
		SVNErrorManager.error(err, SVNLogType.CLIENT);
		return 0;
	}

	/** 
	 * Returns an externals handler used by this update client.
	 * <p/>
	 * If no user's handler is provided then {@link ISVNExternalsHandler#DEFAULT} is returned and 
	 * used by this client object by default.
	 * <p/>
	 * For more information what externals handlers are for, please, refer to {@link ISVNExternalsHandler}. 
	 * @return externals handler being in use
	 * @see #setExternalsHandler(ISVNExternalsHandler)
	 * @since 1.2 
	 * @from org.tmatesoft.svn.core.wc.SVNUpdateClient
	 */
	public ISVNExternalsHandler getExternalsHandler() {
		return null;
	}

	/** 
	 * Says whether keywords expansion during export operations is turned on or not.
	 * @return <span class="javakeyword">true</span> if expanding keywords;
	 * <span class="javakeyword">false</span> otherwise
	 * @since  1.3
	 * @from org.tmatesoft.svn.core.wc.SVNUpdateClient
	 */
	public boolean isExportExpandsKeywords() {
		return false;
	}

	/** 
	 * Updates working copy <code></code> to <code>revision</code>. 
	 * Unversioned paths that are direct children of a versioned path will cause an update that 
	 * attempts to add that path, other unversioned paths are skipped.
	 * <p/>
	 * <code>revision</code> must represent a valid revision number ({@link SVNRevision#getNumber()} >= 0),
	 * or date ({@link SVNRevision#getDate()} != <span class="javakeyword">true</span>), or be equal to {@link SVNRevision#HEAD}. If <code>revision</code> does not meet these requirements, an exception with 
	 * the error code {@link SVNErrorCode#CLIENT_BAD_REVISION} is thrown.
	 * <p/>
	 * If externals are {@link #isIgnoreExternals() ignored}, doesn't process externals definitions
	 * as part of this operation.
	 * <p/>
	 * If <code>depth</code> is {@link SVNDepth#INFINITY}, updates fully recursively.
	 * Else if it is {@link SVNDepth#IMMEDIATES} or {@link SVNDepth#FILES}, updates
	 * <code>path</code> and its file entries, but not its subdirectories. Else if {@link SVNDepth#EMPTY}, 
	 * updates exactly <code>path</code>, nonrecursively (essentially, updates the target's properties).
	 * <p/>
	 * If <code>depth</code> is {@link SVNDepth#UNKNOWN}, takes the working depth from
	 * <code>path</code> and then behaves as described above.
	 * <p/>
	 * If <code>depthIsSticky</code> is set and <code>depth</code> is not {@link SVNDepth#UNKNOWN}, 
	 * then in addition to updating <code>path</code>, also sets its sticky ambient depth value to 
	 * <code>depth</codes>.
	 * <p/>
	 * If <code>allowUnversionedObstructions</code> is <span class="javakeyword">true</span> then the update 
	 * tolerates existing unversioned items that obstruct added paths. Only obstructions of the same type 
	 * (file or dir) as the added item are tolerated. The text of obstructing files is left as-is, effectively
	 * treating it as a user modification after the update. Working properties of obstructing items are set 
	 * equal to the base properties. If <code>allowUnversionedObstructions</code> is 
	 * <span class="javakeyword">false</span> then the update will abort if there are any unversioned 
	 * obstructing items.
	 * <p/>
	 * If the caller's {@link ISVNEventHandler} is non-<span class="javakeyword">null</span>, it is invoked for 
	 * each item handled by the update, and also for files restored from text-base. Also {@link ISVNEventHandler#checkCancelled()} will be used at various places during the update to check 
	 * whether the caller wants to stop the update.
	 * <p/>
	 * This operation requires repository access (in case the repository is not on the same machine, network
	 * connection is established).
	 * @param path                           working copy path
	 * @param revision                       revision to update to
	 * @param depth                          tree depth to update
	 * @param allowUnversionedObstructions   flag that allows tollerating unversioned items 
	 * during update
	 * @param depthIsSticky                  flag that controls whether the requested depth 
	 * should be written to the working copy
	 * @return                                revision to which <code>revision</code> was resolved
	 * @throws SVNException 
	 * @since 1.2, SVN 1.5
	 * @from org.tmatesoft.svn.core.wc.SVNUpdateClient
	 */
	public long doUpdate(File path, SVNRevision revision, SVNDepth depth,
			boolean allowUnversionedObstructions, boolean depthIsSticky)
			throws SVNException {
		SVNErrorMessage err = SVNErrorMessage
				.create(SVNErrorCode.VERSION_MISMATCH);
		SVNErrorManager.error(err, SVNLogType.CLIENT);
		return 0;
	}

	/** 
	 * Exports the contents of a subversion repository into a 'clean' directory (meaning a
	 * directory with no administrative directories). 
	 * <p/>
	 * <code>pegRevision</code> is the revision where the path is first looked up. 
	 * If <code>pegRevision</code> is {@link SVNRevision#UNDEFINED}, 
	 * then it defaults to {@link SVNRevision#HEAD}.
	 * <p/>
	 * If externals are {@link #isIgnoreExternals() ignored}, doesn't process externals definitions
	 * as part of this operation.
	 * <p/>
	 * <code>eolStyle</code> allows you to override the standard eol marker on the platform
	 * you are running on. Can be either "LF", "CR" or "CRLF" or <span class="javakeyword">null</span>.  
	 * If <span class="javakeyword">null</span> will use the standard eol marker. Any other value will cause 
	 * an exception with the error code {@link SVNErrorCode#IO_UNKNOWN_EOL} error to be returned.
	 * <p>
	 * If <code>depth</code> is {@link SVNDepth#INFINITY}, exports fully recursively.
	 * Else if it is {@link SVNDepth#IMMEDIATES}, exports <code>url</code> and its immediate
	 * children (if any), but with subdirectories empty and at{@link SVNDepth#EMPTY}. Else if {@link SVNDepth#FILES}, exports <code>url</code> and
	 * its immediate file children (if any) only.  If <code>depth</code> is {@link SVNDepth#EMPTY}, 
	 * then exports exactly <code>url</code> and none of its children.
	 * @param url             repository url to export from
	 * @param dstPath         path to export to
	 * @param pegRevision     the revision at which <code>url</code> will be firstly seen
	 * in the repository to make sure it's the one that is needed
	 * @param revision        the desired revision of the directory/file to be exported
	 * @param eolStyle        a string that denotes a specific End-Of-Line charecter  
	 * @param overwrite       if <span class="javakeyword">true</span>, will cause the export to overwrite 
	 * files or directories
	 * @param depth           tree depth
	 * @return                value of the revision actually exported
	 * @throws SVNException
	 * @since  1.2, SVN 1.5
	 * @from org.tmatesoft.svn.core.wc.SVNUpdateClient
	 */
	public long doExport(SVNURL url, File dstPath, SVNRevision pegRevision,
			SVNRevision revision, String eolStyle, boolean overwrite,
			SVNDepth depth) throws SVNException {
		SVNErrorMessage err = SVNErrorMessage
				.create(SVNErrorCode.VERSION_MISMATCH);
		SVNErrorManager.error(err, SVNLogType.CLIENT);
		return 0;
	}

	/** 
	 * @from org.tmatesoft.svn.core.wc.SVNUpdateClient
	 */
	public void switchFileExternal(SVNWCAccess wcAccess, File path, SVNURL url,
			SVNRevision pegRevision, SVNRevision revision, SVNURL reposRootURL)
			throws SVNException {
		SVNErrorMessage err = SVNErrorMessage
				.create(SVNErrorCode.VERSION_MISMATCH);
		SVNErrorManager.error(err, SVNLogType.CLIENT);
	}

	/** 
	 * @from org.tmatesoft.svn.core.wc.SVNUpdateClient
	 */
	public Map relocateEntry(SVNEntry entry, String from, String to,
			Map validatedURLs) throws SVNException {
		SVNErrorMessage err = SVNErrorMessage
				.create(SVNErrorCode.VERSION_MISMATCH);
		SVNErrorManager.error(err, SVNLogType.CLIENT);
		return null;
	}

	/** 
	 * @from org.tmatesoft.svn.core.wc.SVNUpdateClient
	 */
	public long update(File path, SVNRevision revision, SVNDepth depth,
			boolean allowUnversionedObstructions, boolean depthIsSticky,
			boolean sendCopyFrom) throws SVNException {
		SVNErrorMessage err = SVNErrorMessage
				.create(SVNErrorCode.VERSION_MISMATCH);
		SVNErrorManager.error(err, SVNLogType.CLIENT);
		return 0;
	}

	/** 
	 * Checks out a working copy of <code>url</code> at <code>revision</code>, looked up at 
	 * <code>pegRevision</code>, using <code>dstPath</code> as the root directory of the newly
	 * checked out working copy. 
	 * <p/>
	 * If <code>pegRevision</code> is {@link SVNRevision#UNDEFINED}, then it
	 * defaults to {@link SVNRevision#HEAD}.
	 * <p/>
	 * <code>revision</code> must represent a valid revision number ({@link SVNRevision#getNumber()} >= 0),
	 * or date ({@link SVNRevision#getDate()} != <span class="javakeyword">true</span>), or be equal to {@link SVNRevision#HEAD}. If <code>revision</code> does not meet these requirements, an exception with 
	 * the error code {@link SVNErrorCode#CLIENT_BAD_REVISION} is thrown.
	 * <p/>
	 * If <code>depth</code> is {@link SVNDepth#INFINITY}, checks out fully recursively.
	 * Else if it is {@link SVNDepth#IMMEDIATES}, checks out <code>url</code> and its
	 * immediate entries (subdirectories will be present, but will be at
	 * depth {@link SVNDepth#EMPTY} themselves); else {@link SVNDepth#FILES},
	 * checks out <code>url</code> and its file entries, but no subdirectories; else
	 * if {@link SVNDepth#EMPTY}, checks out <code>url</code> as an empty directory at
	 * that depth, with no entries present.
	 * <p/>
	 * If <code>depth</code> is {@link SVNDepth#UNKNOWN}, then behave as if for{@link SVNDepth#INFINITY}, except in the case of resuming a previous
	 * checkout of <code>dstPath</code> (i.e., updating), in which case uses the depth
	 * of the existing working copy.
	 * <p/>
	 * If externals are {@link #isIgnoreExternals() ignored}, doesn't process externals definitions
	 * as part of this operation.
	 * <p/>
	 * If <code>allowUnversionedObstructions</code> is <span class="javakeyword">true</span> then the checkout 
	 * tolerates existing unversioned items that obstruct added paths from <code>url</code>. Only
	 * obstructions of the same type (file or dir) as the added item are tolerated.  The text of obstructing 
	 * files is left as-is, effectively treating it as a user modification after the checkout. Working
	 * properties of obstructing items are set equal to the base properties. If 
	 * <code>allowUnversionedObstructions</code> is <span class="javakeyword">false</span> then the checkout 
	 * will abort if there are any unversioned obstructing items.
	 * <p/>
	 * If the caller's {@link ISVNEventHandler} is non-<span class="javakeyword">null</span>, it is invoked 
	 * as the checkout processes. Also {@link ISVNEventHandler#checkCancelled()} will be used at various places 
	 * during the checkout to check whether the caller wants to stop the checkout.
	 * <p/>
	 * This operation requires repository access (in case the repository is not on the same machine, network
	 * connection is established).
	 * @param url                           a repository location from where a Working Copy will be checked out     
	 * @param dstPath                       the local path where the Working Copy will be placed
	 * @param pegRevision                   the revision at which <code>url</code> will be firstly seen
	 * in the repository to make sure it's the one that is needed
	 * @param revision                      the desired revision of the Working Copy to be checked out
	 * @param depth                         tree depth
	 * @param allowUnversionedObstructions  flag that allows tollerating unversioned items 
	 * during 
	 * @return                              value of the revision actually checked out from the repository
	 * @throws SVNException                 <ul>
	 * <li/>{@link SVNErrorCode#UNSUPPORTED_FEATURE} - if <code>url</code> refers to a 
	 * file rather than a directory
	 * <li/>{@link SVNErrorCode#RA_ILLEGAL_URL} - if <code>url</code> does not exist  
	 * </ul>    
	 * @since 1.2, SVN 1.5
	 * @from org.tmatesoft.svn.core.wc.SVNUpdateClient
	 */
	public long doCheckout(SVNURL url, File dstPath, SVNRevision pegRevision,
			SVNRevision revision, SVNDepth depth,
			boolean allowUnversionedObstructions) throws SVNException {
		SVNErrorMessage err = SVNErrorMessage
				.create(SVNErrorCode.VERSION_MISMATCH);
		SVNErrorManager.error(err, SVNLogType.CLIENT);
		return 0;
	}

	/** 
	 * @from org.tmatesoft.svn.core.wc.SVNUpdateClient
	 */
	public Map doRelocate(SVNAdminArea adminArea, String name, String from,
			String to, boolean recursive, Map validatedURLs)
			throws SVNException {
		SVNErrorMessage err = SVNErrorMessage
				.create(SVNErrorCode.VERSION_MISMATCH);
		SVNErrorManager.error(err, SVNLogType.CLIENT);
		return null;
	}

	/** 
	 * Canonicalizes all urls in the specified Working Copy.
	 * @param dst               a WC path     
	 * @param omitDefaultPort   if <span class="javakeyword">true</span> then removes all
	 * port numbers from urls which equal to default ones, otherwise
	 * does not
	 * @param recursive         recurses an operation
	 * @throws SVNException
	 * @from org.tmatesoft.svn.core.wc.SVNUpdateClient
	 */
	public void doCanonicalizeURLs(File dst, boolean omitDefaultPort,
			boolean recursive) throws SVNException {
		SVNErrorMessage err = SVNErrorMessage
				.create(SVNErrorCode.VERSION_MISMATCH);
		SVNErrorManager.error(err, SVNLogType.CLIENT);
	}

	/** 
	 * oldURL is null when externals is added: 
	 * jsvn ps svn:externals "path URL" .
	 * jsvn up .
	 * newURL is null when external is deleted:
	 * jsvn pd svn:externals .
	 * jsvn up .
	 * Also newURL or oldURL could be null, when external property is added or 
	 * removed by update itself (someone else has changed it). For instance, 
	 * oldURL is always null during checkout or export operation.
	 * @from org.tmatesoft.svn.core.wc.SVNUpdateClient
	 */
	public void handleExternalChange(SVNWCAccess access, String targetDir,
			ExternalDiff externalDiff) throws SVNException {
		SVNErrorMessage err = SVNErrorMessage
				.create(SVNErrorCode.VERSION_MISMATCH);
		SVNErrorManager.error(err, SVNLogType.CLIENT);
	}

	/** 
	 * @from org.tmatesoft.svn.core.wc.SVNUpdateClient
	 */
	public void handleExternalItemChange(SVNWCAccess access, String targetDir,
			ExternalDiff externalDiff) throws SVNException {
		SVNErrorMessage err = SVNErrorMessage
				.create(SVNErrorCode.VERSION_MISMATCH);
		SVNErrorManager.error(err, SVNLogType.CLIENT);
	}

	private static class ExternalDiff {
		public SVNExternal oldExternal;
		public SVNExternal newExternal;
		public File owner;
		public SVNURL ownerURL;
		public SVNURL rootURL;
		public boolean isExport;
		public boolean isUpdateUnchanged;

		public boolean compareExternals(SVNURL oldURL, SVNURL newURL) {
			return oldURL.equals(newURL)
					&& oldExternal.getRevision().equals(
							newExternal.getRevision())
					&& oldExternal.getPegRevision().equals(
							newExternal.getPegRevision());
		}
	}
}
