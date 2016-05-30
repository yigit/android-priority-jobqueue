package com.birbit.android.jobqueue;

/**
 * Called when a method is called in the wrong thread. There are 2 thread restrictions:
 * <p>
 * Some methods of the JobManager cannot be called on its own Thread where your Job's onRun method
 * is executed. If you call any of these methods in that method, you'll receive this exception.
 * <p>
 * Some methods of the JobManager may take a long time to execute. If you call these methods on
 * the main thread, it will thrown an exception.
 */
public class WrongThreadException extends RuntimeException {
    public WrongThreadException(String detailMessage) {
        super(detailMessage);
    }
}
