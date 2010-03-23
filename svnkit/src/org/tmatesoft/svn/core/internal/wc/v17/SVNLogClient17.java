package org.tmatesoft.svn.core.internal.wc.v17;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.TreeSet;
import org.tmatesoft.svn.core.ISVNLogEntryHandler;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLogEntry;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.internal.util.SVNEncodingUtil;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.util.SVNURLUtil;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.admin.SVNAdminArea;
import org.tmatesoft.svn.core.internal.wc.admin.SVNEntry;
import org.tmatesoft.svn.core.internal.wc.admin.SVNWCAccess;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.wc.ISVNOptions;
import org.tmatesoft.svn.core.wc.ISVNRepositoryPool;
import org.tmatesoft.svn.core.wc.SVNLogClient;
import org.tmatesoft.svn.core.wc.SVNLogClient;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNRevisionRange;
import org.tmatesoft.svn.util.SVNLogType;

public class SVNLogClient17 extends SVNLogClient {
	protected SVNLogClient dispatcher;

	protected SVNLogClient17(SVNLogClient from) {
		super(from);
		this.dispatcher = dispatcher;
	}

	public static SVNLogClient17 delegate(SVNLogClient dispatcher) {
		SVNLogClient17 delegate = new SVNLogClient17(dispatcher);
		return delegate;
	}

	/** 
	 * Invokes <code>handler</code> on each log message from the given <code>revisionRanges</code> in turn, inclusive 
	 * (but never invokes <code>handler</code> on a given log message more than once).
	 * <p/>
	 * <code>handler</code> is invoked only on messages whose revisions involved a change to some path in <code>paths</code>. 
	 * <code>pegRevision</code> indicates in which revision <code>paths</code> are valid. If <code>pegRevision</code> is{@link SVNRevision#isValid() invalid}, it defaults to {@link SVNRevision#WORKING}.
	 * <p/>
	 * If <code>limit</code> is non-zero, only invokes <code>handler</code> on the first <code>limit</code> logs.
	 * <p/>
	 * If <code>discoverChangedPaths</code> is set, then the changed paths <code>Map</code> argument
	 * will be passed to a constructor of {@link SVNLogEntry} on each invocation of <code>handler</code>.
	 * <p/>
	 * If <code>stopOnCopy</code> is set, copy history (if any exists) will not be traversed while harvesting 
	 * revision logs for each target.
	 * <p/>
	 * If <code>includeMergedRevisions</code> is set, log information for revisions which have been merged to 
	 * <code>paths</code> will also be returned.
	 * <p/> 
	 * Refer to {@link org.tmatesoft.svn.core.SVNLogEntry#hasChildren()} for additional information on how 
	 * to handle mergeinfo information during a log operation.
	 * <p/>
	 * If <code>revisionProperties is <span class="javakeyword">null</span>, retrieves all revision properties; 
	 * else, retrieves only the revprops named in the array (i.e. retrieves none if the array is empty).
	 * <p/>
	 * For every {@link SVNRevisionRange} in <code>revisionRanges</code>:
	 * <b/>
	 * If <code>startRevision</code> is {@link SVNRevision#isValid() valid} but <code>endRevision</code> 
	 * is not, then <code>endRevision</code> defaults to <code>startRevision</code>. If both 
	 * <code>startRevision</code> and <code>endRevision</code> are invalid, then <code>endRevision</code> 
	 * defaults to revision <code>0</code>, and <code>startRevision</code> defaults either to 
	 * <code>pegRevision</code> in case the latter one is valid, or to {@link SVNRevision#BASE}, if it is not.
	 * <p/>
	 * Important: to avoid an exception with the {@link SVNErrorCode#FS_NO_SUCH_REVISION} error code  
	 * when invoked against an empty repository (i.e. one not containing a revision 1), callers should specify 
	 * the range {@link SVNRevision#HEAD}:<code>0</code>. 
	 * <p/>
	 * If the caller has provided a non-<span class="javakeyword">null</span> {@link ISVNEventHandler},
	 * it will be called with the {@link SVNEventAction#SKIP} event action on any unversioned paths. 
	 * <p/>
	 * Note: this routine requires repository access.
	 * @param paths                  an array of Working Copy paths, for which log messages are desired
	 * @param revisionRanges         collection of {@link SVNRevisionRange} objects 
	 * @param pegRevision            a revision in which <code>paths</code> are first looked up
	 * in the repository
	 * @param stopOnCopy             <span class="javakeyword">true</span> not to cross
	 * copies while traversing history, otherwise copies history
	 * will be also included into processing
	 * @param discoverChangedPaths   <span class="javakeyword">true</span> to report
	 * of all changed paths for every revision being processed 
	 * (those paths will be available by calling {@link org.tmatesoft.svn.core.SVNLogEntry#getChangedPaths()})
	 * @param includeMergedRevisions if <span class="javakeyword">true</span>, merged revisions will be also 
	 * reported
	 * @param limit                  a maximum number of log entries to be processed 
	 * @param revisionProperties     names of revision properties to retrieve     
	 * @param handler                a caller's log entry handler
	 * @throws SVNException           if one of the following is true:
	 * <ul>
	 * <li>can not obtain a URL of a WC path - there's no such
	 * entry in the Working Copy
	 * <li><code>paths</code> contain entries that belong to
	 * different repositories
	 * </ul>
	 * @since                         1.3, SVN 1.6 
	 * @from org.tmatesoft.svn.core.wc.SVNLogClient
	 */
	public void doLog(File[] paths, Collection revisionRanges,
			SVNRevision pegRevision, boolean stopOnCopy,
			boolean discoverChangedPaths, boolean includeMergedRevisions,
			long limit, String[] revisionProperties,
			final ISVNLogEntryHandler handler) throws SVNException {
		SVNErrorMessage err = SVNErrorMessage
				.create(SVNErrorCode.VERSION_MISMATCH);
		SVNErrorManager.error(err, SVNLogType.CLIENT);
	}

	/** 
	 * @from org.tmatesoft.svn.core.wc.SVNLogClient
	 */
	public boolean needsWC(SVNRevision revision) {
		return false;
	}
}
