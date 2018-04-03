package com.tarkalabs.android.jobqueue.messaging;

public interface MessagePredicate {
    boolean onMessage(Message message);
}
