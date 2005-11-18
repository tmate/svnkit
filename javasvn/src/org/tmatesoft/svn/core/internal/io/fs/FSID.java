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

    public void setTxnID(String txnID){
        myTxnID = txnID;
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

    public String getTxnID(){
        return myTxnID;
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
    
    public boolean equals(Object obj){
        if (obj == null || obj.getClass() != FSID.class) {
            return false;
        }
        FSID id = (FSID)obj;
        if(this == id){
            return true;
        }
        if(!myNodeID.equals(id.getNodeID())){
            return false;
        }
        if(!myCopyID.equals(id.getCopyID())){
            return false;
        }
        if(!myTxnID.equals(id.getTxnID())){
            return false;
        }
        if(myRevision != id.getRevision() || myOffset != id.getOffset()){
            return false;
        }
        return true;
    }

    public static int compareIds(FSID id1, FSID id2){
        if(areEqualIds(id1, id2)){
            return 0;
        }
        return checkIdsRelated(id1, id2) ? 1 : -1;
    }
    
    private static boolean checkIdsRelated(FSID id1, FSID id2){
        if(id1 == id2){
            return true;
        }
        /* If both node ids start with _ and they have differing transaction
         * IDs, then it is impossible for them to be related. 
         */
        if(id1.getNodeID().startsWith("_")){
            if(!id1.getTxnID().equals(id2.getTxnID())){
                return false;
            }
        }
        return id1.getNodeID().equals(id2.getNodeID());
    }
    
    private static boolean areEqualIds(FSID id1, FSID id2){
        if(id1 == id2){
            return true;
        }else if(id1 != null){
            id1.equals(id2);
        }else if(id2 != null){
            return id2.equals(id1);
        }
        return true;
    }
    
}
