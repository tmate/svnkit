package org.tmatesoft.svn.core.internal.util.jna;

import org.tmatesoft.svn.core.internal.util.file.SVNFileUtilAdapter;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;

import java.io.File;

public class SVNUnixJNAFileUtil extends SVNFileUtilAdapter {

    @Override
    public boolean isExecutable(File file) {
        return SVNLinuxUtil.isExecutable(file);
    }

    @Override
    public File readSymlink(File link) {
        return SVNFileUtil.createFilePath(SVNLinuxUtil.getLinkTarget(link));
    }

    @Override
    public boolean createSymlink(File link, File linkName) {
        return SVNLinuxUtil.createSymlink(link, linkName.getPath());
    }

    @Override
    public boolean isSymlink(File file) {
        return SVNLinuxUtil.isSymlink(file);
    }

    @Override
    public boolean setSGID(File directory) {
        return SVNLinuxUtil.setSGID(directory);
    }
}
