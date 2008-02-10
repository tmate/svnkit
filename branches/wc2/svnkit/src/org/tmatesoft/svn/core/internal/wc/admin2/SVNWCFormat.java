/*
 * ====================================================================
 * Copyright (c) 2004-2008 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.wc.admin2;


/**
 * @version 1.1.2
 * @author  TMate Software Ltd.
 */
public abstract class SVNWCFormat {
    
    // wc keeps format versopn number. 
    // there should be common code to read all wc information.
    // independently on format version.
    
    // this includes:    
    // accessing (read) files in the .svn
    //  entries
    //  properties
    //  wc properties
    //  base and revert files
    
    // we have to read 'format' file outside of this class and 
    // then use it when reading/writing information.
    
    // storing depending on version number:
    //  entries
    //  properties
    //  wc properties
    //  base and revert files
    
    // for the beginning we could start with a single writing code
    // for the latest version.
   
    // there should be single upgrade method to upgrade from
    // any format version: it reads all and saves in the latest
    // version format. it should be probably out of scope of this
    // class.
    
    // upgrading should replace wc version number and instance of
    // the class that is used to read all.
    
    // SVNWCFactory: reads 'format' file and creates SVNWCAccess
    // parametrized with 'reader' and 'writer' instances.
    
    // 'reader' and 'writer' should also accept version number,
    // to allow single implementation for several formats.
    // there probably will be writers for:
    
    // 1.5.x // no xml, changelists, keeplocal, depth.
    // 1.4.x // no xml, single wcprops, propcaching, revert files.
    // 1.2.x // xml, extensions
    // 1.0.x // xml.
    
    // Versions differ not only the way data is stored, but also
    // in names of the files, 'this dir' name and in what files are 
    // really stored (e.g. base and wc properties). 
    
    // SVNWCFactory also upgrades SVNWCAccess by reading all, replacing writer and
    // writing all. It should be parametrized with selector that decides whether to upgrade.
    
    // May be SVNWCFactory should notify client within operation that upgrade is possible
    // then client may insist on batch upgrade.    
    // Also, client should provide default version to use.
    
    // there should be operation 'context' that handles options, event handler, upgrade callback,
    // merger and merge callbacks, commit callbacks, etc.
    
    // whenver possible SVNWCFactory and SVNWCAccess should not operate on Files, but use
    // string paths instead and forward them to the path composer which is part of the 
    // 'reader' and 'writer'. this, theoretically, will allow us to store .svn separately.
    
    // so: SVNWCFactory   (manages all, creates SVNWCAccess)
    //     SVNWCAccess    (high level ops, interface for clients) 
    //     SVNAdminArea   (to read and write files and answer certain questions, with subclasses for different versions)
    //     SVNAdminLayout (low level access to the files, version independent, interchangable)
    //     SVNContext     (parameters passed to SVNWCFactory by the client)
    //     SVNEntry       (entry structure)
    //     SVNEntries     (operations on entry structures, in-memory)
    
    // utilities:
    //     SVNWCAnchor    (pair of SVNWCAccess)
    //     SVNAdminUtil   (various low-level read/write operations)
    //     SVNLog         (log builder)
    
    public abstract AbstractSVNAdminArea getAdminArea();
    
    // we need some sort of selector? 
    
}
