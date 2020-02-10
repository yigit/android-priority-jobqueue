package com.birbit.android.jobqueue.test.jobqueue;

import com.birbit.android.jobqueue.JobHolder;
import com.birbit.android.jobqueue.Params;
import com.birbit.android.jobqueue.network.NetworkUtil;
import com.birbit.android.jobqueue.test.TestBase;
import com.birbit.android.jobqueue.test.jobs.DummyJob;
import com.birbit.android.jobqueue.test.timer.MockTimer;

import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)

public class JobParamsTest extends TestBase {
    @Test
    public void assertParamsUnderstood() {
        MockTimer mockTimer = new MockTimer();
        JobHolder j1 = JobQueueTestBase.createNewJobHolder(new Params(1).requireNetwork(), mockTimer);
        assertThat("require network param should be understood properly", j1.getRequiredNetworkType(), equalTo(NetworkUtil.METERED));

        JobHolder j2 = JobQueueTestBase.createNewJobHolder(new Params(1).groupBy("blah"), mockTimer);
        assertThat("group param should be understood properly", j2.getGroupId(), equalTo("blah"));
        assertThat("require network param should be understood properly",
                j2.getRequiredNetworkType(), equalTo(NetworkUtil.DISCONNECTED));

        JobHolder j3 = JobQueueTestBase.createNewJobHolder(new Params(1).persist(), mockTimer);
        assertThat("persist param should be understood properly", j3.persistent, equalTo(true));

        JobHolder j4 = JobQueueTestBase.createNewJobHolder(new Params(1).setPersistent(false)
                .setRequiresNetwork(false).setGroupId(null).setSingleId(null), mockTimer);
        assertThat("persist param should be understood properly", j4.persistent, equalTo(false));
        assertThat("require network param should be understood properly", j4.getRequiredNetworkType(), equalTo(NetworkUtil.DISCONNECTED));

        assertThat("group param should be understood properly", j4.groupId, nullValue());
        assertThat("single param should be understood properly", j4.getSingleInstanceId(), nullValue());
        mockTimer.incrementMs(2);

        DummyJob j15 = new DummyJob(new Params(1).singleInstanceBy("bloop"));
        assertThat("single param should be understood properly", j15.getSingleInstanceId(), endsWith("bloop"));
        assertThat("group param should be automatically set if single instance", j15.getRunGroupId(), notNullValue());

        mockTimer.setNow(150);

        JobHolder j6 = JobQueueTestBase.createNewJobHolder(new Params(1), mockTimer);
        assertThat("no deadline", j6.getDeadlineNs(), is(Params.FOREVER));

        JobHolder j7 = JobQueueTestBase.createNewJobHolder(new Params(1).overrideDeadlineToCancelInMs(100), mockTimer);
        assertThat("100 ms deadline", j7.getDeadlineNs(), is(100000150L));
        assertThat("100 ms deadline", j7.shouldCancelOnDeadline(), is(true));

        JobHolder j13 = JobQueueTestBase.createNewJobHolder(new Params(1).overrideDeadlineToCancelInMs(200), mockTimer);
        assertThat("100 ms deadline", j13.getDeadlineNs(), is(200000150L));
        assertThat("100 ms deadline", j7.shouldCancelOnDeadline(), is(true));
    }
}
