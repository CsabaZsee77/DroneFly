# L3 – Állapotgép és Engine – Export / Import

**Modul:** M03
**Szint:** L3 – Állapotgép és Engine
**Verzió:** v1.3.0
**Létrehozva:** 2026-04-02
**Utolsó módosítás:** 2026-04-05
**Státusz:** ✅ Implementálva (v1.3.0)

---

## Forrásfájlok

| Fájl | Szerepkör | Státusz |
|------|-----------|---------|
| `mission/ProjectManager.java` | JSON projekt mentés / betöltés | ✅ Implementálva |
| `mission/MissionExporter.java` | Litchi CSV és KMZ generálás | ✅ Implementálva |
| `mission/CsvMissionParser.java` | Litchi CSV beolvasás | ✅ Implementálva |
| `res/xml/file_paths.xml` | FileProvider elérési út konfiguráció | ✅ Implementálva |
| `AndroidManifest.xml` | FileProvider provider deklaráció | ✅ Implementálva |

---

## ProjectManager — teljes API

```java
public class ProjectManager {

    public static final String FILE_EXT     = ".dronefly.json";
    public static final String MISSIONS_DIR = "missions";

    /**
     * Projekt könyvtár: <external files>/missions/
     * Automatikusan létrehozza, ha még nem létezik.
     */
    public static File getProjectsDir(Context ctx) { ... }

    /**
     * Repülési tervet ment JSON fájlba.
     * Fájlnév: sanitizeFilename(name) + ".dronefly.json"
     * Tartalom: version, name, savedAt, polygon, startPoint?, config, obstacles
     * @return a létrehozott fájl
     */
    public static File saveProject(Context ctx,
                                   String name,
                                   List<GeoPoint> polygon,
                                   GeoPoint startPoint,      // null ha nincs
                                   MissionConfig config) throws Exception { ... }

    /**
     * Visszaolvas egy .dronefly.json fájlt.
     * @return ProjectData (összes mező, obstacles lista)
     */
    public static ProjectData loadProject(File file) throws Exception { ... }

    /**
     * Összes elmentett projekt listázása (legújabb elöl).
     * Bubble sort — API 22: lambda/Comparator.comparing nem elérhető.
     */
    public static List<File> listProjects(Context ctx) { ... }

    /** Fájlnévben nem megengedett karakterek cseréje. */
    private static String sanitizeFilename(String name) {
        return name.trim().replace(' ', '_')
                   .replaceAll("[/\\\\:*?\"<>|]", "_");
    }
}
```

### ProjectData adatstruktúra

```java
public static class ProjectData {
    public String         name;            // terv neve
    public List<GeoPoint> polygon;         // polygon csúcsai
    public GeoPoint       startPoint;      // null ha nincs mentve
    // config mezők (MissionConfig-ból kiemelve):
    public double  gsdCm;
    public double  sidelapPercent;
    public double  frontlapPercent;
    public float   speedMs;
    public double  flightAngleDeg;
    public double  offsetM;
    public boolean returnHome;
    public boolean terrainFollowing;
    public String  droneProfileName;       // DroneProfile.name alapján keresés
    // akadályok:
    public List<ObstacleData> obstacles;
}
```

### JSON formátum (v1)

```json
{
  "version": 1,
  "name": "Északi tábla",
  "savedAt": "2026-04-05T14:30:00",
  "polygon": [
    {"lat": 47.5123, "lon": 19.0345},
    {"lat": 47.5134, "lon": 19.0378},
    {"lat": 47.5112, "lon": 19.0389}
  ],
  "startPoint": {"lat": 47.5110, "lon": 19.0330},
  "config": {
    "gsdCm": 2.5,
    "sidelapPercent": 75.0,
    "frontlapPercent": 80.0,
    "speedMs": 7.0,
    "flightAngleDeg": 0.0,
    "offsetM": 5.0,
    "returnHome": true,
    "terrainFollowing": false,
    "droneProfileName": "Phantom 4 Pro v1"
  },
  "obstacles": [
    {"lat": 47.5128, "lon": 19.0360, "radiusM": 20.0, "heightM": 15.0}
  ]
}
```

**Fájlelérési út Crystal Sky-on:**
```
/sdcard/Android/data/com.dronefly.app/files/missions/<névvel>.dronefly.json
```

---

## MissionExporter — teljes API

```java
public class MissionExporter {

    /**
     * Litchi Mission Hub kompatibilis CSV generálása.
     * @param waypoints  waypoint lista (WaypointData)
     * @param speedMs    repülési sebesség m/s (egész értékre kerekítve)
     * @return           48 oszlopos CSV string (fejléccel)
     */
    public String toLitchiCsv(List<WaypointData> waypoints, float speedMs) {
        StringBuilder sb = new StringBuilder();

        // Fejlécsor (48 oszlop)
        sb.append("latitude,longitude,altitude(m),heading(deg),curvesize(m)," +
                  "rotationdir,gimbalmode,gimbalpitchangle," +
                  "actiontype1,actionparam1," +
                  "actiontype2,actionparam2,actiontype3,actionparam3," +
                  "actiontype4,actionparam4,actiontype5,actionparam5," +
                  "actiontype6,actionparam6,actiontype7,actionparam7," +
                  "actiontype8,actionparam8,actiontype9,actionparam9," +
                  "actiontype10,actionparam10,actiontype11,actionparam11," +
                  "actiontype12,actionparam12,actiontype13,actionparam13," +
                  "actiontype14,actionparam14,actiontype15,actionparam15," +
                  "altitudemode,speed(m/s)," +
                  "poi_latitude,poi_longitude,poi_altitude(m),poi_altitudemode," +
                  "photo_timeinterval,photo_distinterval\n");

        int speed = Math.max(1, Math.min(15, Math.round(speedMs)));

        for (WaypointData wp : waypoints) {
            sb.append(String.format("%.8f,%.8f,%.1f,%.1f,0,0,2,%.0f,",
                wp.lat, wp.lon, wp.altitudeM, wp.heading, wp.gimbalPitch));
            sb.append("1,0,");           // take photo
            // action 2–15: inaktív
            for (int i = 2; i <= 15; i++) sb.append("-1,0,");
            sb.append(String.format("0,%d,0,0,0,0,-1,-1\n", speed));
        }

        return sb.toString();
    }

    /**
     * KMZ fájl generálása (KML + ZIP).
     * @param waypoints    waypoint lista
     * @param missionName  misszió neve (KML Document/name)
     * @return             KMZ fájl tartalma byte[] formátumban
     */
    public byte[] toKmz(List<WaypointData> waypoints,
                        String missionName) throws IOException {
        String kml = buildKml(waypoints, missionName);
        return packKmz(kml);
    }

    private String buildKml(List<WaypointData> waypoints,
                             String missionName) { ... }

    private byte[] packKmz(String kmlContent) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ZipOutputStream zos = new ZipOutputStream(baos);
        ZipEntry entry = new ZipEntry("doc.kml");
        zos.putNextEntry(entry);
        zos.write(kmlContent.getBytes(StandardCharsets.UTF_8));
        zos.closeEntry();
        zos.close();
        return baos.toByteArray();
    }
}
```

---

## CsvMissionParser — teljes API

```java
public class CsvMissionParser {

    /**
     * Litchi CSV fájl beolvasása.
     * @param context  Android kontextus (ContentResolver-hez)
     * @param uri      fájlválasztóból kapott URI
     * @return         waypoint lista (üres lista hiba esetén)
     */
    public List<WaypointData> parse(Context context, Uri uri) {
        List<WaypointData> result = new ArrayList<>();
        try (InputStream is = context.getContentResolver().openInputStream(uri);
             BufferedReader reader = new BufferedReader(
                                         new InputStreamReader(is, StandardCharsets.UTF_8))) {

            String header = reader.readLine();
            if (header == null || !header.contains("latitude")) return result;

            String[] columns = header.split(",");
            int latIdx  = findIndex(columns, "latitude");
            int lonIdx  = findIndex(columns, "longitude");
            int altIdx  = findIndex(columns, "altitude(m)");
            int hdgIdx  = findIndex(columns, "heading(deg)");
            int pitIdx  = findIndex(columns, "gimbalpitchangle");
            int act1Idx = findIndex(columns, "actiontype1");

            String line;
            while ((line = reader.readLine()) != null) {
                String[] t = line.split(",", -1);
                WaypointData wp = new WaypointData();
                wp.lat        = parseDoubleSafe(t, latIdx, 0);
                wp.lon        = parseDoubleSafe(t, lonIdx, 0);
                wp.altitudeM  = parseDoubleSafe(t, altIdx, 50);
                wp.heading    = (float) parseDoubleSafe(t, hdgIdx, -1);
                wp.gimbalPitch = (float) parseDoubleSafe(t, pitIdx, -90);
                wp.shootPhoto = (int) parseDoubleSafe(t, act1Idx, -1) == 1;

                if (wp.lat != 0 && wp.lon != 0) result.add(wp);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return result;
    }
}
```

---

## KML struktúra (buildKml output)

```xml
<?xml version="1.0" encoding="UTF-8"?>
<kml xmlns="http://www.opengis.net/kml/2.2"
     xmlns:wpml="http://www.dji.com/wpmz/1.0.3">
  <Document>
    <name>dronefly_mission_20260402_103045</name>
    <description>DroneFly misszió — 342 waypoint, 117m, 7 m/s</description>

    <!-- Útvonal LineString — térkép előnézet -->
    <Placemark>
      <name>Útvonal</name>
      <LineString>
        <coordinates>
          19.123456,47.456789,117
          19.123512,47.456834,117
          ...
        </coordinates>
      </LineString>
    </Placemark>

    <!-- Waypontok egyenként -->
    <Placemark>
      <name>WP001</name>
      <Point>
        <coordinates>19.123456,47.456789,117</coordinates>
      </Point>
      <ExtendedData>
        <Data name="altitude"><value>117.3</value></Data>
        <Data name="speed"><value>7.0</value></Data>
        <Data name="gimbalPitch"><value>-90</value></Data>
        <Data name="shootPhoto"><value>1</value></Data>
        <Data name="heading"><value>-1</value></Data>
      </ExtendedData>
    </Placemark>
    ...
  </Document>
</kml>
```

---

## FileProvider konfiguráció

```xml
<!-- AndroidManifest.xml -->
<provider
    android:name="androidx.core.content.FileProvider"
    android:authorities="com.dronefly.app.provider"
    android:exported="false"
    android:grantUriPermissions="true">
    <meta-data
        android:name="android.support.FILE_PROVIDER_PATHS"
        android:resource="@xml/file_paths" />
</provider>

<!-- res/xml/file_paths.xml -->
<paths>
    <external-files-path name="exports" path="." />
</paths>
```

**Megosztási intent példa (M01-ből):**
```java
File file = new File(getExternalFilesDir(null), fileName);
Uri uri = FileProvider.getUriForFile(this, "com.dronefly.app.provider", file);
Intent intent = new Intent(Intent.ACTION_SEND);
intent.setType("text/csv");  // vagy "application/zip" KMZ-hez
intent.putExtra(Intent.EXTRA_STREAM, uri);
intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
startActivity(Intent.createChooser(intent, "Exportálás"));
```
