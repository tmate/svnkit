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
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;

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
import java.util.Map;
import java.util.HashMap;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class FSReader {
    //to mean the end of a file 
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
    
    public static FSRevisionNode getChildDirNode(String child, FSRevisionNode parent, File reposRootDir) throws SVNException{
        checkPathComponent(child);

        Map entries = getDirEntries(parent, reposRootDir);
        FSRepresentationEntry entry = entries != null ? (FSRepresentationEntry)entries.get(child) : null;
        if(entry == null){
            return null;
            //throw new SVNException("svn: Attempted to open non-existent child node '" + child + "'");
        }

        
        return getRevNode(reposRootDir, entry.getId()); 
    }
    
    private static void checkPathComponent(String name) throws SVNException{
        if(name == null || name.length() == 0 || "..".equals(name) || name.indexOf('/') != -1){
            SVNErrorManager.error("svn: Attempted to open node with an illegal name '" + name + "'");
        }
    }

    public static Map getDirEntries(FSRevisionNode revNode, File reposRootDir) throws SVNException {
        if(revNode == null || revNode.getType() != SVNNodeKind.DIR){
            SVNErrorManager.error("svn: Can't get entries of non-directory");
        }
        
        return getDirContents(revNode.getTextRepresentation(), reposRootDir);
    }
    
    public static Map getProperties(FSRevisionNode revNode, File reposRootDir) throws SVNException{
        return getProplist(revNode.getPropsRepresentation(), reposRootDir);
    }
    
    private static Map getDirContents(FSRepresentation representation, File reposRootDir) throws SVNException {
        if(representation == null){
            return null;
        }
        InputStream is = null;
        try{
            is = readRepresentation(representation, REP_PLAIN, reposRootDir);
            return parsePlainRepresentation(is, false);
        }catch(IOException ioe){
            SVNErrorManager.error("svn: Can't read representation in revision file '" + FSRepositoryUtil.getRevisionFile(reposRootDir, representation.getRevision()).getAbsolutePath() + "': " + ioe.getMessage());
        }catch(SVNException svne){
            SVNErrorManager.error("svn: Revision file '" + FSRepositoryUtil.getRevisionFile(reposRootDir, representation.getRevision()).getAbsolutePath() + "' corrupt" + SVNFileUtil.getNativeEOLMarker() + svne.getMessage());
        }finally{
            SVNFileUtil.closeFile(is);
        }
        return null;
    }
    
    private static Map getProplist(FSRepresentation represnt, File reposRootDir) throws SVNException {
        if(represnt == null){
            return null;
        }
        InputStream is = null;
        try{
            is = readRepresentation(represnt, REP_PLAIN, reposRootDir);
            return parsePlainRepresentation(is, true);
        }catch(IOException ioe){
            SVNErrorManager.error("svn: Can't read representation in revision file '" + FSRepositoryUtil.getRevisionFile(reposRootDir, represnt.getRevision()).getAbsolutePath() + "': " + ioe.getMessage());
        }catch(SVNException svne){
            SVNErrorManager.error("svn: Revision file '" + FSRepositoryUtil.getRevisionFile(reposRootDir, represnt.getRevision()).getAbsolutePath() + "' corrupt" + SVNFileUtil.getNativeEOLMarker() + svne.getMessage());
        }finally{
            SVNFileUtil.closeFile(is);
        }
        return null;
    }
    
    public static InputStream readRepresentation(FSRepresentation representation, String repHeader, File reposRootDir) throws SVNException {
        File revFile = FSRepositoryUtil.getRevisionFile(reposRootDir, representation.getRevision());
        InputStream is = null;
        try{
            is = SVNFileUtil.openFileForReading(revFile);
            
            try{
                readBytesFromStream(new Long(representation.getOffset()).intValue(), is, null);
            }catch(IOException ioe){
                SVNErrorManager.error("svn: Can't set position pointer in file '" + revFile + "': " + ioe.getMessage());
            }
            String header = null;
            try{
                header = readSingleLine(is);
            }catch(FileNotFoundException fnfe){
                SVNErrorManager.error("svn: Can't open file '" + revFile.getAbsolutePath() + "': " + fnfe.getMessage());
            } catch(IOException ioe){
                SVNErrorManager.error("svn: Can't read file '" + revFile.getAbsolutePath() + "': " + ioe.getMessage());
            }
            
            if(!repHeader.equals(header)){
                SVNErrorManager.error("svn: Malformed representation header in revision file '" + revFile.getAbsolutePath() + "'");
            }
            
            MessageDigest digest = null;
            try{
                digest = MessageDigest.getInstance("MD5");
            }catch(NoSuchAlgorithmException nsae){
                SVNErrorManager.error("svn: Can't check the digest in revision file '" + revFile.getAbsolutePath() + "': " + nsae.getMessage());
            }
    
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            long readBytes = 0;
            for(long i = 0; i < representation.getSize(); i++){
                try{
                    readBytes += readBytesFromStream(1, is, os);
                }catch(IOException ioe){
                    SVNErrorManager.error("svn: Can't read representation in revision file '" + revFile.getAbsolutePath() + "': " + ioe.getMessage());            
                }
            }
            byte[] bytes = os.toByteArray();
            digest.update(bytes);
            
            // Compare read and expected checksums 
            if(!MessageDigest.isEqual(SVNFileUtil.fromHexDigest(representation.getHexDigest()), digest.digest())){
                SVNErrorManager.error("svn: Checksum mismatch while reading representation:" + SVNFileUtil.getNativeEOLMarker() + 
                        "   expected:  " + representation.getHexDigest() + SVNFileUtil.getNativeEOLMarker() + 
                        "     actual:  " + SVNFileUtil.toHexDigest(digest));
            }
            
            return new ByteArrayInputStream(bytes);
        }finally{
            SVNFileUtil.closeFile(is);
        }
    }
    
    /* 
     * PLAIN hash format is common for dir contents as well as 
     * for props representation - so, isProps is needed to differentiate
     * between text and props repreentation
     */
    private static Map parsePlainRepresentation(InputStream is, boolean isProps) throws IOException, SVNException{
        Map representationMap = new HashMap();
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        while(readEntry('K', is, os)){
            String key = new String(os.toByteArray(), "UTF-8");
            os.reset();
            if(!readEntry('V', is, os)){
                throw new IOException("malformed file format");
            }
            String value = new String(os.toByteArray(), "UTF-8");
            os.reset();
            if(!isProps){
                FSRepresentationEntry nextRepEntry = null;
                try{
                    nextRepEntry = parseRepEntryValue(key, value);
                }catch(SVNException svne){
                    SVNErrorManager.error("svn: Directory entry '" + key + "' corrupt");
                }
                representationMap.put(key, nextRepEntry);
            }else{
                representationMap.put(key, value);
            }
        }
        
        return representationMap;
    }
    
    private static FSRepresentationEntry parseRepEntryValue(String name, String value) throws SVNException{
        String[] values = value.split(" ");
        if(values == null || values.length < 2){
            throw new SVNException();
        }
        
        SVNNodeKind type = SVNNodeKind.parseKind(values[0]);
        if(type != SVNNodeKind.DIR && type != SVNNodeKind.FILE){
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
            if(r < length){
                throw new IOException("malformed file format");
            }
            os.write(value, 0, r);
        } else {
            while(length > 0) {
                length -= is.skip(length);
            }
        }
        if(is.read() != '\n'){
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
        if (buffer[0] == 'E' && buffer[1] == 'N' && buffer[2] == 'D'
                && buffer[3] == '\n') {
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
    
    public static FSRevisionNode getRootRevNode(File reposRootDir, long revision) throws SVNException{
        FSID id = new FSID(FSID.ID_INAPPLICABLE, FSID.ID_INAPPLICABLE, FSID.ID_INAPPLICABLE, revision, getRootOffset(reposRootDir, revision));
        return getRevNode(reposRootDir, id);
    }
    
    
    public static FSRevisionNode getRevNode(File reposRootDir, FSID id) throws SVNException{
        File revFile = FSRepositoryUtil.getRevisionFile(reposRootDir, id.getRevision());//getRevFile(id.getRevision());
        
        FSRevisionNode revNode = new FSRevisionNode();
        long offset = id.getOffset();
        
        Map headers = readRevNodeHeaders(revFile, offset);
        
        // Read the rev-node id.
        String revNodeId = (String)headers.get(HEADER_ID);
        if(revNodeId == null){
            SVNErrorManager.error("svn: Missing node-id in node-rev in revision file '" + revFile.getAbsolutePath() + "'");
        }
        
        try{
            revNode.setRevNodeID(parseID(revNodeId, null));
        }catch(SVNException svne){
            SVNErrorManager.error("svn: Corrupt node-id in node-rev in revision file '" + revFile.getAbsolutePath() + "'");
        }

        // Read the type. 
        SVNNodeKind nodeKind = SVNNodeKind.parseKind((String)headers.get(HEADER_TYPE));
        if(nodeKind == SVNNodeKind.NONE || nodeKind == SVNNodeKind.UNKNOWN){
            SVNErrorManager.error("svn: Missing kind field in node-rev in revision file '" + revFile.getAbsolutePath() + "'");
        }
        revNode.setType(nodeKind);
        
        // Read the 'count' field.
        String countString = (String)headers.get(HEADER_COUNT);
        if(countString == null){
            revNode.setCount(0);
        }else{
            long cnt = -1;
            try{
                cnt = Long.parseLong(countString);
                if(cnt < 0){
                    throw new NumberFormatException();
                }
            }catch(NumberFormatException nfe){
                SVNErrorManager.error("svn: Corrupt count in node-rev in revision file '" + revFile.getAbsolutePath() + "'");
            }
            revNode.setCount(cnt);
        }

        // Get the properties location (if any).
        String propsRepr = (String)headers.get(HEADER_PROPS);
        if(propsRepr != null){
            try{
            parseRepresentationHeader(propsRepr, revNode, false);
            }catch(SVNException svne){
                throw new SVNException("svn: Malformed props rep offset line in node-rev '" + revFile.getAbsolutePath() + "'");
            }
        }
        
        // Get the data location (if any).
        String textRepr = (String)headers.get(HEADER_TEXT);
        if(textRepr != null){
            try{
                parseRepresentationHeader(textRepr, revNode, true);
            }catch(SVNException svne){
                throw new SVNException("svn: Malformed text rep offset line in node-rev '" + revFile.getAbsolutePath() + "'");
            }
        }

        // Get the created path.
        String cpath = (String)headers.get(HEADER_CPATH);
        if(cpath == null){
            throw new SVNException("svn: Missing cpath in node-rev in revision file '" + revFile.getAbsolutePath() + "'");
        }
        revNode.setCreatedPath(cpath);
        
        // Get the predecessor rev-node id (if any).
        String predId = (String)headers.get(HEADER_PRED);
        if(predId != null){
            try{
                revNode.setPredecessorRevNodeID(parseID(predId, null));
            }catch(SVNException svne){
                throw new SVNException("svn: Corrupt node-id in node-rev in revision file '" + revFile.getAbsolutePath() + "'");
            }
        }
        
        // Get the copyroot.
        String copyroot = (String)headers.get(HEADER_COPYROOT);
        if(copyroot == null){
            revNode.setCopyRootPath(revNode.getCreatedPath());
            revNode.setCopyRootRevision(revNode.getRevNodeID().getRevision());
        }else{
            try{
            parseCopyRoot(copyroot, revNode);
            }catch(SVNException svne){
                throw new SVNException("svn: Malformed copyroot line in node-rev in revision file '" + revFile.getAbsolutePath() + "'");
            }
        }
        
        // Get the copyfrom.
        String copyfrom = (String)headers.get(HEADER_COPYFROM);
        if(copyfrom == null){
            revNode.setCopyFromPath(null);
            revNode.setCopyFromRevision(-1);//maybe this should be replaced with some constants
        }else{
            try{
                parseCopyFrom(copyfrom, revNode);
            }catch(SVNException svne){
                throw new SVNException("svn: Malformed copyfrom line in node-rev in revision file '" + revFile.getAbsolutePath() + "'");
            }
        }
        
        return revNode;
    }
    
    //should it fail if revId is invalid? 
    public static void parseCopyFrom(String copyfrom, FSRevisionNode revNode) throws SVNException {
        if(copyfrom == null || copyfrom.length() == 0){
            throw new SVNException();
        }
        
        String[] cpyfrom = copyfrom.split(" ");
        if(cpyfrom.length < 2){
            throw new SVNException();
        }
        long rev = -1;
        try{
            rev = Long.parseLong(cpyfrom[0]);
        }catch(NumberFormatException nfe){
            throw new SVNException();
        }
        revNode.setCopyFromRevision(rev);
        revNode.setCopyFromPath(cpyfrom[1]);
    }

    //should it fail if revId is invalid? 
    public static void parseCopyRoot(String copyroot, FSRevisionNode revNode) throws SVNException {
        if(copyroot == null || copyroot.length() == 0){
            throw new SVNException();
        }
        
        String[] cpyroot = copyroot.split(" ");
        if(cpyroot.length < 2){
            throw new SVNException();
        }
        long rev = -1;
        try{
            rev = Long.parseLong(cpyroot[0]);
        }catch(NumberFormatException nfe){
            throw new SVNException();
        }
        revNode.setCopyRootRevision(rev);
        revNode.setCopyRootPath(cpyroot[1]);
    }
    
    //isPred - if true - predecessor's id, otherwise a node id
    public static FSID parseID(String revNodeId, FSID id) throws SVNException{
        int firstDotInd = revNodeId.indexOf('.');
        int secondDotInd = revNodeId.lastIndexOf('.');
        int rInd = revNodeId.indexOf('r', secondDotInd);

        if(rInd != -1){//we've a revision id
            int slashInd = revNodeId.indexOf('/');
    
            if(firstDotInd <= 0 || firstDotInd == secondDotInd || rInd <= 0 || slashInd <= 0){
                throw new SVNException();
            }
            
    
            String nodeId = revNodeId.substring(0, firstDotInd);
            String copyId = revNodeId.substring(firstDotInd + 1, secondDotInd);
    
            if(nodeId == null || nodeId.length() == 0 || copyId == null || copyId.length() == 0 ){
                throw new SVNException();
            }
            long rev = -1;
            long offset = -1;
            try{
                rev = Long.parseLong(revNodeId.substring(rInd + 1, slashInd));
                offset = Long.parseLong(revNodeId.substring(slashInd + 1));
            }catch(NumberFormatException nfe){
                throw new SVNException();
            }
            
            
            if(id == null){
                id = new FSID(nodeId, FSID.ID_INAPPLICABLE, copyId, rev, offset);
                return id;
            }
    
            id.setNodeID(nodeId);
            id.setCopyID(copyId);
            id.setRevision(rev);
            id.setOffset(offset);
            return id;
        }
        
        //else it's a txn id
            
        return null;//just be this null before being implemented 
    
    }

    //isData - if true - text, otherwise - props
    public static void parseRepresentationHeader(String representation, FSRevisionNode revNode, boolean isData) throws SVNException{
        if(revNode == null){
            return;
        }
        String[] offsets = representation.split(" ");
        if(offsets == null || offsets.length == 0 || offsets.length < 5){
            throw new SVNException();
        }
        
        long rev = -1;
        try{
            rev = Long.parseLong(offsets[0]);
            if(rev < 0){
                throw new NumberFormatException();
            }
        }catch(NumberFormatException nfe){
            throw new SVNException();
        }
        
        long offset = -1;
        try{
            offset = Long.parseLong(offsets[1]);
            if(offset < 0){
                throw new NumberFormatException();
            }
        }catch(NumberFormatException nfe){
            throw new SVNException();
        }

        long size = -1;
        try{
            size = Long.parseLong(offsets[2]);
            if(size < 0){
                throw new NumberFormatException();
            }
        }catch(NumberFormatException nfe){
            throw new SVNException();
        }
        
        long expandedSize = -1;
        try{
            expandedSize = Long.parseLong(offsets[3]);
            if(expandedSize < 0){
                throw new NumberFormatException();
            }
        }catch(NumberFormatException nfe){
            throw new SVNException();
        }
        
        String hexDigest = offsets[4];
        if(hexDigest.length() != 2*MD5_DIGESTSIZE ||  SVNFileUtil.fromHexDigest(hexDigest)==null){
            throw new SVNException();
        }
        FSRepresentation represnt = new FSRepresentation(rev, offset, size, expandedSize, hexDigest);
        
        if(isData){
            revNode.setTextRepresentation(represnt);
        }else{
            revNode.setPropsRepresentation(represnt);
        }
    }

    public static long getRootOffset(File reposRootDir, long revision) throws SVNException{
        RootAndChangesOffsets offsets = readRootAndChangesOffset(reposRootDir, revision);
        return offsets.getRootOffset();
    }
    
    public static long getChangesOffset(File reposRootDir, long revision) throws SVNException{
        RootAndChangesOffsets offsets = readRootAndChangesOffset(reposRootDir, revision);
        return offsets.getChangesOffset();
    }

    //Read in a rev-node given its offset in a rev-file.
    public static Map readRevNodeHeaders(File revFile, long offset) throws SVNException{
        if(offset < 0){
            return null;
        }
        
        InputStream is = SVNFileUtil.openFileForReading(revFile);
        if (is == null) {
            SVNErrorManager.error("svn: Can't open file '" + revFile.getAbsolutePath() + "'");
        }
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        
        boolean isFirstLine = true;
        Map map = new HashMap();
        try{
            while(true){
                String line = readNextLine(revFile, reader, offset);
                if(line == null || line.length() == 0){
                    break;
                }
                if(isFirstLine){
                    isFirstLine = false;
                    offset = 0;
                }
                int colonIndex = line.indexOf(':');
                if(colonIndex < 0){
                    throw new SVNException("svn: Found malformed header in revision file '" + revFile.getAbsolutePath() + "'");
                }
                
                String localName = line.substring(0, colonIndex);
                String localValue = line.substring(colonIndex + 1);
                map.put(localName, localValue.trim());
            }
        }finally{
            SVNFileUtil.closeFile(reader);
        }
        return map;
    }
    
    public static byte[] readBytesFromFile(long pos, long offset, int bytesToRead, File file) throws SVNException{
        if(bytesToRead < 0 || file == null){ 
            return null;
        }
        
        if(!file.canRead() || !file.isFile()){
            SVNErrorManager.error("svn: Cannot read from '" + file + "': path refers to a directory or read access denied");
        }
        
        RandomAccessFile revRAF = null;
        try{
            revRAF = new RandomAccessFile(file, "r");
        }catch(FileNotFoundException fnfe){
            SVNErrorManager.error("svn: Can't open file '" + file.getAbsolutePath() + "': " + fnfe.getMessage());
        }
        
        long fileLength = -1;
        try{
            fileLength = revRAF.length();
        }catch(IOException ioe){
            SVNErrorManager.error("svn: Can't open file '" + file.getAbsolutePath() + "': " + ioe.getMessage());
        }
        
        if(pos == FILE_END_POS){
            pos = fileLength - 1 + offset;
        }else{
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
    
    public static int readBytesFromStream(int bytesToRead, InputStream is, OutputStream os) throws IOException{
        if(is == null){
            return -1;
        }

        if(os != null){
            byte[] buffer = new byte[bytesToRead];
            int r = is.read(buffer);
            os.write(buffer, 0, r);
            return r;
        } 
        return (int)is.skip(bytesToRead);
    }
    
    public static String readNextLine(File file, BufferedReader reader, long skipBytes) throws SVNException{
        skipBytes = (skipBytes < 0) ? 0 : skipBytes;
        long skipped = -1;
        try{
            skipped = reader.skip(skipBytes);
        }catch(IOException ioe){
            SVNErrorManager.error("svn: Can't set position pointer in file '" + file.getAbsolutePath() + "'");
        }
        
        if(skipped < skipBytes){
            SVNErrorManager.error("svn: Can't set position pointer in file '" + file.getAbsolutePath() + "'");
        }
        
        String line = null;
        
        try{
            line = reader.readLine();
        }catch(IOException ioe){
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
        try{
            reader = new BufferedReader(new InputStreamReader(is));
            line = reader.readLine();
        }catch(IOException ioe){
            SVNErrorManager.error("svn: Can't read from file '" + file.getAbsolutePath() + "': " + ioe.getMessage());
        }finally {
            SVNFileUtil.closeFile(reader);
        }
        return line;
    }

    //to read lines only from svn files ! (eol-specific)
    public static String readSingleLine(InputStream is) throws FileNotFoundException, IOException {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        while(true){
            int b = is.read();
            if(b == -1 || '\n' == (byte)b){
                break;
            }
            os.write(b);
        }
        return new String(os.toByteArray());
    }

    private static RootAndChangesOffsets readRootAndChangesOffset(File reposRootDir, long revision) throws SVNException{
        File revFile =FSRepositoryUtil.getRevisionFile(reposRootDir, revision); //getRevFile(revision);
        String eol = SVNFileUtil.getNativeEOLMarker();
        
        int size = 64;
        byte[] buffer = null;
        
        try{
            /* svn: We will assume that the last line containing the two offsets
             * will never be longer than 64 characters.
             * Read in this last block, from which we will identify the last line. 
             */
            buffer = readBytesFromFile(FILE_END_POS, -size, size, revFile);
        }catch(SVNException svne){
            SVNErrorManager.error(svne.getMessage() + eol + "svn: No such revision " + revision);
        }
        
        // The last byte should be a newline.
        if(buffer[buffer.length - 1] != '\n'){
            SVNErrorManager.error("svn: Revision file '" + revFile.getAbsolutePath() + "' lacks trailing newline");
        }
        String bytesAsString = new String(buffer);
        if(bytesAsString.indexOf('\n') == bytesAsString.lastIndexOf('\n')){
            SVNErrorManager.error("svn: Final line in revision file '" + revFile.getAbsolutePath() + "' is longer than 64 characters");
        }
        String[] lines = bytesAsString.split("\n");
        String lastLine = lines[lines.length - 1];
        String[] offsetsValues = lastLine.split(" ");
        if(offsetsValues.length < 2){
            SVNErrorManager.error("svn: Final line in revision file '" + revFile.getAbsolutePath() + "' missing space");
        }
        
        long rootOffset = -1;
        try{
            rootOffset = Long.parseLong(offsetsValues[0]);
        }catch(NumberFormatException nfe){
            SVNErrorManager.error("svn: Unparsable root offset number in revision file '" + revFile.getAbsolutePath() + "'");
        }
        
        long changesOffset = -1;
        try{
            changesOffset = Long.parseLong(offsetsValues[1]);
        }catch(NumberFormatException nfe){
            SVNErrorManager.error("svn: Unparsable changes offset number in revision file '" + revFile.getAbsolutePath() + "'");
        }
        
        RootAndChangesOffsets offs = new RootAndChangesOffsets(rootOffset, changesOffset, revision);
        return offs;
    }

    private static class RootAndChangesOffsets{
        long changesOffset;
        long rootOffset;
        Long revision;
        
        public RootAndChangesOffsets(long root, long changes, long rev){
            changesOffset = changes;
            rootOffset = root;
            revision = new Long(rev);
        }
        
        public long getRootOffset(){
            return rootOffset;
        }

        public long getChangesOffset(){
            return changesOffset;
        }

        public long getRevision(){
            return revision.longValue();
        }

        public Long getRevObject(){
            return revision;
        }
    }
}
