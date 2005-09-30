/*
 * ====================================================================
 * Copyright (c) 2004 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://tmate.org/svn/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.io.fs;

import org.tmatesoft.svn.core.SVNNodeKind;


public class FSRevisionNode {
    //id: a.b.r<revID>/offset
    private FSID myRevNodeId;
    
    //type: 'dir' or 'file' 
    private SVNNodeKind myType;
    
    //count:  count of revs since base
    private long myCount;
    
    //(_)a.(_)b.tx-y
    private String myTxnID;

    //pred: a.b.r<revID>/offset
    private FSID myPredRevNodeId;
    
    //text: <rev> <offset> <length> <size> <digest>
    private FSRepresentation myTextRepresentation;
    
    //props: <rev> <offset> <length> <size> <digest>
    private FSRepresentation myPropsRepresentation;
    
    //cpath: <path>
    private String myCreatedPath;

    //copyfrom: <revID> <path>
    private long myCopyFromRevision;
    private String myCopyFromPath;

    //copyroot: <revID> <created-path>
    private long myCopyRootRevision;    
    private String myCopyRootPath;
    
    public FSRevisionNode(){
    }
    
    public void setRevNodeID(FSID revNodeID){
        myRevNodeId = revNodeID;
    }

    public void setType(SVNNodeKind nodeKind){
        myType = nodeKind;
    }

    public void setCount(long count){
        myCount = count;
    }

    public void setTxnID(String txnID){
        myTxnID = txnID;
    }

    public void setPredecessorRevNodeID(FSID predRevNodeId){
        myPredRevNodeId = predRevNodeId;
    }

    public void setTextRepresentation(FSRepresentation textRepr){
        myTextRepresentation = textRepr;
    }

    public void setPropsRepresentation(FSRepresentation propsRepr){
        myPropsRepresentation = propsRepr;
    }

    public void setCreatedPath(String cpath){
        myCreatedPath = cpath;
    }
    
    public void setCopyFromRevision(long copyFromRev){
        myCopyFromRevision = copyFromRev;
    }
    
    public void setCopyFromPath(String copyFromPath){
        myCopyFromPath = copyFromPath;
    }

    public void setCopyRootRevision(long copyRootRev){
        myCopyRootRevision = copyRootRev;
    }
    
    public void setCopyRootPath(String copyRootPath){
        myCopyRootPath = copyRootPath;
    }

    public FSID getRevNodeID(){
        return myRevNodeId;
    }

    public SVNNodeKind getType(){
        return myType;
    }

    public long getCount(){
        return myCount;
    }

    //?
    public String getTxnID(){
        return myTxnID;
    }

    public FSID getPredecessorRevNodeId(){
        return myPredRevNodeId;
    }

    //text
    public FSRepresentation getTextRepresentation(){
        return myTextRepresentation;
    }

    //props
    public FSRepresentation getPropsRepresentation(){
        return myPropsRepresentation;
    }

    public String getCreatedPath(){
        return myCreatedPath;
    }
    
    public long getCopyFromRevision(){
        return myCopyFromRevision;
    }
    
    public String getCopyFromPath(){
        return myCopyFromPath;
    }

    public long getCopyRootRevision(){
        return myCopyRootRevision;
    }
    
    public String getCopyRootPath(){
        return myCopyRootPath;
    }
    
}
