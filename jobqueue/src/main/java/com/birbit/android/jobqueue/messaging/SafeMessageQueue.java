package com.birbit.android.jobqueue.messaging;

import com.birbit.android.jobqueue.messaging.message.CommandMessage;
import com.path.android.jobqueue.log.JqLog;
import com.path.android.jobqueue.timer.Timer;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class SafeMessageQueue extends UnsafeMessageQueue implements MessageQueue {
    private final Object LOCK = new Object();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final Timer timer;
    private final DelayedMessageBag delayedBag;

    public SafeMessageQueue(Timer timer) {
        super();
        this.timer = timer;
        this.delayedBag = new DelayedMessageBag();
    }

    public boolean isRunning() {
        return running.get();
    }

    public void consume(MessageQueueConsumer consumer) {
        if(running.getAndSet(true)) {
            throw new IllegalStateException("only 1 consumer per MQ");
        }
        consumer.onStart();
        while (running.get()) {
            Message message = next(consumer);
            consumer.handleMessage(message);
        }
        JqLog.d("finished queue %s", id);
    }

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
        synchronized (LOCK) {
            while (true) {
                long now = timer.nanoTime();
                long nextDelayedReadyAt = delayedBag.flushReadyMessages(now, this);
                Message message = super.next();
                if (message != null) {
                    return message;
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
            super.post(message);
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
    public void postAtFront(Message message) {
        synchronized (LOCK) {
            super.postAtFront(message);
            timer.notifyObject(LOCK);
        }
    }

    public void clearDelayedMessages() {
        synchronized (LOCK) {
            delayedBag.clear();
        }
    }
}
