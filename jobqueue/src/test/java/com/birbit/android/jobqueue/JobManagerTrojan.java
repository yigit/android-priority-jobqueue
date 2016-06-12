package com.birbit.android.jobqueue;

/**
 * Proxy class to access package locals
 */
public class JobManagerTrojan {
    public static ConsumerManager getConsumerManager(JobManager jobManager) {
        return jobManager.jobManagerThread.consumerManager;
    }

    public static CallbackManager getCallbackManager(JobManager jobManager) {
        return jobManager.jobManagerThread.callbackManager;
    }
}
