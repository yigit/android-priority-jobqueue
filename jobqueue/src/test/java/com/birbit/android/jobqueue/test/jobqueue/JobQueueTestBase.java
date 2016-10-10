package com.birbit.android.jobqueue.test.jobqueue;

import com.birbit.android.jobqueue.Constraint;
import com.birbit.android.jobqueue.JobManager;
import com.birbit.android.jobqueue.TestConstraint;
import com.birbit.android.jobqueue.JobHolder;
import com.birbit.android.jobqueue.JobQueue;
import com.birbit.android.jobqueue.Params;
import com.birbit.android.jobqueue.TagConstraint;
import com.birbit.android.jobqueue.network.NetworkUtil;
import com.birbit.android.jobqueue.test.TestBase;
import com.birbit.android.jobqueue.test.jobs.DummyJob;
import com.birbit.android.jobqueue.test.timer.MockTimer;
import com.birbit.android.jobqueue.test.util.JobQueueFactory;
import com.birbit.android.jobqueue.timer.Timer;

import org.fest.reflect.core.*;

import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.*;

import org.hamcrest.CoreMatchers;
import org.hamcrest.Matcher;
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

import static com.birbit.android.jobqueue.TagConstraint.ALL;
import static com.birbit.android.jobqueue.TagConstraint.ANY;
import static com.birbit.android.jobqueue.TestConstraint.forTags;

@Ignore
public abstract class JobQueueTestBase extends TestBase {
    JobQueueFactory currentFactory;
    MockTimer mockTimer = new MockTimer();

    public JobQueueTestBase(JobQueueFactory factory) {
        currentFactory = factory;
    }

    @Test
    public void testBasicAddRemoveCount() throws Exception {
        final int ADD_COUNT = 6;
        JobQueue jobQueue = createNewJobQueue();
        assertThat((int) jobQueue.count(), equalTo(0));
        TestConstraint constraint = new TestConstraint(mockTimer);
        constraint.setExcludeRunning(true);
        assertThat(jobQueue.nextJobAndIncRunCount(constraint), nullValue());
        for (int i = 0; i < ADD_COUNT; i++) {
            JobHolder holder = createNewJobHolder();
            jobQueue.insert(holder);
            assertThat((int) jobQueue.count(), equalTo(i + 1));
            assertThat(holder.getInsertionOrder(), equalTo(i + 1L));
            jobQueue.insertOrReplace(holder);
            assertThat((int) jobQueue.count(), equalTo(i + 1));
        }
        JobHolder firstHolder = jobQueue.nextJobAndIncRunCount(constraint);
        assertThat(firstHolder.getRunCount(), equalTo(1));
        //size should be down 1
        assertThat((int) jobQueue.count(), equalTo(ADD_COUNT - 1));
        //should return another job
        JobHolder secondHolder = jobQueue.nextJobAndIncRunCount(constraint);
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
        TestConstraint constraint = new TestConstraint(mockTimer);
        constraint.setExcludeRunning(true);
        for (int i = 0; i < JOB_LIMIT; i++) {
            JobHolder holder = jobQueue.nextJobAndIncRunCount(constraint);
            assertThat(holder.getPriority() <= minPriority, is(true));
        }
        assertThat(jobQueue.nextJobAndIncRunCount(constraint), nullValue());
    }


    @Test
    public void testDelayUntilWithPriority() throws Exception {
        JobQueue jobQueue = createNewJobQueue();
        JobHolder lowPriorityHolder = createNewJobHolderWithDelayUntil(new Params(5), 1);
        JobHolder highPriorityHolder = createNewJobHolderWithDelayUntil(new Params(10), 2);
        jobQueue.insert(lowPriorityHolder);
        jobQueue.insert(highPriorityHolder);
        assertThat("when asked, if lower priority job has less delay until, we should return it",
                jobQueue.getNextJobDelayUntilNs(new TestConstraint(mockTimer)), equalTo(
                        lowPriorityHolder.getDelayUntilNs()));
    }

    @Test
    public void testNoDeadline() throws Exception {
        JobQueue jobQueue = createNewJobQueue();
        JobHolder requireNetwork = createNewJobHolder(new Params(0).requireNetwork());
        jobQueue.insert(requireNetwork);
        TestConstraint testConstraint = new TestConstraint(mockTimer);
        testConstraint.setMaxNetworkType(NetworkUtil.DISCONNECTED);
        assertThat("when a job w/o a deadline is given, it should not be returned if not ready",
                jobQueue.nextJobAndIncRunCount(testConstraint), is(nullValue()));
        assertThat("when a job w/o a deadline is given, it should not be returned in next ready",
                jobQueue.getNextJobDelayUntilNs(testConstraint), is(nullValue()));
    }

    @Test
    public void testDeadlineWithRun() throws Exception {
        testDeadline(false);
    }

    @Test
    public void testDeadlineWithCancel() throws Exception {
        testDeadline(true);
    }

    private void testDeadline(boolean cancel) throws Exception {
        JobQueue jobQueue = createNewJobQueue();
        Params params = new Params(0).requireNetwork();
        if (cancel) {
            params.overrideDeadlineToCancelInMs(100);
        } else {
            params.overrideDeadlineToRunInMs(100);
        }
        JobHolder requireNetwork = createNewJobHolder(params);
        jobQueue.insert(requireNetwork);
        TestConstraint testConstraint = new TestConstraint(mockTimer);
        testConstraint.setMaxNetworkType(NetworkUtil.DISCONNECTED);
        assertThat("when a job w/ a deadline is given, it should not be returned if not ready",
                jobQueue.nextJobAndIncRunCount(testConstraint), is(nullValue()));
        assertThat("when a job w/ a deadline is given, it should show up in next job ready query",
                jobQueue.getNextJobDelayUntilNs(testConstraint), is(100 * JobManager.NS_PER_MS));
        mockTimer.incrementMs(100);
        JobHolder nextJob = jobQueue.nextJobAndIncRunCount(testConstraint);
        assertThat("when a job reaches deadline, it should be returned",
                nextJob, is(notNullValue()));
        assertThat("when a job reaches deadline, it should be returned",
                nextJob.getId(), is(requireNetwork.getId()));
        assertThat(nextJob.shouldCancelOnDeadline(), is(cancel));
    }

    @Test
    public void testDeadlineDoesNotAffectTags() {
        JobQueue jobQueue = createNewJobQueue();

        JobHolder jobHolder = createNewJobHolder(new Params(0).overrideDeadlineToRunInMs(10));
        jobQueue.insert(jobHolder);
        mockTimer.incrementMs(100);

        TestConstraint constraint = new TestConstraint(mockTimer);
        constraint.setTags(new String[]{"a"});
        constraint.setTagConstraint(TagConstraint.ANY);
        assertThat(jobQueue.findJobs(constraint), is(Collections.EMPTY_SET));
        assertThat(jobQueue.getNextJobDelayUntilNs(constraint), is(nullValue()));
    }

    @Test
    public void testDeadlineDoesNotAffectIdQuery() {
        JobQueue jobQueue = createNewJobQueue();

        JobHolder jobHolder = createNewJobHolder(new Params(0).overrideDeadlineToRunInMs(10));
        jobQueue.insert(jobHolder);
        mockTimer.incrementMs(100);

        TestConstraint constraint = new TestConstraint(mockTimer);
        constraint.setExcludeJobIds(Collections.singletonList(jobHolder.getId()));
        assertThat(jobQueue.findJobs(constraint), is(Collections.EMPTY_SET));
        assertThat(jobQueue.getNextJobDelayUntilNs(constraint), is(nullValue()));
    }

    @Test
    public void testDeadlineDoesNotAffectExcludeGroupQuery() {
        JobQueue jobQueue = createNewJobQueue();

        JobHolder jobHolder = createNewJobHolder(new Params(0).groupBy("g1")
                .overrideDeadlineToRunInMs(10));
        jobQueue.insert(jobHolder);
        mockTimer.incrementMs(100);

        TestConstraint constraint = new TestConstraint(mockTimer);
        constraint.setExcludeGroups(Arrays.asList("g1"));
        assertThat(jobQueue.findJobs(constraint), is(Collections.EMPTY_SET));
        assertThat(jobQueue.getNextJobDelayUntilNs(constraint), is(nullValue()));
    }

    @Test
    public void testDeadlineDoesNotAffectExcludeRunning() {
        JobQueue jobQueue = createNewJobQueue();

        JobHolder jobHolder = createNewJobHolder(new Params(0).overrideDeadlineToRunInMs(10));
        jobQueue.insert(jobHolder);
        TestConstraint testConstraint = new TestConstraint(mockTimer);
        assertThat(jobQueue.nextJobAndIncRunCount(testConstraint).getId(), is(jobHolder.getId()));
        mockTimer.incrementMs(100);
        TestConstraint constraint = new TestConstraint(mockTimer);
        constraint.setExcludeRunning(true);
        assertThat(jobQueue.findJobs(constraint), is(Collections.EMPTY_SET));
        assertThat(jobQueue.getNextJobDelayUntilNs(constraint), is(nullValue()));
    }

    @Test
    public void testGroupId() throws Exception {
        JobQueue jobQueue = createNewJobQueue();
        JobHolder jobHolder1 = createNewJobHolder(new Params(0).groupBy("group1"));
        JobHolder jobHolder2 = createNewJobHolder(new Params(0).groupBy("group1"));
        JobHolder jobHolder3 = createNewJobHolder(new Params(0).groupBy("group2"));
        JobHolder jobHolder4 = createNewJobHolder(new Params(0).groupBy("group2"));
        JobHolder jobHolder5 = createNewJobHolder(new Params(0).groupBy("group1"));
        jobQueue.insert(jobHolder1);
        jobQueue.insert(jobHolder2);
        jobQueue.insert(jobHolder3);
        jobQueue.insert(jobHolder4);
        jobQueue.insert(jobHolder5);
        TestConstraint constraint = new TestConstraint(mockTimer);
        constraint.setExcludeRunning(true);
        constraint.setExcludeGroups(Arrays.asList(new String[]{"group2"}));
        JobHolder received = jobQueue.nextJobAndIncRunCount(constraint);
        assertThat("first jobs should be from group group1 if group2 is excluded",
                received.getJob().getRunGroupId(), equalTo("group1"));
        assertThat("correct job should be returned if groupId is provided",
                received.getId(), equalTo(jobHolder1.getId()));
        constraint.setExcludeGroups(Arrays.asList(new String[]{"group1", "group2"}));
        assertThat("no jobs should be returned if all groups are excluded",
                jobQueue.nextJobAndIncRunCount(constraint), is(nullValue()));
        JobHolder jobHolder6 = createNewJobHolder(new Params(0));
        jobQueue.insert(jobHolder6);
        JobHolder tmpReceived = jobQueue.nextJobAndIncRunCount(constraint);
        assertThat("both groups are disabled, null group job should be returned",
                tmpReceived.getId(),
                is(jobHolder6.getId()));
        constraint.setExcludeGroups(Arrays.asList(new String[]{"group1"}));
        assertThat("if group1 is excluded, next job should be from group2",
                jobQueue.nextJobAndIncRunCount(constraint).getJob().getRunGroupId()
                , equalTo("group2"));

        //to test re-run case, add the job back in
        assertThat(jobQueue.insertOrReplace(received), is(true));
        //ask for it again, should return the same holder because it is grouped
        constraint.clear();
        constraint.setExcludeRunning(true);
        JobHolder received2 = jobQueue.nextJobAndIncRunCount(constraint);
        assertThat("for grouped jobs, re-fetching job should work fine",
                received2.getId(), equalTo(received.getId()));
        constraint.setExcludeGroups(Arrays.asList(new String[]{"group1"}));
        JobHolder received3 = jobQueue.nextJobAndIncRunCount(constraint);
        assertThat("if a group is excluded, next available from another group should be returned",
                received3.getId(), equalTo(jobHolder4.getId()));

        //add two more non-grouped jobs
        JobHolder jobHolder7 = createNewJobHolder(new Params(0));
        jobQueue.insert(jobHolder7);
        JobHolder jobHolder8 = createNewJobHolder(new Params(0));
        jobQueue.insert(jobHolder8);
        constraint.setExcludeGroups(Arrays.asList(new String[]{"group1", "group2"}));
        JobHolder holder4 = jobQueue.nextJobAndIncRunCount(constraint);
        assertThat("if all grouped jobs are excluded, next non-grouped job should be returned",
                holder4.getId(),
                equalTo(jobHolder7.getId()));
        jobQueue.insertOrReplace(holder4);
        //for non-grouped jobs, run counts should be respected
        assertThat("if all grouped jobs are excluded, re-inserted highest priority job should still be returned",
                jobQueue.nextJobAndIncRunCount(constraint).getId(),
                equalTo(jobHolder7.getId()));
    }

    @Test
    public void testDueDelayUntilWithPriority() throws Exception {
        JobQueue jobQueue = createNewJobQueue();
        mockTimer.setNow(2000);
        long now = mockTimer.nanoTime();
        JobHolder lowPriorityHolder = createNewJobHolderWithDelayUntil(new Params(5), now - 1000 * JobManager.NS_PER_MS);
        JobHolder highPriorityHolder = createNewJobHolderWithDelayUntil(new Params(10), now - 10000 * JobManager.NS_PER_MS);
        jobQueue.insert(lowPriorityHolder);
        jobQueue.insert(highPriorityHolder);
        long soonJobDelay = 2000;
        JobHolder highestPriorityDelayedJob = createNewJobHolderWithDelayUntil(new Params(12), now + soonJobDelay * JobManager.NS_PER_MS);
        jobQueue.insert(highestPriorityDelayedJob);
        Constraint constraint = new Constraint();
        constraint.setNowInNs(mockTimer.nanoTime());
        assertThat("when asked, if job's due has passed, highest priority jobs's delay until should be " +
                        "returned",
                jobQueue.getNextJobDelayUntilNs(constraint), equalTo(highPriorityHolder.getDelayUntilNs()));
        //make sure soon job is valid now
        mockTimer.incrementMs(soonJobDelay + 1);

        assertThat("when a job's time come, it should be returned",
                jobQueue.nextJobAndIncRunCount(constraint).getId(), equalTo(highestPriorityDelayedJob.getId()));
    }

    @Test
    public void testDelayUntil() throws Exception {
        JobQueue jobQueue = createNewJobQueue();
        long now = mockTimer.nanoTime();
        JobHolder networkJobHolder = createNewJobHolderWithDelayUntil(new Params(0).requireNetwork(), now + 2);

        JobHolder noNetworkJobHolder = createNewJobHolderWithDelayUntil(new Params(0), now + 5);

        jobQueue.insert(networkJobHolder);
        jobQueue.insert(noNetworkJobHolder);
        TestConstraint constraint = new TestConstraint(mockTimer);
        constraint.setMaxNetworkType(NetworkUtil.DISCONNECTED);
        assertThat("if there is no network, delay until should be provided for no network job",
                jobQueue.getNextJobDelayUntilNs(constraint), equalTo(noNetworkJobHolder.getDelayUntilNs()));
        constraint.clear();
        constraint.setMaxNetworkType(NetworkUtil.UNMETERED);
        assertThat("if there is network, delay until should be provided for network job because it is " +
                "sooner", jobQueue.getNextJobDelayUntilNs(constraint), equalTo(networkJobHolder.getDelayUntilNs()));

        JobHolder noNetworkJobHolder2 = createNewJobHolderWithDelayUntil(new Params(0), now + 1);

        jobQueue.insert(noNetworkJobHolder2);
        assertThat("if there is network, any job's delay until should be returned",
                jobQueue.getNextJobDelayUntilNs(constraint), equalTo(noNetworkJobHolder2.getDelayUntilNs()));
    }

    @Test
    public void testDelayUntilWithExcludeGroups() throws Exception {
        JobQueue jobQueue = createNewJobQueue();
        long now = mockTimer.nanoTime();
        JobHolder networkJobHolder = createNewJobHolderWithDelayUntil(new Params(0).requireNetwork()
                .groupBy("group1"), now + 200000 * JobManager.NS_PER_MS);

        JobHolder noNetworkJobHolder = createNewJobHolderWithDelayUntil(new Params(0)
                .groupBy("group2"), now + 500000 * JobManager.NS_PER_MS);

        jobQueue.insert(networkJobHolder);
        jobQueue.insert(noNetworkJobHolder);
        TestConstraint constraint = new TestConstraint(mockTimer);
        constraint.setMaxNetworkType(NetworkUtil.DISCONNECTED);
        constraint.setExcludeRunning(true);
        assertThat("if there is no network, delay until should be provided for no network job",
                jobQueue.getNextJobDelayUntilNs(constraint),
                equalTo(noNetworkJobHolder.getDelayUntilNs()));
        assertThat("if there is no network, delay until should be provided for no network job",
                jobQueue.getNextJobDelayUntilNs(constraint),
                equalTo(noNetworkJobHolder.getDelayUntilNs()));
        constraint.setExcludeGroups(Arrays.asList("group2"));
        constraint.setMaxNetworkType(NetworkUtil.DISCONNECTED);
        assertThat("if there is no network, but the group is disabled, delay until should be null",
                jobQueue.getNextJobDelayUntilNs(constraint), nullValue());
        constraint.setMaxNetworkType(NetworkUtil.METERED);
        constraint.setExcludeGroups(Arrays.asList("group1", "group2"));
        assertThat("if there is network, but both groups are disabled, delay until should be null"
                , jobQueue.getNextJobDelayUntilNs(constraint),
                nullValue());
        constraint.setMaxNetworkType(NetworkUtil.METERED);
        constraint.setExcludeGroups(Arrays.asList("group1"));
        assertThat("if there is network, but group1 is disabled, delay should come from group2"
                , jobQueue.getNextJobDelayUntilNs(constraint),
                equalTo(noNetworkJobHolder.getDelayUntilNs()));
        constraint.setExcludeGroups(Arrays.asList("group2"));
        assertThat("if there is network, but group2 is disabled, delay should come from group1"
                , jobQueue.getNextJobDelayUntilNs(constraint),
                equalTo(networkJobHolder.getDelayUntilNs()));

        JobHolder noNetworkJobHolder2 = createNewJobHolderWithDelayUntil(new Params(0),
                now + 100000 * JobManager.NS_PER_MS);
        constraint.setExcludeGroups(Arrays.asList("group1", "group2"));
        constraint.setMaxNetworkType(NetworkUtil.METERED);
        jobQueue.insert(noNetworkJobHolder2);
        assertThat("if there is a 3rd job and other gorups are disabled. 3rd job's delay should be "
                        + "returned",
                jobQueue.getNextJobDelayUntilNs(constraint),
                equalTo(noNetworkJobHolder2.getDelayUntilNs()));
    }

    @Test
    public void testTruncate() throws Exception {
        JobQueue jobQueue = createNewJobQueue();
        final int LIMIT = 20;
        for (int i = 0; i < LIMIT; i++) {
            jobQueue.insert(createNewJobHolder());
        }
        assertThat("queue should have all jobs", jobQueue.count(), equalTo(LIMIT));
        jobQueue.clear();
        assertThat("after clear, queue should be empty", jobQueue.count(), equalTo(0));
        for (int i = 0; i < LIMIT; i++) {
            jobQueue.insert(createNewJobHolder());
        }
        assertThat("if we add jobs again, count should match", jobQueue.count(), equalTo(LIMIT));
    }

    @Test
    public void testPriorityWithDelayedJobs() throws Exception {
        JobQueue jobQueue = createNewJobQueue();
        JobHolder delayedPriority_5 = createNewJobHolder(new Params(5));
        org.fest.reflect.field.Invoker<Long> delayUntilField = getDelayUntilNsField(delayedPriority_5);
        delayUntilField.set(mockTimer.nanoTime() - 1000);

        JobHolder delayedPriority_2 = createNewJobHolder(new Params(2));
        delayUntilField = getDelayUntilNsField(delayedPriority_2);
        delayUntilField.set(mockTimer.nanoTime() - 500);


        JobHolder nonDelayedPriority_6 = createNewJobHolder(new Params(6));
        JobHolder nonDelayedPriority_3 = createNewJobHolder(new Params(3));
        JobHolder nonDelayedPriority_2 = createNewJobHolder(new Params(2));


        jobQueue.insert(delayedPriority_5);
        jobQueue.insert(delayedPriority_2);
        jobQueue.insert(nonDelayedPriority_6);
        jobQueue.insert(nonDelayedPriority_2);
        jobQueue.insert(nonDelayedPriority_3);
        TestConstraint constraint = new TestConstraint(mockTimer);
        int lastPriority = Integer.MAX_VALUE;
        for (int i = 0; i < 5; i++) {
            JobHolder next = jobQueue.nextJobAndIncRunCount(constraint);
            assertThat("next job should not be null", next, notNullValue());
            assertThat("next job's priority should be lower then previous for job " + i, next.getPriority() <= lastPriority, is(true));
            lastPriority = next.getPriority();
        }

    }

    private static org.fest.reflect.field.Invoker<Long> getDelayUntilNsField(JobHolder jobHolder) {
        return Reflection.field("delayUntilNs").ofType(long.class).in(jobHolder);
    }

    private static org.fest.reflect.field.Invoker<Integer> getPriorityField(Params params) {
        return Reflection.field("priority").ofType(int.class).in(params);
    }

    private static org.fest.reflect.field.Invoker<Integer> getNetworkTypeField(Params params) {
        return Reflection.field("requiredNetworkType").ofType(int.class).in(params);
    }

    private static org.fest.reflect.field.Invoker<Long> getDelayMsField(Params params) {
        return Reflection.field("delayMs").ofType(long.class).in(params);
    }

    private static org.fest.reflect.field.Invoker<Long> getDeadlineMsField(Params params) {
        return Reflection.field("deadlineMs").ofType(long.class).in(params);
    }

    private static org.fest.reflect.field.Invoker<Boolean> getCancelOnDeadlineDeadlineField(Params params) {
        return Reflection.field("cancelOnDeadline").ofType(Boolean.class).in(params);
    }

    private static org.fest.reflect.field.Invoker<String> getGroupIdField(Params params) {
        return Reflection.field("groupId").ofType(String.class).in(params);
    }

    @Test
    public void testSessionId() throws Exception {
        long sessionId = (long) (Math.random() * 100000);
        JobQueue jobQueue = createNewJobQueueWithSessionId(sessionId);
        JobHolder jobHolder = createNewJobHolder();
        jobQueue.insert(jobHolder);
        jobHolder = jobQueue.nextJobAndIncRunCount(new TestConstraint(mockTimer));
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
            JobHolder holder = jobQueue.nextJobAndIncRunCount(new TestConstraint(mockTimer));
            assertThat(holder.getPriority() <= minPriority, is(true));
            jobQueue.insertOrReplace(holder);
        }
        assertThat(jobQueue.nextJobAndIncRunCount(new TestConstraint(mockTimer)), notNullValue());
    }

    @Test
    public void testRemove() throws Exception {
        JobQueue jobQueue = createNewJobQueue();
        JobHolder holder = createNewJobHolder();
        jobQueue.insert(holder);
        TestConstraint constraint = new TestConstraint(mockTimer);
        constraint.setExcludeRunning(true);
        assertThat(jobQueue.nextJobAndIncRunCount(constraint).getId(), equalTo(holder.getId()));
        assertThat(jobQueue.nextJobAndIncRunCount(constraint), is(nullValue()));
        jobQueue.remove(holder);
        assertThat(jobQueue.nextJobAndIncRunCount(constraint), is(nullValue()));
    }

    @Test
    public void testNetwork() throws Exception {
        JobQueue jobQueue = createNewJobQueue();
        JobHolder jobHolder = createNewJobHolder(new Params(0));
        jobQueue.insert(jobHolder);
        TestConstraint constraint = new TestConstraint(mockTimer);
        constraint.setMaxNetworkType(NetworkUtil.DISCONNECTED);
        assertThat("no network job should be returned even if there is no netowrk",
                jobQueue.nextJobAndIncRunCount(constraint), notNullValue());
        jobQueue.remove(jobHolder);

        jobHolder = createNewJobHolder(new Params(0).requireNetwork());
        assertThat("if there isn't any network, job with network requirement should not return",
                jobQueue.nextJobAndIncRunCount(constraint), nullValue());
        constraint.setMaxNetworkType(NetworkUtil.METERED);
        assertThat("if there is network, job with network requirement should be returned",
                jobQueue.nextJobAndIncRunCount(constraint), nullValue());

        jobQueue.remove(jobHolder);

        jobHolder = createNewJobHolder(new Params(1));
        JobHolder jobHolder2 = createNewJobHolder(new Params(5).requireNetwork());
        jobQueue.insert(jobHolder);
        jobQueue.insert(jobHolder2);
        constraint.setMaxNetworkType(NetworkUtil.DISCONNECTED);
        constraint.setExcludeRunning(true);
        JobHolder retrieved = jobQueue.nextJobAndIncRunCount(constraint);
        assertThat("one job should be returned w/o network", retrieved, notNullValue());
        if (retrieved != null) {
            assertThat("no network job should be returned although it has lower priority", retrieved.getId(), equalTo(jobHolder.getId()));
        }

        assertThat("no other job should be returned w/o network", jobQueue.nextJobAndIncRunCount(constraint), nullValue());
        constraint.setMaxNetworkType(NetworkUtil.METERED);
        retrieved = jobQueue.nextJobAndIncRunCount(constraint);
        assertThat("if network is back, network requiring job should be returned", retrieved, notNullValue());
        if (retrieved != null) {
            assertThat("when there is network, network job should be returned", retrieved.getId(), equalTo(jobHolder2.getId()));
        }
        //add first job back
        jobQueue.insertOrReplace(jobHolder);
        //add second job back
        jobQueue.insertOrReplace(jobHolder2);

        retrieved = jobQueue.nextJobAndIncRunCount(constraint);
        assertThat("if network is back, job w/ higher priority should be returned", retrieved, notNullValue());
        if (retrieved != null) {
            assertThat("if network is back, job w/ higher priority should be returned", retrieved.getId(), equalTo(jobHolder2.getId()));
        }
        jobQueue.insertOrReplace(jobHolder2);

        JobHolder highestPriorityJob = createNewJobHolder(new Params(10));
        jobQueue.insert(highestPriorityJob);
        retrieved = jobQueue.nextJobAndIncRunCount(constraint);
        assertThat("w/ or w/o network, highest priority should be returned", retrieved, notNullValue());
        if (retrieved != null) {
            assertThat("w/ or w/o network, highest priority should be returned", retrieved.getId(), equalTo(highestPriorityJob.getId()));
        }

        //TODO test delay until
    }

    @Test
    public void testCountReadyJobs() throws Exception {
        JobQueue jobQueue = createNewJobQueue();
        TestConstraint constraint = new TestConstraint(mockTimer);
        assertThat("initial count should be 0 for ready jobs", jobQueue.countReadyJobs(constraint), equalTo(0));
        //add some jobs
        jobQueue.insert(createNewJobHolder());
        jobQueue.insert(createNewJobHolder(new Params(0).requireNetwork()));
        long now = mockTimer.nanoTime();
        long delay = 1000;
        constraint.setTimeLimit(now);
        constraint.setMaxNetworkType(NetworkUtil.DISCONNECTED);
        constraint.setExcludeRunning(true);
        jobQueue.insert(createNewJobHolderWithDelayUntil(new Params(0), now + TimeUnit.MILLISECONDS.toNanos(delay)));
        assertThat("ready count should be 1 if there is no network", jobQueue.countReadyJobs(constraint), equalTo(1));
        constraint.setMaxNetworkType(NetworkUtil.METERED);
        assertThat("ready count should be 2 if there is network", jobQueue.countReadyJobs(constraint), equalTo(2));
        mockTimer.incrementMs(delay + 1);
        constraint.setTimeLimit(mockTimer.nanoTime());
        assertThat("when needed delay time passes, ready count should be 3", jobQueue.countReadyJobs(constraint), equalTo(3));
        constraint.setMaxNetworkType(NetworkUtil.DISCONNECTED);
        assertThat("when needed delay time passes but no network, ready count should be 2", jobQueue.countReadyJobs(constraint), equalTo(2));
        jobQueue.insert(createNewJobHolder(new Params(5).groupBy("group1")));
        jobQueue.insert(createNewJobHolder(new Params(5).groupBy("group1")));
        constraint.setMaxNetworkType(NetworkUtil.METERED);
        assertThat("when more than 1 job from same group is created, ready jobs should increment only by 1",
                jobQueue.countReadyJobs(constraint), equalTo(4));
        constraint.setExcludeGroups(Arrays.asList(new String[]{"group1"}));
        assertThat("excluding groups should work",
                jobQueue.countReadyJobs(constraint), equalTo(3));
        constraint.setExcludeGroups(Arrays.asList(new String[]{"group3423"}));
        assertThat("giving a non-existing group should not fool the count",
                jobQueue.countReadyJobs(constraint), equalTo(4));
        jobQueue.insert(createNewJobHolder(new Params(3).groupBy("group2")));
        constraint.clear();
        constraint.setTimeLimit(mockTimer.nanoTime());
        constraint.setMaxNetworkType(NetworkUtil.UNMETERED);
        constraint.setExcludeRunning(true);
        assertThat("when a job from another group is added, ready job count should inc",
                jobQueue.countReadyJobs(constraint), equalTo(5));
        now = mockTimer.nanoTime();
        jobQueue.insert(createNewJobHolderWithDelayUntil(new Params(3).groupBy("group3"), now + TimeUnit.MILLISECONDS.toNanos(delay)));
        assertThat("when a delayed job from another group is added, ready count should not change",
                jobQueue.countReadyJobs(constraint), equalTo(5));
        jobQueue.insert(createNewJobHolder(new Params(3).groupBy("group3")));
        assertThat("when another job from delayed group is added, ready job count should inc",
                jobQueue.countReadyJobs(constraint), equalTo(6));
        mockTimer.incrementMs(delay);
        constraint.setTimeLimit(mockTimer.nanoTime());
        assertThat("when delay passes and a job from existing group becomes available, ready job count should not change",
                jobQueue.countReadyJobs(constraint), equalTo(6));
        constraint.setExcludeGroups(Arrays.asList(new String[]{"group1", "group3"}));
        assertThat("when some groups are excluded, count should be correct",
                jobQueue.countReadyJobs(constraint), equalTo(4));

        //jobs w/ same group id but with different persistence constraints should not fool the count
        now = mockTimer.nanoTime();
        constraint.setTimeLimit(mockTimer.nanoTime());
        jobQueue.insert(createNewJobHolderWithDelayUntil(new Params(0).persist().groupBy("group10"), now + 1000));
        jobQueue.insert(createNewJobHolderWithDelayUntil(new Params(0).groupBy("group10"), now + 1000));
        jobQueue.insert(createNewJobHolderWithDelayUntil(new Params(0).persist().groupBy("group10"), now - 1000));
        jobQueue.insert(createNewJobHolderWithDelayUntil(new Params(0).groupBy("group10"), now - 1000));
        constraint.setExcludeGroups(Arrays.asList(new String[]{"group1", "group3"}));
        assertThat("when many jobs are added w/ different constraints but same group id, ready count should not be fooled",
                jobQueue.countReadyJobs(constraint), equalTo(5));
        constraint.clear();
        constraint.setExcludeRunning(true);
        constraint.setMaxNetworkType(NetworkUtil.UNMETERED);
        assertThat("when many jobs are added w/ different constraints but same group id, ready count should not be fooled",
                jobQueue.countReadyJobs(constraint), equalTo(7));
        constraint.setMaxNetworkType(NetworkUtil.DISCONNECTED);
        constraint.setExcludeGroups(Arrays.asList(new String[]{"group1", "group3"}));
        assertThat("when many jobs are added w/ different constraints but same group id, ready count should not be fooled",
                jobQueue.countReadyJobs(constraint), equalTo(4));
    }

    @Test
    public void testJobFields() throws Exception {
        long sessionId = (long) (Math.random() * 1000);
        JobQueue jobQueue = createNewJobQueueWithSessionId(sessionId);
        int priority = (int) (Math.random() * 1000);
        JobHolder jobHolder = createNewJobHolder(new Params(priority));
        int runCount = (int) (Math.random() * 10);
        jobHolder.setRunCount(runCount);

        jobQueue.insert(jobHolder);


        for (int i = 0; i < 2; i++) {
            JobHolder received = jobQueue.nextJobAndIncRunCount(new TestConstraint(mockTimer));
            assertThat("job id should be preserved", received.getId(), equalTo(jobHolder.getId()));
            assertThat("job priority should be preserved", received.getPriority(), equalTo(priority));
            assertThat("job session id should be assigned", received.getRunningSessionId(), equalTo(sessionId));
            assertThat("job run count should be incremented", received.getRunCount(), equalTo(runCount + i + 1));
            jobQueue.insertOrReplace(received);
        }
    }

    private void assertJob(JobQueue jobQueue, String msg, String id, /*nullable*/ JobHolder holder) {
        if (holder == null) {
            assertThat(msg, jobQueue.findJobById(id), nullValue());
            return;
        }
        assertThat(msg + "(existence check)", jobQueue.findJobById(id), notNullValue());
        assertThat(msg + "(id check)", jobQueue.findJobById(id).getId(), is(holder.getId()));
    }

    @Test
    public void testFindJobHolderById() {
        JobQueue jobQueue = createNewJobQueue();
        assertJob(jobQueue, "non existing job", UUID.randomUUID().toString(), null);
        final int LIMIT = 100;
        JobHolder[] holders = new JobHolder[LIMIT];
        String[] ids = new String[LIMIT];
        for (int i = 0; i < LIMIT; i++) {
            holders[i] = createNewJobHolder(new Params((int) (Math.random() * 50)).setPersistent(Math.random() < .5).setRequiresNetwork(Math.random() < .5));
            ids[i] = holders[i].getId();
            jobQueue.insert(holders[i]);
            assertJob(jobQueue, "job by id should work for inserted job", ids[i], holders[i]);
        }
        final int REMOVE_CNT = LIMIT / 2;
        for (int i = 0; i < REMOVE_CNT; i++) {
            int ind = (int) (Math.random() * LIMIT);
            if (holders[ind] == null) {
                continue;
            }
            //remove some randomly, up to half
            jobQueue.remove(holders[ind]);
            holders[ind] = null;
        }
        //re-query all, ensure we can still find non-removed jobs and not find removed jobs
        for (int i = 0; i < LIMIT; i++) {
            if (holders[i] != null) {
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
        for (int i = 0; i < LIMIT; i++) {
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
        Set<JobHolder> twoJobs = jobQueue.findJobs(forTags(mockTimer, ANY, Collections.<String>emptyList(), tag1));
        Set<String> resultIds = ids(twoJobs);

        assertThat("two jobs should be returned", twoJobs.size(), is(2));
        assertThat("should have job id 1", resultIds, hasItems(holder1.getId(), holder2.getId()));
        for (String tag : new String[]{tag2, tag3}) {
            Set<JobHolder> oneJob = jobQueue.findJobs(forTags(mockTimer, ANY, Collections.<String>emptyList(), tag));
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

    private Set<String> ids(Collection<JobHolder> result) {
        HashSet<String> ids = new HashSet<>();
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
                jobQueue.findJobs(forTags(mockTimer, ANY, Collections.<String>emptyList(), tag1)).size(), is(0));
        assertThat("when job is removed, it should return none",
                jobQueue.findJobs(forTags(mockTimer, ANY, Collections.<String>emptyList(), tag2)).size(), is(0));
    }

    @Test
    public void testFindByTags() {
        JobQueue jobQueue = createNewJobQueue();
        assertThat("empty queue should return 0", jobQueue.findJobs(forTags(mockTimer, ANY, Collections.<String>emptyList(), "abc")).size(), is(0));
        jobQueue.insert(createNewJobHolder());
        Set<JobHolder> result = jobQueue.findJobs(forTags(mockTimer, ANY, Collections.<String>emptyList(), "blah"));
        assertThat("if job does not have a tag, it should return 0", result.size(), is(0));

        final String tag1 = UUID.randomUUID().toString();
        JobHolder holder = createNewJobHolder(new Params(0).addTags(tag1));
        jobQueue.insert(holder);
        assertTags("holder with 1 tag", jobQueue, holder);
        jobQueue.insertOrReplace(holder);
        assertTags("holder with 1 tag reinserted", jobQueue, holder);
        jobQueue.remove(holder);
        assertThat("when job is removed, it should return none",
                jobQueue.findJobs(forTags(mockTimer, ANY, Collections.<String>emptyList(), tag1)).size(), is(0));

        JobHolder holder2 = createNewJobHolder(new Params(0).addTags(tag1));
        jobQueue.insert(holder2);
        assertThat("it should return the job",
                jobQueue.findJobs(forTags(mockTimer, ANY, Collections.<String>emptyList(), tag1)).size(), is(1));
        jobQueue.onJobCancelled(holder2);
        assertThat("when queried w/ exclude cancelled, it should not return the job",
                jobQueue.findJobs(forTags(mockTimer, ANY, Collections.<String>emptyList(), tag1)).size(), is(0));
    }

    @Test
    public void testDelayUntilWithNetworkRequirement() {
        JobQueue jobQueue = createNewJobQueue();
        JobHolder holder1 = createNewJobHolder(new Params(2).requireNetwork()
                .overrideDeadlineToRunInMs(1000));
        jobQueue.insert(holder1);
        TestConstraint constraint = new TestConstraint(mockTimer);
        constraint.setMaxNetworkType(NetworkUtil.DISCONNECTED);
        assertThat(jobQueue.getNextJobDelayUntilNs(constraint), is(1000000000L));
    }

    @Test
    public void testDelayUntilWithNetworkRequirement2() {
        JobQueue jobQueue = createNewJobQueue();
        JobHolder holder1 = createNewJobHolder(new Params(2).overrideDeadlineToRunInMs(3000)
                .delayInMs(2000).requireNetwork());
        jobQueue.insert(holder1);
        TestConstraint constraint = new TestConstraint(mockTimer);
        constraint.setMaxNetworkType(NetworkUtil.DISCONNECTED);
        assertThat(jobQueue.getNextJobDelayUntilNs(constraint), is(3000000000L));
    }

    @Test
    public void testDelayUntilWithNetworkRequirement3() {
        JobQueue jobQueue = createNewJobQueue();
        JobHolder holder1 = createNewJobHolder(new Params(2).overrideDeadlineToRunInMs(2000)
                .delayInMs(1000).requireNetwork());
        jobQueue.insert(holder1);
        TestConstraint constraint = new TestConstraint(mockTimer);
        constraint.setMaxNetworkType(NetworkUtil.UNMETERED);
        assertThat(jobQueue.getNextJobDelayUntilNs(constraint), is(1000000000L));
    }

    @Test
    public void testDelayUntilWithNetworkRequirement4() {
        JobQueue jobQueue = createNewJobQueue();
        JobHolder holder1 = createNewJobHolder(new Params(2).overrideDeadlineToRunInMs(3000)
                .delayInMs(2000).requireNetwork());
        jobQueue.insert(holder1);
        TestConstraint constraint = new TestConstraint(mockTimer);
        constraint.setMaxNetworkType(NetworkUtil.UNMETERED);
        assertThat(jobQueue.getNextJobDelayUntilNs(constraint), is(2000000000L));
    }

    @Test
    public void testDelayUntilWithNetworkRequirement5() {
        JobQueue jobQueue = createNewJobQueue();
        JobHolder holder1 = createNewJobHolder(new Params(2).overrideDeadlineToRunInMs(2000)
                .delayInMs(1000).requireNetwork());
        jobQueue.insert(holder1);
        TestConstraint constraint = new TestConstraint(mockTimer);
        constraint.setMaxNetworkType(NetworkUtil.METERED);
        assertThat(jobQueue.getNextJobDelayUntilNs(constraint), is(1000000000L));
    }


    @Test
    public void testDelayUntilWithNetworkRequirementAndRegularDelayedJob() {
        JobQueue jobQueue = createNewJobQueue();
        JobHolder holder1 = createNewJobHolder(new Params(2).overrideDeadlineToRunInMs(1000)
                .requireNetwork());
        JobHolder holder2 = createNewJobHolder(new Params(2).delayInMs(500));
        jobQueue.insert(holder1);
        jobQueue.insert(holder2);
        TestConstraint constraint = new TestConstraint(mockTimer);
        constraint.setMaxNetworkType(NetworkUtil.DISCONNECTED);
        assertThat(jobQueue.getNextJobDelayUntilNs(constraint), is(500000000L));
    }

    @Test
    public void testDelayUntilWithNetworkRequirementAndRegularDelayedJob2() {
        JobQueue jobQueue = createNewJobQueue();
        JobHolder holder1 = createNewJobHolder(new Params(2).overrideDeadlineToRunInMs(1000)
                .requireUnmeteredNetwork());
        JobHolder holder2 = createNewJobHolder(new Params(2).delayInMs(500));
        jobQueue.insert(holder2);
        jobQueue.insert(holder1);
        TestConstraint constraint = new TestConstraint(mockTimer);
        constraint.setMaxNetworkType(NetworkUtil.METERED);
        assertThat(jobQueue.getNextJobDelayUntilNs(constraint), is(500000000L));
    }

    @Test
    public void testDelayUntilWithNetworkRequirementAndRegularDelayedJob3() {
        JobQueue jobQueue = createNewJobQueue();
        JobHolder holder1 = createNewJobHolder(new Params(2).overrideDeadlineToRunInMs(500)
                .requireNetwork());
        JobHolder holder2 = createNewJobHolder(new Params(2).delayInMs(1000));
        jobQueue.insert(holder1);
        jobQueue.insert(holder2);
        TestConstraint constraint = new TestConstraint(mockTimer);
        constraint.setMaxNetworkType(NetworkUtil.DISCONNECTED);
        assertThat(jobQueue.getNextJobDelayUntilNs(constraint), is(500000000L));
    }

    @Test
    public void testDelayUntilWithNetworkRequirementAndRegularDelayedJob4() {
        JobQueue jobQueue = createNewJobQueue();
        JobHolder holder1 = createNewJobHolder(new Params(2).overrideDeadlineToRunInMs(500)
                .requireNetwork());
        JobHolder holder2 = createNewJobHolder(new Params(2).delayInMs(1000));
        jobQueue.insert(holder2);
        jobQueue.insert(holder1);
        TestConstraint constraint = new TestConstraint(mockTimer);
        constraint.setMaxNetworkType(NetworkUtil.DISCONNECTED);
        assertThat(jobQueue.getNextJobDelayUntilNs(constraint), is(500000000L));
    }

    @Test
    public void testDelayUntilWithUnmeteredNetworkRequirement() {
        JobQueue jobQueue = createNewJobQueue();
        JobHolder holder1 = createNewJobHolder(new Params(2).overrideDeadlineToRunInMs(1000)
                .requireUnmeteredNetwork());
        jobQueue.insert(holder1);
        TestConstraint constraint = new TestConstraint(mockTimer);
        constraint.setMaxNetworkType(NetworkUtil.DISCONNECTED);
        assertThat(jobQueue.getNextJobDelayUntilNs(constraint), is(1000000000L));
        constraint.setMaxNetworkType(NetworkUtil.METERED);
        assertThat(jobQueue.getNextJobDelayUntilNs(constraint), is(1000000000L));
    }

    @Test
    public void testDelayUntilWithUnmeteredNetworkRequirement2() {
        JobQueue jobQueue = createNewJobQueue();
        JobHolder holder1 = createNewJobHolder(new Params(2).overrideDeadlineToRunInMs(3000)
                .requireUnmeteredNetwork()
                .delayInMs(2000));
        jobQueue.insert(holder1);
        TestConstraint constraint = new TestConstraint(mockTimer);
        constraint.setMaxNetworkType(NetworkUtil.UNMETERED);
        assertThat(jobQueue.getNextJobDelayUntilNs(constraint), is(2000000000L));
        constraint.setMaxNetworkType(NetworkUtil.METERED);
        assertThat(jobQueue.getNextJobDelayUntilNs(constraint), is(3000000000L));
    }

    @Test
    public void testDelayUntilWithUnmeteredNetworkRequirement3() {
        JobQueue jobQueue = createNewJobQueue();
        JobHolder holder1 = createNewJobHolder(new Params(2).overrideDeadlineToRunInMs(2000)
                .requireUnmeteredNetwork().delayInMs(1000));
        jobQueue.insert(holder1);
        TestConstraint constraint = new TestConstraint(mockTimer);
        constraint.setMaxNetworkType(NetworkUtil.METERED);
        assertThat(jobQueue.getNextJobDelayUntilNs(constraint), is(2000000000L));
    }

    @Test
    public void testDelayUntilWithUnmeteredNetworkRequirement4() {
        JobQueue jobQueue = createNewJobQueue();
        JobHolder holder1 = createNewJobHolder(new Params(2).overrideDeadlineToRunInMs(3000)
                .requireUnmeteredNetwork()
                .delayInMs(2000));
        jobQueue.insert(holder1);
        TestConstraint constraint = new TestConstraint(mockTimer);
        constraint.setMaxNetworkType(NetworkUtil.UNMETERED);
        assertThat(jobQueue.getNextJobDelayUntilNs(constraint), is(2000000000L));
    }

    @Test
    public void testDelayUntilWithUnmeteredNetworkRequirement5() {
        JobQueue jobQueue = createNewJobQueue();
        JobHolder holder1 = createNewJobHolder(new Params(2).overrideDeadlineToRunInMs(2000)
                .requireUnmeteredNetwork().delayInMs(1000));
        jobQueue.insert(holder1);
        TestConstraint constraint = new TestConstraint(mockTimer);
        constraint.setMaxNetworkType(NetworkUtil.UNMETERED);
        assertThat(jobQueue.getNextJobDelayUntilNs(constraint), is(1000000000L));
    }


    @Test
    public void testDelayUntilWithUnmeteredNetworkRequirementAndRegularDelayedJob() {
        JobQueue jobQueue = createNewJobQueue();
        JobHolder holder1 = createNewJobHolder(new Params(2).overrideDeadlineToRunInMs(1000)
                .requireUnmeteredNetwork());
        JobHolder holder2 = createNewJobHolder(new Params(2).delayInMs(500));
        jobQueue.insert(holder1);
        jobQueue.insert(holder2);
        TestConstraint constraint = new TestConstraint(mockTimer);
        constraint.setMaxNetworkType(NetworkUtil.METERED);
        assertThat(jobQueue.getNextJobDelayUntilNs(constraint), is(500000000L));
    }

    @Test
    public void testDelayUntilWithUnmeteredNetworkRequirementAndRegularDelayedJob2() {
        JobQueue jobQueue = createNewJobQueue();
        JobHolder holder1 = createNewJobHolder(new Params(2).overrideDeadlineToRunInMs(1000)
                .requireUnmeteredNetwork());
        JobHolder holder2 = createNewJobHolder(new Params(2).delayInMs(500));
        jobQueue.insert(holder2);
        jobQueue.insert(holder1);
        TestConstraint constraint = new TestConstraint(mockTimer);
        constraint.setMaxNetworkType(NetworkUtil.METERED);
        assertThat(jobQueue.getNextJobDelayUntilNs(constraint), is(500000000L));
    }

    @Test
    public void testDelayUntilWithUnmeteredNetworkRequirementAndRegularDelayedJob3() {
        JobQueue jobQueue = createNewJobQueue();
        JobHolder holder1 = createNewJobHolder(new Params(2).overrideDeadlineToRunInMs(500)
                .requireUnmeteredNetwork());
        JobHolder holder2 = createNewJobHolder(new Params(2).delayInMs(1000));
        jobQueue.insert(holder1);
        jobQueue.insert(holder2);
        TestConstraint constraint = new TestConstraint(mockTimer);
        constraint.setMaxNetworkType(NetworkUtil.METERED);
        assertThat(jobQueue.getNextJobDelayUntilNs(constraint), is(500000000L));
    }

    @Test
    public void testDelayUntilWithUnmeteredNetworkRequirementAndRegularDelayedJob4() {
        JobQueue jobQueue = createNewJobQueue();
        JobHolder holder1 = createNewJobHolder(new Params(2).overrideDeadlineToRunInMs(500)
                .requireUnmeteredNetwork());
        JobHolder holder2 = createNewJobHolder(new Params(2).delayInMs(1000));
        jobQueue.insert(holder2);
        jobQueue.insert(holder1);
        TestConstraint constraint = new TestConstraint(mockTimer);
        constraint.setMaxNetworkType(NetworkUtil.METERED);
        assertThat(jobQueue.getNextJobDelayUntilNs(constraint), is(500000000L));
    }

    @Test
    public void testDelayUntilWithRunningJobs() {
        JobQueue jobQueue = createNewJobQueue();
        JobHolder holder = createNewJobHolder();
        jobQueue.insert(holder);
        TestConstraint constraint = new TestConstraint(mockTimer);
        constraint.setMaxNetworkType(NetworkUtil.METERED);
        constraint.setExcludeRunning(true);
        assertThat(jobQueue.getNextJobDelayUntilNs(constraint), is(JobManager.NOT_DELAYED_JOB_DELAY));

        JobHolder nextJob = jobQueue.nextJobAndIncRunCount(constraint);
        assertThat(nextJob, is(notNullValue()));
        assertThat(nextJob.getId(), is(holder.getId()));

        assertThat(jobQueue.getNextJobDelayUntilNs(constraint), is(nullValue()));
    }

    private void assertTags(String msg, JobQueue jobQueue, JobHolder holder) {
        Set<JobHolder> result;
        String wrongTag;
        final String id = holder.getId();
        boolean found;
        Matcher allTagsMatcher = CoreMatchers.hasItems(holder.getTags().toArray(new String[holder.getTags().size()]));
        do {
            wrongTag = UUID.randomUUID().toString();
            found = false;
            if (holder.getTags() != null) {
                for (String tag : holder.getTags()) {
                    if (tag.equals(wrongTag)) {
                        found = true;
                        break;
                    }
                }
            }
        } while (found);
        result = jobQueue.findJobs(forTags(mockTimer, ANY, Collections.<String>emptyList(), wrongTag));
        found = false;
        for (JobHolder received : result) {
            if (received.getId().equals(holder.getId())) {
                found = true;
            }
        }
        assertThat(msg + " when wrong tag is given, our job should not return", found, is(false));

        if (holder.getTags() == null) {
            return;// done
        }
        Collection<String> exclude = Arrays.asList(holder.getId());
        for (String[] tags : combinations(holder.getTags())) {
            result = jobQueue.findJobs(forTags(mockTimer, TagConstraint.ANY, Collections.<String>emptyList(), tags));
            if (tags.length == 0) {
                assertThat(msg + " empty tag list, should return 0 jobs", result.size(), is(0));
            } else {
                assertThat(msg + " any combinations: when correct tag is given, it should return one", result.size(), is(1));
                assertThat(msg + " any combinations: returned job should be the correct one", result.iterator().next().getId(), is(id));
                assertThat(msg + " returned holder should have all tags:", result.iterator().next().getTags(), allTagsMatcher);
            }
            result = jobQueue.findJobs(forTags(mockTimer, TagConstraint.ANY, exclude, tags));
            assertThat(msg + " when excluded, holder should not show up in results", result.size(), is(
                    0));

        }

        for (String[] tags : combinations(holder.getTags())) {
            result = jobQueue.findJobs(forTags(mockTimer, ALL, Collections.<String>emptyList(), tags));
            if (tags.length == 0) {
                assertThat(msg + " empty tag list, should return 0 jobs", result.size(), is(0));
            } else {
                assertThat(msg + " all combinations: when correct tag is given, it should return one",
                        result.size(), is(1));
                assertThat(msg + " all combinations: returned job should be the correct one",
                        result.iterator().next().getId(), is(id));
                assertThat(msg + " returned holder should have all tags:", result.iterator().next().getTags(), allTagsMatcher);
            }
            result = jobQueue.findJobs(forTags(mockTimer, ALL, exclude, tags));
            assertThat(msg + " when excluded, holder should not show up in results", result.size(), is(0));
        }

        for (String[] tags : combinations(holder.getTags())) {
            String[] tagsWithAdditional = new String[tags.length + 1];
            System.arraycopy(tags, 0, tagsWithAdditional, 0, tags.length);
            tagsWithAdditional[tags.length] = wrongTag;
            result = jobQueue.findJobs(forTags(mockTimer, TagConstraint.ANY, Collections.<String>emptyList(), tagsWithAdditional));
            if (tags.length == 0) {
                assertThat(msg + " empty tag list, should return 0 jobs", result.size(), is(0));
            } else {
                assertThat(msg + " any combinations with wrong tag: when correct tag is given, it should return one",
                        result.size(), is(1));
                assertThat(msg + " any combinations with wrong tag: returned job should be the correct one",
                        result.iterator().next().getId(), is(id));
                assertThat(msg + " returned holder should have all tags:", result.iterator().next().getTags(), allTagsMatcher);
            }

            result = jobQueue.findJobs(forTags(mockTimer, ALL, Collections.<String>emptyList(), tagsWithAdditional));
            assertThat(msg + " all combinations with wrong tag: when an additional wrong tag is given, it should return 0", result.size(), is(0));

            result = jobQueue.findJobs(forTags(mockTimer, ALL, exclude, tagsWithAdditional));
            assertThat(msg + " when excluded, holder should not show up in results", result.size(), is(0));
        }
    }

    List<String[]> combinations(Set<String> strings) {
        if (strings.size() == 0) {
            List<String[]> result = new ArrayList<String[]>();
            result.add(new String[]{});
            return result;
        }
        Set<String> remaining = new HashSet<String>();
        boolean skip = true;
        for (String str : strings) {
            if (skip) {
                skip = false;
            } else {
                remaining.add(str);
            }
        }
        List<String[]> others = combinations(remaining);
        List<String[]> result = new ArrayList<String[]>();
        for (String[] subset : others) {
            result.add(subset);
            // add myself
            String[] copy = new String[subset.length + 1];
            copy[0] = strings.iterator().next();
            for (int i = 1; i <= subset.length; i++) {
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
        return createNewJobHolder(params, mockTimer);
    }

    public static JobHolder createNewJobHolder(Params params, Timer timer) {
        long delay = getDelayMsField(params).get();
        long deadline = getDeadlineMsField(params).get();
        boolean cancelOnDeadline = Boolean.TRUE.equals(getCancelOnDeadlineDeadlineField(params).get());
        DummyJob job = new DummyJob(params);
        //noinspection WrongConstant
        return new JobHolder.Builder()
                .priority(params.getPriority())
                .groupId(params.getGroupId())
                .job(job)
                .id(job.getId())
                .persistent(params.isPersistent())
                .tags(job.getTags())
                .createdNs(timer.nanoTime())
                .deadline(deadline > 0 ? timer.nanoTime() + deadline * JobManager.NS_PER_MS : Params.FOREVER, cancelOnDeadline)
                .delayUntilNs(delay > 0 ? timer.nanoTime() + delay * JobManager.NS_PER_MS : JobManager.NOT_DELAYED_JOB_DELAY)
                .requiredNetworkType(getNetworkTypeField(params).get())
                .runningSessionId(JobManager.NOT_RUNNING_SESSION_ID).build();
    }

    private JobHolder createNewJobHolderWithDelayUntil(Params params, long delayUntil) {
        JobHolder jobHolder = createNewJobHolder(params);
        getDelayUntilNsField(jobHolder).set(delayUntil);
        return jobHolder;
    }

    protected JobQueue createNewJobQueue() {
        return createNewJobQueueWithSessionId(123L);
    }

    private JobQueue createNewJobQueueWithSessionId(Long sessionId) {
        return currentFactory.createNew(sessionId, "id_" + sessionId, mockTimer);
    }
}
