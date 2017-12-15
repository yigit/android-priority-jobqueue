package com.birbit.android.jobqueue.scheduling;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.PersistableBundle;
import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;

import com.birbit.android.jobqueue.Params;
import com.birbit.android.jobqueue.log.JqLog;
import com.birbit.android.jobqueue.network.NetworkUtil;

import java.util.UUID;

/**
 * Scheduler implementation that uses the frameworks' scheduler API.
 */
@TargetApi(21)
public class FrameworkScheduler extends Scheduler {
    private static final String KEY_UUID = "uuid";
    @VisibleForTesting
    static final String KEY_ID = "id";
    private static final String KEY_DELAY = "delay";
    private static final String KEY_NETWORK_STATUS = "networkStatus";
    private static final String KEY_DEADLINE = "keyDeadline";

    private JobScheduler jobScheduler;
    private SharedPreferences preferences;
    private ComponentName componentName;
    // set when service invokes, cleared when service dies
    @Nullable private JobService jobService;
    final Class<? extends FrameworkJobSchedulerService> serviceImpl;

    FrameworkScheduler(Class<? extends FrameworkJobSchedulerService> serviceImpl) {
        this.serviceImpl = serviceImpl;
    }

    void setJobService(@Nullable JobService jobService) {
        this.jobService = jobService;
    }

    private SharedPreferences getPreferences(Context context) {
        synchronized (FrameworkScheduler.class) {
            if (preferences == null) {
                preferences = context.getSharedPreferences("jobqueue_fw_scheduler",
                        Context.MODE_PRIVATE);
            }
            return preferences;
        }
    }

    @VisibleForTesting
    ComponentName getComponentName() {
        if (componentName == null) {
            componentName = new ComponentName(getApplicationContext().getPackageName(),
                    serviceImpl.getCanonicalName());
        }
        return componentName;
    }

    /**
     * Creates a new ID for the job info. Can be overridden if you need to provide different ids not
     * to conflict with the rest of your application.
     *
     * @return A unique integer id for the next Job request to be sent to system scheduler
     */
    @SuppressLint("CommitPrefEdits")
    @VisibleForTesting
    int createId() {
        synchronized (FrameworkScheduler.class) {
            final SharedPreferences preferences = getPreferences(getApplicationContext());
            final int id = preferences.getInt(KEY_ID, 0) + 1;
            preferences.edit().putInt(KEY_ID, id).commit();
            return id;
        }
    }

    @VisibleForTesting
    JobScheduler getJobScheduler() {
        if (jobScheduler == null) {
            jobScheduler = (JobScheduler) getApplicationContext()
                    .getSystemService(Context.JOB_SCHEDULER_SERVICE);
        }
        return jobScheduler;
    }

    @SuppressLint("SwitchIntDef")
    @Override
    public void request(SchedulerConstraint constraint) {
        JobScheduler jobScheduler = getJobScheduler();

        final int id = createId();
        JobInfo.Builder builder = new JobInfo.Builder(id, getComponentName())
                .setExtras(toPersistentBundle(constraint))
                .setPersisted(true);
        switch (constraint.getNetworkStatus()) {
            case NetworkUtil.UNMETERED:
                builder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED);
                break;
            case NetworkUtil.METERED:
                builder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY);
                break;
            default:
                builder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_NONE);
                builder.setRequiresDeviceIdle(true);
                break;
        }
        if (constraint.getDelayInMs() > 0) {
            builder.setMinimumLatency(constraint.getDelayInMs());
        }
        if (constraint.getOverrideDeadlineInMs() != null) {
            builder.setOverrideDeadline(constraint.getOverrideDeadlineInMs());
        }
        int scheduled = jobScheduler.schedule(builder.build());
        JqLog.d("[FW Scheduler] scheduled a framework job. Success? %s id: %d" +
                " created id: %d", scheduled > 0, scheduled, id);
    }

    @Override
    public void onFinished(SchedulerConstraint constraint, boolean reschedule) {
        JqLog.d("[FW Scheduler] on finished job %s. reschedule:%s", constraint, reschedule);
        JobService service = this.jobService;
        if (service == null) {
            JqLog.e("[FW Scheduler] scheduler onFinished is called but i don't have a job service");
            return;
        }

        Object data = constraint.getData();
        if (data instanceof JobParameters) {
            JobParameters params = (JobParameters) data;
            service.jobFinished(params, reschedule);
        } else {
            JqLog.e("[FW Scheduler] cannot obtain the job parameters");
        }

    }

    @Override
    public void cancelAll() {
        JqLog.d("[FW Scheduler] cancel all");
        getJobScheduler().cancelAll();
    }

    @VisibleForTesting
    static PersistableBundle toPersistentBundle(SchedulerConstraint constraint) {
        PersistableBundle bundle = new PersistableBundle();
        // put boolean is api 22
        bundle.putString(KEY_UUID, constraint.getUuid());
        bundle.putInt(KEY_NETWORK_STATUS, constraint.getNetworkStatus());
        bundle.putLong(KEY_DELAY, constraint.getDelayInMs());
        Long deadline = constraint.getOverrideDeadlineInMs();
        if (deadline != null) {
            bundle.putLong(KEY_DEADLINE, deadline);
        }
        return bundle;
    }

    @VisibleForTesting
    static SchedulerConstraint fromBundle(PersistableBundle bundle) throws Exception {
        SchedulerConstraint constraint = new SchedulerConstraint(bundle.getString(KEY_UUID));
        if (constraint.getUuid() == null) {
            // backward compatibility
            constraint.setUuid(UUID.randomUUID().toString());
        }
        constraint.setNetworkStatus(bundle.getInt(KEY_NETWORK_STATUS, NetworkUtil.DISCONNECTED));
        constraint.setDelayInMs(bundle.getLong(KEY_DELAY, 0));
        if (bundle.containsKey(KEY_DEADLINE)) {
            constraint.setOverrideDeadlineInMs(bundle.getLong(KEY_DEADLINE, Params.FOREVER));
        }

        return constraint;
    }

    boolean onStartJob(JobParameters params) {
        SchedulerConstraint constraint;
        try {
            constraint = fromBundle(params.getExtras());
        } catch (Exception e) {
            JqLog.e(e, "bad bundle from framework job scheduler start callback.");
            return false;
        }
        JqLog.d("[FW Scheduler] start job %s %d", constraint, params.getJobId());
        constraint.setData(params);
        return start(constraint);
    }

    boolean onStopJob(JobParameters params) {
        SchedulerConstraint constraint;
        try {
            constraint = fromBundle(params.getExtras());
        } catch (Exception e) {
            JqLog.e(e, "bad bundle from job scheduler stop callback");
            return false;
        }
        return stop(constraint);
    }
}
