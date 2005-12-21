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

import org.tmatesoft.svn.core.io.SVNLocationEntry;

public class FSChange {    
    /*Path of the change*/
    private String path;
    
    /*Node revision ID of the change*/
    private FSID noderevID;
    
    /*The kind of change*/
    private FSPathChangeKind kind;
    
    /*Text or property mods*/
    private boolean textModi;
    private boolean propModi;
    
    /*Copyfrom revision and path*/
    private SVNLocationEntry copyfromEntry;
    
    public FSChange(String newPath, FSID newID, FSPathChangeKind newKind, boolean newTextMode, boolean newPropMode, SVNLocationEntry newCopyfromEntry){
        path = newPath;
        noderevID = new FSID(newID);
        kind = newKind;
        textModi = newTextMode;
        propModi = newPropMode;
        copyfromEntry = new SVNLocationEntry(newCopyfromEntry.getRevision(), newCopyfromEntry.getPath());
    }
    
    public String getPath(){
        return path;
    }
    
    public FSID getNodeRevID(){
        return noderevID;
    }
    
    public FSPathChangeKind getKind(){
        return kind;
    }    
    
    public boolean getTextModi(){
        return textModi;
    }
    
    public boolean getPropModi(){
        return propModi;
    }
    
    public SVNLocationEntry getCopyfromEntry(){
        return copyfromEntry;
    }
}
