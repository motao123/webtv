package com.fongmi.android.tv.utils;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Build;

import androidx.annotation.NonNull;

import com.fongmi.android.tv.App;
import com.github.catvod.crawler.SpiderDebug;

public class NetworkUtil {

    public interface Listener {
        void onNetworkAvailable();
        void onNetworkLost();
    }

    private static volatile boolean available = true;
    private static ConnectivityManager connectivityManager;
    private static ConnectivityManager.NetworkCallback networkCallback;

    public static boolean isAvailable() {
        return available;
    }

    public static String getType() {
        try {
            ConnectivityManager cm = getConnectivityManager();
            Network network = cm.getActiveNetwork();
            if (network == null) return "none";
            NetworkCapabilities caps = cm.getNetworkCapabilities(network);
            if (caps == null) return "unknown";
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return "wifi";
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) return "ethernet";
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) return "cellular";
            return "unknown";
        } catch (Exception e) {
            return "unknown";
        }
    }

    public static void register(@NonNull Listener listener) {
        try {
            ConnectivityManager cm = getConnectivityManager();
            networkCallback = new ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(@NonNull Network network) {
                    available = true;
                    SpiderDebug.log("network", "available type=%s", getType());
                    App.post(listener::onNetworkAvailable);
                }

                @Override
                public void onLost(@NonNull Network network) {
                    available = false;
                    SpiderDebug.log("network", "lost");
                    App.post(listener::onNetworkLost);
                }
            };
            NetworkRequest request = new NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build();
            cm.registerNetworkCallback(request, networkCallback);
        } catch (Exception e) {
            SpiderDebug.log("network", "register failed: %s", e.getMessage());
        }
    }

    public static void unregister() {
        try {
            if (networkCallback != null) {
                getConnectivityManager().unregisterNetworkCallback(networkCallback);
                networkCallback = null;
            }
        } catch (Exception e) {
            SpiderDebug.log("network", "unregister failed: %s", e.getMessage());
        }
    }

    private static ConnectivityManager getConnectivityManager() {
        if (connectivityManager == null) {
            connectivityManager = (ConnectivityManager) App.get().getSystemService(Context.CONNECTIVITY_SERVICE);
        }
        return connectivityManager;
    }
}
