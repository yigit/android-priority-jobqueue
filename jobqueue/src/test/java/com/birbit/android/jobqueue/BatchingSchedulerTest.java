package com.birbit.android.jobqueue;

import android.content.Context;

import com.birbit.android.jobqueue.BatchingScheduler;
import com.birbit.android.jobqueue.scheduling.Scheduler;
import com.birbit.android.jobqueue.scheduling.SchedulerConstraint;
import com.birbit.android.jobqueue.network.NetworkUtil;
import com.birbit.android.jobqueue.test.timer.MockTimer;

import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import static org.mockito.Mockito.*;
import static com.birbit.android.jobqueue.BatchingScheduler.DEFAULT_BATCHING_PERIOD_IN_MS;
@RunWith(JUnit4.class)
public class BatchingSchedulerTest {
    BatchingScheduler bs;
    Scheduler scheduler;
    MockTimer timer;
    @Before
    public void init() {
        scheduler = mock(Scheduler.class);
        timer = new MockTimer();
        bs = new BatchingScheduler(scheduler, timer);
        Context context = mock(Context.class);
        when(context.getApplicationContext()).thenReturn(mock(Context.class));
        bs.init(context, mock(Scheduler.Callback.class));
    }

    @Test
    public void testCustomDuration() {
        scheduler = mock(Scheduler.class);
        timer = new MockTimer();
        bs = new BatchingScheduler(scheduler, timer, 123);
        MatcherAssert.assertThat(bs.batchingDurationInMs, CoreMatchers.is(123L));
        MatcherAssert.assertThat(bs.batchingDurationInNs, CoreMatchers.is(123000000L));
    }

    @Test
    public void testAddOne() {
        SchedulerConstraint constraint = new SchedulerConstraint("abc");
        constraint.setDelayInMs(0);
        constraint.setNetworkStatus(NetworkUtil.DISCONNECTED);
        bs.request(constraint);
        verify(scheduler, times(1)).request(constraint);
        MatcherAssert.assertThat(constraint.getDelayInMs(),
                CoreMatchers.is(DEFAULT_BATCHING_PERIOD_IN_MS));
    }

    @Test
    public void testAddTwoOfTheSame() {
        SchedulerConstraint constraint = createConstraint(NetworkUtil.METERED, 0);
        bs.request(constraint);
        SchedulerConstraint constraint2 = createConstraint(NetworkUtil.METERED, 0);
        bs.request(constraint2);
        verify(scheduler, times(1)).request(constraint);
        verify(scheduler, times(0)).request(constraint2);
        MatcherAssert.assertThat(constraint.getDelayInMs(),
                CoreMatchers.is(DEFAULT_BATCHING_PERIOD_IN_MS));
    }

    @Test
    public void testAddTwoOfTheSameWithTimeDiff() {
        SchedulerConstraint constraint = createConstraint(NetworkUtil.METERED, 0);
        bs.request(constraint);
        timer.incrementMs(DEFAULT_BATCHING_PERIOD_IN_MS - 10);
        SchedulerConstraint constraint2 = createConstraint(NetworkUtil.METERED, 0);
        bs.request(constraint2);
        verify(scheduler, times(1)).request(constraint);
        verify(scheduler, times(0)).request(constraint2);
        MatcherAssert.assertThat(constraint.getDelayInMs(),
                CoreMatchers.is(DEFAULT_BATCHING_PERIOD_IN_MS));
    }

    @Test
    public void testAddTwoOfTheSameWithDelay() {
        SchedulerConstraint constraint = createConstraint(NetworkUtil.METERED, 0);
        bs.request(constraint);
        SchedulerConstraint constraint2 = createConstraint(NetworkUtil.METERED, 100);
        bs.request(constraint2);
        verify(scheduler, times(1)).request(constraint);
        verify(scheduler, times(0)).request(constraint2);
        MatcherAssert.assertThat(constraint.getDelayInMs(),
                CoreMatchers.is(DEFAULT_BATCHING_PERIOD_IN_MS));
    }

    @Test
    public void testAddTwoOfTheSameWithDelayWithTimeDiff() {
        SchedulerConstraint constraint = createConstraint(NetworkUtil.METERED, 0);
        bs.request(constraint);
        timer.incrementMs(DEFAULT_BATCHING_PERIOD_IN_MS - 101);
        SchedulerConstraint constraint2 = createConstraint(NetworkUtil.METERED, 100);
        bs.request(constraint2);
        verify(scheduler, times(1)).request(constraint);
        verify(scheduler, times(0)).request(constraint2);
        MatcherAssert.assertThat(constraint.getDelayInMs(),
                CoreMatchers.is(DEFAULT_BATCHING_PERIOD_IN_MS));
    }

    @Test
    public void testAddTwoOfTheSameWithEnoughDelay() {
        SchedulerConstraint constraint = createConstraint(NetworkUtil.METERED, 0);
        bs.request(constraint);
        SchedulerConstraint constraint2 = createConstraint(NetworkUtil.METERED,
                BatchingScheduler.DEFAULT_BATCHING_PERIOD_IN_MS);
        bs.request(constraint2);
        verify(scheduler, times(1)).request(constraint);
        verify(scheduler, times(1)).request(constraint2);
        MatcherAssert.assertThat(constraint.getDelayInMs(),
                CoreMatchers.is(DEFAULT_BATCHING_PERIOD_IN_MS));
        MatcherAssert.assertThat(constraint2.getDelayInMs(),
                CoreMatchers.is(DEFAULT_BATCHING_PERIOD_IN_MS * 2));
    }

    @Test
    public void testAddTwoOfTheSameWithEnoughTimeDifference() {
        SchedulerConstraint constraint = createConstraint(NetworkUtil.METERED, 0);
        bs.request(constraint);
        timer.incrementMs(DEFAULT_BATCHING_PERIOD_IN_MS);
        SchedulerConstraint constraint2 = createConstraint(NetworkUtil.METERED, 0);
        bs.request(constraint2);
        verify(scheduler, times(1)).request(constraint);
        verify(scheduler, times(1)).request(constraint2);
        MatcherAssert.assertThat(constraint.getDelayInMs(),
                CoreMatchers.is(DEFAULT_BATCHING_PERIOD_IN_MS));
        MatcherAssert.assertThat(constraint2.getDelayInMs(),
                CoreMatchers.is(DEFAULT_BATCHING_PERIOD_IN_MS));
    }

    @Test
    public void testSecondWithDeadline() {
        SchedulerConstraint constraint = createConstraint(NetworkUtil.METERED, 0);
        bs.request(constraint);
        SchedulerConstraint constraint2 = createConstraint(NetworkUtil.METERED, 0, 10L);
        bs.request(constraint2);
        verify(scheduler, times(1)).request(constraint);
        verify(scheduler, times(1)).request(constraint2);
        MatcherAssert.assertThat(constraint.getDelayInMs(),
                CoreMatchers.is(DEFAULT_BATCHING_PERIOD_IN_MS));
        MatcherAssert.assertThat(constraint2.getDelayInMs(),
                CoreMatchers.is(DEFAULT_BATCHING_PERIOD_IN_MS));
        MatcherAssert.assertThat(constraint2.getOverrideDeadlineInMs(),
                CoreMatchers.is(DEFAULT_BATCHING_PERIOD_IN_MS));
    }

    @Test
    public void testFirstWithDeadline() {
        SchedulerConstraint constraint = createConstraint(NetworkUtil.METERED, 0, 10L);
        bs.request(constraint);
        SchedulerConstraint constraint2 = createConstraint(NetworkUtil.METERED, 0);
        bs.request(constraint2);
        verify(scheduler, times(1)).request(constraint);
        verify(scheduler, times(1)).request(constraint2);
        MatcherAssert.assertThat(constraint.getDelayInMs(),
                CoreMatchers.is(DEFAULT_BATCHING_PERIOD_IN_MS));
        MatcherAssert.assertThat(constraint.getOverrideDeadlineInMs(),
                CoreMatchers.is(DEFAULT_BATCHING_PERIOD_IN_MS));

        MatcherAssert.assertThat(constraint2.getDelayInMs(),
                CoreMatchers.is(DEFAULT_BATCHING_PERIOD_IN_MS));
        MatcherAssert.assertThat(constraint2.getOverrideDeadlineInMs(),
                CoreMatchers.nullValue());
    }

    @Test
    public void testTwoWithDeadlinesAndBatch() {
        SchedulerConstraint constraint = createConstraint(NetworkUtil.METERED, 0, 10L);
        bs.request(constraint);
        SchedulerConstraint constraint2 = createConstraint(NetworkUtil.METERED, 0, 20L);
        bs.request(constraint2);
        verify(scheduler, times(1)).request(constraint);
        verify(scheduler, times(0)).request(constraint2);
        MatcherAssert.assertThat(constraint.getDelayInMs(),
                CoreMatchers.is(DEFAULT_BATCHING_PERIOD_IN_MS));
        MatcherAssert.assertThat(constraint.getOverrideDeadlineInMs(),
                CoreMatchers.is(DEFAULT_BATCHING_PERIOD_IN_MS));
    }

    @Test
    public void testAddTwoOfTheSameWithEnoughDeadline() {
        SchedulerConstraint constraint = createConstraint(NetworkUtil.METERED, 0, 0L);
        bs.request(constraint);
        SchedulerConstraint constraint2 = createConstraint(NetworkUtil.METERED, 0,
                BatchingScheduler.DEFAULT_BATCHING_PERIOD_IN_MS);
        bs.request(constraint2);
        verify(scheduler, times(1)).request(constraint);
        verify(scheduler, times(1)).request(constraint2);
        MatcherAssert.assertThat(constraint.getDelayInMs(),
                CoreMatchers.is(DEFAULT_BATCHING_PERIOD_IN_MS));
        MatcherAssert.assertThat(constraint2.getDelayInMs(),
                CoreMatchers.is(DEFAULT_BATCHING_PERIOD_IN_MS));

        MatcherAssert.assertThat(constraint.getOverrideDeadlineInMs(),
                CoreMatchers.is(DEFAULT_BATCHING_PERIOD_IN_MS));
        MatcherAssert.assertThat(constraint2.getOverrideDeadlineInMs(),
                CoreMatchers.is(DEFAULT_BATCHING_PERIOD_IN_MS * 2));
    }

    @Test
    public void testAddTwoWithDifferentNetwork() {
        SchedulerConstraint constraint = createConstraint(NetworkUtil.METERED, 0);
        bs.request(constraint);
        SchedulerConstraint constraint2 = createConstraint(NetworkUtil.UNMETERED, 0);
        bs.request(constraint2);
        verify(scheduler, times(1)).request(constraint);
        verify(scheduler, times(1)).request(constraint2);
        MatcherAssert.assertThat(constraint.getDelayInMs(),
                CoreMatchers.is(DEFAULT_BATCHING_PERIOD_IN_MS));
        MatcherAssert.assertThat(constraint2.getDelayInMs(),
                CoreMatchers.is(DEFAULT_BATCHING_PERIOD_IN_MS));
    }

    @Test
    public void testAddRemoveThenAddAgainOfTheSame() {
        SchedulerConstraint constraint = createConstraint(NetworkUtil.METERED, 0);
        bs.request(constraint);
        verify(scheduler, times(1)).request(constraint);
        MatcherAssert.assertThat(constraint.getDelayInMs(),
                CoreMatchers.is(DEFAULT_BATCHING_PERIOD_IN_MS));
        bs.onFinished(constraint, false);
        SchedulerConstraint constraint2 = createConstraint(NetworkUtil.METERED, 2);
        bs.request(constraint2);

        verify(scheduler, times(1)).request(constraint2);
        MatcherAssert.assertThat(constraint2.getDelayInMs(),
                CoreMatchers.is(DEFAULT_BATCHING_PERIOD_IN_MS));
    }

    private static SchedulerConstraint createConstraint(int networkStatus, long delay) {
        return createConstraint(networkStatus, delay, null);
    }
    private static SchedulerConstraint createConstraint(int networkStatus, long delay, Long deadline) {
        SchedulerConstraint constraint = new SchedulerConstraint("abc");
        constraint.setDelayInMs(delay);
        constraint.setNetworkStatus(networkStatus);
        constraint.setOverrideDeadlineInMs(deadline);
        return constraint;
    }
}
