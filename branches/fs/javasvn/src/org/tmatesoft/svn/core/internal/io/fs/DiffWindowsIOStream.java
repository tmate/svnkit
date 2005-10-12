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
import java.io.File;
import java.io.IOException;
import java.io.FileOutputStream;
import java.io.FileNotFoundException;
import java.io.FileInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.security.MessageDigest;

import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.io.diff.SVNDiffWindowApplyBaton;

/**
 * @version 1.0
 * @author  TMate Software Ltd.
 */
public class DiffWindowsIOStream {
    private InputStream mySourceInputStream;
    private OutputStream myTargetOutputStream;
    private SVNDiffWindowApplyBaton myDiffWindowApplyBaton;
    private File myTempTargetFile;
    private File myTempSourceFile;
    
    public OutputStream getTemporaryTargetOutputStream(){
        updateSourceStream();
        
        myTempTargetFile = getTemporaryFile();
        if(myTempTargetFile != null){
            try{
                closeTargetStream();
                myTargetOutputStream = new FileOutputStream(myTempTargetFile);
                return myTargetOutputStream;
            }catch(FileNotFoundException fnfe){
                closeTargetStream();
            }
        }
        
        myTargetOutputStream = new ByteArrayOutputStream();

        return myTargetOutputStream;
    }

    public InputStream getTemporarySourceInputStream() {
        updateSourceStream();
        return mySourceInputStream;
    }
    
    public SVNDiffWindowApplyBaton getDiffWindowApplyBaton(MessageDigest digest) {
        myDiffWindowApplyBaton = SVNDiffWindowApplyBaton.create(getTemporarySourceInputStream(), getTemporaryTargetOutputStream(), digest);
        return myDiffWindowApplyBaton;
    }
    
    public void closeSourceStream(){
        SVNFileUtil.closeFile(mySourceInputStream);
    }
    
    public void closeTargetStream(){
        SVNFileUtil.closeFile(myTargetOutputStream);
    }

    private void updateSourceStream() {
        if(myTempSourceFile != null && myTempSourceFile == myTempTargetFile){
            return;
        }
        
        closeSourceStream();
        
        if(myTempSourceFile != null && myTempSourceFile.exists()){
            myTempSourceFile.delete();
        }
        myTempSourceFile = myTempTargetFile;
        if(myTempSourceFile != null){
            try{
                mySourceInputStream = new FileInputStream(myTempSourceFile);
            }catch(FileNotFoundException fnfe){
                closeSourceStream();
                mySourceInputStream = null;
            }
        }else if(myTargetOutputStream != null && myTargetOutputStream instanceof ByteArrayOutputStream){
            mySourceInputStream = new ByteArrayInputStream(((ByteArrayOutputStream)myTargetOutputStream).toByteArray());
        }
    }
    
    private File getTemporaryFile(){
        File tempFile = SVNFileUtil.createUniqueFile(new File("").getParentFile(), "javasvn-diff-window-file", ".tmp");
        try{
            if(tempFile.createNewFile()){
                tempFile.deleteOnExit();
                return tempFile;
            }
        }catch(IOException ioe){
            //
        }
        return null;
    }
}
