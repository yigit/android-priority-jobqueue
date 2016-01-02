package com.path.android.jobqueue.timer;

import com.path.android.jobqueue.JobHolder;

import java.util.concurrent.ConcurrentHashMap;

public interface Timer {
    long nanoTime();
    void schedule(Runnable runnable, long waitInNs);
    void waitOnObject(Object object, long timeout) throws InterruptedException;
    void notifyObject(Object object);
    void waitUntilDrained();

    void waitOnObject(Object object) throws InterruptedException;
}
