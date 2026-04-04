package com.dronefly.app;

import android.content.Context;
import android.util.Log;

import android.app.Application;
import com.cySdkyc.clx.Helper;

public class App extends Application {

    private static final String TAG = "DJIHelper";

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        // MultiDex először (Android 5.x kompatibilitás)
        androidx.multidex.MultiDex.install(this);
        // DJI SecNeo dekódoló – a dji-sdk AAR-ból jön
        Helper.install(this);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        try {
            com.dronefly.app.dji.DJIHelper.getInstance().init(this, null);
        } catch (Throwable t) {
            Log.e(TAG, "DJI SDK init hiba: " + t.getMessage());
        }
    }
}
