/*
 * ====================================================================
 * Copyright (c) 2004-2007 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */

package org.tmatesoft.svn.core.internal.io.svn;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
import org.tmatesoft.svn.core.auth.SVNPasswordAuthentication;
import org.tmatesoft.svn.core.internal.io.svn.sasl.SaslInputStream;
import org.tmatesoft.svn.core.internal.io.svn.sasl.SaslOutputStream;
import org.tmatesoft.svn.core.internal.util.SVNBase64;
import org.tmatesoft.svn.core.internal.util.SVNHashMap;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;

/**
 * @version 1.1.1
 * @author  TMate Software Ltd.
 */
public class SVNConnection {

    private final ISVNConnector myConnector;
    private String myRealm;
    private String myRoot;
    private OutputStream myOutputStream;
    private InputStream myInputStream;
    private SVNRepositoryImpl myRepository;
    private boolean myIsSVNDiff1;
    private boolean myIsCommitRevprops;
    private boolean myIsReopening = false;
    private boolean myIsCredentialsReceived = false;
    private InputStream myLoggingInputStream;
    private Set myCapabilities;
    private byte[] myHandshakeBuffer = new byte[8192];
    
    private static final String SUCCESS = "success";
    private static final String FAILURE = "failure";
    private static final String STEP = "step";
    private static final String EDIT_PIPELINE = "edit-pipeline";
    private static final String SVNDIFF1 = "svndiff1";
    private static final String ABSENT_ENTRIES = "absent-entries";
    private static final String COMMIT_REVPROPS = "commit-revprops";
    private static final String MERGE_INFO = "mergeinfo";
    private static final String DEPTH = "depth";
    private static final String LOG_REVPROPS = "log-revprops";
//    private static final String PARTIAL_REPLAY = "partial-replay";

    public SVNConnection(ISVNConnector connector, SVNRepositoryImpl repository) {
        myConnector = connector;
        myRepository = repository;
    }

    public void open(SVNRepositoryImpl repository) throws SVNException {
        myIsReopening = true;
        try {
            myIsCredentialsReceived = false;
            myConnector.open(repository);
            myRepository = repository;
            handshake(repository);
        } finally {
            myIsReopening = false;
        }
    }

    public String getRealm() {
        return myRealm;
    }
    
    public boolean isSVNDiff1() {
        return myIsSVNDiff1;
    }

    public boolean isCommitRevprops() {
        return myIsCommitRevprops;
    }
    
    private InputStream skipLeadingGrabage() throws SVNException {
        byte[] bytes = myHandshakeBuffer;
        int r = 0;
        try {
            r = getInputStream().read(bytes);
        } catch (IOException e) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_SVN_MALFORMED_DATA, "Handshake failed: ''{0}''", e.getMessage());
            SVNErrorManager.error(err);
        }
        if (r >= 0) {
            for (int i = 0; i < r; i++) {
                if (bytes[i] == '(' && bytes[i + 1] == ' ') {
                    return new ByteArrayInputStream(bytes, i, r - i);
                }
            }
        }
        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_SVN_MALFORMED_DATA, "Handshake failed, received: ''{0}''", new String(bytes));
        SVNErrorManager.error(err);
        return null;
    }

    protected void handshake(SVNRepositoryImpl repository) throws SVNException {
        checkConnection();
        InputStream is = skipLeadingGrabage();
        List items = null;
        try {
            items = SVNReader.parse(is, "nnll", null);
        } finally {
            myRepository.getDebugLog().flushStream(myLoggingInputStream);
        }
        Long minVer = (Long) items.get(0);
        Long maxVer = (Long) items.get(1);
        if (minVer.longValue() > 2) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_SVN_BAD_VERSION, 
            		"Server requires minimum version {0}", minVer));
        } else if (maxVer.longValue() < 2) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_SVN_BAD_VERSION, 
            		"Server requires maximum version {0}", maxVer));
        }

        List capabilities = (List) items.get(3);
        addCapabilities(capabilities);
        if (!hasCapability(EDIT_PIPELINE)) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_SVN_BAD_VERSION, 
            		"Server does not support edit pipelining"));
        }
        
        
        myIsSVNDiff1 = SVNReader.hasValue(items, 3, SVNDIFF1);
        myIsCommitRevprops = SVNReader.hasValue(items, 3, COMMIT_REVPROPS);

        write("(n(wwwwww)s)", new Object[]{"2", EDIT_PIPELINE, SVNDIFF1, ABSENT_ENTRIES, DEPTH, MERGE_INFO, LOG_REVPROPS, 
                repository.getLocation().toString()});
    }

    protected boolean hasCapability(String capability) {
    	if (myCapabilities != null) {
    		return myCapabilities.contains(capability);
    	}
    	return false;
    }
    
    public void authenticate(SVNRepositoryImpl repository) throws SVNException {
        SVNErrorMessage failureReason = null;
        List items = read("ls", null, true);
        List mechs = SVNReader.getList(items, 0);
        if (mechs == null || mechs.size() == 0) {
            return;
        }
//        SVNAuthenticator authenticator = new SVNPlainAuthenticator(this);
        myRealm = SVNReader.getString(items, 1);
//        authenticator.authenticate(mechs, myRealm, repository);
//        receiveRepositoryCredentials(repository);
//        if (true) {
//            return;
//        }
        if (saslAuth((String[]) mechs.toArray(new String[mechs.size()]))) {
            receiveRepositoryCredentials(repository);
            return;
        }
        
        
        ISVNAuthenticationManager authManager = myRepository.getAuthenticationManager();
        if (authManager != null && authManager.isAuthenticationForced() && mechs.contains("ANONYMOUS") && mechs.contains("CRAM-MD5")) {
            mechs.remove("ANONYMOUS");
        }
        SVNURL location = myRepository.getLocation();
        SVNPasswordAuthentication auth = null;
        if (repository.getExternalUserName() != null && mechs.contains("EXTERNAL")) {
            write("(w(s))", new Object[]{"EXTERNAL", repository.getExternalUserName()});
            failureReason = readAuthResponse();
        } else if (mechs.contains("ANONYMOUS")) {
            write("(w())", new Object[]{"ANONYMOUS"});
            failureReason = readAuthResponse();
        } else if (mechs.contains("CRAM-MD5")) {
            while (true) {
                CramMD5 cramMD5 = new CramMD5();
                String realm = getRealm();
                if (location != null) {
                    realm = "<" + location.getProtocol() + "://"
                            + location.getHost() + ":"
                            + location.getPort() + "> " + realm;
                }
                if (auth == null && authManager != null) {
                    auth = (SVNPasswordAuthentication) authManager.getFirstAuthentication(ISVNAuthenticationManager.PASSWORD, realm, location);
                } else if (authManager != null) {
                    authManager.acknowledgeAuthentication(false, ISVNAuthenticationManager.PASSWORD, realm, failureReason, auth);
                    auth = (SVNPasswordAuthentication) authManager.getNextAuthentication(ISVNAuthenticationManager.PASSWORD, realm, location);
                }
                if (auth == null || auth.getUserName() == null || auth.getPassword() == null) {
                    failureReason = SVNErrorMessage.create(SVNErrorCode.RA_NOT_AUTHORIZED, "Can't get password. Authentication is required for ''{0}''", realm);
                    break;
                }
                write("(w())", new Object[]{"CRAM-MD5"});
                while (true) {
                    cramMD5.setUserCredentials(auth);
                    items = readTuple("w(?s)", true);
                    String status = SVNReader.getString(items, 0);
                    if (SUCCESS.equals(status)) {
                        authManager.acknowledgeAuthentication(true, ISVNAuthenticationManager.PASSWORD, realm, null, auth);
                        receiveRepositoryCredentials(repository);
                        return;
                    } else if (FAILURE.equals(status)) {
                        failureReason = SVNErrorMessage.create(SVNErrorCode.RA_NOT_AUTHORIZED, "Authentication error from server: {0}", SVNReader.getString(items, 1));
                        break;
                    } else if (STEP.equals(status)) {
                        try {
                            byte[] response = cramMD5.buildChallengeResponse(SVNReader.getBytes(items, 1));
                            getOutputStream().write(response);
                            getOutputStream().flush();
                        } catch (IOException e) {
                            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_SVN_IO_ERROR, e.getMessage()), e);
                        }
                    }
                }
            }
        } else {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_NOT_AUTHORIZED, "Cannot negotiate authentication mechanism"));
        }
        if (failureReason == null) {
            receiveRepositoryCredentials(repository);
            return;
        }
        SVNErrorManager.error(failureReason);
    }
    
    private boolean saslAuth(String[] mechs) throws SVNException {
        Map props = new SVNHashMap();
        props.put(Sasl.POLICY_NOPLAINTEXT, "true");
        props.put(Sasl.QOP, "auth-conf,auth-int,auth");
        props.put(Sasl.MAX_BUFFER, "8192");
        try {
            for (int i = 0; i < mechs.length; i++) {
                System.out.println("mech: " + mechs[i]);
            }
            final SaslClient client = Sasl.createSaslClient(mechs, null, "svn", myRepository.getLocation().getHost(), props, new CallbackHandler() {
                public void handle(Callback[] callbacks) throws IOException, UnsupportedCallbackException {
                    for (int i = 0; i < callbacks.length; i++) {
                        Callback callback = callbacks[i];
                        System.out.println("callback: " + callback);
                        if (callback instanceof NameCallback) {
                            ((NameCallback) callback).setName("alex");
                        } else if (callback instanceof PasswordCallback) {
                            ((PasswordCallback) callback).setPassword("alex".toCharArray());
                        } else if (callback instanceof RealmCallback) {
                            ((RealmCallback) callback).setText(myRealm);
                        } else {
                            throw new UnsupportedCallbackException(callback);
                        }
                    }
                }
            });
            System.out.println("props: " + props);
            if (client == null) {
                return false;
            }
            String mech = client.getMechanismName();
            System.out.println("initial response: " + client.hasInitialResponse());
            // send message with mech.
            write("(w())", new Object[]{client.getMechanismName()});
            // read response (challenge)
            while(true) {
                List items = readTuple("w(?s)", true);
                String status = (String) items.get(0);
                if ("success".equals(status)) {
                    System.out.println("success");
                    String response = (String) items.get(1);
                    byte[] buffer = new byte[response.length()];
                    System.out.println("response: " + response);
                    int len = SVNBase64.base64ToByteArray(new StringBuffer(response), buffer);
                    System.out.println(new String(buffer, 0, len));
                    for (int i = 0; i < len; i++) {
                        System.out.println("resonse[" + i + "]=" + buffer[i]);
                    }
                    System.out.println("complete: " + client.isComplete());
                    client.evaluateChallenge(new String(buffer, 0, len - 3).getBytes());
                    System.out.println("complete: " + client.isComplete());
                    System.out.println("qop: " + client.getNegotiatedProperty(Sasl.QOP));
                    System.out.println("bufsize: " + client.getNegotiatedProperty(Sasl.MAX_BUFFER));
                    // replace streams (only if auth-conf or auth-int, was negotiated)!
                    myOutputStream = new SaslOutputStream(client, 4096, myOutputStream); 
                    myInputStream = new SaslInputStream(client, 8192, myInputStream);
                    return true;
                } else if ("failure".equals(status)) {
                    System.out.println("failure");
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
                System.out.println("challenge: " + new String(cbytes, 0, clength));
                byte[] response = client.evaluateChallenge(new String(cbytes, 0, clength).getBytes());
//                byte[] response = client.evaluateChallenge(challengeString.getBytes());
//                System.out.println("bufsize: " + client.getNegotiatedProperty(Sasl.MAX_BUFFER));
                String responseString = SVNBase64.byteArrayToBase64(response);
                write("s", new Object[] {responseString});
            }
        } catch (SaslException e) {
            e.printStackTrace();
            return false;
        }
//        return false;        
    }

    private void addCapabilities(List capabilities) throws SVNException {
        if (myCapabilities == null) {
            myCapabilities = new HashSet();
        }
        if (capabilities == null || capabilities.isEmpty()) {
            return;
        }
        for (Iterator caps = capabilities.iterator(); caps.hasNext();) {
            SVNItem item = (SVNItem) caps.next();
            if (item.getKind() != SVNItem.WORD) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_SVN_MALFORMED_DATA, 
                        "Capability entry is not a word"); 
                SVNErrorManager.error(err);
            }
            myCapabilities.add(item.getWord());
        }
    }
    
    private void receiveRepositoryCredentials(SVNRepositoryImpl repository) throws SVNException {
        if (myIsCredentialsReceived) {
            return;
        }
        List creds = read("s?s?l", null, true);
        System.out.println("READ: " + creds);
        myIsCredentialsReceived = true;
        if (creds != null && creds.size() >= 2 && creds.get(0) != null && creds.get(1) != null) {
            SVNURL rootURL = creds.get(1) != null ? SVNURL.parseURIEncoded(SVNReader.getString(creds, 1)) : null;
            if (rootURL != null && rootURL.toString().length() > repository.getLocation().toString().length()) {
                SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_SVN_MALFORMED_DATA, "Impossibly long repository root from server"));
            }
            if (repository != null && repository.getRepositoryRoot(false) == null) {
                repository.updateCredentials(SVNReader.getString(creds, 0), rootURL);
            }
            if (myRealm == null) {
                myRealm = SVNReader.getString(creds, 0);
            }
            if (myRoot == null) {
                myRoot = SVNReader.getString(creds, 1);
            }
            if (creds.size() > 2 && creds.get(2) instanceof List) {
                List capabilities = (List) creds.get(2);
                addCapabilities(capabilities);
            }
        }
    }

    private SVNErrorMessage readAuthResponse() throws SVNException {
        List items = readTuple("w(?s)", true);
        if (SUCCESS.equals(SVNReader.getString(items, 0))) {
            return null;
        } else if (FAILURE.equals(SVNReader.getString(items, 0))) {
            return SVNErrorMessage.create(SVNErrorCode.RA_NOT_AUTHORIZED, "Authentication error from server: {0}", SVNReader.getString(items, 1));
        }
        return SVNErrorMessage.create(SVNErrorCode.RA_NOT_AUTHORIZED, "Unexpected server response to authentication");
    }

    public void close() throws SVNException {
        myInputStream = null;
        myLoggingInputStream = null;
        myOutputStream = null;
        myConnector.close(myRepository);
    }

    public List read(String template, List items, boolean readMalformedData) throws SVNException {
        try {
            checkConnection();
            return SVNReader.parse(getInputStream(), template, items);
        } catch (SVNException e) {
            handleIOError(e, readMalformedData);
            return null;
        } finally {
            myRepository.getDebugLog().flushStream(myLoggingInputStream);
        }
    }

    public List readTuple(String template, boolean readMalformedData) throws SVNException {
        try {
            checkConnection();
            return SVNReader.readTuple(getInputStream(), template);
        } catch (SVNException e) {
            handleIOError(e, readMalformedData);
            return null;
        } finally {
            myRepository.getDebugLog().flushStream(myLoggingInputStream);
        }        
    }

    public SVNItem readItem(boolean readMalformedData) throws SVNException {
        try {
            checkConnection();
            return SVNReader.readItem(getInputStream());
        } catch (SVNException e) {
            handleIOError(e, readMalformedData);
            return null;
        } finally {
            myRepository.getDebugLog().flushStream(myLoggingInputStream);
        }
    }

    private void handleIOError(SVNException e, boolean readMalformedData) throws SVNException {
        if (readMalformedData && e.getErrorMessage().getErrorCode() == SVNErrorCode.RA_SVN_MALFORMED_DATA) {
            byte[] malfored = new byte[1024];
            try {
                getInputStream().read(malfored);
            } catch (IOException e1) {
                //
            }
        }
        throw e;        
    }

    public void writeError(SVNErrorMessage error) throws SVNException {
        Object[] buffer = new Object[]{"failure"};
        write("(w(", buffer);
        for (; error != null; error = error.getChildErrorMessage()) {
            String message = error.getMessage() == null ? "" : error.getMessage();
            buffer = new Object[]{new Long(error.getErrorCode().getCode()), message, "", new Integer(0)};
            write("(nssn)", buffer);
        }
        write(")", null);
    }
    
    public void write(String template, Object[] items) throws SVNException {
        try {
            SVNWriter.write(getOutputStream(), template, items);
        } finally {
            try {
                getOutputStream().flush();
            } catch (IOException e) {
                //
            } catch (SVNException e) {
                //
            }
            myRepository.getDebugLog().flushStream(getOutputStream());
        }
    }
    
    public boolean isConnectionStale() {
        boolean stale = myConnector.isStale();
        System.out.println("STALE: " + stale);
        return stale;
    }

    private void checkConnection() throws SVNException {
        if (!myIsReopening && !myConnector.isConnected(myRepository)) {
            myIsReopening = true;
            try {
                close();
                open(myRepository);
            } finally {
                myIsReopening = false;
            }
        }
    }

    public OutputStream getDeltaStream(final String token) {
        return new OutputStream() {
            Object[] myPrefix = new Object[]{"textdelta-chunk", token};

            public void write(byte b[], int off, int len) throws IOException {
                try {
                    SVNConnection.this.write("(w(s", myPrefix);
                    getOutputStream().write((String.valueOf(len)).getBytes("UTF-8"));
                    getOutputStream().write(':');
                    getOutputStream().write(b, off, len);
                    getOutputStream().write(' ');
                    SVNConnection.this.write("))", null);
                } catch (SVNException e) {
                    throw new IOException(e.getMessage());
                }
            }

            public void write(byte[] b) throws IOException {
                write(b, 0, b.length);
            }

            public void write(int b) throws IOException {
                write(new byte[]{(byte) (b & 0xFF)});
            }
        };
    }

    OutputStream getOutputStream() throws SVNException {
        if (myOutputStream == null) {
            try {
                myOutputStream = myRepository.getDebugLog().createLogStream(myConnector.getOutputStream());
            } catch (IOException e) {
                SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_SVN_IO_ERROR, e.getMessage()), e);
            }
        }
        return myOutputStream;
    }

    InputStream getInputStream() throws SVNException {
        if (myInputStream == null) {
            try {
                myInputStream = myRepository.getDebugLog().createLogStream(new BufferedInputStream(myConnector.getInputStream()));
                myLoggingInputStream = myInputStream;
            } catch (IOException e) {
                SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_SVN_IO_ERROR, e.getMessage()), e);
            }
        }
        return myInputStream;
    }
    
    void setOutputStream(OutputStream os) {
        myOutputStream = os;
    }

    void setInputStream(InputStream is) {
        myInputStream = is;
    }
}