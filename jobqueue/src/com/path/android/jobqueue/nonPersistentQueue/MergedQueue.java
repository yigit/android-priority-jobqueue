package com.path.android.jobqueue.nonPersistentQueue;

import com.path.android.jobqueue.JobHolder;

import java.util.*;

/**
 * A queue implementation that utilize two queues depending on one or multiple properties of the {@link JobHolder}
 * While retrieving items, it uses a different comparison method to handle dynamic comparisons (e.g. time)
 * between two queues
 */
abstract public class MergedQueue implements Queue<JobHolder> {
    Queue<JobHolder> queue0;
    Queue<JobHolder> queue1;

    final Comparator<JobHolder> comparator;
    final Comparator<JobHolder> retrieveComparator;

    /**
     *
     * @param initialCapacity passed to {@link MergedQueue#createQueue(MergedQueue.QeueuId, int, java.util.Comparator)}
     * @param comparator passed to {@link MergedQueue#createQueue(MergedQueue.QeueuId, int, java.util.Comparator)}
     * @param retrieveComparator upon retrieval, if both queues return items, this comparator is used to decide which
     *                           one should be returned
     */
    public MergedQueue(int initialCapacity, Comparator<JobHolder> comparator, Comparator<JobHolder> retrieveComparator) {
        this.comparator = comparator;
        this.retrieveComparator = retrieveComparator;
        queue0 = createQueue(QeueuId.Q0, initialCapacity, comparator);
        queue1 = createQueue(QeueuId.Q1, initialCapacity, comparator);
    }

    /**
     * used to poll from one of the queues
     * @param queueId
     * @return
     */
    protected JobHolder pollFromQueue(QeueuId queueId) {
        if(queueId == QeueuId.Q0) {
            return queue0.poll();
        }
        return queue1.poll();
    }

    /**
     * used to peek from one of the queues
     * @param queueId
     * @return
     */
    protected JobHolder peekFromQueue(QeueuId queueId) {
        if(queueId == QeueuId.Q0) {
            return queue0.peek();
        }
        return queue1.peek();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean offer(JobHolder jobHolder) {
        QeueuId queueId = decideQueue(jobHolder);
        if(queueId == QeueuId.Q0) {
            return queue0.offer(jobHolder);

        }
        return queue1.offer(jobHolder);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public JobHolder poll() {
        JobHolder delayed = queue0.peek();
        if(delayed == null) {
            return queue1.poll();
        }
        //if queue for this job has changed, re-add it and try poll from scratch
        if(decideQueue(delayed) != QeueuId.Q0) {
            //should be moved to the other queue
            queue0.remove(delayed);
            queue1.add(delayed);
            return poll();
        }
        JobHolder nonDelayed = queue1.peek();
        if(nonDelayed == null) {
            queue0.remove(delayed);
            return delayed;
        }
        //if queue for this job has changed, re-add it and try poll from scratch
        if(decideQueue(nonDelayed) != QeueuId.Q1) {
            queue0.add(nonDelayed);
            queue1.remove(nonDelayed);
            return poll();
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
    public JobHolder peek() {
        JobHolder delayed = queue0.peek();
        //if queue for this job has changed, re-add it and try peek from scratch
        if(delayed != null && decideQueue(delayed) != QeueuId.Q0) {
            queue1.add(delayed);
            queue0.remove(delayed);
            return peek();
        }
        JobHolder nonDelayed = queue1.peek();
        //if queue for this job has changed, re-add it and try peek from scratch
        if(nonDelayed != null && decideQueue(nonDelayed) != QeueuId.Q1) {
            queue0.add(nonDelayed);
            queue1.remove(nonDelayed);
            return peek();
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

    /**
     * {@inheritDoc}
     */
    @Override
    public JobHolder remove() {
        JobHolder poll = poll();
        if(poll == null) {
            throw new NoSuchElementException();
        }
        return poll;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public JobHolder element() {
        JobHolder peek = peek();
        if(peek == null) {
            throw new NoSuchElementException();
        }
        return peek;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean add(JobHolder jobHolder) {
        //does not affect our case
        return offer(jobHolder);
    }

    /**
     * this method is not supported
     * @throws UnsupportedOperationException
     */
    @Override
    public boolean addAll(Collection<? extends JobHolder> jobHolders) {
        throw new UnsupportedOperationException();
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
    public boolean contains(Object o) {
        return queue1.contains(o) || queue0.contains(o);
    }

    /**
     * this method is not supported
     * @throws UnsupportedOperationException
     */
    @Override
    public boolean containsAll(Collection<?> objects) {
        throw new UnsupportedOperationException();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isEmpty() {
        return queue1.isEmpty() && queue0.isEmpty();
    }

    /**
     * this method is not supported
     * @throws UnsupportedOperationException
     */
    @Override
    public Iterator<JobHolder> iterator() {
        throw new UnsupportedOperationException();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean remove(Object o) {
        //we cannot check queue here, might be dynamic
        return queue1.remove(o) || queue0.remove(o);
    }

    /**
     * this method is not supported
     * @throws UnsupportedOperationException
     */
    @Override
    public boolean removeAll(Collection<?> objects) {
        throw new UnsupportedOperationException();
    }

    /**
     * this method is not supported
     * @throws UnsupportedOperationException
     */
    @Override
    public boolean retainAll(Collection<?> objects) {
        throw new UnsupportedOperationException();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int size() {
        return queue0.size() + queue1.size();
    }

    /**
     * this method is not supported
     * @throws UnsupportedOperationException
     */
    @Override
    public Object[] toArray() {
        throw new UnsupportedOperationException();
    }

    /**
     * this method is not supported
     * @throws UnsupportedOperationException
     */
    @Override
    public Object[] toArray(Object[] ts) {
        throw new UnsupportedOperationException();
    }

    /**
     * decides which queue should the job holder go
     * if first queue, should return 0
     * if second queue, should return 1
     * is only called when an item is inserted. methods like remove always call both queues.
     * @param jobHolder
     * @return
     */
    abstract protected QeueuId decideQueue(JobHolder jobHolder);

    /**
     * called when we want to create the subsequent queues
     * @param initialCapacity
     * @param comparator
     * @return
     */
    abstract protected Queue<JobHolder> createQueue(QeueuId qeueuId, int initialCapacity, Comparator<JobHolder> comparator);

    /**
     * simple enum to identify queues
     */
    protected static enum QeueuId {
        Q0,
        Q1
    }
}
