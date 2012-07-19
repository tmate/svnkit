package org.tmatesoft.svn.core.internal.util.file;

import java.util.Map;
import java.util.Properties;

public class SVNJava5FileUtil extends SVNFileUtilAdapter {

    @Override
    public Properties getEnvironment() {
        Map<String,String> env = System.getenv();
        Properties environment = new Properties();
        environment.putAll(env);
        return environment;
    }

    @Override
    public String getEnvironmentVariable(String varibaleName) {
        return System.getenv(varibaleName);
    }
}
