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
import org.tmatesoft.svn.core.io.SVNRepository;

/**
 * @author Tim
 *
 * TODO To change the template for this generated type comment go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
public class FSNodeHistory
{	
	//path and revision of historical location
	SVNLocationEntry histEntry;
	
	//internal-use hints about where to resume the history search
	SVNLocationEntry hintsEntry;
	
	//FALSE until the first call to svn_fs_history_prev()
	boolean isInteresting;
	
	//default constructor
	public FSNodeHistory(){		
	}
	public FSNodeHistory(SVNLocationEntry historyEntry, boolean interest, SVNLocationEntry hintsEntr){
		histEntry = historyEntry;
		hintsEntry = hintsEntr;
		isInteresting = interest;
	}
	//methods-accessors
	public SVNLocationEntry getHistoryEntry(){
		return histEntry;
	}
	public void setHistoryEntry(SVNLocationEntry historyEntry){
		histEntry = historyEntry;		
	}
	public SVNLocationEntry getHintsEntry(){
		return hintsEntry;
	}
	public void setHintsEntry(SVNLocationEntry hintsEntr){
		hintsEntry = hintsEntr;
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
		myEntry = FSDAGNode.getCopyrootFromFSDAGNode(reposRootDir, parPath.getDAGNode());
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
	
    public static boolean checkAncestryOfPegPath(File reposRootDir, String fsPath, long pegRev, long futureRev){
    	FSRevisionNode root;
    	FSNodeHistory history=null;
    	SVNLocationEntry currentHistory;
    	
    	try{
    		root = FSReader.getRootRevNode(reposRootDir, futureRev);
    		history = getNodeHistory(root, fsPath);
    	}catch(SVNException ex){
    		System.out.println(ex.getMessage());
    	}
    	
    	fsPath = null;
    	
    	while(true){  
    		history = fsHistoryPrev(reposRootDir, history, true);
    		if(history == null){  
    			//!!!possible fsPath == null or path == null
    			//previous variant, i suppose it not to be right, look at source code!!!
    			//return (fsPath.compareTo(path) == 0);
    			return false;    			
    		}
    		currentHistory = new SVNLocationEntry(history.getHistoryEntry().getRevision(), history.getHistoryEntry().getPath()); 
 		
    		if(fsPath == null){
    			//!!!possible fsPath == null or currentHistory.getPath() == null
    			fsPath = new String(currentHistory.getPath());
    		}
    		if(currentHistory.getRevision() <= pegRev){
    			return (history != null && (fsPath.compareTo(currentHistory.getPath()) == 0));
    		}
    	}    	
    }
    
    /* Set *HISTORY_P to an opaque node history object which represents
    PATH under ROOT. ROOT must be a revision root.  Use POOL for all
    allocations. */
    public static FSNodeHistory getNodeHistory(FSRevisionNode root, String path)throws SVNException{
    	SVNNodeKind kind;
    	
    	//!!!!from SVN source
    	/* We require a revision root. */
    	//if (root->is_txn_root)
    	//	return svn_error_create (SVN_ERR_FS_NOT_REVISION_ROOT, NULL, NULL);
    	
    	//!!!Need to use FSRepository's checkPath() 
    	//kind = checkPath(root, path);
    	    	
    	return new FSNodeHistory(new SVNLocationEntry(root.getRevNodeID().getRevision(), path), 
    			false, new SVNLocationEntry(FSConstants.SVN_INVALID_REVNUM, null));
    }
    
	//!!!need to continue!!!
    public static FSNodeHistory historyPrev(File reposRootDir, FSNodeHistory hist, boolean crossCopies){
    	String commitPath;
    	String srcPath = null;
    	String path = hist.getHistoryEntry().getPath();
    	long commitRev;
    	long srcRev = FSConstants.SVN_INVALID_REVNUM;
    	long dstRev = FSConstants.SVN_INVALID_REVNUM;
    	long revision = hist.getHistoryEntry().getRevision();
    	FSParentPath parentPath;
    	FSDAGNode dagNode;
    	FSRevisionNode root;
    	FSID nodeId;
    	boolean reported = hist.getInterest();
    	boolean retry = false;
    	SVNLocationEntry copyrootEntry = null;
    	FSNodeHistory prevHist = null; //This is return value 
    	
    	//!!!!!Need to check for hist's valid revision using 

    	//If our last history report left us hints about where to pickup
        //the chase, then our last report was on the destination of a
        //copy.  If we are crossing copies, start from those locations,
        //otherwise, we're all done here
    	//!!!!!what will happen if hist.getHintsEntry() == null ??????? I suppose the programm will fail!!!!
    	if(hist.getHintsEntry() != null && hist.getHintsEntry().getPath() != null && FSRepository.isValidRevision(hist.getHintsEntry().getRevision()) ){
    		reported = false;
    		if(crossCopies == false){
    			return null;
    		}
    		path = hist.getHintsEntry().getPath();
    		revision = hist.getHintsEntry().getRevision();
    	}
    	//Construct a ROOT for the current revision
    	try{
    		root = FSReader.getRootRevNode(reposRootDir, revision);
    	}catch(SVNException ex){
    		System.out.println(ex.getMessage());
    	}
    	//Initialize parentPath
    	//!!!!!Here must be function open_path()
    	//AlexSin told me that he has function that make close functionality to that open_path()
    	parentPath = null;	//this is the simulation of initialization
    	dagNode = parentPath.getDAGNode();
    	nodeId = dagNode.getID();
    	commitPath = dagNode.getCreatedPath();
    	commitRev = dagNode.getRevNode().getRevNodeID().getRevision();
    	
    	//The Subversion filesystem is written in such a way that a given
        //line of history may have at most one interesting history point
        //per filesystem revision.  Either that node was edited (and
        //possibly copied), or it was copied but not edited.  And a copy
        //source cannot be from the same revision as its destination.  So,
        //if our history revision matches its node's commit revision, we
        //know that ...
    	if(revision == commitRev){
    		if(reported == false){
    			//Need to use SVN_INVALID_REVNUM unsted of -1 as a invalid revision number
    			prevHist = new FSNodeHistory(new SVNLocationEntry(commitRev, commitPath),
    					true, new SVNLocationEntry(FSConstants.SVN_INVALID_REVNUM, null));
    		}else{
    	        //... or we *have* reported on this revision, and must now
                //progress toward this node's predecessor (unless there is
                //no predecessor, in which case we're all done!)
    			FSID predId = dagNode.getRevNode().getPredecessorRevNodeId();
    			if(predId == null || predId.getRevision() < 0 ){
    				return prevHist;
    			}
    	        //Replace NODE and friends with the information from its
                //predecessor
    			try{
    				dagNode = FSDAGNode.getFSDAGNodeFromId(reposRootDir, predId);
    				nodeId = dagNode.getID();
    				commitPath = dagNode.getCreatedPath();
    				commitRev = dagNode.getID().getRevision();
    			}catch(SVNException ex){
    				System.out.println("svn: bad excecution of FSDAGNode.getFSDAGNodeFromId");
    				ex.printStackTrace();//just for understanding errors during runtime
    			}
    		}
    	}
		//Find the youngest copyroot in the path of this node, including itself
		try{
			copyrootEntry = FSNodeHistory.findYoungestCopyroot(reposRootDir, parentPath);
		}catch(SVNException ex){
			System.out.println("svn: bad excecution of FSNodeHistory.findYoungestCopyroot");    				
			System.out.println("SNVLocationEntry copyrootEntry == null "+(copyrootEntry==null));
		}
		if(copyrootEntry == null){
			//just for error diagnosting
			System.out.println("returned argument of FSNodeHistory.findYoungestCopyroot() is null");
		}
		if(copyrootEntry.getRevision() > commitRev ){
			String reminder;
			String copySrc;
			String copeDst;
			FSRevisionNode copyrootRoot;
			
			try{
				copyrootRoot = FSReader.getRootRevNode(reposRootDir, copyrootEntry.getRevision());
			}catch(SVNException ex){
				System.out.println(ex.getMessage());
				ex.printStackTrace();//just for testing errors during runtime
			}
			
		}


    	
    	return null;
    }
    
    public static FSNodeHistory fsHistoryPrev(File reposRootDir, FSNodeHistory hist, boolean crossCopies){
    	FSNodeHistory prevHist=null;
    	
    	if(hist.getHistoryEntry().getPath().compareTo("/") == 0){
    		if(hist.getInterest() == false){
    			return new FSNodeHistory(new SVNLocationEntry(hist.getHistoryEntry().getRevision(), "/"), true,
    					new SVNLocationEntry(FSConstants.SVN_INVALID_REVNUM, null));
    		}else if(hist.getHistoryEntry().getRevision() > 0){
    			return new FSNodeHistory(new SVNLocationEntry(hist.getHistoryEntry().getRevision() - 1, "/"), true,
    					new SVNLocationEntry(FSConstants.SVN_INVALID_REVNUM, null));    			
    		}
    	}else{
    		while(true){
    			prevHist = historyPrev(reposRootDir, hist, crossCopies);
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
