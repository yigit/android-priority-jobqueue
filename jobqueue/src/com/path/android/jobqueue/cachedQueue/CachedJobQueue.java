package com.path.android.jobqueue.cachedQueue;

import com.path.android.jobqueue.JobHolder;
import com.path.android.jobqueue.JobQueue;

import java.util.Collection;

/**
 * a class that implements {@link JobQueue} interface, wraps another {@link JobQueue} and caches
 * results to avoid unnecessary queries to wrapped JobQueue.
 * does very basic caching but should be sufficient for most of the repeated cases
 * element
 */
public class CachedJobQueue implements JobQueue {
    JobQueue delegate;
    private Cache cache;

    public CachedJobQueue(JobQueue delegate) {
        this.delegate = delegate;
        this.cache = new Cache();
    }

    @Override
    public long insert(JobHolder jobHolder) {
        cache.invalidateAll();
        return delegate.insert(jobHolder);
    }

    @Override
    public long insertOrReplace(JobHolder jobHolder) {
        cache.invalidateAll();
        return delegate.insertOrReplace(jobHolder);
    }

    @Override
    public void remove(JobHolder jobHolder) {
        cache.invalidateAll();
        delegate.remove(jobHolder);
    }

    @Override
    public int count() {
        if(cache.count == null) {
            cache.count = delegate.count();
        }
        return cache.count;
    }

    @Override
    public int countReadyJobs(boolean hasNetwork, Collection<String> excludeGroups) {
        if(cache.count != null && cache.count < 1) {
            //we know count is zero, why query?
            return 0;
        }
        int count = delegate.countReadyJobs(hasNetwork, excludeGroups);
        if(count == 0) {
            //warm up cache if this is an empty queue case. if not, we are creating an unncessary query.
            count();
        }
        return count;
    }

    @Override
    public JobHolder nextJobAndIncRunCount(boolean hasNetwork, Collection<String> excludeGroups) {
        if(cache.count != null && cache.count < 1) {
            return null;//we know we are empty, no need for querying
        }
        JobHolder holder = delegate.nextJobAndIncRunCount(hasNetwork, excludeGroups);
        //if holder is null, there is a good chance that there aren't any jobs in queue try to cache it by calling count
        if(holder == null) {
            //warm up empty state cache
            count();
        } else if(cache.count != null) {
            //no need to invalidate cache for count
            cache.count--;
        }
        return holder;
    }

    @Override
    public Long getNextJobDelayUntilNs(boolean hasNetwork) {
        if(cache.delayUntil == null) {
            cache.delayUntil = new Cache.DelayUntil(hasNetwork, delegate.getNextJobDelayUntilNs(hasNetwork));
        } else if(!cache.delayUntil.isValid(hasNetwork)) {
            cache.delayUntil.set(hasNetwork, delegate.getNextJobDelayUntilNs(hasNetwork));
        }
        return cache.delayUntil.value;
    }

    @Override
    public void clear() {
        cache.invalidateAll();
        delegate.clear();
    }

    @Override
    public JobHolder findJobById(long id) {
        return delegate.findJobById(id);
    }

    private static class Cache {
        Integer count;
        DelayUntil delayUntil;

        public void invalidateAll() {
            count = null;
            delayUntil = null;
        }

        private static class DelayUntil {
            //can be null, is OK
            Long value;
            boolean hasNetwork;

            private DelayUntil(boolean hasNetwork, Long value) {
                this.value = value;
                this.hasNetwork = hasNetwork;
            }

            private boolean isValid(boolean hasNetwork) {
                return this.hasNetwork == hasNetwork;
            }

            public void set(boolean hasNetwork, Long value) {
                this.value = value;
                this.hasNetwork = hasNetwork;
            }
        }
    }
}
