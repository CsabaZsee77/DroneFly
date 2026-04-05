package com.dronefly.app.dji;

import android.util.Log;

import com.dronefly.app.model.CameraSettings;

import dji.common.camera.SettingsDefinitions;
import dji.common.camera.WhiteBalance;
import dji.common.error.DJIError;
import dji.sdk.base.BaseProduct;
import dji.sdk.camera.Camera;
import dji.sdk.sdkmanager.DJISDKManager;

/**
 * DJI kamera beállítások alkalmazása a misszió indítása előtt.
 * Aszinkron callback lánc – a beállítások sorban kerülnek alkalmazásra.
 */
public class CameraConfigurator {

    private static final String TAG = "CameraConfig";

    public interface ConfigCallback {
        void onComplete(boolean success, String message);
    }

    /**
     * Kamera beállítások alkalmazása a csatlakoztatott drónra.
     */
    public static void applySettings(CameraSettings settings, ConfigCallback callback) {
        try {
            Camera camera = getCamera();
            if (camera == null) {
                if (callback != null)
                    callback.onComplete(false, "Kamera nem elérhető");
                return;
            }

            // 1. lépés: Kamera mód beállítása SHOOT_PHOTO-ra
            camera.setMode(SettingsDefinitions.CameraMode.SHOOT_PHOTO, error -> {
                if (error != null) {
                    Log.w(TAG, "Kamera mód hiba: " + error.getDescription());
                }
                // Folytatjuk akkor is ha hiba van – lehet már SHOOT_PHOTO módban van
                applyExposureMode(camera, settings, callback);
            });
        } catch (Throwable t) {
            Log.e(TAG, "Kamera konfiguráció hiba: " + t.getMessage());
            if (callback != null)
                callback.onComplete(false, "Kamera hiba: " + t.getMessage());
        }
    }

    // ── Expozíciós mód ─────────────────────────────────────────────────

    private static void applyExposureMode(Camera camera, CameraSettings settings,
                                           ConfigCallback callback) {
        SettingsDefinitions.ExposureMode mode;

        if (settings.autoMode ||
            (settings.iso == CameraSettings.IsoValue.AUTO &&
             settings.shutterSpeed == CameraSettings.ShutterSpeed.AUTO)) {
            mode = SettingsDefinitions.ExposureMode.PROGRAM;
        } else if (settings.iso != CameraSettings.IsoValue.AUTO &&
                   settings.shutterSpeed != CameraSettings.ShutterSpeed.AUTO) {
            mode = SettingsDefinitions.ExposureMode.MANUAL;
        } else if (settings.shutterSpeed != CameraSettings.ShutterSpeed.AUTO) {
            mode = SettingsDefinitions.ExposureMode.SHUTTER_PRIORITY;
        } else {
            mode = SettingsDefinitions.ExposureMode.PROGRAM;
        }

        camera.setExposureMode(mode, error -> {
            if (error != null) Log.w(TAG, "Expozíció mód hiba: " + error.getDescription());
            applyIso(camera, settings, callback);
        });
    }

    // ── ISO ────────────────────────────────────────────────────────────

    private static void applyIso(Camera camera, CameraSettings settings,
                                  ConfigCallback callback) {
        if (settings.autoMode || settings.iso == CameraSettings.IsoValue.AUTO) {
            applyShutter(camera, settings, callback);
            return;
        }

        SettingsDefinitions.ISO djiIso;
        switch (settings.iso) {
            case ISO_100: djiIso = SettingsDefinitions.ISO.ISO_100; break;
            case ISO_200: djiIso = SettingsDefinitions.ISO.ISO_200; break;
            case ISO_400: djiIso = SettingsDefinitions.ISO.ISO_400; break;
            case ISO_800: djiIso = SettingsDefinitions.ISO.ISO_800; break;
            default:      djiIso = SettingsDefinitions.ISO.AUTO; break;
        }

        camera.setISO(djiIso, error -> {
            if (error != null) Log.w(TAG, "ISO hiba: " + error.getDescription());
            applyShutter(camera, settings, callback);
        });
    }

    // ── Záridő ─────────────────────────────────────────────────────────

    private static void applyShutter(Camera camera, CameraSettings settings,
                                      ConfigCallback callback) {
        if (settings.autoMode || settings.shutterSpeed == CameraSettings.ShutterSpeed.AUTO) {
            applyWhiteBalance(camera, settings, callback);
            return;
        }

        SettingsDefinitions.ShutterSpeed djiShutter;
        switch (settings.shutterSpeed) {
            case S_1_100:  djiShutter = SettingsDefinitions.ShutterSpeed.SHUTTER_SPEED_1_100; break;
            case S_1_200:  djiShutter = SettingsDefinitions.ShutterSpeed.SHUTTER_SPEED_1_200; break;
            case S_1_400:  djiShutter = SettingsDefinitions.ShutterSpeed.SHUTTER_SPEED_1_400; break;
            case S_1_800:  djiShutter = SettingsDefinitions.ShutterSpeed.SHUTTER_SPEED_1_800; break;
            case S_1_1000: djiShutter = SettingsDefinitions.ShutterSpeed.SHUTTER_SPEED_1_1000; break;
            case S_1_1600: djiShutter = SettingsDefinitions.ShutterSpeed.SHUTTER_SPEED_1_1600; break;
            default:       djiShutter = SettingsDefinitions.ShutterSpeed.SHUTTER_SPEED_1_400; break;
        }

        camera.setShutterSpeed(djiShutter, error -> {
            if (error != null) Log.w(TAG, "Záridő hiba: " + error.getDescription());
            applyWhiteBalance(camera, settings, callback);
        });
    }

    // ── Fehéregyensúly ─────────────────────────────────────────────────

    private static void applyWhiteBalance(Camera camera, CameraSettings settings,
                                           ConfigCallback callback) {
        if (settings.autoMode || settings.whiteBalance == CameraSettings.WhiteBalanceValue.AUTO) {
            applyFileFormat(camera, settings, callback);
            return;
        }

        dji.common.camera.WhiteBalance wb;
        switch (settings.whiteBalance) {
            case SUNNY:
                wb = new WhiteBalance(SettingsDefinitions.WhiteBalancePreset.SUNNY);
                break;
            case CLOUDY:
                wb = new WhiteBalance(SettingsDefinitions.WhiteBalancePreset.CLOUDY);
                break;
            default:
                wb = new WhiteBalance(SettingsDefinitions.WhiteBalancePreset.AUTO);
                break;
        }

        camera.setWhiteBalance(wb, error -> {
            if (error != null) Log.w(TAG, "Fehéregyensúly hiba: " + error.getDescription());
            applyFileFormat(camera, settings, callback);
        });
    }

    // ── Fájlformátum ───────────────────────────────────────────────────

    private static void applyFileFormat(Camera camera, CameraSettings settings,
                                         ConfigCallback callback) {
        SettingsDefinitions.PhotoFileFormat fmt;
        switch (settings.fileFormat) {
            case JPEG:         fmt = SettingsDefinitions.PhotoFileFormat.JPEG; break;
            case DNG_RAW:      fmt = SettingsDefinitions.PhotoFileFormat.RAW; break;
            case JPEG_AND_RAW: fmt = SettingsDefinitions.PhotoFileFormat.RAW_AND_JPEG; break;
            default:           fmt = SettingsDefinitions.PhotoFileFormat.RAW_AND_JPEG; break;
        }

        camera.setPhotoFileFormat(fmt, error -> {
            if (error != null) Log.w(TAG, "Fájlformátum hiba: " + error.getDescription());
            applyPhotoMode(camera, settings, callback);
        });
    }

    // ── Fotó mód ───────────────────────────────────────────────────────

    private static void applyPhotoMode(Camera camera, CameraSettings settings,
                                        ConfigCallback callback) {
        SettingsDefinitions.ShootPhotoMode mode =
                settings.photoMode == CameraSettings.PhotoMode.INTERVAL
                        ? SettingsDefinitions.ShootPhotoMode.INTERVAL
                        : SettingsDefinitions.ShootPhotoMode.SINGLE;

        camera.setShootPhotoMode(mode, error -> {
            if (error != null) Log.w(TAG, "Fotó mód hiba: " + error.getDescription());
            Log.i(TAG, "Kamera beállítások alkalmazva");
            if (callback != null)
                callback.onComplete(true, "Kamera beállítások alkalmazva");
        });
    }

    // ── Segéd ──────────────────────────────────────────────────────────

    private static Camera getCamera() {
        try {
            BaseProduct product = DJISDKManager.getInstance().getProduct();
            if (product == null || !product.isConnected()) return null;
            return product.getCamera();
        } catch (Throwable t) {
            Log.e(TAG, "Kamera elérés hiba: " + t.getMessage());
            return null;
        }
    }
}
