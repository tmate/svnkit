package org.tmatesoft.svn.core.internal.wc.v16;

import java.io.File;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import org.tmatesoft.svn.core.SVNCancelException;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.SVNCancellableEditor;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNEventFactory;
import org.tmatesoft.svn.core.internal.wc.SVNExternal;
import org.tmatesoft.svn.core.internal.wc.SVNFileType;
import org.tmatesoft.svn.core.internal.wc.SVNRemoteStatusEditor;
import org.tmatesoft.svn.core.internal.wc.SVNStatusEditor;
import org.tmatesoft.svn.core.internal.wc.SVNStatusReporter;
import org.tmatesoft.svn.core.internal.wc.admin.SVNAdminArea;
import org.tmatesoft.svn.core.internal.wc.admin.SVNAdminAreaFactory;
import org.tmatesoft.svn.core.internal.wc.admin.SVNAdminAreaInfo;
import org.tmatesoft.svn.core.internal.wc.admin.SVNEntry;
import org.tmatesoft.svn.core.internal.wc.admin.SVNReporter;
import org.tmatesoft.svn.core.internal.wc.admin.SVNWCAccess;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.SVNCapability;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.wc.ISVNEventHandler;
import org.tmatesoft.svn.core.wc.ISVNOptions;
import org.tmatesoft.svn.core.wc.ISVNRepositoryPool;
import org.tmatesoft.svn.core.wc.ISVNStatusHandler;
import org.tmatesoft.svn.core.wc.SVNEvent;
import org.tmatesoft.svn.core.wc.SVNEventAction;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNStatus;
import org.tmatesoft.svn.core.wc.SVNStatusClient;
import org.tmatesoft.svn.core.wc.SVNStatusClient;
import org.tmatesoft.svn.core.wc.SVNStatusType;
import org.tmatesoft.svn.util.SVNLogType;

public class SVNStatusClient16 extends SVNStatusClient {
	protected SVNStatusClient dispatcher;

	protected SVNStatusClient16(SVNStatusClient from) {
		super(from);
		this.dispatcher = dispatcher;
	}

	public static SVNStatusClient16 delegate(SVNStatusClient dispatcher) {
		SVNStatusClient16 delegate = new SVNStatusClient16(dispatcher);
		return delegate;
	}

	/** 
	 * Constructs and initializes an <b>SVNStatusClient</b> object
	 * with the specified run-time configuration and repository pool object.
	 * <p/>
	 * If <code>options</code> is <span class="javakeyword">null</span>,
	 * then this <b>SVNStatusClient</b> will be using a default run-time
	 * configuration driver  which takes client-side settings from the
	 * default SVN's run-time configuration area but is not able to
	 * change those settings (read more on {@link ISVNOptions} and {@link SVNWCUtil}).
	 * <p/>
	 * If <code>repositoryPool</code> is <span class="javakeyword">null</span>,
	 * then {@link org.tmatesoft.svn.core.io.SVNRepositoryFactory} will be used to create {@link SVNRepository repository access objects}.
	 * @param repositoryPool   a repository pool object
	 * @param options          a run-time configuration options driver
	 * @from org.tmatesoft.svn.core.wc.SVNStatusClient
	 */
	public SVNStatusClient16(ISVNRepositoryPool repositoryPool,
			ISVNOptions options) {
		super(repositoryPool, options);
	}

	/** 
	 * Constructs and initializes an <b>SVNStatusClient</b> object
	 * with the specified run-time configuration and authentication 
	 * drivers.
	 * <p>
	 * If <code>options</code> is <span class="javakeyword">null</span>,
	 * then this <b>SVNStatusClient</b> will be using a default run-time
	 * configuration driver  which takes client-side settings from the 
	 * default SVN's run-time configuration area but is not able to
	 * change those settings (read more on {@link ISVNOptions} and {@link SVNWCUtil}).  
	 * <p>
	 * If <code>authManager</code> is <span class="javakeyword">null</span>,
	 * then this <b>SVNStatusClient</b> will be using a default authentication
	 * and network layers driver (see {@link SVNWCUtil#createDefaultAuthenticationManager()})
	 * which uses server-side settings and auth storage from the 
	 * default SVN's run-time configuration area (or system properties
	 * if that area is not found).
	 * @param authManager an authentication and network layers driver
	 * @param options     a run-time configuration options driver     
	 * @from org.tmatesoft.svn.core.wc.SVNStatusClient
	 */
	public SVNStatusClient16(ISVNAuthenticationManager authManager,
			ISVNOptions options) {
		super(authManager, options);
	}

	/** 
	 * Given a <code>path</code> to a working copy directory (or single file), calls <code>handler</code> 
	 * with a set of {@link SVNStatus} objects which describe the status of the <code>path</code>, and its 
	 * children (recursing according to <code>depth</code>).
	 * <p/>
	 * If <code>reportAll</code> is set, retrieves all entries; otherwise, retrieves only "interesting" entries 
	 * (local modifications and/or out of date).
	 * <p/>
	 * If <code>remote</code> is set, contacts the repository and augments the status objects with information 
	 * about out-of-dateness (with respect to <code>revision</code>). 
	 * <p/>
	 * If {@link #isIgnoreExternals()} returns <span class="javakeyword">false</span>, then recurses into 
	 * externals definitions (if any exist and <code>depth</code> is either {@link SVNDepth#INFINITY} or {@link SVNDepth#UNKNOWN}) after handling the main target. This calls the client notification 
	 * handler ({@link ISVNEventHandler}) with the {@link SVNEventAction#STATUS_EXTERNAL} action before 
	 * handling each externals definition, and with {@link SVNEventAction#STATUS_COMPLETED} after each.
	 * <p/>
	 * <code>changeLists</code> is a collection of <code>String</code> changelist names, used as a restrictive 
	 * filter on items whose statuses are reported; that is, doesn't report status about any item unless
	 * it's a member of one of those changelists. If <code>changeLists</code> is empty (or 
	 * <span class="javakeyword">null</span>), no changelist filtering occurs.
	 * @param path                    working copy path 
	 * @param revision                if <code>remote</code> is <span class="javakeyword">true</span>,
	 * status is calculated against this revision
	 * @param depth                   tree depth to process
	 * @param remote                  <span class="javakeyword">true</span> to check up the status of the item 
	 * in the repository, that will tell if the local item is out-of-date (like 
	 * <i>'-u'</i> option in the SVN client's <code>'svn status'</code> command), 
	 * otherwise <span class="javakeyword">false</span>
	 * @param reportAll               <span class="javakeyword">true</span> to collect status information on all items including those ones that are in a 
	 * <i>'normal'</i> state (unchanged), otherwise <span class="javakeyword">false</span>
	 * @param includeIgnored          <span class="javakeyword">true</span> to force the operation to collect information
	 * on items that were set to be ignored (like <i>'--no-ignore'</i> option in the SVN 
	 * client's <code>'svn status'</code> command to disregard default and <i>'svn:ignore'</i> property
	 * ignores), otherwise <span class="javakeyword">false</span>
	 * @param collectParentExternals  obsolete (not used)
	 * @param handler                 a caller's status handler that will be involved
	 * in processing status information
	 * @param changeLists             collection with changelist names
	 * @return                         returns the actual revision against which the working copy was compared;
	 * the return value is not meaningful (-1) unless <code>remote</code> is set 
	 * @throws SVNException 
	 * @since                          1.2, SVN 1.5
	 * @from org.tmatesoft.svn.core.wc.SVNStatusClient
	 */
	public long doStatus(File path, SVNRevision revision, SVNDepth depth,
			boolean remote, boolean reportAll, boolean includeIgnored,
			boolean collectParentExternals, final ISVNStatusHandler handler,
			final Collection changeLists) throws SVNException {
		if (handler == null) {
			return -1;
		}
		depth = depth == null ? SVNDepth.UNKNOWN : depth;
		SVNWCAccess wcAccess = createWCAccess();
		SVNStatusEditor editor = null;
		final boolean[] deletedInRepository = new boolean[] { false };
		ISVNStatusHandler realHandler = new ISVNStatusHandler() {
			public void handleStatus(SVNStatus status) throws SVNException {
				if (deletedInRepository[0] && status.getEntry() != null) {
					status.setRemoteStatus(SVNStatusType.STATUS_DELETED, null,
							null, null);
				}
				if (!SVNWCAccess.matchesChangeList(changeLists, status
						.getEntry())) {
					return;
				}
				handler.handleStatus(status);
			}
		};
		try {
			SVNAdminAreaInfo info = null;
			try {
				SVNAdminArea anchor = wcAccess.open(path, false, SVNDepth
						.recurseFromDepth(depth) ? -1 : 1);
				info = new SVNAdminAreaInfo(wcAccess, anchor, anchor, "");
			} catch (SVNException svne) {
				if (svne.getErrorMessage().getErrorCode() == SVNErrorCode.WC_NOT_DIRECTORY) {
					info = wcAccess.openAnchor(path, false, SVNDepth
							.recurseFromDepth(depth) ? -1 : 1);
					if (depth == SVNDepth.EMPTY) {
						depth = SVNDepth.IMMEDIATES;
					}
				} else {
					throw svne;
				}
			}
			SVNEntry entry = null;
			if (remote) {
				SVNAdminArea anchor = info.getAnchor();
				entry = wcAccess.getVersionedEntry(anchor.getRoot(), false);
				if (entry.getURL() == null) {
					SVNErrorMessage error = SVNErrorMessage.create(
							SVNErrorCode.ENTRY_MISSING_URL,
							"Entry ''{0}'' has no URL", info.getAnchor()
									.getRoot());
					SVNErrorManager.error(error, SVNLogType.WC);
				}
				SVNURL url = entry.getSVNURL();
				SVNRepository repository = createRepository(url, anchor
						.getRoot(), wcAccess, true);
				long rev;
				if (revision == SVNRevision.HEAD) {
					rev = -1;
				} else {
					rev = getRevisionNumber(revision, repository, path);
				}
				SVNNodeKind kind = repository.checkPath("", rev);
				checkCancelled();
				SVNReporter reporter = null;
				if (kind == SVNNodeKind.NONE) {
					if (!entry.isScheduledForAddition()) {
						deletedInRepository[0] = true;
					}
					editor = new SVNStatusEditor(getOptions(), wcAccess, info,
							includeIgnored, reportAll, depth, realHandler);
					checkCancelled();
					editor.closeEdit();
				} else {
					editor = new SVNRemoteStatusEditor(getOptions(), wcAccess,
							info, includeIgnored, reportAll, depth, realHandler);
					SVNRepository locksRepos = createRepository(url, anchor
							.getRoot(), wcAccess, false);
					checkCancelled();
					boolean serverSupportsDepth = repository
							.hasCapability(SVNCapability.DEPTH);
					reporter = new SVNReporter(info, path, false,
							!serverSupportsDepth, depth, false, true, true,
							getDebugLog());
					SVNStatusReporter statusReporter = new SVNStatusReporter(
							locksRepos, reporter, editor);
					String target = "".equals(info.getTargetName()) ? null
							: info.getTargetName();
					repository.status(rev, target, depth, statusReporter,
							SVNCancellableEditor.newInstance(
									(ISVNEditor) editor, getEventDispatcher(),
									getDebugLog()));
				}
				if (getEventDispatcher() != null) {
					long reportedFiles = reporter != null ? reporter
							.getReportedFilesCount() : 0;
					long totalFiles = reporter != null ? reporter
							.getTotalFilesCount() : 0;
					SVNEvent event = SVNEventFactory.createSVNEvent(info
							.getAnchor().getFile(info.getTargetName()),
							SVNNodeKind.NONE, null, editor.getTargetRevision(),
							SVNEventAction.STATUS_COMPLETED, null, null, null,
							reportedFiles, totalFiles);
					getEventDispatcher().handleEvent(event,
							ISVNEventHandler.UNKNOWN);
				}
			} else {
				editor = new SVNStatusEditor(getOptions(), wcAccess, info,
						includeIgnored, reportAll, depth, handler);
				editor.closeEdit();
			}
			if (!isIgnoreExternals()
					&& (depth == SVNDepth.INFINITY || depth == SVNDepth.UNKNOWN)) {
				Map externalsMap = info.getNewExternals();
				for (Iterator paths = externalsMap.keySet().iterator(); paths
						.hasNext();) {
					String ownerPath = (String) paths.next();
					String externalValue = (String) externalsMap.get(ownerPath);
					SVNExternal[] externals = SVNExternal.parseExternals(
							ownerPath, externalValue);
					for (int i = 0; i < externals.length; i++) {
						SVNExternal external = externals[i];
						String externalPath = SVNPathUtil.append(ownerPath,
								external.getPath());
						File externalFile = info.getAnchor().getFile(
								externalPath);
						if (SVNFileType.getType(externalFile) != SVNFileType.DIRECTORY) {
							continue;
						}
						try {
							int format = SVNAdminAreaFactory.checkWC(
									externalFile, true);
							if (format == 0) {
								continue;
							}
						} catch (SVNException e) {
							continue;
						}
						handleEvent(SVNEventFactory.createSVNEvent(
								externalFile, SVNNodeKind.DIR, null,
								SVNRepository.INVALID_REVISION,
								SVNEventAction.STATUS_EXTERNAL, null, null,
								null), ISVNEventHandler.UNKNOWN);
						setEventPathPrefix(externalPath);
						try {
							doStatus(externalFile, SVNRevision.HEAD, depth,
									remote, reportAll, includeIgnored, false,
									handler, null);
						} catch (SVNException e) {
							if (e instanceof SVNCancelException) {
								throw e;
							}
						} finally {
							setEventPathPrefix(null);
						}
					}
				}
			}
		} finally {
			wcAccess.close();
		}
		return editor.getTargetRevision();
	}
}
