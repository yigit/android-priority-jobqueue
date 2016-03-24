package com.birbit.android.jobqueue.scheduling;

import android.content.Context;

import com.birbit.android.jobqueue.log.JqLog;
import com.google.android.gms.gcm.GcmNetworkManager;
import com.google.android.gms.gcm.GcmTaskService;
import com.google.android.gms.gcm.TaskParams;

import java.util.HashMap;
import java.util.Map;

abstract public class GcmJobSchedulerService extends GcmTaskService {
    private static final Map<Class<? extends GcmJobSchedulerService>, GcmScheduler>
            schedulerMap = new HashMap<>();

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
    public static GcmScheduler createSchedulerFor(Context appContext,
            Class<? extends GcmJobSchedulerService> klass) {
        if (GcmJobSchedulerService.class == klass) {
            throw new IllegalArgumentException("You must create a service that extends" +
                    " GcmJobSchedulerService");
        }
        synchronized (schedulerMap) {
            if (schedulerMap.get(klass) != null) {
                throw new IllegalStateException("You can create 1 scheduler per" +
                        " GcmJobSchedulerService. " + klass.getCanonicalName() +
                        " already has one.");
            }
            GcmScheduler scheduler = new GcmScheduler(appContext.getApplicationContext(), klass);
            schedulerMap.put(klass, scheduler);
            return scheduler;
        }
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

    protected GcmScheduler getScheduler() {
        return schedulerMap.get(getClass());
    }

}
