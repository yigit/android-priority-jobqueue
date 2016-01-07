package com.birbit.android.jobqueue.messaging;

class DelayedMessageBag {
    Message queue = null;

    long flushReadyMessages(long now, MessageQueue addInto) {
        while (queue != null && queue.readyNs <= now) {
            Message msg = queue;
            queue = msg.next;
            msg.next = null;
            addInto.post(msg);
        }
        if (queue != null) {
            return queue.readyNs;
        }
        return now;
    }
    void add(Message message, long readyNs) {
        message.readyNs = readyNs;
        if (queue == null) {
            queue = message;
            return;
        }
        Message prev = null;
        Message curr = queue;
        while (curr != null && curr.readyNs <= readyNs) {
            prev = curr;
            curr = curr.next;
        }
        if (prev == null) {
            message.next = queue;
            queue = message;
        } else {
            prev.next = message;
            message.next = curr;
        }
    }

    public void clear() {
        queue = null;
    }
}
