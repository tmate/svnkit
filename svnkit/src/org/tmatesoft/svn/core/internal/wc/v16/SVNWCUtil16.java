package org.tmatesoft.svn.core.internal.wc.v16;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.logging.Level;
import org.tmatesoft.svn.core.SVNCancelException;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.internal.wc.DefaultSVNAuthenticationManager;
import org.tmatesoft.svn.core.internal.wc.DefaultSVNOptions;
import org.tmatesoft.svn.core.internal.wc.SVNExternal;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc.admin.SVNAdminArea;
import org.tmatesoft.svn.core.internal.wc.admin.SVNVersionedProperties;
import org.tmatesoft.svn.core.internal.wc.admin.SVNWCAccess;
import org.tmatesoft.svn.core.wc.SVNWCUtil;
import org.tmatesoft.svn.core.wc.SVNWCUtil;

public class SVNWCUtil16 extends SVNWCUtil {
	protected SVNWCUtil dispatcher;

	protected SVNWCUtil16(SVNWCUtil from) {
		super(from);
		this.dispatcher = dispatcher;
	}

	public static SVNWCUtil16 delegate(SVNWCUtil dispatcher) {
		SVNWCUtil16 delegate = new SVNWCUtil16(dispatcher);
		return delegate;
	}

	/** 
	 * Creates a default run-time configuration options driver that uses the
	 * default SVN's run-time configuration area.
	 * @param readonlyif <span class="javakeyword">true</span> then run-time
	 * configuration options are available only for reading, if <span
	 * class="javakeyword">false</span> then those options are
	 * available for both reading and writing
	 * @return a default implementation of the run-time configuration options
	 * driver interface
	 * @see #getDefaultConfigurationDirectory()
	 * @from org.tmatesoft.svn.core.wc.SVNWCUtil
	 */
	public static DefaultSVNOptions createDefaultOptions(boolean readonly) {
		return new DefaultSVNOptions(null, readonly);
	}

	/** 
	 * Gets the location of the default SVN's run-time configuration area on the
	 * current machine. The result path depends on the platform on which SVNKit
	 * is running:
	 * <ul>
	 * <li>on <i>Windows</i> this path usually looks like <i>'Documents and
	 * Settings\UserName\Subversion'</i> or simply <i>'%APPDATA%\Subversion'</i>.
	 * <li>on a <i>Unix</i>-like platform - <i>'~/.subversion'</i>.
	 * </ul>
	 * @return a {@link java.io.File} representation of the default SVN's
	 * run-time configuration area location
	 * @from org.tmatesoft.svn.core.wc.SVNWCUtil
	 */
	public static File getDefaultConfigurationDirectory() {
		if (SVNFileUtil.isWindows) {
			return new File(SVNFileUtil.getApplicationDataPath(), "Subversion");
		} else if (SVNFileUtil.isOpenVMS) {
			return new File("/sys$login", ".subversion").getAbsoluteFile();
		}
		return new File(System.getProperty("user.home"), ".subversion");
	}

	/** 
	 * Creates a default authentication manager that uses the provided
	 * configuration directory and user's credentials. The
	 * <code>storeAuth</code> parameter affects on using the auth storage.
	 * @param configDira new location of the run-time configuration area
	 * @param userNamea user's name
	 * @param passworda user's password
	 * @param storeAuthif <span class="javakeyword">true</span> then the auth
	 * storage is enabled, otherwise disabled
	 * @return a default implementation of the credentials and servers
	 * configuration driver interface
	 * @from org.tmatesoft.svn.core.wc.SVNWCUtil
	 */
	public static ISVNAuthenticationManager createDefaultAuthenticationManager(
			File configDir, String userName, String password, boolean storeAuth) {
		return createDefaultAuthenticationManager(configDir, userName,
				password, null, null, storeAuth);
	}

	/** 
	 * Returns the Working Copy root directory given a versioned directory that
	 * belongs to the Working Copy.
	 * <p>
	 * If both <span>versionedDir</span> and its parent directory are not
	 * versioned this method returns <span class="javakeyword">null</span>.
	 * @param versionedDira directory belonging to the WC which root is to be searched
	 * for
	 * @param stopOnExtenralsif <span class="javakeyword">true</span> then this method
	 * will stop at the directory on which any externals definitions
	 * are set
	 * @return the WC root directory (if it is found) or <span
	 * class="javakeyword">null</span>.
	 * @throws SVNException
	 * @from org.tmatesoft.svn.core.wc.SVNWCUtil
	 */
	public static File getWorkingCopyRoot(File versionedDir,
			boolean stopOnExtenrals) throws SVNException {
		versionedDir = versionedDir.getAbsoluteFile();
		if (versionedDir == null
				|| (!isVersionedDirectory(versionedDir) && !isVersionedDirectory(versionedDir
						.getParentFile()))) {
			return null;
		}
		File parent = versionedDir.getParentFile();
		if (parent == null) {
			return versionedDir;
		}
		if (isWorkingCopyRoot(versionedDir)) {
			if (stopOnExtenrals) {
				return versionedDir;
			}
			File parentRoot = getWorkingCopyRoot(parent, stopOnExtenrals);
			if (parentRoot == null) {
				return versionedDir;
			}
			while (parent != null) {
				SVNWCAccess parentAccess = SVNWCAccess.newInstance(null);
				try {
					SVNAdminArea dir = parentAccess.open(parent, false, 0);
					SVNVersionedProperties props = dir.getProperties(dir
							.getThisDirName());
					final String externalsProperty = props
							.getStringPropertyValue(SVNProperty.EXTERNALS);
					SVNExternal[] externals = externalsProperty != null ? SVNExternal
							.parseExternals(dir.getRoot().getAbsolutePath(),
									externalsProperty)
							: new SVNExternal[0];
					for (int i = 0; i < externals.length; i++) {
						SVNExternal external = externals[i];
						File externalFile = new File(parent, external.getPath());
						if (externalFile.equals(versionedDir)) {
							return parentRoot;
						}
					}
				} catch (SVNException e) {
					if (e instanceof SVNCancelException) {
						throw e;
					}
				} finally {
					parentAccess.close();
				}
				if (parent.equals(parentRoot)) {
					break;
				}
				parent = parent.getParentFile();
			}
			return versionedDir;
		}
		return getWorkingCopyRoot(parent, stopOnExtenrals);
	}

	/** 
	 * Creates a default run-time configuration options driver that uses the
	 * provided configuration directory.
	 * <p>
	 * If <code>dir</code> is not <span class="javakeyword">null</span> then
	 * all necessary config files (in particular <i>config</i> and <i>servers</i>)
	 * will be created in this directory if they still don't exist. Those files
	 * are the same as those ones you can find in the default SVN's run-time
	 * configuration area.
	 * @param dira new location of the run-time configuration area
	 * @param readonlyif <span class="javakeyword">true</span> then run-time
	 * configuration options are available only for reading, if <span
	 * class="javakeyword">false</span> then those options are
	 * available for both reading and writing
	 * @return a default implementation of the run-time configuration options
	 * driver interface
	 * @from org.tmatesoft.svn.core.wc.SVNWCUtil
	 */
	public static DefaultSVNOptions createDefaultOptions(File dir,
			boolean readonly) {
		return new DefaultSVNOptions(dir, readonly);
	}

	/** 
	 * @from org.tmatesoft.svn.core.wc.SVNWCUtil
	 */
	static public boolean isEclipse() {
		if (ourIsEclipse == null) {
			try {
				ClassLoader loader = SVNWCUtil16.class.getClassLoader();
				if (loader == null) {
					loader = ClassLoader.getSystemClassLoader();
				}
				Class platform = loader
						.loadClass("org.eclipse.core.runtime.Platform");
				Method isRunning = platform
						.getMethod("isRunning", new Class[0]);
				Object result = isRunning.invoke(null, new Object[0]);
				if (result != null && Boolean.TRUE.equals(result)) {
					ourIsEclipse = Boolean.TRUE;
					return true;
				}
			} catch (Throwable th) {
			}
			ourIsEclipse = Boolean.FALSE;
		}
		return ourIsEclipse.booleanValue();
	}

	/** 
	 * Creates a default authentication manager that uses the default SVN's
	 * <i>servers</i> configuration and authentication storage. Whether the
	 * default auth storage is used or not depends on the 'store-auth-creds'</i>
	 * option that can be found in the SVN's <i>config</i> file under the
	 * <i>[auth]</i> section.
	 * @return a default implementation of the credentials and servers
	 * configuration driver interface
	 * @see #getDefaultConfigurationDirectory()
	 * @from org.tmatesoft.svn.core.wc.SVNWCUtil
	 */
	public static ISVNAuthenticationManager createDefaultAuthenticationManager() {
		return createDefaultAuthenticationManager(
				getDefaultConfigurationDirectory(), null, null);
	}

	/** 
	 * Creates a default authentication manager that uses the provided
	 * configuration directory and user's credentials. The
	 * <code>storeAuth</code> parameter affects on using the auth storage.
	 * @param configDira new location of the run-time configuration area
	 * @param userNamea user's name
	 * @param passworda user's password
	 * @param privateKeya private key file for SSH session
	 * @param passphrasea passphrase that goes with the key file
	 * @param storeAuthif <span class="javakeyword">true</span> then the auth
	 * storage is enabled, otherwise disabled
	 * @return a default implementation of the credentials and servers
	 * configuration driver interface
	 * @from org.tmatesoft.svn.core.wc.SVNWCUtil
	 */
	public static ISVNAuthenticationManager createDefaultAuthenticationManager(
			File configDir, String userName, String password, File privateKey,
			String passphrase, boolean storeAuth) {
		if (isEclipse()) {
			try {
				ClassLoader loader = SVNWCUtil16.class.getClassLoader();
				if (loader == null) {
					loader = ClassLoader.getSystemClassLoader();
				}
				Class managerClass = loader
						.loadClass(ECLIPSE_AUTH_MANAGER_CLASSNAME);
				if (managerClass != null) {
					Constructor method = managerClass
							.getConstructor(new Class[] { File.class,
									Boolean.TYPE, String.class, String.class,
									File.class, String.class });
					if (method != null) {
						return (ISVNAuthenticationManager) method
								.newInstance(new Object[] {
										configDir,
										storeAuth ? Boolean.TRUE
												: Boolean.FALSE, userName,
										password, privateKey, passphrase });
					}
				}
			} catch (Throwable e) {
			}
		}
		return new DefaultSVNAuthenticationManager(configDir, storeAuth,
				userName, password, privateKey, passphrase);
	}

	/** 
	 * Determines if a directory is the root of the Working Copy.
	 * @param versionedDira versioned directory to check
	 * @return <span class="javakeyword">true</span> if
	 * <code>versionedDir</code> is versioned and the WC root (or the
	 * root of externals if <code>considerExternalAsRoot</code> is
	 * <span class="javakeyword">true</span>), otherwise <span
	 * class="javakeyword">false</span>
	 * @throws SVNException
	 * @since 1.1
	 * @from org.tmatesoft.svn.core.wc.SVNWCUtil
	 */
	public static boolean isWorkingCopyRoot(final File versionedDir)
			throws SVNException {
		SVNWCAccess wcAccess = SVNWCAccess.newInstance(null);
		try {
			wcAccess.open(versionedDir, false, false, false, 0, Level.FINEST);
			return wcAccess.isWCRoot(versionedDir);
		} catch (SVNException e) {
			return false;
		} finally {
			wcAccess.close();
		}
	}

	/** 
	 * Determines if a directory is under version control.
	 * @param dira directory to check
	 * @return <span class="javakeyword">true</span> if versioned, otherwise
	 * <span class="javakeyword">false</span>
	 * @from org.tmatesoft.svn.core.wc.SVNWCUtil
	 */
	public static boolean isVersionedDirectory(File dir) {
		SVNWCAccess wcAccess = SVNWCAccess.newInstance(null);
		try {
			wcAccess.open(dir, false, false, false, 0, Level.FINEST);
		} catch (SVNException e) {
			return false;
		} finally {
			try {
				wcAccess.close();
			} catch (SVNException e) {
			}
		}
		return true;
	}

	/** 
	 * Creates a default authentication manager that uses the provided
	 * configuration directory and user's credentials. Whether the default auth
	 * storage is used or not depends on the 'store-auth-creds'</i> option that
	 * is looked up in the <i>config</i> file under the <i>[auth]</i> section.
	 * Files <i>config</i> and <i>servers</i> will be created (if they still
	 * don't exist) in the specified directory (they are the same as those ones
	 * you can find in the default SVN's run-time configuration area).
	 * @param configDira new location of the run-time configuration area
	 * @param userNamea user's name
	 * @param passworda user's password
	 * @return a default implementation of the credentials and servers
	 * configuration driver interface
	 * @from org.tmatesoft.svn.core.wc.SVNWCUtil
	 */
	public static ISVNAuthenticationManager createDefaultAuthenticationManager(
			File configDir, String userName, String password) {
		DefaultSVNOptions options = createDefaultOptions(configDir, true);
		boolean store = options.isAuthStorageEnabled();
		return createDefaultAuthenticationManager(configDir, userName,
				password, store);
	}
}
