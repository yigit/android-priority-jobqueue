package com.tarkalabs.android.jobqueue.messaging.message;

import com.tarkalabs.android.jobqueue.messaging.Message;
import com.tarkalabs.android.jobqueue.messaging.Type;

public class JobConsumerIdleMessage extends Message {
    private Object worker;
    private long lastJobCompleted;

    public JobConsumerIdleMessage() {
        super(Type.JOB_CONSUMER_IDLE);
    }

    @Override
    protected void onRecycled() {
        worker = null;
    }

    public long getLastJobCompleted() {
        return lastJobCompleted;
    }

    public Object getWorker() {
        return worker;
    }

    public void setWorker(Object worker) {
        this.worker = worker;
    }

    public void setLastJobCompleted(long lastJobCompleted) {
        this.lastJobCompleted = lastJobCompleted;
    }
}
