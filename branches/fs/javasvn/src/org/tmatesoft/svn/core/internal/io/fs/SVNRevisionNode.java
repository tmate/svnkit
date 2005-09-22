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


public class SVNRevisionNode {
    //id: a.b.r<revID>/offset
    private String myNodeID;
    private String myCopyID;
    private long myRevisionID;
    private long myOffset;
    //type: 'dir' or 'file' 
    private SVNNodeKind myType;
    //count:  count of revs since base
    private long myCount;
    //(_)a.(_)b.tx-y
    private String myTxnID;

    //pred: a.b.r<revID>/offset
    private String myPredNodeID;
    private String myPredCopyID;
    private long myPredRevisionID;
    private long myPredOffset;
    
    //text: <rev> <offset> <length> <size> <digest>
    private long myTextRevisionID;
    private long myTextOffset;
    private long myTextLength;
    private long myTextSize;
    private String myTextDigest;
    
    //cpath: <path>
    private String myCreatedPath;

    //copyfrom: <revID> <path>
    private long myCopyFromRevisionID;
    private String myCopyFromPath;

    //copyroot: <revID> <created-path>
    private long myCopyRootRevisionID;    
    private String myCopyRootPath;
    
    public SVNRevisionNode(){
        
    }
    
    public void setNodeID(String nodeID){
        myNodeID = nodeID;
    }

    public void setCopyID(String copyID){
        myCopyID = copyID;
    }
    
    public void setRevisionID(long revID){
        myRevisionID = revID;
    }

    public void setOffset(long offset){
        myOffset = offset;
    }
    
    public void setType(SVNNodeKind nodeType){
        myType = nodeType;
    }

    public void setCount(long count){
        myCount = count;
    }

    public void setTxnID(String txnID){
        myTxnID = txnID;
    }

    public void setPredNodeID(String predNodeID){
        myPredNodeID = predNodeID;
    }

    public void setPredCopyID(String predCopyID){
        myPredCopyID = predCopyID;
    }
    
    public void setPredRevisionID(long predRevID){
        myPredRevisionID = predRevID;
    }

    public void setPredOffset(long predOffset){
        myPredOffset = predOffset;
    }

    public void setTextRevisionID(long textRevID){
        myTextRevisionID = textRevID;
    }

    public void setTextOffset(long textOffset){
        myTextOffset = textOffset;
    }

    public void setTextLength(long textLength){
        myTextLength = textLength;
    }

    public void setTextSize(long textSize){
        myTextSize = textSize;
    }
    
    public void setTextDigest(String textDigest){
        myTextDigest = textDigest;
    }

    public void setCreatedPath(String cpath){
        myCreatedPath = cpath;
    }
    
    public void setCopyFromRevisionID(long copyFromRevID){
        myCopyFromRevisionID = copyFromRevID;
    }
    
    public void setCopyFromPath(String copyFromPath){
        myCopyFromPath = copyFromPath;
    }

    public void setCopyRootRevisionID(long copyRootRevID){
        myCopyRootRevisionID = copyRootRevID;
    }
    
    public void setCopyRootPath(String copyRootPath){
        myCopyRootPath = copyRootPath;
    }

    public String getNodeID(){
        return myNodeID;
    }

    public String getCopyID(){
        return myCopyID;
    }
    
    public long getRevisionID(){
        return myRevisionID;
    }

    public long getOffset(){
        return myOffset;
    }
    
    public SVNNodeKind getType(){
        return myType;
    }

    public long getCount(){
        return myCount;
    }

    public String getTxnID(){
        return myTxnID;
    }

    public String getPredNodeID(){
        return myPredNodeID;
    }

    public String getPredCopyID(){
        return myPredCopyID;
    }
    
    public long getPredRevisionID(){
        return myPredRevisionID;
    }

    public long getPredOffset(){
        return myPredOffset;
    }

    public long getTextRevisionID(){
        return myTextRevisionID;
    }

    public long getTextOffset(){
        return myTextOffset;
    }

    public long getTextLength(){
        return myTextLength;
    }

    public long getTextSize(){
        return myTextSize;
    }
    
    public String getTextDigest(){
        return myTextDigest;
    }

    public String getCreatedPath(){
        return myCreatedPath;
    }
    
    public long getCopyFromRevisionID(){
        return myCopyFromRevisionID;
    }
    
    public String getCopyFromPath(){
        return myCopyFromPath;
    }

    public long getCopyRootRevisionID(){
        return myCopyRootRevisionID;
    }
    
    public String getCopyRootPath(){
        return myCopyRootPath;
    }
    
}
