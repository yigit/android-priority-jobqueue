package com.birbit.android.jobqueue.messaging;

public interface MessageQueue {
    void post(Message message);
    void postAtFront(Message message);
}
