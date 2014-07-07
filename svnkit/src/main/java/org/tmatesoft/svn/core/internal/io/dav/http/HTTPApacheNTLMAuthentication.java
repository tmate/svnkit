package org.tmatesoft.svn.core.internal.io.dav.http;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.util.SVNLogType;

import java.io.IOException;
import java.util.Locale;

public class HTTPApacheNTLMAuthentication extends HTTPNTLMAuthentication {

    public static final String JCIFS_ENGINE = "JCIFS";
    public static final String APACHE_ENGINE = "APACHE";

    private final INTLMEngine myEngine;
    private String myLastToken = null;

    protected HTTPApacheNTLMAuthentication(String charset, String engine) {
        super(charset);
        if (JCIFS_ENGINE.equals(engine) && !NTLMJCIFSEngine.isAvailable()) {
            engine = APACHE_ENGINE;
        }
        if (JCIFS_ENGINE.equals(engine)) {
            myEngine = new NTLMJCIFSEngine();
        } else {
            myEngine = new NTLMEngine();
        }
    }

    public static HTTPNTLMAuthentication newInstance(String charset, String engine) {
        return new HTTPApacheNTLMAuthentication(charset, engine);
    }

    public String authenticate() throws SVNException {
        if (myState != TYPE1 && myState != TYPE3) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_DAV_REQUEST_FAILED,
                    "Unsupported message type in HTTP NTLM authentication");
            SVNErrorManager.error(err, SVNLogType.NETWORK);
        }

        String response = null;
        String domain = getDomain() == null ? "" : getDomain().toUpperCase(Locale.ENGLISH);
        final String domainOverride = System.getProperty("svnkit.http.ntlm.domain");
        if (domainOverride != null) {
            domain = domainOverride.toUpperCase(Locale.ENGLISH);
        }
        String ws = "";
        final String wsOverride = System.getProperty("svnkit.http.ntlm.workstation");
        if (wsOverride != null) {
            ws = wsOverride.toUpperCase(Locale.ENGLISH);
        }
        final String userOverride = System.getProperty("svnkit.http.ntlm.user");
        final String passwordOverride = System.getProperty("svnkit.http.ntlm.password");
        final String userName = userOverride != null ? userOverride : getUserName();
        final String password = passwordOverride != null ? passwordOverride : getPassword();

        try {
            if (myState == TYPE1) {
                response = myEngine.generateType1Msg(domain, ws);
            } else if (myState == TYPE3) {
                response = myEngine.generateType3Msg(userName, password, domain, ws, myLastToken);
            }
        } catch (IOException e) {
            throw new SVNException(SVNErrorMessage.create(SVNErrorCode.RA_DAV_REQUEST_FAILED), e);
        }

        if (isInType3State()) {
            setType1State();
            if (myLastToken != null) {
                myLastToken = null;
            }
        }
        return "NTLM " + response;
    }

    public void parseChallenge(String challenge) throws SVNException {
        // store incoming challenge.
        myLastToken = challenge;
    }

    public boolean isNative() {
        return true;
    }

    @Override
    public boolean allowPropmtForCredentials() {
        return true;
    }

}
