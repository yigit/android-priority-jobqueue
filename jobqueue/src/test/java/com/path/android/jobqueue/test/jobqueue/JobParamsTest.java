package com.path.android.jobqueue.test.jobqueue;

import com.path.android.jobqueue.Params;
import com.path.android.jobqueue.test.TestBase;
import com.path.android.jobqueue.test.jobs.DummyJob;
import com.path.android.jobqueue.test.timer.MockTimer;

import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;

@RunWith(RobolectricGradleTestRunner.class)
@Config(constants = com.path.android.jobqueue.BuildConfig.class)
public class JobParamsTest extends TestBase {
    @Test
    public void assertParamsUnderstood() {
        DummyJob j1 = new DummyJob(new Params(1).requireNetwork());
        MockTimer mockTimer = new MockTimer();
        assertThat("require network param should be understood properly", j1.requiresNetwork(
                mockTimer), equalTo(true));
        j1.seal(mockTimer);
        assertThat("require network param should be understood properly",
                j1.getRequiresNetworkUntilNs(), equalTo(Params.FOREVER));
        DummyJob j2 = new DummyJob(new Params(1).groupBy("blah"));
        assertThat("group param should be understood properly", j2.getRunGroupId(), equalTo("blah"));
        j2.seal(mockTimer);
        assertThat("require network param should be understood properly",
                j2.getRequiresNetworkUntilNs(), equalTo(Params.NEVER));
        DummyJob j3 = new DummyJob(new Params(1).persist());
        assertThat("group param should be understood properly", j3.isPersistent(), equalTo(true));
        DummyJob j4 = new DummyJob(new Params(1).setPersistent(false).setRequiresNetwork(false).setGroupId(null));
        assertThat("persist param should be understood properly", j4.isPersistent(), equalTo(false));
        assertThat("require network param should be understood properly", j4.requiresNetwork(
                mockTimer), equalTo(false));
        assertThat("group param should be understood properly", j4.getRunGroupId(), nullValue());

        DummyJob j5 = new DummyJob(new Params(1).requireNetworkWithTimeout(15));
        mockTimer.incrementMs(2);
        j5.seal(mockTimer);
        assertThat("network requirement with timeout should be understood properly",
                j5.getRequiresNetworkUntilNs(), is(17000000L));
    }
}
