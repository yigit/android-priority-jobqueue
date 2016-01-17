package com.birbit.android.jobqueue;

import com.birbit.android.jobqueue.messaging.MessageFactory;
import com.birbit.android.jobqueue.messaging.MessageQueue;
import com.birbit.android.jobqueue.messaging.PriorityMessageQueue;
import com.birbit.android.jobqueue.messaging.message.AddJobMessage;
import com.birbit.android.jobqueue.messaging.message.CancelMessage;
import com.birbit.android.jobqueue.messaging.message.CommandMessage;
import com.birbit.android.jobqueue.messaging.message.PublicQueryMessage;
import com.path.android.jobqueue.AsyncAddCallback;
import com.path.android.jobqueue.CancelResult;
import com.path.android.jobqueue.Job;
import com.path.android.jobqueue.JobStatus;
import com.path.android.jobqueue.TagConstraint;
import com.path.android.jobqueue.callback.JobManagerCallback;
import com.path.android.jobqueue.callback.JobManagerCallbackAdapter;
import com.path.android.jobqueue.config.Configuration;
import com.path.android.jobqueue.log.JqLog;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class JobManager2 {
    final JobManagerThread jobManagerThread;
    private final PriorityMessageQueue messageQueue;
    private final MessageFactory messageFactory;
    private Thread chefThread;
    public JobManager2(Configuration configuration) {
        messageQueue = new PriorityMessageQueue(configuration.timer());
        messageFactory = new MessageFactory();
        jobManagerThread = new JobManagerThread(configuration, messageQueue, messageFactory);
        chefThread = new Thread(jobManagerThread, "job-manager");
        chefThread.start();
    }

    public void start() {
        PublicQueryMessage message = messageFactory.obtain(PublicQueryMessage.class);
        message.set(PublicQueryMessage.START, null);
        messageQueue.post(message);
    }

    public void stop() {
        PublicQueryMessage message = messageFactory.obtain(PublicQueryMessage.class);
        message.set(PublicQueryMessage.STOP, null);
        messageQueue.post(message);
    }

    public int getActiveConsumerCount() {
        PublicQueryMessage message = messageFactory.obtain(PublicQueryMessage.class);
        message.set(PublicQueryMessage.ACTIVE_CONSUMER_COUNT, null);
        return new PublicQueryFuture(messageQueue, message).getSafe();
    }

    public void destroy() {
        JqLog.d("destroying job queue");
        stopAndWaitUntilConsumersAreFinished();
        CommandMessage message = messageFactory.obtain(CommandMessage.class);
        message.set(CommandMessage.QUIT);
        messageQueue.post(message);
        jobManagerThread.callbackManager.destroy();
    }

    public void stopAndWaitUntilConsumersAreFinished() {
        waitUntilConsumersAreFinished(true);
    }

    public void waitUntilConsumersAreFinished() {
        waitUntilConsumersAreFinished(false);
    }

    private void waitUntilConsumersAreFinished(boolean stop) {
        final CountDownLatch latch = new CountDownLatch(1);
        jobManagerThread.consumerController.addNoConsumersListener(new Runnable() {
            @Override
            public void run() {
                latch.countDown();
                jobManagerThread.consumerController.removeNoConsumersListener(this);
            }
        });
        if (stop) {
            stop();
        }
        if(jobManagerThread.consumerController.totalConsumers == 0) {
            return;
        }
        try {
            latch.await();
        } catch (InterruptedException ignored) {
        }
        PublicQueryMessage pm = messageFactory.obtain(PublicQueryMessage.class);
        pm.set(PublicQueryMessage.CLEAR, null);
        new PublicQueryFuture(jobManagerThread.callbackManager.messageQueue, pm).getSafe();
    }

    public void addJobInBackground(Job job) {
        AddJobMessage message = messageFactory.obtain(AddJobMessage.class);
        message.setJob(job);
        messageQueue.post(message);
    }

    public void cancelJobsInBackground(final CancelResult.AsyncCancelCallback cancelCallback,
            final TagConstraint constraint, final String... tags) {
        CancelMessage message = messageFactory.obtain(CancelMessage.class);
        message.setCallback(cancelCallback);
        message.setConstraint(constraint);
        message.setTags(tags);
        messageQueue.post(message);
    }

    private void assertRightThread() {
        if (Thread.currentThread() == chefThread) {
            // TODO we can allow a configuration to call callbacks in another thread. In that case,
            // we'll have to lock on onAdded calls as well
            throw new RuntimeException("Cannot call sync job manager methods in its runner thread."
                    + "Use async instead");
        }
    }

    public void addCallback(JobManagerCallback callback) {
        jobManagerThread.addCallback(callback);
    }

    public boolean removeCallback(JobManagerCallback callback) {
        return jobManagerThread.removeCallback(callback);
    }

    public String addJob(Job job) {
        final CountDownLatch latch = new CountDownLatch(1);
        final String uuid = job.getId();
        addCallback(new JobManagerCallbackAdapter() {
            @Override
            public void onJobAdded(Job job) {
                if (uuid.equals(job.getId())) {
                    latch.countDown();
                    removeCallback(this);
                }
            }
        });
        addJobInBackground(job);
        try {
            latch.await();
        } catch (InterruptedException ignored) {

        }
        return job.getId();
    }

    public void addJobInBackground(Job job, AsyncAddCallback callback) {
        final CountDownLatch latch = new CountDownLatch(1);
        final String uuid = job.getId();
        addCallback(new JobManagerCallbackAdapter() {
            @Override
            public void onJobAdded(Job job) {
                if (uuid.equals(job.getId())) {
                    latch.countDown();
                    removeCallback(this);
                }
            }
        });
        addJobInBackground(job);
        try {
            latch.await();
        } catch (InterruptedException ignored) {

        }
        callback.onAdded();
    }

    public CancelResult cancelJobs(TagConstraint constraint, String... tags) {
        final CountDownLatch latch = new CountDownLatch(1);
        final CancelResult[] result = new CancelResult[1];
        CancelResult.AsyncCancelCallback myCallback = new CancelResult.AsyncCancelCallback() {
            @Override
            public void onCancelled(CancelResult cancelResult) {
                result[0] = cancelResult;
                latch.countDown();
            }
        };
        CancelMessage message = messageFactory.obtain(CancelMessage.class);
        message.setConstraint(constraint);
        message.setTags(tags);
        message.setCallback(myCallback);
        messageQueue.post(message);
        try {
            latch.await();
        } catch (InterruptedException ignored) {
        }
        return result[0];
    }

    public int count() {
        PublicQueryMessage message = messageFactory.obtain(PublicQueryMessage.class);
        message.set(PublicQueryMessage.COUNT, null);
        return new PublicQueryFuture(messageQueue, message).getSafe();
    }

    public int countReadyJobs() {
        PublicQueryMessage message = messageFactory.obtain(PublicQueryMessage.class);
        message.set(PublicQueryMessage.COUNT_READY, null);
        return new PublicQueryFuture(messageQueue, message).getSafe();
    }

    public JobStatus getJobStatus(String id, boolean isPersistent) {
        PublicQueryMessage message = messageFactory.obtain(PublicQueryMessage.class);
        message.set(PublicQueryMessage.JOB_STATUS, id, isPersistent, null);
        Integer status = new PublicQueryFuture(messageQueue, message).getSafe();
        return JobStatus.values()[status];
    }

    public void clear() {
        final PublicQueryMessage message = messageFactory.obtain(PublicQueryMessage.class);
        message.set(PublicQueryMessage.CLEAR, null);
        new PublicQueryFuture(messageQueue, message).getSafe();
    }

    void internalRunInJobManagerThread(final Runnable runnable) throws Throwable {
        final Throwable[] error = new Throwable[1];
        final PublicQueryMessage message = messageFactory.obtain(PublicQueryMessage.class);
        message.set(PublicQueryMessage.INTERNAL_RUNNABLE, null);
        new PublicQueryFuture(messageQueue, message) {
            @Override
            public void onResult(int result) { // this is hacky but allright
                try {
                    runnable.run();
                } catch (Throwable t) {
                    error[0] = t;
                }
                super.onResult(result);
            }
        }.getSafe();
        if (error[0] != null) {
            throw error[0];
        }
    }

    static class PublicQueryFuture implements Future<Integer>,IntCallback {
        final MessageQueue messageQueue;
        volatile Integer result = null;
        final CountDownLatch latch = new CountDownLatch(1);
        final PublicQueryMessage message;

        public PublicQueryFuture(MessageQueue messageQueue, PublicQueryMessage message) {
            this.messageQueue = messageQueue;
            this.message = message;
            message.setCallback(this);
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            return false;
        }

        @Override
        public boolean isCancelled() {
            return false;
        }

        @Override
        public boolean isDone() {
            return latch.getCount() == 0;
        }

        public Integer getSafe() {
            try {
                return get();
            } catch (Throwable t) {
                JqLog.e(t, "message is not complete");
            }
            throw new RuntimeException("cannot get the result of the JobManager query");
        }

        @Override
        public Integer get() throws InterruptedException, ExecutionException {
            messageQueue.post(message);
            latch.await();
            return result;
        }

        @Override
        public Integer get(long timeout, TimeUnit unit)
                throws InterruptedException, ExecutionException, TimeoutException {
            messageQueue.post(message);
            latch.await(timeout, unit);
            return result;
        }

        @Override
        public void onResult(int result) {
            this.result = result;
            latch.countDown();
        }
    }
}
