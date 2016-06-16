package com.birbit.android.jobqueue;

import android.content.Context;
import android.support.annotation.Nullable;

import com.birbit.android.jobqueue.scheduling.Scheduler;
import com.birbit.android.jobqueue.scheduling.SchedulerConstraint;
import com.birbit.android.jobqueue.timer.Timer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * JobManager calls scheduler every time it receives some job that can use the Scheduler APIs.
 * This may get too noisy and unnecessary.
 * <p>
 * This BatchingScheduler wraps a generic scheduler and avoid calling the system service if a
 * request is made that has the same criteria as the previous one.
 */
public class BatchingScheduler extends Scheduler {
    // batch by 15 min intervals
    public static final long DEFAULT_BATCHING_PERIOD_IN_MS = TimeUnit.SECONDS.toMillis(60 * 15);
    private long batchingDurationInMs = DEFAULT_BATCHING_PERIOD_IN_MS;
    private long batchingDurationInNs = TimeUnit.MILLISECONDS.toNanos(batchingDurationInMs);
    private final Scheduler delegate;
    private final List<ConstraintWrapper> constraints = new ArrayList<>();
    private final Timer timer;
    public BatchingScheduler(Scheduler delegate, Timer timer) {
        this.delegate = delegate;
        this.timer = timer;
    }

    @Override
    public void init(Context context, Callback callback) {
        super.init(context, callback);
        delegate.init(context, new Callback() {
            @Override
            public boolean start(SchedulerConstraint constraint) {
                removeFromConstraints(constraint);
                return BatchingScheduler.this.start(constraint);
            }

            @Override
            public boolean stop(SchedulerConstraint constraint) {
                return BatchingScheduler.this.stop(constraint);
            }
        });
    }

    private void removeFromConstraints(SchedulerConstraint constraint) {
        synchronized (constraints) {
            for (int i = constraints.size() - 1; i >= 0; i--) {
                ConstraintWrapper existing = constraints.get(i);
                if (existing.constraint.getUuid().equals(constraint.getUuid())) {
                    constraints.remove(i);
                }
            }
        }
    }

    private boolean addToConstraints(SchedulerConstraint constraint) {
        final long now = timer.nanoTime();
        long expectedRunTime = TimeUnit.MILLISECONDS.toNanos(constraint.getDelayInMs()) + now;
        Long expectedDeadline = constraint.getOverrideDeadlineInMs() == null
                ? null
                : TimeUnit.MILLISECONDS.toNanos(constraint.getOverrideDeadlineInMs()) + now;
        synchronized (constraints) {
            for (ConstraintWrapper existing : constraints) {
                if (covers(existing, constraint, expectedRunTime, expectedDeadline)) {
                    return false;
                }
            }
            // fix the delay
            long group = constraint.getDelayInMs() / batchingDurationInMs;
            long newDelay = (group + 1) * batchingDurationInMs;
            constraint.setDelayInMs(newDelay);
            Long deadline = null;
            if (constraint.getOverrideDeadlineInMs() != null) {
                group = constraint.getOverrideDeadlineInMs() / batchingDurationInMs;
                deadline = (group + 1) * batchingDurationInMs;
                constraint.setOverrideDeadlineInMs(deadline);
            }
            constraints.add(new ConstraintWrapper(now + TimeUnit.MILLISECONDS.toNanos(newDelay),
                    deadline == null ? null : now + TimeUnit.MILLISECONDS.toNanos(deadline), constraint));
            return true;
        }
    }

    private boolean covers(ConstraintWrapper existing, SchedulerConstraint constraint,
                           long expectedRunTime, Long expectedDeadline) {
        if (existing.constraint.getNetworkStatus() != constraint.getNetworkStatus()) {
            return false;
        }
        if (expectedDeadline != null) {
            if (existing.deadlineNs == null) {
                return false;
            }
            long timeDiff = existing.deadlineNs - expectedDeadline;
            if (timeDiff < 1 || timeDiff > batchingDurationInNs) {
                return false;
            }
        } else if (existing.deadlineNs != null) {
            return false;
        }
        // same network status, check if time matches
        long timeDiff = existing.delayUntilNs - expectedRunTime;
        return timeDiff > 0 && timeDiff <= batchingDurationInNs;
    }

    @Override
    public void request(SchedulerConstraint constraint) {
        if (addToConstraints(constraint)) {
            delegate.request(constraint);
        }
    }

    @Override
    public void onFinished(SchedulerConstraint constraint, boolean reschedule) {
        removeFromConstraints(constraint);
        delegate.onFinished(constraint, false);
        if (reschedule) {
            request(constraint);
        }
    }

    @Override
    public void cancelAll() {
        synchronized (constraints) {
            constraints.clear();
        }
        delegate.cancelAll();
    }

    private static class ConstraintWrapper {
        final long delayUntilNs;
        @Nullable final Long deadlineNs;
        final SchedulerConstraint constraint;

        public ConstraintWrapper(long delayUntilNs, Long deadlineNs, SchedulerConstraint constraint) {
            this.delayUntilNs = delayUntilNs;
            this.deadlineNs = deadlineNs;
            this.constraint = constraint;
        }
    }
}
