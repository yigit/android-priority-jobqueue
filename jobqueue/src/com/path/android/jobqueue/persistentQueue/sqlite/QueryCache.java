package com.path.android.jobqueue.persistentQueue.sqlite;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * a class to cache ready jobs queries so that we can avoid unnecessary memory allocations for them
 */
public class QueryCache {
    private static final String KEY_EMPTY_WITH_NETWORK = "w_n";
    private static final String KEY_EMPTY_WITHOUT_NETWORK = "wo_n";
    //never reach this outside sync block
    private final StringBuilder reusedBuilder;
    private final Map<String, String> cache;

    public QueryCache() {
        reusedBuilder = new StringBuilder();
        cache = new HashMap<String, String>();
    }

    public synchronized String get(boolean hasNetwork, Collection<String> excludeGroups) {
        String key = cacheKey(hasNetwork, excludeGroups);
        return cache.get(key);
    }

    public synchronized void set(String query, boolean hasNetwork, Collection<String> excludeGroups) {
        String key = cacheKey(hasNetwork, excludeGroups);
        cache.put(key, query);
        return;
    }

    /**
     * create a cache key for an exclude group set. exclude groups are guaranteed to be ordered so we rely on that
     * @param hasNetwork
     * @param excludeGroups
     * @return
     */
    private String cacheKey(boolean hasNetwork, Collection<String> excludeGroups) {
        if(excludeGroups == null || excludeGroups.size() == 0) {
            return hasNetwork ? KEY_EMPTY_WITH_NETWORK : KEY_EMPTY_WITHOUT_NETWORK;
        }
        reusedBuilder.setLength(0);
        reusedBuilder.append(hasNetwork ? "X" : "Y");
        for(String group : excludeGroups) {
            reusedBuilder.append("-").append(group);
        }
        return reusedBuilder.toString();
    }

    public synchronized void clear() {
        cache.clear();
    }
}
