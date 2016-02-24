package com.path.android.jobqueue.test.jobqueue;

import com.path.android.jobqueue.Params;
import com.path.android.jobqueue.test.TestBase;
import com.path.android.jobqueue.test.jobs.DummyJob;
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
        assertThat("require network param should be understood properly", j1.requiresNetwork(), equalTo(true));
        DummyJob j2 = new DummyJob(new Params(1).groupBy("blah"));
        assertThat("group param should be understood properly", j2.getRunGroupId(), equalTo("blah"));
        DummyJob j5 = new DummyJob(new Params(1).singleWith("bloop"));
        assertThat("single param should be understood properly", j5.getRunSingleId(), equalTo("bloop"));
        DummyJob j3 = new DummyJob(new Params(1).persist());
        assertThat("persist param should be understood properly", j3.isPersistent(), equalTo(true));
        DummyJob j4 = new DummyJob(new Params(1).setPersistent(false).setRequiresNetwork(false).setGroupId(null).setSingleId(null));
        assertThat("persist param should be understood properly", j4.isPersistent(), equalTo(false));
        assertThat("require network param should be understood properly", j4.requiresNetwork(), equalTo(false));
        assertThat("group param should be understood properly", j4.getRunGroupId(), nullValue());
        assertThat("single param should be understood properly", j4.getRunSingleId(), nullValue());
    }
}
