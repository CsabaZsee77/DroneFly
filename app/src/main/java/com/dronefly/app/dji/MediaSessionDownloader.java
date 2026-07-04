package com.dronefly.app.dji;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.osmdroid.util.GeoPoint;

import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import dji.common.camera.SettingsDefinitions;
import dji.common.error.DJIError;
import dji.sdk.base.BaseProduct;
import dji.sdk.camera.Camera;
import dji.sdk.media.DownloadListener;
import dji.sdk.media.MediaFile;
import dji.sdk.media.MediaManager;
import dji.sdk.sdkmanager.DJISDKManager;

/**
 * Session-alapú médialetöltés mintavételi misszió után (M04 §16).
 *
 * A MediaManager/MediaFile a DJI MSDK v4 publikus, stabil API-ja — nem
 * szükséges hozzá a projekt más részein (RC akku, gimbal) alkalmazott
 * reflection-alapú megközelítés.
 *
 * Determinisztikus 1:1 megfeleltetés (M04 §15): mivel a mintavételi misszió
 * minden waypontján pontosan egy fotó készül (START_TAKE_PHOTO akció, NORMAL
 * flightPathMode), a session utolsó N fájlja — időrendben — pontosan a
 * mintapontok sorrendjének felel meg. Nincs szükség fájlnév-parsingra vagy
 * GPS-koordináta egyeztetésre.
 *
 * FONTOS, terepi teszttel ellenőrizendő pont: a MediaFile.fetchFileData()
 * pontos viselkedése (hogy a subFolder paraméter és az onSuccess(String)
 * visszatérési érték pontosan mit jelent) csak dekompilált bájtkódból lett
 * megállapítva, hivatalos DJI dokumentáció nélkül — ezért a letöltött fájl
 * végleges nevét/helyét mindkét lehetséges eset kezelésével, defenzíven
 * állapítjuk meg (ld. resolveDownloadedFile()).
 */
public class MediaSessionDownloader {

    private static final String TAG = "MediaSessionDL";
    private static final String SESSIONS_DIR = "sampling_sessions";

    // Megszakíthatóság + beragadás-kezelés (M04_L1 §16.5, terepi teszt 2026-07-04)
    private final java.util.concurrent.atomic.AtomicBoolean cancelFlag =
            new java.util.concurrent.atomic.AtomicBoolean(false);
    private final android.os.Handler timeoutHandler =
            new android.os.Handler(android.os.Looper.getMainLooper());
    /** Egy fájlra várt max. idő ÚJABB progress nélkül — enélkül a hang beragad. */
    private static final long FILE_TIMEOUT_MS = 60000;

    /**
     * Folyamatban lévő letöltés megszakítása (Mégse gomb). A ciklus a következő
     * fájl előtt kilép, visszaállítja a kameramódot, és a letöltés újraindítható
     * (nincs app-újraindítás).
     */
    public void cancelDownload() {
        cancelFlag.set(true);
        timeoutHandler.removeCallbacksAndMessages(null);
    }

    public interface SessionDownloadListener {
        void onFileProgress(int fileIndex, int totalFiles, long current, long total);
        void onFileComplete(int fileIndex, String localPath);
        void onFileError(int fileIndex, String error);
        void onSessionWarning(String message);
        void onSessionComplete(int successCount, int totalCount, File sessionDir);
        void onSessionError(String message);
    }

    /** Drón SD kártya fotólistájának lekérése (M09 fotóimport). */
    public interface PhotoListListener {
        void onPhotoList(List<MediaFile> photos);
        void onError(String message);
    }

    /**
     * @param ctx               Android Context (az app-specifikus külső tárhelyhez)
     * @param sampleCount        a session mintapontjainak száma (N) — ennyi fájlt várunk
     * @param missionStartTimeMs a misszió indításának időbélyege (System.currentTimeMillis(),
     *                           sanity check-hez — nem blokkoló)
     * @param samplePoints       mintapontok (lat, lon) sorrendben — a session.json-hoz
     * @param sessionId          egyedi azonosító (pl. "20260702_143012")
     * @param sampleAltitudeM    a mintavételi (fotózási) magasság méterben — M09 előfeltétele,
     *                           enélkül a footprint terület nem számolható a tableten
     * @param droneProfileName   a misszióhoz használt DroneProfile.name (GSD-számításhoz)
     * @param aoiAreaM2          a teljes tábla (AOI) területe négyzetméterben
     */
    public void downloadSessionMedia(Context ctx, int sampleCount, long missionStartTimeMs,
                                     List<GeoPoint> samplePoints, String sessionId,
                                     double sampleAltitudeM, String droneProfileName,
                                     double aoiAreaM2, SessionDownloadListener listener) {
        Camera camera = getCamera();
        if (camera == null) {
            if (listener != null) listener.onSessionError("Kamera nem elérhető");
            return;
        }
        if (sampleCount <= 0) {
            if (listener != null) listener.onSessionError("Érvénytelen mintapontszám");
            return;
        }
        cancelFlag.set(false); // új letöltés — töröljük az esetleges korábbi megszakítást

        // 1. Kameramód váltás — a MediaManager csak MEDIA_DOWNLOAD módban él
        camera.setMode(SettingsDefinitions.CameraMode.MEDIA_DOWNLOAD, modeErr -> {
            if (modeErr != null) {
                if (listener != null)
                    listener.onSessionError("Kameramód váltási hiba: " + modeErr.getDescription());
                return;
            }

            MediaManager mediaManager = camera.getMediaManager();
            if (mediaManager == null) {
                if (listener != null) listener.onSessionError("MediaManager nem elérhető");
                return;
            }

            // 2. Fájllista frissítés
            mediaManager.refreshFileListOfStorageLocation(
                    SettingsDefinitions.StorageLocation.SDCARD, refreshErr -> {
                if (refreshErr != null) {
                    if (listener != null)
                        listener.onSessionError("Fájllista frissítési hiba: "
                                + refreshErr.getDescription());
                    return;
                }

                // 3. Pillanatkép, időrendben (capture time szerint növekvő)
                // Megjegyzés: Comparator.comparingLong() API 24-től érhető el natívan,
                // a Crystal Sky (Android 5.1, API 22) miatt kézzel írt Comparator kell.
                List<MediaFile> allFiles = new ArrayList<>(mediaManager.getSDCardFileListSnapshot());
                Collections.sort(allFiles, new Comparator<MediaFile>() {
                    @Override
                    public int compare(MediaFile a, MediaFile b) {
                        return Long.compare(a.getTimeCreated(), b.getTimeCreated());
                    }
                });

                int total = allFiles.size();
                if (total < sampleCount) {
                    if (listener != null)
                        listener.onSessionError("Csak " + total + " fájl található a kártyán, "
                                + sampleCount + " várt — ellenőrizd manuálisan.");
                    return;
                }

                // 4. Utolsó N fájl — determinisztikus 1:1 megfeleltetés (M04 §15)
                List<MediaFile> selected = new ArrayList<>(
                        allFiles.subList(total - sampleCount, total));

                for (MediaFile f : selected) {
                    if (f.getTimeCreated() < missionStartTimeMs) {
                        if (listener != null)
                            listener.onSessionWarning(
                                    "A(z) " + f.getFileName() + " fájl a misszió kezdete előtti "
                                    + "lehet — ellenőrizd manuálisan, hogy minden fájl ebből "
                                    + "a sessionből származik-e.");
                        break;
                    }
                }

                File sessionDir = getSessionDir(ctx, sessionId);
                if (!sessionDir.exists() && !sessionDir.mkdirs()) {
                    if (listener != null)
                        listener.onSessionError("Nem sikerült létrehozni a session mappát: "
                                + sessionDir.getAbsolutePath());
                    return;
                }

                downloadNext(camera, selected, 0, sessionDir, sessionId, samplePoints,
                             sampleAltitudeM, droneProfileName, aoiAreaM2, listener, 0);
            });
        });
    }

    // ── M09 fotóimport: fájllista + kiválasztott fájlok letöltése ─────────

    /**
     * A drón SD kártyáján lévő JPG fotók listája, időrendben csökkenő
     * sorrendben (legújabb elöl). A kameramódot MEDIA_DOWNLOAD-ra váltja
     * és úgy hagyja — a hívó felelőssége a letöltés után visszaváltani
     * (a downloadSelectedMedia() ezt megteszi a ciklus végén).
     */
    public void listSdCardPhotos(PhotoListListener listener) {
        Camera camera = getCamera();
        if (camera == null) {
            if (listener != null) listener.onError("Kamera nem elérhető — csatlakoztasd a drónt");
            return;
        }
        camera.setMode(SettingsDefinitions.CameraMode.MEDIA_DOWNLOAD, modeErr -> {
            if (modeErr != null) {
                if (listener != null)
                    listener.onError("Kameramód váltási hiba: " + modeErr.getDescription());
                return;
            }
            MediaManager mediaManager = camera.getMediaManager();
            if (mediaManager == null) {
                if (listener != null) listener.onError("MediaManager nem elérhető");
                return;
            }
            mediaManager.refreshFileListOfStorageLocation(
                    SettingsDefinitions.StorageLocation.SDCARD, refreshErr -> {
                if (refreshErr != null) {
                    if (listener != null)
                        listener.onError("Fájllista frissítési hiba: " + refreshErr.getDescription());
                    return;
                }
                List<MediaFile> photos = new ArrayList<>();
                for (MediaFile f : mediaManager.getSDCardFileListSnapshot()) {
                    String name = f.getFileName() != null
                            ? f.getFileName().toLowerCase(Locale.US) : "";
                    if (name.endsWith(".jpg") || name.endsWith(".jpeg")) photos.add(f);
                }
                Collections.sort(photos, new Comparator<MediaFile>() {
                    @Override
                    public int compare(MediaFile a, MediaFile b) {
                        return Long.compare(b.getTimeCreated(), a.getTimeCreated());
                    }
                });
                if (listener != null) listener.onPhotoList(photos);
            });
        });
    }

    /**
     * Felhasználó által kiválasztott fájlok letöltése egy session mappába
     * (M09 fotóimport). A session.json-t NEM írja meg — azt a hívó
     * (PhotoImportActivity) állítja össze a letöltött képek EXIF GPS
     * adataiból és a kiválasztott repülési terv kontextusából.
     */
    public void downloadSelectedMedia(Context ctx, List<MediaFile> files, String sessionId,
                                      SessionDownloadListener listener) {
        Camera camera = getCamera();
        if (camera == null) {
            if (listener != null) listener.onSessionError("Kamera nem elérhető");
            return;
        }
        if (files == null || files.isEmpty()) {
            if (listener != null) listener.onSessionError("Nincs kiválasztott fájl");
            return;
        }
        File sessionDir = getSessionDir(ctx, sessionId);
        if (!sessionDir.exists() && !sessionDir.mkdirs()) {
            if (listener != null)
                listener.onSessionError("Nem sikerült létrehozni a session mappát: "
                        + sessionDir.getAbsolutePath());
            return;
        }
        cancelFlag.set(false); // új letöltés
        // samplePoints = null → downloadNext nem ír session.json-t
        downloadNext(camera, files, 0, sessionDir, sessionId, null, 0, "", 0, listener, 0);
    }

    // ── Letöltési ciklus (szekvenciális) ────────────────────────────────

    private void downloadNext(Camera camera, List<MediaFile> files, int index,
                              File sessionDir, String sessionId, List<GeoPoint> samplePoints,
                              double sampleAltitudeM, String droneProfileName, double aoiAreaM2,
                              SessionDownloadListener listener, int successCount) {
        // Megszakítás (M04_L1 §16.5): a következő fájl előtt kilépünk, visszaállítjuk
        // a kameramódot, és a letöltés újraindítható (a hívó a Mégse gombbal).
        if (cancelFlag.get()) {
            timeoutHandler.removeCallbacksAndMessages(null);
            camera.setMode(SettingsDefinitions.CameraMode.SHOOT_PHOTO, ignored -> {});
            if (listener != null)
                listener.onSessionError("Letöltés megszakítva — újraindítható");
            return;
        }
        if (index >= files.size()) {
            // samplePoints == null: M09 fotóimport útvonal — a session.json-t a hívó írja
            if (samplePoints != null) {
                writeSessionJson(sessionDir, sessionId, samplePoints, files, successCount,
                                 sampleAltitudeM, droneProfileName, aoiAreaM2);
            }
            // Kameramód visszaváltás a következő misszióhoz
            camera.setMode(SettingsDefinitions.CameraMode.SHOOT_PHOTO, ignored -> {});
            if (listener != null)
                listener.onSessionComplete(successCount, files.size(), sessionDir);
            return;
        }

        MediaFile mf = files.get(index);
        final int idx = index;
        final String targetName = String.format(Locale.US, "point_%03d.jpg", idx);

        // Per-fájl "kész" őr + időtúllépés-watchdog (M04_L1 §16.5): ha X mp-en át
        // NINCS újabb progress és nincs siker/hiba callback (SDK-hang), a
        // watchdog hibaként léptet a következő fájlra — nem ragad be.
        final java.util.concurrent.atomic.AtomicBoolean done =
                new java.util.concurrent.atomic.AtomicBoolean(false);
        final Runnable watchdog = () -> {
            if (done.compareAndSet(false, true)) {
                Log.w(TAG, "Fájl időtúllépés (" + idx + ") — lépés a következőre");
                if (listener != null) listener.onFileError(idx, "Időtúllépés (nincs válasz)");
                downloadNext(camera, files, idx + 1, sessionDir, sessionId, samplePoints,
                             sampleAltitudeM, droneProfileName, aoiAreaM2, listener, successCount);
            }
        };
        timeoutHandler.postDelayed(watchdog, FILE_TIMEOUT_MS);

        mf.fetchFileData(sessionDir, null, new DownloadListener<String>() {
            @Override
            public void onStart() {}

            @Override
            public void onRateUpdate(long total, long current, long persize) {
                resetWatchdog(watchdog);
                if (listener != null) listener.onFileProgress(idx, files.size(), current, total);
            }

            @Override
            public void onRealtimeDataUpdate(byte[] bytes, long l, boolean b) {}

            @Override
            public void onProgress(long total, long current) {
                resetWatchdog(watchdog);
                if (listener != null) listener.onFileProgress(idx, files.size(), current, total);
            }

            @Override
            public void onSuccess(String s) {
                if (!done.compareAndSet(false, true)) return; // watchdog már lépett
                timeoutHandler.removeCallbacks(watchdog);
                File finalFile = resolveDownloadedFile(sessionDir, mf, s);
                File renamed = new File(sessionDir, targetName);
                boolean renameOk = finalFile != null && finalFile.exists()
                        && (finalFile.equals(renamed) || finalFile.renameTo(renamed));
                String localPath = renameOk ? renamed.getAbsolutePath()
                        : (finalFile != null ? finalFile.getAbsolutePath() : sessionDir.getAbsolutePath());
                if (!renameOk) {
                    Log.w(TAG, "Nem sikerült point_" + idx + " névre átnevezni — eredeti helyen marad: "
                            + localPath);
                }
                if (listener != null) listener.onFileComplete(idx, localPath);
                downloadNext(camera, files, idx + 1, sessionDir, sessionId, samplePoints,
                             sampleAltitudeM, droneProfileName, aoiAreaM2, listener, successCount + 1);
            }

            @Override
            public void onFailure(DJIError error) {
                if (!done.compareAndSet(false, true)) return; // watchdog már lépett
                timeoutHandler.removeCallbacks(watchdog);
                if (listener != null) listener.onFileError(idx, error.getDescription());
                downloadNext(camera, files, idx + 1, sessionDir, sessionId, samplePoints,
                             sampleAltitudeM, droneProfileName, aoiAreaM2, listener, successCount);
            }
        });
    }

    /** A watchdog időzítő újraindítása — progress esetén (a fájl él, nem hang). */
    private void resetWatchdog(Runnable watchdog) {
        timeoutHandler.removeCallbacks(watchdog);
        timeoutHandler.postDelayed(watchdog, FILE_TIMEOUT_MS);
    }

    /**
     * A fetchFileData(destDir, subFolder, listener) onSuccess(String) paraméterének
     * pontos jelentése (abszolút útvonal vagy a destDir-en belüli fájlnév) hivatalos
     * dokumentáció nélkül nem egyértelmű — mindkét esetet lekezeljük.
     */
    private File resolveDownloadedFile(File sessionDir, MediaFile mf, String onSuccessValue) {
        if (onSuccessValue != null) {
            File asAbsolute = new File(onSuccessValue);
            if (asAbsolute.isAbsolute() && asAbsolute.exists()) return asAbsolute;
            File asRelative = new File(sessionDir, onSuccessValue);
            if (asRelative.exists()) return asRelative;
        }
        // Fallback: a MediaFile saját (kamera által adott) fájlneve a sessionDir-ben
        File byOriginalName = new File(sessionDir, mf.getFileName());
        if (byOriginalName.exists()) return byOriginalName;
        return null;
    }

    // ── Session mappa + metaadat ────────────────────────────────────────

    private File getSessionDir(Context ctx, String sessionId) {
        return new File(getSessionsRootDir(ctx), sessionId);
    }

    /** A mintavételi session-ök gyökérmappája — M09 (SamplingResultsActivity) is ezt használja. */
    public static File getSessionsRootDir(Context ctx) {
        return new File(ctx.getExternalFilesDir(null), SESSIONS_DIR);
    }

    private void writeSessionJson(File sessionDir, String sessionId, List<GeoPoint> samplePoints,
                                  List<MediaFile> downloadedFiles, int successCount,
                                  double sampleAltitudeM, String droneProfileName,
                                  double aoiAreaM2) {
        try {
            JSONObject root = new JSONObject();
            root.put("session_id", sessionId);
            root.put("sample_count", samplePoints != null ? samplePoints.size() : 0);
            root.put("downloaded_count", successCount);
            SimpleDateFormat isoFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
            isoFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
            root.put("created_at", isoFormat.format(new java.util.Date()));

            // M09 előfeltétel (M09_L1 §6): footprint terület számításához szükséges adatok
            root.put("sample_altitude_m", sampleAltitudeM);
            root.put("drone_profile_name", droneProfileName != null ? droneProfileName : "");
            root.put("aoi_area_m2", aoiAreaM2);

            JSONArray points = new JSONArray();
            for (int i = 0; i < (samplePoints != null ? samplePoints.size() : 0); i++) {
                JSONObject p = new JSONObject();
                p.put("index", i);
                p.put("lat", samplePoints.get(i).getLatitude());
                p.put("lon", samplePoints.get(i).getLongitude());
                p.put("local_file", String.format(Locale.US, "point_%03d.jpg", i));
                if (i < downloadedFiles.size()) {
                    MediaFile mf = downloadedFiles.get(i);
                    p.put("original_dji_filename", mf.getFileName());
                    p.put("capture_time_ms", mf.getTimeCreated());
                }
                points.put(p);
            }
            root.put("points", points);

            File jsonFile = new File(sessionDir, "session.json");
            try (FileWriter writer = new FileWriter(jsonFile)) {
                writer.write(root.toString(2));
            }
        } catch (JSONException | java.io.IOException e) {
            Log.e(TAG, "session.json írási hiba: " + e.getMessage());
        }
    }

    // ── Segéd ────────────────────────────────────────────────────────────

    private Camera getCamera() {
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
