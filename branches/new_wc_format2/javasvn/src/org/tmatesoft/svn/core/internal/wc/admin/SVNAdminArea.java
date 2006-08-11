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
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
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
import org.tmatesoft.svn.core.internal.wc.SVNFileType;
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
        ourAreas.put(new Integer(SVNAdminArea14.WC_FORMAT), SVNAdminArea14.class);
        ourAreas.put(new Integer(SVNXMLAdminArea.WC_FORMAT), SVNXMLAdminArea.class);
    }
    
    private File myDirectory;
//    protected SVNEntries2 myEntries;
    private SVNWCAccess myWCAccess;
    private String myPath;
    private File myAdminRoot;
    protected Map myBaseProperties;
    protected Map myProperties;
    protected Map myWCProperties;
    protected Map myEntries;

    
    public abstract boolean isLocked();

    public abstract boolean isVersioned();

    public abstract boolean lock() throws SVNException;

    public abstract ISVNProperties getBaseProperties(String name) throws SVNException;

    public abstract ISVNProperties getWCProperties(String name) throws SVNException;

    public abstract ISVNProperties getProperties(String name) throws SVNException;

    public abstract void save() throws SVNException;

    public abstract String getThisDirName();

    public abstract boolean hasPropModifications(String entryName) throws SVNException;

    public abstract boolean hasProperties(String entryName) throws SVNException;

    public abstract InputStream getBaseFileForReading(String name, boolean tmp) throws SVNException;

    public abstract OutputStream getBaseFileForWriting(String name, boolean tmp) throws SVNException;
    
    public void deleteEntry(String name) throws SVNException {
        Map entries = loadEntries();
        if (entries != null) {
            entries.remove(name);
        }
    }

    public SVNEntry getEntry(String name, boolean hidden) throws SVNException {
        Map entries = loadEntries();
        if (entries != null && entries.containsKey(name)) {
            SVNEntry entry = (SVNEntry)entries.get(name);
            if (!hidden && entry.isHidden()) {
                return null;
            }
            return entry;
        }
        return null;
    }

    public SVNEntry addEntry(String name) throws SVNException {
        Map entries = loadEntries();
        if (entries == null) {
            myEntries = new TreeMap(); 
            entries = myEntries;
        }

        SVNEntry entry = entries.containsKey(name) ? (SVNEntry) entries.get(name) : new SVNEntry(new HashMap(), this, name);
        entries.put(name, entry);
        return entry;
    }

    public Iterator entries(boolean hidden) throws SVNException {
        Map entries = loadEntries();
        if (entries == null) {
            return Collections.EMPTY_LIST.iterator();
        }
        Collection copy = new LinkedList(entries.values());
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

    public File getRoot() {
        return myDirectory;
    }

    public File getAdminDirectory() {
        return myAdminRoot;
    }

    public File getAdminFile(String name) {
        return new File(getAdminDirectory(), name);
    }

    public void setWCAccess(SVNWCAccess wcAccess, String path) {
        myWCAccess = wcAccess;
        myPath = path;
    }

    protected abstract void writeEntries(Writer writer) throws IOException;

    protected abstract int getFormatNumber();

    protected abstract Map fetchEntries() throws SVNException;

    protected SVNAdminArea(SVNWCAccess wcAccess, String path, File dir){
        myDirectory = dir;
        myAdminRoot = new File(dir, SVNFileUtil.getAdminDirectoryName());
        myPath = path;
        myWCAccess = wcAccess;
    }

    protected Map loadEntries() throws SVNException {
        if (myEntries != null) {
            return myEntries;
        }
        myEntries = fetchEntries();
        return myEntries;
    }

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

/*    public static boolean upgradeFormat(SVNAdminArea adminArea) {
        if (adminArea.getFormatNumber() != getLatestFormatVersion()) {
            File logFile = adminArea.getAdminFile("log");
            SVNFileType type = SVNFileType.getType(logFile);
            if (type == SVNFileType.FILE) {
                return false;
            }
            
        }
    }
*/
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

    public static int getLatestFormatVersion() {
        return LATEST_WC_FORMAT;
    }

    public static void createFormatFile(File adminDir) throws SVNException {
        OutputStream os = null;
        try {
            os = SVNFileUtil.openFileForWriting(new File(adminDir, "format"));
            os.write(String.valueOf(getLatestFormatVersion()).getBytes("UTF-8"));
            os.write('\n');            
        } catch (IOException e) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, e.getLocalizedMessage());
            SVNErrorManager.error(err, e);
        } finally {
            SVNFileUtil.closeFile(os);
        }
    }

    public static void createVersionedDirectory(File dir) throws SVNException {
        dir.mkdirs();
        File adminDir = new File(dir, SVNFileUtil.getAdminDirectoryName());
        adminDir.mkdir();
        SVNFileUtil.setHidden(adminDir, true);
        // lock dir.
        File lock = new File(adminDir, "lock"); 
        SVNFileUtil.createEmptyFile(lock);
        File[] tmp = {
                new File(adminDir, "tmp"),
                new File(adminDir, "tmp" + File.separatorChar + "props"),
                new File(adminDir, "tmp" + File.separatorChar + "prop-base"),
                new File(adminDir, "tmp" + File.separatorChar + "text-base"),
                new File(adminDir, "props"), new File(adminDir, "prop-base"),
                new File(adminDir, "text-base")};
        for (int i = 0; i < tmp.length; i++) {
            tmp[i].mkdir();
        }
        // for backward compatibility 
        SVNAdminArea.createFormatFile(adminDir);
        // unlock dir.
        SVNFileUtil.deleteFile(lock);
    }
    
}
