package org.tmatesoft.svn.core.internal.wc.v17;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.internal.wc.admin.SVNAdminArea;
import org.tmatesoft.svn.core.internal.wc.admin.SVNLog;
import org.tmatesoft.svn.core.wc.ISVNMerger;
import org.tmatesoft.svn.core.wc.ISVNMerger;
import org.tmatesoft.svn.core.wc.SVNMergeResult;
import org.tmatesoft.svn.util.SVNLogType;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;

public interface ISVNMerger17 extends ISVNMerger {
	/** 
	 * Given <code>adminArea</code>/<code>localPath</code> and property changes (<code>propDiff</code>) based 
	 * on <code>serverBaseProps</code>, merges the changes into the working copy.
	 * @param localPath           working copy path base name
	 * @param workingProperties   working properties
	 * @param baseProperties      pristine properties
	 * @param serverBaseProps     properties that come from the server
	 * @param propDiff            property changes that come from the repository
	 * @param adminArea           admin area object representing the <code>.svn<./code> admin area of 
	 * the target which properties are merged
	 * @param log                 logger
	 * @param baseMerge           if <span class="javakeyword">false</span>, then changes only working properties;
	 * otherwise, changes both the base and working properties
	 * @param dryRun              if <span class="javakeyword">true</span>, merge is simulated only, no real
	 * changes are done
	 * @return                     result of merging 
	 * @throws SVNException 
	 * @from org.tmatesoft.svn.core.wc.ISVNMerger
	 */
	public SVNMergeResult mergeProperties(String localPath,
			SVNProperties workingProperties, SVNProperties baseProperties,
			SVNProperties serverBaseProps, SVNProperties propDiff,
			SVNAdminArea adminArea, SVNLog log, boolean baseMerge,
			boolean dryRun) throws SVNException;
}
