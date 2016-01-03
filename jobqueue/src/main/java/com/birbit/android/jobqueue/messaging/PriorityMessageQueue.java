package com.birbit.android.jobqueue.messaging;

import com.path.android.jobqueue.log.JqLog;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Uses multiple message queues to simulate priority.
 */
public class PriorityMessageQueue implements MessageQueue {
    private final Object LOCK = new Object();
    private final UnsafeMessageQueue[] queues;
    private final AtomicBoolean running = new AtomicBoolean(false);

    @SuppressWarnings("unused")
    public PriorityMessageQueue() {
        queues = new UnsafeMessageQueue[Type.MAX_PRIORITY + 1];
    }

    public void consume(MessageQueueConsumer consumer) {
        if(running.getAndSet(true)) {
            throw new IllegalStateException("only 1 consumer per MQ");
        }
        while (running.get()) {
            Message message = next(consumer);
            consumer.handleMessage(message);
        }
    }

    public void stop() {
        running.set(false);
        synchronized (LOCK) {
            LOCK.notifyAll();
        }
    }

    private Message next(MessageQueueConsumer consumer) {
        synchronized (LOCK) {
            while (true) {
                for (int i = Type.MAX_PRIORITY; i >= 0; i--) {
                    UnsafeMessageQueue mq = queues[i];
                    if (mq == null) {
                        continue;
                    }
                    Message message = mq.next();
                    if (message != null) {
                        return message;
                    }
                }
                consumer.onIdle();
                try {
                    LOCK.wait();
                } catch (InterruptedException e) {
                    JqLog.e(e, "PMQ is interrupted");
                }
            }
        }
    }

    @Override
    public void post(Message message) {
        synchronized (LOCK) {
            int index = message.type.priority;
            if (queues[index] == null) {
                queues[index] = new UnsafeMessageQueue();
            }
            queues[index].post(message);
            LOCK.notifyAll();
        }
    }
}
