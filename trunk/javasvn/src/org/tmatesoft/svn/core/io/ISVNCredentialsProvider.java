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


/**
 * This interface is a provider of clients' credentials used by the Repository 
 * Access Layer to authenticate them. 
 * 
 * <p>
 * Since a Subversion repository can be configured to demand a client's 
 * authentication to perform his request, the client in this case must provide
 * such information about himself (account name & password).    
 * 
 * <p>
 * This interface implementation is supplied to an <code>SVNRepository</code>
 * instance used as a current session object to communicate with a repository.
 * Later on the Repository Access Layer inner engine retrieves this provider from
 * the <code>SVNRepository</code> and calling the provider's interface methods
 * obtains all the client's credentials (<code>ISVNCredentials</code> 
 * implementations) provided.
 * 
 * @version 1.0
 * @author 	TMate Software Ltd.
 * @see 	ISVNCredentialsProviderEx
 * @see 	ISVNCredentials
 * @see 	ISVNSSHCredentials
 * @see 	SVNRepository#setCredentialsProvider(ISVNCredentialsProvider)
 * @see 	SVNRepository#getCredentialsProvider()
 */
public interface ISVNCredentialsProvider {
	/**
	 * Gets the next
	 * @param realm
	 * @return
	 */
	public ISVNCredentials nextCredentials(String realm);
	
	public void accepted(ISVNCredentials credentials);

	public void notAccepted(ISVNCredentials credentials, String failureReason);
    
    public void reset();
}
