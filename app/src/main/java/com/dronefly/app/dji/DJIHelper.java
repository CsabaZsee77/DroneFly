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
