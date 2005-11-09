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

import java.io.File;
import java.io.IOException;
import java.io.FileOutputStream;
import java.io.FileNotFoundException;

import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;

/**
 * @version 1.0
 * @author  TMate Software Ltd.
 */
public class FSWriter {
    
    public static File testTempDir(File tmpDir){
        File tmpFile = null;
        FileOutputStream fos = null;
        for(int i = 0; i < 2; i++){
            try{
                tmpFile = File.createTempFile("javasvn-tmp", ".tmp", i == 0 ? null : tmpDir);
                fos = new FileOutputStream(tmpFile);
                fos.write('!');
                fos.close();
                return tmpFile.getParentFile();
            }catch(FileNotFoundException fnfe){
                continue;
            }catch(IOException ioe){
                continue;
            }catch(SecurityException se){
                continue;
            }finally{
                SVNFileUtil.closeFile(fos);
                try{
                    tmpFile.delete();
                }catch(SecurityException se){
                }
            }
        }
        return null;
    }
    
}
