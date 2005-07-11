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

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.UsernamePasswordCredentials;
import org.apache.commons.httpclient.auth.AuthScope;
import org.tmatesoft.svn.core.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.SVNAuthentication;
import org.tmatesoft.svn.core.SVNCancelException;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNRepositoryLocation;
import org.tmatesoft.svn.core.io.SVNAuthenticationException;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.util.DebugLog;
import org.tmatesoft.svn.util.LoggingInputStream;
import org.tmatesoft.svn.util.PathUtil;
import org.xml.sax.SAXException;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.FactoryConfigurationError;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * @author TMate Software Ltd.
 *
 */
class HttpConnection {

    private SVNRepositoryLocation mySVNRepositoryLocation;
    private SAXParser mySAXParser;

    private static SAXParserFactory ourSAXParserFactory;

    private Map myCredentialsChallenge;
    private ISVNAuthenticationManager myAuthManager;
    private SVNAuthentication myLastValidAuth;
    private SVNAuthentication myProxyAuth;
    
    private HttpClient myClient;
    private BasicHttpMethod myHttpMethod;

    public HttpConnection(SVNRepositoryLocation location, SVNRepository repos) {
        mySVNRepositoryLocation = location;
        myAuthManager = repos.getAuthenticationManager();
    }

    public void connect() {
        close();
        String host = mySVNRepositoryLocation.getHost();
        int port = mySVNRepositoryLocation.getPort();
        String realm = host + ":" + port;
        myProxyAuth = myAuthManager != null ? myAuthManager.getFirstAuthentication(ISVNAuthenticationManager.PROXY, realm) : null;
        myClient = new HttpClient();
        myClient.getHostConfiguration().setHost(host, port);
        if (myProxyAuth != null && myProxyAuth.getProxyHost() != null) {
            if (isSecured()) {
                if (myProxyAuth.getUserName() != null && myProxyAuth.getProxyPassword() != null) {
                    myClient.getState().setProxyCredentials(new AuthScope(myProxyAuth.getProxyHost(), myProxyAuth.getProxyPort()),
                            new UsernamePasswordCredentials(myProxyAuth.getUserName(), myProxyAuth.getProxyPassword()));
                }else{
                    myClient.getState().setProxyCredentials(new AuthScope(myProxyAuth.getProxyHost(), myProxyAuth.getProxyPort()),
                            null);
                }
            }
        }
    }

    private boolean isSecured() {
        return "https".equals(mySVNRepositoryLocation.getProtocol());
    }

    public void close() {
        if(myHttpMethod != null){
            myHttpMethod.releaseConnection();
            myHttpMethod = null;
        }
    }

    public DAVStatus request(String method, String path, Map header, InputStream body, DefaultHandler handler, int[] okCodes) throws SVNException {
        DAVStatus status = sendRequest(method, path, initHeader(0, null, header), body);
        // check okCodes, read to status if not ok.
        assertOk(path, status, okCodes);
        if (status != null && status.getResponseCode() == 204) {
            finishResponse(status.getResponseHeader());
        } else if (status != null) {
            readResponse(handler, status.getResponseHeader());
        }
        return status;
    }

    public DAVStatus request(String method, String path, Map header, StringBuffer reqBody, DefaultHandler handler, int[] okCodes) throws SVNException {
        DAVStatus status = sendRequest(method, path, initHeader(0, null, header), reqBody, okCodes);
        if (status != null && status.getResponseCode() == 204) {
            finishResponse(status.getResponseHeader());
        } else if (status != null) {
            readResponse(handler, status.getResponseHeader());
        }
        return status;
    }

    public DAVStatus request(String method, String path, int depth, String label, StringBuffer requestBody, OutputStream result, int[] okCodes) throws SVNException {
        DAVStatus status = sendRequest(method, path, initHeader(depth, label, null), requestBody, okCodes);
        if (status != null && status.getResponseCode() == 204) {
            finishResponse(status.getResponseHeader());
        } else if (status != null) {
            readResponse(result, status.getResponseHeader());
        }
        return status;
    }

    public DAVStatus request(String method, String path, int depth, String label, StringBuffer requestBody, DefaultHandler handler, int[] okCodes)
            throws SVNException {
        DAVStatus status = sendRequest(method, path, initHeader(depth, label, null), requestBody, okCodes);
        if (status != null && status.getResponseCode() == 204) {
            finishResponse(status.getResponseHeader());
        } else if (status != null) {
            readResponse(handler, status.getResponseHeader());
        }
        return status;
    }

    private DAVStatus sendRequest(String method, String path, Map header, StringBuffer requestBody, int[] okCodes) throws SVNException {
        byte[] request = null;
        if (requestBody != null) {
            try {
                request = requestBody.toString().getBytes("UTF-8");
            } catch (UnsupportedEncodingException e) {
                request = requestBody.toString().getBytes();
            }
        }
        DAVStatus status = sendRequest(method, path, header, request != null ? new ByteArrayInputStream(request) : null);
        assertOk(path, status, okCodes);
        return status;
    }
    
    private DAVStatus sendRequest(String method, String path, Map header, InputStream requestBody) throws SVNException {
        Map readHeader = new HashMap();
        path = PathUtil.removeTrailingSlash(path);
        if (myCredentialsChallenge != null) {
            myCredentialsChallenge.put("methodname", method);
            myCredentialsChallenge.put("uri", path);
        }
        String realm = null;
        SVNAuthentication auth = myLastValidAuth;
        while (true) {
            DAVStatus status;
            try {
                connect();
                myHttpMethod = new BasicHttpMethod(method, path, requestBody);
                for (Iterator iter = header.keySet().iterator(); iter.hasNext();) {
                    String name = (String) iter.next();
                    String value = (String)header.get(name);
                    myHttpMethod.addRequestHeader(name, value);
                }
                if(auth != null){
                    myClient.getState().setCredentials(new AuthScope(AuthScope.ANY_HOST, AuthScope.ANY_PORT,
                            auth.getRealm()), new UsernamePasswordCredentials(
                            auth.getUserName(), auth.getPassword()));
                    myHttpMethod.setDoAuthentication(true);
                }
                myClient.executeMethod(myHttpMethod);
                status = readHeader();
            } catch (IOException e) {
                close();
                acknowledgeSSLContext(false);
                throw new SVNException(e);
            }
            acknowledgeSSLContext(true);
            if (status != null
                    && (status.getResponseCode() == HttpURLConnection.HTTP_UNAUTHORIZED || status.getResponseCode() == HttpURLConnection.HTTP_FORBIDDEN)) {
                myLastValidAuth = null;
                close();
                myCredentialsChallenge = DAVUtil.parseAuthParameters((String) readHeader.get("WWW-Authenticate"));
                if (myCredentialsChallenge == null) {
                    throw new SVNAuthenticationException("Authentication challenge is not supported:\n" + readHeader.get("WWW-Authenticate"));
                }
                myCredentialsChallenge.put("methodname", method);
                myCredentialsChallenge.put("uri", path);
                realm = (String) myCredentialsChallenge.get("realm");
                realm = realm == null ? "" : " " + realm;
                realm = "<" + mySVNRepositoryLocation.getProtocol() + "://" + mySVNRepositoryLocation.getHost() + ":" + mySVNRepositoryLocation.getPort() + ">" + realm;
                if (myAuthManager == null) {
                    throw new SVNAuthenticationException("No credentials defined");
                }
                if (auth == null) {
                    auth = myAuthManager.getFirstAuthentication(ISVNAuthenticationManager.PASSWORD, realm);
                } else {
                    auth = myAuthManager.getNextAuthentication(ISVNAuthenticationManager.PASSWORD, realm);
                }
                if (auth == null) {
                    throw new SVNCancelException();
                }
                // reset stream!
                if (requestBody instanceof ByteArrayInputStream) {
                    try {
                        requestBody.reset();
                    } catch (IOException e) {
                        //
                    }
                } else if (requestBody != null) {
                    throw new SVNAuthenticationException("Authentication failed");
                }
            } else if (status != null &&
                    (status.getResponseCode() == HttpURLConnection.HTTP_MOVED_PERM || status.getResponseCode() == HttpURLConnection.HTTP_MOVED_TEMP)) {
                close();
                // reconnect
                String newLocation = (String) readHeader.get("Location");
                if (newLocation == null) {
                    throw new SVNException("can't connect: " + status.getMessage());
                }
                int hostIndex = newLocation.indexOf("://");
                if (hostIndex > 0) {
                    hostIndex += 3;
                    hostIndex = newLocation.indexOf("/", hostIndex);
                }
                if (hostIndex > 0 && hostIndex < newLocation.length()) {
                    String newPath = newLocation.substring(hostIndex);
                    if (newPath.endsWith("/") &&
                            !newPath.endsWith("//") && !path.endsWith("/") &&
                            newPath.substring(0, newPath.length() - 1).equals(path)) {
                        path += "//";
                        continue;
                    }
                }
                throw new SVNException("HTTP 301 MOVED PERMANENTLY: " + newLocation);
            } else if (status != null) {
                if (auth != null && myAuthManager != null && realm != null) {
                    myAuthManager.addAuthentication(realm, auth, myAuthManager.isAuthStorageEnabled());
                }
                // remember creds
                myLastValidAuth = auth;
                status.setResponseHeader(readHeader);
                return status;
            } else if (auth != null) {
                close();
                throw new SVNException("can't connect");
            } else {
                close();
                throw new SVNCancelException();
            }
        }
    }

    private void readError(String url, DAVStatus status) {
        close();
        if (status.getResponseCode() == 404) {
            status.setErrorText("svn: '" + url + "' path not found");
        } else {
            status.setErrorText(myHttpMethod.getStatusText());
        }
    }

    private void readResponse(OutputStream result, Map responseHeader) throws SVNException {
        LoggingInputStream stream = null;
        try {
            stream = createInputStream(responseHeader, getInputStream());
            byte[] buffer = new byte[1024];
            while (true) {
                int count = stream.read(buffer);
                if (count <= 0) {
                    break;
                }
                if (result != null) {
                    result.write(buffer, 0, count);
                }
            }
        } catch (IOException e) {
            throw new SVNException(e);
        } finally {
            logInputStream(stream);
            finishResponse(responseHeader);
        }
    }
    
    private InputStream getInputStream() throws IOException{
        if(myHttpMethod == null){
            return null;
        }
        return myHttpMethod.getResponseBodyAsStream();
    }

    private void readResponse(DefaultHandler handler, Map responseHeader) throws SVNException {
        LoggingInputStream is = null;
        try {
            is = createInputStream(responseHeader, myHttpMethod.getResponseBodyAsStream());
            XMLInputStream xmlIs = new XMLInputStream(is);

            if (handler == null) {
                while (true) {
                    int r = is.read();
                    if (r < 0) {
                        break;
                    }
                }
            } else {
                if (mySAXParser == null) {
                    mySAXParser = getSAXParserFactory().newSAXParser();
                }
                while (!xmlIs.isClosed()) {
                    mySAXParser.parse(xmlIs, handler);
                }
            }
        } catch (SAXException e) {
            if (e instanceof SAXParseException) {
                return;
            }
            if (e.getCause() instanceof SVNException) {
                throw (SVNException) e.getCause();
            }
            throw new SVNException(e);
        } catch (ParserConfigurationException e) {
            throw new SVNException(e);
        } catch (EOFException e) {
            // skip it.
        } catch (IOException e) {
            throw new SVNException(e);
        } finally {
            logInputStream(is);
            finishResponse(responseHeader);
        }
    }

    private DAVStatus readHeader() throws IOException{
        if(myHttpMethod == null){
            return null;
        }
        return DAVStatus.parse(myHttpMethod.getResponseBodyAsString());
    }

    private static LoggingInputStream createInputStream(Map readHeader, InputStream is) throws IOException {
        if (readHeader.get("Content-Length") != null) {
            is = new FixedSizeInputStream(is, Long.parseLong(readHeader.get("Content-Length").toString()));
        } else if ("chunked".equals(readHeader.get("Transfer-Encoding"))) {
            is = new ChunkedInputStream(is);
        }
        if ("gzip".equals(readHeader.get("Content-Encoding"))) {
            DebugLog.log("using GZIP to decode server responce");
            is = new GZIPInputStream(is);
        }
        return DebugLog.getLoggingInputStream("http", is);
    }


    private static synchronized SAXParserFactory getSAXParserFactory() throws FactoryConfigurationError, ParserConfigurationException,
            SAXNotRecognizedException, SAXNotSupportedException {
        if (ourSAXParserFactory == null) {
            ourSAXParserFactory = SAXParserFactory.newInstance();
            ourSAXParserFactory.setFeature("http://xml.org/sax/features/namespaces", true);
            ourSAXParserFactory.setNamespaceAware(true);
            ourSAXParserFactory.setValidating(false);
        }
        return ourSAXParserFactory;
    }

    private void assertOk(String url, DAVStatus status, int[] codes) throws SVNException {
        int code = status.getResponseCode();
        if (codes == null) {
            // check that all are > 200.
            if (code >= 200 && code < 300) {
                return;
            }
            readError(url, status);
            throw new SVNException(status.getErrorText());
        }
        for (int i = 0; i < codes.length; i++) {
            if (code == codes[i]) {
                return;
            }
        }
        readError(url, status);
        throw new SVNException(status.getErrorText());
    }

    private static Map initHeader(int depth, String label, Map map) {
        map = map == null ? new HashMap() : map;
        if (label != null && !map.containsKey("Label")) {
            map.put("Label", label);
        }
        if (!map.containsKey("Depth")) {
            if (depth == 1 || depth == 0) {
                map.put("Depth", Integer.toString(depth));
            } else {
                map.put("Depth", "infinity");
            }
        }
        return map;
    }

    private void acknowledgeSSLContext(boolean accepted) {
        if (mySVNRepositoryLocation == null || !"https".equalsIgnoreCase(mySVNRepositoryLocation.getProtocol())) {
            return;
        }

        String host = mySVNRepositoryLocation.getHost();
        int port = mySVNRepositoryLocation.getPort();
        DAVRepositoryFactory.getSSLManager().acknowledgeSSLContext(host, port, accepted);
    }

    private void finishResponse(Map readHeader) {
        if ("close".equals(readHeader.get("Connection")) ||
                "close".equals(readHeader.get("Proxy-Connection"))) {
            DebugLog.log("closing connection due to server request");
            close();
        }
    }

    private void logInputStream(LoggingInputStream is) {
        if (is != null) {
            is.log();
        }
    }

    public SVNAuthentication getLastValidCredentials() {
        return myLastValidAuth;
    }
}