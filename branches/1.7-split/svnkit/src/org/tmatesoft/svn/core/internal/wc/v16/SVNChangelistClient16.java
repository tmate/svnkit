package org.tmatesoft.svn.core.internal.wc.v16;

import java.io.File;
import java.util.Collection;
import java.util.Map;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.internal.util.SVNHashMap;
import org.tmatesoft.svn.core.internal.util.SVNHashSet;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNEventFactory;
import org.tmatesoft.svn.core.internal.wc.admin.ISVNEntryHandler;
import org.tmatesoft.svn.core.internal.wc.admin.SVNAdminArea;
import org.tmatesoft.svn.core.internal.wc.admin.SVNEntry;
import org.tmatesoft.svn.core.internal.wc.admin.SVNWCAccess;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.wc.ISVNChangelistHandler;
import org.tmatesoft.svn.core.wc.ISVNOptions;
import org.tmatesoft.svn.core.wc.ISVNRepositoryPool;
import org.tmatesoft.svn.core.wc.SVNChangelistClient;
import org.tmatesoft.svn.core.wc.SVNChangelistClient;
import org.tmatesoft.svn.core.wc.SVNEvent;
import org.tmatesoft.svn.core.wc.SVNEventAction;
import org.tmatesoft.svn.util.SVNLogType;

public class SVNChangelistClient16 extends SVNChangelistClient {
	protected SVNChangelistClient dispatcher;

	protected SVNChangelistClient16(SVNChangelistClient from) {
		super(from);
		this.dispatcher = dispatcher;
	}

	public static SVNChangelistClient16 delegate(SVNChangelistClient dispatcher) {
		SVNChangelistClient16 delegate = new SVNChangelistClient16(dispatcher);
		return delegate;
	}

	/** 
	 * Constructs and initializes an <b>SVNChangelistClient</b> object
	 * with the specified run-time configuration and repository pool object.
	 * <p/>
	 * If <code>options</code> is <span class="javakeyword">null</span>,
	 * then this <b>SVNChangelistClient</b> will be using a default run-time
	 * configuration driver  which takes client-side settings from the
	 * default SVN's run-time configuration area but is not able to
	 * change those settings (read more on {@link ISVNOptions} and {@link SVNWCUtil}).
	 * <p/>
	 * If <code>repositoryPool</code> is <span class="javakeyword">null</span>,
	 * then {@link org.tmatesoft.svn.core.io.SVNRepositoryFactory} will be used to create {@link SVNRepository repository access objects}.
	 * @param repositoryPool   a repository pool object
	 * @param options          a run-time configuration options driver
	 * @from org.tmatesoft.svn.core.wc.SVNChangelistClient
	 */
	public SVNChangelistClient16(ISVNRepositoryPool repositoryPool,
			ISVNOptions options) {
		super(repositoryPool, options);
	}

	/** 
	 * Constructs and initializes an <b>SVNChangelistClient</b> object
	 * with the specified run-time configuration and authentication
	 * drivers.
	 * <p/>
	 * If <code>options</code> is <span class="javakeyword">null</span>,
	 * then this <b>SVNChangelistClient</b> will be using a default run-time
	 * configuration driver  which takes client-side settings from the
	 * default SVN's run-time configuration area but is not able to
	 * change those settings (read more on {@link ISVNOptions} and {@link SVNWCUtil}).
	 * <p/>
	 * If <code>authManager</code> is <span class="javakeyword">null</span>,
	 * then this <b>SVNChangelistClient</b> will be using a default authentication
	 * and network layers driver (see {@link SVNWCUtil#createDefaultAuthenticationManager()})
	 * which uses server-side settings and auth storage from the
	 * default SVN's run-time configuration area (or system properties
	 * if that area is not found).
	 * @param authManager an authentication and network layers driver
	 * @param options     a run-time configuration options driver
	 * @from org.tmatesoft.svn.core.wc.SVNChangelistClient
	 */
	public SVNChangelistClient16(ISVNAuthenticationManager authManager,
			ISVNOptions options) {
		super(authManager, options);
	}

	/** 
	 * Gets paths belonging to the specified changelists discovered under the specified path.
	 * <p/>
	 * Beginning at <code>path</code>, crawls to <code>depth</code> to discover every path in or under 
	 * <code>path<code> which belongs to one of the changelists in <code>changeLists</code> (a collection of 
	 * <code>String</code> changelist names).
	 * If <code>changeLists</code> is null, discovers paths with any changelist.
	 * Calls <code>handler</code> each time a changelist-having path is discovered.
	 * <p/> 
	 * If there was an event handler provided via {@link #setEventHandler(ISVNEventHandler)}, then its {@link ISVNEventHandler#checkCancelled()} will be invoked during the recursive walk.
	 * <p/>
	 * Note: this method does not require repository access.
	 * @param path            target working copy path            
	 * @param changeLists     collection of changelist names
	 * @param depth           tree depth to process
	 * @param handler         caller's handler to receive path-to-changelist information  
	 * @throws SVNException 
	 * @since                  1.2.0, New in SVN 1.5.0
	 * @from org.tmatesoft.svn.core.wc.SVNChangelistClient
	 */
	public void doGetChangeLists(File path, final Collection changeLists,
			SVNDepth depth, final ISVNChangelistHandler handler)
			throws SVNException {
		path = path.getAbsoluteFile();
		SVNWCAccess wcAccess = createWCAccess();
		try {
			wcAccess.probeOpen(path, false, SVNWCAccess.INFINITE_DEPTH);
			ISVNEntryHandler entryHandler = new ISVNEntryHandler() {
				public void handleEntry(File path, SVNEntry entry)
						throws SVNException {
					if (SVNWCAccess.matchesChangeList(changeLists, entry)
							&& (entry.isFile() || (entry.isDirectory() && entry
									.getName().equals(
											entry.getAdminArea()
													.getThisDirName())))) {
						if (handler != null) {
							handler.handle(path, entry.getChangelistName());
						}
					}
				}

				public void handleError(File path, SVNErrorMessage error)
						throws SVNException {
					SVNErrorManager.error(error, SVNLogType.WC);
				}
			};
			wcAccess.walkEntries(path, entryHandler, false, depth);
		} finally {
			wcAccess.close();
		}
	}

	/** 
	 * @from org.tmatesoft.svn.core.wc.SVNChangelistClient
	 */
	public void setChangelist(File[] paths, String changelistName,
			String[] changelists, SVNDepth depth) throws SVNException {
		if ("".equals(changelistName)) {
			SVNErrorMessage err = SVNErrorMessage.create(
					SVNErrorCode.INCORRECT_PARAMS,
					"Changelist names must not be empty");
			SVNErrorManager.error(err, SVNLogType.WC);
		}
		SVNWCAccess wcAccess = createWCAccess();
		for (int i = 0; i < paths.length; i++) {
			checkCancelled();
			File path = paths[i].getAbsoluteFile();
			Collection changelistsSet = null;
			if (changelists != null && changelists.length > 0) {
				changelistsSet = new SVNHashSet();
				for (int j = 0; j < changelists.length; j++) {
					changelistsSet.add(changelists[j]);
				}
			}
			try {
				wcAccess.probeOpen(path, true, -1);
				wcAccess.walkEntries(path, new SVNChangeListWalker(wcAccess,
						changelistName, changelistsSet), false, depth);
			} finally {
				wcAccess.close();
			}
		}
	}

	private class SVNChangeListWalker implements ISVNEntryHandler {
		private String myChangelist;
		private Collection myChangelists;
		private SVNWCAccess myWCAccess;

		public SVNChangeListWalker(SVNWCAccess wcAccess, String changelistName,
				Collection changelists) {
			myChangelist = changelistName;
			myChangelists = changelists;
			myWCAccess = wcAccess;
		}

		public void handleEntry(File path, SVNEntry entry) throws SVNException {
			if (!SVNWCAccess.matchesChangeList(myChangelists, entry)) {
				return;
			}
			if (!entry.isFile()) {
				if (entry.isThisDir()) {
					SVNEventAction action = myChangelist != null ? SVNEventAction.CHANGELIST_SET
							: SVNEventAction.CHANGELIST_CLEAR;
					SVNEvent event = SVNEventFactory.createSVNEvent(path,
							SVNNodeKind.DIR, null,
							SVNRepository.INVALID_REVISION,
							SVNEventAction.SKIP, action, null, null);
					SVNChangelistClient16.this.dispatchEvent(event);
				}
				return;
			}
			if (entry.getChangelistName() == null && myChangelist == null) {
				return;
			}
			if (entry.getChangelistName() != null
					&& entry.getChangelistName().equals(myChangelist)) {
				return;
			}
			if (myChangelist != null && entry.getChangelistName() != null) {
				SVNErrorMessage err = SVNErrorMessage.create(
						SVNErrorCode.WC_CHANGELIST_MOVE,
						"Removing ''{0}'' from changelist ''{1}''.",
						new Object[] { path, entry.getChangelistName() });
				SVNEvent event = SVNEventFactory.createSVNEvent(path,
						SVNNodeKind.FILE, null, SVNRepository.INVALID_REVISION,
						SVNEventAction.CHANGELIST_MOVED,
						SVNEventAction.CHANGELIST_MOVED, err, null);
				SVNChangelistClient16.this.dispatchEvent(event);
			}
			Map attributes = new SVNHashMap();
			attributes.put(SVNProperty.CHANGELIST, myChangelist);
			SVNAdminArea area = myWCAccess.retrieve(path.getParentFile());
			entry = area.modifyEntry(entry.getName(), attributes, true, false);
			SVNEvent event = SVNEventFactory.createSVNEvent(path,
					SVNNodeKind.UNKNOWN, null, SVNRepository.INVALID_REVISION,
					null, null, null,
					myChangelist != null ? SVNEventAction.CHANGELIST_SET
							: SVNEventAction.CHANGELIST_CLEAR, null, null,
					null, myChangelist);
			SVNChangelistClient16.this.dispatchEvent(event);
		}

		public void handleError(File path, SVNErrorMessage error)
				throws SVNException {
			SVNErrorManager.error(error, SVNLogType.WC);
		}
	}
}
