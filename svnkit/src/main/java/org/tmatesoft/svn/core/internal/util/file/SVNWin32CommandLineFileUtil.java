package org.tmatesoft.svn.core.internal.util.file;

import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.util.SVNDebugLog;
import org.tmatesoft.svn.util.SVNLogType;

import java.io.File;
import java.util.Properties;

public class SVNWin32CommandLineFileUtil extends SVNFileUtilAdapter {

    boolean isWindows9x;
    String attribCommand;
    String cmdCommand;

    public SVNWin32CommandLineFileUtil(String prefix) {
        isWindows9x = System.getProperty("os.name").toLowerCase().contains("windows 9");
        cmdCommand = isWindows9x ? "command.com" : "cmd.exe";
        attribCommand = prefix + "attrib";
    }

    @Override
    public boolean setReadOnly(File file) {
        Process p = null;
        try {
            p = Runtime.getRuntime().exec(attribCommand + " -R \"" + file.getAbsolutePath() + "\"");
            p.waitFor();
            return true;
        } catch (Throwable th) {
            SVNDebugLog.getDefaultLog().logFinest(SVNLogType.DEFAULT, th);
            return false;
        } finally {
            if (p != null) {
                SVNFileUtil.closeFile(p.getInputStream());
                SVNFileUtil.closeFile(p.getOutputStream());
                SVNFileUtil.closeFile(p.getErrorStream());
                p.destroy();
            }
        }
    }

    @Override
    public Properties getEnvironment() {
        return SVNUnixCommandLineFileUtil.getEnvironmentFromCommandOutput(cmdCommand + " /c set");
    }

    @Override
    public boolean setHidden(File file) {
        final boolean hidden = true;
        SVNUnixCommandLineFileUtil.execCommand("attrib ", (hidden ? "+" : "-") + "H", file.getAbsolutePath());
        //TODO: how to check for success?
        return true;
    }
}
