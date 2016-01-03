package com.birbit.android.jobqueue;

import com.birbit.android.jobqueue.messaging.Message;
import com.birbit.android.jobqueue.messaging.MessageFactory;
import com.birbit.android.jobqueue.messaging.MessageQueue;
import com.birbit.android.jobqueue.messaging.MessageQueueConsumer;
import com.birbit.android.jobqueue.messaging.SafeMessageQueue;
import com.birbit.android.jobqueue.messaging.message.CommandMessage;
import com.birbit.android.jobqueue.messaging.message.JobConsumerIdleMessage;
import com.birbit.android.jobqueue.messaging.message.RunJobMessage;
import com.birbit.android.jobqueue.messaging.message.RunJobResultMessage;
import com.path.android.jobqueue.JobHolder;
import com.path.android.jobqueue.RetryConstraint;
import com.path.android.jobqueue.RunningJobSet;
import com.path.android.jobqueue.config.Configuration;
import com.path.android.jobqueue.log.JqLog;
import com.path.android.jobqueue.timer.Timer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The class that is used to manage job consumers
 */
class ConsumerController {
    private List<SafeMessageQueue> waitingWorkers = new ArrayList<>();
    private int totalConsumers = 0;
    private final int maxConsumerCount;
    private final int minConsumerCount;
    private final long consumerKeepAliveNs;
    private final int loadFactor;
    private final ThreadGroup threadGroup;
    private final Chef chef;
    private final Timer timer;
    private final MessageFactory factory;
    private final Map<String, JobHolder> runningJobHolders;
    final RunningJobSet runningJobGroups;

    ConsumerController(Chef chef, Timer timer, MessageFactory factory, Configuration configuration) {
        this.chef = chef;
        this.timer = timer;
        this.factory = factory;
        this.loadFactor = configuration.getLoadFactor();
        this.minConsumerCount = configuration.getMinConsumerCount();
        this.maxConsumerCount = configuration.getMaxConsumerCount();
        this.consumerKeepAliveNs = configuration.getConsumerKeepAlive() * 1000 * Chef.NS_PER_MS;
        runningJobHolders = new HashMap<>();
        runningJobGroups = new RunningJobSet(timer);
        threadGroup = new ThreadGroup("JobConsumers");
    }

    void onJobAdded() {
        considerAddingConsumers(false);
    }

    void handleConstraintChange() {
        considerAddingConsumers(true);
    }

    private void considerAddingConsumers(boolean pokeAllWaiting) {
        if (!chef.isRunning()) {
            return;
        }
        if (waitingWorkers.size() > 0) {
            for (int i = waitingWorkers.size() - 1; i >= 0; i--) {
                MessageQueue mq = waitingWorkers.remove(i);
                CommandMessage command = factory.obtain(CommandMessage.class);
                command.set(CommandMessage.POKE);
                mq.post(command);
                if (!pokeAllWaiting) {
                    break;
                }
            }
            return;
        }
        if (isAboveLoadFactor()) {
            addConsumer();
        }
    }

    private void addConsumer() {
        JqLog.d("adding another consumer");
        Thread thread = new Thread(threadGroup, new Worker(chef.messageQueue,
                new SafeMessageQueue(timer), factory, timer));
        totalConsumers++;
        thread.start();
    }

    private boolean isAboveLoadFactor() {
        return totalConsumers < maxConsumerCount &&
                (totalConsumers < minConsumerCount ||
                        totalConsumers * loadFactor <
                                chef.countRemainingReadyJobs() + runningJobHolders.size());
    }

    void handleIdle(JobConsumerIdleMessage message) {
        JobHolder nextJob = chef.getNextJob(runningJobGroups.getSafe());
        if (nextJob != null) {
            runningJobGroups.add(nextJob.getGroupId());
            RunJobMessage runJobMessage = factory.obtain(RunJobMessage.class);
            runJobMessage.setJobHolder(nextJob);
            runningJobHolders.put(nextJob.getJob().getUuid(), nextJob);
            if (nextJob.getGroupId() != null) {
                runningJobGroups.add(nextJob.getGroupId());
            }
            message.getConsumerQueue().post(runJobMessage);
        } else {
            long keepAliveTimeout = message.getLastJobCompleted() + consumerKeepAliveNs;
            if (!chef.isRunning() ||
                    (totalConsumers > minConsumerCount && keepAliveTimeout > timer.nanoTime())) {
                CommandMessage command = factory.obtain(CommandMessage.class);
                command.set(CommandMessage.QUIT);
                message.getConsumerQueue().post(command);
            } else {
                waitingWorkers.add(message.getConsumerQueue());
                message.getConsumerQueue().wakeUpAtNsIfIdle(keepAliveTimeout);
            }
        }
    }

    void handleRunJobResult(JobHolder jobHolder, RetryConstraint retryConstraint) {
        runningJobHolders.remove(jobHolder.getJob().getUuid());
        if (jobHolder.getGroupId() != null) {
            runningJobGroups.remove(jobHolder.getGroupId());
            if (retryConstraint != null && retryConstraint.willApplyNewDelayToGroup()
                    && retryConstraint.getNewDelayInMs() > 0) {
                runningJobGroups.addGroupUntil(jobHolder.getGroupId(),
                        timer.nanoTime() + retryConstraint.getNewDelayInMs() * Chef.NS_PER_MS);
            }
        }
    }

    static class Worker implements Runnable {
        final SafeMessageQueue messageQueue;
        final MessageQueue parentMessageQueue;
        final MessageFactory factory;
        final Timer timer;
        private long lastJobCompleted = Long.MIN_VALUE;
        public Worker(MessageQueue parentMessageQueue, SafeMessageQueue messageQueue,
                      MessageFactory factory, Timer timer) {
            this.messageQueue = messageQueue;
            this.factory = factory;
            this.parentMessageQueue = parentMessageQueue;
            this.timer = timer;
        }

        @Override
        public void run() {
            messageQueue.consume(new MessageQueueConsumer() {
                @Override
                public void handleMessage(Message message) {
                    switch (message.type) {
                        case RUN_JOB:
                            handleRunJob((RunJobMessage) message);
                            lastJobCompleted = timer.nanoTime();
                            break;
                        case COMMAND:
                            handleCommand((CommandMessage) message);
                            break;
                    }
                }

                @Override
                public void onIdle() {
                    JobConsumerIdleMessage idle = factory.obtain(JobConsumerIdleMessage.class);
                    idle.setConsumerQueue(messageQueue);
                    idle.setLastJobCompleted(lastJobCompleted);
                    parentMessageQueue.post(idle);
                }
            });
        }

        private void handleCommand(CommandMessage message) {
            switch (message.getWhat()) {
                case CommandMessage.QUIT:
                    messageQueue.stop();
                    break;
                case CommandMessage.POKE:
                    // just woke me up, let idle handle
                    JqLog.d("Consumer has been poked.");
                    break;
            }
        }

        private void handleRunJob(RunJobMessage message) {
            JobHolder jobHolder = message.getJobHolder();
            int result = jobHolder.safeRun(jobHolder.getRunCount());
            RunJobResultMessage resultMessage = factory.obtain(RunJobResultMessage.class);
            resultMessage.setJobHolder(jobHolder);
            resultMessage.setResult(result);
            parentMessageQueue.post(resultMessage);
        }
    }
}
