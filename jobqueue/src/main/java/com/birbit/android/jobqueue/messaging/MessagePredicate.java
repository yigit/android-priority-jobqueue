package com.birbit.android.jobqueue.messaging;

public interface MessagePredicate {
    boolean onMessage(Message message);
}
