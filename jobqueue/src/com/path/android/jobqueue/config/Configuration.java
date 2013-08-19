package com.path.android.jobqueue.config;

import com.path.android.jobqueue.JobManager;
import com.path.android.jobqueue.di.DependencyInjector;
import com.path.android.jobqueue.log.CustomLogger;
import com.path.android.jobqueue.network.NetworkUtil;
import com.path.android.jobqueue.network.NetworkUtilImpl;

/**
 * {@link com.path.android.jobqueue.JobManager} configuration object
 */
public class Configuration {
    public static final String DEFAULT_ID = "default_job_manager";
    public static int DEFAULT_THREAD_KEEP_ALIVE_SECONDS = 15;
    public static int DEFAULT_LOAD_FACTOR_PER_CONSUMER = 3;
    public static int MAX_CONSUMER_COUNT = 5;
    public static int MIN_CONSUMER_COUNT = 0;

    private String id = DEFAULT_ID;
    private int maxConsumerCount = MAX_CONSUMER_COUNT;
    private int minConsumerCount = MIN_CONSUMER_COUNT;
    private int consumerKeepAlive = DEFAULT_THREAD_KEEP_ALIVE_SECONDS;
    private int loadFactor = DEFAULT_LOAD_FACTOR_PER_CONSUMER;
    private JobManager.QueueFactory queueFactory;
    private DependencyInjector dependencyInjector;
    private NetworkUtil networkUtil;
    private CustomLogger customLogger;

    public Configuration id(String id) {
        this.id = id;
        return this;
    }

    public Configuration consumerKeepAlive(int keepAlive) {
        this.consumerKeepAlive = keepAlive;
        return this;
    }

    public Configuration queueFactory(JobManager.QueueFactory queueFactory) {
        this.queueFactory = queueFactory;
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

    public Configuration networkUtil(NetworkUtil networkUtil) {
        this.networkUtil = networkUtil;
        return this;
    }

    public Configuration injector(DependencyInjector injector) {
        this.dependencyInjector = injector;
        return this;
    }

    /**
     * calculated by # of jobs (running+waiting) per thread
     * for instance, at a given time, if you have two consumers and 10 jobs in waiting queue (or running right now), load is
     * (10/2) =5
     * if
     * @param count
     * @return
     */
    public Configuration maxConsumerCount(int count) {
        this.maxConsumerCount = count;
        return this;
    }

    public Configuration minConsumerCount(int count) {
        this.minConsumerCount = count;
        return this;
    }

    public Configuration customLogger(CustomLogger logger) {
        this.customLogger = logger;
        return this;
    }

    public Configuration loadFactor(int loadFactor) {
        this.loadFactor = loadFactor;
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
