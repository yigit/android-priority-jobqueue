package com.path.android.jobqueue.network;

import android.content.Context;

/**
 * Interface which you can implement if you want to provide a custom Network callback.
 * Make sure you also implement {@link NetworkEventProvider} for best performance.
 */
public interface NetworkUtil {
    public boolean isConnected(Context context);
}
