package org.tmatesoft.svn.core.internal.wc.v16;

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

public class SVNLogClient16 extends SVNLogClient {
	protected SVNLogClient dispatcher;

	protected SVNLogClient16(SVNLogClient from) {
		super(from);
		this.dispatcher = dispatcher;
	}

	public static SVNLogClient16 delegate(SVNLogClient dispatcher) {
		SVNLogClient16 delegate = new SVNLogClient16(dispatcher);
		return delegate;
	}

	/** 
	 * Constructs and initializes an <b>SVNLogClient</b> object
	 * with the specified run-time configuration and authentication 
	 * drivers.
	 * <p>
	 * If <code>options</code> is <span class="javakeyword">null</span>,
	 * then this <b>SVNLogClient</b> will be using a default run-time
	 * configuration driver  which takes client-side settings from the 
	 * default SVN's run-time configuration area but is not able to
	 * change those settings (read more on {@link ISVNOptions} and {@link SVNWCUtil}).  
	 * <p>
	 * If <code>authManager</code> is <span class="javakeyword">null</span>,
	 * then this <b>SVNLogClient</b> will be using a default authentication
	 * and network layers driver (see {@link SVNWCUtil#createDefaultAuthenticationManager()})
	 * which uses server-side settings and auth storage from the 
	 * default SVN's run-time configuration area (or system properties
	 * if that area is not found).
	 * @param authManager an authentication and network layers driver
	 * @param options     a run-time configuration options driver     
	 * @from org.tmatesoft.svn.core.wc.SVNLogClient
	 */
	public SVNLogClient16(ISVNAuthenticationManager authManager,
			ISVNOptions options) {
		super(authManager, options);
	}

	/** 
	 * Constructs and initializes an <b>SVNLogClient</b> object
	 * with the specified run-time configuration and authentication 
	 * drivers.
	 * <p>
	 * If <code>options</code> is <span class="javakeyword">null</span>,
	 * then this <b>SVNLogClient</b> will be using a default run-time
	 * configuration driver  which takes client-side settings from the 
	 * default SVN's run-time configuration area but is not able to
	 * change those settings (read more on {@link ISVNOptions} and {@link SVNWCUtil}).  
	 * <p/>
	 * If <code>repositoryPool</code> is <span class="javakeyword">null</span>,
	 * then {@link org.tmatesoft.svn.core.io.SVNRepositoryFactory} will be used to create {@link SVNRepository repository access objects}.
	 * @param repositoryPool   a repository pool object
	 * @param options          a run-time configuration options driver
	 * @from org.tmatesoft.svn.core.wc.SVNLogClient
	 */
	public SVNLogClient16(ISVNRepositoryPool repositoryPool, ISVNOptions options) {
		super(repositoryPool, options);
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
		if (paths == null || paths.length == 0 || handler == null) {
			return;
		}
		SVNRevision sessionRevision = SVNRevision.UNDEFINED;
		List editedRevisionRanges = new LinkedList();
		for (Iterator revRangesIter = revisionRanges.iterator(); revRangesIter
				.hasNext();) {
			SVNRevisionRange revRange = (SVNRevisionRange) revRangesIter.next();
			if (revRange.getStartRevision().isValid()
					&& !revRange.getEndRevision().isValid()) {
				revRange = new SVNRevisionRange(revRange.getStartRevision(),
						revRange.getStartRevision());
			} else if (!revRange.getStartRevision().isValid()) {
				SVNRevision start = SVNRevision.UNDEFINED;
				SVNRevision end = SVNRevision.UNDEFINED;
				if (!pegRevision.isValid()) {
					start = SVNRevision.BASE;
				} else {
					start = pegRevision;
				}
				if (!revRange.getEndRevision().isValid()) {
					end = SVNRevision.create(0);
				}
				revRange = new SVNRevisionRange(start, end);
			}
			if (!revRange.getStartRevision().isValid()
					|| !revRange.getEndRevision().isValid()) {
				SVNErrorMessage err = SVNErrorMessage.create(
						SVNErrorCode.CLIENT_BAD_REVISION,
						"Missing required revision specification");
				SVNErrorManager.error(err, SVNLogType.WC);
			}
			editedRevisionRanges.add(revRange);
			if (!sessionRevision.isValid()) {
				SVNRevision start = revRange.getStartRevision();
				SVNRevision end = revRange.getEndRevision();
				if (SVNRevision.isValidRevisionNumber(start.getNumber())
						&& SVNRevision.isValidRevisionNumber(end.getNumber())) {
					sessionRevision = start.getNumber() > end.getNumber() ? start
							: end;
				} else if (start.getDate() != null && end.getDate() != null) {
					sessionRevision = start.getDate().compareTo(end.getDate()) > 0 ? start
							: end;
				}
			}
		}
		if (limit > Integer.MAX_VALUE) {
			limit = Integer.MAX_VALUE;
		}
		ISVNLogEntryHandler wrappingHandler = new ISVNLogEntryHandler() {
			public void handleLogEntry(SVNLogEntry logEntry)
					throws SVNException {
				checkCancelled();
				handler.handleLogEntry(logEntry);
			}
		};
		SVNURL[] urls = new SVNURL[paths.length];
		SVNWCAccess wcAccess = createWCAccess();
		Collection wcPaths = new ArrayList();
		for (int i = 0; i < paths.length; i++) {
			checkCancelled();
			File path = paths[i];
			wcPaths
					.add(path.getAbsolutePath()
							.replace(File.separatorChar, '/'));
			SVNAdminArea area = wcAccess.probeOpen(path, false, 0);
			SVNEntry entry = wcAccess.getVersionedEntry(path, false);
			if (entry.getURL() == null) {
				SVNErrorMessage err = SVNErrorMessage.create(
						SVNErrorCode.ENTRY_MISSING_URL,
						"Entry ''{0}'' has no URL", path);
				SVNErrorManager.error(err, SVNLogType.WC);
			}
			urls[i] = entry.getSVNURL();
			if (area != null) {
				wcAccess.closeAdminArea(area.getRoot());
			}
		}
		if (urls.length == 0) {
			return;
		}
		String[] wcPathsArray = (String[]) wcPaths.toArray(new String[wcPaths
				.size()]);
		String rootWCPath = SVNPathUtil.condencePaths(wcPathsArray, null, true);
		Collection targets = new TreeSet();
		SVNURL baseURL = SVNURLUtil.condenceURLs(urls, targets, true);
		if (baseURL == null) {
			SVNErrorMessage err = SVNErrorMessage.create(
					SVNErrorCode.ILLEGAL_TARGET,
					"target log paths belong to different repositories");
			SVNErrorManager.error(err, SVNLogType.WC);
		}
		if (targets.isEmpty()) {
			targets.add("");
		}
		SVNRepository repos = null;
		if (rootWCPath != null && needsWC(pegRevision)) {
			File root = new File(rootWCPath);
			SVNAdminArea area = wcAccess.probeOpen(root, false, 0);
			repos = createRepository(null, root, area, pegRevision,
					sessionRevision, null);
			if (area != null) {
				wcAccess.closeAdminArea(area.getRoot());
			}
		} else {
			repos = createRepository(baseURL, null, null, pegRevision,
					sessionRevision, null);
		}
		String[] targetPaths = (String[]) targets.toArray(new String[targets
				.size()]);
		for (int i = 0; i < targetPaths.length; i++) {
			targetPaths[i] = SVNEncodingUtil.uriDecode(targetPaths[i]);
		}
		for (Iterator revRangesIter = editedRevisionRanges.iterator(); revRangesIter
				.hasNext();) {
			checkCancelled();
			SVNRevisionRange revRange = (SVNRevisionRange) revRangesIter.next();
			SVNRevision startRevision = revRange.getStartRevision();
			SVNRevision endRevision = revRange.getEndRevision();
			if (startRevision.isLocal() || endRevision.isLocal()) {
				for (int i = 0; i < paths.length; i++) {
					checkCancelled();
					long startRev = getRevisionNumber(startRevision, repos,
							paths[i]);
					long endRev = getRevisionNumber(endRevision, repos,
							paths[i]);
					repos.log(targetPaths, startRev, endRev,
							discoverChangedPaths, stopOnCopy, limit,
							includeMergedRevisions, revisionProperties,
							wrappingHandler);
				}
			} else {
				long startRev = getRevisionNumber(startRevision, repos, null);
				long endRev = getRevisionNumber(endRevision, repos, null);
				repos.log(targetPaths, startRev, endRev, discoverChangedPaths,
						stopOnCopy, limit, includeMergedRevisions,
						revisionProperties, wrappingHandler);
			}
		}
	}

	/** 
	 * @from org.tmatesoft.svn.core.wc.SVNLogClient
	 */
	public boolean needsWC(SVNRevision revision) {
		return revision == SVNRevision.BASE
				|| revision == SVNRevision.COMMITTED
				|| revision == SVNRevision.WORKING
				|| revision == SVNRevision.PREVIOUS;
	}
}
