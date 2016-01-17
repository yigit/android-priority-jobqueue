package com.path.android.jobqueue.test.jobmanager;


import com.path.android.jobqueue.Job;
import com.path.android.jobqueue.JobHolder;
import com.path.android.jobqueue.JobManager;
import com.path.android.jobqueue.Params;
import com.path.android.jobqueue.test.jobs.DummyJob;
import org.fest.reflect.core.*;
import org.fest.reflect.method.*;
import static org.hamcrest.CoreMatchers.*;
import org.hamcrest.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;

@RunWith(RobolectricGradleTestRunner.class)
@Config(constants = com.path.android.jobqueue.BuildConfig.class)
public class SessionIdTest extends JobManagerTestBase {
    @Test
    public void testSessionId() throws Throwable {
        JobManager jobManager = createJobManager();
        Long sessionId = mockTimer.nanoTime(); //we know job manager uses this value :/
        jobManager.stop();
        Job[] jobs = new Job[]{new DummyJob(new Params(0)), new DummyJob(new Params(0).persist())};
        for (Job job : jobs) {
            jobManager.addJob(job);
        }

        for (int i = 0; i < jobs.length; i++) {
            JobHolder jobHolder = nextJob(jobManager);
            MatcherAssert.assertThat("session id should be correct for job " + i, jobHolder.getRunningSessionId(), equalTo(sessionId));
        }
    }
}
