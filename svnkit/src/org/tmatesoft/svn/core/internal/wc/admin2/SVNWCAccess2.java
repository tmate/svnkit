/*
 * ====================================================================
 * Copyright (c) 2004-2008 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.wc.admin2;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

import org.tmatesoft.svn.core.SVNCancelException;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.internal.util.SVNEncodingUtil;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNFileType;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc.admin.SVNEntry;
import org.tmatesoft.svn.core.wc.ISVNEventHandler;


/**
 * @version 1.1.2
 * @author  TMate Software Ltd.
 */
public class SVNWCAccess2 {

    static class SVNWCAccessType {
        private SVNWCAccessType() {
        }
        
        private static final SVNWCAccessType WRITE_LOCK = new SVNWCAccessType();
        private static final SVNWCAccessType UNLOCKED = new SVNWCAccessType();
        private static final SVNWCAccessType CLOSED = new SVNWCAccessType();        
    }
    
    public static final SVNWCAccess2 MISSING = new SVNWCAccess2();
    
    private String myPath;
    private SVNWCAccessType myType;
    private boolean myIsLockExists;
    private boolean myIsOwner;
    private int myFormat;
    
    private Map myChildren;
    private Map myEntries;
    private Map myHiddenEntries;
    private Map myWCProperties;

    private SVNWCAccess2() {
        this(SVNWCAccessType.UNLOCKED, null);
    }

    private SVNWCAccess2(SVNWCAccessType type, String path) {
        myPath = path;
        myType = type;
        myFormat = 0;
    }
    
    public Map getEntries(boolean includeHidden) throws SVNException {
        if (myEntries == null) {
            // read.
            myEntries = new HashMap();
            myHiddenEntries = new HashMap();
            File file = SVNAdminFiles.getAdminFile(getPath(), SVNAdminFiles.ADM_ENTRIES, false);
            SVNEntries.readEntries(file, myEntries, myHiddenEntries);
        }
        return includeHidden ? myHiddenEntries : myEntries;
    }
    
    public SVNEntry getVersionedEntry(String path, boolean includeHidden) throws SVNException {
        SVNEntry entry = getEntry(path, includeHidden);
        if (entry == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_NOT_FOUND, "''{0}'' is not under version control", new File(path));
            SVNErrorManager.error(err);
        }
        return entry;
    }
    
    public SVNEntry getEntry(String path, boolean includeHidden) throws SVNException {
        SVNWCAccess2 access = retrive(path);
        String entryName = SVNEntries.THIS_DIR;
        if (access == null) {
            entryName = SVNPathUtil.tail(path);
            access = retrive(SVNPathUtil.removeTail(path));
        } 
        if (access != null) {
            return (SVNEntry) access.getEntries(includeHidden).get(entryName);
        }
        return null;
    }
    
    public void loadProperties(String path, Map base, Map working, Map revert) throws SVNException {
        Map baseHash = null;
        Map workingHash = null;
        Map revertHash = null;
        
        // check propcaching.
        boolean hasPropCaching = true;
        
        SVNEntry entry = getEntry(path, false);
        if (entry == null) {
            return;
        }
        if (base != null || 
                (hasPropCaching && !entry.asMap().containsKey(SVNEntries.ATTRIBUTE_HAS_PROP_MODS) &&
                        entry.asMap().containsKey(SVNEntries.ATTRIBUTE_HAS_PROPS))) {
            // load base.
            File file = getPropertiesFile(path, true, false, false);
            baseHash = loadPropertiesFile(file);
        }
        if (working != null) {
            if (hasPropCaching && !entry.asMap().containsKey(SVNEntries.ATTRIBUTE_HAS_PROP_MODS) &&
                            entry.asMap().containsKey(SVNEntries.ATTRIBUTE_HAS_PROPS)) {
                // use base.
                workingHash = baseHash;
            } else if (!hasPropCaching || entry.asMap().containsKey(SVNEntries.ATTRIBUTE_HAS_PROPS)) {
                // load working.
                File file = getPropertiesFile(path, false, true, false);
                workingHash = loadPropertiesFile(file);
            }
        }
        if (revert != null) {
            if (entry.isScheduledForReplacement() && entry.isCopied()) {
                // load revert.
                File file = getPropertiesFile(path, false, false, true);
                revertHash = loadPropertiesFile(file);
            }
        }
    }
    
    private File getPropertiesFile(String path, boolean base, boolean working, boolean revert) throws SVNException {
        SVNEntry entry = getVersionedEntry(path, true);
        return null;
    }
    
    private static Map loadPropertiesFile(File file) throws SVNException {
        if (!file.exists()) {
            return null;
        }
        InputStream is = null;
        try {
            is = SVNFileUtil.openFileForReading(file);
            return SVNUtil.readHash(is);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            SVNFileUtil.closeFile(is);
        }
        return null;
    }
        
    
    public void readWCProperties() throws SVNException {
        // check format.
        InputStream is = null;
        File wcPropsFile = SVNAdminFiles.getAdminFile(getPath(), SVNAdminFiles.ADM_ALL_WCPROPS, false);
        if (!wcPropsFile.isFile()) {
            myWCProperties = new HashMap();
            return;
        }
        Map allProperties = new HashMap();
        try {
            is = SVNFileUtil.openFileForReading(wcPropsFile);
            Map props = SVNUtil.readHash(is, SVNUtil.HASH_TERMINATOR, false);
            allProperties.put(SVNEntries.THIS_DIR, props);
            while(true) {
                byte[] line = SVNUtil.readLine(is, (byte) '\n');
                if (line == null) {
                    break;
                }
                props = SVNUtil.readHash(is, SVNUtil.HASH_TERMINATOR, false);
                allProperties.put(new String(line, 0, line.length, "UTF-8"), props);
            }
            myWCProperties = allProperties;
        } catch (IOException e) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR);
            SVNErrorManager.error(err, e);
        } finally {
            SVNFileUtil.closeFile(is);
        }
    }
    
    public void writeWCProperties() throws SVNException {
        if (myWCProperties == null) {
            return;
        }
        boolean hasProps = false;
        for (Iterator names = myWCProperties.keySet().iterator(); names.hasNext();) {
            String name = (String) names.next();
            Map props = (Map) myWCProperties.get(name);
            if (props != null && !props.isEmpty()) {
                hasProps = true;
                break;
            }
        }
        File wcPropsFile = SVNAdminFiles.getAdminFile(myPath, SVNAdminFiles.ADM_ALL_WCPROPS, false);
        if (!hasProps) {
            SVNFileUtil.deleteFile(wcPropsFile);
            return;
        }
        OutputStream os = null;
        try {
            os = SVNFileUtil.openFileForWriting(wcPropsFile);
            Map props = (Map) myWCProperties.get(SVNEntries.THIS_DIR);
            props = props == null ? Collections.EMPTY_MAP : props;
            SVNUtil.writeHash(os, props, null, SVNUtil.HASH_TERMINATOR);
            for (Iterator names = myWCProperties.keySet().iterator(); names.hasNext();) {
                String name = (String) names.next();
                props = (Map) myWCProperties.get(name);
                if (SVNEntries.THIS_DIR.equals(name) || props == null || props.isEmpty()) {
                    continue;
                }
                if (props != null && !props.isEmpty()) {
                    hasProps = true;
                    break;
                }
                os.write(name.getBytes("UTF-8"));
                SVNUtil.writeHash(os, props, null, SVNUtil.HASH_TERMINATOR);
            }
        } catch (IOException e) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR);
            SVNErrorManager.error(err, e);
        } finally {
            SVNFileUtil.closeFile(os);
        }
    }
    
    public void flushWCProperties(String path) throws SVNException {
        SVNWCAccess2 access = probeRetrive(this, path);
        access.writeWCProperties();
    }
    
    public void removeWCProperties(String name) throws SVNException {
        boolean write = false;
        if (name == null) {
            if (myWCProperties == null || !myWCProperties.isEmpty()) {
                myWCProperties = new HashMap();
                write = true;
            }
        } else {
            Map wcProps = null;
            if (myWCProperties == null) {
                readWCProperties();
            }
            if (myWCProperties != null) {
                wcProps = (Map) myWCProperties.get(name);
            } 
            if (wcProps != null && !wcProps.isEmpty()) {
                myWCProperties.remove(name);
                write = true;
            }
        }
        if (write) {
            writeWCProperties();
        }
    }

    public String getPath() {
        return myPath;
    }
    
    public boolean isLocked() {
        return myType == SVNWCAccessType.WRITE_LOCK;
    }
    
    public void assertWritable() throws SVNException {
        if (!isLocked()) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_NOT_LOCKED, "No write-lock in ''{0}''", getPath());
            SVNErrorManager.error(err);
        }
    }
    
    public void join(SVNWCAccess2 access) {
        Map target = getChildren();

        if (access.myChildren == null) {
            access.myChildren = target;
            target.put(access.getPath(), access);
            return;
        }
        
        for(Iterator paths = access.myChildren.keySet().iterator(); paths.hasNext();) {
            String path = (String) paths.next();
            SVNWCAccess2 child = (SVNWCAccess2) access.myChildren.get(path);
            if (child != MISSING) {
                child.myChildren = target;
            }
            target.put(path, access);
        }
        access.myIsOwner = false;
    }
    
    private Map getChildren() {
        if (myChildren == null) {
            myChildren = new HashMap();
            myChildren.put(myPath, this);
            myIsOwner = true;
        }
        return myChildren;
    }
    
    private void createLock() throws SVNException {
        try {
            SVNAdminFiles.createAdminFile(this, SVNAdminFiles.ADM_LOCK, SVNNodeKind.FILE, false);
        } catch (SVNException e) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_LOCKED, "Working copy ''{0}'' locked", getPath());
            SVNErrorManager.error(err);
        }
    }

    private void removeLock() throws SVNException {
        try {
            SVNAdminFiles.removeAdminFile(this, SVNAdminFiles.ADM_LOCK, false);
        } catch (SVNException e) {
            if (SVNAdminFiles.adminFileExists(this, SVNAdminFiles.ADM_LOCK, false)) {
                throw e;
            }
        }
    }
    
    public static SVNWCAccess2 open(SVNWCAccess2 owner, String path, boolean lock, int depth, ISVNEventHandler handler) throws SVNException {
        return open(owner, path, lock, depth, false, handler);
    }

    public static SVNWCAccess2 preOpen(String path) throws SVNException {
        return open(null, path, true, 0, true, null);
    }
    
    public static SVNWCAccess2 probeOpen(SVNWCAccess2 owner, String path, boolean lock, int depth, ISVNEventHandler handler) throws SVNException {
        String dir = probe(path);
        if (dir != path) {
            depth = 0;
        }
        SVNWCAccess2 wcAccess = null;
        try {
            wcAccess = open(owner, dir, lock, depth, false, handler);
        } catch (SVNException e) {
            File child = new File(path);
            SVNFileType childType = SVNFileType.getType(child);
            if (dir != path && 
                    childType == SVNFileType.DIRECTORY && 
                    e.getErrorMessage().getErrorCode() == SVNErrorCode.WC_NOT_DIRECTORY) {
                // error on path, not on dir.
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_NOT_DIRECTORY, "''{0}'' is not a working copy", child);
                SVNErrorManager.error(err);
            }
            throw e;
        }
        if (wcAccess.myFormat == 0) {
            wcAccess.myFormat = SVNUtil.getWCFormat(path);
        }
        return wcAccess;
    }

    public static SVNWCAccess2 retrive(SVNWCAccess2 owner, String path) throws SVNException {
        SVNWCAccess2 access = owner.retrive(path);
        if (access == null) {
            // get entry for path.
            SVNEntry subdirEntry = null;
            try {
                subdirEntry = owner.getEntry(path, true);
            } catch (SVNException e) {
                subdirEntry = null;
            }
            File dir = new File(path);
            SVNFileType type = SVNFileType.getType(dir);
            if (subdirEntry != null) {
                if (type == SVNFileType.FILE && subdirEntry.isDirectory()) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_NOT_DIRECTORY, "Expected ''{0}'' to be a directory but found a file", dir);
                    SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.WC_NOT_LOCKED), err);
                } else if (type == SVNFileType.DIRECTORY && subdirEntry.isFile()) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_NOT_FILE, "Expected ''{0}'' to be a file but found a directory", dir);
                    SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.WC_NOT_LOCKED), err);
                }
            }
            if (type == SVNFileType.NONE) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_PATH_NOT_FOUND, "Directory ''{0}'' is missing", dir);
                SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.WC_NOT_LOCKED), err);
            } 

            File adminDir = SVNAdminFiles.getAdminFile(path, null, false);
            SVNFileType adminDirType = SVNFileType.getType(adminDir);
            if (type == SVNFileType.DIRECTORY && adminDirType == SVNFileType.NONE) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_NOT_LOCKED, "Directory ''{0}'' containing working copy admin area is missing", adminDir);
                SVNErrorManager.error(err);
            } else if (type == SVNFileType.DIRECTORY && adminDirType == SVNFileType.DIRECTORY) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_NOT_LOCKED, "Unable to lock ''{0}''", dir);
                SVNErrorManager.error(err);
            }
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_NOT_LOCKED, "Working copy ''{0}'' is not locked", dir);
            SVNErrorManager.error(err);
        }
        return access;
    }
    
    public static SVNWCAccess2 probeRetrive(SVNWCAccess2 owner, String path) throws SVNException {
        SVNEntry entry = owner.getEntry(path, true);
        String dir = path;
        if (entry == null) {
            dir = probe(path);
        } else if (!entry.isDirectory()) {
            dir = SVNPathUtil.removeTail(path);
        }
        try {
            return retrive(owner, dir);
        } catch (SVNException e) {
            if (e.getErrorMessage().getErrorCode() == SVNErrorCode.WC_NOT_LOCKED) {
                dir = probe(path);
                return retrive(owner, dir);
            }
            throw e;
        }
    }

    public static SVNWCAccess2 probeTry(SVNWCAccess2 owner, String path, boolean lock, int depth, ISVNEventHandler handler) throws SVNException {
        SVNWCAccess2 access = null;
        SVNErrorMessage err = null;
        try {
            access = probeRetrive(owner, path);
        } catch (SVNException e) {
            err = e.getErrorMessage();
        }
        if (err != null && err.getErrorCode() == SVNErrorCode.WC_NOT_LOCKED) {
            try {
                err = null;
                access = probeOpen(owner, path, lock, depth, handler);
            } catch (SVNException e) {
                err = e.getErrorMessage();
            }
            if (err != null && err.getErrorCode() == SVNErrorCode.WC_NOT_DIRECTORY) {
                access = null;
                err = null;
            }
        }
        if (err != null) {
            SVNErrorManager.error(err);
        }
        return access;
    }

    public static SVNWCAnchor openAnchor(String path, boolean lock, int depth, ISVNEventHandler handler) throws SVNException {
        String baseName = SVNPathUtil.tail(path);
        if ("".equals(baseName) || "..".equals(baseName)) {
            SVNWCAccess2 access = open(null, path, lock, depth, false, handler);
            return new SVNWCAnchor(access, access, "");
        }
        String parentPath = SVNPathUtil.removeTail(path);
        SVNWCAccess2 parent = null;
        SVNWCAccess2 target = null;;
        SVNErrorMessage parentError = null;
        try {
            parent = open(null, parentPath, lock, 0, false, handler);
        } catch (SVNException e) {
            if (e.getErrorMessage().getErrorCode() == SVNErrorCode.WC_NOT_DIRECTORY) {
                parent = null;
            } else if (lock && e.getErrorMessage().getErrorCode() == SVNErrorCode.WC_LOCKED) {
                try {
                    parent = open(null, parentPath, false, 0, false, handler);
                    parentError = e.getErrorMessage();
                } catch (SVNException inner) {
                    throw e;
                }
            } else {
                throw e;
            }
        }
        
        try {
            target = open(null, path, lock, 0, false, handler);
        } catch (SVNException e) {
            if (parent == null || e.getErrorMessage().getErrorCode() != SVNErrorCode.WC_NOT_DIRECTORY) {
                if (parent != null) {
                    try {
                        close(parent, false, true);
                    } catch (SVNException inner) {
                        // skip.
                    }
                }
                throw e;
            }
            target = null;
        }

        if (parent != null && target != null) {
            SVNEntry targetEntry = null;
            SVNEntry parentEntry = null;
            SVNEntry targetEntryInParent = null;
            try {
                targetEntryInParent = parent.getEntry(path, false);
                targetEntry = target.getEntry(path, false);
                parentEntry = parent.getEntry(parentPath, false);
            } catch (SVNException e) {
                try {
                    close(parent, false, true);
                } catch (SVNException inner) {
                    // skip.
                }
                try {
                    close(target, false, true);
                } catch (SVNException inner) {
                    // skip.
                }
                throw e;
            }
            String parentURL = parentEntry.getURL();
            String targetURL = targetEntry.getURL();
            if (targetEntryInParent == null || 
                    (parentURL != null && targetURL != null && 
                        (!parentURL.equals(SVNPathUtil.removeTail(targetURL)) || 
                         !SVNEncodingUtil.uriEncode(baseName).equals(SVNPathUtil.tail(targetURL))))) {
                try {
                    close(parent, false, true);
                } catch (SVNException inner) {
                    try {
                        close(target, false, true);
                    } catch (SVNException inner2) {
                        // skip.
                    }
                    throw inner;
                }
                parent = null;
            }
        }
        if (parent != null) {
            if (parentError != null) {
                try {
                    close(parent, false, true);
                } catch (SVNException inner) {
                    // skip.
                }
                if (target != null) {
                    try {
                        close(target, false, true);
                    } catch (SVNException inner) {
                        // skip.
                    }
                }
                SVNErrorManager.error(parentError);
            } else if (target != null) {
                parent.join(target);
            }
        }
        if (target == null) {
            SVNEntry targetEntry = null;
            try {
                targetEntry = parent.getEntry(path, false);
            } catch (SVNException e) {
                try {
                    close(parent, false, true);
                } catch (SVNException inner) {
                    // skip.
                }
                throw e;
            }
            if (targetEntry != null && targetEntry.isDirectory()) {
                parent.getChildren().put(path, MISSING);
            }
        }
        
        SVNWCAccess2 anchorAccess = parent != null ? parent : target;
        SVNWCAccess2 targetAccess = target != null ? target : parent;
        baseName = parent == null ? baseName : "";
        
        return new SVNWCAnchor(anchorAccess, targetAccess, baseName);
    }
    
    private SVNWCAccess2 retrive(String path) {
        SVNWCAccess2 access = null;
        if (myChildren != null) {
            access = (SVNWCAccess2) myChildren.get(path);
        } else if (path.equals(myPath)) {
            access = this;
        } 
        if (access == MISSING) {
            access = null;
        }
        return access;
    }

    private static String probe(String path) throws SVNException {
        SVNFileType type = SVNFileType.getType(new File(path));
        int wcFormat = 0;
        if (type == SVNFileType.DIRECTORY) {
            wcFormat = SVNUtil.getWCFormat(path);
        } 
        if (type != SVNFileType.DIRECTORY || wcFormat == 0) {
            String name = SVNPathUtil.tail(path);
            if ("..".equals(name) || ".".equals(name)) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_BAD_PATH, "Path ''{0}'' ends in ''{1}'', which is unsupported for this operation", 
                        new Object[] {path, name});
                SVNErrorManager.error(err);
            }
            return SVNPathUtil.removeTail(path);
        } 
        return path;
    }

    private static SVNWCAccess2 open(SVNWCAccess2 owner, String path, boolean lock, int depth, boolean underConstruction, ISVNEventHandler eventHandler) throws SVNException {
        SVNWCAccess2 access;
        int wcFormat = 0;
        
        if (owner != null) {
            access = (SVNWCAccess2) owner.getChildren().get(path);
            if (access != null && access != MISSING) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_LOCKED, "Working copy ''{0}'' locked", path);
                SVNErrorManager.error(err);
            }
        }
        
        if (!underConstruction) {
            SVNErrorMessage err = null;
            try {
                wcFormat = SVNAdminFiles.readVersionFile(SVNAdminFiles.getAdminFile(path, SVNAdminFiles.ADM_ENTRIES, false));
            } catch (SVNException e) {
                err = e.getErrorMessage();
            }
            if (err != null && err.getErrorCode() == SVNErrorCode.BAD_VERSION_FILE_FORMAT) {
                try {
                    err = null;
                    wcFormat = SVNAdminFiles.readVersionFile(SVNAdminFiles.getAdminFile(path, SVNAdminFiles.ADM_ENTRIES, false));
                } catch (SVNException e) {
                    err = e.getErrorMessage();  
                }
            }
            if (err != null) {
                SVNErrorMessage err2 = SVNErrorMessage.create(SVNErrorCode.WC_NOT_DIRECTORY, "''{0}'' is not a working copy", path);
                SVNErrorManager.error(err2, err);
            }
            SVNUtil.assertWCFormatIsSupported(wcFormat, new File(path));
        }
        
        if (lock) {
            access = new SVNWCAccess2(SVNWCAccessType.WRITE_LOCK, path);
            access.createLock();
            access.myIsLockExists = true;
        } else {
            access = new SVNWCAccess2(SVNWCAccessType.UNLOCKED, path);
        }
        
        if (!underConstruction) {
            access.myFormat = wcFormat;
            if (lock) {
                // upgrade format.
            }
        }
        
        if (depth != 0) {
            if (depth > 0) {
                depth--;
            }
            // recurse.
            Map entries = access.getEntries(false);
            if (owner != null) {
                access.myChildren = new HashMap();
                // shouldn't we mark 'access' as owner here?
            }
            for(Iterator names = entries.keySet().iterator(); names.hasNext();) {
                String entryName = (String) names.next();
                SVNEntry entry = (SVNEntry) entries.get(entryName);
                if (eventHandler != null) {
                    try {
                        eventHandler.checkCancelled();
                    } catch (SVNCancelException cancel) {
                        // close all open access
                        close(access, false, true);
                        access.myChildren = null;
                        throw cancel;
                    }
                }
                if (entry.isFile() || entry.isThisDir()) {
                    continue;
                }
                String entryPath = SVNPathUtil.append(path, entryName);
                try {
                    open(access, entryPath, lock, depth, false, eventHandler);
                } catch (SVNException e) {
                    if (e.getErrorMessage().getErrorCode() != SVNErrorCode.WC_NOT_DIRECTORY) {
                        // close all.
                        close(access, false, true);
                        access.myChildren = null;
                        throw e;
                    }
                    // add 'missing' to map.
                    access.getChildren().put(entryPath, MISSING);
                    continue;
                }
            }
            if (owner != null) {
                Map tmpChildren = access.myChildren;
                for (Iterator paths = tmpChildren.keySet().iterator(); paths.hasNext();) {
                    String accessPath = (String) paths.next();
                    SVNWCAccess2 childAccess = (SVNWCAccess2) tmpChildren.get(accessPath);
                    owner.myChildren.put(accessPath, childAccess);
                    childAccess.myChildren = owner.myChildren;
                }
                access.myChildren = owner.myChildren;
            }
        }
        
        if (owner != null) {
            access.myChildren = owner.getChildren();
            access.getChildren().put(path, access); 
        }
        return access;
    }
    
    private static void close(SVNWCAccess2 wcAccess, boolean preserveLocks, boolean recurse) throws SVNException {
        if (wcAccess.myType == SVNWCAccessType.CLOSED) {
            return;
        }
        if (recurse && wcAccess.myChildren != null) {
            // close recursively.
            TreeMap sorted = new TreeMap(new Comparator() {
                public int compare(Object o1, Object o2) {
                    return -SVNPathUtil.PATH_COMPARATOR.compare(o1, o2);
                }
            });
            sorted.putAll(wcAccess.myChildren);
            for(Iterator paths = sorted.keySet().iterator(); paths.hasNext();) {
                String childPath = (String) paths.next();
                SVNWCAccess2 childAccess = (SVNWCAccess2) sorted.get(childPath);
                if (childAccess == MISSING) {
                    wcAccess.myChildren.put(childPath, null);
                    continue;
                }
                if (!SVNPathUtil.isAncestor(wcAccess.getPath(), childPath) || wcAccess.getPath().equals(childPath)) {
                    continue;
                }
                close(childAccess, preserveLocks, false);
            }
        }
        if (wcAccess.isLocked()) {
            if (wcAccess.myIsLockExists && !preserveLocks) {
                wcAccess.removeLock();
                wcAccess.myIsLockExists = false;
            }
        }
        wcAccess.myType = SVNWCAccessType.CLOSED;
        if (wcAccess.myChildren != null) {
            wcAccess.myChildren.put(wcAccess.getPath(), null);
            if (!(!wcAccess.myIsOwner || wcAccess.myChildren.isEmpty())) {
                // assert.
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNKNOWN, "assertion failed!");
                SVNErrorManager.error(err);
            }
        }
    }
}
