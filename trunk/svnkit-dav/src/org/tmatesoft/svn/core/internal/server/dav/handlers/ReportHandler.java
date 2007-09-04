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
package org.tmatesoft.svn.core.internal.server.dav.handlers;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Writer;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.io.dav.DAVElement;
import org.tmatesoft.svn.core.internal.server.dav.DAVResource;
import org.tmatesoft.svn.core.internal.server.dav.DAVXMLUtil;
import org.tmatesoft.svn.core.internal.server.dav.XMLUtil;
import org.tmatesoft.svn.core.internal.util.SVNBase64;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.io.diff.SVNDiffWindow;
import org.xml.sax.Attributes;

/**
 * @author TMate Software Ltd.
 * @version 1.1.2
 */
public abstract class ReportHandler {

    protected static final String UTF_8_ENCODING = "UTF-8";

    private DAVResource myDAVResource;
    private Writer myResponseWriter;

    private boolean myWriteTextDeltaHeader = true;

    public ReportHandler(DAVResource resource, Writer responseWriter) {
        myDAVResource = resource;
        myResponseWriter = responseWriter;
    }

    public abstract DAVRequest getDAVRequest();

    protected DAVResource getDAVResource() {
        return myDAVResource;
    }

    protected Writer getResponseWriter() {
        return myResponseWriter;
    }

    private boolean isWriteTextDeltaHeader() {
        return myWriteTextDeltaHeader;
    }

    private void setWriteTextDeltaHeader(boolean writeTextDeltaHeader) {
        myWriteTextDeltaHeader = writeTextDeltaHeader;
    }

    protected void startElement(DAVElement parent, DAVElement element, Attributes attrs) throws SVNException {
        getDAVRequest().startElement(parent, element, attrs);
    }

    protected void endElement(DAVElement parent, DAVElement element, StringBuffer cdata) throws SVNException {
        getDAVRequest().endElement(parent, element, cdata);
    }

    public abstract void sendResponse() throws SVNException;

    public abstract int getContentLength() throws SVNException;

    protected void write(String string) throws SVNException {
        try {
            getResponseWriter().write(string);
        } catch (IOException e) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_DAV_REQUEST_FAILED, e), e);
        }
    }

    protected void write(StringBuffer stringBuffer) throws SVNException {
        write(stringBuffer.toString());
    }

    protected void writeXMLHeader() throws SVNException {
        StringBuffer xmlBuffer = new StringBuffer();
        addXMLHeader(xmlBuffer);
        write(xmlBuffer);
    }

    protected void writeXMLFooter() throws SVNException {
        StringBuffer xmlBuffer = new StringBuffer();
        addXMLFooter(xmlBuffer);
        write(xmlBuffer);
    }

    protected void addXMLHeader(StringBuffer xmlBuffer) {
        XMLUtil.addXMLHeader(xmlBuffer);
        DAVXMLUtil.openNamespaceDeclarationTag(DAVXMLUtil.SVN_NAMESPACE_PREFIX, getDAVRequest().getRootElement().getName(), getDAVRequest().getElements(), xmlBuffer);
    }

    protected void addXMLFooter(StringBuffer xmlBuffer) {
        XMLUtil.closeXMLTag(DAVXMLUtil.SVN_NAMESPACE_PREFIX, getDAVRequest().getRootElement().getName(), xmlBuffer);
    }

    protected void writeTextDeltaChunk(SVNDiffWindow diffWindow, boolean diffVersion) throws SVNException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            diffWindow.writeTo(baos, isWriteTextDeltaHeader(), diffVersion);
            byte[] textDelta = baos.toByteArray();
            String txDelta = SVNBase64.byteArrayToBase64(textDelta);
            write(txDelta);
            setWriteTextDeltaHeader(false);
        } catch (IOException e) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_DAV_REQUEST_FAILED, e), e);
        }
    }
}
