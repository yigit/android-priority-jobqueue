package com.path.android.jobqueue.timer;

import android.annotation.SuppressLint;

import com.path.android.jobqueue.JobManager;
import com.path.android.jobqueue.log.JqLog;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class SystemTimer implements Timer {
    public SystemTimer() {
        JqLog.d("creating system timer");
    }

    @Override
    public long nanoTime() {
        //noinspection DIRECT_TIME_ACCESS
        return System.nanoTime();
    }

    @Override
    public void waitOnObjectUntilNs(Object object, long untilNs) throws InterruptedException {
        //noinspection DIRECT_TIME_ACCESS
        long now = System.nanoTime();
        if (now < untilNs) {
            //noinspection TIMED_WAIT
            object.wait(1);
        } else {
            long ms = TimeUnit.NANOSECONDS.toMillis(untilNs - now);
            if (ms > 0) {
                //noinspection TIMED_WAIT
                object.wait(ms);
            } else {
                //noinspection TIMED_WAIT
                object.wait(1);
            }
        }
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
}
