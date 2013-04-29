package com.path.android.jobqueue.test.jobs;


public class PersistentDummyJob extends DummyJob {
    public PersistentDummyJob() {
    }

    public PersistentDummyJob(boolean requiresNetwork) {
        super(requiresNetwork);
    }

    @Override
    public boolean shouldPersist() {
        return true;
    }
}
