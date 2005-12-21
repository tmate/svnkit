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
import org.tmatesoft.svn.core.io.diff.SVNDiffWindow;
import org.tmatesoft.svn.core.io.diff.SVNDiffWindowApplyBaton;
import org.tmatesoft.svn.core.io.diff.SVNDiffWindowBuilder;
import org.tmatesoft.svn.core.io.diff.SVNDiffInstruction;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.io.ByteArrayInputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.FileNotFoundException;
import java.io.File;
import java.util.Date;
import java.util.LinkedList;
import java.util.Iterator;
import java.util.Map;
import java.util.Collection;
import java.util.HashMap;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class FSReader {

    // to mean the end of a file
    public static final long FILE_END_POS = -1;

    /* String[0] - is to be the fetched out node-id
     * String[1] - is to be the fetched out copy-id
     */
    public static String[] readNextIds(String txnId, File reposRootDir) throws SVNException {
        String[] ids = new String[2];
        ByteArrayOutputStream idsBuffer = new ByteArrayOutputStream();
        InputStream nextIdsFile = SVNFileUtil.openFileForReading(FSRepositoryUtil.getTxnNextIdsFile(txnId, reposRootDir));
        try{
            readBytesFromStream(FSConstants.MAX_KEY_SIZE*2 + 3, nextIdsFile, idsBuffer);
        }catch(IOException ioe){
            SVNErrorManager.error("Can't read length line in file '" + FSRepositoryUtil.getTxnNextIdsFile(txnId, reposRootDir).getAbsolutePath() + "': " + ioe.getMessage());
        }finally{
            SVNFileUtil.closeFile(nextIdsFile);
        }
        String idsToParse = idsBuffer.toString();
        idsToParse = idsToParse.split("\\n")[0];
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
            SVNLock lock = getLock(path, haveWriteLock, null, reposRootDir);
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
        SVNLock lock = fetchLock(digestFile, null, children, reposRootDir);
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
    
    public static SVNLock getLock(String repositoryPath, boolean haveWriteLock, Collection children, File reposRootDir) throws SVNException {
        SVNLock lock = fetchLock(null, repositoryPath, children, reposRootDir);
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
    
    public static SVNLock fetchLock(File digestFile, String repositoryPath, Collection children, File reposRootDir) throws SVNException {
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
            if(davComment == null){
                SVNErrorManager.error("svn: Corrupt lockfile for path '" + lockPath + "' in filesystem '" + FSRepositoryUtil.getRepositoryDBDir(reposRootDir).getAbsolutePath() + "'");
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
                is = readPlainRepresentation(textRepresent, reposRootDir);
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
                is = readPlainRepresentation(propsRepresent, reposRootDir);
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

    public static InputStream readPlainRepresentation(FSRepresentation representation, File reposRootDir) throws SVNException {
        File revFile = FSRepositoryUtil.getRevisionFile(reposRootDir, representation.getRevision());
        InputStream is = null;
        try {
            is = SVNFileUtil.openFileForReading(revFile);

            try {
                readBytesFromStream(new Long(representation.getOffset()).intValue(), is, null);
            } catch (IOException ioe) {
                SVNErrorManager.error("svn: Can't set position pointer in file '" + revFile + "': " + ioe.getMessage());
            }
            String header = null;
            try {
                header = readSingleLine(is);
            } catch (FileNotFoundException fnfe) {
                SVNErrorManager.error("svn: Can't open file '" + revFile.getAbsolutePath() + "': " + fnfe.getMessage());
            } catch (IOException ioe) {
                SVNErrorManager.error("svn: Can't read file '" + revFile.getAbsolutePath() + "': " + ioe.getMessage());
            }

            if (!FSConstants.REP_PLAIN.equals(header)) {
                SVNErrorManager.error("svn: Malformed representation header in revision file '" + revFile.getAbsolutePath() + "'");
            }

            MessageDigest digest = null;
            try {
                digest = MessageDigest.getInstance("MD5");
            } catch (NoSuchAlgorithmException nsae) {
                SVNErrorManager.error("svn: Can't check the digest in revision file '" + revFile.getAbsolutePath() + "': " + nsae.getMessage());
            }
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            try {
                readBytesFromStream(representation.getSize(), is, os);
            } catch (IOException ioe) {
                SVNErrorManager.error("svn: Can't read representation in revision file '" + revFile.getAbsolutePath() + "': " + ioe.getMessage());
            }
            byte[] bytes = os.toByteArray();
            digest.update(bytes);
            // Compare read and expected checksums
            if (!MessageDigest.isEqual(SVNFileUtil.fromHexDigest(representation.getHexDigest()), digest.digest())) {
                SVNErrorManager.error("svn: Checksum mismatch while reading representation:" + SVNFileUtil.getNativeEOLMarker() + "   expected:  " + representation.getHexDigest()
                        + SVNFileUtil.getNativeEOLMarker() + "     actual:  " + SVNFileUtil.toHexDigest(digest));
            }
            return new ByteArrayInputStream(bytes);
        } finally {
            SVNFileUtil.closeFile(is);
        }
    }

    public static void readDeltaRepresentation(FSRepresentation representation, OutputStream targetOS, File reposRootDir) throws SVNException {
        if(representation == null){
            return;
        }
        File revFile = FSRepositoryUtil.getRevisionFile(reposRootDir, representation.getRevision());
        DefaultFSDeltaCollector collector = new DefaultFSDeltaCollector();
        getDiffWindow(collector, representation.getRevision(), representation.getOffset(), representation.getSize(), reposRootDir);

        MessageDigest digest = null;
        try {
            digest = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException nsae) {
            SVNErrorManager.error("svn: Can't check the digest in revision file '" + revFile.getAbsolutePath() + "': " + nsae.getMessage());
        }

        DiffWindowsIOStream windowsStreams = new DiffWindowsIOStream();
        SVNDiffWindowApplyBaton diffWindowApplyBaton = null;
        String finalChecksum = null;
        try {
            while (collector.getWindowsCount() > 0) {
                SVNDiffWindow diffWindow = collector.getLastWindow();
                diffWindowApplyBaton = windowsStreams.getDiffWindowApplyBaton(digest);
                diffWindow.apply(diffWindowApplyBaton, collector.getDeltaDataStorage(diffWindow));
                finalChecksum = diffWindowApplyBaton.close();
                collector.removeWindow(diffWindow);
            }

            // Compare read and expected checksums
            if (!MessageDigest.isEqual(SVNFileUtil.fromHexDigest(representation.getHexDigest()), SVNFileUtil.fromHexDigest(finalChecksum))) {
                SVNErrorManager.error("svn: Checksum mismatch while reading representation:" + SVNFileUtil.getNativeEOLMarker() + "   expected:  " + representation.getHexDigest()
                        + SVNFileUtil.getNativeEOLMarker() + "     actual:  " + SVNFileUtil.toHexDigest(digest));
            }

            InputStream sourceStream = windowsStreams.getTemporarySourceInputStream();
            int b = -1;
            while (true) {
                b = sourceStream.read();
                if (b == -1) {
                    break;
                }
                targetOS.write(b);
                targetOS.flush();
            }

        } catch (IOException ioe) {
            SVNErrorManager.error("Can't write to target stream: " + ioe.getMessage());
        } finally {
            windowsStreams.closeSourceStream();
            windowsStreams.closeTargetStream();
        }
    }

    // returns the amount of bytes read from a revision file
    public static void getDiffWindow(IFSDeltaCollector collector, long revision, long offset, long length, File reposRootDir) throws SVNException {
        if (collector == null) {
            return;
        }

        File revFile = FSRepositoryUtil.getRevisionFile(reposRootDir, revision);
        InputStream is = null;
        try {
            is = SVNFileUtil.openFileForReading(revFile);

            try {
                readBytesFromStream(new Long(offset).intValue(), is, null);
            } catch (IOException ioe) {
                SVNErrorManager.error("svn: Can't set position pointer in file '" + revFile + "': " + ioe.getMessage());
            }
            String header = null;
            try {
                header = readSingleLine(is);
            } catch (FileNotFoundException fnfe) {
                SVNErrorManager.error("svn: Can't open file '" + revFile.getAbsolutePath() + "': " + fnfe.getMessage());
            } catch (IOException ioe) {
                SVNErrorManager.error("svn: Can't read file '" + revFile.getAbsolutePath() + "': " + ioe.getMessage());
            }

            if (header != null && header.startsWith(FSConstants.REP_DELTA)) {
                ByteArrayOutputStream os = new ByteArrayOutputStream();
                try {
                    readBytesFromStream(length, is, os);
                } catch (IOException ioe) {
                    SVNErrorManager.error("svn: Can't read representation in revision file '" + revFile.getAbsolutePath() + "': " + ioe.getMessage());
                }
                byte[] bytes = os.toByteArray();
                SVNDiffWindowBuilder diffWindowBuilder = SVNDiffWindowBuilder.newInstance();

                int bufferOffset = 0;
                int numOfCopyFromSourceInstructions = 0;
                LinkedList windowsPerRevision = new LinkedList();
                HashMap windowsData = new HashMap();

                while (bufferOffset < bytes.length) {
                    int metaDataLength = diffWindowBuilder.accept(bytes, bufferOffset);
                    SVNDiffWindow window = diffWindowBuilder.getDiffWindow();
                    if (window != null) {
                        for (int i = 0; i < diffWindowBuilder.getInstructionsData().length; i++) {
                            int type = (diffWindowBuilder.getInstructionsData()[i] & 0xC0) >> 6;
                            if (type == SVNDiffInstruction.COPY_FROM_SOURCE) {
                                numOfCopyFromSourceInstructions++;
                            }
                        }
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        try {
                            baos.write(diffWindowBuilder.getInstructionsData());
                        } catch (IOException ioe) {
                            SVNErrorManager.error("svn: Can't construct a diff window due to errors: " + ioe.getMessage());
                        }
                        long newDataLength = window.getNewDataLength();
                        newDataLength = (newDataLength + metaDataLength > bytes.length) ? bytes.length - metaDataLength : newDataLength;

                        for (int i = 0; i < newDataLength; i++) {
                            baos.write(bytes[metaDataLength + i]);
                        }
                        SVNFileUtil.closeFile(baos);

                        windowsPerRevision.addLast(window);
                        windowsData.put(window, baos);

                        bufferOffset = metaDataLength + (int) newDataLength;
                        if (bufferOffset < bytes.length) {
                            diffWindowBuilder.reset(SVNDiffWindowBuilder.OFFSET);
                        } else {
                            break;
                        }
                    } else {
                        break;
                    }
                }

                while (windowsPerRevision.size() > 0) {
                    SVNDiffWindow nextWindow = (SVNDiffWindow) windowsPerRevision.getLast();
                    ByteArrayOutputStream deltaDataBytes = (ByteArrayOutputStream) windowsData.get(nextWindow);
                    OutputStream deltaDataStorage = collector.insertWindow(nextWindow);
                    try {
                        deltaDataStorage.write(deltaDataBytes.toByteArray());
                        deltaDataStorage.flush();
                    } catch (IOException ioe) {
                        SVNErrorManager.error("svn: Can't construct a diff window due to errors: " + ioe.getMessage());
                    }
                    SVNFileUtil.closeFile(deltaDataStorage);
                    windowsData.remove(nextWindow);
                    windowsPerRevision.removeLast();
                }
                if (!FSConstants.REP_DELTA.equals(header)) {
                    String[] baseLocation = header.split(" ");
                    if (baseLocation.length != 4) {
                        SVNErrorManager.error("svn: Malformed representation header in revision file '" + revFile.getAbsolutePath() + "'");
                    }

                    // check up if there are any source instructions
                    if (numOfCopyFromSourceInstructions == 0) {
                        return;
                    }

                    try {
                        revision = Long.parseLong(baseLocation[1]);
                        offset = Long.parseLong(baseLocation[2]);
                        length = Long.parseLong(baseLocation[3]);
                        if (revision < 0 || offset < 0 || length < 0) {
                            throw new NumberFormatException();
                        }
                    } catch (NumberFormatException nfe) {
                        SVNErrorManager.error("svn: Malformed representation header in revision file '" + revFile.getAbsolutePath() + "'");
                    }

                    getDiffWindow(collector, revision, offset, length, reposRootDir);
                }
            } else {
                SVNErrorManager.error("svn: Malformed representation header in revision file '" + revFile.getAbsolutePath() + "'");
            }
        } finally {
            SVNFileUtil.closeFile(is);
        }
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
        if (offsets == null || offsets.length == 0 || offsets.length < 5) {
            throw new SVNException();
        }

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
                String line = readNextLine(revFile, raFile, offset, isFirstLine);
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

    private static long readBytesFromStream(long bytesToRead, InputStream is, OutputStream os) throws IOException {
        if (is == null) {
            return -1;
        }
        if (os != null) {
            long bytesRead = 0;
            while (bytesRead != bytesToRead) {
                int b = is.read();
                if (b == -1) {
                    break;
                }
                os.write(b);
                bytesRead++;
            }
            return bytesRead;
        }
        return (int) is.skip(bytesToRead);
    }
    //TODO: replace BufferedReader for some other realization of reading
    //lines (it may cause unpredictable behaviour if a file is HUGE!)
    public static String readNextLine(File file, RandomAccessFile raFile, long offset, boolean makeOffset) throws SVNException {
        offset = (offset < 0) ? 0 : offset;
        if(makeOffset){
            try {
                raFile.seek(offset);
            } catch (IOException ioe) {
                SVNErrorManager.error("svn: Can't set position pointer in file '" + file.getAbsolutePath() + "'");
            }
        }
        StringBuffer lineBuffer = new StringBuffer();
        try {
            int r = -1;
            while((r = raFile.read()) != '\n'){
                if(r == -1){
                    break;
                }
                lineBuffer.append((char)r);
            }
        } catch (IOException ioe) {
            SVNErrorManager.error("svn: Can't read file ");
        }
        String line = lineBuffer.toString();
        return line;
    }

    //to read single line files only
    public static String readSingleLine(File file) throws SVNException {
        InputStream is = SVNFileUtil.openFileForReading(file);
        if (is == null) {
            SVNErrorManager.error("svn: Can't open file '" + file.getAbsolutePath() + "'");
        }

        BufferedReader reader = null;
        String line = null;
        try {
            reader = new BufferedReader(new InputStreamReader(is));
            line = reader.readLine();
        } catch (IOException ioe) {
            SVNErrorManager.error("svn: Can't read from file '" + file.getAbsolutePath() + "': " + ioe.getMessage());
        } finally {
            SVNFileUtil.closeFile(reader);
        }
        return line;
    }

    // to read lines only from svn files ! (eol-specific)
    public static String readSingleLine(InputStream is) throws FileNotFoundException, IOException {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        while (true) {
            int b = is.read();
            if (b == -1 || '\n' == (byte) b) {
                break;
            }
            os.write(b);
        }
        return new String(os.toByteArray());
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
  
    /* Read changes from revision file, RandomAccessFile reader must be already opened*/
    public static FSChange readChanges(File readRevisionFile, RandomAccessFile raReader, long offsetToChanges, boolean isFirstInvocationForThisRevisionFile)throws SVNException{
        /*noo affset */
        String line = FSReader.readNextLine(readRevisionFile, raReader, offsetToChanges, isFirstInvocationForThisRevisionFile);
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
        FSPathChangeKind changesKind = null;
        if(changesKindStr.equals(new String(FSConstants.ACTION_MODIFY))){
            changesKind.equals(FSPathChangeKind.FS_PATH_CHANGE_MODIFY);
        }
        else if(changesKindStr.equals(new String(FSConstants.ACTION_ADD))){
            changesKind.equals(FSPathChangeKind.FS_PATH_CHANGE_ADD);
        }
        else if(changesKindStr.equals(new String(FSConstants.ACTION_DELETE))){
            changesKind.equals(FSPathChangeKind.FS_PATH_CHANGE_DELETE);
        }
        else if(changesKindStr.equals(new String(FSConstants.ACTION_REPLACE))){
            changesKind.equals(FSPathChangeKind.FS_PATH_CHANGE_REPLACE);
        }
        else if(changesKindStr.equals(new String(FSConstants.ACTION_RESET))){
            changesKind.equals(FSPathChangeKind.FS_PATH_CHANGE_RESET);
        }
        else{
            SVNErrorManager.error("Invalid change kind in rev file");
        }
        
        if(piecesOfLine.length < 3 || piecesOfLine[2] == null){
            SVNErrorManager.error("Invalid changes line in rev-file");
        }        
        String textModeStr = piecesOfLine[2];
        boolean textModeBool = false;
        if(textModeStr.equals(FSConstants.FLAG_TRUE)){
            textModeBool = true;
        }
        if(textModeStr.equals(FSConstants.FLAG_FALSE)){
            textModeBool = false;
        }
        else{
            SVNErrorManager.error("Invalid text-mod flag in rev-file");
        }
        
        if(piecesOfLine.length < 4 || piecesOfLine[3] == null){
            SVNErrorManager.error("Invalid changes line in rev-file");
        }
        String propModeStr = piecesOfLine[3];
        boolean propModeBool = false;
        if(propModeStr.equals(new String(FSConstants.FLAG_TRUE))){
            propModeBool = true;
        }
        if(propModeStr.equals(new String(FSConstants.FLAG_FALSE))){
            propModeBool = false;
        }
        else{
            SVNErrorManager.error("Invalid prop-mod flag in rev-file");
        }
        
        if(piecesOfLine.length < 5 || piecesOfLine[4] == null){
            SVNErrorManager.error("Invalid changes line in rev-file");
        }
        String pathStr = piecesOfLine[4];
        
        String nextLine = FSReader.readNextLine(readRevisionFile, raReader, 0, isFirstInvocationForThisRevisionFile);        
        SVNLocationEntry copyfromEntry = null;
        if(nextLine.length() == 0){
            copyfromEntry = new SVNLocationEntry(FSConstants.SVN_INVALID_REVNUM, null);
        }
        else{
            String [] piesesOfNextLine = nextLine.split(" ");
            if(piesesOfNextLine == null){
                SVNErrorManager.error("Invalid changes line in rev-file");
            }
            if(piesesOfNextLine.length < 1 || piesesOfNextLine[0] == null){
                SVNErrorManager.error("Invalid changes line in rev-file");
            }
            if(piesesOfNextLine.length < 2 || piesesOfNextLine[1] == null){
                SVNErrorManager.error("Invalid changes line in rev-file");
            }
            copyfromEntry = new SVNLocationEntry(Long.parseLong(piesesOfNextLine[0]), piesesOfNextLine[1]);
        }
        return new FSChange(new String(pathStr), new FSID(nodeRevID), changesKind, textModeBool, propModeBool, copyfromEntry);
    }
}
