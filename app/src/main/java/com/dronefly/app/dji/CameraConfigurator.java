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
            applyAperture(camera, settings, callback);
        });
    }

    // ── Rekesz ────────────────────────────────────────────────────────

    private static void applyAperture(Camera camera, CameraSettings settings,
                                       ConfigCallback callback) {
        SettingsDefinitions.Aperture ap;
        switch (settings.aperture) {
            case F_5_6: ap = SettingsDefinitions.Aperture.F_5_DOT_6; break;
            case F_11:  ap = SettingsDefinitions.Aperture.F_11;      break;
            default:    ap = SettingsDefinitions.Aperture.F_8;       break;
        }
        camera.setAperture(ap, error -> {
            if (error != null) Log.w(TAG, "Rekesz hiba: " + error.getDescription());
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
            case S_1_100:  djiShutter = SettingsDefinitions.ShutterSpeed.SHUTTER_SPEED_1_100;  break;
            case S_1_200:  djiShutter = SettingsDefinitions.ShutterSpeed.SHUTTER_SPEED_1_200;  break;
            case S_1_400:  djiShutter = SettingsDefinitions.ShutterSpeed.SHUTTER_SPEED_1_400;  break;
            case S_1_500:  djiShutter = SettingsDefinitions.ShutterSpeed.SHUTTER_SPEED_1_500;  break;
            case S_1_640:  djiShutter = SettingsDefinitions.ShutterSpeed.SHUTTER_SPEED_1_640;  break;
            case S_1_800:  djiShutter = SettingsDefinitions.ShutterSpeed.SHUTTER_SPEED_1_800;  break;
            case S_1_1000: djiShutter = SettingsDefinitions.ShutterSpeed.SHUTTER_SPEED_1_1000; break;
            case S_1_1250: djiShutter = SettingsDefinitions.ShutterSpeed.SHUTTER_SPEED_1_1250; break;
            case S_1_1600: djiShutter = SettingsDefinitions.ShutterSpeed.SHUTTER_SPEED_1_1600; break;
            case S_1_2000: djiShutter = SettingsDefinitions.ShutterSpeed.SHUTTER_SPEED_1_2000; break;
            default:       djiShutter = SettingsDefinitions.ShutterSpeed.SHUTTER_SPEED_1_400;  break;
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
            case CUSTOM:
                int ct = Math.max(20, Math.min(100, settings.whiteBalanceKelvin / 100));
                wb = new WhiteBalance(SettingsDefinitions.WhiteBalancePreset.CUSTOM, ct);
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

    // ── Mintavételi misszió kamera-előkészítés (M04 §15) ───────────────

    /**
     * Kamera beállítása mintavételi misszióhoz: SHOOT_PHOTO mód + SINGLE
     * fotó-mód. A fotó triggerelése — a folyamatos intervallum-fotózással
     * ellentétben — a waypoint-akció (START_TAKE_PHOTO, NORMAL flightPathMode)
     * felelőssége, ezért itt NEM hívunk startShootPhoto()-t.
     */
    public static void prepareForSamplingMission(ConfigCallback callback) {
        Camera camera = getCamera();
        if (camera == null) {
            if (callback != null) callback.onComplete(false, "Kamera nem elérhető");
            return;
        }
        camera.setMode(SettingsDefinitions.CameraMode.SHOOT_PHOTO, modeErr -> {
            if (modeErr != null) {
                Log.w(TAG, "Kamera mód hiba (mintavétel): " + modeErr.getDescription());
            }
            camera.setShootPhotoMode(SettingsDefinitions.ShootPhotoMode.SINGLE, singleErr -> {
                if (singleErr != null) {
                    Log.w(TAG, "SINGLE fotó mód hiba: " + singleErr.getDescription());
                }
                Log.i(TAG, "Kamera mintavételi misszióhoz előkészítve (SHOOT_PHOTO + SINGLE)");
                if (callback != null) callback.onComplete(true, "Kamera kész (SINGLE mód)");
            });
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

    // ── Auto expozíciós mód ───────────────────────────────────────────

    /** Kamera visszakapcsolása PROGRAM (auto) módba. */
    public static void setAutoExposureMode(ConfigCallback callback) {
        Camera camera = getCamera();
        if (camera == null) {
            if (callback != null) callback.onComplete(false, "Kamera nem elérhető");
            return;
        }
        camera.setMode(SettingsDefinitions.CameraMode.SHOOT_PHOTO, e ->
            camera.setExposureMode(SettingsDefinitions.ExposureMode.PROGRAM, e2 -> {
                boolean ok = e2 == null;
                if (callback != null) callback.onComplete(ok,
                        ok ? "AUTO mód aktív" : e2.getDescription());
            })
        );
    }

    /**
     * Auto Lock: az auto mód aktuális értékeit visszaolvassuk, majd manuálisra váltunk.
     * A callback visszaad egy CameraSettings objektumot a kiolvasott értékekkel.
     */
    public interface ReadbackCallback {
        void onReadback(boolean success, CameraSettings settings, String message);
    }

    public static void lockAutoToManual(ReadbackCallback callback) {
        Camera camera = getCamera();
        if (camera == null) {
            if (callback != null) callback.onReadback(false, null, "Kamera nem elérhető");
            return;
        }
        try {
            camera.getClass()
                  .getMethod("setCurrentExposureValuesCallback", Object.class)
                  .invoke(camera, (Object) null); // reset first
        } catch (Throwable ignored) {}

        try {
            // Callback interfész keresése futásidőben
            java.lang.reflect.Method setter = null;
            Class<?> cbIface = null;
            for (java.lang.reflect.Method m : camera.getClass().getMethods()) {
                if (m.getName().contains("xposure") && m.getName().contains("allback")
                        && m.getParameterTypes().length == 1) {
                    setter = m;
                    cbIface = m.getParameterTypes()[0];
                    break;
                }
            }
            if (setter == null || cbIface == null || !cbIface.isInterface()) {
                // Fallback: olvasás nélkül visszaadjuk a default survey értékeket
                if (callback != null)
                    callback.onReadback(false, null, "Értékek nem olvashatók vissza — manuális beállítás szükséges");
                return;
            }
            final java.lang.reflect.Method finalSetter = setter;
            final Class<?> finalIface = cbIface;
            Object proxy = java.lang.reflect.Proxy.newProxyInstance(
                    finalIface.getClassLoader(), new Class[]{finalIface},
                    (p, method, args) -> {
                        if (args == null || args.length == 0) return null;
                        Object params = args[0];
                        if (params == null) return null;
                        CameraSettings s = new CameraSettings();
                        s.autoMode = false;
                        // ISO
                        try {
                            Object iso = params.getClass().getMethod("getISO").invoke(params);
                            s.iso = mapDjiIso(iso.toString());
                        } catch (Throwable ignored) {}
                        // Shutter
                        try {
                            Object ss = params.getClass().getMethod("getShutterSpeed").invoke(params);
                            s.shutterSpeed = mapDjiShutter(ss.toString());
                        } catch (Throwable ignored) {}
                        // Aperture
                        try {
                            Object ap = params.getClass().getMethod("getAperture").invoke(params);
                            s.aperture = mapDjiAperture(ap.toString());
                        } catch (Throwable ignored) {}
                        // Callback regisztrációt töröljük
                        try { finalSetter.invoke(camera, (Object) null); } catch (Throwable ignored2) {}
                        if (callback != null) callback.onReadback(true, s, "Értékek visszaolvasva");
                        return null;
                    });
            setter.invoke(camera, proxy);
        } catch (Throwable t) {
            Log.w(TAG, "Auto lock readback hiba: " + t.getMessage());
            if (callback != null) callback.onReadback(false, null, t.getMessage());
        }
    }

    private static com.dronefly.app.model.CameraSettings.IsoValue mapDjiIso(String name) {
        if (name.contains("100")) return com.dronefly.app.model.CameraSettings.IsoValue.ISO_100;
        if (name.contains("200")) return com.dronefly.app.model.CameraSettings.IsoValue.ISO_200;
        if (name.contains("400")) return com.dronefly.app.model.CameraSettings.IsoValue.ISO_400;
        return com.dronefly.app.model.CameraSettings.IsoValue.ISO_800;
    }

    private static com.dronefly.app.model.CameraSettings.ShutterSpeed mapDjiShutter(String name) {
        if (name.contains("2000")) return com.dronefly.app.model.CameraSettings.ShutterSpeed.S_1_2000;
        if (name.contains("1600")) return com.dronefly.app.model.CameraSettings.ShutterSpeed.S_1_1600;
        if (name.contains("1250")) return com.dronefly.app.model.CameraSettings.ShutterSpeed.S_1_1250;
        if (name.contains("1000")) return com.dronefly.app.model.CameraSettings.ShutterSpeed.S_1_1000;
        if (name.contains("640"))  return com.dronefly.app.model.CameraSettings.ShutterSpeed.S_1_640;
        if (name.contains("500"))  return com.dronefly.app.model.CameraSettings.ShutterSpeed.S_1_500;
        return com.dronefly.app.model.CameraSettings.ShutterSpeed.S_1_800;
    }

    private static com.dronefly.app.model.CameraSettings.ApertureValue mapDjiAperture(String name) {
        if (name.contains("5"))  return com.dronefly.app.model.CameraSettings.ApertureValue.F_5_6;
        if (name.contains("11")) return com.dronefly.app.model.CameraSettings.ApertureValue.F_11;
        return com.dronefly.app.model.CameraSettings.ApertureValue.F_8;
    }

    // ── Hisztogram ────────────────────────────────────────────────────

    public interface HistogramListener {
        void onUpdate(int[] data256);
    }

    /**
     * Diagnosztikai szöveg — a panel státusz mezőjébe írjuk, nem logcatba.
     * Megmutatja: kamera null-e, milyen histogram metódusokat talált.
     */
    public static String getHistogramDiagnostic() {
        Camera camera = getCamera();
        if (camera == null) return "Kamera: null (drón nincs csatlakoztatva?)";
        StringBuilder sb = new StringBuilder("Kamera: " + camera.getClass().getSimpleName() + " | ");
        int found = 0;
        for (java.lang.reflect.Method m : camera.getClass().getMethods()) {
            if (m.getName().toLowerCase().contains("histogram")) {
                sb.append(m.getName()).append("(").append(m.getParameterTypes().length).append(") ");
                found++;
            }
        }
        if (found == 0) sb.append("NEM talált histogram metódust!");
        return sb.toString();
    }

    public static void startHistogram(HistogramListener listener) {
        Camera camera = getCamera();
        if (camera == null) { Log.w(TAG, "Histogram: kamera null"); return; }

        // 1. CALLBACK beállítása ELŐSZÖR — az enable előtt kell, hogy ne maradjon ki adat
        boolean callbackSet = false;
        for (java.lang.reflect.Method setter : camera.getClass().getMethods()) {
            String sn = setter.getName().toLowerCase();
            if (!sn.contains("histogram") || !sn.contains("callback")
                    || setter.getParameterCount() != 1) continue;

            Class<?> cbType = setter.getParameterTypes()[0];
            if (cbType == null || cbType.isPrimitive()) continue;

            if (!cbType.isInterface()) {
                Log.w(TAG, "Histogram cb nem interface: " + cbType.getSimpleName() + " — skip");
                continue;
            }

            // Rugalmas metódus keresés: int[] paraméter VAGY bármilyen egyetlen paraméter
            java.lang.reflect.Method cbMethod = null;
            for (java.lang.reflect.Method cm : cbType.getMethods()) {
                if (cm.getParameterCount() == 1 && cm.getParameterTypes()[0] == int[].class) {
                    cbMethod = cm;
                    break;
                }
            }
            if (cbMethod == null) {
                // Fallback: első nem-Object metódus elfogadva
                for (java.lang.reflect.Method cm : cbType.getMethods()) {
                    if (cm.getParameterCount() == 1
                            && cm.getDeclaringClass() != Object.class) {
                        cbMethod = cm;
                        Log.w(TAG, "Histogram: int[] nem talált, fallback: "
                                + cm.getName() + "(" + cm.getParameterTypes()[0].getSimpleName() + ")");
                        break;
                    }
                }
            }
            if (cbMethod == null) {
                Log.w(TAG, "Histogram cbMethod nem található: " + cbType.getSimpleName());
                continue;
            }

            final java.lang.reflect.Method finalCb = cbMethod;
            final boolean isIntArray = cbMethod.getParameterTypes()[0] == int[].class;
            try {
                Object proxy = java.lang.reflect.Proxy.newProxyInstance(
                        cbType.getClassLoader(), new Class[]{cbType},
                        (p, method, args) -> {
                            if (method.getName().equals(finalCb.getName())
                                    && args != null && args.length == 1) {
                                if (isIntArray && args[0] instanceof int[]) {
                                    listener.onUpdate((int[]) args[0]);
                                } else if (!isIntArray) {
                                    Log.w(TAG, "Histogram: váratlan típus "
                                            + (args[0] != null ? args[0].getClass().getSimpleName() : "null"));
                                }
                            }
                            return null;
                        });
                setter.invoke(camera, proxy);
                callbackSet = true;
                Log.i(TAG, "Histogram callback beállítva: " + setter.getName() + "/" + cbMethod.getName());
                break;
            } catch (Throwable t) {
                Log.w(TAG, "Histogram proxy hiba: " + t);
            }
        }
        if (!callbackSet) Log.w(TAG, "Histogram: callback NEM beállítva");

        // 2. ENABLE — a callback után; CompletionCallback-ot logoljuk
        for (java.lang.reflect.Method m : camera.getClass().getMethods()) {
            String n = m.getName().toLowerCase();
            if (!n.contains("histogram") || !n.contains("enable")) continue;
            Class<?>[] params = m.getParameterTypes();
            if (params.length < 1 || params[0] != boolean.class) continue;

            try {
                if (params.length == 1) {
                    m.invoke(camera, true);
                    Log.i(TAG, "Histogram enable(1) OK");
                } else {
                    // 2. param: CompletionCallback — proxy-zuk, hogy lássuk az eredményt
                    Class<?> compCbType = params[1];
                    if (compCbType != null && compCbType.isInterface()) {
                        java.lang.reflect.Method compMethod = null;
                        for (java.lang.reflect.Method cm : compCbType.getMethods()) {
                            if (cm.getParameterCount() == 1
                                    && cm.getDeclaringClass() != Object.class) {
                                compMethod = cm;
                                break;
                            }
                        }
                        if (compMethod != null) {
                            final java.lang.reflect.Method finalComp = compMethod;
                            Object compProxy = java.lang.reflect.Proxy.newProxyInstance(
                                    compCbType.getClassLoader(), new Class[]{compCbType},
                                    (p2, method2, args2) -> {
                                        if (method2.getName().equals(finalComp.getName())) {
                                            Object err = (args2 != null && args2.length > 0) ? args2[0] : null;
                                            Log.i(TAG, "Histogram enable eredmény: "
                                                    + (err == null ? "OK" : err.toString()));
                                        }
                                        return null;
                                    });
                            m.invoke(camera, true, compProxy);
                            Log.i(TAG, "Histogram enable(2) hívva");
                            break;
                        }
                    }
                    m.invoke(camera, true, (Object) null);
                    Log.i(TAG, "Histogram enable(2) null-callback-kal hívva");
                }
                break;
            } catch (Throwable t) {
                Log.w(TAG, "Histogram enable hiba: " + t);
            }
        }
    }

    public static void stopHistogram() {
        Camera camera = getCamera();
        if (camera == null) return;
        try {
            for (java.lang.reflect.Method setter : camera.getClass().getMethods()) {
                String sn = setter.getName();
                if (sn.contains("istogram") && sn.contains("allback")
                        && setter.getParameterTypes().length == 1) {
                    try { setter.invoke(camera, (Object) null); } catch (Throwable ignore) {}
                    break;
                }
            }
            for (java.lang.reflect.Method m : camera.getClass().getMethods()) {
                String n = m.getName();
                if (n.contains("istogram") && n.contains("nable")
                        && m.getParameterTypes().length == 2
                        && m.getParameterTypes()[0] == boolean.class) {
                    try { m.invoke(camera, false, null); } catch (Throwable ignore) {}
                    break;
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "Histogram stop hiba: " + t.getMessage());
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
