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

package org.tmatesoft.svn.cli.command;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedList;
import java.util.Locale;

import org.tmatesoft.svn.cli.SVNArgument;
import org.tmatesoft.svn.cli.SVNCommand;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLock;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.util.SVNFormatUtil;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.wc.ISVNInfoHandler;
import org.tmatesoft.svn.core.wc.SVNChangeList;
import org.tmatesoft.svn.core.wc.SVNCompositePathList;
import org.tmatesoft.svn.core.wc.SVNInfo;
import org.tmatesoft.svn.core.wc.SVNPathList;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNWCClient;
import org.tmatesoft.svn.core.wc.xml.SVNXMLInfoHandler;
import org.tmatesoft.svn.core.wc.xml.SVNXMLSerializer;

/**
 * @version 1.1.1
 * @author  TMate Software Ltd.
 */
public class SVNInfoCommand extends SVNCommand implements ISVNInfoHandler {

    private static final DateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z (EE, d MMM yyyy)", Locale.getDefault());

    private PrintStream myOut;
    private File myBaseFile;

    public void run(InputStream in, PrintStream out, PrintStream err) throws SVNException {
        run(out, err);
    }

    public final void run(final PrintStream out, PrintStream err) throws SVNException {
        SVNRevision revision = SVNRevision.UNDEFINED;

        if (getCommandLine().hasArgument(SVNArgument.REVISION)) {
            revision = SVNRevision.parse((String) getCommandLine().getArgumentValue(SVNArgument.REVISION));
        }
        SVNWCClient wcClient = getClientManager().getWCClient();
        myOut = out;
        SVNXMLSerializer serializer = new SVNXMLSerializer(myOut);
        SVNXMLInfoHandler handler = new SVNXMLInfoHandler(serializer);
        if (getCommandLine().hasArgument(SVNArgument.XML) && !getCommandLine().hasArgument(SVNArgument.INCREMENTAL)) {
            handler.startDocument();
        }

        SVNDepth depth = SVNDepth.DEPTH_UNKNOWN;
        if (getCommandLine().hasArgument(SVNArgument.RECURSIVE)) {
            depth = SVNDepth.fromRecurse(true);
        }
        String depthStr = (String) getCommandLine().getArgumentValue(SVNArgument.DEPTH);
        if (depthStr != null) {
            depth = SVNDepth.fromString(depthStr);
        }
        if (depth == SVNDepth.DEPTH_UNKNOWN) {
            depth = SVNDepth.DEPTH_IMMEDIATES;
        }
        
        ISVNInfoHandler infoHandler = getCommandLine().hasArgument(SVNArgument.XML) ? handler : (ISVNInfoHandler) this;
        
        String changelistName = (String) getCommandLine().getArgumentValue(SVNArgument.CHANGELIST); 
        SVNChangeList changelist = null;
        if (changelistName != null) {
            changelist = SVNChangeList.create(changelistName, new File(".").getAbsoluteFile());
            changelist.setOptions(getClientManager().getOptions());
            changelist.setRepositoryPool(getClientManager().getRepositoryPool());
            if (changelist.getPaths() == null || changelist.getPathsCount() == 0) {
                SVNErrorMessage error = SVNErrorMessage.create(SVNErrorCode.CL_ARG_PARSING_ERROR, 
                                    "no such changelist ''{0}''", changelistName); 
                SVNErrorManager.error(error);
            }
        }

        File[] paths = getCommandLine().getPathCount() > 0 ? new File[getCommandLine().getPathCount()] : null;
        LinkedList pegRevisions = new LinkedList();
        for (int i = 0; i < getCommandLine().getPathCount(); i++) {
            paths[i] = new File(getCommandLine().getPathAt(i));
            pegRevisions.add(getCommandLine().getPathPegRevision(i));
        }
        
        SVNPathList pathList = SVNPathList.create(paths, (SVNRevision[]) pegRevisions.toArray(new SVNRevision[pegRevisions.size()]));
        SVNCompositePathList combinedPathList = SVNCompositePathList.create(pathList, changelist, false); 
        
        if (combinedPathList != null) {
            File[] combinedPaths = combinedPathList.getPaths();
            for (int i = 0; i < combinedPaths.length; i++) {
                myBaseFile = combinedPaths[i];
                SVNRevision pegRev = combinedPathList.getPegRevision(myBaseFile);
                handler.setTargetPath(myBaseFile);
                try {
                    wcClient.doInfo(myBaseFile, pegRev, revision, SVNDepth.recurseFromDepth(depth), infoHandler);
                } catch (SVNException e) {
                    if (e.getErrorMessage().getErrorCode() == SVNErrorCode.UNVERSIONED_RESOURCE) {
                        print(myBaseFile + ":  (Not a versioned resource)", myOut);
                        print("", myOut);
                        continue;
                    }
                    throw e;
                }
            }            
        }
            
        myBaseFile = null;
        for (int i = 0; i < getCommandLine().getURLCount(); i++) {
            String url = getCommandLine().getURL(i);
            SVNURL svnURL = SVNURL.parseURIEncoded(url);
            SVNRevision peg = getCommandLine().getPegRevision(i);
            try {
                wcClient.doInfo(svnURL, peg, revision, SVNDepth.recurseFromDepth(depth), infoHandler);
            } catch (SVNException e) {
                if (e.getErrorMessage().getErrorCode() == SVNErrorCode.RA_ILLEGAL_URL) {
                    print(svnURL + ":  (Not a valid URL)", myOut);
                    print("", myOut);
                    continue;
                }
                throw e;
            }
        }
        
        if (getCommandLine().hasArgument(SVNArgument.XML)&& !getCommandLine().hasArgument(SVNArgument.INCREMENTAL)) {
            handler.endDocument();
        }
        if (getCommandLine().hasArgument(SVNArgument.XML)) {
            try {
                serializer.flush();
            } catch (IOException e) {
            }
        }
    }

    private static void print(String str, PrintStream out) {
        out.println(str);
    }

    public void handleInfo(SVNInfo info) {
        if (info.getError() != null) {
            if (info.getError().getErrorCode() == SVNErrorCode.UNVERSIONED_RESOURCE) {
                print(info.getFile() + ":  (Not a versioned resource)", myOut);
                print("", myOut);
            } else {
                print(info.getError().getMessage(), myOut);
            }
            return;
        }
        if (!info.isRemote()) {
            print("Path: " + SVNFormatUtil.formatPath(info.getFile()), myOut);
        } else if (info.getPath() != null) {
            String path = info.getPath();
            if (myBaseFile != null) {
                File file = new File(myBaseFile, path);
                path = SVNFormatUtil.formatPath(file);
            } else {
                path = path.replace('/', File.separatorChar);
            }
            print("Path: " + path, myOut);
        }
        if (info.getKind() != SVNNodeKind.DIR) {
            if (info.isRemote()) {
                print("Name: " + SVNPathUtil.tail(info.getPath()), myOut);
            } else {
                print("Name: " + info.getFile().getName(), myOut);
            }
        }
        print("URL: " + info.getURL(), myOut);
        if (info.getRepositoryRootURL() != null) {
            print("Repository Root: " + info.getRepositoryRootURL(), myOut);
        }
        if (info.isRemote() && info.getRepositoryUUID() != null) {
            print("Repository UUID: " + info.getRepositoryUUID(), myOut);
        }
        if (info.getRevision() != null && info.getRevision().isValid()) {
            print("Revision: " + info.getRevision(), myOut);
        }
        if (info.getKind() == SVNNodeKind.DIR) {
            print("Node Kind: directory", myOut);
        } else if (info.getKind() == SVNNodeKind.FILE) {
            print("Node Kind: file", myOut);
        } else if (info.getKind() == SVNNodeKind.NONE) {
            print("Node Kind: none", myOut);
        } else {
            print("Node Kind: unknown", myOut);
        }
        
        if (!info.isRemote()) {
            if (info.getSchedule() == null) {
                print("Schedule: normal", myOut);
            } else {
                print("Schedule: " + info.getSchedule(), myOut);
            }
            if (info.getDepth() != null) {
                if (info.getDepth() != SVNDepth.DEPTH_UNKNOWN && info.getDepth() != SVNDepth.DEPTH_INFINITY) {
                    print("Depth: " + info.getDepth(), myOut);
                }
            }
            if (info.getCopyFromURL() != null) {
                print("Copied From URL: " + info.getCopyFromURL(), myOut);
            }
            if (info.getCopyFromRevision() != null && info.getCopyFromRevision().getNumber() >= 0) {
                print("Copied From Rev: " + info.getCopyFromRevision(), myOut);
            }
        }
        if (info.getAuthor() != null) {
            print("Last Changed Author: " + info.getAuthor(), myOut);
        }
        if (info.getCommittedRevision() != null && info.getCommittedRevision().getNumber() >= 0) {
            print("Last Changed Rev: " + info.getCommittedRevision(), myOut);
        }
        if (info.getCommittedDate() != null) {
            print("Last Changed Date: " + formatDate(info.getCommittedDate()), myOut);
        }
        if (!info.isRemote()) {
            if (info.getTextTime() != null) {
                print("Text Last Updated: " + formatDate(info.getTextTime()), myOut);
            }
            if (info.getPropTime() != null) {
                print("Properties Last Updated: " + formatDate(info.getPropTime()), myOut);
            }
            if (info.getChecksum() != null) {
                print("Checksum: " + info.getChecksum(), myOut);
            }
            if (info.getConflictOldFile() != null) {
                print("Conflict Previous Base File: " + info.getConflictOldFile().getName(), myOut);
            }
            if (info.getConflictWrkFile() != null) {
                print("Conflict Previous Working File: " + info.getConflictWrkFile().getName(), myOut);
            }
            if (info.getConflictNewFile() != null) {
                print("Conflict Current Base File: " + info.getConflictNewFile().getName(), myOut);
            }
            if (info.getPropConflictFile() != null) {
                print("Conflict Properties File: " + info.getPropConflictFile().getName(), myOut);
            }
        }
        if (info.getLock() != null) {
            SVNLock lock = info.getLock();
            print("Lock Token: " + lock.getID(), myOut);
            print("Lock Owner: " + lock.getOwner(), myOut);
            print("Lock Created: " + formatDate(lock.getCreationDate()), myOut);
            if (lock.getComment() != null) {
                myOut.print("Lock Comment ");
                int lineCount = getLinesCount(lock.getComment());
                if (lineCount == 1) {
                    myOut.print("(1 line)");
                } else {
                    myOut.print("(" + lineCount + " lines)");
                }
                myOut.print(":\n" + lock.getComment() + "\n");
            }
        }
        if (info.getChangelistName() != null) {
            print("Changelist: " + info.getChangelistName(), myOut);
        }
        println(myOut);
    }

    private static String formatDate(Date date) {
        return DATE_FORMAT.format(date);
    }
}
