package com.birbit.android.jobqueue.messaging;

public interface MessageQueueConsumer {
    public void handleMessage(Message message);
    public void onIdle();
}
