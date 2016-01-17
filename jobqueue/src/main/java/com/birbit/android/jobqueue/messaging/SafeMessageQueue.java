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
    // used to check if any new message is posted inside sync block
    private boolean postMessageTick = false;
    public SafeMessageQueue(Timer timer) {
        super();
        this.timer = timer;
        this.delayedBag = new DelayedMessageBag();
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
            }
        }
        JqLog.d("finished queue %s", id);
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
        synchronized (LOCK) {
            while (running.get()) {
                long now = timer.nanoTime();
                Long nextDelayedReadyAt = delayedBag.flushReadyMessages(now, this);
                Message message = super.next();
                if (message != null) {
                    return message;
                }
                if (!calledIdle) {
                    postMessageTick = false;
                    JqLog.d("calling on idle");
                    consumer.onIdle();
                    JqLog.d("did idle post message? %s", postMessageTick);
                    calledIdle = true;
                    if (postMessageTick) {
                        continue; // callback added a message, requery
                    }
                }
                if (nextDelayedReadyAt != null && nextDelayedReadyAt <= now) {
                    continue;
                }
                try {
                    if (nextDelayedReadyAt == null) {
                        timer.waitOnObject(LOCK);
                    } else {
                        timer.waitOnObjectUntilNs(LOCK, nextDelayedReadyAt);
                    }
                }  catch (InterruptedException ignored) {}
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
