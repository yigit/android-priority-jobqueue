package com.birbit.android.jobqueue.inMemoryQueue;

import android.support.annotation.NonNull;

import com.birbit.android.jobqueue.Constraint;
import com.birbit.android.jobqueue.JobHolder;
import com.birbit.android.jobqueue.JobManager;
import com.birbit.android.jobqueue.JobQueue;
import com.birbit.android.jobqueue.Params;
import com.birbit.android.jobqueue.config.Configuration;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A simple implementation of in memory {@link com.birbit.android.jobqueue.JobQueue}
 */
public class SimpleInMemoryPriorityQueue implements JobQueue {
    private final TreeSet<JobHolder> jobs = new TreeSet<>(new Comparator<JobHolder>() {
        @Override
        public int compare(JobHolder holder1, JobHolder holder2) {
            if (holder1.getJob().getId().equals(holder2.getJob().getId())) {
                return 0;
            }
            int cmp = compareInt(holder1.getPriority(), holder2.getPriority());
            if(cmp != 0) {
                return cmp;
            }

            cmp = -compareLong(holder1.getCreatedNs(), holder2.getCreatedNs());
            if(cmp != 0) {
                return cmp;
            }
            //if jobs were created at the same time, smaller id first
            return -compareLong(holder1.getInsertionOrder(), holder2.getInsertionOrder());
        }

        private int compareInt(int i1, int i2) {
            if (i1 > i2) {
                return -1;
            }
            if (i2 > i1) {
                return 1;
            }
            return 0;
        }

        private int compareLong(long l1, long l2) {
            if (l1 > l2) {
                return -1;
            }
            if (l2 > l1) {
                return 1;
            }
            return 0;
        }
    });
    private final Map<String, JobHolder> idCache = new HashMap<>();

    private final AtomicLong insertionOrderCounter = new AtomicLong(0);
    private final List<String> reusedList = new ArrayList<>();
    private final long sessionId;

    public SimpleInMemoryPriorityQueue(
            @SuppressWarnings("UnusedParameters") Configuration configuration, long sessionId) {
        this.sessionId = sessionId;
    }
    @Override
    public boolean insert(@NonNull JobHolder jobHolder) {
        jobHolder.setInsertionOrder(insertionOrderCounter.incrementAndGet());
        JobHolder existing = idCache.get(jobHolder.getId());
        if (existing != null) {
            throw new IllegalArgumentException("cannot add a job with the same id twice");
        }
        idCache.put(jobHolder.getId(), jobHolder);
        jobs.add(jobHolder);
        return true;
    }

    @Override
    public boolean insertOrReplace(@NonNull JobHolder jobHolder) {
        if (jobHolder.getInsertionOrder() == null) {
            return insert(jobHolder);
        }
        JobHolder existing = idCache.get(jobHolder.getId());
        if (existing != null) {
            remove(existing);
        }
        idCache.put(jobHolder.getId(), jobHolder);
        jobs.add(jobHolder);
        return true;
    }

    @Override
    public void substitute(@NonNull JobHolder newJob, @NonNull JobHolder oldJob) {
        remove(oldJob);
        insert(newJob);
    }

    @Override
    public void remove(@NonNull JobHolder jobHolder) {
        idCache.remove(jobHolder.getId());
        jobs.remove(jobHolder);
    }

    @Override
    public int count() {
        return jobs.size();
    }

    @Override
    public int countReadyJobs(@NonNull Constraint constraint) {
        int count = 0;
        reusedList.clear();
        for (JobHolder holder : jobs) {
            String groupId = holder.getGroupId();
            if ((groupId == null || !reusedList.contains(groupId)) && matches(holder, constraint)) {
                count ++;
                if (groupId != null) {
                    reusedList.add(groupId);
                }
            }
        }
        reusedList.clear();
        return count;
    }

    @Override
    public JobHolder nextJobAndIncRunCount(@NonNull Constraint constraint) {
        for (JobHolder holder : jobs) {
            if (matches(holder, constraint)) {
                remove(holder);
                holder.setRunCount(holder.getRunCount() + 1);
                holder.setRunningSessionId(sessionId);
                return holder;
            }
        }
        return null;
    }

    private static Long getDelayUntil(JobHolder holder, boolean hasNetwork, boolean hasUnmetered) {
        final long networkTimeout = holder.getRequiresNetworkUntilNs();
        final long unmeteredTimeout = holder.getRequiresUnmeteredNetworkUntilNs();
        long delay = holder.getDelayUntilNs();
        if (!hasNetwork) {
            if (networkTimeout == Params.FOREVER) {
                return null; // ineligible
            }
            delay = Math.max(delay, networkTimeout);
        }
        if (!hasUnmetered) {
            if (unmeteredTimeout == Params.FOREVER) {
                return null; // ineligible
            }
            delay = Math.max(delay, unmeteredTimeout);
        }
        return delay;
    }

    @Override
    public Long getNextJobDelayUntilNs(@NonNull Constraint constraint) {
        Long minDelay = null;
        boolean hasNetwork = !constraint.shouldNotRequireNetwork();
        boolean hasUnmetered = !constraint.shouldNotRequireUnmeteredNetwork();
        if (!hasNetwork || !hasUnmetered) {
            for (JobHolder holder : jobs) {
                if (holder.getDeadlineNs() <= constraint.getNowInNs()) {
                    return holder.getDeadlineNs();
                }
                if (matches(holder, constraint, true)) {
                    Long delay = getDelayUntil(holder, hasNetwork, hasUnmetered);
                    if (holder.getDeadlineNs() != Params.FOREVER) {
                        delay = delay == null
                                ? holder.getDeadlineNs()
                                : Math.min(holder.getDeadlineNs(), holder.getDelayUntilNs());
                    }
                    if (delay == null) {
                        continue;// ineligible
                    }
                    if (minDelay == null || delay < minDelay) {
                        minDelay = delay;
                    }
                } else if (holder.getDeadlineNs() != Params.FOREVER) {
                    if (minDelay == null || holder.getDeadlineNs() < minDelay) {
                        minDelay = holder.getDeadlineNs();
                    }
                }
            }
        } else {
            for (JobHolder holder : jobs) {
                if (holder.getDeadlineNs() <= constraint.getNowInNs()) {
                    return holder.getDeadlineNs();
                }
                if (matches(holder, constraint)) {
                    final long delay = holder.getDeadlineNs() != Params.FOREVER
                            ? Math.min(holder.getDeadlineNs(), holder.getDelayUntilNs())
                            : holder.getDelayUntilNs();
                    if (minDelay == null || delay < minDelay) {
                        minDelay = holder.getDelayUntilNs();
                    }
                } else if (holder.getDeadlineNs() != Params.FOREVER) {
                    if (minDelay == null || holder.getDeadlineNs() < minDelay) {
                        minDelay = holder.getDeadlineNs();
                    }
                }
            }
        }

        return minDelay;
    }

    @Override
    public void clear() {
        jobs.clear();
        idCache.clear();
    }

    @Override
    public JobHolder findJobById(@NonNull String id) {
        return idCache.get(id);
    }

    @NonNull
    @Override
    public Set<JobHolder> findJobs(@NonNull Constraint constraint) {
        Set<JobHolder> result = new HashSet<>();
        for (JobHolder holder : jobs) {
            if (matches(holder, constraint)) {
                result.add(holder);
            }
        }
        return result;
    }

    @Override
    public void onJobCancelled(JobHolder holder) {
        remove(holder);
    }

    private static boolean matches(JobHolder holder, Constraint constraint) {
        return matches(holder, constraint, false);
    }
    @SuppressWarnings("RedundantIfStatement")
    private static boolean matches(JobHolder holder, Constraint constraint, boolean ignoreNetwork) {
        if (constraint.getNowInNs() >= holder.getDeadlineNs()) {
            return true;
        }
        if (!ignoreNetwork) {
            if (constraint.shouldNotRequireNetwork()
                    && holder.requiresNetwork(constraint.getNowInNs())) {
                return false;
            }
            if (constraint.shouldNotRequireUnmeteredNetwork()
                    && holder.requiresUnmeteredNetwork(constraint.getNowInNs())) {
                return false;
            }
        }
        if (constraint.getTimeLimit() != null && holder.getDelayUntilNs() > constraint.getTimeLimit()) {
            return false;
        }
        if (holder.getGroupId() != null && constraint.getExcludeGroups().contains(holder.getGroupId())) {
            return false;
        }
        if (constraint.getExcludeJobIds().contains(holder.getId())) {
            return false;
        }
        if (constraint.getTagConstraint() != null &&
                (holder.getTags() == null || constraint.getTags().isEmpty() ||
                !constraint.getTagConstraint().matches(constraint.getTags(), holder.getTags()))) {
            return false;
        }
        return true;
    }
}
