/*
 * ====================================================================
 * Copyright (c) 2004-2008 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.wc.admin2;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

import org.tmatesoft.svn.core.SVNCancelException;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;


/**
 * @version 1.1.2
 * @author  TMate Software Ltd.
 */
public class SVNUtil {
    
    public static final byte[] HASH_TERMINATOR = {'E', 'N', 'D'};
    private static final byte[] NEW_PROP_TERMINATOR = {'P', 'R', 'O', 'P', 'S', '-', 'E', 'N', 'D'};

    public static int getWCFormat(String path) throws SVNException {
        SVNErrorMessage err = null;
        int wcFormat = 0;
        File formatFile = SVNAdminFiles.getAdminFile(path, SVNAdminFiles.ADM_ENTRIES, false);
        try {
            wcFormat = SVNAdminFiles.readVersionFile(formatFile);
        } catch (SVNCancelException e) {
            throw e;
        } catch (SVNException e) {
            err = e.getErrorMessage();
        }
        if (err != null && err.getErrorCode() == SVNErrorCode.BAD_VERSION_FILE_FORMAT) {
            try {
                err = null;
                formatFile = SVNAdminFiles.getAdminFile(path, SVNAdminFiles.ADM_ENTRIES, false);
                wcFormat = SVNAdminFiles.readVersionFile(formatFile);
            } catch (SVNCancelException e) {
                throw e;
            } catch (SVNException e) {
                err = e.getErrorMessage();  
            }
        }
        if (err != null && !formatFile.exists()) {
            File wcFile = new File(path);
            if (!wcFile.exists()) {
                err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, "''{0}'' does not exist", wcFile);
                SVNErrorManager.error(err);
            }
            wcFormat = 0;
        } else if (err != null) {
            SVNErrorManager.error(err);
        }
        SVNUtil.assertWCFormatIsSupported(wcFormat, new File(path));
        return wcFormat;
    }

    public static void assertWCFormatIsSupported(int format, File path) throws SVNException {
        if (format < SVNAdminFiles.MINIMUM_WC_FORMAT) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_UNSUPPORTED_FORMAT, 
                    "Working copy format of ''{0}'' is too old {1}; " +
            		"please check out your working copy again", new Object[] {path, new Integer(format)});
            SVNErrorManager.error(err);
        } else if (format > SVNAdminFiles.MAXIMUM_WC_FORMAT) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_UNSUPPORTED_FORMAT, 
                    "This client is too old to work with working copy ''{0}''. You need \n" +
                    "to get a newer Subversion client, or to downgrade this working copy.\n" +
                    "See http://subversion.tigris.org/faq.html#working-copy-format-change\nfor details.",
                    path);
            SVNErrorManager.error(err);
        }
    }
    
    public static Map readHash(InputStream is, byte[] terminator, boolean incremental) throws IOException, SVNException {
        Map map = new HashMap();
        while (true) {
            byte[] line = readLine(is, (byte) '\n');
            if ((terminator == null && line == null) || (terminator != null && equals(line, terminator))) {
                return map;
            }
            if (line.length >= 3 && line[0] == 'K' && line[1] == ' ') {
                int keyLength = 0;
                try {
                    keyLength = Integer.parseInt(new String(line, 2, line.length - 2));
                } catch (NumberFormatException nfe) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.MALFORMED_FILE);
                    SVNErrorManager.error(err);
                }
                byte[] key = new byte[keyLength];
                int read = is.read(key, 0, keyLength);
                if (read != keyLength) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.MALFORMED_FILE);
                    SVNErrorManager.error(err);
                }
                int c = is.read();
                if (c != '\n') {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.MALFORMED_FILE);
                    SVNErrorManager.error(err);
                }
                line = readLine(is, (byte) '\n');
                if (line.length >= 3 && line[0] == 'V' && line[1] == ' ') {
                    int valueLength = 0;
                    try {
                        valueLength = Integer.parseInt(new String(line, 2, line.length - 2));
                    } catch (NumberFormatException nfe) {
                        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.MALFORMED_FILE);
                        SVNErrorManager.error(err);
                    }
                    byte[] value = new byte[valueLength];
                    read = is.read(value, 0, value.length);
                    if (read != valueLength) {
                        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.MALFORMED_FILE);
                        SVNErrorManager.error(err);
                    }
                    c = is.read();
                    if (c != '\n') {
                        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.MALFORMED_FILE);
                        SVNErrorManager.error(err);
                    }
                    // consider key to be UTF-8 always :(
                    map.put(new String(key, 0, keyLength, "UTF-8"), value);
                } else {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.MALFORMED_FILE);
                    SVNErrorManager.error(err);
                }
            } else if (incremental && line.length >= 3 && line[0] == 'D' && line[1] == ' ') {
                int keyLength = 0;
                try {
                    keyLength = Integer.parseInt(new String(line, 2, line.length - 2));
                } catch (NumberFormatException nfe) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.MALFORMED_FILE);
                    SVNErrorManager.error(err);
                }
                byte[] key = new byte[keyLength];
                int read = is.read(key, 0, key.length);
                if (read != keyLength) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.MALFORMED_FILE);
                    SVNErrorManager.error(err);
                }
                int c = is.read();
                if (c != '\n') {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.MALFORMED_FILE);
                    SVNErrorManager.error(err);
                }
                map.remove(new String(key, 0, keyLength, "UTF-8"));
            } else {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.MALFORMED_FILE);
                SVNErrorManager.error(err);
            }
        }
    }

    public static Map readHash(InputStream is) throws IOException, SVNException {
        Map map = new HashMap();
        boolean firstTime = true;
        while(true) {
            byte[] line = readLine(is, (byte) '\n');
            if (line == null && firstTime) {
                return map;
            } else if (line == null) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.MALFORMED_FILE);
                SVNErrorManager.error(err);
            }
            firstTime = false;
            if ((line.length == 3 && equals(line, HASH_TERMINATOR)) ||
                    (line.length == 9 && equals(line, NEW_PROP_TERMINATOR))) {
                return map;
            } else if (line.length >= 3 && line[0] == 'K' && line[1] == ' ') {
                int keyLength = 0;
                try {
                    keyLength = Integer.parseInt(new String(line, 2, line.length - 2));
                } catch (NumberFormatException nfe) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.MALFORMED_FILE);
                    SVNErrorManager.error(err);
                }
                byte[] key = new byte[keyLength];
                int read = is.read(key, 0, keyLength);
                if (read != keyLength) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.MALFORMED_FILE);
                    SVNErrorManager.error(err);
                }
                int c = is.read();
                if (c != '\n') {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.MALFORMED_FILE);
                    SVNErrorManager.error(err);
                }
                line = readLine(is, (byte) '\n');
                if (line.length >= 3 && line[0] == 'V' && line[1] == ' ') {
                    int valueLength = 0;
                    try {
                        valueLength = Integer.parseInt(new String(line, 2, line.length - 2));
                    } catch (NumberFormatException nfe) {
                        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.MALFORMED_FILE);
                        SVNErrorManager.error(err);
                    }
                    byte[] value = new byte[valueLength];
                    read = is.read(value, 0, value.length);
                    if (read != valueLength) {
                        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.MALFORMED_FILE);
                        SVNErrorManager.error(err);
                    }
                    c = is.read();
                    if (c != '\n') {
                        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.MALFORMED_FILE);
                        SVNErrorManager.error(err);
                    }
                    // consider key to be UTF-8 always :(
                    map.put(new String(key, 0, keyLength, "UTF-8"), value);
                } else {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.MALFORMED_FILE);
                    SVNErrorManager.error(err);
                }
            } else {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.MALFORMED_FILE);
                SVNErrorManager.error(err);
            }
        }
    }
    
    public static byte[] readLine(InputStream is, byte terminator) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        while(true) {
            int b = is.read();
            if (b < 0) {
                return null;
            } else if (b == terminator) {
                return baos.toByteArray();
            }
            baos.write(b);
        }
    }
    
    public static void writeHash(OutputStream os, Map map, Map oldMap, byte[] terminator) throws IOException {
        TreeMap sorted = new TreeMap(map);
        for (Iterator names = sorted.keySet().iterator(); names.hasNext();) {
            String name = (String) names.next();
            byte[] value = (byte[]) map.get(name);
            if (oldMap != null) {
                byte[] oldValue = (byte[]) oldMap.get(name);
                if (oldValue != null && equals(value, oldValue)) {
                    continue;
                }
            }
            byte[] nameBytes = name.getBytes("UTF-8");
            os.write('K');
            os.write(' ');
            os.write(Integer.toString(nameBytes.length).getBytes("UTF-8"));
            os.write('\n');
            os.write(nameBytes);
            os.write('\n');
            os.write('V');
            os.write(' ');
            os.write(Integer.toString(value.length).getBytes("UTF-8"));
            os.write('\n');
            os.write(value);
            os.write('\n');
        }
        if (oldMap != null) {
            sorted = new TreeMap(oldMap);
            for (Iterator names = sorted.keySet().iterator(); names.hasNext();) {
                String name = (String) names.next();
                byte[] nameBytes = name.getBytes("UTF-8");
                os.write('D');
                os.write(' ');
                os.write(Integer.toString(nameBytes.length).getBytes("UTF-8"));
                os.write('\n');
                os.write(nameBytes);
                os.write('\n');
            }
        }
        if (terminator != null) {
            os.write(terminator);
            os.write('\n');
        }
    }

    private static boolean equals(byte[] line1, byte[] line2) {
        if (line1.length != line2.length) {
            return false;
        }
        for (int i = 0; i < line2.length; i++) {
            if (line1[i] != line2[i]) {
                return false;
            }
        }
        return true;
    }

}
