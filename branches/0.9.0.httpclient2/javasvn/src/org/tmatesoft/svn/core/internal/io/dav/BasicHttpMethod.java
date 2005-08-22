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
    
    private static final byte[] CRLF_BYTES = { '\r', '\n' };

    private final String myName;
    private final InputStream myRequestBody;
    private final boolean myChunked;
    
    public BasicHttpMethod(String name, String uri, InputStream requestBody, boolean chunked) {
        super(uri);
        myName = name;
        myRequestBody = requestBody;
        if(myRequestBody != null && myRequestBody.markSupported()){
            myRequestBody.mark(0);
        }
        myChunked = chunked;
    }
    
    public String getName() {
        return myName;
    }

    protected boolean writeRequestBody(HttpState state, HttpConnection conn) throws IOException, HttpException {
        if(myRequestBody == null){
            return true;
        }
        OutputStream os = conn.getRequestOutputStream();
        if(myRequestBody.markSupported()){
            myRequestBody.reset();
        }
        /*byte[] buffer = new byte[1024];
        while (true) {
            int count = myRequestBody.read(buffer);
            if (count <= 0) {
                break;
            }
            os.write(buffer, 0, count);
        }*/
        byte[] buffer = new byte[1024*32];
        while (true) {
            int read = myRequestBody.read(buffer);
            if (myChunked) {
                if (read > 0) {
                    os.write(Integer.toHexString(read).getBytes());
                    os.write(BasicHttpMethod.CRLF_BYTES);
                    os.write(buffer, 0, read);
                    os.write(BasicHttpMethod.CRLF_BYTES);
                } else {
                    os.write('0');
                    os.write(BasicHttpMethod.CRLF_BYTES);
                    os.write(BasicHttpMethod.CRLF_BYTES);
                    break;
                }
            } else {
                if (read > 0) {
                    os.write(buffer, 0, read);
                } else {
                    break;
                }
            }
        }
        return true;
    }
}