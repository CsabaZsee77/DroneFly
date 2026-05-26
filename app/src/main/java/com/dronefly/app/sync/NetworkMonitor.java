package com.dronefly.app.sync;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * Hálózati állapot figyelő — ConnectivityManager.NetworkCallback alapon (nem polling).
 *
 * Használat:
 *   NetworkMonitor.getInstance(context).addListener(listener);
 *   NetworkMonitor.getInstance(context).isOnline();  // szinkron lekérdezés
 *
 * Az app indításakor regisztrálni kell (App.java vagy MainActivity):
 *   NetworkMonitor.getInstance(this).register();
 */
public class NetworkMonitor {

    private static final String TAG = "NetworkMonitor";

    public enum State {
        OFFLINE,   // nincs hálózat
        ONLINE     // van hálózat (WiFi vagy mobiladat)
    }

    public interface Listener {
        void onNetworkStateChanged(State state);
    }

    private static volatile NetworkMonitor instance;

    private final ConnectivityManager connectivityManager;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final List<Listener> listeners = new ArrayList<>();

    private State currentState = State.OFFLINE;
    private boolean registered = false;

    private final ConnectivityManager.NetworkCallback networkCallback =
            new ConnectivityManager.NetworkCallback() {

                @Override
                public void onAvailable(Network network) {
                    setState(State.ONLINE);
                }

                @Override
                public void onLost(Network network) {
                    // Csak akkor váltunk OFFLINE-ra, ha tényleg nincs aktív hálózat
                    if (!hasActiveNetwork()) {
                        setState(State.OFFLINE);
                    }
                }

                @Override
                public void onUnavailable() {
                    setState(State.OFFLINE);
                }
            };

    private NetworkMonitor(Context context) {
        connectivityManager = (ConnectivityManager)
                context.getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        currentState = hasActiveNetwork() ? State.ONLINE : State.OFFLINE;
    }

    public static NetworkMonitor getInstance(Context context) {
        if (instance == null) {
            synchronized (NetworkMonitor.class) {
                if (instance == null) {
                    instance = new NetworkMonitor(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    /**
     * Elindítja a hálózati callback figyelést.
     * Hívd az Activity/Application onCreate()-ben.
     */
    public void register() {
        if (registered) return;
        try {
            NetworkRequest request = new NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build();
            connectivityManager.registerNetworkCallback(request, networkCallback);
            registered = true;
            Log.d(TAG, "NetworkCallback regisztrálva, állapot: " + currentState);
        } catch (Exception e) {
            Log.e(TAG, "NetworkCallback regisztráció sikertelen", e);
        }
    }

    /**
     * Leállítja a figyelést. Hívd az Activity/Application onDestroy()-ban.
     */
    public void unregister() {
        if (!registered) return;
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback);
            registered = false;
        } catch (Exception e) {
            Log.e(TAG, "NetworkCallback unregister hiba", e);
        }
    }

    public void addListener(Listener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    public State getState() {
        return currentState;
    }

    public boolean isOnline() {
        return currentState == State.ONLINE;
    }

    // ---- belső ----

    @SuppressWarnings("deprecation")
    private boolean hasActiveNetwork() {
        try {
            // getActiveNetwork() csak API 23+, Crystal Sky API 22 → getActiveNetworkInfo()
            android.net.NetworkInfo info = connectivityManager.getActiveNetworkInfo();
            return info != null && info.isConnected();
        } catch (Exception e) {
            return false;
        }
    }

    private void setState(final State newState) {
        if (newState == currentState) return;
        currentState = newState;
        Log.d(TAG, "Hálózati állapot: " + newState);
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                for (int i = listeners.size() - 1; i >= 0; i--) {
                    listeners.get(i).onNetworkStateChanged(newState);
                }
            }
        });
    }
}
