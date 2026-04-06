# L1 – Üzleti Folyamat – DJI Integráció

**Modul:** M04
**Szint:** L1 – Üzleti Folyamat
**Verzió:** v1.5.0
**Létrehozva:** 2026-04-02
**Utolsó módosítás:** 2026-04-06
**Státusz:** ✅ Részben implementálva — telemetria + kamera feed PiP + tap-to-expose működik Crystal Sky-on; misszió feltöltés stub

---

## 1. Modul célja

Az M04 modul a DJI Mobile SDK v4 integrációját valósítja meg, amelyen keresztül
a DroneFly app:
- **Feltölti** a generált waypoint missziót a Phantom 4 Pro v1-re
- **Elindítja** az automatikus repülést
- **Szünetelteti** (drón lebeg az aktuális pozícióban)
- **Folytatja** a szüneteltetett missziót
- **Leállítja** (kézi vezérlés átvehető)
- **Telemetriát jelenít meg** — RC és drón akkumulátor, GPS műholdak, drón neve

**Jelenlegi build állapot (v1.3.0):**

| Funkció | Státusz |
|---------|---------|
| DJI SDK regisztráció | ✅ Működik Crystal Sky-on |
| Drón csatlakozás érzékelés | ✅ Működik (onProductConnect + 2mp timer) |
| Drón neve (pl. "Phantom 4 Pro") | ✅ Működik |
| Drón akkumulátor % | ✅ Működik (BatteryState callback) |
| RC csatlakozás érzékelés | ✅ Működik (reflection) |
| RC akkumulátor % | ✅ Működik (BatteryState$Callback, getRemainingChargeInPercent) |
| GPS műholdak száma | ✅ Működik (FlightController StateCallback) |
| Misszió feltöltés / indítás | 🔧 Stub — valódi MSDK implementáció szükséges |
| Misszió pause / stop | 🔧 Stub |
| Kamera feed PiP (élő kép) | ✅ Implementálva (DroneVideoWidget) |
| Tap-to-expose (érintéses expozíció) | ✅ Implementálva (tapToFocus + fókuszgyűrű animáció) |

---

## 2. Szereplők

| Szereplő | Szerepkör |
|----------|-----------|
| MissionPlannerActivity | Misszió feltöltést és vezérlést kér (M01) |
| MissionUploader | MSDK v4 hívások kezelője |
| DJIHelper | SDK inicializálás, regisztráció |
| DJI MSDK v4 | Waypoint misszió végrehajtó a drón oldalán |
| Phantom 4 Pro v1 | A fizikai drón — RC + Crystal Sky csatlakoztatva |

---

## 3. Misszió feltöltés és indítás folyamata (valódi eszközön)

```
[Feltöltés + Start] gomb megnyomva
      │
      ▼
MissionUploader.uploadMission(waypoints, config, callback)
  │
  ├─ WaypointMission.Builder felépítése:
  │    .waypointCount(waypoints.size())
  │    .maxFlightSpeed(config.speedMs)
  │    .autoFlightSpeed(config.speedMs)
  │    .finishedAction(returnHome ? RETURN_TO_HOME : NO_ACTION)
  │    .headingMode(AUTO)
  │    .flightPathMode(NORMAL)
  │    .addWaypoints([...])
  │
  ├─ Minden waypointhoz Waypoint objektum:
  │    new Waypoint(lat, lon, (float)altM)
  │    + WaypointAction(START_TAKE_PHOTO, 0)  ha shootPhoto=true
  │    + gimbalPitch beállítás
  │
  ├─ WaypointMissionOperator.loadMission(mission)
  │    → DJIError? → callback.onError(message)
  │
  └─ WaypointMissionOperator.uploadMission(callback)
       → onResult(DJIError) → error? → callback.onError() : → startMission()
      │
      ▼
MissionUploader.startMission(callback)
  └─ WaypointMissionOperator.startMission(callback)
       → onResult(DJIError) → error? → callback.onError() : callback.onSuccess()
```

---

## 4. Misszió szüneteltetés / folytatás

```
pauseMission():
  WaypointMissionOperator.pauseMission(callback)
  → Drón lebeg az aktuális GPS pozícióban
  → Misszió állapot: EXECUTION_PAUSED

resumeMission():
  WaypointMissionOperator.resumeMission(callback)
  → Drón folytatja a waypoint útvonalat
  → Misszió állapot: EXECUTING
```

---

## 5. Misszió leállítás

```
stopMission():
  WaypointMissionOperator.stopMission(callback)
  → Drón lebeg, kézi vezérlésre vált
  → RTH indítása: FlightController.startGoHome() (opcionális, M04-en kívül)
  → Misszió állapot: READY_TO_UPLOAD (misszió eldobva)
```

---

## 6. Stub implementáció (emulátor build)

```java
// dji/MissionUploader.java — jelenlegi stub
public class MissionUploader {
    public void uploadMission(List<WaypointData> waypoints,
                              MissionConfig config, UploadCallback callback) {
        if (callback != null)
            callback.onError("Stub build – drón nem csatlakoztatva");
    }
    public void startMission(UploadCallback callback) { ... }
    public void pauseMission(UploadCallback callback) { ... }
    public void resumeMission(UploadCallback callback) { ... }
    public void stopMission(UploadCallback callback) { ... }
}
```

---

## 7. Valódi eszközre való aktiválás lépései

```
1. developer.dji.com → App Key regisztráció
   Package name: com.dronefly.app

2. AndroidManifest.xml:
   <meta-data android:name="com.dji.sdk.API_KEY"
              android:value="REPLACE_WITH_YOUR_DJI_APP_KEY" />
   → Saját App Key beírása

3. app/build.gradle — DJI SDK uncomment:
   implementation('com.dji:dji-sdk:4.18') {
       exclude module: 'library-anti-distortion'
   }
   compileOnly 'com.dji:dji-sdk-provided:4.18'

4. App.java — DJI Helper.install visszaállítása:
   DJISDKManager.getInstance().registerApp(this, callback)

5. MissionUploader.java — stub cseréje valódi MSDK implementációra

6. Build → Crystal Sky-ra telepítés USB-n
```

---

## 8. RC és fizikai vezérlők prioritása

```
Az MSDK waypoint misszió FUTÁS KÖZBEN:
  RC jobb joystick mozgatása → kézi beavatkozás → misszió SZÜNETEL
  RC RTH gomb (piros) → misszió LEÁLL, drón hazatér
  RC landing gomb → leszállás az aktuális pozícióban

Az app Pause/Stop gombjai:
  → Software szintű MSDK hívások
  → RC hardware vezérlés MINDIG felülírja az app software parancsait
  → Az operátor mindig visszaveheti a kézi irányítást RC segítségével

Biztonsági kikapcsولás:
  RC jobb joystick bármely irányba > küszöb → automatikus mission pause
  Fizikai RTH gomb → mindig elsőbbséget élvez
```

---

## 9. Kamera feed PiP (DroneVideoWidget)

```
CAM gomb megnyomva (btnCamToggle)
      │
      ▼
DroneVideoWidget.start()
  │
  ├─ TextureView Surface elérhető?
  │     Igen → attachCodecAndFeed()
  │     Nem  → vár onSurfaceTextureAvailable callbackre
  │
  ├─ VideoFeeder.getPrimaryVideoFeed().addVideoDataListener(proxy)
  │     → onReceive(byte[], int) → DJICodecManager.sendDataToDecoder()
  │
  └─ DJICodecManager dekódolja → TextureView rendereli (élő kép)

CAM gomb újra / ✕ bezárás:
  DroneVideoWidget.stop()
  → VideoFeed listener leiratkozás
  → DJICodecManager.cleanSurface()
  → cameraWindow GONE

onPause → widget.stop()  (feed szünetel, ha az app háttérbe kerül)
onDestroy → widget.destroy()  (teljes takarítás)
```

**Emulátoros build viselkedés:** `dji.sdk.codec.DJICodecManager` és
`dji.sdk.camera.VideoFeeder` osztályok hiányoznak a stub-ból →
`ClassNotFoundException` elnyelve, a kamera ablak fekete marad, crash nincs.

**Crystal Sky valódi eszközön:** A feed automatikusan elindul, amint a drón
csatlakoztatva van és a CAM gomb be van kapcsolva.

---

## 10. Tap-to-Expose (érintéses expozíció)

```
Felhasználó érinti a kamera képet (TextureView)
      │
      ▼
px → normalizált koordináta (nx = x/width, ny = y/height)
      │
      ▼
DroneVideoWidget.tapToFocus(nx, ny)
  │
  ├─ Camera.setFocusMode(FocusMode.AUTO) [MSDK reflection]
  │    → onResult(null) [siker]
  │         │
  │         ▼
  │    Camera.setFocusTarget(PointF(nx, ny)) [MSDK reflection]
  │         → Drón expozíciót az érintett pontra állítja
  │
  └─ Párhuzamosan (UI szálon):
       MissionPlannerActivity.showFocusRing(touchX, touchY)
         → ImageView (focus_ring.xml) megjelenik az érintés helyén
         → scale animáció: 1.4x → 1.0x (200 ms)
         → 700 ms várakozás
         → alpha fade-out (300 ms)
         → INVISIBLE

Megjegyzés: P4P v1 FIX FÓKUSZÚ lencse → a setFocusMode(AUTO) +
setFocusTarget() a tényleges fókuszt NEM változtatja, de az expozíciót
az érintett pontra igazítja (tap-to-expose viselkedés, mint a DJI Go 4-ben).
```

**Proxy hashCode/equals/toString kezelés:**
Minden MSDK reflection proxy tartalmaz hashCode/equals/toString kezelést,
mivel az MSDK belső Set gyűjteményekbe teszi a callback-eket, amelyek
`hashCode()`-ot hívnak → null visszatérési értéket nem lehet int-re unboxolni
→ NPE. (Korábbi bugfix: `d6912a3` + `4deba15` commitok.)

---

## 11. Kapcsolódó modulok

| Modul | Kapcsolat típusa |
|-------|-----------------|
| M01 Misszió Tervező | **Közvetlen hívó** — uploadMission(), pause, stop, videoWidget |
| M02 Grid Engine | **Adat forrás** — WaypointData lista input |
