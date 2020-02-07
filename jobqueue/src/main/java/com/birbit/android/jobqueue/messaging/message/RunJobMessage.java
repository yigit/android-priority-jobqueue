package com.birbit.android.jobqueue.messaging.message;

import com.birbit.android.jobqueue.messaging.Message;
import com.birbit.android.jobqueue.messaging.Type;
import com.birbit.android.jobqueue.JobHolder;

public class RunJobMessage extends Message {
    private JobHolder jobHolder;
    public RunJobMessage() {
        super(Type.RUN_JOB);
    }

    public JobHolder getJobHolder() {
        return jobHolder;
    }

    public void setJobHolder(JobHolder jobHolder) {
        this.jobHolder = jobHolder;
    }

    @Override
    protected void onRecycled() {
        jobHolder = null;
    }
}
