package com.path.android.jobqueue.persistentQueue.sqlite;

import com.birbit.android.jobqueue.Constraint;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * a class to cache ready jobs queries so that we can avoid unnecessary memory allocations for them
 */
public class QueryCache {
    private final Map<String, String> cache;

    public QueryCache() {
        cache = new HashMap<>();
    }

    public synchronized String get(Constraint constraint) {
        String key = constraint.getUniqueId();
        return cache.get(key);
    }

    public synchronized void set(String query, Constraint constraint) {
        String key = constraint.getUniqueId();
        cache.put(key, query);
        return;
    }

    public synchronized void clear() {
        cache.clear();
    }
}
