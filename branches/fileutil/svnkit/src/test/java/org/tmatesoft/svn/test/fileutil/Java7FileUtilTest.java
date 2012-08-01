package org.tmatesoft.svn.test.fileutil;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.tmatesoft.svn.core.internal.util.SVNJavaVersion;
import org.tmatesoft.svn.core.internal.util.file.SVNFileAttributes;
import org.tmatesoft.svn.core.internal.util.file.SVNJava7FileUtil;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.wc2.SvnOperationFactory;
import org.tmatesoft.svn.test.Sandbox;
import org.tmatesoft.svn.test.TestOptions;
import org.tmatesoft.svn.test.TestUtil;

import java.io.File;

public class Java7FileUtilTest {

    @Before
    public void setup() {
        Assume.assumeTrue(SVNJavaVersion.getCurrentVersion().getVersion() >= SVNJavaVersion.V17.getVersion());
    }

    @Test
    public void testBasicAttributes() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testBasicAttributes", options);
        try {
            final File directory = sandbox.createDirectory("directory");
            final File file = new File(directory, "file");

            TestUtil.writeFileContentsString(file, "contents");

            final SVNJava7FileUtil java7FileUtil = new SVNJava7FileUtil();
            final SVNFileAttributes attributes = java7FileUtil.readFileAttributes(file, false);

            Assert.assertEquals(file.lastModified(), attributes.getLastModifiedTime());
            Assert.assertEquals(file.lastModified(), attributes.getCreationTime());
            Assert.assertEquals(file.lastModified(), attributes.getLastAccessedTime());
            Assert.assertEquals(file.length(), attributes.getSize());
            Assert.assertFalse(attributes.isDirectory());
            Assert.assertTrue(attributes.isRegularFile());
            Assert.assertFalse(attributes.isSymbolicLink());
            Assert.assertFalse(attributes.isOther());

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testPosixAttributes() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        Assume.assumeTrue(!SVNFileUtil.isWindows);

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testPosixAttributes", options);
        try {
            final File directory = sandbox.createDirectory("directory");
            final File file = new File(directory, "file");

            TestUtil.writeFileContentsString(file, "contents");

            final SVNJava7FileUtil java7FileUtil = new SVNJava7FileUtil();
            final SVNFileAttributes attributes = java7FileUtil.readFileAttributes(file, false);

            Assert.assertEquals(System.getProperty("user.name"), attributes.getPosixOwner());
            Assert.assertEquals(System.getProperty("user.name"), attributes.getPosixGroup());
            Assert.assertEquals("rw-r--r--", attributes.getPosixPermissions().toString());

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testFileType() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testFileType", options);
        try {
            final SVNJava7FileUtil java7FileUtil = new SVNJava7FileUtil();

            final File directory = sandbox.createDirectory("directory");
            final File file = new File(directory, "file");
            final File symlinkToFile = new File(directory, "symlinkToFile");
            final File symlinkToDirectory = new File(directory, "symlinkToDirectory");

            if (SVNFileUtil.symlinksSupported()) {
                java7FileUtil.createSymlink(symlinkToFile, file.getAbsoluteFile());
                java7FileUtil.createSymlink(symlinkToDirectory, directory.getAbsoluteFile());
            }

            TestUtil.writeFileContentsString(file, "contents");

            //           file, directory, symlink, other
            checkAttributes(true, false, false, false, file, java7FileUtil);
            checkAttributes(false, true, false, false, directory, java7FileUtil);
            if (SVNFileUtil.symlinksSupported()) {
                checkAttributes(false, false, true, false, symlinkToDirectory, java7FileUtil);
                checkAttributes(false, false, true, false, symlinkToFile, java7FileUtil);
            }

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    private void checkAttributes(boolean isRegularFile, boolean isDirectory, boolean isSymbolicLink, boolean isOther, File file, SVNJava7FileUtil java7FileUtil) {
        final SVNFileAttributes attributes = java7FileUtil.readFileAttributes(file, false);
        Assert.assertEquals(isRegularFile, attributes.isRegularFile());
        Assert.assertEquals(isDirectory, attributes.isDirectory());
        Assert.assertEquals(isSymbolicLink, attributes.isSymbolicLink());
        Assert.assertEquals(isOther, attributes.isOther());
    }

    private String getTestName() {
        return "Java7FileUtilTest";
    }
}
