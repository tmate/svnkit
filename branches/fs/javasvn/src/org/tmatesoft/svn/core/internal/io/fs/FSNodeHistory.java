/*
 * Created on 12.11.2005
 *
 * TODO To change the template for this generated file go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
package org.tmatesoft.svn.core.internal.io.fs;

import java.io.File;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.io.SVNLocationEntry;
import org.tmatesoft.svn.core.internal.util.*;
import org.tmatesoft.svn.core.internal.io.fs.FSRevisionNode;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;

/**
 * @author Tim
 *
 * TODO To change the template for this generated type comment go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
public class FSNodeHistory
{	
	//path and revision of historical location
	private SVNLocationEntry historyEntry;
	
	//internal-use hints about where to resume the history search
	private SVNLocationEntry searchResumeEntry;
	
	//FALSE until the first call to fsHistoryPrev()
	private boolean isInteresting;
	
	public FSNodeHistory(SVNLocationEntry newHistoryEntry, boolean interest, SVNLocationEntry newSearchResumeEntry){
		historyEntry = newHistoryEntry;
		searchResumeEntry = newSearchResumeEntry;		
		isInteresting = interest;
	}
	//methods-accessors
	public SVNLocationEntry getHistoryEntry(){
		return historyEntry;
	}
	public void setHistoryEntry(SVNLocationEntry newHistoryEntry){
		historyEntry = newHistoryEntry;		
	}
	public SVNLocationEntry getSearchResumeEntry(){
		return searchResumeEntry;
	}
	public void setHintsEntry(SVNLocationEntry newSearchResumeEntry){
		searchResumeEntry = newSearchResumeEntry;
	}
	public boolean getInterest(){
		return isInteresting;
	}
	public void setInterest(boolean someInterest){
		isInteresting = someInterest;
	}
	//methods connected to history entity
	
	//Find the youngest copyroot for path PARENT_PATH or its parents in
	//filesystem FS, and store the copyroot in *REV_P and *PATH_P.
	public static SVNLocationEntry findYoungestCopyroot(File reposRootDir, FSParentPath parPath)throws SVNException{		
		SVNLocationEntry parentEntry = null;
		SVNLocationEntry myEntry;		
		
		if(parPath.getParent() != null){
			parentEntry = FSNodeHistory.findYoungestCopyroot(reposRootDir, parPath.getParent());
		}
		myEntry = new SVNLocationEntry(parPath.getRevNode().getCopyFromRevision(), parPath.getRevNode().getCopyFromPath()); 
		if(myEntry == null){
			System.out.println("returning value (SVNLocationEntry myEntry) is null");
			//not good desision
			return null;
		}
		if(parentEntry != null){
			if(myEntry.getRevision() >= parentEntry.getRevision()){
				return myEntry;
			}
            return parentEntry;
		}
        return myEntry;
	}
	
    public static boolean checkAncestryOfPegPath(File reposRootDir, String fsPath, long pegRev, long futureRev, FSRevisionNodePool revNodesPool)throws SVNException{
   		String localFsPath = null;
   		if(fsPath == null){
   			SVNErrorManager.error("invalid path in repository");
   		}else{
   			localFsPath = new String(fsPath);
   		}
   		FSRevisionNode root = FSReader.getRootRevNode(reposRootDir, futureRev);
   		FSNodeHistory history = getNodeHistory(reposRootDir, root, localFsPath);    	
   		localFsPath = null;
   		SVNLocationEntry currentHistory = null;
   		while(true){  
   			history = history.fsHistoryPrev(reposRootDir, true, revNodesPool);    			
   			if(history == null){  
   				break;    			
   			}
   			currentHistory = new SVNLocationEntry(history.getHistoryEntry().getRevision(), history.getHistoryEntry().getPath()); 		
   			if(localFsPath == null){
   				localFsPath = new String(currentHistory.getPath());
   			}
   			if(currentHistory.getRevision() <= pegRev){
   				break;    				
   			}
   		}
   		return (history != null && (localFsPath.compareTo(currentHistory.getPath()) == 0));
    }
    
    //Retrun FSNodeHistory as an opaque node history object which represents
    //PATH under ROOT. ROOT must be a revision root    
    public static FSNodeHistory getNodeHistory(File reposRootDir, FSRevisionNode root, String path)throws SVNException{
    	if(root == null){
    		SVNErrorManager.error("invalid node root of repository");
    	}
        /*And we require that the path exist in the root*/
    	SVNNodeKind kind = FSRepository.checkPath(reposRootDir, root, path);;    	
    	if(kind == SVNNodeKind.NONE){
    		SVNErrorManager.error("File not found: revision " + root.getId().getRevision() + " path " + path);
    	}
    	
    	return new FSNodeHistory(new SVNLocationEntry(root.getId().getRevision(), path), 
    			false, new SVNLocationEntry(FSConstants.SVN_INVALID_REVNUM, null));
    }
    	
    private FSNodeHistory historyPrev(File reposRootDir, /*FSNodeHistory hist,*/ boolean crossCopies, FSRevisionNodePool revNodesPool)throws SVNException{
    	if(revNodesPool == null){
    		SVNErrorManager.error("invalid revision node pool");
    	}
    	if(reposRootDir == null){
    		SVNErrorManager.error("invalid root directory of repository");
    	}
    	String path = historyEntry.getPath();//String path = hist.getHistoryEntry().getPath();
    	SVNLocationEntry commitEntry;
    	long revision = historyEntry.getRevision();//long revision = hist.getHistoryEntry().getRevision();
    	boolean reported = isInteresting;//boolean reported = hist.getInterest();
    	
    	//If our last history report left us hints about where to pickup
        //the chase, then our last report was on the destination of a
        //copy.  If we are crossing copies, start from those locations,
        //otherwise, we're all done here
//    	if((hist.getSearchResumeEntry() != null) && (hist.getSearchResumeEntry().getPath() != null) && FSRepository.isValidRevision(hist.getSearchResumeEntry().getRevision()) ){
    	if(searchResumeEntry != null && FSRepository.isValidRevision(searchResumeEntry.getRevision())){
    		reported = false;
    		if(crossCopies == false){
    			return null;
    		}
    		path = searchResumeEntry.getPath();//path = hist.getSearchResumeEntry().getPath();
    		revision = searchResumeEntry.getRevision();//revision = hist.getSearchResumeEntry().getRevision();
    	}
    	//Construct a ROOT for the current revision
    	FSRevisionNode root = FSReader.getRootRevNode(reposRootDir, revision);
    	//Open path/revision and get all necessary info: node-id, ...
    	FSParentPath parentPath = revNodesPool.getParentPath(new FSRoot(root.getId().getRevision(), root), path, true, reposRootDir);
    	FSRevisionNode revNode = parentPath.getRevNode();
    	commitEntry = new SVNLocationEntry(revNode.getId().getRevision(), revNode.getCreatedPath()); 
    	//The Subversion filesystem is written in such a way that a given
        //line of history may have at most one interesting history point
        //per filesystem revision.  Either that node was edited (and
        //possibly copied), or it was copied but not edited.  And a copy
        //source cannot be from the same revision as its destination.  So,
        //if our history revision matches its node's commit revision, we
        //know that ...
    	FSNodeHistory prevHist = null;
    	if(revision == commitEntry.getRevision()){
    		if(reported == false){
    			prevHist = new FSNodeHistory(new SVNLocationEntry(commitEntry.getRevision(), commitEntry.getPath()),
    					true, new SVNLocationEntry(FSConstants.SVN_INVALID_REVNUM, null));
    			return prevHist;
    		}
    	    //... or we *have* reported on this revision, and must now
            //progress toward this node's predecessor (unless there is
            //no predecessor, in which case we're all done!)
            FSID predId = revNode.getPredecessorId();
            if(predId == null || predId.getRevision() < 0 ){
                return prevHist;
            }
            //Replace NODE and friends with the information from its predecessor
            revNode = FSReader.getRevNodeFromID(reposRootDir, predId);
            commitEntry = new SVNLocationEntry(revNode.getId().getRevision(), revNode.getCreatedPath());
    	}
		//Find the youngest copyroot in the path of this node, including itself
    	SVNLocationEntry copyrootEntry = FSNodeHistory.findYoungestCopyroot(reposRootDir, parentPath);
		if(copyrootEntry == null){
			//just for error diagnosting
			SVNErrorManager.error("No youngest copyroot");
		}
		SVNLocationEntry srcEntry = new SVNLocationEntry(FSConstants.SVN_INVALID_REVNUM, null);
		long dstRev = FSConstants.SVN_INVALID_REVNUM;
		if(copyrootEntry.getRevision() > commitEntry.getRevision()){
			FSRevisionNode copyrootRoot = FSReader.getRootRevNode(reposRootDir, copyrootEntry.getRevision());
			revNode = FSReader.getRevisionNode(reposRootDir, copyrootEntry.getPath(), copyrootRoot, 0);
		  /* If our current path was the very destination of the copy,
	         then our new current path will be the copy source.  If our
	         current path was instead the *child* of the destination of
	         the copy, then figure out its previous location by taking its
	         path relative to the copy destination and appending that to
	         the copy source.  Finally, if our current path doesn't meet
	         one of these other criteria ... ### for now just fallback to
	         the old copy hunt algorithm. 
	       */
			String copyDst = revNode.getCreatedPath();
			String reminder = new String();
			if(path.compareTo(copyDst) == 0){
				reminder = "/";
			}else{
				reminder = SVNPathUtil.pathIsChild(copyDst, path);
			}
			if(reminder != null){
	          /* If we get here, then our current path is the destination 
	             of, or the child of the destination of, a copy.  Fill
	             in the return values and get outta here.  
	          */
				String copySrc = revNode.getCopyFromPath();
				srcEntry = new SVNLocationEntry(revNode.getCopyFromRevision(), SVNPathUtil.concatToAbs(copySrc, reminder));
				dstRev = copyrootEntry.getRevision();
			}
		}
		//If we calculated a copy source path and revision, we'll make a
	    //'copy-style' history object.
		if(srcEntry.getPath() != null && FSRepository.isValidRevision(srcEntry.getRevision())){
		  /* It's possible for us to find a copy location that is the same
	         as the history point we've just reported.  If that happens,
	         we simply need to take another trip through this history
	         search 
	      */
			boolean retry = false;
			if(dstRev == revision && reported){
				retry = true;
			}
			return new FSNodeHistory(new SVNLocationEntry(dstRev, path), retry ? false : true, new SVNLocationEntry(srcEntry.getRevision(), srcEntry.getPath()));
		}
        return new FSNodeHistory(commitEntry, true, new SVNLocationEntry(FSConstants.SVN_INVALID_REVNUM, null));
	}
    
    public FSNodeHistory fsHistoryPrev(File reposRootDir, boolean crossCopies, FSRevisionNodePool revNodesPool)throws SVNException{    
    	//if("/".compareTo(hist.getHistoryEntry().getPath()) == 0){
    	if("/".compareTo(historyEntry.getPath()) == 0){
    		//if(hist.getInterest() == false){
    		if(isInteresting == false){
    			return new FSNodeHistory(new SVNLocationEntry(/*hist.getHistoryEntry().getRevision()*/historyEntry.getRevision(), "/"), true,
    					new SVNLocationEntry(FSConstants.SVN_INVALID_REVNUM, null));
    		}else if(/*hist.getHistoryEntry().getRevision()*/historyEntry.getRevision() > 0){
    			return new FSNodeHistory(new SVNLocationEntry(/*hist.getHistoryEntry().getRevision()*/historyEntry.getRevision() - 1, "/"), true,
    					new SVNLocationEntry(FSConstants.SVN_INVALID_REVNUM, null));    			
    		}
    	}else{
    		FSNodeHistory prevHist = this;
    		while(true){
    			try{
    				prevHist = prevHist.historyPrev(reposRootDir, /*prevHist,*/ crossCopies, revNodesPool);
    			}catch(SVNException ex){
    				SVNErrorManager.error("can't get predecessor of history object");
    			}
    			if(prevHist == null){
    				return null;
    			}
    			if(prevHist.getInterest() == true){
    				return prevHist;
    			}
    		}
    	}
    	return null;
    }
}

