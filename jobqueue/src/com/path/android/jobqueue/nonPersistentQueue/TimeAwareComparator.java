package com.path.android.jobqueue.nonPersistentQueue;

import com.path.android.jobqueue.JobHolder;

import java.util.Comparator;

/**
 * A real-time comparator class that checks current time to decide of both jobs are valid or not.
 * Return values from this comparator are inconsistent as time may change.
 */
public class TimeAwareComparator implements Comparator<JobHolder> {
    final Comparator<JobHolder> baseComparator;

    public TimeAwareComparator(Comparator<JobHolder> baseComparator) {
        this.baseComparator = baseComparator;
    }

    @Override
    public int compare(JobHolder jobHolder, JobHolder jobHolder2) {
        long now = System.nanoTime();
        boolean job1Valid = jobHolder.getDelayUntilNs() <= now;
        boolean job2Valid = jobHolder2.getDelayUntilNs() <= now;
        if(job1Valid) {
            return job2Valid ? baseComparator.compare(jobHolder, jobHolder2) : -1;
        }
        if(job2Valid) {
            return job1Valid ? baseComparator.compare(jobHolder, jobHolder2) : 1;
        }
        //if both jobs are invalid, return the job that can run earlier. if the want to run at the same time, use base
        //comparison
        if(jobHolder.getDelayUntilNs() < jobHolder2.getDelayUntilNs()) {
            return -1;
        } else if(jobHolder.getDelayUntilNs() > jobHolder2.getDelayUntilNs()) {
            return 1;
        }
        return baseComparator.compare(jobHolder, jobHolder2);
    }
}
