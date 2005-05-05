/*
 * Created on 05.05.2005
 */
package org.tigris.subversion.javahl;

public class NotifyWrapper implements Notify2 {
    
    private Notify myNotify;

    public NotifyWrapper(Notify notify) {
        myNotify = notify;
    }

    public void onNotify(NotifyInformation info) {
        if (myNotify != null && info != null) {
            myNotify.onNotify(info.getPath(), info.getAction(), info.getKind(),
                    info.getMimeType(), info.getContentState(), info.getPropState(), 
                    info.getRevision());
        }
    }

}
