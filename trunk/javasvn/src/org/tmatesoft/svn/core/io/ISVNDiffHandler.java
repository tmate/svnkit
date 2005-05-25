/*
 * ====================================================================
 * Copyright (c) 2004 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://tmate.org/svn/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */

package org.tmatesoft.svn.core.io;

import java.io.OutputStream;

import org.tmatesoft.svn.core.diff.SVNDiffWindow;

/**
 * <code>ISVNDiffHandler</code> is an interface for a handler that processes applying a 
 * delta to a file. For a file it gets a diff window (an <code>SVNDiffWindow</code>)
 * that contains instructions on applying the delta. If a source for the delta is some
 * kind of new data the <code>ISVNDiffHandler</code> provides an <code>OutputStream</code>
 * where the data itself is to be written into. 
 * 
 * @version	1.0
 * @author 	TMate Software Ltd.
 * @see		SVNDiffWindow
 */
public interface ISVNDiffHandler {
    /**
     * Handles a diff window for the file identified by the <code>token</code>.
     * This diff window has got the instructions how to apply the delta to the file.
     * If the file is to be applied a delta from new source data
     * a returned <code>OutputStream</code> should be used to write the new data itself
     * into it. 
     *  
     * @param token			a file identifier
     * @param diffWindow	a diff window that has got the instructions on how to apply 
     * 						the delta 
     * @return				an <code>OutputStream</code> where  new source data should
     * 						be written into
     * @see					SVNDiffWindow
     */
    public OutputStream handleDiffWindow(String token, SVNDiffWindow diffWindow);
    
    /**
     * Finishes all diff windows handling applying the delta to the entire file.
     * If a file is too large a repository server can send a client several diff windows
     * with instructions on file delta applying. At first they are collected and 
     * afterwards all of them will be applied when calling to this method.
     *  
     * @param token		a file identifier
     * @see				SVNDiffWindow
     */
    public void handleDiffWindowClosed(String token);

}
