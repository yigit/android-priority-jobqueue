package com.path.android.jobqueue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.TreeSet;

/**
 * a util class that holds running jobs sorted by name and uniq.
 * it behaves like CopyOnWriteLists
 */
public class CopyOnWriteGroupSet {
    private ArrayList<String> publicClone;
    private final TreeSet<String> internalSet;

    public CopyOnWriteGroupSet() {
        internalSet = new TreeSet<String>();
    }

    public synchronized Collection<String> getSafe() {
        if(publicClone == null) {
            publicClone = new ArrayList<String>(internalSet);
        }
        return publicClone;
    }

    public synchronized void add(String group) {
        if(internalSet.add(group)) {
            publicClone = null;//invalidate
        }
    }

    public synchronized void remove(String group) {
        if(internalSet.remove(group)) {
            publicClone = null;
        }
    }

    public synchronized void clear() {
        internalSet.clear();
        publicClone = null;
    }
}
