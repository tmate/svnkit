package org.tmatesoft.svn.core.internal.util.file;

public class SVNFilePermissions {

    public static SVNFilePermissions parseString(String permissionsString) {
        final SVNFilePermissions permissions = new SVNFilePermissions();

        permissions.setOwnerCanRead(permissionsString.charAt(0) == 'r');
        permissions.setOwnerCanWrite(permissionsString.charAt(1) == 'w');
        permissions.setOwnerCanExecute(permissionsString.charAt(2) == 'x');

        permissions.setGroupCanRead(permissionsString.charAt(3) == 'r');
        permissions.setGroupCanWrite(permissionsString.charAt(4) == 'w');
        permissions.setGroupCanExecute(permissionsString.charAt(5) == 'x');

        permissions.setOthersCanRead(permissionsString.charAt(6) == 'r');
        permissions.setOthersCanWrite(permissionsString.charAt(7) == 'w');
        permissions.setOthersCanExecute(permissionsString.charAt(8) == 'x');

        return permissions;
    }

    private boolean ownerCanRead;
    private boolean ownerCanWrite;
    private boolean ownerCanExecute;
    private boolean groupCanRead;
    private boolean groupCanWrite;
    private boolean groupCanExecute;
    private boolean othersCanRead;
    private boolean othersCanWrite;
    private boolean othersCanExecute;

    @Override
    public String toString() {
        final StringBuilder stringBuilder = new StringBuilder();

        stringBuilder.append(isOwnerCanRead() ? 'r' : '-');
        stringBuilder.append(isOwnerCanWrite() ? 'w' : '-');
        stringBuilder.append(isOwnerCanExecute() ? 'x' : '-');

        stringBuilder.append(isGroupCanRead() ? 'r' : '-');
        stringBuilder.append(isGroupCanWrite() ? 'w' : '-');
        stringBuilder.append(isGroupCanExecute() ? 'x' : '-');

        stringBuilder.append(isOthersCanRead() ? 'r' : '-');
        stringBuilder.append(isOthersCanWrite() ? 'w' : '-');
        stringBuilder.append(isOthersCanExecute() ? 'x' : '-');

        return stringBuilder.toString();
    }

    public boolean isOwnerCanRead() {
        return ownerCanRead;
    }

    public void setOwnerCanRead(boolean ownerCanRead) {
        this.ownerCanRead = ownerCanRead;
    }

    public boolean isOwnerCanWrite() {
        return ownerCanWrite;
    }

    public void setOwnerCanWrite(boolean ownerCanWrite) {
        this.ownerCanWrite = ownerCanWrite;
    }

    public boolean isOwnerCanExecute() {
        return ownerCanExecute;
    }

    public void setOwnerCanExecute(boolean ownerCanExecute) {
        this.ownerCanExecute = ownerCanExecute;
    }

    public boolean isGroupCanRead() {
        return groupCanRead;
    }

    public void setGroupCanRead(boolean groupCanRead) {
        this.groupCanRead = groupCanRead;
    }

    public boolean isGroupCanWrite() {
        return groupCanWrite;
    }

    public void setGroupCanWrite(boolean groupCanWrite) {
        this.groupCanWrite = groupCanWrite;
    }

    public boolean isGroupCanExecute() {
        return groupCanExecute;
    }

    public void setGroupCanExecute(boolean groupCanExecute) {
        this.groupCanExecute = groupCanExecute;
    }

    public boolean isOthersCanRead() {
        return othersCanRead;
    }

    public void setOthersCanRead(boolean othersCanRead) {
        this.othersCanRead = othersCanRead;
    }

    public boolean isOthersCanWrite() {
        return othersCanWrite;
    }

    public void setOthersCanWrite(boolean othersCanWrite) {
        this.othersCanWrite = othersCanWrite;
    }

    public boolean isOthersCanExecute() {
        return othersCanExecute;
    }

    public void setOthersCanExecute(boolean othersCanExecute) {
        this.othersCanExecute = othersCanExecute;
    }
}
