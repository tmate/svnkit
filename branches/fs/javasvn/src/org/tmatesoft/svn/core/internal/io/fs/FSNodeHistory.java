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
		
		if(parPath.getParentPath() != null){
			parentEntry = FSNodeHistory.findYoungestCopyroot(reposRootDir, parPath.getParentPath());
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
			}else{
				return parentEntry;
			}
		}else{
			return myEntry;
		}	
	}
	
    public static boolean checkAncestryOfPegPath(File reposRootDir, String fsPath, long pegRev, long futureRev, FSRevisionNodePool revNodesPool)throws SVNException{
    	try{    
    		String localFsAPath = null;
    		if(fsPath == null){
    			SVNErrorManager.error("invalid path in repository");
    		}else{
    			localFsAPath = new String(fsPath);
    		}
    		FSRevisionNode root = FSReader.getRootRevNode(reposRootDir, futureRev);
    		FSNodeHistory history = getNodeHistory(reposRootDir, root, fsPath);    	
    		fsPath = null;
    		
    		while(true){  
    			history = fsHistoryPrev(reposRootDir, history, true, revNodesPool);
    			
    			if(history == null){  
    			//!!!possible fsPath == null or path == null
    			//previous variant, I suppose it not to be right, look at source code!!!
    			//return (fsPath.compareTo(path) == 0);
    				return false;    			
    			}
    			SVNLocationEntry currentHistory = new SVNLocationEntry(history.getHistoryEntry().getRevision(), history.getHistoryEntry().getPath()); 
 		
    			if(fsPath == null){
    				//!!!possible fsPath == null or currentHistory.getPath() == null
    				fsPath = new String(currentHistory.getPath());
    			}
    			if(currentHistory.getRevision() <= pegRev){
    				return (history != null && (fsPath.compareTo(currentHistory.getPath()) == 0));
    			}
    		}   
    		}catch(SVNException ex){
    			SVNErrorManager.error("");
    		}
    	return false;
    }
    
    //Retrun FSNodeHistory as an opaque node history object which represents
    //PATH under ROOT. ROOT must be a revision root    
    public static FSNodeHistory getNodeHistory(File reposRootDir, FSRevisionNode root, String path)throws SVNException{
    	if(root == null){
    		SVNErrorManager.error("invalid node root of repository");
    	}
    	SVNNodeKind kind = FSRepository.checkPath(reposRootDir, root, path);;    	
    	if(kind == SVNNodeKind.NONE){
    		SVNErrorManager.error("File not found: revision " + root.getId().getRevision() + " path " + path);
    	}
    	
    	return new FSNodeHistory(new SVNLocationEntry(root.getId().getRevision(), path), 
    			false, new SVNLocationEntry(FSConstants.SVN_INVALID_REVNUM, null));
    }
    	
    private static FSNodeHistory historyPrev(File reposRootDir, FSNodeHistory hist, boolean crossCopies, FSRevisionNodePool revNodesPool)throws SVNException{
    	String srcPath = null;
    	String path = hist.getHistoryEntry().getPath();
    	SVNLocationEntry commitEntry;
    	long srcRev = FSConstants.SVN_INVALID_REVNUM;
    	long dstRev = FSConstants.SVN_INVALID_REVNUM;
    	long revision = hist.getHistoryEntry().getRevision();
    	FSParentPath parentPath = null; 
    	FSRevisionNode revNode;
    	FSRevisionNode root = null;    	
    	boolean reported = hist.getInterest();
    	boolean retry = false;
    	SVNLocationEntry copyrootEntry = null;
    	FSNodeHistory prevHist = null; //This is return value 
    	
    	//If our last history report left us hints about where to pickup
        //the chase, then our last report was on the destination of a
        //copy.  If we are crossing copies, start from those locations,
        //otherwise, we're all done here
    	if((hist.getSearchResumeEntry() != null) && (hist.getSearchResumeEntry().getPath() != null) && FSRepository.isValidRevision(hist.getSearchResumeEntry().getRevision()) ){
    		reported = false;
    		if(crossCopies == false){
    			return null;
    		}
    		path = hist.getSearchResumeEntry().getPath();
    		revision = hist.getSearchResumeEntry().getRevision();
    	}
    	//Construct a ROOT for the current revision
    	try{
    		root = FSReader.getRootRevNode(reposRootDir, revision);
    	}catch(SVNException ex){
    		System.out.println(ex.getMessage());
    	}
    	try{
    	//parentPath = FSParentPath.openParentPath(reposRootDir, root, path, 0, null);    	
    	parentPath = revNodesPool.getParentPath(new FSRoot(root.getId().getRevision(), root), path, true, reposRootDir);	
    	}catch(SVNException ex){
    		SVNErrorManager.error("");
    	}
    	revNode = parentPath.getRevNode();
    	//nodeId = revNode.getRevNodeID();
    	commitEntry = new SVNLocationEntry(revNode.getId().getRevision(), revNode.getCreatedPath()); 
    	//The Subversion filesystem is written in such a way that a given
        //line of history may have at most one interesting history point
        //per filesystem revision.  Either that node was edited (and
        //possibly copied), or it was copied but not edited.  And a copy
        //source cannot be from the same revision as its destination.  So,
        //if our history revision matches its node's commit revision, we
        //know that ...
    	if(revision == commitEntry.getRevision()){
    		if(reported == false){
    			prevHist = new FSNodeHistory(new SVNLocationEntry(commitEntry.getRevision(), commitEntry.getPath()),
    					true, new SVNLocationEntry(FSConstants.SVN_INVALID_REVNUM, null));
    		}else{
    	        //... or we *have* reported on this revision, and must now
                //progress toward this node's predecessor (unless there is
                //no predecessor, in which case we're all done!)
    			FSID predId = revNode.getPredecessorId();

    			if(predId == null || predId.getRevision() < 0 ){
    				return prevHist;
    			}
    	        //Replace NODE and friends with the information from its
                //predecessor
    			try{
    				//get FSRevisionNode from ID
    				revNode = FSReader.getRevNodeFromID(reposRootDir, predId);
    				//nodeId = revNode.getRevNodeID();
    				commitEntry = new SVNLocationEntry(revNode.getId().getRevision(), revNode.getCreatedPath());
    			}catch(SVNException ex){
    				System.out.println("bad execution of FSDAGNode.getFSDAGNodeFromId");
    				ex.printStackTrace();//just for understanding errors during runtime
    			}
    		}
    	}
		//Find the youngest copyroot in the path of this node, including itself
		try{
			copyrootEntry = FSNodeHistory.findYoungestCopyroot(reposRootDir, parentPath);
		}catch(SVNException ex){
			System.out.println("bad excecution of FSNodeHistory.findYoungestCopyroot");    				
			System.out.println("SNVLocationEntry copyrootEntry == null "+(copyrootEntry==null));
		}
		if(copyrootEntry == null){
			//just for error diagnosting
			System.out.println("returned argument of FSNodeHistory.findYoungestCopyroot() is null");
		}
		if(copyrootEntry.getRevision() > commitEntry.getRevision() ){
			String reminder;
			String copySrc;
			String copyDst;
			FSRevisionNode copyrootRoot;
			
			try{
				copyrootRoot = FSReader.getRootRevNode(reposRootDir, copyrootEntry.getRevision());
				revNode = FSReader.getRevisionNode(reposRootDir, copyrootEntry.getPath(), copyrootRoot, 0);
			}catch(SVNException ex){
				System.out.println(ex.getMessage());
				ex.printStackTrace();//just for testing errors during runtime
			}
			copyDst = revNode.getCreatedPath();
	      /* If our current path was the very destination of the copy,
	         then our new current path will be the copy source.  If our
	         current path was instead the *child* of the destination of
	         the copy, then figure out its previous location by taking its
	         path relative to the copy destination and appending that to
	         the copy source.  Finally, if our current path doesn't meet
	         one of these other criteria ... ### for now just fallback to
	         the old copy hunt algorithm. 
	       */
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
				//srcRev = dagNode.getRevNode().getCopyFromRevision();
				srcRev = revNode.getCopyFromRevision();
				//copySrc = dagNode.getRevNode().getCopyFromPath();
				copySrc = revNode.getCopyFromPath();
				
				dstRev = copyrootEntry.getRevision();
				srcPath = SVNPathUtil.append(copySrc, reminder);
			}
		}
		//If we calculated a copy source path and revision, we'll make a
	    //'copy-style' history object.
		if(srcPath != null && FSRepository.isValidRevision(srcRev)){
		  /* It's possible for us to find a copy location that is the same
	         as the history point we've just reported.  If that happens,
	         we simply need to take another trip through this history
	         search 
	      */
			if(dstRev == revision && reported){
				retry = true;
			}
			return new FSNodeHistory(new SVNLocationEntry(dstRev, path), retry ? false : true, new SVNLocationEntry(srcRev, srcPath));
		}else{
			return new FSNodeHistory(commitEntry, true, new SVNLocationEntry(FSConstants.SVN_INVALID_REVNUM, null));
		}
	}
    
    public static FSNodeHistory fsHistoryPrev(File reposRootDir, FSNodeHistory hist,  boolean crossCopies, FSRevisionNodePool revNodesPool)throws SVNException{    
    	FSNodeHistory prevHist = null;
    	if(hist == null){
    		SVNErrorManager.error("invalid history object");
    	}
    	if("/".compareTo(hist.getHistoryEntry().getPath()) == 0){
    		if(hist.getInterest() == false){
    			return new FSNodeHistory(new SVNLocationEntry(hist.getHistoryEntry().getRevision(), "/"), true,
    					new SVNLocationEntry(FSConstants.SVN_INVALID_REVNUM, null));
    		}else if(hist.getHistoryEntry().getRevision() > 0){
    			return new FSNodeHistory(new SVNLocationEntry(hist.getHistoryEntry().getRevision() - 1, "/"), true,
    					new SVNLocationEntry(FSConstants.SVN_INVALID_REVNUM, null));    			
    		}
    	}else{
    		prevHist = hist;
    		while(true){
    			try{
    				prevHist = historyPrev(reposRootDir, prevHist, crossCopies, revNodesPool);
    				throw new SVNException();
    			}catch(SVNException ex){
    				SVNErrorManager.error("bad execution of historyPrev() function");
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

