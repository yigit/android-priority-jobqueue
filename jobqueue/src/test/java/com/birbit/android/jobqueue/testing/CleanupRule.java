package com.birbit.android.jobqueue.testing;

import com.path.android.jobqueue.JobManager;
import com.path.android.jobqueue.test.jobmanager.JobManagerTestBase;

import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import org.robolectric.Robolectric;

import java.util.Map;

public class CleanupRule extends TestWatcher {
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
    protected void failed(Throwable e, Description description) {
        Map<Thread, StackTraceElement[]> stackTraces = Thread.getAllStackTraces();
        // add stack traces to the errors
        StringBuilder sb = new StringBuilder("Thread dump:");
        for (Map.Entry<Thread, StackTraceElement[]> entry : stackTraces.entrySet()) {
            sb.append(entry.getKey().getName()).append("\n");
            for (StackTraceElement element : entry.getValue()) {
                sb.append("  ").append(element.getClassName())
                .append("#").append(element.getMethodName())
                .append("#").append(element.getLineNumber())
                .append("\n");
            }
            sb.append("\n----------\n");
        }
        throw new AssertionError(sb.toString());
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
