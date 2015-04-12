package com.path.android.jobqueue.test.jobs;

import com.path.android.jobqueue.Job;
import com.path.android.jobqueue.Params;

public class DummyJob extends Job {
    int onAddedCnt = 0;
    int onRunCnt = 0;
    int onCancelCnt = 0;
    int shouldReRunOnThrowableCnt = 0;

    public DummyJob(Params params) {
        super(params);
    }

    @Override
    public void onAdded() {
        onAddedCnt++;
    }

    @Override
    public void onRun() throws Throwable {
        onRunCnt++;
    }

    @Override
    protected void onCancel() {
        onCancelCnt++;
    }

    @Override
    protected boolean shouldReRunOnThrowable(Throwable throwable) {
        shouldReRunOnThrowableCnt++;
        return false;
    }

    public int getOnAddedCnt() {
        return onAddedCnt;
    }

    public int getOnRunCnt() {
        return onRunCnt;
    }

    public int getOnCancelCnt() {
        return onCancelCnt;
    }

    public int getShouldReRunOnThrowableCnt() {
        return shouldReRunOnThrowableCnt;
    }
}
