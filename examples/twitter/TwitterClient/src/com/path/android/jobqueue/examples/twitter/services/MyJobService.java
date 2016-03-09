package com.path.android.jobqueue.examples.twitter.services;

import com.birbit.android.jobqueue.scheduling.FrameworkJobSchedulerService;
import com.birbit.android.jobqueue.scheduling.FrameworkScheduler;
import com.path.android.jobqueue.examples.twitter.TwitterApplication;

public class MyJobService extends FrameworkJobSchedulerService {
    @Override
    public FrameworkScheduler getScheduler() {
        return TwitterApplication.getInstance().getFrameworkScheduler();
    }
}
