# L3 – Állapotgép és Engine – Misszió Tervező

**Modul:** M01
**Szint:** L3 – Állapotgép és Engine
**Verzió:** v1.9.9
**Létrehozva:** 2026-04-02
**Utolsó módosítás:** 2026-04-17
**Státusz:** ✅ Implementálva (v1.9.9)

---

## Forrásfájlok

| Fájl | Szerepkör | Státusz |
|------|-----------|---------|
| `MissionPlannerActivity.java` | Fő activity — térkép, UI kezelés, misszió vezérlés | ✅ Implementálva |
| `model/MissionConfig.java` | Repülési paraméterek adatmodellje | ✅ Implementálva |
| `model/WaypointData.java` | Egyedi waypoint adatmodellje | ✅ Implementálva |
| `model/ObstacleData.java` | Akadály adatmodellje | ✅ Implementálva |
| `model/DroneProfile.java` | Drón kamera profil adatmodellje | ✅ Implementálva |
| `model/DroneProfiles.java` | Ismert drón profilok listája | ✅ Implementálva |
| `layers/OverpassClient.java` | HTTP POST kliens az OSM Overpass API-hoz (SSL bypass Android 5.1) | ✅ Implementálva |
| `layers/ProtectedAreasLayer.java` | N2K réteg — Natura 2000 + védett területek Overpass-ból | ✅ Implementálva |
| `layers/AirspaceLayer.java` | LGT réteg — légterek OpenAIP Core API-ból, magassági szűrővel, színkitöltéssel | ✅ Implementálva |
| `layers/LandUseLayer.java` | ZÓN réteg — területhasználati zónák Overpass-ból | ✅ Implementálva |
| `res/layout/activity_mission_planner.xml` | UI layout — teljes képernyős térkép + lebegő panel | ✅ Implementálva |
| `res/drawable/status_bar_bg.xml` | Státuszsáv lekerekített háttér drawable | ✅ Implementálva |

**Függőségek:**
- `mission/GridMissionGenerator.java` — M02 Grid Engine
- `mission/MissionExporter.java` — M03 Export
- `mission/CsvMissionParser.java` — M03 Import
- `mission/ProjectManager.java` — M03 Projekt mentés/betöltés (JSON)
- `dji/MissionUploader.java` — M04 DJI Integráció
- `dji/DJIHelper.java` — M04 DJI telemetria
- OSMDroid `MapView`, `Marker`, `Polygon`, `Polyline`, `MyLocationNewOverlay`, `Overlay`

---

## MissionPlannerActivity — mezők

```java
// Térkép és overlay-ek
MapView mapView;
MyLocationNewOverlay locationOverlay;
List<GeoPoint> polygonPoints  = new ArrayList<>();
List<Marker>   polygonMarkers = new ArrayList<>();  // API 22: removeIf kiváltva
Polygon  polygonOverlay;
Polyline missionOverlay;
Marker   startMarker;
GeoPoint startPoint = null;

// Akadályok
List<ObstacleData> obstacleList    = new ArrayList<>();
List<Marker>       obstacleMarkers = new ArrayList<>();
List<Polygon>      obstacleOverlays = new ArrayList<>();
boolean obstacleMode = false;

// Projekt mentés / betöltés
Button btnSaveProject, btnLoadProject;
String lastLoadedProjectName = null;  // előtöltés a mentés dialóghoz

// Állapot
boolean startPointMode = false;
boolean missionUploaded = false;
boolean missionRunning = false;
boolean missionPaused  = false;
int currentSegmentIndex = 0;
GridMissionGenerator.GeneratorResult lastResult;

// Státuszsáv
TextView sbDrone, sbRc, sbRcBatt, sbGps, sbDroneBatt, sbTabletBatt, sbKp;
Handler statusHandler = new Handler(Looper.getMainLooper());
static final int STATUS_INTERVAL_MS = 2000;
long lastKpFetchMs = 0;
static final long KP_FETCH_INTERVAL_MS = 10 * 60 * 1000L; // 10 perc

// Panel
FrameLayout sidePanelContainer;
ScrollView sidePanel;
Button btnTogglePanel;
boolean panelVisible = true;

// Kamera beállítások
LinearLayout cameraSettingsBody, cameraManualControls;
Switch switchCameraAuto;
Spinner spinnerPhotoMode, spinnerIso, spinnerShutter, spinnerWhiteBalance, spinnerFileFormat;
boolean cameraExpanded = false;

// Domborzatkövetés
Switch switchTerrain;
TextView tvTerrainInfo;

// Térkép rétegek
Button btnLayerProtected, btnLayerAirspace, btnLayerLandUse;
ProtectedAreasLayer protectedLayer;        // N2K réteg (Overpass API)
AirspaceLayer airspaceLayer;               // LGT réteg (OpenAIP Core API)
LandUseLayer landUseLayer;                 // ZÓN réteg (Overpass API)
static final String OPENAIP_API_KEY = "ebe3e1941252167b80e2e974613600a1";
// LGT magassági szűrő
Button btnAltFilter;                       // ALT:∞ / ALT:Xm – koppintásra léptet
int altPresetIndex = 0;                    // index az AirspaceLayer.ALT_PRESETS tömbben
// ALT_PRESETS = {0, 30, 40, 50, 60, 80, 100, 120} (0 = összes/∞, max 120 m = EU Open Category)
// ⚠ LGT bekapcsoláskor: mapView.setLayerType(LAYER_TYPE_SOFTWARE, null)
//   Oka: Android 5.1 Adreno GPU HWUI bug – drawPath(fill) és drawBitmap egyaránt crashel
//   a RenderThread-ben (libhwui.so SIGSEGV). Szoftveres renderelés (Skia) megkerüli a bugot.
//   LGT kikapcsoláskor: mapView.setLayerType(LAYER_TYPE_NONE, null) → visszaáll HW accel.

// UI elemek
TextView tvGsd, tvSidelap, tvFrontlap, tvSpeed, tvAngle, tvOffset, tvStats;
SeekBar  sbGsd, sbSidelap, sbFrontlap, sbSpeed, sbAngle, sbOffset;
Spinner  spinnerDrone;
Button   btnSaveProject, btnLoadProject,
         btnUndoPoint, btnObstacle, btnClearObstacles, btnClear, btnGenerate,
         btnUpload, btnStart, btnImportCsv, btnExport, btnSetStart,
         btnMyLocation, btnMapToggle, btnPauseMission, btnStopMission,
         btnRec;                         // képernyőkép / videórögzítés

// GPS pozíció cache (drón GPS preferált, tablet GPS fallback)
double lastDroneLat = 0, lastDroneLon = 0;  // DJI FlightController StateCallback tárolja

// Képernyőrögzítés (v1.9.7)
MediaProjectionManager projectionManager;
MediaProjection  mediaProjection;
MediaRecorder    mediaRecorder;
VirtualDisplay   virtualDisplay;
boolean          isRecording = false;
File             currentRecordingFile;
Handler          recBlinkHandler;       // REC gomb villogtatás
```

---

## MissionPlannerActivity — lifecycle

```
onCreate()
  │
  ├─ OSMDroid konfiguráció:
  │    setOsmdroidBasePath() → external storage / cache
  │    setTileFileSystemCacheMaxBytes(100 MB)
  │    setUserAgentValue("DroneFlyApp/1.0 ...")
  │
  ├─ setContentView(R.layout.activity_mission_planner)
  │
  ├─ initMap()
  │    mapView: ESRI World Imagery műhold tiles (HTTPS)
  │    setMultiTouchControls(true)
  │    center: (47.1, 19.5), zoom: 7
  │    RotationGestureOverlay → forgatás gesztus
  │    MyLocationNewOverlay → GPS pozíció
  │    MapEventsOverlay → érintésfigyelő
  │      singleTapConfirmedHelper:
  │        startPointMode? → setStartPoint()
  │        obstacleMode?   → showAddObstacleDialog()
  │        else (nem fut misszió) → addPolygonPoint()
  │      longPressHelper: return false (marker drag nem ütközik)
  │
  ├─ initControls()
  │    SeekBar-ok bekötése, gomb listener-ek
  │    spinnerDrone (DroneProfiles.ALL)
  │    initCameraControls(), initTerrainControls(), initStatusBar()
  │    updateLabels()
  │    projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE)
  │    btnRec: click → takeScreenshot() | longClick → toggleRecording()
  │
  └─ showDisclaimerIfNeeded()

onResume()
  → DJIHelper.setListener() → updateDroneStatus()
  → statusHandler.post(statusRunnable)  // 2s státusz polling indul
  → mapView.onResume()

onPause()
  → statusHandler.removeCallbacks(statusRunnable)
  → mapView.onPause()

onDestroy()
  → statusHandler.removeCallbacks(statusRunnable)
  → mapView.onDetach()
```

---

## Állapotgép

```
                    ┌─────────────────────────────────┐
                    │            IDLE                  │
                    │  - polygonPoints: üres           │
                    │  - mission: nincs                │
                    └──────────┬──────────────────────┘
                               │ ≥1 pont lerakva
                               ▼
                    ┌─────────────────────────────────┐
                    │        DRAWING                   │
                    │  - 1–2 pont: csak vonal          │
                    │  - stats: "N pont – még X kell"  │
                    └──────────┬──────────────────────┘
                               │ 3. pont lerakva
                               ▼ (autoGenerateIfReady)
                    ┌─────────────────────────────────┐
                    │        MISSION_READY             │
                    │  - narancs polyline megjelenik   │
                    │  - stats megjelenítve            │
                    │  - Feltöltés/Export engedélyezve │
                    └──────┬───────────┬──────────────┘
               [Feltöltés] │           │ [Exportálás]
                            │           ▼
                            │   ┌──────────────┐
                            │   │  EXPORTED    │
                            │   │  CSV / KMZ   │
                            │   └──────────────┘
                            ▼
                    ┌─────────────────────────────────┐
                    │     UPLOADED (feltöltve)         │
                    │  - START gomb engedélyezve       │
                    └──────────┬──────────────────────┘
                               │ [START]
                               ▼
                    ┌─────────────────────────────────┐
                    │       MISSION_RUNNING            │
                    │  - missionRunning = true         │
                    │  - Szünet/Stop gombok aktívak    │
                    └──────┬──────────────────────────┘
                           │ "Szünet"
                           ▼
                    ┌─────────────────────────────────┐
                    │       MISSION_PAUSED             │
                    │  - missionPaused = true          │
                    │  - drón lebeg                    │
                    └──────┬──────────────────────────┘
                           │ "Folytatás"
                           ▼
                    (vissza MISSION_RUNNING-ba)

                    MISSION_RUNNING / PAUSED
                           │ "Stop" + megerősítés
                           ▼
                    (vissza MISSION_READY-be)

Bármely állapotban:
  - Csúcspont drag/törlés/hozzáadás → autoGenerateIfReady()
  - Akadály hozzáadás/törlés → autoGenerateIfReady()
  - MISSION_RUNNING állapotban polygon szerkesztés nem lehetséges
```

---

## autoGenerateIfReady() — azonnali misszió generálás

```java
void autoGenerateIfReady() {
    if (polygonPoints.size() < 3) {
        // 3 pontnál kevesebb: útvonal törlése, stats frissítés
        // "N pont – még X kell a területhez"
        return;
    }
    MissionConfig config = buildConfig();
    config.terrainFollowing = false; // auto-generálásnál nincs DEM lekérés
    lastResult = GridMissionGenerator.generate(polygonPoints, config);

    if (lastResult.errorMessage != null) {
        tvStats.setText("Hiba: " + lastResult.errorMessage);
        return;
    }
    drawMissionPath(lastResult.segments);
    btnUpload.setEnabled(DJIHelper.isConnected() && !missionRunning);
    btnExport.setEnabled(true);
    // Stats megjelenítés (skippedByObstacle is ha >0)
    // 120m EU határt figyelmeztet Toast-tal
}
```

---

## buildConfig() — MissionConfig összeállítása

```java
MissionConfig buildConfig() {
    MissionConfig c = new MissionConfig();
    c.droneProfile     = getSelectedDrone();              // Spinner kiválasztott profil
    c.gsdCm            = 0.5 + sbGsd.getProgress() * 0.1;
    c.altitudeM        = GsdCalculator.altitudeFromGsd(c.gsdCm, c.droneProfile);
    c.sidelapPercent   = 50.0 + sbSidelap.getProgress(); // 50–90%
    c.frontlapPercent  = 60.0 + sbFrontlap.getProgress();// 60–90%
    c.speedMs          = 3.0f + sbSpeed.getProgress();   // 3–15 m/s
    c.flightAngleDeg   = sbAngle.getProgress();          // 0–179°
    c.terrainFollowing = switchTerrain.isChecked();
    c.offsetM          = sbOffset.getProgress();         // 0–30 m
    c.obstacles        = new ArrayList<>(obstacleList);  // akadályok másolata
    c.cameraSettings   = buildCameraSettings();
    return c;
}
```

---

## Obstacle rendszer

```java
// Akadály hozzáadása
void addObstacle(GeoPoint p, float radiusM, float heightM) {
    ObstacleData obs = new ObstacleData(lat, lon, radiusM, heightM);
    obstacleList.add(obs);

    // Kör overlay (36 szegmens közelítés)
    Polygon circle = buildCircleOverlay(lat, lon, radiusM);
    // fill: 0x44FF3300 (áttetsző piros), outline: 0xCCFF4400 (narancs)
    obstacleOverlays.add(circle);
    mapView.getOverlays().add(0, circle);  // legalsó overlay réteg

    // Marker (ANCHOR_CENTER, CENTER) + kattintás → info + törlés dialog
    obstacleMarkers.add(marker);
    autoGenerateIfReady();
}

// Kör polygon összeállítás
Polygon buildCircleOverlay(double lat, double lon, float radiusM) {
    List<GeoPoint> pts = new ArrayList<>();
    for (int i = 0; i <= 36; i++) {
        double angle = 2π * i / 36;
        double dLat = radiusM * cos(angle) / 111000.0;
        double dLon = radiusM * sin(angle) / (111000.0 * cos(toRadians(lat)));
        pts.add(new GeoPoint(lat + dLat, lon + dLon));
    }
    // ...
}
```

**Obstacle mód váltás:**
```java
void toggleObstacleMode() {
    obstacleMode = !obstacleMode;
    // Gomb szöveg + szín változik (actív: narancsvörös, inaktív: sötétpiros)
}
```

**Törlés:**
```java
void removeObstacle(int index)      // egyedi törlés index szerint
void clearAllObstacles()            // megerősítő dialog → összes törlés
// clearAll() is törli az összes akadályt
```

---

## Státuszsáv — updateStatusBar()

```java
// 2 mp-enként fut: statusHandler.postDelayed(statusRunnable, 2000)

void updateStatusBar() {
    // Tablet akku (szinkron)
    Intent bi = registerReceiver(null, ACTION_BATTERY_CHANGED);
    int pct = bi.level * 100 / bi.scale;
    sbTabletBatt.setText("TAB: " + pct + "%" + (charging ? "+" : ""));
    // szín: <20%=piros, <40%=narancs, ≥40%=zöld

    // Kp-index (NOAA, 10 percenként) — dróntól FÜGGETLENÜL fut
    if (sbKp != null && (now - lastKpFetchMs) >= KP_FETCH_INTERVAL_MS) {
        lastKpFetchMs = now;
        KpIndexProvider.fetch(kp -> {
            // callback főszálon érkezik vissza
            if (kp < 0) { sbKp.setText("MAG: --"); /* szürke */ }
            else {
                sbKp.setText("MAG: " + kp);
                // 0–2: zöld | 3–4: sárga | 5: narancs | 6+: piros
            }
        });
    }

    // DJI telemetria (DJIHelper reflection-alapú)
    if (!DJIHelper.isConnected()) {
        // minden mező "--" → szürkére
        return;  // ← itt tér vissza ha nincs drón; Kp-index már lefutott
    }
    sbDrone.setText(DJIHelper.getConnectedProductName());   // zöld
    sbRc.setText("RC: " + (DJIHelper.isRcConnected() ? "OK" : "nincs"));

    DJIHelper.getRcBatteryPercent(pct -> runOnUiThread(
        () -> sbRcBatt.setText(pct + "%")));

    DJIHelper.getDroneBatteryPercent(pct -> runOnUiThread(
        () -> sbDroneBatt.setText("AKKU: " + pct + "%")));

    DJIHelper.setFlightStateCallback((sats, homeSet) -> runOnUiThread(
        () -> sbGps.setText("SAT: " + sats + (homeSet ? " H" : ""))));
}
```

---

## Panel csúsztatás — toggleSidePanel()

```java
void toggleSidePanel() {
    panelVisible = !panelVisible;
    if (panelVisible) {
        sidePanel.setVisibility(VISIBLE);
        ObjectAnimator.ofFloat(sidePanel, "translationX", panelWidth, 0f)
            .setDuration(250).setInterpolator(new DecelerateInterpolator()).start();
        btnTogglePanel.setText("»");  // » = zárásra kész
    } else {
        ObjectAnimator.ofFloat(sidePanel, "translationX", 0f, panelWidth)
            .setDuration(250).addListener(onEnd → sidePanel.setVisibility(GONE)).start();
        btnTogglePanel.setText("«");  // « = nyitásra kész
    }
    // Csak a ScrollView animálódik; a toggle gomb helyben marad
}
```

---

## MissionConfig adatmodell

```java
public class MissionConfig {
    public double gsdCm = 3.0;
    public double altitudeM = 80.0;
    public double sidelapPercent = 75.0;
    public double frontlapPercent = 80.0;
    public float speedMs = 7.0f;
    public double flightAngleDeg = 0.0;
    public boolean returnHome = true;
    public boolean terrainFollowing = false;
    public double offsetM = 0.0;                    // túlrepülési határ (m)
    public DroneProfile droneProfile;               // kamera profil
    public CameraSettings cameraSettings;           // kamera módok
    public List<ObstacleData> obstacles = new ArrayList<>();  // akadályok
}
```

## ObstacleData adatmodell

```java
public class ObstacleData {
    public double latitude, longitude;
    public float radiusM;    // biztonsági zóna sugara (m)
    public float heightM;    // akadály magassága talajhoz képest (m)

    // Veszélyes-e ezen a magasságon?
    public boolean isDangerousAt(float flightAltM) {
        return flightAltM <= heightM;
    }

    // 2D körzetben van-e a waypoint?
    public boolean containsPoint(double lat, double lon) {
        double dLat = (lat - latitude) * 111000.0;
        double dLon = (lon - longitude) * 111000.0 * cos(toRadians(latitude));
        return sqrt(dLat*dLat + dLon*dLon) <= radiusM;
    }
}
```

---

## Layout struktúra (v1.8.0)

```
FrameLayout (rootLayout, full screen)
├─ FrameLayout (mapContainer, match_parent)
│   ├─ org.osmdroid.views.MapView (id: mapView)
│   └─ LinearLayout (vertical, top|start, margin 8dp)
│       ├─ Button (id: btnMyLocation, 48×48dp, "GPS", #CC1a1a2e)
│       │          click → jumpToCurrentPosition()
│       │          drón GPS (lastDroneLat/Lon) preferált → tablet GPS (locationOverlay) fallback
│       │          zoom 15, setZoom() + animateTo(), toast: forrás + pontosság
│       ├─ Button (id: btnMapToggle, 48×48dp, "SAT"/"MAP", #CC1a1a2e)
│       │          click → toggleMapSource()  |  longClick → downloadMapAreaForOffline()
│       │          offline letöltés: zoom 14–17, /sdcard/osmdroid/ cache
│       ├─ Button (id: btnCamToggle, 48×48dp, "CAM", #CC1a1a2e)
│       ├─ Button (id: btnRec, 48×48dp, "REC"/"■", #CC1a1a2e / #CCCC0000 / #CCAA6600)
│       │          click → takeScreenshot() → PNG /sdcard/Pictures/DroneFly/screenshot_*.png
│       │            gomb 400 ms zöld villanás (#CC228822) visszajelzésként
│       │          longClick → toggleRecording()
│       │            start: MediaProjection engedélykérés → startScreenRecording()
│       │              720p, 25fps, 2 Mbps H264 (Crystal Sky Android 5.1 encoder limit)
│       │              → MP4 /sdcard/Pictures/DroneFly/video_*.mp4
│       │              felvétel közben: gomb pirosan villog (600 ms), szöveg "■"
│       │            stop: stopRecording() — UI azonnal visszaáll (főszál)
│       │              gomb narancssárga (#CCAA6600) = mentés folyamatban (háttérszál)
│       │              MediaRecorder.stop() + release() + VirtualDisplay.release()
│       │              + MediaProjection.stop() + MediaScannerConnection → háttérszál
│       │              kész: gomb visszaáll (#CC1a1a2e), toast: "✔ Mentve: video_*.mp4"
│       ├─ View (elválasztó, 32×1dp, #CC334466)
│       ├─ Button (id: btnLayerProtected, 48×48dp, "N2K", #CC1a2e1a)
│       ├─ Button (id: btnLayerAirspace, 48×48dp, "LGT", #CC2e1a1a)
│       ├─ Button (id: btnAltFilter, 48×44dp, "ALT:∞"/"ALT:Xm", #991a0a0a, #FFAA44)
│       │          koppintásra: ∞→30m→40m→50m→60m→80m→100m→120m→∞
│       └─ Button (id: btnLayerLandUse, 48×48dp, "ZÓN", #CC2e1a00)
│
├─ LinearLayout (id: statusBar, top, 38dp magasság, lebegő sziget)
│   │   marginStart=64dp, marginEnd=364dp (GPS gombok és panel között)
│   │   background: status_bar_bg (lekerekített, #DD1a1a2e), elevation=6dp
│   ├─ TextView (id: sbDrone)     "DRON: --" / drón neve
│   ├─ separator
│   ├─ TextView (id: sbRc)        "RC: --" / "RC: OK"
│   ├─ TextView (id: sbRcBatt)    "" / "85%"
│   ├─ separator
│   ├─ TextView (id: sbGps)       "SAT: --" / "SAT: 14 H"
│   ├─ separator
│   ├─ TextView (id: sbDroneBatt) "DRON AKKU: --" / "AKKU: 92%"
│   ├─ separator
│   ├─ TextView (id: sbTabletBatt)"TAB: --" / "TAB: 78%"
│   ├─ separator
│   └─ TextView (id: sbKp)       "MAG: --" / "MAG: 0–9" (zöld/sárga/narancs/piros)
│
└─ FrameLayout (id: sidePanelContainer, end, elevation=8dp)
    │   (együtt csúszik: toggle gomb + panel ScrollView)
    ├─ Button (id: btnTogglePanel, 36×72dp, start|center_vertical)
    │          "»" = panel látható (zárásra kész)
    │          "«" = panel rejtett (nyitásra kész)
    └─ ScrollView (id: sidePanel, 320dp széles, end)
        └─ LinearLayout (vertical, 12dp padding)
            ├─ TextView "Drón" + Spinner (id: spinnerDrone)
            ├─ TextView (id: tvDroneStatus)   DJI kapcsolat státusz
            ├─ TextView "Misszió beállítások" (bold, 16sp)
            ├─ TextView (id: tvGsd) + SeekBar (id: sbGsd, max=95)
            ├─ TextView (id: tvSidelap) + SeekBar (id: sbSidelap, max=40)
            ├─ TextView (id: tvFrontlap) + SeekBar (id: sbFrontlap, max=30)
            ├─ TextView (id: tvSpeed) + SeekBar (id: sbSpeed, max=12)
            ├─ TextView (id: tvAngle) + SeekBar (id: sbAngle, max=179)
            ├─ TextView (id: tvOffset) + SeekBar (id: sbOffset, max=30)
            ├─ separator
            ├─ LinearLayout "Domborzatkövetés:" + Switch (id: switchTerrain)
            ├─ TextView (id: tvTerrainInfo)
            ├─ separator
            ├─ LinearLayout (id: cameraHeader) "Kamera beállítások" + ▼/▲
            ├─ LinearLayout (id: cameraSettingsBody, visibility=gone)
            │   ├─ Switch (id: switchCameraAuto)
            │   ├─ Spinner (id: spinnerFileFormat)
            │   └─ LinearLayout (id: cameraManualControls, visibility=gone)
            │       ├─ Spinner (id: spinnerPhotoMode)
            │       ├─ Spinner (id: spinnerIso)
            │       ├─ Spinner (id: spinnerShutter)
            │       └─ Spinner (id: spinnerWhiteBalance)
            ├─ separator
            ├─ Button (id: btnUndoPoint, "Utolso pont torles", #334466)
            ├─ Button (id: btnObstacle, "+ Akadaly jelolese", #662200)
            │          aktív állapotban: "Akadaly mod: BE...", #CC4400
            ├─ Button (id: btnClearObstacles, "Akadalyok torlese", #441100)
            ├─ Button (id: btnClear, "Osszes torles", #660000)
            ├─ Button (id: btnGenerate, "Ujragenerales (beallitasokkal)", #006600)
            ├─ Button (id: btnSetStart, "Start/Home pont", #005566)
            ├─ Button (id: btnImportCsv, "CSV importalasa", #555500)
            ├─ Button (id: btnExport, "Exportalas (CSV / KMZ)", #334400)
            ├─ Button (id: btnUpload, "Feltoltes", #884400)
            ├─ Button (id: btnStart, "START", #FF6600, bold, 52dp, disabled)
            ├─ LinearLayout (horizontal)
            │   ├─ Button (id: btnPauseMission, "Szunet", disabled)
            │   └─ Button (id: btnStopMission, "Stop", disabled)
            └─ TextView (id: tvStats, #AAAAAA, 11sp)
```

---

## API 22 kompatibilitás

```java
// HELYTELEN (API 24+ szükséges, Crystal Sky API 22):
mapView.getOverlays().removeIf(o -> o instanceof Marker);

// HELYES (API 22-n is működik):
for (Marker m : polygonMarkers) {
    mapView.getOverlays().remove(m);
}
polygonMarkers.clear();
// → polygonMarkers, obstacleMarkers, obstacleOverlays listák
//   mind explicit for loop-pal kerülnek törlésre
```
