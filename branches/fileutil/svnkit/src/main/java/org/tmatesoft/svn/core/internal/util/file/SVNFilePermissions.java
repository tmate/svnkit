package org.tmatesoft.svn.core.internal.util.file;

public class SVNFilePermissions {
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
