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
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.TreeMap;

import org.tmatesoft.svn.core.SVNCancelException;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNMergeInfoInheritance;
import org.tmatesoft.svn.core.SVNMergeRange;
import org.tmatesoft.svn.core.SVNMergeRangeList;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.internal.util.SVNMergeInfoUtil;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.admin.ISVNEntryHandler;
import org.tmatesoft.svn.core.internal.wc.admin.SVNAdminArea;
import org.tmatesoft.svn.core.internal.wc.admin.SVNEntry;
import org.tmatesoft.svn.core.internal.wc.admin.SVNVersionedProperties;
import org.tmatesoft.svn.core.internal.wc.admin.SVNWCAccess;
import org.tmatesoft.svn.core.io.ISVNReporter;
import org.tmatesoft.svn.core.io.ISVNReporterBaton;
import org.tmatesoft.svn.core.io.SVNCapability;
import org.tmatesoft.svn.core.io.SVNLocationEntry;
import org.tmatesoft.svn.core.io.SVNLocationSegment;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.wc.ISVNEventHandler;
import org.tmatesoft.svn.core.wc.ISVNOptions;
import org.tmatesoft.svn.core.wc.ISVNRepositoryPool;
import org.tmatesoft.svn.core.wc.SVNBasicClient;
import org.tmatesoft.svn.core.wc.SVNDiffOptions;
import org.tmatesoft.svn.core.wc.SVNEvent;
import org.tmatesoft.svn.core.wc.SVNEventAction;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNRevisionRange;
import org.tmatesoft.svn.core.wc.SVNStatus;
import org.tmatesoft.svn.core.wc.SVNStatusType;


/**
 * @version 1.1.2
 * @author  TMate Software Ltd.
 */
public abstract class SVNMergeDriver extends SVNBasicClient {

    protected boolean myAreSourcesAncestral;
    protected boolean myIsSameRepository;
    protected boolean myIsDryRun;
    protected boolean myIsRecordOnly;
    protected boolean myIsForce;
    protected boolean myIsTargetMissingChild;
    protected boolean myHasExistingMergeInfo;
    protected boolean myIsOperativeMerge;
    protected boolean myIsTargetHasDummyMergeRange;
    protected boolean myIsIgnoreAncestry;
    protected boolean myIsSingleFileMerge;
    protected boolean myIsMergeInfoCapable;
    protected boolean myIsAddNecessitatedMerge;
    /**
     * @deprecated
     */
    protected boolean myIsOverrideSet;
    protected boolean myIsFirstRange;
    protected int myOperativeNotificationsNumber;
    protected int myNotificationsNumber;
    protected int myCurrentAncestorIndex;
    protected Map myConflictedPaths;
    protected Map myDryRunDeletions;
    protected Map myWorkingMergeInfo;
    protected SVNURL myURL;
    protected File myTarget;
    protected File myAddedPath;
    protected List myMergedPaths;
    protected List mySkippedPaths;
    protected List myChildrenWithMergeInfo;
    protected List myAddedPaths;
    protected SVNWCAccess myWCAccess;
    protected SVNRepository myRepository1;
    protected SVNRepository myRepository2;
    
    public SVNMergeDriver(ISVNAuthenticationManager authManager, ISVNOptions options) {
        super(authManager, options);
    }

    protected SVNMergeDriver(ISVNRepositoryPool repositoryPool, ISVNOptions options) {
        super(repositoryPool, options);
    }
    
    public abstract SVNDiffOptions getMergeOptions();

    protected void runPeggedMerge(SVNURL srcURL, File srcPath, Collection rangesToMerge, 
    		SVNRevision pegRevision, File targetWCPath, SVNDepth depth, boolean dryRun, 
            boolean force, boolean ignoreAncestry, boolean recordOnly) throws SVNException {
        if (rangesToMerge == null || rangesToMerge.isEmpty()) {
        	return;
        }
        
    	myWCAccess = createWCAccess();
        targetWCPath = targetWCPath.getAbsoluteFile();
        try {
            SVNAdminArea adminArea = myWCAccess.probeOpen(targetWCPath, !dryRun, SVNWCAccess.INFINITE_DEPTH);
            SVNEntry targetEntry = myWCAccess.getVersionedEntry(targetWCPath, false);
            SVNURL url = srcURL == null ? getURL(srcPath) : srcURL;
            if (url == null) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_MISSING_URL, 
                		"''{0}'' has no URL", srcPath);
                SVNErrorManager.error(err);
            }
        
            SVNURL wcReposRoot = getReposRoot(targetWCPath, null, SVNRevision.WORKING, adminArea, myWCAccess);
            List mergeSources = null;
            SVNRepository repository = null;
            SVNURL sourceReposRoot = null;
            try {
            	repository = createRepository(url, true);
            	sourceReposRoot = repository.getRepositoryRoot(true);
            	mergeSources = normalizeMergeSources(srcPath, url, sourceReposRoot, pegRevision, rangesToMerge, 
            			repository);
            } finally {
            	repository.closeSession();
            }
            
            doMerge(mergeSources, targetWCPath, targetEntry, adminArea, true, true, 
            		wcReposRoot.equals(sourceReposRoot), ignoreAncestry, force, dryRun, recordOnly, depth);
            
        } finally {
            try {
                myWCAccess.close();
            } catch (SVNException svne) {
                //
            }
        }
    }

    /**
     * @deprecated
     */
    protected void runMerge(SVNURL url1, SVNRevision revision1, SVNURL url2, SVNRevision revision2, 
            File targetWCPath, SVNDepth depth, boolean dryRun, boolean force, 
            boolean ignoreAncestry, boolean recordOnly) throws SVNException {
        
        if (!revision1.isValid() || !revision2.isValid()) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_BAD_REVISION, 
                    "Not all required revisions are specified");            
            SVNErrorManager.error(err);
        }
        
        SVNRepository repository1 = null;
        SVNRepository repository2 = null;
        myWCAccess = createWCAccess();
        targetWCPath = targetWCPath.getAbsoluteFile();
        try {
            SVNAdminArea adminArea = myWCAccess.probeOpen(targetWCPath, !dryRun, SVNWCAccess.INFINITE_DEPTH);

            SVNEntry entry = myWCAccess.getVersionedEntry(targetWCPath, false);
            SVNURL wcReposRoot = getReposRoot(targetWCPath, null, SVNRevision.WORKING, adminArea, myWCAccess);
                
            long[] latestRev = new long[1];
            latestRev[0] = SVNRepository.INVALID_REVISION;

            repository1 = createRepository(url1, false);
            SVNURL sourceReposRoot = repository1.getRepositoryRoot(true); 
            long rev1 = getRevisionNumber(revision1, latestRev, repository1, null); 

            repository2 = createRepository(url2, false);
            long rev2 = getRevisionNumber(revision2, latestRev, repository2, null); 
            
            boolean sameRepos = sourceReposRoot.equals(wcReposRoot);
            String youngestCommonPath = null;
            long youngestCommonRevision = SVNRepository.INVALID_REVISION;
            if (!ignoreAncestry) {
            	SVNLocationEntry youngestLocation = getYoungestCommonAncestor(null, url1, 
            			SVNRevision.create(rev1), null, url2, SVNRevision.create(rev2));
            	youngestCommonPath = youngestLocation.getPath();
            	youngestCommonRevision = youngestLocation.getRevision();
            }

            boolean related = false;
            boolean ancestral = false;
            List mergeSources = null;
            if (youngestCommonPath != null && SVNRevision.isValidRevisionNumber(youngestCommonRevision)) {
            	SVNRevisionRange range = null;
            	List ranges = new LinkedList();
            	related = true;
            	SVNURL youngestCommonURL = sourceReposRoot.appendPath(youngestCommonPath, false);

            	if (youngestCommonURL.equals(url2) && youngestCommonRevision == rev2) {
            		ancestral = true;
            		SVNRevision sRev = SVNRevision.create(rev1);
            		SVNRevision eRev = SVNRevision.create(youngestCommonRevision);
            		range = new SVNRevisionRange(sRev, eRev);
            		ranges.add(range);
            		mergeSources = normalizeMergeSources(null, url1, sourceReposRoot, sRev, 
            				ranges, repository1);
            	} else if (youngestCommonURL.equals(url1) && youngestCommonRevision == rev1) {
            		ancestral = true;
            		SVNRevision sRev = SVNRevision.create(youngestCommonRevision);
            		SVNRevision eRev = SVNRevision.create(rev2);
            		range = new SVNRevisionRange(sRev, eRev);
            		ranges.add(range);
            		mergeSources = normalizeMergeSources(null, url2, sourceReposRoot, eRev, 
            				ranges, repository2);
            	} else {
            		SVNRevision sRev = SVNRevision.create(rev1);
            		SVNRevision eRev = SVNRevision.create(youngestCommonRevision);
            		range = new SVNRevisionRange(sRev, eRev);
            		ranges.add(range);
            		List removeSources = normalizeMergeSources(null, url1, sourceReposRoot, sRev, 
            				ranges, repository1);
            		sRev = eRev;
            		eRev = SVNRevision.create(rev2);
            		range = new SVNRevisionRange(sRev, eRev);
            		ranges.clear();
            		ranges.add(range);
            		List addSources = normalizeMergeSources(null, url2, sourceReposRoot, eRev, 
            				ranges, repository2);
            		
            		repository1.closeSession();
            		repository2.closeSession();
            		
            		if (!recordOnly) {
            			MergeSource fauxSource = new MergeSource();
            			fauxSource.myURL1 = url1;
            			fauxSource.myURL2 = url2;
            			fauxSource.myRevision1 = rev1;
            			fauxSource.myRevision2 = rev2;
            			List fauxSources = new LinkedList();
            			fauxSources.add(fauxSource);
            			doMerge(fauxSources, targetWCPath, entry, adminArea, ancestral, related, sameRepos, 
            					ignoreAncestry, force, dryRun, recordOnly, depth);
            		}
            		
            		doMerge(addSources, targetWCPath, entry, adminArea, true, related, sameRepos, 
            				ignoreAncestry, force, dryRun, true, depth);
            		doMerge(removeSources, targetWCPath, entry, adminArea, true, related, sameRepos, 
            				ignoreAncestry, force, dryRun, true, depth);
            		return;
            	}
            } else {
                MergeSource mergeSrc = new MergeSource();
                mergeSrc.myURL1 = url1;
                mergeSrc.myURL2 = url2;
                mergeSrc.myRevision1 = rev1;
                mergeSrc.myRevision2 = rev2;
                mergeSources = new LinkedList();
                mergeSources.add(mergeSrc);
            }

    		repository1.closeSession();
    		repository2.closeSession();
            
    		doMerge(mergeSources, targetWCPath, entry, adminArea, ancestral, related, sameRepos, 
    				ignoreAncestry, force, dryRun, recordOnly, depth);
        } finally {
        	if (repository1 != null) {
        		repository1.closeSession();
        	}
        	if (repository2 != null) {
        		repository2.closeSession();
        	}
        	try {
                myWCAccess.close();
            } catch (SVNException svne) {
                //
            }
         }
    }

    protected void runMerge2(SVNURL url1, SVNRevision revision1, SVNURL url2, SVNRevision revision2, 
            File targetWCPath, SVNDepth depth, boolean dryRun, boolean force, 
            boolean ignoreAncestry, boolean recordOnly) throws SVNException {
        
        if (!revision1.isValid() || !revision2.isValid()) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_BAD_REVISION, 
                    "Not all required revisions are specified");            
            SVNErrorManager.error(err);
        }
        
        SVNRepository repository1 = null;
        SVNRepository repository2 = null;
        myWCAccess = createWCAccess();
        targetWCPath = targetWCPath.getAbsoluteFile();
        try {
            SVNAdminArea adminArea = myWCAccess.probeOpen(targetWCPath, !dryRun, SVNWCAccess.INFINITE_DEPTH);

            SVNEntry entry = myWCAccess.getVersionedEntry(targetWCPath, false);
            SVNURL wcReposRoot = getReposRoot(targetWCPath, null, SVNRevision.WORKING, adminArea, myWCAccess);
                
            long[] latestRev = new long[1];
            latestRev[0] = SVNRepository.INVALID_REVISION;

            repository1 = createRepository(url1, false);
            SVNURL sourceReposRoot = repository1.getRepositoryRoot(true); 
            long rev1 = getRevisionNumber(revision1, latestRev, repository1, null); 

            repository2 = createRepository(url2, false);
            long rev2 = getRevisionNumber(revision2, latestRev, repository2, null); 
            
            boolean sameRepos = sourceReposRoot.equals(wcReposRoot);
            String youngestCommonPath = null;
            long youngestCommonRevision = SVNRepository.INVALID_REVISION;
            if (!ignoreAncestry) {
            	SVNLocationEntry youngestLocation = getYoungestCommonAncestor2(null, url1, rev1, null, url2, 
            			rev2);
            	youngestCommonPath = youngestLocation.getPath();
            	youngestCommonRevision = youngestLocation.getRevision();
            }

            boolean related = false;
            boolean ancestral = false;
            List mergeSources = null;
            if (youngestCommonPath != null && SVNRevision.isValidRevisionNumber(youngestCommonRevision)) {
            	SVNRevisionRange range = null;
            	List ranges = new LinkedList();
            	related = true;
            	SVNURL youngestCommonURL = sourceReposRoot.appendPath(youngestCommonPath, false);

            	if (youngestCommonURL.equals(url2) && youngestCommonRevision == rev2) {
            		ancestral = true;
            		SVNRevision sRev = SVNRevision.create(rev1);
            		SVNRevision eRev = SVNRevision.create(youngestCommonRevision);
            		range = new SVNRevisionRange(sRev, eRev);
            		ranges.add(range);
            		mergeSources = normalizeMergeSources(null, url1, sourceReposRoot, sRev, 
            				ranges, repository1);
            	} else if (youngestCommonURL.equals(url1) && youngestCommonRevision == rev1) {
            		ancestral = true;
            		SVNRevision sRev = SVNRevision.create(youngestCommonRevision);
            		SVNRevision eRev = SVNRevision.create(rev2);
            		range = new SVNRevisionRange(sRev, eRev);
            		ranges.add(range);
            		mergeSources = normalizeMergeSources(null, url2, sourceReposRoot, eRev, 
            				ranges, repository2);
            	} else {
            		mergeCousinsAndSupplementMergeInfo(targetWCPath, entry, adminArea, repository1, url1, 
            				rev1, url2, rev2, youngestCommonRevision, sourceReposRoot, wcReposRoot, depth, 
            				ignoreAncestry, force, recordOnly, dryRun);
            		return;
            	}
            } else {
                MergeSource mergeSrc = new MergeSource();
                mergeSrc.myURL1 = url1;
                mergeSrc.myURL2 = url2;
                mergeSrc.myRevision1 = rev1;
                mergeSrc.myRevision2 = rev2;
                mergeSources = new LinkedList();
                mergeSources.add(mergeSrc);
            }

    		repository1.closeSession();
    		repository2.closeSession();
            
    		doMerge(mergeSources, targetWCPath, entry, adminArea, ancestral, related, sameRepos, 
    				ignoreAncestry, force, dryRun, recordOnly, depth);
        } finally {
        	if (repository1 != null) {
        		repository1.closeSession();
        	}
        	if (repository2 != null) {
        		repository2.closeSession();
        	}
        	try {
                myWCAccess.close();
            } catch (SVNException svne) {
                //
            }
         }
    }

    /**
     * @deprecated
     */
    protected void doMerge(Collection mergeSources, File target, SVNEntry targetEntry, SVNAdminArea adminArea, 
            boolean sourcesAncestral, boolean sourcesRelated, boolean sameRepository, boolean ignoreAncestry, 
            boolean force, boolean dryRun, boolean recordOnly, SVNDepth depth) throws SVNException {
        if (recordOnly && dryRun) {
            return;
        }
        
        if (recordOnly && !sourcesAncestral) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.INCORRECT_PARAMS, 
            "Use of two URLs is not compatible with mergeinfo modification"); 
            SVNErrorManager.error(err);
        }
        
        if (depth == SVNDepth.UNKNOWN) {
            depth = targetEntry.getDepth();
        }
        
        myIsForce = force;
        myIsDryRun = dryRun;
        myIsRecordOnly = recordOnly;
        myIsIgnoreAncestry = ignoreAncestry;
        myIsSameRepository = sameRepository;
        myIsMergeInfoCapable = false;
        myAreSourcesAncestral = sourcesAncestral;
        myIsTargetMissingChild = false;
        myIsSingleFileMerge = false;
        myTarget = target;
        myIsFirstRange = true;
        myWorkingMergeInfo = null;
        myNotificationsNumber = 0;
        myOperativeNotificationsNumber = 0;
        myCurrentAncestorIndex = -1;
        myMergedPaths = null;
        mySkippedPaths = null;
        myAddedPaths = null;
        myChildrenWithMergeInfo = null;
        myHasExistingMergeInfo = false;
        
        boolean checkedMergeInfoCapability = false;
        for (Iterator mergeSourcesIter = mergeSources.iterator(); mergeSourcesIter.hasNext();) {
            MergeSource mergeSource = (MergeSource) mergeSourcesIter.next();
            SVNURL url1 = mergeSource.myURL1;
            SVNURL url2 = mergeSource.myURL2;
            long revision1 = mergeSource.myRevision1;
            long revision2 = mergeSource.myRevision2;
            if (revision1 == revision2 && mergeSource.myURL1.equals(mergeSource.myURL2)) {
                return;
            }
            
            try {
                myRepository1 = createRepository(url1, false);
                myRepository2 = createRepository(url2, false);
                myIsTargetHasDummyMergeRange = false;

                myURL = url2;
                myAddedPath = null;
                myConflictedPaths = null;
                myDryRunDeletions = dryRun ? new HashMap() : null;
                myIsOperativeMerge = false;
                myIsAddNecessitatedMerge = false;
             
                if (!checkedMergeInfoCapability) {
                	myIsMergeInfoCapable = myRepository1.hasCapability(SVNCapability.MERGE_INFO);
                	checkedMergeInfoCapability = true;
                }
                
                if (myIsSameRepository && recordOnly) {
                    SVNURL mergeSourceURL = revision1 < revision2 ? url2 : url1;
                    SVNMergeRange range = new SVNMergeRange(revision1, revision2, true);
                    recordMergeInfoForRecordOnlyMerge(mergeSourceURL, range, targetEntry);
                    continue;
                }
                
                if (targetEntry.isFile()) {
                    doFileMerge(url1, revision1, url2, revision2, target, adminArea, sourcesRelated);
                } else if (targetEntry.isDirectory()) {
                    doDirectoryMerge(url1, revision1, url2, revision2, targetEntry, adminArea, depth);
                }
                
                if (!dryRun && myIsOperativeMerge) {
                    elideMergeInfo(myWCAccess, target, targetEntry, null);
                }
            } finally {
                if (myRepository1 != null) {
                    myRepository1.closeSession();
                }
                if (myRepository2 != null) {
                    myRepository2.closeSession();
                }
            }
        }
    }

    protected void doMerge2(List mergeSources, File target, SVNEntry targetEntry, SVNAdminArea adminArea, 
            boolean sourcesAncestral, boolean sourcesRelated, boolean sameRepository, boolean ignoreAncestry, 
            boolean force, boolean dryRun, boolean recordOnly, SVNDepth depth) throws SVNException {
        if (recordOnly && dryRun) {
            return;
        }
        
        if (recordOnly && !sourcesAncestral) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.INCORRECT_PARAMS, 
            "Use of two URLs is not compatible with mergeinfo modification"); 
            SVNErrorManager.error(err);
        }
        
        if (depth == SVNDepth.UNKNOWN) {
            depth = targetEntry.getDepth();
        }
        
        myIsForce = force;
        myIsDryRun = dryRun;
        myIsRecordOnly = recordOnly;
        myIsIgnoreAncestry = ignoreAncestry;
        myIsSameRepository = sameRepository;
        myIsMergeInfoCapable = false;
        myAreSourcesAncestral = sourcesAncestral;
        myIsTargetMissingChild = false;
        myIsSingleFileMerge = false;
        myTarget = target;
        myIsFirstRange = true;
        myWorkingMergeInfo = null;
        myNotificationsNumber = 0;
        myOperativeNotificationsNumber = 0;
        myCurrentAncestorIndex = -1;
        myMergedPaths = null;
        mySkippedPaths = null;
        myAddedPaths = null;
        myChildrenWithMergeInfo = null;
        myHasExistingMergeInfo = false;
        
        boolean checkedMergeInfoCapability = false;
        for (int i = 0; i < mergeSources.size(); i++) {
            MergeSource mergeSource = (MergeSource) mergeSources.get(i);
            SVNURL url1 = mergeSource.myURL1;
            SVNURL url2 = mergeSource.myURL2;
            long revision1 = mergeSource.myRevision1;
            long revision2 = mergeSource.myRevision2;
            if (revision1 == revision2 && mergeSource.myURL1.equals(mergeSource.myURL2)) {
                return;
            }
            
            try {
                myRepository1 = createRepository(url1, false);
                myRepository2 = createRepository(url2, false);
                myIsTargetHasDummyMergeRange = false;
                myURL = url2;
                myAddedPath = null;
                myConflictedPaths = null;
                myDryRunDeletions = dryRun ? new HashMap() : null;
                myIsOperativeMerge = false;
                myIsAddNecessitatedMerge = false;
                if (i == 0) {
                	myWorkingMergeInfo = new HashMap();
                }
                if (i > 0) {
                	myIsFirstRange = false;
                }
                if (!checkedMergeInfoCapability) {
                	myIsMergeInfoCapable = myRepository1.hasCapability(SVNCapability.MERGE_INFO);
                	checkedMergeInfoCapability = true;
                }
                
                if (myIsSameRepository && recordOnly) {
                    SVNURL mergeSourceURL = revision1 < revision2 ? url2 : url1;
                    SVNMergeRange range = new SVNMergeRange(revision1, revision2, true);
                    recordMergeInfoForRecordOnlyMerge(mergeSourceURL, range, targetEntry);
                    continue;
                }
                
                if (targetEntry.isFile()) {
                    doFileMerge2(url1, revision1, url2, revision2, target, adminArea, sourcesRelated);
                } else if (targetEntry.isDirectory()) {
                    doDirectoryMerge2(url1, revision1, url2, revision2, targetEntry, adminArea, depth);
                }
                
                if (!dryRun && myIsOperativeMerge) {
                    elideMergeInfo(myWCAccess, target, targetEntry, null);
                }
                
                if (!dryRun && !myIsOperativeMerge && myWorkingMergeInfo != null) {
                	for (Iterator workingMergeInfoIter = myWorkingMergeInfo.keySet().iterator(); 
                	workingMergeInfoIter.hasNext();) {
                		File path = (File) workingMergeInfoIter.next();
                		String workingMergeInfo = (String) myWorkingMergeInfo.get(path);
                		try {
                			SVNPropertiesManager.setProperty(myWCAccess, path, SVNProperty.MERGE_INFO, 
                					SVNPropertyValue.create(workingMergeInfo), true); 
                		} catch (SVNException svne) {
                			if (svne.getErrorMessage().getErrorCode() == SVNErrorCode.WC_NOT_LOCKED) {
                				continue;
                			}
                			throw svne;
                		}
                	}
                }
            } finally {
                if (myRepository1 != null) {
                    myRepository1.closeSession();
                }
                if (myRepository2 != null) {
                    myRepository2.closeSession();
                }
            }
        }
    }

    /**
     * @deprecated
     */
    protected void doFileMerge(SVNURL url1, long revision1, SVNURL url2, long revision2, 
            File targetWCPath, SVNAdminArea adminArea, boolean sourcesRelated) throws SVNException {
        boolean isRollBack = revision1 > revision2;
        SVNURL primaryURL = isRollBack ? url1 : url2;
        boolean honorMergeInfo = isHonorMergeInfo();
        boolean recordMergeInfo = isRecordMergeInfo();
        myIsSingleFileMerge = true;
        boolean[] indirect = { false };
        Map targetMergeInfo = null;
        String mergeInfoPath = null;
        SVNMergeRangeList remainingRangeList = null;
        SVNErrorMessage error = null;
        
        myWCAccess.probeTry(targetWCPath, true, SVNWCAccess.INFINITE_DEPTH);
        SVNEntry entry = myWCAccess.getVersionedEntry(targetWCPath, false);
        
        SVNMergeRange range = new SVNMergeRange(revision1, revision2, true);
        if (honorMergeInfo) {
            myRepository1.setLocation(entry.getSVNURL(), false);
            Map[] fullMergeInfo = getFullMergeInfo(entry, indirect, SVNMergeInfoInheritance.INHERITED, 
                    myRepository1, targetWCPath, Math.max(revision1, revision2), Math.min(revision1, revision2));
            targetMergeInfo = fullMergeInfo[0];
            Map implicitMergeInfo = fullMergeInfo[1];
            myRepository1.setLocation(url1, false);
            SVNURL sourceRootURL = myRepository1.getRepositoryRoot(true);
            mergeInfoPath = getPathRelativeToRoot(null, primaryURL, sourceRootURL, null, null);
            remainingRangeList = calculateRemainingRanges(sourceRootURL, url1, revision1, url2, revision2, true, 
                    targetMergeInfo, implicitMergeInfo, entry, myRepository1);
        } else {
            remainingRangeList = new SVNMergeRangeList(range);
        }
        
        SVNMergeRange[] remainingRanges = remainingRangeList.getRanges();
        SVNMergeCallback callback = new SVNMergeCallback(adminArea, myURL, myIsForce, myIsDryRun, 
                getMergeOptions(), myConflictedPaths);

        for (int i = 0; i < remainingRanges.length; i++) {
            SVNMergeRange nextRange = remainingRanges[i];
            this.handleEvent(SVNEventFactory.createSVNEvent(targetWCPath, SVNNodeKind.UNKNOWN, null, 
                    SVNRepository.INVALID_REVISION, SVNEventAction.MERGE_BEGIN, null, null, 
                    myAreSourcesAncestral ? nextRange : null), ISVNEventHandler.UNKNOWN);

            SVNProperties props1 = new SVNProperties();
            SVNProperties props2 = new SVNProperties();
            File f1 = null;
            File f2 = null;

            String name = targetWCPath.getName();
            String mimeType2;
            String mimeType1;
            SVNStatusType[] mergeResult;

            try {
                f1 = loadFile(myRepository1, nextRange.getStartRevision(), props1, adminArea);
                f2 = loadFile(myRepository2, nextRange.getEndRevision(), props2, adminArea);

                mimeType1 = props1.getStringValue(SVNProperty.MIME_TYPE);
                mimeType2 = props2.getStringValue(SVNProperty.MIME_TYPE);
                props1 = filterProperties(props1, true, false, false);
                props2 = filterProperties(props2, true, false, false);

                SVNProperties propsDiff = computePropsDiff(props1, props2);
                
                if (!(myIsIgnoreAncestry || sourcesRelated)) {
                    SVNStatusType cstatus = callback.fileDeleted(name, f1, f2, mimeType1, 
                            mimeType2, props1);
                    notifySingleFileMerge(targetWCPath, SVNEventAction.UPDATE_DELETE, cstatus, 
                    		SVNStatusType.UNKNOWN);
                    
                    mergeResult = callback.fileAdded(name, f1, f2, nextRange.getStartRevision(), 
                                                     nextRange.getEndRevision(), mimeType1, mimeType2, 
                                                     props1, propsDiff);
                    
                    notifySingleFileMerge(targetWCPath, SVNEventAction.UPDATE_ADD, 
                                    mergeResult[0], mergeResult[1]);
                } else {
                    mergeResult = callback.fileChanged(name, f1, f2, nextRange.getStartRevision(), 
                                                       nextRange.getEndRevision(), mimeType1, 
                                                       mimeType2, props1, propsDiff);
                    notifySingleFileMerge(targetWCPath, SVNEventAction.UPDATE_UPDATE, 
                                    mergeResult[0], mergeResult[1]);
                }
            } finally {
                SVNFileUtil.deleteAll(f1, null);
                SVNFileUtil.deleteAll(f2, null);
            }
            
            if (i < remainingRanges.length - 1 && myConflictedPaths != null && !myConflictedPaths.isEmpty()) {
                error = makeMergeConflictError(targetWCPath, nextRange);
                break;
            }
        }
        
        if (recordMergeInfo && remainingRanges.length > 0) {
            Map merges = determinePerformedMerges(targetWCPath, range, SVNDepth.INFINITY);
            if (myIsOperativeMerge) {
                if (indirect[0]) {
                    SVNPropertiesManager.recordWCMergeInfo(targetWCPath, targetMergeInfo, myWCAccess);
                }
                updateWCMergeInfo(targetWCPath, mergeInfoPath, entry, merges, isRollBack);
            }
        }

        sleepForTimeStamp();
        if (error != null) {
            SVNErrorManager.error(error);
        }
    }

    protected void doFileMerge2(SVNURL url1, long revision1, SVNURL url2, long revision2, 
            File targetWCPath, SVNAdminArea adminArea, boolean sourcesRelated) throws SVNException {
        boolean isRollBack = revision1 > revision2;
        SVNURL primaryURL = isRollBack ? url1 : url2;
        boolean honorMergeInfo = isHonorMergeInfo();
        boolean recordMergeInfo = isRecordMergeInfo();
        myIsSingleFileMerge = true;
        boolean[] indirect = { false };
        Map targetMergeInfo = null;
        String mergeInfoPath = null;
        SVNMergeRangeList remainingRangeList = null;
        SVNErrorMessage error = null;
        
        myWCAccess.probeTry(targetWCPath, true, SVNWCAccess.INFINITE_DEPTH);
        SVNEntry entry = myWCAccess.getVersionedEntry(targetWCPath, false);
        
        SVNMergeRange range = new SVNMergeRange(revision1, revision2, true);
        if (honorMergeInfo) {
            myRepository1.setLocation(entry.getSVNURL(), false);
            Map[] fullMergeInfo = getFullMergeInfo(entry, indirect, SVNMergeInfoInheritance.INHERITED, 
                    myRepository1, targetWCPath, Math.max(revision1, revision2), Math.min(revision1, revision2));
            targetMergeInfo = fullMergeInfo[0];
            Map implicitMergeInfo = fullMergeInfo[1];
            
            if (myIsFirstRange) {
            	String workingMergeInfoPropValue = null;
            	if (!indirect[0] && targetMergeInfo != null) {
            		workingMergeInfoPropValue = SVNMergeInfoManager.formatMergeInfoToString(targetMergeInfo);
            	}
            	myWorkingMergeInfo.put(targetWCPath, workingMergeInfoPropValue);
            }
            myRepository1.setLocation(url1, false);
            SVNURL sourceRootURL = myRepository1.getRepositoryRoot(true);
            mergeInfoPath = getPathRelativeToRoot(null, primaryURL, sourceRootURL, null, null);
            remainingRangeList = calculateRemainingRanges(sourceRootURL, url1, revision1, url2, revision2, true, 
                    targetMergeInfo, implicitMergeInfo, entry, myRepository1);
        } else {
            remainingRangeList = new SVNMergeRangeList(range);
        }
        
        SVNMergeRange[] remainingRanges = remainingRangeList.getRanges();
        SVNMergeCallback callback = new SVNMergeCallback(adminArea, myURL, myIsForce, myIsDryRun, 
                getMergeOptions(), myConflictedPaths);

        for (int i = 0; i < remainingRanges.length; i++) {
            SVNMergeRange nextRange = remainingRanges[i];
            this.handleEvent(SVNEventFactory.createSVNEvent(targetWCPath, SVNNodeKind.UNKNOWN, null, 
                    SVNRepository.INVALID_REVISION, SVNEventAction.MERGE_BEGIN, null, null, 
                    myAreSourcesAncestral ? nextRange : null), ISVNEventHandler.UNKNOWN);

            SVNProperties props1 = new SVNProperties();
            SVNProperties props2 = new SVNProperties();
            File f1 = null;
            File f2 = null;

            String name = targetWCPath.getName();
            String mimeType2;
            String mimeType1;
            SVNStatusType[] mergeResult;

            try {
                f1 = loadFile(myRepository1, nextRange.getStartRevision(), props1, adminArea);
                f2 = loadFile(myRepository2, nextRange.getEndRevision(), props2, adminArea);

                mimeType1 = props1.getStringValue(SVNProperty.MIME_TYPE);
                mimeType2 = props2.getStringValue(SVNProperty.MIME_TYPE);
                props1 = filterProperties(props1, true, false, false);
                props2 = filterProperties(props2, true, false, false);

                SVNProperties propsDiff = computePropsDiff(props1, props2);
                
                if (!(myIsIgnoreAncestry || sourcesRelated)) {
                    SVNStatusType cstatus = callback.fileDeleted(name, f1, f2, mimeType1, 
                            mimeType2, props1);
                    notifySingleFileMerge(targetWCPath, SVNEventAction.UPDATE_DELETE, cstatus, 
                    		SVNStatusType.UNKNOWN);
                    
                    mergeResult = callback.fileAdded(name, f1, f2, nextRange.getStartRevision(), 
                                                     nextRange.getEndRevision(), mimeType1, mimeType2, 
                                                     props1, propsDiff);
                    
                    notifySingleFileMerge(targetWCPath, SVNEventAction.UPDATE_ADD, 
                                    mergeResult[0], mergeResult[1]);
                } else {
                    mergeResult = callback.fileChanged(name, f1, f2, nextRange.getStartRevision(), 
                                                       nextRange.getEndRevision(), mimeType1, 
                                                       mimeType2, props1, propsDiff);
                    notifySingleFileMerge(targetWCPath, SVNEventAction.UPDATE_UPDATE, 
                                    mergeResult[0], mergeResult[1]);
                }
            } finally {
                SVNFileUtil.deleteAll(f1, null);
                SVNFileUtil.deleteAll(f2, null);
            }
            
            if (i < remainingRanges.length - 1 && myConflictedPaths != null && !myConflictedPaths.isEmpty()) {
                error = makeMergeConflictError(targetWCPath, nextRange);
                break;
            }
        }
        
        if (recordMergeInfo && remainingRanges.length > 0) {
            Map merges = determinePerformedMerges(targetWCPath, range, SVNDepth.INFINITY);
            if (myIsOperativeMerge) {
                if (indirect[0]) {
                    SVNPropertiesManager.recordWCMergeInfo(targetWCPath, targetMergeInfo, myWCAccess);
                }
                updateWCMergeInfo(targetWCPath, mergeInfoPath, entry, merges, isRollBack);
            }
        }

        sleepForTimeStamp();
        if (error != null) {
            SVNErrorManager.error(error);
        }
    }

    /**
     * @deprecated
     */
    protected void doDirectoryMerge(SVNURL url1, long revision1, SVNURL url2, long revision2,
    		SVNEntry parentEntry, SVNAdminArea adminArea, SVNDepth depth) throws SVNException {
        File targetWCPath = adminArea.getRoot();
    	boolean isRollBack = revision1 > revision2;
    	SVNURL primaryURL = isRollBack ? url1 : url2;
    	boolean honorMergeInfo = isHonorMergeInfo();
    	boolean recordMergeInfo = isRecordMergeInfo();
    	boolean sameURLs = url1.equals(url2);

    	SVNMergeCallback mergeCallback = new SVNMergeCallback(adminArea, myURL, myIsForce, myIsDryRun, 
                getMergeOptions(), myConflictedPaths);
    	
    	myChildrenWithMergeInfo = new LinkedList();
    	if (!(myAreSourcesAncestral && myIsSameRepository)) {
    		if (myAreSourcesAncestral) {
    			MergePath item = new MergePath(targetWCPath);
    			SVNMergeRange itemRange = new SVNMergeRange(revision1, revision2, true);
    			item.myRemainingRanges = new SVNMergeRangeList(itemRange);
    			myChildrenWithMergeInfo.add(item);
    		}
    		driveMergeReportEditor(targetWCPath, url1, revision1, url2, revision2, null, isRollBack, 
    				depth, adminArea, mergeCallback, null);
    		return;
    	}
    	
    	SVNRepository repository = isRollBack ? myRepository1 : myRepository2;
    	SVNURL sourceRootURL = repository.getRepositoryRoot(true);
    	String mergeInfoPath = getPathRelativeToRoot(null, primaryURL, sourceRootURL, null, null);
    	myChildrenWithMergeInfo = getMergeInfoPaths(myChildrenWithMergeInfo, mergeInfoPath, parentEntry, 
    			sourceRootURL, url1, url2, revision1, revision2, repository, depth);

    	MergePath targetMergePath = (MergePath) myChildrenWithMergeInfo.get(0);
        myIsTargetMissingChild = targetMergePath.myHasMissingChildren;
        boolean inheritable = !myIsTargetMissingChild && (depth == SVNDepth.INFINITY || 
        		depth == SVNDepth.IMMEDIATES);
        
        populateRemainingRanges(myChildrenWithMergeInfo, sourceRootURL, url1, revision1, url2, revision2, 
        		inheritable, honorMergeInfo, repository, mergeInfoPath);
        
        SVNMergeRange range = null;
        SVNRemoteDiffEditor editor = null;
        SVNErrorMessage err = null;
        if (honorMergeInfo) {
        	long startRev = getMostInclusiveStartRevision(myChildrenWithMergeInfo, isRollBack);
        	if (!SVNRevision.isValidRevisionNumber(startRev)) {
        		startRev = revision1;
        	}
        	long endRev = getYoungestEndRevision(myChildrenWithMergeInfo, isRollBack);
        	range = new SVNMergeRange(startRev, revision2, inheritable);
        	
        	while (SVNRevision.isValidRevisionNumber(endRev)) {
        		SVNURL realURL1 = url1;
        		SVNURL realURL2 = url2;
        		SVNURL oldURL1 = null;
        		SVNURL oldURL2 = null;
        		long nextEndRev = SVNRepository.INVALID_REVISION;
        		
        		sliceRemainingRanges(myChildrenWithMergeInfo, isRollBack, endRev);
        		myCurrentAncestorIndex = -1;
        		if (!sameURLs) {
        			if (isRollBack && endRev != revision2) {
        				realURL2 = url1;
        				oldURL2 = ensureSessionURL(myRepository2, realURL2);
        			}
        			if (!isRollBack && startRev != revision1) {
        				realURL1 = url2;
        				oldURL1 = ensureSessionURL(myRepository1, realURL1);
        			}
        		}
        		
        		try {
        			editor = driveMergeReportEditor(myTarget, realURL1, startRev, realURL2, endRev, 
        					myChildrenWithMergeInfo, isRollBack, depth, adminArea, mergeCallback, editor);
        		} finally {
            		if (oldURL1 != null) {
            			myRepository1.setLocation(oldURL1, false);
            		}
            		if (oldURL2 != null) {
            			myRepository2.setLocation(oldURL2, false);
            		}
        		}
        		
        		removeFirstRangeFromRemainingRanges(myChildrenWithMergeInfo);
        		nextEndRev = getYoungestEndRevision(myChildrenWithMergeInfo, isRollBack);
        		if (SVNRevision.isValidRevisionNumber(nextEndRev) && myConflictedPaths != null && 
        				!myConflictedPaths.isEmpty()) {
        			SVNMergeRange conflictedRange = new SVNMergeRange(startRev, endRev, false);
        			err = makeMergeConflictError(myTarget, conflictedRange);
        			range.setEndRevision(endRev);
        			break;
        		}
        		startRev = endRev;
        		endRev = nextEndRev;
        	}
        } else {
        	range = new SVNMergeRange(revision1, revision2, inheritable);
        	editor = driveMergeReportEditor(myTarget, url1, revision1, url2, revision2, null, isRollBack, 
        			depth, adminArea, mergeCallback, editor);
        }

        if (recordMergeInfo) {
        	removeAbsentChildren(myTarget, myChildrenWithMergeInfo);
        	Map merges = determinePerformedMerges(myTarget, range, depth);
        	if (!myIsOperativeMerge) {
        		if (myIsOverrideSet) {
        			for (Iterator childrenIter = myChildrenWithMergeInfo.iterator(); childrenIter.hasNext();) {
						MergePath child = (MergePath) childrenIter.next();
						SVNPropertiesManager.setProperty(myWCAccess, child.myPath, SVNProperty.MERGE_INFO, 
								SVNPropertyValue.create(child.myMergeInfoPropValue), true);
					}
        		}
        		if (err != null) {
        			SVNErrorManager.error(err);
        		}
        		return;
        	}
        	recordMergeInfoOnMergedChildren(depth);
        	updateWCMergeInfo(myTarget, mergeInfoPath, parentEntry, merges, isRollBack);
        	for (int i = 0; i < myChildrenWithMergeInfo.size(); i++) {
				MergePath child = (MergePath) myChildrenWithMergeInfo.get(i);
				if (child == null || child.myIsAbsent) {
					continue;
				}
				
				String childReposPath = null;
				if (child.myPath.equals(myTarget)) {
					childReposPath = ""; 
				} else {
                    childReposPath = SVNPathUtil.getRelativePath(myTarget.getAbsolutePath(), 
                    		child.myPath.getAbsolutePath());
				}
                
				SVNEntry childEntry = myWCAccess.getVersionedEntry(child.myPath, false);
                String childMergeSourcePath = SVNPathUtil.getAbsolutePath(SVNPathUtil.append(mergeInfoPath, 
                		childReposPath));
				if (myIsOperativeMerge) {
                	Map childMerges = new TreeMap();
                    SVNMergeRange childMergeRange = new SVNMergeRange(range.getStartRevision(), 
                            range.getEndRevision(), childEntry.isFile() ? true : (!child.myHasMissingChildren && 
                                    (depth == SVNDepth.INFINITY || depth == SVNDepth.IMMEDIATES)));
                    SVNMergeRangeList childMergeRangeList = new SVNMergeRangeList(childMergeRange);
                    childMerges.put(child.myPath, childMergeRangeList);
                    if (child.myIsIndirectMergeInfo) {
                        SVNPropertiesManager.recordWCMergeInfo(child.myPath, child.myPreMergeMergeInfo, 
                                myWCAccess);
                    }
                    updateWCMergeInfo(child.myPath, childMergeSourcePath, childEntry, childMerges, isRollBack);
                }

				markMergeInfoAsInheritableForARange(child.myPath, childMergeSourcePath, 
						child.myPreMergeMergeInfo, range, myChildrenWithMergeInfo, true, i);
                if (i > 0) {
                    elideMergeInfo(myWCAccess, child.myPath, childEntry, myTarget);
                }
        	}
        }
        
        if (err != null) {
            SVNErrorManager.error(err);
        }
    }

    protected void doDirectoryMerge2(SVNURL url1, long revision1, SVNURL url2, long revision2,
    		SVNEntry parentEntry, SVNAdminArea adminArea, SVNDepth depth) throws SVNException {
        File targetWCPath = adminArea.getRoot();
    	boolean isRollBack = revision1 > revision2;
    	SVNURL primaryURL = isRollBack ? url1 : url2;
    	boolean honorMergeInfo = isHonorMergeInfo();
    	boolean recordMergeInfo = isRecordMergeInfo();
    	boolean sameURLs = url1.equals(url2);

    	SVNMergeCallback mergeCallback = new SVNMergeCallback(adminArea, myURL, myIsForce, myIsDryRun, 
                getMergeOptions(), myConflictedPaths);
    	
    	myChildrenWithMergeInfo = new LinkedList();
    	if (!(myAreSourcesAncestral && myIsSameRepository)) {
    		if (myAreSourcesAncestral) {
    			MergePath item = new MergePath(targetWCPath);
    			SVNMergeRange itemRange = new SVNMergeRange(revision1, revision2, true);
    			item.myRemainingRanges = new SVNMergeRangeList(itemRange);
    			myChildrenWithMergeInfo.add(item);
    		}
    		driveMergeReportEditor2(targetWCPath, url1, revision1, url2, revision2, null, isRollBack, 
    				depth, adminArea, mergeCallback, null);
    		return;
    	}
    	
    	SVNRepository repository = isRollBack ? myRepository1 : myRepository2;
    	SVNURL sourceRootURL = repository.getRepositoryRoot(true);
    	String mergeInfoPath = getPathRelativeToRoot(null, primaryURL, sourceRootURL, null, null);
    	myChildrenWithMergeInfo = getMergeInfoPaths2(myChildrenWithMergeInfo, mergeInfoPath, parentEntry, 
    			sourceRootURL, url1, url2, revision1, revision2, repository, depth);

    	MergePath targetMergePath = (MergePath) myChildrenWithMergeInfo.get(0);
        myIsTargetMissingChild = targetMergePath.myHasMissingChildren;
        boolean inheritable = !myIsTargetMissingChild && (depth == SVNDepth.INFINITY || 
        		depth == SVNDepth.IMMEDIATES);
        
        populateRemainingRanges(myChildrenWithMergeInfo, sourceRootURL, url1, revision1, url2, revision2, 
        		inheritable, honorMergeInfo, repository, mergeInfoPath);
        
        SVNMergeRange range = null;
        SVNRemoteDiffEditor editor = null;
        SVNErrorMessage err = null;
        if (honorMergeInfo) {
        	long startRev = getMostInclusiveStartRevision(myChildrenWithMergeInfo, isRollBack);
        	if (!SVNRevision.isValidRevisionNumber(startRev)) {
        		startRev = revision1;
        	}
        	long endRev = getYoungestEndRevision(myChildrenWithMergeInfo, isRollBack);
        	range = new SVNMergeRange(startRev, revision2, inheritable);
        	
        	while (SVNRevision.isValidRevisionNumber(endRev)) {
        		SVNURL realURL1 = url1;
        		SVNURL realURL2 = url2;
        		SVNURL oldURL1 = null;
        		SVNURL oldURL2 = null;
        		long nextEndRev = SVNRepository.INVALID_REVISION;
        		
        		sliceRemainingRanges(myChildrenWithMergeInfo, isRollBack, endRev);
        		myCurrentAncestorIndex = -1;
        		if (!sameURLs) {
        			if (isRollBack && endRev != revision2) {
        				realURL2 = url1;
        				oldURL2 = ensureSessionURL(myRepository2, realURL2);
        			}
        			if (!isRollBack && startRev != revision1) {
        				realURL1 = url2;
        				oldURL1 = ensureSessionURL(myRepository1, realURL1);
        			}
        		}
        		
        		try {
        			editor = driveMergeReportEditor2(myTarget, realURL1, startRev, realURL2, endRev, 
        					myChildrenWithMergeInfo, isRollBack, depth, adminArea, mergeCallback, editor);
        		} finally {
            		if (oldURL1 != null) {
            			myRepository1.setLocation(oldURL1, false);
            		}
            		if (oldURL2 != null) {
            			myRepository2.setLocation(oldURL2, false);
            		}
        		}
        		
        		removeFirstRangeFromRemainingRanges(myChildrenWithMergeInfo);
        		nextEndRev = getYoungestEndRevision(myChildrenWithMergeInfo, isRollBack);
        		if (SVNRevision.isValidRevisionNumber(nextEndRev) && myConflictedPaths != null && 
        				!myConflictedPaths.isEmpty()) {
        			SVNMergeRange conflictedRange = new SVNMergeRange(startRev, endRev, false);
        			err = makeMergeConflictError(myTarget, conflictedRange);
        			range.setEndRevision(endRev);
        			break;
        		}
        		startRev = endRev;
        		endRev = nextEndRev;
        	}
        } else {
        	range = new SVNMergeRange(revision1, revision2, inheritable);
        	editor = driveMergeReportEditor2(myTarget, url1, revision1, url2, revision2, null, isRollBack, 
        			depth, adminArea, mergeCallback, editor);
        }

        if (recordMergeInfo) {
        	removeAbsentChildren(myTarget, myChildrenWithMergeInfo);
        	Map merges = determinePerformedMerges2(myTarget, range, depth);
        	recordMergeInfoOnMergedChildren(depth);
        	updateWCMergeInfo2(myTarget, mergeInfoPath, parentEntry, merges, isRollBack);
        	for (int i = 0; i < myChildrenWithMergeInfo.size(); i++) {
				MergePath child = (MergePath) myChildrenWithMergeInfo.get(i);
				if (child == null || child.myIsAbsent) {
					continue;
				}
				
				String childReposPath = null;
				if (child.myPath.equals(myTarget)) {
					childReposPath = ""; 
				} else {
                    childReposPath = SVNPathUtil.getRelativePath(myTarget.getAbsolutePath(), 
                    		child.myPath.getAbsolutePath());
				}
                
				SVNEntry childEntry = myWCAccess.getVersionedEntry(child.myPath, false);
                String childMergeSourcePath = SVNPathUtil.getAbsolutePath(SVNPathUtil.append(mergeInfoPath, 
                		childReposPath));
                
            	Map childMerges = new TreeMap();
                SVNMergeRange childMergeRange = new SVNMergeRange(range.getStartRevision(), 
                        range.getEndRevision(), childEntry.isFile() ? true : (!child.myHasMissingChildren && 
                                (depth == SVNDepth.INFINITY || depth == SVNDepth.IMMEDIATES)));
                SVNMergeRangeList childMergeRangeList = new SVNMergeRangeList(childMergeRange);
                childMerges.put(child.myPath, childMergeRangeList);
                if (child.myIsIndirectMergeInfo) {
                    SVNPropertiesManager.recordWCMergeInfo(child.myPath, child.myPreMergeMergeInfo, 
                            myWCAccess);
                }
                updateWCMergeInfo2(child.myPath, childMergeSourcePath, childEntry, childMerges, isRollBack);

				markMergeInfoAsInheritableForARange(child.myPath, childMergeSourcePath, 
						child.myPreMergeMergeInfo, range, myChildrenWithMergeInfo, true, i);
                if (i > 0) {
                    elideMergeInfo(myWCAccess, child.myPath, childEntry, myTarget);
                }
        	}
        }
        
        if (myAddedPaths != null) {
        	for (Iterator addedPathsIter = myAddedPaths.iterator(); addedPathsIter.hasNext();) {
				File addedPath = (File) addedPathsIter.next();
				SVNPropertyValue addedPathParentPropValue = SVNPropertiesManager.getProperty(myWCAccess, 
						addedPath.getParentFile(), SVNProperty.MERGE_INFO);
				String addedPathParentPropValueStr = addedPathParentPropValue != null ? 
						addedPathParentPropValue.getString() : null;
				if (addedPathParentPropValueStr != null && 
						addedPathParentPropValueStr.indexOf(SVNMergeRangeList.MERGE_INFO_NONINHERITABLE_STRING) != -1) {
					String addedPathStr = addedPath.getAbsolutePath().replace(File.separatorChar, '/');
					String targetMergePathStr = targetMergePath.myPath.getAbsolutePath().replace(File.separatorChar, '/');
					String commonAncestorPath = SVNPathUtil.getCommonPathAncestor(addedPathStr, 
							targetMergePathStr);
					String relativeAddedPath = SVNPathUtil.getRelativePath(commonAncestorPath, addedPathStr);
					SVNEntry entry = myWCAccess.getVersionedEntry(addedPath, false);
					Map mergeMergeInfo = new TreeMap();
					SVNMergeRange rng = range.dup();
					if (entry.isFile()) {
						rng.setInheritable(true);
					} else {
						rng.setInheritable(!(depth == SVNDepth.INFINITY || depth == SVNDepth.IMMEDIATES));
					}
					SVNMergeRangeList rangeList = new SVNMergeRangeList(rng);
					mergeMergeInfo.put(SVNPathUtil.getAbsolutePath(SVNPathUtil.append(mergeInfoPath, 
							relativeAddedPath)), rangeList);
					boolean[] inherited = { false };
					Map addedPathMergeInfo = getWCMergeInfo(addedPath, entry, null, 
							SVNMergeInfoInheritance.EXPLICIT, false, inherited);
					if (addedPathMergeInfo != null) {
						mergeMergeInfo = SVNMergeInfoManager.mergeMergeInfos(mergeMergeInfo, addedPathMergeInfo);
					}
					SVNPropertiesManager.recordWCMergeInfo(addedPath, mergeMergeInfo, myWCAccess);
				}
        	}
        }
        
        if (err != null) {
            SVNErrorManager.error(err);
        }
    }

    /**
     * @deprecated
     */
    public void handleEvent(SVNEvent event, double progress) throws SVNException {
        boolean isOperativeNotification = false;
        if (event.getContentsStatus() == SVNStatusType.CONFLICTED || 
                event.getContentsStatus() == SVNStatusType.MERGED ||
                event.getContentsStatus() == SVNStatusType.CHANGED ||
                event.getPropertiesStatus() == SVNStatusType.CONFLICTED ||
                event.getPropertiesStatus() == SVNStatusType.MERGED ||
                event.getPropertiesStatus() == SVNStatusType.CHANGED ||
                event.getAction() == SVNEventAction.UPDATE_ADD) {
            myOperativeNotificationsNumber++;
            isOperativeNotification = true;
        }

        if (myAreSourcesAncestral) {
            myNotificationsNumber++;
            if (!myIsSingleFileMerge && isOperativeNotification) {
            	Object childrenWithMergeInfoArray[] = null;
            	if (myChildrenWithMergeInfo != null) {
            		childrenWithMergeInfoArray = myChildrenWithMergeInfo.toArray();
            	}
            	int newNearestAncestorIndex = findNearestAncestor(childrenWithMergeInfoArray, event.getFile());
                if (newNearestAncestorIndex != myCurrentAncestorIndex) {
                	MergePath child = (MergePath) childrenWithMergeInfoArray[newNearestAncestorIndex];
                    myCurrentAncestorIndex = newNearestAncestorIndex;
                    if (!child.myIsAbsent && !child.myRemainingRanges.isEmpty() &&
                    		!(newNearestAncestorIndex == 0 && myIsTargetHasDummyMergeRange)) {
                        SVNMergeRange ranges[] = child.myRemainingRanges.getRanges();
                    	SVNEvent mergeBeginEvent = SVNEventFactory.createSVNEvent(child.myPath, 
                        		SVNNodeKind.UNKNOWN, null, SVNRepository.INVALID_REVISION, 
                        		SVNEventAction.MERGE_BEGIN, null, null, ranges[0]);
                    	super.handleEvent(mergeBeginEvent, ISVNEventHandler.UNKNOWN);
                    }
                }
            }
            
            if (event.getContentsStatus() == SVNStatusType.MERGED ||
            		event.getContentsStatus() == SVNStatusType.CHANGED ||
            		event.getPropertiesStatus() == SVNStatusType.MERGED ||
            		event.getPropertiesStatus() == SVNStatusType.CHANGED ||
            		event.getAction() == SVNEventAction.UPDATE_ADD) {
            	File mergedPath = event.getFile();
            	if (myMergedPaths == null) {
            		myMergedPaths = new LinkedList();
            	}
            	myMergedPaths.add(mergedPath);
            }
            
            if (event.getAction() == SVNEventAction.SKIP) {
                File skippedPath = event.getFile();
                if (mySkippedPaths == null) {
                    mySkippedPaths = new LinkedList();
                }
                mySkippedPaths.add(skippedPath);
            }
        } else if (!myIsSingleFileMerge && myOperativeNotificationsNumber == 1 && isOperativeNotification) {
        	SVNEvent mergeBeginEvent = SVNEventFactory.createSVNEvent(myTarget, 
            		SVNNodeKind.UNKNOWN, null, SVNRepository.INVALID_REVISION, 
            		SVNEventAction.MERGE_BEGIN, null, null, null);
        	super.handleEvent(mergeBeginEvent, ISVNEventHandler.UNKNOWN);
        }
        
        super.handleEvent(event, progress);
    }

    public void handleEvent2(SVNEvent event, double progress) throws SVNException {
        boolean isOperativeNotification = false;
        if (event.getContentsStatus() == SVNStatusType.CONFLICTED || 
                event.getContentsStatus() == SVNStatusType.MERGED ||
                event.getContentsStatus() == SVNStatusType.CHANGED ||
                event.getPropertiesStatus() == SVNStatusType.CONFLICTED ||
                event.getPropertiesStatus() == SVNStatusType.MERGED ||
                event.getPropertiesStatus() == SVNStatusType.CHANGED ||
                event.getAction() == SVNEventAction.UPDATE_ADD) {
            myOperativeNotificationsNumber++;
            isOperativeNotification = true;
        }

        if (myAreSourcesAncestral) {
            myNotificationsNumber++;
            if (!myIsSingleFileMerge && isOperativeNotification) {
            	Object childrenWithMergeInfoArray[] = null;
            	if (myChildrenWithMergeInfo != null) {
            		childrenWithMergeInfoArray = myChildrenWithMergeInfo.toArray();
            	}
            	int newNearestAncestorIndex = findNearestAncestor(childrenWithMergeInfoArray, event.getFile());
                if (newNearestAncestorIndex != myCurrentAncestorIndex) {
                	MergePath child = (MergePath) childrenWithMergeInfoArray[newNearestAncestorIndex];
                    myCurrentAncestorIndex = newNearestAncestorIndex;
                    if (!child.myIsAbsent && !child.myRemainingRanges.isEmpty() &&
                    		!(newNearestAncestorIndex == 0 && myIsTargetHasDummyMergeRange)) {
                        SVNMergeRange ranges[] = child.myRemainingRanges.getRanges();
                    	SVNEvent mergeBeginEvent = SVNEventFactory.createSVNEvent(child.myPath, 
                        		SVNNodeKind.UNKNOWN, null, SVNRepository.INVALID_REVISION, 
                        		SVNEventAction.MERGE_BEGIN, null, null, ranges[0]);
                    	super.handleEvent(mergeBeginEvent, ISVNEventHandler.UNKNOWN);
                    }
                }
            }
            
            if (event.getContentsStatus() == SVNStatusType.MERGED ||
            		event.getContentsStatus() == SVNStatusType.CHANGED ||
            		event.getPropertiesStatus() == SVNStatusType.MERGED ||
            		event.getPropertiesStatus() == SVNStatusType.CHANGED ||
            		event.getAction() == SVNEventAction.UPDATE_ADD) {
            	File mergedPath = event.getFile();
            	if (myMergedPaths == null) {
            		myMergedPaths = new LinkedList();
            	}
            	myMergedPaths.add(mergedPath);
            }
            
            if (event.getAction() == SVNEventAction.SKIP) {
                File skippedPath = event.getFile();
                if (mySkippedPaths == null) {
                    mySkippedPaths = new LinkedList();
                }
                mySkippedPaths.add(skippedPath);
            } else if (event.getAction() == SVNEventAction.UPDATE_ADD) {
            	boolean isRootOfAddedSubTree = false;
            	File addedPath = event.getFile();
            	if (myAddedPaths == null) {
            		isRootOfAddedSubTree = true;
            		myAddedPaths = new LinkedList();
            	} else {
            		File addedPathParent = addedPath.getParentFile();
            		isRootOfAddedSubTree = !myAddedPaths.contains(addedPathParent);
            	}
            	if (isRootOfAddedSubTree) {
            		myAddedPaths.add(addedPath);
            	}
            }
        } else if (!myIsSingleFileMerge && myOperativeNotificationsNumber == 1 && isOperativeNotification) {
        	SVNEvent mergeBeginEvent = SVNEventFactory.createSVNEvent(myTarget, 
            		SVNNodeKind.UNKNOWN, null, SVNRepository.INVALID_REVISION, 
            		SVNEventAction.MERGE_BEGIN, null, null, null);
        	super.handleEvent(mergeBeginEvent, ISVNEventHandler.UNKNOWN);
        }
        
        super.handleEvent(event, progress);
    }

    public void checkCancelled() throws SVNCancelException {
        super.checkCancelled();
    }

    protected void recordMergeInfoForRecordOnlyMerge(SVNURL url, SVNMergeRange range, 
            SVNEntry entry) throws SVNException {
        Map merges = new TreeMap();
        Map targetMergeInfo = null;
        myRepository1.setLocation(entry.getSVNURL(), true);
        boolean[] isIndirect = new boolean[1];
        isIndirect[0] = false;
        targetMergeInfo = getWCOrRepositoryMergeInfo(myWCAccess, myTarget, 
                entry, SVNMergeInfoInheritance.INHERITED, isIndirect, false, myRepository1);
        myRepository1.setLocation(url, true);
        String reposPath = getPathRelativeToRoot(null, url, null, myWCAccess, myRepository1);
        SVNMergeRangeList rangeList = new SVNMergeRangeList(range);
        merges.put(myTarget, rangeList);
        if (isIndirect[0]) {
            SVNPropertiesManager.recordWCMergeInfo(myTarget, targetMergeInfo, myWCAccess);
        }
        boolean isRollBack = range.getStartRevision() > range.getEndRevision();
        updateWCMergeInfo(myTarget, reposPath, entry, merges, isRollBack);
    }

    private void mergeCousinsAndSupplementMergeInfo(File targetWCPath, SVNEntry entry, 
    		SVNAdminArea adminArea, SVNRepository repository, SVNURL url1, long rev1, SVNURL url2, 
    		long rev2, long youngestCommonRev, SVNURL sourceReposRoot, SVNURL wcReposRoot, SVNDepth depth, 
    		boolean ignoreAncestry,	boolean force, boolean recordOnly, boolean dryRun) throws SVNException {
		SVNURL oldURL = repository.getLocation();
		List addSources = null;
		List removeSources = null;
		try {
			SVNRevision sRev = SVNRevision.create(rev1);
			SVNRevision eRev = SVNRevision.create(youngestCommonRev);
			SVNRevisionRange range = new SVNRevisionRange(sRev, eRev);
	    	List ranges = new LinkedList();
			ranges.add(range);
			repository.setLocation(url1, false);
			removeSources = normalizeMergeSources(null, url1, sourceReposRoot, sRev, ranges, repository);
			sRev = eRev;
			eRev = SVNRevision.create(rev2);
			range = new SVNRevisionRange(sRev, eRev);
			ranges.clear();
			ranges.add(range);
			repository.setLocation(url2, false);
			addSources = normalizeMergeSources(null, url2, sourceReposRoot, eRev, 
					ranges, repository);
		} finally {
			repository.setLocation(oldURL, false);
		}
		
		boolean sameRepos = sourceReposRoot.equals(wcReposRoot);  
		if (!recordOnly) {
			MergeSource fauxSource = new MergeSource();
			fauxSource.myURL1 = url1;
			fauxSource.myURL2 = url2;
			fauxSource.myRevision1 = rev1;
			fauxSource.myRevision2 = rev2;
			List fauxSources = new LinkedList();
			fauxSources.add(fauxSource);
			doMerge2(fauxSources, targetWCPath, entry, adminArea, false, true, 
					sourceReposRoot.equals(wcReposRoot), ignoreAncestry, force, dryRun, 
					false, depth);
		}
		
		doMerge2(addSources, targetWCPath, entry, adminArea, true, true, sameRepos, 
				ignoreAncestry, force, dryRun, true, depth);
		doMerge2(removeSources, targetWCPath, entry, adminArea, true, true, sameRepos, 
				ignoreAncestry, force, dryRun, true, depth);
    }
    
    private boolean isHonorMergeInfo() {
    	return myIsMergeInfoCapable && myAreSourcesAncestral && myIsSameRepository && !myIsIgnoreAncestry;
    }

    private boolean isRecordMergeInfo() {
    	return myIsMergeInfoCapable && myAreSourcesAncestral && myIsSameRepository && !myIsDryRun;
    }
    
    private List normalizeMergeSources(File source, SVNURL sourceURL, SVNURL sourceRootURL, 
    		SVNRevision pegRevision, Collection rangesToMerge, SVNRepository repository) throws SVNException {
    	long youngestRevision[] = { SVNRepository.INVALID_REVISION };
    	long pegRevNum = getRevisionNumber(pegRevision, youngestRevision, repository, source);
    	if (!SVNRevision.isValidRevisionNumber(pegRevNum)) {
    		SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_BAD_REVISION);
    		SVNErrorManager.error(err);
    	}
    	
    	List mergeRanges = new ArrayList(rangesToMerge.size());
    	for (Iterator rangesIter = rangesToMerge.iterator(); rangesIter.hasNext();) {
			SVNRevisionRange revRange = (SVNRevisionRange) rangesIter.next();
			SVNRevision rangeStart = revRange.getStartRevision();
			SVNRevision rangeEnd = revRange.getEndRevision();
			
			if (!rangeStart.isValid() || !rangeEnd.isValid()) {
				SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_BAD_REVISION, 
						"Not all required revisions are specified");
				SVNErrorManager.error(err);
			}
			
			long rangeStartRev = getRevisionNumber(rangeStart, youngestRevision, repository, source); 
			long rangeEndRev = getRevisionNumber(rangeEnd, youngestRevision, repository, source);
			if (rangeStartRev != rangeEndRev) {
				SVNMergeRange range = new SVNMergeRange(rangeStartRev, rangeEndRev, true);
				mergeRanges.add(range);
			}
    	}
    	
    	SVNMergeRangeList mergeRangesList = SVNMergeRangeList.fromCollection(mergeRanges);
    	mergeRangesList = mergeRangesList.compactMergeRanges();
    	mergeRanges = mergeRangesList.getRangesAsList();
    	if (mergeRanges.isEmpty()) {
    		return mergeRanges;
    	}
    	
    	long oldestRequestedRev = SVNRepository.INVALID_REVISION;
    	long youngestRequestedRev = SVNRepository.INVALID_REVISION;
    	for (Iterator rangesIter = mergeRanges.iterator(); rangesIter.hasNext();) {
			SVNMergeRange range = (SVNMergeRange) rangesIter.next();
			long minRev = Math.min(range.getStartRevision(), range.getEndRevision());
			long maxRev = Math.max(range.getStartRevision(), range.getEndRevision());
			
			if (!SVNRevision.isValidRevisionNumber(oldestRequestedRev) || minRev < oldestRequestedRev) {
				oldestRequestedRev = minRev;
			}
			if (!SVNRevision.isValidRevisionNumber(youngestRequestedRev) || maxRev > youngestRequestedRev) {
				youngestRequestedRev = maxRev;
			}
		}
    	
    	if (pegRevNum < youngestRequestedRev) {
            getLocations(sourceURL, null, repository, SVNRevision.create(pegRevNum), 
                    SVNRevision.create(youngestRequestedRev), SVNRevision.UNDEFINED);
            pegRevNum = youngestRequestedRev;
    	}

    	Collection segments = repository.getLocationSegments("", pegRevNum, youngestRequestedRev, 
    			oldestRequestedRev);
		SVNLocationSegment[] segmentsArray = (SVNLocationSegment[]) segments.toArray(new SVNLocationSegment[segments.size()]);
    	
		List resultMergeSources = new LinkedList();
    	for (Iterator rangesIter = mergeRanges.iterator(); rangesIter.hasNext();) {
			SVNMergeRange range = (SVNMergeRange) rangesIter.next();
			List mergeSources = combineRangeWithSegments(range, segmentsArray, sourceRootURL);
			resultMergeSources.addAll(mergeSources);
    	}
    	return resultMergeSources;
    }
    
    private List combineRangeWithSegments(SVNMergeRange range, SVNLocationSegment[] segments, 
    		SVNURL sourceRootURL) throws SVNException {
    	long minRev = Math.min(range.getStartRevision(), range.getEndRevision()) + 1;
    	long maxRev = Math.max(range.getStartRevision(), range.getEndRevision());
    	boolean subtractive = range.getStartRevision() > range.getEndRevision();
    	List mergeSources = new LinkedList();
    	for (int i = 0; i < segments.length; i++) {
			SVNLocationSegment segment = segments[i];
			if (segment.getEndRevision() < minRev || segment.getStartRevision() > maxRev || 
					segment.getPath() == null) {
				continue;
			}
			
			String path1 = null;
			long rev1 = Math.max(segment.getStartRevision(), minRev) - 1;
			if (minRev <= segment.getStartRevision()) {
				if (i > 0) {
					path1 = segments[i - 1].getPath();
				}
				if (path1 == null && i > 1) {
					path1 = segments[i - 2].getPath();
					rev1 = segments[i - 2].getEndRevision();
				}
			} else {
				path1 = segment.getPath();
			}
			
			if (path1 == null || segment.getPath() == null) {
				continue;
			}
			
			MergeSource mergeSource = new MergeSource();
			mergeSource.myURL1 = sourceRootURL.appendPath(path1, false);
			mergeSource.myURL2 = sourceRootURL.appendPath(segment.getPath(), false);
			mergeSource.myRevision1 = rev1;
			mergeSource.myRevision2 = Math.min(segment.getEndRevision(), maxRev);
			if (subtractive) {
				long tmpRev = mergeSource.myRevision1;
				SVNURL tmpURL = mergeSource.myURL1;
				mergeSource.myRevision1 = mergeSource.myRevision2;
				mergeSource.myURL1 = mergeSource.myURL2;
				mergeSource.myRevision2 = tmpRev;
				mergeSource.myURL2 = tmpURL;
			}
			mergeSources.add(mergeSource);
    	}
    	
    	if (subtractive && !mergeSources.isEmpty()) {
    		Collections.sort(mergeSources, new Comparator() {
				public int compare(Object o1, Object o2) {
					MergeSource source1 = (MergeSource) o1;
					MergeSource source2 = (MergeSource) o2;
					long src1Rev1 = source1.myRevision1;
					long src2Rev1 = source2.myRevision1;
					if (src1Rev1 == src2Rev1) {
						return 0;
					}
					return src1Rev1 < src2Rev1 ? 1 : -1;
				}
    		});
    	}
    	return mergeSources;
    }
    
    /**
     * @deprecated
     */
    private SVNLocationEntry getYoungestCommonAncestor(File path1, SVNURL url1, SVNRevision revision1, 
    		File path2, SVNURL url2, SVNRevision revision2) throws SVNException {
    	Map history1 = getHistoryAsMergeInfo(url1, path1, revision1, SVNRepository.INVALID_REVISION, 
    			SVNRepository.INVALID_REVISION, null, null);
    	Map history2 = getHistoryAsMergeInfo(url2, path2, revision2, SVNRepository.INVALID_REVISION, 
    			SVNRepository.INVALID_REVISION, null, null);
    	
    	long youngestCommonRevision = SVNRepository.INVALID_REVISION;
    	String youngestCommonPath = null;
    	for (Iterator historyIter = history1.entrySet().iterator(); historyIter.hasNext();) {
    		Map.Entry historyEntry = (Map.Entry) historyIter.next();
    		String path = (String) historyEntry.getKey();
    		SVNMergeRangeList ranges1 = (SVNMergeRangeList) historyEntry.getValue();
    		SVNMergeRangeList ranges2 = (SVNMergeRangeList) history2.get(path);
    		if (ranges2 != null) {
    			SVNMergeRangeList commonList = ranges2.intersect(ranges1);
    			if (!commonList.isEmpty()) {
    				SVNMergeRange commonRanges[] = commonList.getRanges();
    				SVNMergeRange youngestCommonRange = commonRanges[commonRanges.length - 1];
    				if (!SVNRevision.isValidRevisionNumber(youngestCommonRevision) || 
    						youngestCommonRange.getEndRevision() > youngestCommonRevision) {
    					youngestCommonRevision = youngestCommonRange.getEndRevision();
    					youngestCommonPath = path;
    				}
    			}
    		}
    	}
    	return new SVNLocationEntry(youngestCommonRevision, youngestCommonPath);
    }

    private SVNLocationEntry getYoungestCommonAncestor2(File path1, SVNURL url1, long revision1, 
    		File path2, SVNURL url2, long revision2) throws SVNException {
    	Map history1 = getHistoryAsMergeInfo(url1, path1, SVNRevision.create(revision1), 
    			SVNRepository.INVALID_REVISION, SVNRepository.INVALID_REVISION, null, null);
    	Map history2 = getHistoryAsMergeInfo(url2, path2, SVNRevision.create(revision2), 
    			SVNRepository.INVALID_REVISION,	SVNRepository.INVALID_REVISION, null, null);
    	
    	long youngestCommonRevision = SVNRepository.INVALID_REVISION;
    	String youngestCommonPath = null;
    	for (Iterator historyIter = history1.entrySet().iterator(); historyIter.hasNext();) {
    		Map.Entry historyEntry = (Map.Entry) historyIter.next();
    		String path = (String) historyEntry.getKey();
    		SVNMergeRangeList ranges1 = (SVNMergeRangeList) historyEntry.getValue();
    		SVNMergeRangeList ranges2 = (SVNMergeRangeList) history2.get(path);
    		if (ranges2 != null) {
    			SVNMergeRangeList commonList = ranges2.intersect(ranges1);
    			if (!commonList.isEmpty()) {
    				SVNMergeRange commonRanges[] = commonList.getRanges();
    				SVNMergeRange youngestCommonRange = commonRanges[commonRanges.length - 1];
    				if (!SVNRevision.isValidRevisionNumber(youngestCommonRevision) || 
    						youngestCommonRange.getEndRevision() > youngestCommonRevision) {
    					youngestCommonRevision = youngestCommonRange.getEndRevision();
    					youngestCommonPath = path;
    				}
    			}
    		}
    	}
    	return new SVNLocationEntry(youngestCommonRevision, youngestCommonPath);
    }

    private Map[] getFullMergeInfo(SVNEntry entry, boolean[] indirect, SVNMergeInfoInheritance inherit,
            SVNRepository repos, File target, long start, long end) throws SVNException {
        Map[] result = new Map[2];
        if (!SVNRevision.isValidRevisionNumber(start) || !SVNRevision.isValidRevisionNumber(end) || 
                start <= end) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNKNOWN, 
                    "ASSERTION FAILED in SVNMergeDriver.getFullMergeInfo()");
            SVNErrorManager.error(err);
        }
        
        //get recorded merge info
        result[0] = getWCOrRepositoryMergeInfo(myWCAccess, target, entry, inherit, indirect, false, repos);
        long[] targetRev = new long[1];
        targetRev[0] = SVNRepository.INVALID_REVISION;
        SVNURL url = deriveLocation(target, null, targetRev, SVNRevision.WORKING, repos, myWCAccess);
        if (targetRev[0] <= end) {
            result[1] = new TreeMap();//implicit merge info
            return result;
        }
        
        boolean closeSession = false;
        boolean reparent = false;
        SVNURL sessionURL = null;
        try {
            if (repos != null) {
                sessionURL = repos.getLocation();
                if (!sessionURL.equals(url)) {
                    repos.setLocation(url, false);
                    reparent = true;
                }
            } else {
                repos = createRepository(url, false);
                closeSession = true;
            }
            
            if (targetRev[0] < start) {
                getLocations(url, null, repos, SVNRevision.create(targetRev[0]), 
                        SVNRevision.create(start), SVNRevision.UNDEFINED);
                targetRev[0] = start;
            }
            result[1] = getHistoryAsMergeInfo(url, null, SVNRevision.create(targetRev[0]), start, end, 
            		repos, null);
            if (reparent) {
                repos.setLocation(sessionURL, false);
            }
        } finally {
            if (closeSession) {
                repos.closeSession();
            }
        }
        return result;
    }
    
    private int findNearestAncestor(Object[] childrenWithMergeInfoArray, File path) {
        if (childrenWithMergeInfoArray == null) {
            return 0;
        }

        int ancestorIndex = 0;
        for (int i = 0; i < childrenWithMergeInfoArray.length; i++) {
            MergePath child = (MergePath) childrenWithMergeInfoArray[i];
            String childPath = child.myPath.getAbsolutePath().replace(File.separatorChar, '/');
            String pathStr = path.getAbsolutePath().replace(File.separatorChar, '/');
            if (SVNPathUtil.isAncestor(childPath, pathStr)) {
                ancestorIndex = i;
            }
        }
        return ancestorIndex;
    }
    
    protected Map getHistoryAsMergeInfo(SVNURL url, File path, SVNRevision pegRevision, long rangeYoungest, 
            long rangeOldest, SVNRepository repos, SVNWCAccess access) throws SVNException {
        Map mergeInfo = new TreeMap();
        long[] pegRevNum = new long[1];
        pegRevNum[0] = SVNRepository.INVALID_REVISION;
        url = deriveLocation(path, url, pegRevNum, pegRevision, repos, access);
        
        boolean closeSession = false;
        try {
            if (repos == null) {
                repos = createRepository(url, false);
                closeSession = true;
            }
            if (!SVNRevision.isValidRevisionNumber(rangeYoungest)) {
                rangeYoungest = pegRevNum[0];
            }
            if (!SVNRevision.isValidRevisionNumber(rangeOldest)) {
                rangeOldest = 0;
            }
            
            Collection segments = repos.getLocationSegments("", pegRevNum[0], rangeYoungest, rangeOldest);
            for (Iterator segmentsIter = segments.iterator(); segmentsIter.hasNext();) {
                SVNLocationSegment segment = (SVNLocationSegment) segmentsIter.next();
                if (segment.getPath() == null) {
                    continue;
                }
                String sourcePath = segment.getPath();
                Collection pathRanges = (Collection) mergeInfo.get(sourcePath);
                if (pathRanges == null) {
                    pathRanges = new LinkedList();
                    mergeInfo.put(sourcePath, pathRanges);
                }
                SVNMergeRange range = new SVNMergeRange(Math.max(segment.getStartRevision() - 1, 0), 
                        segment.getEndRevision(), true);
                pathRanges.add(range);
            }
            for (Iterator mergeInfoIter = mergeInfo.entrySet().iterator(); mergeInfoIter.hasNext();) {
                Map.Entry mergeInfoEntry = (Map.Entry) mergeInfoIter.next();
                Collection pathRanges = (Collection) mergeInfoEntry.getValue();
                mergeInfoEntry.setValue(SVNMergeRangeList.fromCollection(pathRanges));
            }
            return mergeInfo;
        } finally {
            if (closeSession) {
                repos.closeSession();
            }
        }
    }

    private static SVNProperties computePropsDiff(SVNProperties props1, SVNProperties props2) {
        SVNProperties propsDiff = new SVNProperties();
        for (Iterator names = props2.nameSet().iterator(); names.hasNext();) {
            String newPropName = (String) names.next();
            if (props1.containsName(newPropName)) {
                // changed.
                SVNPropertyValue oldValue = props2.getSVNPropertyValue(newPropName);
                if (!oldValue.equals(props1.getSVNPropertyValue(newPropName))) {
                    propsDiff.put(newPropName, props2.getSVNPropertyValue(newPropName));
                }
            } else {
                // added.
                propsDiff.put(newPropName, props2.getSVNPropertyValue(newPropName));
            }
        }
        for (Iterator names = props1.nameSet().iterator(); names.hasNext();) {
            String oldPropName = (String) names.next();
            if (!props2.containsName(oldPropName)) {
                // deleted
                propsDiff.put(oldPropName, (SVNPropertyValue) null);
            }
        }
        return propsDiff;
    }

    private static SVNProperties filterProperties(SVNProperties props1, boolean leftRegular,
            boolean leftEntry, boolean leftWC) {
        SVNProperties result = new SVNProperties();
        for (Iterator names = props1.nameSet().iterator(); names.hasNext();) {
            String propName = (String) names.next();
            if (!leftEntry && propName.startsWith(SVNProperty.SVN_ENTRY_PREFIX)) {
                continue;
            }
            if (!leftWC && propName.startsWith(SVNProperty.SVN_WC_PREFIX)) {
                continue;
            }
            if (!leftRegular
                    && !(propName.startsWith(SVNProperty.SVN_ENTRY_PREFIX) || propName
                            .startsWith(SVNProperty.SVN_WC_PREFIX))) {
                continue;
            }
            result.copyValue(props1, propName);
        }
        return result;
    }
    
    private void markMergeInfoAsInheritableForARange(File target, String reposPath, Map targetMergeInfo, 
            SVNMergeRange range, List childrenWithMergeInfo, boolean sameURLs, int targetIndex) throws SVNException {
        if (targetMergeInfo != null && sameURLs && !myIsDryRun && myIsSameRepository && targetIndex >= 0) {
            MergePath mergePath = (MergePath) childrenWithMergeInfo.get(targetIndex);
            if (mergePath != null && mergePath.myHasNonInheritableMergeInfo && !mergePath.myHasMissingChildren) {
                SVNMergeRangeList inheritableRangeList = new SVNMergeRangeList(range);
                Map inheritableMerges = new TreeMap();
                inheritableMerges.put(reposPath, inheritableRangeList);
                Map merges = SVNMergeInfoManager.getInheritableMergeInfo(targetMergeInfo, 
                                                                         reposPath, 
                                                                         range.getStartRevision(), 
                                                                         range.getEndRevision());
                if (!SVNMergeInfoManager.mergeInfoEquals(merges, targetMergeInfo, false)) {
                    merges = SVNMergeInfoManager.mergeMergeInfos(merges, inheritableMerges);
                
                    SVNPropertiesManager.recordWCMergeInfo(target, merges, myWCAccess);
                }
            }
        }
    }
    
    private void recordMergeInfoOnMergedChildren(SVNDepth depth) throws SVNException {
        if (depth != SVNDepth.INFINITY && myMergedPaths != null) {
            boolean[] isIndirectChildMergeInfo = { false };
            Map childTargetMergeInfo = null;
            for (Iterator paths = myMergedPaths.iterator(); paths.hasNext();) {
                File mergedPath = (File) paths.next();
                SVNEntry childEntry = myWCAccess.getVersionedEntry(mergedPath, false);
                if ((childEntry.isDirectory() && myTarget.equals(mergedPath) && depth == SVNDepth.IMMEDIATES) ||
                        (childEntry.isFile() && depth == SVNDepth.FILES)) {
                    childTargetMergeInfo = getWCOrRepositoryMergeInfo(myWCAccess, mergedPath, 
                            childEntry, SVNMergeInfoInheritance.INHERITED, isIndirectChildMergeInfo, false, 
                            myRepository1);
                    if (isIndirectChildMergeInfo[0]) {
                        SVNPropertiesManager.recordWCMergeInfo(mergedPath, childTargetMergeInfo, myWCAccess);
                    }
                }
            }
        }
    }
    
    private void removeAbsentChildren(File targetWCPath, List childrenWithMergeInfo) {
        for (Iterator children = childrenWithMergeInfo.iterator(); children.hasNext();) {
            MergePath child = (MergePath) children.next();
            String topDir = targetWCPath.getAbsolutePath().replace(File.separatorChar, '/');
            String childPath = child.myPath.getAbsolutePath().replace(File.separatorChar, '/');
            if (child != null && (child.myIsAbsent || child.myIsScheduledForDeletion) && 
            		SVNPathUtil.isAncestor(topDir, childPath)) {
                if (mySkippedPaths != null) {
                    mySkippedPaths.remove(child.myPath);
                }
                children.remove();
            }
        }
    }
    
    private void removeFirstRangeFromRemainingRanges(List childrenWithMergeInfo) {
        for (Iterator children = childrenWithMergeInfo.iterator(); children.hasNext();) {
            MergePath child = (MergePath) children.next();
            if (child == null || child.myIsAbsent) {
                continue;
            }
            if (!child.myRemainingRanges.isEmpty()) {
                SVNMergeRange[] originalRemainingRanges = child.myRemainingRanges.getRanges();
                SVNMergeRange[] remainingRanges = new SVNMergeRange[originalRemainingRanges.length - 1];
                System.arraycopy(originalRemainingRanges, 1, remainingRanges, 0, 
                		originalRemainingRanges.length - 1);
                child.myRemainingRanges = new SVNMergeRangeList(remainingRanges);
            }
        }
    }
    
    private void sliceRemainingRanges(List childrenWithMergeInfo, boolean isRollBack, long endRevision) {
        for (Iterator children = childrenWithMergeInfo.iterator(); children.hasNext();) {
            MergePath child = (MergePath) children.next();
            if (child == null || child.myIsAbsent) {
                continue;
            }
            
            if (!child.myRemainingRanges.isEmpty()) {
                SVNMergeRange[] originalRemainingRanges = child.myRemainingRanges.getRanges();
                SVNMergeRange range = originalRemainingRanges[0];
                if ((isRollBack && range.getStartRevision() > endRevision && 
                		range.getEndRevision() < endRevision) ||
                		(!isRollBack && range.getStartRevision() < endRevision && 
                				range.getEndRevision() > endRevision)) {
                    SVNMergeRange splitRange1 = new SVNMergeRange(range.getStartRevision(), endRevision, 
                            range.isInheritable());
                    SVNMergeRange splitRange2 = new SVNMergeRange(endRevision, range.getEndRevision(), 
                            range.isInheritable());
                    SVNMergeRange[] remainingRanges = new SVNMergeRange[originalRemainingRanges.length + 1];
                    remainingRanges[0] = splitRange1;
                    remainingRanges[1] = splitRange2;
                    System.arraycopy(originalRemainingRanges, 1, remainingRanges, 2, 
                    		originalRemainingRanges.length - 1);
                    child.myRemainingRanges = new SVNMergeRangeList(remainingRanges);
                }
            }
        }
    }
    
    private long getYoungestEndRevision(List childrenWithMergeInfo, boolean isRollBack) {
    	long endRev = SVNRepository.INVALID_REVISION;
    	for (int i = 0; i < childrenWithMergeInfo.size(); i++) {
    		MergePath child = (MergePath) childrenWithMergeInfo.get(i);
    		if (child == null || child.myIsAbsent) {
    			continue;
    		}
    		if (child.myRemainingRanges.getSize() > 0) {
        		SVNMergeRange ranges[] = child.myRemainingRanges.getRanges();
        		SVNMergeRange range = ranges[0];
        		if (!SVNRevision.isValidRevisionNumber(endRev) || 
        				(isRollBack && range.getEndRevision() > endRev) ||
        				(!isRollBack && range.getEndRevision() < endRev)) {
        			endRev = range.getEndRevision();
        		}
    		}
    	}
    	return endRev;
    }

    private long getMostInclusiveStartRevision(List childrenWithMergeInfo, boolean isRollBack) {
    	long startRev = SVNRepository.INVALID_REVISION;
    	for (int i = 0; i < childrenWithMergeInfo.size(); i++) {
    		MergePath child = (MergePath) childrenWithMergeInfo.get(i);
    		if (child == null || child.myIsAbsent) {
    			continue;
    		}
    		if (child.myRemainingRanges.isEmpty()) {
    			continue;
    		}
    		SVNMergeRange ranges[] = child.myRemainingRanges.getRanges();
    		SVNMergeRange range = ranges[0];
    		if (i == 0 && range.getStartRevision() == range.getEndRevision()) {
    			continue;
    		}
    		if (!SVNRevision.isValidRevisionNumber(startRev) || 
    				(isRollBack && range.getStartRevision() > startRev) ||
    				(!isRollBack && range.getStartRevision() < startRev)) {
    			startRev = range.getStartRevision();
    		}
    	}
    	return startRev;
    }
    
    private void populateRemainingRanges(List childrenWithMergeInfo, SVNURL sourceRootURL, 
    		SVNURL url1, long revision1, SVNURL url2, long revision2, boolean inheritable, 
    		boolean honorMergeInfo,	SVNRepository repository, String parentMergeSrcPath) throws SVNException {

    	if (!honorMergeInfo) {
        	for (ListIterator childrenIter = childrenWithMergeInfo.listIterator(); childrenIter.hasNext();) {
                MergePath child = (MergePath) childrenIter.next();
                SVNMergeRange range = new SVNMergeRange(revision1, revision2, inheritable);
                child.myRemainingRanges = new SVNMergeRangeList(range);
        	}    		
        	return;
    	}
    	
    	for (ListIterator childrenIter = childrenWithMergeInfo.listIterator(); childrenIter.hasNext();) {
            MergePath child = (MergePath) childrenIter.next();
            if (child == null || child.myIsAbsent) {
                continue;
            }
            
            String childRelativePath = null;
            if (myTarget.equals(child.myPath)) {
                childRelativePath = "";
            } else {
                childRelativePath = SVNPathUtil.getRelativePath(myTarget.getAbsolutePath(), 
                		child.myPath.getAbsolutePath());
            }
            SVNURL childURL1 = url1.appendPath(childRelativePath, false);
            SVNURL childURL2 = url2.appendPath(childRelativePath, false);
            SVNEntry childEntry = myWCAccess.getVersionedEntry(child.myPath, false);
            
            boolean indirect[] = { false };
            Map mergeInfo[] = getFullMergeInfo(childEntry, indirect, SVNMergeInfoInheritance.INHERITED, 
            		null, child.myPath, Math.max(revision1, revision2), Math.min(revision1, revision2));
            child.myPreMergeMergeInfo = mergeInfo[0];
            Map implicitMergeInfo = mergeInfo[1];
            child.myIsIndirectMergeInfo = indirect[0];
            child.myRemainingRanges = calculateRemainingRanges(sourceRootURL, childURL1, revision1, 
            		childURL2, revision2, inheritable, child.myPreMergeMergeInfo, implicitMergeInfo, 
            		childEntry, repository); 
    	}
         
    	if (childrenWithMergeInfo.size() > 1) {
    		MergePath child = (MergePath) childrenWithMergeInfo.get(0);
    		if (child.myRemainingRanges.isEmpty()) {
    			SVNMergeRange dummyRange = new SVNMergeRange(revision2, revision2, inheritable);
    			child.myRemainingRanges = new SVNMergeRangeList(dummyRange);
                myIsTargetHasDummyMergeRange = true;
    		}
    	}
    }

    /**
     * @deprecated
     */
    private SVNRemoteDiffEditor driveMergeReportEditor(File targetWCPath, SVNURL url1, long revision1, 
    		SVNURL url2, final long revision2, final List childrenWithMergeInfo, final boolean isRollBack, 
    		SVNDepth depth, SVNAdminArea adminArea, SVNMergeCallback mergeCallback, 
            SVNRemoteDiffEditor editor) throws SVNException {
        final boolean honorMergeInfo = isHonorMergeInfo();
        long defaultStart = revision1;
        
        if (honorMergeInfo) {
            if (myIsTargetHasDummyMergeRange) {
                defaultStart = revision2;
            } else if (childrenWithMergeInfo != null && !childrenWithMergeInfo.isEmpty()) {
                MergePath targetMergePath = (MergePath) childrenWithMergeInfo.get(0);
                SVNMergeRangeList remainingRanges = targetMergePath.myRemainingRanges; 
                if (remainingRanges != null && !remainingRanges.isEmpty()) {
                    SVNMergeRange[] ranges = remainingRanges.getRanges();
                    SVNMergeRange range = ranges[0];
                    defaultStart = range.getStartRevision();
                }
            }
        }

        if (editor == null) {
            editor = new SVNRemoteDiffEditor(adminArea, adminArea.getRoot(), mergeCallback, myRepository2, 
                    defaultStart, revision2, myIsDryRun, this, this);
        } else {
            editor.reset(defaultStart, revision2);
        }

        SVNURL oldURL = ensureSessionURL(myRepository2, url1);
        try {
            final SVNDepth reportDepth = depth;
            final long reportStart = defaultStart;
            final String targetPath = targetWCPath.getAbsolutePath().replace(File.separatorChar, '/');

        	myRepository1.diff(url2, revision2, revision2, null, myIsIgnoreAncestry, depth, true,
                    new ISVNReporterBaton() {
                        public void report(ISVNReporter reporter) throws SVNException {
                            
                            reporter.setPath("", null, reportStart, reportDepth, false);

                            if (honorMergeInfo && childrenWithMergeInfo != null) {
                            	for (int i = 1; i < childrenWithMergeInfo.size(); i++) {
                                   MergePath childMergePath = (MergePath) childrenWithMergeInfo.get(i);
                                   if (childMergePath == null || childMergePath.myIsAbsent || 
                                           childMergePath.myRemainingRanges == null || 
                                           childMergePath.myRemainingRanges.isEmpty()) {
                                       continue;
                                   }
                                   
                                   SVNMergeRangeList remainingRangesList = childMergePath.myRemainingRanges; 
                                   SVNMergeRange[] remainingRanges = remainingRangesList.getRanges();
                                   SVNMergeRange range = remainingRanges[0];
                                   
                                   if (range.getStartRevision() == reportStart) {
                                       continue;
                                   } 
                                     
                                   String childPath = childMergePath.myPath.getAbsolutePath();
                                   childPath = childPath.replace(File.separatorChar, '/');
                                   String relChildPath = childPath.substring(targetPath.length());
                                   if (relChildPath.startsWith("/")) {
                                       relChildPath = relChildPath.substring(1);
                                   }
                                   
                                   if ((isRollBack && range.getStartRevision() < revision2) ||
                                		   (!isRollBack && range.getStartRevision() > revision2)) {
                                       reporter.setPath(relChildPath, null, revision2, reportDepth, false);
                                   } else {
                                       reporter.setPath(relChildPath, null, range.getStartRevision(), 
                                               reportDepth, false);
                                   }
                                }
                            }
                            reporter.finishReport();
                        }
                    }, 
                    SVNCancellableEditor.newInstance(editor, this, getDebugLog()));
        } finally {
        	if (oldURL != null) {
        		myRepository2.setLocation(oldURL, false);
        	}
            editor.cleanup();
        }
        
        sleepForTimeStamp();
        if (myConflictedPaths == null) {
            myConflictedPaths = mergeCallback.getConflictedPaths();
        }
             
        return editor;
    }
    
    private SVNRemoteDiffEditor driveMergeReportEditor2(File targetWCPath, SVNURL url1, long revision1, 
    		SVNURL url2, final long revision2, final List childrenWithMergeInfo, final boolean isRollBack, 
    		SVNDepth depth, SVNAdminArea adminArea, SVNMergeCallback mergeCallback, 
            SVNRemoteDiffEditor editor) throws SVNException {
        final boolean honorMergeInfo = isHonorMergeInfo();
        long defaultStart = revision1;
        
        if (honorMergeInfo) {
            if (myIsTargetHasDummyMergeRange) {
                defaultStart = revision2;
            } else if (childrenWithMergeInfo != null && !childrenWithMergeInfo.isEmpty()) {
                MergePath targetMergePath = (MergePath) childrenWithMergeInfo.get(0);
                SVNMergeRangeList remainingRanges = targetMergePath.myRemainingRanges; 
                if (remainingRanges != null && !remainingRanges.isEmpty()) {
                    SVNMergeRange[] ranges = remainingRanges.getRanges();
                    SVNMergeRange range = ranges[0];
                    defaultStart = range.getStartRevision();
                }
            }
        }

        if (editor == null) {
            editor = new SVNRemoteDiffEditor(adminArea, adminArea.getRoot(), mergeCallback, myRepository2, 
                    defaultStart, revision2, myIsDryRun, this, this);
        } else {
            editor.reset(defaultStart, revision2);
        }

        SVNURL oldURL = ensureSessionURL(myRepository2, url1);
        try {
            final SVNDepth reportDepth = depth;
            final long reportStart = defaultStart;
            final String targetPath = targetWCPath.getAbsolutePath().replace(File.separatorChar, '/');

        	myRepository1.diff(url2, revision2, revision2, null, myIsIgnoreAncestry, depth, true,
                    new ISVNReporterBaton() {
                        public void report(ISVNReporter reporter) throws SVNException {
                            
                            reporter.setPath("", null, reportStart, reportDepth, false);

                            if (honorMergeInfo && childrenWithMergeInfo != null) {
                            	for (int i = 1; i < childrenWithMergeInfo.size(); i++) {
                                   MergePath childMergePath = (MergePath) childrenWithMergeInfo.get(i);
                                   if (childMergePath == null || childMergePath.myIsAbsent || 
                                           childMergePath.myRemainingRanges == null || 
                                           childMergePath.myRemainingRanges.isEmpty()) {
                                       continue;
                                   }
                                   
                                   SVNMergeRangeList remainingRangesList = childMergePath.myRemainingRanges; 
                                   SVNMergeRange[] remainingRanges = remainingRangesList.getRanges();
                                   SVNMergeRange range = remainingRanges[0];
                                   
                                   if (range.getStartRevision() == reportStart) {
                                       continue;
                                   } else {
                                	   MergePath parent = null;
                                	   for (int j = i - 1; j > 0; j--) {
                                		   MergePath potentialParent = (MergePath) childrenWithMergeInfo.get(j);
                                		   String childPath = childMergePath.myPath.getAbsolutePath().replace(File.separatorChar, '/');
                                		   String potentialParentPath = potentialParent.myPath.getAbsolutePath().replace(File.separatorChar, '/');
                                		   if (SVNPathUtil.isAncestor(potentialParentPath, childPath)) {
                                			   parent = potentialParent;
                                			   break;
                                		   }
                                	   }
                                	   
                                	   if (parent != null && parent.myRemainingRanges != null && 
                                			   !	parent.myRemainingRanges.isEmpty()) {
                                		   SVNMergeRange parentRanges[] = parent.myRemainingRanges.getRanges();
                                		   SVNMergeRange parentRange = parentRanges[0];
                                		   SVNMergeRange childRanges[] = childMergePath.myRemainingRanges.getRanges();
                                		   SVNMergeRange childRange = childRanges[0];
                                		   if (parentRange.getStartRevision() == childRange.getStartRevision()) {
                                			   continue;
                                		   }
                                	   }
                                   }
                                     
                                   String childPath = childMergePath.myPath.getAbsolutePath();
                                   childPath = childPath.replace(File.separatorChar, '/');
                                   String relChildPath = childPath.substring(targetPath.length());
                                   if (relChildPath.startsWith("/")) {
                                       relChildPath = relChildPath.substring(1);
                                   }
                                   
                                   if ((isRollBack && range.getStartRevision() < revision2) ||
                                		   (!isRollBack && range.getStartRevision() > revision2)) {
                                       reporter.setPath(relChildPath, null, revision2, reportDepth, false);
                                   } else {
                                       reporter.setPath(relChildPath, null, range.getStartRevision(), 
                                               reportDepth, false);
                                   }
                                }
                            }
                            reporter.finishReport();
                        }
                    }, 
                    SVNCancellableEditor.newInstance(editor, this, getDebugLog()));
        } finally {
        	if (oldURL != null) {
        		myRepository2.setLocation(oldURL, false);
        	}
            editor.cleanup();
        }
        
        sleepForTimeStamp();
        if (myConflictedPaths == null) {
            myConflictedPaths = mergeCallback.getConflictedPaths();
        }
             
        return editor;
    }
    
    private SVNErrorMessage makeMergeConflictError(File targetPath, SVNMergeRange range) {
        SVNErrorMessage error = SVNErrorMessage.create(SVNErrorCode.WC_FOUND_CONFLICT, 
                "One or more conflicts were produced while merging r{0,number,integer}:{1,number,integer} into\n" + 
                "''{2}'' --\n" +
                "resolve all conflicts and rerun the merge to apply the remaining\n" + 
                "unmerged revisions", new Object[] { new Long(range.getStartRevision()), 
                new Long(range.getEndRevision()), targetPath} );
        return error;
    }
    
    /**
     * @deprecated
     */
    private List getMergeInfoPaths(final List children, final String mergeSrcPath, 
    		SVNEntry entry, final SVNURL sourceRootURL, SVNURL url1, SVNURL url2, final long revision1, 
    		final long revision2, final SVNRepository repository, final SVNDepth depth) throws SVNException {
    	final List childrenWithMergeInfo = children == null ? new LinkedList() : children;
    	ISVNEntryHandler handler = new ISVNEntryHandler() {
            public void handleEntry(File path, SVNEntry entry) throws SVNException {
                File target = myTarget;
            	SVNAdminArea adminArea = entry.getAdminArea();
                if (entry.isDirectory() && !adminArea.getThisDirName().equals(entry.getName()) &&
                        !entry.isAbsent()) {
                    return;
                }
            
                if (entry.isDeleted()) {
                    return;
                }
                
                boolean isSwitched = false;
                boolean hasMergeInfoFromMergeSrc = false;
                boolean pathIsMergeTarget = target.equals(path);
                String mergeInfoProp = null;
                if (!entry.isAbsent() && !entry.isScheduledForDeletion()) {
                    SVNVersionedProperties props = adminArea.getProperties(entry.getName());
                    mergeInfoProp = props.getStringPropertyValue(SVNProperty.MERGE_INFO);
                    if (mergeInfoProp != null && !pathIsMergeTarget) {
                        String relToTargetPath = SVNPathUtil.getRelativePath(target.getAbsolutePath(), 
                        		path.getAbsolutePath());
                        String mergeSrcChildPath = SVNPathUtil.getAbsolutePath(SVNPathUtil.append(mergeSrcPath,
                        		relToTargetPath));
                        Map mergeInfo = SVNMergeInfoManager.parseMergeInfo(new StringBuffer(mergeInfoProp), 
                        		null);
                        if (mergeInfo.containsKey(mergeSrcChildPath)) {
                            hasMergeInfoFromMergeSrc = true;
                        } else {
                        	SVNURL mergeInfoURL = sourceRootURL.appendPath(mergeSrcChildPath, false);
                        	SVNRevision pegRevision = SVNRevision.create(revision1 < revision2 ? 
                        			revision2 : revision1);
                        	SVNErrorCode code = null;
                        	SVNURL originalURL = repository.getLocation();
                        	try {
                        		repository.setLocation(mergeInfoURL, false);
                        		getLocations(mergeInfoURL, null, repository, pegRevision, 
                        				SVNRevision.create(revision1), SVNRevision.create(revision2));
                        		
                        	} catch (SVNException svne) {
                        		code = svne.getErrorMessage().getErrorCode();
                        		if (code != SVNErrorCode.FS_NOT_FOUND && 
                        				code != SVNErrorCode.RA_DAV_PATH_NOT_FOUND &&
                        				code != SVNErrorCode.CLIENT_UNRELATED_RESOURCES) {
                        			throw svne;
                        		}
                        	} finally {
                        		repository.setLocation(originalURL, false);
                        	}
                        	
                        	if (code == null) {
                        		if (mergeInfoProp.length() > 0) {
                        			hasMergeInfoFromMergeSrc = true;
                        		} else {
                        			boolean indirect[] = { false };
                        			Map overridenMergeInfo = getWCOrRepositoryMergeInfo(myWCAccess, path, 
                        					entry, SVNMergeInfoInheritance.NEAREST_ANCESTOR, indirect, false, 
                        					repository);
                        			if (indirect[0]) {
                        				boolean equalMergeInfo = SVNMergeInfoManager.mergeInfoEquals(mergeInfo, 
                        						overridenMergeInfo, false);
                        				if (equalMergeInfo) {
                        					SVNPropertiesManager.recordWCMergeInfo(path, null, myWCAccess);
                        				} else {
                        					hasMergeInfoFromMergeSrc = true;
                        				}
                        			}
                        		}
                        		
                        	}
                        }
                    }
                    
                    isSwitched = SVNWCManager.isEntrySwitched(path, entry);
                }

                File parent = path.getParentFile();
                if (pathIsMergeTarget || hasMergeInfoFromMergeSrc || 
                		entry.isScheduledForDeletion() ||
                		isSwitched || 
                        entry.getDepth() == SVNDepth.EMPTY || 
                        entry.getDepth() == SVNDepth.FILES || 
                        entry.isAbsent() || 
                        (depth == SVNDepth.IMMEDIATES && entry.isDirectory() &&
                                parent != null && !parent.equals(path) && parent.equals(target))) {
                    
                	boolean hasMissingChild = entry.getDepth() == SVNDepth.EMPTY ||	
                	entry.getDepth() == SVNDepth.FILES || 
                	(depth == SVNDepth.IMMEDIATES && entry.isDirectory() && 
                			parent != null && parent.equals(target)); 
                    
                    boolean hasNonInheritable = false;
                    String propVal = null;
                    if (mergeInfoProp != null) {
                        if (mergeInfoProp.indexOf(SVNMergeRangeList.MERGE_INFO_NONINHERITABLE_STRING) != -1) {
                            hasNonInheritable = true;
                        }
                        propVal = mergeInfoProp;
                    }
                    
                    if (!hasNonInheritable && (entry.getDepth() == SVNDepth.EMPTY || 
                            entry.getDepth() == SVNDepth.FILES)) {
                        hasNonInheritable = true;
                    }
                    
                    MergePath child = new MergePath();
                    child.myPath = path;
                    child.myHasMissingChildren = hasMissingChild;
                    child.myIsSwitched = isSwitched;
                    child.myIsAbsent = entry.isAbsent();
                    child.myIsScheduledForDeletion = entry.isScheduledForDeletion();
                    child.myHasNonInheritableMergeInfo = hasNonInheritable;
                    child.myMergeInfoPropValue = propVal;
                    childrenWithMergeInfo.add(child);
                }
            }
            
            public void handleError(File path, SVNErrorMessage error) throws SVNException {
                while (error.hasChildErrorMessage()) {
                    error = error.getChildErrorMessage();
                }
                if (error.getErrorCode() == SVNErrorCode.WC_PATH_NOT_FOUND || 
                        error.getErrorCode() == SVNErrorCode.WC_NOT_LOCKED) {
                    return;
                }
                SVNErrorManager.error(error);
            }
        };
        
        if (entry.isFile()) {
            handler.handleEntry(myTarget, entry);
        } else {
            myWCAccess.walkEntries(myTarget, handler, true, depth);
        }
        
        Collections.sort(childrenWithMergeInfo);
        for (int i = 0; i < childrenWithMergeInfo.size(); i++) {
            MergePath child = (MergePath) childrenWithMergeInfo.get(i);
            
            if (child.myHasNonInheritableMergeInfo) {
                SVNAdminArea childArea = myWCAccess.probeTry(child.myPath, true, SVNWCAccess.INFINITE_DEPTH);
                
                for (Iterator entries = childArea.entries(false); entries.hasNext();) {
                    SVNEntry childEntry = (SVNEntry) entries.next();
                    if (childArea.getThisDirName().equals(childEntry.getName())) {
                        continue;
                    }
                    
                    File childPath = childArea.getFile(childEntry.getName()); 
                    if (!childrenWithMergeInfo.contains(new MergePath(childPath))) {
                    	MergePath childOfNonInheriatable = new MergePath(childPath);
                        childrenWithMergeInfo.add(childOfNonInheriatable);
                        //TODO: optimize these repeating sorts
                        Collections.sort(childrenWithMergeInfo);
                        if (!myIsDryRun && myIsSameRepository) {
                            myIsOverrideSet = true;
                        	Map mergeInfo = getWCMergeInfo(childPath, entry, myTarget, 
                        			SVNMergeInfoInheritance.NEAREST_ANCESTOR, false, new boolean[1]);
                            SVNPropertiesManager.recordWCMergeInfo(childPath, mergeInfo, myWCAccess);
                        }
                    }
                }
            }
            
            if (child.myIsAbsent || child.myIsScheduledForDeletion || (child.myIsSwitched && 
            		!myTarget.equals(child.myPath))) {
                File parentPath = child.myPath.getParentFile();
                int parentInd = childrenWithMergeInfo.indexOf(new MergePath(parentPath));
                MergePath parent = parentInd != -1 ? (MergePath) childrenWithMergeInfo.get(parentInd) : null;
                if (parent != null) {
                    parent.myHasMissingChildren = true; 
                } else {
                    parent = new MergePath(parentPath);
                    parent.myHasMissingChildren = true;
                    childrenWithMergeInfo.add(parent);
                    //TODO: optimize these repeating sorts
                    Collections.sort(childrenWithMergeInfo);
                    i++;
                }
                
                SVNAdminArea parentArea = myWCAccess.probeTry(parentPath, true, 
                		SVNWCAccess.INFINITE_DEPTH);
                for (Iterator siblings = parentArea.entries(false); siblings.hasNext();) {
                    SVNEntry siblingEntry = (SVNEntry) siblings.next();
                    if (parentArea.getThisDirName().equals(siblingEntry.getName())) {
                        continue;
                    }
                    
                    File siblingPath = parentArea.getFile(siblingEntry.getName());
                    MergePath siblingOfMissing = new MergePath(siblingPath);
                    if (!childrenWithMergeInfo.contains(siblingOfMissing)) {
                        childrenWithMergeInfo.add(siblingOfMissing);
                        //TODO: optimize these repeating sorts
                        Collections.sort(childrenWithMergeInfo);
                    }
                }
            }
        }
        
        return childrenWithMergeInfo;
    }
    
    private List getMergeInfoPaths2(final List children, final String mergeSrcPath, 
    		SVNEntry entry, final SVNURL sourceRootURL, SVNURL url1, SVNURL url2, final long revision1, 
    		final long revision2, final SVNRepository repository, final SVNDepth depth) throws SVNException {
    	final List childrenWithMergeInfo = children == null ? new LinkedList() : children;
    	ISVNEntryHandler handler = new ISVNEntryHandler() {
            public void handleEntry(File path, SVNEntry entry) throws SVNException {
                File target = myTarget;
            	SVNAdminArea adminArea = entry.getAdminArea();
                if (entry.isDirectory() && !adminArea.getThisDirName().equals(entry.getName()) &&
                        !entry.isAbsent()) {
                    return;
                }
            
                if (entry.isDeleted()) {
                    return;
                }
                
                boolean isSwitched = false;
                boolean hasMergeInfoFromMergeSrc = false;
                boolean pathIsMergeTarget = target.equals(path);
                String mergeInfoProp = null;
                if (!entry.isAbsent() && !entry.isScheduledForDeletion()) {
                    SVNVersionedProperties props = adminArea.getProperties(entry.getName());
                    mergeInfoProp = props.getStringPropertyValue(SVNProperty.MERGE_INFO);
                    if (mergeInfoProp != null && !pathIsMergeTarget) {
                        String relToTargetPath = SVNPathUtil.getRelativePath(target.getAbsolutePath(), 
                        		path.getAbsolutePath());
                        String mergeSrcChildPath = SVNPathUtil.getAbsolutePath(SVNPathUtil.append(mergeSrcPath,
                        		relToTargetPath));
                        Map mergeInfo = SVNMergeInfoManager.parseMergeInfo(new StringBuffer(mergeInfoProp), 
                        		null);
                        if (mergeInfo.containsKey(mergeSrcChildPath)) {
                            hasMergeInfoFromMergeSrc = true;
                        } else {
                        	SVNURL mergeInfoURL = sourceRootURL.appendPath(mergeSrcChildPath, false);
                        	SVNRevision pegRevision = SVNRevision.create(revision1 < revision2 ? 
                        			revision2 : revision1);
                        	SVNErrorCode code = null;
                        	SVNURL originalURL = repository.getLocation();
                        	try {
                        		repository.setLocation(mergeInfoURL, false);
                        		getLocations(mergeInfoURL, null, repository, pegRevision, 
                        				SVNRevision.create(revision1), SVNRevision.create(revision2));
                        		
                        	} catch (SVNException svne) {
                        		code = svne.getErrorMessage().getErrorCode();
                        		if (code != SVNErrorCode.FS_NOT_FOUND && 
                        				code != SVNErrorCode.RA_DAV_PATH_NOT_FOUND &&
                        				code != SVNErrorCode.CLIENT_UNRELATED_RESOURCES) {
                        			throw svne;
                        		}
                        	} finally {
                        		repository.setLocation(originalURL, false);
                        	}
                        	
                        	if (code == null) {
                        		if (mergeInfoProp.length() > 0) {
                        			hasMergeInfoFromMergeSrc = true;
                        		} else {
                        			boolean indirect[] = { false };
                        			Map overridenMergeInfo = getWCOrRepositoryMergeInfo(myWCAccess, path, 
                        					entry, SVNMergeInfoInheritance.NEAREST_ANCESTOR, indirect, false, 
                        					repository);
                        			if (indirect[0]) {
                        				boolean equalMergeInfo = SVNMergeInfoManager.mergeInfoEquals(mergeInfo, 
                        						overridenMergeInfo, false);
                        				if (equalMergeInfo) {
                        					SVNPropertiesManager.recordWCMergeInfo(path, null, myWCAccess);
                        				} else {
                        					hasMergeInfoFromMergeSrc = true;
                        				}
                        			}
                        		}
                        		
                        	}
                        }
                    }
                    
                    isSwitched = SVNWCManager.isEntrySwitched(path, entry);
                }

                File parent = path.getParentFile();
                if (pathIsMergeTarget || hasMergeInfoFromMergeSrc || 
                		entry.isScheduledForDeletion() ||
                		isSwitched || 
                        entry.getDepth() == SVNDepth.EMPTY || 
                        entry.getDepth() == SVNDepth.FILES || 
                        entry.isAbsent() || 
                        (depth == SVNDepth.IMMEDIATES && entry.isDirectory() &&
                                parent != null && !parent.equals(path) && parent.equals(target))) {
                    
                	boolean hasMissingChild = entry.getDepth() == SVNDepth.EMPTY ||	
                	entry.getDepth() == SVNDepth.FILES || 
                	(depth == SVNDepth.IMMEDIATES && entry.isDirectory() && 
                			parent != null && parent.equals(target)); 
                    
                    boolean hasNonInheritable = false;
                    if (mergeInfoProp != null) {
                    	if (myIsFirstRange) {
                    		myWorkingMergeInfo.put(path, mergeInfoProp);
                    	}
                    	if (mergeInfoProp.indexOf(SVNMergeRangeList.MERGE_INFO_NONINHERITABLE_STRING) != -1) {
                            hasNonInheritable = true;
                        }
                    }
                    
                    if (!hasNonInheritable && (entry.getDepth() == SVNDepth.EMPTY || 
                            entry.getDepth() == SVNDepth.FILES)) {
                        hasNonInheritable = true;
                    }
                    
                    MergePath child = new MergePath();
                    child.myPath = path;
                    child.myHasMissingChildren = hasMissingChild;
                    child.myIsSwitched = isSwitched;
                    child.myIsAbsent = entry.isAbsent();
                    child.myIsScheduledForDeletion = entry.isScheduledForDeletion();
                    child.myHasNonInheritableMergeInfo = hasNonInheritable;
                    childrenWithMergeInfo.add(child);
                }
            }
            
            public void handleError(File path, SVNErrorMessage error) throws SVNException {
                while (error.hasChildErrorMessage()) {
                    error = error.getChildErrorMessage();
                }
                if (error.getErrorCode() == SVNErrorCode.WC_PATH_NOT_FOUND || 
                        error.getErrorCode() == SVNErrorCode.WC_NOT_LOCKED) {
                    return;
                }
                SVNErrorManager.error(error);
            }
        };
        
        if (entry.isFile()) {
            handler.handleEntry(myTarget, entry);
        } else {
            myWCAccess.walkEntries(myTarget, handler, true, depth);
        }
        
        Collections.sort(childrenWithMergeInfo);
        for (int i = 0; i < childrenWithMergeInfo.size(); i++) {
            MergePath child = (MergePath) childrenWithMergeInfo.get(i);
            
            if (child.myHasNonInheritableMergeInfo) {
                SVNAdminArea childArea = myWCAccess.probeTry(child.myPath, true, SVNWCAccess.INFINITE_DEPTH);
                
                for (Iterator entries = childArea.entries(false); entries.hasNext();) {
                    SVNEntry childEntry = (SVNEntry) entries.next();
                    if (childArea.getThisDirName().equals(childEntry.getName())) {
                        continue;
                    }
                    
                    File childPath = childArea.getFile(childEntry.getName()); 
                	MergePath childOfNonInheritable = new MergePath(childPath);
                    if (!childrenWithMergeInfo.contains(childOfNonInheritable)) {
                        childrenWithMergeInfo.add(childOfNonInheritable);
                        //TODO: optimize these repeating sorts
                        Collections.sort(childrenWithMergeInfo);
                        if (!myIsDryRun && myIsSameRepository) {
                        	Map mergeInfo = getWCMergeInfo(childPath, entry, myTarget, 
                        			SVNMergeInfoInheritance.NEAREST_ANCESTOR, false, new boolean[1]);
                            if (myIsFirstRange) {
                            	myWorkingMergeInfo.put(childOfNonInheritable.myPath, null);
                            }
                        	SVNPropertiesManager.recordWCMergeInfo(childPath, mergeInfo, myWCAccess);
                        }
                    }
                }
            }
            
            if (child.myIsAbsent || child.myIsScheduledForDeletion || (child.myIsSwitched && 
            		!myTarget.equals(child.myPath))) {
                File parentPath = child.myPath.getParentFile();
                int parentInd = childrenWithMergeInfo.indexOf(new MergePath(parentPath));
                MergePath parent = parentInd != -1 ? (MergePath) childrenWithMergeInfo.get(parentInd) : null;
                if (parent != null) {
                    parent.myHasMissingChildren = true; 
                } else {
                    parent = new MergePath(parentPath);
                    parent.myHasMissingChildren = true;
                    childrenWithMergeInfo.add(parent);
                    //TODO: optimize these repeating sorts
                    Collections.sort(childrenWithMergeInfo);
                    i++;
                }
                
                SVNAdminArea parentArea = myWCAccess.probeTry(parentPath, true, 
                		SVNWCAccess.INFINITE_DEPTH);
                for (Iterator siblings = parentArea.entries(false); siblings.hasNext();) {
                    SVNEntry siblingEntry = (SVNEntry) siblings.next();
                    if (parentArea.getThisDirName().equals(siblingEntry.getName())) {
                        continue;
                    }
                    
                    File siblingPath = parentArea.getFile(siblingEntry.getName());
                    MergePath siblingOfMissing = new MergePath(siblingPath);
                    if (!childrenWithMergeInfo.contains(siblingOfMissing)) {
                        childrenWithMergeInfo.add(siblingOfMissing);
                        //TODO: optimize these repeating sorts
                        Collections.sort(childrenWithMergeInfo);
                    }
                }
            }
        }
        
        return childrenWithMergeInfo;
    }

    private void notifySingleFileMerge(File targetWCPath, SVNEventAction action, 
            SVNStatusType cstate, SVNStatusType pstate) throws SVNException {
        action = cstate == SVNStatusType.MISSING ? SVNEventAction.SKIP : action;
        SVNEvent event = SVNEventFactory.createSVNEvent(targetWCPath, SVNNodeKind.FILE, null, 
                SVNRepository.INVALID_REVISION, cstate, pstate, SVNStatusType.LOCK_INAPPLICABLE, action, 
                null, null, null);
        this.handleEvent(event, ISVNEventHandler.UNKNOWN);
    }

    /**
     * @deprecated
     */
    private Map determinePerformedMerges(File targetPath, SVNMergeRange range, SVNDepth depth) throws SVNException {
        int numberOfSkippedPaths = mySkippedPaths != null ? mySkippedPaths.size() : 0;
        Map merges = new TreeMap();
        
        if (myOperativeNotificationsNumber > 0) {
            myIsOperativeMerge = true;
        } else {
        	return merges;
        }
        
        SVNMergeRangeList rangeList = new SVNMergeRangeList(range);
        merges.put(targetPath, rangeList);
            
        if (numberOfSkippedPaths > 0) {
            for (Iterator skippedPaths = mySkippedPaths.iterator(); skippedPaths.hasNext();) {
                File skippedPath = (File) skippedPaths.next();
                merges.put(skippedPath, new SVNMergeRangeList(new SVNMergeRange[0]));
                //TODO: numberOfSkippedPaths < myOperativeNotificationsNumber
            }
        }
        
        if (depth != SVNDepth.INFINITY && myMergedPaths != null) {
            for (Iterator mergedPathsIter = myMergedPaths.iterator(); mergedPathsIter.hasNext();) {
                File mergedPath = (File) mergedPathsIter.next();
                SVNMergeRangeList childRangeList = null;
                SVNMergeRange childMergeRange = range.dup();
                SVNEntry childEntry = myWCAccess.getVersionedEntry(mergedPath, false);
                if ((childEntry.isDirectory() && mergedPath.equals(myTarget) && 
                        depth == SVNDepth.IMMEDIATES) || (childEntry.isFile() && 
                                depth == SVNDepth.FILES)) {
                    childMergeRange.setInheritable(true);
                    childRangeList = new SVNMergeRangeList(childMergeRange);
                    merges.put(mergedPath, childRangeList);
                }
            }
        }
        return merges;
    }

    private Map determinePerformedMerges2(File targetPath, SVNMergeRange range, SVNDepth depth) throws SVNException {
        int numberOfSkippedPaths = mySkippedPaths != null ? mySkippedPaths.size() : 0;
        Map merges = new TreeMap();
        
        if (myOperativeNotificationsNumber > 0) {
            myIsOperativeMerge = true;
        } else {
        	if (!myWorkingMergeInfo.containsKey(targetPath)) {
        		myWorkingMergeInfo.put(targetPath, null);
        	}
        }
        
        SVNMergeRangeList rangeList = new SVNMergeRangeList(range);
        merges.put(targetPath, rangeList);
            
        if (numberOfSkippedPaths > 0) {
            for (Iterator skippedPaths = mySkippedPaths.iterator(); skippedPaths.hasNext();) {
                File skippedPath = (File) skippedPaths.next();
                SVNStatus status = SVNStatusUtil.getStatus(skippedPath, myWCAccess);
                if (status.getContentsStatus() == SVNStatusType.STATUS_NONE || 
                		status.getContentsStatus() == SVNStatusType.STATUS_UNVERSIONED) {
                	continue;
                }
                merges.put(skippedPath, new SVNMergeRangeList(new SVNMergeRange[0]));
                if (!myWorkingMergeInfo.containsKey(skippedPath)) {
            		myWorkingMergeInfo.put(targetPath, null);
                }
                //TODO: numberOfSkippedPaths < myOperativeNotificationsNumber
            }
        }
        
        if (depth != SVNDepth.INFINITY && myMergedPaths != null) {
            for (Iterator mergedPathsIter = myMergedPaths.iterator(); mergedPathsIter.hasNext();) {
                File mergedPath = (File) mergedPathsIter.next();
                SVNMergeRangeList childRangeList = null;
                SVNMergeRange childMergeRange = range.dup();
                SVNEntry childEntry = myWCAccess.getVersionedEntry(mergedPath, false);
                if ((childEntry.isDirectory() && mergedPath.equals(myTarget) && 
                        depth == SVNDepth.IMMEDIATES) || (childEntry.isFile() && 
                                depth == SVNDepth.FILES)) {
                    childMergeRange.setInheritable(true);
                    childRangeList = new SVNMergeRangeList(childMergeRange);
                    merges.put(mergedPath, childRangeList);
                }
            }
        }
        return merges;
    }

    /**
     * @deprecated
     */
    private void updateWCMergeInfo(File targetPath, String parentReposPath, 
    		SVNEntry entry, Map merges, boolean isRollBack) throws SVNException {
        
        for (Iterator mergesEntries = merges.entrySet().iterator(); mergesEntries.hasNext();) {
            Map.Entry pathToRangeList = (Map.Entry) mergesEntries.next();
            File path = (File) pathToRangeList.getKey();
            SVNMergeRangeList ranges = (SVNMergeRangeList) pathToRangeList.getValue();
            
            Map mergeInfo = SVNPropertiesManager.parseMergeInfo(path, entry, false);
            if (mergeInfo == null && ranges.isEmpty()) {
                mergeInfo = getWCMergeInfo(path, entry, null, 
                        SVNMergeInfoInheritance.NEAREST_ANCESTOR, true, new boolean[1]);
            }
            
            if (mergeInfo == null) {
                mergeInfo = new TreeMap();
            }
            
            String parent = targetPath.getAbsolutePath();
            parent = parent.replace(File.separatorChar, '/');
            String child = path.getAbsolutePath();
            child = child.replace(File.separatorChar, '/');
            String reposPath = null;
            if (parent.length() < child.length()) {
                String childRelPath = child.substring(parent.length());
                if (childRelPath.startsWith("/")) {
                    childRelPath = childRelPath.substring(1);
                }
                reposPath = SVNPathUtil.getAbsolutePath(SVNPathUtil.append(parentReposPath, childRelPath));
            } else {
                reposPath = parentReposPath;
            }
            
            SVNMergeRangeList rangeList = (SVNMergeRangeList) mergeInfo.get(reposPath);
            if (rangeList == null) {
                rangeList = new SVNMergeRangeList(new SVNMergeRange[0]);
            }
            
            if (isRollBack) {
                ranges = ranges.dup();
                ranges = ranges.reverse();
                rangeList = rangeList.diff(ranges, false);
            } else {
                rangeList = rangeList.merge(ranges);
            }
            
            mergeInfo.put(reposPath, rangeList);
            //TODO: I do not understand this:) how mergeInfo can be ever empty here????
            if (isRollBack && mergeInfo.isEmpty()) {
                mergeInfo = null;
            }
            
            SVNMergeInfoUtil.removeEmptyRangeLists(mergeInfo);
            
            try {
                SVNPropertiesManager.recordWCMergeInfo(path, mergeInfo, myWCAccess);
            } catch (SVNException svne) {
                if (svne.getErrorMessage().getErrorCode() != SVNErrorCode.ENTRY_NOT_FOUND) {
                    throw svne;
                }
            }
        }
    }
    
    private void updateWCMergeInfo2(File targetPath, String parentReposPath, 
    		SVNEntry entry, Map merges, boolean isRollBack) throws SVNException {
        
        for (Iterator mergesEntries = merges.entrySet().iterator(); mergesEntries.hasNext();) {
            Map.Entry pathToRangeList = (Map.Entry) mergesEntries.next();
            File path = (File) pathToRangeList.getKey();
            SVNMergeRangeList ranges = (SVNMergeRangeList) pathToRangeList.getValue();
            Map mergeInfo = null;
            try {
            	mergeInfo = SVNPropertiesManager.parseMergeInfo(path, entry, false);	
            } catch (SVNException svne) {
            	if (svne.getErrorMessage().getErrorCode() == SVNErrorCode.WC_NOT_LOCKED) {
            		continue;
            	}
            	throw svne;
            }
            
            if (mergeInfo == null && ranges.isEmpty()) {
                mergeInfo = getWCMergeInfo(path, entry, null, 
                        SVNMergeInfoInheritance.NEAREST_ANCESTOR, true, new boolean[1]);
            }
            
            if (mergeInfo == null) {
                mergeInfo = new TreeMap();
            }
            
            String parent = targetPath.getAbsolutePath();
            parent = parent.replace(File.separatorChar, '/');
            String child = path.getAbsolutePath();
            child = child.replace(File.separatorChar, '/');
            String reposPath = null;
            if (parent.length() < child.length()) {
                String childRelPath = child.substring(parent.length());
                if (childRelPath.startsWith("/")) {
                    childRelPath = childRelPath.substring(1);
                }
                reposPath = SVNPathUtil.getAbsolutePath(SVNPathUtil.append(parentReposPath, childRelPath));
            } else {
                reposPath = parentReposPath;
            }
            
            SVNMergeRangeList rangeList = (SVNMergeRangeList) mergeInfo.get(reposPath);
            if (rangeList == null) {
                rangeList = new SVNMergeRangeList(new SVNMergeRange[0]);
            }
            
            if (isRollBack) {
                ranges = ranges.dup();
                ranges = ranges.reverse();
                rangeList = rangeList.diff(ranges, false);
            } else {
                rangeList = rangeList.merge(ranges);
            }
            
            mergeInfo.put(reposPath, rangeList);
            //TODO: I do not understand this:) how mergeInfo can be ever empty here????
            if (isRollBack && mergeInfo.isEmpty()) {
                mergeInfo = null;
            }
            
            SVNMergeInfoUtil.removeEmptyRangeLists(mergeInfo);
            
            try {
                SVNPropertiesManager.recordWCMergeInfo(path, mergeInfo, myWCAccess);
            } catch (SVNException svne) {
                if (svne.getErrorMessage().getErrorCode() != SVNErrorCode.ENTRY_NOT_FOUND) {
                    throw svne;
                }
            }
        }
    }

    private SVNMergeRangeList calculateRemainingRanges(SVNURL sourceRootURL, SVNURL url1, long revision1, 
            SVNURL url2, long revision2, boolean inheritable, Map targetMergeInfo, Map implicitMergeInfo,  
            SVNEntry entry, SVNRepository repository) throws SVNException {
        SVNURL primaryURL = revision1 < revision2 ? url2 : url1;
        SVNURL oldURL = repository.getLocation();
        repository.setLocation(sourceRootURL, false);
        
        SVNMergeRangeList requestedRangeList = filterReflectedRevisions(sourceRootURL, url1, revision1, url2, 
                revision2, inheritable, entry.getSVNURL(), repository);
        repository.setLocation(oldURL, false);
        
        String mergeInfoPath = getPathRelativeToRoot(null, primaryURL, sourceRootURL, null, repository);
        
        SVNMergeRangeList remainingRanges = filterMergedRevisions(mergeInfoPath, requestedRangeList, 
                targetMergeInfo, implicitMergeInfo, entry, revision1 > revision2); 
        return remainingRanges;
    }

    private SVNMergeRangeList filterMergedRevisions(String mergeInfoPath, SVNMergeRangeList requestedRangeList,
            Map targetMergeInfo, Map implicitMergeInfo, SVNEntry entry, boolean isRollBack) throws SVNException {
        Map mergeInfo = null;
        SVNMergeRangeList targetRangeList = null;
        SVNMergeRangeList remainingRanges = null;
        
        if (isRollBack) {
            mergeInfo = SVNMergeInfoManager.dupMergeInfo(implicitMergeInfo, null);
            if (targetMergeInfo != null) {
                mergeInfo = SVNMergeInfoManager.mergeMergeInfos(mergeInfo, targetMergeInfo);
            }
            
            targetRangeList = (SVNMergeRangeList) mergeInfo.get(mergeInfoPath);
            if (targetRangeList != null) {
                requestedRangeList = requestedRangeList.dup();
                requestedRangeList = requestedRangeList.reverse();
                remainingRanges = requestedRangeList.intersect(targetRangeList);
                remainingRanges = remainingRanges.reverse();
            } else {
                remainingRanges = new SVNMergeRangeList(new SVNMergeRange[0]);
            }
        } else {
            remainingRanges = requestedRangeList;
            if (getOptions().isAllowAllForwardMergesFromSelf()) {
                if (targetMergeInfo != null) {
                    targetRangeList = (SVNMergeRangeList) targetMergeInfo.get(mergeInfoPath);
                }
            } else {
                mergeInfo = SVNMergeInfoManager.dupMergeInfo(implicitMergeInfo, null);
                if (targetMergeInfo != null) {
                    mergeInfo = SVNMergeInfoManager.mergeMergeInfos(mergeInfo, targetMergeInfo);
                }
                targetRangeList = (SVNMergeRangeList) mergeInfo.get(mergeInfoPath);
            }
            if (targetRangeList != null) {
                remainingRanges = requestedRangeList.diff(targetRangeList, false);
            }
        }
        return remainingRanges;
    }

    /**
     * @deprecated
     */
    private SVNMergeRangeList filterReflectedRevisions(SVNURL sourceRootURL, SVNURL url1, long revision1, 
            SVNURL url2, long revision2, boolean inheritable, SVNURL targetURL, SVNRepository repository) throws SVNException {
        long minRev = Math.min(revision1, revision2);
        long maxRev = Math.max(revision1, revision2);
        SVNURL minURL = revision1 < revision2 ? url1 : url2;
        SVNURL maxURL = revision1 < revision2 ? url2 : url1;
        String minRelPath = getPathRelativeToRoot(null, minURL, sourceRootURL, null, repository);
        String maxRelPath = getPathRelativeToRoot(null, maxURL, sourceRootURL, null, repository);
       
        Map startMergeInfo = getReposMergeInfo(repository, minRelPath, minRev, 
        		SVNMergeInfoInheritance.INHERITED, true);
        Map endMergeInfo = getReposMergeInfo(repository, maxRelPath, maxRev, 
        		SVNMergeInfoInheritance.INHERITED, true);
        
        Map added = new HashMap();
        SVNMergeInfoManager.diffMergeInfo(null, added, startMergeInfo, endMergeInfo, false);

        SVNMergeRangeList srcRangeListForTgt = null;
        if (!added.isEmpty()) {
            String mergeInfoPath = getPathRelativeToRoot(null, targetURL, sourceRootURL, null, repository);
            srcRangeListForTgt = (SVNMergeRangeList) added.get(mergeInfoPath);
        }
        
        SVNMergeRange range = new SVNMergeRange(revision1, revision2, inheritable);
        SVNMergeRangeList requestedRangeList = new SVNMergeRangeList(range);
        if (srcRangeListForTgt != null) {
            requestedRangeList = requestedRangeList.diff(srcRangeListForTgt, false);
        }
        return requestedRangeList;
    }

    private SVNMergeRangeList filterReflectedRevisions2(SVNURL sourceRootURL, SVNURL url1, long revision1, 
            SVNURL url2, long revision2, boolean inheritable, SVNURL targetURL, SVNRepository repository) throws SVNException {
        long minRev = Math.min(revision1, revision2);
        long maxRev = Math.max(revision1, revision2);
        SVNURL minURL = revision1 < revision2 ? url1 : url2;
        SVNURL maxURL = revision1 < revision2 ? url2 : url1;
        String minRelPath = getPathRelativeToRoot(null, minURL, sourceRootURL, null, repository);
        String maxRelPath = getPathRelativeToRoot(null, maxURL, sourceRootURL, null, repository);

        Map startMergeInfo = null;
        try {
        	startMergeInfo = getReposMergeInfo(repository, minRelPath, minRev, 
        			SVNMergeInfoInheritance.INHERITED, true);
        } catch (SVNException svne) {
        	if (svne.getErrorMessage().getErrorCode() != SVNErrorCode.FS_NOT_FOUND && 
        			svne.getErrorMessage().getErrorCode() != SVNErrorCode.RA_DAV_REQUEST_FAILED) {
        		throw svne;
        	}
        }
        
        Map endMergeInfo = null;
        try {
        	endMergeInfo = getReposMergeInfo(repository, maxRelPath, maxRev, 
        			SVNMergeInfoInheritance.INHERITED, true);
        } catch (SVNException svne) {
        	if (svne.getErrorMessage().getErrorCode() != SVNErrorCode.FS_NOT_FOUND && 
        			svne.getErrorMessage().getErrorCode() != SVNErrorCode.RA_DAV_REQUEST_FAILED) {
        		throw svne;
        	}
        }
        
        Map addedMergeInfo = null;
        if (startMergeInfo != null && endMergeInfo != null) {
            addedMergeInfo = new HashMap();
            SVNMergeInfoManager.diffMergeInfo(null, addedMergeInfo, startMergeInfo, endMergeInfo, false);
        } else if (endMergeInfo != null) {
        	addedMergeInfo = endMergeInfo;
        }

        SVNMergeRangeList srcRangeListForTgt = null;
        if (addedMergeInfo != null) {
            String mergeInfoPath = getPathRelativeToRoot(null, targetURL, sourceRootURL, null, repository);
            srcRangeListForTgt = (SVNMergeRangeList) addedMergeInfo.get(mergeInfoPath);
        }
        
        SVNMergeRange range = new SVNMergeRange(revision1, revision2, inheritable);
        SVNMergeRangeList requestedRangeList = new SVNMergeRangeList(range);
        if (srcRangeListForTgt != null) {
            requestedRangeList = requestedRangeList.diff(srcRangeListForTgt, false);
        }
        return requestedRangeList;
    }

    private File loadFile(SVNRepository repository, long revision, 
                          SVNProperties properties, SVNAdminArea adminArea) throws SVNException {
        File tmpDir = adminArea.getAdminTempDirectory();
        File result = SVNFileUtil.createUniqueFile(tmpDir, ".merge", ".tmp");
        SVNFileUtil.createEmptyFile(result);
        
        OutputStream os = null;
        try {
            os = SVNFileUtil.openFileForWriting(result); 
            repository.getFile("", revision, properties, 
                               new SVNCancellableOutputStream(os, this));
        } finally {
            SVNFileUtil.closeFile(os);
        }
        return result;
    }

    protected static class MergeSource {
        private SVNURL myURL1;
        private long myRevision1;
        private SVNURL myURL2;
        private long myRevision2;
    }
    
    protected static class MergeAction {
        public static final MergeAction MERGE = new MergeAction();
        public static final MergeAction ROLL_BACK = new MergeAction();
        public static final MergeAction NO_OP = new MergeAction();
        
        private MergeAction() {
        }
    }
    
    protected static class MergePath implements Comparable {
        File myPath;
        boolean myHasMissingChildren;
        boolean myIsSwitched;
        boolean myHasNonInheritableMergeInfo;
        boolean myIsAbsent;
        boolean myIsIndirectMergeInfo;
        boolean myIsScheduledForDeletion;
        /**
         * @deprecated
         */
        String myMergeInfoPropValue;
        SVNMergeRangeList myRemainingRanges;
        Map myPreMergeMergeInfo;
        
        public MergePath() {
        }

        public MergePath(File path) {
            myPath = path;
        }
        
        public MergePath(File path, boolean hasMissingChildren, boolean isSwitched, 
                boolean hasNonInheritableMergeInfo, boolean absent, String propValue) {
            myPath = path;
            myHasNonInheritableMergeInfo = hasNonInheritableMergeInfo;
            myIsSwitched = isSwitched;
            myHasMissingChildren = hasMissingChildren;
            myIsAbsent = absent;
            myMergeInfoPropValue = propValue;
        }
        
        public int compareTo(Object obj) {
            if (obj == null || obj.getClass() != MergePath.class) {
                return -1;
            }
            MergePath mergePath = (MergePath) obj; 
            if (this == mergePath) {
                return 0;
            }
            return myPath.compareTo(mergePath.myPath);
        }
        
        public boolean equals(Object obj) {
            return compareTo(obj) == 0;
        }
        
        public String toString() {
            return myPath.toString();
        }
    }
}