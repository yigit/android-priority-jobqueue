package com.birbit.android.jobqueue;

import com.birbit.android.jobqueue.log.JqLog;
import com.birbit.android.jobqueue.network.NetworkUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Temporary object to keep track of cancel handling
 */
class CancelHandler {
    private Set<JobHolder> running;
    private final TagConstraint tagConstraint;
    private final String[] tags;
    private final Collection<JobHolder> cancelled;
    private final Set<JobHolder> dependentCancelled;
    private final Collection<JobHolder> failedToCancel;
    private final Map<JobHolder, Set<JobHolder>> dependentOfRunning;
    private final CancelResult.AsyncCancelCallback callback;

    CancelHandler(TagConstraint constraint, String[] tags, CancelResult.AsyncCancelCallback callback) {
        this.tagConstraint = constraint;
        this.tags = tags;
        cancelled = new ArrayList<>();
        dependentCancelled = new HashSet<>();
        failedToCancel = new ArrayList<>();
        dependentOfRunning = new HashMap<>();
        this.callback = callback;
    }

    void query(JobManagerThread jobManagerThread, ConsumerManager consumerManager) {
        running = consumerManager.markJobsCancelled(tagConstraint, tags);

        Set<String> runningJobIds = new HashSet<>(running.size());
        for (JobHolder jobHolder : running) {
            runningJobIds.add(jobHolder.id);
            if (jobHolder.persistent) {
                dependentOfRunning.put(jobHolder, jobManagerThread.persistentJobQueue.findDependentJobs(jobHolder));
            }
        }
        Constraint queryConstraint = jobManagerThread.queryConstraint;
        queryConstraint.clear();
        queryConstraint.setNowInNs(jobManagerThread.timer.nanoTime());
        queryConstraint.setTagConstraint(tagConstraint);

        queryConstraint.setExcludeJobIds(runningJobIds);
        queryConstraint.setTags(tags);
        queryConstraint.setExcludeRunning(true);
        queryConstraint.setMaxNetworkType(NetworkUtil.UNMETERED);
        Set<JobHolder> nonPersistentInQueue = jobManagerThread.nonPersistentJobQueue
                .findJobs(queryConstraint);
        Set<JobHolder> persistentInQueue = jobManagerThread.persistentJobQueue
                .findJobs(queryConstraint);
        Set<JobHolder> persistedDependentInQueue = jobManagerThread.persistentJobQueue
                .findDependentJobs(persistentInQueue);

        for (JobHolder nonPersistent : nonPersistentInQueue) {
            nonPersistent.markAsCancelled();
            cancelled.add(nonPersistent);
            jobManagerThread.nonPersistentJobQueue.onJobCancelled(nonPersistent);
        }
        for (JobHolder persistent : persistentInQueue) {
            persistent.markAsCancelled();
            cancelled.add(persistent);
            jobManagerThread.persistentJobQueue.onJobCancelled(persistent);
        }
        for (JobHolder dependent : persistedDependentInQueue) {
            cancelDependentJob(jobManagerThread, dependent);
        }
    }

    void commit(JobManagerThread jobManagerThread) {
        for (JobHolder jobHolder : cancelled) {
            boolean persistent = jobHolder.getJob().isPersistent();
            if (persistent) {
                jobManagerThread.setupJobHolder(jobHolder);
            }
            try {
                jobHolder.onCancel(CancelReason.CANCELLED_WHILE_RUNNING);
            } catch (Throwable t) {
                JqLog.e(t, "job's on cancel has thrown an exception. Ignoring...");
            }
            if (persistent) {
                jobManagerThread.persistentJobQueue.remove(jobHolder);
            }
        }
        for (JobHolder jobHolder : dependentCancelled) {
            boolean persistent = jobHolder.getJob().isPersistent();
            if (persistent) {
                jobManagerThread.setupJobHolder(jobHolder);
            }
            try {
                jobHolder.onCancel(CancelReason.CANCELLED_DUE_TO_DEPENDENT_JOB_CANCELLED);
            } catch (Throwable t) {
                JqLog.e(t, "job's on cancel has thrown an exception. Ignoring...");
            }
            if (persistent) {
                jobManagerThread.persistentJobQueue.remove(jobHolder);
            }
        }
        if (callback != null) {
            Collection<Job> cancelledJobs = new ArrayList<>(cancelled.size());
            Collection<Job> cancelledDependentJobs = new ArrayList<>(dependentCancelled.size());
            Collection<Job> failedToCancelJobs = new ArrayList<>(failedToCancel.size());
            for (JobHolder holder : cancelled) {
                cancelledJobs.add(holder.getJob());
            }
            for (JobHolder holder : dependentCancelled) {
                cancelledDependentJobs.add(holder.getJob());
            }
            for (JobHolder holder : failedToCancel) {
                failedToCancelJobs.add(holder.getJob());
            }
            CancelResult result = new CancelResult(cancelledJobs, cancelledDependentJobs, failedToCancelJobs);
            jobManagerThread.callbackManager.notifyCancelResult(result, callback);
        }
        for (JobHolder jobHolder : cancelled) {
            jobManagerThread.callbackManager.notifyOnCancel(jobHolder.getJob(), true,
                    jobHolder.getThrowable());
        }
        for (JobHolder jobHolder : dependentCancelled) {
            jobManagerThread.callbackManager.notifyOnCancel(jobHolder.getJob(), true,
                    jobHolder.getThrowable());
        }
    }

    void onJobRun(JobManagerThread jobManagerThread, JobHolder holder, int resultCode) {
        final boolean exists;
        exists = running.remove(holder);
        if (exists) {
            if (resultCode == JobHolder.RUN_RESULT_FAIL_FOR_CANCEL) {
                if (holder.persistent) {
                    final Set<JobHolder> dependentJobs = dependentOfRunning.get(holder);
                    if (dependentJobs != null) {
                        for (JobHolder dependent : dependentJobs) {
                            if (!dependentCancelled.contains(dependent)) {
                                cancelDependentJob(jobManagerThread, dependent);
                            }
                        }
                    }
                }
                cancelled.add(holder);
            } else {
                failedToCancel.add(holder);
            }
        }
    }

    private void cancelDependentJob(JobManagerThread jobManagerThread, JobHolder dependent) {
        dependent.markAsCancelled();
        dependentCancelled.add(dependent);
        jobManagerThread.persistentJobQueue.onJobCancelled(dependent);
    }

    boolean isDone() {
        return running.isEmpty();
    }
}
