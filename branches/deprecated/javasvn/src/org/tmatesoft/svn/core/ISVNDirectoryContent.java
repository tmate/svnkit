package org.tmatesoft.svn.core;

import java.util.List;


/**
 * @author Marc Strapetz
 */
public interface ISVNDirectoryContent extends ISVNEntryContent {
    public List getChildContents() throws SVNException;
}