package com.tarkalabs.android.jobqueue.test.jobs;

import com.tarkalabs.android.jobqueue.CancelReason;
import com.tarkalabs.android.jobqueue.Job;
import com.tarkalabs.android.jobqueue.Params;
import com.tarkalabs.android.jobqueue.RetryConstraint;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

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
    protected void onCancel(@CancelReason int cancelReason, @Nullable Throwable throwable) {
        onCancelCnt++;
    }

    @Override
    protected RetryConstraint shouldReRunOnThrowable(@NonNull Throwable throwable, int runCount, int maxRunCount) {
        shouldReRunOnThrowableCnt++;
        return RetryConstraint.CANCEL;
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
