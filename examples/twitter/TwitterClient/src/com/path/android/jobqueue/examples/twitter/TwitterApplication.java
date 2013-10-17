package com.path.android.jobqueue.examples.twitter;

import android.app.Application;
import android.util.Log;
import com.path.android.jobqueue.JobManager;
import com.path.android.jobqueue.config.Configuration;
import com.path.android.jobqueue.examples.twitter.util.MyNetworkUtil;
import com.path.android.jobqueue.log.CustomLogger;

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
        Configuration configuration = JobManager.createDefaultConfiguration();
        configuration.customLogger(new CustomLogger() {
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
        });

        configuration.minConsumerCount(1);//always keep at least one consumer alive
        configuration.maxConsumerCount(3);//up to 3 consumers at a time
        configuration.loadFactor(3);//3 jobs per consumer
        configuration.consumerKeepAlive(30);//wait 30 secs before killing a consumer
        configuration.networkUtil(new MyNetworkUtil(this));
        jobManager = new JobManager(this, configuration);

    }

    public JobManager getJobManager() {
        return jobManager;
    }

    public static TwitterApplication getInstance() {
        return instance;
    }
}
