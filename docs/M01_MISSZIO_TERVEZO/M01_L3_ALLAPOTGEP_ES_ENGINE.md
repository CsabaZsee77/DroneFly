# L3 – Állapotgép és Engine – Misszió Tervező

**Modul:** M01
**Szint:** L3 – Állapotgép és Engine
**Verzió:** v1.0.0
**Létrehozva:** 2026-04-02
**Státusz:** ✅ Implementálva (v1.0.0)

---

## Forrásfájlok

| Fájl | Szerepkör | Státusz |
|------|-----------|---------|
| `MissionPlannerActivity.java` | Fő activity — térkép, UI kezelés, misszió vezérlés | ✅ Implementálva |
| `model/MissionConfig.java` | Repülési paraméterek adatmodellje | ✅ Implementálva |
| `model/WaypointData.java` | Egyedi waypoint adatmodellje | ✅ Implementálva |
| `res/layout/activity_mission_planner.xml` | UI layout — térkép 65% + panel 35% | ✅ Implementálva |

**Függőségek:**
- `mission/GridMissionGenerator.java` — M02 Grid Engine
- `mission/MissionExporter.java` — M03 Export
- `mission/CsvMissionParser.java` — M03 Import
- `dji/MissionUploader.java` — M04 DJI Integráció
- OSMDroid `MapView`, `MapController`, `MyLocationNewOverlay`

---

## MissionPlannerActivity — mezők

```java
// Térkép és overlay-ek
MapView mapView;
MapController mapController;
MyLocationNewOverlay locationOverlay;
Polyline missionPolyline;           // narancs útvonal
List<Marker> polygonMarkers;        // kék csúcspontok
Marker startMarker;                 // zöld Home pont

// Állapot
List<GeoPoint> polygonPoints;       // rajzolt polygon csúcsok
GeoPoint startPoint;                // Home pont (null = nincs beállítva)
boolean isDrawMode = false;
boolean isMissionRunning = false;
boolean isPaused = false;
int currentSegmentIndex = 0;

// Misszió adatok
List<List<WaypointData>> currentSegments;   // szegmensek listája
List<WaypointData> currentWaypoints;        // összes waypoint (flat)

// DJI
MissionUploader uploader;

// UI elemek (SeekBar-ok és Label-ek)
SeekBar sbGsd, sbSidelap, sbFrontlap, sbSpeed, sbAngle;
TextView tvGsd, tvSidelap, tvFrontlap, tvSpeed, tvAngle, tvStats;
Button btnDrawMode, btnClear, btnGenerate, btnSetStart;
Button btnImportCsv, btnExport, btnUpload;
Button btnPauseMission, btnStopMission;
Button btnMyLocation;
```

---

## MissionPlannerActivity — lifecycle

```
onCreate()
  │
  ├─ OSMDroid konfiguráció:
  │    Configuration.getInstance().setUserAgentValue("DroneFlyApp/1.0 ...")
  │    Configuration.getInstance().setOsmdroidTileCache(getCacheDir()/osmdroid)
  │
  ├─ setContentView(R.layout.activity_mission_planner)
  │
  ├─ mapView setup:
  │    setUseDataConnection(true)
  │    setTileSource(XYTileSource HTTPS)
  │    setBuiltInZoomControls(true)
  │    setMultiTouchControls(true)
  │    mapController.setCenter(47.1, 19.5)
  │    mapController.setZoom(7)
  │
  ├─ locationOverlay setup:
  │    new MyLocationNewOverlay(GpsMyLocationProvider, mapView)
  │    enableMyLocation()
  │    mapView.getOverlays().add(locationOverlay)
  │
  ├─ Touch listener (térkép érintés):
  │    onSingleTapConfirmed → isDrawMode? → addPolygonPoint()
  │    onLongPress → setStartPoint()
  │
  ├─ SeekBar listeners (onProgressChanged):
  │    sbGsd → updateGsdLabel() + updateSpeedFromGsd()
  │    sbSidelap → updateSidelapLabel()
  │    sbFrontlap → updateFrontlapLabel()
  │    sbSpeed → updateSpeedLabel()
  │    sbAngle → updateAngleLabel()
  │
  ├─ Default értékek betöltése:
  │    sbGsd.setProgress(25)      → 3.0 cm/px
  │    sbSidelap.setProgress(25)  → 75%
  │    sbFrontlap.setProgress(30) → 80%
  │    sbSpeed = auto (GSD-ből)
  │    sbAngle.setProgress(0)     → 0°
  │
  ├─ Gomb listenersek bekötése
  │
  └─ uploader = new MissionUploader()

onResume() → mapView.onResume()
onPause()  → mapView.onPause()
```

---

## Állapotgép

```
                    ┌─────────────────────────────────┐
                    │            IDLE                  │
                    │  - polygon: üres                 │
                    │  - mission: nincs                │
                    └──────────┬──────────────────────┘
                               │ polygon rajzolva (≥3 pont)
                               ▼
                    ┌─────────────────────────────────┐
                    │        POLYGON_DRAWN             │
                    │  - polygonPoints: feltöltve      │
                    │  - mission: nincs                │
                    └──────────┬──────────────────────┘
                               │ "Misszió generálása"
                               ▼
                    ┌─────────────────────────────────┐
                    │        MISSION_READY             │
                    │  - currentSegments: feltöltve    │
                    │  - polyline: megjelenítve        │
                    │  - stats: megjelenítve           │
                    └──────┬───────────┬──────────────┘
               feltöltés+  │           │ export
               start        │           ▼
                            │   ┌──────────────┐
                            │   │  EXPORTED    │
                            │   │  CSV / KMZ   │
                            │   └──────────────┘
                            ▼
                    ┌─────────────────────────────────┐
                    │       MISSION_RUNNING            │
                    │  - isMissionRunning = true       │
                    │  - Pause/Stop gombok aktívak     │
                    └──────┬──────────────────────────┘
                           │ "⏸ Szünet"
                           ▼
                    ┌─────────────────────────────────┐
                    │       MISSION_PAUSED             │
                    │  - isPaused = true               │
                    │  - drón lebeg                    │
                    └──────┬──────────────────────────┘
                           │ "▶ Folytatás"
                           ▼
                    (vissza MISSION_RUNNING-ba)

                    MISSION_RUNNING / PAUSED
                           │ "⏹ Stop" + megerősítés
                           ▼
                    (vissza MISSION_READY-be)
```

---

## buildConfig() — MissionConfig összeállítása

```java
MissionConfig buildConfig() {
    double gsdCm   = 0.5 + sbGsd.getProgress() * 0.1;
    int sidelap    = 50 + sbSidelap.getProgress();   // 50–90%
    int frontlap   = 50 + sbFrontlap.getProgress();  // 50–80%
    float speed    = 3f + sbSpeed.getProgress();     // 3–15 m/s
    int angle      = sbAngle.getProgress();          // 0–179°

    return new MissionConfig(gsdCm, sidelap, frontlap, speed, angle, true);
    // true = returnHome = misszió végén RTH
}
```

---

## drawMissionPath() — térkép frissítés

```java
void drawMissionPath(List<WaypointData> waypoints) {
    // Régi polyline eltávolítása
    if (missionPolyline != null)
        mapView.getOverlays().remove(missionPolyline);

    List<GeoPoint> points = new ArrayList<>();

    // Start pont hozzáadása az elejére (ha van)
    if (startPoint != null)
        points.add(startPoint);

    // Waypontok
    for (WaypointData wp : waypoints)
        points.add(new GeoPoint(wp.lat, wp.lon));

    // Start pont hozzáadása a végére (RTH)
    if (startPoint != null)
        points.add(startPoint);

    // Narancs polyline
    missionPolyline = new Polyline();
    missionPolyline.setPoints(points);
    missionPolyline.getOutlinePaint().setColor(0xFFFF8C00);
    missionPolyline.getOutlinePaint().setStrokeWidth(3f);
    mapView.getOverlays().add(missionPolyline);
    mapView.invalidate();
}
```

---

## showStats() — statisztika kijelzés

```java
void showStats(GridMissionGenerator.GeneratorResult result) {
    double areHa = result.areaM2 / 10_000.0;
    int segments = result.segments.size();
    String segInfo = segments > 1
        ? result.totalWaypoints + " waypoint (" + segments + " szegmens)"
        : result.totalWaypoints + " waypoint";

    tvStats.setText(
        "Terület: " + String.format("%.2f", areHa) + " ha\n" +
        "Magasság: " + (int)result.altitudeM + " m\n" +
        "Waypontok: " + segInfo + "\n" +
        "Becsült idő: ~" + (int)result.estimatedMinutes + " perc\n" +
        "Sávköz: " + String.format("%.1f", result.stripSpacingM) + " m  |  " +
        "Fotóköz: " + String.format("%.1f", result.photoDistM) + " m"
    );
}
```

---

## setMissionRunning() — UI állapotváltás

```java
void setMissionRunning(boolean running) {
    isMissionRunning = running;
    btnPauseMission.setEnabled(running);
    btnStopMission.setEnabled(running);
    btnUpload.setEnabled(!running);
    btnGenerate.setEnabled(!running);
    if (!running) {
        isPaused = false;
        btnPauseMission.setText("⏸ Szünet");
    }
}
```

---

## MissionConfig adatmodell

```java
public class MissionConfig {
    public double gsdCm;           // pl. 3.0
    public double altitudeM;       // GsdCalculator-ból számítva
    public int sidelapPercent;     // 50–90, default 75
    public int frontlapPercent;    // 50–80, default 80
    public float speedMs;          // 3–15, default auto
    public int flightAngleDeg;     // 0–179, default 0
    public boolean returnHome;     // true = RTH misszió végén
}
```

## WaypointData adatmodell

```java
public class WaypointData {
    public double lat;             // WGS84 szélességi fok
    public double lon;             // WGS84 hosszúsági fok
    public double altitudeM;       // repülési magasság (AGL méter)
    public float gimbalPitch;      // -90 (nadir)
    public boolean shootPhoto;     // true = foto készítés
    public float heading;          // drón iránya (-1 = automatikus)
}
```

---

## Layout struktúra

```
LinearLayout (horizontal)
├─ FrameLayout (65% weight)
│   ├─ org.osmdroid.views.MapView (id: mapView)
│   └─ Button (id: btnMyLocation, jobb felső, 48×48dp, "📍")
│
└─ ScrollView (35% weight, #1a1a2e háttér)
    └─ LinearLayout (vertical, 12dp padding)
        ├─ TextView "Misszió beállítások" (bold, 16sp)
        ├─ TextView (id: tvGsd) + SeekBar (id: sbGsd, max=90)
        ├─ TextView (id: tvSidelap) + SeekBar (id: sbSidelap, max=40)
        ├─ TextView (id: tvFrontlap) + SeekBar (id: sbFrontlap, max=30)
        ├─ TextView (id: tvSpeed) + SeekBar (id: sbSpeed, max=12)
        ├─ TextView (id: tvAngle) + SeekBar (id: sbAngle, max=179)
        ├─ Button (id: btnDrawMode, "Terület rajzolása", #004499)
        ├─ Button (id: btnClear, "Törlés", #660000)
        ├─ Button (id: btnGenerate, "Misszió generálása", #006600)
        ├─ Button (id: btnSetStart, "Start/Home pont", #005566)
        ├─ Button (id: btnImportCsv, "CSV importálása", #555500)
        ├─ Button (id: btnExport, "Exportálás (CSV / KMZ)", #334400)
        ├─ Button (id: btnUpload, "Feltöltés + Start", #FF6600, bold, 52dp)
        ├─ LinearLayout (horizontal)
        │   ├─ Button (id: btnPauseMission, "⏸ Szünet", #885500, disabled)
        │   └─ Button (id: btnStopMission, "⏹ Stop", #880000, disabled)
        └─ TextView (id: tvStats, #AAAAAA, 11sp)
```
