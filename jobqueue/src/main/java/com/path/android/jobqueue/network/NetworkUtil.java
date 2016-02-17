package com.path.android.jobqueue.network;

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
    @IntDef({DISCONNECTED, MOBILE, WIFI})
    @interface NetworkStatus {}
    int DISCONNECTED = 1;
    int MOBILE = 2;
    int WIFI = 3;

    /**
     * Returns the current connection status. If you cannot detect granular network type, return
     * {@link #WIFI} if there is an internet connection or {@link #DISCONNECTED} if there is no
     * connection.
     *
     * @param context The application context
     *
     * @return The current connection status. It should be one of {@link #DISCONNECTED},
     * {@link #MOBILE} or {@link #WIFI}.
     */
    @NetworkStatus
    int getNetworkStatus(Context context);
}
