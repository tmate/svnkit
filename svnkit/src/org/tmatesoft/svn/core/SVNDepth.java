/*
 * ====================================================================
 * Copyright (c) 2004-2007 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core;


/**
 * @version 1.1.2
 * @author  TMate Software Ltd.
 */
public class SVNDepth {
    
    public static final SVNDepth DEPTH_UNKNOWN = new SVNDepth(-2, "unknown"); 
    public static final SVNDepth DEPTH_EXCLUDE = new SVNDepth(-1, "exclude"); 
    public static final SVNDepth DEPTH_EMPTY = new SVNDepth(0, "empty"); 
    public static final SVNDepth DEPTH_FILES = new SVNDepth(1, "files"); 
    public static final SVNDepth DEPTH_IMMEDIATES = new SVNDepth(2, "immediates"); 
    public static final SVNDepth DEPTH_INFINITY = new SVNDepth(3, "infinity"); 
    
    private int myId;
    private String myName;
    
    private SVNDepth(int id, String name) {
        myId = id;
        myName = name;
    }

    public int getId() {
        return myId;
    }
    
    public String getName() {
        return myName;
    }
    
    public String toString() {
        return getName();
    }
    
    public static String asString(SVNDepth depth) {
        if (depth != null) {
            return depth.getName();
        } 
        return "INVALID-DEPTH";
    }
    
    public static boolean recurseFromDepth(SVNDepth depth) {
        return depth == null || depth == DEPTH_INFINITY || depth == DEPTH_UNKNOWN;
    }
    
    public static SVNDepth fromRecurse(boolean recurse) {
        return recurse ? DEPTH_INFINITY : DEPTH_FILES;
    }
    
    public static SVNDepth fromString(String string) {
        if (DEPTH_EMPTY.getName().equals(string)) {
            return DEPTH_EMPTY;
        } else if (DEPTH_EXCLUDE.getName().equals(string)) {
            return DEPTH_EXCLUDE;
        } else if (DEPTH_FILES.getName().equals(string)) {
            return DEPTH_FILES;
        } else if (DEPTH_IMMEDIATES.getName().equals(string)) {
            return DEPTH_IMMEDIATES;
        } else if (DEPTH_INFINITY.getName().equals(string)) {
            return DEPTH_INFINITY;
        } else {
            return DEPTH_UNKNOWN;
        }
    }

}
