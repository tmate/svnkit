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

import java.util.*;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLock;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.util.SVNTimeUtil;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNProperties;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.io.diff.SVNDiffWindow;
import org.tmatesoft.svn.core.io.diff.SVNDiffWindowApplyBaton;
import org.tmatesoft.svn.core.io.diff.SVNDiffWindowBuilder;
import org.tmatesoft.svn.core.io.diff.SVNDiffInstruction;
import org.tmatesoft.svn.core.io.SVNLocationEntry;

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
import java.util.Map;
import java.util.Collection;
import java.util.HashMap;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class FSReader {

    // to mean the end of a file
    public static final long FILE_END_POS = -1;

    public static final String HEADER_ID = "id";
    public static final String HEADER_TYPE = "type";
    public static final String HEADER_COUNT = "count";
    public static final String HEADER_PROPS = "props";
    public static final String HEADER_TEXT = "text";
    public static final String HEADER_CPATH = "cpath";
    public static final String HEADER_PRED = "pred";
    public static final String HEADER_COPYFROM = "copyfrom";
    public static final String HEADER_COPYROOT = "copyroot";
    public static final String REP_DELTA = "DELTA";
    public static final String REP_PLAIN = "PLAIN";

    public static final int MD5_DIGESTSIZE = 16;

    public static boolean checkRelated(FSID idOne, FSID idTwo){
    	return false;
    }
    
    public static FSID getRevNodeID(FSRevisionNode root, String path){
    	return null;
    }
    
    public static String pathIsChild(String path, String pathChild){
    	return null;
    }
    
    public static void copiedFrom(long rev_p, String path_p, FSRevisionNode root, String path){
    	//do something
    }
    
    public static void closestCopy(FSRevisionNode root_p, String path_p, FSRevisionNode root, String path){
    	//do something
    }
    
    public static Hashtable traceNodeLocations(File reposRootDir, String absPath, long pegRevision, long[] revisions){
    	long[] locationRevs = new long[revisions.length];
    	String path;
    	boolean isAncestor = false;
    	long revision;
    	Hashtable locations = new Hashtable();
    	FSRevisionNode root;
    	FSID id=null;
    	//check if absPath is really absolute path relatively to repository
    	if(absPath.charAt(0) != '/'){
    		absPath = "/".concat(absPath);
    	}
    	//Sort revisions from greatest downward
    	Arrays.sort(revisions);
    	for(int count=0; count<revisions.length; ++count){
    		locationRevs[count] = revisions[revisions.length-(count+1)];
    	}
   	    //Ignore revisions R that are younger than the peg_revisions where
        //path@peg_revision is not an ancestor of path@R.
    	int count = 0;
    	isAncestor = false;
    	for(count=0; count<revisions.length&&locationRevs[count]>pegRevision; ++count){
    		if(true == (isAncestor = FSNodeHistory.checkAncestryOfPegPath(reposRootDir, absPath, pegRevision, locationRevs[count]))){
    			break;
    		}
    	}
    	revision = isAncestor ? locationRevs[count] : pegRevision;
    	path = absPath;
    	
    	while(count < revisions.length){
    		FSRevisionNode croot = null;
    		long crev=0; 
			long srev=0;
			String cpath=null; 
			String spath=null;
			String reminder=null;
    		try{
   		      //Find the target of the innermost copy relevant to path@revision.
   	          //The copy may be of path itself, or of a parent directory.    			
    			root = getRootRevNode(reposRootDir, revision);
    			closestCopy(croot, cpath, root, path);
    			if(croot == null){
    				break;
    			}
    		}catch(SVNException ex){
    			System.out.println(ex.getMessage());
    		}
    	  //Assign the current path to all younger revisions until we reach
  	      //the copy target rev
    		//!!!here must be INVALID_REVISION_NUMBER, but not -1!!!
    		crev = croot.getRevNodeID().isTxn() ? -1 : croot.getRevNodeID().getRevision();
    		while((count < revisions.length) && (locationRevs[count] >= crev)){
    			//!!!Possible bad usage new Long(locationRevs[count]) as a key
    			//!!!Need good key for locations hash table
    			locations.put(new Long(locationRevs[count]), path);
    			++count;
    		}
    	  // Follow the copy to its source.  Ignore all revs between the
          // copy target rev and the copy source rev (non-inclusive).
    		copiedFrom(srev, spath, croot, cpath);
    		while((count < revisions.length) && locationRevs[count] > srev ){
    			++count;
    		}
    	      /* Ultimately, it's not the path of the closest copy's source
            that we care about -- it's our own path's location in the
            copy source revision.  So we'll tack the relative path that
            expresses the difference between the copy destination and our
            path in the copy revision onto the copy source path to
            determine this information.  

            In other words, if our path is "/branches/my-branch/foo/bar",
            and we know that the closest relevant copy was a copy of
            "/trunk" to "/branches/my-branch", then that relative path
            under the copy destination is "/foo/bar".  Tacking that onto
            the copy source path tells us that our path was located at
            "/trunk/foo/bar" before the copy.
         */
    		reminder = (path.compareTo(cpath) == 0) ? new String("") : new String(pathIsChild(cpath, path));
    		path = spath.concat(reminder);
    		revision = srev;
    	}
   	  /* There are no copies relevant to path@revision.  So any remaining
        revisions either predate the creation of path@revision or have
        the node existing at the same path.  We will look up path@lrev
        for each remaining location-revision and make sure it is related
        to path@revision. */    	
    	try{
			root = getRootRevNode(reposRootDir, revision);	
			id = getRevNodeID(root, path);
    	}catch(SVNException ex){   
    		System.out.println(ex.getMessage());
    	}
    	while(count < revisions.length){
    		SVNNodeKind kind=null;
    		FSID lrev_id=null;
    		try{
    			root = getRootRevNode(reposRootDir, revisions[count]);
    			//!!!need to understand how to use implemented 
    			//!!!FSRepository's checkPath 
    			//kind = checkPath(root, path);
    			if(kind == SVNNodeKind.NONE){
    				break;
    			}
    			lrev_id = getRevNodeID(root, path);
    			if( checkRelated(id, lrev_id) == false ){
    				break;
    			}
    		}catch(SVNException ex){
    			System.out.println(ex.getMessage());
    		}
    		///!!!!need to deside good key for hash table
    		locations.put(new Long(locationRevs[count]), path);
    		++count;
    	}

    	return locations;
    }
    public static SVNLock getLock(String repositoryPath, boolean haveWriteLock, Collection children, File reposRootDir) throws SVNException {
        SVNLock lock = fetchLock(repositoryPath, children, reposRootDir);
        if(lock == null){
            return null;//SVNErrorManager.error("svn: No lock on path '" + fsAbsPath + "' in filesystem '" + FSRepositoryUtil.getRepositoryDBDir(reposRootDir).getAbsolutePath() + "'");
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
    
    
    
    public static SVNLock fetchLock(String repositoryPath, Collection children, File reposRootDir) throws SVNException {
        File digestLockFile = FSRepositoryUtil.getDigestFileFromRepositoryPath(repositoryPath, reposRootDir);
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

    /*
     * If root is not null, tries to find the rev-node for repositoryPath 
     * in the provided root, oherwise if root is null, uses the provided 
     * revision to get the root first. 
     */
    public static FSRevisionNode getRevisionNode(File reposRootDir, String repositoryPath, FSRevisionNode root, long revision) throws SVNException {

        String absPath = repositoryPath;

        String nextPathComponent = null;
        FSRevisionNode parent = root != null ? root : FSReader.getRootRevNode(reposRootDir, revision);
        FSRevisionNode child = null;

        while (true) {
            nextPathComponent = SVNPathUtil.head(absPath);
            absPath = SVNPathUtil.removeHead(absPath);

            if (nextPathComponent.length() == 0) {
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
        if (child == null || child.length() == 0 || "..".equals(child) || child.indexOf('/') != -1) {

            SVNErrorManager.error("svn: Attempted to open node with an illegal name '" + child + "'");
        }
        Map entries = getDirEntries(parent, reposRootDir);
        FSRepresentationEntry entry = entries != null ? (FSRepresentationEntry) entries.get(child) : null;
        return entry == null ? null : getRevNodeFromID(reposRootDir, entry.getId());
    }

    public static FSRepresentationEntry getChildEntry(String child, FSRevisionNode parent, File reposRootDir) throws SVNException {
        if (child == null || child.length() == 0 || "..".equals(child) || child.indexOf('/') != -1) {
            SVNErrorManager.error("svn: Attempted to open node with an illegal name '" + child + "'");
        }
        Map entries = getDirEntries(parent, reposRootDir);
        FSRepresentationEntry entry = entries != null ? (FSRepresentationEntry) entries.get(child) : null;
        return entry;
    }
    
    public static Map getDirEntries(FSRevisionNode revNode, File reposRootDir) throws SVNException {
        if (revNode == null || revNode.getType() != SVNNodeKind.DIR) {
            SVNErrorManager.error("svn: Can't get entries of non-directory");
        }

        return getDirContents(revNode.getTextRepresentation(), reposRootDir);
    }

    public static Map getProperties(FSRevisionNode revNode, File reposRootDir) throws SVNException {
        return getProplist(revNode.getPropsRepresentation(), reposRootDir);
    }

    private static Map getDirContents(FSRepresentation representation, File reposRootDir) throws SVNException {
        if (representation == null) {
            return new HashMap();
        }
        InputStream is = null;
        try {
            is = readPlainRepresentation(representation, reposRootDir);
            return parsePlainRepresentation(is, false);
        } catch (IOException ioe) {
            SVNErrorManager.error("svn: Can't read representation in revision file '" + FSRepositoryUtil.getRevisionFile(reposRootDir, representation.getRevision()).getAbsolutePath() + "': "
                    + ioe.getMessage());
        } catch (SVNException svne) {
            SVNErrorManager.error("svn: Revision file '" + FSRepositoryUtil.getRevisionFile(reposRootDir, representation.getRevision()).getAbsolutePath() + "' corrupt"
                    + SVNFileUtil.getNativeEOLMarker() + svne.getMessage());
        } finally {
            SVNFileUtil.closeFile(is);
        }
        return new HashMap();
    }

    private static Map getProplist(FSRepresentation representation, File reposRootDir) throws SVNException {
        if (representation == null) {
            return new HashMap();
        }
        InputStream is = null;
        try {
            is = readPlainRepresentation(representation, reposRootDir);
            return parsePlainRepresentation(is, true);
        } catch (IOException ioe) {
            SVNErrorManager.error("svn: Can't read representation in revision file '" + FSRepositoryUtil.getRevisionFile(reposRootDir, representation.getRevision()).getAbsolutePath() + "': "
                    + ioe.getMessage());
        } catch (SVNException svne) {
            SVNErrorManager.error("svn: Revision file '" + FSRepositoryUtil.getRevisionFile(reposRootDir, representation.getRevision()).getAbsolutePath() + "' corrupt"
                    + SVNFileUtil.getNativeEOLMarker() + svne.getMessage());
        } finally {
            SVNFileUtil.closeFile(is);
        }
        return null;
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

            if (!REP_PLAIN.equals(header)) {
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
            ioe.printStackTrace();
            // ????
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

            if (header != null && header.startsWith(REP_DELTA)) {
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
                if (!REP_DELTA.equals(header)) {
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
     * PLAIN hash format is common for dir contents as well as for props
     * representation - so, isProps is needed to differentiate between text and
     * props repreentation
     */
    private static Map parsePlainRepresentation(InputStream is, boolean isProps) throws IOException, SVNException {
        Map representationMap = new HashMap();
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        while (readEntry('K', is, os)) {
            String key = new String(os.toByteArray(), "UTF-8");
            os.reset();
            if (!readEntry('V', is, os)) {
                throw new IOException("malformed file format");
            }
            String value = new String(os.toByteArray(), "UTF-8");
            os.reset();
            if (!isProps) {
                FSRepresentationEntry nextRepEntry = null;
                try {
                    nextRepEntry = parseRepEntryValue(key, value);
                } catch (SVNException svne) {
                    SVNErrorManager.error("svn: Directory entry '" + key + "' corrupt");
                }
                representationMap.put(key, nextRepEntry);
            } else {
                representationMap.put(key, value);
            }
        }

        return representationMap;
    }

    private static FSRepresentationEntry parseRepEntryValue(String name, String value) throws SVNException {
        String[] values = value.split(" ");
        if (values == null || values.length < 2) {
            throw new SVNException();
        }

        SVNNodeKind type = SVNNodeKind.parseKind(values[0]);
        if (type != SVNNodeKind.DIR && type != SVNNodeKind.FILE) {
            throw new SVNException();
        }

        FSID id = parseID(values[1], null);
        return new FSRepresentationEntry(id, type, name);
    }

    private static boolean readEntry(char type, InputStream is, OutputStream os) throws IOException {
        int length = readLength(is, type);
        if (length < 0) {
            return false;
        }
        if (os != null) {
            byte[] value = new byte[length];
            int r = is.read(value);
            if (r < length) {
                throw new IOException("malformed file format");
            }
            os.write(value, 0, r);
        } else {
            while (length > 0) {
                length -= is.skip(length);
            }
        }
        if (is.read() != '\n') {
            throw new IOException("malformed file format");
        }
        return true;
    }

    private static int readLength(InputStream is, char type) throws IOException {
        byte[] buffer = new byte[255];
        int r = is.read(buffer, 0, 4);
        if (r != 4) {
            throw new IOException("malformed file format");
        }
        // either END\n or K x\n
        if (buffer[0] == 'E' && buffer[1] == 'N' && buffer[2] == 'D' && buffer[3] == '\n') {
            return -1;
        } else if (buffer[0] == type && buffer[1] == ' ') {
            int i = 4;
            if (buffer[3] != '\n') {
                while (true) {
                    int b = is.read();
                    if (b < 0) {
                        throw new IOException("malformed file format");
                    } else if (b == '\n') {
                        break;
                    }
                    buffer[i] = (byte) (0xFF & b);
                    i++;
                }
            } else {
                i = 3;
            }
            String length = new String(buffer, 2, i - 2);
            return Integer.parseInt(length);
        }
        throw new IOException("malformed file format");
    }

    public static FSRevisionNode getRootRevNode(File reposRootDir, long revision) throws SVNException {
        FSID id = new FSID(FSID.ID_INAPPLICABLE, FSID.ID_INAPPLICABLE, FSID.ID_INAPPLICABLE, revision, getRootOffset(reposRootDir, revision));
        return getRevNodeFromID(reposRootDir, id);
    }

    public static FSRevisionNode getRevNodeFromID(File reposRootDir, FSID id) throws SVNException {
        File revFile = FSRepositoryUtil.getRevisionFile(reposRootDir, id.getRevision());

        FSRevisionNode revNode = new FSRevisionNode();
        long offset = id.getOffset();

        Map headers = readRevNodeHeaders(revFile, offset);

        // Read the rev-node id.
        String revNodeId = (String) headers.get(HEADER_ID);
        if (revNodeId == null) {
            SVNErrorManager.error("svn: Missing node-id in node-rev in revision file '" + revFile.getAbsolutePath() + "'");
        }

        try {
            revNode.setRevNodeID(parseID(revNodeId, null));
        } catch (SVNException svne) {
            SVNErrorManager.error("svn: Corrupt node-id in node-rev in revision file '" + revFile.getAbsolutePath() + "'");
        }

        // Read the type.
        SVNNodeKind nodeKind = SVNNodeKind.parseKind((String) headers.get(HEADER_TYPE));
        if (nodeKind == SVNNodeKind.NONE || nodeKind == SVNNodeKind.UNKNOWN) {
            SVNErrorManager.error("svn: Missing kind field in node-rev in revision file '" + revFile.getAbsolutePath() + "'");
        }
        revNode.setType(nodeKind);

        // Read the 'count' field.
        String countString = (String) headers.get(HEADER_COUNT);
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
        String propsRepr = (String) headers.get(HEADER_PROPS);
        if (propsRepr != null) {
            try {
                parseRepresentationHeader(propsRepr, revNode, false);
            } catch (SVNException svne) {
                throw new SVNException("svn: Malformed props rep offset line in node-rev '" + revFile.getAbsolutePath() + "'");
            }
        }

        // Get the data location (if any).
        String textRepr = (String) headers.get(HEADER_TEXT);
        if (textRepr != null) {
            try {
                parseRepresentationHeader(textRepr, revNode, true);
            } catch (SVNException svne) {
                throw new SVNException("svn: Malformed text rep offset line in node-rev '" + revFile.getAbsolutePath() + "'");
            }
        }

        // Get the created path.
        String cpath = (String) headers.get(HEADER_CPATH);
        if (cpath == null) {
            throw new SVNException("svn: Missing cpath in node-rev in revision file '" + revFile.getAbsolutePath() + "'");
        }
        revNode.setCreatedPath(cpath);

        // Get the predecessor rev-node id (if any).
        String predId = (String) headers.get(HEADER_PRED);
        if (predId != null) {
            try {
                revNode.setPredecessorRevNodeID(parseID(predId, null));
            } catch (SVNException svne) {
                throw new SVNException("svn: Corrupt node-id in node-rev in revision file '" + revFile.getAbsolutePath() + "'");
            }
        }

        // Get the copyroot.
        String copyroot = (String) headers.get(HEADER_COPYROOT);
        if (copyroot == null) {
            revNode.setCopyRootPath(revNode.getCreatedPath());
            revNode.setCopyRootRevision(revNode.getRevNodeID().getRevision());
        } else {
            try {
                parseCopyRoot(copyroot, revNode);
            } catch (SVNException svne) {
                throw new SVNException("svn: Malformed copyroot line in node-rev in revision file '" + revFile.getAbsolutePath() + "'");
            }
        }

        // Get the copyfrom.
        String copyfrom = (String) headers.get(HEADER_COPYFROM);
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

    // isPred - if true - predecessor's id, otherwise a node id
    public static FSID parseID(String revNodeId, FSID id) throws SVNException {
        int firstDotInd = revNodeId.indexOf('.');
        int secondDotInd = revNodeId.lastIndexOf('.');
        int rInd = revNodeId.indexOf('r', secondDotInd);

        if (rInd != -1) {// we've a revision id
            int slashInd = revNodeId.indexOf('/');

            if (firstDotInd <= 0 || firstDotInd == secondDotInd || rInd <= 0 || slashInd <= 0) {
                throw new SVNException();
            }

            String nodeId = revNodeId.substring(0, firstDotInd);
            String copyId = revNodeId.substring(firstDotInd + 1, secondDotInd);

            if (nodeId == null || nodeId.length() == 0 || copyId == null || copyId.length() == 0) {
                throw new SVNException();
            }
            long rev = -1;
            long offset = -1;
            try {
                rev = Long.parseLong(revNodeId.substring(rInd + 1, slashInd));
                offset = Long.parseLong(revNodeId.substring(slashInd + 1));
            } catch (NumberFormatException nfe) {
                throw new SVNException();
            }

            if (id == null) {
                id = new FSID(nodeId, FSID.ID_INAPPLICABLE, copyId, rev, offset);
                return id;
            }

            id.setNodeID(nodeId);
            id.setCopyID(copyId);
            id.setRevision(rev);
            id.setOffset(offset);
            return id;
        }

        // else it's a txn id

        return null;// just be this null before being implemented

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
            if (rev < 0) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException nfe) {
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
        if (hexDigest.length() != 2 * MD5_DIGESTSIZE || SVNFileUtil.fromHexDigest(hexDigest) == null) {
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
        RootAndChangesOffsets offsets = readRootAndChangesOffset(reposRootDir, revision);
        return offsets.getRootOffset();
    }

    public static long getChangesOffset(File reposRootDir, long revision) throws SVNException {
        RootAndChangesOffsets offsets = readRootAndChangesOffset(reposRootDir, revision);
        return offsets.getChangesOffset();
    }

    // Read in a rev-node given its offset in a rev-file.
    public static Map readRevNodeHeaders(File revFile, long offset) throws SVNException {
        if (offset < 0) {
            return null;
        }

        InputStream is = SVNFileUtil.openFileForReading(revFile);
        if (is == null) {
            SVNErrorManager.error("svn: Can't open file '" + revFile.getAbsolutePath() + "'");
        }
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));

        boolean isFirstLine = true;
        Map map = new HashMap();
        try {
            while (true) {
                String line = readNextLine(revFile, reader, offset);
                if (line == null || line.length() == 0) {
                    break;
                }
                if (isFirstLine) {
                    isFirstLine = false;
                    offset = 0;
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
            SVNFileUtil.closeFile(reader);
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
        try {
            revRAF = new RandomAccessFile(file, "r");
        } catch (FileNotFoundException fnfe) {
            SVNErrorManager.error("svn: Can't open file '" + file.getAbsolutePath() + "': " + fnfe.getMessage());
        }

        long fileLength = -1;
        try {
            fileLength = revRAF.length();
        } catch (IOException ioe) {
            SVNErrorManager.error("svn: Can't open file '" + file.getAbsolutePath() + "': " + ioe.getMessage());
        }

        if (pos == FILE_END_POS) {
            pos = fileLength - 1 + offset;
        } else {
            pos = pos + offset;
        }
        byte[] buf = new byte[bytesToRead];

        int r = -1;
        try {
            revRAF.seek(pos + 1);
            r = revRAF.read(buf);

            if (r <= 0) {
                throw new IOException("eof unexpectedly found");
            }
        } catch (IOException ioe) {
            SVNErrorManager.error("svn: Can't read length line in file '" + file.getAbsolutePath() + "': " + ioe.getMessage());
        } finally {
            SVNFileUtil.closeFile(revRAF);
        }

        ByteArrayOutputStream os = new ByteArrayOutputStream();
        os.write(buf, 0, r);

        return os.toByteArray();
    }

    public static long readBytesFromStream(long bytesToRead, InputStream is, OutputStream os) throws IOException {
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

    public static String readNextLine(File file, BufferedReader reader, long skipBytes) throws SVNException {
        skipBytes = (skipBytes < 0) ? 0 : skipBytes;
        long skipped = -1;
        try {
            skipped = reader.skip(skipBytes);
        } catch (IOException ioe) {
            SVNErrorManager.error("svn: Can't set position pointer in file '" + file.getAbsolutePath() + "'");
        }

        if (skipped < skipBytes) {
            SVNErrorManager.error("svn: Can't set position pointer in file '" + file.getAbsolutePath() + "'");
        }

        String line = null;

        try {
            line = reader.readLine();
        } catch (IOException ioe) {
            SVNErrorManager.error("svn: Can't read file ");
        }
        return line;
    }

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

    private static RootAndChangesOffsets readRootAndChangesOffset(File reposRootDir, long revision) throws SVNException {
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

        RootAndChangesOffsets offs = new RootAndChangesOffsets(rootOffset, changesOffset, revision);
        return offs;
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
            return "";// ?
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

    private static class RootAndChangesOffsets {

        long changesOffset;
        long rootOffset;
        Long revision;

        public RootAndChangesOffsets(long root, long changes, long rev) {
            changesOffset = changes;
            rootOffset = root;
            revision = new Long(rev);
        }

        public long getRootOffset() {
            return rootOffset;
        }

        public long getChangesOffset() {
            return changesOffset;
        }

        public long getRevision() {
            return revision.longValue();
        }

        public Long getRevObject() {
            return revision;
        }
    }
}
