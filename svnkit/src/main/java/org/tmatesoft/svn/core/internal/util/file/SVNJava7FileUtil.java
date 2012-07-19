package org.tmatesoft.svn.core.internal.util.file;

import java.io.File;
import java.lang.reflect.Method;

public class SVNJava7FileUtil extends SVNJava6FileUtil {

    private Class filesClass;
    private Class pathClass;
    private Class linkOptionsArrayClass;

    private Method toPathMethod;
    private Method toFileMethod;
    private Method isSymbolicLinkMethod;
    private Method createSymbolicLinkMethod;
    private Method readSymbolicLinkMethod;
    private Method setAttributeMethod;

    public SVNJava7FileUtil() {
        try {
            filesClass = Class.forName("java.nio.file.Files");
            pathClass = Class.forName("java.nio.file.Path");
            linkOptionsArrayClass = Class.forName("[Ljava.nio.file.LinkOption;");
        } catch (ClassNotFoundException e) {
        }

        if (filesClass == null || pathClass == null) {
            return;
        }

        toPathMethod = getMethodWithReflection(File.class, "toPath");
        toFileMethod = getMethodWithReflection(pathClass, "toFile");
        isSymbolicLinkMethod = getMethodWithReflection(filesClass, "isSymbolicLink", pathClass);
        createSymbolicLinkMethod = getMethodWithReflection(filesClass, "createSymbolicLink", pathClass, pathClass);
        readSymbolicLinkMethod = getMethodWithReflection(filesClass, "readSymbolicLink", pathClass);
        setAttributeMethod = getMethodWithReflection(filesClass, "setAttribute", pathClass, String.class, Object.class, linkOptionsArrayClass);
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
}
