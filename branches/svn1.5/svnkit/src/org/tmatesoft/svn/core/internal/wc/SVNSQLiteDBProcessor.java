/*
 * ====================================================================
 * Copyright (c) 2004-2007 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.wc;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.Map;
import java.util.TreeMap;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNMergeRange;
import org.tmatesoft.svn.core.SVNMergeRangeList;
import org.tmatesoft.svn.core.wc.SVNRevision;


/**
 * @version 1.1.2
 * @author  TMate Software Ltd.
 */
public class SVNSQLiteDBProcessor implements ISVNDBProcessor {
    protected static final int MERGE_INFO_INDEX_SCHEMA_FORMAT = 1;
    protected static final String MERGEINFO_DB_NAME = "mergeinfo.db";

    private static String[] CREATE_TABLES_COMMANDS = { 
        "PRAGMA auto_vacuum = 1;",
        "CREATE TABLE mergeinfo (revision INTEGER NOT NULL, mergedfrom TEXT NOT " +
        "NULL, mergedto TEXT NOT NULL, mergedrevstart INTEGER NOT NULL, " +
        "mergedrevend INTEGER NOT NULL);",
        "CREATE INDEX mi_mergedfrom_idx ON mergeinfo (mergedfrom);",
        "CREATE INDEX mi_mergedto_idx ON mergeinfo (mergedto);",
        "CREATE INDEX mi_revision_idx ON mergeinfo (revision);",
        "CREATE TABLE mergeinfo_changed (revision INTEGER NOT NULL, path TEXT " +
        "NOT NULL);",
        "CREATE UNIQUE INDEX mi_c_revpath_idx ON mergeinfo_changed (revision, path);",
        "CREATE INDEX mi_c_path_idx ON mergeinfo_changed (path);",
        "CREATE INDEX mi_c_revision_idx ON mergeinfo_changed (revision);", 
        "PRAGMA user_version = " + MERGE_INFO_INDEX_SCHEMA_FORMAT + ";"
    };
    
    private File myDBDirectory;
    private File myDBFile;
    private String myDBPath; 
    private Connection myConnection;
    private PreparedStatement myUserVersionStatement;
    private PreparedStatement mySinglePathSelectFromMergeInfoChangedStatement;
    private PreparedStatement mySelectMergeInfoStatement;
    private PreparedStatement myPathLikeSelectFromMergeInfoChangedStatement;
    
    public void openDB(File dbDir) throws SVNException {
        if (myDBDirectory == null || !myDBDirectory.equals(dbDir)) {
            reset(dbDir);
        }
        
        try {
            checkFormat();
        } catch (SVNException svne) {
            if (svne.getErrorMessage().getErrorCode() == SVNErrorCode.FS_GENERAL) {
                createMergeInfoTables();
            } else {
                throw svne;
            }
        }
    }

    public void closeDB() throws SVNException {
        if (myConnection != null) {
            try {
                myConnection.close();
                if (myUserVersionStatement != null) {
                    myUserVersionStatement.close();
                    myUserVersionStatement = null;
                }
                if (mySinglePathSelectFromMergeInfoChangedStatement != null) {
                    mySinglePathSelectFromMergeInfoChangedStatement.close();
                    mySinglePathSelectFromMergeInfoChangedStatement = null;
                }
                if (myPathLikeSelectFromMergeInfoChangedStatement != null) {
                    myPathLikeSelectFromMergeInfoChangedStatement.close();
                    myPathLikeSelectFromMergeInfoChangedStatement = null;
                }
                if (mySelectMergeInfoStatement != null) {
                    mySelectMergeInfoStatement.close();
                    mySelectMergeInfoStatement = null;
                }
            } catch (SQLException sqle) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_SQLITE_ERROR, sqle.getLocalizedMessage());
                SVNErrorManager.error(err, sqle);
            }
        }
    }

    public long getMaxRevisionForPathFromMergeInfoChangedTable(String path, long upperRevision) throws SVNException {
        PreparedStatement statement = createSinglePathSelectFromMergeInfoChangedStatement();
        try {
            statement.setString(1, path);
            statement.setLong(2, upperRevision);
            ResultSet result = statement.executeQuery();
            if (result.next()) {
                return result.getLong(1);
            }
        } catch (SQLException sqle) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_SQLITE_ERROR, sqle.getLocalizedMessage());
            SVNErrorManager.error(err, sqle);
        }
        return 0;
    }

    public Map parseMergeInfoFromDB(String path, long lastMergedRevision) throws SVNException {
        Map result = new TreeMap();
        PreparedStatement statement = createSelectMergeInfoStatement();
        try {
            statement.setString(1, path);
            statement.setLong(2, lastMergedRevision);
            ResultSet rows = statement.executeQuery();
            if (!rows.isBeforeFirst()) {
                return result;
            } 
            
            String lastMergedFrom = null;
            String mergedFrom = null;
            Collection ranges = new LinkedList(); 
            while(rows.next()) {
                mergedFrom = rows.getString("mergedfrom");
                long startRev = rows.getLong("mergedrevstart");
                long endRev = rows.getLong("mergedrevend");
                if (lastMergedFrom != null && !lastMergedFrom.equals(mergedFrom)) {
                    SVNMergeRange[] rangesArray = (SVNMergeRange[]) ranges.toArray(new SVNMergeRange[ranges.size()]);
                    Arrays.sort(rangesArray);
                    result.put(lastMergedFrom, new SVNMergeRangeList(rangesArray));
                    ranges.clear();
                }
                if (SVNRevision.isValidRevisionNumber(startRev) 
                        && SVNRevision.isValidRevisionNumber(endRev)) {
                    ranges.add(new SVNMergeRange(startRev, endRev));
                }
                lastMergedFrom = mergedFrom;
            }
            SVNMergeRange[] rangesArray = (SVNMergeRange[]) ranges.toArray(new SVNMergeRange[ranges.size()]); 
            Arrays.sort(rangesArray);
            result.put(mergedFrom, new SVNMergeRangeList(rangesArray));
        } catch (SQLException sqle) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_SQLITE_ERROR, sqle.getLocalizedMessage());
            SVNErrorManager.error(err, sqle);
        }
        return result;
    }

    public Map getMergeInfoForChildren(String parentPath, long revision, Map parentMergeInfo) throws SVNException {
        parentMergeInfo = parentMergeInfo == null ? new TreeMap() : parentMergeInfo;
        PreparedStatement statement = createPathLikeSelectFromMergeInfoChangedStatement();
        try {
            statement.setString(1, parentPath + "/%");
            statement.setLong(2, revision);
            ResultSet result = statement.executeQuery();
            while (result.next()) {
                long lastMergedRevision = result.getLong(1);
                String path = result.getString(2);
                if (lastMergedRevision > 0) {
                    Map srcsToRangeLists = parseMergeInfoFromDB(path, lastMergedRevision);
                    SVNMergeInfoManager.mergeMergeInfos(parentMergeInfo, srcsToRangeLists);
                }
            }
        } catch (SQLException sqle) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_SQLITE_ERROR, sqle.getLocalizedMessage());
            SVNErrorManager.error(err, sqle);
        }
        return parentMergeInfo;
    }

    private void createMergeInfoTables() throws SVNException {
        Connection connection = getConnection();
        try {
            Statement stmt = connection.createStatement();
            for (int i = 0; i < CREATE_TABLES_COMMANDS.length; i++) {
                stmt.addBatch(CREATE_TABLES_COMMANDS[i]);
            }
            stmt.executeBatch();
        } catch (SQLException sqle) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_SQLITE_ERROR, sqle.getLocalizedMessage());
            SVNErrorManager.error(err, sqle);
        }
    }
    
    private void reset(File dbDir) {
        myDBDirectory = dbDir;
        myDBFile = new File(myDBDirectory, MERGEINFO_DB_NAME);
        myDBPath = myDBFile.getAbsolutePath().replace(File.separatorChar, '/');
    }
    
    private void checkFormat() throws SVNException {
        PreparedStatement userVersionStatement = createUserVersionStatement();
        try {
            ResultSet result = userVersionStatement.executeQuery();
            if (result.next()) {
                int schemaFormat = result.getInt(1);
                if (schemaFormat == MERGE_INFO_INDEX_SCHEMA_FORMAT) {
                    return;
                } else if (schemaFormat == 0) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_GENERAL, "Merge Tracking schema format not set");
                    SVNErrorManager.error(err);
                } else if (schemaFormat > MERGE_INFO_INDEX_SCHEMA_FORMAT) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_UNSUPPORTED_FORMAT, "Merge Tracking schema format ''{0,number,integer}'' not recognized", new Integer(schemaFormat));
                    SVNErrorManager.error(err);
                }
            } else {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_SQLITE_ERROR, "Error while getting the schema format");
                SVNErrorManager.error(err);
            }
        } catch (SQLException sqle) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_SQLITE_ERROR, sqle.getLocalizedMessage());
            SVNErrorManager.error(err, sqle);
        }
    }
    
    private PreparedStatement createUserVersionStatement() throws SVNException {
        if (myUserVersionStatement == null) {
            Connection connection = getConnection();
            try {
                myUserVersionStatement = connection.prepareStatement("PRAGMA user_version;");
            } catch (SQLException sqle) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_SQLITE_ERROR, sqle.getLocalizedMessage());
                SVNErrorManager.error(err, sqle);
            }
        }
        return myUserVersionStatement;
    }

    private PreparedStatement createPathLikeSelectFromMergeInfoChangedStatement() throws SVNException {
        if (myPathLikeSelectFromMergeInfoChangedStatement == null) {
            Connection connection = getConnection();
            try {
                myPathLikeSelectFromMergeInfoChangedStatement = connection.prepareStatement("SELECT MAX(revision), path FROM mergeinfo_changed WHERE path LIKE ? AND revision <= ?;");
            } catch (SQLException sqle) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_SQLITE_ERROR, sqle.getLocalizedMessage());
                SVNErrorManager.error(err, sqle);
            }
        }
        return myPathLikeSelectFromMergeInfoChangedStatement;
    }

    private PreparedStatement createSinglePathSelectFromMergeInfoChangedStatement() throws SVNException {
        if (mySinglePathSelectFromMergeInfoChangedStatement == null) {
            Connection connection = getConnection();
            try {
                mySinglePathSelectFromMergeInfoChangedStatement = connection.prepareStatement("SELECT MAX(revision) FROM mergeinfo_changed WHERE path = ? AND revision <= ?;");
            } catch (SQLException sqle) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_SQLITE_ERROR, sqle.getLocalizedMessage());
                SVNErrorManager.error(err, sqle);
            }
        }
        return mySinglePathSelectFromMergeInfoChangedStatement;
    }

    private PreparedStatement createSelectMergeInfoStatement() throws SVNException {
        if (mySelectMergeInfoStatement == null) {
            Connection connection = getConnection();
            try {
                mySelectMergeInfoStatement = connection.prepareStatement("SELECT mergedfrom, mergedrevstart, mergedrevend FROM mergeinfo WHERE mergedto = ? AND revision = ? ORDER BY mergedfrom;");
            } catch (SQLException sqle) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_SQLITE_ERROR, sqle.getLocalizedMessage());
                SVNErrorManager.error(err, sqle);
            }
        }
        return mySelectMergeInfoStatement;
    }

    private Connection getConnection() throws SVNException {
        if (myConnection == null) {
            try {
                Class.forName("org.sqlite.JDBC");
            } catch (ClassNotFoundException e) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_SQLITE_ERROR, e.getLocalizedMessage());
                SVNErrorManager.error(err, e);
            }

            try {
                myConnection = DriverManager.getConnection("jdbc:sqlite:" + myDBPath);
            } catch (SQLException sqle) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_SQLITE_ERROR, sqle.getLocalizedMessage());
                SVNErrorManager.error(err, sqle);
            }
        }
        return myConnection;
    }

}
