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
import java.io.OutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.lang.Process;
import java.lang.InterruptedException;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.wc.SVNFileType;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;

/**
 * @version 1.0
 * @author  TMate Software Ltd.
 */
public class FSHooks {
    static String SVN_REPOS_HOOK_START_COMMIT = "start-commit";
    static String SVN_REPOS_HOOK_PRE_COMMIT = "pre-commit";
    static String SVN_REPOS_HOOK_POST_COMMIT = "post-commit";
    static String SVN_REPOS_HOOK_PRE_REVPROP_CHANGE = "pre-revprop-change";
    static String SVN_REPOS_HOOK_POST_REVPROP_CHANGE = "post-revprop-change";
    static String SVN_REPOS_HOOK_PRE_LOCK = "pre-lock";
    static String SVN_REPOS_HOOK_POST_LOCK = "post-lock";
    static String SVN_REPOS_HOOK_PRE_UNLOCK = "pre-unlock";
    static String SVN_REPOS_HOOK_POST_UNLOCK = "post-unlock";
    static String SVN_REPOS_HOOK_READ_SENTINEL = "read-sentinels";
    static String SVN_REPOS_HOOK_WRITE_SENTINEL = "write-sentinels";
    static String SVN_REPOS_HOOK_DESC_EXT = ".tmpl";
    static String SVN_REPOS_HOOKS_DIR = "hooks";
    static String REVPROP_DELETE = "D";
    static String REVPROP_ADD = "A";
    static String REVPROP_MODIFY = "M";
    
    private static String[] winExtensions = {".exe", ".bat", ".cmd"};
    private static String[] nonWinExtensions = {""};
    private static Map myHookers = new HashMap();
    private String myReposRootDir;
    
    static File getHookFile(String reposRootPath, String hookName) throws SVNException {
        String[] extensions = null;
        if(SVNFileUtil.isWindows){
            extensions = winExtensions;
        }else{
            extensions = nonWinExtensions;
        }
        for(int i = 0; i < extensions.length; i++){
            File hookFile = new File(getHooksDir(reposRootPath), hookName + extensions[i]);
            SVNFileType type = SVNFileType.getType(hookFile);
            if(type == SVNFileType.FILE){
                return hookFile; 
            }else if(type == SVNFileType.SYMLINK){
                //should first resolve the symlink and then decide if it's broken and
                //throw an exception
                File realFile = SVNFileUtil.resolveSymlinkToFile(hookFile);
                if(realFile == null){
                    throw new SVNException("svn: Failed to run '" + hookFile.getAbsolutePath() + "' hook; broken symlink");
                }
                return hookFile;
            }
        }
        return null;
    }

    static File getHooksDir(String reposRootPath) {
        return new File(reposRootPath, SVN_REPOS_HOOKS_DIR);
    }

    private FSHooks(String reposRootPath){
        myReposRootDir = reposRootPath;
    }
    
    public static FSHooks getInstance(String reposRootPath){
        FSHooks hooker = (FSHooks)myHookers.get(reposRootPath);
        if(hooker != null){
            return hooker;
        }
        
        hooker = new FSHooks(reposRootPath);
        myHookers.put(reposRootPath, hooker);
        return hooker;
    }
    
    public void runPreRevPropChangeHook(String propName, String propNewValue, String reposPath, String author, long revision, String action) throws SVNException {
        changeRevPropHook(SVN_REPOS_HOOK_PRE_REVPROP_CHANGE, propName, propNewValue, reposPath, author, revision, action, true);
    }
    
    public void runPostRevPropChangeHook(String propName, String propOldValue, String reposPath, String author, long revision, String action) throws SVNException {
        changeRevPropHook(SVN_REPOS_HOOK_POST_REVPROP_CHANGE, propName, propOldValue, reposPath, author, revision, action, false);
    }
    
    private void changeRevPropHook(String hookName, String propName, String propValue, String reposPath, String author, long revision, String action, boolean isPre) throws SVNException {
        File hookFile = getHookFile(myReposRootDir, hookName);
        if(hookFile == null && isPre){
            throw new SVNException("svn: Repository has not been enabled to accept revision propchanges;" + SVNFileUtil.getNativeEOLMarker() + 
                                   "ask the administrator to create a pre-revprop-change hook");
        }else if(hookFile == null){
            return;
        }
        
        String[] cmd = {hookFile.getAbsolutePath(), reposPath, String.valueOf(revision), author != null ? author : "", propName, action};
        
        Process hookProc = null;
        try{
            hookProc = Runtime.getRuntime().exec(cmd);
        }catch(IOException svne){
            throw new SVNException("svn: Failed to run '" + hookFile.getAbsolutePath() + "' hook");
        }
        
        // Pass the new value as stdin to hook
        if(propValue != null){
            OutputStream os = hookProc.getOutputStream();
            try{
                os.write(propValue.getBytes());
            }catch(IOException ioe){
                throw new SVNException("svn: Failed to run '" + hookFile.getAbsolutePath() + "' hook" + SVNFileUtil.getNativeEOLMarker() + 
                                       "svn: Can't set process '" + hookFile.getAbsolutePath() + "' child input");
            }
        }
        
        int rc = -1;
        try{
            rc = hookProc.waitFor();
        }catch(InterruptedException ie){
            throw new SVNException("svn: Failed to run '" + hookFile.getAbsolutePath() + "' hook" + SVNFileUtil.getNativeEOLMarker() +
                                   "svn: Error waiting for process '" + hookFile.getAbsolutePath() + "'");
        }

        InputStream is = null;
        StringBuffer result = null;
        String errString = null;

        if(isPre){
            is = hookProc.getInputStream();
            result = new StringBuffer();
            int r;            
            try{
                while (is != null && (r = is.read()) >= 0) {
                    result.append((char) (r & 0xFF));
                }
            }catch(IOException ioe){
                //
            }finally{
                SVNFileUtil.closeFile(is);
            }
            errString = result.toString();
        }
        
        if (rc != 0) {
            throw new SVNException((errString == null || !isPre ? "svn: '" + hookName + "' hook failed; no error output available" : 
                                    "svn: '" + hookName + "' hook failed with error output:" + SVNFileUtil.getNativeEOLMarker() + 
                                    errString));
        }
    }
}
