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
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.internal.wc.SVNTranslator;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.FileReader;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.RandomAccessFile;
import java.io.ByteArrayInputStream;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.File;
import java.util.Map;
import java.util.HashMap;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class SVNFSReader {
    private static Map myReaders = new HashMap();
    
    private File myReposRootDir;
    private Map myOffsets = new HashMap();

    static BufferedReader myCurReader;
    //to mean the end of a file 
    static long FILE_END_POS = -1;
    
    static String HEADER_ID = "id";
    static String HEADER_TYPE = "type";
    static String HEADER_COUNT = "count";
    static String HEADER_PROPS = "props";
    static String HEADER_TEXT = "text";
    static String HEADER_CPATH = "cpath";
    static String HEADER_PRED = "pred";
    static String HEADER_COPYFROM = "copyfrom";
    static String HEADER_COPYROOT = "copyroot";
    static String REP_DELTA = "DELTA";
    static String REP_PLAIN = "PLAIN";
    
    static int MD5_DIGESTSIZE = 16;
    
    private SVNFSReader(String reposRootPath){
        myReposRootDir = new File(reposRootPath);
    }

    private File getRevFile(long revision) throws SVNException{
        File revsDir = new File(new File(myReposRootDir, FSRepository.SVN_REPOS_DB_DIR), FSRepository.SVN_REPOS_REVS_DIR);
        File revFile = new File(revsDir, String.valueOf(revision));
        if(!revFile.exists()){
            throw new SVNException("svn: No such revision " + revision);
        }

        return revFile;
    }

    public static SVNFSReader getInstance(String reposRootPath){
        SVNFSReader reader = (SVNFSReader)myReaders.get(reposRootPath);
        if(reader != null){
            return reader;
        }
        
        reader = new SVNFSReader(reposRootPath);
        myReaders.put(reposRootPath, reader);
        return reader;
    }
    
    public SVNRevisionNode getChildDirNode(String child, SVNRevisionNode parent) throws SVNException{
        Map entries = getDirEntries(parent);
        SVNRepEntry entry = (SVNRepEntry)entries.get(child);
        if(entry == null){
            return null;
            //throw new SVNException("svn: Attempted to open non-existent child node '" + child + "'");
        }

        checkPathComponent(child);
        
        return getRevNode(entry.getId()); 
    }
    private void checkPathComponent(String name) throws SVNException{
        if(name == null || name.length() == 0 || "..".equals(name) || name.indexOf('/') != -1){
            throw new SVNException("svn: Attempted to open node with an illegal name '" + name + "'");
        }
    }

//    public Map getDirEntries(SVNID id) throws SVNException {
//        return getDirEntries(getRevNode(id));
//    }
    
    public Map getDirEntries(SVNRevisionNode revNode) throws SVNException {
        if(revNode.getType() != SVNNodeKind.DIR){
            throw new SVNException("svn: Can't get entries of non-directory");
        }
        
        return getDirContents(revNode.getTextRepresentation());
    }
    
    private Map getDirContents(SVNRepresentation represnt) throws SVNException {
        File revFile = getRevFile(represnt.getRevision());
        InputStream is = null;
        try{
            is = SVNFileUtil.openFileForReading(revFile);
            
            try{
                readBytesFromStream(new Long(represnt.getOffset()).intValue(), is, null);
            }catch(IOException ioe){
                throw new SVNException("svn: Can't set position pointer in file '" + revFile + "'");
            }
            String header = null;
            try{
                header = readSingleLine(is);
            }catch(FileNotFoundException fnfe){
                throw new SVNException("svn: Can't open file '" + revFile.getAbsolutePath() + "'", fnfe);
            } catch(IOException ioe){
                throw new SVNException("svn: Can't read file '" + revFile.getAbsolutePath() + "'", ioe);
            }
            
            if(!REP_PLAIN.equals(header)){
                throw new SVNException("svn: Malformed representation header in revision file '" + revFile.getAbsolutePath() + "'");
            }
            
            MessageDigest digest = null;
            try{
                digest = MessageDigest.getInstance("MD5");
            }catch(NoSuchAlgorithmException nsae){
                throw new SVNException("svn: Can't check the digest in revision file '" + revFile.getAbsolutePath() + "': "+nsae.getMessage());
            }
    
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            long readBytes = 0;
            for(long i = 0; i < represnt.getSize(); i++){
                try{
                    readBytes += readBytesFromStream(1, is, os);
                }catch(IOException ioe){
                    throw new SVNException("svn: Can't read representation in revision file '" + revFile.getAbsolutePath() + "': " + ioe.getMessage());            
                }
            }
            byte[] bytes = os.toByteArray();
            digest.update(bytes);
            
            // Compare read and expected checksums 
            if(!MessageDigest.isEqual(SVNFileUtil.fromHexDigest(represnt.getHexDigest()), digest.digest())){
                throw new SVNException("svn: Checksum mismatch while reading representation:" + SVNFileUtil.getNativeEOLMarker() + 
                        "   expected:  " + represnt.getHexDigest() + SVNFileUtil.getNativeEOLMarker() + 
                        "     actual:  " + SVNFileUtil.toHexDigest(digest));
            }
            
            ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
            try{
                return readPlainRepresentation(bais);
            }catch(IOException ioe){
                throw new SVNException("svn: Can't read representation in revision file '" + revFile.getAbsolutePath() + "': " + ioe.getMessage());
            }catch(SVNException svne){
                throw new SVNException("svn: Revision file '" + revFile.getAbsolutePath() + "' corrupt" + SVNFileUtil.getNativeEOLMarker() + svne.getMessage());
            }
        }finally{
            if(is != null){
                try{
                    is.close();
                }catch(IOException ioe){
                    //
                }
            }
        }
    }
    
    private Map readPlainRepresentation(InputStream is) throws IOException, SVNException{
        Map entriesMap = new HashMap();
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        while(readEntry('K', is, os)){
            String entryName = new String(os.toByteArray(), "UTF-8");
            os.reset();
            if(!readEntry('V', is, os)){
                throw new IOException("malformed file format");
            }
            String entryValue = new String(os.toByteArray(), "UTF-8");
            os.reset();
            SVNRepEntry nextRepEntry = null;
            try{
                nextRepEntry = parseRepEntryValue(entryValue);
            }catch(SVNException svne){
                throw new SVNException("svn: Directory entry '" + entryName + "' corrupt");
            }
            entriesMap.put(entryName, nextRepEntry);
        }
        
        return entriesMap;
    }
    
    private SVNRepEntry parseRepEntryValue(String value) throws SVNException{
        String[] values = value.split(" ");
        if(values == null || values.length < 2){
            throw new SVNException();
        }
        
        SVNNodeKind type = SVNNodeKind.parseKind(values[0]);
        if(type != SVNNodeKind.DIR && type != SVNNodeKind.FILE){
            throw new SVNException();
        }
        
        SVNID id = parseID(values[1], null);
        return new SVNRepEntry(id, type);
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
    
    public SVNRevisionNode getRootRevNode(long revision) throws SVNException{
        SVNID id = new SVNID(SVNID.ID_INAPPLICABLE, SVNID.ID_INAPPLICABLE, SVNID.ID_INAPPLICABLE, revision, getRootOffset(revision));
        return getRevNode(id);
    }
    
    
    public SVNRevisionNode getRevNode(SVNID id) throws SVNException{
        File revFile = getRevFile(id.getRevision());
        
        SVNRevisionNode revNode = new SVNRevisionNode();
        long offset = id.getOffset();
        
        Map headers = readRevNodeHeaders(revFile, offset);
        
        // Read the rev-node id.
        String revNodeId = (String)headers.get(HEADER_ID);
        if(revNodeId == null){
            throw new SVNException("svn: Missing node-id in node-rev in revision file '" + revFile.getAbsolutePath() + "'");
        }
        
        try{
            revNode.setRevNodeID(parseID(revNodeId, null));
        }catch(SVNException svne){
            throw new SVNException("svn: Corrupt node-id in node-rev in revision file '" + revFile.getAbsolutePath() + "'");
        }

        // Read the type. 
        SVNNodeKind nodeKind = SVNNodeKind.parseKind((String)headers.get(HEADER_TYPE));
        if(nodeKind == SVNNodeKind.NONE || nodeKind == SVNNodeKind.UNKNOWN){
            throw new SVNException("svn: Missing kind field in node-rev in revision file '" + revFile.getAbsolutePath() + "'");
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
                throw new SVNException("svn: Corrupt count in node-rev in revision file '" + revFile.getAbsolutePath() + "'");
            }
            revNode.setCount(cnt);
        }

        // Get the properties location (if any).
        String propsRepr = (String)headers.get(HEADER_PROPS);
        if(propsRepr != null){
            try{
            parseRepresentation(propsRepr, revNode, false);
            }catch(SVNException svne){
                throw new SVNException("svn: Malformed props rep offset line in node-rev '" + revFile.getAbsolutePath() + "'");
            }
        }
        
        // Get the data location (if any).
        String textRepr = (String)headers.get(HEADER_TEXT);
        if(textRepr != null){
            try{
                parseRepresentation(textRepr, revNode, true);
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
    public static void parseCopyFrom(String copyfrom, SVNRevisionNode revNode) throws SVNException {
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
    public static void parseCopyRoot(String copyroot, SVNRevisionNode revNode) throws SVNException {
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
    public static SVNID parseID(String revNodeId, SVNID id) throws SVNException{
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
                id = new SVNID(nodeId, SVNID.ID_INAPPLICABLE, copyId, rev, offset);
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
    public static void parseRepresentation(String representation, SVNRevisionNode revNode, boolean isData) throws SVNException{
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
        SVNRepresentation represnt = new SVNRepresentation(rev, offset, size, expandedSize, hexDigest);
        
        if(isData){
            revNode.setTextRepresentation(represnt);
        }else{
            revNode.setPropsRepresentation(represnt);
        }
    }

    public long getRootOffset(long revision) throws SVNException{
        Long rev = new Long(revision);
        RootAndChangesOffsets offsets = (RootAndChangesOffsets)myOffsets.get(rev);

        if(offsets == null){
            offsets = readRootAndChangesOffset(revision);
        }
        
        return offsets.getRootOffset();
    }
    
    public long getChangesOffset(long revision) throws SVNException{
        Long rev = new Long(revision);
        RootAndChangesOffsets offsets = (RootAndChangesOffsets)myOffsets.get(rev);
        
        if(offsets == null){
            offsets = readRootAndChangesOffset(revision);
        }
        
        return offsets.getChangesOffset();
    }
    
    /**
     * Build a rev-node given its offset in a rev-file.
     * 
     * @param revNode
     * @param offset
     */
    public static Map readRevNodeHeaders(File revFile, long offset) throws SVNException{
        if(offset < 0){
            return null;
        }
        
        BufferedReader reader = openFileForReading(revFile);
        
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
            closeFile(reader);
        }
        return map;
    }
    
    public static byte[] readBytesFromFile(long pos, long offset, int bytesToRead, File file) throws SVNException{
        if(bytesToRead < 0 || file == null){ 
            return null;
        }
        
        if(!file.canRead() || !file.isFile()){
            throw new SVNException("svn: Cannot read from '" + file + "': path refers to a directory or read access denied");
        }
        
        RandomAccessFile revRAF = null;
        try{
            revRAF = new RandomAccessFile(file, "r");
        }catch(FileNotFoundException fnfe){
            throw new SVNException("svn: Can't open file '" + file.getAbsolutePath() + "': " + fnfe.getMessage());
        }
        
        long fileLength = -1;
        try{
            fileLength = revRAF.length();
        }catch(IOException ioe){
            throw new SVNException("svn: Can't open file '" + file.getAbsolutePath() + "': " + ioe.getMessage());
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
            throw new SVNException("svn: Can't read length line in file '" + file.getAbsolutePath() + "'", ioe);
        } finally {
            if(revRAF!=null){
                try {
                    revRAF.close();
                } catch (IOException e) {
                    //
                }
            }
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
    
    static String readNextLine(File file, BufferedReader reader, long skipBytes) throws SVNException{
        skipBytes = (skipBytes < 0) ? 0 : skipBytes;
        long skipped = -1;
        try{
            skipped = reader.skip(skipBytes);
        }catch(IOException ioe){
            throw new SVNException("svn: Can't set position pointer in file '" + file.getAbsolutePath() + "'");
        }
        
        if(skipped < skipBytes){
            throw new SVNException("svn: Can't set position pointer in file '" + file.getAbsolutePath() + "'");
        }
        
        String line = null;
        
        try{
            line = reader.readLine();
        }catch(IOException ioe){
            throw new SVNException("svn: Can't read file ");
        }
        return line;
    }
    
    public static String readSingleLine(File file) throws SVNException {
        if (!file.isFile() || !file.canRead()) {
            throw new SVNException("svn: Can't open file '" + file.getAbsolutePath() + "'");
        }

        BufferedReader reader = null;
        String line = null;
        try {
            reader = new BufferedReader(new FileReader(file));
            line = reader.readLine();
        }catch(FileNotFoundException fnfe){
            throw new SVNException("svn: Can't open file '" + file.getAbsolutePath() + "'", fnfe);
        } catch(IOException ioe){
            throw new SVNException("svn: Can't read file '" + file.getAbsolutePath() + "'", ioe);
        }
        finally {
            closeFile(reader);
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

    static void closeFile(Reader is) {
        if (is != null) {
            try {
                is.close();
            } catch (IOException e) {
                //
            }
        }
    }
    
    static BufferedReader openFileForReading(File file) throws SVNException{
        if (file == null || !file.isFile() || !file.canRead()) {
            throw new SVNException("svn: Can't open file '" + file.getAbsolutePath() + "'");
        }
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader(file));
        }catch(FileNotFoundException fnfe){
            if(reader!=null){
                try{
                    reader.close();
                }catch(IOException ioe){
                    //
                }
            }
            throw new SVNException("svn: Can't open file '" + file.getAbsolutePath() + "'");
        }
        return reader;
    }
    
    private RootAndChangesOffsets readRootAndChangesOffset(long revision) throws SVNException{
        File revFile = getRevFile(revision);
        String eolBytes = new String(SVNTranslator.getEOL(SVNProperty.EOL_STYLE_NATIVE));
        
        int size = 64;
        byte[] buffer = null;
        
        try{
            /* svn: We will assume that the last line containing the two offsets
             * will never be longer than 64 characters.
             * Read in this last block, from which we will identify the last line. 
             */
            buffer = readBytesFromFile(FILE_END_POS, -size, size, revFile);
        }catch(SVNException svne){
            throw new SVNException(svne.getMessage() + eolBytes + "svn: No such revision " + revision);
        }
        
        // The last byte should be a newline.
        if(buffer[buffer.length - 1] != '\n'){
            throw new SVNException("svn: Revision file '" + revFile.getAbsolutePath() + "' lacks trailing newline");
        }
        String bytesAsString = new String(buffer);
        if(bytesAsString.indexOf('\n')==bytesAsString.lastIndexOf('\n')){
            throw new SVNException("svn: Final line in revision file '" + revFile.getAbsolutePath() + "' is longer than 64 characters");
        }
        String[] lines = bytesAsString.split("\n");
        String lastLine = lines[lines.length - 1];
        String[] offsetsValues = lastLine.split(" ");
        if(offsetsValues.length < 2){
            throw new SVNException("svn: Final line in revision file '" + revFile.getAbsolutePath() + "' missing space");
        }
        
        long rootOffset = -1;
        try{
            rootOffset = Long.parseLong(offsetsValues[0]);
        }catch(NumberFormatException nfe){
            throw new SVNException("svn: Unparsable root offset number in revision file '" + revFile.getAbsolutePath() + "'");
        }
        
        long changesOffset = -1;
        try{
            changesOffset = Long.parseLong(offsetsValues[1]);
        }catch(NumberFormatException nfe){
            throw new SVNException("svn: Unparsable changes offset number in revision file '" + revFile.getAbsolutePath() + "'");
        }
        
        RootAndChangesOffsets offs = new RootAndChangesOffsets(rootOffset, changesOffset, revision);
        myOffsets.put(offs.getRevObject(), offs);
        return offs;
    }

    private class RootAndChangesOffsets{
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
    
/*    private class RepresentationReadState{
        private SVNRepresentation represnt;
        // The starting offset for the raw svndiff/plaintext data minus header.
        private long myStart;
        // The end offset of the raw data.
        private long myLength;
        // The current offset into the file.
        private long off;
        // The stored checksum of the representation we are reading
        private MessageDigest myChecksum;
        
        public RepresentationReadState(SVNRepresentation repr, long start, long len, long offset) throws NoSuchAlgorithmException{
            represnt = repr;
            myStart = start;
            myLength = len;
            off = offset;
            myChecksum = MessageDigest.getInstance("MD5");
        }
        
        public void setOffset(long offset){
            off = offset;
        }
        
        public void setStart(long start){
            myStart = start;
        }

        public void setLength(long len){
            myLength = len;
        }

        public void setRepresentation(SVNRepresentation repr){
            represnt = repr;
        }
        
        public long getOffset(){
            return off;
        }

        public long getStart(){
            return myStart;
        }
        
        public long getLength(){
            return myLength;
        }
        
        public SVNRepresentation getRepresentation(){
            return represnt;
        }
        
        public void updateChecksum(byte[] input){
            myChecksum.update(input);
        }
        
        public byte[] getFinalChecksum(){
            return myChecksum.digest();
        }
    }
*/
    
}
