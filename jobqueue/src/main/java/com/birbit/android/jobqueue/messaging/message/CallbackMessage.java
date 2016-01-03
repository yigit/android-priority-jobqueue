package com.birbit.android.jobqueue.messaging.message;

import com.birbit.android.jobqueue.messaging.Message;
import com.birbit.android.jobqueue.messaging.Type;
import com.path.android.jobqueue.Job;
import com.path.android.jobqueue.JobHolder;

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
    private JobHolder jobHolder;
    public CallbackMessage() {
        super(Type.CALLBACK);
    }

    @Override
    protected void onRecycled() {
        jobHolder = null;
    }

    public void set(JobHolder jobHolder, int what) {
        this.what = what;
        this.jobHolder = jobHolder;
    }

    public void set(JobHolder jobHolder, int what, int resultCode) {
        this.what = what;
        this.resultCode = resultCode;
        this.jobHolder = jobHolder;
    }

    public void set(JobHolder jobHolder, int what, boolean byUserRequest) {
        this.what = what;
        this.byUserRequest = byUserRequest;
        this.jobHolder = jobHolder;
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

    public JobHolder getJobHolder() {
        return jobHolder;
    }
}
