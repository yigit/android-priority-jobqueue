package com.birbit.android.jobqueue.lint;

import com.android.tools.lint.client.api.IssueRegistry;
import com.android.tools.lint.detector.api.Issue;

import java.util.Arrays;
import java.util.List;

public class JobQueueDevelopmentLintRegistery extends IssueRegistry {
    @Override
    public List<Issue> getIssues() {
        return Arrays.asList(SystemTimeDetector.ISSUE, SleepDetector.ISSUE, WaitOnObjectWithTimeDetector.ISSUE,
                NotifyOnObjectDetector.ISSUE);
    }
}
