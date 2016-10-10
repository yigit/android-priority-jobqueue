package com.birbit.android.jobqueue;

import android.support.annotation.NonNull;

import com.birbit.android.jobqueue.config.Configuration;
import com.birbit.android.jobqueue.log.JqLog;
import com.birbit.android.jobqueue.messaging.Message;
import com.birbit.android.jobqueue.messaging.MessageFactory;
import com.birbit.android.jobqueue.messaging.MessagePredicate;
import com.birbit.android.jobqueue.messaging.MessageQueue;
import com.birbit.android.jobqueue.messaging.MessageQueueConsumer;
import com.birbit.android.jobqueue.messaging.SafeMessageQueue;
import com.birbit.android.jobqueue.messaging.Type;
import com.birbit.android.jobqueue.messaging.message.CommandMessage;
import com.birbit.android.jobqueue.messaging.message.JobConsumerIdleMessage;
import com.birbit.android.jobqueue.messaging.message.RunJobMessage;
import com.birbit.android.jobqueue.messaging.message.RunJobResultMessage;
import com.birbit.android.jobqueue.scheduling.SchedulerConstraint;
import com.birbit.android.jobqueue.timer.Timer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadFactory;

/**
 * This class is responsible to communicate with the Workers(consumers) that run the jobs.
 * It run's on {@link JobManagerThread}'s thread and directly controlled by it using its message
 * queue.
 */
class ConsumerManager {

    private List<Consumer> waitingConsumers = new ArrayList<>();

    private final List<Consumer> consumers = new ArrayList<>();

    private final int maxConsumerCount;

    private final int minConsumerCount;

    private final long consumerKeepAliveNs;

    private final int threadPriority;

    private final int loadFactor;

    private final ThreadGroup threadGroup;

    private final JobManagerThread jobManagerThread;

    private final Timer timer;

    private final MessageFactory factory;

    private final Map<String, JobHolder> runningJobHolders;

    final RunningJobSet runningJobGroups;

    private final ThreadFactory threadFactory;

    private final CopyOnWriteArrayList<Runnable> internalZeroConsumersListeners
            = new CopyOnWriteArrayList<>();

    ConsumerManager(JobManagerThread jobManagerThread, Timer timer, MessageFactory factory,
            Configuration configuration) {
        this.jobManagerThread = jobManagerThread;
        this.timer = timer;
        this.factory = factory;
        this.loadFactor = configuration.getLoadFactor();
        this.minConsumerCount = configuration.getMinConsumerCount();
        this.maxConsumerCount = configuration.getMaxConsumerCount();
        this.consumerKeepAliveNs = configuration.getConsumerKeepAlive() * 1000
                * JobManagerThread.NS_PER_MS;
        this.threadPriority = configuration.getThreadPriority();
        this.threadFactory = configuration.getThreadFactory();
        runningJobHolders = new HashMap<>();
        runningJobGroups = new RunningJobSet(timer);
        threadGroup = new ThreadGroup("JobConsumers");
    }

    void addNoConsumersListener(Runnable runnable) {
        internalZeroConsumersListeners.add(runnable);
    }

    boolean removeNoConsumersListener(Runnable runnable) {
        return internalZeroConsumersListeners.remove(runnable);
    }

    void onJobAdded() {
        considerAddingConsumers(false);
    }

    /**
     * @return True if a new consumer is added or a waiting consumer is waken up
     */
    boolean handleConstraintChange() {
        return considerAddingConsumers(true);
    }

    void handleStop() {
        // poke everybody so we can kill them
        for (Consumer consumer : consumers) {
            SafeMessageQueue mq = consumer.messageQueue;
            CommandMessage command = factory.obtain(CommandMessage.class);
            command.set(CommandMessage.POKE);
            mq.post(command);
        }
        if (consumers.isEmpty()) {
            for (Runnable runnable : internalZeroConsumersListeners) {
                runnable.run();
            }
        }
    }

    /**
     * @param pokeAllWaiting True if all waiting consumers should be poked instead of 1
     * @return True if a consumer is poked or a new consumer is added
     */
    private boolean considerAddingConsumers(boolean pokeAllWaiting) {
        JqLog.d("considering adding a new consumer. Should poke all waiting? %s isRunning? %s"
                        + " waiting workers? %d"
                , pokeAllWaiting, jobManagerThread.isRunning(), waitingConsumers.size());
        if (!jobManagerThread.isRunning()) {
            JqLog.d("jobqueue is not running, no consumers will be added");
            return false;
        }
        if (waitingConsumers.size() > 0) {
            JqLog.d("there are waiting workers, will poke them instead");
            for (int i = waitingConsumers.size() - 1; i >= 0; i--) {
                Consumer consumer = waitingConsumers.remove(i);
                CommandMessage command = factory.obtain(CommandMessage.class);
                command.set(CommandMessage.POKE);
                consumer.messageQueue.post(command);
                if (!pokeAllWaiting) {
                    break;
                }
            }
            JqLog.d("there were waiting workers, poked them and I'm done");
            return true;
        }
        boolean isAboveLoadFactor = isAboveLoadFactor();
        JqLog.d("nothing has been poked. are we above load factor? %s", isAboveLoadFactor);
        if (isAboveLoadFactor) {
            addWorker();
            return true;
        }
        return false;
    }

    private void addWorker() {
        JqLog.d("adding another consumer");
        Consumer consumer = new Consumer(jobManagerThread.messageQueue,
                new SafeMessageQueue(timer, factory, "consumer"), factory, timer);
        final Thread thread;
        if (threadFactory != null) {
            thread = threadFactory.newThread(consumer);
        } else {
            thread = new Thread(threadGroup, consumer, "job-queue-worker-" + UUID.randomUUID());
            thread.setPriority(threadPriority);
        }
        consumers.add(consumer);
        thread.start();
    }

    private boolean isAboveLoadFactor() {
        final int workerCount = consumers.size();
        if (workerCount >= maxConsumerCount) {
            JqLog.d("too many consumers, clearly above load factor %s", workerCount);
            return false;
        }
        final int remainingJobs = jobManagerThread.countRemainingReadyJobs();
        final int runningHolders = runningJobHolders.size();

        boolean aboveLoadFactor = (workerCount * loadFactor < remainingJobs + runningHolders) ||
                (workerCount < minConsumerCount && workerCount < remainingJobs + runningHolders);
        JqLog.d("check above load factor: totalCons:%s minCons:%s maxConsCount: %s, loadFactor %s"
                        + " remainingJobs: %s running holders: %s. isAbove:%s", workerCount,
                minConsumerCount, maxConsumerCount, loadFactor, remainingJobs, runningHolders,
                aboveLoadFactor);
        return aboveLoadFactor;
    }

    /**
     * @return true if consumer received a job or busy, false otherwise
     */
    boolean handleIdle(@NonNull JobConsumerIdleMessage message) {
        Consumer consumer = (Consumer) message.getWorker();
        if (consumer.hasJob) {
            return true;// ignore, it has a job to process.
        }
        JobHolder nextJob = null;
        final boolean running = jobManagerThread.isRunning();
        if (running) {
            nextJob = jobManagerThread.getNextJob(runningJobGroups.getSafe());
        }
        if (nextJob != null) {
            consumer.hasJob = true;
            runningJobGroups.add(nextJob.getGroupId());
            RunJobMessage runJobMessage = factory.obtain(RunJobMessage.class);
            runJobMessage.setJobHolder(nextJob);
            runningJobHolders.put(nextJob.getJob().getId(), nextJob);
            if (nextJob.getGroupId() != null) {
                runningJobGroups.add(nextJob.getGroupId());
            }
            consumer.messageQueue.post(runJobMessage);
            return true;
        } else {
            long keepAliveTimeout = message.getLastJobCompleted() + consumerKeepAliveNs;
            JqLog.d("keep alive: %s", keepAliveTimeout);
            final boolean tooMany = consumers.size() > minConsumerCount;
            boolean kill = !running || (tooMany && keepAliveTimeout < timer.nanoTime());
            JqLog.d("Consumer idle, will kill? %s . isRunning: %s", kill, running);
            if (kill) {
                CommandMessage command = factory.obtain(CommandMessage.class);
                command.set(CommandMessage.QUIT);
                consumer.messageQueue.post(command);
                waitingConsumers.remove(consumer);
                consumers.remove(consumer);
                JqLog.d("killed consumers. remaining consumers %d", consumers.size());
                if (consumers.isEmpty() && internalZeroConsumersListeners != null) {
                    for (Runnable runnable : internalZeroConsumersListeners) {
                        runnable.run();
                    }
                }
            } else {
                if (!waitingConsumers.contains(consumer)) {
                    waitingConsumers.add(consumer);
                }
                if (tooMany || !jobManagerThread.canListenToNetwork()) {
                    CommandMessage cm = factory.obtain(CommandMessage.class);
                    cm.set(CommandMessage.POKE);
                    if (!tooMany) {
                        keepAliveTimeout = timer.nanoTime() + consumerKeepAliveNs;
                    }
                    consumer.messageQueue.postAt(cm, keepAliveTimeout);
                    JqLog.d("poke consumer manager at %s", keepAliveTimeout);
                }
            }
            return false;
        }
    }

    /**
     * Excludes cancelled jobs
     */
    Set<String> markJobsCancelled(TagConstraint constraint, String[] tags) {
        return markJobsCancelled(constraint, tags, false);
    }

    Set<String> markJobsCancelledSingleId(TagConstraint constraint, String[] tags) {
        return markJobsCancelled(constraint, tags, true);
    }

    private Set<String> markJobsCancelled(TagConstraint constraint, String[] tags, boolean singleId) {
        Set<String> result = new HashSet<>();
        for (JobHolder holder : runningJobHolders.values()) {
            JqLog.d("checking job tag %s. tags of job: %s", holder.getJob(),
                    holder.getJob().getTags());
            if (!holder.hasTags()) {
                continue;
            }
            if (holder.isCancelled()) {
                continue;
            }

            if (constraint.matches(tags, holder.getTags())) {
                result.add(holder.getId());
                if (singleId) {
                    holder.markAsCancelledSingleId();
                } else {
                    holder.markAsCancelled();
                }
            }
        }
        return result;
    }

    void handleRunJobResult(RunJobResultMessage message, JobHolder jobHolder,
            RetryConstraint retryConstraint) {
        Consumer consumer = (Consumer) message.getWorker();
        if (!consumer.hasJob) {
            throw new IllegalStateException("this worker should not have a job");
        }
        consumer.hasJob = false;
        runningJobHolders.remove(jobHolder.getJob().getId());
        if (jobHolder.getGroupId() != null) {
            runningJobGroups.remove(jobHolder.getGroupId());
            if (retryConstraint != null && retryConstraint.willApplyNewDelayToGroup()
                    && retryConstraint.getNewDelayInMs() > 0) {
                runningJobGroups.addGroupUntil(jobHolder.getGroupId(),
                        timer.nanoTime()
                                + retryConstraint.getNewDelayInMs() * JobManagerThread.NS_PER_MS);
            }
        }
    }

    boolean isJobRunning(String id) {
        return runningJobHolders.get(id) != null;
    }

    public int getWorkerCount() {
        return consumers.size();
    }

    public boolean hasJobsWithSchedulerConstraint(SchedulerConstraint constraint) {
        for (JobHolder jobHolder : runningJobHolders.values()) {
            if (!jobHolder.getJob().isPersistent()) {
                continue;
            }
            if(constraint.getNetworkStatus() >= jobHolder.requiredNetworkType) {
                return true;
            }
        }
        return false;
    }

    public boolean areAllConsumersIdle() {
        return waitingConsumers.size() == consumers.size();
    }

    static class Consumer implements Runnable {

        final SafeMessageQueue messageQueue;

        final MessageQueue parentMessageQueue;

        final MessageFactory factory;

        final Timer timer;

        boolean hasJob;// controlled by the consumer controller to avoid multiple idle-job loops

        long lastJobCompleted;

        static final MessagePredicate pokeMessagePredicate =
                new MessagePredicate() {
                    @Override
                    public boolean onMessage(Message message) {
                        return message.type == Type.COMMAND &&
                                ((CommandMessage) message).getWhat() == CommandMessage.POKE;
                    }
                };

        final MessageQueueConsumer queueConsumer = new MessageQueueConsumer() {
            @Override
            public void handleMessage(Message message) {
                switch (message.type) {
                    case RUN_JOB:
                        handleRunJob((RunJobMessage) message);
                        lastJobCompleted = timer.nanoTime();
                        removePokeMessages();
                        break;
                    case COMMAND:
                        handleCommand((CommandMessage) message);
                        break;
                }
            }

            @Override
            public void onIdle() {
                JqLog.d("consumer manager on idle");
                JobConsumerIdleMessage idle = factory.obtain(JobConsumerIdleMessage.class);
                idle.setWorker(Consumer.this);
                idle.setLastJobCompleted(lastJobCompleted);
                parentMessageQueue.post(idle);
            }
        };

        private void removePokeMessages() {
            messageQueue.cancelMessages(pokeMessagePredicate);
        }

        public Consumer(MessageQueue parentMessageQueue, SafeMessageQueue messageQueue,
                MessageFactory factory, Timer timer) {
            this.messageQueue = messageQueue;
            this.factory = factory;
            this.parentMessageQueue = parentMessageQueue;
            this.timer = timer;
            this.lastJobCompleted = timer.nanoTime();
        }

        @Override
        public void run() {
            messageQueue.consume(queueConsumer);
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
            JqLog.d("running job %s", message.getJobHolder().getClass().getSimpleName());
            JobHolder jobHolder = message.getJobHolder();
            int result = jobHolder.safeRun(jobHolder.getRunCount(), timer);
            RunJobResultMessage resultMessage = factory.obtain(RunJobResultMessage.class);
            resultMessage.setJobHolder(jobHolder);
            resultMessage.setResult(result);
            resultMessage.setWorker(this);
            parentMessageQueue.post(resultMessage);
        }
    }
}
