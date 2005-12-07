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
import java.util.Stack;
import java.io.File;

import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNRevisionProperty;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.ISVNWorkspaceMediator;
import org.tmatesoft.svn.core.io.diff.SVNDiffWindow;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;


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
    private FSTransaction myTxn;
    private boolean isTxnOwner;
    private boolean keepLocks;
    private File myReposRootDir;
    private FSRepository myRepository;
    private Stack myDirsStack;
    
    public FSCommitEditor(String path, String logMessage, String userName, Map lockTokens, boolean keepLocks, ISVNWorkspaceMediator mediator, FSTransaction txn, FSRepository repository){
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
        DirBaton dirBaton = new DirBaton(revision, myBasePath, false);  
        myDirsStack.push(dirBaton);
    }
    
    private FSTransaction beginTransactionForCommit(long baseRevision) throws SVNException {
        /* Run start-commit hooks. */
        FSHooks.runStartCommitHook(myReposRootDir, myAuthor);
        /* Begin the transaction, ask for the fs to do on-the-fly lock checks. */
        FSTransaction txn = FSWriter.beginTxn(baseRevision, FSConstants.SVN_FS_TXN_CHECK_LOCKS, myRepository.getRevisionNodePool(), myReposRootDir);
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
        //TODO: fix it in SVNPathUtil.append
        String fullPath = "/".equals(parentBaton.getPath()) ? parentBaton.getPath() + path : SVNPathUtil.append(parentBaton.getPath(), path); 
        /* Check path in our transaction.  If it does not exist,
         * return a 'Path not present' error. 
         */

    }
    
    public void deleteEntry(String path, long revision) throws SVNException {
    }

    public void absentDir(String path) throws SVNException {
    }

    public void absentFile(String path) throws SVNException {
    }

    public void addDir(String path, String copyFromPath, long copyFromRevision) throws SVNException {
    }

    public void changeDirProperty(String name, String value) throws SVNException {
    }

    public void closeDir() throws SVNException {
    }

    public void addFile(String path, String copyFromPath, long copyFromRevision) throws SVNException {
    }

    public void openFile(String path, long revision) throws SVNException {
    }

    public void applyTextDelta(String path, String baseChecksum) throws SVNException {
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
