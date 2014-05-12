package org.tmatesoft.svn.test;

import org.junit.Assert;
import org.junit.Test;
import org.tmatesoft.svn.core.*;
import org.tmatesoft.svn.core.internal.wc2.SvnWcGeneration;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;
import org.tmatesoft.svn.core.wc.SVNClientManager;
import org.tmatesoft.svn.core.wc.SVNLogClient;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc2.SvnOperationFactory;
import org.tmatesoft.svn.core.wc2.SvnRemoteSetProperty;
import org.tmatesoft.svn.core.wc2.SvnSetProperty;
import org.tmatesoft.svn.core.wc2.SvnTarget;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class LogTest {

    @Test
    public void testLogEntryContainsRevisionProperties() throws Exception {
        //SVNKIT-60
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testLogEntryContainsRevisionProperties", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder1 = new CommitBuilder(url);
            commitBuilder1.addFile("file");
            commitBuilder1.commit();

            final CommitBuilder commitBuilder2 = new CommitBuilder(url);
            commitBuilder2.delete("file");
            commitBuilder2.commit();

            final SVNRepository svnRepository = SVNRepositoryFactory.create(url);
            try {
                svnRepository.setRevisionPropertyValue(1, "propertyName", SVNPropertyValue.create("propertyValue1"));
                svnRepository.setRevisionPropertyValue(2, "propertyName", SVNPropertyValue.create("propertyValue2"));

                final Collection logEntries1 = svnRepository.log(new String[]{""}, null, 1, 1, true, true);
                final Collection logEntries2 = svnRepository.log(new String[]{""}, null, 2, 2, true, true);

                final SVNLogEntry logEntry1 = (SVNLogEntry) logEntries1.iterator().next();
                final SVNLogEntry logEntry2 = (SVNLogEntry) logEntries2.iterator().next();

                final SVNProperties revisionProperties1 = logEntry1.getRevisionProperties();
                final SVNProperties revisionProperties2 = logEntry2.getRevisionProperties();
                Assert.assertEquals("propertyValue1", SVNPropertyValue.getPropertyAsString(revisionProperties1.getSVNPropertyValue("propertyName")));
                Assert.assertEquals("propertyValue2", SVNPropertyValue.getPropertyAsString(revisionProperties2.getSVNPropertyValue("propertyName")));
            } finally {
                svnRepository.closeSession();
            }
        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testLogPegWorkingWC16() throws Exception {
        //SVNKIT-507
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testLogPegWorkingWC16", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.addFile("file");
            commitBuilder.commit();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url, -1, true, SvnWcGeneration.V16);
            final File workingCopyDirectory = workingCopy.getWorkingCopyDirectory();

            final List<SVNLogEntry> logEntries = new ArrayList<SVNLogEntry>(1);

            final SVNClientManager clientManager = SVNClientManager.newInstance();
            try {
                SVNLogClient logClient = clientManager.getLogClient();
                logClient.doLog(new File[]{workingCopyDirectory}, SVNRevision.WORKING,
                        SVNRevision.create(0), SVNRevision.create(1), true, true, 10, new ISVNLogEntryHandler() {
                    public void handleLogEntry(SVNLogEntry logEntry) throws SVNException {
                        logEntries.add(logEntry);
                    }
                });
            } finally {
                clientManager.dispose();
            }
            Assert.assertEquals(2, logEntries.size());
            final SVNLogEntry logEntry0 = logEntries.get(0);
            final SVNLogEntry logEntry1 = logEntries.get(1);
            Assert.assertEquals(0, logEntry0.getChangedPaths().size());
            Assert.assertEquals(1, logEntry1.getChangedPaths().size());

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    private String getTestName() {
        return getClass().getSimpleName();
    }
}
