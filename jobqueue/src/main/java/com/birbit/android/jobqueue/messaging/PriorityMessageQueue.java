package com.birbit.android.jobqueue.messaging;

import com.path.android.jobqueue.log.JqLog;
import com.path.android.jobqueue.timer.Timer;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Uses multiple message queues to simulate priority.
 */
public class PriorityMessageQueue implements MessageQueue {
    private final Object LOCK = new Object();
    private final UnsafeMessageQueue[] queues;
    private final DelayedMessageBag delayedBag;
    private final Timer timer;
    private final AtomicBoolean running = new AtomicBoolean(false);

    @SuppressWarnings("unused")
    public PriorityMessageQueue(Timer timer) {
        delayedBag = new DelayedMessageBag();
        queues = new UnsafeMessageQueue[Type.MAX_PRIORITY + 1];
        this.timer = timer;
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
            timer.notifyObject(LOCK);
        }
    }

    public Message next(MessageQueueConsumer consumer) {
        synchronized (LOCK) {
            while (true) {
                long now = timer.nanoTime();
                long nextDelayedReadyAt = delayedBag.flushReadyMessages(now, this);
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
                // this may create too many wake up messages but we can avoid them by simply
                // clearing delayed messages by a constraint query
                consumer.onIdle();
                long waitMs = TimeUnit.NANOSECONDS.toMillis(nextDelayedReadyAt - now);
                try {
                    if (waitMs > 0) {
                        timer.waitOnObject(LOCK, waitMs);
                    } else {
                        timer.waitOnObject(LOCK);
                    }
                }  catch (InterruptedException ignored) {}
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
            timer.notifyObject(LOCK);
        }
    }

    @Override
    public void postAt(Message message, long readyNs) {
        synchronized (LOCK) {
            delayedBag.add(message, readyNs);
            timer.notifyObject(LOCK);
        }
    }

    @Override
    public void cancelMessages(MessagePredicate predicate) {
        synchronized (LOCK) {
            for (int i = 0; i <= Type.MAX_PRIORITY; i++) {
                UnsafeMessageQueue mq = queues[i];
                if (mq == null) {
                    continue;
                }
                mq.removeMessages(predicate);
            }
            delayedBag.removeMessages(predicate);
        }
    }
}
