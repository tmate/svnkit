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
public class SVNDepth implements Comparable {
    /**
     * Depth Type Constants
     * The order of these depths is important: the higher the number,
     * the deeper it descends.  This allows us to compare two depths
     * numerically to decide which should govern.
     */
    
    /**
     * Depth undetermined or ignored.
     */
    public static final SVNDepth UNKNOWN = new SVNDepth(-2, "unknown"); 
    
    /**
     * Exclude (don't descend into) directory D.
     */
    public static final SVNDepth EXCLUDE = new SVNDepth(-1, "exclude"); 
    
    /**
     * Just the named directory D, no entries.  Updates will not pull in
     * any files or subdirectories.
     */
    public static final SVNDepth EMPTY = new SVNDepth(0, "empty"); 
    
    /**
     * D and its file children, but not subdirectories.  Updates will pull in any
     * files, but not subdirectories.
     */
    public static final SVNDepth FILES = new SVNDepth(1, "files"); 
    
    /**
     * D and its immediate children (D and its entries).  Updates will pull in
     * any files or subdirectories with depth <code>EMPTY</code>.
     */
    public static final SVNDepth IMMEDIATES = new SVNDepth(2, "immediates"); 
    
    /**
     * D and all descendants (full recursion from D).  Updates will pull
     in any files or subdirectories; those subdirectories' entries will have depth <code>INFINITY</code>.
     */
    public static final SVNDepth INFINITY = new SVNDepth(3, "infinity"); 
    
    private int myId;
    private String myName;
    
    private SVNDepth(int id, String name) {
        myId = id;
        myName = name;
    }

    /** 
     * Returns numerical Id of depth
     * @return int
     * @since  SVNKit 1.2.0, SVN 1.5.0
     */
    public int getId() {
        return myId;
    }
    
    /** 
     * Returns name of depth
     * @return String
     * @since  SVNKit 1.2.0, SVN 1.5.0
     */
    public String getName() {
        return myName;
    }
    
    public String toString() {
        return getName();
    }
    
    /** 
    * Returns a recursion boolean based on depth.
    *
    * Although much code has been converted to use depth, some code still
    * takes a recurse boolean.  In most cases, it makes sense to treat
    * unknown or infinite depth as recursive, and any other depth as
    * non-recursive (which in turn usually translates to <code>FILES</code>).
    * @return boolean
    * @since  SVNKit 1.2.0, SVN 1.5.0
    */
    public boolean isRecursive() {
        return this == INFINITY || this == UNKNOWN;
    }
    
    public int compareTo(Object o) {
        if (o == null || o.getClass() != SVNDepth.class) {
            return -1;
        }
        SVNDepth otherDepth = (SVNDepth) o;
        return myId == otherDepth.myId ? 0 : (myId > otherDepth.myId ? 1 : -1);
    }

    public boolean equals(Object obj) {
        return compareTo(obj) == 0;
    }
    
    /** 
     * Appropriate name of <code>depth</code> is returned. If <code>depth</code> does not represent
     * a recognized depth, <code>"INVALID-DEPTH"</code> is returned.
     * @param depth depth, which name needs to be returned 
     * @return int
     * @since  SVNKit 1.2.0, SVN 1.5.0
     */
    public static String asString(SVNDepth depth) {
        if (depth != null) {
            return depth.getName();
        } 
        return "INVALID-DEPTH";
    }
    
    /** 
     * Based on depth determines if it recursive or not.
     * In most cases, it makes sense to treat unknown or infinite depth as recursive, 
     * and any other depth as non-recursive
     * 
     * @param depth depth value
     * @return boolean
     * @see #isRecursive()
     * @see #fromRecurse(boolean)
     * @since  SVNKit 1.2.0, SVN 1.5.0
     */
    public static boolean recurseFromDepth(SVNDepth depth) {
        return depth == null || depth == INFINITY || depth == UNKNOWN;
    }
    
    /**
     * Treats recursion as <code>INFINITY</code> depth and <code>FILES</code> otherwise
     * @param recurse indicator of recursion
     * @return SVNDepth
     * @see #isRecursive()
     * @see #recurseFromDepth(SVNDepth)
     * @since  SVNKit 1.2.0, SVN 1.5.0
     */
    public static SVNDepth fromRecurse(boolean recurse) {
        return recurse ? INFINITY : FILES;
    }
    
    /**
     * Based on string value finds <code>SVNDepth</code> constant.
     * @param string depth value represented by string
     * @return SVNDepth
     * @since  SVNKit 1.2.0, SVN 1.5.0
     */
    public static SVNDepth fromString(String string) {
        if (EMPTY.getName().equals(string)) {
            return EMPTY;
        } else if (EXCLUDE.getName().equals(string)) {
            return EXCLUDE;
        } else if (FILES.getName().equals(string)) {
            return FILES;
        } else if (IMMEDIATES.getName().equals(string)) {
            return IMMEDIATES;
        } else if (INFINITY.getName().equals(string)) {
            return INFINITY;
        } else {
            return UNKNOWN;
        }
    }
    
    /** 
     * Based on depth id returns <code>SVNDepth</code> constant.
     * @param id depth id
     * @return SVNDepth
     * @since  SVNKit 1.2.0, SVN 1.5.0
     */
    public static SVNDepth fromID(int id) { 
        switch (id) {
            case 3:
                return INFINITY;
            case 2:
                return IMMEDIATES;
            case 1:
                return FILES;
            case 0:
                return EMPTY;
            case -1:
                return EXCLUDE;
            case -2:
            default:
                return UNKNOWN;
        }
    }
    
    /** 
     * Returns <code>INFINITY</code> if <code>recurse</code> is <code>true</code>, else
     * returns <code>EMPTY</code>.
     * Code should never need to use this, it is called only from pre-depth APIs, for compatibility.
     * @param recurse boolean
     * @return SVNDepth
     * @since  SVNKit 1.2.0, SVN 1.5.0
     */
    public static SVNDepth getInfinityOrEmptyDepth(boolean recurse) {
        return recurse ? INFINITY : EMPTY;
    }
    
    /** 
     * The same as {@link #getInfinityOrEmptyDepth(boolean)}, but <code>FILES</code> is returned when recursive.
     * Code should never need to use this, it is called only from pre-depth APIs, for compatibility.
     * @param recurse boolean
     * @return SVNDepth
     * @since  SVNKit 1.2.0, SVN 1.5.0
     */
    public static SVNDepth getInfinityOrFilesDepth(boolean recurse) {
        return recurse ? INFINITY : FILES;
    }
    
    /** 
     * The same as {@link #getInfinityOrEmptyDepth(boolean)}, but <code>IMMEDIATES</code> is returned when recursive.
     * Code should never need to use this, it is called only from pre-depth APIs, for compatibility.
     * @param recurse boolean
     * @return SVNDepth
     * @since  SVNKit 1.2.0, SVN 1.5.0
     */
    public static SVNDepth getInfinityOrImmediatesDepth(boolean recurse) {
        return recurse ? INFINITY : IMMEDIATES;
    }

    /** 
     * Returns <code>UNKNOWN</code> if <code>recurse</code> is <code>true</code>, else
     * returns <code>EMPTY</code>.
     * Code should never need to use this, it is called only from pre-depth APIs, for compatibility.
     * @param recurse boolean
     * @return SVNDepth
     * @since  SVNKit 1.2.0, SVN 1.5.0
     */
    public static SVNDepth getUnknownOrEmptyDepth(boolean recurse) {
        return recurse ? UNKNOWN : EMPTY;
    }
    
    /** 
     * The same as {@link #getUnknownOrEmptyDepth(boolean)}, but <code>FILES</code> is returned when recursive.
     * Code should never need to use this, it is called only from pre-depth APIs, for compatibility.
     * @param recurse boolean
     * @return SVNDepth
     * @since  SVNKit 1.2.0, SVN 1.5.0
     */
    public static SVNDepth getUnknownOrFilesDepth(boolean recurse) {
        return recurse ? UNKNOWN : FILES;
    }
    
    /** 
     * The same as {@link #getUnknownOrEmptyDepth(boolean)}, but <code>IMMEDIATES</code> is returned when recursive.
     * Code should never need to use this, it is called only from pre-depth APIs, for compatibility.
     * @param recurse boolean
     * @return SVNDepth
     * @since  SVNKit 1.2.0, SVN 1.5.0
     */
    public static SVNDepth getUnknownOrImmediatesDepth(boolean recurse) {
        return recurse ? UNKNOWN : IMMEDIATES;
    }
}
