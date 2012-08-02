package org.tmatesoft.svn.core.internal.util.file;

import org.tmatesoft.svn.core.internal.util.jna.SVNWin32Util;

import java.io.File;

public class SVNWin32JNAFileUtil extends SVNFileUtilAdapter {

    @Override
    public boolean setWritable(File file) {
        return SVNWin32Util.setWritable(file);
    }

    @Override
    public boolean setHidden(File file) {
        return SVNWin32Util.setHidden(file);
    }
}
