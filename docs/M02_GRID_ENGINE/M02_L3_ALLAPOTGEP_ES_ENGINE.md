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
