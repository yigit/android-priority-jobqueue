package com.birbit.android.jobqueue.scheduling;

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

import com.path.android.jobqueue.Job;
import com.path.android.jobqueue.log.JqLog;

/**
 * Scheduler implementation that uses the frameworks' scheduler API.
 */
@TargetApi(21)
public class FrameworkScheduler extends Scheduler {
    private static final String KEY_ID = "id";
    private static final String KEY_DELAY = "delay";
    private static final String KEY_REQUIRE_NETWORK = "requireNetwork";
    private static final String KEY_REQUIRE_UNMETERED_NETWORK = "requireUnmeteredNetwork";

    private JobScheduler jobScheduler;
    private SharedPreferences preferences;
    private ComponentName componentName;
    // set when service invokes, cleared when service dies
    @Nullable private JobService jobService;
    private final Class<? extends FrameworkJobSchedulerService> serviceImpl;

    public FrameworkScheduler(Class< ? extends FrameworkJobSchedulerService> serviceImpl) {
        this.serviceImpl = serviceImpl;
    }

    public void setJobService(@Nullable JobService jobService) {
        this.jobService = jobService;
    }

    private SharedPreferences getPreferences() {
        if (preferences == null) {
            preferences = getApplicationContext().getSharedPreferences("jobqueue_fw_scheduler",
                    Context.MODE_PRIVATE);
        }
        return preferences;
    }

    private ComponentName getComponentName() {
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
    public int createId() {
        final SharedPreferences preferences = getPreferences();
        final int id = preferences.getInt(KEY_ID, 0) + 1;
        preferences.edit().putInt(KEY_ID, id).commit();
        return id;
    }

    private JobScheduler getJobScheduler() {
        if (jobScheduler == null) {
            jobScheduler = (JobScheduler) getApplicationContext()
                    .getSystemService(Context.JOB_SCHEDULER_SERVICE);
        }
        return jobScheduler;
    }

    @Override
    public void request(SchedulerConstraint constraint) {
        JobScheduler jobScheduler = getJobScheduler();

        final int id = createId();
        JobInfo.Builder builder = new JobInfo.Builder(id, getComponentName())
                .setExtras(toPersistentBundle(constraint))
                .setPersisted(true);
        if (constraint.requireUnmeteredNetwork()) {
            builder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED);
        } else if (constraint.requireNetwork()) {
            builder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY);
        } else {
            builder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY);
            builder.setRequiresDeviceIdle(true);
        }
        int scheduled = jobScheduler.schedule(builder.build());
        JqLog.d("[FW Scheduler] scheduled a framework job. Success? %s id: %d" +
                " created id: %d", scheduled > 0, scheduled, id);
    }

    @Override
    public void onFinished(SchedulerConstraint constraint, boolean reschedule) {
        if (JqLog.isDebugEnabled()) {
            JqLog.d("[FW Scheduler] on finished job %s. reschedule:%s", constraint, reschedule);
        }
        JobService service = this.jobService;
        if (service == null) {
            JqLog.e("scheduler onfinished is called but i don't have a job service");
            return;
        }

        Object data = constraint.getData();
        if (data instanceof JobParameters) {
            JobParameters params = (JobParameters) data;
            service.jobFinished(params, reschedule);
        } else {
            JqLog.e("cannot obtain the job parameters");
        }

    }

    private static PersistableBundle toPersistentBundle(SchedulerConstraint constraint) {
        PersistableBundle bundle = new PersistableBundle();
        // put boolean is api 22
        bundle.putInt(KEY_REQUIRE_NETWORK, constraint.requireNetwork() ? 1 : 0);
        bundle.putInt(KEY_REQUIRE_UNMETERED_NETWORK, constraint.requireUnmeteredNetwork() ? 1 : 0);
        bundle.putLong(KEY_DELAY, constraint.getDelayInMs());
        return bundle;
    }

    private static SchedulerConstraint fromPersistentBundle(PersistableBundle bundle) {
        SchedulerConstraint constraint = new SchedulerConstraint();
        constraint.setRequireNetwork(bundle.getInt(KEY_REQUIRE_NETWORK, 0) == 1);
        constraint.setRequireUnmeteredNetwork(bundle.getInt(KEY_REQUIRE_UNMETERED_NETWORK, 0) == 1);
        constraint.setDelayInMs(bundle.getLong(KEY_DELAY, 0));
        return constraint;
    }

    public boolean onStartJob(JobParameters params) {
        SchedulerConstraint constraint = fromPersistentBundle(params.getExtras());
        if (JqLog.isDebugEnabled()) {
            JqLog.d("[FW Scheduler] start job %s %d", constraint, params.getJobId());
        }
        return start(constraint);
    }

    public boolean onStopJob(JobParameters params) {
        SchedulerConstraint constraint = fromPersistentBundle(params.getExtras());
        return stop(constraint);
    }
}
