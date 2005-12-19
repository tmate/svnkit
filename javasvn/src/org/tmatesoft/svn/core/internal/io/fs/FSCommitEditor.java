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

import java.io.OutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.Collection;
import java.util.LinkedList;
import java.util.Iterator;
import java.util.Stack;
import java.io.File;

import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNRevisionProperty;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.ISVNWorkspaceMediator;
import org.tmatesoft.svn.core.io.diff.SVNDiffWindow;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.util.SVNEncodingUtil;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;

/**
 * @version 1.0
 * @author  TMate Software Ltd.
 */
public class FSCommitEditor implements ISVNEditor {
    private ISVNWorkspaceMediator myMediator;
    private Map myLockTokens;
    private String myAuthor;
    private String myBasePath;
    private String myLogMessage;
    private FSTransactionInfo myTxn;
    private FSRoot myTxnRoot;
    private boolean isTxnOwner;
    private boolean keepLocks;
    private File myReposRootDir;
    private FSRevisionNodePool myRevNodesPool;
    private FSRepository myRepository;
    private Stack myDirsStack;
    
    public FSCommitEditor(String path, String logMessage, String userName, Map lockTokens, boolean keepLocks, ISVNWorkspaceMediator mediator, FSTransactionInfo txn, FSRepository repository){
        myMediator = mediator;
        myLockTokens = lockTokens;
        myAuthor = userName;
        myBasePath = path;
        myLogMessage = logMessage;
        this.keepLocks = keepLocks;
        myTxn = txn;
        isTxnOwner = txn == null ? true : false;
        myRepository = repository;
        myReposRootDir = myRepository.getReposRootDir();
        myRevNodesPool = myRepository.getRevisionNodePool();
        myDirsStack = new Stack();
    }
    
    public void targetRevision(long revision) throws SVNException {
        //does nothing
    }

    public void openRoot(long revision) throws SVNException {
        /* Ignore revision.  We always build our transaction against
         * HEAD. However, we will keep it in dir baton for out of dateness checks.  
         */
        long youngestRev = myRepository.getYoungestRev(myReposRootDir);
        
        /* Unless we've been instructed to use a specific transaction, 
         * we'll make our own. 
         */
        if(isTxnOwner){
            myTxn = beginTransactionForCommit(youngestRev);
        }else{
            /* Even if we aren't the owner of the transaction, we might
             * have been instructed to set some properties. 
             */
            /* User (author). */
            if(myAuthor != null && !"".equals(myAuthor)){
                FSWriter.setTransactionProperty(myReposRootDir, myTxn.getTxnId(), SVNRevisionProperty.AUTHOR, myAuthor);
            }
            /* Log message. */
            if(myLogMessage != null && !"".equals(myLogMessage)){
                FSWriter.setTransactionProperty(myReposRootDir, myTxn.getTxnId(), SVNRevisionProperty.LOG, myLogMessage);
            }
        }
        Map txnProps = FSRepositoryUtil.getTransactionProperties(myReposRootDir, myTxn.getTxnId());
        int flags = 0;
        if(txnProps.get(SVNProperty.TXN_CHECK_OUT_OF_DATENESS) != null){
            flags |= FSConstants.SVN_FS_TXN_CHECK_OUT_OF_DATENESS;
        }
        if(txnProps.get(SVNProperty.TXN_CHECK_LOCKS) != null){
            flags |= FSConstants.SVN_FS_TXN_CHECK_LOCKS;
        }
        myTxnRoot = new FSRoot(myTxn.getTxnId(), flags);
        DirBaton dirBaton = new DirBaton(revision, myBasePath, false);  
        myDirsStack.push(dirBaton);
    }
    
    private FSTransactionInfo beginTransactionForCommit(long baseRevision) throws SVNException {
        /* Run start-commit hooks. */
        FSHooks.runStartCommitHook(myReposRootDir, myAuthor);
        /* Begin the transaction, ask for the fs to do on-the-fly lock checks. */
        FSTransactionInfo txn = FSWriter.beginTxn(baseRevision, FSConstants.SVN_FS_TXN_CHECK_LOCKS, myRevNodesPool, myReposRootDir);
        /* We pass the author and log message to the filesystem by adding
         * them as properties on the txn.  Later, when we commit the txn,
         * these properties will be copied into the newly created revision. 
         */
        /* User (author). */
        if(myAuthor != null && !"".equals(myAuthor)){
            FSWriter.setTransactionProperty(myReposRootDir, txn.getTxnId(), SVNRevisionProperty.AUTHOR, myAuthor);
        }
        /* Log message. */
        if(myLogMessage != null && !"".equals(myLogMessage)){
            FSWriter.setTransactionProperty(myReposRootDir, txn.getTxnId(), SVNRevisionProperty.LOG, myLogMessage);
        }
        return txn; 
    }

    public void openDir(String path, long revision) throws SVNException {
        DirBaton parentBaton = (DirBaton)myDirsStack.peek();
        String fullPath = SVNPathUtil.concatToAbs(myBasePath, path); 
        /* Check path in our transaction.  If it does not exist,
         * return a 'Path not present' error. 
         */
        SVNNodeKind kind = myRepository.checkNodeKind(fullPath, myTxnRoot, -1);
        if(kind == SVNNodeKind.NONE){
            SVNErrorManager.error("Path '" + path + "' not present");
        }
        /* Build a new dir baton for this directory */
        DirBaton dirBaton = new DirBaton(revision, fullPath, parentBaton.isCopied());  
        myDirsStack.push(dirBaton);
    }
    
    public void deleteEntry(String path, long revision) throws SVNException {
        DirBaton parentBaton = (DirBaton)myDirsStack.peek();
        String fullPath = SVNPathUtil.concatToAbs(myBasePath, path); 
        /* Check path in our transaction.  */
        SVNNodeKind kind = myRepository.checkNodeKind(fullPath, myTxnRoot, -1);
        /* If path doesn't exist in the txn, that's fine (merge
         * allows this). 
         */
        if(kind == SVNNodeKind.NONE){
            return;
        }
        /* Now, make sure we're deleting the node that we think we're
         * deleting, else return an out-of-dateness error. 
         */
        FSRevisionNode existingNode = myRevNodesPool.getRevisionNode(myTxnRoot, fullPath, myReposRootDir);
        long createdRev = existingNode.getId().getRevision();
        if(FSRepository.isValidRevision(revision) && revision < createdRev){
            SVNErrorManager.error("Out of date: '" + fullPath + "' in transaction '" + myTxnRoot.getTxnId() + "'");
        }
        /* Delete files and recursively delete
         * directories.  
         */
        deleteNode(myTxnRoot, fullPath);
    }
    
    /* Delete the node at path under root.  root must be a transaction
     * root. 
     */
    private void deleteNode(FSRoot root, String path) throws SVNException {
        String txnId = root.getTxnId();
        if(!root.isTxnRoot()){
            SVNErrorManager.error("Root object must be a transaction root");
        }
        FSParentPath parentPath = myRevNodesPool.getParentPath(root, path, true, myReposRootDir);
        /* We can't remove the root of the filesystem.  */
        if(parentPath.getParent() == null){
            SVNErrorManager.error("The root directory cannot be deleted");
        }
        /* Check to see if path (or any child thereof) is locked; if so,
         * check that we can use the existing lock(s). 
         */
        if((root.getTxnFlags() & FSConstants.SVN_FS_TXN_CHECK_LOCKS) != 0){
            FSReader.allowLockedOperation(path, myAuthor, myLockTokens.values(), true, false, myReposRootDir);            
        }
        /* Make the parent directory mutable, and do the deletion.  */
        makePathMutable(root, parentPath.getParent(), path);
        deleteEntry(parentPath.getParent().getRevNode(), parentPath.getNameEntry(), txnId);
        /* Remove this node and any children from the path cache. */
        root.removeRevNodeFromCache(parentPath.getAbsPath());
        /* Make a record of this modification in the changes table. */
        addChange(txnId, path, parentPath.getRevNode().getId(), FSPathChangeKind.FS_PATH_CHANGE_DELETE, false, false, FSConstants.SVN_INVALID_REVNUM, null);
    }
    
    /* Delete the directory entry named entryName from parent. parent must be 
     * mutable. entryName must be a single path component. Throws an exception if there is no 
     * entry entryName in parent.  
     */
    private void deleteEntry(FSRevisionNode parent, String entryName, String txnId) throws SVNException {
        /* Make sure parent is a directory. */
        if(parent.getType() != SVNNodeKind.DIR){
            SVNErrorManager.error("Attempted to delete entry '" + entryName + "' from *non*-directory node");
        }
        /* Make sure parent is mutable. */
        if(!parent.getId().isTxn()){
            SVNErrorManager.error("Attempted to delete entry '" + entryName + "' from immutable directory node");
        }
        /* Make sure that entryName is a single path component. */
        if(!SVNPathUtil.isSinglePathComponent(entryName)){
            SVNErrorManager.error("Attempted to delete a node with an illegal name '" + entryName + "'");
        }
        /* Get a dirent hash for this directory. */
        Map entries = FSReader.getDirEntries(parent, myReposRootDir);
        /* Find name in the entries hash. */
        FSEntry dirEntry = (FSEntry)entries.get(entryName);
        /* If we never found id in entries (perhaps because there are no
         * entries or maybe because just there's no such id in the existing 
         * entries... it doesn't matter), throw an exception.  
         */
        if(dirEntry == null){
            SVNErrorManager.error("Delete failed--directory has no entry '" + entryName + "'");
        }
        /* Use the id to get the entry's node.  */
        /* TODO: Well, I don't understand this place - why svn devs try to get 
         * the node revision here, - just to act only as a sanity check or what?
         * The read out node-rev is not used then. The node is got then in 
         * ...delete_if_mutable. So, that is already a check, but when it's really 
         * needed.   
         */
        FSReader.getRevNodeFromID(myReposRootDir, dirEntry.getId());
        /* If mutable, remove it and any mutable children from fs. */
        deleteEntryIfMutable(dirEntry.getId(), txnId);
        /* Remove this entry from its parent's entries list. */
        FSWriter.setEntry(parent, entryName, null, SVNNodeKind.UNKNOWN, txnId, myReposRootDir);
    }
    
    private void deleteEntryIfMutable(FSID id, String txnId) throws SVNException {
        /* Get the node. */
        FSRevisionNode node = FSReader.getRevNodeFromID(myReposRootDir, id);
        /* If immutable, do nothing and return immediately. */
        if(!node.getId().isTxn()){
            return;
        }
        /* Else it's mutable.  Recurse on directories... */
        if(node.getType() == SVNNodeKind.DIR){
            /* Loop over hash entries */
            Map entries = FSReader.getDirEntries(node, myReposRootDir);
            for(Iterator names = entries.keySet().iterator(); names.hasNext();){
                String name = (String)names.next();
                FSEntry entry = (FSEntry)entries.get(name);
                deleteEntryIfMutable(entry.getId(), txnId);
            }
        }
        /* ... then delete the node itself, after deleting any mutable
         * representations and strings it points to. 
         */
        FSWriter.removeRevisionNode(id, myReposRootDir);
    }
    
    public void absentDir(String path) throws SVNException {
        //does nothing
    }

    public void absentFile(String path) throws SVNException {
        //does nothing
    }

    public void addDir(String path, String copyFromPath, long copyFromRevision) throws SVNException {
        DirBaton parentBaton = (DirBaton)myDirsStack.peek();
        String fullPath = SVNPathUtil.concatToAbs(myBasePath, path); 
        boolean isCopied = false;
        /* Sanity check. */  
        if(copyFromPath != null && FSRepository.isInvalidRevision(copyFromRevision)){
            SVNErrorManager.error("Got source path but no source revision for '" + fullPath + "'");
        }else if(copyFromPath != null){
            /* Check path in our transaction.  Make sure it does not exist
             * unless its parent directory was copied (in which case, the
             * thing might have been copied in as well), else return an
             * out-of-dateness error. 
             */
            SVNNodeKind kind = myRepository.checkNodeKind(fullPath, myTxnRoot, -1);
            if(kind != SVNNodeKind.NONE && !parentBaton.isCopied()){
                SVNErrorManager.error("Out of date: '" + fullPath + "' in transaction '" + myTxnRoot.getTxnId() + "'");
            }
            copyFromPath = SVNPathUtil.concatToAbs(myBasePath, SVNEncodingUtil.uriDecode(copyFromPath));
            /* Now use the copyFromPath as an absolute path within the
             * repository to make the copy from. 
             */      
            FSRoot copyRoot = new FSRoot(copyFromRevision, myRevNodesPool.getRootRevisionNode(copyFromRevision, myReposRootDir));
            makeCopy(copyRoot, copyFromPath, myTxnRoot, fullPath, true);
            isCopied = true;
        }else{
            /* No ancestry given, just make a new directory.  We don't
             * bother with an out-of-dateness check here because
             * makeDir() will error out if path already exists.
             */
            makeDir(myTxnRoot, fullPath);
        }
        /* Build a new dir baton for this directory. */
        DirBaton dirBaton = new DirBaton(FSConstants.SVN_INVALID_REVNUM, fullPath, isCopied);  
        myDirsStack.push(dirBaton);
    }
    
    /* Create a new directory named "path" in "root".  The new directory has
     * no entries, and no properties.  root must be the root of a
     * transaction, not a revision.  
     */
    private void makeDir(FSRoot root, String path) throws SVNException {
        String txnId = root.getTxnId();
        FSParentPath parentPath = myRevNodesPool.getParentPath(root, path, false, myReposRootDir);
        /* If there's already a sub-directory by that name, complain.  This
         * also catches the case of trying to make a subdirectory named `/'.  
         */
        if(parentPath.getRevNode() != null){
            pathAlreadyExistsError(root, path);
        }
        /* Check (recursively) to see if some lock is 'reserving' a path at
         * that location, or even some child-path; if so, check that we can
         * use it. 
         */
        if((root.getTxnFlags() & FSConstants.SVN_FS_TXN_CHECK_LOCKS) != 0){
            FSReader.allowLockedOperation(path, myAuthor, myLockTokens.values(), true, false, myReposRootDir);            
        }
        /* Create the subdirectory.  */
        makePathMutable(root, parentPath.getParent(), path);
        FSRevisionNode subDirNode = makeEntry(parentPath.getParent().getRevNode(), parentPath.getParent().getAbsPath(), parentPath.getNameEntry(), true, txnId);
        /* Add this directory to the path cache. */
        root.putRevNodeToCache(parentPath.getAbsPath(), subDirNode);
        /* Make a record of this modification in the changes table. */
        addChange(txnId, path, subDirNode.getId(), FSPathChangeKind.FS_PATH_CHANGE_ADD, false, false, FSConstants.SVN_INVALID_REVNUM, null);
    }
    
    /* Make a new entry named entryName in parent. If isDir is true, then the
     * node revision the new entry points to will be a directory, else it
     * will be a file. parent must be mutable, and must not have an entry named 
     * entryName.  
     */
    private FSRevisionNode makeEntry(FSRevisionNode parent, String parentPath, String entryName, boolean isDir, String txnId) throws SVNException {
        /* Make sure that entryName is a single path component. */
        if(!SVNPathUtil.isSinglePathComponent(entryName)){
            SVNErrorManager.error("Attempted to create a node with an illegal name '" + entryName + "'");
        }
        /* Make sure that parent is a directory */
        if(parent.getType() != SVNNodeKind.DIR){
            SVNErrorManager.error("Attempted to create entry in non-directory parent");
        }
        /* Check that the parent is mutable. */
        if(!parent.getId().isTxn()){
            SVNErrorManager.error("Attempted to clone child of non-mutable node");
        }
        /* Create the new node's node-revision */
        FSRevisionNode newRevNode = new FSRevisionNode();
        newRevNode.setType(isDir ? SVNNodeKind.DIR : SVNNodeKind.FILE);
        newRevNode.setCreatedPath(SVNPathUtil.concatToAbs(parentPath, entryName));
        newRevNode.setCopyRootPath(parent.getCopyRootPath());
        newRevNode.setCopyRootRevision(parent.getCopyRootRevision());
        newRevNode.setCopyFromRevision(FSConstants.SVN_INVALID_REVNUM);
        newRevNode.setCopyFromPath(null);
        FSID newNodeId = createNode(newRevNode, parent.getId().getCopyID(), txnId);
        /* Get a new node for our new node */
        FSRevisionNode childNode = FSReader.getRevNodeFromID(myReposRootDir, newNodeId);
        /* We can safely call setEntry() because we already know that
         * parent is mutable, and we just created childNode, so we know it has
         * no ancestors (therefore, parent cannot be an ancestor of child) 
         */
        FSWriter.setEntry(parent, entryName, childNode.getId(), newRevNode.getType(), txnId, myReposRootDir);
        return childNode;
    }

    private FSID createNode(FSRevisionNode revNode, String copyId, String txnId) throws SVNException {
        /* Get a new node-id for this node. */
        String nodeId = getNewTxnNodeId(txnId);
        FSID id = FSID.createTxnId(nodeId, copyId, txnId);
        revNode.setId(id);
        FSWriter.putTxnRevisionNode(id, revNode, myReposRootDir);
        return id;
    }

    /* Get a new and unique to this transaction node-id for transaction
     * txnId.
     */
    private String getNewTxnNodeId(String txnId) throws SVNException {
        /* First read in the current next-ids file. */
        String[] curIds = FSReader.readNextIds(txnId, myReposRootDir);
        String curNodeId = curIds[0];
        String curCopyId = curIds[1];
        String nextNodeId = FSKeyGenerator.generateNextKey(curNodeId.toCharArray());
        FSWriter.writeNextIds(txnId, nextNodeId, curCopyId, myReposRootDir);
        return "_" + nextNodeId; 
    }
    
    private void pathAlreadyExistsError(FSRoot root, String path) throws SVNException {
        if(root.isTxnRoot()){
            SVNErrorManager.error("File already exists: filesystem '" + myReposRootDir.getAbsolutePath() + "', transaction '" + root.getTxnId() + "', path '" + path + "'");
        }else{
            SVNErrorManager.error("File already exists: filesystem '" + myReposRootDir.getAbsolutePath() + "', revision " + root.getRevision() + ", path '" + path + "'");
        }
    }
    
    /* Create a copy of fromPath in fromRoot named toPath in toRoot.
     * If fromPath is a directory, copy it recursively. If preserveHistory is true, 
     * then the copy is recorded in the copies table.
     */
    private void makeCopy(FSRoot fromRoot, String fromPath, FSRoot toRoot, String toPath, boolean preserveHistory) throws SVNException {
        String txnId = toRoot.getTxnId();
        if(fromRoot.isTxnRoot()){
            SVNErrorManager.error("Copy from mutable tree not currently supported");
        }
        /* Get the node for fromPath in fromRoot. */
        FSRevisionNode fromNode = myRevNodesPool.getRevisionNode(fromRoot, fromPath, myReposRootDir);
        /* Build up the parent path from toPath in toRoot.  If the last
         * component does not exist, it's not that big a deal.  We'll just
         * make one there. 
         */
        FSParentPath toParentPath = myRevNodesPool.getParentPath(toRoot, toPath, false, myReposRootDir);
        /* Check to see if path (or any child thereof) is locked; if so,
         * check that we can use the existing lock(s). 
         */
        if((toRoot.getTxnFlags() & FSConstants.SVN_FS_TXN_CHECK_LOCKS) != 0){
            FSReader.allowLockedOperation(toPath, myAuthor, myLockTokens.values(), true, false, myReposRootDir);
        }
        /* If the destination node already exists as the same node as the
         * source (in other words, this operation would result in nothing
         * happening at all), just do nothing an return successfully,
         * proud that you saved yourself from a tiresome task. 
         */
        if(toParentPath.getRevNode() != null && toParentPath.getRevNode().getId().equals(fromNode.getId())){
            return;
        }
        if(!fromRoot.isTxnRoot()){
            FSPathChangeKind changeKind;
            /* If toPath already existed prior to the copy, note that this
             * operation is a replacement, not an addition. 
             */
            if(toParentPath.getRevNode() != null){
                changeKind = FSPathChangeKind.FS_PATH_CHANGE_REPLACE;
            }else{
                changeKind = FSPathChangeKind.FS_PATH_CHANGE_ADD;
            }
            /* Make sure the target node's parents are mutable.  */
            makePathMutable(toRoot, toParentPath.getParent(), toPath);
            /* Canonicalize the copyfrom path. */
            String fromCanonPath = SVNPathUtil.canonicalizeAbsPath(fromPath);
            copy(toParentPath.getParent().getRevNode(), toParentPath.getNameEntry(), fromNode, preserveHistory, fromRoot.getRevision(), fromCanonPath, txnId);
            if(changeKind == FSPathChangeKind.FS_PATH_CHANGE_REPLACE){
                toRoot.removeRevNodeFromCache(toParentPath.getAbsPath());
            }
            /* Make a record of this modification in the changes table. */
            FSRevisionNode newNode = myRevNodesPool.getRevisionNode(toRoot, toPath, myReposRootDir);
            addChange(txnId, toPath, newNode.getId(), changeKind, false, false, fromRoot.getRevision(), fromCanonPath);
        }else{
            /* Copying from transaction roots not currently available.
               Note that when copying from mutable trees, you have to make sure that
               you aren't creating a cyclic graph filesystem, and a simple
               referencing operation won't cut it.  Currently, we should not
               be able to reach this clause, and the interface reports that
               this only works from immutable trees anyway, but this requirement 
               need not be necessary in the future.
            */
            SVNErrorManager.error("Copy from mutable tree not currently supported");
        }
    }

    /* Populating the 'changes' table. Add a change to the changes table in FS, keyed on transaction id,
     * and indicated that a change of kind "changeKind" occurred on
     * path (whose node revision id is - or was, in the case of a
     * deletion, - "id"), and optionally that text or props modifications
     * occurred.  If the change resulted from a copy, copyFromRevision and
     * copyFromPath specify under which revision and path the node was
     * copied from.  If this was not part of a copy, copyFromrevision should
     * be FSConstants.SVN_INVALID_REVNUM. 
     */
    private void addChange(String txnId, String path, FSID id, FSPathChangeKind changeKind, boolean textModified, boolean propsModified, long copyFromRevision, String copyFromPath) throws SVNException {
        OutputStream changesFile = null;
        try{
            changesFile = SVNFileUtil.openFileForWriting(FSRepositoryUtil.getTxnChangesFile(txnId, myReposRootDir), true);
            String copyfrom = "";
            if(FSRepository.isValidRevision(copyFromRevision)){
                copyfrom = copyFromRevision + " " + copyFromPath;
            }
            FSPathChange pathChange = new FSPathChange(id, changeKind, textModified, propsModified);
            FSWriter.writeChangeEntry(changesFile, path, pathChange, copyfrom);
        }catch(IOException ioe){
            SVNErrorManager.error("svn: Can't write to '" + FSRepositoryUtil.getTxnChangesFile(txnId, myReposRootDir).getAbsolutePath() + "': " + ioe.getMessage());
        }finally{
            SVNFileUtil.closeFile(changesFile);
        }
    }
    
    private void copy(FSRevisionNode toNode, String entryName, FSRevisionNode fromNode, boolean preserveHistory, long fromRevision, String fromPath, String txnId) throws SVNException {
        FSID id = null;
        if(preserveHistory){
            FSID srcId = fromNode.getId();
            /* Make a copy of the original node revision. */
            FSRevisionNode toRevNode = FSRevisionNode.dumpRevisionNode(fromNode);
            /* Reserve a copy ID for this new copy. */
            String copyId = reserveCopyId(txnId);
            /* Create a successor with its predecessor pointing at the copy
             * source. 
             */
            toRevNode.setPredecessorId(new FSID(srcId));
            if(toRevNode.getCount() != -1){
                toRevNode.setCount(toRevNode.getCount() + 1);
            }
            toRevNode.setCreatedPath(SVNPathUtil.concatToAbs(toNode.getCreatedPath(), entryName));
            toRevNode.setCopyFromPath(fromPath);
            toRevNode.setCopyFromRevision(fromRevision);
            /* Set the copyroot equal to our own id. */
            toRevNode.setCopyRootPath(null);
            id = FSWriter.createSuccessor(srcId, toRevNode, copyId, txnId, myReposRootDir);
        }else{
            /* don't preserve history */
            id = fromNode.getId();
        }
        /* Set the entry in toNode to the new id. */
        FSWriter.setEntry(toNode, entryName, id, fromNode.getType(), txnId, myReposRootDir);
    }
    
    public void changeDirProperty(String name, String value) throws SVNException {
        DirBaton dirBaton = (DirBaton)myDirsStack.peek();
        if(FSRepository.isValidRevision(dirBaton.getBaseRevision())){
            /* Subversion rule:  propchanges can only happen on a directory
             * which is up-to-date. 
             */
            FSRevisionNode existingNode = myRevNodesPool.getRevisionNode(myTxnRoot, dirBaton.getPath(), myReposRootDir);
            long createdRev = existingNode.getId().getRevision();
            if(dirBaton.getBaseRevision() < createdRev){
                SVNErrorManager.error("Out of date: '" + dirBaton.getPath() + "' in transaction '" + myTxnRoot.getTxnId() + "'");
            }
        }
    }

    /* Change, add, or delete a node's property value.  The node affect is
     * path under root, the property value to modify is propName, and propValue
     * points to either a string value to set the new contents to, or null
     * if the property should be deleted. 
     */
    private void changeNodeProperty(FSRoot root, String path, String propName, String propValue) throws SVNException {
        /* Validate the property. */
        if(!SVNProperty.isRegularProperty(propName)){
            SVNErrorManager.error("Storage of non-regular property '" + propName + "' is disallowed through the repository interface, and could indicate a bug in your client");
        }
    }
    
    public void closeDir() throws SVNException {
        myDirsStack.pop();
    }

    public void addFile(String path, String copyFromPath, long copyFromRevision) throws SVNException {
        DirBaton parentBaton = (DirBaton)myDirsStack.peek();
        String fullPath = SVNPathUtil.concatToAbs(myBasePath, path); 
        /* Sanity check. */  
        if(copyFromPath != null && FSRepository.isInvalidRevision(copyFromRevision)){
            SVNErrorManager.error("Got source path but no source revision for '" + fullPath + "'");
        }else if(copyFromPath != null){
            /* Check path in our transaction.  Make sure it does not exist
             * unless its parent directory was copied (in which case, the
             * thing might have been copied in as well), else return an
             * out-of-dateness error. 
             */
            SVNNodeKind kind = myRepository.checkNodeKind(fullPath, myTxnRoot, -1);
            if(kind != SVNNodeKind.NONE && !parentBaton.isCopied()){
                SVNErrorManager.error("Out of date: '" + fullPath + "' in transaction '" + myTxnRoot.getTxnId() + "'");
            }
            copyFromPath = SVNPathUtil.concatToAbs(myBasePath, SVNEncodingUtil.uriDecode(copyFromPath));
            /* Now use the copyFromPath as an absolute path within the
             * repository to make the copy from. 
             */      
            FSRoot copyRoot = new FSRoot(copyFromRevision, myRevNodesPool.getRootRevisionNode(copyFromRevision, myReposRootDir));
            makeCopy(copyRoot, copyFromPath, myTxnRoot, fullPath, true);
        }else{
            /* No ancestry given, just make a new, empty file.  Note that we
             * don't perform an existence check here like the copy-from case
             * does -- that's because makeFile() already errors out
             * if the file already exists.
             */
            makeFile(myTxnRoot, fullPath);
        }
    }

    /* Create an empty file path under the root.
     */
    private void makeFile(FSRoot root, String path) throws SVNException {
        String txnId = root.getTxnId();
        FSParentPath parentPath = myRevNodesPool.getParentPath(root, path, false, myReposRootDir);
        /* If there's already a file by that name, complain.
         * This also catches the case of trying to make a file named `/'.  
         */
        if(parentPath.getRevNode() != null){
            pathAlreadyExistsError(root, path);
        }
        /* Check (non-recursively) to see if path is locked;  if so, check
         * that we can use it. 
         */
        if((root.getTxnFlags() & FSConstants.SVN_FS_TXN_CHECK_LOCKS) != 0){
            FSReader.allowLockedOperation(path, myAuthor, myLockTokens.values(), false, false, myReposRootDir);            
        }
        /* Create the file.  */
        makePathMutable(root, parentPath.getParent(), path);
        FSRevisionNode childNode = makeEntry(parentPath.getParent().getRevNode(), parentPath.getParent().getAbsPath(), parentPath.getNameEntry(), false, txnId);
        /* Add this file to the path cache. */
        root.putRevNodeToCache(parentPath.getAbsPath(), childNode);
        /* Make a record of this modification in the changes table. */
        addChange(txnId, path, childNode.getId(), FSPathChangeKind.FS_PATH_CHANGE_ADD, false, false, FSConstants.SVN_INVALID_REVNUM, null);
    }

    public void openFile(String path, long revision) throws SVNException {
        DirBaton parentBaton = (DirBaton)myDirsStack.peek();
        String fullPath = SVNPathUtil.concatToAbs(myBasePath, path); 
        /* Get this node's node-rev (doubles as an existence check). */
        FSRevisionNode revNode = myRevNodesPool.getRevisionNode(myTxnRoot, fullPath, myReposRootDir);
        /* If the node our caller has is an older revision number than the
         * one in our transaction, return an out-of-dateness error. 
         */
        if(FSRepository.isValidRevision(revision) && revision < revNode.getId().getRevision()){
            SVNErrorManager.error("Out of date: '" + fullPath + "' in transaction '" + myTxnRoot.getTxnId() + "'");
        }
    }

    public void applyTextDelta(String path, String baseChecksum) throws SVNException {
        /* Call getParentPath() with the flag entryMustExist set to true, as we 
         * want this to return an error if the node for which we are searching 
         * doesn't exist. 
         */
        DirBaton parentBaton = (DirBaton)myDirsStack.peek();
        String fullPath = SVNPathUtil.concatToAbs(myBasePath, path);
        FSParentPath parentPath = myRevNodesPool.getParentPath(myTxnRoot, fullPath, true, myReposRootDir);
        /* Check (non-recursively) to see if path is locked; if so, check
         * that we can use it. 
         */
        if((myTxnRoot.getTxnFlags() & FSConstants.SVN_FS_TXN_CHECK_LOCKS) != 0){
            FSReader.allowLockedOperation(fullPath, myAuthor, myLockTokens.values(), false, false, myReposRootDir);
        }
        /* Now, make sure this path is mutable. */
        makePathMutable(myTxnRoot, parentPath, fullPath);
        FSRevisionNode node = parentPath.getRevNode();
        if(baseChecksum != null){
            /* Until we finalize the node, its textRepresentation points to the old
             * contents, in other words, the base text. 
             */
            String md5HexChecksum = FSRepositoryUtil.getFileChecksum(node);
            if(md5HexChecksum != null && !md5HexChecksum.equals(baseChecksum)){
                String eol = SVNFileUtil.getNativeEOLMarker();
                SVNErrorManager.error("Base checksum mismatch on '" + path + "':" + eol + "   expected:  " + baseChecksum + eol + "     actual:  " + md5HexChecksum + eol);
            }
        }
        /* Make a readable "source" stream out of the current contents of
         * ROOT/PATH; obviously, this must be done in the context of a db_txn.
         * The stream is returned in tb->source_stream. */

    }
    
    /* Make the node referred to by parentPath mutable, if it isn't
     * already.  root must be the root from which
     * parentPath descends.  Clone any parent directories as needed.
     * Adjust the dag nodes in parentPath to refer to the clones.  Use
     * errorPath in error messages.  */
    private void makePathMutable(FSRoot root, FSParentPath parentPath, String errorPath) throws SVNException {
        String txnId = root.getTxnId();
        /* Is the node mutable already?  */
        if(parentPath.getRevNode().getId().isTxn()){
            return;
        }
        FSRevisionNode clone = null;
        /* Are we trying to clone the root, or somebody's child node?  */
        if(parentPath.getParent() != null){
            /* We're trying to clone somebody's child.  Make sure our parent
             * is mutable.  
             */
            makePathMutable(root, parentPath.getParent(), errorPath);
            FSID parentId = null;
            String copyId = null;
            switch(parentPath.getCopyStyle()){
                case FSParentPath.COPY_ID_INHERIT_PARENT:
                    parentId = parentPath.getParent().getRevNode().getId();
                    copyId = parentId.getCopyID();
                    break;
                case FSParentPath.COPY_ID_INHERIT_NEW:
                    copyId = reserveCopyId(txnId);
                    break;
                case FSParentPath.COPY_ID_INHERIT_SELF:
                    break;
                case FSParentPath.COPY_ID_INHERIT_UNKNOWN:
                    //well, svn aborts here, should we do the same?
                    /* uh-oh -- somebody didn't calculate copy-ID
                     * inheritance data. 
                     */                    
                    SVNErrorManager.error("FATAL error: can not make path mutable");;
            }
            /* Determine what copyroot our new child node should use. */
            String copyRootPath = parentPath.getRevNode().getCopyRootPath();
            long copyRootRevision = parentPath.getRevNode().getCopyRootRevision();
            FSRevisionNode copyRootNode = myRevNodesPool.getRevisionNode(copyRootRevision, copyRootPath, myReposRootDir);
            FSID childId = parentPath.getRevNode().getId();
            FSID copyRootId = copyRootNode.getId();
            boolean isParentCopyRoot = false;
            if(!childId.getNodeID().equals(copyRootId.getNodeID())){
                isParentCopyRoot = true;
            }
            /* Now make this node mutable.  */
            String clonePath = parentPath.getParent().getAbsPath();
            clone = FSWriter.cloneChild(parentPath.getParent().getRevNode(), clonePath, parentPath.getNameEntry(), copyId, txnId, isParentCopyRoot, myReposRootDir);
            /* Update the path cache. */
            root.putRevNodeToCache(parentPath.getAbsPath(), clone);
        }else{
            /* We're trying to clone the root directory.  */
            if(root.isTxnRoot()){
                /* Get the node id's of the root directories of the transaction 
                 * and its base revision.  
                 */
                FSTransaction txn = FSReader.getTxn(txnId, myReposRootDir);
                /* If they're the same, we haven't cloned the transaction's 
                 * root directory yet. 
                 */
                if(txn.getRootId().equals(txn.getBaseId())){
                    SVNErrorManager.error("FATAL error: txn '" + txnId + "', txn root id '" + txn.getRootId() + "', txn base id '" + txn.getBaseId() + "'");
                }
                /* One way or another, root_id now identifies a cloned root node. */
                clone = FSReader.getRevNodeFromID(myReposRootDir, txn.getRootId());
            }else{
                /* If it's not a transaction root, we can't change its contents.  */
                SVNErrorManager.error("File is not mutable: filesystem '" + myReposRootDir.getAbsolutePath() + "', revision " + root.getRevision() + ", path '" + errorPath + "'");
            }
        }
        /* Update the parentPath link to refer to the clone.  */
        parentPath.setRevNode(clone);
    }
    
    private String reserveCopyId(String txnId) throws SVNException {
        /* First read in the current next-ids file. */
        String[] nextIds = FSReader.readNextIds(txnId, myReposRootDir);
        String copyId = FSKeyGenerator.generateNextKey(nextIds[1].toCharArray());
        FSWriter.writeNextIds(txnId, nextIds[0], copyId, myReposRootDir);
        return "_" + nextIds[1];
    }
    
    public OutputStream textDeltaChunk(String path, SVNDiffWindow diffWindow) throws SVNException {
        return null;
    }

    public void textDeltaEnd(String path) throws SVNException {
    }

    public void changeFileProperty(String path, String name, String value) throws SVNException {
    }

    public void closeFile(String path, String textChecksum) throws SVNException {
    }

    public SVNCommitInfo closeEdit() throws SVNException {
        return null;
    }

    public void abortEdit() throws SVNException {
    }
    
    private class DirBaton {
        /* the revision I'm based on  */
        private long myBaseRevision;
        /* the -absolute- path to this dir in the fs */
        private String myPath;
        /* was this directory added with history? */
        private boolean isCopied;
        
        public DirBaton(long revision, String path, boolean copied) {
            myBaseRevision = revision;
            myPath = path;
            isCopied = copied;
        }
        
        public boolean isCopied() {
            return isCopied;
        }
        
        public void setCopied(boolean isCopied) {
            this.isCopied = isCopied;
        }
        
        public long getBaseRevision() {
            return myBaseRevision;
        }
        
        public void setBaseRevision(long baseRevision) {
            myBaseRevision = baseRevision;
        }
        
        public String getPath() {
            return myPath;
        }
        
        public void setPath(String path) {
            myPath = path;
        }
    }
}
