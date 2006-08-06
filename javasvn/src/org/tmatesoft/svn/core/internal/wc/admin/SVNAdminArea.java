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
package org.tmatesoft.svn.core.internal.wc.admin;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc.SVNWCAccess;

/**
 * @version 1.1
 * @author  TMate Software Ltd.
 */
public abstract class SVNAdminArea {
    private static final int LATEST_WC_FORMAT = 8;

    private static final Map ourAreas = new TreeMap();
    static {
        ourAreas.put(new Integer(SVNPostXMLEntries.WC_FORMAT), SVNPostXMLEntries.class);
        ourAreas.put(new Integer(SVNXMLEntries.WC_FORMAT), SVNXMLEntries.class);
    }
    
    //TODO: maybe move it to XML-aware format realization
    protected static final Set BOOLEAN_PROPERTIES = new HashSet();
    static {
        BOOLEAN_PROPERTIES.add(SVNProperty.COPIED);
        BOOLEAN_PROPERTIES.add(SVNProperty.DELETED);
        BOOLEAN_PROPERTIES.add(SVNProperty.ABSENT);
        BOOLEAN_PROPERTIES.add(SVNProperty.INCOMPLETE);
    }

    private File myDirectory;
    protected SVNEntries myEntries;
    private SVNWCAccess myWCAccess;
    private String myPath;
    private File myAdminRoot;
    private File myLockFile;
    protected File myEntriesFile;
    protected Map myBaseProperties;
    protected Map myProperties;
    protected Map myWCProperties;
    
    public SVNEntries getEntries() throws SVNException {
        if (myEntries == null) {
            myEntries = new SVNEntries();
            load();
        }
        return myEntries;
    }

    public File getRoot() {
        return myDirectory;
    }

    public File getAdminDirectory() {
        return myAdminRoot;
    }

    public File getAdminFile(String name) {
        return new File(getAdminDirectory(), name);
    }

    public boolean isLocked() {
        return getLockFile().isFile();
    }

    private File getLockFile() {
        return myLockFile;
    }

    public void load() throws SVNException {
        if (myEntries == null || !myEntriesFile.exists()) {
            return;
        }
        try {
            fetchEntries();
        } catch (IOException e) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, "Cannot read entries file ''{0}'': {1}", new Object[] {myEntriesFile, e.getLocalizedMessage()});
            SVNErrorManager.error(err, e);
        }
    }
    
    protected abstract void fetchEntries() throws IOException, SVNException;

    public abstract String getThisDirName();
    
    public void save() throws SVNException {
        saveEntries(true);
        saveBaseProperties(true);
        saveProperties(true);
        saveWCProperties(true);
    }

    protected void saveEntries(boolean close) throws SVNException {
        if (myEntries != null) {
            Map rootEntry = myEntries.getEntryMap(getThisDirName());
            if (rootEntry == null) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_NOT_FOUND, "No default entry in directory ''{0}''", getRoot());
                SVNErrorManager.error(err);
            }
            
            String reposURL = (String)rootEntry.get(SVNProperty.REPOS);
            String url = (String)rootEntry.get(SVNProperty.URL);
            if (reposURL != null && !SVNPathUtil.isAncestor(reposURL, url)) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNKNOWN, "Entry ''{0}'' has inconsistent repository root and url", getThisDirName());
                SVNErrorManager.error(err);
            }
    
            File tmpFile = new File(getAdminDirectory(), "tmp/entries");
            Writer os = null;
            try {
                String formattedEntries = formatEntries(); 
                os = new OutputStreamWriter(SVNFileUtil.openFileForWriting(tmpFile), "UTF-8");
                os.write(formattedEntries);
            } catch (IOException e) {
                SVNFileUtil.closeFile(os);
                SVNFileUtil.deleteFile(tmpFile);
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, "Cannot wrtie entries file ''{0}'': {1}", new Object[] {myEntriesFile, e.getLocalizedMessage()});
                SVNErrorManager.error(err, e);
            } finally {
                SVNFileUtil.closeFile(os);
            }
            
            SVNFileUtil.rename(tmpFile, myEntriesFile);
            SVNFileUtil.setReadonly(myEntriesFile, true);
            if (close) {
                myEntries.close();
                myEntries = null;
            }
        }
    }
    
    protected abstract void saveProperties(boolean close) throws SVNException;
    
    protected abstract void saveBaseProperties(boolean close) throws SVNException;
    
    protected abstract void saveWCProperties(boolean close) throws SVNException;
    
    protected abstract String formatEntries();

    protected abstract int getFormatNumber();

    public abstract ISVNProperties getBaseProperties(String name) throws SVNException;

    public abstract ISVNProperties getWCProperties(String name) throws SVNException;

    public abstract ISVNProperties getProperties(String name) throws SVNException;

    protected Map getBasePropertiesStorage(boolean create) {
        if (myBaseProperties == null && create) {
            myBaseProperties = new HashMap();
        }
        return myBaseProperties;
    }

    protected Map getPropertiesStorage(boolean create) {
        if (myProperties == null && create) {
            myProperties = new HashMap();
        }
        return myProperties;
    }
    
    protected Map getWCPropertiesStorage(boolean create) {
        if (myWCProperties == null && create) {
            myWCProperties = new HashMap();
        }
        return myWCProperties;
    }
    
    public static void checkWCFormat(int formatVersion, File dir) throws SVNException {
        if (formatVersion > SVNAdminArea.getLatestFormatVersion()) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_UNSUPPORTED_FORMAT, "This client is too old to work with working copy ''{0}''; please get a newer JavaSVN client", dir);
            SVNErrorManager.error(err);
        }
        
        if (!SVNAdminArea.isFormatSupported(formatVersion)) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_UNSUPPORTED_FORMAT, "Working copy format of ''{0}'' is too old ''{1,number,integer}''; please check out your working copy again", dir);
            SVNErrorManager.error(err);
        }
    }
    
    public static SVNAdminArea createAdminArea(SVNWCAccess wcAccess, String path, File dir, boolean isUnderConstruction) throws SVNException {
        File adminDir = new File(dir, SVNFileUtil.getAdminDirectoryName());
        File entriesFile = new File(adminDir, "entries");
        int formatVersion = isUnderConstruction ? getLatestFormatVersion() : -1;

        if (!isUnderConstruction) {
            BufferedReader reader = null;
            String line = null;
            boolean readFormatFile = false;
    
            if (!entriesFile.isFile() || !entriesFile.canRead()) {
                readFormatFile = true;
            } else {
                try {
                    reader = new BufferedReader(new InputStreamReader(SVNFileUtil.openFileForReading(entriesFile), "UTF-8"));
                    line = reader.readLine();
                } catch (IOException e) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, "Cannot read entries file ''{0}'': {1}", new Object[] {entriesFile, e.getLocalizedMessage()});
                    SVNErrorManager.error(err, e);
                } finally {
                    SVNFileUtil.closeFile(reader);
                }
                readFormatFile = line != null ? false : true;
            }
    
            if (!readFormatFile) {
                try {
                    formatVersion = Integer.parseInt(line.trim());
                } catch (NumberFormatException e) {
                    readFormatFile = true;
                }
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
            
            checkWCFormat(formatVersion, dir);
        }

        Class areaClass = (Class)ourAreas.get(new Integer(formatVersion));
        Constructor areaConstructor = null;
        SVNAdminArea adminArea = null;
        try {
            areaConstructor = areaClass.getConstructor(new Class[]{SVNWCAccess.class, String.class, File.class});
            adminArea = (SVNAdminArea)areaConstructor.newInstance(new Object[]{wcAccess, path, dir});
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
    
    public static boolean isFormatSupported(int formatVersion){
        return ourAreas.get(new Integer(formatVersion)) != null;
    }
    
    public abstract boolean hasPropModifications(String entryName) throws SVNException;

    protected SVNAdminArea(SVNWCAccess wcAccess, String path, File dir){
        myDirectory = dir;
        myAdminRoot = new File(dir, SVNFileUtil.getAdminDirectoryName());
        myLockFile = new File(myAdminRoot, "lock");
        myEntriesFile = new File(myAdminRoot, "entries");
        myPath = path;
        myWCAccess = wcAccess;
    }

    public static int getLatestFormatVersion() {
        return LATEST_WC_FORMAT;
    }
}
