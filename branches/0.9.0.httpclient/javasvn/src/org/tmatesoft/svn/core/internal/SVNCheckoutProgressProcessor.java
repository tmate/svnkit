package org.tmatesoft.svn.core.internal;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.tmatesoft.svn.core.SVNDirEntry;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.progress.ISVNProgressViewer;
import org.tmatesoft.svn.core.progress.SVNProgressCancelledException;
import org.tmatesoft.svn.util.PathUtil;
import org.tmatesoft.svn.util.SVNAssert;

/**
 * @author Marc Strapetz
 */
class SVNCheckoutProgressProcessor {

	private static final int MAX_PATHS = 20;

	private final ISVNProgressViewer myProgressViewer;
	private final Set myKnownPaths;
	private final int myKnownPathCount;

	public SVNCheckoutProgressProcessor(ISVNProgressViewer progressViewer, SVNRepository repository, long revision) throws SVNException {
		SVNAssert.assertNotNull(progressViewer);

		this.myProgressViewer = progressViewer;
		this.myKnownPaths = listRepository(repository, revision);;
		this.myKnownPathCount = myKnownPaths.size();
	}

	public void entryProcessed(String path) throws SVNProgressCancelledException {
		myKnownPaths.remove(path);
		myProgressViewer.setProgress((double)(myKnownPathCount - myKnownPaths.size()) / (double)myKnownPathCount);
		myProgressViewer.checkCancelled();
	}

	private HashSet listRepository(SVNRepository repository, long revision) throws SVNException {
		final HashSet paths = new HashSet();
		final List pathsToProcess = new ArrayList();

		paths.add("");
		pathsToProcess.add("");

		while (paths.size() < MAX_PATHS && !pathsToProcess.isEmpty()) {
			final String path = (String)pathsToProcess.remove(0);
			final List entries = (List)repository.getDir(path, revision, null, new ArrayList());
			for (Iterator it = entries.iterator(); it.hasNext();) {
				final SVNDirEntry entry = (SVNDirEntry)it.next();
				if (entry.getKind() == SVNNodeKind.DIR) {
					final String childPath = PathUtil.removeLeadingSlash(PathUtil.append(path, entry.getName()));
					paths.add(childPath);
					paths.remove(path);
					pathsToProcess.add(childPath);
				}
			}
		}

		return paths;
	}
}