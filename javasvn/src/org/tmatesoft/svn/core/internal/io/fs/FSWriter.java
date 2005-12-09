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
import java.io.IOException;
import java.io.FileOutputStream;
import java.io.FileNotFoundException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.HashMap;
import java.util.Date;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLock;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc.SVNProperties;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.util.SVNTimeUtil;
import org.tmatesoft.svn.core.SVNRevisionProperty;
import org.tmatesoft.svn.core.SVNProperty;

/**
 * @version 1.0
 * @author  TMate Software Ltd.
 */
public class FSWriter {
    public static FSTransactionInfo beginTxn(long baseRevision, int flags, FSRevisionNodePool revNodesPool, File reposRootDir) throws SVNException {
        FSTransactionInfo txn = createTxn(baseRevision, revNodesPool, reposRootDir);
        /* Put a datestamp on the newly created txn, so we always know
         * exactly how old it is.  (This will help sysadmins identify
         * long-abandoned txns that may need to be manually removed.)  When
         * a txn is promoted to a revision, this property will be
         * automatically overwritten with a revision datestamp. 
         */
        String commitTime = SVNTimeUtil.formatDate(new Date(System.currentTimeMillis()));
        setTransactionProperty(reposRootDir, txn.getTxnId(), SVNRevisionProperty.DATE, commitTime);
        /* Set temporary txn props that represent the requested 'flags'
         * behaviors. 
         */
        if((flags & FSConstants.SVN_FS_TXN_CHECK_OUT_OF_DATENESS) != 0){
            setTransactionProperty(reposRootDir, txn.getTxnId(), SVNProperty.TXN_CHECK_OUT_OF_DATENESS, "true");
        }
        if((flags & FSConstants.SVN_FS_TXN_CHECK_LOCKS) != 0){
            setTransactionProperty(reposRootDir, txn.getTxnId(), SVNProperty.TXN_CHECK_LOCKS, "true");
        }
        return txn;
    }
    
    //create txn dir & necessary files in the fs
    public static FSTransactionInfo createTxn(long baseRevision, FSRevisionNodePool revNodesPool, File reposRootDir) throws SVNException {
        /* Get the txn id. */
        String txnId = FSWriter.createTxnDir(baseRevision, reposRootDir); 
        //TODO: add to FSTransactionInfo an equivalent of txn_vtable
        FSTransactionInfo txn = new FSTransactionInfo(baseRevision, txnId);
        FSRevisionNode root = revNodesPool.getRootRevisionNode(baseRevision, reposRootDir);// FSReader.getRootRevNode(reposRootDir, baseRevision)
        if(root == null){
            SVNErrorManager.error("svn: No such revision " + baseRevision);
        }
        /* Create a new root node for this transaction. */
        FSWriter.createNewTxnNodeRevisionFromRevision(txn.getTxnId(), root, reposRootDir);
        /* Create an empty rev file. */
        SVNFileUtil.createEmptyFile(FSRepositoryUtil.getTxnRevFile(txn.getTxnId(), reposRootDir));
        /* Create an empty changes file. */
        SVNFileUtil.createEmptyFile(FSRepositoryUtil.getTxnChangesFile(txn.getTxnId(), reposRootDir));
        /* Write the next-ids file. */
        OutputStream nextIdsFile = null;
        try{
            nextIdsFile = SVNFileUtil.openFileForWriting(FSRepositoryUtil.getTxnNextIdsFile(txn.getTxnId(), reposRootDir));
            nextIdsFile.write("0 0\n".getBytes());
        }catch(IOException ioe){
            SVNErrorManager.error("svn: Can't write to '" + FSRepositoryUtil.getTxnNextIdsFile(txn.getTxnId(), reposRootDir).getAbsolutePath() + "': " + ioe.getMessage());  
        }finally{
            SVNFileUtil.closeFile(nextIdsFile);
        }
        return txn;
    }
    
    /* Copy a source revision node into the current transaction txnId. */
    public static void createNewTxnNodeRevisionFromRevision(String txnId, FSRevisionNode sourceNode, File reposRootDir) throws SVNException {
        if(sourceNode.getId().isTxn()){
            SVNErrorManager.error("svn: Copying from transactions not allowed");
        }
        FSRevisionNode revNode = sourceNode.cloneRevisionNode(); 
        revNode.setPredecessorId(sourceNode.getId());
        revNode.setCount(revNode.getCount() + 1);
        revNode.setCopyFromPath(null);
        revNode.setCopyFromRevision(FSConstants.SVN_INVALID_REVNUM);
        /* For the transaction root, the copyroot never changes. */
        revNode.setId(new FSID(sourceNode.getId().getNodeID(), txnId, sourceNode.getId().getCopyID(), FSConstants.SVN_INVALID_REVNUM, -1));
        putTxnNodeRevision(revNode.getId(), revNode, reposRootDir);
    }

    private static void putTxnNodeRevision(FSID id, FSRevisionNode revNode, File reposRootDir) throws SVNException{
        if(!id.isTxn()){
            SVNErrorManager.error("svn: Attempted to write to non-transaction");
        }
        OutputStream revNodeFile = SVNFileUtil.openFileForWriting(FSRepositoryUtil.getTxnRevNodeFile(id, reposRootDir));
        try{
            writeTxnNodeRevision(revNodeFile, revNode);
        }catch(IOException ioe){
            SVNErrorManager.error("svn: Can't write to txn file");
        }finally{
            SVNFileUtil.closeFile(revNodeFile);
        }
    }

    /* Write the revision node revNode into the file. */
    private static void writeTxnNodeRevision(OutputStream revNodeFile, FSRevisionNode revNode) throws IOException{
        String id = FSConstants.HEADER_ID + ": " + revNode.getId() + "\n";
        revNodeFile.write(id.getBytes());
        String type = FSConstants.HEADER_TYPE + ": " + revNode.getType() + "\n";
        revNodeFile.write(type.getBytes());
        if(revNode.getPredecessorId() != null){
            String predId = FSConstants.HEADER_PRED + ": " + revNode.getPredecessorId() + "\n";
            revNodeFile.write(predId.getBytes());
        }
        String count = FSConstants.HEADER_COUNT + ": " + revNode.getCount() + "\n";
        revNodeFile.write(count.getBytes());
        if(revNode.getTextRepresentation() != null){
            String textRepresentation = FSConstants.HEADER_TEXT + ": " + (FSID.isTxn(revNode.getTextRepresentation().getTxnId()) && revNode.getType() == SVNNodeKind.DIR ? "-1" : revNode.getTextRepresentation().toString()) + "\n";
            revNodeFile.write(textRepresentation.getBytes());
        }
        if(revNode.getPropsRepresentation() != null){
            String propsRepresentation = FSConstants.HEADER_PROPS + ": " + (FSID.isTxn(revNode.getPropsRepresentation().getTxnId()) ? "-1" : revNode.getPropsRepresentation().toString()) + "\n";
            revNodeFile.write(propsRepresentation.getBytes());
        }
        String cpath = FSConstants.HEADER_COUNT + ": " + revNode.getCount() + "\n";
        revNodeFile.write(cpath.getBytes());
        if(revNode.getCopyFromPath() != null){
            String copyFromPath = FSConstants.HEADER_COPYFROM + ": " + revNode.getCopyFromRevision() + " " + revNode.getCopyFromPath() + "\n";
            revNodeFile.write(copyFromPath.getBytes());
        }
        if(revNode.getCopyRootRevision() != revNode.getId().getRevision() || !revNode.getCopyRootPath().equals(revNode.getCreatedPath())){
            String copyroot = FSConstants.HEADER_COPYROOT + ": " + revNode.getCopyRootRevision() + " " + revNode.getCopyRootPath() + "\n";
            revNodeFile.write(copyroot.getBytes());
        }
        revNodeFile.write("\n".getBytes());
    }
    
    /* Create a unique directory for a transaction in FS based on the 
     * provided revision. Return the ID for this transaction. 
     */
    public static String createTxnDir(long revision, File reposRootDir) throws SVNException {
        File parent = FSRepositoryUtil.getTransactionsDir(reposRootDir);
        File uniquePath = null;
        /* Try to create directories named "<txndir>/<rev>-<uniquifier>.txn". */
        for (int i = 1; i < 99999; i++) {
            uniquePath = new File(parent, revision + "-" + i + FSConstants.TXN_PATH_EXT);
            if (!uniquePath.exists() && uniquePath.mkdirs()) {
                /* We succeeded.  Return the basename minus the ".txn" extension. */
                return revision + "-" + i;
            }
        }
        SVNErrorManager.error("svn: Unable to create transaction directory in '" + parent.getAbsolutePath() + "' for revision " + revision);
        return null;
    }
    
    public static void writePathInfoToReportFile(OutputStream tmpFileOS, String target, String path, String linkPath, String lockToken, long revision, boolean startEmpty) throws IOException {
        String anchorRelativePath = SVNPathUtil.append(target, path);
        String linkPathRep = linkPath != null ? "+" + linkPath.length() + ":" + linkPath : "-";
        String revisionRep = FSRepository.isValidRevision(revision) ? "+" + revision + ":" : "-";
        String lockTokenRep = lockToken != null ? "+" + lockToken.length() + ":" + lockToken : "-";
        String startEmptyRep = startEmpty ? "+" : "-";
        String fullRepresentation = "+" + anchorRelativePath.length() + ":" + anchorRelativePath + linkPathRep + revisionRep + startEmptyRep + lockTokenRep;
        tmpFileOS.write(fullRepresentation.getBytes());
    }
    
    /* Delete LOCK from FS in the actual OS filesystem. */
    public static void deleteLock(SVNLock lock, File reposRootDir) throws SVNException {
        String reposPath = lock.getPath();
        String childToKill = null;
        Collection children = new ArrayList();;
        while(true){
            FSReader.fetchLock(reposPath, children, reposRootDir);
            if(childToKill != null){
                children.remove(childToKill);
            }
            
            /* Delete the lock.*/
            if(children.size() == 0){
                /* Special case:  no goodz, no file.  And remember to nix
                 * the entry for it in its parent. 
                 */
                childToKill = FSRepositoryUtil.getDigestFromRepositoryPath(reposPath);
                File digestFile = FSRepositoryUtil.getDigestFileFromRepositoryPath(reposPath, reposRootDir);
                SVNFileUtil.deleteFile(digestFile);
            }else{
                FSWriter.writeDigestLockFile(null, children, reposPath, reposRootDir);
                childToKill = null;
                /* ? Why should we go upper rewriting files where nothing is changed?
                 * For now i guess we should break here, maybe later i'll figure out
                 * the reason of non-stopping   
                 */
                break;
            }
            /* Prep for next iteration, or bail if we're done. */
            if("/".equals(reposPath)){
                break;
            }
            reposPath = SVNPathUtil.removeTail(reposPath);
            if("".equals(reposPath)){
                reposPath = "/";
            }
            children.clear(); 
        }
    }
    
    public static void writeDigestLockFile(SVNLock lock, Collection children, String repositoryPath, File reposRootDir) throws SVNException {
        if(!ensureDirExists(FSRepositoryUtil.getDBLocksDir(reposRootDir), true)){
            SVNErrorManager.error("svn: Can't create a directory at '" + FSRepositoryUtil.getDBLocksDir(reposRootDir).getAbsolutePath() + "'");
        }
        File digestLockFile = FSRepositoryUtil.getDigestFileFromRepositoryPath(repositoryPath, reposRootDir);
        File lockDigestSubdir = FSRepositoryUtil.getLockDigestSubdirectory(FSRepositoryUtil.getDigestFromRepositoryPath(repositoryPath), reposRootDir);
        if(!ensureDirExists(lockDigestSubdir, true)){
            SVNErrorManager.error("svn: Can't create a directory at '" + FSRepositoryUtil.getDBLocksDir(reposRootDir).getAbsolutePath() + "'");
        }
        Map props = new HashMap();
        if(lock != null){
            props.put(FSConstants.PATH_LOCK_KEY, lock.getPath());
            props.put(FSConstants.OWNER_LOCK_KEY, lock.getOwner());
            props.put(FSConstants.TOKEN_LOCK_KEY, lock.getID());
            props.put(FSConstants.IS_DAV_COMMENT_LOCK_KEY, "0");
            if(lock.getComment() != null){
                props.put(FSConstants.COMMENT_LOCK_KEY, lock.getComment());
            }
            if(lock.getCreationDate() != null){
                props.put(FSConstants.CREATION_DATE_LOCK_KEY, SVNTimeUtil.formatDate(lock.getCreationDate()));
            }
            if(lock.getExpirationDate() != null){
                props.put(FSConstants.EXPIRATION_DATE_LOCK_KEY, SVNTimeUtil.formatDate(lock.getExpirationDate()));
            }
        }
        if(children != null && children.size() > 0){
            Object[] digests = children.toArray();
            StringBuffer value = new StringBuffer();
            for(int i = 0; i < digests.length; i++){
                value.append(digests[i]);
                value.append('\n');
            }
            props.put(FSConstants.CHILDREN_LOCK_KEY, value.toString());
        }

        try{
            SVNProperties.setProperties(props, digestLockFile);
        }catch(SVNException svne){
            SVNErrorManager.error("svn: Cannot write lock/entries hashfile '" + digestLockFile.getAbsolutePath() + "': " + svne.getMessage());
        }
    }

    public static void setTransactionProperty(File reposRootDir, String txnId, String propertyName, String propertyValue) throws SVNException {
        SVNProperties revProps = new SVNProperties(FSRepositoryUtil.getTxnPropsFile(txnId, reposRootDir), null);
        revProps.setPropertyValue(propertyName, propertyValue);
    }

    public static void setRevisionProperty(File reposRootDir, long revision, String propertyName, String propertyNewValue, String propertyOldValue, String userName, String action) throws SVNException {
        FSHooks.runPreRevPropChangeHook(reposRootDir, propertyName, propertyNewValue, userName, revision, action);
        SVNProperties revProps = new SVNProperties(FSRepositoryUtil.getRevisionPropertiesFile(reposRootDir, revision), null);
        revProps.setPropertyValue(propertyName, propertyNewValue);
        FSHooks.runPostRevPropChangeHook(reposRootDir, propertyName, propertyOldValue, userName, revision, action);
    }

    public static boolean ensureDirExists(File dir, boolean create){
        if(!dir.exists() && create == true){
            return dir.mkdirs();
        }else if(!dir.exists()){
            return false;
        }
        return true;
    }
    
    public static File createUniqueTemporaryFile(String name, String suffix) throws SVNException {
        File tmpDir = getTmpDir();
        if (tmpDir == null) {
            SVNErrorManager.error("svn: Can't get a temporary directory");
        }
        File tmpFile = null;
        try {
            tmpFile = SVNFileUtil.createUniqueFile(tmpDir, name, suffix);
            tmpFile.createNewFile();
            tmpFile.deleteOnExit();
        } catch (IOException ioe) {
            SVNErrorManager.error("svn: Can't create a temporary file");
        }
        return tmpFile;
    }

    public static File getTmpDir() {
        return FSWriter.testTempDir(new File("")); 
    }

    public static File testTempDir(File tmpDir){
        File tmpFile = null;
        FileOutputStream fos = null;
        for(int i = 0; i < 2; i++){
            try{
                tmpFile = File.createTempFile("javasvn-test", ".tmp", i == 0 ? null : tmpDir);
                fos = new FileOutputStream(tmpFile);
                fos.write('!');
                fos.close();
                return tmpFile.getParentFile();
            }catch(FileNotFoundException fnfe){
                continue;
            }catch(IOException ioe){
                continue;
            }catch(SecurityException se){
                continue;
            }finally{
                SVNFileUtil.closeFile(fos);
                /* it should not be a fatal error that a security
                 * exception may occur?
                 */
                try{
                    SVNFileUtil.deleteFile(tmpFile);
                }catch(SecurityException se){
                }
            }
        }
        return null;
    }
}
