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
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
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
import org.tmatesoft.svn.core.internal.util.SVNEncodingUtil;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;

/**
 * 
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
    
    public void close() {
        myData = null;
        myEntries = null;
    }

    public SVNEntry addEntry(String name) {
        if (myData == null) {
            myData = new TreeMap();
            myEntries = new TreeSet();
        }

        Map map = myData.containsKey(name) ? (Map) myData.get(name)
                : new HashMap();
        myData.put(name, map);
        SVNEntry entry = new SVNEntry(this, name);
        myEntries.add(entry);
        setPropertyValue(name, SVNProperty.NAME, name);
        return entry;
    }

    public void deleteEntry(String name){
        if (myData != null) {
            myData.remove(name);
            myEntries.remove(new SVNEntry(this, name));
        }
    }
    
    public SVNEntry getEntry(String name, boolean hidden){
        if (myData != null && myData.containsKey(name)) {
            SVNEntry entry = new SVNEntry(this, name);
            if (!hidden && entry.isHidden()) {
                return null;
            }
            return entry;
        }
        return null;
    }

    public abstract String getThisDirName();
    
    public void save(boolean close) throws SVNException {
        if (myData == null) {
            return;
        }
        Map rootEntry = (Map) myData.get(getThisDirName());
        if (rootEntry == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_NOT_FOUND, "No default entry in directory ''{0}''", getParent().getRoot());
            SVNErrorManager.error(err);
        }
        
        String reposURL = (String)rootEntry.get(SVNProperty.REPOS);
        String url = (String)rootEntry.get(SVNProperty.URL);
        if (reposURL != null && !SVNPathUtil.isAncestor(reposURL, url)) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNKNOWN, "Entry ''{0}'' has inconsistent repository root and url", getThisDirName());
            SVNErrorManager.error(err);
        }

        File tmpFile = new File(getParent().getAdminDirectory(), "tmp/entries");
        Writer os = null;
        try {
            String formattedEntries = formatEntries(); 
            os = new OutputStreamWriter(SVNFileUtil.openFileForWriting(tmpFile), "UTF-8");
            os.write(formattedEntries);
        } catch (IOException e) {
            SVNFileUtil.closeFile(os);
            SVNFileUtil.deleteFile(tmpFile);
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, "Cannot wrtie entries file ''{0}'': {1}", new Object[] {getParent().getEntriesFile(), e.getLocalizedMessage()});
            SVNErrorManager.error(err, e);
        } finally {
            SVNFileUtil.closeFile(os);
        }
        
        SVNFileUtil.rename(tmpFile, getParent().getEntriesFile());
        SVNFileUtil.setReadonly(getParent().getEntriesFile(), true);
        if (close) {
            close();
        }
    }

    protected abstract String formatEntries();
        
    public boolean setPropertyValue(String name, String propertyName, String propertyValue) {
        if (myData == null) {
            return false;
        }
        Map entry = (Map) myData.get(name);
        if (entry != null) {
            if (SVNProperty.SCHEDULE.equals(propertyName)) {
                if (SVNProperty.SCHEDULE_DELETE.equals(propertyValue)) {
                    if (SVNProperty.SCHEDULE_ADD.equals(entry.get(SVNProperty.SCHEDULE))) {
                        if (entry.get(SVNProperty.DELETED) == null) {
                            deleteEntry(name);
                        } else {
                            entry.remove(SVNProperty.SCHEDULE);
                        }
                        return true;
                    }
                }
            }
            if (propertyValue == null) {
                return entry.remove(propertyName) != null;
            }
            Object oldValue = entry.put(propertyName, propertyValue);
            return !propertyValue.equals(oldValue);            
        }
        return false;
    }

    public abstract void setCachableProperties(String name, String[] cachableProps);
    
    public Iterator entries(boolean hidden) {
        if (myEntries == null) {
            return Collections.EMPTY_LIST.iterator();
        }
        Collection copy = new LinkedList(myEntries);
        if (!hidden) {
            for (Iterator iterator = copy.iterator(); iterator.hasNext();) {
                SVNEntry entry = (SVNEntry) iterator.next();
                if (entry.isHidden()) {
                    iterator.remove();
                }
            }
        }
        return copy.iterator();
    }

    protected Map getEntryMap(String name) {
        if (myData != null && name != null) {
            return (Map) myData.get(name);
        }
        return null;
    }

    protected abstract int getFormatNumber();

    public static SVNAdminArea createAdminArea(SVNDirectory parent, boolean isUnderConstruction) throws SVNException {
        File dir = parent.getRoot(); 
        File adminDir = new File(dir, SVNFileUtil.getAdminDirectoryName());
        File entriesFile = parent.getEntriesFile();
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
            
            parent.checkWCFormat(formatVersion);
        }

        Class areaClass = (Class)ourAreas.get(new Integer(formatVersion));
        Constructor areaConstructor = null;
        SVNAdminArea adminArea = null;
        try {
            areaConstructor = areaClass.getConstructor(new Class[]{SVNDirectory.class});
            adminArea = (SVNAdminArea)areaConstructor.newInstance(new Object[]{parent});
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
    
    public String getPropertyValue(String name, String propertyName){
        if (myData == null) {
            return null;
        }
        Map entry = (Map) myData.get(name);
        if (entry != null) {
            return (String) entry.get(propertyName);
        }
        return null;

    }

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
