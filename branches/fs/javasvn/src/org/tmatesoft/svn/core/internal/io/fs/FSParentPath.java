/*
 * Created on 13.11.2005
 *
 * TODO To change the template for this generated file go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
package org.tmatesoft.svn.core.internal.io.fs;

import java.io.File;

import org.tmatesoft.svn.core.internal.wc.*;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.io.*;

/**
 * @author Tim
 *
 * TODO To change the template for this generated type comment go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
public class FSParentPath
{	
	//A node along the path.  This could be the final node, one of its
    //parents, or the root.  Every parent path ends with an element for
    //the root directory
	FSRevisionNode revNode;
	
	//The name NODE has in its parent directory.  This is zero for the
    //root directory, which (obviously) has no name in its parent
	String nameEntry;
	
	//The parent of NODE, or zero if NODE is the root directory
	FSParentPath parent;
	
	//The copy ID inheritence style
	int copyStyle;
	
	//If copy ID inheritence style is copy_id_inherit_new, this is the
    //path which should be implicitly copied; otherwise, this is NULL
	String copySrcPath;

	//constructors
	public FSParentPath(){		
	}
	
    public FSParentPath(FSRevisionNode newRevNode, String newNameEntry, FSParentPath newParent, int newCopyStyle, String newCopySrcPath){
		revNode = newRevNode;
		nameEntry = newNameEntry;
		parent = newParent;
		copyStyle = newCopyStyle;
		copySrcPath = newCopySrcPath;
	}
	public FSParentPath(FSParentPath newParentPath){
		revNode = newParentPath.getRevNode();
		nameEntry = newParentPath.getNameEntry();
		parent = newParentPath.getParent();
		copyStyle = newParentPath.getCopyStyle();
		copySrcPath = newParentPath.getCopySrcPath();		
	}
	public FSParentPath(FSRevisionNode newRevNode, String newEntry, FSParentPath newParentPath){
		revNode = newRevNode;
		nameEntry = newEntry;
		parent = newParentPath;
		copyStyle = FSParentPath.COPY_ID_INHERIT_UNKNOWN;
        if(newRevNode != null){
            copySrcPath = newRevNode.getCopyFromPath();
        }else{
            copySrcPath = null;
        }
		
	}
	
	//methods-accessors
	public FSRevisionNode getRevNode(){
		return revNode;
	}
	public void setRevNode(FSRevisionNode newRevNode){
		revNode = newRevNode;
	}
	public String getNameEntry(){
		return nameEntry;
	}
	public void setNameEntry(String newNameEntry){
		nameEntry = newNameEntry;
	}
	public FSParentPath getParent(){
		return parent;
	}
	public void setParent(FSParentPath newParent){
		parent = newParent;
	}
	public int getCopyStyle(){
		return copyStyle;
	}
	public void setCopyStyle(int newCopyStyle){
		copyStyle = newCopyStyle;
	}
	public String getCopySrcPath(){
		return copySrcPath;
	}
	public void setCopySrcPath(String newCopyPath){
		copySrcPath = newCopyPath;
	}
	public void setParentPath(FSRevisionNode newRevNode, String newEntry, FSParentPath newParentPath){
		revNode = newRevNode;
		nameEntry = newEntry;
		parent = newParentPath;
		copyStyle = FSParentPath.COPY_ID_INHERIT_UNKNOWN;
		copySrcPath = null;
	}

	//methods
	public String constructParentPath(){
		String pathSoFar = "/";
		
		if(this.getParent() != null){
			pathSoFar = this.getParent().constructParentPath();
		}
		
		return this.getNameEntry() != null ? SVNPathUtil.append(pathSoFar, this.getNameEntry()) : pathSoFar;
	}

    /* Return a text string describing the absolute path of parentPath.
     */
    public String getAbsPath(){
        String pathSoFar = "/";
        if(getParent() != null){
            pathSoFar = getAbsPath();
        }
        return getNameEntry() != null ? SVNPathUtil.concatToAbs(pathSoFar, getNameEntry()) : pathSoFar;    
    }

	//Return value consist of :
	//1:	SVNLocationEntry.revision
	//		copy inheritance style
	//2:	SVNLocationEntry.path
	//		copy src path
	public static SVNLocationEntry getCopyInheritance(File reposRootDir, FSParentPath child, String txnID)throws SVNException{
		if(child == null){
			SVNErrorManager.error("argument FSParentPath have to be valid");			
		}
		if(child.getParent() == null){
			SVNErrorManager.error("argument FSParentPath have to be not root");
			return null;
		}
		if(txnID == null){
			SVNErrorManager.error("argument String txnID = null");
		}
		FSID childID = child.getRevNode().getId();
		FSID parentID = child.getParent().getRevNode().getId();
		FSID copyrootID = new FSID();
		String childCopyID = childID.getCopyID();
		String parentCopyID = parentID.getCopyID();
		FSRevisionNode copyrootRoot = null;
		FSRevisionNode copyrootNode = null;				
		
		//If this child is already mutable, we have nothing to do
		if(childID.getTxnID() != null){
			return new SVNLocationEntry(FSParentPath.COPY_ID_INHERIT_SELF, null);
		}
		//From this point on, we'll assume that the child will just take
	    //its copy ID from its parent
		SVNLocationEntry constrEntry = new SVNLocationEntry(FSParentPath.COPY_ID_INHERIT_PARENT, null);
		
		//Special case: if the child's copy ID is '0', use the parent's
	    //copy ID
		if(childCopyID.compareTo("0") == 0){
			return constrEntry;
		}
		
		//Compare the copy IDs of the child and its parent.  If they are
	    //the same, then the child is already on the same branch as the
	    //parent, and should use the same mutability copy ID that the
	    //parent will use
		if(childCopyID.compareTo(parentCopyID) == 0){
			return constrEntry;
		}
		
	    //If the child is on the same branch that the parent is on, the
	    //child should just use the same copy ID that the parent would use.
	    //Else, the child needs to generate a new copy ID to use should it
	    //need to be made mutable.  We will claim that child is on the same
	    //branch as its parent if the child itself is not a branch point,
	    //or if it is a branch point that we are accessing via its original
	    //copy destination path
		SVNLocationEntry copyrootEntry = new SVNLocationEntry(child.getRevNode().getCopyRootRevision(), child.getRevNode().getCopyRootPath());
		try{
			copyrootRoot = FSReader.getRootRevNode(reposRootDir, copyrootEntry.getRevision());
			copyrootNode = FSReader.getRevisionNode(reposRootDir, copyrootEntry.getPath(), copyrootRoot, 0);
			copyrootID = copyrootNode.getId();
		}catch(SVNException ex){
			SVNErrorManager.error("");
		}
		if(FSID.compareIds(copyrootID, childID) == -1){
			return copyrootEntry;
		}
		
	    //Determine if we are looking at the child via its original path or
	    //as a subtree item of a copied tree
		if(child.getRevNode().getCreatedPath().compareTo(child.constructParentPath()) == 0){
			return new SVNLocationEntry(FSParentPath.COPY_ID_INHERIT_SELF, null);
		}
		return new SVNLocationEntry(FSParentPath.COPY_ID_INHERIT_NEW, child.getRevNode().getCreatedPath());
	}	
	//Copy id inheritance style 
	public static final int COPY_ID_INHERIT_UNKNOWN = 0;
	public static final int COPY_ID_INHERIT_SELF = 1;
	public static final int COPY_ID_INHERIT_PARENT = 2;
	public static final int COPY_ID_INHERIT_NEW = 3;
	
}
