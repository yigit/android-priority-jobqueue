package com.birbit.android.jobqueue.messaging;

import com.path.android.jobqueue.log.JqLog;
import com.path.android.jobqueue.timer.Timer;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class SafeMessageQueue extends UnsafeMessageQueue implements MessageQueue {
    private static final long ALARM_UNDEFINED = Long.MAX_VALUE;
    private final Object LOCK = new Object();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final Timer timer;
    private Long wakeUpAtNs = ALARM_UNDEFINED;
    public SafeMessageQueue(Timer timer) {
        super();
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
            LOCK.notifyAll();
        }
    }

    public void clear() {
        synchronized (LOCK) {
            super.clear();
        }
    }

    Message next(MessageQueueConsumer consumer) {
        synchronized (LOCK) {
            while (true) {
                Message message = super.next();
                if (message == null) {
                    try {
                        consumer.onIdle();
                        if (wakeUpAtNs != ALARM_UNDEFINED) {
                            long waitInNs = wakeUpAtNs - timer.nanoTime();
                            wakeUpAtNs = ALARM_UNDEFINED;
                            if (waitInNs > 0) {
                                timer.waitOnObject(LOCK, TimeUnit.NANOSECONDS.toMillis(waitInNs));
                            } else {
                                timer.waitOnObject(LOCK);
                            }
                        } else {
                            timer.waitOnObject(LOCK);
                        }

                    } catch (InterruptedException e) {
                        JqLog.e(e, "message queue is interrupted");
                    }
                } else {
                    return message;
                }
            }
        }
    }

    @Override
    public void post(Message message) {
        synchronized (LOCK) {
            super.post(message);
            LOCK.notifyAll();
        }
    }

    public void wakeUpAtNsIfIdle(long timeInNs) {
        synchronized (LOCK) {
            if (wakeUpAtNs > timeInNs) {
                wakeUpAtNs = timeInNs;
                LOCK.notifyAll();
            }
        }
    }

    @Override
    public void postAtFront(Message message) {
        synchronized (LOCK) {
            super.postAtFront(message);
            LOCK.notifyAll();
        }
    }
}
