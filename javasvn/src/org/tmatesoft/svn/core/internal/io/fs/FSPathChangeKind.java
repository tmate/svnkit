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
 * The kind of change that occurred on the path. 
 * 
 * @version 1.0
 * @author  TMate Software Ltd.
 */
public class FSPathChangeKind {
    /* default value */
    public static final FSPathChangeKind FS_PATH_CHANGE_MODIFY = new FSPathChangeKind(0); 
    /* path added in txn */
    public static final FSPathChangeKind FS_PATH_CHANGE_ADD = new FSPathChangeKind(1); 
    /* path removed in txn */
    public static final FSPathChangeKind FS_PATH_CHANGE_DELETE = new FSPathChangeKind(2); 
    /* path removed and re-added in txn */
    public static final FSPathChangeKind FS_PATH_CHANGE_REPLACE = new FSPathChangeKind(3); 
    /* ignore all previous change items for path (internal-use only) */
    public static final FSPathChangeKind FS_PATH_CHANGE_RESET = new FSPathChangeKind(4); 

    private int myID;

    private FSPathChangeKind(int id) {
        myID = id;
    }
    public boolean equals(Object o){
        if (o == null || o.getClass() != FSPathChangeKind.class) {
            return false;
        }
        return myID == ((FSPathChangeKind) o).myID;
    }
}
