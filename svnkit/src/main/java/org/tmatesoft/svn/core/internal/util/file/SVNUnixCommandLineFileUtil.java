package org.tmatesoft.svn.core.internal.util.file;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.util.SVNDebugLog;
import org.tmatesoft.svn.util.SVNLogType;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.logging.Level;

public class SVNUnixCommandLineFileUtil extends SVNFileUtilAdapter {

    private String lsCommand;
    private String lnCommand;
    private String chmodCommand;

    public SVNUnixCommandLineFileUtil(String prefix) {
        lsCommand = prefix + "ls";
        lnCommand = prefix + "ln";
        chmodCommand = prefix + "chmod";
    }

    static String execCommand(String... commandLine) {
        try {
            return SVNFileUtil.execCommand(commandLine);
        } catch (Throwable th) {
            SVNDebugLog.getDefaultLog().logFinest(SVNLogType.DEFAULT, th);
            return null;
        }
    }
    @Override
    public boolean isSymlink(File file) {
        return readSymlink(file) != null;
    }

    @Override
    public File readSymlink(File link) {
        final String output = execCommand(lsCommand, "-ld", link.getAbsolutePath());
        if (output == null || output.lastIndexOf(" -> ") < 0) {
            return null;
        }
        int index = output.lastIndexOf(" -> ") + " -> ".length();
        if (index <= output.length()) {
            return SVNFileUtil.createFilePath(output.substring(index));
        }
        return null;
    }

    @Override
    public boolean createSymlink(File link, File linkName) {
        execCommand(lnCommand, "-s", linkName.getPath(), link.getAbsolutePath());
        //TODO: how to check for success?
        return true;
    }

    @Override
    public boolean setReadOnly(File file) {
        execCommand(chmodCommand, "ugo+w", file.getAbsolutePath());
        //TODO: how to check for success?
        return true;
    }

    @Override
    public boolean isExecutable(File file) {
        String output = execCommand(lsCommand, "-ln", file.getAbsolutePath());
        if (output == null || output.indexOf(' ') < 0) {
            return false;
        }
        int index = 0;

        String mod = null;
        String fuid = null;
        String fgid = null;
        for (StringTokenizer tokens = new StringTokenizer(output, " \t"); tokens.hasMoreTokens();) {
            String token = tokens.nextToken();
            if (index == 0) {
                mod = token;
            } else if (index == 2) {
                fuid = token;
            } else if (index == 3) {
                fgid = token;
            } else if (index > 3) {
                break;
            }
            index++;
        }
        if (mod == null) {
            return false;
        }
        try {
            if (SVNFileUtil.getCurrentUser().equals(fuid)) {
                return mod.toLowerCase().indexOf('x') >= 0 && mod.toLowerCase().indexOf('x') < 4;
            } else if (SVNFileUtil.getCurrentGroup().equals(fgid)) {
                return mod.toLowerCase().indexOf('x', 4) >= 4 && mod.toLowerCase().indexOf('x', 4) < 7;
            } else {
                return mod.toLowerCase().indexOf('x', 7) >= 7;
            }
        } catch (SVNException e) {
            SVNDebugLog.getDefaultLog().logFinest(SVNLogType.DEFAULT, e);
            return false;
        }
    }

    @Override
    public boolean setExecutable(File file, boolean executable) {
        execCommand(chmodCommand, executable ? "ugo+x" : "ugo-x", file.getAbsolutePath());
        //TODO: how to check for success?
        return true;
    }

    @Override
    public boolean setSGID(File directory) {
        execCommand(chmodCommand, "g+s", directory.getAbsolutePath());
        //TODO: how to check for success?
        return true;
    }

    @Override
    public Properties getEnvironment() {
        return getEnvironmentFromCommandOutput("env");
    }

    static Properties getEnvironmentFromCommandOutput(String command) {
        Process p = null;
        Runtime r = Runtime.getRuntime();
        BufferedReader br = null;
        try {
            //TODO: can we execute using execCommand ?
            p = r.exec(command);
            if (p == null) {
                return null;
            }
            br = new BufferedReader(new InputStreamReader(p.getInputStream()));
            return SVNUnixCommandLineFileUtil.parseEnvironmentAndClose(br);
        } catch (Throwable th) {
            SVNDebugLog.getDefaultLog().log(SVNLogType.DEFAULT, th, Level.FINEST);
            return null;
        } finally {
            if (p != null) {
                try {
                    p.waitFor();
                } catch (InterruptedException e) {
                }
                SVNFileUtil.closeFile(br);
                p.destroy();
            }
        }
    }

    static Properties parseEnvironmentAndClose(BufferedReader br) throws IOException {
        String line;
        Properties envVars = new Properties();
        while ((line = br.readLine()) != null) {
            int idx = line.indexOf('=');
            if (idx >= 0) {
                String key = line.substring(0, idx);
                String value = line.substring(idx + 1);
                envVars.setProperty(key, value);
            }
        }
        return envVars;
    }
}
