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
import java.lang.Process;
import java.lang.InterruptedException;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNFileType;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;

/**
 * @version 1.0
 * @author  TMate Software Ltd.
 */
public class FSHooks {
    public static final String SVN_REPOS_HOOK_START_COMMIT = "start-commit";
    public static final String SVN_REPOS_HOOK_PRE_COMMIT = "pre-commit";
    public static final String SVN_REPOS_HOOK_POST_COMMIT = "post-commit";
    public static final String SVN_REPOS_HOOK_PRE_REVPROP_CHANGE = "pre-revprop-change";
    public static final String SVN_REPOS_HOOK_POST_REVPROP_CHANGE = "post-revprop-change";
    public static final String SVN_REPOS_HOOK_PRE_LOCK = "pre-lock";
    public static final String SVN_REPOS_HOOK_POST_LOCK = "post-lock";
    public static final String SVN_REPOS_HOOK_PRE_UNLOCK = "pre-unlock";
    public static final String SVN_REPOS_HOOK_POST_UNLOCK = "post-unlock";
    public static final String SVN_REPOS_HOOK_READ_SENTINEL = "read-sentinels";
    public static final String SVN_REPOS_HOOK_WRITE_SENTINEL = "write-sentinels";
    public static final String SVN_REPOS_HOOK_DESC_EXT = ".tmpl";
    public static final String SVN_REPOS_HOOKS_DIR = "hooks";
    public static final String REVPROP_DELETE = "D";
    public static final String REVPROP_ADD = "A";
    public static final String REVPROP_MODIFY = "M";
    private static final String[] winExtensions = {".exe", ".bat", ".cmd"};
    
    public static File getHookFile(File reposRootDir, String hookName) throws SVNException {
        File hookFile = null;
        if(SVNFileUtil.isWindows){
            for(int i = 0; i < winExtensions.length; i++){
                hookFile = new File(getHooksDir(reposRootDir), hookName + winExtensions[i]);
                SVNFileType type = SVNFileType.getType(hookFile);
                if(type == SVNFileType.FILE){
                    return hookFile;
                }
            } 
        }else{
            hookFile = new File(getHooksDir(reposRootDir), hookName);
            SVNFileType type = SVNFileType.getType(hookFile);
            if(type == SVNFileType.FILE){
                return hookFile; 
            }else if(type == SVNFileType.SYMLINK){
                //should first resolve the symlink and then decide if it's broken and
                //throw an exception
                File realFile = SVNFileUtil.resolveSymlinkToFile(hookFile);
                if(realFile == null){
                    SVNErrorManager.error("svn: Failed to run '" + hookFile.getAbsolutePath() + "' hook; broken symlink");
                }
                return hookFile;
            }
        }
        return null;
    }

    public static File getHooksDir(File reposRootDir) {
        return new File(reposRootDir, SVN_REPOS_HOOKS_DIR);
    }

    public static void runPreLockHook(File reposRootDir, String path, String username) throws SVNException {
        runLockHook(reposRootDir, SVN_REPOS_HOOK_PRE_LOCK, path, username, null);
    }

    public static void runPostLockHook(File reposRootDir, String[] paths, String username) throws SVNException {
        StringBuffer pathsStr = new StringBuffer();
        for(int i = 0; i < paths.length; i++){
            pathsStr.append(paths[i]);
            pathsStr.append("\n");
        }
        runLockHook(reposRootDir, SVN_REPOS_HOOK_POST_LOCK, null, username, pathsStr.toString());
    }

    public static void runPreUnlockHook(File reposRootDir, String path, String username) throws SVNException {
        runLockHook(reposRootDir, SVN_REPOS_HOOK_PRE_UNLOCK, path, username, null);
    }

    public static void runPostUnlockHook(File reposRootDir, String[] paths, String username) throws SVNException {
        StringBuffer pathsStr = new StringBuffer();
        for(int i = 0; i < paths.length; i++){
            pathsStr.append(paths[i]);
            pathsStr.append("\n");
        }
        runLockHook(reposRootDir, SVN_REPOS_HOOK_POST_UNLOCK, null, username, pathsStr.toString());
    }
    
    private static void runLockHook(File reposRootDir, String hookName, String path, String username, String paths) throws SVNException {
        File hookFile = getHookFile(reposRootDir, hookName);
        if(hookFile == null){
            return;
        }
        username = username == null ? "" : username;
        Process hookProc = null;
        String reposPath = reposRootDir.getAbsolutePath().replace(File.separatorChar, '/');
        try{
            String executableName = hookFile.getName().toLowerCase();
            if((executableName.endsWith(".bat") || executableName.endsWith(".cmd")) && SVNFileUtil.isWindows){
                String cmd = "cmd /C \"" +  
                             "\"" + hookFile.getAbsolutePath() + "\" " +
                             "\"" + reposPath + "\" " + 
                             (path != null ? "\"" + path + "\" " : "") + 
                             "\"" + username + "\"";
                hookProc = Runtime.getRuntime().exec(cmd);
            }else{
                if(path != null){
                    String[] cmd = {hookFile.getAbsolutePath(), reposPath, path, !"".equals(username) ? username : "\"\""};
                    hookProc = Runtime.getRuntime().exec(cmd);
                }else{
                    String[] cmd = {hookFile.getAbsolutePath(), reposPath, !"".equals(username) ? username : "\"\""};
                    hookProc = Runtime.getRuntime().exec(cmd);
                }
            }
        }catch(IOException ioe){
            SVNErrorManager.error("svn: Failed to run '" + hookFile.getAbsolutePath() + "' hook: " + ioe.getMessage());
        }
        runHook(hookFile, hookProc, paths, path != null);
    }
    
    public static void runPreRevPropChangeHook(File reposRootDir, String propName, String propNewValue, String author, long revision, String action) throws SVNException {
        runChangeRevPropHook(reposRootDir, SVN_REPOS_HOOK_PRE_REVPROP_CHANGE, propName, propNewValue, author, revision, action, true);
    }
    
    public static void runPostRevPropChangeHook(File reposRootDir, String propName, String propOldValue, String author, long revision, String action) throws SVNException {
        runChangeRevPropHook(reposRootDir, SVN_REPOS_HOOK_POST_REVPROP_CHANGE, propName, propOldValue, author, revision, action, false);
    }

    public static void runStartCommitHook(File reposRootDir, String author) throws SVNException {
        author = author == null ? "" : author;
        runCommitHook(reposRootDir, SVN_REPOS_HOOK_START_COMMIT, author, true);
    }

    public static void runPreCommitHook(File reposRootDir, String txnName) throws SVNException {
        runCommitHook(reposRootDir, SVN_REPOS_HOOK_PRE_COMMIT, txnName, true);
    }

    public static void runPostCommitHook(File reposRootDir, long committedRevision) throws SVNException {
        runCommitHook(reposRootDir, SVN_REPOS_HOOK_POST_COMMIT, String.valueOf(committedRevision), false);
    }
    
    private static void runCommitHook(File reposRootDir, String hookName, String secondArg, boolean readErrorStream) throws SVNException {
        File hookFile = getHookFile(reposRootDir, hookName);
        if(hookFile == null){
            return;
        }
        Process hookProc = null;
        String reposPath = reposRootDir.getAbsolutePath().replace(File.separatorChar, '/');
        try{
            String executableName = hookFile.getName().toLowerCase();
            if((executableName.endsWith(".bat") || executableName.endsWith(".cmd")) && SVNFileUtil.isWindows){
                String cmd = "cmd /C \"" +  
                             "\"" + hookFile.getAbsolutePath() + "\" " +
                             "\"" + reposPath + "\" " + 
                             "\"" + secondArg + "\"";
                hookProc = Runtime.getRuntime().exec(cmd);
            }else{
                String[] cmd = {hookFile.getAbsolutePath(), reposPath, secondArg != null && !"".equals(secondArg) ? secondArg : "\"\""};
                hookProc = Runtime.getRuntime().exec(cmd);
            }
        }catch(IOException ioe){
            SVNErrorManager.error("svn: Failed to run '" + hookFile.getAbsolutePath() + "' hook: " + ioe.getMessage());
        }
        runHook(hookFile, hookProc, null, readErrorStream);
    }
    
    private static void runChangeRevPropHook(File reposRootDir, String hookName, String propName, String propValue, String author, long revision, String action, boolean isPre) throws SVNException {
        File hookFile = getHookFile(reposRootDir, hookName);
        if(hookFile == null && isPre){
            SVNErrorManager.error("svn: Repository has not been enabled to accept revision propchanges;" + SVNFileUtil.getNativeEOLMarker() + 
                                   "ask the administrator to create a pre-revprop-change hook");
        }else if(hookFile == null){
            return;
        }
        author = author == null ? "" : author;
        Process hookProc = null;
        String reposPath = reposRootDir.getAbsolutePath().replace(File.separatorChar, '/');
        try{
            String executableName = hookFile.getName().toLowerCase();
            if((executableName.endsWith(".bat") || executableName.endsWith(".cmd")) && SVNFileUtil.isWindows){
                String cmd = "cmd /C \"" +  
                             "\"" + hookFile.getAbsolutePath() + "\" " +
                             "\"" + reposPath + "\" " + 
                             String.valueOf(revision) + " " + 
                             "\"" + author + "\" " + 
                             "\"" + propName + "\" " + 
                             action + "\"";
                hookProc = Runtime.getRuntime().exec(cmd);
            }else{
                String[] cmd = {hookFile.getAbsolutePath(), reposPath, String.valueOf(revision), !"".equals(author) ? author : "\"\"", propName, action};
                hookProc = Runtime.getRuntime().exec(cmd);
            }
        }catch(IOException ioe){
            SVNErrorManager.error("svn: Failed to run '" + hookFile.getAbsolutePath() + "' hook: " + ioe.getMessage());
        }
        runHook(hookFile, hookProc, propValue, isPre);
    }
    
    private static void runHook(File hook, Process hookProcess, String stdInValue, boolean readErrorStream) throws SVNException {
        if(hookProcess == null){
            SVNErrorManager.error("svn: Can't run '" + hook.getAbsolutePath() + "' hook process");
        }
        if(stdInValue != null){
            OutputStream osToStdIn = hookProcess.getOutputStream();
            try{
                osToStdIn.write(stdInValue.getBytes());
            }catch(IOException ioe){
                SVNErrorManager.error("svn: Failed to run '" + hook.getAbsolutePath() + "' hook" + SVNFileUtil.getNativeEOLMarker() + 
                                       "svn: Can't set process '" + hook.getAbsolutePath() + "' child input: " + ioe.getMessage());
            }finally{
                SVNFileUtil.closeFile(osToStdIn);
            }
        }
        int rc = -1;
        try{
            rc = hookProcess.waitFor();
        }catch(InterruptedException ie){
            SVNErrorManager.error("svn: Failed to run '" + hook.getAbsolutePath() + "' hook" + SVNFileUtil.getNativeEOLMarker() +
                                   "svn: Error waiting for process '" + hook.getAbsolutePath() + "' " + ie.getMessage());
        }
        String errString = null;
        if(readErrorStream){
            InputStream is = null;
            StringBuffer result = null;
            is = hookProcess.getErrorStream();
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
            SVNErrorManager.error((!readErrorStream ? "svn: '" + hook.getName() + "' hook failed; no error output available" : 
                                    "svn: '" + hook.getName() + "' hook failed with error output:" + SVNFileUtil.getNativeEOLMarker() + 
                                    errString != null ? errString : ""));
        }
    }
}