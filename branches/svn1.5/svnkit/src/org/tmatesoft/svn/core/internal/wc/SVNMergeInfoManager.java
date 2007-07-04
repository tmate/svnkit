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
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNMergeInfo;
import org.tmatesoft.svn.core.SVNMergeInfoInheritance;
import org.tmatesoft.svn.core.SVNMergeRange;
import org.tmatesoft.svn.core.internal.io.fs.FSRevisionRoot;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;


/**
 * @version 1.1.2
 * @author  TMate Software Ltd.
 */
public class SVNMergeInfoManager {
    private ISVNDBProcessor myDBProcessor;
    
    private SVNMergeInfoManager(ISVNDBProcessor dbProcessor) {
        myDBProcessor = dbProcessor;
    }
    
    public void createIndex(File dbDirectory) throws SVNException {
        try {
            myDBProcessor.openDB(dbDirectory);
        } finally {
            myDBProcessor.closeDB();
        }
    }
    
    public Map getMergeInfo(String[] paths, FSRevisionRoot root, SVNMergeInfoInheritance inherit) throws SVNException {
        Map mergeInfo = null; 
        try {
            myDBProcessor.openDB(root.getOwner().getDBRoot());
            mergeInfo = getMergeInfoImpl(paths, root, inherit);
        } finally {
            myDBProcessor.closeDB();
        }
        return mergeInfo == null ? new HashMap() : mergeInfo;
    }
    
    public static SVNMergeInfoManager createMergeInfoManager(ISVNDBProcessor dbProcessor) {
        if (dbProcessor == null) {
            dbProcessor =  new SVNSQLiteDBProcessor();
        }
        return new SVNMergeInfoManager(dbProcessor);
    }
    
    /**
     * Each element of the resultant array is formed like this:
     * %s:%ld-%ld,.. where the first %s is a merge src path 
     * and %ld-%ld is startRev-endRev merge range.
     */
    public static String[] formatMergeInfo(SVNMergeInfo mergeInfo) {
        Map srcsToRanges = mergeInfo.getMergeSourcesToMergeRanges();
        String[] pathRanges = new String[srcsToRanges.size()];
        int k = 0;
        for (Iterator paths = srcsToRanges.keySet().iterator(); paths.hasNext();) {
            String path = (String) paths.next();
            String output = path + ':';  
            SVNMergeRange[] ranges = (SVNMergeRange[]) srcsToRanges.get(path);
            for (int i = 0; i < ranges.length; i++) {
                SVNMergeRange range = ranges[i];
                long startRev = range.getStartRevision();
                long endRev = range.getEndRevision();
                if (startRev == endRev) {
                    output += String.valueOf(startRev);
                } else {
                    output += startRev + '-' + endRev;
                }
                if (i < ranges.length - 1) {
                    output += ',';
                }
            }
            pathRanges[k++] = output;
        }
        return pathRanges;
    }
    
    private Map getMergeInfoImpl(String[] paths, FSRevisionRoot root, SVNMergeInfoInheritance inherit) throws SVNException {
        Map mergeInfoCache = new HashMap();
        Map result = null;
        long revision = root.getRevision();
        for (int i = 0; i < paths.length; i++) {
            String path = paths[i];
            result = getMergeInfoForPath(path, revision, mergeInfoCache, result, inherit);
        }
        return result;
    } 
    
    private Map getMergeInfoForPath(String path, long revision, Map mergeInfoCache, Map result, SVNMergeInfoInheritance inherit) throws SVNException {
        result = result == null ? new HashMap() : result;
        long lastMergedRevision = 0;
        if (inherit != SVNMergeInfoInheritance.NEAREST_ANCESTOR) {
            SVNMergeInfo pathMergeInfo = (SVNMergeInfo) mergeInfoCache.get(path);
            if (pathMergeInfo != null) {
                result.put(path, pathMergeInfo);
            }
            
            lastMergedRevision = myDBProcessor.getMaxRevisionForPathFromMergeInfoChangedTable(path, revision);
            if (lastMergedRevision > 0) {
                Map mergeSrcsToMergeRanges = myDBProcessor.parseMergeInfoFromDB(path, lastMergedRevision);
                if (!mergeSrcsToMergeRanges.isEmpty()) {
                    SVNMergeInfo mergeInfo = new SVNMergeInfo(path, mergeSrcsToMergeRanges);
                    result.put(path, mergeInfo);
                    mergeInfoCache.put(path, mergeInfo);
                } else {
                    mergeInfoCache.remove(path);
                }
                return result;
            }
        }
        if ((lastMergedRevision == 0 && inherit == SVNMergeInfoInheritance.INHERITED) || 
                inherit == SVNMergeInfoInheritance.NEAREST_ANCESTOR) {
            if ("".equals(path) || "/".equals(path)) {
                return result;
            }
            String parent = SVNPathUtil.removeTail(path);
            getMergeInfoForPath(parent, revision, mergeInfoCache, null, SVNMergeInfoInheritance.INHERITED);
            SVNMergeInfo parentInfo = (SVNMergeInfo) mergeInfoCache.get(parent);
            if (parentInfo == null) {
                mergeInfoCache.remove(path);
            } else {
                String name = SVNPathUtil.tail(path);
                Map parentSrcsToRanges = parentInfo.getMergeSourcesToMergeRanges();
                Map translatedSrcsToRanges = new TreeMap();
                for (Iterator paths = parentSrcsToRanges.keySet().iterator(); paths.hasNext();) {
                    String mergeSrcPath = (String) paths.next();
                    SVNMergeRange[] ranges = (SVNMergeRange[]) parentSrcsToRanges.get(mergeSrcPath);
                    translatedSrcsToRanges.put(SVNPathUtil.append(mergeSrcPath, name), ranges);
                }
                SVNMergeInfo translatedMergeInfo = new SVNMergeInfo(path, translatedSrcsToRanges); 
                mergeInfoCache.put(path, translatedMergeInfo);
                result.put(path, translatedMergeInfo);
            }
            
        }
        return result;
    }
}
