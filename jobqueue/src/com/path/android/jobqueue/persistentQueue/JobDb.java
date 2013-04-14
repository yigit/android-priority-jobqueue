package com.path.android.jobqueue.persistentQueue;

import com.path.android.jobqueue.JobHolder;

/**
 * Created with IntelliJ IDEA.
 * User: yigit
 * Date: 4/13/13
 * Time: 2:35 PM
 * To change this template use File | Settings | File Templates.
 */
public interface JobDb {
    long insert(JobHolder jobHolder);

    long insertOrReplace(JobHolder jobHolder);

    void remove(JobHolder jobHolder);

    long count();

    JobHolder nextJob();
}
