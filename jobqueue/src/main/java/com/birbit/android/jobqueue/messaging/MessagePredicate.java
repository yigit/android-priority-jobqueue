package com.birbit.android.jobqueue.messaging;

public interface MessagePredicate {
    public boolean onMessage(Message message);
}
