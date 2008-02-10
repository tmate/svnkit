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
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

import org.tmatesoft.svn.core.SVNCancelException;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.util.SVNEncodingUtil;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;


/**
 * This class creates SVNWCAccess instances
 * 
 * @version 1.2
 * @author  TMate Software Ltd.
 */
public class SVNWCFactory {

    public static SVNWCAnchor openAnchor(String path, boolean lock, int depth, ISVNContext context) throws SVNException {
        String baseName = SVNPathUtil.tail(path);
        if ("".equals(baseName) || "..".equals(baseName)) {
            SVNWCAccess access = open(null, path, lock, depth, false, context);
            return new SVNWCAnchor(access, access, "");
        }
        String parentPath = SVNPathUtil.removeTail(path);
        SVNWCAccess parent = null;
        SVNWCAccess target = null;;
        SVNErrorMessage parentError = null;
        try {
            parent = open(null, parentPath, lock, 0, false, context);
        } catch (SVNException e) {
            if (e.getErrorMessage().getErrorCode() == SVNErrorCode.WC_NOT_DIRECTORY) {
                parent = null;
            } else if (lock && e.getErrorMessage().getErrorCode() == SVNErrorCode.WC_LOCKED) {
                try {
                    parent = open(null, parentPath, false, 0, false, context);
                    parentError = e.getErrorMessage();
                } catch (SVNException inner) {
                    throw e;
                }
            } else {
                throw e;
            }
        }
        
        try {
            target = open(null, path, lock, 0, false, context);
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
                parent.getChildren().put(path, SVNWCAccess.MISSING);
            }
        }
        
        SVNWCAccess anchorAccess = parent != null ? parent : target;
        SVNWCAccess targetAccess = target != null ? target : parent;
        baseName = parent == null ? baseName : "";
        
        return new SVNWCAnchor(anchorAccess, targetAccess, baseName);
    }


    public static SVNWCAccess open(SVNWCAccess owner, String path, boolean lock, int depth, ISVNContext context) throws SVNException {
        return open(owner, path, lock, depth, false, context);
    }

    public static SVNWCAccess preOpen(String path) throws SVNException {
        return open(null, path, true, 0, true, null);
    }

    public static SVNWCAccess probeOpen(SVNWCAccess owner, String path, boolean lock, int depth, ISVNContext context) throws SVNException {
        String dir = probe(path);
        if (dir != path) {
            depth = 0;
        }
        SVNWCAccess wcAccess = null;
        try {
            wcAccess = open(owner, dir, lock, depth, false, context);
        } catch (SVNException e) {
            File child = new File(path);
            if (dir != path && child.isDirectory() && 
                    e.getErrorMessage().getErrorCode() == SVNErrorCode.WC_NOT_DIRECTORY) {
                // error on path, not on dir.
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_NOT_DIRECTORY, "''{0}'' is not a working copy", child);
                SVNErrorManager.error(err);
            }
            throw e;
        }
        // how could it happen that format is zero?
        // it should be always set on open.
        /*
        if (wcAccess.getFormat() == 0) {
            wcAccess.myFormat = SVNUtil.getWCFormat(path);
        }*/
        return wcAccess;
    }

    public static SVNWCAccess probeRetrive(SVNWCAccess owner, String path) throws SVNException {
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

    public static SVNWCAccess probeTry(SVNWCAccess owner, String path, boolean lock, int depth, ISVNContext context) throws SVNException {
        SVNWCAccess access = null;
        SVNErrorMessage err = null;
        try {
            access = probeRetrive(owner, path);
        } catch (SVNException e) {
            err = e.getErrorMessage();
        }
        if (err != null && err.getErrorCode() == SVNErrorCode.WC_NOT_LOCKED) {
            try {
                err = null;
                access = probeOpen(owner, path, lock, depth, context);
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


    public static SVNWCAccess retrive(SVNWCAccess owner, String path) throws SVNException {
        SVNWCAccess access = owner.retrive(path);
        if (access == null) {
            SVNEntry subdirEntry = null;
            try {
                subdirEntry = owner.getEntry(path, true);
            } catch (SVNException e) {
                subdirEntry = null;
            }
            File dir = new File(path);
            if (subdirEntry != null) {
                if (dir.isFile() && subdirEntry.isDirectory()) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_NOT_DIRECTORY, "Expected ''{0}'' to be a directory but found a file", dir);
                    SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.WC_NOT_LOCKED), err);
                } else if (dir.isDirectory() && subdirEntry.isFile()) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_NOT_FILE, "Expected ''{0}'' to be a file but found a directory", dir);
                    SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.WC_NOT_LOCKED), err);
                }
            }
            if (!dir.exists()) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_PATH_NOT_FOUND, "Directory ''{0}'' is missing", dir);
                SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.WC_NOT_LOCKED), err);
            }
            boolean adminExists = SVNAdminLayout.getInstance().adminAreaExists(path);
            if (dir.isDirectory() && !adminExists) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_NOT_LOCKED, "Directory ''{0}'' containing working copy admin area is missing", dir);
                SVNErrorManager.error(err);
            } else if (dir.isDirectory() && adminExists) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_NOT_LOCKED, "Unable to lock ''{0}''", dir);
                SVNErrorManager.error(err);
            }
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_NOT_LOCKED, "Working copy ''{0}'' is not locked", dir);
            SVNErrorManager.error(err);
        }
        return access;
    }

    private static SVNWCAccess open(SVNWCAccess owner, String path, boolean lock, int depth, boolean underConstruction, ISVNContext context) throws SVNException {
        SVNWCAccess access;
        int wcFormat = 0;
        
        if (owner != null) {
            owner.ensureChildren();
            access = (SVNWCAccess) owner.getChildren().get(path);
            if (access != null && access != SVNWCAccess.MISSING) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_LOCKED, "Working copy ''{0}'' locked", path);
                SVNErrorManager.error(err);
            }
        }
        
        if (!underConstruction) {
            SVNErrorMessage err = null;
            try {
                wcFormat = SVNAdminLayout.getInstance().readVersion(path); 
            } catch (SVNException e) {
                SVNErrorMessage err2 = SVNErrorMessage.create(SVNErrorCode.WC_NOT_DIRECTORY, "''{0}'' is not a working copy", path);
                SVNErrorManager.error(err2, err);
            }
            SVNAdminArea.assertWCFormatIsSupported(wcFormat, new File(path));
        }
        
        SVNAdminArea area = SVNAdminArea.getAdminArea(underConstruction ? -1 : wcFormat);
        if (lock) {
            access = new SVNWCAccess(path, area, wcFormat, SVNWCAccess.Type.WRITE_LOCK);
            access.lock();
        } else {
            access = new SVNWCAccess(path, area, wcFormat, SVNWCAccess.Type.UNLOCKED);
        }
        
        if (!underConstruction) {
            if (lock) {
                // TODO upgrade
                // upgrade format.
                // or just notify on possible format upgrade.
            }
        }
        
        if (depth != 0) {
            if (depth > 0) {
                depth--;
            }
            Map entries = access.getEntries(false);
            if (owner != null) {
                access.setChildren(new HashMap());
            }
            for(Iterator names = entries.keySet().iterator(); names.hasNext();) {
                String entryName = (String) names.next();
                SVNEntry entry = (SVNEntry) entries.get(entryName);
                if (context != null && context.getCanceller() != null) {
                    try {
                        context.getCanceller().checkCancelled();
                    } catch (SVNCancelException cancel) {
                        close(access, false, true);
                        access.setChildren(null);
                        throw cancel;
                    }
                }
                if (entry.isFile() || access.getAdminArea().getThisDirName(access).equals(entry.getName())) {
                    continue;
                }
                String entryPath = SVNPathUtil.append(path, entryName);
                try {
                    open(access, entryPath, lock, depth, false, context);
                } catch (SVNException e) {
                    if (e.getErrorMessage().getErrorCode() != SVNErrorCode.WC_NOT_DIRECTORY) {
                        close(access, false, true);
                        access.setChildren(null);
                        throw e;
                    }
                    access.ensureChildren();
                    access.getChildren().put(entryPath, SVNWCAccess.MISSING);
                    continue;
                }
            }
            if (owner != null) {
                Map tmpChildren = access.getChildren();
                for (Iterator paths = tmpChildren.keySet().iterator(); paths.hasNext();) {
                    String accessPath = (String) paths.next();
                    SVNWCAccess childAccess = (SVNWCAccess) tmpChildren.get(accessPath);
                    owner.getChildren().put(accessPath, childAccess);
                    childAccess.setChildren(owner.getChildren());
                }
                access.setChildren(owner.getChildren());
            }
        }
        
        if (owner != null) {
            access.setChildren(owner.getChildren());
            access.getChildren().put(path, access); 
        }
        return access;
    }

    protected static void close(SVNWCAccess wcAccess, boolean preserveLocks, boolean recurse) throws SVNException {
        if (wcAccess.getType() == SVNWCAccess.Type.CLOSED) {
            return;
        }
        if (recurse && wcAccess.getChildren() != null) {
            TreeMap sorted = new TreeMap(new Comparator() {
                public int compare(Object o1, Object o2) {
                    return -SVNPathUtil.PATH_COMPARATOR.compare(o1, o2);
                }
            });
            sorted.putAll(wcAccess.getChildren());
            for(Iterator paths = sorted.keySet().iterator(); paths.hasNext();) {
                String childPath = (String) paths.next();
                SVNWCAccess childAccess = (SVNWCAccess) sorted.get(childPath);
                if (childAccess == SVNWCAccess.MISSING) {
                    wcAccess.getChildren().remove(childPath);
                    continue;
                }
                if (!SVNPathUtil.isAncestor(wcAccess.getPath(), childPath) || wcAccess.getPath().equals(childPath)) {
                    continue;
                }
                close(childAccess, preserveLocks, false);
            }
        }
        if (wcAccess.getType() == SVNWCAccess.Type.WRITE_LOCK) {
            if (wcAccess.isLocked() && !preserveLocks) {
                wcAccess.unlock();
            }
        }
        wcAccess.setType(SVNWCAccess.Type.CLOSED);
        if (wcAccess.getChildren() != null) {
            wcAccess.getChildren().remove(wcAccess.getPath());
            if (!(!wcAccess.isOwner() || wcAccess.getChildren().isEmpty())) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNKNOWN, "assertion failed!");
                SVNErrorManager.error(err);
            }
        }
    }

    private static String probe(String path) throws SVNException {
        // could we access file type directly here?
        // yes, because we only work with wc-dirs, not files.
        
        File file = new File(path);
        int wcFormat = 0;
        if (file.isDirectory()) {
            wcFormat = SVNAdminLayout.getInstance().readVersion(path);
        } 
        if (!file.isDirectory() || wcFormat == 0) {
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


}
