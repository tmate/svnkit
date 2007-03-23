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
package org.tmatesoft.svn.core.internal.wc;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.security.acl.LastOwnerException;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.wc.ISVNMerger;
import org.tmatesoft.svn.core.wc.SVNDiffOptions;
import org.tmatesoft.svn.core.wc.SVNStatusType;
import org.tmatesoft.svn.util.SVNDebugLog;

import de.regnis.q.sequence.line.QSequenceLineRAData;
import de.regnis.q.sequence.line.QSequenceLineRAFileData;


/**
 * @version 1.1.1
 * @author  TMate Software Ltd.
 */
public class DefaultSVNMerger implements ISVNMerger {
    
    private byte[] myStart;
    private byte[] mySeparator;
    private byte[] myEnd;

    public DefaultSVNMerger(byte[] start, byte[] sep, byte[] end) {
        myStart = start;
        mySeparator = sep;
        myEnd = end;
    }

    public SVNStatusType mergeText(File baseFile, File localFile, File latestFile, boolean dryRun, SVNDiffOptions options, OutputStream result) throws SVNException {
        FSMergerBySequence merger = new FSMergerBySequence(myStart, mySeparator, myEnd);
        SVNDebugLog.getDefaultLog().info("dry run: " + dryRun);
        dump(baseFile);
        dump(localFile);
        dump(latestFile);
        int mergeResult = 0;
        RandomAccessFile localIS = null;
        RandomAccessFile latestIS = null;
        RandomAccessFile baseIS = null;
        try {
            localIS = new RandomAccessFile(localFile, "r");
            latestIS = new RandomAccessFile(latestFile, "r");
            baseIS = new RandomAccessFile(baseFile, "r");

            QSequenceLineRAData baseData = new QSequenceLineRAFileData(baseIS);
            QSequenceLineRAData localData = new QSequenceLineRAFileData(localIS);
            QSequenceLineRAData latestData = new QSequenceLineRAFileData(latestIS);
            mergeResult = merger.merge(baseData, localData, latestData, options, result);
        } catch (IOException e) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, e.getLocalizedMessage());
            SVNErrorManager.error(err, e);
        } finally {
            if (localIS != null) {
                try {
                    localIS.close();
                } catch (IOException e) {
                    //
                }
            }
            if (baseIS != null) {
                try {
                    baseIS.close();
                } catch (IOException e) {
                    //
                }
            }
            if (latestIS != null) {
                try {
                    latestIS.close();
                } catch (IOException e) {
                    //
                }
            }
        }
        SVNStatusType status = SVNStatusType.UNCHANGED;
        if (mergeResult == FSMergerBySequence.CONFLICTED) {
            status = SVNStatusType.CONFLICTED;
        } else if (mergeResult == FSMergerBySequence.MERGED) {
            status = SVNStatusType.MERGED;
        }
        SVNDebugLog.getDefaultLog().info("result: " + status);
        
        return status;
    }

    public SVNStatusType mergeBinary(File baseFile, File localFile, File latestFile, boolean dryRun, OutputStream out) throws SVNException {
        return SVNStatusType.CONFLICTED;
    }
    
    private static void dump(File f) {
        
        InputStream is = null;
        StringBuffer sb = new StringBuffer();
        try {
            is = SVNFileUtil.openFileForReading(f);
            byte[] buffer = new byte[1024];
            while(true) {
                int l = is.read(buffer);
                if (l <= 0) {
                    break;
                }
                sb.append(new String(buffer, 0, l));
            }
        } catch (IOException e) {
            SVNDebugLog.getDefaultLog().info(e);
        } catch (SVNException e) {
            SVNDebugLog.getDefaultLog().info(e);
        } finally {
            SVNFileUtil.closeFile(is);
        }
        SVNDebugLog.getDefaultLog().info(f.getAbsolutePath());
        SVNDebugLog.getDefaultLog().info(sb.toString());
    }

}
