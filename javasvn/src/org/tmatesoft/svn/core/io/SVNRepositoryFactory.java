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

package org.tmatesoft.svn.core.io;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.regex.Pattern;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;

/**
 * <b>SVNRepositoryFactory</b> is an abstract factory that is responsible
 * for creating an appropriate <b>SVNRepository</b> driver specific for the 
 * protocol (svn, http) to use.
 * 
 * <p>
 * Depending on what protocol a user exactly would like to use
 * to access the repository he should first of all set up an 
 * appropriate extension of this factory. So, if the user is going to
 * work with the repository via the custom <i>svn</i>-protocol (or 
 * <i>svn+ssh</i>) he initially calls:
 * <pre class="javacode">
 * ...
 * <span class="javakeyword">import</span> org.tmatesoft.svn.core.internal.io.svn.SVNRepositoryFactoryImpl;
 * ...		
 *     <span class="javacomment">//do it once in your application prior to using the library</span>
 *     <span class="javacomment">//enables working with a repository via the svn-protocol (over svn and svn+ssh)</span>
 *     SVNRepositoryFactoryImpl.setup();
 * ...</pre><br />
 * That <b>setup()</b> method registers an 
 * <b>SVNRepositoryFactoryImpl</b> instance in the factory (calling
 * {@link #registerRepositoryFactory(String, SVNRepositoryFactory) registerRepositoryFactory}). From 
 * this point the <b>SVNRepositoryFactory</b> knows how to create
 * <b>SVNRepository</b> instances specific for the <i>svn</i>-protocol.
 * And further the user can create an <b>SVNRepository</b> instance:
 * <pre class="javacode">
 *     ...
 *     <span class="javacomment">//the user gets an SVNRepository not caring</span>
 *     <span class="javacomment">//how it's implemented for the svn-protocol</span>
 *     SVNRepository repository = SVNRepositoryFactory.create(location);
 *     ...</pre><br />
 * All that was previously said about the <i>svn</i>-protocol is similar for
 * the <i>WebDAV</i>-protocol:
 * <pre class="javacode">
 * ...
 * <span class="javakeyword">import</span> org.tmatesoft.svn.core.internal.io.dav.DAVRepositoryFactory;
 * ...
 * 
 *     <span class="javacomment">//do it once in your application prior to using the library</span>
 *     <span class="javacomment">//enables working with a repository via the DAV-protocol (over http and https)</span>
 *     DAVRepositoryFactory.setup();
 * ...</pre>
 * <p>
 * <b>NOTE:</b> unfortunately, at present the JavaSVN library doesn't 
 * provide an implementation for accessing a Subversion repository via the
 * <i>file:///</i> protocol (on a local machine), but in future it will be
 * certainly realized.
 * 
 * @version 1.0
 * @author 	TMate Software Ltd.
 * @see		SVNRepository
 */
public abstract class SVNRepositoryFactory {
    
    private static final Map myFactoriesMap = new HashMap();
    
    protected static void registerRepositoryFactory(String protocol, SVNRepositoryFactory factory) {
        if (protocol != null && factory != null) {
            if (!myFactoriesMap.containsKey(protocol)) {
                myFactoriesMap.put(protocol, factory);
            }
        }
    }
    
    protected static boolean hasRepositoryFactory(String protocol) {
        if (protocol != null) {
            return myFactoriesMap.get(protocol) != null;
        }
        return false;
    }
    
    /**
     * Creates an <code>SVNRepository</code> according to the protocol that is to be 
     * used to access a repository.
     * 
     * <p>
     * The protocol is defined as the beginning part of the URL schema. Currently
     * JavaSVN supports only <i>svn://</i> (<i>svn+ssh://</i>) and <i>http://</i> (<i>https://</i>)
     * schemas.
     * 
     * <p>
     * JavaSVN provides an ability to keep the socket connection (so called 'session mode') opened 
     * since it was once opened during the whole runtime. When using this session mode
     * JavaSVN methods do run faster than the same methods run with the session mode switched off, since
     * time is not wasted on opening and closing socket connections for every operation
     * of an <b>SVNRepository</b> driver that accesses a repository. However this method
     * does not provide this enhancement. An <b>SVNRepository</b> driver created by this
     * method always closes a socket connection when an operation finishes. To use a session
     * mode, refer to this {@link #create(SVNURL, boolean) create()} method instead.  
     *     
     * @param  url				a repository location URL  
     * @return					a protocol specific <b>SVNRepository</b> driver
     * @throws SVNException		if there's no implementation for the specified protocol
     * 							(the user may have forgotten to register a specific 
     * 							factory that creates <b>SVNRepository</b>
     * 							instances for that protocol or the JavaSVN 
     * 							library does not support that protocol at all)
     * @see 					SVNRepository
     */
    public static SVNRepository create(SVNURL url) throws SVNException {
        return create(url, null);
        
    }
    
    /**
     * Creates an <code>SVNRepository</code> according to the protocol that is to be 
     * used to access a repository.
     * 
     * <p>
     * The protocol is defined as the beginning part of the URL schema. Currently
     * JavaSVN supports only <i>svn://</i> (<i>svn+ssh://</i>) and <i>http://</i> (<i>https://</i>)
     * schemas.
     * 
     * <p>
     * JavaSVN provides an ability to keep the socket connection (so called 'session mode') opened 
     * since it was once opened during the whole runtime. When using this session mode
     * JavaSVN methods do run faster than the same methods run with the session mode switched off, since
     * time is not wasted on opening and closing socket connections for every operation
     * of an <b>SVNRepository</b> driver that accesses a repository. The session mode is enabled/disabled
     * by the <code>sessionMode</code> parameter.  
     * 
     * @param  url              a repository location URL  
     * @param  sessionMode      if <span class="javakeyword">true</span> then the
     *                          session mode is enabled (socket connection is kept opened), 
     *                          otherwise disabled
     * @return                  a protocol specific <b>SVNRepository</b> driver
     * @throws SVNException     if there's no implementation for the specified protocol
     *                          (the user may have forgotten to register a specific 
     *                          factory that creates <b>SVNRepository</b>
     *                          instances for that protocol or the JavaSVN 
     *                          library does not support that protocol at all)
     * @see                     #create(SVNURL)
     * @see                     SVNRepository
     * 
     */
    public static SVNRepository create(SVNURL url, ISVNSession options) throws SVNException {
        String urlString = url.toString();
    	for(Iterator keys = myFactoriesMap.keySet().iterator(); keys.hasNext();) {
    		String key = (String) keys.next();
    		if (Pattern.matches(key, urlString)) {
    			return ((SVNRepositoryFactory) myFactoriesMap.get(key)).createRepositoryImpl(url, options);
    		}
    	}
    	SVNErrorManager.error("svn: Unable to open an ra_local session to URL '" + url + "'\nsvn: No connection protocol implementation for " + url.getProtocol());
        return null;
    }

    protected abstract SVNRepository createRepositoryImpl(SVNURL url, ISVNSession session);

}
