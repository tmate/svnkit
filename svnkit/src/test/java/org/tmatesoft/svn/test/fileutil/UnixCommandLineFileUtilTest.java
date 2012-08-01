package org.tmatesoft.svn.test.fileutil;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.tmatesoft.svn.core.internal.util.file.SVNFileAttributes;
import org.tmatesoft.svn.core.internal.util.file.SVNUnixCommandLineFileUtil;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.wc2.SvnOperationFactory;
import org.tmatesoft.svn.test.Sandbox;
import org.tmatesoft.svn.test.TestOptions;
import org.tmatesoft.svn.test.TestUtil;

import java.io.File;

public class UnixCommandLineFileUtilTest {

    @Before
    public void setup() {
        Assume.assumeTrue(!SVNFileUtil.isWindows);
    }

    @Test
    public void testBasics() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testBasics", options);
        try {
            final File directory = sandbox.createDirectory("directory");
            final File file = new File(directory, "file");

            TestUtil.writeFileContentsString(file, "contents");

            SVNUnixCommandLineFileUtil unixCommandLineFileUtil = new SVNUnixCommandLineFileUtil("");
            final SVNFileAttributes attributes = unixCommandLineFileUtil.readFileAttributes(file, false);

            Assert.assertTrue(TestUtil.areTimestampsNearlyEqual(file.lastModified(), attributes.getLastModifiedTime()));
            Assert.assertTrue(TestUtil.areTimestampsNearlyEqual(file.lastModified(), attributes.getCreationTime()));
            Assert.assertTrue(TestUtil.areTimestampsNearlyEqual(file.lastModified(), attributes.getLastAccessedTime()));
            Assert.assertEquals(file.length(), attributes.getSize());
            Assert.assertFalse(attributes.isDirectory());
            Assert.assertTrue(attributes.isRegularFile());
            Assert.assertFalse(attributes.isSymbolicLink());
            Assert.assertFalse(attributes.isOther());
            Assert.assertFalse(attributes.getSuid());
            Assert.assertFalse(attributes.getSgid());

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }

    }

    private String getTestName() {
        return "UnixCommandLineFileUtilTest";
    }
}
