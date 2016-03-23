package com.birbit.android.jobqueue.scheduling;

import com.google.android.gms.gcm.GcmTaskService;
import com.google.android.gms.gcm.TaskParams;

abstract public class GcmJobSchedulerService extends GcmTaskService {

    @Override
    public int onRunTask(TaskParams taskParams) {
        return getScheduler().onStartJob(taskParams);
    }

    abstract public GcmScheduler getScheduler();

}
