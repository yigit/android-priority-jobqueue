package com.path.android.jobqueue.nonPersistentQueue;

import com.path.android.jobqueue.JobHolder;

import java.util.*;

/**
 * A queue implementation that utilize two queues depending on one or multiple properties of the {@link JobHolder}
 * While retrieving items, it uses a different comparison method to handle dynamic comparisons (e.g. time)
 * between two queues
 */
abstract public class MergedQueue implements JobSet {
    JobSet queue0;
    JobSet queue1;

    final Comparator<JobHolder> comparator;
    final Comparator<JobHolder> retrieveComparator;

    /**
     *
     * @param initialCapacity passed to {@link MergedQueue#createQueue(com.path.android.jobqueue.nonPersistentQueue.MergedQueue.SetId, int, java.util.Comparator)}
     * @param comparator passed to {@link MergedQueue#createQueue(com.path.android.jobqueue.nonPersistentQueue.MergedQueue.SetId, int, java.util.Comparator)}
     * @param retrieveComparator upon retrieval, if both queues return items, this comparator is used to decide which
     *                           one should be returned
     */
    public MergedQueue(int initialCapacity, Comparator<JobHolder> comparator, Comparator<JobHolder> retrieveComparator) {
        this.comparator = comparator;
        this.retrieveComparator = retrieveComparator;
        queue0 = createQueue(SetId.S0, initialCapacity, comparator);
        queue1 = createQueue(SetId.S1, initialCapacity, comparator);
    }

    /**
     * used to poll from one of the queues
     * @param queueId
     * @return
     */
    protected JobHolder pollFromQueue(SetId queueId, Collection<String> excludeGroupIds) {
        if(queueId == SetId.S0) {
            return queue0.poll(excludeGroupIds);
        }
        return queue1.poll(excludeGroupIds);
    }

    /**
     * used to peek from one of the queues
     * @param queueId
     * @return
     */
    protected JobHolder peekFromQueue(SetId queueId, Collection<String> excludeGroupIds) {
        if(queueId == SetId.S0) {
            return queue0.peek(excludeGroupIds);
        }
        return queue1.peek(excludeGroupIds);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean offer(JobHolder jobHolder) {
        SetId queueId = decideQueue(jobHolder);
        if(queueId == SetId.S0) {
            return queue0.offer(jobHolder);

        }
        return queue1.offer(jobHolder);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public JobHolder poll(Collection<String> excludeGroupIds) {
        JobHolder delayed = queue0.peek(excludeGroupIds);
        if(delayed == null) {
            return queue1.poll(excludeGroupIds);
        }
        //if queue for this job has changed, re-add it and try poll from scratch
        if(decideQueue(delayed) != SetId.S0) {
            //should be moved to the other queue
            queue0.remove(delayed);
            queue1.offer(delayed);
            return poll(excludeGroupIds);
        }
        JobHolder nonDelayed = queue1.peek(excludeGroupIds);
        if(nonDelayed == null) {
            queue0.remove(delayed);
            return delayed;
        }
        //if queue for this job has changed, re-add it and try poll from scratch
        if(decideQueue(nonDelayed) != SetId.S1) {
            queue0.offer(nonDelayed);
            queue1.remove(nonDelayed);
            return poll(excludeGroupIds);
        }
        //both are not null, need to compare and return the better
        int cmp = retrieveComparator.compare(delayed, nonDelayed);
        if(cmp == -1) {
            queue0.remove(delayed);
            return delayed;
        } else {
            queue1.remove(nonDelayed);
            return nonDelayed;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public JobHolder peek(Collection<String> excludeGroupIds) {
        while (true) {
            JobHolder delayed = queue0.peek(excludeGroupIds);
            //if queue for this job has changed, re-add it and try peek from scratch
            if(delayed != null && decideQueue(delayed) != SetId.S0) {
                queue1.offer(delayed);
                queue0.remove(delayed);
                continue;//retry
            }
            JobHolder nonDelayed = queue1.peek(excludeGroupIds);
            //if queue for this job has changed, re-add it and try peek from scratch
            if(nonDelayed != null && decideQueue(nonDelayed) != SetId.S1) {
                queue0.offer(nonDelayed);
                queue1.remove(nonDelayed);
                continue;//retry
            }
            if(delayed == null) {
                return nonDelayed;
            }
            if(nonDelayed == null) {
                return delayed;
            }
            int cmp = retrieveComparator.compare(delayed, nonDelayed);
            if(cmp == -1) {
                return delayed;
            }
            return nonDelayed;
        }
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public void clear() {
        queue1.clear();
        queue0.clear();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean remove(JobHolder holder) {
        //we cannot check queue here, might be dynamic
        return queue1.remove(holder) || queue0.remove(holder);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int size() {
        return queue0.size() + queue1.size();
    }

    /**
     * decides which queue should the job holder go
     * if first queue, should return 0
     * if second queue, should return 1
     * is only called when an item is inserted. methods like remove always call both queues.
     * @param jobHolder
     * @return
     */
    abstract protected SetId decideQueue(JobHolder jobHolder);

    /**
     * called when we want to create the subsequent queues
     * @param initialCapacity
     * @param comparator
     * @return
     */
    abstract protected JobSet createQueue(SetId setId, int initialCapacity, Comparator<JobHolder> comparator);

    public CountWithGroupIdsResult countReadyJobs(SetId setId, long now, Collection<String> excludeGroups) {
        if(setId == SetId.S0) {
            return queue0.countReadyJobs(now, excludeGroups);
        } else {
            return queue1.countReadyJobs(now, excludeGroups);
        }
    }

    public CountWithGroupIdsResult countReadyJobs(SetId setId, Collection<String> excludeGroups) {
        if(setId == SetId.S0) {
            return queue0.countReadyJobs(excludeGroups);
        } else {
            return queue1.countReadyJobs(excludeGroups);
        }
    }



    /**
     * Returns the JobHolder that has the given id
     * @param id id job the job
     * @return
     */
    @Override
    public JobHolder findById(long id) {
        JobHolder q0 = queue0.findById(id);
        return q0 == null ? queue1.findById(id) : q0;
    }

    /**
     * simple enum to identify queues
     */
    protected static enum SetId {
        S0,
        S1
    }
}
