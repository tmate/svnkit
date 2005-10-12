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
package org.tmatesoft.svn.core.internal.io.fs;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

import org.tmatesoft.svn.core.io.diff.SVNDiffWindow;


/**
 * @version 1.0
 * @author  TMate Software Ltd.
 */
public class DefaultFSDeltaCollector implements IFSDeltaCollector {
    private LinkedList myDiffWindows = new LinkedList();
    private Map myWindowsData = new HashMap();
 
    public OutputStream insertWindow(SVNDiffWindow window) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        myWindowsData.put(window, baos);
        myDiffWindows.addLast(window);
        return baos;
    }
    
    public InputStream getDeltaDataStorage(SVNDiffWindow window){
        return new ByteArrayInputStream(((ByteArrayOutputStream)myWindowsData.get(window)).toByteArray());
    }
    
    public SVNDiffWindow getLastWindow() {
        return (SVNDiffWindow)myDiffWindows.getLast();
    }

    public void removeWindow(SVNDiffWindow window) {
        myDiffWindows.remove(window);
        myWindowsData.remove(window);
    }

    public int getWindowsCount() {
        return myDiffWindows.size();
    }
}
