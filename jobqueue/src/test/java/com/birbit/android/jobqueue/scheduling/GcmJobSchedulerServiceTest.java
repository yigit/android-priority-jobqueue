package com.birbit.android.jobqueue.scheduling;

import android.annotation.TargetApi;
import android.os.Build;
import android.support.annotation.NonNull;

import com.birbit.android.jobqueue.JobManager;
import com.google.android.gms.gcm.TaskParams;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(RobolectricTestRunner.class)
@Config(constants = com.birbit.android.jobqueue.BuildConfig.class)
@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class GcmJobSchedulerServiceTest {
    JobManager mockJobManager;
    GcmJobSchedulerServiceImpl service;
    GcmScheduler mockScheduler;

    @Before
    public void init() {
        mockJobManager = mock(JobManager.class);
        service = new GcmJobSchedulerServiceImpl();
        mockScheduler = mock(GcmScheduler.class);
        when(mockJobManager.getScheduler()).thenReturn(mockScheduler);
    }

    @Test
    public void createScheduler() {
        GcmScheduler scheduler = GcmJobSchedulerService
                .createSchedulerFor(RuntimeEnvironment.application,
                        GcmJobSchedulerServiceImpl.class);
        assertThat(scheduler.serviceClass == GcmJobSchedulerServiceImpl.class, is(true));
    }

    @Test(expected = IllegalArgumentException.class)
    public void createBadScheduler() {
        GcmJobSchedulerService.createSchedulerFor(RuntimeEnvironment.application,
                GcmJobSchedulerService.class);
    }

    @Test
    public void onStartJob() {
        TaskParams params = mock(TaskParams.class);
        service.onRunTask(params);
        verify(mockScheduler).onStartJob(params);
    }

    @Test
    public void onStartJobWithoutScheduler() {
        when(mockJobManager.getScheduler()).thenReturn(null);
        TaskParams params = mock(TaskParams.class);
        service.onRunTask(params);
        // test sanity
        verify(mockScheduler, never()).onStartJob(params);
    }

    public class GcmJobSchedulerServiceImpl extends GcmJobSchedulerService {

        @NonNull
        @Override
        protected JobManager getJobManager() {
            return mockJobManager;
        }
    }
}
