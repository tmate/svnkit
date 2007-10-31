/*
 * ====================================================================
 * Copyright (c) 2004-2007 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */

package org.tmatesoft.svn.core.internal.io.svn;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.delta.SVNDeltaReader;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.io.ISVNEditor;

/**
 * @author TMate Software Ltd.
 * @version 1.1.1
 */
public class SVNEditModeReader {

    private static final Map COMMANDS_MAP = new HashMap();

    static {
        COMMANDS_MAP.put("target-rev", "r");
        COMMANDS_MAP.put("open-root", "(?r)s");
        COMMANDS_MAP.put("delete-entry", "s(?r)s");
        COMMANDS_MAP.put("add-dir", "sss(?sr)");
        COMMANDS_MAP.put("open-dir", "sss(?r)");
        COMMANDS_MAP.put("change-dir-prop", "ss(?s)");
        COMMANDS_MAP.put("close-dir", "s");
        COMMANDS_MAP.put("add-file", "sss(?sr)");
        COMMANDS_MAP.put("open-file", "sss(?r)");
        COMMANDS_MAP.put("apply-textdelta", "s(?c)");
        COMMANDS_MAP.put("textdelta-chunk", "sc");
        COMMANDS_MAP.put("textdelta-end", "s");
        COMMANDS_MAP.put("change-file-prop", "ss(?s)");
        COMMANDS_MAP.put("close-file", "s(?c)");
        COMMANDS_MAP.put("close-edit", "()");
        COMMANDS_MAP.put("abort-edit", "()");
        COMMANDS_MAP.put("finish-replay", "()");
        COMMANDS_MAP.put("absent-dir", "ss");
        COMMANDS_MAP.put("absent-file", "ss");
    }

    private SVNConnection myConnection;
    private ISVNEditor myEditor;
    private SVNDeltaReader myDeltaReader;
    private String myFilePath;

    private boolean myDone;
    private boolean myAborted;
    private Map myTokens;

    public SVNEditModeReader(SVNConnection connection, ISVNEditor editor) {
        myConnection = connection;
        myEditor = editor;
        myDeltaReader = new SVNDeltaReader();
        myDone = false;
        myAborted = false;
        myTokens = new HashMap();
    }

    private void storeToken(String token, boolean isFile) {
        myTokens.put(token, Boolean.valueOf(isFile));
    }

    private void lookupToken(String token, boolean isFile) throws SVNException {
        Boolean tokenType = (Boolean) myTokens.get(token);
        if (tokenType == null || tokenType != Boolean.valueOf(isFile)) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_SVN_MALFORMED_DATA, "Invalid file or dir token during edit");
            SVNErrorManager.error(err);
        }
    }

    private void removeToken(String token) {
        myTokens.remove(token);
    }

    private void processCommand(String commandName, List params) throws SVNException {
        if ("target-rev".equals(commandName)) {
            myEditor.targetRevision(SVNReader.getLong(params, 0));
        } else if ("open-root".equals(commandName)) {
            myEditor.openRoot(SVNReader.getLong(params, 0));
            String token = SVNReader.getString(params, 1);
            storeToken(token, false);
        } else if ("delete-entry".equals(commandName)) {
            lookupToken(SVNReader.getString(params, 2), false);
            String path = SVNPathUtil.canonicalizePath(SVNReader.getString(params, 0));
            myEditor.deleteEntry(path, SVNReader.getLong(params, 1));
        } else if ("add-dir".equals(commandName)) {
            lookupToken(SVNReader.getString(params, 1), false);
            String path = SVNPathUtil.canonicalizePath(SVNReader.getString(params, 0));
            String copyFromPath = SVNReader.getString(params, 3);
            if (copyFromPath != null) {
                copyFromPath = SVNPathUtil.canonicalizePath(copyFromPath);
            }
            myEditor.addDir(path, copyFromPath, SVNReader.getLong(params, 4));
            storeToken(SVNReader.getString(params, 2), false);
        } else if ("open-dir".equals(commandName)) {
            lookupToken(SVNReader.getString(params, 1), false);
            String path = SVNPathUtil.canonicalizePath(SVNReader.getString(params, 0));
            myEditor.openDir(path, SVNReader.getLong(params, 3));
            storeToken(SVNReader.getString(params, 2), false);
        } else if ("change-dir-prop".equals(commandName)) {
            lookupToken(SVNReader.getString(params, 0), false);
            myEditor.changeDirProperty(SVNReader.getString(params, 1), SVNReader.getString(params, 2));
        } else if ("close-dir".equals(commandName)) {
            String token = SVNReader.getString(params, 0);
            lookupToken(token, false);
            myEditor.closeDir();
            removeToken(token);
        } else if ("add-file".equals(commandName)) {
            lookupToken(SVNReader.getString(params, 1), false);
            String path = SVNPathUtil.canonicalizePath(SVNReader.getString(params, 0));
            String copyFromPath = SVNReader.getString(params, 3);
            if (copyFromPath != null) {
                copyFromPath = SVNPathUtil.canonicalizePath(copyFromPath);
            }
            storeToken(SVNReader.getString(params, 2), true);
            myEditor.addFile(path, copyFromPath, SVNReader.getLong(params, 4));
            myFilePath = path;
        } else if ("open-file".equals(commandName)) {
            lookupToken(SVNReader.getString(params, 1), false);
            String path = SVNPathUtil.canonicalizePath(SVNReader.getString(params, 0));
            storeToken(SVNReader.getString(params, 2), true);
            myEditor.openFile(SVNReader.getString(params, 0), SVNReader.getLong(params, 3));
            myFilePath = path;
        } else if ("change-file-prop".equals(commandName)) {
            lookupToken(SVNReader.getString(params, 0), true);
            myEditor.changeFileProperty(myFilePath, SVNReader.getString(params, 1), SVNReader.getString(params, 2));
        } else if ("close-file".equals(commandName)) {
            String token = SVNReader.getString(params, 0);
            lookupToken(token, true);
            myEditor.closeFile(myFilePath, SVNReader.getString(params, 1));
            removeToken(token);
        } else if ("apply-textdelta".equals(commandName)) {
            lookupToken(SVNReader.getString(params, 0), true);
            myEditor.applyTextDelta(myFilePath, SVNReader.getString(params, 1));
        } else if ("textdelta-chunk".equals(commandName)) {
            lookupToken(SVNReader.getString(params, 0), true);
            byte[] chunk = SVNReader.getBytes(params, 1);
            myDeltaReader.nextWindow(chunk, 0, chunk.length, myFilePath, myEditor);
        } else if ("textdelta-end".equals(commandName)) {
            // reset delta reader,
            // this should send empty window when diffstream contained only header.
            lookupToken(SVNReader.getString(params, 0), true);
            myDeltaReader.reset(myFilePath, myEditor);
            myEditor.textDeltaEnd(myFilePath);
        } else if ("close-edit".equals(commandName)) {
            myEditor.closeEdit();
            myDone = true;
            myAborted = false;
        } else if ("abort-edit".equals(commandName)) {
            myEditor.abortEdit();
            myDone = true;
            myAborted = true;
        } else if ("absent-dir".equals(commandName)) {
            lookupToken(SVNReader.getString(params, 1), false);
            myEditor.absentDir(SVNReader.getString(params, 0));
        } else if ("absent-file".equals(commandName)) {
            lookupToken(SVNReader.getString(params, 1), false);
            myEditor.absentFile(SVNReader.getString(params, 0));
        } else if ("finish-replay".equals(commandName)){
            myDone = true;
            if (myAborted){
                myAborted = false;
            }
        }
    }


    public void driveEditor(boolean doReplay) throws SVNException {
        while (!myDone) {
            try {
                List items = readTuple("wl", true);
                String commandName = SVNReader.getString(items, 0);
                boolean allowFinishReplay = doReplay && "finish-replay".equals(commandName);
                String template = (String) COMMANDS_MAP.get(commandName);
                if (template == null) {
                    if (!allowFinishReplay) {
                        SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_SVN_UNKNOWN_CMD));
                    }
                }
                List parameters = null;
                if (!allowFinishReplay) {
                    parameters = SVNReader.parseTuple(template, (Collection) items.get(1), null);
                }
                processCommand(commandName, parameters);
            } catch (SVNException e) {
                if (e.getErrorMessage().getErrorCode() == SVNErrorCode.RA_SVN_CMD_ERR) {
                    myAborted = true;
                    if (!myDone) {
                        myEditor.abortEdit();
                        break;
                    }
                }
                throw e;
            }
        }
        while (!myDone) {
            List items = readTuple("wl", true);
            String command = SVNReader.getString(items, 0);
            myDone = "abort-edit".equals(command);
        }
    }

    private List readTuple(String template, boolean readMalformedData) throws SVNException {
        if (myConnection == null) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_SVN_CONNECTION_CLOSED));
        }
        return myConnection.readTuple(template, readMalformedData);
    }
}
