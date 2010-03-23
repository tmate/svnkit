package org.tmatesoft.svn.core.internal.wc.v17;

import java.io.File;
import java.util.Iterator;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.internal.util.SVNEncodingUtil;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNEventFactory;
import org.tmatesoft.svn.core.internal.wc.SVNFileType;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc.admin.SVNAdminArea;
import org.tmatesoft.svn.core.internal.wc.admin.SVNEntry;
import org.tmatesoft.svn.core.internal.wc.admin.SVNLog;
import org.tmatesoft.svn.core.internal.wc.admin.SVNVersionedProperties;
import org.tmatesoft.svn.core.internal.wc.admin.SVNWCAccess;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.wc.ISVNOptions;
import org.tmatesoft.svn.core.wc.ISVNRepositoryPool;
import org.tmatesoft.svn.core.wc.SVNCopyClient;
import org.tmatesoft.svn.core.wc.SVNCopySource;
import org.tmatesoft.svn.core.wc.SVNEvent;
import org.tmatesoft.svn.core.wc.SVNEventAction;
import org.tmatesoft.svn.core.wc.SVNMoveClient;
import org.tmatesoft.svn.core.wc.SVNMoveClient;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.util.SVNLogType;

public class SVNMoveClient17 extends SVNMoveClient {
	protected SVNMoveClient dispatcher;

	protected SVNMoveClient17(SVNMoveClient from) {
		super(from);
		this.dispatcher = dispatcher;
	}

	public static SVNMoveClient17 delegate(SVNMoveClient dispatcher) {
		SVNMoveClient17 delegate = new SVNMoveClient17(dispatcher);
		return delegate;
	}

	/** 
	 * Copies/moves administrative version control information of a source file 
	 * to administrative information of a destination file.
	 * For example, if you have manually copied/moved a source file to a target one 
	 * (manually means just in the filesystem, not using version control operations) and then
	 * would like to turn this copying/moving into a complete version control copy
	 * or move operation, use this method that will finish all the work for you - it
	 * will copy/move all the necessary administrative information (kept in the source
	 * <i>.svn</i> directory) to the target <i>.svn</i> directory. 
	 * <p>
	 * In that case when you have your files copied/moved in the filesystem, you
	 * can not perform standard (version control) copying/moving - since the target already exists and
	 * the source may be already deleted. Use this method to overcome that restriction.  
	 * @param src           a source file path (was copied/moved to <code>dst</code>)
	 * @param dst           a destination file path
	 * @param move          if <span class="javakeyword">true</span> then
	 * completes moving <code>src</code> to <code>dst</code>,
	 * otherwise completes copying <code>src</code> to <code>dst</code>
	 * @throws SVNException  if one of the following is true:
	 * <ul>
	 * <li><code>move = </code><span class="javakeyword">true</span> and <code>src</code>
	 * still exists
	 * <li><code>dst</code> does not exist
	 * <li><code>dst</code> is a directory 
	 * <li><code>src</code> is a directory
	 * <li><code>src</code> is not under version control
	 * <li><code>dst</code> is already under version control
	 * <li>if <code>src</code> is copied but not scheduled for
	 * addition, and SVNKit is not able to locate the copied
	 * directory root for <code>src</code>
	 * </ul>
	 * @from org.tmatesoft.svn.core.wc.SVNMoveClient
	 */
	public void doVirtualCopy(File src, File dst, boolean move)
			throws SVNException {
		SVNErrorMessage err = SVNErrorMessage
				.create(SVNErrorCode.VERSION_MISMATCH);
		SVNErrorManager.error(err, SVNLogType.CLIENT);
	}

	/** 
	 * @from org.tmatesoft.svn.core.wc.SVNMoveClient
	 */
	public String getCopyFromURL(File path, String urlTail) throws SVNException {
		SVNErrorMessage err = SVNErrorMessage
				.create(SVNErrorCode.VERSION_MISMATCH);
		SVNErrorManager.error(err, SVNLogType.CLIENT);
		return null;
	}

	/** 
	 * Moves a source item to a destination one. 
	 * <p>
	 * <code>dst</code> should not exist. Furher it's considered to be versioned if
	 * its parent directory is under version control, otherwise <code>dst</code>
	 * is considered to be unversioned.
	 * <p>
	 * If both <code>src</code> and <code>dst</code> are unversioned, then simply 
	 * moves <code>src</code> to <code>dst</code> in the filesystem.
	 * <p>
	 * If <code>src</code> is versioned but <code>dst</code> is not, then 
	 * exports <code>src</code> to <code>dst</code> in the filesystem and
	 * removes <code>src</code> from version control.
	 * <p>
	 * If <code>dst</code> is versioned but <code>src</code> is not, then 
	 * moves <code>src</code> to <code>dst</code> (even if <code>dst</code>
	 * is scheduled for deletion).
	 * <p>
	 * If both <code>src</code> and <code>dst</code> are versioned and located
	 * within the same Working Copy, then moves <code>src</code> to 
	 * <code>dst</code> (even if <code>dst</code> is scheduled for deletion),
	 * or tries to replace <code>dst</code> with <code>src</code> if the former
	 * is missing and has a node kind different from the node kind of the source.
	 * If <code>src</code> is scheduled for addition with history, 
	 * <code>dst</code> will be set the same ancestor URL and revision from which
	 * the source was copied. If <code>src</code> and <code>dst</code> are located in 
	 * different Working Copies, then this method copies <code>src</code> to 
	 * <code>dst</code>, tries to put the latter under version control and 
	 * finally removes <code>src</code>.
	 * @param src            a source path
	 * @param dst            a destination path
	 * @throws SVNException   if one of the following is true:
	 * <ul>
	 * <li><code>dst</code> already exists
	 * <li><code>src</code> does not exist
	 * </ul>
	 * @from org.tmatesoft.svn.core.wc.SVNMoveClient
	 */
	public void doMove(File src, File dst) throws SVNException {
		SVNErrorMessage err = SVNErrorMessage
				.create(SVNErrorCode.VERSION_MISMATCH);
		SVNErrorManager.error(err, SVNLogType.CLIENT);
	}

	/** 
	 * Reverts a previous move operation back. Provided in pair with {@link #doMove(File,File) doMove()} 
	 * and used to roll back move operations. In this case <code>src</code> is
	 * considered to be the target of the previsous move operation, and <code>dst</code>
	 * is regarded to be the source of that same operation which have been moved
	 * to <code>src</code> and now is to be restored. 
	 * <p>
	 * <code>dst</code> could exist in that case if it has been a WC directory
	 * that was scheduled for deletion during the previous move operation. Furher 
	 * <code>dst</code> is considered to be versioned if its parent directory is 
	 * under version control, otherwise <code>dst</code> is considered to be unversioned.
	 * <p>
	 * If both <code>src</code> and <code>dst</code> are unversioned, then simply 
	 * moves <code>src</code> back to <code>dst</code> in the filesystem.
	 * <p>
	 * If <code>src</code> is versioned but <code>dst</code> is not, then 
	 * unmoves <code>src</code> to <code>dst</code> in the filesystem and
	 * removes <code>src</code> from version control.
	 * <p>
	 * If <code>dst</code> is versioned but <code>src</code> is not, then 
	 * first tries to make a revert on <code>dst</code> - if it has not been committed
	 * yet, it will be simply reverted. However in the case <code>dst</code> has been already removed 
	 * from the repository, <code>src</code> will be copied back to <code>dst</code>
	 * and scheduled for addition. Then <code>src</code> is removed from the filesystem.
	 * <p>
	 * If both <code>src</code> and <code>dst</code> are versioned then the 
	 * following situations are possible:
	 * <ul>
	 * <li>If <code>dst</code> is still scheduled for deletion, then it is
	 * reverted back and <code>src</code> is scheduled for deletion.
	 * <li>in the case if <code>dst</code> exists but is not scheduled for 
	 * deletion, <code>src</code> is cleanly exported to <code>dst</code> and
	 * removed from version control.
	 * <li>if <code>dst</code> and <code>src</code> are from different repositories
	 * (appear to be in different Working Copies), then <code>src</code> is copied
	 * to <code>dst</code> (with scheduling <code>dst</code> for addition, but not
	 * with history since copying is made in the filesystem only) and removed from
	 * version control.
	 * <li>if both <code>dst</code> and <code>src</code> are in the same 
	 * repository (appear to be located in the same Working Copy) and: 
	 * <ul style="list-style-type: lower-alpha">
	 * <li>if <code>src</code> is scheduled for addition with history, then
	 * copies <code>src</code> to <code>dst</code> specifying the source
	 * ancestor's URL and revision (i.e. the ancestor of the source is the
	 * ancestor of the destination);
	 * <li>if <code>src</code> is already under version control, then
	 * copies <code>src</code> to <code>dst</code> specifying the source
	 * URL and revision as the ancestor (i.e. <code>src</code> itself is the
	 * ancestor of <code>dst</code>);
	 * <li>if <code>src</code> is just scheduled for addition (without history),
	 * then simply copies <code>src</code> to <code>dst</code> (only in the filesystem,
	 * without history) and schedules <code>dst</code> for addition;  
	 * </ul>
	 * then <code>src</code> is removed from version control.
	 * </ul>
	 * @param src            a source path
	 * @param dst            a destination path
	 * @throws SVNException   if <code>src</code> does not exist
	 * @from org.tmatesoft.svn.core.wc.SVNMoveClient
	 */
	public void undoMove(File src, File dst) throws SVNException {
		SVNErrorMessage err = SVNErrorMessage
				.create(SVNErrorCode.VERSION_MISMATCH);
		SVNErrorManager.error(err, SVNLogType.CLIENT);
	}

	/** 
	 * @from org.tmatesoft.svn.core.wc.SVNMoveClient
	 */
	static public boolean isVersionedFile(File file) {
		return false;
	}

	/** 
	 * @from org.tmatesoft.svn.core.wc.SVNMoveClient
	 */
	public long getCopyFromRevision(File path) throws SVNException {
		SVNErrorMessage err = SVNErrorMessage
				.create(SVNErrorCode.VERSION_MISMATCH);
		SVNErrorManager.error(err, SVNLogType.CLIENT);
		return 0;
	}

	/** 
	 * @from org.tmatesoft.svn.core.wc.SVNMoveClient
	 */
	public void updateCopiedDirectory(SVNAdminArea dir, String name,
			String newURL, String reposRootURL, String copyFromURL,
			long copyFromRevision) throws SVNException {
		SVNErrorMessage err = SVNErrorMessage
				.create(SVNErrorCode.VERSION_MISMATCH);
		SVNErrorManager.error(err, SVNLogType.CLIENT);
	}
}
