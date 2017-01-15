package com.birbit.android.jobqueue.test.jobmanager;

import com.birbit.android.jobqueue.Job;
import com.birbit.android.jobqueue.JobManager;
import com.birbit.android.jobqueue.Params;
import com.birbit.android.jobqueue.callback.JobManagerCallback;
import com.birbit.android.jobqueue.callback.JobManagerCallbackAdapter;
import com.birbit.android.jobqueue.test.jobs.DummyJob;
import static org.hamcrest.CoreMatchers.*;
import org.hamcrest.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;

import android.annotation.TargetApi;
import android.os.Build;
import android.support.annotation.NonNull;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@RunWith(RobolectricTestRunner.class)
@Config(constants = com.birbit.android.jobqueue.BuildConfig.class)
public class CountTest extends JobManagerTestBase {
    @TargetApi(Build.VERSION_CODES.GINGERBREAD)
    @Test
    public void testCount() throws Exception {
        enableDebug();
        JobManager jobManager = createJobManager();
        jobManager.stop();
        for (int i = 0; i < 10; i++) {
            jobManager.addJob(new DummyJob(new Params(0).persist()));
            MatcherAssert.assertThat((int) jobManager.count(), equalTo(i * 2 + 1));
            jobManager.addJob(new DummyJob(new Params(0).persist()));
            MatcherAssert.assertThat((int) jobManager.count(), equalTo(i * 2 + 2));
        }
        final CountDownLatch jobsToRun = new CountDownLatch(20);
        jobManager.addCallback(new JobManagerCallbackAdapter() {
            @Override
            public void onAfterJobRun(@NonNull Job job, int resultCode) {
                if (resultCode == JobManagerCallback.RESULT_SUCCEED) {
                    jobsToRun.countDown();
                }
            }
        });
        jobManager.start();
        MatcherAssert.assertThat("test sanity", jobsToRun.await(1, TimeUnit.MINUTES), is(true));
        MatcherAssert.assertThat((int) jobManager.count(), equalTo(0));
    }
}
