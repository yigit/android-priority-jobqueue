package com.birbit.android.jobqueue.test.jobmanager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.birbit.android.jobqueue.CancelReason;
import com.birbit.android.jobqueue.Job;
import com.birbit.android.jobqueue.JobManager;
import com.birbit.android.jobqueue.Params;
import com.birbit.android.jobqueue.RetryConstraint;
import com.birbit.android.jobqueue.config.Configuration;
import com.birbit.android.jobqueue.network.NetworkUtil;
import com.birbit.android.jobqueue.scheduling.Scheduler;
import com.birbit.android.jobqueue.scheduling.SchedulerConstraint;

import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.robolectric.ParameterizedRobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@RunWith(ParameterizedRobolectricTestRunner.class)

public class SchedulerSimpleTestCase extends JobManagerTestBase {
    final boolean persistent;
    final boolean requireNetwork;
    final boolean requireUnmeteredNetwork;
    final long delayInMs;
    final Long deadline;

    public SchedulerSimpleTestCase(boolean persistent, boolean requireNetwork,
                                   boolean requireUnmeteredNetwork, long delayInMs, Long deadline) {
        this.persistent = persistent;
        this.requireNetwork = requireNetwork;
        this.requireUnmeteredNetwork = requireUnmeteredNetwork;
        this.delayInMs = delayInMs;
        this.deadline = deadline;
    }

    @ParameterizedRobolectricTestRunner.Parameters(name =
            "persistent: {0} reqNetwork: {1} reqUnmetered: {2} delay: {3} deadline: {4}")
    public static List<Object[]> getParams() {
        List<Object[]> params = new ArrayList<>();
        for (long delay : new long[]{0, 1000, JobManager.MIN_DELAY_TO_USE_SCHEDULER_IN_MS}) {
            for (Long deadline : new Long[]{null, 500L, JobManager.MIN_DELAY_TO_USE_SCHEDULER_IN_MS}) {
                if (deadline != null && deadline < delay) {
                    continue;
                }
                for (int i = 0; i < 8; i++) {
                    params.add(new Object[] {
                            (i & 1) == 1,
                            (i & 2) == 2,
                            (i & 4) == 4,
                            delay,
                            deadline
                    });
                }
            }
        }
        return params;
    }

    @Test
    public void testScheduleWhenJobAdded() throws InterruptedException {
        Scheduler scheduler = Mockito.mock(Scheduler.class);
        ArgumentCaptor<SchedulerConstraint> captor =
                ArgumentCaptor.forClass(SchedulerConstraint.class);
        DummyNetworkUtilWithConnectivityEventSupport networkUtil =
                new DummyNetworkUtilWithConnectivityEventSupport();
        Configuration.Builder builder = new Configuration.Builder(RuntimeEnvironment.application)
                .timer(mockTimer)
                .networkUtil(networkUtil)
                .inTestMode()
                .scheduler(scheduler, false);
        if (requireUnmeteredNetwork) {
            networkUtil.setNetworkStatus(NetworkUtil.UNMETERED);
        } else if (requireNetwork) {
            networkUtil.setNetworkStatus(NetworkUtil.METERED);
        } else {
            networkUtil.setNetworkStatus(NetworkUtil.DISCONNECTED);
        }

        final JobManager jobManager = createJobManager(builder);
        Params params = new Params(1);
        params.setPersistent(persistent);
        params.setRequiresNetwork(requireNetwork);
        params.setRequiresUnmeteredNetwork(requireUnmeteredNetwork);
        params.setDelayMs(delayInMs);
        if (deadline != null) {
            params.overrideDeadlineToRunInMs(deadline);
        }
        final SchedulerJob job = new SchedulerJob(params);
        final CountDownLatch cancelLatch = new CountDownLatch(1);
        Mockito.doAnswer(new Answer() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                cancelLatch.countDown();
                return null;
            }
        }).when(scheduler).cancelAll();
        waitUntilJobsAreDone(jobManager, Collections.singletonList(job), new Runnable() {
            @Override
            public void run() {
                jobManager.addJob(job);
                mockTimer.incrementMs(delayInMs);
            }
        });
        if (persistent && (requireNetwork || requireUnmeteredNetwork ||
                delayInMs >= JobManager.MIN_DELAY_TO_USE_SCHEDULER_IN_MS ||
                (deadline != null && deadline >= JobManager.MIN_DELAY_TO_USE_SCHEDULER_IN_MS))) {
            Mockito.verify(scheduler).request(captor.capture());
            SchedulerConstraint constraint = captor.getValue();
            MatcherAssert.assertThat(constraint.getNetworkStatus(),
                    CoreMatchers.is(requireUnmeteredNetwork ? NetworkUtil.UNMETERED :
                    requireNetwork ? NetworkUtil.METERED : NetworkUtil.DISCONNECTED));
            MatcherAssert.assertThat(constraint.getDelayInMs(), CoreMatchers.is(delayInMs));
            // wait until cancel is called because it is called when JQ is idle.
            // for more clear reporting, let mockito handle the check
            MatcherAssert.assertThat(constraint.getOverrideDeadlineInMs(), CoreMatchers.is(deadline));
            cancelLatch.await(30, TimeUnit.SECONDS);
            Mockito.verify(scheduler).cancelAll();
        } else {
            Mockito.verify(scheduler, Mockito.never())
                    .request(Mockito.any(SchedulerConstraint.class));
        }
    }

    public static class SchedulerJob extends Job {

        @SuppressWarnings("WeakerAccess")
        public SchedulerJob(Params params) {
            super(params);
        }

        @Override
        public void onAdded() {

        }

        @Override
        public void onRun() throws Throwable {

        }

        @Override
        protected void onCancel(@CancelReason int cancelReason, @Nullable Throwable throwable) {

        }

        @Override
        protected RetryConstraint shouldReRunOnThrowable(@NonNull Throwable throwable, int runCount, int maxRunCount) {
            throw new UnsupportedOperationException("not expected to arrive here");
        }
    }
}
