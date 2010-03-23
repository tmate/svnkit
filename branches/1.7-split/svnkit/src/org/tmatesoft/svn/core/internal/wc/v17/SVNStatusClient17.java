package org.tmatesoft.svn.core.internal.wc.v17;

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

public class SVNStatusClient17 extends SVNStatusClient {
	protected SVNStatusClient dispatcher;

	protected SVNStatusClient17(SVNStatusClient from) {
		super(from);
		this.dispatcher = dispatcher;
	}

	public static SVNStatusClient17 delegate(SVNStatusClient dispatcher) {
		SVNStatusClient17 delegate = new SVNStatusClient17(dispatcher);
		return delegate;
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
		SVNErrorMessage err = SVNErrorMessage
				.create(SVNErrorCode.VERSION_MISMATCH);
		SVNErrorManager.error(err, SVNLogType.CLIENT);
		return 0;
	}
}
