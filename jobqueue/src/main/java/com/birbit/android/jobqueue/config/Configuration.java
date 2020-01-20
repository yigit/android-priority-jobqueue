package com.birbit.android.jobqueue.config;

import android.content.Context;
import android.net.ConnectivityManager;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.birbit.android.jobqueue.DefaultQueueFactory;
import com.birbit.android.jobqueue.JobQueue;
import com.birbit.android.jobqueue.QueueFactory;
import com.birbit.android.jobqueue.di.DependencyInjector;
import com.birbit.android.jobqueue.log.CustomLogger;
import com.birbit.android.jobqueue.log.JqLog;
import com.birbit.android.jobqueue.network.NetworkUtil;
import com.birbit.android.jobqueue.network.NetworkUtilImpl;
import com.birbit.android.jobqueue.persistentQueue.sqlite.SqliteJobQueue;
import com.birbit.android.jobqueue.scheduling.Scheduler;
import com.birbit.android.jobqueue.timer.SystemTimer;
import com.birbit.android.jobqueue.timer.Timer;

import java.util.concurrent.ThreadFactory;
import java.util.regex.Pattern;

/**
 * {@link com.birbit.android.jobqueue.JobManager} configuration object
 */
@SuppressWarnings("WeakerAccess")
public class Configuration {
    /**
     * The default id for a Job. If you have multiple JobManagers, you should set this value via
     * {@link Builder#id(String)}
     */
    public static final String DEFAULT_ID = "default_job_manager";
    /**
     * The default timeout for an idle thread before it is destroyed
     */
    public static final int DEFAULT_THREAD_KEEP_ALIVE_SECONDS = 15;
    /**
     * The default number of jobs per thread before JobManager creates a new one
     */
    public static final int DEFAULT_LOAD_FACTOR_PER_CONSUMER = 3;
    /**
     * The default max number of consumers that will be created by the JobManager
     */
    public static final int MAX_CONSUMER_COUNT = 5;
    /**
     * The default min number of consumers that will be kept alive by the JobManager
     */
    public static final int MIN_CONSUMER_COUNT = 0;
    /**
     * The default priority for new job consumers ({@code Thread.NORM_PRIORITY}).
     */
    public static final int DEFAULT_THREAD_PRIORITY = Thread.NORM_PRIORITY;

    String id = DEFAULT_ID;
    int maxConsumerCount = MAX_CONSUMER_COUNT;
    int minConsumerCount = MIN_CONSUMER_COUNT;
    int consumerKeepAlive = DEFAULT_THREAD_KEEP_ALIVE_SECONDS;
    int loadFactor = DEFAULT_LOAD_FACTOR_PER_CONSUMER;
    Context appContext;
    QueueFactory queueFactory;
    DependencyInjector dependencyInjector;
    NetworkUtil networkUtil;
    CustomLogger customLogger = new JqLog.ErrorLogger();
    Timer timer;
    Scheduler scheduler;
    boolean inTestMode = false;
    boolean resetDelaysOnRestart = false;
    int threadPriority = DEFAULT_THREAD_PRIORITY;
    boolean batchSchedulerRequests = true;
    ThreadFactory threadFactory = null;

    private Configuration(){
        //use builder instead
    }

    @NonNull
    public Context getAppContext() {
        return appContext;
    }

    @NonNull
    public String getId() {
        return id;
    }

    public boolean batchSchedulerRequests() {
        return batchSchedulerRequests;
    }

    @NonNull
    public QueueFactory getQueueFactory() {
        return queueFactory;
    }

    @Nullable
    public DependencyInjector getDependencyInjector() {
        return dependencyInjector;
    }

    public int getConsumerKeepAlive() {
        return consumerKeepAlive;
    }

    @NonNull
    public NetworkUtil getNetworkUtil() {
        return networkUtil;
    }

    public int getMaxConsumerCount() {
        return maxConsumerCount;
    }

    public int getMinConsumerCount() {
        return minConsumerCount;
    }

    @Nullable
    public CustomLogger getCustomLogger() {
        return customLogger;
    }

    public int getLoadFactor() {
        return loadFactor;
    }

    public boolean isInTestMode() {
        return inTestMode;
    }

    @NonNull
    public Timer getTimer() {
        return timer;
    }

    public boolean resetDelaysOnRestart() {
        return resetDelaysOnRestart;
    }

    @Nullable
    public Scheduler getScheduler() {
        return scheduler;
    }

    public int getThreadPriority() {
        return threadPriority;
    }

    @Nullable
    public ThreadFactory getThreadFactory() {
        return threadFactory;
    }

    @SuppressWarnings("unused")
    public static final class Builder {
        private Pattern idRegex = Pattern.compile("^([A-Za-z]|[0-9]|_|-)+$");
        private Configuration configuration;

        public Builder(@NonNull Context context) {
            this.configuration = new Configuration();
            this.configuration.appContext = context.getApplicationContext();
        }

        /**
         * Provide and ID for this job manager to be used while creating persistent queue. it is
         * necessary if you are going to create multiple instances of it.
         * default id is {@link #DEFAULT_ID}
         * <p>
         * You can only use alphanumeric characters, <code>-</code> and <code>_</code> .
         * @param id if you have multiple instances of job manager, you should provide an id to
         *           distinguish their persistent files.
         * @return This Configuration for easy chaining
         */
        @NonNull
        public Builder id(@NonNull String id) {
            //noinspection ConstantConditions
            if (id == null || !idRegex.matcher(id).matches()) {
                throw new IllegalArgumentException("id cannot be null or empty and can only include"
                        + " alphanumeric characters, - or _ .");
            }
            configuration.id = id;
            return this;
        }

        /**
         * When JobManager runs out of `ready` jobs, it will keep consumers alive for this duration.
         * It defaults to {@link #DEFAULT_THREAD_KEEP_ALIVE_SECONDS}
         * @param keepAlive in seconds
         * @return This Configuration for easy chaining
         */
        @NonNull
        public Builder consumerKeepAlive(int keepAlive) {
            configuration.consumerKeepAlive = keepAlive;
            return this;
        }

        /**
         * JobManager 1.x versions used to clear delay for existing jobs when the application is
         * restarted because there is no reliable way to measure time difference between device
         * reboots (and from the app's perspective, device reboot is no different than app restart).
         * <p>
         * This may cause unexpected behaviors as delayed persistent jobs instantly become available
         * when application restarts.
         * <p>
         * JobManager 2.x versions change this behavior and does not reset the delay of persistent
         * jobs on restart. This may create a problem if jobs were added when the device's clock is
         * set to some unreasonable time but for common cases, it is more desirable.
         * <p>
         * You can get the v1 behavior by calling this method. Note that it will also effect jobs
         * which require network with a timeout. Their timeouts will be triggered on restart if you
         * call this method.
         *
         * @return This Configuration for easy chaining
         */
        @NonNull
        public Builder resetDelaysOnRestart() {
            configuration.resetDelaysOnRestart = true;
            return this;
        }

        /**
         * JobManager needs one persistent and one non-persistent {@link JobQueue} to function.
         * By default, it will use {@link SqliteJobQueue} and
         * {@link com.birbit.android.jobqueue.inMemoryQueue.SimpleInMemoryPriorityQueue}
         * You can provide your own implementation if they don't fit your needs. Make sure it passes all tests in
         * {@code JobQueueTestBase} to ensure it will work fine.
         * @param queueFactory your custom queue factory.
         *
         * @return This Configuration for easy chaining
         */
        @NonNull
        public Builder queueFactory(@Nullable QueueFactory queueFactory) {
            if(configuration.queueFactory != null && queueFactory != null) {
                throw new RuntimeException("already set a queue factory. This might happen if"
                        + "you've provided a custom job serializer");
            }
            configuration.queueFactory = queueFactory;
            return this;
        }

        /**
         * convenient configuration to replace job serializer while using {@link SqliteJobQueue}
         * queue for persistence. By default, it uses a
         * {@link com.birbit.android.jobqueue.persistentQueue.sqlite.SqliteJobQueue.JavaSerializer}
         * which will use default Java serialization.
         * @param jobSerializer The serializer to be used to persist jobs.
         *
         * @return This Configuration for easy chaining
         */
        @NonNull
        public Builder jobSerializer(@NonNull SqliteJobQueue.JobSerializer jobSerializer) {
            configuration.queueFactory = new DefaultQueueFactory(jobSerializer);
            return this;
        }

        /**
         * By default, Job Manager comes with a simple {@link NetworkUtilImpl} that queries {@link ConnectivityManager}
         * to check if network connection exists. You can provide your own if you need a custom logic (e.g. check your
         * server health etc).
         *
         * @param networkUtil The NetworkUtil to be used by the JobManager. If it is null, JobManager
         *                    will use {@link NetworkUtilImpl}
         * @return This Configuration for easy chaining
         */
        @NonNull
        public Builder networkUtil(@Nullable NetworkUtil networkUtil) {
            configuration.networkUtil = networkUtil;
            return this;
        }

        /**
         * JobManager is suitable for DependencyInjection. Just provide your DependencyInjector and it will call it
         * before {Job#onAdded} method is called.
         * if job is persistent, it will also be called before run method.
         * 
         * @param injector your dependency injector interface, if using one
         *
         * @return This Configuration for easy chaining
         */
        @NonNull
        public Builder injector(@Nullable DependencyInjector injector) {
            configuration.dependencyInjector = injector;
            return this;
        }

        /**
         * # of max consumers to run concurrently. defaults to {@link #MAX_CONSUMER_COUNT}
         * @param count The max number of threads that JobManager can create to run jobs
         *
         * @return This Configuration for easy chaining
         */
        @NonNull
        public Builder maxConsumerCount(int count) {
            configuration.maxConsumerCount = count;
            return this;
        }

        /**
         * you can specify to keep minConsumers alive even if there are no ready jobs. defaults to
         * {@link #MIN_CONSUMER_COUNT}
         *
         * @param count The min of of threads that JobManager will keep alive even if they are idle.
         *
         * @return This Configuration for easy chaining
         */
        @NonNull
        public Builder minConsumerCount(int count) {
            configuration.minConsumerCount = count;
            return this;
        }

        /**
         * You can specify a custom timer to control task execution. Useful for testing.
         *
         * @param timer The timer to use
         *
         * @return This Configuration for easy chaining
         */
        @NonNull
        public Builder timer(@Nullable Timer timer) {
            configuration.timer = timer;
            return this;
        }

        /**
         * you can provide a custom logger to get logs from JobManager.
         * by default, JobManager only logs error via Android's <code>Log.e</code>.
         *
         * @param logger The logger to be used by the JobManager.
         *
         * @return This Configuration for easy chaining
         */
        @NonNull
        public Builder customLogger(@Nullable CustomLogger logger) {
            configuration.customLogger = logger;
            return this;
        }

        /**
         * calculated by # of jobs (running+waiting) per thread
         * for instance, at a given time, if you have two consumers and 10 jobs in waiting queue (or running right now), load is
         * (10/2) =5
         * defaults to {@link #DEFAULT_LOAD_FACTOR_PER_CONSUMER}
         *
         * @param loadFactor Number of available jobs per thread
         *
         * @return This Configuration for easy chaining
         */
        @NonNull
        public Builder loadFactor(int loadFactor) {
            configuration.loadFactor = loadFactor;
            return this;
        }

        /**
         * Sets the JobManager in test mode. This information is passed to JobQueue's.
         * If you are using default JobQueues, calling this method will cause {@link SqliteJobQueue}
         * to use an in-memory database.
         *
         * @return This Configuration for easy chaining
         */
        @NonNull
        public Builder inTestMode() {
            configuration.inTestMode = true;
            return this;
        }

        /**
         * Assigns a scheduler that can be used to wake up the application when JobManager has jobs
         * to execute. This is the integration point with the system
         * {@link android.app.job.JobScheduler}.
         * <p>
         * <b>Batching</b>
         * <p>
         * By default, JobManager batches scheduling requests so that it will not call JobScheduler
         * too many times. For instance, if a persistent job that requires network is added, when
         * batching is enabled, JobManager creates the JobScheduler request with
         * {@link com.birbit.android.jobqueue.BatchingScheduler#DEFAULT_BATCHING_PERIOD_IN_MS} delay.
         * Any subsequent job request that has the same criteria will use the previous batching
         * request. This way, JobManager can avoid making a JobScheduler request for every job.
         * It will still execute the Job if it becomes available without waiting for the delay but
         * if the application is killed, the JobScheduler will wait until the delay passes before
         * waking up the application to consume the jobs.
         *
         * @param scheduler The scheduler to be used
         * @param batch     Defines whether the scheduling requests should be batched or not.
         *
         * @return This Configuration for easy chaining
         */
        @NonNull
        public Builder scheduler(@Nullable Scheduler scheduler, boolean batch) {
            configuration.scheduler = scheduler;
            configuration.batchSchedulerRequests = batch;
            return this;
        }

        /**
         * Sets the priority for the threads of this manager. By default it is
         * {@link #DEFAULT_THREAD_PRIORITY}.
         * <p>
         * If a {@link ThreadFactory} is provided, this value is ignored.
         *
         * @param threadPriority The thread priority to be used for new jobs
         *
         * @return This Configuration for easy chaining
         */
        @NonNull
        public Builder consumerThreadPriority(int threadPriority) {
            configuration.threadPriority = threadPriority;
            return this;
        }

        /**
         * Assigns a scheduler that can be used to wake up the application when JobManager has jobs
         * to execute. This is the integration point with the system
         * {@link android.app.job.JobScheduler}.
         * <p>
         * <b>Batching</b>
         * <p>
         * By default, JobManager batches scheduling requests so that it will not call JobScheduler
         * too many times. For instance, if a persistent job that requires network is added, when
         * batching is enabled, JobManager creates the JobScheduler request with
         * {@link com.birbit.android.jobqueue.BatchingScheduler#DEFAULT_BATCHING_PERIOD_IN_MS} delay.
         * Any subsequent job request that has the same criteria will use the previous batching
         * request. This way, JobManager can avoid making a JobScheduler request for every job.
         * It will still execute the Job if it becomes available without waiting for the delay but
         * if the application is killed, the JobScheduler will wait until the delay passes before
         * waking up the application to consume the jobs.
         *
         * @param scheduler The scheduler to be used
         *
         * @return This Configuration.Builder for easy chaining
         */
        @NonNull
        public Builder scheduler(@Nullable Scheduler scheduler) {
            return scheduler(scheduler, true);
        }

        /**
         * Provide a factory class to create new worker instances when JobManager needs them.
         * <p>
         * When a factory is installed, it becomes its responsibility to configure
         * the {@link Thread} with proper group and priority. JobManager will use the {@link Thread}
         * as is.
         *
         * @param threadFactory The factory to be used
         *
         * @return This Configuration.Builder for easy chaining
         */
        @NonNull
        public Builder threadFactory(@Nullable final ThreadFactory threadFactory) {
            configuration.threadFactory = threadFactory;
            return this;
        }

        @NonNull
        public Configuration build() {
            if(configuration.queueFactory == null) {
                configuration.queueFactory = new DefaultQueueFactory();
            }
            if(configuration.networkUtil == null) {
                configuration.networkUtil = new NetworkUtilImpl(configuration.appContext);
            }
            if (configuration.timer == null) {
                configuration.timer = new SystemTimer();
            }
            return configuration;
        }
    }
}
