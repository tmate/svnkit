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
import java.io.FileInputStream;
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

public class SVNRevFileReader {
    private File myRevisionFile;
    private long myRootOffset;
    private long myChangesOffset;
    private long myRevision;
    private FSRepository myFSRepos;
    
    //to mean the end of a file 
    private long FILE_END_POS = -1;

    public SVNRevFileReader(File revFile, long myRevision, FSRepository fsRepos){
        myRevisionFile = revFile;
        myFSRepos = fsRepos;
        myRootOffset = -1;
        myChangesOffset = -1;
    }
    
    public SVNRevisionNode getRootNode() throws SVNException{
        SVNRevisionNode rootNode = new SVNRevisionNode();
        
        getRootOffset();
        byte[] buffer = new byte[1024];

        while(true){
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
    
    private void readRootAndChangesOffset() throws SVNException{
        String eolBytes = new String(SVNTranslator.getEOL(SVNProperty.EOL_STYLE_NATIVE));
        
        int size = 64;
        byte[] buffer = new byte[size];
        
        try{
            /* svn: We will assume that the last line containing the two offsets
             * will never be longer than 64 characters.
             * Read in this last block, from which we will identify the last line. 
             */
            myFSRepos.readBytesFromFile(FSRepository.FILE_END_POS, -size, buffer, size, myRevisionFile);
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
            throw new SVNException("svn: unparsable root offset number in revision file '" + myRevisionFile + "'");
        }
        try{
            myChangesOffset = Long.parseLong(offsetsValues[1]);
        }catch(NumberFormatException nfe){
            throw new SVNException("svn: unparsable changes offset number in revision file '" + myRevisionFile + "'");
        }
    }
}
