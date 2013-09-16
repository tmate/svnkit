/*
 * ====================================================================
 * Copyright (c) 2004-2012 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.util.jna;

import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc.SVNMethodCallLogger;
import org.tmatesoft.svn.util.SVNDebugLog;
import org.tmatesoft.svn.util.SVNLogType;

import com.sun.jna.Native;


/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
class JNALibraryLoader {

    private static final String GLIB_LIBRARY = "glib-2.0";
    private static final String GNOME_KEYRING_LIBRARY = "gnome-keyring";
    
    private static ISVNWinCryptLibrary ourWinCryptLibrary;
    private static ISVNKernel32Library ourKernelLibrary;
    private static ISVNSecurityLibrary ourSecurityLibrary;
    private static ISVNCLibrary ourCLibrary;
    private static ISVNWin32Library ourWin32Library;
    private static ISVNMacOsSecurityLibrary ourMacOsSecurityLibrary;

    private static ISVNMacOsCFLibrary ourMacOsCFLibrary;
    private static ISVNGnomeKeyringLibrary ourGnomeKeyringLibrary;

    private static ISVNGLibrary ourGLibrary;
    private static volatile int ourUID = -1;

    private static volatile int ourGID = -1;

    static {
        // load win32 libraries.
        if (SVNFileUtil.isWindows && !SVNFileUtil.isOS2) {
            try {
                ourWinCryptLibrary = (ISVNWinCryptLibrary) Native.loadLibrary("Crypt32", ISVNWinCryptLibrary.class);
                ourWinCryptLibrary = SVNFileUtil.logNativeCalls && ourWinCryptLibrary != null ? new DebugProxyISVNWinCryptLibrary(ourWinCryptLibrary, SVNDebugLog.getDefaultLog()) : ourWinCryptLibrary;
                ourKernelLibrary = (ISVNKernel32Library) Native.loadLibrary("Kernel32", ISVNKernel32Library.class);
                ourKernelLibrary = SVNFileUtil.logNativeCalls && ourKernelLibrary != null ? new DebugProxyISVNKernel32Library(ourKernelLibrary, SVNDebugLog.getDefaultLog()) : ourKernelLibrary;
                ourSecurityLibrary = loadSecurityLibrary();
                ourWin32Library = (ISVNWin32Library) Native.loadLibrary("Shell32", ISVNWin32Library.class);
                ourWin32Library = SVNFileUtil.logNativeCalls && ourWin32Library != null ?
                        new DebugProxyISVNWin32Library(ourWin32Library, SVNDebugLog.getDefaultLog()) : ourWin32Library;
            } catch (Throwable th) {
                SVNDebugLog.getDefaultLog().logFine(SVNLogType.DEFAULT, th);
                ourWinCryptLibrary = null;
                ourKernelLibrary = null;
                ourSecurityLibrary = null;
                ourWin32Library = null;
            }
            
            SVNDebugLog.getDefaultLog().logFine(SVNLogType.NETWORK,  "security library name: " + getSecurityLibraryName());
            SVNDebugLog.getDefaultLog().logFine(SVNLogType.NETWORK,  "security library: " + ourSecurityLibrary);
        }
        
        if (SVNFileUtil.isOSX || SVNFileUtil.isLinux || SVNFileUtil.isBSD || SVNFileUtil.isSolaris) {
            try {
                ISVNCLibrary isvncLibrary = (ISVNCLibrary) Native.loadLibrary("c", ISVNCLibrary.class);
                ourCLibrary = SVNFileUtil.logNativeCalls && isvncLibrary != null ? new DebugProxyISVNCLibrary(isvncLibrary, SVNDebugLog.getDefaultLog()) :  isvncLibrary;
                SVNDebugLog.getDefaultLog().logFine(SVNLogType.DEFAULT, "C Library loaded with JNA: " + isvncLibrary);
                try {
                    ourUID = ourCLibrary.getuid();
                } catch (Throwable th) {
                    SVNDebugLog.getDefaultLog().logFine(SVNLogType.DEFAULT, th);
                    ourUID = -1;
                }
                try {
                    ourGID = ourCLibrary.getgid();
                } catch (Throwable th) {
                    SVNDebugLog.getDefaultLog().logFine(SVNLogType.DEFAULT, th);
                    ourGID = -1;
                }

            } catch (Throwable th) {
                SVNDebugLog.getDefaultLog().logFine(SVNLogType.DEFAULT, th);
                ourCLibrary = null;
            }
        }

        if (SVNFileUtil.isLinux || SVNFileUtil.isBSD || SVNFileUtil.isSolaris) {
            try {
                ISVNGnomeKeyringLibrary gnomeKeyringLibrary = (ISVNGnomeKeyringLibrary) Native.loadLibrary(getGnomeKeyringLibraryName(), ISVNGnomeKeyringLibrary.class);
                ISVNGLibrary gLibrary = (ISVNGLibrary) Native.loadLibrary(getGLibraryName(), ISVNGLibrary.class);

                Class<?>[] callSites = new Class[]{SVNGnomeKeyring.class, JNALibraryLoader.class};
                if (gnomeKeyringLibrary != null) {
                    ourGnomeKeyringLibrary = (ISVNGnomeKeyringLibrary) SVNMethodCallLogger.newInstance(gnomeKeyringLibrary, callSites);
                    ourGnomeKeyringLibrary = SVNFileUtil.logNativeCalls && ourGnomeKeyringLibrary != null ?
                            new DebugProxyISVNGnomeKeyringLibrary(ourGnomeKeyringLibrary, SVNDebugLog.getDefaultLog()) : ourGnomeKeyringLibrary;
                } else {
                    ourGnomeKeyringLibrary = null;
                }

                if (gLibrary != null) {
                    ourGLibrary = (ISVNGLibrary) SVNMethodCallLogger.newInstance(gLibrary, callSites);
                    ourGLibrary = SVNFileUtil.logNativeCalls && ourGLibrary != null?
                            new DebugProxyISVNGLibrary(ourGLibrary, SVNDebugLog.getDefaultLog()) : ourGLibrary;
                } else {
                    ourGLibrary = null;
                }

                SVNGnomeKeyring.initialize();
            } catch (Throwable th) {
                SVNDebugLog.getDefaultLog().logFine(SVNLogType.DEFAULT, th);
                ourGnomeKeyringLibrary = null;
                ourGLibrary = null;
            }
        }

        if (SVNFileUtil.isOSX) {
            try {
                ourMacOsSecurityLibrary = (ISVNMacOsSecurityLibrary) Native.loadLibrary("Security", ISVNMacOsSecurityLibrary.class);
                ourMacOsSecurityLibrary = SVNFileUtil.logNativeCalls && ourMacOsSecurityLibrary != null?
                        new DebugProxyISVNMacOsSecurityLibrary(ourMacOsSecurityLibrary, SVNDebugLog.getDefaultLog()) : ourMacOsSecurityLibrary;
                ourMacOsCFLibrary = (ISVNMacOsCFLibrary) Native.loadLibrary("CoreFoundation", ISVNMacOsCFLibrary.class);
                ourMacOsCFLibrary = SVNFileUtil.logNativeCalls && ourMacOsCFLibrary != null?
                        new DebugProxyISVNMacOsCFLibrary(ourMacOsCFLibrary, SVNDebugLog.getDefaultLog()) : ourMacOsCFLibrary;
            } catch (Throwable th) {
                SVNDebugLog.getDefaultLog().logFine(SVNLogType.DEFAULT, th);
                ourMacOsSecurityLibrary = null;
                ourMacOsCFLibrary = null;
            }
        }
    }

    private static String getGLibraryName() {
        return System.getProperty("svnkit.library.glib", GLIB_LIBRARY);
    }

    private static String getGnomeKeyringLibraryName() {
        return System.getProperty("svnkit.library.gnome-keyring", GNOME_KEYRING_LIBRARY);
    }

    public static int getUID() {
        return ourUID;
    }

    public static int getGID() {
        return ourGID;
    }
    
    public static synchronized ISVNWinCryptLibrary getWinCryptLibrary() {
        return ourWinCryptLibrary;
    }

    public static synchronized ISVNWin32Library getWin32Library() {
        return ourWin32Library;
    }

    public static synchronized ISVNKernel32Library getKernelLibrary() {
        return ourKernelLibrary;
    }

    public static synchronized ISVNSecurityLibrary getSecurityLibrary() {
        return ourSecurityLibrary;
    }
    
    public static synchronized ISVNCLibrary getCLibrary() {
        return ourCLibrary;
    }

    public static synchronized ISVNMacOsSecurityLibrary getMacOsSecurityLibrary() {
        return ourMacOsSecurityLibrary;
    }

    public static synchronized ISVNMacOsCFLibrary getMacOsCFLibrary() {
        return ourMacOsCFLibrary;
    }

    public static synchronized ISVNGnomeKeyringLibrary getGnomeKeyringLibrary() {
        return ourGnomeKeyringLibrary;
    }

    public static synchronized ISVNGLibrary getGLibrary() {
        return ourGLibrary;
    }
    
    private static ISVNSecurityLibrary loadSecurityLibrary() {
        ISVNSecurityLibrary lib = null;
        try {
            SVNDebugLog.getDefaultLog().logFine(SVNLogType.DEFAULT, "Loading 'Security' library");
            lib = (ISVNSecurityLibrary) Native.loadLibrary("Security", ISVNSecurityLibrary.class);
        } catch (Throwable th) {
            SVNDebugLog.getDefaultLog().logFine(SVNLogType.DEFAULT, th);
        }
        if (lib == null) {
            try {
                SVNDebugLog.getDefaultLog().logFine(SVNLogType.DEFAULT, "Loading 'Secur32' library");
                lib = (ISVNSecurityLibrary) Native.loadLibrary("Secur32", ISVNSecurityLibrary.class);
            } catch (Throwable th) {
                SVNDebugLog.getDefaultLog().logFine(SVNLogType.DEFAULT, th);
            }
        }
        SVNDebugLog.getDefaultLog().logFine(SVNLogType.DEFAULT, "Library loaded: " + lib);
        return SVNFileUtil.logNativeCalls && lib != null ? new DebugProxyISVNSecurityLibrary(lib, SVNDebugLog.getDefaultLog()) : lib;
    }

    private static String getSecurityLibraryName() {
        ISVNKernel32Library library = getKernelLibrary();
        if (library == null) {
            return null;
        }

        ISVNKernel32Library.OSVERSIONINFO osInfo = null;
        synchronized (library) {
            try {
                osInfo = new ISVNKernel32Library.OSVERSIONINFO();
                osInfo.write();
                int rc = library.GetVersionExW(osInfo.getPointer());
                osInfo.read();
                if (rc == 0) {
                    return null;
                }
            } catch (Throwable th) {
                SVNDebugLog.getDefaultLog().logFine(SVNLogType.DEFAULT, th);
                return null;
            }
        }

        if (osInfo.dwPlatformId.intValue() == ISVNKernel32Library.VER_PLATFORM_WIN32_NT) {
            return "Security";
        } else if (osInfo.dwPlatformId.intValue() == ISVNKernel32Library.VER_PLATFORM_WIN32_WINDOWS) {
            return "Secur32";
        }
        return null;
    }
}
