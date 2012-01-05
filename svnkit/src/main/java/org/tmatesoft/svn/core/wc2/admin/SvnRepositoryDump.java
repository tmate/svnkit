package org.tmatesoft.svn.core.wc2.admin;

import java.io.File;
import java.io.OutputStream;

import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.admin.SVNAdminEvent;
import org.tmatesoft.svn.core.wc2.SvnOperationFactory;
import org.tmatesoft.svn.core.wc2.SvnReceivingOperation;

public class SvnRepositoryDump extends SvnReceivingOperation<SVNAdminEvent> {
    
    private File repository;
    
    private OutputStream out;
    
    private SVNRevision startRevision;
    private SVNRevision endRevision;
    private boolean useDelta;
    private boolean incremental;

    public SvnRepositoryDump(SvnOperationFactory factory) {
        super(factory);
    }

    public File getRepository() {
        return repository;
    }

    public void setRepository(File repository) {
        this.repository = repository;
    }

    public OutputStream getOut() {
        return out;
    }

    public void setOut(OutputStream out) {
        this.out = out;
    }

    public SVNRevision getStartRevision() {
        return startRevision;
    }

    public void setStartRevision(SVNRevision startRevision) {
        this.startRevision = startRevision;
    }

    public SVNRevision getEndRevision() {
        return endRevision;
    }

    public void setEndRevision(SVNRevision endRevision) {
        this.endRevision = endRevision;
    }

    public boolean isUseDelta() {
        return useDelta;
    }

    public void setUseDelta(boolean useDelta) {
        this.useDelta = useDelta;
    }

    public boolean isIncremental() {
        return incremental;
    }

    public void setIncremental(boolean incremental) {
        this.incremental = incremental;
    }
}
