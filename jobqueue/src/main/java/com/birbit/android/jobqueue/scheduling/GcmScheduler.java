package com.birbit.android.jobqueue.scheduling;

import android.content.Context;
import android.os.Bundle;

import com.birbit.android.jobqueue.BatchingScheduler;
import com.birbit.android.jobqueue.log.JqLog;
import com.birbit.android.jobqueue.network.NetworkUtil;
import com.google.android.gms.gcm.GcmNetworkManager;
import com.google.android.gms.gcm.OneoffTask;
import com.google.android.gms.gcm.Task;
import com.google.android.gms.gcm.TaskParams;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

class GcmScheduler extends Scheduler {
    private static final String KEY_UUID = "uuid";
    private static final String KEY_DELAY = "delay";
    private static final String KEY_OVERRIDE_DEADLINE = "deadline";
    private static final String KEY_NETWORK_STATUS = "networkStatus";
    GcmNetworkManager gcmNetworkManager;
    final Class<? extends GcmJobSchedulerService> serviceClass;

    GcmScheduler(Context context, Class<? extends GcmJobSchedulerService> serviceClass) {
        this.serviceClass = serviceClass;
        gcmNetworkManager = GcmNetworkManager.getInstance(context.getApplicationContext());
    }

    @Override
    public void request(SchedulerConstraint constraint) {
        if (JqLog.isDebugEnabled()) {
            JqLog.d("creating gcm wake up request for %s", constraint);
        }
        OneoffTask.Builder builder = new OneoffTask.Builder()
                .setRequiredNetwork(toNetworkState(constraint.getNetworkStatus()))
                .setPersisted(true)
                .setService(serviceClass)
                .setTag(constraint.getUuid())
                .setExtras(toBundle(constraint));
        long endTimeMs = constraint.getOverrideDeadlineInMs() == null
                ? constraint.getDelayInMs() + TimeUnit.SECONDS.toMillis(getExecutionWindowSizeInSeconds())
                : constraint.getOverrideDeadlineInMs();
        long executionStart = TimeUnit.MILLISECONDS.toSeconds(constraint.getDelayInMs());
        long executionEnd = TimeUnit.MILLISECONDS.toSeconds(endTimeMs);
        // JobManager uses MS vs GCM uses seconds so we must check to avoid illegal arg exceptions
        if (executionEnd <= executionStart) {
            executionEnd = executionStart + 1;
        }
        builder.setExecutionWindow(executionStart, executionEnd);

        gcmNetworkManager.schedule(builder.build());
    }

    /**
     * GCMNetworkManager accepts an execution window for jobs so that it can batch them together for
     * better battery utilization. You can override this method to provide a different execution
     * window. The default value is {@link BatchingScheduler#DEFAULT_BATCHING_PERIOD_IN_MS} (converted
     * to seconds).
     * <p>
     * If this scheduling request is made for a Job with a deadline, this method is NOT called.
     *
     * @return The execution window time for the Job request
     */
    long getExecutionWindowSizeInSeconds() {
        // let jobs timeout in a week
        return TimeUnit.DAYS.toSeconds(7);
    }

    @Override
    public void cancelAll() {
        gcmNetworkManager.cancelAllTasks(serviceClass);
    }

    private static int toNetworkState(@NetworkUtil.NetworkStatus int networkStatus) {
        switch (networkStatus) {
            case NetworkUtil.DISCONNECTED:
                return Task.NETWORK_STATE_ANY;
            case NetworkUtil.METERED:
                return Task.NETWORK_STATE_CONNECTED;
            case NetworkUtil.UNMETERED:
                return Task.NETWORK_STATE_UNMETERED;
        }
        JqLog.e("unknown network status %d. Defaulting to CONNECTED", networkStatus);
        return Task.NETWORK_STATE_CONNECTED;
    }

    static Bundle toBundle(SchedulerConstraint constraint) {
        Bundle bundle = new Bundle();
        // put boolean is api 22
        if (constraint.getUuid() != null) {
            // gcm throws an exception if this is null
            bundle.putString(KEY_UUID, constraint.getUuid());
        }
        bundle.putInt(KEY_NETWORK_STATUS, constraint.getNetworkStatus());
        bundle.putLong(KEY_DELAY, constraint.getDelayInMs());
        if (constraint.getOverrideDeadlineInMs() != null) {
            bundle.putLong(KEY_OVERRIDE_DEADLINE, constraint.getOverrideDeadlineInMs());
        }
        return bundle;
    }

    static SchedulerConstraint fromBundle(Bundle bundle) throws Exception {
        SchedulerConstraint constraint = new SchedulerConstraint(bundle.getString(KEY_UUID));
        if (constraint.getUuid() == null) {
            // backward compatibility
            constraint.setUuid(UUID.randomUUID().toString());
        }
        constraint.setNetworkStatus(bundle.getInt(KEY_NETWORK_STATUS, NetworkUtil.DISCONNECTED));
        constraint.setDelayInMs(bundle.getLong(KEY_DELAY, 0));
        if (bundle.containsKey(KEY_OVERRIDE_DEADLINE)) {
            constraint.setOverrideDeadlineInMs(bundle.getLong(KEY_OVERRIDE_DEADLINE));
        }
        return constraint;
    }

    int onStartJob(TaskParams taskParams) {
        SchedulerConstraint constraint = null;
        try {
            constraint = fromBundle(taskParams.getExtras());
        } catch (Exception e) {
            JqLog.e(e, "bad bundle from GcmScheduler. Ignoring the call");
            return GcmNetworkManager.RESULT_SUCCESS;
        }
        if (JqLog.isDebugEnabled()) {
            JqLog.d("starting job %s", constraint);
        }

        ResultCallback callback = new ResultCallback();
        constraint.setData(callback);
        start(constraint);
        return callback.get() ? GcmNetworkManager.RESULT_RESCHEDULE : GcmNetworkManager.RESULT_SUCCESS;
    }

    @Override
    public void onFinished(SchedulerConstraint constraint, boolean reschedule) {
        Object data = constraint.getData();
        if (JqLog.isDebugEnabled()) {
            JqLog.d("finished job %s", constraint);
        }
        if (data instanceof ResultCallback) {
            ResultCallback callback = (ResultCallback) data;
            callback.onDone(reschedule);
        }
    }

    private static class ResultCallback {
        volatile boolean reschedule;
        CountDownLatch latch;

        ResultCallback() {
            latch = new CountDownLatch(1);
            reschedule = false;
        }

        public boolean get() {
            try {
                latch.await(10 * 60, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                JqLog.e("job did not finish in 10 minutes :/");
            }
            return reschedule;
        }

        void onDone(boolean reschedule) {
            this.reschedule = reschedule;
            latch.countDown();
        }
    }
}
