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
package org.tmatesoft.svn.core.internal.io.dav.handlers;

import java.util.Map;
import java.util.TreeMap;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNMergeInfo;
import org.tmatesoft.svn.core.SVNMergeInfoInheritance;
import org.tmatesoft.svn.core.internal.io.dav.DAVElement;
import org.tmatesoft.svn.core.internal.util.SVNXMLUtil;
import org.tmatesoft.svn.core.internal.wc.SVNMergeInfoManager;

import org.xml.sax.Attributes;


/**
 * @author TMate Software Ltd.
 * @version 1.1.2
 */
public class DAVMergeInfoHandler extends BasicDAVHandler {

    public static StringBuffer generateMergeInfoRequest(StringBuffer xmlBuffer, long revision, 
            String[] paths, SVNMergeInfoInheritance inherit) {
        xmlBuffer = xmlBuffer == null ? new StringBuffer() : xmlBuffer;
        SVNXMLUtil.addXMLHeader(xmlBuffer);
        SVNXMLUtil.openNamespaceDeclarationTag(SVNXMLUtil.SVN_NAMESPACE_PREFIX, "mergeinfo-report", 
                SVN_NAMESPACES_LIST, SVNXMLUtil.PREFIX_MAP, xmlBuffer);
        SVNXMLUtil.openCDataTag(SVNXMLUtil.SVN_NAMESPACE_PREFIX, "revision", String.valueOf(revision), xmlBuffer);
        SVNXMLUtil.openCDataTag(SVNXMLUtil.SVN_NAMESPACE_PREFIX, "inherit", inherit.toString(), xmlBuffer);
        if (paths != null) {
            for (int i = 0; i < paths.length; i++) {
                SVNXMLUtil.openCDataTag(SVNXMLUtil.SVN_NAMESPACE_PREFIX, "path", paths[i], xmlBuffer);
            }
        }
        SVNXMLUtil.addXMLFooter(SVNXMLUtil.SVN_NAMESPACE_PREFIX, "mergeinfo-report", xmlBuffer);
        return xmlBuffer;
    }

    private String myPath;
    private StringBuffer myCurrentInfo;
    private Map myPathsToMergeInfos;

    public DAVMergeInfoHandler() {
        init();
        myPathsToMergeInfos = new TreeMap();
    }

    public Map getMergeInfo() {
        return myPathsToMergeInfos;
    }

    protected void startElement(DAVElement parent, DAVElement element, Attributes attrs) throws SVNException {
        if (element == DAVElement.MERGE_INFO_ITEM) {
            myPath = null;
            myCurrentInfo = null;
        }
    }

    protected void endElement(DAVElement parent, DAVElement element, StringBuffer cdata) throws SVNException {
        if (element == DAVElement.MERGE_INFO_PATH) {
            myPath = cdata.toString();
        } else if (element == DAVElement.MERGE_INFO_INFO) {
            myCurrentInfo = cdata;
        } else if (element == DAVElement.MERGE_INFO_ITEM) {
            if (myPath != null && myCurrentInfo != null) {
                Map srcPathsToRangeLists = SVNMergeInfoManager.parseMergeInfo(myCurrentInfo, myPathsToMergeInfos);
                myPathsToMergeInfos.put(myPath, new SVNMergeInfo(myPath, srcPathsToRangeLists));
            }
        }
    }

}