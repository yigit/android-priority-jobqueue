package com.birbit.android.jobqueue;

/**
 * Internal interface to mimic sync requests.
 */
public interface IntCallback {
    void onResult(int result);
    interface MessageWithCallback {
        void setCallback(IntCallback intCallback);
    }
}
