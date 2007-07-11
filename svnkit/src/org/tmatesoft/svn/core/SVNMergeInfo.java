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
package org.tmatesoft.svn.core;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.TreeMap;

import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;


/**
 * @version 1.1.2
 * @author  TMate Software Ltd.
 */
public class SVNMergeInfo {
    private String myPath;
    private Map myMergeSrcPathsToRangeLists;

    public SVNMergeInfo(String path, Map srcsToRangeLists) {
        myPath = path;
        myMergeSrcPathsToRangeLists = srcsToRangeLists;
    }

    public String getPath() {
        return myPath;
    }
    
    /**
     * keys are String paths, values - SVNMergeRange[]
     */
    public Map getMergeSourcesToMergeLists() {
        return myMergeSrcPathsToRangeLists;
    }

    public static Map dupMergeInfo(Map srcsToRangeLists, Map target) {
        if (srcsToRangeLists == null) {
            return null;
        }
        target = target == null ? new TreeMap() : target;
        for (Iterator paths = srcsToRangeLists.keySet().iterator(); paths.hasNext();) {
            String path = (String) paths.next();
            SVNMergeRangeList rangeList = (SVNMergeRangeList) srcsToRangeLists.get(path);
            target.put(path, rangeList.dup());
        }
        return target;
    }
    
    public static Map parseMergeInfo(StringBuffer mergeInfo, Map srcPathsToRangeLists) throws SVNException {
        srcPathsToRangeLists = srcPathsToRangeLists == null ? new TreeMap() : srcPathsToRangeLists;
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
        srcPathsToRangeLists.put(path, new SVNMergeRangeList(ranges));
        return srcPathsToRangeLists;
    }

    /**
     * Each element of the resultant array is formed like this:
     * %s:%ld-%ld,.. where the first %s is a merge src path 
     * and %ld-%ld is startRev-endRev merge range.
     */
    public static String[] formatMergeInfoToArray(Map srcsToRangeLists) {
        srcsToRangeLists = srcsToRangeLists == null ? Collections.EMPTY_MAP : srcsToRangeLists;
        String[] pathRanges = new String[srcsToRangeLists.size()];
        int k = 0;
        for (Iterator paths = srcsToRangeLists.keySet().iterator(); paths.hasNext();) {
            String path = (String) paths.next();
            SVNMergeRangeList rangeList = (SVNMergeRangeList) srcsToRangeLists.get(path);
            String output = path + ':' + rangeList;  
            pathRanges[k++] = output;
        }
        return pathRanges;
    }

    public static String formatMergeInfoToString(Map srcsToRangeLists) {
        String[] infosArray = formatMergeInfoToArray(srcsToRangeLists);
        String result = "";
        for (int i = 0; i < infosArray.length; i++) {
            result += infosArray[i];
            if (i < infosArray.length - 1) {
                result += '\n';
            }
        }
        return result;
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
                SVNMergeRange combinedRange = lastRange == null ? range : 
                                                                  lastRange.combine(range, false);
                if (lastRange != combinedRange) {
                    lastRange = combinedRange;
                    ranges.add(lastRange);
                }
                return (SVNMergeRange[]) ranges.toArray(new SVNMergeRange[ranges.size()]);
            } else if (mergeInfo.length() > 0 && mergeInfo.charAt(0) == ',') {
                SVNMergeRange combinedRange = lastRange == null ? range :
                                                                  lastRange.combine(range, false);
                if (lastRange != combinedRange) {
                    lastRange = combinedRange;
                    ranges.add(lastRange);
                }
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
