package com.birbit.android.jobqueue.test.jobmanager;

import com.birbit.android.jobqueue.JobManager;
import com.birbit.android.jobqueue.Params;
import com.birbit.android.jobqueue.test.jobs.DummyJob;
import static org.hamcrest.CoreMatchers.*;
import org.hamcrest.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(constants = com.birbit.android.jobqueue.BuildConfig.class)
public class ClearTest extends JobManagerTestBase {
    @Test
    public void testClear() throws Exception {
        JobManager jobManager = createJobManager();
        final int LIMIT = 20;
        for(int i = 0; i < LIMIT; i++) {
            jobManager.addJob(new DummyJob(new Params(0).setPersistent(i % 2 == 1)));
        }
        jobManager.clear();
        MatcherAssert.assertThat("after clear, count should be 0", jobManager.count(), equalTo(0));
    }
}
