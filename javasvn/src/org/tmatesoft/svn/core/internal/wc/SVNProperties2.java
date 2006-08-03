/*
 * ====================================================================
 * Copyright (c) 2004-2006 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://tmate.org/svn/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.wc;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeSet;

/**
 * @version 1.1
 * @author  TMate Software Ltd.
 */
public class SVNProperties2 {
    private Map myProperties;
    
    public SVNProperties2(Map properties) {
        myProperties = properties;
    }
    
    public String getPropertyValue(String name) {
        if (!isEmpty()) {
            return (String)myProperties.get(name); 
        }
        return null;
    }

    public boolean isEmpty() {
        return myProperties == null || myProperties.isEmpty();
    }
    
    public Collection properties(Collection target) {
        target = target == null ? new TreeSet() : target;
        if (isEmpty()) {
            return target;
        }
        for (Iterator names = myProperties.keySet().iterator(); names.hasNext();) {
            target.add(names.next());
        }
        return target;
    }

    public void setPropertyValue(String name, String value) {
        if (myProperties == null) {
            myProperties = new HashMap();
        }
        if (value != null) {
            myProperties.put(name, value);
        } else {
            myProperties.remove(name);
        }
    }

    public SVNProperties2 compareTo(SVNProperties2 properties) {
        Map result = new HashMap();
        if (!isEmpty()) {
            result.putAll(myProperties);
        } else {
            result.putAll(properties.myProperties);
            return new SVNProperties2(result);
        }
        
        Collection props1 = properties(null);
        Collection props2 = properties.properties(null);
        
        // missed in props2.
        Collection tmp = new TreeSet(props1);
        tmp.removeAll(props2);
        for (Iterator props = tmp.iterator(); props.hasNext();) {
            String missing = (String) props.next();
            result.remove(missing);
        }

        // added in props2.
        tmp = new TreeSet(props2);
        tmp.removeAll(props1);

        for (Iterator props = tmp.iterator(); props.hasNext();) {
            String added = (String) props.next();
            result.put(added, properties.getPropertyValue(added));
        }

        // changed in props2
        props2.retainAll(props1);
        for (Iterator props = props2.iterator(); props.hasNext();) {
            String changed = (String) props.next();
            String value1 = getPropertyValue(changed);
            String value2 = properties.getPropertyValue(changed);
            if (!value1.equals(value2)) {
                result.put(changed, value2);
            }
        }
        return new SVNProperties2(result);
    }
    
    public void copyTo(SVNProperties2 destination) {
        if (!isEmpty()) {
            destination.myProperties.putAll(myProperties);
        }
    }
    
    public void removeAll(){
        myProperties = null;
    }
}
