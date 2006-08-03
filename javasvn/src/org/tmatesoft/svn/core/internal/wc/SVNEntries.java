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
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.tmatesoft.svn.core.SVNProperty;
/**
 * @version 1.0
 * @author TMate Software Ltd.
 */
public class SVNEntries {

    private Map myData;
    private Set myEntries;

    void putEntry(String entryName, Map entry) {
        if (entryName == null) {
            return;
        }
        if (myData == null) {
            myData = new TreeMap();
            myEntries = new TreeSet();
        }
        myData.put(entryName, entry);
        myEntries.add(new SVNEntry(this, entryName));
    }

    public void close() {
        myData = null;
        myEntries = null;
    }

    public String getPropertyValue(String name, String propertyName) {
        if (myData == null) {
            return null;
        }
        Map entry = (Map) myData.get(name);
        if (entry != null) {
            return (String) entry.get(propertyName);
        }
        return null;
    }

    public boolean setPropertyValue(String name, String propertyName,
            String propertyValue) {
        if (myData == null) {
            return false;
        }
        Map entry = (Map) myData.get(name);
        if (entry != null) {
            if (SVNProperty.SCHEDULE.equals(propertyName)) {
                if (SVNProperty.SCHEDULE_DELETE.equals(propertyValue)) {
                    if (SVNProperty.SCHEDULE_ADD.equals(entry
                            .get(SVNProperty.SCHEDULE))) {
                        if (entry.get(SVNProperty.DELETED) == null) {
                            deleteEntry(name);
                        } else {
                            entry.remove(SVNProperty.SCHEDULE);
                        }
                        return true;
                    }
                }
            }
            if (propertyValue == null) {
                return entry.remove(propertyName) != null;
            }
            Object oldValue = entry.put(propertyName, propertyValue);
            return !propertyValue.equals(oldValue);            
        }
        return false;
    }

    public Iterator entries(boolean hidden) {
        if (myEntries == null) {
            return Collections.EMPTY_LIST.iterator();
        }
        Collection copy = new LinkedList(myEntries);
        if (!hidden) {
            for (Iterator iterator = copy.iterator(); iterator.hasNext();) {
                SVNEntry entry = (SVNEntry) iterator.next();
                if (entry.isHidden()) {
                    iterator.remove();
                }
            }
        }
        return copy.iterator();
    }

    public SVNEntry getEntry(String name, boolean hidden) {
        if (myData != null && myData.containsKey(name)) {
            SVNEntry entry = new SVNEntry(this, name);
            if (!hidden && entry.isHidden()) {
                return null;
            }
            return entry;
        }
        return null;
    }

    public SVNEntry addEntry(String name) {
        if (myData == null) {
            myData = new TreeMap();
            myEntries = new TreeSet();
        }
        if (myData != null) {
            Map map = myData.containsKey(name) ? (Map) myData.get(name)
                    : new HashMap();
            myData.put(name, map);
            SVNEntry entry = new SVNEntry(this, name);
            myEntries.add(entry);
            setPropertyValue(name, SVNProperty.NAME, name);
            return entry;
        }
        return null;
    }

    public void deleteEntry(String name) {
        if (myData != null) {
            myData.remove(name);
            myEntries.remove(new SVNEntry(this, name));
        }
    }

    Map getEntryMap(String name) {
        if (myData != null && name != null) {
            return (Map) myData.get(name);
        }
        return null;
    }
}
