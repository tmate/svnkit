package org.tmatesoft.svn.core.internal.wc.v17;

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

public class SVNChangelistClient17 extends SVNChangelistClient {
	protected SVNChangelistClient dispatcher;

	protected SVNChangelistClient17(SVNChangelistClient from) {
		super(from);
		this.dispatcher = dispatcher;
	}

	public static SVNChangelistClient17 delegate(SVNChangelistClient dispatcher) {
		SVNChangelistClient17 delegate = new SVNChangelistClient17(dispatcher);
		return delegate;
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
		SVNErrorMessage err = SVNErrorMessage
				.create(SVNErrorCode.VERSION_MISMATCH);
		SVNErrorManager.error(err, SVNLogType.CLIENT);
	}

	/** 
	 * @from org.tmatesoft.svn.core.wc.SVNChangelistClient
	 */
	public void setChangelist(File[] paths, String changelistName,
			String[] changelists, SVNDepth depth) throws SVNException {
		SVNErrorMessage err = SVNErrorMessage
				.create(SVNErrorCode.VERSION_MISMATCH);
		SVNErrorManager.error(err, SVNLogType.CLIENT);
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
					SVNChangelistClient17.this.dispatchEvent(event);
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
				SVNChangelistClient17.this.dispatchEvent(event);
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
			SVNChangelistClient17.this.dispatchEvent(event);
		}

		public void handleError(File path, SVNErrorMessage error)
				throws SVNException {
			SVNErrorManager.error(error, SVNLogType.WC);
		}
	}
}
