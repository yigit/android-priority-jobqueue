package com.birbit.android.jobqueue.scheduling;

import android.annotation.TargetApi;
import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.birbit.android.jobqueue.JobManager;
import com.birbit.android.jobqueue.log.JqLog;

/**
 * The service implementation for the framework job scheduler
 */
@TargetApi(21)
abstract public class FrameworkJobSchedulerService extends JobService {
    /**
     * Creates a scheduler for the given service.
     * Keep in mind that there is a strict 1-1 mapping between the created scheduler and the
     * service. You should pass the returned scheduler to the JobManager configuration.
     *
     * @param appContext The application context
     * @param klass The service implementation that extends FrameworkJobSchedulerService.
     *
     * @return A scheduler that is associated with the given service class.
     */
    @SuppressWarnings("unused")
    public static FrameworkScheduler createSchedulerFor(
            @SuppressWarnings("UnusedParameters") Context appContext,
            Class<? extends FrameworkJobSchedulerService> klass) {
        if (FrameworkJobSchedulerService.class == klass) {
            throw new IllegalArgumentException("You must create a service that extends" +
                    " FrameworkJobSchedulerService");
        }
        return new FrameworkScheduler(klass);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        FrameworkScheduler scheduler = getScheduler();
        if (scheduler != null) {
            scheduler.setJobService(this);
        } else {
            JqLog.e("FrameworkJobSchedulerService has been created but it does not have a" +
                    " scheduler. You must initialize JobManager before the service is created.");
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        FrameworkScheduler scheduler = getScheduler();
        if (scheduler != null) {
            scheduler.setJobService(null);
        } else {
            JqLog.e("FrameworkJobSchedulerService is being destroyed but it does not have a " +
                    "scheduler :/. You must initialize JobManager before the service is created.");
        }
    }

    @Override
    public boolean onStartJob(JobParameters params) {
        FrameworkScheduler scheduler = getScheduler();
        if (scheduler != null) {
            return scheduler.onStartJob(params);
        }
        JqLog.e("FrameworkJobSchedulerService has been triggered but it does not have a" +
                " scheduler. You must initialize JobManager before the service is created.");
        return false;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        FrameworkScheduler scheduler = getScheduler();
        if (scheduler != null) {
            return scheduler.onStopJob(params);
        }
        JqLog.e("FrameworkJobSchedulerService has been stopped but it does not have a" +
                " scheduler. You must initialize JobManager before the service is created.");
        return false;
    }

    @Nullable
    private FrameworkScheduler getScheduler() {
        Scheduler scheduler = getJobManager().getScheduler();
        if (scheduler instanceof FrameworkScheduler) {
            return (FrameworkScheduler) scheduler;
        }
        JqLog.e("FrameworkJobSchedulerService has been created but the JobManager does not" +
                " have a scheduler created by FrameworkJobSchedulerService.");
        return null;
    }

    /**
     * Return the JobManager that is associated with this service
     *
     * @return The JobManager that is associated with this service
     */
    @NonNull
    protected abstract JobManager getJobManager();
}
