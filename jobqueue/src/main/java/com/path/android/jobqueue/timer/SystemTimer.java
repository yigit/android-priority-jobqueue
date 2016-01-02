package com.path.android.jobqueue.timer;

import android.annotation.SuppressLint;

import com.path.android.jobqueue.log.JqLog;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class SystemTimer implements Timer {
    private volatile ScheduledExecutorService timedExecutor;

    public SystemTimer() {
        JqLog.d("creating system timer");
    }

    @Override
    public long nanoTime() {
        //noinspection DIRECT_TIME_ACCESS
        return System.nanoTime();
    }

    @Override
    public void schedule(Runnable runnable, long waitInNs) {
        //noinspection ConstantConditions
        getTimedExecutor(true).schedule(runnable, waitInNs, TimeUnit.NANOSECONDS);
    }

    @Override
    public void waitOnObject(Object object, long timeout) throws InterruptedException {
        //noinspection TIMED_WAIT
        object.wait(timeout);
    }

    @Override
    public void waitOnObject(Object object) throws InterruptedException {
        //noinspection TIMED_WAIT
        object.wait();
    }

    @Override
    public void notifyObject(Object object) {
        //noinspection NOTIFY_ON_OBJECT
        object.notifyAll();
    }

    @Override
    public void waitUntilDrained() {
        ScheduledExecutorService executor = getTimedExecutor(false);
        if (executor == null) {
            return;
        }
        synchronized (this) {
            executor.shutdown();
            try {
                executor.awaitTermination(100, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                JqLog.e(e, "received interrupt while waiting for scheduled tasks.");
            }
            this.timedExecutor = null;
        }
    }

    private ScheduledExecutorService getTimedExecutor(boolean createIfNotExists) {
        if (timedExecutor == null) {
            if (!createIfNotExists) {
                return null;
            }
            synchronized (this) {
                if (timedExecutor == null) {
                    timedExecutor = Executors.newSingleThreadScheduledExecutor();
                }
            }
        }
        return timedExecutor;
    }
}
