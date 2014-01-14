package com.path.android.jobqueue.test.jobqueue;

import com.path.android.jobqueue.JobHolder;
import com.path.android.jobqueue.JobManager;
import com.path.android.jobqueue.JobQueue;
import com.path.android.jobqueue.Params;
import com.path.android.jobqueue.test.TestBase;
import com.path.android.jobqueue.test.jobs.DummyJob;
import com.path.android.jobqueue.test.util.JobQueueFactory;
import org.fest.reflect.core.*;
import static org.hamcrest.CoreMatchers.*;
import org.hamcrest.*;
import org.junit.Ignore;
import org.junit.Test;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

@Ignore
public abstract class JobQueueTestBase extends TestBase {
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
            jobQueue.insert(createNewJobHolder(new Params((int) (Math.random() * 10))));
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
        JobHolder lowPriorityHolder = createNewJobHolderWithDelayUntil(new Params(5), now + 10000 * JobManager.NS_PER_MS);
        JobHolder highPriorityHolder = createNewJobHolderWithDelayUntil(new Params(10), now + 20000 * JobManager.NS_PER_MS);
        jobQueue.insert(lowPriorityHolder);
        jobQueue.insert(highPriorityHolder);
        MatcherAssert.assertThat("when asked, if lower priority job has less delay until, we should return it",
                jobQueue.getNextJobDelayUntilNs(true), equalTo(lowPriorityHolder.getDelayUntilNs()));

    }

    @Test
    public void testGroupId() throws Exception {
        JobQueue jobQueue = createNewJobQueue();
        long jobId1 = jobQueue.insert(createNewJobHolder(new Params(0).groupBy("group1")));
        long jobId2 = jobQueue.insert(createNewJobHolder(new Params(0).groupBy("group1")));
        long jobId3 = jobQueue.insert(createNewJobHolder(new Params(0).groupBy("group2")));
        long jobId4 = jobQueue.insert(createNewJobHolder(new Params(0).groupBy("group2")));
        long jobId5 = jobQueue.insert(createNewJobHolder(new Params(0).groupBy("group1")));
        JobHolder holder1 = jobQueue.nextJobAndIncRunCount(true, Arrays.asList(new String[]{"group2"}));
        MatcherAssert.assertThat("first jobs should be from group group2 if group1 is excluded",
                holder1.getBaseJob().getRunGroupId(), equalTo("group1"));
        MatcherAssert.assertThat("correct job should be returned if groupId is provided",
                holder1.getId(), equalTo(jobId1));
        MatcherAssert.assertThat("no jobs should be returned if all groups are excluded",
                jobQueue.nextJobAndIncRunCount(true,
                        Arrays.asList(new String[]{"group1", "group2"})),
                is(nullValue()));
        long jobId6 = jobQueue.insert(createNewJobHolder(new Params(0)));
        MatcherAssert.assertThat("both groups are disabled, null group job should be returned",
                jobQueue.nextJobAndIncRunCount(true,
                        Arrays.asList(new String[]{"group1", "group2"})).getId(),
                is(jobId6));
        MatcherAssert.assertThat("if group1 is excluded, next job should be from group2",
                jobQueue.nextJobAndIncRunCount(true, Arrays.asList(new String[]{"group1"})).getBaseJob().getRunGroupId()
                , equalTo("group2"));

        //to test re-run case, add the job back in
        jobQueue.insertOrReplace(holder1);
        //ask for it again, should return the same holder because it is grouped
        JobHolder holder2 = jobQueue.nextJobAndIncRunCount(true, null);
        MatcherAssert.assertThat("for grouped jobs, re-fetching job should work fine",
                holder2.getId(), equalTo(holder1.getId()));

        JobHolder holder3 = jobQueue.nextJobAndIncRunCount(true,
                        Arrays.asList(new String[]{"group1"}));
        MatcherAssert.assertThat("if a group it excluded, next available from another group should be returned",
                holder3.getId(), equalTo(jobId4));

        //add two more non-grouped jobs
        long jobId7 = jobQueue.insert(createNewJobHolder(new Params(0)));
        long jobId8 = jobQueue.insert(createNewJobHolder(new Params(0)));
        JobHolder holder4 = jobQueue.nextJobAndIncRunCount(true,
                Arrays.asList(new String[]{"group1", "group2"}));
        MatcherAssert.assertThat("if all grouped jobs are excluded, non-grouped jobs should be returned",
                holder4.getId(),
                equalTo(jobId7));
        jobQueue.insertOrReplace(holder4);
        //for non-grouped jobs, run counts should be respected
        MatcherAssert.assertThat("if all grouped jobs are excluded, re-inserted highest priority job should still be returned",
                jobQueue.nextJobAndIncRunCount(true,
                        Arrays.asList(new String[]{"group1", "group2"})).getId(),
                equalTo(jobId7));
    }

    @Test
    public void testDueDelayUntilWithPriority() throws Exception {
        JobQueue jobQueue = createNewJobQueue();
        long now = System.nanoTime();
        JobHolder lowPriorityHolder = createNewJobHolderWithDelayUntil(new Params(5),now - 1000 * JobManager.NS_PER_MS);
        JobHolder highPriorityHolder = createNewJobHolderWithDelayUntil(new Params(10), now - 10000 * JobManager.NS_PER_MS);
        jobQueue.insert(lowPriorityHolder);
        jobQueue.insert(highPriorityHolder);
        long soonJobDelay = 2000;
        JobHolder highestPriorityDelayedJob = createNewJobHolderWithDelayUntil(new Params(12), now + soonJobDelay * JobManager.NS_PER_MS);
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
        JobHolder networkJobHolder = createNewJobHolderWithDelayUntil(new Params(0).requireNetwork(), now + 200000 * JobManager.NS_PER_MS);

        JobHolder noNetworkJobHolder = createNewJobHolderWithDelayUntil(new Params(0), now + 500000 * JobManager.NS_PER_MS);

        jobQueue.insert(networkJobHolder);
        jobQueue.insert(noNetworkJobHolder);

        MatcherAssert.assertThat("if there is no network, delay until should be provided for no network job",
            jobQueue.getNextJobDelayUntilNs(false), equalTo(noNetworkJobHolder.getDelayUntilNs()));

        MatcherAssert.assertThat("if there is network, delay until should be provided for network job because it is " +
                "sooner", jobQueue.getNextJobDelayUntilNs(true), equalTo(networkJobHolder.getDelayUntilNs()));

        JobHolder noNetworkJobHolder2 = createNewJobHolderWithDelayUntil(new Params(0), now + 100000 * JobManager.NS_PER_MS);

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
        JobHolder delayedPriority_5 = createNewJobHolder(new Params(5));
        org.fest.reflect.field.Invoker<Long> delayUntilField = getDelayUntilNsField(delayedPriority_5);
        delayUntilField.set(System.nanoTime() - 1000);

        JobHolder delayedPriority_2 = createNewJobHolder(new Params(2));
        delayUntilField = getDelayUntilNsField(delayedPriority_2);
        delayUntilField.set(System.nanoTime() - 500);



        JobHolder nonDelayedPriority_6 = createNewJobHolder(new Params(6));
        JobHolder nonDelayedPriority_3 = createNewJobHolder(new Params(3));
        JobHolder nonDelayedPriority_2 = createNewJobHolder(new Params(2));


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

    private org.fest.reflect.field.Invoker<Integer> getPriorityField(Params params) {
        return Reflection.field("priority").ofType(int.class).in(params);
    }

    private org.fest.reflect.field.Invoker<Long> getDelayMsField(Params params) {
        return Reflection.field("delayMs").ofType(long.class).in(params);
    }

    private org.fest.reflect.field.Invoker<String> getGroupIdField(Params params) {
        return Reflection.field("groupId").ofType(String.class).in(params);
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
            jobQueue.insert(createNewJobHolder(new Params((int) (Math.random() * 10))));
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
        JobHolder jobHolder = createNewJobHolder(new Params(0));
        jobQueue.insert(jobHolder);
        MatcherAssert.assertThat("no network job should be returned even if there is no netowrk",
                jobQueue.nextJobAndIncRunCount(false, null), notNullValue());
        jobQueue.remove(jobHolder);

        jobHolder = createNewJobHolder(new Params(0).requireNetwork());
        MatcherAssert.assertThat("if there isn't any network, job with network requirement should not return",
                jobQueue.nextJobAndIncRunCount(false, null), nullValue());

        MatcherAssert.assertThat("if there is network, job with network requirement should be returned",
                jobQueue.nextJobAndIncRunCount(true, null), nullValue());

        jobQueue.remove(jobHolder);

        jobHolder = createNewJobHolder(new Params(1));
        JobHolder jobHolder2 = createNewJobHolder(new Params(5).requireNetwork());
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

        JobHolder highestPriorityJob = createNewJobHolder(new Params(10));
        long highestPriorityJobId = jobQueue.insert(highestPriorityJob);
        retrieved = jobQueue.nextJobAndIncRunCount(true, null);
        MatcherAssert.assertThat("w/ or w/o network, highest priority should be returned", retrieved, notNullValue());
        if(retrieved != null) {
            MatcherAssert.assertThat("w/ or w/o network, highest priority should be returned", retrieved.getId(), equalTo(highestPriorityJobId));
        }

        //TODO test delay until
    }

    @Test
    public void testCountReadyJobs() throws Exception {
        JobQueue jobQueue = createNewJobQueue();
        MatcherAssert.assertThat("initial count should be 0 for ready jobs", jobQueue.countReadyJobs(true, null), equalTo(0));
        //add some jobs
        jobQueue.insert(createNewJobHolder());
        jobQueue.insert(createNewJobHolder(new Params(0).requireNetwork()));
        long now = System.nanoTime();
        long delay = 1000;
        jobQueue.insert(createNewJobHolderWithDelayUntil(new Params(0), now + TimeUnit.MILLISECONDS.toNanos(delay)));
        MatcherAssert.assertThat("ready count should be 1 if there is no network", jobQueue.countReadyJobs(false, null), equalTo(1));
        MatcherAssert.assertThat("ready count should be 2 if there is network", jobQueue.countReadyJobs(true, null), equalTo(2));
        Thread.sleep(delay);
        MatcherAssert.assertThat("when needed delay time passes, ready count should be 3", jobQueue.countReadyJobs(true, null), equalTo(3));
        MatcherAssert.assertThat("when needed delay time passes but no network, ready count should be 2", jobQueue.countReadyJobs(false, null), equalTo(2));
        jobQueue.insert(createNewJobHolder(new Params(5).groupBy("group1")));
        jobQueue.insert(createNewJobHolder(new Params(5).groupBy("group1")));
        MatcherAssert.assertThat("when more than 1 job from same group is created, ready jobs should increment only by 1",
                jobQueue.countReadyJobs(true, null), equalTo(4));
        MatcherAssert.assertThat("excluding groups should work",
                jobQueue.countReadyJobs(true, Arrays.asList(new String[]{"group1"})), equalTo(3));
        MatcherAssert.assertThat("giving a non-existing group should not fool the count",
                jobQueue.countReadyJobs(true, Arrays.asList(new String[]{"group3423"})), equalTo(4));
        jobQueue.insert(createNewJobHolder(new Params(3).groupBy("group2")));
        MatcherAssert.assertThat("when a job from another group is added, ready job count should inc",
                jobQueue.countReadyJobs(true, null), equalTo(5));
        now = System.nanoTime();
        jobQueue.insert(createNewJobHolderWithDelayUntil(new Params(3).groupBy("group3"), now + TimeUnit.MILLISECONDS.toNanos(delay)));
        MatcherAssert.assertThat("when a delayed job from another group is added, ready count should not change",
                jobQueue.countReadyJobs(true, null), equalTo(5));
        jobQueue.insert(createNewJobHolder(new Params(3).groupBy("group3")));
        MatcherAssert.assertThat("when another job from delayed group is added, ready job count should inc",
                jobQueue.countReadyJobs(true, null), equalTo(6));
        Thread.sleep(delay);
        MatcherAssert.assertThat("when delay passes and a job from existing group becomes available, ready job count should not change",
                jobQueue.countReadyJobs(true, null), equalTo(6));
        MatcherAssert.assertThat("when some groups are excluded, count should be correct",
                jobQueue.countReadyJobs(true, Arrays.asList(new String[]{"group1", "group3"})), equalTo(4));

        //jobs w/ same group id but with different persistence constraints should not fool the count
        now = System.nanoTime();
        jobQueue.insert(createNewJobHolderWithDelayUntil(new Params(0).persist().groupBy("group10"), now + 1000));
        jobQueue.insert(createNewJobHolderWithDelayUntil(new Params(0).groupBy("group10"), now + 1000));
        jobQueue.insert(createNewJobHolderWithDelayUntil(new Params(0).persist().groupBy("group10"), now - 1000));
        jobQueue.insert(createNewJobHolderWithDelayUntil(new Params(0).groupBy("group10"), now - 1000));
        MatcherAssert.assertThat("when many jobs are added w/ different constraints but same group id, ready count should not be fooled",
                jobQueue.countReadyJobs(true, Arrays.asList(new String[]{"group1", "group3"})), equalTo(5));
        MatcherAssert.assertThat("when many jobs are added w/ different constraints but same group id, ready count should not be fooled",
                jobQueue.countReadyJobs(true, null), equalTo(7));
        MatcherAssert.assertThat("when many jobs are added w/ different constraints but same group id, ready count should not be fooled",
                jobQueue.countReadyJobs(false, Arrays.asList(new String[]{"group1", "group3"})), equalTo(4));
    }

    @Test
    public void testJobFields() throws Exception {
        long sessionId = (long) (Math.random() * 1000);
        JobQueue jobQueue = createNewJobQueueWithSessionId(sessionId);
        JobHolder jobHolder = createNewJobHolder();


        int priority = (int) (Math.random() * 1000);
        jobHolder.setPriority(priority);
        DummyJob dummyJob = new DummyJob(new Params(0));
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
        return createNewJobHolder(new Params(0));
    }

    protected JobHolder createNewJobHolder(Params params) {
        return new JobHolder(null, getPriorityField(params).get(), getGroupIdField(params).get(), 0, new DummyJob(params), System.nanoTime(), Long.MIN_VALUE, JobManager.NOT_RUNNING_SESSION_ID);
    }

    private JobHolder createNewJobHolderWithDelayUntil(Params params, long delayUntil) {
        JobHolder jobHolder = createNewJobHolder(params);
        getDelayUntilNsField(jobHolder).set(delayUntil);
        return jobHolder;
    }

    private JobQueue createNewJobQueue() {
        return createNewJobQueueWithSessionId(System.nanoTime());
    }

    private JobQueue createNewJobQueueWithSessionId(Long sessionId) {
        return currentFactory.createNew(sessionId, "id_" + sessionId);
    }
}
