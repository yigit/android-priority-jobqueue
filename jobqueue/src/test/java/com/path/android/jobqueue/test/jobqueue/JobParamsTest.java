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
        assertThat("require unmetered network param should be understood properly",
                j1.getRequiresUnmeteredNetworkUntilNs(), equalTo(Params.NEVER));
        DummyJob j2 = new DummyJob(new Params(1).groupBy("blah"));
        assertThat("group param should be understood properly", j2.getRunGroupId(), equalTo("blah"));
        j2.seal(mockTimer);
        assertThat("require network param should be understood properly",
                j2.getRequiresNetworkUntilNs(), equalTo(Params.NEVER));
        assertThat("require unmetered network param should be understood properly",
                j2.getRequiresUnmeteredNetworkUntilNs(), equalTo(Params.NEVER));
        DummyJob j3 = new DummyJob(new Params(1).persist());
        assertThat("group param should be understood properly", j3.isPersistent(), equalTo(true));
        DummyJob j4 = new DummyJob(new Params(1).setPersistent(false).setRequiresNetwork(false).setGroupId(null));
        assertThat("persist param should be understood properly", j4.isPersistent(), equalTo(false));
        assertThat("require network param should be understood properly", j4.requiresNetwork(
                mockTimer), equalTo(false));
        assertThat("require unmetered network param should be understood properly",
                j4.requiresUnmeteredNetwork(mockTimer), equalTo(false));
        assertThat("group param should be understood properly", j4.getRunGroupId(), nullValue());

        DummyJob j5 = new DummyJob(new Params(1).requireNetworkWithTimeout(15));
        mockTimer.incrementMs(2);
        j5.seal(mockTimer);
        assertThat("network requirement with timeout should be understood properly",
                j5.getRequiresNetworkUntilNs(), is(17000000L));
        assertThat("network unmetered requirement with timeout should be understood properly",
                j5.getRequiresNetworkUntilNs(), is(17000000L));

        DummyJob j6 = new DummyJob(new Params(1).requireUnmeteredNetworkWithTimeout(15));
        mockTimer.incrementMs(2);
        j6.seal(mockTimer);
        assertThat("requireNetwork should be set from UNMETERED if it is not specified",
                j6.getRequiresNetworkUntilNs(), is(19000000L));
        assertThat("network unmetered requirement with timeout should be understood properly",
                j6.getRequiresUnmeteredNetworkUntilNs(), is(19000000L));

        DummyJob j7 = new DummyJob(new Params(1).requireUnmeteredNetworkWithTimeout(15)
                .requireNetworkWithTimeout(20));
        mockTimer.incrementMs(2);
        j7.seal(mockTimer);
        assertThat("requireNetwork should be kept when both are specified",
                j7.getRequiresNetworkUntilNs(), is(26000000L));
        assertThat("network unmetered requirement with timeout should be understood properly",
                j7.getRequiresUnmeteredNetworkUntilNs(), is(21000000L));

        DummyJob j8 = new DummyJob(new Params(1).requireUnmeteredNetworkWithTimeout(15)
                .requireNetwork());
        j8.seal(mockTimer);
        assertThat("requireNetwork should be kept when both are specified",
                j8.getRequiresNetworkUntilNs(), is(Params.FOREVER));
        assertThat("network unmetered requirement with timeout should be understood properly",
                j8.getRequiresUnmeteredNetworkUntilNs(), is(21000000L));

        DummyJob j9 = new DummyJob(new Params(1).requireUnmeteredNetworkWithTimeout(15)
                .requireNetworkWithTimeout(10));
        mockTimer.setNow(10000000);
        j9.seal(mockTimer);
        assertThat("if requireNetwork is less than require unmetered, it should be overridden.",
                j9.getRequiresNetworkUntilNs(), is(25000000L));
        assertThat("network unmetered requirement with timeout should be understood properly",
                j9.getRequiresUnmeteredNetworkUntilNs(), is(25000000L));
    }
}
