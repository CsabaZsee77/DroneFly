# L2 – Döntési Logika – DJI Integráció

**Modul:** M04
**Szint:** L2 – Döntési Logika
**Verzió:** v1.6.0
**Létrehozva:** 2026-04-02
**Utolsó módosítás:** 2026-04-13
**Státusz:** ✅ Implementálva — misszió feltöltés/indítás, kézi felszállás utáni indítás, kamera feed, tap-to-expose

---

## 1. SDK inicializálás döntési folyamata

```
App.onCreate()
  │
  ▼
DJIHelper.init(context, listener)
  │
  ▼
Emulátorban fut? (DJIHelper.isEmulator())
  │ IGEN → listener.onRegistered(false, "Stub build") → leáll
  │ NEM
  ▼
DJISDKManager.getInstance().registerApp(context, new SDKManagerCallback)
  │
  ├─ onRegister(DJIError):
  │    error == null?
  │      │ IGEN → SDK regisztrálva → MainActivity: "DJI SDK: Regisztrálva"
  │      │ NEM  → MainActivity: "SDK hiba: " + error.getDescription()
  │
  ├─ onProductDisconnect():
  │    → MainActivity: "Drón: Lecsatlakoztatva"
  │
  ├─ onProductConnect(BaseProduct):
  │    → MainActivity: "Drón: Csatlakoztatva — " + product.getModel()
  │
  └─ onComponentChange():
       → Esetleg UI frissítés (kamera, akku stb.)
```

---

## 2. Waypoint misszió validáció (valódi eszköznél)

```
uploadMission() hívás előtt:
  │
  ▼
waypoints.size() == 0?
  │ IGEN → callback.onError("Nincsenek waypontok")
  │ NEM
  ▼
waypoints.size() > 99?
  │ IGEN → callback.onError("Max 99 waypoint/misszió (MSDK v4 limit)")
  │        (Az M01 már szegmentálja, ez safety check)
  │ NEM
  ▼
config.altitudeM < 5 || config.altitudeM > 500?
  │ IGEN → callback.onError("Érvénytelen magasság: " + altM + " m")
  │ NEM
  ▼
config.speedMs < 1 || config.speedMs > 15?
  │ IGEN → callback.onError("Érvénytelen sebesség: " + speedMs + " m/s")
  │ NEM
  ▼
WaypointMission.Builder felépítés → loadMission() → uploadMission()
```

---

## 3. MSDK waypoint action döntés

```
wp.shootPhoto == true?
  │ IGEN → waypointActions.add(
  │          new WaypointAction(WaypointActionType.START_TAKE_PHOTO, 0))
  │ NEM  → nincs action ennél a waypointnál

wp.gimbalPitch != 0?
  │ IGEN → waypointActions.add(
  │          new WaypointAction(WaypointActionType.GIMBAL_PITCH, (int)gimbalPitch))
  │        (gimbalPitch = -90 → MSDK int: -90)
  │ NEM  → nincs gimbal action

Maximális 15 action per waypoint (MSDK v4 limit)
  → Foto (1) + Gimbal (1) = 2 action → bőven belül
```

---

## 4. Misszió operator állapot ellenőrzés

```
uploadMission() hívás előtt:
  WaypointMissionOperator operator =
      MissionControl.getInstance().getWaypointMissionOperator()

  operator == null?
    │ IGEN → callback.onError("DJI SDK nem inicializálva")
    │ NEM
    ▼
  operator.getCurrentState() == ?
    DISCONNECTED   → callback.onError("Drón nem csatlakoztatva")
    RECOVERING     → callback.onError("Drón visszaáll, kérlek várj")
    EXECUTING      → callback.onError("Misszió már fut")
    NOT_SUPPORTED  → callback.onError("Ez a drón nem támogatja a waypoint missziót")
    Egyéb (READY_TO_UPLOAD, READY_TO_EXECUTE) → folytatás
```

---

## 5. Misszióindítás döntési logika (levegőben vs. földön)

```
[START] gomb megnyomva (missionUploaded == true)
      │
      ▼
DJIHelper.getInstance().isFlying()
      │
      ├─ true (drón levegőben)
      │    Dialog cím:    "Repülés indítása"
      │    Dialog szöveg: "A drón levegőben van. A misszió indítása után
      │                    az első waypointra repül (SAFELY módban:
      │                    előbb a misszió magasságára emelkedik)."
      │    [START] → startMission() közvetlenül
      │
      └─ false (drón földön, vagy isFlying nem érhető el)
           Dialog cím:    "Repülés indítása"
           Dialog szöveg: "A drón felszáll és elindítja a missziót.
                           Biztos vagy benne?"
           [START] → startMission() → auto-felszállás + 1. waypoint

Mindkét esetben a tényleges misszió-indítás azonos:
  CameraConfigurator.applySettings() → MissionUploader.startMission()
  → setMissionRunning(true) + startMissionListener()

isFlying() visszatérési értéke:
  - DJIHelper.droneIsFlying volatile mező (FlightController StateCallback frissíti)
  - Ha az SDK nem elérhető / nincs csatlakoztatva → false (biztonságos alapértelmezés)
  - Catch(Throwable) → false (nem crashel)
```

---

## 6. Hibakódok és magyarázatok

| MSDK DJIError | Magyar üzenet az appban |
|---------------|------------------------|
| `null` (siker) | — |
| `WAYPOINT_MISSION_WAYPOINTS_TOO_CLOSE` | "Waypontok túl közel vannak egymáshoz (min. 0.5 m)" |
| `WAYPOINT_MISSION_WAYPOINTS_TOO_FAR` | "Waypontok túl messze vannak (max. 2 km)" |
| `WAYPOINT_MISSION_INVALID_WAYPOINT_ALTITUDE` | "Érvénytelen magasság egy waypointnál" |
| `COMMON_SYSTEM_BUSY` | "A drón foglalt, kérlek várj" |
| `COMMON_NO_PRODUCT_CONNECTED` | "Nincs csatlakoztatott drón" |
| `COMMON_AIRCRAFT_NOT_IN_THE_AIR` | "A drón nincs a levegőben — kézzel szállj fel" |

---

## 6. Akkumulátor csere döntési logika

```
1. szegmens végrehajtva → drón RTH (config.returnHome = true)
  │
  ▼
Drón leszállt, akkumulátor lemerülőben
  │
  ▼
Operátor:
  1. Drón kikapcsol (akkumulátor csere)
  2. Új akkumulátor behelyezése
  3. Drón bekapcsol, Crystal Sky újracsatlakozik
  │
  ▼
App állapot:
  isMissionRunning = false (DJI disconnect esemény)
  btnUpload: enabled
  Statisztika: "Szegmens 1/3 kész — következő: [Feltöltés + Start]"
  │
  ▼
Operátor: [Feltöltés + Start] → 2. szegmens feltöltése és indítása
```

---

## 7. Kézi vezérlés visszavétel döntési logika

```
MSDK event: onExecutionUpdate() → currentWaypointIndex folyamatosan nő
  → Opcionális: M01-ben progress bar megjelenítése

RC bal joystick (throttle/rudder) >30% kitérés:
  → MSDK automatikusan szünetelteti a missziót
  → operator.getCurrentState() → EXECUTION_PAUSED
  → App: btnPauseMission.setText("▶ Folytatás") [ha állapot figyelve van]

RC RTH gomb:
  → Misszió leáll
  → operator.getCurrentState() → READY_TO_UPLOAD
  → App: setMissionRunning(false)

Megjegyzés: Az MSDK állapotfigyelés (addListener) implementálása
opcionális a v1.0.0-ban — a stop/pause az app gombokkal vezérelt.
```

---

## 8. Kamera feed döntési logika (DroneVideoWidget)

```
CAM gomb megnyomva:
  videoWidget.isRunning()?
    │ IGEN → stop() → cameraWindow GONE → gomb szín: #CC1a1a2e (sötét)
    │ NEM  → cameraWindow VISIBLE → start() → gomb szín: #CC226622 (zöld)

start() híváskor:
  activeSurface != null?
    │ IGEN → attachCodecAndFeed() azonnal
    │ NEM  → vár onSurfaceTextureAvailable() callback-re

attachCodecAndFeed():
  DJICodecManager létrehozható?
    │ ClassNotFoundException → emulátoros stub (nincs crash, kép fekete)
    │ OK → DJICodecManager inicializálva
  VideoFeeder.getInstance() == null?
    │ IGEN → SDK még nem inicializált → return
    │ NEM  → getPrimaryVideoFeed()
  getPrimaryVideoFeed() == null?
    │ IGEN → kamera még nem aktív → releaseCodec() → return
    │ NEM  → addVideoDataListener(proxy) → feed aktív
```

---

## 9. Tap-to-expose döntési logika

```
Érintés a TextureView-n:
  videoWidget.isRunning() == false?
    │ IGEN → esemény elnyelve (return false), semmi sem történik
    │ NEM  → folytatás

  nx = touchX / textureView.getWidth()
  ny = touchY / textureView.getHeight()
  → tapToFocus(nx, ny) hívás
  → showFocusRing(touchX, touchY) hívás

tapToFocus(nx, ny):
  DJISDKManager.getProduct() == null || !isConnected()?
    │ IGEN → return (csendesen)
    │ NEM  → getCamera() → null?
              │ IGEN → return
              │ NEM  →

  Camera.setFocusMode(FocusMode.AUTO) keresése reflexióval:
    setFocusMode metódus megtalálható?
      │ NEM  → "setFocusMode metódus nem található" log → return
      │ IGEN → proxy callback létrehozva (hashCode/equals/toString kezelve)

  setFocusMode callback:
    onResult(error == null)?
      │ MINDKÉT esetben → setFocusTarget(camera, nx, ny) hívás
      │   (hiba esetén is próbálkozunk — a P4P tap-to-expose tolerálja)

  setFocusTarget(camera, nx, ny):
    setFocusTarget metódus megtalálható?
      │ NEM  → return
      │ IGEN → invoke(camera, PointF(nx, ny), proxy)
                → Drón expozíció az érintett pontra igazítva

P4P v1 hardver korlát:
  FIX FÓKUSZÚ lencse → a fókuszszint nem változik
  A setFocusTarget() mégis hasznos: expozíciót / AE mérési pontot állítja
  Viselkedés: azonos a DJI Go 4 "tap-to-focus" funkciójával P4P-n
```

---

## 10. Mintavételi fotó-trigger döntés — WaypointAction vs. app-vezérelt Camera hívás — ✅ Implementálva (2026-07-03)

**Kontextus:** terepi teszt (2026-07-03) kimutatta, hogy a §15 szerinti
`WaypointAction(START_TAKE_PHOTO)` nem megbízható (M04_L1 §18). Az alábbi
döntési logika a javítás irányát rögzíti.

```
Fotó-trigger mechanizmus választás mintavételi waypointon:

  Opció A — WaypointAction (jelenlegi, MEGBÍZHATATLANNAK bizonyult):
    Előny:  egyszerű, a misszió-motor "magától" végrehajtja
    Hátrány: nincs siker/hiba visszaigazolás, nincs újrapróbálkozási lehetőség,
             a DJI firmware csendben eldobhatja az akciót

  Opció B — app-vezérelt Camera.startShootPhoto() (VÁLASZTOTT):
    Előny:  van SDK-szintű onResult/hiba callback → újrapróbálkozás lehetséges,
            a §9 (tap-to-expose) reflection-mintája újrahasznosítható
            (Camera metódus keresés reflexióval, proxy callback hashCode/equals/
            toString kezeléssel)
    Hátrány: az app-nak pontosan tudnia kell, MELYIK onWaypointReached esemény
             tartozik mintavételi (nem transit) ponthoz

  → Opció B választva, mert a megbízhatóság (retry-képesség) fontosabb, mint
    az egyszerűség — egy mezőgazdasági felmérésnél a hiányzó fotó közvetlen
    adatvesztés (az adott mintapont teljes egészében kiesik a tőszámlálásból).

Melyik waypoint a "mintavételi" pont?
  A SamplingMissionGenerator (M02 §7) minden mintaponthoz pontosan 3 waypointot
  generál, FIX sorrendben: [érkezés, mintavétel, emelkedés].
  → mintavételi waypoint index = 3×k + 1  (k = 0, 1, 2, ... mintapont sorszáma)
  → onWaypointReached(index, total) callback-ben:
      IF (index % 3 == 1): ez egy mintavételi pont → fotó-trigger indítása
      ELSE: transit waypoint → nincs teendő

Fotó-trigger + retry állapotgép (egy mintavételi ponton):
  ÁLLAPOT: WAITING_STABILIZE (rövid, pl. 500 ms késleltetés a hover-lengés
           lecsengéséhez, mielőtt a kamera exponál)
      │
      ▼
  ÁLLAPOT: SHOOTING (Camera.startShootPhoto() hívás, reflexióval)
      │
      ├─ onResult(error == null) → ÁLLAPOT: CONFIRMED (naplózva: "mintapont #k OK")
      │
      └─ onResult(error != null) →
            attemptCount < 2?
              │ IGEN → 1 mp késleltetés → vissza SHOOTING (attemptCount++)
              │ NEM  → ÁLLAPOT: FAILED (naplózva: "mintapont #k — fotó sikertelen,
                        kézi ellenőrzés szükséges" — nem blokkolja a misszió
                        folytatását, csak jelöl)
```

**Miért nem blokkoló a FAILED állapot:** egy mintavételi misszió jellemzően
20–50 pontot érint — egyetlen pont kiesése a statisztikai extrapolációt (ld.
tervezett M09 Edge AI modul) minimálisan torzítja, míg a teljes misszió
megszakítása minden eddig sikeresen lefotózott pontot is elveszítene. A
felhasználó a CONFIRMED/FAILED jelölésekből utólag látja, mely pontokat
érdemes esetleg külön, kézi repüléssel pótolni.
