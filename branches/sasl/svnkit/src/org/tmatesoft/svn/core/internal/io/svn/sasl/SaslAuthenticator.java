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
package org.tmatesoft.svn.core.internal.io.svn.sasl;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.sasl.RealmCallback;
import javax.security.sasl.Sasl;
import javax.security.sasl.SaslClient;
import javax.security.sasl.SaslException;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.internal.io.svn.SVNAuthenticator;
import org.tmatesoft.svn.core.internal.io.svn.SVNConnection;
import org.tmatesoft.svn.core.internal.io.svn.SVNRepositoryImpl;
import org.tmatesoft.svn.core.internal.util.SVNBase64;
import org.tmatesoft.svn.core.internal.util.SVNHashMap;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;


/**
 * @version 1.1.2
 * @author  TMate Software Ltd.
 */
public class SaslAuthenticator extends SVNAuthenticator {

    protected SaslAuthenticator(SVNConnection connection) throws SVNException {
        super(connection);
    }

    public void authenticate(List mechs, String realm, SVNRepositoryImpl repository) throws SVNException {
        
        // 1. create client for mechs.
        //    (probably always use plain authenticator for ANONYMOUS or EXTERNAL mech if available,
        //     however, for anonymous there still could be encryption!
        //     for now consider we don't have them at this point).
        // 2. if client is ok, send initial response if any.
        // 3. process server challenges until success or failure.
        // 4. set up encryption.
        
        try {
            SaslClient client = createSaslClient(mechs, realm, repository.getLocation(), repository.getAuthenticationManager());
            if (client == null) {
                // TODO throw exception.
                return;
            }
            String mech = client.getMechanismName();
            // send message with mech.
            getConnection().write("(w())", new Object[]{client.getMechanismName()});
            // read response (challenge)
            while(true) {
                List items = getConnection().readTuple("w(?s)", true);
                String status = (String) items.get(0);
                if ("success".equals(status)) {
                    String response = (String) items.get(1);
                    byte[] buffer = new byte[response.length()];
                    int len = SVNBase64.base64ToByteArray(new StringBuffer(response), buffer);
                    for (int i = 0; i < len; i++) {
                        System.out.println("resonse[" + i + "]=" + buffer[i]);
                    }
                    client.evaluateChallenge(new String(buffer, 0, len - 3).getBytes());
                    // replace streams (only if auth-conf or auth-int, was negotiated)!
                    if (!"auth".equals(client.getNegotiatedProperty(Sasl.QOP))) {
                        setOutputStream(new SaslOutputStream(client, 4096, getConnectionOutputStream())); 
                        setInputStream(new SaslInputStream(client, 8192, getConnectionInputStream()));
                    }
                    return;
                } else if ("failure".equals(status)) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_NOT_AUTHORIZED, (String) items.get(1));
                    SVNErrorManager.error(err);
                }
                //  compute response.
                String challengeString = (String) items.get(1);
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                for (int i = 0; i < challengeString.length(); i++) {
                    char ch = challengeString.charAt(i);
                    if (ch != '\n' && ch != '\r') {
                        bos.write((byte) ch & 0xFF);
                    }                    
                }
                byte[] cbytes = new byte[challengeString.length()]; 
                int clength = SVNBase64.base64ToByteArray(new StringBuffer(new String(bos.toByteArray())), cbytes);
                byte[] response = client.evaluateChallenge(new String(cbytes, 0, clength).getBytes());

                String responseString = SVNBase64.byteArrayToBase64(response);
                getConnection().write("s", new Object[] {responseString});
            }
        } catch (SaslException e) {
            // TODO SVNException.
            e.printStackTrace();
        }
    }
    
    protected SaslClient createSaslClient(List mechs, final String realm, SVNURL location, final ISVNAuthenticationManager authManager) throws SVNException {
        Map props = new SVNHashMap();
        props.put(Sasl.POLICY_NOPLAINTEXT, "true");
        props.put(Sasl.QOP, "auth-conf,auth-int,auth");
        props.put(Sasl.MAX_BUFFER, "8192");
        
        String[] mechsArray = (String[]) mechs.toArray(new String[mechs.size()]);
        SaslClient client = null;
        
        try {
            client = Sasl.createSaslClient(mechsArray, null, "svn", location.getHost(), props, new CallbackHandler() {
                public void handle(Callback[] callbacks) throws IOException, UnsupportedCallbackException {
                    for (int i = 0; i < callbacks.length; i++) {
                        Callback callback = callbacks[i];
                        // use auth manager!.
                        if (callback instanceof NameCallback) {
                            ((NameCallback) callback).setName("alex");
                        } else if (callback instanceof PasswordCallback) {
                            ((PasswordCallback) callback).setPassword("alex".toCharArray());
                        } else if (callback instanceof RealmCallback) {
                            ((RealmCallback) callback).setText(realm);
                        } else {
                            throw new UnsupportedCallbackException(callback);
                        }
                    }
                }
            });
        } catch (SaslException e) {
            // TODO throw SVNException.
            e.printStackTrace();
        }
        return client;
    }

}
