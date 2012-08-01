package org.tmatesoft.svn.core.internal.util;

public enum SVNJavaVersion {

    UNKNOWN(-1), V14(14), V15(15), V16(16), V17(17);

    private final int version;

    private SVNJavaVersion(int version) {
        this.version = version;
    }

    public int getVersion() {
        return version;
    }

    public static SVNJavaVersion getCurrentVersion() {
        final String javaVersionString = System.getProperty("java.version");
        if (javaVersionString == null) {
            return UNKNOWN;
        }

        if (javaVersionString.startsWith("1.7")) {
            return V17;
        } else if (javaVersionString.startsWith("1.6")) {
            return V16;
        } else if (javaVersionString.startsWith("1.5")) {
            return V15;
        } else if (javaVersionString.startsWith("1.4")) {
            return V14;
        } else {
            return UNKNOWN;
        }
    }
}
