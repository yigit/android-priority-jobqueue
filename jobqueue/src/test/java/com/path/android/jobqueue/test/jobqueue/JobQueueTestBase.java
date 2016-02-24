package com.path.android.jobqueue.test.jobqueue;

import com.path.android.jobqueue.JobHolder;
import com.path.android.jobqueue.JobManager;
import com.path.android.jobqueue.JobQueue;
import com.path.android.jobqueue.Params;
import com.path.android.jobqueue.TagConstraint;
import com.path.android.jobqueue.test.TestBase;
import com.path.android.jobqueue.test.jobs.DummyJob;
import com.path.android.jobqueue.test.util.JobQueueFactory;
import org.fest.reflect.core.*;
import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.*;

import org.hamcrest.CoreMatchers;
import org.hamcrest.Matcher;
import org.hamcrest.MatcherAssert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import static com.path.android.jobqueue.TagConstraint.ALL;
import static com.path.android.jobqueue.TagConstraint.ANY;

@Ignore
public abstract class JobQueueTestBase extends TestBase {
    JobQueueFactory currentFactory;

    public JobQueueTestBase(JobQueueFactory factory) {
        currentFactory = factory;
    }

    @Before
    public void setup() {
        enableDebug();
    }

    @Test
    public void testBasicAddRemoveCount() throws Exception {
        final int ADD_COUNT = 6;
        JobQueue jobQueue = createNewJobQueue();
        assertThat((int) jobQueue.count(), equalTo(0));
        assertThat(jobQueue.nextJobAndIncRunCount(true, null), nullValue());
        for (int i = 0; i < ADD_COUNT; i++) {
            JobHolder holder = createNewJobHolder();
            jobQueue.insert(holder);
            assertThat((int) jobQueue.count(), equalTo(i + 1));
            assertThat(holder.getId(), notNullValue());
            jobQueue.insertOrReplace(holder);
            assertThat((int) jobQueue.count(), equalTo(i + 1));
        }
        JobHolder firstHolder = jobQueue.nextJobAndIncRunCount(true, null);
        assertThat(firstHolder.getRunCount(), equalTo(1));
        //size should be down 1
        assertThat((int) jobQueue.count(), equalTo(ADD_COUNT - 1));
        //should return another job
        JobHolder secondHolder = jobQueue.nextJobAndIncRunCount(true, null);
        assertThat(secondHolder.getRunCount(), equalTo(1));
        //size should be down 2
        assertThat((int) jobQueue.count(), equalTo(ADD_COUNT - 2));
        //second holder and first holder should have different ids
        assertThat(firstHolder.getId(), not(secondHolder.getId()));
        jobQueue.remove(secondHolder);
        assertThat((int) jobQueue.count(), equalTo(ADD_COUNT - 2));
        jobQueue.remove(secondHolder);
        //non existed job removed, count should be the same
        assertThat((int) jobQueue.count(), equalTo(ADD_COUNT - 2));
        jobQueue.remove(firstHolder);
        assertThat((int) jobQueue.count(), equalTo(ADD_COUNT - 2));
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
            assertThat(holder.getPriority() <= minPriority, is(true));
        }
        assertThat(jobQueue.nextJobAndIncRunCount(true, null), nullValue());
    }


    @Test
    public void testDelayUntilWithPriority() throws Exception {
        JobQueue jobQueue = createNewJobQueue();
        long now = System.nanoTime();
        JobHolder lowPriorityHolder = createNewJobHolderWithDelayUntil(new Params(5), now + 10000 * JobManager.NS_PER_MS);
        JobHolder highPriorityHolder = createNewJobHolderWithDelayUntil(new Params(10), now + 20000 * JobManager.NS_PER_MS);
        jobQueue.insert(lowPriorityHolder);
        jobQueue.insert(highPriorityHolder);
        assertThat("when asked, if lower priority job has less delay until, we should return it",
                jobQueue.getNextJobDelayUntilNs(true, null), equalTo(
                lowPriorityHolder.getDelayUntilNs()));

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
        assertThat("first jobs should be from group group1 if group2 is excluded",
                holder1.getJob().getRunGroupId(), equalTo("group1"));
        assertThat("correct job should be returned if groupId is provided",
                holder1.getId(), equalTo(jobId1));
        assertThat("no jobs should be returned if all groups are excluded",
                jobQueue.nextJobAndIncRunCount(true,
                        Arrays.asList(new String[]{"group1", "group2"})),
                is(nullValue()));
        long jobId6 = jobQueue.insert(createNewJobHolder(new Params(0)));
        assertThat("both groups are disabled, null group job should be returned",
                jobQueue.nextJobAndIncRunCount(true,
                        Arrays.asList(new String[]{"group1", "group2"})).getId(),
                is(jobId6));
        assertThat("if group1 is excluded, next job should be from group2",
                jobQueue.nextJobAndIncRunCount(true, Arrays.asList(new String[]{"group1"})).getJob().getRunGroupId()
                , equalTo("group2"));

        //to test re-run case, add the job back in
        jobQueue.insertOrReplace(holder1);
        //ask for it again, should return the same holder because it is grouped
        JobHolder holder2 = jobQueue.nextJobAndIncRunCount(true, null);
        assertThat("for grouped jobs, re-fetching job should work fine",
                holder2.getId(), equalTo(holder1.getId()));

        JobHolder holder3 = jobQueue.nextJobAndIncRunCount(true,
                        Arrays.asList(new String[]{"group1"}));
        assertThat("if a group it excluded, next available from another group should be returned",
                holder3.getId(), equalTo(jobId4));

        //add two more non-grouped jobs
        long jobId7 = jobQueue.insert(createNewJobHolder(new Params(0)));
        long jobId8 = jobQueue.insert(createNewJobHolder(new Params(0)));
        JobHolder holder4 = jobQueue.nextJobAndIncRunCount(true,
                Arrays.asList(new String[]{"group1", "group2"}));
        assertThat("if all grouped jobs are excluded, non-grouped jobs should be returned",
                holder4.getId(),
                equalTo(jobId7));
        jobQueue.insertOrReplace(holder4);
        //for non-grouped jobs, run counts should be respected
        assertThat("if all grouped jobs are excluded, re-inserted highest priority job should still be returned",
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
        assertThat("when asked, if job's due has passed, highest priority jobs's delay until should be " +
                "returned",
                jobQueue.getNextJobDelayUntilNs(true, null), equalTo(highPriorityHolder.getDelayUntilNs()));
        //make sure soon job is valid now
        Thread.sleep(soonJobDelay);

        assertThat("when a job's time come, it should be returned",
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

        assertThat("if there is no network, delay until should be provided for no network job",
            jobQueue.getNextJobDelayUntilNs(false, null), equalTo(noNetworkJobHolder.getDelayUntilNs()));

        assertThat("if there is network, delay until should be provided for network job because it is " +
                "sooner", jobQueue.getNextJobDelayUntilNs(true, null), equalTo(networkJobHolder.getDelayUntilNs()));

        JobHolder noNetworkJobHolder2 = createNewJobHolderWithDelayUntil(new Params(0), now + 100000 * JobManager.NS_PER_MS);

        jobQueue.insert(noNetworkJobHolder2);
        assertThat("if there is network, any job's delay until should be returned",
                jobQueue.getNextJobDelayUntilNs(true, null), equalTo(noNetworkJobHolder2.getDelayUntilNs()));
    }

    @Test
    public void testDelayUntilWithExcludeGroups() throws Exception {
        JobQueue jobQueue = createNewJobQueue();
        long now = System.nanoTime();
        JobHolder networkJobHolder = createNewJobHolderWithDelayUntil(new Params(0).requireNetwork()
                .groupBy("group1"), now + 200000 * JobManager.NS_PER_MS);

        JobHolder noNetworkJobHolder = createNewJobHolderWithDelayUntil(new Params(0)
                .groupBy("group2"), now + 500000 * JobManager.NS_PER_MS);

        jobQueue.insert(networkJobHolder);
        jobQueue.insert(noNetworkJobHolder);

        assertThat("if there is no network, delay until should be provided for no network job",
                jobQueue.getNextJobDelayUntilNs(false, null),
                equalTo(noNetworkJobHolder.getDelayUntilNs()));
        assertThat("if there is no network, delay until should be provided for no network job",
                jobQueue.getNextJobDelayUntilNs(false, new ArrayList<String>()),
                equalTo(noNetworkJobHolder.getDelayUntilNs()));

        assertThat("if there is no network, but the group is disabled, delay until should be null",
                jobQueue.getNextJobDelayUntilNs(false, Arrays.asList("group2")), nullValue());

        assertThat("if there is network, but both groups are disabled, delay until should be null"
                , jobQueue.getNextJobDelayUntilNs(true, Arrays.asList("group1", "group2")),
                nullValue());
        assertThat("if there is network, but group1 is disabled, delay should come from group2"
                , jobQueue.getNextJobDelayUntilNs(true, Arrays.asList("group1")),
                equalTo(noNetworkJobHolder.getDelayUntilNs()));
        assertThat("if there is network, but group2 is disabled, delay should come from group1"
                , jobQueue.getNextJobDelayUntilNs(true, Arrays.asList("group2")),
                equalTo(networkJobHolder.getDelayUntilNs()));

        JobHolder noNetworkJobHolder2 = createNewJobHolderWithDelayUntil(new Params(0),
                now + 100000 * JobManager.NS_PER_MS);

        jobQueue.insert(noNetworkJobHolder2);
        assertThat("if there is a 3rd job and other gorups are disabled. 3rd job's delay should be "
                        + "returned",
                jobQueue.getNextJobDelayUntilNs(true, Arrays.asList("group1", "group2")),
                equalTo(noNetworkJobHolder2.getDelayUntilNs()));
    }

    @Test
    public void testTruncate() throws Exception {
        JobQueue jobQueue = createNewJobQueue();
        final int LIMIT = 20;
        for(int i = 0; i < LIMIT; i ++) {
            jobQueue.insert(createNewJobHolder());
        }
        assertThat("queue should have all jobs", jobQueue.count(), equalTo(LIMIT));
        jobQueue.clear();
        assertThat("after clear, queue should be empty", jobQueue.count(), equalTo(0));
        for(int i = 0; i < LIMIT; i ++) {
            jobQueue.insert(createNewJobHolder());
        }
        assertThat("if we add jobs again, count should match", jobQueue.count(), equalTo(LIMIT));
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
            assertThat("next job should not be null", next, notNullValue());
            assertThat("next job's priority should be lower then previous for job " + i, next.getPriority() <= lastPriority, is(true));
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

    private org.fest.reflect.field.Invoker<String> getSingleIdField(Params params) {
        return Reflection.field("singleId").ofType(String.class).in(params);
    }

    @Test
    public void testSessionId() throws Exception {
        long sessionId = (long) (Math.random() * 100000);
        JobQueue jobQueue = createNewJobQueueWithSessionId(sessionId);
        JobHolder jobHolder = createNewJobHolder();
        jobQueue.insert(jobHolder);
        jobHolder = jobQueue.nextJobAndIncRunCount(true, null);
        assertThat("session id should be attached to next job",
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
            assertThat(holder.getPriority() <= minPriority, is(true));
            jobQueue.insertOrReplace(holder);
        }
        assertThat(jobQueue.nextJobAndIncRunCount(true, null), notNullValue());
    }

    @Test
    public void testRemove() throws Exception {
        JobQueue jobQueue = createNewJobQueue();
        JobHolder holder = createNewJobHolder();
        jobQueue.insert(holder);
        Long jobId = holder.getId();
        assertThat(jobQueue.nextJobAndIncRunCount(true, null).getId(), equalTo(jobId));
        assertThat(jobQueue.nextJobAndIncRunCount(true, null), is(nullValue()));
        jobQueue.remove(holder);
        assertThat(jobQueue.nextJobAndIncRunCount(true, null), is(nullValue()));
    }

    @Test
    public void testNetwork() throws Exception {
        JobQueue jobQueue = createNewJobQueue();
        JobHolder jobHolder = createNewJobHolder(new Params(0));
        jobQueue.insert(jobHolder);
        assertThat("no network job should be returned even if there is no netowrk",
                jobQueue.nextJobAndIncRunCount(false, null), notNullValue());
        jobQueue.remove(jobHolder);

        jobHolder = createNewJobHolder(new Params(0).requireNetwork());
        assertThat("if there isn't any network, job with network requirement should not return",
                jobQueue.nextJobAndIncRunCount(false, null), nullValue());

        assertThat("if there is network, job with network requirement should be returned",
                jobQueue.nextJobAndIncRunCount(true, null), nullValue());

        jobQueue.remove(jobHolder);

        jobHolder = createNewJobHolder(new Params(1));
        JobHolder jobHolder2 = createNewJobHolder(new Params(5).requireNetwork());
        long firstJobId = jobQueue.insert(jobHolder);
        long secondJobId = jobQueue.insert(jobHolder2);
        JobHolder retrieved = jobQueue.nextJobAndIncRunCount(false, null);
        assertThat("one job should be returned w/o network", retrieved, notNullValue());
        if(retrieved != null) {
            assertThat("no network job should be returned although it has lower priority", retrieved.getId(), equalTo(firstJobId));
        }

        assertThat("no other job should be returned w/o network", jobQueue.nextJobAndIncRunCount(false, null), nullValue());

        retrieved = jobQueue.nextJobAndIncRunCount(true, null);
        assertThat("if network is back, network requiring job should be returned", retrieved, notNullValue());
        if(retrieved != null) {
            assertThat("when there is network, network job should be returned", retrieved.getId(), equalTo(secondJobId));
        }
        //add first job back
        jobQueue.insertOrReplace(jobHolder);
        //add second job back
        jobQueue.insertOrReplace(jobHolder2);

        retrieved = jobQueue.nextJobAndIncRunCount(true, null);
        assertThat("if network is back, job w/ higher priority should be returned", retrieved, notNullValue());
        if(retrieved != null) {
            assertThat("if network is back, job w/ higher priority should be returned", retrieved.getId(), equalTo(secondJobId));
        }
        jobQueue.insertOrReplace(jobHolder2);

        JobHolder highestPriorityJob = createNewJobHolder(new Params(10));
        long highestPriorityJobId = jobQueue.insert(highestPriorityJob);
        retrieved = jobQueue.nextJobAndIncRunCount(true, null);
        assertThat("w/ or w/o network, highest priority should be returned", retrieved, notNullValue());
        if(retrieved != null) {
            assertThat("w/ or w/o network, highest priority should be returned", retrieved.getId(), equalTo(highestPriorityJobId));
        }

        //TODO test delay until
    }

    @Test
    public void testCountReadyJobs() throws Exception {
        JobQueue jobQueue = createNewJobQueue();
        assertThat("initial count should be 0 for ready jobs", jobQueue.countReadyJobs(true, null), equalTo(0));
        //add some jobs
        jobQueue.insert(createNewJobHolder());
        jobQueue.insert(createNewJobHolder(new Params(0).requireNetwork()));
        long now = System.nanoTime();
        long delay = 1000;
        jobQueue.insert(createNewJobHolderWithDelayUntil(new Params(0), now + TimeUnit.MILLISECONDS.toNanos(delay)));
        assertThat("ready count should be 1 if there is no network", jobQueue.countReadyJobs(false, null), equalTo(1));
        assertThat("ready count should be 2 if there is network", jobQueue.countReadyJobs(true, null), equalTo(2));
        Thread.sleep(delay);
        assertThat("when needed delay time passes, ready count should be 3", jobQueue.countReadyJobs(true, null), equalTo(3));
        assertThat("when needed delay time passes but no network, ready count should be 2", jobQueue.countReadyJobs(false, null), equalTo(2));
        jobQueue.insert(createNewJobHolder(new Params(5).groupBy("group1")));
        jobQueue.insert(createNewJobHolder(new Params(5).groupBy("group1")));
        assertThat("when more than 1 job from same group is created, ready jobs should increment only by 1",
                jobQueue.countReadyJobs(true, null), equalTo(4));
        assertThat("excluding groups should work",
                jobQueue.countReadyJobs(true, Arrays.asList(new String[]{"group1"})), equalTo(3));
        assertThat("giving a non-existing group should not fool the count",
                jobQueue.countReadyJobs(true, Arrays.asList(new String[]{"group3423"})), equalTo(4));
        jobQueue.insert(createNewJobHolder(new Params(3).groupBy("group2")));
        assertThat("when a job from another group is added, ready job count should inc",
                jobQueue.countReadyJobs(true, null), equalTo(5));
        now = System.nanoTime();
        jobQueue.insert(createNewJobHolderWithDelayUntil(new Params(3).groupBy("group3"), now + TimeUnit.MILLISECONDS.toNanos(delay)));
        assertThat("when a delayed job from another group is added, ready count should not change",
                jobQueue.countReadyJobs(true, null), equalTo(5));
        jobQueue.insert(createNewJobHolder(new Params(3).groupBy("group3")));
        assertThat("when another job from delayed group is added, ready job count should inc",
                jobQueue.countReadyJobs(true, null), equalTo(6));
        Thread.sleep(delay);
        assertThat("when delay passes and a job from existing group becomes available, ready job count should not change",
                jobQueue.countReadyJobs(true, null), equalTo(6));
        assertThat("when some groups are excluded, count should be correct",
                jobQueue.countReadyJobs(true, Arrays.asList(new String[]{"group1", "group3"})), equalTo(4));

        //jobs w/ same group id but with different persistence constraints should not fool the count
        now = System.nanoTime();
        jobQueue.insert(createNewJobHolderWithDelayUntil(new Params(0).persist().groupBy("group10"), now + 1000));
        jobQueue.insert(createNewJobHolderWithDelayUntil(new Params(0).groupBy("group10"), now + 1000));
        jobQueue.insert(createNewJobHolderWithDelayUntil(new Params(0).persist().groupBy("group10"), now - 1000));
        jobQueue.insert(createNewJobHolderWithDelayUntil(new Params(0).groupBy("group10"), now - 1000));
        assertThat("when many jobs are added w/ different constraints but same group id, ready count should not be fooled",
                jobQueue.countReadyJobs(true, Arrays.asList(new String[]{"group1", "group3"})), equalTo(5));
        assertThat("when many jobs are added w/ different constraints but same group id, ready count should not be fooled",
                jobQueue.countReadyJobs(true, null), equalTo(7));
        assertThat("when many jobs are added w/ different constraints but same group id, ready count should not be fooled",
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
        jobHolder.setJob(dummyJob);
        int runCount = (int) (Math.random() * 10);
        jobHolder.setRunCount(runCount);

        long id = jobQueue.insert(jobHolder);


        for (int i = 0; i < 2; i++) {
            JobHolder received = jobQueue.nextJobAndIncRunCount(true, null);
            assertThat("job id should be preserved", received.getId(), equalTo(id));
            assertThat("job priority should be preserved", received.getPriority(), equalTo(priority));
            assertThat("job session id should be assigned", received.getRunningSessionId(), equalTo(sessionId));
            assertThat("job run count should be incremented", received.getRunCount(), equalTo(runCount + i + 1));
            jobQueue.insertOrReplace(received);
        }
    }

    private void assertJob(JobQueue jobQueue, String msg, long id, /*nullable*/ JobHolder holder) {
        if(holder == null) {
            assertThat(msg, jobQueue.findJobById(id), nullValue());
            return;
        }
        assertThat(msg + "(existence check)", jobQueue.findJobById(id), notNullValue());
        assertThat(msg + "(id check)", jobQueue.findJobById(id).getId(), is(holder.getId()));
    }

    @Test
    public void testFindJobHolderById() {
        JobQueue jobQueue = createNewJobQueue();
        assertJob(jobQueue, "non existing job (negative id)", -4, null);
        assertJob(jobQueue, "non existing job (positive id)", +4, null);
        final int LIMIT = 100;
        JobHolder[] holders = new JobHolder[LIMIT];
        long[] ids = new long[LIMIT];
        for(int i =  0; i < LIMIT; i++) {
            holders[i] = createNewJobHolder(new Params((int) (Math.random() * 50)).setPersistent(Math.random() < .5).setRequiresNetwork(Math.random() < .5));
            ids[i] = jobQueue.insert(holders[i]);
            assertJob(jobQueue, "job by id should work for inserted job", ids[i], holders[i]);
        }
        final int REMOVE_CNT = LIMIT / 2;
        for(int i = 0; i < REMOVE_CNT; i++) {
            int ind = (int) (Math.random() * LIMIT);
            if(holders[ind] == null) {
                continue;
            }
            //remove some randomly, up to half
            jobQueue.remove(holders[ind]);
            holders[ind] = null;
        }
        //re-query all, ensure we can still find non-removed jobs and not find removed jobs
        for(int i =  0; i < LIMIT; i++) {
            if(holders[i] != null) {
                assertJob(jobQueue, "if job is still in the Q, it should be returned", ids[i], holders[i]);
                //re add job
                jobQueue.insertOrReplace(holders[i]);
                //re-test after re-add
                assertJob(jobQueue, "after re-insert, if job is still in the Q, it should be returned", ids[i], holders[i]);
            } else {
                assertJob(jobQueue, "removed job should not be returned in id query", ids[i], null);
            }
        }
        jobQueue.clear();
        for(int i = 0; i < LIMIT; i++) {
            assertJob(jobQueue, "after clear, find by id should return null", ids[i], null);
        }
    }

    @Test
    public void testTagsWithMultipleHolders() {
        JobQueue jobQueue = createNewJobQueue();
        final String tag1 = UUID.randomUUID().toString();
        String tag2 = UUID.randomUUID().toString();
        while (tag2.equals(tag1)) {
            tag2 = UUID.randomUUID().toString();
        }
        String tag3 = UUID.randomUUID().toString();
        while (tag3.equals(tag1) || tag3.equals(tag2)) {
            tag3 = UUID.randomUUID().toString();
        }

        JobHolder holder1 = createNewJobHolder(new Params(0).addTags(tag1, tag2));
        JobHolder holder2 = createNewJobHolder(new Params(0).addTags(tag1, tag3));
        jobQueue.insert(holder1);
        jobQueue.insert(holder2);
        Set<JobHolder> twoJobs = jobQueue.findJobsByTags(ANY, true, Collections.<Long>emptyList(), tag1);
        Set<Long> resultIds = ids(twoJobs);

        assertThat("two jobs should be returned", twoJobs.size(), is(2));
        assertThat("should have job id 1", resultIds, hasItems(holder1.getId(), holder2.getId()));
        for (String tag : new String[]{tag2, tag3}) {
            Set<JobHolder> oneJob = jobQueue.findJobsByTags(ANY, true, Collections.<Long>emptyList(), tag);
            resultIds = ids(oneJob);
            assertThat("one job should be returned", oneJob.size(), is(1));
            if (tag.equals(tag2)) {
                assertThat("should have job id 1", resultIds, hasItems(holder1.getId()));
            } else {
                assertThat("should have job id 2", resultIds, hasItems(holder2.getId()));
            }
        }
        jobQueue.remove(holder1);
        assertTags("after one of the jobs is removed", jobQueue, holder2);
    }

    private Set<Long> ids(Collection<JobHolder> result) {
        HashSet<Long> ids = new HashSet<Long>();
        for (JobHolder holder : result) {
            ids.add(holder.getId());
        }
        return ids;
    }

    @Test
    public void testFindByMultipleTags() {
        JobQueue jobQueue = createNewJobQueue();
        final String tag1 = UUID.randomUUID().toString();
        String tag2 = UUID.randomUUID().toString();
        while (tag2.equals(tag1)) {
            tag2 = UUID.randomUUID().toString();
        }
        JobHolder holder = createNewJobHolder(new Params(0).addTags(tag1, tag2));
        jobQueue.insert(holder);

        assertTags("job with two tags", jobQueue, holder);
        jobQueue.insertOrReplace(holder);
        assertTags("job with two tags, reinserted", jobQueue, holder);
        jobQueue.remove(holder);
        assertThat("when job is removed, it should return none",
                jobQueue.findJobsByTags(ANY, true, Collections.<Long>emptyList(), tag1).size(), is(0));
        assertThat("when job is removed, it should return none",
                jobQueue.findJobsByTags(ANY, true, Collections.<Long>emptyList(), tag2).size(), is(0));
    }

    @Test
    public void testFindByTags() {
        JobQueue jobQueue = createNewJobQueue();
        assertThat("empty queue should return 0",jobQueue.findJobsByTags(ANY, false, Collections.<Long>emptyList(), "abc").size(), is(0));
        jobQueue.insert(createNewJobHolder());
        Set<JobHolder> result = jobQueue.findJobsByTags(ANY, false, Collections.<Long>emptyList(), "blah");
        assertThat("if job does not have a tag, it should return 0", result.size(), is(0));

        final String tag1 = UUID.randomUUID().toString();
        JobHolder holder = createNewJobHolder(new Params(0).addTags(tag1));
        jobQueue.insert(holder);
        assertTags("holder with 1 tag", jobQueue, holder);
        jobQueue.insertOrReplace(holder);
        assertTags("holder with 1 tag reinserted", jobQueue, holder);
        jobQueue.remove(holder);
        assertThat("when job is removed, it should return none", jobQueue.findJobsByTags(ANY, false, Collections.<Long>emptyList(), tag1).size(), is(0));

        JobHolder holder2 = createNewJobHolder(new Params(0).addTags(tag1));
        jobQueue.insert(holder2);
        assertThat("it should return the job", jobQueue.findJobsByTags(ANY, false, Collections.<Long>emptyList(), tag1).size(), is(1));
        jobQueue.onJobCancelled(holder2);
        assertThat("when queried w/ exclude cancelled, it should not return the job",
                jobQueue.findJobsByTags(ANY, true, Collections.<Long>emptyList(), tag1).size(), is(0));

    }

    private void assertTags(String msg, JobQueue jobQueue, JobHolder holder) {
        Set<JobHolder> result;
        String wrongTag;
        final long id = holder.getId();
        boolean found;
        Matcher allTagsMatcher = CoreMatchers.hasItems(holder.getTags().toArray(new String[holder.getTags().size()]));
        do {
            wrongTag = UUID.randomUUID().toString();
            found = false;
            if(holder.getTags() != null) {
                for(String tag : holder.getTags()) {
                    if(tag.equals(wrongTag)) {
                        found = true;
                        break;
                    }
                }
            }
        } while (found);
        result = jobQueue.findJobsByTags(ANY, true, Collections.<Long>emptyList(), wrongTag);
        found = false;
        for(JobHolder received : result) {
            if(received.getId().equals(holder.getId())) {
                found = true;
            }
        }
        assertThat(msg + " when wrong tag is given, our job should not return", found, is(false));

        if(holder.getTags() == null) {
            return;// done
        }
        Collection<Long> exclude = Arrays.asList(holder.getId());
        for(String[] tags : combinations(holder.getTags())) {
            result = jobQueue.findJobsByTags(TagConstraint.ANY, true, Collections.<Long>emptyList(), tags);
            if (tags.length == 0) {
                assertThat(msg + " empty tag list, should return 0 jobs", result.size(), is(0));
            } else {
                assertThat(msg + " any combinations: when correct tag is given, it should return one", result.size(), is(1));
                assertThat(msg + " any combinations: returned job should be the correct one", result.iterator().next().getId(), is(id));
                assertThat(msg + " returned holder should have all tags:", result.iterator().next().getTags(), allTagsMatcher);
            }
            result = jobQueue.findJobsByTags(TagConstraint.ANY,true,  exclude, tags);
            assertThat(msg + " when excluded, holder should not show up in results", result.size(), is(
                    0));

        }

        for(String[] tags : combinations(holder.getTags())) {
            result = jobQueue.findJobsByTags(ALL, true, Collections.<Long>emptyList(), tags);
            if (tags.length == 0) {
                assertThat(msg + " empty tag list, should return 0 jobs", result.size(), is(0));
            } else {
                assertThat(msg + " all combinations: when correct tag is given, it should return one",
                        result.size(), is(1));
                assertThat(msg + " all combinations: returned job should be the correct one",
                        result.iterator().next().getId(), is(id));
                assertThat(msg + " returned holder should have all tags:", result.iterator().next().getTags(), allTagsMatcher);
            }
            result = jobQueue.findJobsByTags(ALL, true, exclude, tags);
            assertThat(msg + " when excluded, holder should not show up in results", result.size(), is(0));
        }

        for(String[] tags : combinations(holder.getTags())) {
            String[] tagsWithAdditional = new String[tags.length + 1];
            System.arraycopy(tags, 0, tagsWithAdditional, 0, tags.length);
            tagsWithAdditional[tags.length] = wrongTag;
            result = jobQueue.findJobsByTags(TagConstraint.ANY, true, Collections.<Long>emptyList(), tagsWithAdditional);
            if (tags.length == 0) {
                assertThat(msg + " empty tag list, should return 0 jobs", result.size(), is(0));
            } else {
                assertThat(msg + " any combinations with wrong tag: when correct tag is given, it should return one",
                        result.size(), is(1));
                assertThat(msg + " any combinations with wrong tag: returned job should be the correct one",
                        result.iterator().next().getId(), is(id));
                assertThat(msg + " returned holder should have all tags:", result.iterator().next().getTags(), allTagsMatcher);
            }

            result = jobQueue.findJobsByTags(ALL, true, Collections.<Long>emptyList(), tagsWithAdditional);
            assertThat(msg + " all combinations with wrong tag: when an additional wrong tag is given, it should return 0", result.size(), is(0));

            result = jobQueue.findJobsByTags(ALL, true, exclude, tagsWithAdditional);
            assertThat(msg + " when excluded, holder should not show up in results", result.size(), is(0));
        }
    }

    @Test
    public void testFindAll() {
        JobQueue jobQueue = createNewJobQueue();
        assertThat("empty queue should return 0",jobQueue.findAllJobs(false, Collections.<Long>emptyList()).size(), is(0));
        jobQueue.insert(createNewJobHolder());
        Set<JobHolder> result = jobQueue.findAllJobs(true, Collections.<Long>emptyList());
        assertThat("if job was not cancelled, it should still return it", result.size(), is(1));

        final String singleId = UUID.randomUUID().toString();
        JobHolder holder = createNewJobHolder(new Params(0).singleWith(singleId));
        jobQueue.insert(holder);
        assertThat("when second job is inserted, it should return it", jobQueue.findAllJobs(false, Collections.<Long>emptyList()).size(), is(2));
        jobQueue.insertOrReplace(holder);
        assertThat("when second job is reinserted, result should not change", jobQueue.findAllJobs(false, Collections.<Long>emptyList()).size(), is(2));
        jobQueue.remove(holder);
        assertThat("when job is removed, it should return one less", jobQueue.findAllJobs(false, Collections.<Long>emptyList()).size(), is(1));

        JobHolder holder2 = createNewJobHolder(new Params(0).singleWith(singleId));
        jobQueue.insert(holder2);
        assertThat("it should return the job", jobQueue.findAllJobs(false, Collections.<Long>emptyList()).size(), is(2));
        jobQueue.onJobCancelled(holder2);
        assertThat("when queried w/ exclude cancelled, it should not return the job",
                jobQueue.findAllJobs(true, Collections.<Long>emptyList()).size(), is(1));

    }

    List<String[]> combinations(Set<String> strings) {
        if(strings.size() == 0) {
            List<String[]> result = new ArrayList<String[]>();
            result.add(new String[]{});
            return result;
        }
        Set<String> remaining = new HashSet<String>();
        boolean skip = true;
        for(String str : strings) {
            if(skip) {
                skip = false;
            } else {
                remaining.add(str);
            }
        }
        List<String[]> others = combinations(remaining);
        List<String[]> result = new ArrayList<String[]>();
        for(String[] subset : others) {
            result.add(subset);
            // add myself
            String[] copy = new String[subset.length + 1];
            copy[0] = strings.iterator().next();
            for(int i = 1; i <= subset.length; i++) {
                copy[i] = subset[i - 1];
            }
            result.add(copy);
        }
        return result;
    }


    protected JobHolder createNewJobHolder() {
        return createNewJobHolder(new Params(0));
    }

    protected JobHolder createNewJobHolder(Params params) {
        long delay = getDelayMsField(params).get();
        return new JobHolder(null, getPriorityField(params).get(), getGroupIdField(params).get(),
                getSingleIdField(params).get(), 0, new DummyJob(params), System.nanoTime(),
                delay > 0 ? System.nanoTime() +  delay * JobManager.NS_PER_MS : JobManager.NOT_DELAYED_JOB_DELAY, JobManager.NOT_RUNNING_SESSION_ID);
    }

    private JobHolder createNewJobHolderWithDelayUntil(Params params, long delayUntil) {
        JobHolder jobHolder = createNewJobHolder(params);
        getDelayUntilNsField(jobHolder).set(delayUntil);
        return jobHolder;
    }

    protected JobQueue createNewJobQueue() {
        return createNewJobQueueWithSessionId(System.nanoTime());
    }

    private JobQueue createNewJobQueueWithSessionId(Long sessionId) {
        return currentFactory.createNew(sessionId, "id_" + sessionId);
    }
}
