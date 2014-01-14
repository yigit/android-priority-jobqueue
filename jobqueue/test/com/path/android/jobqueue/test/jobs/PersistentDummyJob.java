package com.path.android.jobqueue.test.jobs;


import com.path.android.jobqueue.Params;

public class PersistentDummyJob extends DummyJob {
    public PersistentDummyJob(Params params) {
        super(params.persist());
    }
}
