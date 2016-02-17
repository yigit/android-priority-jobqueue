package com.path.android.jobqueue.network;

/**
 * An interface that NetworkUtil can implement if it supports a callback method when network state is changed
 * This is not mandatory but highly suggested so that {@link com.path.android.jobqueue.JobManager} can avoid
 * busy loops when there is a job waiting for network and there is no network available
 */
public interface NetworkEventProvider {
    void setListener(Listener listener);
    interface Listener {
        /**
         * @param networkStatus {@link com.path.android.jobqueue.network.NetworkUtil.NetworkStatus}
         */
        void onNetworkChange(@NetworkUtil.NetworkStatus int networkStatus);
    }
}
