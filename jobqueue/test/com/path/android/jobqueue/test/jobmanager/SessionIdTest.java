package com.path.android.jobqueue.test.jobmanager;


import com.path.android.jobqueue.BaseJob;
import com.path.android.jobqueue.JobHolder;
import com.path.android.jobqueue.JobManager;
import com.path.android.jobqueue.test.jobs.DummyJob;
import com.path.android.jobqueue.test.jobs.PersistentDummyJob;
import org.fest.reflect.core.Reflection;
import org.fest.reflect.method.Invoker;
import org.hamcrest.MatcherAssert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static org.hamcrest.CoreMatchers.equalTo;

@RunWith(RobolectricTestRunner.class)
public class SessionIdTest extends JobManagerTestBase {
    @Test
    public void testSessionId() throws Exception {
        JobManager jobManager = createJobManager();
        Long sessionId = Reflection.field("sessionId").ofType(long.class)
                .in(jobManager).get();
        jobManager.stop();
        BaseJob[] jobs = new BaseJob[]{new DummyJob(), new PersistentDummyJob()};
        for (BaseJob job : jobs) {
            jobManager.addJob(0, job);
        }

        Invoker<JobHolder> nextJobMethod = getNextJobMethod(jobManager);
        for (int i = 0; i < jobs.length; i++) {
            JobHolder jobHolder = nextJobMethod.invoke();
            MatcherAssert.assertThat("session id should be correct for job " + i, jobHolder.getRunningSessionId(), equalTo(sessionId));
        }
    }
}
