package org.tmatesoft.svn.core.internal.util.file;

public class SVNFileAttributes {
    private long size;
    private long lastModifiedTime;
    private long lastAccessedTime;
    private long creationTime;
    private boolean isRegularFile;
    private boolean isDirectory;
    private boolean isSymbolicLink;
    private boolean isOther;

    private boolean dosReadOnly;
    private boolean dosHidden;
    private boolean dosSystem;
    private boolean dosArchive;

    private SVNFilePermissions posixPermissions;
    private String posixOwner;
    private String posixGroup;

    public void setSize(long size) {
        this.size = size;
    }

    public void setLastModifiedTime(long lastModifiedTime) {
        this.lastModifiedTime = lastModifiedTime;
    }

    public void setLastAccessTime(long lastAccessedTime) {
        this.lastAccessedTime = lastAccessedTime;
    }

    public void setDosReadOnly(boolean dosReadOnly) {
        this.dosReadOnly = dosReadOnly;
    }

    public void setDosHidden(boolean dosHidden) {
        this.dosHidden = dosHidden;
    }

    public void setDosSystem(boolean dosSystem) {
        this.dosSystem = dosSystem;
    }

    public void setDosArchive(boolean dosArchive) {
        this.dosArchive = dosArchive;
    }

    public void setPosixPermissions(SVNFilePermissions posixPermissions) {
        this.posixPermissions = posixPermissions;
    }

    public void setPosixOwner(String posixUser) {
        this.posixOwner = posixUser;
    }

    public void setPosixGroup(String posixGroup) {
        this.posixGroup = posixGroup;
    }

    public void setCreationTime(long creationTime) {
        this.creationTime = creationTime;
    }

    public void setIsRegularFile(boolean regularFile) {
        this.isRegularFile = regularFile;
    }

    public void setIsDirectory(boolean directory) {
        this.isDirectory = directory;
    }

    public void setSymbolicLink(boolean symbolicLink) {
        this.isSymbolicLink = symbolicLink;
    }

    public void setIsOther(boolean isOther) {
        this.isOther = isOther;
    }

    public long getCreationTime() {
        return creationTime;
    }

    public boolean isDosArchive() {
        return dosArchive;
    }

    public boolean isDosHidden() {
        return dosHidden;
    }

    public boolean isDosReadOnly() {
        return dosReadOnly;
    }

    public boolean isDosSystem() {
        return dosSystem;
    }

    public boolean isDirectory() {
        return isDirectory;
    }

    public boolean isOther() {
        return isOther;
    }

    public boolean isRegularFile() {
        return isRegularFile;
    }

    public boolean isSymbolicLink() {
        return isSymbolicLink;
    }

    public long getLastAccessedTime() {
        return lastAccessedTime;
    }

    public long getLastModifiedTime() {
        return lastModifiedTime;
    }

    public String getPosixGroup() {
        return posixGroup;
    }

    public SVNFilePermissions getPosixPermissions() {
        return posixPermissions;
    }

    public String getPosixOwner() {
        return posixOwner;
    }

    public long getSize() {
        return size;
    }
}
