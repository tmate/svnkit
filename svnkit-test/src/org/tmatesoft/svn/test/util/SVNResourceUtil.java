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
package org.tmatesoft.svn.test.util;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.*;

import org.tmatesoft.svn.test.environments.AbstractSVNTestEnvironment;
import org.tmatesoft.svn.test.tests.AbstractSVNTest;

/**
 * @author TMate Software Ltd.
 * @version 1.3
 */
public class SVNResourceUtil {

    private static final String RESOURCE_BUNDLE = "org.tmatesoft.svn.test.util.test";

    public static ResourceBundle createBundle() {
        return ResourceBundle.getBundle(RESOURCE_BUNDLE);
    }

    public static boolean getBoolean(String name, ResourceBundle bundle) {
        String value = bundle.getString(name);
        return Boolean.TRUE.toString().equals(value);
    }

    public static Object createClassInstance(String className, Class[] parameterClasses, Object[] parameters) {
        try {
            Class clazz = SVNResourceUtil.class.getClassLoader().loadClass(className);
            Constructor constructor = clazz.getConstructor(parameterClasses);
            return constructor.newInstance(parameters);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        } catch (InstantiationException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static AbstractSVNTestEnvironment createEnvironment(ResourceBundle bundle) {
        String className = bundle.getString("environment.class");
        Object instance = createClassInstance(className, new Class[0], new Object[0]);
        return (AbstractSVNTestEnvironment) instance;
    }

    public static Map createTests(ResourceBundle bundle) {
        Map tests = new HashMap();
        String classNamesString = bundle.getString("test.class");
        for (StringTokenizer tokenizer = new StringTokenizer(classNamesString); tokenizer.hasMoreTokens();) {
            String className = tokenizer.nextToken().trim();
            Class[] classes = new Class[]{};
            Object[] parameters = new Object[]{};
            Object instance = createClassInstance(className, classes, parameters);
            tests.put(className, instance);
        }
        return tests;
    }
}
