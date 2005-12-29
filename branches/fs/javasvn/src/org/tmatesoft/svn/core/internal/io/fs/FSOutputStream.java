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
import java.io.OutputStream;
import java.io.InputStream;
import java.io.File;
import java.io.RandomAccessFile;

import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.io.diff.SVNDiffWindow;
import org.tmatesoft.svn.core.io.diff.SVNDiffWindowBuilder;
import org.tmatesoft.svn.core.io.diff.ISVNDiffWindowHandler;
import org.tmatesoft.svn.core.io.diff.SVNDeltaChunksGenerator;

/**
 * @version 1.0
 * @author  TMate Software Ltd.
 */
public class FSOutputStream extends FSBufferStream implements ISVNDiffWindowHandler {
    public static final int WRITE_BUFFER_SIZE = 512000;

    private boolean isHeaderWritten;

    /* Actual file to which we are writing. */
    private RandomAccessFile myTargetFile;
    
    /* Start of the actual data. */
    private long myDeltaStart;

    /* How many bytes have been written to this rep already. */
    private long myRepSize;
    
    /* Where is this representation header stored. */
    private long myRepOffset;
    
    private InputStream mySource;
    
    private String myTargetPath; 
    
    private SVNDeltaChunksGenerator myDeltaGenerator;
    
    private FSBufferStream myNewDataStream;
    
    private FSOutputStream(String targetPath, RandomAccessFile file, InputStream source, long deltaStart, long repSize, long repOffset){
        super();
        myTargetFile = file;
        mySource = source;
        myDeltaStart = deltaStart;
        myRepSize = repSize;
        myRepOffset = repOffset; 
        isHeaderWritten = false;
        myTargetPath = targetPath;
        myDeltaGenerator = new SVNDeltaChunksGenerator(mySource, this, FSWriter.getTmpDir());
        myNewDataStream = new FSBufferStream();
    }
    
    /*
     * revNode must be mutable!
     */
    public OutputStream createStream(String path, FSRevisionNode revNode, File reposRootDir) throws SVNException {
        RandomAccessFile targetFile = null;
        InputStream sourceStream = null;
        long offset = -1;
        long deltaStart = -1;
        try{
            /* Open the prototype rev file and seek to its end. */
            String txnId = revNode.getId().getTxnID();
            targetFile = SVNFileUtil.openRAFileForWriting(FSRepositoryUtil.getTxnRevFile(txnId, reposRootDir), true);
            offset = targetFile.getFilePointer();
            /* Get the base for this delta. */
            FSRepresentation baseRep = chooseDeltaBase(revNode, reposRootDir);
            sourceStream = FSInputStream.createStream(baseRep, reposRootDir);
            /* Write out the rep header. */
            String header;
            if(baseRep != null){
                header = FSConstants.REP_DELTA + " " + baseRep.getRevision() + " " + baseRep.getOffset() + " " + baseRep.getSize() + "\n"; 
            }else{
                header = FSConstants.REP_DELTA + "\n";
            }
            targetFile.write(header.getBytes());
            /* Now determine the offset of the actual svndiff data. */
            deltaStart = targetFile.getFilePointer();
            /* Prepare to write the svndiff data. */
        }catch(IOException ioe){
            SVNFileUtil.closeFile(targetFile);
            SVNErrorManager.error(ioe.getMessage());
        }catch(SVNException svne){
            SVNFileUtil.closeFile(targetFile);
            throw svne;
        }
        return new FSOutputStream(path, targetFile, sourceStream, deltaStart, 0, offset);
    }
    
    private static FSRepresentation chooseDeltaBase(FSRevisionNode revNode, File reposRootDir) throws SVNException {
        /* If we have no predecessors, then use the empty stream as a
         * base. 
         */
        if(revNode.getCount() == 0){
            return null;
        }
        /* Flip the rightmost '1' bit of the predecessor count to determine
         * which file rev (counting from 0) we want to use.  (To see why
         * count & (count - 1) unsets the rightmost set bit, think about how
         * you decrement a binary number.) 
         */
        long count = revNode.getCount();
        count = count & (count - 1);
        /* Walk back a number of predecessors equal to the difference
         * between count and the original predecessor count.  (For example,
         * if noderev has ten predecessors and we want the eighth file rev,
         * walk back two predecessors.) 
         */
        FSRevisionNode baseNode = revNode;
        while((count++) < revNode.getCount()){
            baseNode = FSReader.getRevNodeFromID(reposRootDir, baseNode.getId());
        }
        return baseNode.getTextRepresentation(); 
    }
    
    public void write(int b) throws IOException{
        super.write(b);
        if(super.myBufferLength > WRITE_BUFFER_SIZE){
            try{
                myDeltaGenerator.makeDiffWindowFromData(super.myBuffer, super.myBufferLength);
            }catch(SVNException svne){
                throw new IOException(svne.getMessage());
            }
            super.myBufferLength = 0;
            super.myBuffer = null;
        }
    }
    
    public void write(byte[] b) throws IOException{
        super.write(b);
        if(super.myBufferLength > WRITE_BUFFER_SIZE){
            try{
                myDeltaGenerator.makeDiffWindowFromData(super.myBuffer, super.myBufferLength);
            }catch(SVNException svne){
                throw new IOException(svne.getMessage());
            }
            super.myBufferLength = 0;
            super.myBuffer = null;
        }
    }
    
    public void write(byte[] b, int off, int len) throws IOException{
        super.write(b, off, len);
        if(super.myBufferLength > WRITE_BUFFER_SIZE){
            try{
                myDeltaGenerator.makeDiffWindowFromData(super.myBuffer, super.myBufferLength);
            }catch(SVNException svne){
                throw new IOException(svne.getMessage());
            }
            super.myBufferLength = 0;
            super.myBuffer = null;
        }
    }

    public void close() throws IOException {
        try{
            myDeltaGenerator.makeDiffWindowFromData(super.myBuffer, super.myBufferLength);
            myDeltaGenerator.flushNextWindow();
            super.myBufferLength = 0;
            super.myBuffer = null;
            //TODO: finalizing code
            handleDiffWindow(null);
        }catch(SVNException svne){
            throw new IOException(svne.getMessage());
        }finally{
            /* We're done; clean up. */
            SVNFileUtil.closeFile(myTargetFile);
        }
    }
    
    public OutputStream getNewDataStream(){
        myNewDataStream.myBufferLength = 0;
        return myNewDataStream;
    }
    
    public void handleDiffWindow(SVNDiffWindow window) throws SVNException {
        try{
            /* Make sure we write the header.  */
            if(!isHeaderWritten){
                myTargetFile.write("SVN\0".getBytes());
                isHeaderWritten = true;
            }
            if(window == null){
                return;
            }
            SVNDiffWindowBuilder.save(window, !isHeaderWritten, myTargetFile);
            myTargetFile.write(myNewDataStream.myBuffer, 0, myNewDataStream.myBufferLength);
        }catch(IOException ioe){
            SVNErrorManager.error(ioe.getMessage());
        }
    }
}
