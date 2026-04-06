# L3 – Állapotgép és Engine – DJI Integráció

**Modul:** M04
**Szint:** L3 – Állapotgép és Engine
**Verzió:** v1.5.0
**Létrehozva:** 2026-04-02
**Utolsó módosítás:** 2026-04-06
**Státusz:** ✅ Részben implementálva — RC akku, drón akku, drón név, GPS, kamera feed PiP, tap-to-expose (reflection); misszió feltöltés stub

---

## Forrásfájlok

| Fájl | Szerepkör | Jelenlegi státusz |
|------|-----------|-------------------|
| `dji/DJIHelper.java` | SDK regisztráció, telemetria (reflection) | ✅ Crystal Sky-on működik |
| `dji/DroneVideoWidget.java` | Kamera feed PiP, tap-to-expose | ✅ Crystal Sky-on működik |
| `dji/MissionUploader.java` | Waypoint misszió feltöltés, vezérlés | 🔧 Stub (mindig hiba) |
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
               .flightPathMode(WaypointMissionFlightPathMode.NORMAL)
               .gotoFirstWaypointMode(WaypointMissionGotoWaypointMode.SAFELY)
               .exitMissionOnRCSignalLostEnabled(false);

        for (WaypointData wp : waypoints) {
            Waypoint waypoint = new Waypoint((float) wp.lat,
                                             (float) wp.lon,
                                             (float) wp.altitudeM);
            if (wp.shootPhoto) {
                waypoint.addAction(new WaypointAction(
                    WaypointActionType.START_TAKE_PHOTO, 0));
            }
            waypoint.addAction(new WaypointAction(
                WaypointActionType.GIMBAL_PITCH, (int) wp.gimbalPitch));
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
    public interface GpsCallback { void onResult(int satelliteCount, boolean homeSet); }
    public interface ConnectionListener {
        void onRegistered(boolean success, String message);
        void onProductConnected(String productName);
        void onProductDisconnected();
    }

    // Singleton
    public static DJIHelper getInstance() { ... }

    // Kapcsolat állapot
    public boolean isConnected() { ... }
    public boolean isRegistered() { ... }
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

    // GPS műholdak + Home pont (Flight Controller state callback)
    // Dinamikus osztálylekérés: setStateCallback() paramétertípusából reflexióval.
    // → state.getSatelliteCount(), state.isHomePointSet()
    public void setFlightStateCallback(GpsCallback callback) { ... }

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

## build.gradle módosítás (Crystal Sky buildhez)

```groovy
// Uncomment ezeket (jelenleg kommentálva):
implementation('com.dji:dji-sdk:4.18') {
    exclude module: 'library-anti-distortion'
    exclude group: 'com.squareup.okhttp3'
}
compileOnly 'com.dji:dji-sdk-provided:4.18'
```
