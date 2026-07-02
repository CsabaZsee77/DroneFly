# L3 – Állapotgép és Engine – DJI Integráció

**Modul:** M04
**Szint:** L3 – Állapotgép és Engine
**Verzió:** v1.9.7
**Létrehozva:** 2026-04-02
**Utolsó módosítás:** 2026-04-17
**Státusz:** ✅ Implementálva — RC akku, drón akku, drón név, GPS, isFlying, kamera feed PiP, tap-to-expose (reflection); misszió feltöltés valódi MSDK; folyamatos repülés (CURVED); gimbal nadir; SD kártya ellenőrzés; képernyőkép/videó rögzítés (v1.9.7)

---

## Forrásfájlok

| Fájl | Szerepkör | Jelenlegi státusz |
|------|-----------|-------------------|
| `dji/DJIHelper.java` | SDK regisztráció, telemetria (reflection) | ✅ Crystal Sky-on működik |
| `dji/DroneVideoWidget.java` | Kamera feed PiP, tap-to-expose | ✅ Crystal Sky-on működik |
| `dji/MissionUploader.java` | Waypoint misszió feltöltés, vezérlés (CURVED mód) | ✅ Valódi MSDK v4 implementáció |
| `dji/CameraConfigurator.java` | Kamera beállítások alkalmazása | ✅ Implementálva |
| `App.java` | Application osztály, MultiDex | MultiDex only |

---

## MissionUploader — stub implementáció (jelenlegi)

```java
public class MissionUploader {

    public interface UploadCallback {
        void onSuccess();
        void onError(String message);
    }

    public void uploadMission(List<WaypointData> waypoints,
                              MissionConfig config, UploadCallback callback) {
        if (callback != null)
            callback.onError("Stub build – drón nem csatlakoztatva");
    }

    public void startMission(UploadCallback callback) {
        if (callback != null)
            callback.onError("Stub build – drón nem csatlakoztatva");
    }

    public void pauseMission(UploadCallback callback) {
        if (callback != null)
            callback.onError("Stub build – drón nem csatlakoztatva");
    }

    public void resumeMission(UploadCallback callback) {
        if (callback != null)
            callback.onError("Stub build – drón nem csatlakoztatva");
    }

    public void stopMission(UploadCallback callback) {
        if (callback != null)
            callback.onError("Stub build – drón nem csatlakoztatva");
    }
}
```

---

## MissionUploader — valódi MSDK v4 implementáció (Crystal Sky buildhez)

```java
public class MissionUploader {

    private WaypointMissionOperator getOperator() {
        return MissionControl.getInstance().getWaypointMissionOperator();
    }

    public void uploadMission(List<WaypointData> waypoints,
                              MissionConfig config,
                              UploadCallback callback) {

        WaypointMission.Builder builder = new WaypointMission.Builder();
        builder.waypointCount(waypoints.size())
               .maxFlightSpeed(config.speedMs)
               .autoFlightSpeed(config.speedMs)
               .finishedAction(config.returnHome
                   ? WaypointMissionFinishedAction.RETURN_TO_HOME
                   : WaypointMissionFinishedAction.NO_ACTION)
               .headingMode(WaypointMissionHeadingMode.AUTO)
               .flightPathMode(WaypointMissionFlightPathMode.CURVED)
               .gotoFirstWaypointMode(WaypointMissionGotoWaypointMode.SAFELY)
               .exitMissionOnRCSignalLostEnabled(false);

        for (WaypointData wp : waypoints) {
            // CURVED módban waypoint akciók figyelmen kívül maradnak —
            // fotót a kamera intervallum triggereli, gimbalt setNadirPitch() kezeli
            Waypoint waypoint = new Waypoint((float) wp.latitude,
                                             (float) wp.longitude,
                                             wp.altitudeM);
            waypoint.cornerRadiusInMeters = 0.2f; // szoros kanyar sávváltásnál
            builder.addWaypoint(waypoint);
        }

        WaypointMission mission = builder.build();
        DJIError loadError = getOperator().loadMission(mission);
        if (loadError != null) {
            if (callback != null)
                callback.onError("Misszió betöltési hiba: "
                                 + loadError.getDescription());
            return;
        }

        getOperator().uploadMission(djiError -> {
            if (djiError == null) {
                if (callback != null) callback.onSuccess();
            } else {
                if (callback != null)
                    callback.onError("Feltöltési hiba: "
                                     + djiError.getDescription());
            }
        });
    }

    public void startMission(UploadCallback callback) {
        getOperator().startMission(djiError -> {
            if (djiError == null) {
                if (callback != null) callback.onSuccess();
            } else {
                if (callback != null)
                    callback.onError("Start hiba: " + djiError.getDescription());
            }
        });
    }

    public void pauseMission(UploadCallback callback) {
        getOperator().pauseMission(djiError -> {
            if (djiError == null) {
                if (callback != null) callback.onSuccess();
            } else {
                if (callback != null)
                    callback.onError("Szünet hiba: " + djiError.getDescription());
            }
        });
    }

    public void resumeMission(UploadCallback callback) {
        getOperator().resumeMission(djiError -> {
            if (djiError == null) {
                if (callback != null) callback.onSuccess();
            } else {
                if (callback != null)
                    callback.onError("Folytatás hiba: " + djiError.getDescription());
            }
        });
    }

    public void stopMission(UploadCallback callback) {
        getOperator().stopMission(djiError -> {
            if (djiError == null) {
                if (callback != null) callback.onSuccess();
            } else {
                if (callback != null)
                    callback.onError("Stop hiba: " + djiError.getDescription());
            }
        });
    }
}
```

---

## DJIHelper — telemetria API (Crystal Sky build, reflection-alapú)

```java
public class DJIHelper {

    // Callback interfészek (telemetria visszahívásokhoz)
    public interface BatteryCallback { void onResult(int percent); }
    public interface GpsCallback { void onResult(int satelliteCount, boolean homeSet, double latitude, double longitude); }
    public interface ConnectionListener {
        void onRegistered(boolean success, String message);
        void onProductConnected(String productName);
        void onProductDisconnected();
    }

    // Singleton
    public static DJIHelper getInstance() { ... }

    // Kapcsolat és repülési állapot
    public boolean isConnected() { ... }
    public boolean isRegistered() { ... }
    public boolean isFlying() { return droneIsFlying; }  // volatile, szálbiztos
    public String getConnectedProductName() { ... }

    // RC kapcsolat (reflection: Aircraft → getRemoteController().isConnected())
    public boolean isRcConnected() { ... }

    // Drón akkumulátor (async, reflection + dynamic proxy)
    // → dji.sdk.battery.Battery → getBatteryState(BatteryState$Callback)
    // → callback.getChargeRemainingInPercent()
    public void getDroneBatteryPercent(BatteryCallback callback) { ... }

    // RC akkumulátor (async, reflection + dynamic proxy)
    // Crystal Sky-on (MSDK v4.18, P4P v1):
    //   RC class: dji.sdk.remotecontroller.uio (obfuszkált)
    //   Callback class: dji.common.remotecontroller.BatteryState$Callback
    //   Charge method: getRemainingChargeInPercent()
    // A callback osztályneve NEM hardcode-olt — a setter paramétertípusából
    // kérdezi le reflexióval, így SDK verzióváltásra robusztus.
    public void getRcBatteryPercent(BatteryCallback callback) { ... }

    // GPS műholdak + Home pont + repülési állapot (Flight Controller state callback)
    // Dinamikus osztálylekérés: setStateCallback() paramétertípusából reflexióval.
    // → state.getSatelliteCount(), state.isHomePointSet(), state.isFlying()
    // Az isFlying() eredménye a droneIsFlying volatile mezőbe kerül (szálbiztos).
    public void setFlightStateCallback(GpsCallback callback) { ... }

    // Repülési állapot lekérdezése (szinkron, volatile cache alapján)
    // true  = drón levegőben van (utolsó FlightController callback alapján)
    // false = földön, vagy SDK nem elérhető (biztonságos alapértelmezés)
    public boolean isFlying() { return droneIsFlying; }

    // DJI kapcsolat listener (M01 státusz frissítéshez)
    public void setListener(ConnectionListener listener) { ... }
}
```

**Reflection megközelítés magyarázata:**

A `dji-sdk-provided` stub nem tartalmazza az összes szükséges osztályt
(pl. `dji.sdk.aircraft.Aircraft`), ezért a Crystal Sky build esetén
Java reflection + dynamic proxy mechanizmussal hívjuk az MSDK v4 API-t.

**Kulcstanulság (Crystal Sky, MSDK v4.18 debugolásból):**
Az MSDK belső osztályai obfuszkáltak (pl. `dji.sdk.remotecontroller.uio`),
és az osztályok neve SDK verzióváltáskor változhat. Ezért a callback
osztálynevét **nem hardcode-oljuk**, hanem a setter metódus paramétertípusából
kérdezzük le dinamikusan:

```java
// Dinamikus callback osztálylekérés — SDK verzióra robusztus
for (Method setter : rcObject.getClass().getMethods()) {
    if ("setChargeRemainingCallback".equals(setter.getName())
            && setter.getParameterTypes().length == 1) {
        Class<?> callbackClass = setter.getParameterTypes()[0];
        // callbackClass = dji.common.remotecontroller.BatteryState$Callback
        // (Crystal Sky-on, MSDK v4.18, P4P v1)
        Object proxy = Proxy.newProxyInstance(
            getClass().getClassLoader(),
            new Class[]{callbackClass},
            (p, method, args) -> {
                if (args != null && args.length > 0) {
                    // BatteryState.getRemainingChargeInPercent() — NEM getPercent()!
                    int pct = (int) args[0].getClass()
                        .getMethod("getRemainingChargeInPercent").invoke(args[0]);
                    callback.onResult(pct);
                }
                return null;
            });
        setter.invoke(rcObject, proxy);
    }
}
```

**Ismert osztályok Crystal Sky MSDK v4.18, P4P v1 eszközön:**

| Komponens | Osztály | Megjegyzés |
|-----------|---------|------------|
| RemoteController | `dji.sdk.remotecontroller.uio` | Obfuszkált |
| RC Callback | `dji.common.remotecontroller.BatteryState$Callback` | Publikus |
| RC töltöttség | `getRemainingChargeInPercent()` | NEM `getPercent()` |
| Drón Battery Callback | `dji.common.battery.BatteryState$Callback` | |
| Drón töltöttség | `getChargeRemainingInPercent()` | |

---

## DJI Misszió Operator állapotok (MSDK v4)

```
WaypointMissionState:
  DISCONNECTED           → Drón nem csatlakoztatva
  NOT_SUPPORTED          → Nem waypoint-kompatibilis drón
  READY_TO_UPLOAD        → Misszió betölthető / feltölthető
  UPLOADING              → Feltöltés folyamatban
  READY_TO_EXECUTE       → Feltöltve, indításra kész
  EXECUTING              → Misszió fut
  EXECUTION_PAUSED       → Szüneteltetve (drón lebeg)
  RECOVERING             → Újracsatlakozás után helyreállítás
```

---

## App.java — aktiváláshoz szükséges módosítás

```java
// Stub (jelenlegi):
public class App extends MultiDexApplication {
    @Override
    public void onCreate() {
        super.onCreate();
        MultiDex.install(this);
    }
}

// Valódi eszközhöz (Crystal Sky build):
public class App extends MultiDexApplication {
    @Override
    public void onCreate() {
        super.onCreate();
        MultiDex.install(this);
        Helper.install(this);  // DJI SDK inicializálás
    }
}
```

---

---

## DroneVideoWidget — kamera feed + tap-to-expose (Crystal Sky build)

```java
public class DroneVideoWidget implements TextureView.SurfaceTextureListener {

    // Reflexióval kezelt MSDK objektumok (emulátoros build kompatibilitás)
    private Object codecManager;       // dji.sdk.codec.DJICodecManager
    private Object videoDataListener;  // dji.sdk.camera.VideoFeeder.VideoDataListener
    private Object videoFeed;          // dji.sdk.camera.VideoFeeder.VideoFeed
    private boolean running = false;

    // Publikus API
    public void start()   { running = true;  /* attachCodecAndFeed ha surface kész */ }
    public void stop()    { running = false; detachFeed(); releaseCodec(); }
    public void destroy() { stop(); textureView.setSurfaceTextureListener(null); }
    public boolean isRunning() { return running; }

    // Tap-to-expose: reflection-alapú MSDK hívás
    // normalizedX/Y: 0.0–1.0, TextureView-n belüli relatív pozíció
    public void tapToFocus(float normalizedX, float normalizedY) {
        // 1. getCamera() → reflection
        // 2. Camera.setFocusMode(FocusMode.AUTO) → proxy callback
        // 3. callback: setFocusTarget(PointF(nx, ny)) → reflection
        //    → P4P v1-en: expozíció az érintett pontra (fix fókusz!)
    }
}
```

**Proxy pattern (minden MSDK callback):**
```java
// Kötelező: hashCode/equals/toString explicit kezelése!
// Az MSDK Set gyűjteménybe teszi a callbackeket → hashCode()-ot hív.
// null visszatérés int-re unboxolhatatlan → NPE.
(proxy, method, args) -> {
    switch (method.getName()) {
        case "hashCode": return System.identityHashCode(proxy);
        case "equals":   return proxy == (args != null ? args[0] : null);
        case "toString": return "DroneVideoWidget$VideoDataListener";
    }
    // ... tényleges callback logika ...
    return null;
}
```

**Ismert MSDK osztályok (Crystal Sky, MSDK v4.18, P4P v1):**

| Osztály | Elérés |
|---------|--------|
| `dji.sdk.codec.DJICodecManager` | Constructor: (Context, SurfaceTexture, int, int) |
| `dji.sdk.camera.VideoFeeder` | `getInstance()` → `getPrimaryVideoFeed()` |
| `VideoFeeder.VideoDataListener` | `onReceive(byte[], int)` |
| Camera (reflection) | `product.getCamera()` |
| `SettingsDefinitions$FocusMode` | `.AUTO` field |
| `setFocusMode(FocusMode, CompletionCallback)` | dinamikus metóduskeresés |
| `setFocusTarget(PointF, CompletionCallback)` | dinamikus metóduskeresés |

---

## CameraConfigurator — intervallum fotózás, gimbal nadir, SD ellenőrzés (v1.9.4)

```java
public class CameraConfigurator {

    public interface ConfigCallback {
        void onComplete(boolean success, String message);
    }

    // ── Kamera beállítások ────────────────────────────────────────────
    // Sorrend: setMode(SHOOT_PHOTO) → setExposureMode → setISO →
    //          setShutterSpeed → setWhiteBalance → setPhotoFileFormat →
    //          setShootPhotoMode(SINGLE/INTERVAL)
    public static void applySettings(CameraSettings settings, ConfigCallback callback) { ... }

    // ── Gimbal nadir (-90°) survey fotózás előtt ──────────────────────
    // A callback mindig true-val tér vissza — gimbal hiba NEM blokkolja a missziót.
    public static void setNadirPitch(ConfigCallback callback) {
        // product.getGimbal().rotate(
        //   new Rotation.Builder().pitch(-90f)
        //     .mode(RotationMode.ABSOLUTE_ANGLE).time(3.0).build(),
        //   callback)
    }

    // ── SD kártya jelenlét ellenőrzése ────────────────────────────────
    // callback(true)  = van kártya VAGY nem sikerült lekérdezni → folytatható
    // callback(false) = biztosan NINCS kártya → UI figyelmeztetés szükséges
    public static void checkSDCard(ConfigCallback callback) {
        // camera.setSystemStateCallback(state -> {
        //   camera.setSystemStateCallback(null);  // egyszeri lekérés
        //   boolean inserted = (boolean) state.getClass()
        //       .getMethod("isSDCardInserted").invoke(state);  // reflection
        // });
    }

    // ── Intervallum fotózás indítása (survey repülés) ─────────────────
    // intervalSec = max(2.0, photoDistM / speedMs)
    // 1. setMode(SHOOT_PHOTO)
    // 2. setShootPhotoMode(INTERVAL)
    // 3. PhotoTimeIntervalSettings(captureCount=0, intervalSec) [reflection]
    //    → Class.forName("dji.common.camera.PhotoTimeIntervalSettings")
    //    → setPhotoTimeIntervalSettings(settings, callback) [reflection]
    //    Fallback: reflection sikertelen → startShootPhoto() közvetlen hívás
    // 4. startShootPhoto()
    public static void startIntervalShooting(float photoDistM, float speedMs,
                                              ConfigCallback callback) { ... }

    // Helper: startShootPhoto + callback kezelés
    private static void startShootPhotoInternal(Camera camera, float intervalSec,
                                                  ConfigCallback callback) { ... }

    // ── Intervallum fotózás leállítása ────────────────────────────────
    // Camera.stopShootPhoto() — misszió vége / stop / setMissionRunning(false) után
    public static void stopIntervalShooting() { ... }
}
```

**Gimbal API (MSDK v4):**

| Osztály | Csomag |
|---------|--------|
| `Gimbal` | `dji.sdk.gimbal.Gimbal` |
| `Rotation.Builder` | `dji.common.gimbal.Rotation` |
| `RotationMode.ABSOLUTE_ANGLE` | `dji.common.gimbal.RotationMode` |

**PhotoTimeIntervalSettings — reflection szükséges:**

Az osztály (`dji.common.camera.PhotoTimeIntervalSettings`) neve MSDK verzióktól függ — direkt import
helyett reflection garantálja a kompatibilitást:

```java
Class<?> cls = Class.forName("dji.common.camera.PhotoTimeIntervalSettings");
Object settings = cls.getConstructor(int.class, float.class)
                     .newInstance(0, intervalSec); // captureCount=0 = végtelen
```

**SD kártya ellenőrzés — reflection + egyszeri callback:**

```java
camera.setSystemStateCallback(state -> {
    camera.setSystemStateCallback(null);  // leiratkozás az első hívás után
    boolean inserted = (boolean) state.getClass()
            .getMethod("isSDCardInserted").invoke(state);
});
```

**Misszió start szekvencia (MissionPlannerActivity.doLaunchMission):**

```
checkSDCard → (dialog ha nincs) → setNadirPitch → applySettings
           → startIntervalShooting → MissionUploader.startMission
```

---

## MissionUploader — mintavételi kiegészítés (uploadSamplingMission) — 🔲 Tervezve

**Forrásfájl-bővítés (tervezett):** `dji/MissionUploader.java` — új publikus metódus,
a meglévő `uploadMission()` mellett, NEM annak lecserélése.

```java
public class MissionUploader {

    // ... meglévő uploadMission() (CURVED, grid misszió) VÁLTOZATLAN ...

    /**
     * Mintavételi misszió feltöltése — NORMAL flightPathMode, waypoint-akciókkal.
     * A waypoints listában a hoverSeconds > 0 elemek kapnak STAY + START_TAKE_PHOTO
     * akciót; a hoverSeconds == 0 elemek (transit ki/be) akció nélküli áthaladási pontok.
     */
    public void uploadSamplingMission(List<WaypointData> waypoints, MissionConfig config,
                                      UploadCallback callback) {
        WaypointMissionOperator operator = getOperator();
        if (operator == null) {
            if (callback != null) callback.onError("DJI SDK nincs inicializálva");
            return;
        }

        List<Waypoint> wpList = new ArrayList<>();
        for (WaypointData wp : waypoints) {
            Waypoint waypoint = new Waypoint((float) wp.latitude, (float) wp.longitude,
                                             wp.altitudeM);
            if (wp.hoverSeconds > 0f) {
                waypoint.addAction(new WaypointAction(
                        WaypointActionType.STAY, (int) (wp.hoverSeconds * 1000)));
                waypoint.addAction(new WaypointAction(
                        WaypointActionType.START_TAKE_PHOTO, 0));
            }
            wpList.add(waypoint);
        }

        WaypointMission.Builder builder = new WaypointMission.Builder();
        builder.waypointList(wpList)
               .waypointCount(wpList.size())
               .maxFlightSpeed(Math.min(15f, Math.max(2f, config.speedMs)))
               .autoFlightSpeed(Math.min(15f, Math.max(2f, config.speedMs)))
               .finishedAction(config.returnHome
                       ? WaypointMissionFinishedAction.GO_HOME
                       : WaypointMissionFinishedAction.NO_ACTION)
               .headingMode(WaypointMissionHeadingMode.AUTO)
               .flightPathMode(WaypointMissionFlightPathMode.NORMAL)  // ← eltérés!
               .gotoFirstWaypointMode(WaypointMissionGotoWaypointMode.SAFELY);

        DJIError loadError = operator.loadMission(builder.build());
        if (loadError != null) {
            if (callback != null)
                callback.onError("Misszió betöltési hiba: " + loadError.getDescription());
            return;
        }
        operator.uploadMission(djiError -> {
            if (djiError == null) { if (callback != null) callback.onSuccess(); }
            else if (callback != null)
                callback.onError("Feltöltési hiba: " + djiError.getDescription());
        });
    }
}
```

**Kameramód-előkészítés mintavételi misszióhoz** (a `doLaunchMission()` szekvencia
mintavételi-módú változata, `CameraConfigurator` kiegészítéssel):

```
checkSDCard → setNadirPitch → applySettings
           → setShootPhotoMode(SINGLE)     ← INTERVAL helyett
           → (NINCS startIntervalShooting hívás — a fotó a waypoint-akció felelőssége)
           → MissionUploader.uploadSamplingMission(...) → startMission
```

**`WaypointActionType.STAY` viselkedés (MSDK v4):** a paraméter ezred­másodpercben
(int) adja meg a hover időtartamát; a drón a megadott ideig lebeg a waypoint
pozíciójában, mielőtt a következő akciót (vagy a következő waypointra indulást)
végrehajtaná. A `START_TAKE_PHOTO` paramétere nem használt (0).

---

## MediaSessionDownloader — session-alapú médialetöltés (🔲 Tervezve)

**Forrásfájl (tervezett):** `dji/MediaSessionDownloader.java`

**Fontos architekturális megjegyzés:** a `MediaManager`/`MediaFile` a DJI MSDK v4
**publikus, stabil, nem obfuszkált** API-ja (`dji.sdk.media.MediaManager`,
`dji.sdk.media.MediaFile`) — ezekhez **nem szükséges** a projekt más részein
(RC akkumulátor, gimbal) alkalmazott reflection-alapú megközelítés. Direkt import
és hívás használható, ami egyszerűbb és megbízhatóbb kódot eredményez.

```java
public class MediaSessionDownloader {

    public interface SessionDownloadListener {
        void onFileProgress(int fileIndex, int totalFiles, long current, long total);
        void onFileComplete(int fileIndex, String localPath);
        void onFileError(int fileIndex, String error);
        void onSessionComplete(int successCount, int totalCount, File sessionJsonFile);
    }

    /**
     * @param sampleCount          a session mintapontjainak száma (N)
     * @param missionStartTimeMs   a misszió indításának időbélyege (sanity check-hez)
     * @param samplePoints         mintapontok (lat, lon) sorrendben — a session.json-hoz
     * @param sessionId            egyedi azonosító (pl. dátum+idő alapú)
     */
    public void downloadSessionMedia(int sampleCount, long missionStartTimeMs,
                                     List<GeoPoint> samplePoints, String sessionId,
                                     SessionDownloadListener listener) {

        Camera camera = getCamera(); // meglévő getProduct().getCamera() minta
        if (camera == null) { /* hiba */ return; }

        // 1. Kameramód váltás — csak MEDIA_DOWNLOAD módban él a MediaManager
        camera.setMode(SettingsDefinitions.CameraMode.MEDIA_DOWNLOAD, djiError -> {
            if (djiError != null) { /* onFileError összegzés */ return; }

            MediaManager mediaManager = camera.getMediaManager();

            // 2. Fájllista frissítés
            mediaManager.refreshFileListOfStorageLocation(
                    SettingsDefinitions.StorageLocation.SDCARD, refreshError -> {
                if (refreshError != null) { /* hiba */ return; }

                // 3. Pillanatkép lekérése, időrend szerint rendezve
                List<MediaFile> allFiles = mediaManager.getSDCardFileListSnapshot();
                allFiles.sort((a, b) -> a.getTimeCreated().compareTo(b.getTimeCreated()));

                // 4. Utolsó N fájl — determinisztikus 1:1 megfeleltetés (M04 §15)
                int total = allFiles.size();
                if (total < sampleCount) { /* onFileError: kevesebb fájl, mint várt */ return; }
                List<MediaFile> selected = allFiles.subList(total - sampleCount, total);

                // Sanity check — nem blokkoló, csak figyelmeztetés a listenerben
                for (MediaFile f : selected) {
                    if (f.getTimeCreated().getTime() < missionStartTimeMs) {
                        // onSessionWarning(...) — a misszió kezdete előtti fájl a válogatásban
                    }
                }

                // 5. Letöltési ciklus (szekvenciális — egyszerre 1 fájl, MSDK ajánlás)
                downloadNext(selected, 0, sessionId, samplePoints, sampleCount,
                             missionStartTimeMs, listener, camera);
            });
        });
    }

    private void downloadNext(List<MediaFile> files, int index, String sessionId,
                              List<GeoPoint> samplePoints, int sampleCount,
                              long missionStartTimeMs, SessionDownloadListener listener,
                              Camera camera) {
        if (index >= files.size()) {
            // Session JSON írása + kameramód visszaváltás SHOOT_PHOTO-ra
            File sessionJson = writeSessionJson(sessionId, samplePoints, files);
            camera.setMode(SettingsDefinitions.CameraMode.SHOOT_PHOTO, ignored -> {});
            listener.onSessionComplete(files.size(), sampleCount, sessionJson);
            return;
        }

        MediaFile mf = files.get(index);
        File localFile = new File(getSessionDir(sessionId),
                                  String.format("point_%03d.jpg", index));

        mf.fetchFileData(localFile, null, new DownloadListener<String>() {
            @Override
            public void onRateUpdate(long total, long current, long persize) {
                listener.onFileProgress(index, files.size(), current, total);
            }
            @Override
            public void onSuccess(String s) {
                listener.onFileComplete(index, localFile.getAbsolutePath());
                downloadNext(files, index + 1, sessionId, samplePoints, sampleCount,
                            missionStartTimeMs, listener, camera);
            }
            @Override
            public void onFailure(DJIError error) {
                listener.onFileError(index, error.getDescription());
                downloadNext(files, index + 1, sessionId, samplePoints, sampleCount,
                            missionStartTimeMs, listener, camera); // folytatás hiba esetén is
            }
        });
    }

    /** /sdcard/DroneFly/sessions/{sessionId}/ mappa létrehozása, ha nem létezik */
    private File getSessionDir(String sessionId) { ... }

    /** session.json írása a M04 L1 §16.1 6. lépésben leírt séma szerint */
    private File writeSessionJson(String sessionId, List<GeoPoint> samplePoints,
                                  List<MediaFile> downloadedFiles) { ... }
}
```

**Ismert MSDK osztályok a médialetöltéshez (v4.18):**

| Osztály | Csomag | Megjegyzés |
|---------|--------|-----------|
| `MediaManager` | `dji.sdk.media.MediaManager` | `camera.getMediaManager()` — csak MEDIA_DOWNLOAD módban nem-null |
| `MediaFile` | `dji.sdk.media.MediaFile` | `getFileName()`, `getTimeCreated()`, `fetchFileData()` |
| `SettingsDefinitions.CameraMode` | `dji.common.camera.SettingsDefinitions` | `.SHOOT_PHOTO`, `.MEDIA_DOWNLOAD` |
| `SettingsDefinitions.StorageLocation` | ua. | `.SDCARD` |
| `MediaManager.FetchMediaTaskContent.DownloadListener` | `dji.sdk.media.MediaManager` | `onRateUpdate/onProgress/onSuccess/onFailure` |

**Kameramód-váltás időzítési korlátja:** a `MEDIA_DOWNLOAD` mód csak akkor
kérhető, ha a misszió véglegesen befejeződött (`onMissionFinished`) — aktív
fotózás/repülés közben a váltás hibát ad vagy figyelmen kívül marad.

---

## build.gradle módosítás (Crystal Sky buildhez)

```groovy
// Uncomment ezeket (jelenleg kommentálva):
implementation('com.dji:dji-sdk:4.18') {
    exclude module: 'library-anti-distortion'
    exclude group: 'com.squareup.okhttp3'
}
compileOnly 'com.dji:dji-sdk-provided:4.18'
```
