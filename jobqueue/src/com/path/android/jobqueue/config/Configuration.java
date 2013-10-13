package com.path.android.jobqueue.config;

import android.net.ConnectivityManager;
import com.path.android.jobqueue.JobManager;
import com.path.android.jobqueue.JobQueue;
import com.path.android.jobqueue.di.DependencyInjector;
import com.path.android.jobqueue.log.CustomLogger;
import com.path.android.jobqueue.network.NetworkUtil;
import com.path.android.jobqueue.network.NetworkUtilImpl;
import com.path.android.jobqueue.nonPersistentQueue.NonPersistentPriorityQueue;
import com.path.android.jobqueue.persistentQueue.sqlite.SqliteJobQueue;

/**
 * {@link com.path.android.jobqueue.JobManager} configuration object
 */
public class Configuration {
    public static final String DEFAULT_ID = "default_job_manager";
    public static final int DEFAULT_THREAD_KEEP_ALIVE_SECONDS = 15;
    public static final int DEFAULT_LOAD_FACTOR_PER_CONSUMER = 3;
    public static final int MAX_CONSUMER_COUNT = 5;
    public static final int MIN_CONSUMER_COUNT = 0;

    private String id = DEFAULT_ID;
    private int maxConsumerCount = MAX_CONSUMER_COUNT;
    private int minConsumerCount = MIN_CONSUMER_COUNT;
    private int consumerKeepAlive = DEFAULT_THREAD_KEEP_ALIVE_SECONDS;
    private int loadFactor = DEFAULT_LOAD_FACTOR_PER_CONSUMER;
    private JobManager.QueueFactory queueFactory;
    private DependencyInjector dependencyInjector;
    private NetworkUtil networkUtil;
    private CustomLogger customLogger;

    /**
     * provide and ID for this job manager to be used while creating persistent queue. it is useful if you are going to
     * create multiple instances of it.
     * default id is {@value #DEFAULT_ID}
     * @param id
     * @return
     */
    public Configuration id(String id) {
        this.id = id;
        return this;
    }

    /**
     * When JobManager runs out of `ready` jobs, it will keep consumers alive for this duration. it defaults to {@value #DEFAULT_THREAD_KEEP_ALIVE_SECONDS}
     * @param keepAlive in seconds
     * @return
     */
    public Configuration consumerKeepAlive(int keepAlive) {
        this.consumerKeepAlive = keepAlive;
        return this;
    }

    /**
     * JobManager needs one persistent and one non-persistent {@link JobQueue} to function.
     * By default, it will use {@link SqliteJobQueue} and {@link NonPersistentPriorityQueue}
     * You can provide your own implementation if they don't fit your needs. Make sure it passes all tests in
     * {@link JobQueueTestBase} to ensure it will work fine.
     * @param queueFactory
     * @return
     */
    public Configuration queueFactory(JobManager.QueueFactory queueFactory) {
        this.queueFactory = queueFactory;
        return this;
    }

    /**
     * By default, Job Manager comes with a simple {@link NetworkUtilImpl} that queries {@link ConnectivityManager}
     * to check if network connection exists. You can provide your own if you need a custom logic (e.g. check your
     * server health etc).
     * @param networkUtil
     * @return
     */
    public Configuration networkUtil(NetworkUtil networkUtil) {
        this.networkUtil = networkUtil;
        return this;
    }

    /**
     * JobManager is suitable for DependencyInjection. Just provide your DependencyInjector and it will call it
     * before running jobs.
     * @param injector
     * @return
     */
    public Configuration injector(DependencyInjector injector) {
        this.dependencyInjector = injector;
        return this;
    }

    /**
     * # of max consumers to run concurrently. defaults to {@value #MAX_CONSUMER_COUNT}
     * @param count
     * @return
     */
    public Configuration maxConsumerCount(int count) {
        this.maxConsumerCount = count;
        return this;
    }

    /**
     * you can specify to keep minConsumers alive even if there are no ready jobs. defaults to {@value #MIN_CONSUMER_COUNT}
     * @param count
     * @return
     */
    public Configuration minConsumerCount(int count) {
        this.minConsumerCount = count;
        return this;
    }

    /**
     * you can provide a custom logger to get logs from JobManager.
     * by default, logs will go no-where.
     * @param logger
     * @return
     */
    public Configuration customLogger(CustomLogger logger) {
        this.customLogger = logger;
        return this;
    }

    /**
     * calculated by # of jobs (running+waiting) per thread
     * for instance, at a given time, if you have two consumers and 10 jobs in waiting queue (or running right now), load is
     * (10/2) =5
     * defaults to {@value #DEFAULT_LOAD_FACTOR_PER_CONSUMER}
     * @param loadFactor
     * @return
     */
    public Configuration loadFactor(int loadFactor) {
        this.loadFactor = loadFactor;
        return this;
    }

    public Configuration defaultQueueFactory() {
        this.queueFactory = new JobManager.DefaultQueueFactory();
        return this;
    }

    public Configuration defaultNetworkUtil() {
        this.networkUtil = new NetworkUtilImpl();
        return this;
    }

    public String getId() {
        return id;
    }

    public JobManager.QueueFactory getQueueFactory() {
        return queueFactory;
    }

    public DependencyInjector getDependencyInjector() {
        return dependencyInjector;
    }

    public int getConsumerKeepAlive() {
        return consumerKeepAlive;
    }

    public NetworkUtil getNetworkUtil() {
        return networkUtil;
    }

    public int getMaxConsumerCount() {
        return maxConsumerCount;
    }

    public int getMinConsumerCount() {
        return minConsumerCount;
    }

    public CustomLogger getCustomLogger() {
        return customLogger;
    }

    public int getLoadFactor() {
        return loadFactor;
    }
}
