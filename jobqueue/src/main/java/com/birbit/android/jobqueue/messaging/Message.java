package com.birbit.android.jobqueue.messaging;

abstract public class Message {
    public final Type type;
    // used by the pool
    Message next;

    protected Message(Type type) {
        this.type = type;
    }

    abstract protected void onRecycled();

    final void recycle() {
        next = null;
        onRecycled();
    }
}
