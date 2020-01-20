package com.birbit.android.jobqueue.messaging.message;

import androidx.annotation.Nullable;

import com.birbit.android.jobqueue.Job;
import com.birbit.android.jobqueue.messaging.Message;
import com.birbit.android.jobqueue.messaging.Type;

/**
 * Used for external callbacks to user code
 */
public class CallbackMessage extends Message {
    public static final int ON_ADDED = 1;
    public static final int ON_RUN = 2;
    public static final int ON_CANCEL = 3;
    public static final int ON_DONE = 4;
    public static final int ON_AFTER_RUN = 5;

    private int what;
    private int resultCode;
    private boolean byUserRequest;
    private Job job;
    @Nullable private Throwable throwable;

    public CallbackMessage() {
        super(Type.CALLBACK);
    }

    @Override
    protected void onRecycled() {
        job = null;
        throwable = null;
    }

    public void set(Job job, int what) {
        this.what = what;
        this.job = job;
    }

    public void set(Job job, int what, int resultCode) {
        this.what = what;
        this.resultCode = resultCode;
        this.job = job;
    }

    public void set(Job job, int what, boolean byUserRequest, @Nullable Throwable throwable) {
        this.what = what;
        this.byUserRequest = byUserRequest;
        this.job = job;
        this.throwable = throwable;
    }

    public int getWhat() {
        return what;
    }

    public int getResultCode() {
        return resultCode;
    }

    public boolean isByUserRequest() {
        return byUserRequest;
    }

    public Job getJob() {
        return job;
    }

    @Nullable
    public Throwable getThrowable() {
        return throwable;
    }
}
