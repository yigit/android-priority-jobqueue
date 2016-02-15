package com.path.android.jobqueue.test.jobqueue;

import com.birbit.android.jobqueue.TestConstraint;
import com.path.android.jobqueue.JobHolder;
import com.path.android.jobqueue.JobQueue;
import com.path.android.jobqueue.Params;
import com.path.android.jobqueue.config.Configuration;
import com.path.android.jobqueue.nonPersistentQueue.NonPersistentPriorityQueue;
import com.path.android.jobqueue.test.util.JobQueueFactory;
import com.path.android.jobqueue.timer.Timer;

import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.assertThat;
import org.hamcrest.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;

import android.test.mock.MockApplication;

import java.util.Collections;

import static com.path.android.jobqueue.TagConstraint.ANY;

@RunWith(RobolectricGradleTestRunner.class)
@Config(constants = com.path.android.jobqueue.BuildConfig.class)
public class NonPersistentJobQueueTest extends JobQueueTestBase {
    public NonPersistentJobQueueTest() {
        super(new JobQueueFactory() {
            @Override
            public JobQueue createNew(long sessionId, String id, Timer timer) {
                return new NonPersistentPriorityQueue(
                        new Configuration.Builder(new MockApplication()).timer(timer)
                                .id(id).build(), sessionId);
            }
        });
    }

    /**
     * issue #21 https://github.com/path/android-priority-jobqueue/issues/21
     */
    @Test
    public void testTooManyQueueChanges() throws InterruptedException {
        JobQueue jobQueue = createNewJobQueue();
        int limit = 10000;
        long delayMs = 2000;
        for(int i = 0; i < limit; i++) {
            jobQueue.insert(createNewJobHolder(new Params(0).requireNetwork().delayInMs(delayMs)));
        }
        TestConstraint constraint = new TestConstraint();
        constraint.setShouldNotRequireNetwork(true);
        MatcherAssert.assertThat("all jobs require network, should return null",
                jobQueue.nextJobAndIncRunCount(constraint), nullValue());
        mockTimer.incrementMs(delayMs + 1);
        //should be able to get it w/o an overflow
        constraint.setShouldNotRequireNetwork(false);
        for(int i = 0; i < limit; i++) {
            JobHolder holder = jobQueue.nextJobAndIncRunCount(constraint);
            MatcherAssert.assertThat("should get a next job", holder, notNullValue());
            jobQueue.remove(holder);
        }
    }

    @Test
    public void testFindByTags() {
        JobQueue jobQueue = createNewJobQueue();
        assertThat("empty queue should return 0",jobQueue.findJobs(TestConstraint.forTags(ANY,
                Collections.<String>emptyList(), "abc")).size(), is(0));

    }
}
