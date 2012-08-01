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

    private static int SYMLINK_MASK = 0120000;
    private static int REGULAR_FILE_MASK = 0100000;
    private static int DIRECTORY_MASK = 0040000;
    private static int BLOCK_DEVICE_MASK = 0060000;
    private static int CHARACTER_DEVICE_MASK = 0020000;
    private static int FIFO_MASK = 0020000;
    private static int SOCKET_MASK = 0140000;

    private static int OTHER_MASK = 0140000 | 0060000 | 0020000 | 0010000;
    private static int SUID_MASK = 0004000;
    private static int SGID_MASK = 0002000;

    private String lsCommand;
    private String lnCommand;
    private String chmodCommand;
    private String statCommand;

    public SVNUnixCommandLineFileUtil(String prefix) {
        lsCommand = prefix + "ls";
        lnCommand = prefix + "ln";
        chmodCommand = prefix + "chmod";
        statCommand = prefix + "stat";
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

    @Override
    public Long getFileLastModified(File file) {
        String output = execCommand(statCommand, "-c", "%Y", file.getAbsolutePath());
        if (output != null) {
            try {
                return parseSecondsConvertToMilliseconds(output);
            } catch (NumberFormatException e) {
                //ignore
            }
        }
        return null;
    }

    public SVNFileAttributes readFileAttributes(File file, boolean followSymlinks) {
        String format = "%Y %X %W %s %f %U %G %A";
        String output = followSymlinks ? execCommand(statCommand, "--dereference", "-c", format, file.getAbsolutePath()) :
                execCommand(statCommand, "-c", format, file.getAbsolutePath());
        if (output != null) {
            return parseAttributes(output);
        }
        return null;
    }

    protected SVNFileAttributes parseAttributes(String output) {
        SVNFileAttributes attributes = new SVNFileAttributes();
        String[] attributesArray = output.split(" ");

        long lastModifiedTime = parseSecondsConvertToMilliseconds(attributesArray[0]);
        long lastAccessedTime = parseSecondsConvertToMilliseconds(attributesArray[1]);
        long creationTime = parseSecondsConvertToMilliseconds(attributesArray[2]);

        if (creationTime == 0) {
            creationTime = lastModifiedTime;
        }

        attributes.setLastModifiedTime(lastModifiedTime);
        attributes.setLastAccessTime(lastAccessedTime);
        attributes.setCreationTime(creationTime);
        try {
            attributes.setSize(Long.parseLong(attributesArray[3]));
        } catch (NumberFormatException e) {
            attributes.setSize(-1);
        }

        int fileMode = Integer.parseInt(attributesArray[4], 16);
        boolean isDirectory = (fileMode & DIRECTORY_MASK) == DIRECTORY_MASK;
        boolean isRegularFile = (fileMode & REGULAR_FILE_MASK) == REGULAR_FILE_MASK;
        boolean isSymlink = (fileMode & SYMLINK_MASK) == SYMLINK_MASK;
        boolean isBlockDevice = (fileMode & BLOCK_DEVICE_MASK) == BLOCK_DEVICE_MASK;
        boolean isCharacterDevice = (fileMode & CHARACTER_DEVICE_MASK) == CHARACTER_DEVICE_MASK;
        boolean isFifo = (fileMode & FIFO_MASK) == FIFO_MASK;
        boolean isSocket = (fileMode & SOCKET_MASK) == SOCKET_MASK;

        boolean suid = (fileMode & SUID_MASK) != 0;
        boolean sgid = (fileMode & SGID_MASK) != 0;

        attributes.setIsDirectory(isDirectory);
        attributes.setIsRegularFile(isRegularFile);
        attributes.setSymbolicLink(isSymlink);
        attributes.setIsOther(isBlockDevice || isCharacterDevice || isFifo || isSocket);

        attributes.setSuid(suid);
        attributes.setSgid(sgid);

        attributes.setPosixOwner(attributesArray[5]);
        attributes.setPosixGroup(attributesArray[6]);

        String typePermissionsString = attributesArray[7];
        attributes.setPosixPermissions(SVNFilePermissions.parseString(typePermissionsString.substring(1)));

        return attributes;
    }

    private long parseSecondsConvertToMilliseconds(String s) {
        try {
            return Long.parseLong(s) * 1000;
        } catch (NumberFormatException e) {
            return 0;
        }
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
