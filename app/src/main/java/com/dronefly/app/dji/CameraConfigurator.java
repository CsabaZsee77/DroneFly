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

    // ── Intervallum fotózás (survey repüléshez) ────────────────────────

    /**
     * Kamera beállítása intervallum módra és fotózás indítása.
     *
     * @param photoDistM  Fotótávolság méterben (GridMissionGenerator.result.photoDistM)
     * @param speedMs     Repülési sebesség m/s (config.speedMs)
     * @param callback    Eredmény visszahívó
     *
     * Számítás: timeInterval = photoDistM / speedMs
     * Minimum: 2.0 másodperc (P4P v1 kamera fizikai korlátja)
     */
    public static void startIntervalShooting(float photoDistM, float speedMs,
                                              ConfigCallback callback) {
        Camera camera = getCamera();
        if (camera == null) {
            if (callback != null) callback.onComplete(false, "Kamera nem elérhető");
            return;
        }

        float intervalSec = Math.max(2.0f, photoDistM / speedMs);
        Log.i(TAG, "Intervallum fotózás: " + photoDistM + "m / " + speedMs
                + "m/s = " + intervalSec + "s");

        // 1. Kamera mód: SHOOT_PHOTO
        camera.setMode(SettingsDefinitions.CameraMode.SHOOT_PHOTO, modeErr -> {
            // 2. Fotó mód: INTERVAL
            camera.setShootPhotoMode(SettingsDefinitions.ShootPhotoMode.INTERVAL, modeErr2 -> {
                if (modeErr2 != null) {
                    Log.w(TAG, "Intervallum mód beállítás hiba: " + modeErr2.getDescription());
                }
                // 3. Időköz beállítása reflection-nal (PhotoTimeIntervalSettings package-neve
                //    verziónként eltérhet; reflection garantálja a kompatibilitást)
                boolean intervalSet = false;
                try {
                    Class<?> cls = Class.forName("dji.common.camera.PhotoTimeIntervalSettings");
                    Object intervalSettings = cls.getConstructor(int.class, float.class)
                                                 .newInstance(0, intervalSec);
                    Class<?> cbClass = Class.forName(
                            "dji.common.util.CommonCallbacks$CompletionCallback");
                    camera.getClass()
                          .getMethod("setPhotoTimeIntervalSettings", cls, cbClass)
                          .invoke(camera, intervalSettings,
                                  (dji.common.util.CommonCallbacks.CompletionCallback) err -> {
                                      if (err != null) {
                                          Log.w(TAG, "Időköz beállítás hiba: "
                                                  + err.getDescription());
                                      }
                                      startShootPhotoInternal(camera, intervalSec, callback);
                                  });
                    intervalSet = true;
                } catch (Throwable t) {
                    Log.w(TAG, "PhotoTimeIntervalSettings reflection hiba: " + t.getMessage());
                }
                // 4. Fallback: ha reflection nem sikerült, azonnal indítjuk a fotózást
                if (!intervalSet) {
                    startShootPhotoInternal(camera, intervalSec, callback);
                }
            });
        });
    }

    private static void startShootPhotoInternal(Camera camera, float intervalSec,
                                                 ConfigCallback callback) {
        camera.startShootPhoto(startErr -> {
            if (startErr != null) {
                Log.e(TAG, "Fotózás indítás hiba: " + startErr.getDescription());
                if (callback != null)
                    callback.onComplete(false, "Fotózás hiba: " + startErr.getDescription());
            } else {
                Log.i(TAG, "Intervallum fotózás elindítva (" + intervalSec + "s)");
                if (callback != null)
                    callback.onComplete(true, "Fotózás elindítva");
            }
        });
    }

    // ── Gimbal nadir ──────────────────────────────────────────────────

    /**
     * Gimbal lefele forgatása -90°-ra (nadir) survey fotózás előtt.
     * A callback mindig true-val tér vissza — gimbal hiba nem blokkolja a missziót.
     */
    public static void setNadirPitch(ConfigCallback callback) {
        try {
            BaseProduct product = DJISDKManager.getInstance().getProduct();
            if (product == null || !product.isConnected()) {
                if (callback != null) callback.onComplete(true, "Gimbal: nincs kapcsolat");
                return;
            }
            dji.sdk.gimbal.Gimbal gimbal = product.getGimbal();
            if (gimbal == null) {
                Log.w(TAG, "Gimbal nem elérhető");
                if (callback != null) callback.onComplete(true, "Gimbal nem elérhető");
                return;
            }
            dji.common.gimbal.Rotation rotation = new dji.common.gimbal.Rotation.Builder()
                    .pitch(-90f)
                    .mode(dji.common.gimbal.RotationMode.ABSOLUTE_ANGLE)
                    .time(3.0)
                    .build();
            gimbal.rotate(rotation, error -> {
                if (error != null) {
                    Log.w(TAG, "Gimbal pitch hiba: " + error.getDescription());
                } else {
                    Log.i(TAG, "Gimbal nadir állásba forgatva (-90°)");
                }
                if (callback != null) callback.onComplete(true, "Gimbal OK");
            });
        } catch (Throwable t) {
            Log.e(TAG, "Gimbal nadir hiba: " + t.getMessage());
            if (callback != null) callback.onComplete(true, "Gimbal hiba");
        }
    }

    // ── SD kártya ellenőrzés ───────────────────────────────────────────

    /**
     * SD kártya jelenlét ellenőrzése a kamera rendszerállapotából.
     * callback(true)  = van kártya VAGY nem sikerült ellenőrizni → folytatható
     * callback(false) = biztosan NINCS kártya → figyelmeztetés szükséges
     */
    public static void checkSDCard(ConfigCallback callback) {
        Camera camera = getCamera();
        if (camera == null) {
            if (callback != null) callback.onComplete(true, "");
            return;
        }
        try {
            camera.setSystemStateCallback(state -> {
                camera.setSystemStateCallback(null);
                try {
                    boolean inserted = (boolean) state.getClass()
                            .getMethod("isSDCardInserted").invoke(state);
                    Log.i(TAG, "SD kártya behelyezve: " + inserted);
                    if (callback != null)
                        callback.onComplete(inserted,
                                inserted ? "SD kártya OK" : "Nincs SD kártya!");
                } catch (Throwable ignored) {
                    // Nem sikerült lekérdezni — engedjük tovább
                    if (callback != null) callback.onComplete(true, "SD ellenőrzés kihagyva");
                }
            });
        } catch (Throwable t) {
            Log.w(TAG, "SD kártya ellenőrzés hiba: " + t.getMessage());
            if (callback != null) callback.onComplete(true, "");
        }
    }

    /** Intervallum fotózás leállítása (misszió vége / stop után). */
    public static void stopIntervalShooting() {
        Camera camera = getCamera();
        if (camera == null) return;
        try {
            camera.stopShootPhoto(err -> {
                if (err != null) Log.w(TAG, "Fotózás leállítás hiba: " + err.getDescription());
                else             Log.i(TAG, "Intervallum fotózás leállítva");
            });
        } catch (Throwable t) {
            Log.w(TAG, "stopShootPhoto hiba: " + t.getMessage());
        }
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
