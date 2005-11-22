/*
 * Created on 13.11.2005
 *
 * TODO To change the template for this generated file go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
package org.tmatesoft.svn.core.internal.io.fs;

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
	FSDAGNode dagNode;
	
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
	public FSParentPath(FSDAGNode newDagNode, String newNameEntry, FSParentPath newParent, int newCopyStyle, String newCopySrcPath){
		dagNode = newDagNode;
		nameEntry = newNameEntry;
		parent = newParent;
		copyStyle = newCopyStyle;
		copySrcPath = newCopySrcPath;
	}
	public FSParentPath(FSParentPath newParentPath){
		dagNode = newParentPath.getDAGNode();
		nameEntry = newParentPath.getNameEntry();
		parent = newParentPath.getParentPath();
		copyStyle = newParentPath.getCopyStyle();
		copySrcPath = newParentPath.getCopySrcPath();		
	}
	
	//mothods-accessors
	public FSDAGNode getDAGNode(){
		return dagNode;
	}
	public void setDAGNode(FSDAGNode newDagNode){
		dagNode = newDagNode;
	}
	public String getNameEntry(){
		return nameEntry;
	}
	public void setNameEntry(String newNameEntry){
		nameEntry = newNameEntry;
	}
	public FSParentPath getParentPath(){
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
	
	//Copy id inheritance style 
	public static int COPY_ID_INHERIT_UNKNOWN = 0;
	public static int COPY_ID_INHERIT_SELF = 1;
	public static int COPY_ID_INHERIT_PARENT = 2;
	public static int COPY_ID_INHERIT_NEW = 3;

}
