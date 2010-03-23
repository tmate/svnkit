package org.tmatesoft.svn.core.internal.wc.v16;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.AbstractDiffCallback;
import org.tmatesoft.svn.core.internal.wc.SVNAmbientDepthFilterEditor;
import org.tmatesoft.svn.core.internal.wc.SVNCancellableEditor;
import org.tmatesoft.svn.core.internal.wc.SVNDiffCallback;
import org.tmatesoft.svn.core.internal.wc.SVNDiffEditor;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.admin.SVNAdminArea;
import org.tmatesoft.svn.core.internal.wc.admin.SVNAdminAreaInfo;
import org.tmatesoft.svn.core.internal.wc.admin.SVNEntry;
import org.tmatesoft.svn.core.internal.wc.admin.SVNReporter;
import org.tmatesoft.svn.core.internal.wc.admin.SVNWCAccess;
import org.tmatesoft.svn.core.internal.wc.patch.SVNPatch;
import org.tmatesoft.svn.core.internal.wc.patch.SVNPatchFileStream;
import org.tmatesoft.svn.core.internal.wc.patch.SVNPatchTarget;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.SVNCapability;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.wc.DefaultSVNDiffGenerator;
import org.tmatesoft.svn.core.wc.ISVNDiffGenerator;
import org.tmatesoft.svn.core.wc.ISVNOptions;
import org.tmatesoft.svn.core.wc.ISVNRepositoryPool;
import org.tmatesoft.svn.core.wc.SVNDiffClient;
import org.tmatesoft.svn.core.wc.SVNDiffClient;
import org.tmatesoft.svn.core.wc.SVNDiffOptions;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.util.SVNLogType;

public class SVNDiffClient16 extends SVNDiffClient {
	protected SVNDiffClient dispatcher;

	protected SVNDiffClient16(SVNDiffClient from) {
		super(from);
		this.dispatcher = dispatcher;
	}

	public static SVNDiffClient16 delegate(SVNDiffClient dispatcher) {
		SVNDiffClient16 delegate = new SVNDiffClient16(dispatcher);
		return delegate;
	}

	/** 
	 * Constructs and initializes an <b>SVNDiffClient</b> object with the
	 * specified run-time configuration and repository pool object.
	 * <p/>
	 * If <code>options</code> is <span class="javakeyword">null</span>, then
	 * this <b>SVNDiffClient</b> will be using a default run-time configuration
	 * driver which takes client-side settings from the default SVN's run-time
	 * configuration area but is not able to change those settings (read more on{@link ISVNOptions} and {@link SVNWCUtil}).
	 * <p/>
	 * If <code>repositoryPool</code> is <span class="javakeyword">null</span>,
	 * then {@link org.tmatesoft.svn.core.io.SVNRepositoryFactory} will be used
	 * to create {@link SVNRepository repository access objects}.
	 * @param repositoryPoola repository pool object
	 * @param optionsa run-time configuration options driver
	 * @from org.tmatesoft.svn.core.wc.SVNDiffClient
	 */
	public SVNDiffClient16(ISVNRepositoryPool repositoryPool,
			ISVNOptions options) {
		super(repositoryPool, options);
	}

	/** 
	 * Constructs and initializes an <b>SVNDiffClient</b> object with the
	 * specified run-time configuration and authentication drivers.
	 * <p/>
	 * If <code>options</code> is <span class="javakeyword">null</span>, then
	 * this <b>SVNDiffClient</b> will be using a default run-time configuration
	 * driver which takes client-side settings from the default SVN's run-time
	 * configuration area but is not able to change those settings (read more on{@link ISVNOptions} and {@link SVNWCUtil}).
	 * <p/>
	 * If <code>authManager</code> is <span class="javakeyword">null</span>,
	 * then this <b>SVNDiffClient</b> will be using a default authentication and
	 * network layers driver (see{@link SVNWCUtil#createDefaultAuthenticationManager()}) which uses
	 * server-side settings and auth storage from the default SVN's run-time
	 * configuration area (or system properties if that area is not found).
	 * @param authManageran authentication and network layers driver
	 * @param optionsa run-time configuration options driver
	 * @from org.tmatesoft.svn.core.wc.SVNDiffClient
	 */
	public SVNDiffClient16(ISVNAuthenticationManager authManager,
			ISVNOptions options) {
		super(authManager, options);
	}

	/** 
	 * Gets the diff options that are used in merge operations by this client.
	 * If none was provided by the user, one created as
	 * <code>new SVNDiffOptions()</code> will be returned and used further.
	 * @return diff options
	 * @from org.tmatesoft.svn.core.wc.SVNDiffClient
	 */
	public SVNDiffOptions getMergeOptions() {
		if (myDiffOptions == null) {
			myDiffOptions = new SVNDiffOptions();
		}
		return myDiffOptions;
	}

	/** 
	 * @from org.tmatesoft.svn.core.wc.SVNDiffClient
	 */
	public void doPatch(File absPatchPath, File localAbsPath, boolean dryRun,
			int stripCount) throws SVNException {
		if (stripCount < 0) {
			SVNErrorMessage err = SVNErrorMessage.create(
					SVNErrorCode.INCORRECT_PARAMS,
					"strip count must be positive");
			SVNErrorManager.error(err, SVNLogType.CLIENT);
		}
		final SVNWCAccess wcAccess = createWCAccess();
		try {
			wcAccess.setEventHandler(this);
			final SVNAdminArea wc = wcAccess.open(localAbsPath, true,
					SVNWCAccess.INFINITE_DEPTH);
			applyPatches(absPatchPath, localAbsPath, dryRun, stripCount, wc);
		} catch (IOException e) {
			SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR,
					e.getMessage());
			SVNErrorManager.error(err, SVNLogType.CLIENT);
		} finally {
			wcAccess.close();
		}
	}

	/** 
	 * @from org.tmatesoft.svn.core.wc.SVNDiffClient
	 */
	public void doDiffWCWC(File path1, SVNRevision revision1, File path2,
			SVNRevision revision2, SVNDepth depth, boolean useAncestry,
			OutputStream result, Collection changeLists) throws SVNException {
		if (!path1.equals(path2)
				|| !(revision1 == SVNRevision.BASE && revision2 == SVNRevision.WORKING)) {
			SVNErrorMessage err = SVNErrorMessage
					.create(
							SVNErrorCode.UNSUPPORTED_FEATURE,
							"Only diffs between a path's text-base and its working files are supported at this time (-rBASE:WORKING)");
			SVNErrorManager.error(err, SVNLogType.WC);
		}
		SVNWCAccess wcAccess = createWCAccess();
		try {
			int admDepth = getAdminDepth(depth);
			SVNAdminAreaInfo info = wcAccess.openAnchor(path1, false, admDepth);
			wcAccess.getVersionedEntry(path1, false);
			long rev = getRevisionNumber(revision1, null, path1);
			AbstractDiffCallback callback = new SVNDiffCallback(info
					.getAnchor(), getDiffGenerator(), rev, -1, result);
			SVNDiffEditor editor = new SVNDiffEditor(wcAccess, info, callback,
					useAncestry, false, false, depth, changeLists);
			try {
				editor.closeEdit();
			} finally {
				editor.cleanup();
			}
		} finally {
			wcAccess.close();
		}
	}

	/** 
	 * @from org.tmatesoft.svn.core.wc.SVNDiffClient
	 */
	public void applyPatches(File absPatchPath, File absWCPath, boolean dryRun,
			int stripCount, SVNAdminArea wc) throws SVNException, IOException {
		final SVNPatchFileStream patchFile = SVNPatchFileStream
				.openReadOnly(absPatchPath);
		try {
			final List targets = new ArrayList();
			SVNPatch patch;
			do {
				checkCancelled();
				patch = SVNPatch.parseNextPatch(patchFile);
				if (patch != null) {
					final SVNPatchTarget target = SVNPatchTarget.applyPatch(
							patch, absWCPath, stripCount, wc);
					targets.add(target);
				}
			} while (patch != null);
			for (Iterator i = targets.iterator(); i.hasNext();) {
				checkCancelled();
				final SVNPatchTarget target = (SVNPatchTarget) i.next();
				if (!target.isSkipped()) {
					target.installPatchedTarget(absWCPath, dryRun, wc);
				}
				target.sendPatchNotification(wc);
				target.getPatch().close();
			}
		} catch (IOException e) {
			SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR,
					e.getMessage(), null, 0, e);
			SVNErrorManager.error(err, Level.FINE, SVNLogType.WC);
		} finally {
			if (patchFile != null) {
				patchFile.close();
			}
		}
	}

	/** 
	 * @from org.tmatesoft.svn.core.wc.SVNDiffClient
	 */
	public void doDiffURLWC(SVNURL url1, SVNRevision revision1,
			SVNRevision pegRevision, File path2, SVNRevision revision2,
			boolean reverse, SVNDepth depth, boolean useAncestry,
			OutputStream result, Collection changeLists) throws SVNException {
		SVNWCAccess wcAccess = createWCAccess();
		try {
			SVNAdminAreaInfo info = wcAccess.openAnchor(path2, false, SVNDepth
					.recurseFromDepth(depth) ? SVNWCAccess.INFINITE_DEPTH : 0);
			File anchorPath = info.getAnchor().getRoot();
			String target = "".equals(info.getTargetName()) ? null : info
					.getTargetName();
			SVNEntry anchorEntry = info.getAnchor()
					.getVersionedEntry("", false);
			if (anchorEntry.getURL() == null) {
				SVNErrorMessage err = SVNErrorMessage.create(
						SVNErrorCode.ENTRY_MISSING_URL, "''{0}'' has no URL",
						anchorPath);
				SVNErrorManager.error(err, SVNLogType.WC);
			}
			SVNURL anchorURL = anchorEntry.getSVNURL();
			if (pegRevision.isValid()) {
				SVNRepositoryLocation[] locations = getLocations(url1, null,
						null, pegRevision, revision1, SVNRevision.UNDEFINED);
				url1 = locations[0].getURL();
				String anchorPath2 = SVNPathUtil.append(anchorURL.toString(),
						target == null ? "" : target);
				getDiffGenerator().init(url1.toString(), anchorPath2);
			}
			SVNRepository repository = createRepository(anchorURL, null, null,
					true);
			long revNumber = getRevisionNumber(revision1, repository, null);
			AbstractDiffCallback callback = new SVNDiffCallback(info
					.getAnchor(), getDiffGenerator(), reverse ? -1 : revNumber,
					reverse ? revNumber : -1, result);
			SVNDiffEditor editor = new SVNDiffEditor(wcAccess, info, callback,
					useAncestry, reverse, revision2 == SVNRevision.BASE
							|| revision2 == SVNRevision.COMMITTED, depth,
					changeLists);
			boolean serverSupportsDepth = repository
					.hasCapability(SVNCapability.DEPTH);
			SVNReporter reporter = new SVNReporter(info, info.getAnchor()
					.getFile(info.getTargetName()), false,
					!serverSupportsDepth, depth, false, false, true,
					getDebugLog());
			long pegRevisionNumber = getRevisionNumber(revision2, repository,
					path2);
			try {
				repository.diff(url1, revNumber, pegRevisionNumber, target,
						!useAncestry, depth, true, reporter,
						SVNCancellableEditor.newInstance(editor, this,
								getDebugLog()));
			} finally {
				editor.cleanup();
			}
		} finally {
			wcAccess.close();
		}
	}

	/** 
	 * @from org.tmatesoft.svn.core.wc.SVNDiffClient
	 */
	public int getAdminDepth(SVNDepth depth) {
		int admDepth = SVNWCAccess.INFINITE_DEPTH;
		if (depth == SVNDepth.IMMEDIATES) {
			admDepth = 1;
		} else if (depth == SVNDepth.EMPTY || depth == SVNDepth.FILES) {
			admDepth = 0;
		}
		return admDepth;
	}

	/** 
	 * @from org.tmatesoft.svn.core.wc.SVNDiffClient
	 */
	public Object[] getLocationFromPathAndRevision(File path, SVNURL url,
			SVNRevision pegRevision) throws SVNException {
		SVNWCAccess wcAccess = null;
		SVNRepository repos = null;
		SVNAdminArea adminArea = null;
		try {
			if (path != null
					&& (pegRevision == SVNRevision.BASE
							|| pegRevision == SVNRevision.WORKING
							|| pegRevision == SVNRevision.COMMITTED || pegRevision == SVNRevision.UNDEFINED)) {
				int admLockLevel = getLevelsToLockFromDepth(SVNDepth.EMPTY);
				wcAccess = createWCAccess();
				wcAccess.probeOpen(path, false, admLockLevel);
			}
			long[] rev = { SVNRepository.INVALID_REVISION };
			repos = createRepository(url, path, adminArea, pegRevision,
					pegRevision, rev);
			return new Object[] { repos.getLocation(),
					SVNRevision.create(rev[0]) };
		} finally {
			if (wcAccess != null) {
				wcAccess.close();
			}
			if (repos != null) {
				repos.closeSession();
			}
		}
	}

	/** 
	 * Returns the diff driver being in use.
	 * <p>
	 * If no specific diff driver was previously provided, a default one will be
	 * returned (see {@link DefaultSVNDiffGenerator}).
	 * @return the diff driver being in use
	 * @see #setDiffGenerator(ISVNDiffGenerator)
	 * @from org.tmatesoft.svn.core.wc.SVNDiffClient
	 */
	public ISVNDiffGenerator getDiffGenerator() {
		if (myDiffGenerator == null) {
			DefaultSVNDiffGenerator defaultDiffGenerator = new DefaultSVNDiffGenerator();
			defaultDiffGenerator.setOptions(getOptions());
			myDiffGenerator = defaultDiffGenerator;
		}
		return myDiffGenerator;
	}

	/** 
	 * @from org.tmatesoft.svn.core.wc.SVNDiffClient
	 */
	public void doDiffURLWC(File path1, SVNRevision revision1,
			SVNRevision pegRevision, File path2, SVNRevision revision2,
			boolean reverse, SVNDepth depth, boolean useAncestry,
			OutputStream result, Collection changeLists) throws SVNException {
		SVNWCAccess wcAccess = createWCAccess();
		try {
			int admDepth = getAdminDepth(depth);
			SVNAdminAreaInfo info = wcAccess.openAnchor(path2, false, admDepth);
			File anchorPath = info.getAnchor().getRoot();
			String target = "".equals(info.getTargetName()) ? null : info
					.getTargetName();
			SVNEntry anchorEntry = info.getAnchor()
					.getVersionedEntry("", false);
			if (anchorEntry.getURL() == null) {
				SVNErrorMessage err = SVNErrorMessage.create(
						SVNErrorCode.ENTRY_MISSING_URL, "''{0}'' has no URL",
						anchorPath);
				SVNErrorManager.error(err, SVNLogType.WC);
			}
			SVNURL url1;
			SVNURL anchorURL = anchorEntry.getSVNURL();
			if (pegRevision.isValid()) {
				SVNRepositoryLocation[] locations = getLocations(null, path1,
						null, pegRevision, revision1, SVNRevision.UNDEFINED);
				url1 = locations[0].getURL();
				String anchorPath2 = SVNPathUtil.append(anchorURL.toString(),
						target == null ? "" : target);
				if (!reverse) {
					getDiffGenerator().init(url1.toString(), anchorPath2);
				} else {
					getDiffGenerator().init(anchorPath2, url1.toString());
				}
			} else {
				url1 = getURL(path1);
			}
			SVNRepository repository = createRepository(anchorURL, null, null,
					true);
			long revNumber = getRevisionNumber(revision1, repository, path1);
			AbstractDiffCallback callback = new SVNDiffCallback(info
					.getAnchor(), getDiffGenerator(), reverse ? -1 : revNumber,
					reverse ? revNumber : -1, result);
			SVNDiffEditor editor = new SVNDiffEditor(wcAccess, info, callback,
					useAncestry, reverse, revision2 == SVNRevision.BASE
							|| revision2 == SVNRevision.COMMITTED, depth,
					changeLists);
			ISVNEditor filterEditor = SVNAmbientDepthFilterEditor.wrap(editor,
					info, false);
			boolean serverSupportsDepth = repository
					.hasCapability(SVNCapability.DEPTH);
			SVNReporter reporter = new SVNReporter(info, info.getAnchor()
					.getFile(info.getTargetName()), false,
					!serverSupportsDepth, depth, false, false, true,
					getDebugLog());
			long pegRevisionNumber = getRevisionNumber(revision2, repository,
					path2);
			try {
				repository.diff(url1, revNumber, pegRevisionNumber, target,
						!useAncestry, depth, true, reporter,
						SVNCancellableEditor.newInstance(filterEditor, this,
								getDebugLog()));
			} finally {
				editor.cleanup();
			}
		} finally {
			wcAccess.close();
		}
	}
}
