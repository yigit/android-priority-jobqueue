package com.birbit.android.jobqueue.scheduling;

import android.annotation.TargetApi;
import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.Context;

import com.birbit.android.jobqueue.log.JqLog;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The service implementation for the framework job scheduler
 */
@TargetApi(21)
abstract public class FrameworkJobSchedulerService extends JobService {
    private static final Map<Class<? extends FrameworkJobSchedulerService>, FrameworkScheduler>
            schedulerMap = new HashMap<>();

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
    public static FrameworkScheduler createSchedulerFor(
            @SuppressWarnings("UnusedParameters") Context appContext,
            Class<? extends FrameworkJobSchedulerService> klass) {
        if (FrameworkJobSchedulerService.class == klass) {
            throw new IllegalArgumentException("You must create a service that extends" +
                    " FrameworkJobSchedulerService");
        }
        synchronized (schedulerMap) {
            if (schedulerMap.get(klass) != null) {
                throw new IllegalStateException("You can create 1 scheduler per" +
                        " FrameworkJobService. " + klass.getCanonicalName() + " already has one.");
            }
            FrameworkScheduler scheduler = new FrameworkScheduler(klass);
            schedulerMap.put(klass, scheduler);
            return scheduler;
        }
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
        return getScheduler().onStartJob(params);
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return getScheduler().onStopJob(params);
    }

    protected FrameworkScheduler getScheduler() {
        return schedulerMap.get(getClass());
    }
}
