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
package org.tmatesoft.svn.core.internal.wc.admin3;

import java.io.File;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;


/**
 * @version 1.1.2
 * @author  TMate Software Ltd.
 */
public class SVNWCAccess {
    
    public static final SVNWCAccess MISSING = new SVNWCAccess();

    public static class Type {
        private Type() {
        }
        
        public static final Type WRITE_LOCK = new Type();
        public static final Type UNLOCKED = new Type();
        public static final Type CLOSED = new Type();        
    }
    
    private String myPath;
    private SVNAdminArea myAdminArea;
    private int myFormat;
    private Map myEntries;
    private Map myPublicEntries;
    private Map myChildren;
    private Type myType;
    private boolean myIsOwner;
    private boolean myIsLocked;
    
    private SVNWCAccess() {
        
    }
    
    protected SVNWCAccess(String path, SVNAdminArea adminArea, int format, Type type) {
        myPath = path;
        myAdminArea = adminArea;
        myFormat = format;
        myType = type;
    }

    public String getPath() {
        return myPath;
    }
    
    public SVNAdminArea getAdminArea() {
        return myAdminArea;
    }
    
    public int getFormat() {
        return myFormat;
    }
    
    public Map getEntries(boolean showHidden) throws SVNException {
        if (myEntries == null) {
            getAdminArea().readEntries(this);
            // entries set, now filter hidden.
            boolean hasHidden = false;
            myPublicEntries = new HashMap();
            for (Iterator entries = myEntries.keySet().iterator(); entries.hasNext();) {
                String name = (String) entries.next();
                SVNEntry entry = (SVNEntry) myEntries.get(name);
                if (!entry.isHidden()) {
                    myPublicEntries.put(name, entry);
                } else {
                    hasHidden = true;
                }
            }
            if (!hasHidden) {
                myPublicEntries = myEntries;
            }
        }
        return showHidden ? myEntries : myPublicEntries;
    }
    
    public void loadProperties(String path, Map base, Map working, Map revert) throws SVNException {
        SVNEntry entry = getEntry(path, false);
        if (entry == null) {
            return;
        }
        SVNWCAccess wcAccess = entry.isFile() ? retrive(SVNPathUtil.removeTail(path)) : retrive(path);
        boolean hasPropCaching = getAdminArea().hasPropcaching(wcAccess);
        if (entry == null) {
            return;
        }
        if (base != null || (hasPropCaching && !entry.isPropertiesModified() && entry.hasProperties())) {
            base = getAdminArea().readProperties(wcAccess, path, entry.getKind(), SVNAdminArea.BASE_PROPERTIES, false, base);
        } 
        if (working != null) {
            if (hasPropCaching && !entry.isPropertiesModified() && entry.hasProperties()) {
                working.putAll(base);  
            } else if (!hasPropCaching || entry.hasProperties()) {
                working = getAdminArea().readProperties(wcAccess, path, entry.getKind(), SVNAdminArea.WORKING_PROPERTIES, false, working);
            } 
        }
        if (revert != null) {
            if (SVNEntry.SCHEDULE_REPLACE.equals(entry.getSchedule()) && entry.isCopied()) {
                revert = getAdminArea().readProperties(wcAccess, path, entry.getKind(), SVNAdminArea.REVERT_PROPERTIES, false, working);
            }
        }
    }
    
    public void lock() throws SVNException {
        getAdminArea().lock(this);
        myIsLocked = true;
        
    }
    
    public void unlock() throws SVNException {
        getAdminArea().unlock(this);
        myIsLocked = false;
    }
    
    public SVNEntry getEntry(String path, boolean showHidden) throws SVNException {
        SVNWCAccess owner = retrive(path);
        String name;
        if (owner == null) {
            owner = retrive(SVNPathUtil.removeTail(path));
            name = SVNPathUtil.tail(path);
        } else {
            name = getAdminArea().getThisDirName(owner);
        }
        if (owner != null) {
            return (SVNEntry) owner.getEntries(showHidden).get(name);
        }
        return null;
    }
    
    public SVNEntry getVersionedEntry(String path, boolean showHidden) throws SVNException {
        SVNEntry entry = getEntry(path, showHidden);
        if (entry == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_NOT_FOUND, "''{0}'' is not under version control", new File(path));
            SVNErrorManager.error(err);
        }
        return entry;
    }
    
    public void visitEntries(String path, SVNDepth depth, boolean showHidden, ISVNContext context, ISVNEntryVisitor visitor) throws SVNException {
        SVNEntry entry = getEntry(path, showHidden);
        if (entry == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNVERSIONED_RESOURCE, "''{0}'' is not under version control", new File(path));
            visitor.handleError(path, err);
            return;
        }
        if (entry.isFile()) {
            try {
                visitor.visitEntry(path, entry);
            } catch (SVNException e) {
                visitor.handleError(path, e.getErrorMessage());
            }
            return;
        } else if (entry.isDirectory()) {
            SVNWCAccess wcAccess = retrive(path);
            visitEntriesRecursively(wcAccess, depth, showHidden, context, visitor);
        } else {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.NODE_UNKNOWN_KIND, "''{0}'' has unrecognized node kind", new File(path));
            visitor.handleError(path, err);
        }
    }
    
    public SVNLog installProperties(String path, Map base, Map working, boolean writeBase, SVNLog log) throws SVNException {
        SVNWCAccess wcAccess = SVNWCFactory.probeRetrive(this, path);
        boolean hasPropcaching = wcAccess.getAdminArea().hasPropcaching(wcAccess);
        SVNNodeKind kind = SVNNodeKind.DIR; 
        
        if (SVNPathUtil.getPathAsChild(wcAccess.getPath(), path) != null) {
            kind = SVNNodeKind.FILE;
        }
        Map diff = SVNHashUtil.computeDiff(working, base);
        log = log != null ? log : new SVNLog(wcAccess);
        SVNEntry tmpEntry = new SVNEntry();
        tmpEntry.myIsPropertiesModified = !diff.isEmpty();
        tmpEntry.myHasProperties = !working.isEmpty();
        // get from adminarea
        
        tmpEntry.myCachableProperties = wcAccess.getAdminArea().getCachableProperties();
        tmpEntry.buildPresentProperties(working);
        
        log.modifyEntry(tmpEntry, path, SVNEntry.FLAG_HAS_PROPS | SVNEntry.FLAG_HAS_PROP_MODS | SVNEntry.FLAG_CACHABLE_PROPS | SVNEntry.FLAG_PRESENT_PROPS);
        SVNEntry entry = hasPropcaching ? wcAccess.getEntry(path, false) : null;
        String wcPropPath = getAdminArea().getPropertiesPath(wcAccess, path, kind, SVNAdminArea.WORKING_PROPERTIES, false);
        if (tmpEntry.isPropertiesModified()) {
            String wcPropPathTmp = getAdminArea().writeProperties(wcAccess, path, kind, SVNAdminArea.WORKING_PROPERTIES, true, working);
            log.move(wcPropPathTmp, wcPropPath, false);
            log.readonly(wcPropPath);
        } else if (!hasPropcaching || (entry != null && entry.isPropertiesModified())) {
            log.remove(wcPropPath);
        }
        if (writeBase) {
            String basePropPath = getAdminArea().getPropertiesPath(wcAccess, path, kind, SVNAdminArea.BASE_PROPERTIES, false);
            if (!base.isEmpty()) {
                String basePropPathTmp = getAdminArea().writeProperties(wcAccess, path, kind, SVNAdminArea.BASE_PROPERTIES, true, base);
                log.move(basePropPathTmp, basePropPath, false);
                log.readonly(basePropPath);
            } else if (!hasPropcaching || (entry != null && entry.isPropertiesModified())) {
                log.remove(basePropPath);
            }
        }
        return log;
    }
    
    private static void visitEntriesRecursively(SVNWCAccess wcAccess, SVNDepth depth, boolean showHidden, ISVNContext context, ISVNEntryVisitor visitor) throws SVNException {
        Map entriesMap = null;
        try {
            entriesMap = wcAccess.getEntries(showHidden);
        } catch (SVNException e) {
            visitor.handleError(wcAccess.getPath(), e.getErrorMessage());
        }
        SVNEntry thisDirEntry = entriesMap != null ? (SVNEntry) entriesMap.get(wcAccess.getAdminArea().getThisDirName(wcAccess)) : null;
        if (thisDirEntry == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_NOT_FOUND, "Directory ''{0}'' has no THIS_DIR entry", new File(wcAccess.getPath()));
            visitor.handleError(wcAccess.getPath(), err);
            return;
        }
        try {
            visitor.visitEntry(wcAccess.getPath(), thisDirEntry);
        } catch (SVNException e) {
            visitor.handleError(wcAccess.getPath(), e.getErrorMessage());
        }
        if (depth == SVNDepth.EMPTY) {
            return;
        }
        for (Iterator entries = entriesMap.values().iterator(); entries.hasNext();) {
            SVNEntry entry = (SVNEntry) entries.next();
            if (context != null && context.getCanceller() != null) {
                context.getCanceller().checkCancelled();
            }
            if (entry == thisDirEntry) {
                continue;
            }
            String entryPath = SVNPathUtil.append(wcAccess.getPath(), entry.getName());
            if (entry.isFile() || depth.compareTo(SVNDepth.IMMEDIATES) >= 0) {
                try {
                    visitor.visitEntry(entryPath, entry);
                } catch (SVNException e) {
                    visitor.handleError(entryPath, e.getErrorMessage());
                }
            } else if (entry.isDirectory() && depth.compareTo(SVNDepth.IMMEDIATES) >= 0) {
                SVNDepth childDepth = depth;
                if (childDepth == SVNDepth.IMMEDIATES) {
                    childDepth = SVNDepth.EMPTY;
                }
                SVNWCAccess entryAccess = null;
                try {
                    entryAccess = SVNWCFactory.retrive(wcAccess, entryPath);
                } catch (SVNException e) {
                    visitor.handleError(entryPath, e.getErrorMessage());
                }
                if (entryAccess != null) {
                    visitEntriesRecursively(entryAccess, childDepth, showHidden, context, visitor);
                }
            }
        }
        
    }

    public void join(SVNWCAccess src) {
        ensureChildren();
        Map parent = getChildren();
        
        if (src.getChildren() == null) {
            src.setChildren(parent);
            parent.put(src.getPath(), src);
            return;
        }
        
        for(Iterator paths = src.getChildren().keySet().iterator(); paths.hasNext();) {
            String path = (String) paths.next();
            SVNWCAccess child = (SVNWCAccess) src.getChildren().get(path);
            if (child != SVNWCAccess.MISSING) {
                child.setChildren(parent);
            }
            parent.put(path, child);
        }
        src.setOwner(false);
    }
    
    public void close() throws SVNException {
        SVNWCFactory.close(this, false, true);
    }
    
    protected SVNWCAccess retrive(String path) {
        SVNWCAccess child = null;
        if (getChildren() != null) {
            child = (SVNWCAccess) getChildren().get(path);
        } else if (path.equals(getPath())) {
            child = this;
        } 
        return child == MISSING ? null : child;
    }
    
    protected void ensureChildren() {
        if (getChildren() == null) {
            myIsOwner = true;
            setChildren(new HashMap());
            getChildren().put(getPath(), this);
        }
    }
    
    protected boolean isLocked() {
        return myIsLocked;
    }
    
    protected boolean isOwner() {
        return myIsOwner;
    }
    
    protected Type getType() {
        return myType;
    }

    protected Map getChildren() {
        return myChildren;
    }
    
    protected void setOwner(boolean owner) {
        myIsOwner = owner;
    }

    protected void setEntries(Map entries) {
        myEntries = entries;
    }
    
    protected void setChildren(Map children) {
        myChildren = children;
    }

    protected void setType(Type type) {
        myType = type;
    }
}
