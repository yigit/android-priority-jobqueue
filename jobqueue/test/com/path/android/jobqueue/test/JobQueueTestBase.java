package com.path.android.jobqueue.test;

import com.path.android.jobqueue.JobHolder;
import com.path.android.jobqueue.JobManager;
import com.path.android.jobqueue.JobQueue;
import com.path.android.jobqueue.test.jobs.DummyJob;
import com.path.android.jobqueue.test.util.JobQueueFactory;
import edu.emory.mathcs.backport.java.util.Arrays;
import org.fest.reflect.core.Reflection;
import org.fest.reflect.method.Invoker;
import org.hamcrest.MatcherAssert;
import org.junit.Ignore;
import org.junit.Test;

import java.util.ArrayList;

import static org.hamcrest.CoreMatchers.*;

@Ignore
public abstract class JobQueueTestBase {
    JobQueueFactory currentFactory;

    public JobQueueTestBase(JobQueueFactory factory) {
        currentFactory = factory;
    }

    @Test
    public void testBasicAddRemoveCount() throws Exception {
        final int ADD_COUNT = 6;
        JobQueue jobQueue = createNewJobQueue();
        MatcherAssert.assertThat((int) jobQueue.count(), equalTo(0));
        MatcherAssert.assertThat(jobQueue.nextJobAndIncRunCount(true, null), nullValue());
        for (int i = 0; i < ADD_COUNT; i++) {
            JobHolder holder = createNewJobHolder();
            jobQueue.insert(holder);
            MatcherAssert.assertThat((int) jobQueue.count(), equalTo(i + 1));
            MatcherAssert.assertThat(holder.getId(), notNullValue());
            jobQueue.insertOrReplace(holder);
            MatcherAssert.assertThat((int) jobQueue.count(), equalTo(i + 1));
        }
        JobHolder firstHolder = jobQueue.nextJobAndIncRunCount(true, null);
        MatcherAssert.assertThat(firstHolder.getRunCount(), equalTo(1));
        //size should be down 1
        MatcherAssert.assertThat((int) jobQueue.count(), equalTo(ADD_COUNT - 1));
        //should return another job
        JobHolder secondHolder = jobQueue.nextJobAndIncRunCount(true, null);
        MatcherAssert.assertThat(secondHolder.getRunCount(), equalTo(1));
        //size should be down 2
        MatcherAssert.assertThat((int) jobQueue.count(), equalTo(ADD_COUNT - 2));
        //second holder and first holder should have different ids
        MatcherAssert.assertThat(firstHolder.getId(), not(secondHolder.getId()));
        jobQueue.remove(secondHolder);
        MatcherAssert.assertThat((int) jobQueue.count(), equalTo(ADD_COUNT - 2));
        jobQueue.remove(secondHolder);
        //non existed job removed, count should be the same
        MatcherAssert.assertThat((int) jobQueue.count(), equalTo(ADD_COUNT - 2));
        jobQueue.remove(firstHolder);
        MatcherAssert.assertThat((int) jobQueue.count(), equalTo(ADD_COUNT - 2));
    }

    @Test
    public void testPriority() throws Exception {
        int JOB_LIMIT = 20;
        JobQueue jobQueue = createNewJobQueue();
        //create and add JOB_LIMIT jobs with random priority
        for (int i = 0; i < JOB_LIMIT; i++) {
            jobQueue.insert(createNewJobHolderWithPriority((int) (Math.random() * 10)));
        }
        //ensure we get jobs in correct priority order
        int minPriority = Integer.MAX_VALUE;
        for (int i = 0; i < JOB_LIMIT; i++) {
            JobHolder holder = jobQueue.nextJobAndIncRunCount(true, null);
            MatcherAssert.assertThat(holder.getPriority() <= minPriority, is(true));
        }
        MatcherAssert.assertThat(jobQueue.nextJobAndIncRunCount(true, null), nullValue());
    }

    @Test
    public void testDelayUntilWithPriority() throws Exception {
        JobQueue jobQueue = createNewJobQueue();
        long now = System.nanoTime();
        JobHolder lowPriorityHolder = createNewJobHolderWithDelayUntil(false, 5, now + 10000 * JobManager.NS_PER_MS);
        JobHolder highPriorityHolder = createNewJobHolderWithDelayUntil(false, 10, now + 20000 * JobManager.NS_PER_MS);
        jobQueue.insert(lowPriorityHolder);
        jobQueue.insert(highPriorityHolder);
        MatcherAssert.assertThat("when asked, if lower priority job has less delay until, we should return it",
                jobQueue.getNextJobDelayUntilNs(true), equalTo(lowPriorityHolder.getDelayUntilNs()));

    }

    @Test
    public void testGroupId() throws Exception {
        JobQueue jobQueue = createNewJobQueue();
        long jobId1 = jobQueue.insert(createNewJobHolderWithPriorityAndGroupId(0, "group1"));
        long jobId2 = jobQueue.insert(createNewJobHolderWithPriorityAndGroupId(0, "group1"));
        long jobId3 = jobQueue.insert(createNewJobHolderWithPriorityAndGroupId(0, "group2"));
        long jobId4 = jobQueue.insert(createNewJobHolderWithPriorityAndGroupId(0, "group2"));
        long jobId5 = jobQueue.insert(createNewJobHolderWithPriorityAndGroupId(0, "group1"));
        JobHolder holder1 = jobQueue.nextJobAndIncRunCount(true, Arrays.asList(new String[]{"group2"}));
        MatcherAssert.assertThat("first jobs should be from group group2 if group1 is excluded",
                holder1.getBaseJob().getRunGroupId(), equalTo("group1"));
        MatcherAssert.assertThat("correct job should be returned if groupId is provided",
                holder1.getId(), equalTo(jobId1));
        MatcherAssert.assertThat("no jobs should be returned if all groups are excluded",
                jobQueue.nextJobAndIncRunCount(true,
                        Arrays.asList(new String[]{"group1", "group2"})),
                is(nullValue()));
        MatcherAssert.assertThat("if group1 is excluded, next job should be from group2",
                jobQueue.nextJobAndIncRunCount(true, Arrays.asList(new String[]{"group1"})).getBaseJob().getRunGroupId()
                , equalTo("group2"));

        //to test re-run case, add the job back in
        jobQueue.insertOrReplace(holder1);
        //ask for it again, should return the same holder because it is grouped
        JobHolder holder2 = jobQueue.nextJobAndIncRunCount(true, null);
        MatcherAssert.assertThat("for grouped jobs, run count should be ignored when fetching",
                holder2.getId(), equalTo(holder1.getId()));

        JobHolder holder3 = jobQueue.nextJobAndIncRunCount(true,
                        Arrays.asList(new String[]{"group1"}));
        MatcherAssert.assertThat("if a group it excluded, next available from another group should be returned",
                holder3.getId(), equalTo(jobId2));

        //add two more non-grouped jobs
        long jobId6 = jobQueue.insert(createNewJobHolderWithPriority(0));
        long jobId7 = jobQueue.insert(createNewJobHolderWithPriority(0));
        JobHolder holder4 = jobQueue.nextJobAndIncRunCount(true,
                Arrays.asList(new String[]{"group1", "group2"}));
        MatcherAssert.assertThat("if all grouped jobs are excluded, non-grouped jobs should be returned",
                holder4.getId(),
                equalTo(jobId6));
        jobQueue.insertOrReplace(holder4);
        //for non-grouped jobs, run counts should be respected
        MatcherAssert.assertThat("if all grouped jobs are excluded, run count for non-grouped jobs should be respected",
                jobQueue.nextJobAndIncRunCount(true,
                        Arrays.asList(new String[]{"group1", "group2"})).getId(),
                equalTo(jobId7));
    }

    @Test
    public void testDueDelayUntilWithPriority() throws Exception {
        JobQueue jobQueue = createNewJobQueue();
        long now = System.nanoTime();
        JobHolder lowPriorityHolder = createNewJobHolderWithDelayUntil(false, 5, now - 1000 * JobManager.NS_PER_MS);
        JobHolder highPriorityHolder = createNewJobHolderWithDelayUntil(false, 10, now - 10000 * JobManager.NS_PER_MS);
        jobQueue.insert(lowPriorityHolder);
        jobQueue.insert(highPriorityHolder);
        long soonJobDelay = 2000;
        JobHolder highestPriorityDelayedJob = createNewJobHolderWithDelayUntil(false, 12, now + soonJobDelay * JobManager.NS_PER_MS);
        long highestPriorityDelayedJobId = jobQueue.insert(highestPriorityDelayedJob);
        MatcherAssert.assertThat("when asked, if job's due has passed, highest priority jobs's delay until should be " +
                "returned",
                jobQueue.getNextJobDelayUntilNs(true), equalTo(highPriorityHolder.getDelayUntilNs()));
        //make sure soon job is valid now
        Thread.sleep(soonJobDelay);

        MatcherAssert.assertThat("when a job's time come, it should be returned",
                jobQueue.nextJobAndIncRunCount(true, null).getId(), equalTo(highestPriorityDelayedJobId));
    }

    @Test
    public void testDelayUntil() throws Exception {
        JobQueue jobQueue = createNewJobQueue();
        long now = System.nanoTime();
        JobHolder networkJobHolder = createNewJobHolderWithDelayUntil(true, 0, now + 200000 * JobManager.NS_PER_MS);

        JobHolder noNetworkJobHolder = createNewJobHolderWithDelayUntil(false, 0, now + 500000 * JobManager.NS_PER_MS);

        jobQueue.insert(networkJobHolder);
        jobQueue.insert(noNetworkJobHolder);

        MatcherAssert.assertThat("if there is no network, delay until should be provided for no network job",
            jobQueue.getNextJobDelayUntilNs(false), equalTo(noNetworkJobHolder.getDelayUntilNs()));

        MatcherAssert.assertThat("if there is network, delay until should be provided for network job because it is " +
                "sooner", jobQueue.getNextJobDelayUntilNs(true), equalTo(networkJobHolder.getDelayUntilNs()));

        JobHolder noNetworkJobHolder2 = createNewJobHolderWithDelayUntil(false, 0, now + 100000 * JobManager.NS_PER_MS);

        jobQueue.insert(noNetworkJobHolder2);
        MatcherAssert.assertThat("if there is network, any job's delay until should be returned",
                jobQueue.getNextJobDelayUntilNs(true), equalTo(noNetworkJobHolder2.getDelayUntilNs()));
    }

    @Test
    public void testTruncate() throws Exception {
        JobQueue jobQueue = createNewJobQueue();
        final int LIMIT = 20;
        for(int i = 0; i < LIMIT; i ++) {
            jobQueue.insert(createNewJobHolder());
        }
        MatcherAssert.assertThat("queue should have all jobs", jobQueue.count(), equalTo(LIMIT));
        jobQueue.clear();
        MatcherAssert.assertThat("after clear, queue should be empty", jobQueue.count(), equalTo(0));
        for(int i = 0; i < LIMIT; i ++) {
            jobQueue.insert(createNewJobHolder());
        }
        MatcherAssert.assertThat("if we add jobs again, count should match", jobQueue.count(), equalTo(LIMIT));
    }

    @Test
    public void testPriorityWithDelayedJobs() throws Exception {
        JobQueue jobQueue = createNewJobQueue();
        JobHolder delayedPriority_5 = createNewJobHolderWithPriority(5);
        org.fest.reflect.field.Invoker<Long> delayUntilField = getDelayUntilNsField(delayedPriority_5);
        delayUntilField.set(System.nanoTime() - 1000);

        JobHolder delayedPriority_2 = createNewJobHolderWithPriority(2);
        delayUntilField = getDelayUntilNsField(delayedPriority_2);
        delayUntilField.set(System.nanoTime() - 500);



        JobHolder nonDelayedPriority_6 = createNewJobHolderWithPriority(6);
        JobHolder nonDelayedPriority_3 = createNewJobHolderWithPriority(3);
        JobHolder nonDelayedPriority_2 = createNewJobHolderWithPriority(2);


        jobQueue.insert(delayedPriority_5);
        jobQueue.insert(delayedPriority_2);
        jobQueue.insert(nonDelayedPriority_6);
        jobQueue.insert(nonDelayedPriority_2);
        jobQueue.insert(nonDelayedPriority_3);

        int lastPriority = Integer.MAX_VALUE;
        for(int i = 0; i < 5; i++) {
            JobHolder next = jobQueue.nextJobAndIncRunCount(true, null);
            MatcherAssert.assertThat("next job should not be null", next, notNullValue());
            MatcherAssert.assertThat("next job's priority should be lower then previous for job " + i, next.getPriority() <= lastPriority, is(true));
            lastPriority = next.getPriority();
        }

    }

    private org.fest.reflect.field.Invoker<Long> getDelayUntilNsField(JobHolder jobHolder) {
        return Reflection.field("delayUntilNs").ofType(long.class).in(jobHolder);
    }

    @Test
    public void testSessionId() throws Exception {
        long sessionId = (long) (Math.random() * 100000);
        JobQueue jobQueue = createNewJobQueueWithSessionId(sessionId);
        JobHolder jobHolder = createNewJobHolder();
        jobQueue.insert(jobHolder);
        jobHolder = jobQueue.nextJobAndIncRunCount(true, null);
        MatcherAssert.assertThat("session id should be attached to next job",
                jobHolder.getRunningSessionId(), equalTo(sessionId));
    }

    @Test
    public void testPriorityWithReAdd() throws Exception {
        int JOB_LIMIT = 20;
        JobQueue jobQueue = createNewJobQueue();
        //create and add JOB_LIMIT jobs with random priority
        for (int i = 0; i < JOB_LIMIT; i++) {
            jobQueue.insert(createNewJobHolderWithPriority((int) (Math.random() * 10)));
        }
        //ensure we get jobs in correct priority order
        int minPriority = Integer.MAX_VALUE;
        for (int i = 0; i < JOB_LIMIT; i++) {
            JobHolder holder = jobQueue.nextJobAndIncRunCount(true, null);
            MatcherAssert.assertThat(holder.getPriority() <= minPriority, is(true));
            jobQueue.insertOrReplace(holder);
        }
        MatcherAssert.assertThat(jobQueue.nextJobAndIncRunCount(true, null), notNullValue());
    }

    @Test
    public void testRemove() throws Exception {
        JobQueue jobQueue = createNewJobQueue();
        JobHolder holder = createNewJobHolder();
        jobQueue.insert(holder);
        Long jobId = holder.getId();
        MatcherAssert.assertThat(jobQueue.nextJobAndIncRunCount(true, null).getId(), equalTo(jobId));
        MatcherAssert.assertThat(jobQueue.nextJobAndIncRunCount(true, null), is(nullValue()));
        jobQueue.remove(holder);
        MatcherAssert.assertThat(jobQueue.nextJobAndIncRunCount(true, null), is(nullValue()));
    }

    @Test
    public void testNetwork() throws Exception {
        JobQueue jobQueue = createNewJobQueue();
        JobHolder jobHolder = createNewJobHolderWithRequiresNetwork(false);
        jobQueue.insert(jobHolder);
        MatcherAssert.assertThat("no network job should be returned even if there is no netowrk",
                jobQueue.nextJobAndIncRunCount(false, null), notNullValue());
        jobQueue.remove(jobHolder);

        jobHolder = createNewJobHolderWithRequiresNetwork(true);
        MatcherAssert.assertThat("if there isn't any network, job with network requirement should not return",
                jobQueue.nextJobAndIncRunCount(false, null), nullValue());

        MatcherAssert.assertThat("if there is network, job with network requirement should be returned",
                jobQueue.nextJobAndIncRunCount(true, null), nullValue());

        jobQueue.remove(jobHolder);

        jobHolder = createNewJobHolderWithRequiresNetworkAndPriority(false, 1);
        JobHolder jobHolder2 = createNewJobHolderWithRequiresNetworkAndPriority(true, 5);
        long firstJobId = jobQueue.insert(jobHolder);
        long secondJobId = jobQueue.insert(jobHolder2);
        JobHolder retrieved = jobQueue.nextJobAndIncRunCount(false, null);
        MatcherAssert.assertThat("one job should be returned w/o network", retrieved, notNullValue());
        if(retrieved != null) {
            MatcherAssert.assertThat("no network job should be returned although it has lower priority", retrieved.getId(), equalTo(firstJobId));
        }

        MatcherAssert.assertThat("no other job should be returned w/o network", jobQueue.nextJobAndIncRunCount(false, null), nullValue());

        retrieved = jobQueue.nextJobAndIncRunCount(true, null);
        MatcherAssert.assertThat("if network is back, network requiring job should be returned", retrieved, notNullValue());
        if(retrieved != null) {
            MatcherAssert.assertThat("when there is network, network job should be returned", retrieved.getId(), equalTo(secondJobId));
        }
        //add first job back
        jobQueue.insertOrReplace(jobHolder);
        //add second job back
        jobQueue.insertOrReplace(jobHolder2);

        retrieved = jobQueue.nextJobAndIncRunCount(true, null);
        MatcherAssert.assertThat("if network is back, job w/ higher priority should be returned", retrieved, notNullValue());
        if(retrieved != null) {
            MatcherAssert.assertThat("if network is back, job w/ higher priority should be returned", retrieved.getId(), equalTo(secondJobId));
        }
        jobQueue.insertOrReplace(jobHolder2);

        JobHolder highestPriorityJob = createNewJobHolderWithRequiresNetworkAndPriority(false, 10);
        long highestPriorityJobId = jobQueue.insert(highestPriorityJob);
        retrieved = jobQueue.nextJobAndIncRunCount(true, null);
        MatcherAssert.assertThat("w/ or w/o network, highest priority should be returned", retrieved, notNullValue());
        if(retrieved != null) {
            MatcherAssert.assertThat("w/ or w/o network, highest priority should be returned", retrieved.getId(), equalTo(highestPriorityJobId));
        }

        //TODO test delay until
    }

    @Test
    public void testJobFields() throws Exception {
        long sessionId = (long) (Math.random() * 1000);
        JobQueue jobQueue = createNewJobQueueWithSessionId(sessionId);
        JobHolder jobHolder = createNewJobHolder();


        int priority = (int) (Math.random() * 1000);
        jobHolder.setPriority(priority);
        DummyJob dummyJob = new DummyJob();
        jobHolder.setBaseJob(dummyJob);
        int runCount = (int) (Math.random() * 10);
        jobHolder.setRunCount(runCount);

        long id = jobQueue.insert(jobHolder);


        for (int i = 0; i < 2; i++) {
            JobHolder received = jobQueue.nextJobAndIncRunCount(true, null);
            MatcherAssert.assertThat("job id should be preserved", received.getId(), equalTo(id));
            MatcherAssert.assertThat("job priority should be preserved", received.getPriority(), equalTo(priority));
            MatcherAssert.assertThat("job session id should be assigned", received.getRunningSessionId(), equalTo(sessionId));
            MatcherAssert.assertThat("job run count should be incremented", received.getRunCount(), equalTo(runCount + i + 1));
            jobQueue.insertOrReplace(received);
        }
    }

    protected JobHolder createNewJobHolder() {
        return new JobHolder(null, 0, 0, new DummyJob(), System.nanoTime(), Long.MIN_VALUE, JobManager.NOT_RUNNING_SESSION_ID);
    }

    private JobHolder createNewJobHolderWithRequiresNetwork(boolean requiresNetwork) {
        return new JobHolder(null, 0, 0, new DummyJob(requiresNetwork), System.nanoTime(), Long.MIN_VALUE, JobManager.NOT_RUNNING_SESSION_ID);
    }

    private JobHolder createNewJobHolderWithDelayUntil(boolean requiresNetwork, int priority, long delayUntil) {
        JobHolder jobHolder = new JobHolder(null, priority, 0, new DummyJob(requiresNetwork), System.nanoTime(), Long.MIN_VALUE, JobManager.NOT_RUNNING_SESSION_ID);
        getDelayUntilNsField(jobHolder).set(delayUntil);
        return jobHolder;
    }

    private JobHolder createNewJobHolderWithRequiresNetworkAndPriority(boolean requiresNetwork, int priority) {
        return new JobHolder(null, priority, 0, new DummyJob(requiresNetwork), System.nanoTime(), Long.MIN_VALUE, JobManager.NOT_RUNNING_SESSION_ID);
    }

    private JobHolder createNewJobHolderWithPriority(int priority) {
        return new JobHolder(null, priority, 0, new DummyJob(), System.nanoTime(), Long.MIN_VALUE, JobManager.NOT_RUNNING_SESSION_ID);
    }

    private JobHolder createNewJobHolderWithPriorityAndGroupId(int priority, String groupId) {
        return new JobHolder(null, priority, 0, new DummyJob(groupId), System.nanoTime(), Long.MIN_VALUE, JobManager.NOT_RUNNING_SESSION_ID);
    }

    private JobQueue createNewJobQueue() {
        return createNewJobQueueWithSessionId(System.nanoTime());
    }

    private JobQueue createNewJobQueueWithSessionId(Long sessionId) {
        return currentFactory.createNew(sessionId, "id_" + sessionId);
    }
}
