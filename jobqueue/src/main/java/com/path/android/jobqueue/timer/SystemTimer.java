package com.path.android.jobqueue.timer;

import com.path.android.jobqueue.log.JqLog;

import java.util.concurrent.TimeUnit;

public class SystemTimer implements Timer {
    final long startWallClock;
    final long startNs;
    public SystemTimer() {
        JqLog.d("creating system timer");
        //noinspection DIRECT_TIME_ACCESS
        startWallClock = TimeUnit.MILLISECONDS.toNanos(System.currentTimeMillis());
        //noinspection DIRECT_TIME_ACCESS
        startNs = System.nanoTime();
    }

    @Override
    public long nanoTime() {
        //noinspection DIRECT_TIME_ACCESS
        return System.nanoTime() - startNs + startWallClock;
    }

    @Override
    public void waitOnObjectUntilNs(Object object, long untilNs) throws InterruptedException {
        long now = nanoTime();
        if (now > untilNs) {
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
