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
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.admin.SVNEntry;
import org.tmatesoft.svn.core.wc.ISVNEventHandler;


/**
 * @version 1.1.2
 * @author  TMate Software Ltd.
 */
public class SVNWCAccess2 {

    private static class SVNWCAccessType {        
    }
    
    public static final SVNWCAccess2 MISSING = new SVNWCAccess2();
    
    private static final SVNWCAccessType WRITE_LOCK = new SVNWCAccessType();
    private static final SVNWCAccessType UNLOCKED = new SVNWCAccessType();
    private static final SVNWCAccessType CLOSED = new SVNWCAccessType();
    
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
        this(UNLOCKED, null);
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

    public String getPath() {
        return myPath;
    }
    
    public boolean isLocked() {
        return myType == WRITE_LOCK;
    }
    
    public void assertWritable() throws SVNException {
        if (!isLocked()) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_NOT_LOCKED, "No write-lock in ''{0}''", getPath());
            SVNErrorManager.error(err);
        }
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
        return null;
    }

    public static SVNWCAccess2 retrive() throws SVNException {
        return null;
    }
    
    public static SVNWCAccess2 probeRetrive() throws SVNException {
        return null;
    }

    public static SVNWCAccess2 probeTry() throws SVNException {
        return null;
    }
    
    public static SVNWCAccess2 join() throws SVNException {
        return null;
    }

    public static SVNWCAccess2 openAnchor() throws SVNException {
        return null;
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
            } catch (SVNCancelException e) {
                throw e;
            } catch (SVNException e) {
                err = e.getErrorMessage();
            }
            if (err != null && err.getErrorCode() == SVNErrorCode.BAD_VERSION_FILE_FORMAT) {
                try {
                    err = null;
                    wcFormat = SVNAdminFiles.readVersionFile(SVNAdminFiles.getAdminFile(path, SVNAdminFiles.ADM_ENTRIES, false));
                } catch (SVNCancelException e) {
                    throw e;
                } catch (SVNException e) {
                    err = e.getErrorMessage();  
                }
            }
            if (err != null) {
                SVNErrorMessage err2 = SVNErrorMessage.create(SVNErrorCode.WC_NOT_DIRECTORY, "''{0}'' is not a working copy", path);
                SVNErrorManager.error(err2, err);
            }
            SVNAdminFiles.assertWCFormatIsSupported(wcFormat, new File(path));
        }
        
        if (lock) {
            access = new SVNWCAccess2(WRITE_LOCK, path);
            access.createLock();
            access.myIsLockExists = true;
        } else {
            access = new SVNWCAccess2(UNLOCKED, path);
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
        if (wcAccess.myType == CLOSED) {
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
        wcAccess.myType = CLOSED;
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
