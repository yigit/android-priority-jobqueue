package com.birbit.android.jobqueue.messaging;

import com.path.android.jobqueue.log.JqLog;

import java.util.concurrent.atomic.AtomicInteger;

class UnsafeMessageQueue {
    private Message queue = null;
    private Message tail = null;
    private static final AtomicInteger idCounter = new AtomicInteger(0);
    public final int id = idCounter.incrementAndGet();
    private final MessageFactory factory;

    public UnsafeMessageQueue(MessageFactory factory) {
        this.factory = factory;
    }

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

    protected void removeMessages(MessagePredicate predicate) {
        Message prev = null;
        Message curr = queue;
        while (curr != null) {
            final boolean remove = predicate.onMessage(curr);
            if (remove) {
                final Message next = curr.next;
                remove(prev, curr);
                curr = next;
            } else {
                prev = curr;
                curr = curr.next;
            }
        }
    }

    private void remove(Message prev, Message curr) {
        if (tail == curr) {
            tail = prev;
        }
        if (prev == null) {
            queue = curr.next;
        } else {
            prev.next = curr.next;
        }
        factory.release(curr);
    }

    public void clear() {
        while (queue != null) {
            Message curr = queue;
            queue = curr.next;
            factory.release(curr);
        }
        tail = null;
    }
}
