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
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.TreeMap;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNMergeInfo;
import org.tmatesoft.svn.core.SVNMergeInfoInheritance;
import org.tmatesoft.svn.core.SVNMergeRange;
import org.tmatesoft.svn.core.internal.io.fs.FSRevisionRoot;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.util.SVNDebugLog;


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
                Map srcsToRanges = mergeInfo != null ? mergeInfo.getMergeSourcesToMergeRanges() : null;
                srcsToRanges = myDBProcessor.getMergeInfoForChildren(path, revision, srcsToRanges);
                if (mergeInfo == null) {
                    mergeInfo = new SVNMergeInfo(path, srcsToRanges);
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
            }
            
            lastMergedRevision = myDBProcessor.getMaxRevisionForPathFromMergeInfoChangedTable(path, revision);
            if (lastMergedRevision > 0) {
                Map mergeSrcsToMergeRanges = myDBProcessor.parseMergeInfoFromDB(path, lastMergedRevision);
                if (!mergeSrcsToMergeRanges.isEmpty()) {
                    SVNMergeInfo mergeInfo = new SVNMergeInfo(path, combineRanges(mergeSrcsToMergeRanges));
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
                SVNMergeInfo translatedMergeInfo = new SVNMergeInfo(path, combineRanges(translatedSrcsToRanges)); 
                mergeInfoCache.put(path, translatedMergeInfo);
                result.put(path, translatedMergeInfo);
            }
            
        }
        return result;
    }
    
    private Map combineRanges(Map srcPathsToRanges) {
        Collection combinedRanges = new LinkedList();
        String[] paths = (String[]) srcPathsToRanges.keySet().toArray();
        for (int i = 0; i < paths.length; i++) {
            String path = paths[i];
            SVNMergeRange[] ranges = (SVNMergeRange[]) srcPathsToRanges.get(path);
            SVNMergeRange lastRange = null;
            for (int k = 0; k < ranges.length; k++) {
                SVNMergeRange nextRange = ranges[k];
                if (lastRange != null && 
                    lastRange.getStartRevision() <= nextRange.getEndRevision() + 1 &&
                    nextRange.getStartRevision() <= lastRange.getEndRevision() + 1) {
                    lastRange.setStartRevision(Math.min(lastRange.getStartRevision(), nextRange.getStartRevision()));
                    lastRange.setEndRevision(Math.max(lastRange.getEndRevision(), nextRange.getEndRevision()));
                    continue; 
                }
                lastRange = nextRange;
                combinedRanges.add(nextRange);
            }
            ranges = (SVNMergeRange[]) combinedRanges.toArray(new SVNMergeRange[combinedRanges.size()]);
            combinedRanges.clear();
            Arrays.sort(ranges);
            srcPathsToRanges.put(path, ranges);
        }
        return srcPathsToRanges;
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
    
    public static Map parseMergeInfo(StringBuffer mergeInfo, Map srcPathsToRanges) throws SVNException {
        srcPathsToRanges = srcPathsToRanges == null ? new TreeMap() : srcPathsToRanges;
        int ind = mergeInfo.indexOf(":");
        if (ind == -1) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.MERGE_INFO_PARSE_ERROR, "Pathname not terminated by ':'");
            SVNErrorManager.error(err);
        }
        String path = mergeInfo.substring(0, ind);
        mergeInfo = mergeInfo.delete(0, ind + 1);
        SVNMergeRange[] ranges = parseRanges(mergeInfo);
        if (mergeInfo.length() != 0 && mergeInfo.charAt(0) != '\n') {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.MERGE_INFO_PARSE_ERROR, "Could not find end of line in range list line in ''{0}''", mergeInfo);
            SVNErrorManager.error(err);
        }
        if (mergeInfo.length() > 0) {
            mergeInfo = mergeInfo.deleteCharAt(0);
        }
        Arrays.sort(ranges);
        srcPathsToRanges.put(path, ranges);
        return srcPathsToRanges;
    }

    public static void mergeMergeInfos(Map originalSrcsToRanges, Map changedSrcsToRanges) {
        String[] paths1 = (String[]) originalSrcsToRanges.keySet().toArray();
        String[] paths2 = (String[]) changedSrcsToRanges.keySet().toArray();
        int i = 0;
        int j = 0;
        while (i < paths1.length && j < paths2.length) {
            String path1 = paths1[i];
            String path2 = paths2[j];
            int res = path1.compareTo(path2);
            if (res == 0) {
                SVNMergeRange[] ranges1 = (SVNMergeRange[]) originalSrcsToRanges.get(path1);
                SVNMergeRange[] ranges2 = (SVNMergeRange[]) changedSrcsToRanges.get(path2);
                ranges1 = mergeMergeRanges(ranges1, ranges2);
                originalSrcsToRanges.put(path1, ranges1);
                i++;
                j++;
            } else if (res < 0) {
                i++;
            } else {
                originalSrcsToRanges.put(path2, changedSrcsToRanges.get(path2));
                j++;
            }
        }
        
        for (; j < paths2.length; j++) {
            String path = paths2[j];
            originalSrcsToRanges.put(path, changedSrcsToRanges.get(path));
        }
    }
    
    private static SVNMergeRange[] mergeMergeRanges(SVNMergeRange[] ranges1, SVNMergeRange[] ranges2) {
        int i = 0;
        int j = 0;
        SVNMergeRange lastRange = null;
        Collection resultRanges = new LinkedList();
        while (i < ranges1.length && j < ranges2.length) {
            SVNMergeRange range1 = ranges1[i];
            SVNMergeRange range2 = ranges2[j];
            int res = range1.compareTo(range2);
            if (res == 0) {
                lastRange = combineWithLastRange(lastRange, range1, resultRanges, true);
                i++;
                j++;
            } else if (res < 0) {
                lastRange = combineWithLastRange(lastRange, range1, resultRanges, true);
                i++;
            } else { 
                lastRange = combineWithLastRange(lastRange, range2, resultRanges, true);
                j++;
            }
        }
        SVNDebugLog.assertCondition(i >= ranges1.length || j >= ranges2.length, "assertion failure in SVNMergeInfoManager.mergeMergeRanges()");
        for (; i < ranges1.length; i++) {
            SVNMergeRange range = ranges1[i];
            lastRange = combineWithLastRange(lastRange, range, resultRanges, true);
        }
        for (; j < ranges2.length; j++) {
            SVNMergeRange range = ranges2[j];
            lastRange = combineWithLastRange(lastRange, range, resultRanges, true);
        }
        return (SVNMergeRange[]) resultRanges.toArray(new SVNMergeRange[resultRanges.size()]);
    }

    private static SVNMergeRange[] parseRanges(StringBuffer mergeInfo) throws SVNException {
        Collection ranges = new LinkedList();
        while (mergeInfo.length() > 0 && mergeInfo.charAt(0) != '\n' && 
                Character.isWhitespace(mergeInfo.charAt(0))) {
            mergeInfo = mergeInfo.deleteCharAt(0);
        }
        if (mergeInfo.length() == 0 || mergeInfo.charAt(0) == '\n') {
            return null;
        }
        
        SVNMergeRange lastRange = null;
        while (mergeInfo.length() > 0 && mergeInfo.charAt(0) != '\n') {
            long startRev = parseRevision(mergeInfo);
            if (mergeInfo.length() > 0 && mergeInfo.charAt(0) != '\n' && 
                mergeInfo.charAt(0) != '-' && mergeInfo.charAt(0) != ',') {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.MERGE_INFO_PARSE_ERROR, 
                                                             "Invalid character ''{0}'' found in revision list", 
                                                             new Character(mergeInfo.charAt(0)));
                SVNErrorManager.error(err);
            }
            
            SVNMergeRange range = new SVNMergeRange(startRev, startRev);
            if (mergeInfo.length() > 0 && mergeInfo.charAt(0) == '-') {
                mergeInfo = mergeInfo.deleteCharAt(0);
                long endRev = parseRevision(mergeInfo);
                range.setEndRevision(endRev);
            }
            if (mergeInfo.length() == 0 || mergeInfo.charAt(0) == '\n') {
                lastRange = combineWithLastRange(lastRange, range, ranges, false);
                return (SVNMergeRange[]) ranges.toArray(new SVNMergeRange[ranges.size()]);
            } else if (mergeInfo.length() > 0 && mergeInfo.charAt(0) == ',') {
                lastRange = combineWithLastRange(lastRange, range, ranges, false);
                mergeInfo = mergeInfo.deleteCharAt(0);
            } else {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.MERGE_INFO_PARSE_ERROR, 
                                                             "Invalid character ''{0}'' found in range list", 
                                                             mergeInfo.length() > 0 ?  mergeInfo.charAt(0) + "" : "");
                SVNErrorManager.error(err);
            }
        }
        
        if (mergeInfo.length() == 0 || mergeInfo.charAt(0) != '\n' ) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.MERGE_INFO_PARSE_ERROR, "Range list parsing ended before hitting newline");
            SVNErrorManager.error(err);
        }
        
        return (SVNMergeRange[]) ranges.toArray(new SVNMergeRange[ranges.size()]);
    }
    
    private static SVNMergeRange combineWithLastRange(SVNMergeRange lastRange, SVNMergeRange nextRange, Collection ranges, boolean dup) {
        if (lastRange != null && 
            lastRange.getStartRevision() <= nextRange.getEndRevision() + 1 &&
            nextRange.getStartRevision() <= lastRange.getEndRevision() + 1) {
            lastRange.setStartRevision(Math.min(lastRange.getStartRevision(), nextRange.getStartRevision()));
            lastRange.setEndRevision(Math.max(lastRange.getEndRevision(), nextRange.getEndRevision()));
            return lastRange; 
        }
        SVNMergeRange range = dup ? new SVNMergeRange(nextRange.getStartRevision(), 
                                                      nextRange.getEndRevision()) : 
                                    nextRange;
        ranges.add(range);
        return range;
    }
    
    private static long parseRevision(StringBuffer mergeInfo) throws SVNException {
        int ind = 0;
        while (ind < mergeInfo.length() && Character.isDigit(mergeInfo.charAt(ind))) {
            ind++;
        }
        
        if (ind == 0) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.MERGE_INFO_PARSE_ERROR, 
                                                         "Invalid revision number found parsing ''{0}''", 
                                                         mergeInfo.length() > 0 ? mergeInfo.charAt(0) + "" : "");
            SVNErrorManager.error(err);
        }
        
        String numberStr = mergeInfo.substring(0, ind);
        mergeInfo = mergeInfo.delete(0, ind);
        return Long.parseLong(numberStr);
    }
}
