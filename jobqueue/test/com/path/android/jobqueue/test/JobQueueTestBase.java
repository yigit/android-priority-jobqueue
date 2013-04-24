package com.path.android.jobqueue.test;

import com.path.android.jobqueue.JobHolder;
import com.path.android.jobqueue.JobQueue;
import com.path.android.jobqueue.test.jobs.DummyJob;
import com.path.android.jobqueue.test.util.JobQueueFactory;
import org.hamcrest.MatcherAssert;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.theories.Theory;

import static org.hamcrest.CoreMatchers.*;

@Ignore
public abstract class JobQueueTestBase {
    JobQueueFactory currentFactory;

    public JobQueueTestBase(JobQueueFactory factory) {
        currentFactory = factory;
    }

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
    public void testSessionId() throws Exception {
        long sessionId = (long)(Math.random() * 100000);
        JobQueue jobQueue = createNewJobQueueWithSessionId(sessionId);
        JobHolder jobHolder = createNewJobHolder();
        jobQueue.insert(jobHolder);
        jobHolder = jobQueue.nextJobAndIncRunCount();
        MatcherAssert.assertThat("session id should be attached to next job",
                jobHolder.getRunningSessionId(), equalTo(sessionId));
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
        return createNewJobQueueWithSessionId(System.nanoTime());
    }

    private JobQueue createNewJobQueueWithSessionId(Long sessionId) {
        return currentFactory.createNew(sessionId, "id_" + sessionId);
    }
}
