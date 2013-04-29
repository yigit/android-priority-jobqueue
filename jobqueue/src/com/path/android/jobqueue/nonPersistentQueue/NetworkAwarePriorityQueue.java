package com.path.android.jobqueue.nonPersistentQueue;

import com.path.android.jobqueue.JobHolder;

import java.util.Comparator;
import java.util.Queue;

/**
 * A {@link MergedQueue} class that can separate jobs based on their network requirement
 */
public class NetworkAwarePriorityQueue extends MergedQueue {

    /**
     * create a network aware priority queue with given initial capacity * 2 and comparator
     * @param initialCapacity
     * @param comparator
     */
    public NetworkAwarePriorityQueue(int initialCapacity, Comparator<JobHolder> comparator) {
        super(initialCapacity, comparator, comparator);
    }

    /**
     * {@link java.util.Queue#peek()} implementation with network requirement filter
     * @param canUseNetwork if {@code true}, does not check network requirement if {@code false}, returns only from
     *                      no network queue
     * @return
     */
    public JobHolder peek(boolean canUseNetwork) {
        if(canUseNetwork) {
            return super.peek();
        } else {
            return super.peekFromQueue(QeueuId.Q1);
        }
    }

    /**
     * {@link java.util.Queue#poll()} implementation with network requirement filter
     * @param canUseNetwork if {@code true}, does not check network requirement if {@code false}, returns only from
     *                      no network queue
     * @return
     */
    public JobHolder poll(boolean canUseNetwork) {
        if(canUseNetwork) {
            return super.peek();
        } else {
            return super.peekFromQueue(QeueuId.Q1);
        }
    }

    @Override
    protected QeueuId decideQueue(JobHolder jobHolder) {
        return jobHolder.requiresNetwork() ? QeueuId.Q0 : QeueuId.Q1;
    }

    /**
     * create a {@link TimeAwarePriorityQueue}
     * @param initialCapacity
     * @param comparator
     * @return
     */
    @Override
    protected Queue<JobHolder> createQueue(int initialCapacity, Comparator<JobHolder> comparator) {
        return new TimeAwarePriorityQueue(initialCapacity, comparator);
    }


}
