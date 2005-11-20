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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNRevisionProperty;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNFileType;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc.SVNProperties;


/**
 * @version 1.0
 * @author  TMate Software Ltd.
 */
public class FSRepositoryUtil {
    
    public static Map getPropsDiffs(Map sourceProps, Map targetProps){
        Map result = new HashMap();
        /* Loop over sourceProps and examine each key.  This will allow 
         * us to detect any `deletion' events or `set-modification' 
         * events.  
         */
        Object[] names = sourceProps.keySet().toArray();
        for(int i = 0; i < names.length; i++){
            String propName = (String)names[i];
            String srcPropVal = (String)sourceProps.get(propName);
            /* Does property name exist in targetProps? */
            String targetPropVal = (String)targetProps.get(propName);
            if(targetPropVal == null){
                /* Add a delete event to the result */
                result.put(propName, null);
            }else if(!targetPropVal.equals(srcPropVal)){
                /* Add a set (modification) event to the result */
                result.put(propName, targetPropVal);
            }
        }
        /* Loop over targetProps and examine each key.  This allows us 
         * to detect `set-creation' events 
         */
        names = targetProps.keySet().toArray();
        for(int i = 0; i < names.length; i++){
            String propName = (String)names[i];
            String targetPropVal = (String)targetProps.get(propName);
            /* Does property name exist in sourceProps? */
            if(sourceProps.get(propName) == null){
                /* Add a set (creation) event to the result */
                result.put(propName, targetPropVal);
            }
        }        
        return result;
    }
    
    public static boolean areContentsEqual(FSRevisionNode revNode1, FSRevisionNode revNode2) {
        return areRepresentationsEqual(revNode1, revNode2, false);
    }

    public static boolean arePropertiesEqual(FSRevisionNode revNode1, FSRevisionNode revNode2) {
        return areRepresentationsEqual(revNode1, revNode2, true);
    }

    private static boolean areRepresentationsEqual(FSRevisionNode revNode1, FSRevisionNode revNode2, boolean forProperties) {
        if(revNode1 == revNode2){
            return true;
        }else if(revNode1 == null || revNode2 == null){
            return false;
        }
        /* If forProperties is true - compares property keys.
         * Otherwise compares contents keys. 
         */
        return compareRepresentations(forProperties ? revNode1.getPropsRepresentation() : revNode1.getTextRepresentation(), forProperties ? revNode2.getPropsRepresentation() : revNode2.getTextRepresentation());
    }
    
    private static boolean compareRepresentations(FSRepresentation r1, FSRepresentation r2){
        if(r1 == r2){
            return true;
        }else if(r1 == null){
            return false;
        }
        return r1.equals(r2);
    }
    
    public static File getDigestFileFromRepositoryPath(String repositoryPath, File reposRootDir) throws SVNException {
        String digestPath = getDigestFromRepositoryPath(repositoryPath);
        return new File(getLockDigestSubdirectory(digestPath, reposRootDir), digestPath);
    }
    
    public static File getLockDigestSubdirectory(String digestPath, File reposRootDir){
        return new File(FSRepositoryUtil.getDBLocksDir(reposRootDir), digestPath.substring(0, FSConstants.DIGEST_SUBDIR_LEN -1));
    }
    
    public static String getDigestFromRepositoryPath(String repositoryPath) throws SVNException {
        MessageDigest digestFromPath = null;
        try {
            digestFromPath = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException nsae) {
            SVNErrorManager.error("svn: Can't get digest: " + nsae.getMessage());
        }
        digestFromPath.update(repositoryPath.getBytes());
        return SVNFileUtil.toHexDigest(digestFromPath); 
    }
    
    public static void setRevisionProperty(File reposRootDir, long revision, String propertyName, String propertyNewValue, String propertyOldValue, String reposPath, String userName, String action) throws SVNException {
        FSHooks.runPreRevPropChangeHook(reposRootDir, propertyName, propertyNewValue, reposPath, userName, revision, action);
        SVNProperties revProps = new SVNProperties(getRevisionPropertiesFile(reposRootDir, revision), null);
        revProps.setPropertyValue(propertyName, propertyNewValue);
        FSHooks.runPostRevPropChangeHook(reposRootDir, propertyName, propertyOldValue, reposPath, userName, revision, action);
    }
    
    public static Map getMetaProps(File reposRootDir, long revision, FSRepository repository) throws SVNException {
        Map metaProps = new HashMap();
        Map revProps = null;
        revProps = getRevisionProperties(reposRootDir, revision);
        String author = (String) revProps.get(SVNRevisionProperty.AUTHOR);
        String date = (String) revProps.get(SVNRevisionProperty.DATE);
        String uuid = repository.getRepositoryUUID();
        String rev = String.valueOf(revision);

        metaProps.put(SVNProperty.LAST_AUTHOR, author);
        metaProps.put(SVNProperty.COMMITTED_DATE, date);
        metaProps.put(SVNProperty.COMMITTED_REVISION, rev);
        metaProps.put(SVNProperty.UUID, uuid);
        return metaProps;
    }
    
    public static Map getRevisionProperties(File reposRootDir, long revision) throws SVNException {
        /*
        Map allProps = new HashMap();
        String author = getRevisionProperty(reposRootDir, revision, SVNRevisionProperty.AUTHOR);
        String date = getRevisionProperty(reposRootDir, revision, SVNRevisionProperty.DATE);
        String log = getRevisionProperty(reposRootDir, revision, SVNRevisionProperty.LOG);
        */
        File revPropFile = getRevisionPropertiesFile(reposRootDir, revision);
        SVNProperties revProps = new SVNProperties(revPropFile, null);
        return revProps.asMap();
        /*
        allProps.put(SVNRevisionProperty.AUTHOR, author);
        allProps.put(SVNRevisionProperty.DATE, date);
        allProps.put(SVNRevisionProperty.LOG, log);

        return allProps;
        */
    }

    public static String getRevisionProperty(File reposRootDir, long revision, String revPropName) throws SVNException {
        File revPropFile = getRevisionPropertiesFile(reposRootDir, revision);
        SVNProperties revProps = new SVNProperties(revPropFile, null);
        return revProps.getPropertyValue(revPropName);
    }

    public static String getRepositoryUUID(File reposRootDir) throws SVNException {
        File uuidFile = getRepositoryUUIDFile(reposRootDir);
        String uuidLine = FSReader.readSingleLine(uuidFile);

        if (uuidLine == null) {
            SVNErrorManager.error("svn: Can't read file '" + uuidFile.getAbsolutePath() + "': End of file found");
        }

        if (uuidLine.length() > FSConstants.SVN_UUID_FILE_MAX_LENGTH) {
            SVNErrorManager.error("svn: Can't read length line in file '" + uuidFile.getAbsolutePath() + "'");
        }
        return uuidLine;
    }
    
    public static File findRepositoryRoot(File path) throws SVNException, IOException {
        if (path == null) {
            path = new File("");
        }
        File rootPath = path;
        while (!isRepositoryRoot(rootPath)) {
            rootPath = rootPath.getParentFile();//SVNPathUtil.removeTail(rootPath);
            if (rootPath == null) {
                SVNErrorManager.error("can't find a repository root at path '" + path + "'");
            }
        }
        return rootPath.getCanonicalFile();
    }

    public static boolean isRepositoryRoot(File candidatePath) {
        File formatFile = new File(candidatePath, FSConstants.SVN_REPOS_FORMAT_FILE);
        SVNFileType fileType = SVNFileType.getType(formatFile);
        if (fileType != SVNFileType.FILE) {
            return false;
        }
        File dbFile = new File(candidatePath, FSConstants.SVN_REPOS_DB_DIR);
        fileType = SVNFileType.getType(dbFile);
        if (fileType != SVNFileType.DIRECTORY && fileType != SVNFileType.SYMLINK) {
            return false;
        }
        return true;
    }

    public static void checkRepositoryFormat(File reposRootDir) throws SVNException {
        int formatNumber = getFormat(getRepositoryFormatFile(reposRootDir), true, -1);
        if (formatNumber != FSConstants.SVN_REPOS_FORMAT_NUMBER) {
            SVNErrorManager.error("svn: Expected format '" + FSConstants.SVN_REPOS_FORMAT_NUMBER + "' of repository; found format '" + formatNumber + "'");
        }
    }
    
    public static void checkFSFormat(File reposRootDir) throws SVNException {
        int formatNumber = -1;
        formatNumber = getFormat(getFSFormatFile(reposRootDir), false, FSConstants.SVN_FS_FORMAT_NUMBER);

        if (formatNumber != FSConstants.SVN_FS_FORMAT_NUMBER) {
            SVNErrorManager.error("svn: Expected FS format '" + FSConstants.SVN_FS_FORMAT_NUMBER + "'; found format '" + formatNumber + "'");
        }
    }

    public static int getFormat(File formatFile, boolean formatFileMustExist, int defaultValue) throws SVNException {
        if(!formatFile.exists() && !formatFileMustExist){
            return defaultValue;
        }
        
        String firstLine = FSReader.readSingleLine(formatFile);

        if (firstLine == null) {
            SVNErrorManager.error("svn: Can't read file '" + formatFile.getAbsolutePath() + "': End of file found");
        }

        // checking for non-digits
        for (int i = 0; i < firstLine.length(); i++) {
            if (!Character.isDigit(firstLine.charAt(i))) {
                SVNErrorManager.error("svn: First line of '" + formatFile.getAbsolutePath() + "' contains non-digit");
            }
        }
        return Integer.parseInt(firstLine);
    }
    
    public static void checkFSType(File reposRootDir) throws SVNException {
        File fsTypeFile = getFSTypeFile(reposRootDir);

        String fsType = FSReader.readSingleLine(fsTypeFile);

        if (fsType == null) {
            SVNErrorManager.error("svn: Can't read file '" + fsTypeFile.getAbsolutePath() + "': End of file found");
        }

        if (!fsType.equals(FSConstants.SVN_REPOS_FSFS_FORMAT)) {
            SVNErrorManager.error("svn: Unknown FS type '" + fsType + "'");
        }
    }
    
    public static File getFSCurrentFile(File reposRootDir) {
        return new File(getRepositoryDBDir(reposRootDir), FSConstants.SVN_REPOS_DB_CURRENT_FILE);
    }

    public static File getDBLockFile(File reposRootDir) {
        return new File(getLocksDir(reposRootDir), FSConstants.SVN_REPOS_DB_LOCKFILE);
    }

    public static File getLocksDir(File reposRootDir) {
        return new File(reposRootDir, FSConstants.SVN_REPOS_LOCKS_DIR);
    }

    public static File getDBLocksDir(File reposRootDir) {
        return new File(getRepositoryDBDir(reposRootDir), FSConstants.SVN_REPOS_LOCKS_DIR);
    }

    public static File getRepositoryUUIDFile(File reposRootDir) {
        return new File(getRepositoryDBDir(reposRootDir), FSConstants.SVN_REPOS_UUID_FILE);
    }

    public static File getFSTypeFile(File reposRootDir) {
        return new File(getRepositoryDBDir(reposRootDir), FSConstants.SVN_REPOS_FS_TYPE_FILE);
    }
    
    public static File getRevisionPropertiesFile(File reposRootDir, long revision) {
        return new File(getRevisionPropertiesDir(reposRootDir), String.valueOf(revision));
    }

    public static File getRevisionPropertiesDir(File reposRootDir) {
        return new File(getRepositoryDBDir(reposRootDir), FSConstants.SVN_REPOS_REVPROPS_DIR);
    }

    
    public static File getRevisionsDir(File reposRootDir) {
        return new File(getRepositoryDBDir(reposRootDir), FSConstants.SVN_REPOS_REVS_DIR);
    }

    public static File getRevisionFile(File reposRootDir, long revision) throws SVNException {
        File revFile = new File(getRevisionsDir(reposRootDir), String.valueOf(revision));
        if (!revFile.exists()) {
            SVNErrorManager.error("svn: No such revision " + revision);
        }
        return revFile;
    }

    public static File getRepositoryFormatFile(File reposRootDir) {
        return new File(reposRootDir, FSConstants.SVN_REPOS_FORMAT_FILE);
    }

    public static File getFSFormatFile(File reposRootDir) {
        return new File(getRepositoryDBDir(reposRootDir), FSConstants.SVN_REPOS_FS_FORMAT_FILE);
    }

    public static File getRepositoryDBDir(File reposRootDir) {
        return new File(reposRootDir, FSConstants.SVN_REPOS_DB_DIR);
    }
    
}
