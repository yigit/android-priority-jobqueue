package com.birbit.android.jobqueue.test;

import android.util.Log;

import com.birbit.android.jobqueue.testing.StackTraceRule;
import com.birbit.android.jobqueue.log.CustomLogger;
import com.birbit.android.jobqueue.log.JqLog;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.robolectric.shadows.ShadowLog;

public class TestBase {
    protected static boolean ENABLE_DEBUG = false;
    @Rule
    public StackTraceRule stackTraceRule = new StackTraceRule();
    @Before
    public void setUp() throws Exception {
        if(ENABLE_DEBUG) {
            enableDebug();
        }
    }

    @After
    public void clearDebugger() {
        JqLog.clearLogger();
    }

    protected void enableDebug() {
        ShadowLog.stream = System.out;
        JqLog.setCustomLogger(new CustomLogger() {
            private String TAG = "test_logger";

            @Override
            public boolean isDebugEnabled() {
                return true;
            }

            @Override
            public void d(String text, Object... args) {
                Log.d(TAG, prefix() + String.format(text, args));
            }

            @Override
            public void e(Throwable t, String text, Object... args) {
                Log.e(TAG, prefix() + String.format(text, args), t);
            }

            @Override
            public void e(String text, Object... args) {
                Log.e(TAG, prefix() + String.format(text, args));
            }

            @Override
            public void v(String text, Object... args) {
                Log.v(TAG, prefix() + String.format(text, args));
            }

            private String prefix() {
                return Thread.currentThread().getName() + "[" + System.currentTimeMillis() + "]";
            }
        });
    }
}
