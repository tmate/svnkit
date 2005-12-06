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

import java.io.File;
import org.tmatesoft.svn.core.SVNException;

/**
 * @version 1.0
 * @author  TMate Software Ltd.
 */
public abstract class FSRevisionNodePool {
    private File myReposRootDir;

    public abstract void setRootsCacheSize(int numberOfRoots); 
    
    public abstract void setRevisionNodesCacheSize(int numberOfNodes);
    
    public abstract void setRevisionsCacheSize(int numberOfRevs);
    
    public abstract int getRootsCacheSize(); 
    
    public abstract int getRevisionNodesCacheSize();
    
    public abstract int getRevisionsCacheSize();

    //first tries to find a necessary root node in the cache
    //if not found, the root node is read from the repository
    public FSRevisionNode getRootRevisionNode(long revision, File reposRootDir) throws SVNException{
        if(reposRootDir == null || revision == FSConstants.SVN_INVALID_REVNUM){
            return null;
        }
        FSRevisionNode root = null;
        if(myReposRootDir == null || reposRootDir.compareTo(myReposRootDir) != 0){
            myReposRootDir = reposRootDir;
            clearRootsCache();
            clearRevisionsCache();
        }else {
            root = fetchRootRevisionNode(revision);
        }        
        if(root == null){
            root = FSReader.getRootRevNode(myReposRootDir, revision);
            if(root != null){
                cacheRootRevisionNode(revision, root);
            }
        }
        return root;
    }
    
    protected abstract FSRevisionNode fetchRootRevisionNode(long revision);
    
    protected abstract void cacheRootRevisionNode(long revision, FSRevisionNode root);

    //first tries to find a necessary rev node in the cache
    //if not found, the rev node is read from the repository
    public FSRevisionNode getRevisionNode(long revision, String path, File reposRootDir) throws SVNException{
        if(reposRootDir == null || path == null || "".equals(path) || revision == FSConstants.SVN_INVALID_REVNUM){
            return null;
        }
        FSRevisionNode revNode = null;
        if(myReposRootDir == null || reposRootDir.compareTo(myReposRootDir) != 0){
            myReposRootDir = reposRootDir;
            clearRootsCache();
            clearRevisionsCache();
        }else {
            revNode = fetchRevisionNode(revision, path);
        }        
        if(revNode == null){
            FSRevisionNode root = fetchRootRevisionNode(revision); 
            revNode = FSReader.getRevisionNode(myReposRootDir, path, root, revision);
            if(revNode != null){
                cacheRevisionNode(revision, path, revNode);
            }
        }
        return revNode;
    }

    protected abstract void cacheRevisionNode(long revision, String path, FSRevisionNode revNode);

    protected abstract FSRevisionNode fetchRevisionNode(long revision, String path);

    //first tries to find a necessary rev node in the cache
    //if not found, the rev node is read from the repository
    public FSRevisionNode getRevisionNode(FSRevisionNode root, String path, File reposRootDir) throws SVNException{
        if(reposRootDir == null || path == null || "".equals(path) || root == null){
            return null;
        }
        FSRevisionNode revNode = null;
        if(myReposRootDir == null || reposRootDir.compareTo(myReposRootDir) != 0){
            myReposRootDir = reposRootDir;
            clearRootsCache();
            clearRevisionsCache();
        }else {
            revNode = fetchRevisionNode(root.getId().getRevision(), path);
        }        
        if(revNode == null){
            revNode = FSReader.getRevisionNode(myReposRootDir, path, root, FSConstants.SVN_INVALID_REVNUM);
            if(revNode != null){
                cacheRevisionNode(root.getId().getRevision(), path, revNode);
            }
        }
        return revNode;
    }
    
    public abstract void clearRootsCache();
    
    public abstract void clearRevisionsCache();
}
