/*
 * ====================================================================
 * Copyright (c) 2004-2010 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.wc.db;

import java.util.HashMap;
import java.util.Map;

import org.tmatesoft.sqljet.core.SqlJetException;
import org.tmatesoft.sqljet.core.schema.SqlJetConflictAction;
import org.tmatesoft.sqljet.core.table.ISqlJetCursor;
import org.tmatesoft.sqljet.core.table.ISqlJetTable;


/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public abstract class SVNAbstractDbStrategy {
    private long myLimit;

    protected SVNAbstractDbStrategy() {
        this(-1);
    }

    protected SVNAbstractDbStrategy(long limit) {
        myLimit = limit;
    }
    
    public long getCount(ISqlJetTable table) throws SqlJetException {
        ISqlJetCursor cursor = getCursor(table);
        try {
            return cursor.getRowCount();
        } finally {
            cursor.close();
        }
    }
    
    public Object runSelect(ISqlJetTable table) throws SqlJetException {
        final Map<SVNDbTableField, Object> result = new HashMap<SVNDbTableField, Object>();
        runSelect(table, new ISVNRecordHandler() {
            
            public void handleRecord(ISqlJetCursor recordCursor) throws SqlJetException {
                for(SVNDbTableField field : getFieldNames()) {
                    Object valObj = recordCursor.getValue(field.toString());
                    result.put(field, valObj);
                }   
            }
        });
        
        return result;
    }

    public void runSelect(ISqlJetTable table, ISVNRecordHandler handler) throws SqlJetException {
        ISqlJetCursor cursor = getCursor(table);
        try {
            long count = 0;
            if (!cursor.eof() && myLimit != 0) {
                do {
                    if (handler != null) {
                        handler.handleRecord(cursor);
                    }
                    
                    if (myLimit > 0 && ++count == myLimit) {
                        break;
                    }
                } while (cursor.next());
            } 
        } finally {
            cursor.close();
        }
    }
    
    public void runDelete(ISqlJetTable table, ISVNRecordHandler handler) throws SqlJetException {
        ISqlJetCursor cursor = getCursor(table);
        try {
            if (!cursor.eof()) {
                do {
                    if (handler != null) {
                        handler.handleRecord(cursor);
                    }
                    cursor.delete();
                } while (cursor.next());
            }
        } finally {
            cursor.close();
        }
    }
    
    public long runInsertByFieldNames(ISqlJetTable table, SqlJetConflictAction conflictAction, Map<SVNDbTableField, Object> fieldsToValues) throws SqlJetException {
        if (fieldsToValues == null || fieldsToValues.isEmpty()) {
            return -1;
        }
        
        Map<String, Object> convertedFieldsToValues = convertValuesMap(fieldsToValues);
        return table.insertByFieldNamesOr(conflictAction, convertedFieldsToValues);
    }

    public long runUpdate(ISqlJetTable table, Map<SVNDbTableField, Object> fieldsToValues) throws SqlJetException {
        long updatedRowsCount = 0;
        ISqlJetCursor cursor = getCursor(table);
        try {
            if (!cursor.eof()) {
                Map<String, Object> convertedFieldsToValues = convertValuesMap(fieldsToValues);
                do {
                    cursor.updateByFieldNames(convertedFieldsToValues);
                    updatedRowsCount++;
                } while (cursor.next());
            }
        } finally {
            cursor.close();
        }
        return updatedRowsCount;
    }
    
    public long runInsert(ISqlJetTable table, SqlJetConflictAction conflictAction, Object... values) throws SqlJetException {
        return table.insertOr(conflictAction, values);
    }

    private Map<String, Object> convertValuesMap(Map<SVNDbTableField, Object> fieldsToValues) {
        Map<String, Object> result = new HashMap<String, Object>();
        for (SVNDbTableField field : fieldsToValues.keySet()) {
            result.put(field.toString(), fieldsToValues.get(field));
        }
        return result;
    }
    
    protected abstract SVNDbTableField[] getFieldNames(); 
    
    protected abstract ISqlJetCursor getCursor(ISqlJetTable table) throws SqlJetException;
    
}