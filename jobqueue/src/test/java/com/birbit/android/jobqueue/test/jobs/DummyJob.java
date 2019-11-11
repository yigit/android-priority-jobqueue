package com.birbit.android.jobqueue.test.jobs;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.birbit.android.jobqueue.CancelReason;
import com.birbit.android.jobqueue.Job;
import com.birbit.android.jobqueue.Params;
import com.birbit.android.jobqueue.RetryConstraint;

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
