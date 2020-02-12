package com.birbit.android.jobqueue.testing;

import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

public class StackTraceRule extends TestWatcher {

    @Override
    protected void failed(Throwable e, Description description) {
        System.out.println("stack trace detected failed test");
        super.failed(e, description);
    }
}
