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

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedList;
import java.util.Iterator;
import java.util.ListIterator;
import java.io.File;
import java.io.RandomAccessFile;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.io.diff.SVNDiffInstruction;
import org.tmatesoft.svn.core.io.diff.SVNDiffWindowApplyBaton;
import org.tmatesoft.svn.core.io.diff.SVNDiffWindow;
import org.tmatesoft.svn.core.io.diff.SVNDiffWindowBuilder;
import org.tmatesoft.svn.core.io.diff.ISVNInputStream;

/**
 * @version 1.0
 * @author  TMate Software Ltd.
 */
public class FSInputStream implements ISVNInputStream {
    /* The state of all prior delta representations. */
    private LinkedList myRepStateList = new LinkedList();

    /* The index of the current delta chunk, if we are reading a delta. */
    private int myChunkIndex;
    
    private boolean isChecksumFinalized;
    
    /* The stored checksum of the representation we are reading, its
     * length, and the amount we've read so far.  Some of this
     * information is redundant with myReposStateList, but it's
     * convenient for the checksumming code to have it here. 
     */
    private String myHexChecksum;
    private long myLength;
    private long myOffset;
    
    private SVNDiffWindowBuilder myDiffWindowBuilder = SVNDiffWindowBuilder.newInstance();
    
    private MessageDigest myDigest;
    
    private byte[] myBuffer;
    
    private int myBufPos = 0;
    
    private FSInputStream(FSRepresentation representation, File reposRootDir) throws SVNException {
        myChunkIndex = 0;
        isChecksumFinalized = false;
        myHexChecksum = representation.getHexDigest();
        myOffset = 0;
        myLength = representation.getExpandedSize();
        try {
            myDigest = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException nsae) {
            SVNErrorManager.error(nsae.getMessage());
        }
        try{
            FSRepresentationState.buildRepresentationList(representation, myRepStateList, reposRootDir);
            for(ListIterator states = myRepStateList.listIterator(); states.hasNext();){
                FSRepresentationState state = (FSRepresentationState)states.next();
                myDiffWindowBuilder.reset(SVNDiffWindowBuilder.OFFSET);
                long currentPos = state.file.getFilePointer();
                myDiffWindowBuilder.accept(state.file);
                //go back to the beginning of the window's offsets section
                state.file.seek(currentPos);
                int sourceInstructions = 0;
                for (int i = 0; i < myDiffWindowBuilder.getInstructionsData().length; i++) {
                    int type = (myDiffWindowBuilder.getInstructionsData()[i] & 0xC0) >> 6;
                    if (type == SVNDiffInstruction.COPY_FROM_SOURCE) {
                        sourceInstructions++;
                    }
                }
                if(sourceInstructions == 0){
                    for(;states.hasNext();){
                        FSRepresentationState unnecessaryState = (FSRepresentationState)states.next();
                        SVNFileUtil.closeFile(unnecessaryState.file);
                        states.remove();
                    }
                    break;
                }
            }
        }catch(SVNException svne){
            close();
            throw svne;
        }catch(IOException ioe){
            close();
            SVNErrorManager.error(ioe.getMessage());            
        }
    }
    
    public static ISVNInputStream createStream(FSRevisionNode fileNode, File reposRootDir) throws SVNException {
        if (fileNode.getType() != SVNNodeKind.FILE) {
            SVNErrorManager.error("svn: Attempted to get textual contents of a *non*-file node");
        }
        FSRepresentation representation = fileNode.getTextRepresentation(); 
        if(representation == null){
            return new FSEmptyInputStream(); 
        }
        return new FSInputStream(representation, reposRootDir);
    }
    
    public int read(byte[] buf) throws SVNException {
        return readContents(buf);
    }
    
    private int readContents(byte[] buf) throws SVNException {
        /* Get the next block of data. */
        int length = getContents(buf);
        /* Perform checksumming.  We want to check the checksum as soon as
         * the last byte of data is read, in case the caller never performs
         * a short read, but we don't want to finalize the MD5 context
         * twice. 
         */
        if(!isChecksumFinalized){
            myDigest.update(buf, 0, length);
            myOffset += length;
            if(myOffset == myLength){
                isChecksumFinalized = true;
                String hexDigest = SVNFileUtil.toHexDigest(myDigest);
                // Compare read and expected checksums
                if (!myHexChecksum.equals(hexDigest)) {
                    SVNErrorManager.error("svn: Checksum mismatch while reading representation:" + SVNFileUtil.getNativeEOLMarker() + "   expected:  " + myHexChecksum
                            + SVNFileUtil.getNativeEOLMarker() + "     actual:  " + hexDigest);
                }
            }
        }
        return length;
    }
    
    private int getContents(byte[] buffer) throws SVNException {
        int remaining = buffer.length;
        int targetPos = 0;
        while(remaining > 0){
            if(myBuffer != null){
                //copy bytes to buffer and mobe the bufPos pointer
                /* Determine how much to copy from the buffer. */
                int copyLength = myBuffer.length - myBufPos;
                if(copyLength > remaining){
                    copyLength = remaining;
                }
                /* Actually copy the data. */
                System.arraycopy(myBuffer, myBufPos, buffer, targetPos, copyLength);
                myBufPos += copyLength;
                targetPos += copyLength;
                remaining -= copyLength;
                /* If the buffer is all used up, clear it. 
                 */
                if(myBufPos == myBuffer.length){
                    myBuffer = null;
                    myBufPos = 0;
                }
            }else{
                if(myRepStateList.isEmpty()){
                    break;
                }
                FSRepresentationState repState = (FSRepresentationState)myRepStateList.getFirst();
                if(repState.offset == repState.end){
                    break;
                }
                myBuffer = applyNextWindow();
            }
        }
        return targetPos;
    }
    
    private byte[] applyNextWindow() throws SVNException {
        ByteArrayOutputStream target = new ByteArrayOutputStream();
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        ByteArrayInputStream source = null;
        FSRepresentationState startState = (FSRepresentationState)myRepStateList.getLast();
        for(ListIterator states = myRepStateList.listIterator(myRepStateList.indexOf(startState) + 1); states.hasPrevious();){
            FSRepresentationState state = (FSRepresentationState)states.previous();
            data.reset();
            SVNDiffWindow window = null;
            try{
                window = readWindow(state, myChunkIndex, data);
            }catch(IOException ioe){
                SVNErrorManager.error(ioe.getMessage());
            }
            SVNDiffWindowApplyBaton applyBaton = SVNDiffWindowApplyBaton.create(source, target, null);
            window.apply(applyBaton, new ByteArrayInputStream(data.toByteArray()));
            if(states.hasPrevious()){
                source = new ByteArrayInputStream(target.toByteArray());
                target.reset();
            }
        }
        myChunkIndex++;
        return target.toByteArray();
    }

    /* Skip forwards to thisChunk in rep state and then read the next delta
     * window. 
     */
    private SVNDiffWindow readWindow(FSRepresentationState state, int thisChunk, OutputStream dataBuf) throws SVNException, IOException {
        if(state.chunkIndex > thisChunk){
            SVNErrorManager.error("Fatal error while reading diff windows");
        }
        /* Skip windows to reach the current chunk if we aren't there yet. */
        while(state.chunkIndex < thisChunk){
            skipDiffWindow(state.file);
            state.chunkIndex++;
            state.offset = state.file.getFilePointer();
            if(state.offset >= state.end){
                SVNErrorManager.error("Reading one svndiff window read beyond the end of the representation");
            }
        }
        /* Read the next window. */
        myDiffWindowBuilder.reset(SVNDiffWindowBuilder.OFFSET);
        myDiffWindowBuilder.accept(state.file);
        SVNDiffWindow window = myDiffWindowBuilder.getDiffWindow();
        long length = window.getNewDataLength();
        byte[] buffer = new byte[(int)length];
        int read = state.file.read(buffer);
        if(read < length){
            SVNErrorManager.error("Unexpected end of svndiff input");
        }
        state.chunkIndex++;
        state.offset = state.file.getFilePointer();
        if(state.offset > state.end){
            SVNErrorManager.error("Reading one svndiff window read beyond the end of the representation");
        }
        dataBuf.write(myDiffWindowBuilder.getInstructionsData());
        dataBuf.write(buffer);
        return window;
    }
    
    private void skipDiffWindow(RandomAccessFile file) throws IOException, SVNException {
        myDiffWindowBuilder.reset(SVNDiffWindowBuilder.OFFSET);
        myDiffWindowBuilder.accept(file);
        SVNDiffWindow window = myDiffWindowBuilder.getDiffWindow();
        long len = window.getInstructionsLength() + window.getNewDataLength();
        long curPos = file.getFilePointer();
        file.seek(curPos + len);
    }
    
    public void close() {
        for(Iterator states = myRepStateList.iterator(); states.hasNext();){
            FSRepresentationState state = (FSRepresentationState)states.next();
            SVNFileUtil.closeFile(state.file);
            states.remove();
        }
    }
    
    public static class FSEmptyInputStream implements ISVNInputStream {
        public int read(byte[] buf) throws SVNException {
            return 0;
        }
        
        public void close() {
        }
    }
}
