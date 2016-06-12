package com.birbit.android.jobqueue.messaging.message;

import com.birbit.android.jobqueue.messaging.Message;
import com.birbit.android.jobqueue.messaging.Type;

public class CommandMessage extends Message {
    public static final int QUIT = 1;
    public static final int POKE = 2; // simple message to wake it up
    public static final int RUNNABLE = 3; // only used in tests
    private int what;

    private Runnable runnable;

    public CommandMessage() {
        super(Type.COMMAND);
    }

    @Override
    protected void onRecycled() {
        what = -1;
        runnable = null;
    }

    public int getWhat() {
        return what;
    }

    public void set(int what) {
        this.what = what;
    }

    public Runnable getRunnable() {
        return runnable;
    }

    public void setRunnable(Runnable runnable) {
        this.runnable = runnable;
    }

    @Override
    public String toString() {
        return "Command[" + what + "]";
    }
}
