package com.birbit.android.jobqueue.testing;


import com.birbit.android.jobqueue.log.CustomLogger;
import com.birbit.android.jobqueue.log.JqLog;

import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

public class CollectLogsRule extends TestWatcher {
    public final CollectingLogger logger = new CollectingLogger();

    public CollectLogsRule() {
        super();
    }

    @Override
    protected void starting(Description description) {
        super.starting(description);
        JqLog.setCustomLogger(logger);
    }

    @Override
    public void failed(Throwable e, Description description) {
        throw new AssertionError(logger.logs.toString());
    }

    static class CollectingLogger implements CustomLogger {
        private StringBuffer logs = new StringBuffer();
        @Override
        public boolean isDebugEnabled() {
            return true;
        }

        @Override
        public void d(String text, Object... args) {
            logs.append(Thread.currentThread().getName()).append("[d]:");
            logs.append(String.format(text, args)).append("\n");
        }

        @Override
        public void e(Throwable t, String text, Object... args) {
            logs.append(Thread.currentThread().getName()).append("[e]:");
            logs.append(String.format(text, args)).append("\n");
        }

        @Override
        public void e(String text, Object... args) {
            logs.append(Thread.currentThread().getName()).append("[e]:");
            logs.append(String.format(text, args)).append("\n");
        }

        @Override
        public void v(String text, Object... args) {
            logs.append(Thread.currentThread().getName()).append("[v]:");
            logs.append(String.format(text, args)).append("\n");
        }
    }
}
