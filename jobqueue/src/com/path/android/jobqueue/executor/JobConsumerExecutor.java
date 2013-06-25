package com.path.android.jobqueue.executor;

import com.path.android.jobqueue.JobHolder;
import com.path.android.jobqueue.log.JqLog;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class JobConsumerExecutor {
    private int maxConsumerSize;
    private final ThreadGroup threadGroup;
    private int loadFactor = 3;//3 jobs per thread is OK
    private final Contract contract;
    private static final int WAIT_SECONDS = 15;//TODO make this a parameter
    private final AtomicInteger activeConsumerCount = new AtomicInteger(0);


    public JobConsumerExecutor(int maxConsumerSize, Contract contract) {
        this.contract = contract;
        this.maxConsumerSize = maxConsumerSize;
        threadGroup = new ThreadGroup("JobConsumers");
    }

    public void considerAddingConsumer() {
        //TODO if network provider cannot notify us, we have to busy wait
        if(contract.isRunning() == false && contract.canDetectNetworkChanges()) {
            return;
        }
        synchronized (threadGroup) {
            if(isAboveLoadFactor() && canAddMoreConsumers()) {
                addConsumer();
            }
        }
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

    private boolean isAboveLoadFactor() {
        synchronized (threadGroup) {
            return activeConsumerCount.intValue() * loadFactor < contract.countRemainingJobs();
        }
    }

    public static interface Contract {
        public boolean isRunning();
        public boolean canDetectNetworkChanges();
        public void insertOrReplace(JobHolder jobHolder);
        public void removeJob(JobHolder jobHolder);
        public JobHolder getNextJob(int wait, TimeUnit waitDuration);
        public int countRemainingJobs();
    }

    private static class JobConsumer implements Runnable {
        private final Contract contract;
        private final JobConsumerExecutor executor;
        public JobConsumer(Contract contract, JobConsumerExecutor executor) {
            this.executor = executor;
            this.contract = contract;
        }

        @Override
        public void run() {
            try {
                JqLog.d("starting consumer %s", Thread.currentThread().getName());
                JobHolder nextJob;
                do {
                    nextJob = contract.isRunning() ? contract.getNextJob(WAIT_SECONDS, TimeUnit.SECONDS) : null;
                    if (nextJob != null) {
                        if (nextJob.safeRun(nextJob.getRunCount())) {
                            contract.removeJob(nextJob);
                        } else {
                            contract.insertOrReplace(nextJob);
                        }
                    }
                } while (nextJob != null);
            } finally {
                //to avoid race conditions, we count active consumers ourselves
                JqLog.d("finishing consumer %s", Thread.currentThread().getName());
                executor.activeConsumerCount.decrementAndGet();
                executor.considerAddingConsumer();
            }
        }
    }
}
