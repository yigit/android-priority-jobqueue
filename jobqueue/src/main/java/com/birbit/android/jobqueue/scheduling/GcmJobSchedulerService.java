package com.birbit.android.jobqueue.scheduling;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.birbit.android.jobqueue.JobManager;
import com.birbit.android.jobqueue.log.JqLog;
import com.google.android.gms.gcm.GcmNetworkManager;
import com.google.android.gms.gcm.GcmTaskService;
import com.google.android.gms.gcm.TaskParams;

abstract public class GcmJobSchedulerService extends GcmTaskService {
    /**
     * Creates a scheduler for the given service.
     * Keep in mind that there is a strict 1-1 mapping between the created scheduler and the
     * service. You should pass the returned scheduler to the JobManager configuration.
     *
     * @param appContext The application context
     * @param klass The service implementation that extends GcmJobSchedulerService.
     *
     * @return A scheduler that is associated with the given service class.
     */
    @SuppressWarnings("unused")
    public static GcmScheduler createSchedulerFor(Context appContext,
                                                  Class<? extends GcmJobSchedulerService> klass) {
        if (GcmJobSchedulerService.class == klass) {
            throw new IllegalArgumentException("You must create a service that extends" +
                    " GcmJobSchedulerService");
        }
        return new GcmScheduler(appContext.getApplicationContext(), klass);
    }

    @Override
    public int onRunTask(TaskParams taskParams) {
        GcmScheduler scheduler = getScheduler();
        if (scheduler != null) {
            return scheduler.onStartJob(taskParams);
        } else {
            JqLog.e("RunTask on GcmJobSchedulerService has been called but it does not have a " +
                    "scheduler. Make sure you've initialized JobManager before the service might" +
                    " be created.");
            return GcmNetworkManager.RESULT_FAILURE;
        }

    }

    @Nullable
    protected GcmScheduler getScheduler() {
        Scheduler scheduler = getJobManager().getScheduler();
        if (scheduler instanceof GcmScheduler) {
            return (GcmScheduler) scheduler;
        }
        JqLog.e("GcmJobSchedulerService has been created but the JobManager does not" +
                " have a scheduler created by GcmJobSchedulerService.");
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
