package org.tmatesoft.svn.core.wc2.admin;

import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc2.SvnOperation;
import org.tmatesoft.svn.core.wc2.SvnOperationFactory;

public class SvnRepositorySyncInfo extends SvnOperation<Long> {
    
    private SVNURL toUrl;
    
    public SvnRepositorySyncInfo(SvnOperationFactory factory) {
        super(factory);
    }

	public SVNURL getToUrl() {
		return toUrl;
	}

	public void setToUrl(SVNURL toUrl) {
		this.toUrl = toUrl;
	}

	

	    
}
