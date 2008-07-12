/*
 * ====================================================================
 * Copyright (c) 2004-2008 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.io;


/**
 * @version 1.1.2
 * @author  TMate Software Ltd.
 */
public class SVNCapability {
    
    /**
     * Capability Type Constants
     */
    
    /**
     * Represents the capability of the server to understand 
     * what the client means when the client describes the
     * depth of a working copy to the server
     */
	public static final SVNCapability DEPTH = new SVNCapability("depth");
	
    /**
     * Represents the capability of the server to support merge-tracking
     * information 
     */
	public static final SVNCapability MERGE_INFO = new SVNCapability("mergeinfo");
    
	/**
     * Represents the capability of retrieving arbitrary revision properties 
     */
	public static final SVNCapability LOG_REVPROPS = new SVNCapability("log-revprops");
    
	/**
     * Represents the capability of replaying a directory in the repository (partial replay)
     */
	public static final SVNCapability PARTIAL_REPLAY = new SVNCapability("partial-replay");
    
	/**
     * Represents the capability of including revision properties in a commit
     */
	public static final SVNCapability COMMIT_REVPROPS = new SVNCapability("commit-revprops");
	
	private String myName;
	
	private SVNCapability(String name) {
		myName = name;
	}

	public String toString() {
        return myName;
    }

}
