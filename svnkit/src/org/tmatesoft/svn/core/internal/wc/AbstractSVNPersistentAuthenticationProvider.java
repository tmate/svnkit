/*
 * ====================================================================
 * Copyright (c) 2004-2009 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.wc;

import java.io.File;
import java.util.Map;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationProvider;
import org.tmatesoft.svn.core.auth.SVNAuthentication;
import org.tmatesoft.svn.core.auth.SVNPasswordAuthentication;
import org.tmatesoft.svn.core.auth.SVNSSHAuthentication;
import org.tmatesoft.svn.core.auth.SVNSSLAuthentication;
import org.tmatesoft.svn.core.auth.SVNUserNameAuthentication;
import org.tmatesoft.svn.core.internal.util.SVNHashMap;
import org.tmatesoft.svn.util.SVNLogType;

/**
 * @author TMate Software Ltd.
 * @version 1.3
 */
public class AbstractSVNPersistentAuthenticationProvider implements ISVNAuthenticationProvider {

    private File myDirectory;
    private String myUserName;
    private ISVNConnectionOptions myConnectionOptions;

    public AbstractSVNPersistentAuthenticationProvider(File directory, String userName, ISVNConnectionOptions connectionOptions) {
        myDirectory = directory;
        myUserName = userName;
        myConnectionOptions = connectionOptions;
    }

    private SVNPasswordAuthentication readSSLPassphrase(String kind, String realm, boolean storageAllowed) {
        File dir = new File(myDirectory, kind);
        if (!dir.isDirectory()) {
            return null;
        }
        String fileName = SVNFileUtil.computeChecksum(realm);
        File authFile = new File(dir, fileName);
        if (authFile.exists()) {
            SVNWCProperties props = new SVNWCProperties(authFile, "");
            try {
                SVNProperties values = props.asMap();
                String storedRealm = values.getStringValue("svn:realmstring");
                if (storedRealm == null || !storedRealm.equals(realm)) {
                    return null;
                }
                String cipherType = SVNPropertyValue.getPropertyAsString(values.getSVNPropertyValue("passtype"));
                if (cipherType != null && !SVNPasswordCipher.hasCipher(cipherType)) {
                    return null;
                }
                SVNPasswordCipher cipher = SVNPasswordCipher.getInstance(cipherType);
                String passphrase = SVNPropertyValue.getPropertyAsString(values.getSVNPropertyValue("passphrase"));
                if (cipher != null) {
                    passphrase = cipher.decrypt(passphrase);
                }
                return new SVNPasswordAuthentication("", passphrase, storageAllowed);
            } catch (SVNException e) {
                //
            }
        }
        return null;
    }

    public SVNAuthentication requestClientAuthentication(String kind, SVNURL url, String realm, SVNErrorMessage errorMessage,
                                                         SVNAuthentication previousAuth, boolean authMayBeStored) {
        if (ISVNAuthenticationManager.SSL.equals(kind)) {
            if (SVNSSLAuthentication.isCertificatePath(realm)) {
                return readSSLPassphrase(kind, realm, authMayBeStored);
            }
            String sslClientCert = myConnectionOptions.getSSLClientCertFile(url); // PKCS#12
            if (sslClientCert != null && !"".equals(sslClientCert)) {
                if (isMSCapi(sslClientCert)) {
                    String alias = null;
                    if (sslClientCert.lastIndexOf(';') > 0) {
                        alias = sslClientCert.substring(sslClientCert.lastIndexOf(';') + 1);
                    }
                    return new SVNSSLAuthentication(SVNSSLAuthentication.MSCAPI, alias, authMayBeStored, url, false);
                }

                sslClientCert = SVNSSLAuthentication.formatCertificatePath(sslClientCert);

                String sslClientCertPassword = myConnectionOptions.getSSLClientCertPassword(url);
                File clientCertFile = sslClientCert != null ? new File(sslClientCert) : null;
                SVNSSLAuthentication sslAuth = new SVNSSLAuthentication(clientCertFile, sslClientCertPassword, authMayBeStored, url, false);
                if (sslClientCertPassword == null || "".equals(sslClientCertPassword)) {
                    // read from cache at once.
                    SVNPasswordAuthentication passphrase = readSSLPassphrase(kind, sslClientCert, authMayBeStored);
                    if (passphrase != null && passphrase.getPassword() != null) {
                        sslAuth = new SVNSSLAuthentication(clientCertFile, passphrase.getPassword(), authMayBeStored, url, false);
                    }
                }
                sslAuth.setCertificatePath(sslClientCert);
                return sslAuth;
            }
        }

        File dir = new File(myDirectory, kind);
        if (!dir.isDirectory()) {
            return null;
        }
        String fileName = SVNFileUtil.computeChecksum(realm);
        File authFile = new File(dir, fileName);
        if (authFile.exists()) {
            SVNWCProperties props = new SVNWCProperties(authFile, "");
            try {
                SVNProperties values = props.asMap();
                String storedRealm = values.getStringValue("svn:realmstring");
                String cipherType = SVNPropertyValue.getPropertyAsString(values.getSVNPropertyValue("passtype"));
                if (cipherType != null && !SVNPasswordCipher.hasCipher(cipherType)) {
                    return null;
                }
                SVNPasswordCipher cipher = SVNPasswordCipher.getInstance(cipherType);
                if (storedRealm == null || !storedRealm.equals(realm)) {
                    return null;
                }

                String userName = SVNPropertyValue.getPropertyAsString(values.getSVNPropertyValue("username"));

                if (!ISVNAuthenticationManager.SSL.equals(kind)) {
                    if (userName == null || "".equals(userName.trim())) {
                        return null;
                    }
                    if (myUserName != null && !myUserName.equals(userName)) {
                        return null;
                    }
                }

                String password = SVNPropertyValue.getPropertyAsString(values.getSVNPropertyValue("password"));
                password = cipher.decrypt(password);

                String path = SVNPropertyValue.getPropertyAsString(values.getSVNPropertyValue("key"));
                String passphrase = SVNPropertyValue.getPropertyAsString(values.getSVNPropertyValue("passphrase"));
                passphrase = cipher.decrypt(passphrase);
                String port = SVNPropertyValue.getPropertyAsString(values.getSVNPropertyValue("port"));
                port = port == null ? ("" + myConnectionOptions.getDefaultSSHPortNumber()) : port;
                String sslKind = SVNPropertyValue.getPropertyAsString(values.getSVNPropertyValue("ssl-kind"));

                if (ISVNAuthenticationManager.PASSWORD.equals(kind)) {
                    if (password == null) {
                        return new SVNPasswordAuthentication(userName, password, authMayBeStored, null, true);
                    }
                    return new SVNPasswordAuthentication(userName, password, authMayBeStored, url, false);
                } else if (ISVNAuthenticationManager.SSH.equals(kind)) {
                    // get port from config file or system property?
                    int portNumber;
                    try {
                        portNumber = Integer.parseInt(port);
                    } catch (NumberFormatException nfe) {
                        portNumber = myConnectionOptions.getDefaultSSHPortNumber();
                    }
                    if (path != null) {
                        return new SVNSSHAuthentication(userName, new File(path), passphrase, portNumber, authMayBeStored, url, false);
                    } else if (password != null) {
                        return new SVNSSHAuthentication(userName, password, portNumber, authMayBeStored, url, false);
                    }
                } else if (ISVNAuthenticationManager.USERNAME.equals(kind)) {
                    return new SVNUserNameAuthentication(userName, authMayBeStored, url, false);
                } else if (ISVNAuthenticationManager.SSL.equals(kind)) {
                    if (isMSCapi(sslKind)) {
                        String alias = SVNPropertyValue.getPropertyAsString(values.getSVNPropertyValue("alias"));
                        return new SVNSSLAuthentication(SVNSSLAuthentication.MSCAPI, alias, authMayBeStored, url, false);
                    }
                    SVNSSLAuthentication sslAuth = new SVNSSLAuthentication(new File(path), passphrase, authMayBeStored, url, false);
                    if (passphrase == null || "".equals(passphrase)) {
                        SVNPasswordAuthentication passphraseAuth = readSSLPassphrase(kind, path, authMayBeStored);
                        if (passphraseAuth != null && passphraseAuth.getPassword() != null) {
                            sslAuth = new SVNSSLAuthentication(new File(path), passphraseAuth.getPassword(), authMayBeStored, url, false);
                        }
                    }
                    sslAuth.setCertificatePath(path);
                    return sslAuth;
                }
            } catch (SVNException e) {
                //
            }
        }
        return null;
    }

    public boolean isMSCapi(String filepath) {
        if (filepath != null && filepath.startsWith(SVNSSLAuthentication.MSCAPI)) {
            return true;
        }
        return false;
    }

    public void saveAuthentication(SVNAuthentication auth, String kind, String realm) throws SVNException {
        File dir = new File(myDirectory, kind);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        if (!dir.isDirectory()) {
            SVNErrorMessage error = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, "Cannot create directory ''{0}''", dir.getAbsolutePath());
            SVNErrorManager.error(error, SVNLogType.DEFAULT);
        }
        if (!ISVNAuthenticationManager.SSL.equals(kind) && ("".equals(auth.getUserName()) || auth.getUserName() == null)) {
            return;
        }

        Map values = new SVNHashMap();
        values.put("svn:realmstring", realm);

        if (ISVNAuthenticationManager.PASSWORD.equals(kind)) {
            savePasswordCredential(values, auth, realm);
        } else if (ISVNAuthenticationManager.SSH.equals(kind)) {
            saveSSHCredential(values, auth, realm);
        } else if (ISVNAuthenticationManager.SSL.equals(kind)) {
            saveSSLCredential(values, auth, realm);
        } else if (ISVNAuthenticationManager.USERNAME.equals(kind)) {
            saveUserNameCredential(values, auth);
        }
        // get file name for auth and store password.
        String fileName = SVNFileUtil.computeChecksum(realm);
        File authFile = new File(dir, fileName);

        if (authFile.isFile()) {
            SVNWCProperties props = new SVNWCProperties(authFile, "");
            try {
                if (values.equals(props.asMap())) {
                    return;
                }
            } catch (SVNException e) {
                //
            }
        }
        File tmpFile = SVNFileUtil.createUniqueFile(dir, "auth", ".tmp", true);
        try {
            SVNWCProperties.setProperties(SVNProperties.wrap(values), authFile, tmpFile, SVNWCProperties.SVN_HASH_TERMINATOR);
        } finally {
            SVNFileUtil.deleteFile(tmpFile);
        }
    }

    public int acceptServerAuthentication(SVNURL url, String r, Object serverAuth, boolean resultMayBeStored) {
        return ACCEPTED;
    }

    private void saveUserNameCredential(Map values, SVNAuthentication auth) {
        values.put("username", auth.getUserName());
    }

    private void savePasswordCredential(Map values, SVNAuthentication auth, String realm) throws SVNException {
        values.put("username", auth.getUserName());

        boolean storePasswords = myConnectionOptions.isStorePasswords(auth.getURL());
        boolean maySavePassword = false;

        SVNPasswordCipher cipher = null;

        if (storePasswords) {
            String cipherType = SVNPasswordCipher.getDefaultCipherType();
            cipher = SVNPasswordCipher.getInstance(cipherType);
            if (cipherType != null) {
                if (!SVNPasswordCipher.SIMPLE_CIPHER_TYPE.equals(cipherType)) {
                    maySavePassword = true;
                } else {
                    maySavePassword = myConnectionOptions.isStorePlainTextPasswords(realm, auth);
                }

                if (maySavePassword) {
                    values.put("passtype", cipherType);
                }
            }
        }

        if (maySavePassword) {
            SVNPasswordAuthentication passwordAuth = (SVNPasswordAuthentication) auth;
            values.put("password", cipher.encrypt(passwordAuth.getPassword()));
        }
    }

    private void saveSSHCredential(Map values, SVNAuthentication auth, String realm) throws SVNException {
        values.put("username", auth.getUserName());

        boolean storePasswords = myConnectionOptions.isStorePasswords(auth.getURL());
        boolean maySavePassword = false;

        SVNPasswordCipher cipher = null;

        if (storePasswords) {
            String cipherType = SVNPasswordCipher.getDefaultCipherType();
            cipher = SVNPasswordCipher.getInstance(cipherType);
            if (cipherType != null) {
                if (!SVNPasswordCipher.SIMPLE_CIPHER_TYPE.equals(cipherType)) {
                    maySavePassword = true;
                } else {
                    maySavePassword = myConnectionOptions.isStorePlainTextPasswords(realm, auth);
                }

                if (maySavePassword) {
                    values.put("passtype", cipherType);
                }
            }
        }

        SVNSSHAuthentication sshAuth = (SVNSSHAuthentication) auth;
        if (maySavePassword) {
            values.put("password", cipher.encrypt(sshAuth.getPassword()));
        }

        int port = sshAuth.getPortNumber();
        if (sshAuth.getPortNumber() < 0) {
            port = myConnectionOptions.getDefaultSSHPortNumber();
        }
        values.put("port", Integer.toString(port));

        if (sshAuth.getPrivateKeyFile() != null) {
            String path = sshAuth.getPrivateKeyFile().getAbsolutePath();
            if (maySavePassword) {
                values.put("passphrase", cipher.encrypt(sshAuth.getPassphrase()));
            }
            values.put("key", path);
        }
    }

    private void saveSSLCredential(Map values, SVNAuthentication auth, String realm) throws SVNException {
        boolean storePassphrases = myConnectionOptions.isStoreSSLClientCertificatePassphrases(auth.getURL());
        boolean maySavePassphrase = false;

        SVNPasswordCipher cipher = null;

        String passphrase;
        if (auth instanceof SVNPasswordAuthentication) {
            passphrase = ((SVNPasswordAuthentication) auth).getPassword();
        } else {
            // do not save passphrase, it have to be saved already.
            passphrase = null;
        }
        if (storePassphrases && passphrase != null) {
            String cipherType = SVNPasswordCipher.getDefaultCipherType();
            cipher = SVNPasswordCipher.getInstance(cipherType);
            if (cipherType != null) {
                if (!SVNPasswordCipher.SIMPLE_CIPHER_TYPE.equals(cipherType)) {
                    maySavePassphrase = true;
                } else {
                    maySavePassphrase = myConnectionOptions.isStorePlainTextPassphrases(realm, auth);
                }

                if (maySavePassphrase) {
                    values.put("passtype", cipherType);
                }
            }
        }
        if (maySavePassphrase && passphrase != null) {
            values.put("passphrase", cipher.encrypt(passphrase));
        }
        if (auth instanceof SVNSSLAuthentication) {
            SVNSSLAuthentication sslAuth = (SVNSSLAuthentication) auth;
            if (SVNSSLAuthentication.SSL.equals(sslAuth.getSSLKind())) {
                if (sslAuth.getCertificateFile() != null) {
                    String path = sslAuth.getCertificatePath();
                    values.put("key", path);
                }
            } else if (SVNSSLAuthentication.MSCAPI.equals(sslAuth.getSSLKind())) {
                values.put("ssl-kind", sslAuth.getSSLKind());
                values.put("alias", sslAuth.getAlias());
            }
        }
    }

    public byte[] loadFingerprints(String realm) {
        File dir = new File(myDirectory, "svn.ssh.server");
        if (!dir.isDirectory()) {
            return null;
        }
        File file = new File(dir, SVNFileUtil.computeChecksum(realm));
        if (!file.isFile()) {
            return null;
        }
        SVNWCProperties props = new SVNWCProperties(file, "");
        SVNProperties values;
        try {
            values = props.asMap();
            String storedRealm = values.getStringValue("svn:realmstring");
            if (!realm.equals(storedRealm)) {
                return null;
            }
            return values.getBinaryValue("hostkey");
        } catch (SVNException e) {
            return null;
        }
    }

    public void saveFingerprints(String realm, byte[] fingerprints) {
        File dir = new File(myDirectory, "svn.ssh.server");
        if (!dir.isDirectory()) {
            dir.mkdirs();
        }
        File file = new File(dir, SVNFileUtil.computeChecksum(realm));

        SVNProperties values = new SVNProperties();
        values.put("svn:realmstring", realm);
        values.put("hostkey", fingerprints);
        try {
            SVNWCProperties.setProperties(values, file, null, SVNWCProperties.SVN_HASH_TERMINATOR);
        } catch (SVNException e) {
        }
    }
}
