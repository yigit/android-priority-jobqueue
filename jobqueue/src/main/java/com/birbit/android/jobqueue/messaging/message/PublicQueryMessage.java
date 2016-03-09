package com.birbit.android.jobqueue.messaging.message;

import com.birbit.android.jobqueue.IntCallback;
import com.birbit.android.jobqueue.messaging.Message;
import com.birbit.android.jobqueue.messaging.Type;

public class PublicQueryMessage extends Message implements IntCallback.MessageWithCallback {
    public static final int COUNT = 0;
    public static final int COUNT_READY = 1;
    public static final int START = 2;
    public static final int STOP = 3;
    public static final int JOB_STATUS = 4;
    public static final int CLEAR = 5;
    public static final int ACTIVE_CONSUMER_COUNT = 6;
    public static final int SCHEDULER_START = 7;
    // used for testing
    public static final int INTERNAL_RUNNABLE = 101;

    private IntCallback callback;
    private int what = -1;
    private String stringArg;

    public PublicQueryMessage() {
        super(Type.PUBLIC_QUERY);
    }

    public void set(int what, IntCallback callback) {
        this.callback = callback;
        this.what = what;
    }

    public void set(int what, String stringArg, IntCallback callback) {
        this.what = what;
        this.stringArg = stringArg;
        this.callback = callback;
    }

    public IntCallback getCallback() {
        return callback;
    }

    public int getWhat() {
        return what;
    }

    public String getStringArg() {
        return stringArg;
    }

    public void setCallback(IntCallback callback) {
        this.callback = callback;
    }

    @Override
    protected void onRecycled() {
        callback = null;
        what = -1;
    }

    @Override
    public String toString() {
        return "PublicQuery[" + what + "]";
    }
}
