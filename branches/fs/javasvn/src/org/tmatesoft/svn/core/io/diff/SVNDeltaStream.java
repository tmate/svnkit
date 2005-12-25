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

import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.SVNException;

/**
 * 
 * @version 1.0
 * @author  TMate Software Ltd.
 */
public class SVNDeltaStream {
    public static final int SVN_DELTA_WINDOW_SIZE = 102400;
    
    private ISVNInputStream mySourceStream;
    
    private ISVNInputStream myTargetStream;
    
    private ISVNEditor myConsumer;
    
    private String myTargetPath;
    
    private byte[] mySourceBuf = new byte[SVN_DELTA_WINDOW_SIZE];
    
    private byte[] myTargetBuf = new byte[SVN_DELTA_WINDOW_SIZE];
    
    private long myPos;

    private ISVNDeltaGenerator myDeltaGenerator;
    
    public SVNDeltaStream(ISVNInputStream sourceStream, ISVNInputStream targetStream, ISVNEditor consumer, String path, File tmpDir) {
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
        /* Read the source stream. */
        int sourceLength = mySourceStream.read(mySourceBuf);
        /* Read the target stream. */
        int targetLength = myTargetStream.read(myTargetBuf);
        myPos += sourceLength;
        if(targetLength == 0){
            myConsumer.textDeltaEnd(myTargetPath);
            return false;
        }
        byte[] source = new byte[sourceLength];
        System.arraycopy(mySourceBuf, 0, source, 0, sourceLength);
        byte[] target = new byte[targetLength];
        System.arraycopy(myTargetBuf, 0, target, 0, targetLength);
        ISVNRAData sourceData = new SVNRAStreamData(source);
        ISVNRAData targetData = new SVNRAStreamData(target);
        myDeltaGenerator.generateNextDiffWindow(myTargetPath, myConsumer, targetData, sourceData, myPos - sourceLength);
        return true;
    }
    
    public void close(){
        mySourceStream.close();
        myTargetStream.close();
    }
}
