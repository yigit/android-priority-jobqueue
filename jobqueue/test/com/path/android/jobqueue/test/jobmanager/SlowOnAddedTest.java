package com.path.android.jobqueue.test.jobmanager;

import com.path.android.jobqueue.Job;
import com.path.android.jobqueue.JobManager;
import com.path.android.jobqueue.Params;
import com.path.android.jobqueue.test.jobs.DummyJob;
import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;

import java.util.concurrent.CountDownLatch;

@RunWith(RobolectricTestRunner.class)
public class SlowOnAddedTest extends JobManagerTestBase {
    @Test
    public void testNonPersistent() throws InterruptedException {
        JobManager jobManager = createJobManager();
        CountDownLatch runLatch = new CountDownLatch(1);
        MyDummyJob job = new MyDummyJob(new Params(2), runLatch);
        for(int i = 0; i < 50; i++) {
            jobManager.addJob(new DummyJob(new Params(1)));
        }
        jobManager.addJob(job);
        runLatch.await();
        assertThat("on added should be called before on run", job.onAddedCntWhenRun, equalTo(1));
    }

    @Test
    public void testPersistent() throws InterruptedException {
        JobManager jobManager = createJobManager();
        MyDummyPersistentJob.persistentJobLatch = new CountDownLatch(1);
        for(int i = 0; i < 50; i++) {
            jobManager.addJob(new DummyJob(new Params(1).persist()));
        }
        jobManager.addJob(new MyDummyPersistentJob(2));
        MyDummyPersistentJob.persistentJobLatch.await();
        assertThat("even if job is persistent, onAdded should be called b4 onRun",
                MyDummyPersistentJob.onAddedCountWhenOnRun, equalTo(1));
    }

    public static class MyDummyPersistentJob extends Job {
        private static CountDownLatch persistentJobLatch;
        private static int persistentOnAdded = 0;
        private static int onAddedCountWhenOnRun = -1;

        protected MyDummyPersistentJob(int priority) {
            super(new Params(priority).persist());
        }

        @Override
        public void onAdded() {
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                //
            }
            persistentOnAdded ++;
        }

        @Override
        public void onRun() throws Throwable {
            onAddedCountWhenOnRun = persistentOnAdded;
            persistentJobLatch.countDown();
        }

        @Override
        protected void onCancel() {

        }

        @Override
        protected boolean shouldReRunOnThrowable(Throwable throwable) {
            return true;
        }
    }

    private static class MyDummyJob extends DummyLatchJob {
        int onAddedCntWhenRun = -1;

        protected MyDummyJob(Params params, CountDownLatch latch) {
            super(params, latch);
        }

        @Override
        public void onAdded() {
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            super.onAdded();
        }

        @Override
        public void onRun() throws Throwable {
            onAddedCntWhenRun = super.getOnAddedCnt();
            super.onRun();
        }
    }
}
