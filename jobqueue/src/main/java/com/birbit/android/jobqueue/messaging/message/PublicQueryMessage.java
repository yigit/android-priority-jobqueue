package com.birbit.android.jobqueue.messaging.message;

import com.birbit.android.jobqueue.IntCallback;
import com.birbit.android.jobqueue.messaging.Message;
import com.birbit.android.jobqueue.messaging.Type;

public class PublicQueryMessage extends Message {
    public static final int COUNT = 0;
    public static final int COUNT_READY = 1;
    public static final int START = 2;
    public static final int STOP = 3;
    public static final int JOB_STATUS = 4;
    public static final int CLEAR = 5;

    private IntCallback callback;
    private int what;
    private long longArg;
    private boolean booleanArg;

    public PublicQueryMessage() {
        super(Type.PUBLIC_QUERY);
    }

    public void set(int what, IntCallback callback) {
        this.callback = callback;
        this.what = what;
    }

    public void set(int what, long longArg, boolean booleanArg, IntCallback callback) {
        this.what = what;
        this.longArg = longArg;
        this.booleanArg = booleanArg;
        this.callback = callback;
    }

    public IntCallback getCallback() {
        return callback;
    }

    public int getWhat() {
        return what;
    }

    public long getLongArg() {
        return longArg;
    }

    public boolean getBooleanArg() {
        return booleanArg;
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
