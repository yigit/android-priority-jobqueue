package com.tarkalabs.android.jobqueue.messaging.message;

import com.tarkalabs.android.jobqueue.messaging.Message;
import com.tarkalabs.android.jobqueue.messaging.Type;
import com.tarkalabs.android.jobqueue.JobHolder;

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
