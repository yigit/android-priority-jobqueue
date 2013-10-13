package com.path.android.jobqueue.test.jobs;


public class PersistentDummyJob extends DummyJob {
    public PersistentDummyJob() {
        super(false, true);
    }

    public PersistentDummyJob(boolean requiresNetwork) {
        super(requiresNetwork, true);
    }

    public PersistentDummyJob(String groupId) {
        super(groupId);
    }

}
