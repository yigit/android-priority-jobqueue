package com.birbit.android.jobqueue;

import com.birbit.android.jobqueue.messaging.MessageFactory;
import com.birbit.android.jobqueue.messaging.PriorityMessageQueue;
import com.birbit.android.jobqueue.messaging.message.AddJobMessage;
import com.path.android.jobqueue.Job;
import com.path.android.jobqueue.callback.JobManagerCallback;
import com.path.android.jobqueue.config.Configuration;
import com.path.android.jobqueue.log.JqLog;

public class JobManager2 {
    private final Chef chef;
    private final PriorityMessageQueue messageQueue;
    private final MessageFactory messageFactory;
    private Thread chefThread;
    public JobManager2(Configuration configuration) {
        messageQueue = new PriorityMessageQueue();
        messageFactory = new MessageFactory();
        chef = new Chef(configuration, messageQueue, messageFactory);
        chefThread = new Thread(chef, "job-manager");
        chefThread.start();
    }

    public void addJobInBackground(Job job) {
        AddJobMessage message = messageFactory.obtain(AddJobMessage.class);
        message.setJob(job);
        messageQueue.post(message);
    }

    private void assertRightThread() {
        if (Thread.currentThread() == chefThread) {
            // TODO we can allow a configuration to call callbacks in another thread. In that case,
            // we'll have to lock on onAdded calls as well
            throw new RuntimeException("Cannot call sync job manager methods in its runner thread."
                    + "Use async instead");
        }
    }

    public void addCallback(JobManagerCallback callback) {
        chef.addCallback(callback);
    }

    public boolean removeCallback(JobManagerCallback callback) {
        return chef.removeCallback(callback);
    }
}
