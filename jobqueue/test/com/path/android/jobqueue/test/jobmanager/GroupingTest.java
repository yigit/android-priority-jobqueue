package com.path.android.jobqueue.test.jobmanager;

import com.path.android.jobqueue.JobHolder;
import com.path.android.jobqueue.JobManager;
import com.path.android.jobqueue.Params;
import com.path.android.jobqueue.test.jobs.DummyJob;
import com.path.android.jobqueue.test.jobs.PersistentDummyJob;
import org.fest.reflect.method.Invoker;
import org.hamcrest.MatcherAssert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;

@RunWith(RobolectricTestRunner.class)
public class GroupingTest extends JobManagerTestBase {
    @Test
    public void testGrouping() throws Exception {
        JobManager jobManager = createJobManager();
        jobManager.stop();
        Invoker<JobHolder> nextJobMethod = getNextJobMethod(jobManager);
        Invoker<Void> removeJobMethod = getRemoveJobMethod(jobManager);

        long jobId1 = jobManager.addJob(new DummyJob(new Params(0).groupBy("group1")));
        long jobId2 = jobManager.addJob(new DummyJob(new Params(0).groupBy("group1")));
        long jobId3 = jobManager.addJob(new PersistentDummyJob(new Params(0).groupBy("group2")));
        long jobId4 = jobManager.addJob(new PersistentDummyJob(new Params(0).groupBy("group1")));
        JobHolder nextJob = nextJobMethod.invoke();
        MatcherAssert.assertThat("next job should be the first job from group1", nextJob.getId(), equalTo(jobId1));
        JobHolder group2Job = nextJobMethod.invoke();
        MatcherAssert.assertThat("since group 1 is running now, next job should be from group 2", group2Job.getId(), equalTo(jobId3));
        removeJobMethod.invoke(nextJob);
        JobHolder group1NextJob =nextJobMethod.invoke();
        MatcherAssert.assertThat("after removing job from group 1, another job from group1 should be returned", group1NextJob.getId(), equalTo(jobId2));
        MatcherAssert.assertThat("when jobs from both groups are running, no job should be returned from next job", nextJobMethod.invoke(), is(nullValue()));
        removeJobMethod.invoke(group2Job);
        MatcherAssert.assertThat("even after group2 job is complete, no jobs should be returned since we only have group1 jobs left", nextJobMethod.invoke(), is(nullValue()));
    }
}
