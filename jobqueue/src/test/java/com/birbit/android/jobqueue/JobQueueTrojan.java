package com.birbit.android.jobqueue;

import com.path.android.jobqueue.JobHolder;
import com.path.android.jobqueue.JobManager;
import com.path.android.jobqueue.JobQueue;

import org.fest.reflect.core.Reflection;
import org.fest.reflect.method.Invoker;

import java.util.Collection;

public class JobQueueTrojan {
    public static Invoker<JobHolder> getNextJobMethod(JobManager2 jobManager) {
        return Reflection.method("getNextJobForTesting").withReturnType(JobHolder.class)
                .in(jobManager.jobQueueThread);
    }

    public static Invoker<JobHolder> getNextJobWithGroupsMethod(JobManager2 jobManager) {
        return Reflection.method("getNextJobForTesting").withReturnType(JobHolder.class)
                .withParameterTypes(Collection.class).in(jobManager.jobQueueThread);
    }

    public static Invoker<Void> getRemoveJobMethod(JobManager2 jobManager) {
        return Reflection.method("removeJob").withParameterTypes(JobHolder.class)
                .in(jobManager.jobQueueThread);
    }

    public static JobQueue getNonPersistentQueue(JobManager2 jobManager) {
        return Reflection.field("nonPersistentJobQueue").ofType(JobQueue.class)
                .in(jobManager.jobQueueThread).get();
    }
}
