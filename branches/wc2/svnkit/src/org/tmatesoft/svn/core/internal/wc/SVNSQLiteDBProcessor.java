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
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.TreeMap;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNMergeRange;
import org.tmatesoft.svn.core.SVNMergeRangeList;
import org.tmatesoft.svn.core.internal.io.fs.FSID;
import org.tmatesoft.svn.core.wc.SVNRevision;


/**
 * @version 1.1.2
 * @author  TMate Software Ltd.
 */
public class SVNSQLiteDBProcessor implements ISVNDBProcessor {
    protected static final int MERGE_INFO_INDEX_SCHEMA_FORMAT = 1;
    protected static final String MERGEINFO_DB_NAME = "mergeinfo.db";
    private static boolean OUR_HAS_DRIVER;
    
    static {
        try {
            Class.forName("org.sqlite.JDBC");
            OUR_HAS_DRIVER = true;
        } catch (ClassNotFoundException e) {
            OUR_HAS_DRIVER = false;
        }
    }
    
    private static String[] CREATE_TABLES_COMMANDS = { 
        "PRAGMA auto_vacuum = 1;",
        "CREATE TABLE mergeinfo (revision INTEGER NOT NULL, mergedfrom TEXT NOT " +
        "NULL, mergedto TEXT NOT NULL, mergedrevstart INTEGER NOT NULL, " +
        "mergedrevend INTEGER NOT NULL, inheritable INTEGER NOT NULL);",
        "CREATE INDEX mi_mergedfrom_idx ON mergeinfo (mergedfrom);",
        "CREATE INDEX mi_mergedto_idx ON mergeinfo (mergedto);",
        "CREATE INDEX mi_revision_idx ON mergeinfo (revision);",
        "CREATE TABLE mergeinfo_changed (revision INTEGER NOT NULL, path TEXT " +
        "NOT NULL);",
        "CREATE UNIQUE INDEX mi_c_revpath_idx ON mergeinfo_changed (revision, path);",
        "CREATE INDEX mi_c_path_idx ON mergeinfo_changed (path);",
        "CREATE INDEX mi_c_revision_idx ON mergeinfo_changed (revision);", 
        "CREATE TABLE node_origins (node_id TEXT NOT NULL, node_rev_id TEXT NOT NULL);",
        "CREATE UNIQUE INDEX no_ni_idx ON node_origins (node_id);",
        "PRAGMA user_version = " + MERGE_INFO_INDEX_SCHEMA_FORMAT + ";"//TODO: correct and remove
    };
    
    private File myDBDirectory;
    private File myDBFile;
    private String myDBPath; 
    private Connection myConnection;
    private PreparedStatement mySinglePathSelectFromMergeInfoChangedStatement;
    private PreparedStatement mySelectMergeInfoStatement;
    private PreparedStatement myPathLikeSelectFromMergeInfoChangedStatement;
    private PreparedStatement myInsertIntoMergeInfoTableStatement;
    private PreparedStatement myInsertIntoMergeInfoChangedTableStatement;
    private PreparedStatement mySelectNodeRevIDStatement;
    private PreparedStatement myInsertIntoNodeOriginsTableStatement;
    
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
                dispose();
                myConnection.close();
                myConnection = null;
            } catch (SQLException sqle) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_SQLITE_ERROR, sqle.getLocalizedMessage());
                SVNErrorManager.error(err, sqle);
            }
        }
    }

    public long getMaxRevisionForPathFromMergeInfoChangedTable(String path, long upperRevision) throws SVNException {
        PreparedStatement statement = createSinglePathSelectFromMergeInfoChangedStatement();
        long maxRev = 0;
        try {
            statement.setString(1, path);
            statement.setLong(2, upperRevision);
            ResultSet result = statement.executeQuery();
            if (result.next()) {
                maxRev = result.getLong(1);
            }
        } catch (SQLException sqle) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_SQLITE_ERROR, sqle.getLocalizedMessage());
            SVNErrorManager.error(err, sqle);
        } 
        return maxRev;
    }

    public Map parseMergeInfoFromDB(String path, long lastMergedRevision) throws SVNException {
        Map result = null;
        PreparedStatement statement = createSelectMergeInfoStatement();
        try {
            statement.setString(1, path);
            statement.setLong(2, lastMergedRevision);
            ResultSet rows = statement.executeQuery();
            if (!rows.isBeforeFirst()) {
                return result;
            } 

            result = new TreeMap();
            String lastMergedFrom = null;
            String mergedFrom = null;
            Collection ranges = new LinkedList(); 
            while(rows.next()) {
                mergedFrom = rows.getString("mergedfrom");
                long startRev = rows.getLong("mergedrevstart");
                long endRev = rows.getLong("mergedrevend");
                boolean isInheritable = rows.getInt("inheritable") != 0;
                if (lastMergedFrom != null && !lastMergedFrom.equals(mergedFrom)) {
                    SVNMergeRange[] rangesArray = (SVNMergeRange[]) ranges.toArray(new SVNMergeRange[ranges.size()]);
                    Arrays.sort(rangesArray);
                    result.put(lastMergedFrom, new SVNMergeRangeList(rangesArray));
                    ranges.clear();
                }
                if (SVNRevision.isValidRevisionNumber(startRev) && 
                    SVNRevision.isValidRevisionNumber(endRev)) {
                    ranges.add(new SVNMergeRange(startRev, endRev, isInheritable));
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

    public Map getMergeInfoForChildren(String parentPath, long revision, Map parentMergeInfo, 
                                       ISVNMergeInfoFilter filter) throws SVNException {
        parentPath = "/".equals(parentPath) ? "" : parentPath;
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
                    boolean omitMergeInfo = filter != null && filter.omitMergeInfo(path, 
                                                                                   srcsToRangeLists); 
                    if (!omitMergeInfo) {
                        parentMergeInfo = SVNMergeInfoManager.mergeMergeInfos(parentMergeInfo, srcsToRangeLists);
                    }
                }
            }
        } catch (SQLException sqle) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_SQLITE_ERROR, sqle.getLocalizedMessage());
            SVNErrorManager.error(err, sqle);
        }
        return parentMergeInfo;
    }

    public void beginTransaction() throws SVNException {
        Connection connection = getConnection();
        try {
            Statement stmt = connection.createStatement();
            stmt.execute("BEGIN TRANSACTION;");
            stmt.close();
        } catch (SQLException sqle) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_SQLITE_ERROR, sqle.getLocalizedMessage());
            SVNErrorManager.error(err, sqle);
        }
    }

    public void commitTransaction() throws SVNException {
        Connection connection = getConnection();
        try {
            dispose();
            Statement stmt = connection.createStatement();
            stmt.execute("COMMIT TRANSACTION;");
            stmt.close();
        } catch (SQLException sqle) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_SQLITE_ERROR, sqle.getLocalizedMessage());
            SVNErrorManager.error(err, sqle);
        }
    }

    public void cleanUpFailedTransactionsInfo(long revision) throws SVNException {
        Connection connection = getConnection();
        try {
            Statement stmt = connection.createStatement();
            stmt.executeUpdate("DELETE FROM mergeinfo_changed WHERE revision = " + revision + ";");
            stmt.executeUpdate("DELETE FROM mergeinfo WHERE revision = " + revision + ";");
            stmt.close();
        } catch (SQLException sqle) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_SQLITE_ERROR, sqle.getLocalizedMessage());
            SVNErrorManager.error(err, sqle);
        }
    }

    public void insertMergeInfo(long revision, String mergedFrom, String mergedTo, 
                                SVNMergeRange[] ranges) throws SVNException {
        PreparedStatement insertIntoMergeInfoTableStmt = createInsertIntoMergeInfoTableStatement();
        try {
            insertIntoMergeInfoTableStmt.setLong(1, revision);
            insertIntoMergeInfoTableStmt.setString(2, mergedFrom);
            insertIntoMergeInfoTableStmt.setString(3, mergedTo);
            for (int i = 0; i < ranges.length; i++) {
                SVNMergeRange range = ranges[i];
                insertIntoMergeInfoTableStmt.setLong(4, range.getStartRevision());
                insertIntoMergeInfoTableStmt.setLong(5, range.getEndRevision());
                insertIntoMergeInfoTableStmt.setInt(6, range.isInheritable() ? 1 : 0);
                insertIntoMergeInfoTableStmt.execute();
            }
        } catch (SQLException sqle) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_SQLITE_ERROR, 
                                                         sqle.getLocalizedMessage());
            SVNErrorManager.error(err, sqle);
        }
    }

    public void updateMergeInfoChanges(long newRevision, String path) throws SVNException {
        PreparedStatement insertIntoMergeInfoChangedTableStmt = 
            createInsertIntoMergeInfoChangedTableStatement(); 
        try {
            insertIntoMergeInfoChangedTableStmt.setLong(1, newRevision);
            insertIntoMergeInfoChangedTableStmt.setString(2, path);
            insertIntoMergeInfoChangedTableStmt.execute();
        } catch (SQLException sqle) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_SQLITE_ERROR, 
                                                         sqle.getLocalizedMessage());
            SVNErrorManager.error(err, sqle);
        }
    }

    public String getNodeOrigin(String nodeID) throws SVNException {
        String nodeRevID = null;
        PreparedStatement selectNodeRevIDStmt = createSelectNodeRevIDStatement();
        try {
            selectNodeRevIDStmt.setString(1, nodeID);
            ResultSet result = selectNodeRevIDStmt.executeQuery();
            if (result.next()) {
                nodeRevID = result.getString(1);
            }
        } catch (SQLException sqle) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_SQLITE_ERROR, 
                    sqle.getLocalizedMessage());
            SVNErrorManager.error(err, sqle);
        }
        return nodeRevID;
    }
    
    public void setNodeOrigins(Map nodeOrigins) throws SVNException {
        beginTransaction();
        for (Iterator nodeOriginEntries = nodeOrigins.entrySet().iterator(); nodeOriginEntries.hasNext();) {
            Map.Entry nodeOriginEntry = (Map.Entry) nodeOriginEntries.next();
            String nodeID = (String) nodeOriginEntry.getKey();
            FSID nodeRevisionID = (FSID) nodeOriginEntry.getValue();
            setNodeOrigin(nodeID, nodeRevisionID.toString());
        }
        commitTransaction();
    }

    private void setNodeOrigin(String nodeID, String nodeRevID) throws SVNException {
        String oldNodeRevID = getNodeOrigin(nodeID);
        if (oldNodeRevID != null) {
            if (oldNodeRevID.equals(nodeRevID)) {
                return;
            } 
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_CORRUPT, 
                    "Node origin for ''{0}'' exists with a different value ({1}) than what we were about to store ({2})", 
                    new Object[] { nodeID, oldNodeRevID, nodeRevID });
            SVNErrorManager.error(err);
        }
        
        PreparedStatement stmt = createInsertIntoNodeOriginsTableStatement();
        try {
            stmt.setString(1, nodeID);
            stmt.setString(2, nodeRevID);
            stmt.execute();
        } catch (SQLException sqle) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_SQLITE_ERROR, 
                    sqle.getLocalizedMessage());
            SVNErrorManager.error(err, sqle);
        }
    }
    
    private void dispose() throws SVNException {
        try {
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
            if (myInsertIntoMergeInfoTableStatement != null) {
                myInsertIntoMergeInfoTableStatement.close();
                myInsertIntoMergeInfoTableStatement = null;
            }
            if (mySelectNodeRevIDStatement != null) {
                mySelectNodeRevIDStatement.close();
                mySelectNodeRevIDStatement = null;
            }
            if (myInsertIntoNodeOriginsTableStatement != null) {
                myInsertIntoNodeOriginsTableStatement.close();
                myInsertIntoNodeOriginsTableStatement = null;
            }
        } catch (SQLException sqle) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_SQLITE_ERROR, sqle.getLocalizedMessage());
            SVNErrorManager.error(err, sqle);
        }
    }
    
    private void createMergeInfoTables() throws SVNException {
        Connection connection = getConnection();
        try {
            Statement stmt = connection.createStatement();
            for (int i = 0; i < CREATE_TABLES_COMMANDS.length; i++) {
                stmt.addBatch(CREATE_TABLES_COMMANDS[i]);
            }
            stmt.executeBatch();
            stmt.close();
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
        Connection connection = getConnection();
        Statement stmt = null;
        try {
            stmt = connection.createStatement();
            ResultSet result = stmt.executeQuery("PRAGMA user_version;");
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
        } finally {
            if (stmt != null) {
                try {
                    stmt.close();
                } catch (SQLException sqle) {
                    //
                }
            }
        }
    }
    
    private PreparedStatement createInsertIntoMergeInfoTableStatement() throws SVNException {
        if (myInsertIntoMergeInfoTableStatement == null) {
            Connection connection = getConnection();
            try {
                myInsertIntoMergeInfoTableStatement = connection.prepareStatement("INSERT INTO mergeinfo (revision, mergedfrom, mergedto, mergedrevstart, mergedrevend, inheritable) VALUES (?, ?, ?, ?, ?, ?);");
            } catch (SQLException sqle) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_SQLITE_ERROR, sqle.getLocalizedMessage());
                SVNErrorManager.error(err, sqle);
            }
        }
        return myInsertIntoMergeInfoTableStatement;
    }

    private PreparedStatement createInsertIntoMergeInfoChangedTableStatement() throws SVNException {
        if (myInsertIntoMergeInfoChangedTableStatement == null) {
            Connection connection = getConnection();
            try {
                myInsertIntoMergeInfoChangedTableStatement = connection.prepareStatement("INSERT INTO mergeinfo_changed (revision, path) VALUES (?, ?);");
            } catch (SQLException sqle) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_SQLITE_ERROR, sqle.getLocalizedMessage());
                SVNErrorManager.error(err, sqle);
            }
        }
        return myInsertIntoMergeInfoChangedTableStatement;
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
                mySelectMergeInfoStatement = connection.prepareStatement("SELECT mergedfrom, mergedrevstart, mergedrevend, inheritable FROM mergeinfo WHERE mergedto = ? AND revision = ? ORDER BY mergedfrom, mergedrevstart;");
            } catch (SQLException sqle) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_SQLITE_ERROR, sqle.getLocalizedMessage());
                SVNErrorManager.error(err, sqle);
            }
        }
        return mySelectMergeInfoStatement;
    }

    private PreparedStatement createSelectNodeRevIDStatement() throws SVNException {
        if (mySelectNodeRevIDStatement == null) {
            Connection connection = getConnection();
            try {
                mySelectNodeRevIDStatement = connection.prepareStatement("SELECT node_rev_id FROM node_origins WHERE node_id = ?;");
            } catch (SQLException sqle) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_SQLITE_ERROR, 
                        sqle.getLocalizedMessage());
                SVNErrorManager.error(err, sqle);
            }
        }
        return mySelectNodeRevIDStatement;
    }

    private PreparedStatement createInsertIntoNodeOriginsTableStatement() throws SVNException {
        if (myInsertIntoNodeOriginsTableStatement == null) {
            Connection connection = getConnection();
            try {
                myInsertIntoNodeOriginsTableStatement = connection.prepareStatement("INSERT INTO node_origins (node_id, node_rev_id) VALUES (?, ?);");
            } catch (SQLException sqle) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_SQLITE_ERROR, 
                        sqle.getLocalizedMessage());
                SVNErrorManager.error(err, sqle);
            }
        }
        return myInsertIntoNodeOriginsTableStatement;
    }
    
    private Connection getConnection() throws SVNException {
        if (!OUR_HAS_DRIVER) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_SQLITE_ERROR, "No sqlite driver found");
            SVNErrorManager.error(err);
        }
        if (myConnection == null) {
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