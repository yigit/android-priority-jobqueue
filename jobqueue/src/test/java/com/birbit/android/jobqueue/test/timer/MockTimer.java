package com.birbit.android.jobqueue.test.timer;

import com.birbit.android.jobqueue.JobManager;
import com.birbit.android.jobqueue.log.JqLog;
import com.birbit.android.jobqueue.timer.Timer;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class MockTimer implements Timer {
    private long now;
    private CopyOnWriteArrayList<ObjectWait> waitingList = new CopyOnWriteArrayList<>();
    // MockTimer introduces a potential race condition in waits which may prevent it from stopping
    // properly. To avoid this, it keeps a track of all objects it should notify and notify them
    // multiple times when stopped.
    private Map<Object, Boolean> anyObjectToNotify = new WeakHashMap<>();
    private volatile boolean stopped = false;

    @Override
    public synchronized long nanoTime() {
        return now;
    }

    public synchronized void incrementMs(long time) {
        setNow(now + time * JobManager.NS_PER_MS);
    }

    public synchronized void incrementNs(long time) {
        setNow(now + time);
    }

    public synchronized void setNow(long now) {
        this.now = now;
        JqLog.d("set time to %s", now);
        notifyObjectWaits(now);
    }

    private synchronized void notifyObjectWaits(long now) {
        JqLog.d("notify object waits by time %s", now);
        for (ObjectWait objectWait : waitingList) {
            JqLog.d("checking %s : %s for time %s",objectWait, objectWait.target,
                    objectWait.timeUntil);
            if (objectWait.timeUntil <= now) {
                final ObjectWait toWait = objectWait;
                waitingList.remove(objectWait);
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        synchronized (toWait.target) {
                            JqLog.d("notifying %s", toWait);
                            toWait.target.notifyAll();
                        }
                    }
                }).start();
            }
        }
        JqLog.d("done notifying by time");
    }

    @Override
    public void waitOnObjectUntilNs(Object object, long untilNs) throws InterruptedException {
        synchronized (this) {
            // all waits are w/o a timeout because time is controlled by the mock timer
            JqLog.d("wait on object request for %s timeout %s stopped: %s", object, untilNs,
                    stopped);
            if (stopped) {
                return;
            }
            if(untilNs < now) {
                return;
            }
            anyObjectToNotify.put(object, true);
            ObjectWait objectWait = new ObjectWait(object, untilNs);
            waitingList.add(objectWait);
            JqLog.d("will wait on object %s: %s for time %s forever:%s", objectWait,
                    objectWait.target,
                    objectWait.timeUntil, objectWait.timeUntil == ObjectWait.FOREVER);
        }
        object.wait();
    }

    @Override
    public void notifyObject(Object object) {
        JqLog.d("notify request for %s", object);
        for (ObjectWait objectWait : waitingList) {
            if (objectWait.target == object) {
                JqLog.d("notifying object %s", objectWait);
                waitingList.remove(objectWait);
                object.notifyAll();
            }
        }
        JqLog.d("done notifying objects");
    }

    @Override
    public void waitOnObject(Object object) throws InterruptedException {
        waitOnObjectUntilNs(object, ObjectWait.FOREVER);
    }

    static class ObjectWait {
        static final long FOREVER = Long.MAX_VALUE;
        final long timeUntil;
        final Object target;
        public ObjectWait(Object target, long time) {
            timeUntil = time;
            this.target = target;
        }
    }
}
