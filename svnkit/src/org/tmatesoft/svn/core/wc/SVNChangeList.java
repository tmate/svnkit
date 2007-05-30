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
package org.tmatesoft.svn.core.wc;

import java.io.File;
import java.util.Collection;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;


/**
 * @version 1.1.2
 * @author  TMate Software Ltd.
 */
public class SVNChangeList {
    private String myChangelistName;
    private Collection myPaths;
    private File myRootPath;
    private ISVNOptions myOptions;
    private ISVNRepositoryPool myRepositoryPool;
    private ISVNAuthenticationManager myAuthManager;    
    private SVNChangelistClient myChangelistClient;
    
    public SVNChangeList(String changelistName) {
        myChangelistName = changelistName;
    }

    public SVNChangeList(String changelistName, File rootPath) {
        myChangelistName = changelistName;
        myRootPath = rootPath;
    }

    public String getChangelistName() {
        return myChangelistName;
    }
    
    public Collection getPaths() {
        return myPaths;
    }

    public File getRootPath() {
        return myRootPath;
    }

    public void setRootPath(File rootPath) {
        myRootPath = rootPath;
    }
    
    public Collection getChangelistPaths() throws SVNException {
        SVNChangelistClient client = getChangelistClient();
        Collection changelistTargets = client.getChangelist(myRootPath, myChangelistName, (Collection) null);
        if (changelistTargets.isEmpty()) {
            SVNErrorMessage error = SVNErrorMessage.create(SVNErrorCode.CL_ARG_PARSING_ERROR, 
                                    "no such changelist ''{0}''", myChangelistName); 
            SVNErrorManager.error(error);
        }
        return changelistTargets;
    }

    protected void setAuthManager(ISVNAuthenticationManager authManager) {
        myAuthManager = authManager;
    }
    
    protected void setOptions(ISVNOptions options) {
        myOptions = options;
    }
    
    protected void setRepositoryPool(ISVNRepositoryPool repositoryPool) {
        myRepositoryPool = repositoryPool;
    }

    private ISVNAuthenticationManager getAuthManager() {
        if (myAuthManager == null) {
            myAuthManager = SVNWCUtil.createDefaultAuthenticationManager();
        }
        return myAuthManager;
    }
    
    private ISVNOptions getOptions() {
        if (myOptions == null) {
            myOptions = SVNWCUtil.createDefaultOptions(true);
        }
        return myOptions;
    }

    private SVNChangelistClient getChangelistClient() {
        if (myChangelistClient == null) {
            if (myRepositoryPool != null) {
                myChangelistClient = new SVNChangelistClient(myRepositoryPool, getOptions());
            } else {
                myChangelistClient = new SVNChangelistClient(getAuthManager(), getOptions());
            }
        }
        return myChangelistClient;
    }
    
}
