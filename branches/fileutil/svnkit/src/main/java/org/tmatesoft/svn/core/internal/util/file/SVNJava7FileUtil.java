package org.tmatesoft.svn.core.internal.util.file;

import java.io.File;
import java.lang.reflect.Method;
import java.util.Map;

public class SVNJava7FileUtil extends SVNJava6FileUtil {

    private Class filesClass;
    private Class pathClass;
    private Class linkOptionClass;
    private Class linkOptionsArrayClass;

    private Enum noFollowSymlinkEnum;

    private Method toPathMethod;
    private Method toFileMethod;
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
        } catch (ClassNotFoundException e) {
        }

        noFollowSymlinkEnum = Enum.valueOf(linkOptionClass, "NOFOLLOW_LINKS");

        if (filesClass == null || pathClass == null) {
            return;
        }

        toPathMethod = getMethodWithReflection(File.class, "toPath");
        toFileMethod = getMethodWithReflection(pathClass, "toFile");
        isSymbolicLinkMethod = getMethodWithReflection(filesClass, "isSymbolicLink", pathClass);
        createSymbolicLinkMethod = getMethodWithReflection(filesClass, "createSymbolicLink", pathClass, pathClass);
        readSymbolicLinkMethod = getMethodWithReflection(filesClass, "readSymbolicLink", pathClass);
        setAttributeMethod = getMethodWithReflection(filesClass, "setAttribute", pathClass, String.class, Object.class, linkOptionsArrayClass);
        readAttributesMethod = getMethodWithReflection(filesClass, "setAttribute", pathClass, String.class, linkOptionsArrayClass);
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
}
