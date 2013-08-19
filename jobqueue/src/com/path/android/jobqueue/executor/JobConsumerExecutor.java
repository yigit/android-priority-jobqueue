package com.path.android.jobqueue.executor;

import com.path.android.jobqueue.JobHolder;
import com.path.android.jobqueue.log.JqLog;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import com.path.android.jobqueue.config.Configuration;

public class JobConsumerExecutor {
    private int maxConsumerSize;
    private int minConsumerSize;
    private final ThreadGroup threadGroup;
    private int loadFactor;
    private final Contract contract;
    private final int keepAliveSeconds;
    private final AtomicInteger activeConsumerCount = new AtomicInteger(0);
    private final AtomicInteger runningJobCount = new AtomicInteger(0);


    public JobConsumerExecutor(Configuration config, Contract contract) {
        this.loadFactor = config.getLoadFactor();
        this.maxConsumerSize = config.getMaxConsumerCount();
        this.minConsumerSize = config.getMinConsumerCount();
        this.keepAliveSeconds = config.getConsumerKeepAlive();
        this.contract = contract;
        threadGroup = new ThreadGroup("JobConsumers");
    }

    public void considerAddingConsumer() {
        doINeedANewThread(false, true);
    }

    private boolean canIDie() {
        if(doINeedANewThread(true, false) == false) {
            return true;
        }
        return false;
    }

    private boolean doINeedANewThread(boolean inConsumerThread, boolean addIfNeeded) {
        //if network provider cannot notify us, we have to busy wait
        if(contract.isRunning() == false) {
            if(inConsumerThread) {
                activeConsumerCount.decrementAndGet();
            }
            return false;
        }

        synchronized (threadGroup) {
            if(isAboveLoadFactor(inConsumerThread) && canAddMoreConsumers()) {
                if(addIfNeeded) {
                    addConsumer();
                }
                return true;
            }
        }
        if(inConsumerThread) {
            activeConsumerCount.decrementAndGet();
        }
        return false;
    }

    private void addConsumer() {
        JqLog.d("adding another consumer");
        synchronized (threadGroup) {
            Thread thread = new Thread(threadGroup, new JobConsumer(contract, this));
            activeConsumerCount.incrementAndGet();
            thread.start();
        }
    }

    private boolean canAddMoreConsumers() {
        synchronized (threadGroup) {
            //there is a race condition for the time thread if about to finish
            return activeConsumerCount.intValue() < maxConsumerSize;
        }
    }

    private boolean isAboveLoadFactor(boolean inConsumerThread) {
        synchronized (threadGroup) {
            //if i am called from a consumer thread, don't count me
            int consumerCnt = activeConsumerCount.intValue() - (inConsumerThread ? 1 : 0);
            boolean res =
                    consumerCnt < minConsumerSize ||
                    consumerCnt * loadFactor < contract.countRemainingReadyJobs() + runningJobCount.get();
            if(JqLog.isDebugEnabled()) {
                JqLog.d("%s: load factor check. %s = (%d < %d)|| (%d * %d < %d + %d). consumer thread: %s", Thread.currentThread().getName(), res,
                        consumerCnt, minConsumerSize,
                        consumerCnt, loadFactor, contract.countRemainingReadyJobs(), runningJobCount.get(), inConsumerThread);
            }
            return res;
        }

    }

    public static interface Contract {
        public boolean isRunning();
        public void insertOrReplace(JobHolder jobHolder);
        public void removeJob(JobHolder jobHolder);
        public JobHolder getNextJob(int wait, TimeUnit waitDuration);
        public int countRemainingReadyJobs();
    }

    private static class JobConsumer implements Runnable {
        private final Contract contract;
        private final JobConsumerExecutor executor;
        private boolean didRunOnce = false;
        public JobConsumer(Contract contract, JobConsumerExecutor executor) {
            this.executor = executor;
            this.contract = contract;
        }

        @Override
        public void run() {
            boolean canDie;
            do {
                try {
                    if(JqLog.isDebugEnabled()) {
                        if(didRunOnce == false) {
                            JqLog.d("starting consumer %s", Thread.currentThread().getName());
                            didRunOnce = true;
                        } else {
                            JqLog.d("re-running consumer %s", Thread.currentThread().getName());
                        }
                    }
                    JobHolder nextJob;
                    do {
                        nextJob = contract.isRunning() ? contract.getNextJob(executor.keepAliveSeconds, TimeUnit.SECONDS) : null;
                        if (nextJob != null) {
                            executor.runningJobCount.incrementAndGet();
                            if (nextJob.safeRun(nextJob.getRunCount())) {
                                contract.removeJob(nextJob);
                            } else {
                                contract.insertOrReplace(nextJob);
                            }
                            executor.runningJobCount.decrementAndGet();
                        }
                    } while (nextJob != null);
                } finally {
                    //to avoid creating a new thread for no reason, consider not killing this one first
                    //if all threads die at the same time, this may cause a problem
                    canDie = executor.canIDie();
                    if(JqLog.isDebugEnabled()) {
                        if(canDie) {
                            JqLog.d("finishing consumer %s", Thread.currentThread().getName());
                        } else {
                            JqLog.d("didn't allow me to die, re-running %s", Thread.currentThread().getName());
                        }
                    }
                }
            } while (!canDie);
        }
    }
}
