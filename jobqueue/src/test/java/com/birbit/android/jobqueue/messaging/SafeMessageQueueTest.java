package com.birbit.android.jobqueue.messaging;

import com.path.android.jobqueue.timer.Timer;

public class SafeMessageQueueTest extends MessageQueueTestBase<SafeMessageQueue> {

    @Override
    SafeMessageQueue createMessageQueue(Timer timer, MessageFactory factory) {
        return new SafeMessageQueue(timer, factory, "test");
    }
}
