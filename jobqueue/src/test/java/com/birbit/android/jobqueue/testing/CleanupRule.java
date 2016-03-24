package com.birbit.android.jobqueue.testing;

import com.birbit.android.jobqueue.JobManager;
import com.birbit.android.jobqueue.test.jobmanager.JobManagerTestBase;

import org.junit.runner.Description;

public class CleanupRule extends ThreadDumpRule {
    final JobManagerTestBase test;

    public CleanupRule(JobManagerTestBase test) {
        this.test = test;
    }

    @Override
    protected void starting(Description description) {
        super.starting(description);
        System.out.println("started test " + getId(description));
    }

    @Override
    protected void finished(Description description) {
        String id = getId(description);
        System.out.println("tear down " + id);
        for (JobManager jobManager : test.getCreatedJobManagers()) {
            JobManagerTestBase.NeverEndingDummyJob.unlockAll();
            jobManager.destroy();
        }
        System.out.println("finished tear down of " + id);
    }

    private String getId(Description description) {
        return description.getMethodName() + "/" + description.getClassName();
    }
}
