package com.birbit.android.jobqueue.cachedQueue;

import com.birbit.android.jobqueue.Constraint;
import com.birbit.android.jobqueue.JobHolder;
import com.birbit.android.jobqueue.JobQueue;

import java.util.Set;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * a class that implements {@link JobQueue} interface, wraps another {@link JobQueue} and caches
 * results to avoid unnecessary queries to wrapped JobQueue.
 * does very basic caching but should be sufficient for most of the repeated cases
 * element
 */
public class CachedJobQueue implements JobQueue {
    private JobQueue delegate;
    private Integer cachedCount;

    public CachedJobQueue(JobQueue delegate) {
        this.delegate = delegate;
    }

    @Override
    public boolean insert(@NonNull JobHolder jobHolder) {
        invalidateCache();
        return delegate.insert(jobHolder);
    }

    private void invalidateCache() {
        cachedCount = null;
    }

    @Override
    public boolean insertOrReplace(@NonNull JobHolder jobHolder) {
        invalidateCache();
        return delegate.insertOrReplace(jobHolder);
    }

    @Override
    public void substitute(@NonNull JobHolder newJob, @NonNull JobHolder oldJob) {
        invalidateCache();
        delegate.substitute(newJob, oldJob);
    }

    @Override
    public void remove(@NonNull JobHolder jobHolder) {
        invalidateCache();
        delegate.remove(jobHolder);
    }

    @Override
    public int count() {
        if (cachedCount == null) {
            cachedCount = delegate.count();
        }
        return cachedCount;
    }

    private boolean isEmpty() {
        return cachedCount != null && cachedCount == 0;
    }

    @Override
    public int countReadyJobs(@NonNull Constraint constraint) {
        if (isEmpty()) {
            return 0;
        }
        return delegate.countReadyJobs(constraint);
    }

    @Override
    public JobHolder nextJobAndIncRunCount(@NonNull Constraint constraint) {
        if (isEmpty()) {
            return null;//we know we are empty, no need for querying
        }
        JobHolder holder = delegate.nextJobAndIncRunCount(constraint);
        if (holder != null && cachedCount != null) {
            cachedCount -= 1;
        }
        return holder;
    }

    @Override
    public Long getNextJobDelayUntilNs(@NonNull Constraint constraint) {
        return delegate.getNextJobDelayUntilNs(constraint);
    }

    @Override
    public void clear() {
        invalidateCache();
        delegate.clear();
    }

    @Override
    @NonNull
    public Set<JobHolder> findJobs(@NonNull Constraint constraint) {
        return delegate.findJobs(constraint);
    }

    @Override
    public void onJobCancelled(@NonNull JobHolder holder) {
        invalidateCache();
        delegate.onJobCancelled(holder);
    }

    @Override
    @Nullable
    public JobHolder findJobById(@NonNull String id) {
        return delegate.findJobById(id);
    }

    @Override
    @NonNull
    public Set<JobHolder> findDependentJobs(Set<JobHolder> jobHolders) {
        return delegate.findDependentJobs(jobHolders);
    }

    @Override
    @NonNull
    public Set<JobHolder> findDependentJobs(JobHolder jobHolder) {
        return delegate.findDependentJobs(jobHolder);
    }

    @NonNull
    @Override
    public Set<JobHolder> findJobsAndMarkScheduled(@NonNull Constraint constraint, int limit) {
        return delegate.findJobsAndMarkScheduled(constraint, limit);
    }

    @Override
    public int countJobs(Constraint constraint) {
        return delegate.countJobs(constraint);
    }

    @Override public Set<JobHolder> findJobsByTags(String[] tags) {
        return delegate.findJobsByTags(tags);
    }
}
