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
import java.util.Map;
import java.util.Collection;
import java.util.LinkedList;
import java.util.Stack;
import java.io.File;

import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNRevisionProperty;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.ISVNWorkspaceMediator;
import org.tmatesoft.svn.core.io.diff.SVNDiffWindow;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;

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
        FSTransactionInfo txn = FSWriter.beginTxn(baseRevision, FSConstants.SVN_FS_TXN_CHECK_LOCKS, myRepository.getRevisionNodePool(), myReposRootDir);
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
        String fullPath = SVNPathUtil.concatToAbs(parentBaton.getPath(), path); 
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
        
    }

    public void absentDir(String path) throws SVNException {
        //does nothing
    }

    public void absentFile(String path) throws SVNException {
        //does nothing
    }

    public void addDir(String path, String copyFromPath, long copyFromRevision) throws SVNException {
    }

    public void changeDirProperty(String name, String value) throws SVNException {
    }

    public void closeDir() throws SVNException {
        myDirsStack.pop();
    }

    public void addFile(String path, String copyFromPath, long copyFromRevision) throws SVNException {
    }

    public void openFile(String path, long revision) throws SVNException {
        DirBaton parentBaton = (DirBaton)myDirsStack.peek();
        String fullPath = SVNPathUtil.concatToAbs(parentBaton.getPath(), path); 
        /* Get this node's node-rev (doubles as an existence check). */
        FSRevisionNode revNode = myRepository.getRevisionNodePool().getRevisionNode(myTxnRoot, fullPath, myReposRootDir);
        /* If the node our caller has is an older revision number than the
        one in our transaction, return an out-of-dateness error. */
        if(FSRepository.isValidRevision(revision) && revision < revNode.getId().getRevision()){
            SVNErrorManager.error("Out of date: '" + fullPath + "' in transaction '" + myTxnRoot.getTxnId() + "'");
        }
    }

    public void applyTextDelta(String path, String baseChecksum) throws SVNException {
        /* Call getParentPath with the flag entryMustExist set to true, as we 
         * want this to return an error if the node for which we are searching 
         * doesn't exist. 
         */
        DirBaton parentBaton = (DirBaton)myDirsStack.peek();
        String fullPath = SVNPathUtil.concatToAbs(parentBaton.getPath(), path);
        FSParentPath parentPath = myRepository.getRevisionNodePool().getParentPath(myTxnRoot, fullPath, true, myReposRootDir);
        /* Check (non-recursively) to see if path is locked; if so, check
         * that we can use it. 
         */
        if((myTxnRoot.getTxnFlags() & FSConstants.SVN_FS_TXN_CHECK_LOCKS) != 0){
            FSReader.allowLockedOperation(fullPath, myAuthor, myLockTokens.values(), false, false, myReposRootDir);
        }
        /* Now, make sure this path is mutable. */

    }

    /* Make the node referred to by parentPath mutable, if it isn't
     * already.  root must be the root from which
     * parentPath descends.  Clone any parent directories as needed.
     * Adjust the dag nodes in PARENT_PATH to refer to the clones.  Use
     * ERROR_PATH in error messages.  */
    private void makePathMutable(FSRoot root, FSParentPath parentPath, String errorPath, File reposRootDir) throws SVNException {
        String txnId = root.getTxnId();
        /* Is the node mutable already?  */
        if(parentPath.getRevNode().getId().isTxn()){
            return;
        }
        /* Are we trying to clone the root, or somebody's child node?  */
        if(parentPath.getParent() != null){
            /* We're trying to clone somebody's child.  Make sure our parent
             * is mutable.  
             */
            makePathMutable(root, parentPath.getParent(), errorPath, reposRootDir);
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
            FSRevisionNode copyRootNode = myRepository.getRevisionNodePool().getRevisionNode(copyRootRevision, copyRootPath, myReposRootDir);
            FSID childId = parentPath.getRevNode().getId();
            FSID copyRootId = copyRootNode.getId();
            boolean isParentCopyRoot = false;
            if(!childId.getNodeID().equals(copyRootId.getNodeID())){
                isParentCopyRoot = true;
            }
            /* Now make this node mutable.  */
            String clonePath = FSRepositoryUtil.getAbsParentPath(parentPath.getParent());
            
        }
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
