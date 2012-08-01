package org.tmatesoft.svn.core.internal.util.file;

import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;

import java.io.File;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.security.Principal;
import java.util.Map;
import java.util.Set;

public class SVNJava7FileUtil extends SVNJava6FileUtil {

    private Class filesClass;
    private Class pathClass;
    private Class linkOptionClass;
    private Class linkOptionsArrayClass;
    private Class fileTimeClass;
    private Class posixFilePermissionClass;

    private Enum noFollowSymlinkEnum;
    private Object linkOptionFollowSymlinksArray;
    private Object linkOptionNoFollowSymlinksArray;

    //posix permissions constants
    private Enum groupExecuteEnum;
    private Enum groupReadEnum;
    private Enum groupWriteEnum;
    private Enum othersExecuteEnum;
    private Enum othersReadEnum;
    private Enum othersWriteEnum;
    private Enum ownerExecuteEnum;
    private Enum ownerReadEnum;
    private Enum ownerWriteEnum;

    private Method toPathMethod;
    private Method toFileMethod;
    private Method toMillisMethod;
    private Method fromMillisMethod;

    private Method isSymbolicLinkMethod;
    private Method createSymbolicLinkMethod;
    private Method readSymbolicLinkMethod;
    private Method setAttributeMethod;
    private Method readAttributesMethod;

    public SVNJava7FileUtil() {
        try {
            filesClass = Class.forName("java.nio.file.Files");
            pathClass = Class.forName("java.nio.file.Path");
            linkOptionClass = Class.forName("java.nio.file.LinkOption");
            linkOptionsArrayClass = Class.forName("[Ljava.nio.file.LinkOption;");
            fileTimeClass = Class.forName("java.nio.file.attribute.FileTime");
            posixFilePermissionClass = Class.forName("java.nio.file.attribute.PosixFilePermission");
        } catch (ClassNotFoundException e) {
        }

        noFollowSymlinkEnum = Enum.valueOf(linkOptionClass, "NOFOLLOW_LINKS");
        linkOptionFollowSymlinksArray = Array.newInstance(linkOptionClass, 0);
        linkOptionNoFollowSymlinksArray = Array.newInstance(linkOptionClass, 1);
        Array.set(linkOptionNoFollowSymlinksArray, 0, noFollowSymlinkEnum);

        groupExecuteEnum = Enum.valueOf(posixFilePermissionClass, "GROUP_EXECUTE");
        groupReadEnum = Enum.valueOf(posixFilePermissionClass, "GROUP_READ");
        groupWriteEnum = Enum.valueOf(posixFilePermissionClass, "GROUP_WRITE");
        othersExecuteEnum = Enum.valueOf(posixFilePermissionClass, "OTHERS_EXECUTE");
        othersReadEnum = Enum.valueOf(posixFilePermissionClass, "OTHERS_READ");
        othersWriteEnum = Enum.valueOf(posixFilePermissionClass, "OTHERS_WRITE");
        ownerExecuteEnum = Enum.valueOf(posixFilePermissionClass, "OWNER_EXECUTE");
        ownerReadEnum = Enum.valueOf(posixFilePermissionClass, "OWNER_READ");
        ownerWriteEnum = Enum.valueOf(posixFilePermissionClass, "OWNER_WRITE");

        if (filesClass == null || pathClass == null) {
            return;
        }

        toPathMethod = getMethodWithReflection(File.class, "toPath");
        toFileMethod = getMethodWithReflection(pathClass, "toFile");
        toMillisMethod = getMethodWithReflection(fileTimeClass, "toMillis");
        fromMillisMethod = getMethodWithReflection(fileTimeClass, "fromMillis", Long.TYPE);

        isSymbolicLinkMethod = getMethodWithReflection(filesClass, "isSymbolicLink", pathClass);
        createSymbolicLinkMethod = getMethodWithReflection(filesClass, "createSymbolicLink", pathClass, pathClass);
        readSymbolicLinkMethod = getMethodWithReflection(filesClass, "readSymbolicLink", pathClass);
        setAttributeMethod = getMethodWithReflection(filesClass, "setAttribute", pathClass, String.class, Object.class, linkOptionsArrayClass);
        readAttributesMethod = getMethodWithReflection(filesClass, "readAttributes", pathClass, String.class, linkOptionsArrayClass);
    }

    protected Object convertFileToPath(File file) {
        if (file == null) {
            return null;
        }
        return invokeMethodWithReflection(toPathMethod, file);
    }

    protected File convertPathToFile(Object path) {
        if (path == null) {
            return null;
        }
        Object value = invokeMethodWithReflection(toFileMethod, path);
        if (value == null || !(value instanceof File)) {
            return null;
        }
        return (File) value;
    }

    protected Long convertFileTimeToLong(Object fileTime) {
        if (fileTime == null) {
            return null;
        }
        Object value = invokeMethodWithReflection(toMillisMethod, fileTime);
        if (value == null || !(value instanceof Long)) {
            return null;
        }
        return (Long) value;
    }

    protected Long convertLongToFileTime(Long milliseconds) {
        if (milliseconds == null) {
            return null;
        }
        Object value = invokeStaticMethodWithReflection(fromMillisMethod, milliseconds);
        return (Long) value;
    }

    private String convertPrincipalToString(Principal principal) {
        if (principal == null) {
            return null;
        }
        return principal.getName();
    }

    private SVNFilePermissions convertPosixFilePermissionSetToFilePermissions(Set permissionsSet) {
        if (permissionsSet == null) {
            return null;
        }
        final SVNFilePermissions permissions = new SVNFilePermissions();
        permissions.setOwnerCanRead(permissionsSet.contains(ownerReadEnum));
        permissions.setOwnerCanWrite(permissionsSet.contains(ownerWriteEnum));
        permissions.setOwnerCanExecute(permissionsSet.contains(ownerExecuteEnum));
        permissions.setGroupCanRead(permissionsSet.contains(groupReadEnum));
        permissions.setGroupCanWrite(permissionsSet.contains(groupWriteEnum));
        permissions.setGroupCanExecute(permissionsSet.contains(groupExecuteEnum));
        permissions.setOthersCanRead(permissionsSet.contains(othersReadEnum));
        permissions.setOthersCanWrite(permissionsSet.contains(othersWriteEnum));
        permissions.setOthersCanExecute(permissionsSet.contains(othersExecuteEnum));
        return permissions;
    }

    @Override
    public boolean isSymlink(File file) {
        return convertToBoolean(invokeStaticMethodWithReflection(isSymbolicLinkMethod, convertFileToPath(file)));
    }

    @Override
    public File readSymlink(File link) {
        return convertPathToFile(invokeStaticMethodWithReflection(readSymbolicLinkMethod, convertFileToPath(link)));
    }

    @Override
    public boolean createSymlink(File link, File linkName) {
        return convertToBoolean(invokeStaticMethodWithReflection(createSymbolicLinkMethod, convertFileToPath(link), convertFileToPath(linkName)));
    }

    @Override
    public boolean setHidden(File file) {
        return convertToBoolean(invokeStaticMethodWithReflection(setAttributeMethod, convertFileToPath(file), "dos:hidden", true/*hidden*/));
    }

    @Override
    public Long getFileLength(File file) {
        Object value = invokeStaticMethodWithReflection(readAttributesMethod, convertFileToPath(file), "size", noFollowSymlinkEnum);
        if (value == null || !(value instanceof Map)) {
            return null;
        }
        Map attributes = (Map) value;
        Object size = attributes.get("size");
        if (size == null || !(size instanceof Long)) {
            return null;
        }
        return (Long) size;
    }

    @Override
    public Long getFileLastModified(File file) {
        Object value = invokeStaticMethodWithReflection(readAttributesMethod, convertFileToPath(file), "lastModifiedTime", noFollowSymlinkEnum);
        if (value == null || !(value instanceof Map)) {
            return null;
        }
        Map attributes = (Map) value;
        Object lastModifiedTime = attributes.get("lastModifiedTime");
        if (lastModifiedTime == null || !(lastModifiedTime instanceof Long)) {
            return null;
        }
        return (Long) lastModifiedTime;
    }

    @Override
    public SVNFileAttributes readFileAttributes(File file, boolean followSymlinks) {
        String requestedAttributes;
        if (SVNFileUtil.isWindows) {
            requestedAttributes = "dos:*";
        } else {
            requestedAttributes = "posix:*";
        }

        Object value = invokeStaticMethodWithReflection(readAttributesMethod, convertFileToPath(file), requestedAttributes,
                followSymlinks ? linkOptionFollowSymlinksArray : linkOptionNoFollowSymlinksArray);

        if (value == null || !(value instanceof Map)) {
            return null;
        }

        Map attributesMap = (Map) value;
        final SVNFileAttributes attributes = new SVNFileAttributes();

        Long lastModifiedTime = convertFileTimeToLong(attributesMap.get("lastModifiedTime"));
        Long lastAccessTime = convertFileTimeToLong(attributesMap.get("lastAccessTime"));
        Long creationTime = convertFileTimeToLong(attributesMap.get("creationTime"));
        Long size = (Long) attributesMap.get("size");
        Boolean isRegularFile = (Boolean) attributesMap.get("isRegularFile");
        Boolean isDirectory = (Boolean) attributesMap.get("isDirectory");
        Boolean isSymbolicLink = (Boolean) attributesMap.get("isSymbolicLink");
        Boolean isOther = (Boolean) attributesMap.get("isOther");

        attributes.setSize(size);
        attributes.setLastModifiedTime(lastModifiedTime);
        attributes.setLastAccessTime(lastAccessTime);
        attributes.setCreationTime(creationTime);
        attributes.setIsRegularFile(isRegularFile);
        attributes.setIsDirectory(isDirectory);
        attributes.setSymbolicLink(isSymbolicLink);
        attributes.setIsOther(isOther);

        if (!SVNFileUtil.isWindows) {
            String owner = convertPrincipalToString((Principal) attributesMap.get("owner"));
            String group = convertPrincipalToString((Principal) attributesMap.get("group"));
            SVNFilePermissions permissions = convertPosixFilePermissionSetToFilePermissions((Set)attributesMap.get("permissions"));

            attributes.setPosixOwner(owner);
            attributes.setPosixGroup(group);
            attributes.setPosixPermissions(permissions);
        } else {
            Boolean readonly = (Boolean) attributesMap.get("readonly");
            Boolean hidden = (Boolean) attributesMap.get("hidden");
            Boolean system = (Boolean) attributesMap.get("system");
            Boolean archive = (Boolean) attributesMap.get("archive");

            attributes.setDosReadOnly(readonly);
            attributes.setDosHidden(hidden);
            attributes.setDosSystem(system);
            attributes.setDosArchive(archive);
        }

        return attributes;
    }
}
