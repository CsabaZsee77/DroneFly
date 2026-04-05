# L3 – Állapotgép és Engine – DJI Integráció

**Modul:** M04
**Szint:** L3 – Állapotgép és Engine
**Verzió:** v1.2.0
**Létrehozva:** 2026-04-02
**Utolsó módosítás:** 2026-04-05
**Státusz:** 🔧 Stub + Reflection (Crystal Sky build) — MSDK v4 hívások Java reflection-alapú

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
    // → RemoteController → setChargeRemainingCallback(ChargeRemainingCallback)
    // → callback.getPercent()
    public void getRcBatteryPercent(BatteryCallback callback) { ... }

    // GPS műholdak + Home pont (Flight Controller state callback)
    // → FlightController → setStateCallback(FlightControllerState$Callback)
    // → state.getSatelliteCount(), state.isHomePointSet()
    public void setFlightStateCallback(GpsCallback callback) { ... }

    // DJI kapcsolat listener (M01 státusz frissítéshez)
    public void setListener(ConnectionListener listener) { ... }
}
```

**Reflection megközelítés magyarázata:**

A `dji-sdk-provided` stub nem tartalmazza az összes szükséges osztályt
(pl. `dji.sdk.aircraft.Aircraft`), ezért a Crystal Sky build esetén
Java reflection + dynamic proxy mechanizmussal hívjuk az MSDK v4 API-t:

```java
// Példa: akkumulátor callback beállítása reflection-nal
Class<?> batteryClass = Class.forName("dji.sdk.battery.Battery");
Class<?> callbackClass = Class.forName(
    "dji.sdk.battery.Battery$BatteryStateCallback");
Object proxy = Proxy.newProxyInstance(
    callbackClass.getClassLoader(),
    new Class[]{callbackClass},
    (p, method, args) -> {
        if ("onUpdate".equals(method.getName()) && args != null) {
            // state objektumból getChargeRemainingInPercent()
            Method pctMethod = args[0].getClass()
                .getMethod("getChargeRemainingInPercent");
            int pct = (int) pctMethod.invoke(args[0]);
            callback.onResult(pct);
        }
        return null;
    });
// Battery.setBatteryStateCallback(proxy)
batteryClass.getMethod("setBatteryStateCallback", callbackClass)
    .invoke(batteryInstance, proxy);
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
