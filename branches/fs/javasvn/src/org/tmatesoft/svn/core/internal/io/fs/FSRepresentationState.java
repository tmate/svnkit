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

import java.io.RandomAccessFile;
import java.util.List;
import java.io.File;
import java.util.LinkedList;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;

/**
 * Represents where in the current svndiff data block each
 * representation is.
 *  
 * @version 1.0
 * @author  TMate Software Ltd.
 */
public class FSRepresentationState {
    private RandomAccessFile file;
    /* The starting offset for the raw svndiff/plaintext data minus header. */
    private long start;
    /* The current offset into the file. */
    private long offset;
    /* The end offset of the raw data. */
    private long end;
    /* If a delta, what svndiff version? */
    private int version;
    
    private int chunkIndex;

    public FSRepresentationState(RandomAccessFile file, long start, long offset, long end, int version, int index) {
        this.file = file;
        this.start = start;
        this.offset = offset;
        this.end = end;
        this.version = version;
        chunkIndex = index;
    }
    
    public int getChunkIndex() {
        return chunkIndex;
    }

    public void setChunkIndex(int chunkIndex) {
        this.chunkIndex = chunkIndex;
    }
    
    public long getEnd() {
        return end;
    }
    
    public void setEnd(long end) {
        this.end = end;
    }

    public RandomAccessFile getFile() {
        return file;
    }
    
    public void setFile(RandomAccessFile file) {
        this.file = file;
    }

    public long getOffset() {
        return offset;
    }
    
    public void setOffset(long offset) {
        this.offset = offset;
    }

    public long getStart() {
        return start;
    }

    public void setStart(long start) {
        this.start = start;
    }
    
    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    /* Build an array of FSRepresentationState objects in 'result' giving the delta
     * reps from firstRep to a self-compressed rep. 
     */
    public static List buildRepresentationList(FSRepresentation firstRep, List result, File reposRootDir) throws SVNException {
        result = result == null ? new LinkedList() : result;
        while(true){
            
        }
    }

    /* Read the next line from a file and parse it as a text
     * representation entry. Return parsed args.
     */
    private static FSRepresentationArgs readRepresentationLine(RandomAccessFile file) throws SVNException {
        try{
            String line = FSReader.readNextLine(null, file, 0, false, 160);
            FSRepresentationArgs repArgs = new FSRepresentationArgs();
            repArgs.isDelta = false;
            if(FSConstants.REP_PLAIN.equals(line)){
                return repArgs;
            }
            if(FSConstants.REP_DELTA.equals(line)){
                /* This is a delta against the empty stream. */
                repArgs.isDelta = true;
                repArgs.isDeltaVsEmpty = true;
                return repArgs;
            }
            repArgs.isDelta = true;
            repArgs.isDeltaVsEmpty = false;
            /* We have hopefully a DELTA vs. a non-empty base revision. */
            String[] args = line.split(" ");
            if(args.length != 4){
                SVNErrorManager.error("Malformed representation header");
            }
            if(!FSConstants.REP_DELTA.equals(args[0])){
                SVNErrorManager.error("Malformed representation header");
            }
            try{
                repArgs.myBaseRevision = Long.parseLong(args[1]);
                repArgs.myBaseOffset = Long.parseLong(args[2]);
                repArgs.myBaseLength = Long.parseLong(args[3]);
            }catch(NumberFormatException nfe){
                SVNErrorManager.error("Malformed representation header");
            }
            return repArgs;
        }catch(SVNException svne){
            SVNFileUtil.closeFile(file);
            throw svne;
        }
    }
    
    private static class FSRepresentationArgs {
        boolean isDelta;
        boolean isDeltaVsEmpty;
        long myBaseRevision;
        long myBaseOffset;
        long myBaseLength;
    }
}
