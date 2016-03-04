package com.birbit.android.jobqueue.messaging.message;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.birbit.android.jobqueue.IntCallback;
import com.birbit.android.jobqueue.messaging.Message;
import com.birbit.android.jobqueue.messaging.Type;
import com.birbit.android.jobqueue.scheduling.SchedulerConstraint;

/**
 * The messages with the scheduler
 */
public class SchedulerMessage extends Message implements IntCallback.MessageWithCallback {
    public static final int START = 1;
    public static final int STOP = 2;

    private int what;
    @NonNull
    private SchedulerConstraint constraint;
    @Nullable
    private IntCallback resultCallback;

    public SchedulerMessage() {
        super(Type.SCHEDULER);
    }

    public void set(int what, SchedulerConstraint constraint) {
        this.what = what;
        this.constraint = constraint;
    }

    public int getWhat() {
        return what;
    }

    @NonNull
    public SchedulerConstraint getConstraint() {
        return constraint;
    }

    @Nullable
    public IntCallback getCallback() {
        return resultCallback;
    }

    @Override
    protected void onRecycled() {
        constraint = null;
        resultCallback = null;
    }

    @Override
    public void setCallback(IntCallback intCallback) {
        this.resultCallback = intCallback;
    }
}
