package org.tmatesoft.svn.core;


/**
 * @author Marc Strapetz
 * @deprecated
 */
public interface ISVNEntryContent {
	public String getPath();

	public String getName();

	public ISVNFileContent asFile();

	public ISVNDirectoryContent asDirectory();

	public boolean isDirectory();

	public void deleteWorkingCopyContent() throws SVNException;
}