package com.birbit.android.jobqueue.messaging;

import com.path.android.jobqueue.log.JqLog;

import java.util.concurrent.atomic.AtomicInteger;

class UnsafeMessageQueue {
    private Message queue = null;
    private Message tail = null;
    private static final AtomicInteger idCounter = new AtomicInteger(0);
    public final int id = idCounter.incrementAndGet();

    Message next() {
        final Message result = queue;
        JqLog.d("remove message %s from queue %d", result, id);
        if (result != null) {
            queue = result.next;
            if (tail == result) {
                tail = null;
            }
        }
        return result;
    }

    protected void post(Message message) {
        JqLog.d("post message %s to queue %d", message, id);
        if (tail == null) {
            queue = message;
            tail = message;
        } else {
            tail.next = message;
            tail = message;
        }
    }

    protected void postAtFront(Message message) {
        message.next = queue;
        if (tail == null) {
            tail = message;
        }
        queue = message;
    }

    public void clear() {
        queue = tail = null;
    }
}
