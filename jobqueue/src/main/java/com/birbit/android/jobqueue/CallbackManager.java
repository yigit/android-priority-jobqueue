package com.birbit.android.jobqueue;

import com.birbit.android.jobqueue.messaging.Message;
import com.birbit.android.jobqueue.messaging.MessageFactory;
import com.birbit.android.jobqueue.messaging.MessageQueueConsumer;
import com.birbit.android.jobqueue.messaging.SafeMessageQueue;
import com.birbit.android.jobqueue.messaging.Type;
import com.birbit.android.jobqueue.messaging.message.CallbackMessage;
import com.birbit.android.jobqueue.messaging.message.CancelResultMessage;
import com.birbit.android.jobqueue.messaging.message.CommandMessage;
import com.birbit.android.jobqueue.messaging.message.PublicQueryMessage;
import com.birbit.android.jobqueue.CancelResult;
import com.birbit.android.jobqueue.Job;
import com.birbit.android.jobqueue.callback.JobManagerCallback;
import com.birbit.android.jobqueue.timer.Timer;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Handles callbacks to user code.
 * <p>
 * Although this costs an additional thread, it is worth for the benefit of isolation.
 */
public class CallbackManager {
    final SafeMessageQueue messageQueue;
    private final CopyOnWriteArrayList<JobManagerCallback> callbacks;
    private final MessageFactory factory;
    private final AtomicInteger callbacksSize = new AtomicInteger(0);
    private final Timer timer;
    private final AtomicBoolean started = new AtomicBoolean(false);
    public CallbackManager(MessageFactory factory, Timer timer) {
        this.timer = timer;
        this.messageQueue = new SafeMessageQueue(timer, factory, "jq_callback");
        callbacks = new CopyOnWriteArrayList<>();
        this.factory = factory;
    }

    void addCallback(JobManagerCallback callback) {
        callbacks.add(callback);
        callbacksSize.incrementAndGet();
        startIfNeeded();
    }

    private void startIfNeeded() {
        if (!started.getAndSet(true)) {
            start();
        }
    }

    boolean removeCallback(JobManagerCallback callback) {
        boolean removed = callbacks.remove(callback);
        if (removed) {
            callbacksSize.decrementAndGet();
        }
        return removed;
    }

    private void start() {
        new Thread(new Runnable() {
            @Override
            public void run() {

                messageQueue.consume(new MessageQueueConsumer() {
                    long lastDelivery = Long.MIN_VALUE;

                    @Override
                    public void onStart() {
                    }

                    @Override
                    public void handleMessage(Message message) {
                        if (message.type == Type.CALLBACK) {
                            CallbackMessage cm = (CallbackMessage) message;
                            deliverMessage(cm);
                            lastDelivery = timer.nanoTime();
                        } else if (message.type == Type.CANCEL_RESULT_CALLBACK) {
                            deliverCancelResult((CancelResultMessage) message);
                            lastDelivery = timer.nanoTime();
                        } else if (message.type == Type.COMMAND) {
                            CommandMessage command = (CommandMessage) message;
                            if (command.getWhat() == CommandMessage.QUIT) {
                                messageQueue.stop();
                                started.set(false);
                            }
                        } else if (message.type == Type.PUBLIC_QUERY) {
                            ((PublicQueryMessage) message).getCallback().onResult(0);
                        }
                    }

                    @Override
                    public void onIdle() {

                    }
                });
            }
        }, "job-manager-callbacks").start();
    }

    private void deliverCancelResult(CancelResultMessage message) {
        message.getCallback().onCancelled(message.getResult());
        startIfNeeded();
    }

    private void deliverMessage(CallbackMessage cm) {
        switch (cm.getWhat()) {
            case CallbackMessage.ON_ADDED:
                notifyOnAddedListeners(cm.getJob());
                break;
            case CallbackMessage.ON_AFTER_RUN:
                notifyAfterRunListeners(cm.getJob(), cm.getResultCode());
                break;
            case CallbackMessage.ON_CANCEL:
                notifyOnCancelListeners(cm.getJob(), cm.isByUserRequest());
                break;
            case CallbackMessage.ON_DONE:
                notifyOnDoneListeners(cm.getJob());
                break;
            case CallbackMessage.ON_RUN:
                notifyOnRunListeners(cm.getJob(), cm.getResultCode());
                break;
        }
    }

    private void notifyOnCancelListeners(Job job, boolean byCancelRequest) {
        for (JobManagerCallback callback : callbacks) {
            callback.onJobCancelled(job, byCancelRequest);
        }
    }

    private void notifyOnRunListeners(Job job, int resultCode) {
        for (JobManagerCallback callback : callbacks) {
            callback.onJobRun(job, resultCode);
        }
    }

    private void notifyAfterRunListeners(Job job, int resultCode) {
        for (JobManagerCallback callback : callbacks) {
            callback.onAfterJobRun(job, resultCode);
        }
    }

    private void notifyOnDoneListeners(Job job) {
        for (JobManagerCallback callback : callbacks) {
            callback.onDone(job);
        }
    }

    private void notifyOnAddedListeners(Job job) {
        for (JobManagerCallback callback : callbacks) {
            callback.onJobAdded(job);
        }
    }

    public void notifyOnRun(Job job, int result) {
        if (!hasAnyCallbacks()) {
            return;
        }
        CallbackMessage callback = factory.obtain(CallbackMessage.class);
        callback.set(job, CallbackMessage.ON_RUN, result);
        messageQueue.post(callback);
    }

    private boolean hasAnyCallbacks() {
        return callbacksSize.get() > 0;
    }

    public void notifyAfterRun(Job job, int result) {
        if (!hasAnyCallbacks()) {
            return;
        }
        CallbackMessage callback = factory.obtain(CallbackMessage.class);
        callback.set(job, CallbackMessage.ON_AFTER_RUN, result);
        messageQueue.post(callback);
    }

    public void notifyOnCancel(Job job, boolean byCancelRequest) {
        if (!hasAnyCallbacks()) {
            return;
        }
        CallbackMessage callback = factory.obtain(CallbackMessage.class);
        callback.set(job, CallbackMessage.ON_CANCEL, byCancelRequest);
        messageQueue.post(callback);
    }

    public void notifyOnAdded(Job job) {
        if (!hasAnyCallbacks()) {
            return;
        }
        CallbackMessage callback = factory.obtain(CallbackMessage.class);
        callback.set(job, CallbackMessage.ON_ADDED);
        messageQueue.post(callback);
    }

    public void notifyOnDone(Job job) {
        if (!hasAnyCallbacks()) {
            return;
        }
        CallbackMessage callback = factory.obtain(CallbackMessage.class);
        callback.set(job, CallbackMessage.ON_DONE);
        messageQueue.post(callback);
    }

    public void notifyCancelResult(CancelResult result, CancelResult.AsyncCancelCallback callback) {
        CancelResultMessage message = factory.obtain(CancelResultMessage.class);
        message.set(callback, result);
        messageQueue.post(message);
        startIfNeeded();
    }

    public void destroy() {
        if (!started.get()) {
            return;
        }
        CommandMessage message = factory.obtain(CommandMessage.class);
        message.set(CommandMessage.QUIT);
        messageQueue.post(message);
    }
}
