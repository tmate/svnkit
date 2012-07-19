package org.tmatesoft.svn.core.internal.util.file;

import java.io.File;
import java.util.Properties;

public class SVNFileUtilAdapter {

    public boolean isExecutable(File file) {
        throw new UnsupportedOperationException();
    }

    public boolean setExecutable(File file, boolean executable) {
        throw new UnsupportedOperationException();
    }

    public boolean isWritable(File file) {
        throw new UnsupportedOperationException();
    }

    public boolean setWritable(File file) {
        throw new UnsupportedOperationException();
    }

    public boolean setReadOnly(File file) {
        throw new UnsupportedOperationException();
    }

    public boolean isSymlink(File file) {
        throw new UnsupportedOperationException();
    }

    public boolean createSymlink(File link, File linkName) {
        throw new UnsupportedOperationException();
    }

    public File readSymlink(File link) {
        throw new UnsupportedOperationException();
    }

    public boolean setHidden(File file) {
        throw new UnsupportedOperationException();
    }

    public boolean setSGID(File directory) {
        throw new UnsupportedOperationException();
    }

    public Properties getEnvironment() {
        throw new UnsupportedOperationException();
    }

    public String getEnvironmentVariable(String varibaleName) {
        final Properties environment = getEnvironment();
        if (environment == null) {
            return null;
        }
        return environment.getProperty(varibaleName);
    }
}
