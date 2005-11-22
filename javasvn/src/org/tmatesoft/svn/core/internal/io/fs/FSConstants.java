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


/**
 * @version 1.0
 * @author  TMate Software Ltd.
 */
public class FSConstants {
//    public static String SVN_REPOS_README = "README.txt";
    public static final String SVN_REPOS_DB_DIR = "db";
    public static final String SVN_REPOS_DAV_DIR = "dav";
    public static final String SVN_REPOS_LOCKS_DIR = "locks";
    public static final String SVN_REPOS_CONF_DIR = "conf";
    public static final String SVN_REPOS_DB_LOCKFILE = "db.lock";
    public static final String SVN_REPOS_DB_LOGS_LOCKFILE = "db-logs.lock";
    public static final String SVN_REPOS_CONF_SVNSERVE_CONF_FILE = "svnserve.conf";
    public static final String SVN_REPOS_CONF_PASSWD_FILE = "passwd";
    public static final String SVN_REPOS_FSFS_FORMAT = "fsfs";
    public static final String SVN_REPOS_DB_CURRENT_FILE = "current";
    public static final String SVN_REPOS_FORMAT_FILE = "format";
    public static final int SVN_REPOS_FORMAT_NUMBER = 3;
    public static final String SVN_REPOS_FS_FORMAT_FILE = "format";
    public static final int SVN_FS_FORMAT_NUMBER = 1;
    public static final String SVN_REPOS_FS_TYPE_FILE = "fs-type";
    public static final String SVN_REPOS_UUID_FILE = "uuid";
    public static final String SVN_REPOS_REVPROPS_DIR = "revprops";
    public static final String SVN_REPOS_REVS_DIR = "revs";

    //the following are keys that appear in digest lock file
    public static final String PATH_LOCK_KEY = "path";
    public static final String CHILDREN_LOCK_KEY = "children";
    public static final String TOKEN_LOCK_KEY = "token";
    public static final String OWNER_LOCK_KEY = "owner";
    public static final String IS_DAV_COMMENT_LOCK_KEY = "is_dav_comment";
    public static final String CREATION_DATE_LOCK_KEY = "creation_date";
    public static final String EXPIRATION_DATE_LOCK_KEY = "expiration_date";
    public static final String COMMENT_LOCK_KEY = "comment";
    
    // uuid format - 36 symbols
    public static final int SVN_UUID_FILE_LENGTH = 36;
    // if > max svn 1.2 stops working
    public static final int SVN_UUID_FILE_MAX_LENGTH = SVN_UUID_FILE_LENGTH + 1;
    
    /* Number of characters from the head of a digest file name used to
     * calculate a subdirectory in which to drop that file. 
     */
    public static final int DIGEST_SUBDIR_LEN = 3;
    
    //invalid revision number, suppose it to be -1
    public static final int SVN_INVALID_REVNUM = -1;
}
