package org.tmatesoft.svn.test;

import org.junit.Assert;
import org.junit.Test;
import org.tmatesoft.svn.core.SVNLogEntry;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.wc2.ng.SvnDiffGenerator;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc2.SvnDiff;
import org.tmatesoft.svn.core.wc2.SvnOperationFactory;
import org.tmatesoft.svn.core.wc2.SvnPatch;
import org.tmatesoft.svn.core.wc2.SvnTarget;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.Collection;

public class PatchTest {

    @Test
    public void testBasics() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testBasics", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder1 = new CommitBuilder(url);
            commitBuilder1.addFile("file", "old".getBytes());
            commitBuilder1.commit();

            final CommitBuilder commitBuilder2 = new CommitBuilder(url);
            commitBuilder2.changeFile("file", "new".getBytes());
            commitBuilder2.commit();

            final SvnDiffGenerator diffGenerator = new SvnDiffGenerator();
            diffGenerator.setBasePath(new File("").getAbsoluteFile());
            final ByteArrayOutputStream output = new ByteArrayOutputStream();

            final SvnDiff diff = svnOperationFactory.createDiff();
            diff.setSource(SvnTarget.fromURL(url), SVNRevision.create(1), SVNRevision.create(2));
            diff.setDiffGenerator(diffGenerator);
            diff.setOutput(output);
            diff.run();

            final String patchString = output.toString();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url, 1);
            final File workingCopyDirectory = workingCopy.getWorkingCopyDirectory();
            final File file = workingCopy.getFile("file");
            final File patchFile = new File(sandbox.createDirectory("directory"), "patchFile");

            TestUtil.writeFileContentsString(patchFile, patchString);

            final SvnPatch patch = svnOperationFactory.createPatch();
            patch.setSingleTarget(SvnTarget.fromFile(workingCopyDirectory));
            patch.setPatchFile(patchFile);
            patch.run();

            Assert.assertEquals("new", TestUtil.readFileContentsString(file));
        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    private String getTestName() {
        return getClass().getSimpleName();
    }
}
