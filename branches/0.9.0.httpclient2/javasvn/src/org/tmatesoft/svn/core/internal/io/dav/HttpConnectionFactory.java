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
package org.tmatesoft.svn.core.internal.io.dav;

import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.io.SVNRepository;


public class HttpConnectionFactory {
    
    public static IHttpConnection generateHttpConnection(SVNURL location, SVNRepository repos, boolean useHttpClient){
        if(useHttpClient){
            return new CommonsHttpConnection(location, repos);
        }
        return new HttpConnection(location, repos);
    };
}
