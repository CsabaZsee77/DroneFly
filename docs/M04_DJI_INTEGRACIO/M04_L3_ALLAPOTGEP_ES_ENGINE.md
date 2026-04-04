# L3 – Állapotgép és Engine – DJI Integráció

**Modul:** M04
**Szint:** L3 – Állapotgép és Engine
**Verzió:** v1.0.0
**Létrehozva:** 2026-04-02
**Státusz:** 🔧 Stub (emulátor) — valódi implementáció a Crystal Sky buildhöz

---

## Forrásfájlok

| Fájl | Szerepkör | Jelenlegi státusz | Valódi eszközön |
|------|-----------|-------------------|-----------------|
| `dji/DJIHelper.java` | SDK regisztráció, csatlakozás figyelés | Stub (mindig false) | MSDK v4 implementáció |
| `dji/MissionUploader.java` | Waypoint misszió feltöltés, vezérlés | Stub (mindig hiba) | MSDK v4 implementáció |
| `App.java` | Application osztály, MultiDex | MultiDex only | DJIHelper.init() hívás |

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

## DJIHelper — stub implementáció (jelenlegi)

```java
public class DJIHelper {

    public interface InitListener {
        void onRegistered(boolean success, String message);
    }

    /** Mindig true az emulátor build esetén */
    public static boolean isEmulator() { return true; }

    public void init(Context context, InitListener listener) {
        if (listener != null)
            listener.onRegistered(false, "Stub build – DJI SDK nincs betöltve");
    }

    public boolean isConnected() { return false; }
}
```

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

## build.gradle módosítás (Crystal Sky buildhez)

```groovy
// Uncomment ezeket (jelenleg kommentálva):
implementation('com.dji:dji-sdk:4.18') {
    exclude module: 'library-anti-distortion'
    exclude group: 'com.squareup.okhttp3'
}
compileOnly 'com.dji:dji-sdk-provided:4.18'
```
