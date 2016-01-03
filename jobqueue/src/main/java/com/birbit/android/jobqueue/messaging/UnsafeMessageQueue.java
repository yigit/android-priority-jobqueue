package com.birbit.android.jobqueue.messaging;


class UnsafeMessageQueue {
    private Message queue = null;
    private Message tail = null;

    Message next() {
        final Message result = queue;
        if (result != null) {
            queue = result.next;
            if (tail == result) {
                tail = null;
            }
        }
        return result;
    }

    protected void post(Message message) {
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
