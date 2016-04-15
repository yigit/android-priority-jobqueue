package com.birbit.android.jobqueue.examples.twitter;

import android.app.Application;
import android.os.Build;
import android.util.Log;

import com.birbit.android.jobqueue.examples.twitter.services.MyGcmJobService;
import com.birbit.android.jobqueue.scheduling.FrameworkJobSchedulerService;
import com.birbit.android.jobqueue.scheduling.FrameworkScheduler;
import com.birbit.android.jobqueue.JobManager;
import com.birbit.android.jobqueue.config.Configuration;
import com.birbit.android.jobqueue.examples.twitter.services.MyJobService;
import com.birbit.android.jobqueue.log.CustomLogger;
import com.birbit.android.jobqueue.scheduling.GcmJobSchedulerService;
import com.birbit.android.jobqueue.scheduling.GcmScheduler;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;


public class TwitterApplication extends Application {
    private static TwitterApplication instance;
    private JobManager jobManager;

    public TwitterApplication() {
        instance = this;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        configureJobManager();
    }

    private void configureJobManager() {
        Configuration.Builder builder = new Configuration.Builder(this)
        .customLogger(new CustomLogger() {
            private static final String TAG = "JOBS";
            @Override
            public boolean isDebugEnabled() {
                return true;
            }

            @Override
            public void d(String text, Object... args) {
                Log.d(TAG, String.format(text, args));
            }

            @Override
            public void e(Throwable t, String text, Object... args) {
                Log.e(TAG, String.format(text, args), t);
            }

            @Override
            public void e(String text, Object... args) {
                Log.e(TAG, String.format(text, args));
            }

            @Override
            public void v(String text, Object... args) {

            }
        })
        .minConsumerCount(1)//always keep at least one consumer alive
        .maxConsumerCount(3)//up to 3 consumers at a time
        .loadFactor(3)//3 jobs per consumer
        .consumerKeepAlive(120);//wait 2 minute
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            builder.scheduler(FrameworkJobSchedulerService.createSchedulerFor(this,
                    MyJobService.class), false);
        } else {
            int enableGcm = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(this);
            if (enableGcm == ConnectionResult.SUCCESS) {
                builder.scheduler(GcmJobSchedulerService.createSchedulerFor(this,
                        MyGcmJobService.class), false);
            }
        }
        jobManager = new JobManager(builder.build());
    }

    public JobManager getJobManager() {
        return jobManager;
    }

    public static TwitterApplication getInstance() {
        return instance;
    }
}
