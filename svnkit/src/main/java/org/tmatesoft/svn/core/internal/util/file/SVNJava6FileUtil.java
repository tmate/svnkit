package org.tmatesoft.svn.core.internal.util.file;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class SVNJava6FileUtil extends SVNJava5FileUtil {

    private Method setWritableMethod;
    private Method canExecuteMethod;
    private Method setExecutableMethod;

    public SVNJava6FileUtil() {
        setWritableMethod = getMethodWithReflection(File.class, "setWritable", Boolean.TYPE);
        canExecuteMethod = getMethodWithReflection(File.class, "canExecute", File.class);
        setExecutableMethod = getMethodWithReflection(File.class, "setExecutable", File.class, Boolean.TYPE);
    }

    protected static Method getMethodWithReflection(Class clazz, String methodName, Class... argTypes) {
        try {
            return clazz.getMethod(methodName, argTypes);
        } catch (SecurityException e) {
        } catch (NoSuchMethodException e) {
        }
        return null;
    }

    protected static Object invokeStaticMethodWithReflection(Method method, Object... args) {
        return invokeMethodWithReflection(method, null, args);
    }

    protected static Object invokeMethodWithReflection(Method method, Object object, Object... args) {
        if (method == null) {
            throw new UnsupportedOperationException();
        }
        try {
            return method.invoke(object, args);

        } catch (IllegalAccessException e) {
            //TODO: handle exception or let it be unsupported?
        } catch (InvocationTargetException e) {
            //TODO: handle exception or let it be unsupported?
        }

        throw new UnsupportedOperationException();
    }

    public boolean isExecutable(File file) {
        Object value = invokeStaticMethodWithReflection(canExecuteMethod, file);
        if (value == null || !(value instanceof Boolean)) {
            throw new UnsupportedOperationException();
        }
        return (Boolean) value;
    }

    public void setExecutable(File file, boolean executable) {
        invokeStaticMethodWithReflection(setExecutableMethod, file, executable);
    }
}
