# L1 – Üzleti Folyamat – DJI Integráció

**Modul:** M04
**Szint:** L1 – Üzleti Folyamat
**Verzió:** v1.9.7
**Létrehozva:** 2026-04-02
**Utolsó módosítás:** 2026-04-17
**Státusz:** ✅ Teljes MSDK v4 integráció — feltöltés, vezérlés, haladásjelző, misszió folytatás, szimuláció, kézi felszállás utáni indítás, folyamatos repülés (CURVED), gimbal nadir, SD kártya ellenőrzés; képernyőkép/videó rögzítés (v1.9.7)

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

**Jelenlegi build állapot (v1.6.1):**

| Funkció | Státusz |
|---------|---------|
| DJI SDK regisztráció | ✅ Működik Crystal Sky-on |
| Drón csatlakozás érzékelés | ✅ Működik (onProductConnect + 2mp timer) |
| Drón neve (pl. "Phantom 4 Pro") | ✅ Működik |
| Drón akkumulátor % | ✅ Működik (BatteryState callback) |
| RC csatlakozás érzékelés | ✅ Működik (reflection) |
| RC akkumulátor % | ✅ Működik (BatteryState$Callback, getRemainingChargeInPercent) |
| GPS műholdak száma + drón pozíció | ✅ Működik (FlightController StateCallback, lat/lon is) |
| Repülési állapot (isFlying) | ✅ Működik (FlightController StateCallback, volatile cache) |
| Misszió feltöltés / indítás | ✅ Valódi MSDK implementáció; ProgressDialog + AlertDialog visszajelzés |
| Kézi felszállás utáni misszióindítás | ✅ isFlying() alapú állapotfelismerés; differenciált dialog |
| Misszió pause / resume / stop | ✅ Valódi MSDK implementáció |
| GPS ellenőrzés (START gomb) | ✅ START csak ≥6 műhold esetén engedélyezett; „START (GPS!)" felirat ha nincs elég |
| Folyamatos repülés (CURVED mód) | ✅ CURVED flightPathMode — a drón nem áll meg waypointnál, intervallum kamera triggereli a fotókat |
| Gimbal nadir automatikus beállítás | ✅ Mission start előtt -90°-ra forgatja a gimbalt (survey fotózás) |
| SD kártya ellenőrzés | ✅ Mission start előtt ellenőrzi; ha nincs kártya: blokkoló figyelmeztetés |
| Haladásjelző (drón marker + zöld vonal + WP számláló) | ✅ WaypointMissionOperatorListener alapú |
| Misszió folytatása (resume dialog) | ✅ Utolsó elért WP−1 indexről, akkucsere után |
| Szimuláció (10× gyorsított animáció) | ✅ Inkrementális polyline, rate-limited invalidate |
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

## 3. Kézi felszállás utáni misszióindítás

Az operátor manuálisan felszállhat RC-vel, majd az appból indíthatja el az automatikus missziót. A drón az első waypointra repül, onnan a terv szerint hajtja végre a felmérést.

**Workflow:**

```
1. Misszió generálva (polygon + paraméterek)
      │
      ▼
2. [Feltöltés] gomb → misszió feltöltve a drónra (drón még a földön vagy levegőben)
      │
      ▼
3. Operátor kézzel felszállt RC-vel → körözés, ellenőrzés
      │
      ▼
4. [START] gomb megnyomva
      │
      ▼
isFlying() == true?
  │
  ├─ IGEN (levegőben):
  │    Dialog: "A drón levegőben van. A misszió indítása után az első
  │             waypointra repül (SAFELY módban: előbb a misszió
  │             magasságára emelkedik)."
  │    [START] → startMission() → drón az 1. waypointra repül
  │
  └─ NEM (földön):
       Dialog: "A drón felszáll és elindítja a missziót. Biztos vagy benne?"
       [START] → startMission() → auto-felszállás → 1. waypoint
```

**Miért SAFELY mód?**
A `gotoFirstWaypointMode = SAFELY` azt jelenti, hogy a drón levegőből indítva is
először a misszió tervezett magasságára emelkedik, majd onnan repül az 1. waypointra.
Ez megakadályozza, hogy a drón alacsony körözésből egyenesen nekimenjen egy akadálynak.

**Feltöltés időzítése:**
A misszió feltölthető még a felszállás előtt (ajánlott), vagy levegőben is.
Az MSDK v4 `startMission()` mindkét esetben működik — a drón állapota (földön/levegőben)
csak az automatikus felszállást befolyásolja, az útvonal végrehajtását nem.

---

## 5. Misszió feltöltés és indítás folyamata (valódi eszközön)

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
  │    .flightPathMode(CURVED)     ← folyamatos repülés, megállás nélkül
  │    .addWaypoints([...])
  │
  ├─ Minden waypointhoz Waypoint objektum (csak sávvégpontok!):
  │    new Waypoint(lat, lon, (float)altM)
  │    waypoint.cornerRadiusInMeters = 0.2f    ← szoros kanyar sávváltásnál
  │    (nincs TAKE_PHOTO akció — CURVED módban figyelmen kívül maradna)
  │    (nincs gimbalPitch akció — külön CameraConfigurator.setNadirPitch() kezeli)
  │    builder.addWaypoint(waypoint)
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

## 5b. Mission start szekvencia (doLaunchMission)

```
START gomb → megerősítő dialog → [START] megnyomva
      │
      ▼
1. CameraConfigurator.checkSDCard(callback)
      │
      ├─ sdOk == false → AlertDialog: "⚠️ Nincs SD kártya!"
      │    [Folytatás kártya nélkül] → doLaunchMission()
      │    [Mégse] → visszalép
      │
      └─ sdOk == true → doLaunchMission()
             │
             ▼
      2. CameraConfigurator.setNadirPitch(callback)
             → Gimbal -90°-ra (nadir) — 3 mp alatt
             │
             ▼
      3. CameraConfigurator.applySettings(camSettings, callback)
             → Kamera mód, expo, ISO, záridő, WB, fájlformátum
             │
             ▼
      4. CameraConfigurator.startIntervalShooting(photoDistM, speedMs, callback)
             → intervalSec = max(2.0, photoDistM / speedMs)
             → Camera.setMode(SHOOT_PHOTO)
             → Camera.setShootPhotoMode(INTERVAL)
             → PhotoTimeIntervalSettings(0, intervalSec) [reflection]
             → Camera.startShootPhoto()
             │
             ▼
      5. MissionUploader.startMission(callback)
             → Drón elindul, CURVED módban repüli az útvonalat
             → Kamera automatikusan fotóz intervallum szerint

Misszió befejezésekor / leállításakor:
      CameraConfigurator.stopIntervalShooting()
      → Camera.stopShootPhoto()
```

---

## 6. Misszió szüneteltetés / folytatás

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

## 7. Misszió leállítás

```
stopMission():
  WaypointMissionOperator.stopMission(callback)
  → Drón lebeg, kézi vezérlésre vált
  → RTH indítása: FlightController.startGoHome() (opcionális, M04-en kívül)
  → Misszió állapot: READY_TO_UPLOAD (misszió eldobva)
```

---

## 8. Haladásjelző folyamata

```
startMission() sikeresen meghívva
      │
      ▼
MissionUploader.startListening(totalWaypoints, listener)
  │
  └─ WaypointMissionOperator.addListener(executionListener)
       │
       ├─ onExecutionUpdate(event)
       │    └─ progress.isWaypointReached == true
       │         └─ trackedWaypointIndex = progress.targetWaypointIndex
       │              ├─ MissionPlannerActivity.updateProgressUI(index, total)
       │              │    → tvMissionProgress.setText("WP: X / Y")
       │              │    → pbMissionProgress.setProgress(X)
       │              ├─ updateDroneMarker(lat, lon)  [GPS callback-ből]
       │              └─ updateCompletedOverlay(index)  → zöld Polyline
       │
       └─ onExecutionFinish(error)
            └─ listener.onMissionFinished(success, lastIndex)
                 → setMissionRunning(false)
                 → stopListening()
                 → clearDroneMarker() + clearCompletedOverlay()
```

---

## 9. Misszió folytatása (resume) folyamata

```
Misszió megszakítása (RTH / Stop / akkucsere)
      │
      │  [resumeWaypointIndex mentve az utolsó onWaypointReached alapján]
      │
Drón újraindul → Crystal Sky újra csatlakozik
      │
      ▼
Felhasználó megnyomja a "Feltöltés" gombot
      │
      ▼
MissionPlannerActivity.uploadCurrentSegment()
  └─ resumeWaypointIndex > 0 && resumeSegmentIndex == currentSegmentIndex?
       │
       ├─ Igen → AlertDialog:
       │    "Folytatod a missziót? Az előző repülés a(z) N. waypontnál szakadt meg."
       │    [Folytatás (WP N-1-től)]  [Újrakezdés]
       │         │
       │         ├─ Folytatás: fromIndex = resumeWaypointIndex - 1
       │         │   doUpload(segment.subList(fromIndex, size), fromIndex, size)
       │         │
       │         └─ Újrakezdés: fromIndex = 0
       │             doUpload(segment, 0, size)
       │
       └─ Nem → normál feltöltés (fromIndex = 0)
```

**Állapot tárolása:** Csak memóriában (app futása alatt). Ha az appot bezárják, az állapot elveszik → újrakezdés szükséges.

---

## 10. Szimuláció folyamata

```
"Szimuláció" gomb megnyomva (feltöltés után engedélyezett)
      │
      ▼
MissionPlannerActivity.startSimulation()
  ├─ Összes szegmens waypontjainak összegyűjtése (allWaypoints lista)
  ├─ simCompletedPoints = new ArrayList<>()  [inkrementális lista]
  ├─ completedOverlay = new Polyline()  [egyszer létrehozva]
  └─ simulateStep(allWaypoints, 0, speedMs)
       │
       ├─ updateDroneMarker(wp.lat, wp.lon)
       ├─ simCompletedPoints.add(GeoPoint)  [inkrementális hozzáadás]
       ├─ completedOverlay.setPoints(simCompletedPoints)
       ├─ if (index % 10 == 0) mapView.invalidate()  [rate-limited]
       ├─ tvMissionProgress.setText("SIM  WP: X / Y")
       └─ simHandler.postDelayed(→ simulateStep(index+1), delayMs)
            delayMs = distanceBetween / (speedMs × 10) × 1000
            clamp: 80ms – 3000ms

"Szimulacio leállítása" gomb / útvonal vége:
  → stopSimulation()
  → simHandler.removeCallbacksAndMessages(null)
  → clearDroneMarker(), clearCompletedOverlay()
  → tvMissionProgress.setText(""), pbMissionProgress GONE
```

**Teljesítmény:** O(n) memóriafoglalás (korábbi O(n²) helyett), Crystal Sky 4666 WP-nél sem fagy le.

---

## 10. Valódi eszközre való aktiválás lépései

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

## 11. RC és fizikai vezérlők prioritása

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

## 12. Kamera feed PiP (DroneVideoWidget)

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

## 13. Tap-to-Expose (érintéses expozíció)

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

## 15. Mintavételi misszió végrehajtása (NORMAL mód) — ✅ Implementálva, terepi teszt: fotó-trigger megbízhatósági hiba azonosítva (2026-07-03, ld. §18)

**Üzleti kontextus:** M01 §10, M02 §7. A meglévő grid-misszió `CURVED` (folyamatos,
megállás nélküli) repülési módban, kamera-időintervallum alapú fotózással fut — ez
mintavételi misszióhoz nem alkalmas, mert ott **diszkrét pontokon kell megállni és
fotózni**, a köztes (transit) szakaszon fotó nélkül.

**A DJI MSDK egy kulcsfontosságú korlátja, ami ezt meghatározza:** a waypoint-akciók
(`WaypointAction`, pl. `STAY` hover + `START_TAKE_PHOTO`) **csak `NORMAL`
(egyenes-vonalú, megállós) `flightPathMode`-ban hajtódnak végre** — `CURVED` módban
figyelmen kívül maradnak (ezt a jelenlegi `MissionUploader.java` kódkommentje is
rögzíti). Ezért a mintavételi misszió egy **második, párhuzamos feltöltési útvonalat**
igényel, nem a meglévő módosítását.

```
uploadSamplingMission(waypoints, config, callback)
      │
      ▼
WaypointMission.Builder felépítése — ELTÉRÉSEK a grid-misszióhoz képest:
  .flightPathMode(NORMAL)              ← CURVED helyett — megáll minden waypontnál
  .maxFlightSpeed(config.speedMs)
  .autoFlightSpeed(config.speedMs)
  .finishedAction(RETURN_TO_HOME)
  .headingMode(AUTO)
  .gotoFirstWaypointMode(SAFELY)
      │
      ▼
Minden WaypointData → Waypoint objektum:
  new Waypoint(lat, lon, altitudeM)
  IF wp.hoverSeconds > 0:
      waypoint.addAction(new WaypointAction(WaypointActionType.STAY,
                                             (int)(wp.hoverSeconds * 1000)))
      waypoint.addAction(new WaypointAction(WaypointActionType.START_TAKE_PHOTO, 0))
  (a transit — hoverSeconds==0 — waypontoknak nincs akciójuk, csak áthaladás)
      │
      ▼
Kameramód mission előtt: SHOOT_PHOTO + SINGLE (INTERVAL helyett — a fotó
  triggerelése mostantól a waypoint-akció felelőssége, nem az intervallum-időzítő)
      │
      ▼
loadMission() → uploadMission() → startMission()  (a meglévő pause/resume/stop
  vezérlők VÁLTOZATLANOK — a WaypointMissionOperator API azonos)
```

**Kameramód-eltérés a grid-misszióhoz képest:**

| | Grid misszió (meglévő) | Mintavételi misszió (tervezett) |
|---|---|---|
| `flightPathMode` | `CURVED` | `NORMAL` |
| Fotó trigger | Kamera időintervallum (`startIntervalShooting`) | Waypoint-akció (`START_TAKE_PHOTO`) |
| Megállás waypontnál | Nem | Igen, a mintavételi (alacsony) waypontokon |
| `shootPhotoMode` | `INTERVAL` | `SINGLE` |
| Fotó/mintapont determinizmusa | Nem garantált (idő alapú) | **Garantált 1:1** — minden mintapont pontosan egy fotót eredményez, sorrendben |

**Miért fontos az utolsó sor?** Mivel a fotó sorrendje determinisztikus (az N-edik
lefotózott kép = az N-edik mintapont), a médialetöltésnél (§16) **nem kell** a fájlnevet
vagy GPS-koordinátát elemezni a hozzárendeléshez — elég az utolsó N fájlt kapcsolatba
hozni a mintapontok sorrendjével.

> **⚠ Terepi teszt (2026-07-03) megcáfolta ezt a feltevést:** a `START_TAKE_PHOTO`
> waypoint-akció önmagában **nem garantálja** a fotó tényleges elkészültét — ld.
> részletesen §18. A determinisztikus 1:1 megfeleltetés csak akkor áll fenn, ha
> minden akció ténylegesen lefut, ami MSDK-szinten nem biztosított.

---

## 16. Session-alapú médialetöltés (Media Download) — ✅ Implementálva, eszközön még nem tesztelve (2026-07-02)

**Üzleti probléma:** a mintavételi misszió befejezése után a memóriakártyán lévő
összes fotó közül **csak az ebben a sessionben készült N db** érdekes — a kártyán
lehetnek korábbi felmérésekből származó, akár több ezer fájl is. Ezeket egyenként,
kézzel kikeresni WiFi-n (DJI OcuSync/Lightbridge downlink) át irreális.

**Jelenlegi állapot:** a kódban **nincs médialetöltési funkció** — a meglévő
"SD kártya ellenőrzés" (`CameraConfigurator.checkSDCard()`) csak azt nézi, van-e
kártya, fájlokat nem listáz és nem tölt le. Ez egy teljesen új M04 komponens.

### 16.1 Folyamat

```
Misszió befejeződött (MissionProgressListener.onMissionFinished, siker)
      │
      ▼
1. Kameramód váltás: SHOOT_PHOTO → MEDIA_DOWNLOAD
   (csak ebben a módban érhető el a MediaManager)
      │
      ▼
2. MediaManager.refreshFileListOfStorageLocation(SDCARD, callback)
   → a kártya teljes fájllistája frissül a memóriában
      │
      ▼
3. getSDCardFileListSnapshot() → List<MediaFile>
   Rendezés: getTimeCreated() (vagy fájlnév sorszám) szerint csökkenő
      │
      ▼
4. Az UTOLSÓ N fájl kiválasztása (N = a session mintapontjainak száma)
   → determinisztikus 1:1 megfeleltetés a §15 szerint:
     lista[0] = mintapont #0, lista[1] = mintapont #1, ...
      │
      ▼
   Biztonsági ellenőrzés (nem blokkoló, csak figyelmeztetés):
     IF bármelyik kiválasztott fájl getTimeCreated() < missionStartTimestamp:
        → UI figyelmeztetés: "A talált fájlok egy része a misszió kezdete előtti
           lehet — ellenőrizd manuálisan." (pl. kihagyott/sikertelen fotóakció esetén)
      │
      ▼
5. Letöltési ciklus — minden kiválasztott MediaFile-ra:
   mediaFile.fetchFileData(localDestFile, null, downloadListener)
     onProgress(total, current) → progress bar frissítés (ProgressDialog, a
        meglévő misszió-feltöltési dialog mintájára)
     onSuccess() → local fájl mentve: /sdcard/DroneFly/sessions/{session_id}/
                    point_{index:03d}.jpg
                    (a HELYI fájlnevet MI adjuk — a DJI eredeti (DJI_0001.JPG stb.)
                     nevét firmware-szinten nem tudjuk felülírni, de nem is kell:
                     a helyi másolat neve már a mintapont-indexet hordozza)
     onFailure(error) → hibalista, összegzés a ciklus végén
      │
      ▼
6. Session metaadat JSON mentése: /sdcard/DroneFly/sessions/{session_id}/session.json
   {
     "session_id": "...", "parcel_name": "...", "mission_start_ts": ...,
     "mission_end_ts": ..., "transit_altitude_m": ..., "sample_altitude_m": ...,
     "points": [
       {"index": 0, "lat": ..., "lon": ..., "local_file": "point_000.jpg",
        "original_dji_filename": "DJI_0001.JPG", "capture_time": ...},
       ...
     ]
   }
      │
      ▼
7. Kameramód visszaváltás: MEDIA_DOWNLOAD → SHOOT_PHOTO (a következő misszióhoz)
      │
      ▼
8. UI összegzés: "N/N fotó letöltve. Session mentve: {session_id}"
   [📁 Session megnyitása] — helyi galéria nézet a letöltött képekhez
   [⬆️ Feltöltés Dronterapiába] — jövőbeli bővítés (jelenleg: manuális feltöltés
     a Dronterapia Counting oldal "🎯 Mintavételezéses állományfelmérés" expanderébe,
     a session.json és a képek alapján kézzel kitöltve a GSD-t mintaponkénti)
```

**M09 előfeltétel (2026-07-03, ld. M09_L1 §6):** a ténylegesen implementált
`writeSessionJson()` a fentebbi tervezett sémánál egyszerűbb mezőket ír
(`session_id`, `sample_count`, `downloaded_count`, `created_at`, `points[]`)
— ehhez járult hozzá 3 új mező, amit az M09 (Edge AI Tőszámlálás) igényel a
footprint-terület tableten történő kiszámításához:

```json
{
  "sample_altitude_m": 6.0,
  "drone_profile_name": "Phantom 4 Pro v1",
  "aoi_area_m2": 100000.0
}
```

A `downloadSessionMedia()` hívás ezért 3 új paramétert kapott
(`sampleAltitudeM`, `droneProfileName`, `aoiAreaM2`) — a hívó
(`MissionPlannerActivity.triggerSessionDownload()`) a `lastResult.altitudeM`
/ `lastResult.areaM2` (SamplingMissionGenerator kimenete) és
`getSelectedDrone().name` értékekből tölti ki.

### 16.2 Miért "utolsó N fájl", nem fájlnév-parsing vagy GPS-egyeztetés?

Mert a §15 szerinti egy-fotó-per-waypoint-akció végrehajtás **garantálja** a
determinisztikus sorrendet — nincs szükség bonyolultabb (és hibázóbb) logikára.
Ha a jövőben interval-shooting is bekerülne mintavételi módba (pl. több fotó
mintaponton belüli redundanciához), ezt a mechanizmust felül kell vizsgálni
(pl. `capture_time` ablak szerinti csoportosítás mintapontonként, nem egyszerű index).

### 16.3 Teljesítmény-becslés

| Mintapontok száma | Fotók mérete (5472×3648 JPEG) | Becsült letöltési idő (OcuSync downlink) |
|---|---|---|
| 20 | ~8–12 MB/fotó → 160–240 MB | ~1–3 perc |
| 50 | ~8–12 MB/fotó → 400–600 MB | ~3–7 perc |

> Pontos érték a terepi teszttől függ (jelerősség, távolság) — a progress bar
> (`onRateUpdate`) ad valós idejű visszajelzést, nem egy előre becsült időt kell
> mutatni a UI-n.

### 16.4 Kapcsolódó modulok

| Modul | Kapcsolat típusa |
|-------|-----------------|
| M01 Misszió Tervező | **UI-hívó** — "Fotók letöltése" gomb a misszió befejezése után |
| M02 Grid Engine | **Adat forrás** — mintapontok száma és sorrendje (session korreláció) |
| Dronterapia M03 (Counting) | **Downstream, jelenleg manuális** — a session mappa tartalma manuálisan tölthető fel a meglévő mintavételezéses expanderbe |

### 16.5 Megszakíthatóság és beragadás-kezelés — terepi teszt lelet — ✅ Javítás implementálva (2026-07-04), eszközön még nem tesztelve

**Terepi megfigyelés (2026-07-04, éles teszt):** leszállás után a fotóletöltés
(a) többször **"system busy, try later"** hibával elszállt, és (b) volt, hogy
egy elindított letöltés **beragadt**, és **csak az app újraindítása** után
lehetett újra elindítani.

**Root cause:**
- **"System busy":** közvetlenül leszállás után a kamera még **írja a kártyára
  az utolsó felvételeket** és/vagy a `MediaManager` másik művelettel foglalt.
  A jelenlegi `downloadSessionMedia()` ezt nem kezeli — a `setMode` /
  `refreshFileListOfStorageLocation` hibája egyből `onSessionError`, nincs
  várakozás + újrapróbálkozás.
- **Beragadás:** a `MediaFile.fetchFileData()` néha **se `onSuccess`-t, se
  `onFailure`-t nem hív** (SDK-hang) — ilyenkor a `downloadNext()` rekurzió
  megáll. A `ProgressDialog` viszont `setCancelable(false)` és nincs
  időtúllépés, ezért örökre ott marad; a `MissionPlannerActivity`-ben nincs
  megszakítási út, csak az app-újraindítás.

**Javítás (a felhasználói kérés minimuma: a Mégse indítsa újra):**
1. **Megszakítási flag** a `MediaSessionDownloader`-ben (`AtomicBoolean cancel`)
   — a letöltési ciklus képek között ellenőrzi; megszakításkor tiszta
   kilépés + kameramód-visszaállítás (`SHOOT_PHOTO`).
2. **Cancelable ProgressDialog** a `triggerSessionDownload()`-ban: a **Mégse**
   gomb beállítja a flaget, elrejti a dialógust, és **azonnal újraindítható**
   állapotba hozza (nincs app-újraindítás).
3. **Per-fájl időtúllépés** (pl. 30–60 mp): ha egy `fetchFileData` ennyin belül
   nem ad callback-et, a downloader hibaként kezeli az adott fájlt és lép a
   következőre (vagy megszakít) — nem ragad be.
4. **System-busy retry:** a `setMode` / `refresh` "busy"-jellegű hibájánál
   rövid várakozás (2–3 mp) + néhányszori újrapróbálkozás, mielőtt feladná.
5. **Garantált kameramód-visszaállítás** minden kilépési ágon (hiba, megszakítás,
   siker) — hogy a kamera ne maradjon MEDIA_DOWNLOAD/busy állapotban.

Részletes állapotgép: M04_L3 (bővítendő); tranzakciós/időtúllépés-kezelés:
M04_L4 (bővítendő).

---

## 17. Kapcsolódó modulok (teljes lista, §15–16 bővítéssel)

| Modul | Kapcsolat típusa |
|-------|-----------------|
| M01 Misszió Tervező | **Közvetlen hívó** — uploadMission(), pause, stop, videoWidget; §15 esetén uploadSamplingMission() |
| M02 Grid Engine | **Adat forrás** — WaypointData lista input; §15 esetén SamplingMissionGenerator kimenete |

---

## 18. Fotó-trigger megbízhatóság — terepi teszt lelet — ✅ Javítás implementálva (2026-07-03), eszközön még nem tesztelve

**Terepi megfigyelés (2026-07-03):** 10 mintapontos misszió, mind a 10 leszállás/hover
megtörtént (pilóta által vizuálisan megerősítve), **de csak 6 fotó** került a
memóriakártyára. A fotók JPEG formátumban készültek (nem RAW), és a mintapontok
között hosszú volt az idő (a hover-idő nem volt szűk) — tehát **nem** írási
sebesség/időzítés kérdése.

### 18.1 Root cause

A §15 szerinti fotó-trigger egy **"tűzz és felejtsd el" (fire-and-forget) waypoint-akció**:

```java
waypoint.addAction(new WaypointAction(WaypointActionType.STAY, hoverMs));
waypoint.addAction(new WaypointAction(WaypointActionType.START_TAKE_PHOTO, 0));
```

A DJI MSDK v4 **nem ad vissza semmilyen visszaigazolást** arra, hogy egy adott
waypointon a `START_TAKE_PHOTO` akció ténylegesen lefutott-e — a
`WaypointMissionOperatorListener` csak waypoint-elérést és misszió-befejezést
jelez (§14, `executionListener`), **akció-szintű** siker/hiba callback nincs.
Ez egy ismerten megbízhatatlan MSDK-viselkedés: a waypoint-akciók (főleg
kameravezérlés) firmware-szintű végrehajtása nem garantált, és ha egy akció
"elveszik", az app-nak **nincs módja észlelni vagy újrapróbálni**.

Ez felülírja a §15 alján tett kijelentést ("a fotó sorrendje determinisztikus,
minden mintapont pontosan egy fotót eredményez") — ez **tervezési feltételezés
volt, amit a terepi teszt megcáfolt.** A `MediaSessionDownloader` (§16)
"utolsó N fájl" kiválasztási logikája ráadásul akkor is pontosan N fájlt vesz
be, ha ezek közül nem mindegyik az adott misszióból származik — ha csak 6 új
fotó készült, a §16.1 4. lépése ettől függetlenül megpróbálja az utolsó 10-et
kiválasztani (4 régebbi, a misszió előtti fájllal kiegészítve), és csak
**figyelmeztet**, nem blokkol, ha egy kiválasztott fájl a misszió kezdete
előttről származik.

### 18.2 Javítás — ✅ Implementálva (2026-07-03), app-vezérelt trigger, visszaigazolással és újrapróbálkozással

A fotó-triggert **kivettük a néma `WaypointAction`-ből**, és az app oldalán, a
már meglévő `onWaypointReached()` callback alapján, közvetlen kameraparanccsal
váltottuk ki — ennek **van** SDK-szintű siker/hiba callback-je, tehát hiba
esetén újrapróbálható:

```
Waypoint-szekvencia mintázata VÁLTOZATLAN maradt (érkezés/mintavétel/emelkedés,
3 waypoint/mintapont — SamplingMissionGenerator, M02 §7) — csak a fotó-trigger
mechanizmusa változott:

  waypoint.addAction(STAY, hoverMs)   ← MARADT (a hover pozícióban tartáshoz
                                          továbbra is szükséges NORMAL módban)
  waypoint.addAction(START_TAKE_PHOTO) ← TÖRÖLVE (megbízhatatlan)

onWaypointReached(index, total):
  actualIndex = offset + index
  IF actualIndex % 3 == 1 (mintavételi waypoint, ld. M02 §7 sorrend):
      → CameraConfigurator.triggerSamplePhoto(listener) hívás
        → 500 ms stabilizációs várakozás, majd Camera.startShootPhoto()
          KÖZVETLEN hívás (nem reflection — ld. megvalósítási megjegyzés lent)
        → SIKER callback → a pont GeoPoint-ja bekerül a
          confirmedSamplePointsForSession listába (Activity-szintű)
        → HIBA callback → 1× újrapróbálkozás 1000 ms késleltetéssel
        → ha az újrapróbálkozás is hibázik → samplingPhotosFailed++,
          Toast figyelmeztetés (nem blokkolja a misszió folytatását)
```

**Megvalósítási eltérés a tervtől — közvetlen hívás, nem reflection:** az
eredeti terv a `DroneVideoWidget` tap-to-expose mintáját (dinamikus
`InvocationHandler` proxy) javasolta a `Camera.startShootPhoto()` hívásához.
A kódolás közben kiderült, hogy ez **nem szükséges**: a `CameraConfigurator`
már ma is **közvetlenül** hívja a `camera.startShootPhoto(callback)`-ot
(`startShootPhotoInternal()`, az intervallum-fotózás indításánál) — ez egy
stabil, régóta változatlan publikus MSDK v4 API, nem igényli a
verzió-bizonytalanság elleni reflection-védelmet (amit a `DroneVideoWidget`
a kevésbé stabil fókusz/expozíció API-knál alkalmaz). Az új
`CameraConfigurator.triggerSamplePhoto()` metódus ugyanezt a közvetlen,
egyszerűbb mintát követi — ld. `CameraConfigurator.java` "Mintavételi
fotó-trigger" szakasz.

**Megvalósítási eltérés — pontonkénti azonosítás index-számítás helyett:**
a terv a `session.json` `trigger_confirmed` mezőjével (per-pont index alapján)
számolt volna. A tényleges megvalósítás egyszerűbb és robusztusabb: az
`Activity` egy `samplePointCursor`-t növel minden mintavételi waypoint
elérésekor (szegmenshatároktól függetlenül, mert a mintapontok szigorúan
sorban látogatottak), és **csak a sikeresen megerősített pontok GeoPoint-ját**
gyűjti egy `confirmedSamplePointsForSession` listába. A médialetöltés
(§16, `triggerSessionDownload()`) ezt a **szűrt** listát adja át a
`MediaSessionDownloader`-nek a nyers `lastSamplePoints` helyett — így egy
sikertelen pont nem tolja el a rákövetkező pontok geo-taggelését, és a "last
N file" kiválasztás is a valós, megerősített darabszámmal dolgozik.

**Miért jobb ez a §15 eredeti tervénél:** a `Camera.startShootPhoto()` hívásnak
**van** SDK-szintű `onResult`/hiba callback-je (ellentétben a `WaypointAction`
néma végrehajtásával) — ez teszi lehetővé az újrapróbálkozást és a pontos,
mintapontonkénti visszajelzést. A `MediaSessionDownloader` (§16) így egy
pontosabb elvárt-darabszámot kap (a ténylegesen megerősített fotók száma,
nem a nyers mintapontszám), ami a §16.1 4. lépésének biztonsági ellenőrzését
is megbízhatóbbá teszi.

**Kapcsolódó modulváltozás:** M02 `SamplingMissionGenerator` **nem** változott
(a waypoint-szekvencia mintázata ugyanaz maradt) — a `MissionUploader.java`-ban
csak a `START_TAKE_PHOTO` akció lett törölve, az új trigger-logika a
`CameraConfigurator.java`-ban és a `MissionPlannerActivity.java`
`onWaypointReached()`/`onMissionFinished()`/`triggerSessionDownload()`
metódusaiban él (ld. M04_L2 §10, M04_L3, M04_L4 §10 a részletes tervhez).

**Terepi validáció még szükséges:** a javítás kódszinten kész és lefordul
(`./gradlew compileDebugJavaWithJavac` sikeres, 2026-07-03), de valódi
Crystal Sky + Phantom 4 Pro v1 eszközön még nincs kipróbálva. Érdemes lenne
megnézni a `session.json` `capture_time_ms` mezőit
([MediaSessionDownloader.java:243-277](../../app/src/main/java/com/dronefly/app/dji/MediaSessionDownloader.java))
egy következő repülés flight logjával összevetve, hogy a retry ténylegesen
javítja-e a korábban tapasztalt 10-ből-6-os arányt.

---

## 19. Fotó-trigger DUPLÁZÓDÁS — terepi teszt lelet — ✅ Javítás implementálva (2026-07-04), eszközön még nem tesztelve

**Terepi megfigyelés (2026-07-04, éles teszt):** 5 mintapontos misszió, a drón
**pontosan 5 helyen** ereszkedett le és fotózott (pilóta által megerősítve), **de
10 JPEG** került a kártyára — pontonként **kettő**. Mind JPEG (nem RAW —
a felhasználó azonnal ellenőrizte, a RAW-formátum tehát NEM ok). Ez a §18
javítás **ellentétes hibája**: ott alultriggerelés volt (10→6 hiányzó), most
túltriggerelés (5→10 dupla) — ugyanabban az app-vezérelt trigger-folyamban.

### 19.1 Root cause — a waypoint-elérés esemény nincs deduplikálva

A `MissionUploader.onExecutionUpdate()` (a `WaypointMissionOperatorListener`
végrehajtási callback-je) **másodpercenként sokszor** lefut a misszió alatt.
A jelenlegi logika:

```java
public void onExecutionUpdate(WaypointMissionExecutionEvent event) {
    WaypointExecutionProgress p = event.getProgress();
    if (p.isWaypointReached) {
        trackedWaypointIndex = p.targetWaypointIndex;
        listener.onWaypointReached(trackedWaypointIndex, totalWaypoints); // MINDEN frissítésnél tüzel
    }
}
```

A hiba: amíg a drón a mintaponton **hoverel (STAY akció, §15)**, az
`isWaypointReached` végig `true` marad, és az `onExecutionUpdate` **többször
is** meghívja az `onWaypointReached`-et **ugyanarra a waypoint-indexre** —
nincs ellenőrzés, hogy *új* waypointról van-e szó. A mintavételi ág
(`MissionPlannerActivity.onWaypointReached`, `actualIndex % 3 == 1`) ilyenkor
pontonként többször:

```java
if (actualIndex % 3 == 1) {
    int pointIdx = samplePointCursor++;   // minden ismételt híváskor NŐ
    triggerSamplePointPhoto(point);        // minden ismételt híváskor EXPONÁL
}
```

→ pontonként ~2× exponálás → **5 pont = 10 JPEG.** (A `samplePointCursor` is
túllép, ami a geo-taggelést és a "last N file" letöltést is elronthatja.)

**Ez érinti az M10 sűrű rácsot is** (`triggerDenseGridPhoto()`, minden
waypointon) — ott is duplázódhat, ami magyarázhatja a nagy felbontású
repülésnél tapasztalt megnövekedett fotószámot (ld. §20).

### 19.2 Javítás — csak index-változásra tüzeljen

```java
if (p.isWaypointReached && p.targetWaypointIndex != trackedWaypointIndex) {
    trackedWaypointIndex = p.targetWaypointIndex;
    listener.onWaypointReached(trackedWaypointIndex, totalWaypoints);
}
```

Az `onWaypointReached` így **waypointonként pontosan egyszer** tüzel (az első
"elérve" pillanatban, amikor az index az előzőhöz képest változik). Ez
megszünteti a dupla exponálást (§18 mintavételi és M10 sűrű rács ágban is) és
a `samplePointCursor` túlléptetését. A `trackedWaypointIndex` már ma is
mezőben tárolt (a resume-hoz), így a dedup-gate természetesen illeszkedik.

**Miért nem derült ki a §18 javításkor:** a §18 a hiányzó fotókat oldotta meg
(a néma WaypointAction cseréje visszaigazolt app-triggerre), de az új app-trigger
a nem-deduplikált `onWaypointReached`-re épült — a hover alatti ismételt
esemény-tüzelés akkor nem volt kipróbálva eszközön (a §18 is "eszközön még nem
tesztelve" státuszú volt). Az éles teszt most ezt hozta felszínre.

---

## 20. Fotó hangjelzés — DJI GO4-szerű visszajelzés — ✅ Implementálva (2026-07-04), eszközön még nem tesztelve

**Felhasználói igény:** a DJI GO4 hangjelzést ad, amikor a drón fotóz — jó lenne
a tableten is hallani, hogy **ténylegesen mikor történik fotózás** (a
`btnDownloadSession` „N/N megerősítve" száma csak a végén látszik, repülés
közben nem ad valós idejű visszajelzést).

**Megoldás:** az app-vezérelt triggerű módoknál (mintavételi + M10 sűrű rács) az
`onPhotoConfirmed()` az SDK-visszaigazolás pillanata — ekkor **rövid csippanás**
szól (`ToneGenerator.TONE_PROP_BEEP`), sikertelen fotónál (`onPhotoFailed()`)
**eltérő, figyelmeztető hang** (`TONE_PROP_NACK`). Így a pilóta hallja, ha egy
pont kimaradt, anélkül hogy a képernyőt néznie kellene.

- `ToneGenerator` (STREAM_MUSIC) — nincs szükség hangfájlra, Android 5.1-en is fut.
- `MissionPlannerActivity.playPhotoBeep()` / `playPhotoFailTone()`; a
  `ToneGenerator` az `onDestroy()`-ban felszabadul.

**Korlát:** intervallum (CURVED) módban a kamera önállóan időzít, arra nincs
per-fotó SDK-callback, ezért ott jelenleg nincs hangjelzés. Jövőbeli bővítés:
a `Camera` SystemState „exponálás/tárolás" jelének él-detektálása (reflection,
a histogram-mintához hasonlóan) — akkor minden módban szólna. A felhasználó
jelenlegi munkafolyamata (M10 sűrű rács + mintavételi) viszont app-vezérelt,
tehát lefedett.
