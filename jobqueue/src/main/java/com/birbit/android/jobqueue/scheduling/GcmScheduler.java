package com.birbit.android.jobqueue.scheduling;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;

import com.birbit.android.jobqueue.BatchingScheduler;
import com.birbit.android.jobqueue.log.JqLog;
import com.birbit.android.jobqueue.network.NetworkUtil;
import com.google.android.gms.gcm.GcmNetworkManager;
import com.google.android.gms.gcm.GcmTaskService;
import com.google.android.gms.gcm.OneoffTask;
import com.google.android.gms.gcm.Task;
import com.google.android.gms.gcm.TaskParams;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class GcmScheduler extends Scheduler {
    private static final String KEY_UUID = "uuid";
    private static final String KEY_ID = "id";
    private static final String KEY_DELAY = "delay";
    private static final String KEY_NETWORK_STATUS = "networkStatus";
    private static SharedPreferences preferences;
    private final GcmNetworkManager gcmNetworkManager;
    private final Class<? extends GcmJobSchedulerService> serviceClass;

    public GcmScheduler(Context context, Class<? extends GcmJobSchedulerService> serviceClass) {
        this.serviceClass = serviceClass;
        gcmNetworkManager = GcmNetworkManager.getInstance(context.getApplicationContext());
    }

    private static SharedPreferences getPreferences(Context context) {
        synchronized (GcmScheduler.class) {
            if (preferences == null) {
                preferences = context.getSharedPreferences("jobqueue_gcm_scheduler",
                        Context.MODE_PRIVATE);
            }
            return preferences;
        }
    }

    /**
     * Creates a new ID for the job info. Can be overridden if you need to provide different ids not
     * to conflict with the rest of your application.
     *
     * @return A unique integer id for the next Job request to be sent to system scheduler
     */
    @SuppressLint("CommitPrefEdits")
    public int createId() {
        synchronized (GcmScheduler.class) {
            final SharedPreferences preferences = getPreferences(getApplicationContext());
            final int id = preferences.getInt(KEY_ID, 0) + 1;
            preferences.edit().putInt(KEY_ID, id).commit();
            return id;
        }
    }

    @Override
    public void request(SchedulerConstraint constraint) {
        if (JqLog.isDebugEnabled()) {
            JqLog.d("creating gcm wake up request for %s", constraint);
        }
        OneoffTask oneoffTask = new OneoffTask.Builder()
                .setExecutionWindow(constraint.getDelayInMs(), constraint.getDelayInMs()
                        + getExecutionWindowSizeInSeconds())
                .setRequiredNetwork(toNetworkState(constraint.getNetworkStatus()))
                .setPersisted(true)
                .setService(serviceClass)
                .setTag("jobmanager-" + createId())
                .setExtras(toBundle(constraint))
                .build();
        gcmNetworkManager.schedule(oneoffTask);
    }

    /**
     * GCMNetworkManager accepts an execution window for jobs so that it can batch them together for
     * better battery utilization. You can override this method to provide a different execution
     * window. The default value is {@link BatchingScheduler#DEFAULT_BATCHING_PERIOD_IN_MS} (converted
     * to seconds).
     *
     * @return The execution window time for the Job request
     */
    public long getExecutionWindowSizeInSeconds() {
        return TimeUnit.MILLISECONDS.toSeconds(BatchingScheduler.DEFAULT_BATCHING_PERIOD_IN_MS);
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

    private static Bundle toBundle(SchedulerConstraint constraint) {
        Bundle bundle = new Bundle();
        // put boolean is api 22
        bundle.putString(KEY_UUID, constraint.getUuid());
        bundle.putInt(KEY_NETWORK_STATUS, constraint.getNetworkStatus());
        bundle.putLong(KEY_DELAY, constraint.getDelayInMs());
        return bundle;
    }

    private static SchedulerConstraint fromBundle(Bundle bundle) {
        SchedulerConstraint constraint = new SchedulerConstraint(bundle.getString(KEY_UUID));
        if (constraint.getUuid() == null) {
            // backward compatibility
            constraint.setUuid(UUID.randomUUID().toString());
        }
        constraint.setNetworkStatus(bundle.getInt(KEY_NETWORK_STATUS, NetworkUtil.DISCONNECTED));
        constraint.setDelayInMs(bundle.getLong(KEY_DELAY, 0));
        return constraint;
    }

    public int onStartJob(TaskParams taskParams) {
        SchedulerConstraint constraint = fromBundle(taskParams.getExtras());
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

        public ResultCallback() {
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

        public void onDone(boolean reschedule) {
            this.reschedule = reschedule;
            latch.countDown();
        }
    }
}
