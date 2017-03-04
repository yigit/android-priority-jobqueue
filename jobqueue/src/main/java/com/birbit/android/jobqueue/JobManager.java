package com.birbit.android.jobqueue;

import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;

import com.birbit.android.jobqueue.callback.JobManagerCallback;
import com.birbit.android.jobqueue.callback.JobManagerCallbackAdapter;
import com.birbit.android.jobqueue.config.Configuration;
import com.birbit.android.jobqueue.log.JqLog;
import com.birbit.android.jobqueue.messaging.Message;
import com.birbit.android.jobqueue.messaging.MessageFactory;
import com.birbit.android.jobqueue.messaging.MessageQueue;
import com.birbit.android.jobqueue.messaging.PriorityMessageQueue;
import com.birbit.android.jobqueue.messaging.message.AddJobMessage;
import com.birbit.android.jobqueue.messaging.message.CancelMessage;
import com.birbit.android.jobqueue.messaging.message.CommandMessage;
import com.birbit.android.jobqueue.messaging.message.PublicQueryMessage;
import com.birbit.android.jobqueue.messaging.message.SchedulerMessage;
import com.birbit.android.jobqueue.scheduling.Scheduler;
import com.birbit.android.jobqueue.scheduling.SchedulerConstraint;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class JobManager {
    public static final long NS_PER_MS = 1000000;
    public static final long NOT_RUNNING_SESSION_ID = Long.MIN_VALUE;
    public static final long NOT_DELAYED_JOB_DELAY = Params.NEVER;
    public static final long NETWORK_CHECK_INTERVAL = TimeUnit.MILLISECONDS.toNanos(10000);
    /**
     * The min delay in MS which will trigger usage of JobScheduler.
     * If a job is added with a delay in less than this value, JobManager will not use the scheduler
     * to wake up the application.
     */
    public static final long MIN_DELAY_TO_USE_SCHEDULER_IN_MS = 1000 * 30;

    final JobManagerThread jobManagerThread;
    private final PriorityMessageQueue messageQueue;
    private final MessageFactory messageFactory;
    @SuppressWarnings("FieldCanBeLocal")
    private Thread chefThread;
    @Nullable
    // this is the scheduler that was given in the configuration, not necessarily the scheduler
    // used by the JobManagerThread.
    private Scheduler scheduler;

    /**
     * Creates a JobManager with the given configuration
     *
     * @param configuration The configuration to be used for the JobManager
     *
     * @see com.birbit.android.jobqueue.config.Configuration.Builder
     */
    public JobManager(Configuration configuration) {
        messageFactory = new MessageFactory();
        messageQueue = new PriorityMessageQueue(configuration.getTimer(), messageFactory);
        jobManagerThread = new JobManagerThread(configuration, messageQueue, messageFactory);
        chefThread = new Thread(jobManagerThread, "job-manager");
        if (configuration.getScheduler() != null) {
            scheduler = configuration.getScheduler();
            Scheduler.Callback callback = createSchedulerCallback();
            configuration.getScheduler().init(configuration.getAppContext(), callback);
        }
        chefThread.start();
    }

    /**
     * Returns the main thread of the JobManager.
     * <p>
     * This is the thread where the Jobs' onAdded methods are run.
     *
     * @return The thread used by the JobManager for its own logic.
     */
    @VisibleForTesting
    public Thread getJobManagerExecutionThread() {
        return chefThread;
    }

    /**
     * The scheduler that was given to this JobManager when it was initialized.
     * <p>
     * The scheduler is used by the JobService to communicate with the JobManager.
     *
     * @return The scheduler that was given to this JobManager or null if it does not exist
     */
    @Nullable
    public Scheduler getScheduler() {
        return scheduler;
    }

    private Scheduler.Callback createSchedulerCallback() {
        return new Scheduler.Callback() {
            @Override
            public boolean start(SchedulerConstraint constraint) {
                dispatchSchedulerStart(constraint);
                return true;
            }

            @Override
            public boolean stop(SchedulerConstraint constraint) {
                dispatchSchedulerStop(constraint);
                // always return false to avoid blocking the queue
                return false;
            }
        };
    }

    private void dispatchSchedulerStart(SchedulerConstraint constraint) {
        SchedulerMessage message = messageFactory.obtain(SchedulerMessage.class);
        message.set(SchedulerMessage.START, constraint);
        messageQueue.post(message);
    }

    private void dispatchSchedulerStop(SchedulerConstraint constraint) {
        SchedulerMessage message = messageFactory.obtain(SchedulerMessage.class);
        message.set(PublicQueryMessage.START, constraint);
        messageQueue.post(message);
    }

    /**
     * Starts the JobManager if it is not already running.
     *
     * @see #stop()
     */
    public void start() {
        PublicQueryMessage message = messageFactory.obtain(PublicQueryMessage.class);
        message.set(PublicQueryMessage.START, null);
        messageQueue.post(message);
    }

    /**
     * Stops the JobManager. Currently running Jobs will continue to run but no new Jobs will be
     * run until restarted.
     *
     * @see #start()
     */
    public void stop() {
        PublicQueryMessage message = messageFactory.obtain(PublicQueryMessage.class);
        message.set(PublicQueryMessage.STOP, null);
        messageQueue.post(message);
    }

    /**
     * Returns the number of consumer threads that are currently running Jobs. This number includes
     * consumer threads that are currently idle.
     * <p>
     * You cannot call this method on the main thread because it may potentially block it for a long
     * time.
     * @return The number of consumer threads
     */
    public int getActiveConsumerCount() {
        assertNotInMainThread();
        assertNotInJobManagerThread("Cannot call sync methods in JobManager's callback thread.");
        PublicQueryMessage message = messageFactory.obtain(PublicQueryMessage.class);
        message.set(PublicQueryMessage.ACTIVE_CONSUMER_COUNT, null);
        return new IntQueryFuture<>(messageQueue, message).getSafe();
    }

    /**
     * Destroys the JobManager. You cannot make any calls to this JobManager after this call.
     * Useful to be called after your tests.
     *
     * @see #stopAndWaitUntilConsumersAreFinished()
     */
    public void destroy() {
        JqLog.d("destroying job queue");
        stopAndWaitUntilConsumersAreFinished();
        CommandMessage message = messageFactory.obtain(CommandMessage.class);
        message.set(CommandMessage.QUIT);
        messageQueue.post(message);
        jobManagerThread.callbackManager.destroy();
    }

    /**
     * Stops the JobManager and waits until all currently running Jobs are complete (or failed).
     * Useful to be called in your tests.
     * <p>
     * You cannot call this method on the main thread because it may potentially block it for a long
     * time.
     *
     * @see #destroy()
     */
    public void stopAndWaitUntilConsumersAreFinished() {
        waitUntilConsumersAreFinished(true);
    }

    /**
     * Waits until all consumers are destroyed. If min consumer count is NOT 0, this method will
     * never return.
     * <p>
     * You cannot call this method on the main thread because it may potentially block it for a long
     * time.
     */
    public void waitUntilConsumersAreFinished() {
        waitUntilConsumersAreFinished(false);
    }

    private void waitUntilConsumersAreFinished(boolean stop) {
        assertNotInMainThread();
        final CountDownLatch latch = new CountDownLatch(1);
        JqLog.v("adding no consumers listener.");
        jobManagerThread.consumerManager.addNoConsumersListener(new Runnable() {
            @Override
            public void run() {
                JqLog.v("received no consumers callback");
                latch.countDown();
                jobManagerThread.consumerManager.removeNoConsumersListener(this);
            }
        });
        if (stop) {
            stop();
        }
        if(jobManagerThread.consumerManager.getWorkerCount() == 0) {
            return;
        }
        try {
            latch.await();
        } catch (InterruptedException ignored) {
        }
        PublicQueryMessage pm = messageFactory.obtain(PublicQueryMessage.class);
        pm.set(PublicQueryMessage.CLEAR, null);
        new IntQueryFuture<>(jobManagerThread.callbackManager.messageQueue, pm).getSafe();
    }

    /**
     * Adds a Job to the JobManager. This method instantly returns and does not wait until the Job
     * is added. You should always prefer this method over {@link #addJob(Job)}.
     *
     * @param job The Job to be added
     *
     * @see #addJobInBackground(Job, AsyncAddCallback)
     * @see #addJob(Job)
     */
    public void addJobInBackground(Job job) {
        AddJobMessage message = messageFactory.obtain(AddJobMessage.class);
        message.setJob(job);
        messageQueue.post(message);
    }

    /**
     * Cancels the Jobs that match the given criteria. If a Job that matches the criteria is
     * currently running, JobManager waits until it finishes its {@link Job#onRun()} method before
     * calling the callback.
     *
     * @param cancelCallback The callback to call once cancel is handled
     * @param constraint The constraint to be used to match tags
     * @param tags The list of tags
     */
    public void cancelJobsInBackground(final CancelResult.AsyncCancelCallback cancelCallback,
            final TagConstraint constraint, final String... tags) {
        if (constraint == null) {
            throw new IllegalArgumentException("must provide a TagConstraint");
        }
        CancelMessage message = messageFactory.obtain(CancelMessage.class);
        message.setCallback(cancelCallback);
        message.setConstraint(constraint);
        message.setTags(tags);
        messageQueue.post(message);
    }

    /**
     * Adds a JobManagerCallback to observe this JobManager.
     *
     * @param callback The callback to be added
     */
    public void addCallback(JobManagerCallback callback) {
        jobManagerThread.addCallback(callback);
    }

    /**
     * Removes the JobManagerCallback from the callbacks list. This method is safe to be called
     * inside any method of the JobManagerCallback.
     *
     * @param callback The callback to be removed
     *
     * @return true if the callback is removed, false otherwise (if it did not exist).
     */
    public boolean removeCallback(JobManagerCallback callback) {
        return jobManagerThread.removeCallback(callback);
    }

    /**
     * Adds the Job to the JobManager and waits until the add is handled.
     * <p>
     * You cannot call this method on the main thread because it may potentially block it for a long
     * time.
     *
     * Even if you are not on the main thread, you should prefer using
     * {@link #addJobInBackground(Job)} or {@link #addJobInBackground(Job, AsyncAddCallback)} if
     * you don't need to block your thread until the Job is actually added.
     *
     * @param job The Job to be added
     *
     * @see #addJobInBackground(Job)
     * @see #addJobInBackground(Job, AsyncAddCallback)
     */
    public void addJob(Job job) {
        assertNotInMainThread("Cannot call this method on main thread. Use addJobInBackground "
                + "instead.");
        assertNotInJobManagerThread("Cannot call sync methods in JobManager's callback thread." +
                "Use addJobInBackground instead");
        final CountDownLatch latch = new CountDownLatch(1);
        final String uuid = job.getId();
        addCallback(new JobManagerCallbackAdapter() {
            @Override
            public void onJobAdded(@NonNull Job job) {
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
    }

    /**
     * Adds a Job in a background thread and calls the provided callback once the Job is added
     * to the JobManager.
     *
     * @param job The Job to be added
     * @param callback The callback to be invoked once Job is saved in the JobManager's queues
     */
    public void addJobInBackground(Job job, final AsyncAddCallback callback) {
        if (callback == null) {
            addJobInBackground(job);
            return;
        }
        final String uuid = job.getId();
        addCallback(new JobManagerCallbackAdapter() {
            @Override
            public void onJobAdded(@NonNull Job job) {
                if (uuid.equals(job.getId())) {
                    try {
                        callback.onAdded();
                    } finally {
                        removeCallback(this);
                    }
                }
            }
        });
        addJobInBackground(job);
    }

    /**
     * Cancels jobs that match the given criteria. This method blocks until the cancellation is
     * handled, which might be a long time if a Job that matches the given criteria is currently
     * running. Consider using
     * {@link #cancelJobsInBackground(CancelResult.AsyncCancelCallback, TagConstraint, String...)}
     * if possible.
     * <p>
     * You cannot call this method on the main thread because it may potentially block it for a long
     * time.
     *
     * @param constraint The constraints to be used for tags
     * @param tags The list of tags
     *
     * @return A cancel result that has the list of cancelled and failed to cancel Jobs. A job
     * might fail to cancel if it already started before cancel request is handled.
     */
    public CancelResult cancelJobs(TagConstraint constraint, String... tags) {
        assertNotInMainThread("Cannot call this method on main thread. Use cancelJobsInBackground"
                + " instead");
        assertNotInJobManagerThread("Cannot call this method on JobManager's thread. Use" +
                "cancelJobsInBackground instead");
        if (constraint == null) {
            throw new IllegalArgumentException("must provide a TagConstraint");
        }
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

    /**
     * Returns the number of jobs in the JobManager. This number does not include jobs that are
     * currently running.
     * <p>
     * You cannot call this method on the main thread because it may potentially block it for a long
     * time.
     *
     * @return The number of jobs that are waiting to be run
     */
    public int count() {
        assertNotInMainThread();
        assertNotInJobManagerThread("Cannot call count sync method in JobManager's thread");
        PublicQueryMessage message = messageFactory.obtain(PublicQueryMessage.class);
        message.set(PublicQueryMessage.COUNT, null);
        return new IntQueryFuture<>(messageQueue, message).getSafe();
    }

    /**
     * Returns the number of jobs that are ready to be executed but waiting in the queue.
     * <p>
     * You cannot call this method on the main thread because it may potentially block it for a long
     * time.
     * @return The number of jobs that are ready to be executed but waiting in the queue.
     */
    public int countReadyJobs() {
        assertNotInMainThread();
        assertNotInJobManagerThread("Cannot call countReadyJobs sync method on JobManager's thread");
        PublicQueryMessage message = messageFactory.obtain(PublicQueryMessage.class);
        message.set(PublicQueryMessage.COUNT_READY, null);
        return new IntQueryFuture<>(messageQueue, message).getSafe();
    }

    /**
     * Returns the current status of a given job
     * <p>
     * You cannot call this method on the main thread because it may potentially block it for a long
     * time.
     * @param id The id of the job ({@link Job#getId()})
     *
     * @return The current status of the Job
     */
    public JobStatus getJobStatus(String id) {
        assertNotInMainThread();
        assertNotInJobManagerThread("Cannot call getJobStatus on JobManager's thread");
        PublicQueryMessage message = messageFactory.obtain(PublicQueryMessage.class);
        message.set(PublicQueryMessage.JOB_STATUS, id, null);
        Integer status = new IntQueryFuture<>(messageQueue, message).getSafe();
        return JobStatus.values()[status];
    }

    /**
     * Clears all waiting Jobs in the JobManager. Note that this won't touch any job that is
     * currently running.
     * <p>
     * You cannot call this method on the main thread because it may potentially block it for a long
     * time.
     */
    public void clear() {
        assertNotInMainThread();
        assertNotInJobManagerThread("Cannot call clear on JobManager's thread");
        final PublicQueryMessage message = messageFactory.obtain(PublicQueryMessage.class);
        message.set(PublicQueryMessage.CLEAR, null);
        new IntQueryFuture<>(messageQueue, message).getSafe();
    }

    void internalRunInJobManagerThread(final Runnable runnable) throws Throwable {
        final Throwable[] error = new Throwable[1];
        final PublicQueryMessage message = messageFactory.obtain(PublicQueryMessage.class);
        message.set(PublicQueryMessage.INTERNAL_RUNNABLE, null);
        new IntQueryFuture<PublicQueryMessage>(messageQueue, message) {
            @Override
            public void onResult(int result) { // this is hacky but alright
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

    private void assertNotInMainThread() {
        assertNotInMainThread("Cannot call this method on main thread.");
    }

    private void assertNotInMainThread(String message) {
        if (Looper.getMainLooper().getThread() == Thread.currentThread()) {
            throw new WrongThreadException(message);
        }
    }

    private void assertNotInJobManagerThread(String message) {
        if (Thread.currentThread() == chefThread) {
            throw new WrongThreadException(message);
        }
    }

    @SuppressWarnings("WeakerAccess")
    static class IntQueryFuture<T extends Message & IntCallback.MessageWithCallback>
            implements Future<Integer>,IntCallback {
        final MessageQueue messageQueue;
        volatile Integer result = null;
        final CountDownLatch latch = new CountDownLatch(1);
        final T message;

        IntQueryFuture(MessageQueue messageQueue, T message) {
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

        Integer getSafe() {
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
        public Integer get(long timeout, @NonNull TimeUnit unit)
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
