/*
 * ====================================================================
 * Copyright (c) 2004-2006 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://tmate.org/svn/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.wc;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNProperty;

/**
 * 
 * @version 1.1
 * @author  TMate Software Ltd.
 */
public abstract class SVNAdminArea {
    private static final int LATEST_WC_FORMAT = 8;
    private static final int XML_ENTRIES_WC_FORMAT = 6;

    private static final Map ourAreas = new TreeMap();
    static {
        ourAreas.put(new Integer(LATEST_WC_FORMAT), SVNPostXMLEntries.class);
        ourAreas.put(new Integer(XML_ENTRIES_WC_FORMAT), SVNXMLEntries.class);
    }
    
    protected static final Set BOOLEAN_PROPERTIES = new HashSet();
    static {
        BOOLEAN_PROPERTIES.add(SVNProperty.COPIED);
        BOOLEAN_PROPERTIES.add(SVNProperty.DELETED);
        BOOLEAN_PROPERTIES.add(SVNProperty.ABSENT);
        BOOLEAN_PROPERTIES.add(SVNProperty.INCOMPLETE);
    }

    protected Map myData;
    protected Set myEntries;
    private SVNDirectory myParent;
    
    public void open() throws SVNException {
        if (myData != null) {
            return;
        }
        if (!getParent().getEntriesFile().exists()) {
            return;
        }
        myData = new TreeMap();
        myEntries = new TreeSet();
        try {
            fetchEntries();
        } catch (IOException e) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, "Cannot read entries file ''{0}'': {1}", new Object[] {getParent().getEntriesFile(), e.getLocalizedMessage()});
            SVNErrorManager.error(err, e);
        }
    }
    
    protected abstract void fetchEntries() throws IOException, SVNException;
    
    public abstract void close();

    public abstract SVNEntry addEntry(String name);

    public abstract void deleteEntry(String name);
    
    public abstract SVNEntry getEntry(String name, boolean hidden);

    public abstract  void save(boolean close) throws SVNException;

    public abstract boolean setPropertyValue(String name, String propertyName, String propertyValue);

    public abstract Iterator entries(boolean hidden);

    protected abstract Map getEntryMap(String name);

    protected int getFormatNumber() {
        return -1;
    }

    public static SVNAdminArea createAdminArea(SVNDirectory parent) throws SVNException {
        File dir = parent.getRoot(); 
        File adminDir = new File(dir, SVNFileUtil.getAdminDirectoryName());
        File entriesFile = parent.getEntriesFile();
        BufferedReader reader = null;
        String line = null;
        try {
            reader = new BufferedReader(new InputStreamReader(SVNFileUtil.openFileForReading(entriesFile), "UTF-8"));
            line = reader.readLine();
        } catch (IOException e) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, "Cannot read entries file ''{0}'': {1}", new Object[] {entriesFile, e.getLocalizedMessage()});
            SVNErrorManager.error(err, e);
        } finally {
            SVNFileUtil.closeFile(reader);
        }

        boolean readFormatFile = false;
        int formatVersion = -1;
        try {
            formatVersion = Integer.parseInt(line.trim());
        } catch (NumberFormatException e) {
            readFormatFile = true;
        }

        if (readFormatFile) {
            File formatFile = new File(adminDir, "format");
            try {
                reader = new BufferedReader(new InputStreamReader(SVNFileUtil.openFileForReading(formatFile), "UTF-8"));
                line = reader.readLine();
                formatVersion = Integer.parseInt(line.trim());
            } catch (IOException e) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, "Cannot read format file ''{0}'': {1}", new Object[] {formatFile, e.getLocalizedMessage()});
                SVNErrorManager.error(err, e);
            } catch (NumberFormatException nfe) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_NOT_DIRECTORY, "''{0}'' is not a working copy: {1}", new Object[] {dir, nfe.getLocalizedMessage()});
                SVNErrorManager.error(err, nfe);
            } catch (SVNException svne) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_NOT_DIRECTORY, "''{0}'' is not a working copy", dir);
                err.setChildErrorMessage(svne.getErrorMessage());
                SVNErrorManager.error(err, svne);
            } finally {
                SVNFileUtil.closeFile(reader);
            }
        }
        
        parent.checkWCFormat(formatVersion);

        Class areaClass = (Class)ourAreas.get(new Integer(formatVersion));
        Constructor areaConstructor = null;
        SVNAdminArea adminArea = null;
        try {
            areaConstructor = areaClass.getConstructor(new Class[]{File.class});
            adminArea = (SVNAdminArea)areaConstructor.newInstance(new Object[]{adminDir});
        } catch (SecurityException e) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNKNOWN, "Can't instantiate an admin area object for working copy ''{0}'': {1}", new Object[]{dir, e.getLocalizedMessage()});
            SVNErrorManager.error(err);
        } catch (NoSuchMethodException e) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNKNOWN, "Can't instantiate an admin area object for working copy ''{0}'': {1}", new Object[]{dir, e.getLocalizedMessage()});
            SVNErrorManager.error(err);
        } catch (IllegalAccessException e) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNKNOWN, "Can't instantiate an admin area object for working copy ''{0}'': {1}", new Object[]{dir, e.getLocalizedMessage()});
            SVNErrorManager.error(err);
        } catch (InstantiationException e) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNKNOWN, "Can't instantiate an admin area object for working copy ''{0}'': {1}", new Object[]{dir, e.getLocalizedMessage()});
            SVNErrorManager.error(err);
        } catch (InvocationTargetException e) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNKNOWN, "Can't instantiate an admin area object for working copy ''{0}'': {1}", new Object[]{dir, e.getLocalizedMessage()});
            SVNErrorManager.error(err);
        }        
        
        return adminArea;
    }
    
    
    public static boolean supportFormat(int formatVersion){
        return ourAreas.get(new Integer(formatVersion)) != null;
    }
    
    public abstract SVNProperties getProperties(String name, boolean tmp);
    
    public abstract SVNProperties getBaseProperties(String name, boolean tmp);

    public abstract SVNProperties getWCProperties(String name);
    
    public abstract String getPropertyValue(String name, String propertyName);

    protected SVNAdminArea(SVNDirectory parent){
        myParent = parent;
    }

    public static int getLatestFormatVersion() {
        return LATEST_WC_FORMAT;
    }
    
    protected SVNDirectory getParent(){
        return myParent;
    }
}
