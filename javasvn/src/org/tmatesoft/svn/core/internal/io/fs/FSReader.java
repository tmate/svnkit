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

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLock;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.io.SVNLocationEntry;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.util.SVNTimeUtil;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNProperties;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;

import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.io.File;
import java.util.Date;
import java.util.LinkedList;
import java.util.Iterator;
import java.util.Map;
import java.util.Collection;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Set;
import java.rmi.server.UID;

public class FSReader {
    /* Read the 'current' file for filesystem reposRootDir and return the next
     * available node id and the next available copy id. 
     *
     * String[0] - current node-id
     * String[1] - current copy-id
     */
    public static String[] getNextRevisionIds(File reposRootDir) throws SVNException{
        String[] ids = new String[2];
        File currentFile = FSRepositoryUtil.getFSCurrentFile(reposRootDir);
        String idsLine = FSReader.readSingleLine(currentFile, 80);
        if(idsLine == null || idsLine.length() == 0){
            SVNErrorManager.error("Corrupt current file");
        }
        String[] parsedIds = idsLine.split(" ");
        if(parsedIds.length != 3){
            SVNErrorManager.error("Corrupt current file");
        }
        ids[0] = parsedIds[1];
        ids[1] = parsedIds[2];
        return ids;
    }
    
    public static Map fetchTxnChanges(Map changedPaths, String txnId, Map copyFromCache, File reposRootDir) throws SVNException {
        changedPaths = changedPaths == null ? new HashMap() : changedPaths;
        File changesFile = FSRepositoryUtil.getTxnChangesFile(txnId, reposRootDir);
        fetchAllChanges(changedPaths, changesFile, false, 0, copyFromCache);
        return changedPaths;
    }
    
    /* Return Object[] consist of two Maps:
     * Object[0]: pathChanged Map
     * Object[1]: copyfromCache Map*/    
    public static Object[] fetchAllChanges(Map changedPaths, File changesFile, boolean prefolded, long offsetToFirstChanges, Map mapCopyfrom)throws SVNException{        
        RandomAccessFile raReader = SVNFileUtil.openRAFileForReading(changesFile);
        try{
        changedPaths = changedPaths != null ? changedPaths : new HashMap();  
        mapCopyfrom = mapCopyfrom != null ? mapCopyfrom : new HashMap();
        OffsetContainerClass offset = new OffsetContainerClass(offsetToFirstChanges);
        FSChange change = FSReader.readChanges(changesFile, raReader, offset, true);        
        while(change != null){
            ArrayList retArr = foldChange(changedPaths, change, mapCopyfrom);
            changedPaths = (Map)retArr.get(0);
            mapCopyfrom = (Map)retArr.get(1);
            
            if( ( FSPathChangeKind.FS_PATH_CHANGE_DELETE.equals(change.getKind()) || 
                    FSPathChangeKind.FS_PATH_CHANGE_REPLACE.equals(change.getKind()) ) && 
                    prefolded == false){                                
                Collection keySet = changedPaths.keySet();
                Iterator curIter = keySet.iterator();
                while(curIter.hasNext()){
                    String hashKeyPath = (String)curIter.next();
                    //If we come across our own path, ignore it                    
                    if(change.getPath().equals(hashKeyPath)){
                        continue;
                    }
                    //If we come across a child of our path, remove it
                    if(SVNPathUtil.pathIsChild(change.getPath(), hashKeyPath) != null){
                        changedPaths.remove(hashKeyPath);
                    }
                }                
            }
            change = FSReader.readChanges(changesFile, raReader, offset, false);
        }
        }finally{
            SVNFileUtil.closeFile(raReader);
        }

        Object[] result = new Object[2];
        result[0] = changedPaths;
        result[1] = mapCopyfrom;
        return result;        
    }
    
    /* Merge the internal-use-only FSChange into a hash of FSPathChanges, 
     * collapsing multiple changes into a single summarising change per path.  
     * Also keep copyfromCache (here it is a parameter Map mapCopyfrom) up to date with new adds and replaces */
    private static ArrayList foldChange(Map mapChanges, FSChange change, Map mapCopyfrom)throws SVNException{
        if(change == null){
            return null;            
        }
        mapChanges = mapChanges != null ? mapChanges : new HashMap();
        mapCopyfrom = mapCopyfrom != null ? mapCopyfrom : new HashMap();
        FSPathChange newChange = null;
        SVNLocationEntry copyfromEntry = null;
        String path = null;
        
        FSPathChange oldChange = (FSPathChange)mapChanges.get(change.getPath());
        if(oldChange != null){
            /* Get the existing copyfrom entry for this path. */
            copyfromEntry = (SVNLocationEntry)mapCopyfrom.get(change.getPath());
            path = change.getPath();
            /* Sanity check:  only allow NULL node revision ID in the 'reset' case. */
            if((change.getNodeRevID() == null) && 
                    (FSPathChangeKind.FS_PATH_CHANGE_RESET != change.getKind())){
                SVNErrorManager.error("Missing required node revision ID");
            }
            /* Sanity check: we should be talking about the same node
            revision ID as our last change except where the last change
            was a deletion*/
            if((change.getNodeRevID() != null) && 
                    (oldChange.getRevNodeId().equals(change.getNodeRevID()) == false) && 
                    (oldChange.getChangeKind() != FSPathChangeKind.FS_PATH_CHANGE_DELETE)){
                SVNErrorManager.error("Invalid change ordering: new node revision ID without delete");
            }
            /* Sanity check: an add, replacement, or reset must be the first
            thing to follow a deletion*/            
            if(FSPathChangeKind.FS_PATH_CHANGE_DELETE == oldChange.getChangeKind() && 
                    !( FSPathChangeKind.FS_PATH_CHANGE_REPLACE == change.getKind() || 
                       FSPathChangeKind.FS_PATH_CHANGE_RESET == change.getKind()   ||
                       FSPathChangeKind.FS_PATH_CHANGE_ADD == change.getKind()) ){
                SVNErrorManager.error("Invalid change ordering: non-add change on deleted path");
            }    
            /*Merging the changes*/
            switch(change.getKind().intValue()){
                case 0 /*FSPathChangeKind.FS_PATH_CHANGE_MODIFY*/ :
                    if(change.getTextModification()){
                        oldChange.setTextModified(true);
                    }
                    if(change.getPropModification()){
                        oldChange.setPropertiesModified(true);
                    }
                    break;
                case 1 /*FSPathChangeKind.FS_PATH_CHANGE_ADD*/ :
                case 3 /*FSPathChangeKind.FS_PATH_CHANGE_REPLACE*/ :
                    /*An add at this point must be following a previous delete,
                    so treat it just like a replace*/        
                    oldChange.setChangeKind(FSPathChangeKind.FS_PATH_CHANGE_REPLACE);
                    oldChange.setRevNodeId(new FSID(change.getNodeRevID()));
                    oldChange.setTextModified(change.getTextModification());
                    oldChange.setPropertiesModified(change.getPropModification());
                    if(change.getCopyfromEntry() == null){
                        copyfromEntry = new SVNLocationEntry(FSConstants.SVN_INVALID_REVNUM, "");
                    }else{
                        copyfromEntry = new SVNLocationEntry(change.getCopyfromEntry().getRevision(), change.getCopyfromEntry().getPath());
                    }
                    break;
                case 2 /*FSPathChangeKind FS_PATH_CHANGE_DELETE*/:
                    if(FSPathChangeKind.FS_PATH_CHANGE_ADD.equals(oldChange.getChangeKind())){
                        /*If the path was introduced in this transaction via an
                        add, and we are deleting it, just remove the path altogether*/
                        oldChange = null;
                        mapChanges.remove(change.getPath());
                    }else{
                        /* A deletion overrules all previous changes. */
                        oldChange.setChangeKind(FSPathChangeKind.FS_PATH_CHANGE_DELETE);
                        oldChange.setPropertiesModified(change.getPropModification());
                        oldChange.setTextModified(change.getTextModification());
                    }
                    copyfromEntry = null;
                    mapCopyfrom.remove(change.getPath());
                    break;                    
                case 4 : /*FSPathChangeKind.FS_PATH_CHANGE_RESET*/
                    //A reset here will simply remove the path change from the hash
                    oldChange = null;
                    copyfromEntry = null;
                    mapChanges.remove(change.getPath());
                    mapCopyfrom.remove(change.getPath());
                    break;
            }
            newChange = oldChange;
        }else{
            newChange = new FSPathChange(new FSID(change.getNodeRevID()), change.getKind(), change.getTextModification(), change.getPropModification());
            if(change.getCopyfromEntry().getRevision() != FSConstants.SVN_INVALID_REVNUM){
                copyfromEntry = change.getCopyfromEntry();
            }else{
                copyfromEntry = new SVNLocationEntry(FSConstants.SVN_INVALID_REVNUM, "");
            }
            path = change.getPath();
        }
        /* Add (or update) this path. */
        mapChanges.put(path, newChange);        
  
        if(copyfromEntry == null){
            mapCopyfrom.put(path, null);
        }else{
            mapCopyfrom.put(path, new SVNLocationEntry(copyfromEntry.getRevision(), copyfromEntry.getPath()));            
        }      
        
        ArrayList arr = new ArrayList(0);
        arr.add(mapChanges);
        arr.add(mapCopyfrom);
        return arr;
    }
    
    public static InputStream getFileContentsInputStream(FSRoot root, String path, FSRevisionNodePool pool, File reposRootDir) throws SVNException {
        FSRevisionNode fileNode = pool.getRevisionNode(root, path, reposRootDir);
        if(fileNode == null){
            if(root.isTxnRoot()){
                SVNErrorManager.error("File not found: transaction '" + root.getTxnId() + "', path '" + path + "'");
            }else{
                SVNErrorManager.error("File not found: revision " + root.getRevision() + ", path '" + path + "'");
            }
        }
        return FSInputStream.createDeltaStream(fileNode, reposRootDir);
    }

    /* Given a representation 'rep', open the correct file and seek to the 
     * correction location. 
     */
    public static RandomAccessFile openAndSeekRepresentation(FSRepresentation rep, File reposRootDir) throws SVNException {
        if(!rep.isTxn()){
            return openAndSeekRevision(rep, reposRootDir);
        }
        return openAndSeekTransaction(rep, reposRootDir);
    }

    /* Open the representation for a node-revision in a transaction in 
     * filesystem. Seek to an offset location before returning. Only appropriate 
     * for file contents, nor props or directory contents. 
     */
    private static RandomAccessFile openAndSeekTransaction(FSRepresentation rep, File reposRootDir) throws SVNException {
        RandomAccessFile file = null;
        try{
            file = SVNFileUtil.openRAFileForReading(FSRepositoryUtil.getTxnRevFile(rep.getHexDigest(), reposRootDir));
            file.seek(rep.getOffset());
        }catch(IOException ioe){
            SVNFileUtil.closeFile(file);
            SVNErrorManager.error(ioe.getMessage());
        }catch(SVNException svne){
            SVNFileUtil.closeFile(file);
            throw svne;
        }
        return file; 
    }

    /* Open the revision file for a revision in a filesystem. Seek to an offset 
     * location before returning. 
     */
    private static RandomAccessFile openAndSeekRevision(FSRepresentation rep, File reposRootDir) throws SVNException {
        RandomAccessFile file = null;
        try{
            file = SVNFileUtil.openRAFileForReading(FSRepositoryUtil.getRevisionFile(reposRootDir, rep.getRevision()));
            file.seek(rep.getOffset());
        }catch(IOException ioe){
            SVNFileUtil.closeFile(file);
            SVNErrorManager.error(ioe.getMessage());
        }catch(SVNException svne){
            SVNFileUtil.closeFile(file);
            throw svne;
        }
        return file; 
    }
    
    // to mean the end of a file
    public static final long FILE_END_POS = -1;

    /* String[0] - is to be the fetched out node-id
     * String[1] - is to be the fetched out copy-id
     */
    public static String[] readNextIds(String txnId, File reposRootDir) throws SVNException {
        String[] ids = new String[2];
        String idsToParse = null;
        try{
            idsToParse = FSReader.readSingleLine(FSRepositoryUtil.getTxnNextIdsFile(txnId, reposRootDir), FSConstants.MAX_KEY_SIZE*2 + 3);
        }catch(SVNException svne){
            SVNErrorManager.error("Can't read length line in file '" + FSRepositoryUtil.getTxnNextIdsFile(txnId, reposRootDir).getAbsolutePath() + "': " + svne.getMessage());
        }
        String[] parsedIds = idsToParse.split(" ");
        if(parsedIds.length < 2){
            SVNErrorManager.error("next-ids file corrupt");
        }
        ids[0] = parsedIds[0];
        ids[1] = parsedIds[1];
        return ids;
    }
    
    public static void allowLockedOperation(String path, final String username, final Collection lockTokens, boolean recursive, boolean haveWriteLock, File reposRootDir) throws SVNException {
        path = SVNPathUtil.canonicalizeAbsPath(path);
        if(recursive){
            IFSLockHandler handler = new IFSLockHandler(){
                private String myUsername = username;
                private Collection myTokens = lockTokens;
                public void handleLock(SVNLock lock) throws SVNException{
                    verifyLock(lock, myTokens, myUsername);
                }
            };
            /* Discover all locks at or below the path. */
            walkDigestFiles(FSRepositoryUtil.getDigestFileFromRepositoryPath(path, reposRootDir), handler, haveWriteLock, reposRootDir);
        }else{
            /* Discover and verify any lock attached to the path. */
            SVNLock lock = getLock(path, haveWriteLock, reposRootDir);
            if(lock != null){
                verifyLock(lock, lockTokens, username);
            }
        }
    }
    
    /* A recursive function that calls getLocksHandler for
     * all locks in and under PATH in FS.
     * haveWriteLock should be true if the caller (directly or indirectly)
     * has the FS write lock. 
     */
    private static void walkDigestFiles(File digestFile, IFSLockHandler getLocksHandler, boolean haveWriteLock, File reposRootDir) throws SVNException {
        Collection children = new LinkedList();
        /* First, send up any locks in the current path. */
        SVNLock lock = fetchLockFromDigestFile(digestFile, null, children, reposRootDir);
        if(lock != null){
            Date current = new Date(System.currentTimeMillis());
            /* Don't report an expired lock. */
            if(lock.getExpirationDate() == null || current.compareTo(lock.getExpirationDate()) < 0){
                getLocksHandler.handleLock(lock);
            }else if(haveWriteLock) {
                /* Only remove the lock if we have the write lock.
                 * Read operations shouldn't change the filesystem. 
                 */
                FSWriter.deleteLock(lock, reposRootDir);
            }
        }
        /* Now, recurse on this thing's child entries (if any; bail otherwise). */
        if(children.isEmpty()){
            return;
        }
        for(Iterator entries = children.iterator(); entries.hasNext();){
            String digestName = (String)entries.next();
            File childDigestFile = FSRepositoryUtil.getDigestFileFromDigest(digestName, reposRootDir);
            walkDigestFiles(childDigestFile, getLocksHandler, haveWriteLock, reposRootDir);
        }
    }
    
    /* Utility function:  verify that a lock can be used. */
    private static void verifyLock(SVNLock lock, Collection lockTokens, String username) throws SVNException {
        if(username == null || "".equals(username)){
            SVNErrorManager.error("Cannot verify lock on path '" + lock.getPath() + "'; no username available");
        }else if(username.compareTo(lock.getOwner()) != 0){
            SVNErrorManager.error("User " + username + " does not own lock on path '" + lock.getPath() + "' (currently locked by " + lock.getOwner() + ")");
        }
        for(Iterator tokens = lockTokens.iterator(); tokens.hasNext();){
            String token = (String)tokens.next();
            if(token.equals(lock.getID())){
                return;
            }
        }
        SVNErrorManager.error("Cannot verify lock on path '" + lock.getPath() + "'; no matching lock-token available");
    }
    
    public static SVNLock getLock(String repositoryPath, boolean haveWriteLock, File reposRootDir) throws SVNException {
        SVNLock lock = fetchLockFromDigestFile(null, repositoryPath, null, reposRootDir);
        /* TODO later replace this with an exception with an appropriate error code
         * like NO_SUCH_LOCK
         */
        if(lock == null){
            return null;
        }
        Date current = new Date(System.currentTimeMillis());
        /* Don't return an expired lock. */
        if(lock.getExpirationDate() != null && current.compareTo(lock.getExpirationDate()) > 0){
            /* Only remove the lock if we have the write lock.
             * Read operations shouldn't change the filesystem. 
             */
            if(haveWriteLock){
                FSWriter.deleteLock(lock, reposRootDir);
            }
            return null;//yet recently it has been an error
        }
        return lock;
    }
    
    public static SVNLock fetchLockFromDigestFile(File digestFile, String repositoryPath, Collection children, File reposRootDir) throws SVNException {
        File digestLockFile = digestFile == null ? FSRepositoryUtil.getDigestFileFromRepositoryPath(repositoryPath, reposRootDir) : digestFile;
        SVNProperties props = new SVNProperties(digestLockFile, null);
        Map lockProps = null;
        try{
            lockProps = props.asMap();
        } catch(SVNException svne){
            SVNErrorManager.error("svn: Can't parse lock/entries hashfile '" + digestLockFile.getAbsolutePath() + "'");
        }
        
        SVNLock lock = null;
        String lockPath = (String)lockProps.get(FSConstants.PATH_LOCK_KEY);
        if(lockPath != null){
            String lockToken = (String)lockProps.get(FSConstants.TOKEN_LOCK_KEY);
            if(lockToken == null){
                SVNErrorManager.error("svn: Corrupt lockfile for path '" + lockPath + "' in filesystem '" + FSRepositoryUtil.getRepositoryDBDir(reposRootDir).getAbsolutePath() + "'");
            }
            String lockOwner = (String)lockProps.get(FSConstants.OWNER_LOCK_KEY);
            if(lockOwner == null){
                SVNErrorManager.error("svn: Corrupt lockfile for path '" + lockPath + "' in filesystem '" + FSRepositoryUtil.getRepositoryDBDir(reposRootDir).getAbsolutePath() + "'");
            }
            String davComment = (String)lockProps.get(FSConstants.IS_DAV_COMMENT_LOCK_KEY);
            //? how about it? what is to do with this flag? add it to SVNLock?
            //dav comment is not used in file:/// protocol
            if(davComment == null){
                //SVNErrorManager.error("svn: Corrupt lockfile for path '" + lockPath + "' in filesystem '" + FSRepositoryUtil.getRepositoryDBDir(reposRootDir).getAbsolutePath() + "'");
            }
            
            String creationTime = (String)lockProps.get(FSConstants.CREATION_DATE_LOCK_KEY);
            if(creationTime == null){
                SVNErrorManager.error("svn: Corrupt lockfile for path '" + lockPath + "' in filesystem '" + FSRepositoryUtil.getRepositoryDBDir(reposRootDir).getAbsolutePath() + "'");
            }
            Date creationDate = SVNTimeUtil.parseDate(creationTime);
            if (creationDate == null) {
                SVNErrorManager.error("svn: Corrupt lockfile for path '" + lockPath + "' in filesystem '" + FSRepositoryUtil.getRepositoryDBDir(reposRootDir).getAbsolutePath() + "'" + SVNFileUtil.getNativeEOLMarker() + "svn: Can't parse creation time");
            }
            String expirationTime = (String)lockProps.get(FSConstants.EXPIRATION_DATE_LOCK_KEY);
            Date expirationDate = null;
            if(expirationTime != null){
                expirationDate = SVNTimeUtil.parseDate(expirationTime);
                if (expirationDate == null) {
                    SVNErrorManager.error("svn: Corrupt lockfile for path '" + lockPath + "' in filesystem '" + FSRepositoryUtil.getRepositoryDBDir(reposRootDir).getAbsolutePath() + "'" + SVNFileUtil.getNativeEOLMarker() + "svn: Can't parse expiration time");
                }
            }
            
            String comment = (String)lockProps.get(FSConstants.COMMENT_LOCK_KEY);
            lock = new SVNLock(lockPath, lockToken, lockOwner, comment, creationDate, expirationDate);
        }
        
        String childEntries = (String)lockProps.get(FSConstants.CHILDREN_LOCK_KEY);
        if(children != null && childEntries != null){
            String[] digests = childEntries.split("\n");
            for(int i = 0; i < digests.length; i++){
                children.add(digests[i]);
            }
        }
        
        return lock;
    }
    
    public static FSTransaction getTxn(String txnId, File reposRootDir) throws SVNException {
        FSTransaction txn = getTxn(txnId, false, reposRootDir);
        if(txn.getKind() != FSTransactionKind.TXN_KIND_NORMAL){
            SVNErrorManager.error("Cannot modify transaction named '" + txnId + "' in filesystem '" + reposRootDir.getAbsolutePath() + "'");
        }
        return txn;
    }

    /* If expectDead is true, this transaction must be a dead one, 
     * else an error is returned. If expectDead is false, an error is 
     * thrown if the transaction is *not* dead. 
     */
    private static FSTransaction getTxn(String txnId, boolean expectDead, File reposRootDir) throws SVNException {
        FSTransaction txn = fetchTxn(txnId, reposRootDir);
        if(expectDead && txn.getKind() != FSTransactionKind.TXN_KIND_DEAD){
            SVNErrorManager.error("Transaction is not dead: '" + txnId + "'");
        }
        if(!expectDead && txn.getKind() == FSTransactionKind.TXN_KIND_DEAD){
            SVNErrorManager.error("Transaction is dead: '" + txnId + "'");
        }
        return txn;
    }

    private static FSTransaction fetchTxn(String txnId, File reposRootDir) throws SVNException {
        Map txnProps = FSRepositoryUtil.getTransactionProperties(reposRootDir, txnId);
        FSID rootId = FSID.createTxnId("0", "0", txnId);
        FSRevisionNode revNode = getRevNodeFromID(reposRootDir, rootId);
        return new FSTransaction(FSTransactionKind.TXN_KIND_NORMAL, revNode.getId(), revNode.getPredecessorId(), null, txnProps);
    }
    
    /*
     * If root is not null, tries to find the rev-node for repositoryPath 
     * in the provided root, otherwise if root is null, uses the provided 
     * revision to get the root first. 
     */
    public static FSRevisionNode getRevisionNode(File reposRootDir, String repositoryPath, FSRevisionNode root, long revision) throws SVNException {
        if(repositoryPath == null){
            return null;
        }
        String absPath = SVNPathUtil.canonicalizeAbsPath(repositoryPath);
        String nextPathComponent = null;
        FSRevisionNode parent = root != null ? root : FSReader.getRootRevNode(reposRootDir, revision);
        FSRevisionNode child = null;
        while (true) {
            nextPathComponent = SVNPathUtil.head(absPath);
            absPath = SVNPathUtil.removeHead(absPath);
            if ("".equals(nextPathComponent)) {
                child = parent;
            } else {
                child = FSReader.getChildDirNode(nextPathComponent, parent, reposRootDir);
                if (child == null) {
                    return null;
                }
            }
            parent = child;

            if ("".equals(absPath)) {
                break;
            }
        }
        return parent;
    }
    
    public static FSRevisionNode getChildDirNode(String child, FSRevisionNode parent, File reposRootDir) throws SVNException {
        /* Make sure that NAME is a single path component. */
        if (!SVNPathUtil.isSinglePathComponent(child)) {
            SVNErrorManager.error("svn: Attempted to open node with an illegal name '" + child + "'");
        }
        /* Now get the node that was requested. */
        Map entries = getDirEntries(parent, reposRootDir); 
        FSEntry entry = entries != null ? (FSEntry) entries.get(child) : null;
        return entry == null ? null : getRevNodeFromID(reposRootDir, entry.getId());
    }

    public static FSEntry getChildEntry(String child, FSRevisionNode parent, File reposRootDir) throws SVNException {
        /* Make sure that NAME is a single path component. */
        if (!SVNPathUtil.isSinglePathComponent(child)) {
            SVNErrorManager.error("svn: Attempted to open node with an illegal name '" + child + "'");
        }
        /* Now get the entry that was requested. */
        Map entries = getDirEntries(parent, reposRootDir); 
        FSEntry entry = entries != null ? (FSEntry) entries.get(child) : null;
        return entry;
    }
    
    public static Map getDirEntries(FSRevisionNode parent, File reposRootDir) throws SVNException {
        if (parent == null || parent.getType() != SVNNodeKind.DIR) {
            SVNErrorManager.error("svn: Can't get entries of non-directory");
        }
        //first try to ask the object's cache for entries
        Map entries = parent.getDirContents(); 
        if(entries == null){
            entries = getDirContents(parent, reposRootDir);
            entries = entries == null ? new HashMap() : entries;
            parent.setDirContents(entries);
        }
        return entries;
    }

    public static Map getProperties(FSRevisionNode revNode, File reposRootDir) throws SVNException {
        return getProplist(revNode, reposRootDir);
    }

    private static Map getDirContents(FSRevisionNode revNode, File reposRootDir) throws SVNException {
        if(revNode.getTextRepresentation() != null && revNode.getTextRepresentation().isTxn()){
            /* The representation is mutable.  Read the old directory
             * contents from the mutable children file, followed by the
             * changes we've made in this transaction. 
             */
            File childrenFile = FSRepositoryUtil.getTxnRevNodeChildrenFile(revNode.getId(), reposRootDir);
            InputStream file = null;
            Map entries = null;
            try{
                file = SVNFileUtil.openFileForReading(childrenFile);
                Map rawEntries = SVNProperties.asMap(null, file, false, SVNProperties.SVN_HASH_TERMINATOR);
                rawEntries = SVNProperties.asMap(rawEntries, file, true, null);
                entries = parsePlainRepresentation(rawEntries);
            }catch(IOException ioe){
                SVNErrorManager.error(ioe.getMessage());
            }finally{
                SVNFileUtil.closeFile(file);
            }
            return entries;
        }else if(revNode.getTextRepresentation() != null){
            InputStream is = null;
            FSRepresentation textRepresent = revNode.getTextRepresentation(); 
            try {
                is = FSInputStream.createPlainStream(textRepresent, reposRootDir);//readPlainRepresentation(textRepresent, reposRootDir);
                Map rawEntries = SVNProperties.asMap(null, is, false, SVNProperties.SVN_HASH_TERMINATOR);
                return parsePlainRepresentation(rawEntries);
            } catch (IOException ioe) {
                SVNErrorManager.error("svn: Can't read representation in revision file '" + FSRepositoryUtil.getRevisionFile(reposRootDir, textRepresent.getRevision()).getAbsolutePath() + "': "
                        + ioe.getMessage());
            } catch (SVNException svne) {
                SVNErrorManager.error("svn: Revision file '" + FSRepositoryUtil.getRevisionFile(reposRootDir, textRepresent.getRevision()).getAbsolutePath() + "' corrupt"
                        + SVNFileUtil.getNativeEOLMarker() + svne.getMessage());
            } finally {
                SVNFileUtil.closeFile(is);
            }
        }
        return new HashMap();//returns an empty map, must not be null!!
    }

    private static Map getProplist(FSRevisionNode revNode, File reposRootDir) throws SVNException {
        Map properties = new HashMap();
        if(revNode.getPropsRepresentation() != null && revNode.getPropsRepresentation().isTxn()){
            File propsFile = FSRepositoryUtil.getTxnRevNodePropsFile(revNode.getId(), reposRootDir);
            InputStream file = null;
            try{
                file = SVNFileUtil.openFileForReading(propsFile);
                properties = SVNProperties.asMap(properties, file, false, SVNProperties.SVN_HASH_TERMINATOR);
            }catch(IOException ioe){
                SVNErrorManager.error(ioe.getMessage());
            }finally{
                SVNFileUtil.closeFile(file);
            }
        }else if(revNode.getPropsRepresentation() != null){
            InputStream is = null;
            FSRepresentation propsRepresent = revNode.getPropsRepresentation();
            try {
                is = FSInputStream.createPlainStream(propsRepresent, reposRootDir);//readPlainRepresentation(propsRepresent, reposRootDir);
                properties = SVNProperties.asMap(properties, is, false, SVNProperties.SVN_HASH_TERMINATOR);
                //parsePlainRepresentation(rawEntries, true);
            } catch (IOException ioe) {
                SVNErrorManager.error("svn: Can't read representation in revision file '" + FSRepositoryUtil.getRevisionFile(reposRootDir, propsRepresent.getRevision()).getAbsolutePath() + "': "
                        + ioe.getMessage());
            } catch (SVNException svne) {
                SVNErrorManager.error("svn: Revision file '" + FSRepositoryUtil.getRevisionFile(reposRootDir, propsRepresent.getRevision()).getAbsolutePath() + "' corrupt"
                        + SVNFileUtil.getNativeEOLMarker() + svne.getMessage());
            } finally {
                SVNFileUtil.closeFile(is);
            }
        }
        return properties;//no properties? return an empty map 
    }

    /*
     * Now this routine is intended only for parsing dir entries since
     * the static method asMap() of SVNProperties does all the job for 
     * reading properties.
     */
    private static Map parsePlainRepresentation(Map entries) throws SVNException {
        Map representationMap = new HashMap();
        Object[] names = entries.keySet().toArray();
        for(int i = 0; i < names.length; i++){
            String name = (String)names[i];
            FSEntry nextRepEntry = null;
            try {
                nextRepEntry = parseRepEntryValue(name, (String)entries.get(names[i]));
            } catch (SVNException svne) {
                SVNErrorManager.error("svn: Directory entry '" + name + "' corrupt");
            }
            representationMap.put(name, nextRepEntry);
        }
        return representationMap;
    }

    private static FSEntry parseRepEntryValue(String name, String value) throws SVNException {
        String[] values = value.split(" ");
        if (values == null || values.length < 2) {
            throw new SVNException();
        }
        SVNNodeKind type = SVNNodeKind.parseKind(values[0]);
        FSID id = parseID(values[1]);
        if ((type != SVNNodeKind.DIR && type != SVNNodeKind.FILE) || id == null) {
            throw new SVNException();
        }
        return new FSEntry(id, type, name);
    }

    public static FSRevisionNode getTxnRootNode(String txnId, File reposRootDir) throws SVNException {
        FSTransaction txn = getTxn(txnId, reposRootDir);
        FSRevisionNode txnRootNode = getRevNodeFromID(reposRootDir, txn.getRootId()); 
        return txnRootNode;
    }

    public static FSRevisionNode getTxnBaseRootNode(String txnId, File reposRootDir) throws SVNException {
        FSTransaction txn = getTxn(txnId, reposRootDir);
        FSRevisionNode txnBaseNode = getRevNodeFromID(reposRootDir, txn.getBaseId()); 
        return txnBaseNode;
    }
    
    public static FSRevisionNode getRootRevNode(File reposRootDir, long revision) throws SVNException {
        FSID id = new FSID(FSID.ID_INAPPLICABLE, FSID.ID_INAPPLICABLE, FSID.ID_INAPPLICABLE, revision, getRootOffset(reposRootDir, revision));
        return getRevNodeFromID(reposRootDir, id);
    }

    public static FSRevisionNode getRevNodeFromID(File reposRootDir, FSID id) throws SVNException {
        File revFile = !id.isTxn() ? FSRepositoryUtil.getRevisionFile(reposRootDir, id.getRevision()) : FSRepositoryUtil.getTxnRevNodeFile(id, reposRootDir);

        FSRevisionNode revNode = new FSRevisionNode();
        long offset = !id.isTxn() ? id.getOffset() : 0;

        Map headers = readRevNodeHeaders(revFile, offset);

        // Read the rev-node id.
        String revNodeId = (String) headers.get(FSConstants.HEADER_ID);
        if (revNodeId == null) {
            SVNErrorManager.error("svn: Missing node-id in node-rev in revision file '" + revFile.getAbsolutePath() + "'");
        }

        FSID revnodeId = parseID(revNodeId);    
        if(revnodeId == null){
            SVNErrorManager.error("svn: Corrupt node-id in node-rev in revision file '" + revFile.getAbsolutePath() + "'");
        }
        revNode.setId(revnodeId);

        // Read the type.
        SVNNodeKind nodeKind = SVNNodeKind.parseKind((String) headers.get(FSConstants.HEADER_TYPE));
        if (nodeKind == SVNNodeKind.NONE || nodeKind == SVNNodeKind.UNKNOWN) {
            SVNErrorManager.error("svn: Missing kind field in node-rev in revision file '" + revFile.getAbsolutePath() + "'");
        }
        revNode.setType(nodeKind);

        // Read the 'count' field.
        String countString = (String) headers.get(FSConstants.HEADER_COUNT);
        if (countString == null) {
            revNode.setCount(0);
        } else {
            long cnt = -1;
            try {
                cnt = Long.parseLong(countString);
                if (cnt < 0) {
                    throw new NumberFormatException();
                }
            } catch (NumberFormatException nfe) {
                SVNErrorManager.error("svn: Corrupt count in node-rev in revision file '" + revFile.getAbsolutePath() + "'");
            }
            revNode.setCount(cnt);
        }

        // Get the properties location (if any).
        String propsRepr = (String) headers.get(FSConstants.HEADER_PROPS);
        if (propsRepr != null) {
            try {
                parseRepresentationHeader(propsRepr, revNode, false);
            } catch (SVNException svne) {
                SVNErrorManager.error("svn: Malformed props rep offset line in node-rev '" + revFile.getAbsolutePath() + "'");
            }
        }

        // Get the data location (if any).
        String textRepr = (String) headers.get(FSConstants.HEADER_TEXT);
        if (textRepr != null) {
            try {
                parseRepresentationHeader(textRepr, revNode, true);
            } catch (SVNException svne) {
                SVNErrorManager.error("svn: Malformed text rep offset line in node-rev '" + revFile.getAbsolutePath() + "'");
            }
        }

        // Get the created path.
        String cpath = (String) headers.get(FSConstants.HEADER_CPATH);
        if (cpath == null) {
            SVNErrorManager.error("svn: Missing cpath in node-rev in revision file '" + revFile.getAbsolutePath() + "'");
        }
        revNode.setCreatedPath(cpath);

        // Get the predecessor rev-node id (if any).
        String predId = (String) headers.get(FSConstants.HEADER_PRED);
        if (predId != null) {
            FSID predRevNodeId = parseID(predId);
            if(predRevNodeId == null){
                SVNErrorManager.error("svn: Corrupt node-id in node-rev in revision file '" + revFile.getAbsolutePath() + "'");
            }
            revNode.setPredecessorId(predRevNodeId);
        }

        // Get the copyroot.
        String copyroot = (String) headers.get(FSConstants.HEADER_COPYROOT);
        if (copyroot == null) {
            revNode.setCopyRootPath(revNode.getCreatedPath());
            revNode.setCopyRootRevision(revNode.getId().getRevision());
        } else {
            try {
                parseCopyRoot(copyroot, revNode);
            } catch (SVNException svne) {
                throw new SVNException("svn: Malformed copyroot line in node-rev in revision file '" + revFile.getAbsolutePath() + "'");
            }
        }

        // Get the copyfrom.
        String copyfrom = (String) headers.get(FSConstants.HEADER_COPYFROM);
        if (copyfrom == null) {
            revNode.setCopyFromPath(null);
            revNode.setCopyFromRevision(-1);// maybe this should be replaced
                                            // with some constants
        } else {
            try {
                parseCopyFrom(copyfrom, revNode);
            } catch (SVNException svne) {
                throw new SVNException("svn: Malformed copyfrom line in node-rev in revision file '" + revFile.getAbsolutePath() + "'");
            }
        }

        return revNode;
    }

    // should it fail if revId is invalid?
    public static void parseCopyFrom(String copyfrom, FSRevisionNode revNode) throws SVNException {
        if (copyfrom == null || copyfrom.length() == 0) {
            throw new SVNException();
        }

        String[] cpyfrom = copyfrom.split(" ");
        if (cpyfrom.length < 2) {
            throw new SVNException();
        }
        long rev = -1;
        try {
            rev = Long.parseLong(cpyfrom[0]);
        } catch (NumberFormatException nfe) {
            throw new SVNException();
        }
        revNode.setCopyFromRevision(rev);
        revNode.setCopyFromPath(cpyfrom[1]);
    }

    // should it fail if revId is invalid?
    public static void parseCopyRoot(String copyroot, FSRevisionNode revNode) throws SVNException {
        if (copyroot == null || copyroot.length() == 0) {
            throw new SVNException();
        }

        String[] cpyroot = copyroot.split(" ");
        if (cpyroot.length < 2) {
            throw new SVNException();
        }
        long rev = -1;
        try {
            rev = Long.parseLong(cpyroot[0]);
        } catch (NumberFormatException nfe) {
            throw new SVNException();
        }
        revNode.setCopyRootRevision(rev);
        revNode.setCopyRootPath(cpyroot[1]);
    }

    public static FSID parseID(String revNodeId) {
        /* Now, we basically just need to "split" this data on `.'
         * characters.
         */
        String[] idParts = revNodeId.split("\\.");
        if(idParts.length != 3){
            return null;
        }
        /* Node Id */
        String nodeId = idParts[0];  
        /* Copy Id */
        String copyId = idParts[1];
        if(idParts[2].charAt(0) == 'r'){
            /* This is a revision type ID */
            int slashInd = idParts[2].indexOf('/');
            long rev = -1;
            long offset = -1;
            try {
                rev = Long.parseLong(idParts[2].substring(1, slashInd));
                offset = Long.parseLong(idParts[2].substring(slashInd + 1));
            } catch (NumberFormatException nfe) {
                return null;
            }
            return new FSID(nodeId, FSID.ID_INAPPLICABLE, copyId, rev, offset);
        }else if(idParts[2].charAt(0) == 't'){
            /* This is a transaction type ID */
            String txnId = idParts[2].substring(1);
            return FSID.createTxnId(nodeId, copyId, txnId);
        }
        return null;
    }

    // isData - if true - text, otherwise - props
    public static void parseRepresentationHeader(String representation, FSRevisionNode revNode, boolean isData) throws SVNException {
        if (revNode == null) {
            return;
        }
        String[] offsets = representation.split(" ");
        long rev = -1;
        try {
            rev = Long.parseLong(offsets[0]);
        } catch (NumberFormatException nfe) {
            throw new SVNException();
        }
        if(FSRepository.isInvalidRevision(rev)){
            FSRepresentation represent = new FSRepresentation();
            represent.setRevision(rev);
            represent.setTxnId(revNode.getId().getTxnID());
            if(isData){
                revNode.setTextRepresentation(represent);
            }else{
                revNode.setPropsRepresentation(represent);
            }
            //is it a mutable representation?
            if(!isData || revNode.getType() == SVNNodeKind.DIR){
                return;
            }
        }
        if (offsets == null || offsets.length == 0 || offsets.length < 5) {
            throw new SVNException();
        }
        long offset = -1;
        try {
            offset = Long.parseLong(offsets[1]);
            if (offset < 0) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException nfe) {
            throw new SVNException();
        }

        long size = -1;
        try {
            size = Long.parseLong(offsets[2]);
            if (size < 0) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException nfe) {
            throw new SVNException();
        }

        long expandedSize = -1;
        try {
            expandedSize = Long.parseLong(offsets[3]);
            if (expandedSize < 0) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException nfe) {
            throw new SVNException();
        }

        String hexDigest = offsets[4];
        if (hexDigest.length() != 2 * FSConstants.MD5_DIGESTSIZE || SVNFileUtil.fromHexDigest(hexDigest) == null) {
            throw new SVNException();
        }
        FSRepresentation represnt = new FSRepresentation(rev, offset, size, expandedSize, hexDigest);

        if (isData) {
            revNode.setTextRepresentation(represnt);
        } else {
            revNode.setPropsRepresentation(represnt);
        }
    }

    public static long getRootOffset(File reposRootDir, long revision) throws SVNException {
        Long[] offsets = readRootAndChangesOffset(reposRootDir, revision);
        return offsets[0].longValue();
    }

    public static long getChangesOffset(File reposRootDir, long revision) throws SVNException {
        Long[] offsets = readRootAndChangesOffset(reposRootDir, revision);
        return offsets[1].longValue();
    }

    // Read in a rev-node given its offset in a rev-file.
    private static Map readRevNodeHeaders(File revFile, long offset) throws SVNException {
        if (offset < 0) {
            return null;
        }
        RandomAccessFile raFile = null;
        Map map = new HashMap();
        try {
            raFile = SVNFileUtil.openRAFileForReading(revFile);
            boolean isFirstLine = true;
            while (true) {
                String line = readNextLine(revFile, raFile, offset, isFirstLine, 1024);
                if (line == null || line.length() == 0) {
                    break;
                }
                if (isFirstLine) {
                    isFirstLine = false;
                }
                int colonIndex = line.indexOf(':');
                if (colonIndex < 0) {
                    throw new SVNException("svn: Found malformed header in revision file '" + revFile.getAbsolutePath() + "'");
                }

                String localName = line.substring(0, colonIndex);
                String localValue = line.substring(colonIndex + 1);
                map.put(localName, localValue.trim());
            }
        } finally {
            SVNFileUtil.closeFile(raFile);
        }
        return map;
    }

    public static byte[] readBytesFromFile(long pos, long offset, int bytesToRead, File file) throws SVNException {
        if (bytesToRead < 0 || file == null) {
            return null;
        }
        if (!file.canRead() || !file.isFile()) {
            SVNErrorManager.error("svn: Cannot read from '" + file + "': path refers to a directory or read access denied");
        }
        RandomAccessFile revRAF = null;
        byte[] result = null;
        try {
            revRAF = SVNFileUtil.openRAFileForReading(file);
            long fileLength = -1;
            fileLength = revRAF.length();
            if (pos == FILE_END_POS) {
                pos = fileLength - 1 + offset;
            } else {
                pos = pos + offset;
            }
            byte[] buf = new byte[bytesToRead];
            int r = -1;
            revRAF.seek(pos + 1);
            r = revRAF.read(buf);
            if (r <= 0) {
                throw new IOException("eof unexpectedly found");
            }
            result = new byte[r];
            System.arraycopy(buf, 0, result, 0, r);
        } catch (IOException ioe) {
            SVNErrorManager.error("svn: Can't read length line in file '" + file.getAbsolutePath() + "': " + ioe.getMessage());
        } finally {
            SVNFileUtil.closeFile(revRAF);
        }
        return result;
    }

    /* limitBytes MUST NOT be 0! it defines the maximum number of bytes 
     * that should be read (not counting EOL)
     */ 
    public static String readNextLine(File file, RandomAccessFile raFile, long offset, boolean makeOffset, int limitBytes) throws SVNException {
        offset = (offset < 0) ? 0 : offset;
        if(makeOffset){
            try {
                raFile.seek(offset);
            } catch (IOException ioe) {
                SVNErrorManager.error("svn: Can't set position pointer in " + (file != null ? "file '" + file.getAbsolutePath() + "': " + ioe.getMessage() : "stream"));
            }
        }
        StringBuffer lineBuffer = new StringBuffer();
        int r = -1;
        try {
            for(int i = 0; i < limitBytes; i++){
                r = raFile.read();
                //TODO: sometimes it may be necessary to know that EOF has
                //been met
                if(r == '\n' || r == -1){
                    return lineBuffer.toString();
                }
                lineBuffer.append((char)r);
            }
        } catch (IOException ioe) {
            SVNErrorManager.error("Can't read length line in " + (file != null ? "file '" + file.getAbsolutePath() + "': " + ioe.getMessage() : "stream"));
        }
        SVNErrorManager.error("Can't read length line in " + (file != null ? "file '" + file.getAbsolutePath() + "'" : "stream"));
        return null;
    }

    //to read single line files only
    public static String readSingleLine(File file, int limit) throws SVNException {
        if (file == null) {
            return null;
        }
        InputStream is = null;
        String line = null;
        try {
            is = SVNFileUtil.openFileForReading(file);
            line = readSingleLine(is, limit);
        } catch (IOException ioe) {
            SVNErrorManager.error("Can't read length line in file '" + file.getAbsolutePath() + "': " + ioe.getMessage());
        } finally {
            SVNFileUtil.closeFile(is);
        }
        return line;
    }

    // to read lines only from svn files ! (eol-specific)
    public static String readSingleLine(InputStream is, int limit) throws IOException, SVNException {
        int r = -1;
        StringBuffer lineBuffer = new StringBuffer();
        //TODO: sometimes it may be necessary to know that EOF has
        //been met
        for(int i = 0; i < limit; i++){
            r = is.read();
            if(r == '\n' || r == '\r' || r == -1){
                return lineBuffer.toString();
            }
            lineBuffer.append((char)r);
        }
        SVNErrorManager.error("Can't read length line in stream");
        return null;
    }

    private static Long[] readRootAndChangesOffset(File reposRootDir, long revision) throws SVNException {
        File revFile = FSRepositoryUtil.getRevisionFile(reposRootDir, revision); // getRevFile(revision);
        String eol = SVNFileUtil.getNativeEOLMarker();

        int size = 64;
        byte[] buffer = null;

        try {
            /*
             * svn: We will assume that the last line containing the two offsets
             * will never be longer than 64 characters. Read in this last block,
             * from which we will identify the last line.
             */
            buffer = readBytesFromFile(FILE_END_POS, -size, size, revFile);
        } catch (SVNException svne) {
            SVNErrorManager.error(svne.getMessage() + eol + "svn: No such revision " + revision);
        }

        // The last byte should be a newline.
        if (buffer[buffer.length - 1] != '\n') {
            SVNErrorManager.error("svn: Revision file '" + revFile.getAbsolutePath() + "' lacks trailing newline");
        }
        String bytesAsString = new String(buffer);
        if (bytesAsString.indexOf('\n') == bytesAsString.lastIndexOf('\n')) {
            SVNErrorManager.error("svn: Final line in revision file '" + revFile.getAbsolutePath() + "' is longer than 64 characters");
        }
        String[] lines = bytesAsString.split("\n");
        String lastLine = lines[lines.length - 1];
        String[] offsetsValues = lastLine.split(" ");
        if (offsetsValues.length < 2) {
            SVNErrorManager.error("svn: Final line in revision file '" + revFile.getAbsolutePath() + "' missing space");
        }

        long rootOffset = -1;
        try {
            rootOffset = Long.parseLong(offsetsValues[0]);
        } catch (NumberFormatException nfe) {
            SVNErrorManager.error("svn: Unparsable root offset number in revision file '" + revFile.getAbsolutePath() + "'");
        }

        long changesOffset = -1;
        try {
            changesOffset = Long.parseLong(offsetsValues[1]);
        } catch (NumberFormatException nfe) {
            SVNErrorManager.error("svn: Unparsable changes offset number in revision file '" + revFile.getAbsolutePath() + "'");
        }
        Long[] offsets = new Long[2];
        offsets[0] = new Long(rootOffset);
        offsets[1] = new Long(changesOffset);
        return offsets;
    }

    public static PathInfo readPathInfoFromReportFile(InputStream reportFile) throws IOException {
        int firstByte = reportFile.read();
        if (firstByte == -1 || firstByte == '-') {
            return null;
        }
        String path = readStringFromReportFile(reportFile);
        String linkPath = reportFile.read() == '+' ? readStringFromReportFile(reportFile) : null;
        long revision = readRevisionFromReportFile(reportFile);
        boolean startEmpty = reportFile.read() == '+' ? true : false;
        String lockToken = reportFile.read() == '+' ? readStringFromReportFile(reportFile) : null;
        return new PathInfo(path, linkPath, lockToken, revision, startEmpty);
    }

    public static String readStringFromReportFile(InputStream reportFile) throws IOException {
        int length = readNumberFromReportFile(reportFile);
        if (length == 0) {
            return "";
        }
        byte[] buffer = new byte[length];
        reportFile.read(buffer);
        return new String(buffer);
    }

    public static int readNumberFromReportFile(InputStream reportFile) throws IOException {
        int b;
        StringBuffer result = new StringBuffer();
        while ((b = reportFile.read()) != ':') {
            result.append((char)b);
        }
        return Integer.parseInt(result.toString(), 10);
    }

    public static long readRevisionFromReportFile(InputStream reportFile) throws IOException {
        if (reportFile.read() == '+') {
            return readNumberFromReportFile(reportFile);
        }

        return -1;
    }
  
    private static class OffsetContainerClass{
        private long offset;
        OffsetContainerClass(long newOffset){
            offset = newOffset;
        }
        public void setOffset(long newOffset){
            offset = newOffset;
        }
        public long getOffset(){
            return offset;
        }
    }
    
    /* Read changes from revision file, RandomAccessFile reader must be already opened.
     * OffsetContainerClass before invoking 'readChanges' method contains offset to changes, after invoking 
     * 'readChanges' contains offset to next changes (if file has them) in raFile */
    public static FSChange readChanges(File readRevisionFile, RandomAccessFile raReader, OffsetContainerClass offset, boolean isFirstInvocationForThisRevisionFile)throws SVNException{
        
        String line = FSReader.readNextLine(readRevisionFile, raReader, offset.getOffset(), isFirstInvocationForThisRevisionFile, 4096);
        offset.setOffset(offset.getOffset()+line.length()+1);

        if(line.length() == 0){
            return null;
        }        
        String [] piecesOfLine = line.split(" ");
        if(piecesOfLine == null){
            SVNErrorManager.error("Invalid changes line in rev-file");
        }
        if(piecesOfLine.length < 1 || piecesOfLine[0] == null){
            SVNErrorManager.error("Invalid changes line in rev-file");
        }
        String nodeRevStr = piecesOfLine[0];
        FSID nodeRevID = parseID(nodeRevStr);
        if(piecesOfLine.length < 2 || piecesOfLine[1] == null){
            SVNErrorManager.error("Invalid changes line in rev-file");
        }
        String changesKindStr = piecesOfLine[1];
        FSPathChangeKind changesKind = (FSPathChangeKind)FSConstants.ACTIONS_TO_CHANGE_KINDS.get(changesKindStr);
        if(changesKind == null){
            SVNErrorManager.error("Invalid change kind in rev file");
        }
        if(piecesOfLine.length < 3 || piecesOfLine[2] == null){
            SVNErrorManager.error("Invalid changes line in rev-file");
        }        
        String textModeStr = piecesOfLine[2];
        boolean textModeBool = false;
        if(textModeStr.equals(FSConstants.FLAG_TRUE)){
            textModeBool = true;
        }else if(textModeStr.equals(FSConstants.FLAG_FALSE)){
            textModeBool = false;
        } else{
            SVNErrorManager.error("Invalid text-mod flag in rev-file");
        }
        if(piecesOfLine.length < 4 || piecesOfLine[3] == null){
            SVNErrorManager.error("Invalid changes line in rev-file");
        }
        String propModeStr = piecesOfLine[3];
        boolean propModeBool = false;
        if(propModeStr.equals(new String(FSConstants.FLAG_TRUE))){
            propModeBool = true;
        }else if(propModeStr.equals(new String(FSConstants.FLAG_FALSE))){
            propModeBool = false;
        } else{
            SVNErrorManager.error("Invalid prop-mod flag in rev-file");
        }
        if(piecesOfLine.length < 5 || piecesOfLine[4] == null){
            SVNErrorManager.error("Invalid changes line in rev-file");
        }
        String pathStr = piecesOfLine[4];

        //offsetToChanges = new Long(offsetToChanges.longValue()+line.length()+1);
        String nextLine = FSReader.readNextLine(readRevisionFile, raReader, offset.getOffset(), isFirstInvocationForThisRevisionFile, 4096);
        offset.setOffset(offset.getOffset()+nextLine.length()+1);

        SVNLocationEntry copyfromEntry = null;
        if(nextLine.length() == 0){
            copyfromEntry = new SVNLocationEntry(FSConstants.SVN_INVALID_REVNUM, null);
        }else{
            String [] piecesOfNextLine = nextLine.split(" ");
            if(piecesOfNextLine == null){
                SVNErrorManager.error("Invalid changes line in rev-file");
            }
            if(piecesOfNextLine.length < 1 || piecesOfNextLine[0] == null){
                SVNErrorManager.error("Invalid changes line in rev-file");
            }
            if(piecesOfNextLine.length < 2 || piecesOfNextLine[1] == null){
                SVNErrorManager.error("Invalid changes line in rev-file");
            }
            copyfromEntry = new SVNLocationEntry(Long.parseLong(piecesOfNextLine[0]), piecesOfNextLine[1]);
        }
        //offsetToChanges = new Long(offsetToChanges.longValue()+nextLine.length()+1);
        return new FSChange(new String(pathStr), new FSID(nodeRevID), changesKind, textModeBool, propModeBool, copyfromEntry);
    }
    
    public static ArrayList walkDigestFiles(File digestFile, File reposRootDir, ArrayList lockArray)throws SVNException{        
        Collection children = new LinkedList();
        lockArray = lockArray == null ? new ArrayList(0) : lockArray;        
        SVNLock currentLock = FSReader.fetchLockFromDigestFile(digestFile, reposRootDir.getAbsolutePath(), children, reposRootDir);        
        if(currentLock != null){
            Date currentDate = new Date(System.currentTimeMillis());
            if(currentLock.getExpirationDate() == null || currentDate.compareTo(currentLock.getExpirationDate()) > 0){
                lockArray.add(currentLock);
            }            
        }        
        if(children == null || children.isEmpty()){
            return lockArray;
        }
        Iterator chIter = children.iterator();
        while(chIter.hasNext()){
            String digestName = (String)chIter.next();
            File childDigestFile = FSRepositoryUtil.getDigestFileFromDigest(digestName, reposRootDir);
            lockArray = walkDigestFiles(childDigestFile, reposRootDir, lockArray);
        }                
        return lockArray;
    }
    
    public static String generateLockToken(File reposRootDir)throws SVNException{
        if(reposRootDir == null || reposRootDir.getAbsolutePath() == null){
            SVNErrorManager.error("File object was not opened yet");           
        }
        UID forToken = new UID();        
        return FSConstants.SVN_OPAQUE_LOCK_TOKEN + forToken.toString();
    }    


    public static long getYoungestRevision(File reposRootDir) throws SVNException {
        File dbCurrentFile = FSRepositoryUtil.getFSCurrentFile(reposRootDir);
        String firstLine = readSingleLine(dbCurrentFile, 80);
        if (firstLine == null) {
            SVNErrorManager.error("svn: Can't read file '" + dbCurrentFile.getAbsolutePath() + "': End of file found");
        }
        String splittedLine[] = firstLine.split(" ");
        long latestRev = -1;
        try {
            latestRev = Long.parseLong(splittedLine[0]);
        } catch (NumberFormatException nfe) {
            SVNErrorManager.error("svn: Can't parse revision number in file '" + dbCurrentFile.getAbsolutePath() + "'");
        }
        return latestRev;
    }
    
    /* Store as keys in returned Map the paths of all node in ROOT that show a
     * significant change.  "Significant" means that the text or
     * properties of the node were changed, or that the node was added or
     * deleted.
     * Keys are String paths and values are FSLogChangedPath.
     */
    public static Map detectChanged(File reposRootDir, FSRevisionNodePool revNodesPool, FSRoot root)throws SVNException{
        Map returnChanged = new HashMap();
        Map changes = FSReader.getFSpathChanged(reposRootDir, root);        
        if(changes.size() == 0){
            return changes;
        }
        Set hashKeys = changes.keySet();
        Iterator chgIter = hashKeys.iterator();
        while(chgIter.hasNext()){
            char action;
            String hashPathKey = (String)chgIter.next();
            FSPathChange change = (FSPathChange)changes.get(hashPathKey);
            String path = hashPathKey;                      
            
            switch(change.getChangeKind().intValue()){
                case 4 /*FS_PATH_CHANGE_RESET*/:
                continue;
                case 1 /*FS_PATH_CHANGE_ADD*/:
                    action = 'A';
                    break;
                case 2 /*FS_PATH_CHANGE_DELETE*/:
                    action = 'D';
                    break;
                case 3 /*FS_PATH_CHANGE_REPLACE*/:
                    action = 'R';
                    break;
                case 0 /*FS_PATH_CHANGE_MODIFY*/:
                default:
                    action = 'M';
                    break;                    
            }
            FSLogChangedPath itemCopyfrom = new FSLogChangedPath(action, new SVNLocationEntry(FSConstants.SVN_INVALID_REVNUM, null));
            if(action == 'A' || action == 'R'){                                
                SVNLocationEntry copyfromEntry = FSReader.copiedFrom(reposRootDir, root.getRootRevisionNode(), path, revNodesPool);
                if(copyfromEntry.getPath() != null && FSRepository.isValidRevision(copyfromEntry.getRevision())){
                    itemCopyfrom = new FSLogChangedPath(action, copyfromEntry);
                }                
            }
            returnChanged.put(path, itemCopyfrom);
        }
        return returnChanged;
    }
    /* Return MAP with hash containing descriptions of the paths changed under ROOT. 
     * The hash is keyed with String paths and has FSPathChange values
     */    
    private static Map getFSpathChanged(File reposRootDir, FSRoot root)throws SVNException{   
        Map changedPaths = new HashMap();
        if(root.isTxnRoot() == true){
            File txnFile = FSRepositoryUtil.getTxnChangesFile(root.getTxnId(), reposRootDir);
            Object[] result = FSReader.fetchAllChanges(changedPaths, txnFile, false, 0, root.getCopyfromCache());
            root.setCopyfromCache((Map)result[1]);
            return (Map)result[0];
        }           
        long changeOffset = FSReader.getChangesOffset(reposRootDir, root.getRevision());
        File revFile = FSRepositoryUtil.getRevisionFile(reposRootDir, root.getRevision());
        Object[] result = FSReader.fetchAllChanges(changedPaths, revFile, true, changeOffset, root.getCopyfromCache());    
        root.setCopyfromCache((Map)result[1]);
        return (Map)result[0];      
    }
    /* Discover the copy ancestry of PATH under ROOT.  Return a relevant
     * ancestor/revision combination in PATH(SVNLocationEntry) and REVISON(SVNLocationEntry)*/
    public static SVNLocationEntry copiedFrom(File reposRootDir, FSRevisionNode root, String path, FSRevisionNodePool revNodesPool)throws SVNException{
        FSRevisionNode node = revNodesPool.getRevisionNode(FSRoot.createRevisionRoot(root.getId().getRevision(), root), path, reposRootDir);
        return new SVNLocationEntry(node.getCopyFromRevision(), node.getCopyFromPath());
    }
}
