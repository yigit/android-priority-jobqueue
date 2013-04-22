package com.path.android.jobqueue.jobs;

import com.path.android.jobqueue.BaseJob;

public class DummyJob extends BaseJob {
    @Override
    public void onAdded() {

    }

    @Override
    public void onRun() throws Throwable {

    }

    @Override
    public boolean shouldPersist() {
        return false;
    }

    @Override
    protected void onCancel() {

    }

    @Override
    protected boolean shouldReRunOnThrowable(Throwable throwable) {
        return false;
    }
}
