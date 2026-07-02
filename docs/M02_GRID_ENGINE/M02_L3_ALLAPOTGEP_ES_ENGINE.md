# L3 – Állapotgép és Engine – Grid Engine

**Modul:** M02
**Szint:** L3 – Állapotgép és Engine
**Verzió:** v1.2.0
**Létrehozva:** 2026-04-02
**Utolsó módosítás:** 2026-04-05
**Státusz:** ✅ Implementálva (v1.2.0)

---

## Forrásfájlok

| Fájl | Szerepkör | Státusz |
|------|-----------|---------|
| `mission/GsdCalculator.java` | GSD ↔ magasság, footprint, sávköz, sebesség kalkulátor | ✅ Implementálva |
| `mission/GridMissionGenerator.java` | Kígyózó waypoint útvonal generátor, szegmentálás | ✅ Implementálva |
| `model/MissionConfig.java` | Bemeneti adatmodell | ✅ Implementálva |
| `model/WaypointData.java` | Kimeneti adatmodell | ✅ Implementálva |

---

## GsdCalculator — teljes API

```java
public class GsdCalculator {

    // P4P v1 kamera konstansok
    public static final double SENSOR_WIDTH_MM  = 13.2;
    public static final double SENSOR_HEIGHT_MM = 8.8;
    public static final double FOCAL_LENGTH_MM  = 8.8;
    public static final double IMAGE_WIDTH_PX   = 5472;
    public static final double IMAGE_HEIGHT_PX  = 3648;

    /** GSD (cm/px) → repülési magasság (m) */
    public double altitudeFromGsd(double gsdCm) {
        return (gsdCm * FOCAL_LENGTH_MM * IMAGE_WIDTH_PX)
               / (SENSOR_WIDTH_MM * 100.0);
    }

    /** Repülési magasság (m) → GSD (cm/px) */
    public double gsdFromAltitude(double altitudeM) {
        return (altitudeM * SENSOR_WIDTH_MM * 100.0)
               / (FOCAL_LENGTH_MM * IMAGE_WIDTH_PX);
    }

    /** Talajon látható képszélesség (m) */
    public double imageCoverageWidthM(double altitudeM) {
        return altitudeM * SENSOR_WIDTH_MM / FOCAL_LENGTH_MM;
    }

    /** Talajon látható képmagasság (m) */
    public double imageCoverageHeightM(double altitudeM) {
        return altitudeM * SENSOR_HEIGHT_MM / FOCAL_LENGTH_MM;
    }

    /** Sávköz (m) — sidelap alapján */
    public double stripSpacingM(double altitudeM, int sidelapPercent) {
        return imageCoverageWidthM(altitudeM) * (1.0 - sidelapPercent / 100.0);
    }

    /** Fotóköz (m) — frontlap alapján */
    public double photoDistanceM(double altitudeM, int frontlapPercent) {
        return imageCoverageHeightM(altitudeM) * (1.0 - frontlapPercent / 100.0);
    }

    /**
     * Javasolt repülési sebesség (m/s).
     * Alap: 0.5 × GSD_m × 800 (1/800s záridőhöz)
     * Korlát: 3–12 m/s
     */
    public float recommendedSpeedMs(double gsdCm) {
        double speed = 0.5 * (gsdCm / 100.0) * 800.0;
        return (float) Math.max(3.0, Math.min(12.0, speed));
    }

    /** Becsült repülési idő (perc) */
    public double estimatedFlightMinutes(double totalDistanceM,
                                         float speedMs,
                                         int numStrips) {
        double flightMin  = totalDistanceM / (speedMs * 60.0);
        double turnsMin   = numStrips * 5.0 / 60.0;
        return flightMin + turnsMin;
    }
}
```

---

## GridMissionGenerator — teljes API

```java
public class GridMissionGenerator {

    private static final int MAX_WAYPOINTS_PER_MISSION = 99;

    public static class GeneratorResult {
        public List<List<WaypointData>> segments = new ArrayList<>();
        public int    totalWaypoints;
        public double areaM2;
        public double estimatedMinutes;
        public double altitudeM;
        public double stripSpacingM;
        public double photoDistM;
        public String errorMessage;          // null = sikeres
        public float  terrainMinAlt = Float.NaN;  // domborzatkövetés min. magasság
        public float  terrainMaxAlt = Float.NaN;  // domborzatkövetés max. magasság
        public boolean terrainCorrected = false;  // DEM korrekció alkalmazva?
        public int    skippedByObstacle = 0;      // akadály miatt kihagyott wp-ok
    }

    /**
     * Fő belépési pont.
     * @param polygonPoints  legalább 3 GPS pont
     * @param config         repülési paraméterek
     */
    public GeneratorResult generate(List<GeoPoint> polygonPoints,
                                    MissionConfig config) { ... }

    // Belső metódusok:

    /** GPS → helyi XY méter (centroid körüli) */
    private double[][] toLocalXY(List<GeoPoint> points,
                                  double centLat, double centLon) { ... }

    /** 2D rotáció (fokban) */
    private double[][] rotate(double[][] xy, double angleDeg) { ... }

    /** Polygon terület számítás (Shoelace formula) */
    private double polygonArea(double[][] xy) { ... }

    /** Polygon-scanline metszéspontok */
    private List<Double> scanlineIntersections(double[][] poly,
                                                double scanY) { ... }

    /** Kígyózó sorrend alkalmazása */
    private List<WaypointData> buildSerpentine(List<List<double[]>> strips,
                                                double altM,
                                                double photoDistM) { ... }

    /** Szegmentálás (max 98 waypoint/szegmens) */
    private List<List<WaypointData>> splitIntoSegments(
                                        List<WaypointData> all) { ... }

    /** helyi XY → GPS */
    private GeoPoint toGeoPoint(double x, double y,
                                 double centLat, double centLon) { ... }
}
```

---

## WaypointData adatmodell — részletes

```java
public class WaypointData {
    public double lat;           // WGS84 szélességi fok (pl. 47.4567890)
    public double lon;           // WGS84 hosszúsági fok (pl. 19.1234567)
    public double altitudeM;     // AGL magasság méterben (pl. 117.3)
    public float  gimbalPitch;   // -90.0 = nadir (egyenesen lefelé)
    public boolean shootPhoto;   // true = foto triggerelés ennél a waypointnál
    public float  heading;       // drón iránya (-1 = útvonal alapján automatikus)
}
```

## MissionConfig adatmodell — részletes

```java
public class MissionConfig {
    public double gsdCm = 3.0;
    public double altitudeM = 80.0;
    public double sidelapPercent = 75.0;
    public double frontlapPercent = 80.0;
    public float  speedMs = 7.0f;
    public double flightAngleDeg = 0.0;
    public boolean returnHome = true;
    public boolean terrainFollowing = false;
    public double offsetM = 0.0;                     // túlrepülési határ méterben
    public DroneProfile droneProfile;                // kamera profil
    public CameraSettings cameraSettings;            // kamera beállítások
    public List<ObstacleData> obstacles = new ArrayList<>();  // akadályok listája
}
```

---

## Teljesítmény jellemzők (Crystal Sky, ARM Cortex-A53)

| Terület | Sávköz | Waypontok | Futási idő (becsült) |
|---------|--------|-----------|---------------------|
| 0.5 ha  | 44 m   | ~15 wp    | < 10 ms             |
| 2 ha    | 44 m   | ~60 wp    | < 30 ms             |
| 10 ha   | 44 m   | ~300 wp   | < 150 ms            |
| 50 ha   | 44 m   | ~1500 wp  | < 800 ms            |
| 100 ha  | 44 m   | ~3000 wp  | ~1.5 s              |

> 50 ha felett a generálás érzékelhetően lassul — AsyncTask ajánlott.

---

## SamplingPointGenerator + SamplingMissionGenerator — teljes API (✅ Implementálva, 2026-07-02)

**Forrásfájlok:**

| Fájl | Szerepkör | Státusz |
|------|-----------|---------|
| `mission/SamplingPointGenerator.java` | Mintapont-kijelölés polygonon belül (stratified/halton/random) | ✅ Implementálva |
| `mission/SamplingMissionGenerator.java` | Mintapontokból waypoint-szekvencia (transit+süllyedés+emelkedés) | ✅ Implementálva |
| `model/WaypointData.java` | `hoverSeconds` mező | ✅ Implementálva (meglévő fájl bővítve) |
| `model/MissionConfig.java` | `samplingMode`, `nSamplePoints`, `samplingMethod`, `samplingSeed`, `transitAltitudeM`, `sampleAltitudeM`, `hoverSeconds` mezők | ✅ Implementálva (meglévő fájl bővítve) |
| `mission/GridMissionGenerator.java` | `GeneratorResult.isSamplingMission` + `.sampleCount` mezők | ✅ Implementálva (additív, nem törő bővítés) |

**Tervezési döntés a dokumentáció korábbi verziójához képest:** a `SamplingMissionGenerator`
**nem** önálló `SamplingResult` típust ad vissza, hanem a már meglévő
`GridMissionGenerator.GeneratorResult`-ot hasznosítja újra (két új mezővel bővítve:
`isSamplingMission`, `sampleCount`). Ennek oka: a `MissionPlannerActivity` a `lastResult`
mezőt (ez a típus) **30+ helyen** használja (feltöltés, export, szimuláció, domborzatkövetés,
GPS-ellenőrzés, resume-logika) — ha ezek mindegyike csak `List<WaypointData> segments`-en és
néhány primitív mezőn dolgozik (nem a generátor-specifikus logikán), a közös típus
újrahasznosítása **nulla módosítást** igényelt ezekben a meglévő, jól tesztelt
kódrészletekben. Az egyetlen ténylegesen elágazó pont a tényleges DJI feltöltés
(`uploadMission()` vs `uploadSamplingMission()`), amit az `isSamplingMission` flag vezérel.

```java
public class SamplingPointGenerator {

    private static double halton(int index, int base) { ... } // Dronterapia sampling_plan.py port

    /**
     * @param polygon  legalább 3 GPS pont
     * @param nPoints  kívánt mintapontszám (>0)
     * @param method   "stratified" (alapért.) | "halton" | "random"
     * @param seed     reprodukálhatóság
     * @return legfeljebb nPoints db GeoPoint, mind a polygonon belül
     *
     * Önálló, kis méretű GPS<->helyi-XY transzformációt és egy point-in-polygon
     * (ray-casting) tesztet tartalmaz — utóbbi a GridMissionGenerator-ben nem
     * létezik (az scanline-alapú algoritmust használ), ezért nem indokolt közös
     * geometria-kódra refaktorálni ezt a kis, önálló darabot.
     */
    public static List<GeoPoint> generate(List<GeoPoint> polygon, int nPoints,
                                          String method, long seed) { ... }

    public static int recommendedNPoints(double areaHectares, double cvEstimate) { ... }
    public static int recommendedNPoints(double areaHectares) { ... } // cv=0.3 default
}
```

```java
public class SamplingMissionGenerator {

    private static final int MAX_WAYPOINTS_PER_MISSION = 99; // azonos limit, mint Grid Engine

    /**
     * @param samplePoints SamplingPointGenerator.generate() kimenete
     * @param polygon      az eredeti AOI polygon (terület-számításhoz), lehet null
     * @param config       transitAltitudeM, sampleAltitudeM, hoverSeconds, speedMs
     * @return GridMissionGenerator.GeneratorResult, isSamplingMission=true
     */
    public static GeneratorResult generate(List<GeoPoint> samplePoints,
                                           List<GeoPoint> polygon,
                                           MissionConfig config) {
        // altitudeM = sampleAltitudeM (ez jelenik meg a meglévő UI-ban, pl. EU 120m
        // jogszabályi figyelmeztetésnél — bár a MissionPlannerActivity a transit
        // magasságot KÜLÖN is ellenőrzi ugyanerre a határra)
        // Minden mintaponthoz 3 WaypointData: érkezés (transit, shootPhoto=false),
        // mintavétel (sample, shootPhoto=true, hoverSeconds beállítva),
        // emelkedés (transit, shootPhoto=false)
        // estimatedMinutes: (vízszintes transit-táv + 2×(transitAlt-sampleAlt)×n) / speed
        //                   + hoverSeconds × n, haversine-távolsággal számolva
        ...
    }
}
```

**MissionPlannerActivity integráció (`runGeneration()` közös belépési pont):**

```java
private GridMissionGenerator.GeneratorResult runGeneration(List<GeoPoint> polygon,
                                                           MissionConfig config) {
    if (config.samplingMode) {
        lastSamplePoints = SamplingPointGenerator.generate(
                polygon, config.nSamplePoints, config.samplingMethod, config.samplingSeed);
        drawSamplePointMarkers(lastSamplePoints);
        return SamplingMissionGenerator.generate(lastSamplePoints, polygon, config);
    } else {
        clearSamplePointMarkers();
        lastSamplePoints = null;
        return GridMissionGenerator.generate(polygon, config);
    }
}
```

Ezt hívja `autoGenerateIfReady()` és `generateMission()` is a korábbi közvetlen
`GridMissionGenerator.generate(...)` hívás helyett — ez az EGYETLEN hely, ahol a
két misszió-típus közötti elágazás a generálási oldalon történik.

**Build-ellenőrzés:** `./gradlew compileDebugJavaWithJavac --offline` és
`./gradlew assembleDebug --offline` mindkettő sikeres (a valódi DJI SDK 4.18
függőséggel, nem stub-bal) — a `WaypointAction`/`WaypointActionType.STAY`/
`START_TAKE_PHOTO`/`WaypointMissionFlightPathMode.NORMAL`/`MediaManager`/`MediaFile`
API-hívások helyessége a letöltött DJI SDK jar dekompilált bájtkódjából (`javap`)
lett ellenőrizve, nem csak feltételezve. **Fizikai eszközön (Crystal Sky + Phantom 4
Pro v1) még nem lett tesztelve** — ez a következő lépés, valódi repülés előtt
kötelező.
