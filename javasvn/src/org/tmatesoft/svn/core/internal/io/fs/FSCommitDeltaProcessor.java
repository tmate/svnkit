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

import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.io.ByteArrayInputStream;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.io.diff.SVNDiffWindow;
import org.tmatesoft.svn.core.io.diff.SVNDiffWindowApplyBaton;


/**
 * @version 1.0
 * @author  TMate Software Ltd.
 */
public class FSCommitDeltaProcessor extends OutputStream{
    private InputStream mySourceStream;
    
    private OutputStream myTargetStream;
    
    private SVNDiffWindowApplyBaton myApplyBaton;
    
    private SVNDiffWindow myCurrentWindow;
    
    private byte[] myBuffer;
    
    private int myBufferLength;
    
    public FSCommitDeltaProcessor(InputStream sourceStream, OutputStream targetStream){
        super();
        mySourceStream = sourceStream;
        myTargetStream = targetStream;
        myApplyBaton = SVNDiffWindowApplyBaton.create(mySourceStream, myTargetStream, null);
        myBufferLength = 0;
    }
    
    public OutputStream handleDiffWindow(SVNDiffWindow window){
        //super.reset();
        myCurrentWindow = window;
        return this;
    }
    
    public void write(int b) throws IOException{
        byte[] result = new byte[myBufferLength + 1];
        if(myBufferLength > 0){
            System.arraycopy(myBuffer, 0, result, 0, myBufferLength);
        }
        result[myBufferLength] = (byte)b;
        myBuffer = result;
        myBufferLength++;
    }
    
    public void write(byte[] b) throws IOException{
        byte[] result = new byte[myBufferLength + b.length];
        if(myBufferLength > 0){
            System.arraycopy(myBuffer, 0, result, 0, myBufferLength);
        }
        System.arraycopy(b, 0, result, myBufferLength, b.length);
        myBuffer = result;
        myBufferLength += b.length;
    }
    
    public void write(byte[] b, int off, int len) throws IOException{
        byte[] result = new byte[myBufferLength + len];
        if(myBufferLength > 0){
            System.arraycopy(myBuffer, 0, result, 0, myBufferLength);
        }
        System.arraycopy(b, off, result, myBufferLength, len);
        myBuffer = result;
        myBufferLength += len;
    }
    
    public void close() throws IOException {
        try{
            myCurrentWindow.apply(myApplyBaton, new ByteArrayInputStream(myBuffer == null ? new byte[0] : myBuffer));
        }catch(SVNException svne){
            throw new IOException(svne.getMessage());
        }
    }
    
    public void onTextDeltaEnd(){
        myApplyBaton.close();
    }
}
