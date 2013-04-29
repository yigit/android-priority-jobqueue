package com.path.android.jobqueue.nonPersistentQueue;

import com.path.android.jobqueue.JobHolder;

import java.util.*;

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
     * @param initialCapacity
     * @param comparator
     * @return
     */
    @Override
    protected Queue<JobHolder> createQueue(int initialCapacity, Comparator<JobHolder> comparator) {
        return new PriorityQueue<JobHolder>(initialCapacity, comparator);
    }

    /**
     * A real-time comparator class that checks current time to decide of both jobs are valid or not.
     * Return values from this comparator are inconsistent as time may change.
     */
    private static class TimeAwareComparator implements Comparator<JobHolder> {
        final Comparator<JobHolder> baseComparator;

        public TimeAwareComparator(Comparator<JobHolder> baseComparator) {
            this.baseComparator = baseComparator;
        }

        @Override
        public int compare(JobHolder jobHolder, JobHolder jobHolder2) {
            long now = System.nanoTime();
            boolean job1Valid = jobHolder.getDelayUntilNs() <= now;
            boolean job2Valid = jobHolder2.getDelayUntilNs() <= now;
            if(job1Valid) {
                return job2Valid ? baseComparator.compare(jobHolder, jobHolder2) : -1;
            }
            if(job2Valid) {
                return job1Valid ? baseComparator.compare(jobHolder, jobHolder2) : 1;
            }
            return baseComparator.compare(jobHolder, jobHolder2);
        }

        @Override
        public boolean equals(Object o) {
            return false;
        }
    }
}
