package com.path.android.jobqueue.cachedQueue;

import com.path.android.jobqueue.JobHolder;
import com.path.android.jobqueue.JobQueue;
import com.path.android.jobqueue.TagConstraint;

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.Set;

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
    public Long getNextJobDelayUntilNs(boolean hasNetwork, Collection<String> excludeGroups) {
        if(cache.delayUntil == null) {
            cache.delayUntil = new Cache.DelayUntil(hasNetwork,
                    delegate.getNextJobDelayUntilNs(hasNetwork, excludeGroups), excludeGroups);
        } else if(!cache.delayUntil.isValid(hasNetwork, excludeGroups)) {
            cache.delayUntil.set(hasNetwork,
                    delegate.getNextJobDelayUntilNs(hasNetwork, excludeGroups), excludeGroups);
        }
        return cache.delayUntil.value;
    }

    @Override
    public void clear() {
        cache.invalidateAll();
        delegate.clear();
    }

    @Override
    public Set<JobHolder> findJobsByTags(TagConstraint constraint, boolean excludeCancelled,
            Collection<Long> exclude, String... tags) {
        return delegate.findJobsByTags(constraint, excludeCancelled, exclude, tags);
    }

    @Override
    public void onJobCancelled(JobHolder holder) {
        delegate.onJobCancelled(holder);
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
            Collection<String> excludeGroups;

            private DelayUntil(boolean hasNetwork, Long value, Collection<String> excludeGroups) {
                this.value = value;
                this.hasNetwork = hasNetwork;
                this.excludeGroups = excludeGroups;
            }

            private boolean isValid(boolean hasNetwork, Collection<String> excludeGroups) {
                return this.hasNetwork == hasNetwork && validateExcludes(excludeGroups);
            }

            private boolean validateExcludes(Collection<String> excludeGroups) {
                if (this.excludeGroups == excludeGroups) {
                    return true;
                }
                if (this.excludeGroups == null || excludeGroups == null) {
                    return false;
                }
                if (this.excludeGroups.size() != excludeGroups.size()) {
                    return false;
                }
                Iterator<String> itr1 = this.excludeGroups.iterator();
                Iterator<String> itr2 = excludeGroups.iterator();
                while (itr1.hasNext()) {
                    if (!itr1.next().equals(itr2.next())) {
                        return false;
                    }
                }
                return true;
            }

            public void set(boolean hasNetwork, Long value, Collection<String> excludeGroups) {
                this.value = value;
                this.hasNetwork = hasNetwork;
                this.excludeGroups = excludeGroups;
            }
        }
    }
}
