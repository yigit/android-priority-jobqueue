package com.path.android.jobqueue.network;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.PowerManager;

/**
 * default implementation for network Utility to observe network events
 */
public class NetworkUtilImpl implements NetworkUtil, NetworkEventProvider {
    private Listener listener;
    public NetworkUtilImpl(Context context) {
        IntentFilter networkIntentFilter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
        if (VERSION.SDK_INT >= VERSION_CODES.M) {
            networkIntentFilter.addAction(PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED);
        }
        context.getApplicationContext().registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if(listener == null) {//shall not be but just be safe
                    return;
                }
                //http://developer.android.com/reference/android/net/ConnectivityManager.html#EXTRA_NETWORK_INFO
                //Since NetworkInfo can vary based on UID, applications should always obtain network information
                // through getActiveNetworkInfo() or getAllNetworkInfo().
                listener.onNetworkChange(isConnected(context));
            }
        }, networkIntentFilter);
    }

    @Override
    public boolean isConnected(Context context) {
        // During Doze mode, also called Idle, the network is unavailable but isConnectedOrConnecting()
        // will return true. So we first check if we are in idle mode through the PowerManager before
        // trusting the ConnectivityManager.
        if (VERSION.SDK_INT >= VERSION_CODES.M) {
            PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            if (powerManager.isDeviceIdleMode()) {
                return false;
            }
        }

        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo netInfo = cm.getActiveNetworkInfo();
        return netInfo != null && netInfo.isConnectedOrConnecting();
    }

    @Override
    public void setListener(Listener listener) {
        this.listener = listener;
    }
}
