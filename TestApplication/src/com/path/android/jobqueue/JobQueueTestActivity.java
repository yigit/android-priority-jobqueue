package com.path.android.jobqueue;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import com.path.android.jobqueue.log.JqLog;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class JobQueueTestActivity extends Activity {
    private JobManager jobManager;
    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        jobManager = new JobManager(this);
        testNonPersistentJob();
    }

    private void testNonPersistentJob() {
        final CountDownLatch latch = new CountDownLatch(1);
        jobManager.addJob(0, new NonPersistentLatchJob(latch));
        try {
            latch.await(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            JqLog.e(e);
        }
        assert latch.getCount() == 0;
    }

    private static class NonPersistentLatchJob extends BaseJob {
        CountDownLatch latch;
        public NonPersistentLatchJob(CountDownLatch latch) {
            this.latch = latch;
        }
        @Override
        public void onAdded() {

        }

        @Override
        public void onRun() throws Throwable {
            latch.countDown();
        }

        @Override
        public boolean shouldPersist() {
            return false;
        }

        @Override
        protected void onCancel() {

        }

        @Override
        protected boolean shouldReRunOnThrowable(Throwable throwable) {
            return false;
        }
    }
}
