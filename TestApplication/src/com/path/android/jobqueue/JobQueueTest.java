package com.path.android.jobqueue;

import com.path.android.jobqueue.jobs.DummyJob;
import com.path.android.jobqueue.nonPersistentQueue.NonPersistentPriorityQueue;
import com.path.android.jobqueue.persistentQueue.sqlite.SqliteJobQueue;
import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;

import java.util.List;

import static org.hamcrest.CoreMatchers.*;

@RunWith(RobolectricTestRunner.class)
public class JobQueueTest {
    @Test
    public void testBasicAddRemoveCount() throws Exception {
        final int ADD_COUNT = 6;
        JobQueue jobQueue = createNewJobQueue();
        MatcherAssert.assertThat((int)jobQueue.count(), equalTo(0));
        MatcherAssert.assertThat(jobQueue.nextJobAndIncRunCount(), nullValue());
        for(int i = 0; i < ADD_COUNT; i++) {
            JobHolder holder = createNewJobHolder();
            jobQueue.insert(holder);
            MatcherAssert.assertThat((int)jobQueue.count(), equalTo(i+1));
            MatcherAssert.assertThat(holder.getId(), notNullValue());
        }
        JobHolder firstHolder = jobQueue.nextJobAndIncRunCount();
        MatcherAssert.assertThat(firstHolder.getRunCount(), equalTo(1));
        //size should still be the same
        MatcherAssert.assertThat((int)jobQueue.count(), equalTo(ADD_COUNT));
        //should return another job
        JobHolder secondHolder = jobQueue.nextJobAndIncRunCount();
        MatcherAssert.assertThat(secondHolder.getRunCount(), equalTo(1));
        //size should still be the same
        MatcherAssert.assertThat((int)jobQueue.count(), equalTo(ADD_COUNT));
        //second holder and first holder should have different ids
        MatcherAssert.assertThat(firstHolder.getId(), not(secondHolder.getId()));
        jobQueue.remove(secondHolder);
        MatcherAssert.assertThat((int)jobQueue.count(), equalTo(ADD_COUNT - 1));
        jobQueue.remove(secondHolder);
        //non existed job removed, count should be the same
        MatcherAssert.assertThat((int)jobQueue.count(), equalTo(ADD_COUNT - 1));
        jobQueue.remove(firstHolder);
        MatcherAssert.assertThat((int)jobQueue.count(), equalTo(ADD_COUNT - 2));
    }

    @Test
    public void testPriority() throws Exception {
        int JOB_LIMIT = 20;
        JobQueue jobQueue = createNewJobQueue();
        //create and add JOB_LIMIT jobs with random priority
        for(int i = 0; i < JOB_LIMIT; i++) {
            jobQueue.insert(createNewJobHolderWithPriority((int) (Math.random() * 10)));
        }
        //ensure we get jobs in correct priority order
        int minPriority = Integer.MAX_VALUE;
        for(int i = 0; i < JOB_LIMIT; i++) {
            JobHolder holder = jobQueue.nextJobAndIncRunCount();
            MatcherAssert.assertThat(holder.getPriority() <= minPriority, is(true));
        }
        MatcherAssert.assertThat(jobQueue.nextJobAndIncRunCount(), nullValue());
    }

    @Test
    public void testPriorityWithReAdd() throws Exception {
        int JOB_LIMIT = 20;
        JobQueue jobQueue = createNewJobQueue();
        //create and add JOB_LIMIT jobs with random priority
        for(int i = 0; i < JOB_LIMIT; i++) {
            jobQueue.insert(createNewJobHolderWithPriority((int) (Math.random() * 10)));
        }
        //ensure we get jobs in correct priority order
        int minPriority = Integer.MAX_VALUE;
        for(int i = 0; i < JOB_LIMIT; i++) {
            JobHolder holder = jobQueue.nextJobAndIncRunCount();
            MatcherAssert.assertThat(holder.getPriority() <= minPriority, is(true));
            jobQueue.insertOrReplace(holder);
        }
        MatcherAssert.assertThat(jobQueue.nextJobAndIncRunCount(), notNullValue());
    }

    private JobHolder createNewJobHolder() {
        return new JobHolder(null, 0, 0, new DummyJob(), System.nanoTime(), Long.MIN_VALUE);
    }

    private JobHolder createNewJobHolderWithPriority(int priority) {
        return new JobHolder(null, priority, 0, new DummyJob(), System.nanoTime(), Long.MIN_VALUE);
    }

    private JobQueue createNewJobQueue() {
        return new NonPersistentPriorityQueue(System.nanoTime(), "id_" + System.nanoTime());
//        return new SqliteJobQueue(Robolectric.application,System.nanoTime(), "id_" + System.nanoTime());
    }


}
