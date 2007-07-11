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
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNMergeInfo;
import org.tmatesoft.svn.core.SVNMergeInfoInheritance;
import org.tmatesoft.svn.core.SVNMergeRange;
import org.tmatesoft.svn.core.SVNMergeRangeList;
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
        return mergeInfo == null ? new TreeMap() : mergeInfo;
    }
    
    public Map getMergeInfoForTree(String[] paths, FSRevisionRoot root) throws SVNException {
        Map pathsToMergeInfos = null; 
        try {
            myDBProcessor.openDB(root.getOwner().getDBRoot());
            pathsToMergeInfos = getMergeInfoImpl(paths, root, SVNMergeInfoInheritance.INHERITED);
            long revision = root.getRevision();
            for (int i = 0; i < paths.length; i++) {
                String path = paths[i];
                SVNMergeInfo mergeInfo = (SVNMergeInfo) pathsToMergeInfos.get(path); 
                Map srcsToRangeLists = mergeInfo != null ? mergeInfo.getMergeSourcesToMergeLists() : null;
                srcsToRangeLists = myDBProcessor.getMergeInfoForChildren(path, revision, srcsToRangeLists);
                if (mergeInfo == null) {
                    mergeInfo = new SVNMergeInfo(path, srcsToRangeLists);
                    pathsToMergeInfos.put(path, mergeInfo);
                }
            }
        } finally {
            myDBProcessor.closeDB();
        }
        return pathsToMergeInfos == null ? new TreeMap() : pathsToMergeInfos;
    }
    
    private Map getMergeInfoImpl(String[] paths, FSRevisionRoot root, SVNMergeInfoInheritance inherit) throws SVNException {
        Map mergeInfoCache = new TreeMap();
        Map result = null;
        long revision = root.getRevision();
        for (int i = 0; i < paths.length; i++) {
            String path = paths[i];
            result = getMergeInfoForPath(path, revision, mergeInfoCache, result, inherit);
        }
        return result;
    } 
    
    private Map getMergeInfoForPath(String path, long revision, Map mergeInfoCache, Map result, SVNMergeInfoInheritance inherit) throws SVNException {
        result = result == null ? new TreeMap() : result;
        long lastMergedRevision = 0;
        if (inherit != SVNMergeInfoInheritance.NEAREST_ANCESTOR) {
            SVNMergeInfo pathMergeInfo = (SVNMergeInfo) mergeInfoCache.get(path);
            if (pathMergeInfo != null) {
                result.put(path, pathMergeInfo);
                return result;
            }
            
            lastMergedRevision = myDBProcessor.getMaxRevisionForPathFromMergeInfoChangedTable(path, revision);
            if (lastMergedRevision > 0) {
                Map mergeSrcsToMergeRangeLists = myDBProcessor.parseMergeInfoFromDB(path, lastMergedRevision);
                if (!mergeSrcsToMergeRangeLists.isEmpty()) {
                    SVNMergeInfo mergeInfo = new SVNMergeInfo(path, combineRanges(mergeSrcsToMergeRangeLists));
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
                Map parentSrcsToRangeLists = parentInfo.getMergeSourcesToMergeLists();
                Map translatedSrcsToRangeLists = new TreeMap();
                for (Iterator paths = parentSrcsToRangeLists.keySet().iterator(); paths.hasNext();) {
                    String mergeSrcPath = (String) paths.next();
                    SVNMergeRangeList rangeList = (SVNMergeRangeList) parentSrcsToRangeLists.get(mergeSrcPath);
                    translatedSrcsToRangeLists.put(SVNPathUtil.append(mergeSrcPath, name), rangeList);
                }
                SVNMergeInfo translatedMergeInfo = new SVNMergeInfo(path, combineRanges(translatedSrcsToRangeLists)); 
                mergeInfoCache.put(path, translatedMergeInfo);
                result.put(path, translatedMergeInfo);
            }
        }
        return result;
    }
    
    private Map combineRanges(Map srcPathsToRangeLists) {
        String[] paths = (String[]) srcPathsToRangeLists.keySet().toArray();
        for (int i = 0; i < paths.length; i++) {
            String path = paths[i];
            SVNMergeRangeList rangeList = (SVNMergeRangeList) srcPathsToRangeLists.get(path);
            rangeList = rangeList.combineRanges();
            srcPathsToRangeLists.put(path, rangeList);
        }
        return srcPathsToRangeLists;
    }
    
    public static SVNMergeInfoManager createMergeInfoManager(ISVNDBProcessor dbProcessor) {
        if (dbProcessor == null) {
            dbProcessor =  new SVNSQLiteDBProcessor();
        }
        return new SVNMergeInfoManager(dbProcessor);
    }
    
    public static Map mergeMergeInfos(Map originalSrcsToRangeLists, Map changedSrcsToRangeLists) {
        originalSrcsToRangeLists = originalSrcsToRangeLists == null ? new TreeMap() : originalSrcsToRangeLists;
        changedSrcsToRangeLists = changedSrcsToRangeLists == null ? Collections.EMPTY_MAP : changedSrcsToRangeLists;
        String[] paths1 = (String[]) originalSrcsToRangeLists.keySet().toArray();
        String[] paths2 = (String[]) changedSrcsToRangeLists.keySet().toArray();
        int i = 0;
        int j = 0;
        while (i < paths1.length && j < paths2.length) {
            String path1 = paths1[i];
            String path2 = paths2[j];
            int res = path1.compareTo(path2);
            if (res == 0) {
                SVNMergeRangeList rangeList1 = (SVNMergeRangeList) originalSrcsToRangeLists.get(path1);
                SVNMergeRangeList rangeList2 = (SVNMergeRangeList) changedSrcsToRangeLists.get(path2);
                rangeList1 = rangeList1.merge(rangeList2);
                originalSrcsToRangeLists.put(path1, rangeList1);
                i++;
                j++;
            } else if (res < 0) {
                i++;
            } else {
                originalSrcsToRangeLists.put(path2, changedSrcsToRangeLists.get(path2));
                j++;
            }
        }
        
        for (; j < paths2.length; j++) {
            String path = paths2[j];
            originalSrcsToRangeLists.put(path, changedSrcsToRangeLists.get(path));
        }
        return originalSrcsToRangeLists;
    }
    
    public static String combineMergeInfoProperties(String propValue1, String propValue2) throws SVNException {
        Map srcsToRanges1 = SVNMergeInfo.parseMergeInfo(new StringBuffer(propValue1), null);
        Map srcsToRanges2 = SVNMergeInfo.parseMergeInfo(new StringBuffer(propValue2), null);
        srcsToRanges1 = mergeMergeInfos(srcsToRanges1, srcsToRanges2);
        return SVNMergeInfo.formatMergeInfoToString(srcsToRanges1);
    }
    
    public static void diffMergeInfoProperties(Map deleted, Map added, String fromPropValue, String toPropValue) throws SVNException {
        if (fromPropValue.equals(toPropValue)) {
            return;
        } 
        Map from = SVNMergeInfo.parseMergeInfo(new StringBuffer(fromPropValue), null);
        Map to = SVNMergeInfo.parseMergeInfo(new StringBuffer(toPropValue), null);
        diffMergeInfo(deleted, added, from, to);
    }
    
    public static void diffMergeInfo(Map deleted, Map added, Map from, Map to) {
        from = from == null ? Collections.EMPTY_MAP : from;
        to = to == null ? Collections.EMPTY_MAP : to;
        if (!from.isEmpty() && to.isEmpty()) {
            SVNMergeInfo.dupMergeInfo(from, deleted);
        } else if (from.isEmpty() && !to.isEmpty()) {
            SVNMergeInfo.dupMergeInfo(to, added);
        } else if (!from.isEmpty() && !to.isEmpty()) {
            walkMergeInfoHashForDiff(deleted, added, from, to);
        }
    }
    
    private static void walkMergeInfoHashForDiff(Map deleted, Map added, Map from, Map to) {
        for (Iterator paths = from.keySet().iterator(); paths.hasNext();) {
            String path = (String) paths.next();
            SVNMergeRangeList fromRangeList = (SVNMergeRangeList) from.get(path);
            SVNMergeRangeList toRangeList = (SVNMergeRangeList) to.get(path);
            if (toRangeList != null) {
                SVNMergeRangeList deletedRangeList = fromRangeList.diff(toRangeList);
                SVNMergeRangeList addedRangeList = toRangeList.diff(fromRangeList);
                if (deleted != null && deletedRangeList.getSize() > 0) {
                    deleted.put(path, deletedRangeList);
                }
                if (added != null && addedRangeList.getSize() > 0) {
                    added.put(path, addedRangeList);
                }
            } else if (deleted != null) {
                deleted.put(path, fromRangeList.dup());
            }
        }
        
        if (added == null) {
            return;
        }
        
        for (Iterator paths = to.keySet().iterator(); paths.hasNext();) {
            String path = (String) paths.next();
            SVNMergeRangeList toRangeList = (SVNMergeRangeList) to.get(path);
            if (!from.containsKey(path)) {
                added.put(path, toRangeList.dup());
            }
        }        
    }
    
}
