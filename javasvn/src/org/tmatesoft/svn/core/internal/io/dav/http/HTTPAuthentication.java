/*
 * ====================================================================
 * Copyright (c) 2004-2006 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://tmate.org/svn/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.io.dav.http;

import java.util.Map;
import java.util.TreeMap;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.auth.SVNPasswordAuthentication;

/**
 * @version 1.1
 * @author  TMate Software Ltd.
 */
abstract class HTTPAuthentication {

    private Map myChallengeParameters;
    private SVNPasswordAuthentication myOriginalCredentials;
    
    public HTTPAuthentication (SVNPasswordAuthentication credentials) {
        myOriginalCredentials = credentials;
    }

    public void setChallengeParameter(String name, String value) {
        Map params = getChallengeParameters();
        params.put(name, value);
    }
    
    public String getChallengeParameter(String name) {
        if (myChallengeParameters == null) {
            return null;
        }
        return (String)myChallengeParameters.get(name);
    }
    
    protected Map getChallengeParameters() {
        if (myChallengeParameters == null) {
            myChallengeParameters = new TreeMap();
        }
        return myChallengeParameters;
    }
    
    public SVNPasswordAuthentication getCredentials() {
        return myOriginalCredentials;
    }
    
    public void setCredentials(SVNPasswordAuthentication originalCredentials) {
        myOriginalCredentials = originalCredentials;
    }

    public String getUserName() {
        if (myOriginalCredentials != null) {
            return myOriginalCredentials.getUserName();
        }
        return null;
    }
    
    public String getPassword() {
        if (myOriginalCredentials != null) {
            return myOriginalCredentials.getPassword();
        }
        return null;
    }
    
    public abstract String authenticate() throws SVNException;

}
