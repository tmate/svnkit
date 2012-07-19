package org.tmatesoft.svn.core.internal.util.file;

import java.io.File;

public class SVNFileUtilAdapter {

    public boolean isExecutable(File file) {
        throw new UnsupportedOperationException();
    }

    public void setExecutable(File file, boolean executable) {
        throw new UnsupportedOperationException();
    }

    public boolean isSymlink(File file) {
        throw new UnsupportedOperationException();
    }

    public void createSymlink(File link, File linkName) {
        throw new UnsupportedOperationException();
    }

    public File readSymlink(File link) {
        throw new UnsupportedOperationException();
    }
}
