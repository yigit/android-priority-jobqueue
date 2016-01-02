package com.path.android.jobqueue.test.timer;

import com.path.android.jobqueue.JobManager;
import com.path.android.jobqueue.timer.Timer;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MockTimer implements Timer {
    private long now;
    private volatile Thread incrementThread;
    private ArrayList<ObjectWait> waitingList = new ArrayList<>();
    private ExecutorService callbackRunner = Executors.newSingleThreadExecutor();
    // MockTimer introduces a potential race condition in waits which may prevent it from stopping
    // properly. To avoid this, it keeps a track of all objects it should notify and notify them
    // multiple times when stopped.
    private Map<Object, Boolean> anyObjectToNotify = new WeakHashMap<>();
    private volatile boolean stopped = false;
    PriorityQueue<RunnableItem> runnableQueue = new PriorityQueue<>(200, new Comparator<RunnableItem>() {
        @Override
        public int compare(RunnableItem lhs, RunnableItem rhs) {
            return Long.compare(lhs.executionTime, rhs.executionTime);
        }
    });

    @Override
    public long nanoTime() {
        return now;
    }

    public void incrementMs(long time) {
        setNow(now + time * JobManager.NS_PER_MS);
    }

    public void incrementNs(long time) {
        setNow(now + time);
    }

    public synchronized void setNow(long now) {
        this.now = now;
        invokeTasks(now);
        notifyObjectWaits(now);
    }

    private void notifyObjectWaits(long now) {
        synchronized (waitingList) {
            for (int i = waitingList.size() - 1; i >= 0; i --) {
                ObjectWait objectWait = waitingList.get(i);
                if (objectWait.timeUntil < now) {
                    synchronized (objectWait.target) {
                        waitingList.remove(i);
                        objectWait.target.notifyAll();
                    }
                }
            }
        }
    }

    private synchronized void invokeTasks(final long now) {
        callbackRunner.execute(new Runnable() {
            @Override
            public void run() {
                while (runnableQueue.size() > 0) {
                    RunnableItem item = runnableQueue.poll();
                    if (item.executionTime <= now) {
                        item.runnable.run();
                    } else {
                        runnableQueue.offer(item);
                        break;
                    }
                }
            }
        });
    }

    @Override
    public synchronized void schedule(Runnable runnable, long waitInNs) {
        runnableQueue.offer(new RunnableItem(now + waitInNs, runnable));
        if (waitInNs < 1) {
            // always call in another thread
            new Thread(new Runnable() {
                @Override
                public void run() {
                    invokeTasks(now);
                }
            }).start();
        }
    }

    @Override
    public void waitOnObject(Object object, long timeout) throws InterruptedException {
        // all waits are w/o a timeout because time is controlled by the mock timer
        synchronized (this) {
            if (stopped) {
                return;
            }
        }
        synchronized (waitingList) {
            synchronized (anyObjectToNotify) {
                anyObjectToNotify.put(object, true);
            }
            ObjectWait objectWait = new ObjectWait(object, timeout > 0 ? now + timeout : Long.MAX_VALUE);
            waitingList.add(objectWait);
        }
        object.wait();
    }

    @Override
    public void notifyObject(Object object) {
        synchronized (waitingList) {
            for (int i = waitingList.size() - 1; i >= 0; i--) {
                ObjectWait objectWait = waitingList.get(i);
                if (objectWait.target == object) {
                    waitingList.remove(i);
                    object.notifyAll();
                }
            }
        }
    }

    @Override
    public void waitUntilDrained() {
        synchronized (this) {
            stopped = true;
        }
        invokeTasks(Long.MAX_VALUE);
        synchronized (this) {
            notifyObjectWaits(Long.MAX_VALUE);
        }
        synchronized (anyObjectToNotify) {
            for (Map.Entry<Object, Boolean> entry : anyObjectToNotify.entrySet()) {
                Object obj = entry.getKey();
                synchronized (obj) {
                    obj.notifyAll();
                }
            }
        }
    }

    @Override
    public void waitOnObject(Object object) throws InterruptedException {
        waitOnObject(object, Long.MAX_VALUE - 1);
    }

    private static class RunnableItem {
        final long executionTime;
        final Runnable runnable;

        public RunnableItem(long executionTime, Runnable runnable) {
            this.executionTime = executionTime;
            this.runnable = runnable;
        }
    }

    private static class ObjectWait {
        final long timeUntil;
        final Object target;
        public ObjectWait(Object target, long time) {
            timeUntil = time;
            this.target = target;
        }
    }
}
