package com.birbit.android.jobqueue.log;

/**
 * Wrapper around {@link CustomLogger}. by default, logs to nowhere
 */
public class JqLog {
    private static CustomLogger customLogger;
    static {
        clearLogger();
    }

    public static void clearLogger() {
        setCustomLogger(new CustomLogger() {
            @Override
            public boolean isDebugEnabled() {
                return false;
            }

            @Override
            public void d(String text, Object... args) {
                //void
            }

            @Override
            public void e(Throwable t, String text, Object... args) {
                //void
            }

            @Override
            public void e(String text, Object... args) {
                //void
            }

            @Override
            public void v(String text, Object... args) {
                //void
            }
        });
    }

    public static void setCustomLogger(CustomLogger customLogger) {
        JqLog.customLogger = customLogger;
    }

    public static boolean isDebugEnabled() {
        return customLogger.isDebugEnabled();
    }

    public static void d(String text, Object... args) {
        customLogger.d(text, args);
    }

    public static void e(Throwable t, String text, Object... args) {
        customLogger.e(t, text, args);
    }

    public static void e(String text, Object... args) {
        customLogger.e(text, args);
    }

    public static void v(String text, Object... args) {
        customLogger.v(text, args);
    }
}
