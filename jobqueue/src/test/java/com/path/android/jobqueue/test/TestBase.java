package com.path.android.jobqueue.test;

import android.util.Log;
import com.path.android.jobqueue.log.CustomLogger;
import com.path.android.jobqueue.log.JqLog;

import org.junit.After;
import org.junit.Before;
import org.robolectric.shadows.ShadowLog;

public class TestBase {
    protected static boolean ENABLE_DEBUG = true;
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
                Log.d(TAG, Thread.currentThread().getName() + ":" + String.format(text, args));
            }

            @Override
            public void e(Throwable t, String text, Object... args) {
                Log.e(TAG, Thread.currentThread().getName() + ":" + String.format(text, args), t);
            }

            @Override
            public void e(String text, Object... args) {
                Log.e(TAG, Thread.currentThread().getName() + ":" + String.format(text, args));
            }
        });
    }

    protected void enableCollectingDebug() {
        ShadowLog.stream = System.out;
        JqLog.setCustomLogger(new CustomLogger() {
            private String TAG = "test_logger";
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
        });
    }
}
