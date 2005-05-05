/*
 * Created on 05.05.2005
 */
package org.tigris.subversion.javahl;

public class SVNClientUtil {
    
    public static NotifyInformation createNotifyInformation(String path, int action, int kind, String mimeType, Lock lock, String errMsg, int contentState, int propState, int lockState, long revision) {
        return new NotifyInformation(path, action, kind, mimeType, lock, errMsg, contentState, propState, lockState,
                revision);
    }

    public static NotifyInformation createNotifyInformation(String path, int action, long revision) {
        return createNotifyInformation(path, action, NodeKind.unknown, null, null, null, 
                NotifyStatus.inapplicable, NotifyStatus.inapplicable, LockStatus.inapplicable,
                revision);
    }

}
