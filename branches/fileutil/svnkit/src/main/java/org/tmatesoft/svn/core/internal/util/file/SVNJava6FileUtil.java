package org.tmatesoft.svn.core.internal.util.file;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class SVNJava6FileUtil extends SVNJava5FileUtil {

    private Method canExecuteMethod;
    private Method setExecutableMethod;
    private Method canWriteMethod;
    private Method setWritableMethod;
    private Method setReadOnlyMethod;

    public SVNJava6FileUtil() {
        canExecuteMethod = getMethodWithReflection(File.class, "canExecute");
        setExecutableMethod = getMethodWithReflection(File.class, "setExecutable", Boolean.TYPE);
        canWriteMethod = getMethodWithReflection(File.class, "canWrite");
        setWritableMethod = getMethodWithReflection(File.class, "setWritable", Boolean.TYPE);
        setReadOnlyMethod = getMethodWithReflection(File.class, "setReadOnly");
    }

    protected static Method getMethodWithReflection(Class clazz, String methodName, Class... argTypes) {
        try {
            //make sure all return types are loaded
            if (argTypes != null) {
                for (Class argType : argTypes) {
                    if (argType == null) {
                        return null;
                    }
                }
            }
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

    protected static boolean convertToBoolean(Object value) {
        if (value == null || !(value instanceof Boolean)) {
            throw new UnsupportedOperationException();
        }
        return (Boolean) value;
    }

    @Override
    public boolean isExecutable(File file) {
        return convertToBoolean(invokeMethodWithReflection(canExecuteMethod, file));
    }

    @Override
    public boolean setExecutable(File file, boolean executable) {
        return convertToBoolean(invokeMethodWithReflection(setExecutableMethod, file, executable));
    }

    @Override
    public boolean isWritable(File file) {
        return convertToBoolean(invokeMethodWithReflection(canWriteMethod, file));
    }

    @Override
    public boolean setWritable(File file) {
        return convertToBoolean(invokeMethodWithReflection(setWritableMethod, file, true /*writable*/));
    }

    @Override
    public boolean setReadOnly(File file) {
        return convertToBoolean(invokeMethodWithReflection(setReadOnlyMethod, file));
    }
}
