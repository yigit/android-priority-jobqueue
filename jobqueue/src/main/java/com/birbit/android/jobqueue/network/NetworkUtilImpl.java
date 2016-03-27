package com.birbit.android.jobqueue.network;

import android.annotation.TargetApi;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.NetworkRequest;
import android.os.Build;
import android.os.Build.VERSION;
import android.os.PowerManager;

/**
 * default implementation for network Utility to observe network events
 */
public class NetworkUtilImpl implements NetworkUtil, NetworkEventProvider {
    private Listener listener;
    public NetworkUtilImpl(final Context context) {
        if (VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            if (VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                listenForIdle(context);
            }
            listenNetworkViaConnectivityManager(context);
        } else {
            context.getApplicationContext().registerReceiver(new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    dispatchNetworkChange(context);
                }
            }, getNetworkIntentFilter());
        }
    }

    @TargetApi(23)
    private void listenNetworkViaConnectivityManager(final Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getApplicationContext()
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkRequest request = new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
                .build();
        cm.registerNetworkCallback(request, new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(Network network) {
                dispatchNetworkChange(context);
            }
        });
    }

    @TargetApi(23)
    private void listenForIdle(Context context) {
        context.getApplicationContext().registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                dispatchNetworkChange(context);
            }
        }, new IntentFilter(PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED));
    }

    void dispatchNetworkChange(Context context) {
        if(listener == null) {//shall not be but just be safe
            return;
        }
        //http://developer.android.com/reference/android/net/ConnectivityManager.html#EXTRA_NETWORK_INFO
        //Since NetworkInfo can vary based on UID, applications should always obtain network information
        // through getActiveNetworkInfo() or getAllNetworkInfo().
        listener.onNetworkChange(getNetworkStatus(context));
    }

    @Override
    public int getNetworkStatus(Context context) {
        if (isDozing(context)) {
            return NetworkUtil.DISCONNECTED;
        }
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo netInfo = cm.getActiveNetworkInfo();
        if (netInfo == null) {
            return NetworkUtil.DISCONNECTED;
        }
        if (netInfo.getType() == ConnectivityManager.TYPE_WIFI ||
                netInfo.getType() == ConnectivityManager.TYPE_ETHERNET) {
            return NetworkUtil.UNMETERED;
        }
        return NetworkUtil.METERED;
    }

    @TargetApi(23)
    private static IntentFilter getNetworkIntentFilter() {
        IntentFilter networkIntentFilter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
        if (VERSION.SDK_INT >= 23) {
            networkIntentFilter.addAction(PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED);
        }
        return networkIntentFilter;
    }

    /**
     * Returns true if the device is in Doze/Idle mode. Should be called before checking the network connection because
     * the ConnectionManager may report the device is connected when it isn't during Idle mode.
     */
    @TargetApi(23)
    private static boolean isDozing(Context context) {
        if (VERSION.SDK_INT >= 23) {
            PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            return powerManager.isDeviceIdleMode() &&
                    !powerManager.isIgnoringBatteryOptimizations(context.getPackageName());
        } else {
            return false;
        }
    }

    @Override
    public void setListener(Listener listener) {
        this.listener = listener;
    }
}
