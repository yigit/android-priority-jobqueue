package com.birbit.android.jobqueue.testing;

import org.junit.Rule;
import org.junit.rules.TestRule;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.lang.annotation.Annotation;
import java.util.Map;

public class StackTraceRule extends TestWatcher {

    @Override
    protected void failed(Throwable e, Description description) {
        System.out.println("stack trace detected failed test");
        super.failed(e, description);
    }
}
