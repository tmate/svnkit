package org.tmatesoft.svn.core.internal.wc.v16;

import java.io.File;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc.admin.SVNAdminArea;
import org.tmatesoft.svn.core.internal.wc.admin.SVNLog;
import org.tmatesoft.svn.core.wc.SVNMergeFileSet;
import org.tmatesoft.svn.core.wc.SVNMergeFileSet;

public class SVNMergeFileSet16 extends SVNMergeFileSet {
	protected SVNMergeFileSet dispatcher;

	protected SVNMergeFileSet16(SVNMergeFileSet from) {
		super(from);
		this.dispatcher = dispatcher;
	}

	public static SVNMergeFileSet16 delegate(SVNMergeFileSet dispatcher) {
		SVNMergeFileSet16 delegate = new SVNMergeFileSet16(dispatcher);
		return delegate;
	}

	/** 
	 * Creates a new <code>SVNMergeFileSet</code> object given the data prepared for 
	 * merging a file.
	 * <p/>
	 * Note: This is intended for internal use only, not for API users.
	 * @param adminArea     admin area the file is controlled under 
	 * @param log           log object
	 * @param baseFile      file with pristine contents
	 * @param localFile     file with translated working contents
	 * @param wcPath        working copy path relative to the location of <code>adminArea</code>
	 * @param reposFile     file contents from the repository
	 * @param resultFile    file where the resultant merged contents will be written to  
	 * @param copyFromFile  contents of the copy source file (if any)  
	 * @param mimeType      file mime type       
	 * @from org.tmatesoft.svn.core.wc.SVNMergeFileSet
	 */
	public SVNMergeFileSet16(SVNAdminArea adminArea, SVNLog log, File baseFile,
			File localFile, String wcPath, File reposFile, File resultFile,
			File copyFromFile, String mimeType) {
		super(adminArea, log, baseFile, localFile, wcPath, reposFile,
				resultFile, copyFromFile, mimeType);
	}

	/** 
	 * Returns the admin area which controls the file.
	 * <p/>
	 * Note: this method is not intended for API users.
	 * @return admin area
	 * @from org.tmatesoft.svn.core.wc.SVNMergeFileSet
	 */
	public SVNAdminArea getAdminArea() {
		return myAdminArea;
	}
}
