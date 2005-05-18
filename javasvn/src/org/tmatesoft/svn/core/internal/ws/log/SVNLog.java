/*
 * Created on 17.05.2005
 */
package org.tmatesoft.svn.core.internal.ws.log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.tmatesoft.svn.core.io.SVNException;

public class SVNLog {

    public static final String DELETE_ENTRY = "delete-entry";
    public static final String MODIFY_ENTRY = "modify-entry";

    public static final String NAME_ATTR = "name";

    private File myFile;
    private File myTmpFile;
    private Collection myCache;
    private SVNDirectory myDirectory;

    public SVNLog(SVNDirectory directory, int id) {
        String name = id == 0 ? "log" : "log." + id;
        myFile = new File(directory.getRoot(), ".svn/" + name);
        myTmpFile = new File(directory.getRoot(), ".svn/tmp/" + name);
        myDirectory = directory;
    }
    
    public void addCommand(String name, Map attributes, boolean save) throws SVNException {
        if (myCache == null) {
            myCache = new ArrayList();
        }
        attributes = new HashMap(attributes);
        attributes.put("", name);
        myCache.add(attributes);
        if (save) {
            save();
        }        
    }
    
    public void save() throws SVNException {
        OutputStream os = null; 
        try {
            os = new FileOutputStream(myTmpFile);
            for (Iterator commands = myCache.iterator(); commands.hasNext();) {
                Map command = (Map) commands.next();
                String name = (String) command.remove("");
                os.write('<');
                os.write(name.getBytes("UTF-8"));
                os.write('\n');
                for (Iterator attrs = command.keySet().iterator(); attrs.hasNext();) {
                    String attr = (String) attrs.next();
                    String value = (String) command.get(attr);
                    value = SVNTranslator.xmlEncode(value);
                    os.write(' ');
                    os.write(' ');
                    os.write(' ');
                    os.write(attr.getBytes("UTF-8"));
                    os.write('=');
                    os.write('\"');
                    os.write(value.getBytes("UTF-8"));
                    os.write('\"');
                    if (!attrs.hasNext()) {
                        os.write('/');
                        os.write('>');
                    }
                    os.write('\n');
                }
            }            
        } catch (IOException e) {
            SVNErrorManager.error(0, e);
        } finally {
            if (os != null) {
                try {
                    os.close();
                } catch (IOException e) {
                }
            }
            myCache = null;
        }
        try {
            SVNFileUtil.rename(myTmpFile, myFile);
            SVNFileUtil.setReadonly(myFile, true);
        } catch (IOException e) {
            SVNErrorManager.error(0, e);
        }
    }
    
    public void run(SVNLogRunner runner) throws SVNException {
        if (!myFile.exists()) {
            return;
        }
        BufferedReader reader = null;
        Collection commands = new ArrayList();
        try {
            reader = new BufferedReader(new FileReader(myFile));
            String line;
            Map attrs = new HashMap();
            String name = null;
            while((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.startsWith("<")) {
                    name = line.substring(1);
                    continue;
                } else {
                    int index = line.indexOf('=');
                    if (index > 0) {
                        String attrName = line.substring(0, index).trim();
                        String value = line.substring(index + 1).trim();
                        if (value.endsWith("/>")) {
                            value = value.substring(0, value.length() - "/>".length());                            
                        }
                        if (value.startsWith("\"")) {
                            value = value.substring(1);
                        }
                        if (value.endsWith("\"")) {
                            value = value.substring(0, value.length() - 1);                            
                        }
                        value = SVNTranslator.xmlDecode(value);
                        attrs.put(attrName, value);
                    }                    
                }
                if (line.endsWith("/>") && name != null) {
                    // run command
                    attrs.put("", name);
                    commands.add(attrs);
                    attrs = new HashMap();
                    name = null;
                }
            }
        } catch (IOException e) {
            SVNErrorManager.error(0, e);
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                }
            }
        }
        for (Iterator cmds = commands.iterator(); cmds.hasNext();) {
            Map command = (Map) cmds.next();
            String name = (String) command.remove("");
            if (runner != null) {
                try {
                    runner.runCommand(myDirectory, name, command);
                    cmds.remove();
                } catch (Throwable th) {
                    command.put("", name);
                    myCache = commands;
                    save();
                    SVNErrorManager.error(0, th);
                }
            }
        }
        delete();
    }
    
    public void delete() {
        myFile.delete();
        myTmpFile.delete();
    }

    public boolean exists() {
        return myFile.exists();
    }

}
