package com.path.android.jobqueue.test.jobmanager;

import android.util.Log;
import com.path.android.jobqueue.JobManager;
import com.path.android.jobqueue.Params;
import com.path.android.jobqueue.config.Configuration;
import com.path.android.jobqueue.test.jobs.DummyJob;
import static org.hamcrest.CoreMatchers.*;
import org.hamcrest.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;

import java.util.Collection;
import java.util.LinkedList;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@RunWith(RobolectricTestRunner.class)
public class MultiThreadTest extends JobManagerTestBase {
    private static AtomicInteger multiThreadedJobCounter;
    @Test
    public void testMultiThreaded() throws Exception {
        multiThreadedJobCounter = new AtomicInteger(0);
        final JobManager jobManager = createJobManager(new Configuration.Builder(Robolectric.application)
            .loadFactor(3).maxConsumerCount(10));
        int limit = 200;
        ExecutorService executor = new ThreadPoolExecutor(20, 20, 60, TimeUnit.SECONDS, new ArrayBlockingQueue<Runnable>(limit));
        Collection<Future<?>> futures = new LinkedList<Future<?>>();
        for(int i = 0; i < limit; i++) {
            final int id = i;
            futures.add(executor.submit(new Runnable() {
                @Override
                public void run() {
                    final boolean persistent = Math.round(Math.random()) % 2 == 0;
                    boolean requiresNetwork = Math.round(Math.random()) % 2 == 0;
                    int priority = (int) (Math.round(Math.random()) % 10);
                    multiThreadedJobCounter.incrementAndGet();
                    jobManager.addJob(new DummyJobForMultiThread(id, new Params(priority).setRequiresNetwork(requiresNetwork).setPersistent(persistent)));
                }
            }));
        }
        for (Future<?> future:futures) {
            future.get();
        }
        Log.d("TAG", "added all jobs");
        //wait until all jobs are added
        long start = System.nanoTime();
        long timeLimit = JobManager.NS_PER_MS * 20000;//20 seconds
        while(System.nanoTime() - start < timeLimit && multiThreadedJobCounter.get() != 0) {
            Thread.sleep(1000);
        }
        Log.d("TAG", "did we reach timeout? " + (System.nanoTime() - start >= timeLimit));

        MatcherAssert.assertThat("jobmanager count should be 0",
                jobManager.count(), equalTo(0));

        MatcherAssert.assertThat("multiThreadedJobCounter should be 0",
                multiThreadedJobCounter.get(), equalTo(0));

    }
    public static class DummyJobForMultiThread extends DummyJob {
        private int id;
        private DummyJobForMultiThread(int id, Params params) {
            super(params);
            this.id = id;
        }

        @Override
        public void onRun() throws Throwable {
            super.onRun();
            int remaining = multiThreadedJobCounter.decrementAndGet();
            //take some time
            Thread.sleep((long) (Math.random() * 1000));
            //throw exception w/ small change
            if(Math.random() < .1) {
                throw new Exception("decided to die, will retry");
            }
            Log.d("DummyJobForMultiThread", "persistent:" + isPersistent() + ", requires network:" + requiresNetwork() + ", running " + id + ", remaining: " + remaining);
        }

        @Override
        protected boolean shouldReRunOnThrowable(Throwable throwable) {
            multiThreadedJobCounter.incrementAndGet();
            return true;
        }
    };
}
