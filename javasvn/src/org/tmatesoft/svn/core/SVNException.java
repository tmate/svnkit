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

package org.tmatesoft.svn.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


/**
 * A main exception class that is used in the JavaSVN library. All other
 * JavaSVN exception classes extend this one. 
 *  
 * @version	1.0
 * @author 	TMate Software Ltd.
 */
public class SVNException extends Exception {

    private static final SVNErrorMessage UNKNOWN_ERROR = SVNErrorMessage.create(SVNErrorCode.UNKNOWN, "Unknown error");
    
    public static void throwException(SVNErrorMessage message) throws SVNException {
        throwException(message, null);
    }
    
    public static void throwException(SVNErrorMessage message, Throwable cause) throws SVNException {
        if (message == null) {
            message = UNKNOWN_ERROR;
        }
        if (cause instanceof SVNException) {
            List messagesList = new ArrayList();
            messagesList.add(message);
            messagesList.addAll(((SVNException) cause).myMessages);
            throw new SVNException(messagesList, cause);
        } 
        throwException(Collections.singletonList(message), cause);
    }

    public static void throwException(List messages, Throwable cause) throws SVNException {
        if (messages == null || messages.isEmpty()) {
            throwException(UNKNOWN_ERROR, cause);
        }
        SVNErrorMessage message = (SVNErrorMessage) messages.get(0);
        if (message.getErrorCode() == SVNErrorCode.CANCELLED) {
            throw new SVNCancelException();
        } else if (message.getErrorCode().isAuthentication()) {
            throw new SVNAuthenticationException(message);
        }
        throw new SVNException(messages, cause);
    }
    
    public static void throwCancelException() throws SVNCancelException {
        throw new SVNCancelException();
    }

    private List myMessages;
    
    protected SVNException(List messages, Throwable cause) {
        super(cause);
        myMessages = messages == null || messages.isEmpty() ? 
                Collections.singletonList(SVNErrorMessage.UNKNOWN_ERROR_MESSAGE) : messages ;
    }

    protected SVNException(SVNErrorMessage message, Throwable cause) {
        super(cause);
        myMessages = Collections.singletonList(message == null ? SVNErrorMessage.UNKNOWN_ERROR_MESSAGE : message);
    }
    
    public String getLocalizedMessage() {
        return getMessage();
    }
    
    public String getMessage() {
        return getErrorMessage().toString();
    }

    public SVNErrorMessage getErrorMessage() {
        return (SVNErrorMessage) myMessages.get(0);
    }
    
    public String toString() {
        StringBuffer result = new StringBuffer();
        for (int i = 0; i < myMessages.size(); i++) {
            SVNErrorMessage message = (SVNErrorMessage) myMessages.get(i);
            result.append(message.toString());
            if (i + 1 < myMessages.size()) {
                result.append("\n");
            }
        }
        return result.toString();
    }
}
