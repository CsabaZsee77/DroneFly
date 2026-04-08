package com.dronefly.app.dji;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import dji.common.error.DJIError;
import dji.common.error.DJISDKError;
import dji.sdk.base.BaseComponent;
import dji.sdk.base.BaseProduct;
import dji.sdk.sdkmanager.DJISDKInitEvent;
import dji.sdk.sdkmanager.DJISDKManager;

public class DJIHelper {

    private static final String TAG = "DJIHelper";
    private static DJIHelper instance;

    public interface ConnectionListener {
        void onRegistered(boolean success, String message);
        void onProductConnected(String productName);
        void onProductDisconnected();
    }

    private ConnectionListener listener;
    private boolean registered = false;

    public static DJIHelper getInstance() {
        if (instance == null) instance = new DJIHelper();
        return instance;
    }

    public void init(Context context, ConnectionListener listener) {
        this.listener = listener;
        try {
        DJISDKManager.getInstance().registerApp(context.getApplicationContext(),
                new DJISDKManager.SDKManagerCallback() {

            @Override
            public void onRegister(DJIError djiError) {
                if (djiError == DJISDKError.REGISTRATION_SUCCESS) {
                    registered = true;
                    Log.i(TAG, "DJI SDK regisztrálva");
                    DJISDKManager.getInstance().startConnectionToProduct();
                    if (DJIHelper.this.listener != null)
                        DJIHelper.this.listener.onRegistered(true, "DJI SDK regisztrálva");
                } else {
                    Log.e(TAG, "DJI SDK regisztráció sikertelen: "
                            + (djiError != null ? djiError.getDescription() : "ismeretlen hiba"));
                    if (DJIHelper.this.listener != null)
                        DJIHelper.this.listener.onRegistered(false,
                                djiError != null ? djiError.getDescription() : "Regisztráció sikertelen");
                }
            }

            @Override
            public void onProductDisconnect() {
                Log.i(TAG, "Drón lecsatlakoztatva");
                if (DJIHelper.this.listener != null)
                    DJIHelper.this.listener.onProductDisconnected();
            }

            @Override
            public void onProductConnect(BaseProduct product) {
                String name = (product != null && product.getModel() != null)
                        ? product.getModel().getDisplayName()
                        : "Ismeretlen drón";
                Log.i(TAG, "Drón csatlakoztatva: " + name);
                if (DJIHelper.this.listener != null)
                    DJIHelper.this.listener.onProductConnected(name);
            }

            @Override
            public void onProductChanged(BaseProduct product) {}

            @Override
            public void onComponentChange(BaseProduct.ComponentKey key,
                                          BaseComponent oldComponent,
                                          BaseComponent newComponent) {
                if (newComponent != null)
                    newComponent.setComponentListener(isConnected ->
                            Log.d(TAG, key + " kapcsolat: " + isConnected));
            }

            @Override
            public void onInitProcess(DJISDKInitEvent event, int totalProcess) {}

            @Override
            public void onDatabaseDownloadProgress(long current, long total) {}
        });
        } catch (Throwable t) {
            Log.e(TAG, "DJI registerApp hiba: " + t.getMessage());
        }
    }

    public void setListener(ConnectionListener listener) {
        this.listener = listener;
    }

    public boolean isRegistered() { return registered; }

    // ── Telemetria segédek (reflexión keresztül – Aircraft API nincs a provided stubban) ──

    /** RC csatlakoztatva van-e (reflexió az Aircraft-ból) */
    public boolean isRcConnected() {
        try {
            BaseProduct p = DJISDKManager.getInstance().getProduct();
            if (p == null || !p.isConnected()) return false;
            Object rc = p.getClass().getMethod("getRemoteController").invoke(p);
            if (rc == null) return false;
            return (boolean) rc.getClass().getMethod("isConnected").invoke(rc);
        } catch (Throwable t) { return false; }
    }

    /** RC akkumulátor töltöttség — dinamikus paramétertípus-lekérés reflexióval.
     *  Valódi osztályok Crystal Sky-on (MSDK v4.18, P4P v1):
     *  RC class: dji.sdk.remotecontroller.uio (obfuszkált)
     *  Callback class: dji.common.remotecontroller.BatteryState$Callback
     *  Charge method: getRemainingChargeInPercent()
     */
    public void getRcBatteryPercent(BatteryCallback cb) {
        try {
            BaseProduct p = DJISDKManager.getInstance().getProduct();
            if (p == null || !p.isConnected()) { cb.onResult(-1); return; }
            Object rc = p.getClass().getMethod("getRemoteController").invoke(p);
            if (rc == null) { cb.onResult(-1); return; }

            // Keresünk setChargeRemainingCallback metódust, és lekérjük a tényleges
            // paramétertípust — így nem kell az osztálynevet hardcode-olni
            for (java.lang.reflect.Method setter : rc.getClass().getMethods()) {
                if ("setChargeRemainingCallback".equals(setter.getName())
                        && setter.getParameterTypes().length == 1) {
                    Class<?> callbackClass = setter.getParameterTypes()[0];
                    Object proxy = java.lang.reflect.Proxy.newProxyInstance(
                        getClass().getClassLoader(),
                        new Class[]{callbackClass},
                        (proxyObj, method, args) -> {
                            switch (method.getName()) {
                                case "hashCode": return System.identityHashCode(proxyObj);
                                case "equals":   return proxyObj == (args != null ? args[0] : null);
                                case "toString": return "DJIHelper$RcBatteryCallback";
                            }
                            if (args != null && args.length > 0) {
                                try {
                                    int pct = (int) args[0].getClass()
                                            .getMethod("getRemainingChargeInPercent").invoke(args[0]);
                                    cb.onResult(pct);
                                } catch (Throwable t2) { cb.onResult(-1); }
                            }
                            return null;
                        });
                    setter.invoke(rc, proxy);
                    return;
                }
            }
            cb.onResult(-1);

        } catch (Throwable t) {
            Log.d(TAG, "RC battery hiba: " + t.getMessage());
            cb.onResult(-1);
        }
    }

    /** Drón akkumulátor töltöttség, -1 ha nem elérhető */
    public interface BatteryCallback { void onResult(int percent); }

    public void getDroneBatteryPercent(BatteryCallback cb) {
        try {
            BaseProduct p = DJISDKManager.getInstance().getProduct();
            if (p == null || !p.isConnected()) { cb.onResult(-1); return; }
            Object battery = p.getClass().getMethod("getBattery").invoke(p);
            if (battery == null) { cb.onResult(-1); return; }
            // getChargeRemainingInPercent(CommonCallbacks.CompletionCallbackWith)
            // Egyszerűbb: StateCallback regisztrálás
            Class<?> battCbClass = Class.forName("dji.common.battery.BatteryState$Callback");
            battery.getClass().getMethod("setStateCallback", battCbClass)
                .invoke(battery, java.lang.reflect.Proxy.newProxyInstance(
                    getClass().getClassLoader(),
                    new Class[]{battCbClass},
                    (proxy, method, args) -> {
                        switch (method.getName()) {
                            case "hashCode": return System.identityHashCode(proxy);
                            case "equals":   return proxy == (args != null ? args[0] : null);
                            case "toString": return "DJIHelper$DroneBatteryCallback";
                        }
                        if ("onUpdate".equals(method.getName()) && args != null && args.length > 0) {
                            try {
                                int pct = (int) args[0].getClass()
                                    .getMethod("getChargeRemainingInPercent").invoke(args[0]);
                                cb.onResult(pct);
                            } catch (Throwable t2) { cb.onResult(-1); }
                        }
                        return null;
                    }));
        } catch (Throwable t) { cb.onResult(-1); }
    }

    /** Flight Controller state callback – műholdak, home pont, drón GPS pozíció */
    public interface GpsCallback {
        void onResult(int satellites, boolean homeSet, double latitude, double longitude);
    }

    public void setFlightStateCallback(GpsCallback cb) {
        try {
            BaseProduct p = DJISDKManager.getInstance().getProduct();
            if (p == null || !p.isConnected()) return;
            Object fc = p.getClass().getMethod("getFlightController").invoke(p);
            if (fc == null) return;
            for (java.lang.reflect.Method setter : fc.getClass().getMethods()) {
                if ("setStateCallback".equals(setter.getName())
                        && setter.getParameterTypes().length == 1) {
                    Class<?> callbackClass = setter.getParameterTypes()[0];
                    Object proxy = java.lang.reflect.Proxy.newProxyInstance(
                        getClass().getClassLoader(),
                        new Class[]{callbackClass},
                        (proxyObj, method, args) -> {
                            switch (method.getName()) {
                                case "hashCode": return System.identityHashCode(proxyObj);
                                case "equals":   return proxyObj == (args != null ? args[0] : null);
                                case "toString": return "DJIHelper$FlightStateCallback";
                            }
                            if ("onUpdate".equals(method.getName()) && args != null && args.length > 0) {
                                try {
                                    int sats = (int) args[0].getClass()
                                        .getMethod("getSatelliteCount").invoke(args[0]);
                                    boolean homeSet = (boolean) args[0].getClass()
                                        .getMethod("isHomePointSet").invoke(args[0]);
                                    double lat = 0, lon = 0;
                                    try {
                                        Object loc = args[0].getClass()
                                            .getMethod("getAircraftLocation").invoke(args[0]);
                                        if (loc != null) {
                                            lat = (double) loc.getClass()
                                                .getMethod("getLatitude").invoke(loc);
                                            lon = (double) loc.getClass()
                                                .getMethod("getLongitude").invoke(loc);
                                        }
                                    } catch (Throwable ignored) {}
                                    cb.onResult(sats, homeSet, lat, lon);
                                } catch (Throwable t2) { /* ignore */ }
                            }
                            return null;
                        });
                    setter.invoke(fc, proxy);
                    return;
                }
            }
            Log.d(TAG, "FlightState: setStateCallback metódus nem található az FC-n");
        } catch (Throwable t) {
            Log.d(TAG, "FlightState callback hiba: " + t.getMessage());
        }
    }

    public boolean isConnected() {
        try {
            BaseProduct product = DJISDKManager.getInstance().getProduct();
            return product != null && product.isConnected();
        } catch (Throwable t) {
            return false;
        }
    }

    public String getConnectedProductName() {
        try {
            BaseProduct product = DJISDKManager.getInstance().getProduct();
            if (product != null && product.isConnected() && product.getModel() != null)
                return product.getModel().getDisplayName();
        } catch (Throwable t) { /* SDK nem elérhető */ }
        return null;
    }
}
