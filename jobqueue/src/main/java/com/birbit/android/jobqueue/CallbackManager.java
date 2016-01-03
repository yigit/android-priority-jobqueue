package com.birbit.android.jobqueue;

import com.birbit.android.jobqueue.messaging.Message;
import com.birbit.android.jobqueue.messaging.MessageFactory;
import com.birbit.android.jobqueue.messaging.MessageQueueConsumer;
import com.birbit.android.jobqueue.messaging.SafeMessageQueue;
import com.birbit.android.jobqueue.messaging.Type;
import com.birbit.android.jobqueue.messaging.message.CallbackMessage;
import com.path.android.jobqueue.JobHolder;
import com.path.android.jobqueue.callback.JobManagerCallback;
import com.path.android.jobqueue.log.JqLog;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Handles callbacks to user code.
 * <p>
 * Although this costs an additional thread, it is worth for the benefit of isolation.
 */
public class CallbackManager {
    private final SafeMessageQueue messageQueue;
    private final CopyOnWriteArrayList<JobManagerCallback> callbacks;
    private final MessageFactory factory;
    private AtomicInteger callbackSize = new AtomicInteger(0);
    public CallbackManager(MessageFactory factory) {
        this.messageQueue = new SafeMessageQueue();
        callbacks = new CopyOnWriteArrayList<>();
        this.factory = factory;
    }

    void addCallback(JobManagerCallback callback) {
        callbacks.add(callback);
        if (callbackSize.incrementAndGet() == 1) {
            start();
        }
    }

    boolean removeCallback(JobManagerCallback callback) {
        boolean removed = callbacks.remove(callback);
        if (removed && callbackSize.decrementAndGet() == 0) {
            messageQueue.stop();
            messageQueue.clear();
        }
        return removed;
    }

    private void start() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                messageQueue.consume(new MessageQueueConsumer() {
                    @Override
                    public void handleMessage(Message message) {
                        if (message.type == Type.CALLBACK) {
                            CallbackMessage cm = (CallbackMessage) message;
                            deliverMessage(cm);
                        }
                    }

                    @Override
                    public void onIdle() {

                    }
                });
            }
        }, "job-manager-callbacks");
    }

    private void deliverMessage(CallbackMessage cm) {
        switch (cm.getWhat()) {
            case CallbackMessage.ON_ADDED:
                notifyOnAddedListeners(cm.getJobHolder());
                break;
            case CallbackMessage.ON_AFTER_RUN:
                notifyAfterRunListeners(cm.getJobHolder(), cm.getResultCode());
                break;
            case CallbackMessage.ON_CANCEL:
                notifyOnCancelListeners(cm.getJobHolder(), cm.isByUserRequest());
                break;
            case CallbackMessage.ON_DONE:
                notifyOnDoneListeners(cm.getJobHolder());
                break;
            case CallbackMessage.ON_RUN:
                notifyOnRunListeners(cm.getJobHolder(), cm.getResultCode());
                break;
        }
    }

    private void notifyOnCancelListeners(JobHolder jobHolder, boolean byCancelRequest) {
        for (JobManagerCallback callback : callbacks) {
            callback.onJobCancelled(jobHolder.getJob(), byCancelRequest);
        }
    }

    private void notifyOnRunListeners(JobHolder jobHolder, int resultCode) {
        for (JobManagerCallback callback : callbacks) {
            callback.onJobRun(jobHolder.getJob(), resultCode);
        }
    }

    private void notifyAfterRunListeners(JobHolder jobHolder, int resultCode) {
        for (JobManagerCallback callback : callbacks) {
            callback.onAfterJobRun(jobHolder.getJob(), resultCode);
        }
    }

    private void notifyOnDoneListeners(JobHolder jobHolder) {
        for (JobManagerCallback callback : callbacks) {
            callback.onDone(jobHolder.getJob());
        }
    }

    private void notifyOnAddedListeners(JobHolder jobHolder) {
        for (JobManagerCallback callback : callbacks) {
            callback.onJobAdded(jobHolder.getJob());
        }
    }

    public void notifyOnRun(JobHolder jobHolder, int result) {
        if (callbackSize.get() == 0) {
            return;// no consumers
        }
        CallbackMessage callback = factory.obtain(CallbackMessage.class);
        callback.set(jobHolder, CallbackMessage.ON_RUN, result);
        messageQueue.post(callback);
    }

    public void notifyAfterRun(JobHolder jobHolder, int result) {
        if (callbackSize.get() == 0) {
            return;// no consumers
        }
        CallbackMessage callback = factory.obtain(CallbackMessage.class);
        callback.set(jobHolder, CallbackMessage.ON_AFTER_RUN, result);
        messageQueue.post(callback);
    }

    public void notifyOnCancel(JobHolder jobHolder, boolean byCancelRequest) {
        if (callbackSize.get() == 0) {
            return;// no consumers
        }
        CallbackMessage callback = factory.obtain(CallbackMessage.class);
        callback.set(jobHolder, CallbackMessage.ON_CANCEL, byCancelRequest);
        messageQueue.post(callback);
    }

    public void notifyOnAdded(JobHolder jobHolder) {
        if (callbackSize.get() == 0) {
            return;// no consumers
        }
        CallbackMessage callback = factory.obtain(CallbackMessage.class);
        callback.set(jobHolder, CallbackMessage.ON_ADDED);
        messageQueue.post(callback);
    }

    public void notifyOnDone(JobHolder jobHolder) {
        if (callbackSize.get() == 0) {
            return;// no consumers
        }
        CallbackMessage callback = factory.obtain(CallbackMessage.class);
        callback.set(jobHolder, CallbackMessage.ON_DONE);
        messageQueue.post(callback);
    }
}
