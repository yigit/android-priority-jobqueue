package com.birbit.android.jobqueue.messaging;

import com.path.android.jobqueue.log.JqLog;
import com.path.android.jobqueue.timer.Timer;

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
    // used when jobs are posted inside sync blocks
    private boolean postJobTick = false;
    private final MessageFactory factory;

    @SuppressWarnings("unused")
    public PriorityMessageQueue(Timer timer, MessageFactory factory) {
        delayedBag = new DelayedMessageBag(factory);
        this.factory = factory;
        queues = new UnsafeMessageQueue[Type.MAX_PRIORITY + 1];
        this.timer = timer;
    }

    @Override
    public void consume(MessageQueueConsumer consumer) {
        if(running.getAndSet(true)) {
            throw new IllegalStateException("only 1 consumer per MQ");
        }
        while (running.get()) {
            Message message = next(consumer);
            if (message != null) {
                consumer.handleMessage(message);
                factory.release(message);
            }
        }
    }

    @Override
    public void clear() {
        synchronized (LOCK) {
            for (int i = Type.MAX_PRIORITY; i >= 0; i--) {
                UnsafeMessageQueue mq = queues[i];
                if (mq == null) {
                    continue;
                }
                mq.clear();
            }
        }
    }

    @Override
    public void stop() {
        running.set(false);
        synchronized (LOCK) {
            timer.notifyObject(LOCK);
        }
    }

    public Message next(MessageQueueConsumer consumer) {
        boolean calledOnIdle = false;
        while (running.get()) {
            final Long nextDelayedReadyAt;
            final long now;
            synchronized (LOCK) {
                now = timer.nanoTime();
                JqLog.d("looking for next message at time %s", now);
                nextDelayedReadyAt = delayedBag.flushReadyMessages(now, this);
                JqLog.d("next delayed job %s", nextDelayedReadyAt);
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
                postJobTick = false;
            }
            if (!calledOnIdle) {
                consumer.onIdle();
                calledOnIdle = true;
            }
            synchronized (LOCK) {
                JqLog.d("did on idle post a message? %s", postJobTick);
                // callback may add new messages
                if (postJobTick) {
                    continue; // idle posted jobs, requery
                }
                if (nextDelayedReadyAt != null && nextDelayedReadyAt <= now) {
                    continue;
                }
                if (running.get()) {
                    try {
                        if (nextDelayedReadyAt == null) {
                            timer.waitOnObject(LOCK);
                        } else {
                            timer.waitOnObjectUntilNs(LOCK, nextDelayedReadyAt);
                        }
                    } catch (InterruptedException ignored) {
                    }
                }
            }
        }
        return null;
    }

    @Override
    public void post(Message message) {
        synchronized (LOCK) {
            postJobTick = true;
            int index = message.type.priority;
            if (queues[index] == null) {
                queues[index] = new UnsafeMessageQueue(factory);
            }
            queues[index].post(message);
            timer.notifyObject(LOCK);
        }
    }

    @Override
    public void postAt(Message message, long readyNs) {
        synchronized (LOCK) {
            postJobTick = true;
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
