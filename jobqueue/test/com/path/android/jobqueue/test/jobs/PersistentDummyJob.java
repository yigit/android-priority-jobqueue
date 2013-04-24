package com.path.android.jobqueue.test.jobs;


public class PersistentDummyJob extends DummyJob {
    @Override
    public boolean shouldPersist() {
        return true;
    }
}
