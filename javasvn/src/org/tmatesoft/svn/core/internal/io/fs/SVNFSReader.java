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
import org.tmatesoft.svn.core.internal.wc.SVNTranslator;

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

    public SVNFSReader(File revFile, long myRevision){
        myRevisionFile = revFile;
        myRootOffset = -1;
        myChangesOffset = -1;
    }
    
    public SVNRevisionNode getRootRevNode() throws SVNException{
        return getRevNode(myRevisionFile, getRootOffset());
    }
    
    public SVNRevisionNode getRevNode(File revFile, long offset) throws SVNException{
        SVNRevisionNode revNode = new SVNRevisionNode();
        revNode.setOffset(offset);
        
        Map headers = readRevNodeHeaders(revFile, revNode.getOffset());
        String revNodeId = (String)headers.get(HEADER_ID);
        if(revNodeId == null){
            throw new SVNException("svn: Missing node-id in node-rev in revision file '" + revFile + "'");
        }
        
        parseRevNodeID(revFile, revNodeId, revNode);
        
        
        return null;
    }
    
    public static void parseRevNodeID(File revFile, String revNodeId, SVNRevisionNode revNode) throws SVNException{
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
        long revID = -1;
        long offset = -1;
        try{
            revID = Long.parseLong(revNodeId.substring(rInd + 1, slashInd));
            offset = Long.parseLong(revNodeId.substring(slashInd + 1));
        }catch(NumberFormatException nfe){
            throw new SVNException("svn: Corrupt node-id in node-rev in revision file '" + revFile + "'");
        }
        
        revNode.setNodeID(nodeId);
        revNode.setCopyID(copyId);
        revNode.setRevisionID(revID);
        revNode.setOffset(offset);
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
                if(line==null || line.length()==0){
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
