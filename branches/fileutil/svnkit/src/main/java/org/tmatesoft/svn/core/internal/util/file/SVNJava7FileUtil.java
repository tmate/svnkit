package org.tmatesoft.svn.core.internal.util.file;

import java.io.File;
import java.lang.reflect.Method;

public class SVNJava7FileUtil extends SVNJava6FileUtil {

    private Class filesClass;
    private Class pathClass;

    private Method toPathMethod;
    private Method toFileMethod;
    private Method isSymbolicLinkMethod;
    private Method createSymbolicLinkMethod;
    private Method readSymbolicLinkMethod;

    public SVNJava7FileUtil() {
        try {
            filesClass = Class.forName("java.nio.file.Files");
            pathClass = Class.forName("java.nio.file.Path");
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
    }

    @Override
    public boolean isSymlink(File file) {
        Object value = invokeStaticMethodWithReflection(isSymbolicLinkMethod, convertFileToPath(file));
        if (value == null || !(value instanceof Boolean)) {
            throw new UnsupportedOperationException();
        }
        return (Boolean) value;
    }

    @Override
    public File readSymlink(File link) {
        return convertPathToFile(invokeStaticMethodWithReflection(readSymbolicLinkMethod, convertFileToPath(link)));
    }

    @Override
    public void createSymlink(File link, File linkName) {
        invokeStaticMethodWithReflection(createSymbolicLinkMethod, convertFileToPath(link), convertFileToPath(linkName));
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
}
