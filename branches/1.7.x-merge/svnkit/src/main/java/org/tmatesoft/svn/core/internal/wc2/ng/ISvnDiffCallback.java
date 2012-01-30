package org.tmatesoft.svn.core.internal.wc2.ng;

import java.io.File;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNProperties;

interface ISvnDiffCallback {
    
   
    
    public void fileOpened(SvnDiffCallbackResult result, File path, long revision) throws SVNException;
    
    public void fileChanged(SvnDiffCallbackResult result, 
            String path, File tmpFile1, File tmpFile2, long rev1, long rev2, String mimetype1, String mimeType2,
            SVNProperties propChanges, SVNProperties originalProperties) throws SVNException;
    
    public void fileAdded(SvnDiffCallbackResult result,  
            String path, File tmpFile1, File tmpFile2, long rev1, long rev2, String mimetype1, String mimeType2,
            File copyFromPath, long copyFromRevision, SVNProperties propChanges, SVNProperties originalProperties) throws SVNException;
    
    public void fileDeleted(SvnDiffCallbackResult result,  
            String path, File tmpFile1, File tmpFile2, String mimetype1, String mimeType2,
            SVNProperties originalProperties) throws SVNException;


    public void dirDeleted(SvnDiffCallbackResult result, String path) throws SVNException;
    
    public void dirOpened(SvnDiffCallbackResult result, String path, long revision) throws SVNException;
    
    public void dirAdded(SvnDiffCallbackResult result, String path, long revision, String copyFromPath, long copyFromRevision) throws SVNException;
    
    public void dirPropsChanged(SvnDiffCallbackResult result, String path, boolean isAdded, SVNProperties propChanges, SVNProperties originalProperties) throws SVNException;
    
    public void dirClosed(SvnDiffCallbackResult result, String path, boolean isAdded) throws SVNException;
}
