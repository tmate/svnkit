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
package org.tmatesoft.svn.cli2.command;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.tmatesoft.svn.cli2.SVNCommandTarget;
import org.tmatesoft.svn.cli2.SVNCommandUtil;
import org.tmatesoft.svn.core.ISVNLogEntryHandler;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLogEntry;
import org.tmatesoft.svn.core.SVNLogEntryPath;
import org.tmatesoft.svn.core.internal.util.SVNDate;
import org.tmatesoft.svn.core.internal.util.SVNEncodingUtil;
import org.tmatesoft.svn.core.internal.util.SVNFormatUtil;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.wc.SVNChangelistClient;
import org.tmatesoft.svn.core.wc.SVNLogClient;
import org.tmatesoft.svn.core.wc.SVNPathList;
import org.tmatesoft.svn.core.wc.SVNRevision;


/**
 * @version 1.1.2
 * @author  TMate Software Ltd.
 */
public class SVNLogCommand extends SVNXMLCommand implements ISVNLogEntryHandler {

    private static final String SEPARATOR = "------------------------------------------------------------------------\n";

    public SVNLogCommand() {
        super("log", null);
    }
    
    public boolean acceptsRevisionRange() {
        return true;
    }

    protected Collection createSupportedOptions() {
        Collection options = new LinkedList();
        options.add(SVNOption.REVISION);
        options.add(SVNOption.QUIET);
        options.add(SVNOption.VERBOSE);
        options.add(SVNOption.USE_MERGE_HISTORY);
        options.add(SVNOption.TARGETS);
        options.add(SVNOption.STOP_ON_COPY);
        options.add(SVNOption.INCREMENTAL);
        options.add(SVNOption.XML);
        options = SVNOption.addAuthOptions(options);
        options.add(SVNOption.CONFIG_DIR);
        options.add(SVNOption.LIMIT);
        options.add(SVNOption.CHANGELIST);
        return options;
    }

    public void run() throws SVNException {
        List targets = new ArrayList(); 
        if (getSVNEnvironment().getChangelist() != null) {
            SVNCommandTarget target = new SVNCommandTarget("");
            SVNChangelistClient changelistClient = getSVNEnvironment().getClientManager().getChangelistClient();
            changelistClient.getChangelist(target.getFile(), getSVNEnvironment().getChangelist(), targets);
            if (targets.isEmpty()) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CL_ARG_PARSING_ERROR, "no such changelist ''{0}''", getSVNEnvironment().getChangelist());
                SVNErrorManager.error(err);
            }
        }
        if (getSVNEnvironment().getTargets() != null) {
            targets.addAll(getSVNEnvironment().getTargets());
        }
        targets = getSVNEnvironment().combineTargets(targets);
        if (targets.isEmpty()) {
            targets.add("");
        }
        SVNCommandTarget target = new SVNCommandTarget((String) targets.get(0), true);
        
        SVNRevision start = getSVNEnvironment().getStartRevision();
        SVNRevision end = getSVNEnvironment().getEndRevision();
        if (start != SVNRevision.UNDEFINED && end == SVNRevision.UNDEFINED) {
            end = start;
        } else if (start == SVNRevision.UNDEFINED) {
            if (target.getPegRevision() == SVNRevision.UNDEFINED) {
                start = target.isURL() ? SVNRevision.HEAD : SVNRevision.BASE;
            } else {
                start = target.getPegRevision();
            }
            if (end == SVNRevision.UNDEFINED) {
                end = SVNRevision.create(0);
            }
        }
        
        if (!target.isURL()) {
            if (targets.size() > 1) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNSUPPORTED_FEATURE, 
                        "When specifying working copy paths, only one target may be given");
                SVNErrorManager.error(err);
            }
        } else {
            for(int i = 1; i < targets.size(); i++) {
                if (SVNCommandUtil.isURL((String) targets.get(i))) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNSUPPORTED_FEATURE, 
                        "Only relative paths can be specified after a URL");
                    SVNErrorManager.error(err);
                }
            }
        }
        SVNLogClient client = getSVNEnvironment().getClientManager().getLogClient();
        if (!getSVNEnvironment().isQuiet()) {
            client.setEventHandler(new SVNNotifyPrinter(getSVNEnvironment()));
        }
        
        if (getSVNEnvironment().isXML() && !getSVNEnvironment().isIncremental()) {
            printXMLHeader("log");
        }

        if (target.isFile()) {
            SVNPathList list = SVNPathList.create(new File[] {target.getFile()}, target.getPegRevision());
            client.doLog(list, start, end, 
                    getSVNEnvironment().isStopOnCopy(), 
                    getSVNEnvironment().isVerbose(), 
                    getSVNEnvironment().isUseMergeHistory(),
                    getSVNEnvironment().isQuiet(),
                    getSVNEnvironment().getLimit(), this);
        } else {
            targets.remove(0);
            String[] paths = (String[]) targets.toArray(new String[targets.size()]);
            client.doLog(target.getURL(), paths, target.getPegRevision(), start, end, 
                    getSVNEnvironment().isStopOnCopy(), 
                    getSVNEnvironment().isVerbose(),
                    getSVNEnvironment().isUseMergeHistory(),
                    getSVNEnvironment().isQuiet(),
                    getSVNEnvironment().getLimit(), this);
        }

        if (getSVNEnvironment().isXML() && !getSVNEnvironment().isIncremental()) {
            printXMLFooter("log");
        } else if (!getSVNEnvironment().isIncremental()) {
            getSVNEnvironment().getOut().print(SEPARATOR);
        }
    }

    public void handleLogEntry(SVNLogEntry logEntry) throws SVNException {
        if (!getSVNEnvironment().isXML()) {
            printLogEntry(logEntry);
        } else {
            printLogEntryXML(logEntry);
        }
    }
    
    private LinkedList myMergeStack;
    
    protected void printLogEntry(SVNLogEntry logEntry) {
        if (logEntry == null || (logEntry.getMessage() == null && logEntry.getRevision() == 0)) {
            return;
        }
        StringBuffer buffer = new StringBuffer();
        String author = logEntry.getAuthor() == null ? "(no author)" : logEntry.getAuthor();
        String date = logEntry.getDate() == null ? "(no date)" : SVNFormatUtil.formatHumanDate(logEntry.getDate(), getSVNEnvironment().getClientManager().getOptions());
        String message = logEntry.getMessage();
        if (!getSVNEnvironment().isQuiet() && message == null) {
            message = "";
        }
        buffer.append(SEPARATOR);
        buffer.append("r" + Long.toString(logEntry.getRevision()) + " | " + author + " | " + date);
        if (!getSVNEnvironment().isQuiet()) {
            int count = SVNCommandUtil.getLinesCount(message);
            buffer.append(" | " + count + (count == 1 ? " line" : " lines"));
        }
        buffer.append("\n");
        if (getSVNEnvironment().isVerbose() && logEntry.getChangedPaths() != null) {
            Map sortedPaths = new TreeMap(logEntry.getChangedPaths());
            buffer.append("Changed paths:\n");
            for (Iterator paths = sortedPaths.keySet().iterator(); paths.hasNext();) {
                String path = (String) paths.next();
                SVNLogEntryPath lPath = (SVNLogEntryPath) sortedPaths.get(path);
                buffer.append("   " + lPath.getType() + " " + path);
                if (lPath.getCopyPath() != null) {
                    buffer.append(" (from " + lPath.getCopyPath() + ":" + lPath.getCopyRevision() + ")");
                }
                buffer.append("\n");
            }
        }
        
        if (myMergeStack != null && !myMergeStack.isEmpty()) {
            buffer.append("Result of a merge from:");
            MergeFrame frame = (MergeFrame) myMergeStack.getLast();
            for (Iterator frames = myMergeStack.iterator(); frames.hasNext();) {
                MergeFrame outputFrame = (MergeFrame) frames.next();
                buffer.append(" r");
                buffer.append(outputFrame.myMergeRevision);
                if (frames.hasNext()) {
                    buffer.append(',');
                } else {
                    buffer.append('\n');
                }
            }
            frame.myNumberOfChildrenRemaining--;
        }
        
        if (!getSVNEnvironment().isQuiet()) {
            buffer.append("\n" + message + "\n");
        }
        
        if (logEntry.getNumberOfChildren() > 0) {
            MergeFrame frame = new MergeFrame();
            frame.myMergeRevision = logEntry.getRevision();
            frame.myNumberOfChildrenRemaining = logEntry.getNumberOfChildren();
            if (myMergeStack == null) {
                myMergeStack = new LinkedList();
            }
            myMergeStack.addLast(frame);
        } else {
            while(myMergeStack != null && !myMergeStack.isEmpty()) {
                MergeFrame frame = (MergeFrame) myMergeStack.getLast();
                if (frame.myNumberOfChildrenRemaining == 0) {
                    myMergeStack.removeLast();
                } else {
                    break;
                }
            }
        }
        getSVNEnvironment().getOut().print(buffer.toString());
    }
    
    protected void printLogEntryXML(SVNLogEntry logEntry) {
        if (logEntry.getRevision() == 0 && logEntry.getMessage() == null) {
            return;
        }
        StringBuffer buffer = new StringBuffer();
        buffer = openXMLTag("logentry", XML_STYLE_NORMAL, "revision", Long.toString(logEntry.getRevision()), buffer);
        buffer = openCDataTag("author", logEntry.getAuthor(), buffer);
        if (logEntry.getDate() != null && logEntry.getDate().getTime() != 0) {
            buffer = openCDataTag("date", ((SVNDate) logEntry.getDate()).format(), buffer);
        }
        if (logEntry.getChangedPaths() != null && !logEntry.getChangedPaths().isEmpty()) {
            buffer = openXMLTag("paths", XML_STYLE_NORMAL, null, buffer);
            for (Iterator paths = logEntry.getChangedPaths().keySet().iterator(); paths.hasNext();) {
                String key = (String) paths.next();
                SVNLogEntryPath path = (SVNLogEntryPath) logEntry.getChangedPaths().get(key);
                Map attrs = new LinkedHashMap();
                attrs.put("action", path.getType() + "");
                if (path.getCopyPath() != null) {
                    attrs.put("copyfrom-path", path.getCopyPath());
                    attrs.put("copyfrom-rev", Long.toString(path.getCopyRevision()));
                }
                buffer = openXMLTag("path", XML_STYLE_PROTECT_PCDATA, attrs, buffer);
                buffer.append(SVNEncodingUtil.xmlEncodeCDATA(path.getPath()));
                buffer = closeXMLTag("path", buffer);
            }
            buffer = closeXMLTag("paths", buffer);
        }
        
        if (!getSVNEnvironment().isQuiet()) {
            String message = logEntry.getMessage();
            message = message == null ? "" : message;
            buffer = openCDataTag("msg", message, buffer);
        }
        
        if (myMergeStack != null && !myMergeStack.isEmpty()) {
            MergeFrame frame = (MergeFrame) myMergeStack.getLast();
            frame.myNumberOfChildrenRemaining--;
        }
        
        if (logEntry.getNumberOfChildren() > 0) {
            MergeFrame frame = new MergeFrame();
            frame.myNumberOfChildrenRemaining = logEntry.getNumberOfChildren();
            if (myMergeStack == null) {
                myMergeStack = new LinkedList();
            }
            myMergeStack.addLast(frame);
        } else {
            while(myMergeStack != null && !myMergeStack.isEmpty()) {
                MergeFrame frame = (MergeFrame) myMergeStack.getLast();
                if (frame.myNumberOfChildrenRemaining == 0) {
                    buffer = closeXMLTag("logentry", buffer);
                    myMergeStack.removeLast();
                } else {
                    break;
                }
            }
            buffer = closeXMLTag("logentry", buffer);
        }
        getSVNEnvironment().getOut().print(buffer.toString());
    }

    private static class MergeFrame {
        private long myMergeRevision;
        private long myNumberOfChildrenRemaining;
    }

}
