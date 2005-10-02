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
import java.util.HashMap;
import java.util.Map;

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
    static String ACTION_DELETE = "D";
    static String ACTION_ADD = "A";
    static String ACTION_MODIFY = "M";
    
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
    
    public void runPreRevPropChangeHook(String propName, String propValue, String reposPath, String author, long revision, String wkDir, String action) throws SVNException {
        File hookFile = getHookFile(myReposRootDir, SVN_REPOS_HOOK_PRE_REVPROP_CHANGE);
        if(hookFile == null){
            throw new SVNException("svn: Repository has not been enabled to accept revision propchanges;" + SVNFileUtil.getNativeEOLMarker() + "ask the administrator to create a pre-revprop-change hook");
        }
        String[] cmd = {hookFile.getAbsolutePath(), reposPath, String.valueOf(revision), author != null ? author : "", action};
        
        Process hookProc = null;
        try{
            hookProc = Runtime.getRuntime().exec(cmd);
        }catch(IOException svne){
            throw new SVNException("svn: Failed to run '" + hookFile.getAbsolutePath() + "' hook");
        }
        
        
        
    }
}
