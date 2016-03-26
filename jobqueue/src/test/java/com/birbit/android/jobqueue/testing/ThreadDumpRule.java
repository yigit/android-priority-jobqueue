package com.birbit.android.jobqueue.testing;

import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

import java.util.Map;

/**
 * Throws a new assertion with thread dump
 */
public class ThreadDumpRule extends TestWatcher {
    @Override
    public void failed(Throwable e, Description description) {
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
}
