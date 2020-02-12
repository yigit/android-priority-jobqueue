package com.birbit.android.jobqueue.scheduling;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.PersistableBundle;

import com.birbit.android.jobqueue.network.NetworkUtil;
import com.google.android.gms.gcm.GcmNetworkManager;
import com.google.android.gms.gcm.OneoffTask;
import com.google.android.gms.gcm.Task;
import com.google.android.gms.gcm.TaskParams;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(RobolectricTestRunner.class)

@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class GcmSchedulerTest {
    GcmScheduler gcmScheduler;
    GcmNetworkManager mockGcmNetworkManager;
    Context mockContext;
    Scheduler.Callback mockCallback;
    SharedPreferences mockSharedPreferences;
    SharedPreferences.Editor mockEditor;

    @Before
    public void init() {
        gcmScheduler = new GcmScheduler(RuntimeEnvironment.application, MockGcmService.class);
        mockGcmNetworkManager = mock(GcmNetworkManager.class);
        gcmScheduler.gcmNetworkManager = mockGcmNetworkManager;

        mockContext = mock(Context.class);
        mockEditor = mock(SharedPreferences.Editor.class);
        mockSharedPreferences = mock(SharedPreferences.class);
        when(mockSharedPreferences.edit()).thenReturn(mockEditor);

        when(mockEditor.commit()).thenReturn(true);
        when(mockEditor.putInt(anyString(), anyInt())).thenReturn(mockEditor);

        when(mockContext.getApplicationContext()).thenReturn(mockContext);
        when(mockContext.getPackageName()).thenReturn("com.foo");
        when(mockContext.getSharedPreferences(anyString(), anyInt()))
                .thenReturn(mockSharedPreferences);

        mockCallback = mock(Scheduler.Callback.class);
        gcmScheduler.init(mockContext, mockCallback);
    }

    @Test
    public void requestSimple() {
        networkTest(NetworkUtil.METERED, Task.NETWORK_STATE_CONNECTED);
    }

    @Test
    public void requestUnmetered() {
        networkTest(NetworkUtil.UNMETERED, Task.NETWORK_STATE_UNMETERED);
    }

    @Test
    public void requestAnyNetwork() {
        networkTest(NetworkUtil.DISCONNECTED, Task.NETWORK_STATE_ANY);
    }

    @Test
    public void delay() {
        long delaySeconds = 3;
        SchedulerConstraint constraint = mock(SchedulerConstraint.class);
        when(constraint.getNetworkStatus()).thenReturn(NetworkUtil.DISCONNECTED);
        when(constraint.getDelayInMs()).thenReturn(TimeUnit.SECONDS.toMillis(delaySeconds));
        when(constraint.getOverrideDeadlineInMs()).thenReturn(null);
        OneoffTask jobInfo = schedule(constraint);
        assertThat(jobInfo.isPersisted(), is(true));
        assertThat(jobInfo.getWindowStart(), is(delaySeconds));
        assertThat(jobInfo.getWindowEnd(), is(delaySeconds +
                gcmScheduler.getExecutionWindowSizeInSeconds()));
    }

    @Test
    public void deadline() {
        SchedulerConstraint constraint = mock(SchedulerConstraint.class);
        when(constraint.getNetworkStatus()).thenReturn(NetworkUtil.DISCONNECTED);
        when(constraint.getOverrideDeadlineInMs()).thenReturn(
                TimeUnit.SECONDS.toMillis(37)
        );
        OneoffTask jobInfo = schedule(constraint);
        assertThat(jobInfo.isPersisted(), is(true));
        assertThat(jobInfo.getWindowStart(), is(0L));
        assertThat(jobInfo.getWindowEnd(), is(37L));
    }

    @Test
    public void deadlineAndDelay() {
        long delaySeconds = 43;
        long deadlineSeconds = 57;
        SchedulerConstraint constraint = mock(SchedulerConstraint.class);
        when(constraint.getNetworkStatus()).thenReturn(NetworkUtil.DISCONNECTED);
        when(constraint.getOverrideDeadlineInMs()).thenReturn(
                TimeUnit.SECONDS.toMillis(deadlineSeconds));
        when(constraint.getDelayInMs()).thenReturn(TimeUnit.SECONDS.toMillis(delaySeconds));
        OneoffTask jobInfo = schedule(constraint);
        assertThat(jobInfo.isPersisted(), is(true));
        assertThat(jobInfo.getWindowStart(), is(delaySeconds));
        assertThat(jobInfo.getWindowEnd(), is(deadlineSeconds));
    }

    @Test
    public void bundle() throws Exception {
        SchedulerConstraint constraint = mock(SchedulerConstraint.class);
        when(constraint.getNetworkStatus()).thenReturn(NetworkUtil.METERED);
        when(constraint.getUuid()).thenReturn("abc");
        when(constraint.getDelayInMs()).thenReturn(345L);
        when(constraint.getOverrideDeadlineInMs()).thenReturn(235L);
        OneoffTask jobInfo = schedule(constraint);
        Bundle extras = jobInfo.getExtras();
        SchedulerConstraint fromBundle = GcmScheduler.fromBundle(extras);
        assertThat(fromBundle.getNetworkStatus(), is(NetworkUtil.METERED));
        assertThat(fromBundle.getUuid(), is("abc"));
        assertThat(fromBundle.getDelayInMs(), is(345L));
        assertThat(fromBundle.getOverrideDeadlineInMs(), is(235L));
    }

    @Test
    public void badBundleOnStart() {
        // see https://github.com/yigit/android-priority-jobqueue/issues/254
        TaskParams params = mock(TaskParams.class);
        PersistableBundle badBundle = mock(PersistableBundle.class);
        when(badBundle.getString(anyString(), anyString())).thenThrow(new NullPointerException());
        when(badBundle.getString(anyString())).thenThrow(new NullPointerException());
        // return success since we cannot handle this bad bundle
        assertThat(gcmScheduler.onStartJob(params), is(GcmNetworkManager.RESULT_SUCCESS));
    }

    @Test
    public void bundleNullDeadline() throws Exception {
        SchedulerConstraint constraint = mock(SchedulerConstraint.class);
        when(constraint.getNetworkStatus()).thenReturn(NetworkUtil.METERED);
        when(constraint.getUuid()).thenReturn("abc");
        when(constraint.getDelayInMs()).thenReturn(345L);
        when(constraint.getOverrideDeadlineInMs()).thenReturn(null);
        OneoffTask jobInfo = schedule(constraint);
        Bundle extras = jobInfo.getExtras();
        SchedulerConstraint fromBundle = GcmScheduler.fromBundle(extras);
        assertThat(fromBundle.getNetworkStatus(), is(NetworkUtil.METERED));
        assertThat(fromBundle.getUuid(), is("abc"));
        assertThat(fromBundle.getDelayInMs(), is(345L));
        assertThat(fromBundle.getOverrideDeadlineInMs(), is(nullValue()));
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

    private void onStartTest(int networkStatus, long delay, Long overrideDeadline) {
        SchedulerConstraint constraint = mock(SchedulerConstraint.class);
        when(constraint.getNetworkStatus()).thenReturn(networkStatus);
        when(constraint.getUuid()).thenReturn("abc");
        when(constraint.getDelayInMs()).thenReturn(delay);
        when(constraint.getOverrideDeadlineInMs()).thenReturn(overrideDeadline);
        SchedulerConstraint received = triggerOnStart(constraint);

        assertThat(received.getOverrideDeadlineInMs(), is(overrideDeadline));
        assertThat(received.getDelayInMs(), is(delay));
        assertThat(received.getUuid(), is("abc"));
        assertThat(received.getNetworkStatus(), is(networkStatus));
    }

    private SchedulerConstraint triggerOnStart(SchedulerConstraint constraint) {
        ArgumentCaptor<SchedulerConstraint> constraintCaptor =
                ArgumentCaptor.forClass(SchedulerConstraint.class);
        when(mockCallback.start(constraintCaptor.capture())).thenAnswer(new Answer<Boolean>() {
            @Override
            public Boolean answer(final InvocationOnMock invocation) throws Throwable {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        SchedulerConstraint constrant =
                                (SchedulerConstraint) invocation.getArguments()[0];
                        gcmScheduler.onFinished(constrant, false);
                    }
                }).start();
                return true;
            }
        });

        TaskParams params = prepareJobParameters(constraint);
        gcmScheduler.onStartJob(params);

        verify(mockCallback).start(Mockito.any(SchedulerConstraint.class));
        return constraintCaptor.getValue();
    }

    private TaskParams prepareJobParameters(SchedulerConstraint constraint) {
        return new TaskParams(constraint.getUuid(),
                GcmScheduler.toBundle(constraint));
    }


    private void networkTest(@NetworkUtil.NetworkStatus int networkType, int expected) {
        SchedulerConstraint constraint = mock(SchedulerConstraint.class);
        when(constraint.getNetworkStatus()).thenReturn(networkType);
        when(constraint.getOverrideDeadlineInMs()).thenReturn(null);

        OneoffTask jobInfo = schedule(constraint);
        assertThat(jobInfo.isPersisted(), is(true));
        assertThat(jobInfo.getRequiredNetwork(), is(expected));
        assertThat(jobInfo.getWindowStart(), is(0L));

        assertThat(jobInfo.getWindowEnd(), is(gcmScheduler.getExecutionWindowSizeInSeconds()));
    }

    private OneoffTask schedule(SchedulerConstraint constraint) {
        if (constraint.getUuid() == null) {
            when(constraint.getUuid()).thenReturn(UUID.randomUUID().toString());
        }
        ArgumentCaptor<OneoffTask> infoCaptor = ArgumentCaptor.forClass(OneoffTask.class);
        gcmScheduler.request(constraint);
        verify(mockGcmNetworkManager).schedule(infoCaptor.capture());
        OneoffTask jobInfo = infoCaptor.getValue();
        assertThat(jobInfo.getTag(), is(constraint.getUuid()));
        return jobInfo;
    }

}
