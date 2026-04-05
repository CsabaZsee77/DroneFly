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

    /** RC akkumulátor töltöttség callback */
    public void getRcBatteryPercent(BatteryCallback cb) {
        try {
            BaseProduct p = DJISDKManager.getInstance().getProduct();
            if (p == null || !p.isConnected()) { cb.onResult(-1); return; }
            Object rc = p.getClass().getMethod("getRemoteController").invoke(p);
            if (rc == null) { cb.onResult(-1); return; }
            // RC: setChargeRemainingCallback vagy getBatteryInfo
            // MSDK v4: RemoteController.setChargeRemainingCallback
            Class<?> callbackClass = Class.forName(
                    "dji.sdk.remotecontroller.RemoteController$ChargeRemainingCallback");
            rc.getClass().getMethod("setChargeRemainingCallback", callbackClass)
                .invoke(rc, java.lang.reflect.Proxy.newProxyInstance(
                    getClass().getClassLoader(),
                    new Class[]{callbackClass},
                    (proxy, method, args) -> {
                        if (args != null && args.length > 0) {
                            try {
                                int pct = (int) args[0].getClass()
                                        .getMethod("getPercent").invoke(args[0]);
                                cb.onResult(pct);
                            } catch (Throwable t2) { cb.onResult(-1); }
                        }
                        return null;
                    }));
        } catch (Throwable t) {
            Log.d(TAG, "RC battery callback nem elérhető: " + t.getMessage());
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
            battery.getClass().getMethod("setStateCallback",
                    Class.forName("dji.common.battery.BatteryState$Callback"))
                .invoke(battery, java.lang.reflect.Proxy.newProxyInstance(
                    getClass().getClassLoader(),
                    new Class[]{Class.forName("dji.common.battery.BatteryState$Callback")},
                    (proxy, method, args) -> {
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

    /** GPS műholdak száma – Flight Controller state callback (reflexió) */
    public interface GpsCallback { void onResult(int satellites, boolean homeSet); }

    public void setFlightStateCallback(GpsCallback cb) {
        try {
            BaseProduct p = DJISDKManager.getInstance().getProduct();
            if (p == null || !p.isConnected()) return;
            // Aircraft.getFlightController()
            Object fc = p.getClass().getMethod("getFlightController").invoke(p);
            if (fc == null) return;
            Class<?> cbClass = Class.forName("dji.sdk.flightcontroller.FlightController$FlightControllerCurrentState");
            // setStateCallback(FlightControllerCurrentState.Callback)
            Class<?> callbackClass = Class.forName("dji.common.flightcontroller.FlightControllerState$Callback");
            fc.getClass().getMethod("setStateCallback", callbackClass)
                .invoke(fc, java.lang.reflect.Proxy.newProxyInstance(
                    getClass().getClassLoader(),
                    new Class[]{callbackClass},
                    (proxy, method, args) -> {
                        if ("onUpdate".equals(method.getName()) && args != null && args.length > 0) {
                            try {
                                int sats = (int) args[0].getClass()
                                    .getMethod("getSatelliteCount").invoke(args[0]);
                                boolean homeSet = (boolean) args[0].getClass()
                                    .getMethod("isHomePointSet").invoke(args[0]);
                                cb.onResult(sats, homeSet);
                            } catch (Throwable t2) { /* ignore */ }
                        }
                        return null;
                    }));
        } catch (Throwable t) {
            Log.d(TAG, "FlightState callback nem érhető el: " + t.getMessage());
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
