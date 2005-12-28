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
package org.tmatesoft.svn.core.io.diff;

import java.io.File;
import java.io.InputStream;
import java.io.IOException;

import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;

/**
 * 
 * @version 1.0
 * @author  TMate Software Ltd.
 */
public class SVNDeltaChunksGenerator {
    public static final int SVN_DELTA_WINDOW_SIZE = 102400;
    
    private InputStream mySourceStream;
    
    private InputStream myTargetStream;
    
    private ISVNEditor myConsumer;
    
    private String myTargetPath;
    
    private byte[] mySourceBuf = new byte[SVN_DELTA_WINDOW_SIZE];
    
    private byte[] myTargetBuf = new byte[SVN_DELTA_WINDOW_SIZE];
    
    private long myPos;

    private ISVNDeltaGenerator myDeltaGenerator;
    
    public SVNDeltaChunksGenerator(InputStream sourceStream, InputStream targetStream, ISVNEditor consumer, String path, File tmpDir) {
        mySourceStream = sourceStream;
        myTargetStream = targetStream;
        myConsumer = consumer;
        myTargetPath = path;
        myPos = 0;
        myDeltaGenerator = new SVNSequenceDeltaGenerator(tmpDir);
    }
    
    public void sendWindows() throws SVNException {
        //runs loop until target runs out of text 
        while(generateNextWindow()){
            ;
        }
    }
    
    private boolean generateNextWindow() throws SVNException {
        int sourceLength = 0;
        int targetLength = 0;
        try{
            /* Read the source stream. */
            sourceLength = mySourceStream.read(mySourceBuf);
            /* Read the target stream. */
            targetLength = myTargetStream.read(myTargetBuf);
        }catch(IOException ioe){
            SVNErrorManager.error(ioe.getMessage());
        }
        sourceLength = sourceLength == -1 ? 0 : sourceLength;
        targetLength = targetLength == -1 ? 0 : targetLength;
        myPos += sourceLength;
        if(targetLength == 0){
            myConsumer.textDeltaEnd(myTargetPath);
            return false;
        }
        ISVNRAData sourceData = new SVNRABufferData(mySourceBuf, sourceLength);
        ISVNRAData targetData = new SVNRABufferData(myTargetBuf, targetLength);
        myDeltaGenerator.generateNextDiffWindow(myTargetPath, myConsumer, targetData, sourceData, myPos - sourceLength);
        return true;
    }
}
