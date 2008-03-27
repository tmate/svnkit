/*
 * ====================================================================
 * Copyright (c) 2004-2008 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.util;

import java.util.AbstractCollection;
import java.util.AbstractSet;
import java.util.Arrays;
import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;


/**
 * @version 1.1.2
 * @author  TMate Software Ltd.
 */
public class SVNMap implements Map {

    private static final Object NULL_KEY = new Object();
    private static final int INITIAL_CAPACITY = 15;
    private static final double LOAD_FACTOR = 0.75;
    
    private Entry[] myTable;
    private int myEntryCount;
    private int myLimit;
    private int myModCount;
    
    private Set myKeySet;
    private Set myEntrySet;
    private Collection myValueCollection;
    
    public static Map createMap() {
        return new SVNMap(null);
    }

    public static Map createMap(Map map) {
        return new SVNMap(map);
    }
    
    private SVNMap(Map map) {
        myTable = new Entry[INITIAL_CAPACITY];
        myEntryCount = 0;
        myLimit = (int) (myTable.length * LOAD_FACTOR);
        putAll(map);
    }

    public void clear() {
        Arrays.fill(myTable, null);
        myEntryCount = 0;
        myModCount = 0;
    }

    public boolean isEmpty() {
        return myEntryCount == 0;
    }

    public boolean containsKey(Object key) {
        if (isEmpty()) {
            return false;
        }
        key = key == null ? NULL_KEY : key;

        int hash = hashCode(key);
        int index = indexForHash(hash);
        Entry entry = myTable[index];
        while (entry != null) {
            if (entry.hash == hash && eq(key, entry.key)) {
                return true;
            }
            entry = entry.next;
        }
        return false;
    }

    public boolean containsValue(Object value) {
        if (isEmpty()) {
            return false;
        }
        if (value == null) {
            return containsNullValue();
        }
        for (int i = 0; i < myTable.length; i++) {
            Entry entry = myTable[i];
            while (entry != null) {
                if (value.equals(entry.value)) {
                    return true;
                }
                entry = entry.next;
            }
        }
        return false;
    }
    
    private boolean containsNullValue() {
        for (int i = 0; i < myTable.length; i++) {
            Entry entry = myTable[i];
            while (entry != null) {
                if (entry.value == null) {
                    return true;
                }
                entry = entry.next;
            }
        }
        return false;
    }

    public Object get(Object key) {
        key = key == null ? NULL_KEY : key;

        int hash = hashCode(key); 
        int index = indexForHash(hash);
        Entry entry = myTable[index];
        
        while (entry != null) {
            if (hash == entry.hash && eq(key, entry.key)) {
                return entry.value;
            }
            entry = entry.next;
        }
        return null;
    }

    public int size() {
        return myEntryCount;
    }

    public Object put(Object key, Object value) {
        key = key == null ? NULL_KEY : key;
        
        int hash = hashCode(key);
        int index = indexForHash(hash);
        
        Entry entry = myTable[index];
        Entry previousEntry = null;
        
        while (entry != null) {
            if (entry.hash == hash && entry.key.equals(key)) {
                myModCount++;
                return entry.setValue(value);
            }
            previousEntry = entry;
            entry = entry.next;
        }
        Entry newEntry = new Entry(key, value, hash);
        
        if (previousEntry != null) {
            previousEntry.next = newEntry;
        } else {
            myTable[index] = newEntry;
        }
        myEntryCount++;
        myModCount++;
        if (myEntryCount >= myLimit) {
            resize(myTable.length * 2);
        }
        return null;
    }

    public Object remove(Object key) {
        if (isEmpty()) {
            return null;
        }
        int hash = hashCode(key);
        int index = indexForHash(hash);
        
        Entry entry = myTable[index];
        Entry previousEntry = null;
        
        while (entry != null) {
            if (entry.hash == hash && entry.key.equals(key)) {
                if (previousEntry != null) {
                    previousEntry.next = entry.next;
                }
                myEntryCount--;
                myModCount++;
                return entry.getValue();
            }
            previousEntry = entry;
            entry = entry.next;
        }
        return null;
    }

    public void putAll(Map t) {
        if (t == null || t.isEmpty()) {
            return;
        }
        if (myEntryCount + t.size() >= myLimit) {
            resize((myEntryCount + t.size())*2);
        }
        for (Iterator entries = t.entrySet().iterator(); entries.hasNext();) {
            Map.Entry entry = (Map.Entry) entries.next();
            put(entry.getKey(), entry.getValue());
        }
    }

    public Set keySet() {
        if (myKeySet == null) {
            myKeySet = new KeySet();
        }
        return myKeySet;
    }

    public Set entrySet() {
        if (myEntrySet == null) {
            myEntrySet = new EntrySet();
        }
        return myEntrySet;
    }

    public Collection values() {
        if (myValueCollection == null) {
            myValueCollection = new ValueCollection();
        }
        return myValueCollection;
    }
    
    private int indexForHash(int hash) {
        return (myTable.length - 1) & hash;
    }
    
    private static int hashCode(Object key) {
        if (key.getClass() == String.class) {
            int hash = 0;
            String str = (String) key;
            for (int i = 0; i < str.length(); i++) {
                hash = hash*33 + str.charAt(i);
            }
            return hash;
        }
        return key.hashCode();
    }
    
    private void resize(int newSize) {
        Entry[] oldTable = myTable;
        myTable = new Entry[newSize];

        for (int i = 0; i < oldTable.length; i++) {
            Entry oldEntry = oldTable[i];
            while (oldEntry != null) {
                int index = indexForHash(oldEntry.hash);
                Entry newEntry = myTable[index];
                if (newEntry == null) {
                    myTable[index] = oldEntry;
                } else {
                    while (newEntry.next != null) {
                        newEntry = newEntry.next;
                    }
                    newEntry.next = oldEntry;                    
                }
                Entry nextEntry = oldEntry.next;
                oldEntry.next = null;
                oldEntry = nextEntry;
            }
        }
        myLimit = (int) (myTable.length * LOAD_FACTOR);
    }
    
    private static boolean eq(Object a, Object b) {
        return a == b || a.equals(b);
    }
    
    private class KeySet extends AbstractSet {
        public Iterator iterator() {
            return new KeyIterator();
        }
        public int size() {
            return myEntryCount;
        }
        public boolean contains(Object o) {
            return containsKey(o);
        }
        public boolean remove(Object o) {
            return SVNMap.this.remove(o) != null;
        }
        public void clear() {
            SVNMap.this.clear();
        }
    }

    private class EntrySet extends AbstractSet {
        public Iterator iterator() {
            return new TableIterator();
        }
        public int size() {
            return myEntryCount;
        }
        public boolean contains(Object o) {
            if (o instanceof Map.Entry) {
                Map.Entry entry = (Entry) o;
                if (SVNMap.this.containsKey(entry.getKey())) {
                    Object value = SVNMap.this.get(entry.getKey());
                    if (value == null) {
                        return entry.getValue() == null;
                    }
                    return value.equals(entry.getValue());
                }
            }
            return false;
        }
        
        public boolean remove(Object o) {
            if (o instanceof Map.Entry) {
                Map.Entry entry = (Entry) o;
                return SVNMap.this.remove(entry.getKey()) != null;
            }
            return false;
        }
        public void clear() {
            SVNMap.this.clear();
        }
    }

    private class ValueCollection extends AbstractCollection {
        public Iterator iterator() {
            return new ValueIterator();
        }
        public int size() {
            return myEntryCount;
        }
        public boolean contains(Object o) {
            return containsValue(o);
        }
        public void clear() {
            SVNMap.this.clear();
        }
    }
    
    private class TableIterator implements Iterator {
        
        private int index;
        private Entry entry;
        private Entry previous;
        private int modCount;
        
        public TableIterator() {
            index = 0;
            entry = null;
            modCount = myModCount;
            while (index < myTable.length && entry == null) {
                entry = myTable[index];
                index++;
            }
        }

        public boolean hasNext() {
            return entry != null;
        }

        public Object next() {
            if (myModCount != modCount) {
                throw new ConcurrentModificationException();
            }
            if (entry == null) {
                throw new NoSuchElementException();
            }
            previous = entry;
            entry = entry.next;
            while (entry == null && index < myTable.length) {
                entry = myTable[index];
                index++;
            }
            return previous;
        }

        public void remove() {
            if (myModCount != modCount) {
                throw new ConcurrentModificationException();
            }
            if (previous != null) {
                SVNMap.this.remove(entry.getKey());
                previous = null;
            } else {
                throw new IllegalStateException();
            }
        }
    }
    
    private class KeyIterator extends TableIterator {

        public Object next() {
            Entry next = (Entry) super.next();
            return next.getKey();
        }
    }

    private class ValueIterator extends TableIterator {

        public Object next() {
            Entry next = (Entry) super.next();
            return next.getValue();
        }
    }
    
    private static class Entry implements Map.Entry {
        
        public Entry next;
        public Object key;
        public Object value;
        public int hash;
        
        public Entry(Object key, Object value, int hash) {
            this.key = key;
            this.value = value;
            this.hash = hash;
        }

        public Object setValue(Object value) {
            Object oldValue = getValue();
            this.value = value; 
            return oldValue;
        }

        public Object getValue() {
            return value;
        }

        public Object getKey() {
            return key == NULL_KEY ? null : key;
        }
        
        public int hashCode() {
            return (key == NULL_KEY ? 0 : key.hashCode()) ^ (value == null ? 0 : value.hashCode());
        }

        public boolean equals(Object o) {
            if (!(o instanceof Map.Entry)) {
                return false;
            }
            Map.Entry e = (Map.Entry)o;
            Object k1 = getKey();
            Object k2 = e.getKey();
            
            if (k1 == k2 || (k1 != null && k1.equals(k2))) {
                Object v1 = getValue();
                Object v2 = e.getValue();
                
                if (v1 == v2 || (v1 != null && v1.equals(v2))) { 
                    return true;
                }
            }
            return false;
        }
    }
}
