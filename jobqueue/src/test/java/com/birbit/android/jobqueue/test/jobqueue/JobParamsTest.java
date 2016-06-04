package com.birbit.android.jobqueue.test.jobqueue;

import com.birbit.android.jobqueue.JobHolder;
import com.birbit.android.jobqueue.Params;
import com.birbit.android.jobqueue.test.TestBase;
import com.birbit.android.jobqueue.test.jobs.DummyJob;
import com.birbit.android.jobqueue.test.timer.MockTimer;

import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;

@RunWith(RobolectricGradleTestRunner.class)
@Config(constants = com.birbit.android.jobqueue.BuildConfig.class)
public class JobParamsTest extends TestBase {
    @Test
    public void assertParamsUnderstood() {
        MockTimer mockTimer = new MockTimer();
        JobHolder j1 = JobQueueTestBase.createNewJobHolder(new Params(1).requireNetwork(), mockTimer);
        assertThat("require network param should be understood properly", j1.requiresNetwork(mockTimer.nanoTime()), equalTo(true));
        assertThat("require network param should be understood properly",
                j1.getRequiresNetworkUntilNs(), equalTo(Params.FOREVER));
        assertThat("require unmetered network param should be understood properly",
                j1.getRequiresUnmeteredNetworkUntilNs(), equalTo(Params.NEVER));
        JobHolder j2 = JobQueueTestBase.createNewJobHolder(new Params(1).groupBy("blah"), mockTimer);
        assertThat("group param should be understood properly", j2.getGroupId(), equalTo("blah"));
        assertThat("require network param should be understood properly",
                j2.getRequiresNetworkUntilNs(), equalTo(Params.NEVER));
        assertThat("require unmetered network param should be understood properly",
                j2.getRequiresUnmeteredNetworkUntilNs(), equalTo(Params.NEVER));
        JobHolder j3 = JobQueueTestBase.createNewJobHolder(new Params(1).persist(), mockTimer);
        assertThat("persist param should be understood properly", j3.persistent, equalTo(true));
        JobHolder j4 = JobQueueTestBase.createNewJobHolder(new Params(1).setPersistent(false).setRequiresNetwork(false).setGroupId(null).setSingleId(null), mockTimer);
        assertThat("persist param should be understood properly", j4.persistent, equalTo(false));
        assertThat("require network param should be understood properly", j4.requiresNetwork(mockTimer.nanoTime()), equalTo(false));
        assertThat("require unmetered network param should be understood properly",
                j4.requiresUnmeteredNetwork(mockTimer.nanoTime()), equalTo(false));
        assertThat("group param should be understood properly", j4.groupId, nullValue());
        assertThat("single param should be understood properly", j4.getSingleInstanceId(), nullValue());
        mockTimer.incrementMs(2);
        JobHolder j5 = JobQueueTestBase.createNewJobHolder(new Params(1).requireNetworkWithTimeout(15), mockTimer);

        assertThat("network requirement with timeout should be understood properly",
                j5.getRequiresNetworkUntilNs(), is(17000000L));
        assertThat("network unmetered requirement with timeout should be understood properly",
                j5.getRequiresNetworkUntilNs(), is(17000000L));
        mockTimer.incrementMs(2);
        JobHolder j6 = JobQueueTestBase.createNewJobHolder(new Params(1).requireUnmeteredNetworkWithTimeout(15), mockTimer);
        assertThat("requireNetwork should be set from UNMETERED if it is not specified",
                j6.getRequiresNetworkUntilNs(), is(19000000L));
        assertThat("network unmetered requirement with timeout should be understood properly",
                j6.getRequiresUnmeteredNetworkUntilNs(), is(19000000L));
        mockTimer.incrementMs(2);
        JobHolder j7 = JobQueueTestBase.createNewJobHolder(new Params(1).requireUnmeteredNetworkWithTimeout(15)
                .requireNetworkWithTimeout(20), mockTimer);
        assertThat("requireNetwork should be kept when both are specified",
                j7.getRequiresNetworkUntilNs(), is(26000000L));
        assertThat("network unmetered requirement with timeout should be understood properly",
                j7.getRequiresUnmeteredNetworkUntilNs(), is(21000000L));

        JobHolder j8 = JobQueueTestBase.createNewJobHolder(new Params(1).requireUnmeteredNetworkWithTimeout(15)
                .requireNetwork(), mockTimer);
        assertThat("requireNetwork should be kept when both are specified",
                j8.getRequiresNetworkUntilNs(), is(Params.FOREVER));
        assertThat("network unmetered requirement with timeout should be understood properly",
                j8.getRequiresUnmeteredNetworkUntilNs(), is(21000000L));
        mockTimer.setNow(10000000);
        JobHolder j9 = JobQueueTestBase.createNewJobHolder(new Params(1).requireUnmeteredNetworkWithTimeout(15)
                .requireNetworkWithTimeout(10), mockTimer);
        assertThat("if requireNetwork is less than require unmetered, it should be overridden.",
                j9.getRequiresNetworkUntilNs(), is(25000000L));
        assertThat("network unmetered requirement with timeout should be understood properly",
                j9.getRequiresUnmeteredNetworkUntilNs(), is(25000000L));

        DummyJob j10 = new DummyJob(new Params(1).singleInstanceBy("bloop"));
        assertThat("single param should be understood properly", j10.getSingleInstanceId(), endsWith("bloop"));
        assertThat("group param should be automatically set if single instance", j10.getRunGroupId(), notNullValue());

        mockTimer.setNow(150);

        JobHolder j11 = JobQueueTestBase.createNewJobHolder(new Params(1), mockTimer);
        assertThat("no deadline", j11.getDeadlineNs(), is(Params.FOREVER));

        JobHolder j12 = JobQueueTestBase.createNewJobHolder(new Params(1).overrideDeadlineToCancelInMs(100), mockTimer);
        assertThat("100 ms deadline", j12.getDeadlineNs(), is(100000150L));
        assertThat("100 ms deadline", j12.shouldCancelOnDeadline(), is(true));

        JobHolder j13 = JobQueueTestBase.createNewJobHolder(new Params(1).overrideDeadlineToCancelInMs(200), mockTimer);
        assertThat("100 ms deadline", j13.getDeadlineNs(), is(200000150L));
        assertThat("100 ms deadline", j12.shouldCancelOnDeadline(), is(true));
    }
}
