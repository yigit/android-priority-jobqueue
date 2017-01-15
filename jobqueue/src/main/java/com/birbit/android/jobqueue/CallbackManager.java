package com.birbit.android.jobqueue;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;

import com.birbit.android.jobqueue.callback.JobManagerCallback;
import com.birbit.android.jobqueue.log.JqLog;
import com.birbit.android.jobqueue.messaging.Message;
import com.birbit.android.jobqueue.messaging.MessageFactory;
import com.birbit.android.jobqueue.messaging.MessageQueueConsumer;
import com.birbit.android.jobqueue.messaging.SafeMessageQueue;
import com.birbit.android.jobqueue.messaging.Type;
import com.birbit.android.jobqueue.messaging.message.CallbackMessage;
import com.birbit.android.jobqueue.messaging.message.CancelResultMessage;
import com.birbit.android.jobqueue.messaging.message.CommandMessage;
import com.birbit.android.jobqueue.messaging.message.PublicQueryMessage;
import com.birbit.android.jobqueue.timer.Timer;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
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

    void addCallback(@NonNull JobManagerCallback callback) {
        callbacks.add(callback);
        callbacksSize.incrementAndGet();
        startIfNeeded();
    }

    private void startIfNeeded() {
        if (!started.getAndSet(true)) {
            start();
        }
    }

    /**
     * convenience method to wait for existing callbacks to be consumed
     */
    @VisibleForTesting
    public boolean waitUntilAllMessagesAreConsumed(int seconds) {
        final CountDownLatch latch = new CountDownLatch(1);
        CommandMessage poke = factory.obtain(CommandMessage.class);
        poke.set(CommandMessage.RUNNABLE);
        poke.setRunnable(new Runnable() {
            @Override
            public void run() {
                latch.countDown();
            }
        });
        messageQueue.post(poke);
        try {
            return latch.await(seconds, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            return false;
        }
    }

    boolean removeCallback(@NonNull JobManagerCallback callback) {
        boolean removed = callbacks.remove(callback);
        if (removed) {
            callbacksSize.decrementAndGet();
        }
        return removed;
    }

    private void start() {
        Thread callbackThread = new Thread(new Runnable() {
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
                            final int what = command.getWhat();
                            if (what == CommandMessage.QUIT) {
                                messageQueue.stop();
                                started.set(false);
                            } else if (what == CommandMessage.RUNNABLE) {
                                command.getRunnable().run();
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
        }, "job-manager-callbacks");
        try {
            callbackThread.start();
        } catch (InternalError error) {
            // process is already dying, no reason to crash for this (and hide the real crash)
            JqLog.e(error, "Cannot start a thread. Looks like app is shutting down."
                    + "See issue #294 for details.");
        }
    }

    private void deliverCancelResult(@NonNull CancelResultMessage message) {
        message.getCallback().onCancelled(message.getResult());
        startIfNeeded();
    }

    private void deliverMessage(@NonNull CallbackMessage cm) {
        switch (cm.getWhat()) {
            case CallbackMessage.ON_ADDED:
                notifyOnAddedListeners(cm.getJob());
                break;
            case CallbackMessage.ON_AFTER_RUN:
                notifyAfterRunListeners(cm.getJob(), cm.getResultCode());
                break;
            case CallbackMessage.ON_CANCEL:
                notifyOnCancelListeners(cm.getJob(), cm.isByUserRequest(), cm.getThrowable());
                break;
            case CallbackMessage.ON_DONE:
                notifyOnDoneListeners(cm.getJob());
                break;
            case CallbackMessage.ON_RUN:
                notifyOnRunListeners(cm.getJob(), cm.getResultCode());
                break;
        }
    }

    private void notifyOnCancelListeners(@NonNull Job job, boolean byCancelRequest, @Nullable Throwable throwable) {
        for (JobManagerCallback callback : callbacks) {
            callback.onJobCancelled(job, byCancelRequest, throwable);
        }
    }

    private void notifyOnRunListeners(@NonNull Job job, int resultCode) {
        for (JobManagerCallback callback : callbacks) {
            callback.onJobRun(job, resultCode);
        }
    }

    private void notifyAfterRunListeners(@NonNull Job job, int resultCode) {
        for (JobManagerCallback callback : callbacks) {
            callback.onAfterJobRun(job, resultCode);
        }
    }

    private void notifyOnDoneListeners(@NonNull Job job) {
        for (JobManagerCallback callback : callbacks) {
            callback.onDone(job);
        }
    }

    private void notifyOnAddedListeners(@NonNull Job job) {
        for (JobManagerCallback callback : callbacks) {
            callback.onJobAdded(job);
        }
    }

    public void notifyOnRun(@NonNull Job job, int result) {
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

    public void notifyAfterRun(@NonNull Job job, int result) {
        if (!hasAnyCallbacks()) {
            return;
        }
        CallbackMessage callback = factory.obtain(CallbackMessage.class);
        callback.set(job, CallbackMessage.ON_AFTER_RUN, result);
        messageQueue.post(callback);
    }

    public void notifyOnCancel(@NonNull Job job, boolean byCancelRequest, @Nullable Throwable throwable) {
        if (!hasAnyCallbacks()) {
            return;
        }
        CallbackMessage callback = factory.obtain(CallbackMessage.class);
        callback.set(job, CallbackMessage.ON_CANCEL, byCancelRequest, throwable);
        messageQueue.post(callback);
    }

    public void notifyOnAdded(@NonNull Job job) {
        if (!hasAnyCallbacks()) {
            return;
        }
        CallbackMessage callback = factory.obtain(CallbackMessage.class);
        callback.set(job, CallbackMessage.ON_ADDED);
        messageQueue.post(callback);
    }

    public void notifyOnDone(@NonNull Job job) {
        if (!hasAnyCallbacks()) {
            return;
        }
        CallbackMessage callback = factory.obtain(CallbackMessage.class);
        callback.set(job, CallbackMessage.ON_DONE);
        messageQueue.post(callback);
    }

    public void notifyCancelResult(@NonNull CancelResult result, @NonNull CancelResult.AsyncCancelCallback callback) {
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
