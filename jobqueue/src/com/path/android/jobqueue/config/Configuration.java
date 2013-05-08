package com.path.android.jobqueue.config;

import com.path.android.jobqueue.JobManager;
import com.path.android.jobqueue.di.DependencyInjector;
import com.path.android.jobqueue.network.NetworkUtil;
import com.path.android.jobqueue.network.NetworkUtilImpl;

/**
 * {@link com.path.android.jobqueue.JobManager} configuration object
 */
public class Configuration {
    public static final String DEFAULT_ID = "default_job_manager";
    public static int DEFAULT_THREAD_KEEP_ALIVE_SECONDS = 15;
    public static int MAX_CONSUMER_COUNT = 5;

    private String id = DEFAULT_ID;
    private int maxConsumerCount = MAX_CONSUMER_COUNT;
    private long threadKeepAlive = DEFAULT_THREAD_KEEP_ALIVE_SECONDS;
    private JobManager.QueueFactory queueFactory;
    private DependencyInjector dependencyInjector;
    private NetworkUtil networkUtil;

    public Configuration withId(String id) {
        this.id = id;
        return this;
    }

    public Configuration withThreadKeepAlive(int keepAlive) {
        this.threadKeepAlive = keepAlive;
        return this;
    }

    public Configuration withQueueFactory(JobManager.QueueFactory queueFactory) {
        this.queueFactory = queueFactory;
        return this;
    }

    public Configuration withDefaultQueueFactory() {
        this.queueFactory = new JobManager.DefaultQueueFactory();
        return this;
    }

    public Configuration withDefaultNetworkUtil() {
        this.networkUtil = new NetworkUtilImpl();
        return this;
    }

    public Configuration withNetworkUtil(NetworkUtil networkUtil) {
        this.networkUtil = networkUtil;
        return this;
    }

    public Configuration withInjector(DependencyInjector injector) {
        this.dependencyInjector = injector;
        return this;
    }

    public Configuration withMaxConsumerCount(int count) {
        this.maxConsumerCount = count;
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

    public long getThreadKeepAlive() {
        return threadKeepAlive;
    }

    public NetworkUtil getNetworkUtil() {
        return networkUtil;
    }

    public int getMaxConsumerCount() {
        return maxConsumerCount;
    }
}
