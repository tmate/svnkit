package org.tmatesoft.svn.core.internal.wc.v17;

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
import org.tmatesoft.svn.util.SVNLogType;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;

public class SVNWCUtil17 extends SVNWCUtil {
	protected SVNWCUtil dispatcher;

	protected SVNWCUtil17(SVNWCUtil from) {
		super(from);
		this.dispatcher = dispatcher;
	}

	public static SVNWCUtil17 delegate(SVNWCUtil dispatcher) {
		SVNWCUtil17 delegate = new SVNWCUtil17(dispatcher);
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
		return null;
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
		return null;
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
		return null;
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
		SVNErrorMessage err = SVNErrorMessage
				.create(SVNErrorCode.VERSION_MISMATCH);
		SVNErrorManager.error(err, SVNLogType.CLIENT);
		return null;
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
		return null;
	}

	/** 
	 * @from org.tmatesoft.svn.core.wc.SVNWCUtil
	 */
	static public boolean isEclipse() {
		return false;
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
		return null;
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
		return null;
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
		SVNErrorMessage err = SVNErrorMessage
				.create(SVNErrorCode.VERSION_MISMATCH);
		SVNErrorManager.error(err, SVNLogType.CLIENT);
		return false;
	}

	/** 
	 * Determines if a directory is under version control.
	 * @param dira directory to check
	 * @return <span class="javakeyword">true</span> if versioned, otherwise
	 * <span class="javakeyword">false</span>
	 * @from org.tmatesoft.svn.core.wc.SVNWCUtil
	 */
	public static boolean isVersionedDirectory(File dir) {
		return false;
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
		return null;
	}
}
