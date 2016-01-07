package com.birbit.android.jobqueue;

import com.birbit.android.jobqueue.messaging.MessageFactory;
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

public class JobManager2 {
    private final Chef chef;
    private final PriorityMessageQueue messageQueue;
    private final MessageFactory messageFactory;
    private Thread chefThread;
    public JobManager2(Configuration configuration) {
        messageQueue = new PriorityMessageQueue(configuration.timer());
        messageFactory = new MessageFactory();
        chef = new Chef(configuration, messageQueue, messageFactory);
        chefThread = new Thread(chef, "job-manager");
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

    public void destroy() {
        JqLog.d("destroying job queue");
        stopAndWaitUntilConsumersAreFinished();
        CommandMessage message = messageFactory.obtain(CommandMessage.class);
        message.set(CommandMessage.QUIT);
        messageQueue.post(message);
        chef.callbackManager.destroy();
    }

    public void stopAndWaitUntilConsumersAreFinished() {
        final CountDownLatch latch = new CountDownLatch(1);
        chef.consumerController.addNoConsumersListener(new Runnable() {
            @Override
            public void run() {
                latch.countDown();
                chef.consumerController.removeNoConsumersListener(this);
            }
        });
        stop();
        if(chef.consumerController.totalConsumers == 0) {
            return;
        }
        try {
            latch.await();
        } catch (InterruptedException ignored) {
        }
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
        chef.addCallback(callback);
    }

    public boolean removeCallback(JobManagerCallback callback) {
        return chef.removeCallback(callback);
    }

    public long addJob(Job job) {
        final CountDownLatch latch = new CountDownLatch(1);
        final String uuid = job.getUuid();
        addCallback(new JobManagerCallbackAdapter() {
            @Override
            public void onJobAdded(Job job) {
                if (uuid.equals(job.getUuid())) {
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
        return 1;
    }

    public void addJobInBackground(Job job, AsyncAddCallback callback) {
        final CountDownLatch latch = new CountDownLatch(1);
        final String uuid = job.getUuid();
        addCallback(new JobManagerCallbackAdapter() {
            @Override
            public void onJobAdded(Job job) {
                if (uuid.equals(job.getUuid())) {
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
        final CountDownLatch latch = new CountDownLatch(1);
        final int[] result = new int[1];
        message.set(PublicQueryMessage.COUNT, new IntCallback() {
            @Override
            public void onResult(int count) {
                result[0] = count;
                latch.countDown();
            }
        });
        messageQueue.post(message);
        try {
            latch.await();
        } catch (InterruptedException ignored) {
        }
        return result[0];
    }

    public int countReadyJobs() {
        PublicQueryMessage message = messageFactory.obtain(PublicQueryMessage.class);
        final CountDownLatch latch = new CountDownLatch(1);
        final int[] result = new int[1];
        message.set(PublicQueryMessage.COUNT_READY, new IntCallback() {
            @Override
            public void onResult(int count) {
                result[0] = count;
                latch.countDown();
            }
        });
        messageQueue.post(message);
        try {
            latch.await();
        } catch (InterruptedException ignored) {
        }
        return result[0];
    }

    public JobStatus getJobStatus(long id, boolean isPersistent) {
        if (true) {
            throw new UnsupportedOperationException("use uuid version");
        }
        PublicQueryMessage message = messageFactory.obtain(PublicQueryMessage.class);
        final CountDownLatch latch = new CountDownLatch(1);
        final JobStatus[] result = new JobStatus[1];
        message.set(PublicQueryMessage.JOB_STATUS, id, isPersistent, new IntCallback() {
            @Override
            public void onResult(int index) {
                result[0] = JobStatus.values()[index];
                latch.countDown();
            }
        });
        messageQueue.post(message);
        try {
            latch.await();
        } catch (InterruptedException ignored) {
        }
        return result[0];
    }

    public void clear() {
        final CountDownLatch latch = new CountDownLatch(1);
        PublicQueryMessage message = messageFactory.obtain(PublicQueryMessage.class);
        message.set(PublicQueryMessage.CLEAR, new IntCallback() {
            @Override
            public void onResult(int result) {
                latch.countDown();
            }
        });
        messageQueue.post(message);
        try {
            latch.await();
        } catch (InterruptedException ignored) {
        }
    }
}
