/*
 * ====================================================================
 * Copyright (c) 2004-2010 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.wc.db;


/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public class DBResult {
    private Object[] myResultArray;
    
    public DBResult(Object... result) {
        myResultArray = result;
    }

    public Object getResultObjectAt(int index) {
        return myResultArray[index];
    }
    
}
