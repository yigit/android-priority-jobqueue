package com.path.android.jobqueue.examples.twitter;

import android.app.Application;
import android.os.Build;
import android.util.Log;

import com.birbit.android.jobqueue.JobManager2;
import com.birbit.android.jobqueue.scheduling.FrameworkScheduler;
import com.path.android.jobqueue.JobManager;
import com.path.android.jobqueue.config.Configuration;
import com.path.android.jobqueue.examples.twitter.services.MyJobService;
import com.path.android.jobqueue.log.CustomLogger;


public class TwitterApplication extends Application {
    private static TwitterApplication instance;
    private JobManager2 jobManager;
    private FrameworkScheduler frameworkScheduler;

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
        })
        .minConsumerCount(1)//always keep at least one consumer alive
        .maxConsumerCount(3)//up to 3 consumers at a time
        .loadFactor(3)//3 jobs per consumer
        .consumerKeepAlive(120);//wait 2 minute
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            frameworkScheduler = new FrameworkScheduler(MyJobService.class);
            builder.scheduler(frameworkScheduler);
        }
        jobManager = new JobManager2(builder.build());
    }

    public JobManager2 getJobManager() {
        return jobManager;
    }

    public FrameworkScheduler getFrameworkScheduler() {
        return frameworkScheduler;
    }

    public static TwitterApplication getInstance() {
        return instance;
    }
}
