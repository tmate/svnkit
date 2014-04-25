package org.tmatesoft.svn.test;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.io.fs.FSFS;
import org.tmatesoft.svn.core.internal.wc.SVNConfigFile;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.wc.SVNClientManager;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.admin.ISVNChangeEntryHandler;
import org.tmatesoft.svn.core.wc.admin.ISVNChangedDirectoriesHandler;
import org.tmatesoft.svn.core.wc.admin.SVNChangeEntry;
import org.tmatesoft.svn.core.wc.admin.SVNLookClient;
import org.tmatesoft.svn.core.wc2.SvnGetInfo;
import org.tmatesoft.svn.core.wc2.SvnInfo;
import org.tmatesoft.svn.core.wc2.SvnOperationFactory;
import org.tmatesoft.svn.core.wc2.SvnTarget;
import org.tmatesoft.svn.core.wc2.admin.SvnRepositoryGetFileSize;

import java.io.File;

public class SvnLookTest {

    @Test
    public void testNoChangedDirectoriesAtZeroRevision() throws Exception {

        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".test", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();
            Assume.assumeTrue("file".equals(url.getProtocol()));

            final String path = url.getPath();
            final File repositoryRoot = new File(path);

            final SVNClientManager clientManager = SVNClientManager.newInstance();
            try {
                final SVNLookClient lookClient = clientManager.getLookClient();
                lookClient.doGetChangedDirectories(repositoryRoot, SVNRevision.create(0), new ISVNChangedDirectoriesHandler() {
                    public void handleDir(String path) throws SVNException {
                        Assert.fail("The handler should be never called, because there're no changes at r0");
                    }
                });
                lookClient.doGetChangedDirectories(repositoryRoot, SVNRevision.HEAD, new ISVNChangedDirectoriesHandler() {
                    public void handleDir(String path) throws SVNException {
                        Assert.fail("The handler should be never called, because there're no changes at r0");
                    }
                });
            } finally {
                clientManager.dispose();
            }

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testFileSize() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testFileSize", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();
            Assume.assumeTrue("file".equals(url.getProtocol()));

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.addFile("file1", new byte[]{});
            commitBuilder.addFile("file2", new byte[]{1,2,3});
            commitBuilder.addFile("directory/file3", new byte[]{1,2,3,4,5,6});
            commitBuilder.commit();

            final String path = url.getPath();
            final File repositoryRoot = new File(path);

            final SVNClientManager clientManager = SVNClientManager.newInstance();
            try {
                final SVNLookClient lookClient = clientManager.getLookClient();
                Assert.assertEquals(0, lookClient.doGetFileSize(repositoryRoot, "file1", SVNRevision.HEAD));
                Assert.assertEquals(3, lookClient.doGetFileSize(repositoryRoot, "file2", SVNRevision.HEAD));
                Assert.assertEquals(6, lookClient.doGetFileSize(repositoryRoot, "directory/file3", SVNRevision.HEAD));
            } finally {
                clientManager.dispose();
            }

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testDeltaRepresentationHeader() throws Exception {
        //SVNKIT-504
        final TestOptions options = TestOptions.getInstance();
        Assume.assumeNotNull(options.getSvnCommand());

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testDeltaRepresentationHeader", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final String enableDirDeltification = "enable-dir-deltification";
            final String enablePropsDeltification = "enable-props-deltification";

            final File repositoryRoot = new File(url.getPath());
            final File dbDirectory = new File(repositoryRoot, FSFS.DB_DIR);
            final File fsfsConfigFile = new File(dbDirectory, FSFS.PATH_CONFIG);

            final SVNConfigFile fsfsConfig = new SVNConfigFile(fsfsConfigFile);
            fsfsConfig.setPropertyValue("deltification", enableDirDeltification, "true", false);
            fsfsConfig.setPropertyValue("deltification", enablePropsDeltification, "true", false);
            fsfsConfig.save();

            final String svnCommand = options.getSvnCommand();
            SVNFileUtil.execCommand(new String[] {svnCommand, "mkdir", url.appendPath("trunk", false).toString(), "-m", "m"});

            SVNClientManager clientManager = SVNClientManager.newInstance();
            try {
                final SVNLookClient lookClient = clientManager.getLookClient();

                lookClient.doGetChanged(repositoryRoot, SVNRevision.create(1), new ISVNChangeEntryHandler() {
                    public void handleEntry(SVNChangeEntry entry) throws SVNException {
                        Assert.assertEquals("/trunk", entry.getPath());
                    }
                }, true);
            } finally {
                clientManager.dispose();
            }

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    private String getTestName() {
        return "SvnLookTest";
    }
}
