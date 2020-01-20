package com.birbit.android.jobqueue.scheduling;

import android.annotation.TargetApi;
import android.app.job.JobParameters;
import android.os.Build;
import androidx.annotation.NonNull;

import com.birbit.android.jobqueue.JobManager;

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

@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class FrameworkJobSchedulerServiceTest {
    JobManager mockJobManager;
    FrameworkJobSchedulerServiceImpl service;
    FrameworkScheduler mockScheduler;

    @Before
    public void init() {
        mockJobManager = mock(JobManager.class);
        service = new FrameworkJobSchedulerServiceImpl();
        mockScheduler = mock(FrameworkScheduler.class);
        when(mockJobManager.getScheduler()).thenReturn(mockScheduler);
    }

    @Test
    public void createScheduler() {
        FrameworkScheduler scheduler = FrameworkJobSchedulerService
                .createSchedulerFor(RuntimeEnvironment.application,
                        FrameworkJobSchedulerServiceImpl.class);
        assertThat(scheduler.serviceImpl == FrameworkJobSchedulerServiceImpl.class, is(true));
    }

    @Test(expected = IllegalArgumentException.class)
    public void createBadScheduler() {
        FrameworkJobSchedulerService.createSchedulerFor(RuntimeEnvironment.application,
                FrameworkJobSchedulerService.class);
    }

    @Test
    public void onCreate() {
        service.onCreate();
        verify(mockScheduler).setJobService(service);
    }

    @Test
    public void onCreateWithoutScheduler() {
        when(mockJobManager.getScheduler()).thenReturn(null);
        service.onCreate();
        // test sanity
        verify(mockScheduler, never()).setJobService(service);
    }

    @Test
    public void onDestroy() {
        service.onDestroy();
        verify(mockScheduler).setJobService(null);
    }

    @Test
    public void onDestroyWithoutScheduler() {
        when(mockJobManager.getScheduler()).thenReturn(null);
        service.onDestroy();
        // test sanity
        verify(mockScheduler, never()).setJobService(null);
    }

    @Test
    public void onStartJob() {
        JobParameters params = mock(JobParameters.class);
        service.onStartJob(params);
        verify(mockScheduler).onStartJob(params);
    }

    @Test
    public void onStartJobWithoutScheduler() {
        when(mockJobManager.getScheduler()).thenReturn(null);
        JobParameters params = mock(JobParameters.class);
        service.onStartJob(params);
        // test sanity
        verify(mockScheduler, never()).onStartJob(params);
    }

    @Test
    public void onStopJob() {
        JobParameters params = mock(JobParameters.class);
        service.onStopJob(params);
        verify(mockScheduler).onStopJob(params);
    }

    @Test
    public void onStopJobWithoutScheduler() {
        when(mockJobManager.getScheduler()).thenReturn(null);
        JobParameters params = mock(JobParameters.class);
        service.onStopJob(params);
        // test sanity
        verify(mockScheduler, never()).onStopJob(params);
    }

    public class FrameworkJobSchedulerServiceImpl extends FrameworkJobSchedulerService {

        @NonNull
        @Override
        protected JobManager getJobManager() {
            return mockJobManager;
        }
    }
}
