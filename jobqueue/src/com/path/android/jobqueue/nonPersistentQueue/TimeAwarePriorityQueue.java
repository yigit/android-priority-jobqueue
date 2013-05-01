package com.path.android.jobqueue.nonPersistentQueue;

import com.path.android.jobqueue.JobHolder;

import java.util.*;

/**
 * This is a {@link MergedQueue} class that can handle queue updates based on time.
 * It uses two queues, one for jobs that can run now and the other for jobs that should wait.
 * Upon retrieval, if it detects a job in delayed queue that can run now, it removes it from there, adds it to Q0
 * and re-runs the operation. This is not very efficient but provides proper ordering for delayed jobs.
 */
public class TimeAwarePriorityQueue extends MergedQueue {

    /**
     * When retrieving jobs, considers current system nanotime to check if jobs are valid. if both jobs are valid
     * or both jobs are invalid, returns based on regular comparison
     * @param initialCapacity
     * @param comparator
     */
    public TimeAwarePriorityQueue(int initialCapacity, Comparator<JobHolder> comparator) {
        super(initialCapacity, comparator, new TimeAwareComparator(comparator));
    }

    @Override
    protected QeueuId decideQueue(JobHolder jobHolder) {
        return jobHolder.getDelayUntilNs() <= System.nanoTime() ? QeueuId.Q0 : QeueuId.Q1;
    }

    /**
     * create a {@link PriorityQueue} with given comparator
     * @param qeueuId
     * @param initialCapacity
     * @param comparator
     * @return
     */
    @Override
    protected Queue<JobHolder> createQueue(QeueuId qeueuId, int initialCapacity, Comparator<JobHolder> comparator) {
        if(qeueuId == QeueuId.Q0) {
            return new PriorityQueue<JobHolder>(initialCapacity, comparator);
        } else {
            return new PriorityQueue<JobHolder>(initialCapacity, new ConsistentTimedComparator(comparator));
        }
    }
}
