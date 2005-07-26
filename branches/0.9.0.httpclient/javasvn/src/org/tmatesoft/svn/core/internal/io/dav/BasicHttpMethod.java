/*
 * Created on 08.07.2005
 */
package org.tmatesoft.svn.core.internal.io.dav;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.apache.commons.httpclient.HttpConnection;
import org.apache.commons.httpclient.HttpException;
import org.apache.commons.httpclient.HttpMethodBase;
import org.apache.commons.httpclient.HttpState;
import org.tmatesoft.svn.core.SVNException;

public class BasicHttpMethod extends HttpMethodBase{
    
    private final String myName;
    private final byte[] myRequestBodyArray;
    
    public BasicHttpMethod(String name, String uri, InputStream requestBody) throws SVNException{
        super(uri);
        myName = name;
        try{
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            while (true) {
                int count = requestBody.read(buffer);
                if (count <= 0) {
                    break;
                }
                os.write(buffer, 0, count);
            }
            myRequestBodyArray = os.toByteArray();
        }catch(IOException e){
            throw new SVNException(e);
        }
    }
    
    public String getName() {
        return myName;
    }

    protected boolean writeRequestBody(HttpState state, HttpConnection conn) throws IOException, HttpException {
        OutputStream os = conn.getRequestOutputStream();
        os.write(myRequestBodyArray);
        return true;
    }
}