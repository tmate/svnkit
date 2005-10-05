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


/**
 * @version 1.0
 * @author  TMate Software Ltd.
 */
public class FSID {
    public static final String ID_INAPPLICABLE = "inapplicable";
    
    private String myNodeID;
    private String myCopyID;
    private String myTxnID;
    private long myRevision;
    private long myOffset;

    
    public FSID(){
        myNodeID = ID_INAPPLICABLE;
        myCopyID = ID_INAPPLICABLE;
        myTxnID = ID_INAPPLICABLE;
        myRevision = -1;
        myOffset = -1;
    }
    
    public boolean isTxn(){
        if(myTxnID != ID_INAPPLICABLE && myTxnID != null){
            return true;
        }
        return false;
    }
    
    public FSID(String nodeId, String txnId, String copyId, long revision, long offset){
        myNodeID = (nodeId == null) ? ID_INAPPLICABLE :  nodeId;
        myCopyID = (copyId == null) ? ID_INAPPLICABLE : copyId;
        myTxnID = (txnId == null) ? ID_INAPPLICABLE :  txnId; 
        myRevision = revision;
        myOffset = offset;
    }

    public void setNodeID(String nodeID){
        myNodeID = nodeID;
    }

    public void setCopyID(String copyID){
        myCopyID = copyID;
    }
    
    public void setRevision(long rev){
        myRevision = rev;
    }

    public void setOffset(long offset){
        myOffset = offset;
    }

    public String getNodeID(){
        return myNodeID;
    }

    public String getCopyID(){
        return myCopyID;
    }
    
    public long getRevision(){
        return myRevision;
    }

    public long getOffset(){
        return myOffset;
    }
    
}
