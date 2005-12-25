/*
 * ====================================================================
 * Copyright (c) 2004 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://tmate.org/svn/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.io.diff;

import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayInputStream;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;


/**
 * @version 1.0
 * @author  TMate Software Ltd.
 */
public class SVNRAStreamData implements ISVNRAData {
    private ByteArrayInputStream myStream;
    private byte[] myBuffer;
    
    public SVNRAStreamData(byte[] buffer){
        myBuffer = buffer;
    }
    /**
     * @return
     * @throws SVNException
     */
    public InputStream readAll() throws SVNException {
        return new ByteArrayInputStream(myBuffer);
    }
    
    private ByteArrayInputStream getStream(){
        if(myStream == null){
            myStream = new ByteArrayInputStream(myBuffer);
        }
        return myStream;
    }
    
    /**
     * @param offset
     * @param length
     * @return
     * @throws SVNException
     */
    public InputStream read(long offset, long length) throws SVNException {
        byte[] resultingArray = new byte[(int) length];
        ByteArrayInputStream stream = getStream();
        int read = 0;
        try {
            stream.reset();
            stream.skip(offset);
            read = stream.read(resultingArray);
        } catch (IOException e) {
            SVNErrorManager.error(e.getMessage());
        } 
        for (int i = read; i < length && read >= 0; i++) {
            resultingArray[i] = resultingArray[i - read];
        }
        return new LocalInputStream(resultingArray);
    }

    /**
     * @param source
     * @param length
     * @throws SVNException
     */
    public void append(InputStream source, long length) throws SVNException {
        byte[] bytes = null;
        try {
            if (source instanceof LocalInputStream) {
                bytes = ((LocalInputStream) source).getBuffer();
            } else {
                bytes = new byte[(int) length];
                source.read(bytes, 0, (int) length);
            }
        } catch (IOException e) {
            SVNErrorManager.error(e.getMessage());
        } 
        byte[] newBuffer = new byte[bytes.length + myBuffer.length];
        System.arraycopy(myBuffer, 0, newBuffer, 0, myBuffer.length);
        System.arraycopy(bytes, 0, newBuffer, myBuffer.length, bytes.length);
        myBuffer = newBuffer;
        myStream = null;
    }

    /**
     * @return
     */
    public long length() {
        return myBuffer.length;
    }

    /**
     * @return
     */
    public long lastModified() {
        return 0;
    }

    /**
     * @throws IOException
     */
    public void close() throws IOException {
    }

    private static class LocalInputStream extends ByteArrayInputStream {

        public LocalInputStream(byte[] buffer) {
            super(buffer);
        }
        
        public byte[] getBuffer() {
            return buf;
        }
        
    }

}
