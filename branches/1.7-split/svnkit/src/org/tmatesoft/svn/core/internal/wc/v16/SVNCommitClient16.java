package org.tmatesoft.svn.core.internal.wc.v16;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.tmatesoft.svn.core.SVNCancelException;
import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.internal.util.SVNHashMap;
import org.tmatesoft.svn.core.internal.util.SVNHashSet;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.SVNCommitMediator;
import org.tmatesoft.svn.core.internal.wc.SVNCommitUtil;
import org.tmatesoft.svn.core.internal.wc.SVNCommitter;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNEventFactory;
import org.tmatesoft.svn.core.internal.wc.SVNPropertiesManager;
import org.tmatesoft.svn.core.internal.wc.admin.SVNAdminArea;
import org.tmatesoft.svn.core.internal.wc.admin.SVNEntry;
import org.tmatesoft.svn.core.internal.wc.admin.SVNWCAccess;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.wc.DefaultSVNCommitHandler;
import org.tmatesoft.svn.core.wc.DefaultSVNCommitParameters;
import org.tmatesoft.svn.core.wc.ISVNCommitHandler;
import org.tmatesoft.svn.core.wc.ISVNCommitParameters;
import org.tmatesoft.svn.core.wc.ISVNEventHandler;
import org.tmatesoft.svn.core.wc.ISVNOptions;
import org.tmatesoft.svn.core.wc.ISVNRepositoryPool;
import org.tmatesoft.svn.core.wc.SVNCommitClient;
import org.tmatesoft.svn.core.wc.SVNCommitClient;
import org.tmatesoft.svn.core.wc.SVNCommitItem;
import org.tmatesoft.svn.core.wc.SVNCommitPacket;
import org.tmatesoft.svn.core.wc.SVNEvent;
import org.tmatesoft.svn.core.wc.SVNEventAction;
import org.tmatesoft.svn.util.SVNLogType;

public class SVNCommitClient16 extends SVNCommitClient {
	protected SVNCommitClient dispatcher;

	protected SVNCommitClient16(SVNCommitClient from) {
		super(from);
		this.dispatcher = dispatcher;
	}

	public static SVNCommitClient16 delegate(SVNCommitClient dispatcher) {
		SVNCommitClient16 delegate = new SVNCommitClient16(dispatcher);
		return delegate;
	}

	/** 
	 * Constructs and initializes an <b>SVNCommitClient</b> object
	 * with the specified run-time configuration and authentication 
	 * drivers.
	 * <p>
	 * If <code>options</code> is <span class="javakeyword">null</span>,
	 * then this <b>SVNCommitClient</b> will be using a default run-time
	 * configuration driver  which takes client-side settings from the 
	 * default SVN's run-time configuration area but is not able to
	 * change those settings (read more on {@link ISVNOptions} and {@link SVNWCUtil}).  
	 * <p>
	 * If <code>authManager</code> is <span class="javakeyword">null</span>,
	 * then this <b>SVNCommitClient</b> will be using a default authentication
	 * and network layers driver (see {@link SVNWCUtil#createDefaultAuthenticationManager()})
	 * which uses server-side settings and auth storage from the 
	 * default SVN's run-time configuration area (or system properties
	 * if that area is not found).
	 * @param authManager an authentication and network layers driver
	 * @param options     a run-time configuration options driver     
	 * @from org.tmatesoft.svn.core.wc.SVNCommitClient
	 */
	public SVNCommitClient16(ISVNAuthenticationManager authManager,
			ISVNOptions options) {
		super(authManager, options);
	}

	/** 
	 * Constructs and initializes an <b>SVNCommitClient</b> object
	 * with the specified run-time configuration and repository pool object.
	 * <p/>
	 * If <code>options</code> is <span class="javakeyword">null</span>,
	 * then this <b>SVNCommitClient</b> will be using a default run-time
	 * configuration driver  which takes client-side settings from the
	 * default SVN's run-time configuration area but is not able to
	 * change those settings (read more on {@link ISVNOptions} and {@link SVNWCUtil}).
	 * <p/>
	 * If <code>repositoryPool</code> is <span class="javakeyword">null</span>,
	 * then {@link org.tmatesoft.svn.core.io.SVNRepositoryFactory} will be used to create {@link SVNRepository repository access objects}.
	 * @param repositoryPool   a repository pool object
	 * @param options          a run-time configuration options driver
	 * @from org.tmatesoft.svn.core.wc.SVNCommitClient
	 */
	public SVNCommitClient16(ISVNRepositoryPool repositoryPool,
			ISVNOptions options) {
		super(repositoryPool, options);
	}

	/** 
	 * Returns commit parameters. 
	 * <p>
	 * If no user parameters were previously specified, once creates and 
	 * returns {@link DefaultSVNCommitParameters default} ones. 
	 * @return commit parameters
	 * @see #setCommitParameters(ISVNCommitParameters)
	 * @from org.tmatesoft.svn.core.wc.SVNCommitClient
	 */
	public ISVNCommitParameters getCommitParameters() {
		if (myCommitParameters == null) {
			myCommitParameters = new DefaultSVNCommitParameters();
		}
		return myCommitParameters;
	}

	/** 
	 * @from org.tmatesoft.svn.core.wc.SVNCommitClient
	 */
	static public boolean hasProcessedParents(Collection paths, String path)
			throws SVNException {
		path = SVNPathUtil.removeTail(path);
		if (paths.contains(path)) {
			return true;
		}
		if ("".equals(path)) {
			return false;
		}
		return hasProcessedParents(paths, path);
	}

	/** 
	 * Commits files or directories into repository.
	 * <p>
	 * <code>commitPackets</code> is an array of packets that contain commit items ({@link SVNCommitItem}) 
	 * which represent local Working Copy items that were changed and are to be committed. 
	 * Commit items are gathered in a single {@link SVNCommitPacket}by invoking {@link #doCollectCommitItems(File[],boolean,boolean,SVNDepth,String[])} or {@link #doCollectCommitItems(File[],boolean,boolean,SVNDepth,boolean,String[])}. 
	 * <p>
	 * This allows to commit items from separate Working Copies checked out from the same or different 
	 * repositories. For each commit packet {@link #getCommitHandler() commit handler} is invoked to 
	 * produce a commit message given the one <code>commitMessage</code> passed to this method.
	 * Each commit packet is committed in a separate transaction.
	 * <p/>
	 * For more details on parameters, please, refer to {@link #doCommit(File[],boolean,String,SVNProperties,String[],boolean,boolean,SVNDepth)}.
	 * @param commitPackets       commit packets containing commit commit items per one commit
	 * @param keepLocks           if <span class="javakeyword">true</span> and there are local items that 
	 * were locked then the commit will left them locked, otherwise the items will 
	 * be unlocked by the commit
	 * @param keepChangelist      whether to remove changelists or not
	 * @param commitMessage       a string to be a commit log message
	 * @param revisionProperties  custom revision properties
	 * @return                     information about the new committed revisions 
	 * @throws SVNException 
	 * @since                      1.2.0, SVN 1.5.0
	 * @from org.tmatesoft.svn.core.wc.SVNCommitClient
	 */
	public SVNCommitInfo[] doCommit(SVNCommitPacket[] commitPackets,
			boolean keepLocks, boolean keepChangelist, String commitMessage,
			SVNProperties revisionProperties) throws SVNException {
		if (commitPackets == null || commitPackets.length == 0) {
			return new SVNCommitInfo[0];
		}
		Collection tmpFiles = null;
		SVNCommitInfo info = null;
		ISVNEditor commitEditor = null;
		Collection infos = new ArrayList();
		boolean needsSleepForTimeStamp = false;
		for (int p = 0; p < commitPackets.length; p++) {
			SVNCommitPacket commitPacket = commitPackets[p]
					.removeSkippedItems();
			if (commitPacket.getCommitItems().length == 0) {
				continue;
			}
			try {
				commitMessage = getCommitHandler().getCommitMessage(
						commitMessage, commitPacket.getCommitItems());
				if (commitMessage == null) {
					infos.add(SVNCommitInfo.NULL);
					continue;
				}
				commitMessage = SVNCommitUtil
						.validateCommitMessage(commitMessage);
				Map commitables = new TreeMap();
				SVNURL baseURL = SVNCommitUtil.translateCommitables(
						commitPacket.getCommitItems(), commitables);
				Map lockTokens = SVNCommitUtil.translateLockTokens(commitPacket
						.getLockTokens(), baseURL.toString());
				SVNCommitItem firstItem = commitPacket.getCommitItems()[0];
				SVNRepository repository = createRepository(baseURL, firstItem
						.getFile(), firstItem.getWCAccess(), true);
				SVNCommitMediator mediator = new SVNCommitMediator(commitables);
				tmpFiles = mediator.getTmpFiles();
				String repositoryRoot = repository.getRepositoryRoot(true)
						.getPath();
				SVNPropertiesManager
						.validateRevisionProperties(revisionProperties);
				commitEditor = repository.getCommitEditor(commitMessage,
						lockTokens, keepLocks, revisionProperties, mediator);
				for (int i = 0; i < commitPacket.getCommitItems().length; i++) {
					commitPacket.getCommitItems()[i].getWCAccess()
							.setEventHandler(getEventDispatcher());
				}
				info = SVNCommitter.commit(mediator.getTmpFiles(), commitables,
						repositoryRoot, commitEditor);
				Collection processedItems = new SVNHashSet();
				Collection explicitCommitPaths = new SVNHashSet();
				for (Iterator urls = commitables.keySet().iterator(); urls
						.hasNext();) {
					String url = (String) urls.next();
					SVNCommitItem item = (SVNCommitItem) commitables.get(url);
					explicitCommitPaths.add(item.getPath());
				}
				for (Iterator urls = commitables.keySet().iterator(); urls
						.hasNext();) {
					String url = (String) urls.next();
					SVNCommitItem item = (SVNCommitItem) commitables.get(url);
					SVNWCAccess wcAccess = item.getWCAccess();
					String path = item.getPath();
					SVNAdminArea dir = null;
					String target = null;
					try {
						if (item.getKind() == SVNNodeKind.DIR) {
							target = "";
							dir = wcAccess.retrieve(item.getFile());
						} else {
							target = SVNPathUtil.tail(path);
							dir = wcAccess.retrieve(item.getFile()
									.getParentFile());
						}
					} catch (SVNException e) {
						if (e.getErrorMessage().getErrorCode() == SVNErrorCode.WC_NOT_LOCKED) {
							dir = null;
						}
					}
					if (dir == null) {
						if (hasProcessedParents(processedItems, path)) {
							processedItems.add(path);
							continue;
						}
						if (item.isDeleted()
								&& item.getKind() == SVNNodeKind.DIR) {
							File parentPath = "".equals(path) ? null : item
									.getFile().getParentFile();
							String nameInParent = "".equals(path) ? null
									: SVNPathUtil.tail(path);
							if (parentPath != null) {
								SVNAdminArea parentDir = wcAccess
										.retrieve(parentPath);
								if (parentDir != null) {
									SVNEntry entryInParent = parentDir
											.getEntry(nameInParent, true);
									if (entryInParent != null) {
										Map attributes = new SVNHashMap();
										attributes.put(SVNProperty.SCHEDULE,
												null);
										attributes.put(SVNProperty.DELETED,
												Boolean.TRUE.toString());
										parentDir.modifyEntry(nameInParent,
												attributes, true, true);
									}
								}
							}
							processedItems.add(path);
							continue;
						}
					}
					SVNEntry entry = dir.getEntry(target, true);
					if (entry == null
							&& hasProcessedParents(processedItems, path)) {
						processedItems.add(path);
						continue;
					}
					boolean recurse = false;
					if (item.isAdded() && item.getCopyFromURL() != null
							&& item.getKind() == SVNNodeKind.DIR) {
						recurse = true;
					}
					boolean removeLock = !keepLocks && item.isLocked();
					SVNProperties wcPropChanges = mediator
							.getWCProperties(item);
					dir.commit(target, info, wcPropChanges, removeLock,
							recurse, !keepChangelist, explicitCommitPaths,
							getCommitParameters());
					processedItems.add(path);
				}
				needsSleepForTimeStamp = true;
				dispatchEvent(SVNEventFactory.createSVNEvent(null,
						SVNNodeKind.NONE, null, info.getNewRevision(),
						SVNEventAction.COMMIT_COMPLETED, null, null, null),
						ISVNEventHandler.UNKNOWN);
			} catch (SVNException e) {
				if (e instanceof SVNCancelException) {
					throw e;
				}
				SVNErrorMessage err = e.getErrorMessage().wrap(
						"Commit failed (details follow):");
				infos.add(new SVNCommitInfo(-1, null, null, err));
				dispatchEvent(SVNEventFactory.createErrorEvent(err),
						ISVNEventHandler.UNKNOWN);
				continue;
			} finally {
				if (info == null && commitEditor != null) {
					try {
						commitEditor.abortEdit();
					} catch (SVNException e) {
					}
				}
				if (tmpFiles != null) {
					for (Iterator files = tmpFiles.iterator(); files.hasNext();) {
						File file = (File) files.next();
						file.delete();
					}
				}
				if (commitPacket != null) {
					commitPacket.dispose();
				}
			}
			infos.add(info != null ? info : SVNCommitInfo.NULL);
		}
		if (needsSleepForTimeStamp) {
			sleepForTimeStamp();
		}
		return (SVNCommitInfo[]) infos.toArray(new SVNCommitInfo[infos.size()]);
	}

	/** 
	 * Collects commit items (containing detailed information on each Working Copy item
	 * that contains changes and need to be committed to the repository) into a single {@link SVNCommitPacket}. Further this commit packet can be passed to{@link #doCommit(SVNCommitPacket,boolean,boolean,String,SVNProperties)}.
	 * <p/>
	 * For more details on parameters, please, refer to {@link #doCommit(File[],boolean,String,SVNProperties,String[],boolean,boolean,SVNDepth)}. 
	 * @param paths            an array of local items which should be traversed
	 * to collect information on every changed item (one 
	 * <b>SVNCommitItem</b> per each
	 * modified local item)
	 * @param keepLocks        if <span class="javakeyword">true</span> and there are local items that 
	 * were locked then these items will be left locked after
	 * traversing all of them, otherwise the items will be unlocked
	 * @param force            forces collecting commit items for a non-recursive commit  
	 * @param depth            tree depth to process
	 * @param changelists      changelist names array 
	 * @return                  commit packet containing commit items                 
	 * @throws SVNException 
	 * @since                   1.2.0
	 * @from org.tmatesoft.svn.core.wc.SVNCommitClient
	 */
	public SVNCommitPacket doCollectCommitItems(File[] paths,
			boolean keepLocks, boolean force, SVNDepth depth,
			String[] changelists) throws SVNException {
		depth = depth == null ? SVNDepth.UNKNOWN : depth;
		if (depth == SVNDepth.UNKNOWN) {
			depth = SVNDepth.INFINITY;
		}
		if (paths == null || paths.length == 0) {
			return SVNCommitPacket.EMPTY;
		}
		Collection targets = new ArrayList();
		SVNStatusClient16 statusClient = new SVNStatusClient16(
				getRepositoryPool(), getOptions());
		statusClient.setEventHandler(new ISVNEventHandler() {
			public void handleEvent(SVNEvent event, double progress)
					throws SVNException {
			}

			public void checkCancelled() throws SVNCancelException {
				SVNCommitClient16.this.checkCancelled();
			}
		});
		SVNWCAccess wcAccess = SVNCommitUtil.createCommitWCAccess(paths, depth,
				force, targets, statusClient);
		SVNAdminArea[] areas = wcAccess.getAdminAreas();
		for (int i = 0; areas != null && i < areas.length; i++) {
			if (areas[i] != null) {
				areas[i].setCommitParameters(getCommitParameters());
			}
		}
		try {
			Map lockTokens = new SVNHashMap();
			checkCancelled();
			Collection changelistsSet = changelists != null ? new SVNHashSet()
					: null;
			if (changelists != null) {
				for (int j = 0; j < changelists.length; j++) {
					changelistsSet.add(changelists[j]);
				}
			}
			SVNCommitItem[] commitItems = SVNCommitUtil.harvestCommitables(
					wcAccess, targets, lockTokens, !keepLocks, depth, force,
					changelistsSet, getCommitParameters());
			boolean hasModifications = false;
			checkCancelled();
			for (int i = 0; commitItems != null && i < commitItems.length; i++) {
				SVNCommitItem commitItem = commitItems[i];
				if (commitItem.isAdded() || commitItem.isDeleted()
						|| commitItem.isContentsModified()
						|| commitItem.isPropertiesModified()
						|| commitItem.isCopied()) {
					hasModifications = true;
					break;
				}
			}
			if (!hasModifications) {
				wcAccess.close();
				return SVNCommitPacket.EMPTY;
			}
			return new SVNCommitPacket(wcAccess, commitItems, lockTokens);
		} catch (SVNException e) {
			wcAccess.close();
			if (e instanceof SVNCancelException) {
				throw e;
			}
			SVNErrorMessage nestedErr = e.getErrorMessage();
			SVNErrorMessage err = SVNErrorMessage.create(nestedErr
					.getErrorCode(), "Commit failed (details follow):");
			SVNErrorManager.error(err, e, SVNLogType.DEFAULT);
			return null;
		}
	}

	/** 
	 * Collects commit items (containing detailed information on each Working Copy item that was changed and 
	 * need to be committed to the repository) into different 
	 * <code>SVNCommitPacket</code>s. This method may be considered as an advanced version of the {@link #doCollectCommitItems(File[],boolean,boolean,SVNDepth,String[])} method. Its main difference 
	 * from the aforementioned method is that it provides an ability to collect commit items from different 
	 * working copies checked out from the same repository and combine them into a single commit packet. 
	 * This is attained via setting <code>combinePackets</code> into <span class="javakeyword">true</span>. 
	 * However even if <code>combinePackets</code> is set, combining may only occur if (besides that the paths
	 * must be from the same repository) URLs of <code>paths</code> are formed of identical components, that is 
	 * protocol name, host name, port number (if any) must match for all paths. Otherwise combining will not 
	 * occur.   
	 * <p/>
	 * Combined items will be committed in a single transaction.
	 * <p/>
	 * For details on other parameters, please, refer to {@link #doCommit(File[],boolean,String,SVNProperties,String[],boolean,boolean,SVNDepth)}.
	 * @param paths            an array of local items which should be traversed
	 * to collect information on every changed item (one 
	 * <b>SVNCommitItem</b> per each
	 * modified local item)
	 * @param keepLocks        if <span class="javakeyword">true</span> and there are local items that 
	 * were locked then these items will be left locked after
	 * traversing all of them, otherwise the items will be unlocked
	 * @param force            forces collecting commit items for a non-recursive commit  
	 * @param depth            tree depth to process
	 * @param combinePackets   whether combining commit packets into a single commit packet is allowed or not   
	 * @param changelists      changelist names array
	 * @return                  array of commit packets
	 * @throws SVNException     in the following cases:
	 * <ul>
	 * <li/>exception with {@link SVNErrorCode#ENTRY_MISSING_URL} error code - if 
	 * working copy root of either path has no url
	 * </ul>     
	 * @since                   1.2.0 
	 * @from org.tmatesoft.svn.core.wc.SVNCommitClient
	 */
	public SVNCommitPacket[] doCollectCommitItems(File[] paths,
			boolean keepLocks, boolean force, SVNDepth depth,
			boolean combinePackets, String[] changelists) throws SVNException {
		depth = depth == null ? SVNDepth.UNKNOWN : depth;
		if (depth == SVNDepth.UNKNOWN) {
			depth = SVNDepth.INFINITY;
		}
		if (paths == null || paths.length == 0) {
			return new SVNCommitPacket[0];
		}
		Collection packets = new ArrayList();
		Map targets = new SVNHashMap();
		SVNStatusClient16 statusClient = new SVNStatusClient16(
				getRepositoryPool(), getOptions());
		statusClient.setEventHandler(new ISVNEventHandler() {
			public void handleEvent(SVNEvent event, double progress)
					throws SVNException {
			}

			public void checkCancelled() throws SVNCancelException {
				SVNCommitClient16.this.checkCancelled();
			}
		});
		SVNWCAccess[] wcAccesses = SVNCommitUtil.createCommitWCAccess2(paths,
				depth, force, targets, statusClient);
		for (int i = 0; i < wcAccesses.length; i++) {
			SVNWCAccess wcAccess = wcAccesses[i];
			SVNAdminArea[] areas = wcAccess.getAdminAreas();
			for (int j = 0; areas != null && j < areas.length; j++) {
				if (areas[j] != null) {
					areas[j].setCommitParameters(getCommitParameters());
				}
			}
			Collection targetPaths = (Collection) targets.get(wcAccess);
			try {
				checkCancelled();
				Map lockTokens = new SVNHashMap();
				Collection changelistsSet = changelists != null ? new SVNHashSet()
						: null;
				if (changelists != null) {
					for (int j = 0; j < changelists.length; j++) {
						changelistsSet.add(changelists[j]);
					}
				}
				SVNCommitItem[] commitItems = SVNCommitUtil.harvestCommitables(
						wcAccess, targetPaths, lockTokens, !keepLocks, depth,
						force, changelistsSet, getCommitParameters());
				checkCancelled();
				boolean hasModifications = false;
				for (int j = 0; commitItems != null && j < commitItems.length; j++) {
					SVNCommitItem commitItem = commitItems[j];
					if (commitItem.isAdded() || commitItem.isDeleted()
							|| commitItem.isContentsModified()
							|| commitItem.isPropertiesModified()
							|| commitItem.isCopied()) {
						hasModifications = true;
						break;
					}
				}
				if (!hasModifications) {
					wcAccess.close();
					continue;
				}
				packets.add(new SVNCommitPacket(wcAccess, commitItems,
						lockTokens));
			} catch (SVNException e) {
				for (int j = 0; j < wcAccesses.length; j++) {
					wcAccesses[j].close();
				}
				if (e instanceof SVNCancelException) {
					throw e;
				}
				SVNErrorMessage nestedErr = e.getErrorMessage();
				SVNErrorMessage err = SVNErrorMessage.create(nestedErr
						.getErrorCode(), "Commit failed (details follow):");
				SVNErrorManager.error(err, e, SVNLogType.DEFAULT);
			}
		}
		SVNCommitPacket[] packetsArray = (SVNCommitPacket[]) packets
				.toArray(new SVNCommitPacket[packets.size()]);
		if (!combinePackets) {
			return packetsArray;
		}
		Map repoUUIDs = new SVNHashMap();
		Map locktokensMap = new SVNHashMap();
		try {
			for (int i = 0; i < packetsArray.length; i++) {
				checkCancelled();
				SVNCommitPacket packet = packetsArray[i];
				File wcRoot = SVNWCUtil16.getWorkingCopyRoot(packet
						.getCommitItems()[0].getWCAccess().getAnchor(), true);
				SVNWCAccess rootWCAccess = createWCAccess();
				String uuid = null;
				SVNURL url = null;
				try {
					SVNAdminArea rootDir = rootWCAccess.open(wcRoot, false, 0);
					uuid = rootDir.getEntry(rootDir.getThisDirName(), false)
							.getUUID();
					url = rootDir.getEntry(rootDir.getThisDirName(), false)
							.getSVNURL();
				} finally {
					rootWCAccess.close();
				}
				checkCancelled();
				if (uuid == null) {
					if (url != null) {
						SVNRepository repos = createRepository(url, wcRoot,
								rootWCAccess, true);
						uuid = repos.getRepositoryUUID(true);
					} else {
						SVNErrorMessage err = SVNErrorMessage.create(
								SVNErrorCode.ENTRY_MISSING_URL,
								"''{0}'' has no URL", wcRoot);
						SVNErrorManager.error(err, SVNLogType.WC);
					}
				}
				uuid += url.getProtocol() + ":" + url.getHost() + ":"
						+ url.getPort() + ":" + url.getUserInfo();
				if (!repoUUIDs.containsKey(uuid)) {
					repoUUIDs.put(uuid, new ArrayList());
					locktokensMap.put(uuid, new SVNHashMap());
				}
				Collection items = (Collection) repoUUIDs.get(uuid);
				Map lockTokens = (Map) locktokensMap.get(uuid);
				for (int j = 0; j < packet.getCommitItems().length; j++) {
					items.add(packet.getCommitItems()[j]);
				}
				if (packet.getLockTokens() != null) {
					lockTokens.putAll(packet.getLockTokens());
				}
				checkCancelled();
			}
			packetsArray = new SVNCommitPacket[repoUUIDs.size()];
			int index = 0;
			for (Iterator roots = repoUUIDs.keySet().iterator(); roots
					.hasNext();) {
				checkCancelled();
				String uuid = (String) roots.next();
				Collection items = (Collection) repoUUIDs.get(uuid);
				Map lockTokens = (Map) locktokensMap.get(uuid);
				SVNCommitItem[] itemsArray = (SVNCommitItem[]) items
						.toArray(new SVNCommitItem[items.size()]);
				packetsArray[index++] = new SVNCommitPacket(null, itemsArray,
						lockTokens);
			}
		} catch (SVNException e) {
			for (int j = 0; j < wcAccesses.length; j++) {
				wcAccesses[j].close();
			}
			if (e instanceof SVNCancelException) {
				throw e;
			}
			SVNErrorMessage nestedErr = e.getErrorMessage();
			SVNErrorMessage err = SVNErrorMessage.create(nestedErr
					.getErrorCode(), "Commit failed (details follow):");
			SVNErrorManager.error(err, e, SVNLogType.DEFAULT);
		}
		return packetsArray;
	}

	/** 
	 * Returns the specified commit handler (if set) being in use or a default one 
	 * (<b>DefaultSVNCommitHandler</b>) if no special 
	 * implementations of <b>ISVNCommitHandler</b> were 
	 * previously provided.
	 * @return	the commit handler being in use or a default one
	 * @see #setCommitHander(ISVNCommitHandler)
	 * @see ISVNCommitHandler
	 * @see DefaultSVNCommitHandler 
	 * @from org.tmatesoft.svn.core.wc.SVNCommitClient
	 */
	public ISVNCommitHandler getCommitHandler() {
		if (myCommitHandler == null) {
			myCommitHandler = new DefaultSVNCommitHandler();
		}
		return myCommitHandler;
	}
}
