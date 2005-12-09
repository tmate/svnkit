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
public class FSRoot {
    /* The kind of root this is */
    private boolean myIsTxnRoot;
    /* For transaction roots, the name of the transaction  */
    private String myTxnId;
    /* For transaction roots, flags describing the txn's behavior. */
    private int myTxnFlags;
    /* For revision roots, the number of the revision.  */
    private long myRevision;
    /* For revision roots, the node-rev representation of the root */
    private FSRevisionNode myRootRevNode;
    
    public FSRoot(long revision, FSRevisionNode root) {
        myRevision = revision;
        myRootRevNode = root;
        myIsTxnRoot = false;
        myTxnId = null;
        myTxnFlags = 0;

    }

    public FSRoot(String id, int flags) {
        myTxnId = id;
        myTxnFlags = flags;
        myIsTxnRoot = true;
        myRevision = FSConstants.SVN_INVALID_REVNUM;
        myRootRevNode = null;
    }

    public boolean isTxnRoot() {
        return myIsTxnRoot;
    }
    
    public long getRevision() {
        return myRevision;
    }

    public void setRevision(long revision) {
        myRevision = revision;
    }

    public FSRevisionNode getRootRevisionNode() {
        return myRootRevNode;
    }

    public void setRootRevisionNode(FSRevisionNode root) {
        myRootRevNode = root;
    }

    public int getTxnFlags() {
        return myTxnFlags;
    }

    public void setTxnFlags(int txnFlags) {
        myTxnFlags = txnFlags;
    }

    public String getTxnId() {
        return myTxnId;
    }

    public void setTxnId(String txnId) {
        myTxnId = txnId;
    }
}
