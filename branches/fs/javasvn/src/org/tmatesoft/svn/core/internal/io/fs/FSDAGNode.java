/*
 * Created on 13.11.2005
 *
 * TODO To change the template for this generated file go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
package org.tmatesoft.svn.core.internal.io.fs;

import org.tmatesoft.svn.core.*;
import org.tmatesoft.svn.core.io.*; 

import java.io.*; 

/**
 * @author Tim
 *
 * TODO To change the template for this generated type comment go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
public class FSDAGNode{
	//The node revision ID for this dag node
	FSID id;
	
	//The node's type (file, dir, etc.)
	SVNNodeKind kind;
	
	//The node's NODE-REVISION, or NULL if we haven't read it in yet.
	FSRevisionNode revNode;
	
	//The path at which this node was created
	String createdPath;
	
	//constructors
	public FSDAGNode(){		
	}
	public FSDAGNode(FSID newId, SVNNodeKind newKind, FSRevisionNode newRevNode, String newCreatedPath){
		id = newId;
		kind = newKind;
		revNode = newRevNode;
		createdPath = newCreatedPath;
	}
	public FSDAGNode(FSDAGNode newDAGNode){
		id = newDAGNode.getID();
		kind = newDAGNode.getNodeKind();
		revNode = newDAGNode.getRevNode();
		createdPath = newDAGNode.getCreatedPath();
	}
	
	//methods-accessors
	public FSID getID(){
		return id;		
	}
	public void setId(FSID newId){
		id = newId;
	}
	public SVNNodeKind getNodeKind(){
		return kind;
	}
	public void setNodeKind(SVNNodeKind newKind){
		kind = newKind;
	}
	public FSRevisionNode getRevNode(){
		return revNode;
	}
	public void setRevNode(FSRevisionNode newRevNode){
		revNode = newRevNode;		
	}
	public String getCreatedPath(){
		return createdPath;
	}
	public void setCreatedPath(String newCreatedPath){
		createdPath = newCreatedPath; 
	}
	public static FSRevisionNode getFSRevisionNodeFromFSDAGNode(File reposRootDir, FSDAGNode dagNode)throws SVNException{
		if(dagNode.getRevNode() == null){
			try{				
				dagNode.setRevNode(FSReader.getRevNodeFromID(reposRootDir, dagNode.getID()));
			}catch(SVNException ex){
				System.out.println("svn: bad excecution of FSReader.getRevNodeFromID");
				System.out.println("reposRootDir==null"+(reposRootDir==null));
				System.out.println("dagNode==null"+(dagNode==null));
			}
		}		
		return dagNode.getRevNode();
	}
	
	public static FSDAGNode getFSDAGNodeFromId(File reposRootDir, FSID id)throws SVNException{
		FSRevisionNode newRev = null;
		FSDAGNode dagNode = new FSDAGNode();
		dagNode.setId(id);
		try{
			newRev = getFSRevisionNodeFromFSDAGNode(reposRootDir, dagNode);			
		}catch(SVNException ex){
			System.out.println("svn: bad excecution of FSDAGNode.getFSRevisionNodeFromFSDAGNode");
			System.out.println("reposRootDir==null"+(reposRootDir==null));
			System.out.println("dagNode==null"+(dagNode==null));
		}		
		return new FSDAGNode(id, newRev.getType(), newRev, newRev.getCreatedPath());
	}	
	public static SVNLocationEntry getCopyrootFromFSDAGNode(File reposRootDir, FSDAGNode dagNode)throws SVNException{
		FSRevisionNode revNode=null;
		
		try{
			revNode = FSDAGNode.getFSRevisionNodeFromFSDAGNode(reposRootDir, dagNode);
		}catch(SVNException ex){
			System.out.println("svn: bad excecution of FSDAGNode.getFSRevisionNodeFromFSDAGNode()");
			System.out.println("FSRevisionNode revNode==null"+(revNode==null));
		}		
		return new SVNLocationEntry(revNode.getCopyRootRevision(), revNode.getCopyRootPath());
	}
}
