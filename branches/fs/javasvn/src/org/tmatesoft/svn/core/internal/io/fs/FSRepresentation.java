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
public class FSRepresentation {
    private long myRevision;
    private long myOffset;
    private long mySize;
    private long myExpandedSize;
    private String myHexDigest;
    private String myTxnId;
    
    public FSRepresentation(long revision, long offset, long size, long expandedSize, String hexDigest){
        myRevision = revision;
        myOffset = offset;
        mySize = size;
        myExpandedSize = expandedSize;
        myHexDigest = hexDigest;
    }

    public FSRepresentation(){
        myRevision = FSConstants.SVN_INVALID_REVNUM;
        myOffset = -1;
        mySize = -1;
        myExpandedSize = -1;
        myHexDigest = null;
    }
    
    public void setRevision(long rev){
        myRevision = rev;
    }

    public void setOffset(long offset){
        myOffset = offset;
    }

    public void setSize(long size){
        mySize = size;
    }

    public void setExpandedSize(long expandedSize){
        myExpandedSize = expandedSize;
    }
    
    public void setHexDigest(String hexDigest){
        myHexDigest = hexDigest;
    }
    
    public long getRevision(){
        return myRevision;
    }

    public long getOffset(){
        return myOffset;
    }

    public long getSize(){
        return mySize;
    }

    public long getExpandedSize(){
        return myExpandedSize;
    }
    
    public String getHexDigest(){
        return myHexDigest;
    }
    
    public boolean equals(Object obj){
        if (obj == null || obj.getClass() != FSRepresentation.class) {
            return false;
        }
        FSRepresentation rep = (FSRepresentation)obj;
        return myRevision == rep.getRevision() && myOffset == rep.getOffset();
    }
    
    public String toString(){
        return myRevision + " " + myOffset + " " + mySize + " " + myExpandedSize + " " + myHexDigest;
    }

    
    public String getTxnId() {
        return myTxnId;
    }

    
    public void setTxnId(String txnId) {
        myTxnId = txnId;
    }
}
