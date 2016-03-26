package com.birbit.android.jobqueue.messaging;

import com.birbit.android.jobqueue.log.JqLog;
import com.birbit.android.jobqueue.timer.Timer;

import java.util.concurrent.atomic.AtomicBoolean;

public class SafeMessageQueue extends UnsafeMessageQueue implements MessageQueue {
    private final Object LOCK = new Object();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final Timer timer;
    private final DelayedMessageBag delayedBag;
    // used to check if any new message is posted inside sync block
    private boolean postMessageTick = false;
    private final MessageFactory factory;
    public SafeMessageQueue(Timer timer, MessageFactory factory, String logTag) {
        super(factory, logTag);
        this.factory = factory;
        this.timer = timer;
        this.delayedBag = new DelayedMessageBag(factory);
    }

    public boolean isRunning() {
        return running.get();
    }

    @Override
    public void consume(MessageQueueConsumer consumer) {
        if(running.getAndSet(true)) {
            throw new IllegalStateException("only 1 consumer per MQ");
        }
        consumer.onStart();
        while (running.get()) {
            Message message = next(consumer);
            if (message != null) {
                consumer.handleMessage(message);
                factory.release(message);
            }
        }
        JqLog.d("[%s] finished queue", logTag);
    }

    @Override
    public void stop() {
        running.set(false);
        synchronized (LOCK) {
            timer.notifyObject(LOCK);
        }
    }

    public void clear() {
        synchronized (LOCK) {
            super.clear();
        }
    }

    Message next(MessageQueueConsumer consumer) {
        boolean calledIdle = false;

        while (running.get()) {
            final Long nextDelayedReadyAt;
            final long now;
            synchronized (LOCK) {
                now = timer.nanoTime();
                nextDelayedReadyAt = delayedBag.flushReadyMessages(now, this);
                Message message = super.next();
                if (message != null) {
                    return message;
                }
                postMessageTick = false;
            }
            // call onIdle outside the lock. This risks calling onIdle after a message post but
            // it is better than locking post messages until idle finishes.
            if (!calledIdle) {
                consumer.onIdle();
                calledIdle = true;
            }
            synchronized (LOCK) {
                if (postMessageTick) {
                    continue; // callback added a message, requery
                }
                if (nextDelayedReadyAt != null && nextDelayedReadyAt <= now) {
                    JqLog.d("[%s] next message is ready, requery", logTag);
                    continue;
                }
                if (running.get()) {
                    try {
                        if (nextDelayedReadyAt == null) {
                            JqLog.d("[%s] will wait on the lock forever", logTag);
                            timer.waitOnObject(LOCK);
                        } else {
                            JqLog.d("[%s] will wait on the lock until %d", logTag,
                                    nextDelayedReadyAt);
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
            postMessageTick = true;
            super.post(message);
            timer.notifyObject(LOCK);
        }
    }

    @Override
    public void postAt(Message message, long readyNs) {
        synchronized (LOCK) {
            postMessageTick = true;
            delayedBag.add(message, readyNs);
            timer.notifyObject(LOCK);
        }
    }

    @Override
    public void cancelMessages(MessagePredicate predicate) {
        synchronized (LOCK) {
            super.removeMessages(predicate);
            delayedBag.removeMessages(predicate);
        }
    }

    @Override
    public void postAtFront(Message message) {
        synchronized (LOCK) {
            postMessageTick = true;
            super.postAtFront(message);
            timer.notifyObject(LOCK);
        }
    }
}
