package com.birbit.android.jobqueue.scheduling;

import android.annotation.TargetApi;
import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.PersistableBundle;
import android.support.annotation.NonNull;

import com.birbit.android.jobqueue.network.NetworkUtil;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.robolectric.RobolectricGradleTestRunner;
import org.robolectric.annotation.Config;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(RobolectricGradleTestRunner.class)
@Config(constants = com.birbit.android.jobqueue.BuildConfig.class)
@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class FrameworkSchedulerTest {
    FrameworkScheduler fwScheduler;
    Context mockContext;
    Scheduler.Callback mockCallback;
    JobScheduler mockJobScheduler;
    SharedPreferences mockSharedPreferences;
    SharedPreferences.Editor mockEditor;

    @Before
    public void init() {
        fwScheduler = new FrameworkScheduler(MockFwService.class);

        mockJobScheduler = mock(JobScheduler.class);

        mockContext = mock(Context.class);
        mockEditor = mock(SharedPreferences.Editor.class);
        mockSharedPreferences = mock(SharedPreferences.class);
        when(mockSharedPreferences.edit()).thenReturn(mockEditor);

        when(mockEditor.commit()).thenReturn(true);
        when(mockEditor.putInt(anyString(), anyInt())).thenReturn(mockEditor);

        when(mockContext.getApplicationContext()).thenReturn(mockContext);
        when(mockContext.getPackageName()).thenReturn("com.foo");
        when(mockContext.getSystemService(Context.JOB_SCHEDULER_SERVICE))
                .thenReturn(mockJobScheduler);
        when(mockContext.getSharedPreferences(anyString(), anyInt()))
                .thenReturn(mockSharedPreferences);

        mockCallback = mock(Scheduler.Callback.class);
        fwScheduler.init(mockContext, mockCallback);
    }

    @Test
    public void componentName() {
        assertThat(fwScheduler.getComponentName(),
                equalTo(new ComponentName(mockContext, MockFwService.class)));
    }

    @Test
    public void requestSimple() {
        networkTest(NetworkUtil.METERED, JobInfo.NETWORK_TYPE_ANY);
    }

    @Test
    public void requestUnmetered() {
        networkTest(NetworkUtil.UNMETERED, JobInfo.NETWORK_TYPE_UNMETERED);
    }

    @Test
    public void requestAnyNetwork() {
        networkTest(NetworkUtil.DISCONNECTED, JobInfo.NETWORK_TYPE_NONE);
    }

    @Test
    public void delay() {
        SchedulerConstraint constraint = mock(SchedulerConstraint.class);
        when(constraint.getNetworkStatus()).thenReturn(NetworkUtil.DISCONNECTED);
        when(constraint.getDelayInMs()).thenReturn(133L);
        JobInfo jobInfo = schedule(constraint);
        assertThat(jobInfo.isPersisted(), is(true));
        assertThat(jobInfo.getId(), is(1));
        assertThat(jobInfo.getMinLatencyMillis(), is(133L));
        assertThat(jobInfo.getMaxExecutionDelayMillis(), is(0L));
    }

    @Test
    public void deadline() {
        SchedulerConstraint constraint = mock(SchedulerConstraint.class);
        when(constraint.getNetworkStatus()).thenReturn(NetworkUtil.DISCONNECTED);
        when(constraint.getOverrideDeadlineInMs()).thenReturn(255L);
        JobInfo jobInfo = schedule(constraint);
        assertThat(jobInfo.isPersisted(), is(true));
        assertThat(jobInfo.getId(), is(1));
        assertThat(jobInfo.getMinLatencyMillis(), is(0L));
        assertThat(jobInfo.getMaxExecutionDelayMillis(), is(255L));
    }

    @Test
    public void deadlineAndDelay() {
        SchedulerConstraint constraint = mock(SchedulerConstraint.class);
        when(constraint.getNetworkStatus()).thenReturn(NetworkUtil.DISCONNECTED);
        when(constraint.getOverrideDeadlineInMs()).thenReturn(255L);
        when(constraint.getDelayInMs()).thenReturn(133L);
        JobInfo jobInfo = schedule(constraint);
        assertThat(jobInfo.isPersisted(), is(true));
        assertThat(jobInfo.getId(), is(1));
        assertThat(jobInfo.getMinLatencyMillis(), is(133L));
        assertThat(jobInfo.getMaxExecutionDelayMillis(), is(255L));
    }

    @Test
    public void bundle() throws Exception {
        SchedulerConstraint constraint = mock(SchedulerConstraint.class);
        when(constraint.getNetworkStatus()).thenReturn(NetworkUtil.METERED);
        when(constraint.getUuid()).thenReturn("abc");
        when(constraint.getDelayInMs()).thenReturn(345L);
        when(constraint.getOverrideDeadlineInMs()).thenReturn(235L);
        JobInfo jobInfo = schedule(constraint);
        PersistableBundle extras = jobInfo.getExtras();
        SchedulerConstraint fromBundle = FrameworkScheduler.fromBundle(extras);
        assertThat(fromBundle.getNetworkStatus(), is(NetworkUtil.METERED));
        assertThat(fromBundle.getUuid(), is("abc"));
        assertThat(fromBundle.getDelayInMs(), is(345L));
        assertThat(fromBundle.getOverrideDeadlineInMs(), is(235L));
    }

    @Test
    public void bundleNullDeadline() throws Exception {
        SchedulerConstraint constraint = mock(SchedulerConstraint.class);
        when(constraint.getNetworkStatus()).thenReturn(NetworkUtil.METERED);
        when(constraint.getUuid()).thenReturn("abc");
        when(constraint.getDelayInMs()).thenReturn(345L);
        when(constraint.getOverrideDeadlineInMs()).thenReturn(null);
        JobInfo jobInfo = schedule(constraint);
        PersistableBundle extras = jobInfo.getExtras();
        SchedulerConstraint fromBundle = FrameworkScheduler.fromBundle(extras);
        assertThat(fromBundle.getNetworkStatus(), is(NetworkUtil.METERED));
        assertThat(fromBundle.getUuid(), is("abc"));
        assertThat(fromBundle.getDelayInMs(), is(345L));
        assertThat(fromBundle.getOverrideDeadlineInMs(), is(nullValue()));
    }

    @Test
    public void badBundleOnStart() {
        // see https://github.com/yigit/android-priority-jobqueue/issues/254
        JobParameters params = mock(JobParameters.class);
        PersistableBundle badBundle = mock(PersistableBundle.class);
        when(badBundle.getString(anyString(), anyString())).thenThrow(new NullPointerException());
        when(badBundle.getString(anyString())).thenThrow(new NullPointerException());
        assertThat(fwScheduler.onStartJob(params), is(false));
    }

    @Test
    public void badBundleOnStop() {
        // see https://github.com/yigit/android-priority-jobqueue/issues/254
        JobParameters params = mock(JobParameters.class);
        PersistableBundle badBundle = mock(PersistableBundle.class);
        when(badBundle.getString(anyString(), anyString())).thenThrow(new NullPointerException());
        when(badBundle.getString(anyString())).thenThrow(new NullPointerException());
        assertThat(fwScheduler.onStopJob(params), is(false));
    }

    @Test
    public void onStart1() {
        onStartTest(NetworkUtil.DISCONNECTED, 0, null);
    }

    @Test
    public void onStart2() {
        onStartTest(NetworkUtil.METERED, 0, null);
    }

    @Test
    public void onStart3() {
        onStartTest(NetworkUtil.UNMETERED, 0, null);
    }

    @Test
    public void onStart4() {
        onStartTest(NetworkUtil.METERED, 10, null);
    }

    @Test
    public void onStart5() {
        onStartTest(NetworkUtil.METERED, 10, null);
    }

    @Test
    public void onStart6() {
        onStartTest(NetworkUtil.METERED, 0, 100L);
    }

    @Test
    public void onStart7() {
        onStartTest(NetworkUtil.UNMETERED, 35, 77L);
    }

    @Test
    public void onFinished() {
        SchedulerConstraint constraint = mock(SchedulerConstraint.class);
        when(constraint.getNetworkStatus()).thenReturn(NetworkUtil.METERED);
        when(constraint.getUuid()).thenReturn("abc");
        when(constraint.getDelayInMs()).thenReturn(22L);
        when(constraint.getOverrideDeadlineInMs()).thenReturn(null);
        JobParameters[] outParams = new JobParameters[1];
        SchedulerConstraint received = triggerOnStart(constraint, outParams);
        fwScheduler.onFinished(received, false);
        // TODO would be nice to use powermock and assert onFinished call
    }

    @Test
    public void cancelAll() {
        fwScheduler.cancelAll();
        verify(mockJobScheduler).cancelAll();
    }

    private void onStartTest(int networkStatus, long delay, Long overrideDeadline) {
        SchedulerConstraint constraint = mock(SchedulerConstraint.class);
        when(constraint.getNetworkStatus()).thenReturn(networkStatus);
        when(constraint.getUuid()).thenReturn("abc");
        when(constraint.getDelayInMs()).thenReturn(delay);
        when(constraint.getOverrideDeadlineInMs()).thenReturn(overrideDeadline);
        JobParameters[] outParams = new JobParameters[1];
        SchedulerConstraint received = triggerOnStart(constraint, outParams);

        assertThat(received.getOverrideDeadlineInMs(), is(overrideDeadline));
        assertThat(received.getDelayInMs(), is(delay));
        assertThat(received.getUuid(), is("abc"));
        assertThat(received.getNetworkStatus(), is(networkStatus));

        received = triggerOnStop(constraint, outParams[0]);
        assertThat(received.getOverrideDeadlineInMs(), is(overrideDeadline));
        assertThat(received.getDelayInMs(), is(delay));
        assertThat(received.getUuid(), is("abc"));
        assertThat(received.getNetworkStatus(), is(networkStatus));
    }

    private SchedulerConstraint triggerOnStart(SchedulerConstraint constraint, JobParameters[]
                                               outParams) {
        ArgumentCaptor<SchedulerConstraint> constraintCaptor =
                ArgumentCaptor.forClass(SchedulerConstraint.class);
        when(mockCallback.start(constraintCaptor.capture())).thenReturn(true);

        JobParameters params = prepareJobParameters(constraint);
        outParams[0] = params;
        fwScheduler.onStartJob(params);

        verify(mockCallback).start(Mockito.any(SchedulerConstraint.class));
        return constraintCaptor.getValue();
    }

    private SchedulerConstraint triggerOnStop(SchedulerConstraint constraint,
                                              JobParameters params) {
        ArgumentCaptor<SchedulerConstraint> constraintCaptor =
                ArgumentCaptor.forClass(SchedulerConstraint.class);
        when(mockCallback.stop(constraintCaptor.capture())).thenReturn(false);

        fwScheduler.onStopJob(params);

        verify(mockCallback).stop(Mockito.any(SchedulerConstraint.class));
        return constraintCaptor.getValue();
    }

    @NonNull
    private JobParameters prepareJobParameters(SchedulerConstraint constraint) {
        JobInfo info = schedule(constraint);

        JobParameters params = mock(JobParameters.class);
        when(params.getExtras()).thenReturn(info.getExtras());
        when(params.getJobId()).thenReturn(info.getId());
        return params;
    }

    private void networkTest(@NetworkUtil.NetworkStatus int networkType, int expected) {
        SchedulerConstraint constraint = mock(SchedulerConstraint.class);
        when(constraint.getNetworkStatus()).thenReturn(networkType);
        JobInfo jobInfo = schedule(constraint);
        assertThat(jobInfo.isPersisted(), is(true));
        assertThat(jobInfo.getId(), is(1));
        assertThat(jobInfo.getMinLatencyMillis(), is(0L));
        assertThat(jobInfo.getMaxExecutionDelayMillis(), is(0L));
        assertThat(jobInfo.getNetworkType(), is(expected));
    }

    @Test
    public void createId() {
        when(mockSharedPreferences.getInt(FrameworkScheduler.KEY_ID, 0)).thenReturn(33);
        assertThat(fwScheduler.createId(), is(34));
        verify(mockEditor).putInt(FrameworkScheduler.KEY_ID, 34);
    }

    private JobInfo schedule(SchedulerConstraint constraint) {
        ArgumentCaptor<JobInfo> infoCaptor = ArgumentCaptor.forClass(JobInfo.class);
        when(mockJobScheduler.schedule(infoCaptor.capture())).thenReturn(1);
        fwScheduler.request(constraint);
        verify(mockJobScheduler).schedule(Mockito.any(JobInfo.class));
        verify(mockEditor).putInt(FrameworkScheduler.KEY_ID, 1);
        return infoCaptor.getValue();
    }
}
