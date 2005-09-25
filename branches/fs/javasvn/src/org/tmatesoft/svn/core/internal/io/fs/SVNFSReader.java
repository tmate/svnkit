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

import java.io.IOException;
import java.io.InputStream;
import java.io.BufferedInputStream;
import java.io.InputStreamReader;
import java.io.FileReader;
import java.io.FileInputStream;
import java.io.Reader;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.io.RandomAccessFile;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.OutputStream;
import java.io.File;
import java.util.Collection;
import java.util.Date;
import java.util.Map;
import java.util.HashMap;

public class SVNFSReader {
    private File myRevisionFile;
    private long myRootOffset;
    private long myChangesOffset;
    private long myRevision;
    
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
    static int MD5_DIGESTSIZE = 16;
    
    public SVNFSReader(File revFile, long myRevision){
        myRevisionFile = revFile;
        myRootOffset = -1;
        myChangesOffset = -1;
    }

    public void getDirEntries(SVNRevisionNode revNode, Map entries) throws SVNException{
        if(revNode.getType() != SVNNodeKind.DIR){
            throw new SVNException("svn: Can't get entries of non-directory");
        }
        
    }
    
    public SVNRevisionNode getRootRevNode() throws SVNException{
        return getRevNode(myRevisionFile, getRootOffset());
    }
    
    public SVNRevisionNode getRevNode(File revFile, long offset) throws SVNException{
        SVNRevisionNode revNode = new SVNRevisionNode();
        
        Map headers = readRevNodeHeaders(revFile, offset);
        
        // Read the node-rev id.
        String revNodeId = (String)headers.get(HEADER_ID);
        if(revNodeId == null){
            throw new SVNException("svn: Missing node-id in node-rev in revision file '" + revFile + "'");
        }
        parseID(revFile, revNodeId, revNode, false);

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
            parseRepresentation(revFile, propsRepr, revNode, false);
        }
        
        // Get the data location (if any).
        String textRepr = (String)headers.get(HEADER_TEXT);
        if(textRepr != null){
            parseRepresentation(revFile, textRepr, revNode, true);
        }

        // Get the created path.
        String cpath = (String)headers.get(HEADER_CPATH);
        if(cpath == null){
            throw new SVNException("svn: Missing cpath in node-rev in revision file '" + revFile.getAbsolutePath() + "'");
        }
        revNode.setCreatedPath(cpath);
        
        // Get the predecessor ID (if any).
        String predId = (String)headers.get(HEADER_PRED);
        if(predId != null){
            parseID(revFile, predId, revNode, true);
        }
        
        // Get the copyroot.
        String copyroot = (String)headers.get(HEADER_COPYROOT);
        if(copyroot == null){
            revNode.setCopyRootPath(revNode.getCreatedPath());
            revNode.setCopyRootRevision(revNode.getRevNodeID().getRevision());
        }else{
            parseCopyRoot(revFile, copyroot, revNode);
        }
        
        // Get the copyfrom.
        String copyfrom = (String)headers.get(HEADER_COPYFROM);
        if(copyfrom == null){
            revNode.setCopyFromPath(null);
            revNode.setCopyFromRevision(-1);//maybe this should be replaced with some constants
        }else{
            parseCopyFrom(revFile, copyfrom, revNode);
        }
        
        return revNode;
    }
    
    //should it fail if revId is invalid? 
    public static void parseCopyFrom(File revFile, String copyfrom, SVNRevisionNode revNode) throws SVNException {
        if(copyfrom == null || copyfrom.length() == 0){
            throw new SVNException("svn: Malformed copyfrom line in node-rev in revision file '" + revFile.getAbsolutePath() + "'");
        }
        
        String[] cpyfrom = copyfrom.split(" ");
        if(cpyfrom.length < 2){
            throw new SVNException("svn: Malformed copyfrom line in node-rev in revision file '" + revFile.getAbsolutePath() + "'");
        }
        long rev = -1;
        try{
            rev = Long.parseLong(cpyfrom[0]);
        }catch(NumberFormatException nfe){
            throw new SVNException("svn: Malformed copyfrom line in node-rev in revision file '" + revFile.getAbsolutePath() + "'");
        }
        revNode.setCopyFromRevision(rev);
        revNode.setCopyFromPath(cpyfrom[1]);
    }

    //should it fail if revId is invalid? 
    public static void parseCopyRoot(File revFile, String copyroot, SVNRevisionNode revNode) throws SVNException {
        if(copyroot == null || copyroot.length() == 0){
            throw new SVNException("svn: Malformed copyroot line in node-rev in revision file '" + revFile.getAbsolutePath() + "'");
        }
        
        String[] cpyroot = copyroot.split(" ");
        if(cpyroot.length < 2){
            throw new SVNException("svn: Malformed copyroot line in node-rev in revision file '" + revFile.getAbsolutePath() + "'");
        }
        long rev = -1;
        try{
            rev = Long.parseLong(cpyroot[0]);
        }catch(NumberFormatException nfe){
            throw new SVNException("svn: Malformed copyroot line in node-rev in revision file '" + revFile.getAbsolutePath() + "'");
        }
        revNode.setCopyRootRevision(rev);
        revNode.setCopyRootPath(cpyroot[1]);
    }
    
    //isPred - if true - predecessor's id, otherwise a node's id
    public static void parseID(File revFile, String revNodeId, SVNRevisionNode revNode, boolean isPred) throws SVNException{
        if(revNode == null){
            return;
        }
        
        int firstDotInd = revNodeId.indexOf('.');
        int secondDotInd = revNodeId.lastIndexOf('.');
        int rInd = revNodeId.indexOf('r');
        int slashInd = revNodeId.indexOf('/');

        if(firstDotInd <= 0 || firstDotInd == secondDotInd || rInd <= 0 || slashInd <= 0){
            throw new SVNException("svn: Corrupt node-id in node-rev in revision file '" + revFile + "'");
        }
        

        String nodeId = revNodeId.substring(0, firstDotInd);
        String copyId = revNodeId.substring(firstDotInd + 1, secondDotInd);

        if(nodeId == null || nodeId.length() == 0 || copyId == null || copyId.length() == 0 ){
            throw new SVNException("svn: Corrupt node-id in node-rev in revision file '" + revFile + "'");
        }
        long rev = -1;
        long offset = -1;
        try{
            rev = Long.parseLong(revNodeId.substring(rInd + 1, slashInd));
            offset = Long.parseLong(revNodeId.substring(slashInd + 1));
        }catch(NumberFormatException nfe){
            throw new SVNException("svn: Corrupt node-id in node-rev in revision file '" + revFile + "'");
        }
        SVNID id = new SVNID(nodeId, SVNID.ID_INAPPLICABLE, copyId, rev, offset);
        
        if(!isPred){
            revNode.setRevNodeID(id);
        }else{
            revNode.setPredecessorRevNodeID(id);
        }
    }

    //isData - if true - text, otherwise - props
    public static void parseRepresentation(File revFile, String representation, SVNRevisionNode revNode, boolean isData) throws SVNException{
        if(revNode == null){
            return;
        }
        String[] offsets = representation.split(" ");
        if(offsets == null || offsets.length == 0 || offsets.length < 5){
            throw new SVNException("svn: Malformed text rep offset line in node-rev '" + revFile.getAbsolutePath() + "'");
        }
        
        long rev = -1;
        try{
            rev = Long.parseLong(offsets[0]);
            if(rev < 0){
                throw new NumberFormatException();
            }
        }catch(NumberFormatException nfe){
            throw new SVNException("svn: Malformed text rep offset line in node-rev '" + revFile.getAbsolutePath() + "'");
        }
        
        long offset = -1;
        try{
            offset = Long.parseLong(offsets[1]);
            if(offset < 0){
                throw new NumberFormatException();
            }
        }catch(NumberFormatException nfe){
            throw new SVNException("svn: Malformed text rep offset line in node-rev '" + revFile.getAbsolutePath() + "'");
        }

        long size = -1;
        try{
            size = Long.parseLong(offsets[2]);
            if(size < 0){
                throw new NumberFormatException();
            }
        }catch(NumberFormatException nfe){
            throw new SVNException("svn: Malformed text rep offset line in node-rev '" + revFile.getAbsolutePath() + "'");
        }
        
        long expandedSize = -1;
        try{
            expandedSize = Long.parseLong(offsets[3]);
            if(expandedSize < 0){
                throw new NumberFormatException();
            }
        }catch(NumberFormatException nfe){
            throw new SVNException("svn: Malformed text rep offset line in node-rev '" + revFile.getAbsolutePath() + "'");
        }
        
        String hexDigest = offsets[4];
        if(hexDigest.length() != 2*MD5_DIGESTSIZE ||  SVNFileUtil.fromHexDigest(hexDigest)==null){
            throw new SVNException("svn: Malformed text rep offset line in node-rev '" + revFile.getAbsolutePath() + "'");
        }
        SVNRepresentation represnt = new SVNRepresentation(rev, offset, size, expandedSize, hexDigest);
        
        if(isData){
            revNode.setTextRepresentation(represnt);
        }else{
            revNode.setPropsRepresentation(represnt);
        }
    }

    public long getRootOffset() throws SVNException{
        if(myRootOffset == -1){
            readRootAndChangesOffset();
        }
        return myRootOffset;
    }
    
    public long getChangesOffset() throws SVNException{
        if(myChangesOffset == -1){
            readRootAndChangesOffset();
        }
        return myChangesOffset;
    
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
                    throw new SVNException("svn: Found malformed header in revision file '" + revFile + "'");
                }
                
                String localName = line.substring(0, colonIndex);
                String localValue = line.substring(colonIndex + 1);
                map.put(localName, localValue);
            }
        }finally{
            closeFile(reader);
        }
        return map;
    }
    
    public static byte[] readBytesFromFile(long pos, long offset, byte[] buffer, long bytesToRead, File file) throws SVNException{
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
        if(bytesToRead > buffer.length || bytesToRead < 0){
            bytesToRead = buffer.length;
        }
        
        try {
            while (true) {
                int l = revRAF.read(buffer, (int)(pos + 1), (int)bytesToRead);
                if (l <= 0) {
                    break;
                }
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
        return buffer;
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
    
    private void readRootAndChangesOffset() throws SVNException{
        String eolBytes = new String(SVNTranslator.getEOL(SVNProperty.EOL_STYLE_NATIVE));
        
        int size = 64;
        byte[] buffer = new byte[size];
        
        try{
            /* svn: We will assume that the last line containing the two offsets
             * will never be longer than 64 characters.
             * Read in this last block, from which we will identify the last line. 
             */
            readBytesFromFile(FILE_END_POS, -size, buffer, size, myRevisionFile);
        }catch(SVNException svne){
            throw new SVNException(svne.getMessage() + eolBytes + "svn: No such revision " + myRevision);
        }
        
        // The last byte should be a newline.
        if(buffer[size-1]!='\n'){
            throw new SVNException("svn: Revision file '" + myRevisionFile + "' lacks trailing newline");
        }
        String bytesAsString = new String(buffer);
        if(bytesAsString.indexOf('\n')==bytesAsString.lastIndexOf('\n')){
            throw new SVNException("svn: Final line in revision file '" + myRevisionFile + "' is longer than 64 characters");
        }
        String[] lines = bytesAsString.split("\n");
        String lastLine = lines[lines.length - 1];
        String[] offsetsValues = lastLine.split(" ");
        if(offsetsValues.length < 2){
            throw new SVNException("svn: Final line in revision file '" + myRevisionFile + "' missing space");
        }
        
        try{
            myRootOffset = Long.parseLong(offsetsValues[0]);
        }catch(NumberFormatException nfe){
            throw new SVNException("svn: Unparsable root offset number in revision file '" + myRevisionFile + "'");
        }
        try{
            myChangesOffset = Long.parseLong(offsetsValues[1]);
        }catch(NumberFormatException nfe){
            throw new SVNException("svn: Unparsable changes offset number in revision file '" + myRevisionFile + "'");
        }
    }
}
