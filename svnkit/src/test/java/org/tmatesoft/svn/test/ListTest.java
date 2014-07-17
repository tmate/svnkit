package org.tmatesoft.svn.test;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Ignore;
import org.junit.Test;
import org.tmatesoft.svn.core.SVNDirEntry;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.io.fs.FSFS;
import org.tmatesoft.svn.core.internal.wc2.SvnWcGeneration;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;
import org.tmatesoft.svn.core.wc.SVNClientManager;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.admin.SVNAdminClient;
import org.tmatesoft.svn.core.wc2.ISvnObjectReceiver;
import org.tmatesoft.svn.core.wc2.SvnList;
import org.tmatesoft.svn.core.wc2.SvnOperationFactory;
import org.tmatesoft.svn.core.wc2.SvnTarget;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class ListTest {

    @Test
    public void testListOnRepositoryRoot() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testListOnRepositoryRoot", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.addDirectory("directory");
            commitBuilder.commit();

            final List<SVNDirEntry> entries = new ArrayList<SVNDirEntry>();

            final SvnList list = svnOperationFactory.createList();
            list.setSingleTarget(SvnTarget.fromURL(url, SVNRevision.HEAD));
            list.setRevision(SVNRevision.HEAD);
            list.setReceiver(new ISvnObjectReceiver<SVNDirEntry>() {
                public void receive(SvnTarget target, SVNDirEntry dirEntry) throws SVNException {
                    entries.add(dirEntry);
                }
            });
            list.run();

            Collections.sort(entries);

            Assert.assertEquals(2, entries.size());
            Assert.assertEquals("", entries.get(0).getName());
            Assert.assertEquals("", entries.get(0).getRelativePath());
            Assert.assertEquals("directory", entries.get(1).getName());
            Assert.assertEquals("directory", entries.get(1).getRelativePath());

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();

        }
    }

    @Test
    public void testListOnRepositoryRootDavAccess() throws Exception {
        final TestOptions options = TestOptions.getInstance();
        Assume.assumeTrue(TestUtil.areAllApacheOptionsSpecified(options));

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testListOnRepositoryRootDavAccess", options);
        try {
            final SVNURL url = sandbox.createSvnRepositoryWithDavAccess();

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.addDirectory("directory");
            commitBuilder.commit();

            final List<SVNDirEntry> entries = new ArrayList<SVNDirEntry>();

            final SvnList list = svnOperationFactory.createList();
            list.setSingleTarget(SvnTarget.fromURL(url, SVNRevision.HEAD));
            list.setRevision(SVNRevision.HEAD);
            list.setReceiver(new ISvnObjectReceiver<SVNDirEntry>() {
                public void receive(SvnTarget target, SVNDirEntry dirEntry) throws SVNException {
                    entries.add(dirEntry);
                }
            });
            list.run();

            Collections.sort(entries);

            Assert.assertEquals(2, entries.size());
            Assert.assertEquals("", entries.get(0).getName());
            Assert.assertEquals("", entries.get(0).getRelativePath());
            Assert.assertEquals("directory", entries.get(1).getName());
            Assert.assertEquals("directory", entries.get(1).getRelativePath());

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testListOnDirectory() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testListOnDirectory", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.addDirectory("directory/subdirectory");
            commitBuilder.commit();

            final SVNURL directoryUrl = url.appendPath("directory", false);

            final List<SVNDirEntry> entries = new ArrayList<SVNDirEntry>();

            final SvnList list = svnOperationFactory.createList();
            list.setSingleTarget(SvnTarget.fromURL(directoryUrl, SVNRevision.HEAD));
            list.setRevision(SVNRevision.HEAD);
            list.setReceiver(new ISvnObjectReceiver<SVNDirEntry>() {
                public void receive(SvnTarget target, SVNDirEntry dirEntry) throws SVNException {
                    entries.add(dirEntry);
                }
            });
            list.run();

            Collections.sort(entries);

            Assert.assertEquals(2, entries.size());
            Assert.assertEquals("", entries.get(0).getName());
            Assert.assertEquals("", entries.get(0).getRelativePath());
            Assert.assertEquals("subdirectory", entries.get(1).getName());
            Assert.assertEquals("subdirectory", entries.get(1).getRelativePath());

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();

        }
    }

    @Test
    public void testListOnDirectoryDavAccess() throws Exception {
        final TestOptions options = TestOptions.getInstance();
        Assume.assumeTrue(TestUtil.areAllApacheOptionsSpecified(options));

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testListOnDirectoryDavAccess", options);
        try {
            final SVNURL url = sandbox.createSvnRepositoryWithDavAccess();

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.addDirectory("directory/subdirectory");
            commitBuilder.commit();

            final SVNURL directoryUrl = url.appendPath("directory", false);

            final List<SVNDirEntry> entries = new ArrayList<SVNDirEntry>();

            final SvnList list = svnOperationFactory.createList();
            list.setSingleTarget(SvnTarget.fromURL(directoryUrl, SVNRevision.HEAD));
            list.setRevision(SVNRevision.HEAD);
            list.setReceiver(new ISvnObjectReceiver<SVNDirEntry>() {
                public void receive(SvnTarget target, SVNDirEntry dirEntry) throws SVNException {
                    entries.add(dirEntry);
                }
            });
            list.run();

            Collections.sort(entries);

            Assert.assertEquals(2, entries.size());
            Assert.assertEquals("", entries.get(0).getName());
            Assert.assertEquals("", entries.get(0).getRelativePath());
            Assert.assertEquals("subdirectory", entries.get(1).getName());
            Assert.assertEquals("subdirectory", entries.get(1).getRelativePath());

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testListWC16() throws Exception {
        final TestOptions options = TestOptions.getInstance();
        Assume.assumeTrue(TestUtil.areAllApacheOptionsSpecified(options));

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testListWC16", options);
        try {
            final SVNURL url = sandbox.createSvnRepositoryWithDavAccess();

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.commit();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url, -1, true, SvnWcGeneration.V16);
            final File workingCopyDirectory = workingCopy.getWorkingCopyDirectory();

            final SvnList list = svnOperationFactory.createList();
            list.setSingleTarget(SvnTarget.fromFile(workingCopyDirectory));
            final SVNDirEntry dirEntry = list.run();

            Assert.assertEquals(url, dirEntry.getURL());
        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Ignore("SVNKIT-529")
    @Test
    public void testGetDirHangsLongCommitMessage() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final long oldMaxFilesPerDirectory = FSFS.getDefaultMaxFilesPerDirectory();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testGetDirHangsLongCommitMessage", options);
        try {
            FSFS.setDefaultMaxFilesPerDirectory(3); //3 < 5(number of revisions), so the repository will be packed
            final File repositoryDirectory = sandbox.createDirectory("svn.repo");

            final SVNURL url;
            final SVNClientManager clientManager = SVNClientManager.newInstance();
            try {
                SVNAdminClient adminClient = clientManager.getAdminClient();
                adminClient.doCreateRepository(repositoryDirectory, null, true, false, false, false, false, false, true);

                url = SVNURL.fromFile(repositoryDirectory);

                final CommitBuilder commitBuilder1 = new CommitBuilder(url);
                commitBuilder1.addDirectory("directory1");
                commitBuilder1.commit();

                final CommitBuilder commitBuilder2 = new CommitBuilder(url);
                commitBuilder2.addDirectory("directory2");
                commitBuilder2.commit();

                final CommitBuilder commitBuilder3 = new CommitBuilder(url);
                commitBuilder3.addDirectory("directory3");
                commitBuilder3.commit();

                final CommitBuilder commitBuilder4 = new CommitBuilder(url);
                commitBuilder4.addDirectory("directory4");
                commitBuilder4.commit();

                final CommitBuilder commitBuilder5 = new CommitBuilder(url);
                commitBuilder5.addDirectory("directory5/file");
                commitBuilder5.setCommitMessage(createStringForLength(1936)); //large number >> 1024
                commitBuilder5.commit();

                adminClient.doPack(new File(url.getPath()));
            } finally {
                clientManager.dispose();
            }

            final SVNRepository svnRepository = SVNRepositoryFactory.create(url);
            try {
                final List<SVNDirEntry> dirEntries = new ArrayList<SVNDirEntry>();
                svnRepository.getDir("directory5", 5, null, (Collection) dirEntries);

                Assert.assertEquals(1, dirEntries.size());
                Assert.assertEquals("file", dirEntries.get(0).getName());
            } finally {
                svnRepository.closeSession();
            }
        } finally {
            FSFS.setDefaultMaxFilesPerDirectory(oldMaxFilesPerDirectory);

            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    private String createStringForLength(int length) {
        final StringBuilder stringBuilder = new StringBuilder();
        for (int i = 0; i < length; i++) {
            stringBuilder.append('a');
        }
        return stringBuilder.toString();
    }

    private String getTestName() {
        return "ListTest";
    }
}
