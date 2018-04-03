package com.tarkalabs.android.jobqueue.timer;

public interface Timer {
    long nanoTime();
    void waitOnObjectUntilNs(Object object, long timeout) throws InterruptedException;
    void notifyObject(Object object);
    void waitOnObject(Object object) throws InterruptedException;
}
