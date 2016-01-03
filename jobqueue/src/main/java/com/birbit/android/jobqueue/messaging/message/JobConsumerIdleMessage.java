package com.birbit.android.jobqueue.messaging.message;

import com.birbit.android.jobqueue.messaging.Message;
import com.birbit.android.jobqueue.messaging.MessageQueue;
import com.birbit.android.jobqueue.messaging.SafeMessageQueue;
import com.birbit.android.jobqueue.messaging.Type;

public class JobConsumerIdleMessage extends Message {
    private SafeMessageQueue consumerQueue;
    private long lastJobCompleted;

    public JobConsumerIdleMessage() {
        super(Type.JOB_CONSUMER_IDLE);
    }

    @Override
    protected void onRecycled() {
        consumerQueue = null;
    }

    public SafeMessageQueue getConsumerQueue() {
        return consumerQueue;
    }

    public long getLastJobCompleted() {
        return lastJobCompleted;
    }

    public void setConsumerQueue(SafeMessageQueue consumerQueue) {
        this.consumerQueue = consumerQueue;
    }

    public void setLastJobCompleted(long lastJobCompleted) {
        this.lastJobCompleted = lastJobCompleted;
    }
}
