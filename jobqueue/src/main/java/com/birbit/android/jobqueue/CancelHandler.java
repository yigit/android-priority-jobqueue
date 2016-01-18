package com.birbit.android.jobqueue;

import com.path.android.jobqueue.CancelResult;
import com.path.android.jobqueue.Job;
import com.path.android.jobqueue.JobHolder;
import com.path.android.jobqueue.TagConstraint;
import com.path.android.jobqueue.log.JqLog;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Set;

/**
 * Temporary object to keep track of cancel handling
 */
class CancelHandler {
    private Set<String> runningNonPersistent;
    private Set<String> runningPersistent;
    private final TagConstraint constraint;
    private final String[] tags;
    private final Collection<JobHolder> cancelled;
    private final Collection<JobHolder> failedToCancel;
    private final CancelResult.AsyncCancelCallback callback;

    CancelHandler(TagConstraint constraint, String[] tags, CancelResult.AsyncCancelCallback callback) {
        this.constraint = constraint;
        this.tags = tags;
        cancelled = new ArrayList<>();
        failedToCancel = new ArrayList<>();
        this.callback = callback;
    }
    
    void query(JobManagerThread jobManagerThread, ConsumerManager consumerManager) {
        runningNonPersistent = consumerManager.markJobsCancelled(constraint, tags, false);
        runningPersistent = consumerManager.markJobsCancelled(constraint, tags, true);
        Set<JobHolder> nonPersistentInQueue = jobManagerThread.nonPersistentJobQueue
                .findJobsByTags(constraint, true, runningNonPersistent, tags);
        Set<JobHolder> persistentInQueue = jobManagerThread.persistentJobQueue
                .findJobsByTags(constraint, true, runningPersistent, tags);
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
    }

    void commit(JobManagerThread jobManagerThread) {
        for (JobHolder jobHolder : cancelled) {
            try {
                jobHolder.onCancel();
            } catch (Throwable t) {
                JqLog.e(t, "job's on cancel has thrown an exception. Ignoring...");
            }
            if (jobHolder.getJob().isPersistent()) {
                jobManagerThread.nonPersistentJobQueue.remove(jobHolder);
            }
        }
        if (callback != null) {
            Collection<Job> cancelledJobs = new ArrayList<>(cancelled.size());
            Collection<Job> failedToCancelJobs = new ArrayList<>(failedToCancel.size());
            for (JobHolder holder : cancelled) {
                cancelledJobs.add(holder.getJob());
            }
            for (JobHolder holder : failedToCancel) {
                failedToCancelJobs.add(holder.getJob());
            }
            CancelResult result = new CancelResult(cancelledJobs, failedToCancelJobs);
            jobManagerThread.callbackManager.notifyCancelResult(result, callback);
        }
        for (JobHolder jobHolder : cancelled) {
            jobManagerThread.callbackManager.notifyOnCancel(jobHolder.getJob(), true);
        }
    }

    void onJobRun(JobHolder holder, int resultCode) {
        final boolean exists;
        if (holder.getJob().isPersistent()) {
            exists = runningPersistent.remove(holder.getId());
        } else {
            exists = runningNonPersistent.remove(holder.getId());
        }
        if (exists) {
            if (resultCode == JobHolder.RUN_RESULT_FAIL_FOR_CANCEL) {
                cancelled.add(holder);
            } else {
                failedToCancel.add(holder);
            }
        }
    }

    boolean isDone() {
        return runningNonPersistent.isEmpty() && runningPersistent.isEmpty();
    }
}
