package com.birbit.android.jobqueue.messaging;

public abstract class MessageQueueConsumer {
    abstract public void handleMessage(Message message);
    abstract public void onIdle();
    public void onStart() {

    }
}
