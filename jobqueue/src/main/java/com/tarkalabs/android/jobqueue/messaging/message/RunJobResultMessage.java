package com.tarkalabs.android.jobqueue.messaging.message;

import com.tarkalabs.android.jobqueue.messaging.Message;
import com.tarkalabs.android.jobqueue.messaging.Type;
import com.tarkalabs.android.jobqueue.JobHolder;

public class RunJobResultMessage extends Message {
    private JobHolder jobHolder;
    private Object worker;
    private int result;

    public RunJobResultMessage() {
        super(Type.RUN_JOB_RESULT);
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

    public void setResult(int result) {
        this.result = result;
    }

    public int getResult() {
        return result;
    }

    public Object getWorker() {
        return worker;
    }

    public void setWorker(Object worker) {
        this.worker = worker;
    }
}
