package com.birbit.android.jobqueue.messaging;

import com.path.android.jobqueue.log.JqLog;

class DelayedMessageBag {
    Message queue = null;

    Long flushReadyMessages(long now, MessageQueue addInto) {
        JqLog.d("flushing messages at time %s", now);
        while (queue != null && queue.readyNs <= now) {
            Message msg = queue;
            queue = msg.next;
            msg.next = null;
            addInto.post(msg);
        }
        if (queue != null) {
            return queue.readyNs;
        }
        return null;
    }
    void add(Message message, long readyNs) {
        JqLog.d("add delayed message %s at time %s", message, readyNs);
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

    public void removeMessages(MessagePredicate predicate) {
        Message prev = null;
        Message curr = queue;
        while (curr != null) {
            final boolean remove = predicate.onMessage(curr);
            final Message next = curr.next;
            if (remove) {
                if (prev == null) {
                    queue = curr.next;
                } else {
                    prev.next = curr.next;
                }
            } else {
                prev = curr;
            }
            curr = next;
        }
    }
}
