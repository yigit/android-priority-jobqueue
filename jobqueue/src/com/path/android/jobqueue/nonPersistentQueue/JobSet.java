package com.path.android.jobqueue.nonPersistentQueue;

import com.path.android.jobqueue.JobHolder;
import com.path.android.jobqueue.TagConstraint;

import java.util.Collection;
import java.util.Set;

/**
 * An interface for Job Containers
 * It is very similar to SortedSet
 */
public interface JobSet {
    JobHolder peek(Collection<String> excludeGroupIds);
    JobHolder poll(Collection<String> excludeGroupIds);
    JobHolder findById(long id);
    Set<JobHolder> findByTags(TagConstraint constraint, Collection<Long> exclude,
            String... tags);
    boolean offer(JobHolder holder);
    boolean remove(JobHolder holder);
    void clear();
    int size();
    CountWithGroupIdsResult countReadyJobs(long now, Collection<String> excludeGroups);
    CountWithGroupIdsResult countReadyJobs(Collection<String> excludeGroups);
}
