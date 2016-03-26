package com.birbit.android.jobqueue.network;

import android.content.Context;
import android.support.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Interface which you can implement if you want to provide a custom Network callback.
 * Make sure you also implement {@link NetworkEventProvider} for best performance.
 */
public interface NetworkUtil {
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({DISCONNECTED, METERED, UNMETERED})
    @interface NetworkStatus {}
    int DISCONNECTED = 1;
    int METERED = 2;
    int UNMETERED = 3;

    /**
     * Returns the current connection status. If you cannot detect granular network type, return
     * {@link #UNMETERED} if there is an internet connection or {@link #DISCONNECTED} if there is no
     * connection.
     *
     * @param context The application context
     *
     * @return The current connection status. It should be one of {@link #DISCONNECTED},
     * {@link #METERED} or {@link #UNMETERED}.
     */
    @NetworkStatus
    int getNetworkStatus(Context context);
}
