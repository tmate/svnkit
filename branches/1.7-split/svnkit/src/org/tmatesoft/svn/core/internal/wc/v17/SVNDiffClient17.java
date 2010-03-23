package org.tmatesoft.svn.core.internal.wc.v17;

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

public class SVNDiffClient17 extends SVNDiffClient {
	protected SVNDiffClient dispatcher;

	protected SVNDiffClient17(SVNDiffClient from) {
		super(from);
		this.dispatcher = dispatcher;
	}

	public static SVNDiffClient17 delegate(SVNDiffClient dispatcher) {
		SVNDiffClient17 delegate = new SVNDiffClient17(dispatcher);
		return delegate;
	}

	/** 
	 * Gets the diff options that are used in merge operations by this client.
	 * If none was provided by the user, one created as
	 * <code>new SVNDiffOptions()</code> will be returned and used further.
	 * @return diff options
	 * @from org.tmatesoft.svn.core.wc.SVNDiffClient
	 */
	public SVNDiffOptions getMergeOptions() {
		return null;
	}

	/** 
	 * @from org.tmatesoft.svn.core.wc.SVNDiffClient
	 */
	public void doPatch(File absPatchPath, File localAbsPath, boolean dryRun,
			int stripCount) throws SVNException {
		SVNErrorMessage err = SVNErrorMessage
				.create(SVNErrorCode.VERSION_MISMATCH);
		SVNErrorManager.error(err, SVNLogType.CLIENT);
	}

	/** 
	 * @from org.tmatesoft.svn.core.wc.SVNDiffClient
	 */
	public void doDiffWCWC(File path1, SVNRevision revision1, File path2,
			SVNRevision revision2, SVNDepth depth, boolean useAncestry,
			OutputStream result, Collection changeLists) throws SVNException {
		SVNErrorMessage err = SVNErrorMessage
				.create(SVNErrorCode.VERSION_MISMATCH);
		SVNErrorManager.error(err, SVNLogType.CLIENT);
	}

	/** 
	 * @from org.tmatesoft.svn.core.wc.SVNDiffClient
	 */
	public void applyPatches(File absPatchPath, File absWCPath, boolean dryRun,
			int stripCount, SVNAdminArea wc) throws SVNException, IOException {
		SVNErrorMessage err = SVNErrorMessage
				.create(SVNErrorCode.VERSION_MISMATCH);
		SVNErrorManager.error(err, SVNLogType.CLIENT);
	}

	/** 
	 * @from org.tmatesoft.svn.core.wc.SVNDiffClient
	 */
	public void doDiffURLWC(SVNURL url1, SVNRevision revision1,
			SVNRevision pegRevision, File path2, SVNRevision revision2,
			boolean reverse, SVNDepth depth, boolean useAncestry,
			OutputStream result, Collection changeLists) throws SVNException {
		SVNErrorMessage err = SVNErrorMessage
				.create(SVNErrorCode.VERSION_MISMATCH);
		SVNErrorManager.error(err, SVNLogType.CLIENT);
	}

	/** 
	 * @from org.tmatesoft.svn.core.wc.SVNDiffClient
	 */
	public int getAdminDepth(SVNDepth depth) {
		return 0;
	}

	/** 
	 * @from org.tmatesoft.svn.core.wc.SVNDiffClient
	 */
	public Object[] getLocationFromPathAndRevision(File path, SVNURL url,
			SVNRevision pegRevision) throws SVNException {
		SVNErrorMessage err = SVNErrorMessage
				.create(SVNErrorCode.VERSION_MISMATCH);
		SVNErrorManager.error(err, SVNLogType.CLIENT);
		return null;
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
		return null;
	}

	/** 
	 * @from org.tmatesoft.svn.core.wc.SVNDiffClient
	 */
	public void doDiffURLWC(File path1, SVNRevision revision1,
			SVNRevision pegRevision, File path2, SVNRevision revision2,
			boolean reverse, SVNDepth depth, boolean useAncestry,
			OutputStream result, Collection changeLists) throws SVNException {
		SVNErrorMessage err = SVNErrorMessage
				.create(SVNErrorCode.VERSION_MISMATCH);
		SVNErrorManager.error(err, SVNLogType.CLIENT);
	}
}
