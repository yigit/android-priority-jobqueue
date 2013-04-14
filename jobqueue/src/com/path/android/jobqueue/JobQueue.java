package com.path.android.jobqueue;

import com.path.android.jobqueue.JobHolder;

public interface JobQueue {
    long insert(JobHolder jobHolder);

    long insertOrReplace(JobHolder jobHolder);

    void remove(JobHolder jobHolder);

    long count();

    JobHolder nextJobAndIncRunCount();
}
