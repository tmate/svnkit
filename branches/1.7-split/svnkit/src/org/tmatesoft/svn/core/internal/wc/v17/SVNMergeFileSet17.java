package org.tmatesoft.svn.core.internal.wc.v17;

import java.io.File;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc.admin.SVNAdminArea;
import org.tmatesoft.svn.core.internal.wc.admin.SVNLog;
import org.tmatesoft.svn.core.wc.SVNMergeFileSet;
import org.tmatesoft.svn.core.wc.SVNMergeFileSet;
import org.tmatesoft.svn.util.SVNLogType;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;

public class SVNMergeFileSet17 extends SVNMergeFileSet {
	protected SVNMergeFileSet dispatcher;

	protected SVNMergeFileSet17(SVNMergeFileSet from) {
		super(from);
		this.dispatcher = dispatcher;
	}

	public static SVNMergeFileSet17 delegate(SVNMergeFileSet dispatcher) {
		SVNMergeFileSet17 delegate = new SVNMergeFileSet17(dispatcher);
		return delegate;
	}

	/** 
	 * Returns the admin area which controls the file.
	 * <p/>
	 * Note: this method is not intended for API users.
	 * @return admin area
	 * @from org.tmatesoft.svn.core.wc.SVNMergeFileSet
	 */
	public SVNAdminArea getAdminArea() {
		return null;
	}
}
