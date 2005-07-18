/*
 * Created on 08.07.2005
 */
package org.tmatesoft.svn.core.internal.io.dav;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.apache.commons.httpclient.HttpConnection;
import org.apache.commons.httpclient.HttpException;
import org.apache.commons.httpclient.HttpMethodBase;
import org.apache.commons.httpclient.HttpState;

public class BasicHttpMethod extends HttpMethodBase{
    
    private final String myName;
    private final InputStream myRequestBody;
    
    public BasicHttpMethod(String name, String uri, InputStream requestBody){
        super(uri);
        myName = name;
        myRequestBody = requestBody;
    }
    
    public String getName() {
        return myName;
    }

    protected boolean writeRequestBody(HttpState state, HttpConnection conn) throws IOException, HttpException {
        OutputStream os = conn.getRequestOutputStream();
        byte[] buffer = new byte[1024];
        while (true) {
            int count = myRequestBody.read(buffer);
            if (count <= 0) {
                break;
            }
            os.write(buffer, 0, count);
        }
        return true;
    }
}