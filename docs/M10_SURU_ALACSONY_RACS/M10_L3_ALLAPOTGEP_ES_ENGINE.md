# L3 – Állapotgép és Engine – Sűrű Rács (Alacsony Magasságú Mozaikoláshoz)

**Modul:** M10
**Szint:** L3 – Állapotgép és Engine
**Verzió:** v1.0.0 — ✅ Implementálva (2026-07-03)
**Létrehozva:** 2026-07-03
**Utolsó módosítás:** 2026-07-03
**Státusz:** ✅ Implementálva (`./gradlew assembleDebug` sikeres), eszközön még nem tesztelve

---

## Forrásfájlok (tényleges, megvalósult állapot)

| Fájl | Változás |
|------|----------|
| `model/MissionConfig.java` | Új mező: `denseGridMode` (boolean) |
| `mission/GridMissionGenerator.java` | `denseMode` paraméterezés a meglévő generátorban (NEM önálló osztály — ld. döntés lent); új `GeneratorResult` mezők: `isDenseGridMission`, `estimatedBatteryCount` |
| `dji/CameraConfigurator.java` | **Nincs változás** — `triggerSamplePhoto()` (M04 §18) változtatás nélkül újrahasznosítva |
| `dji/MissionUploader.java` | **Nincs változás** — `uploadSamplingMission()` (NORMAL mód + STAY akció) újrahasznosítva mindkét (mintavételi és sűrű rács) misszió-típushoz |
| `mission/ProjectManager.java` | `flight_settings.dense_grid_mode` mező mentés/betöltés |
| `MissionPlannerActivity.java` | `switchDenseGridMode` UI, `initDenseGridControls()`, `triggerDenseGridPhoto()`, `onWaypointReached()`/`onMissionFinished()` harmadik ága, kötelező megerősítő dialog |
| `res/layout/activity_mission_planner.xml` | Új `switchDenseGridMode` + `tvDenseGridInfo` UI elem |

---

## Megvalósítási döntés — nem önálló generátor osztály

A M10_L2 §3-ban felvetett kérdés ("önálló generátor osztály vagy
paraméterezett `GridMissionGenerator`") a **paraméterezés** mellett dőlt el:
a `GridMissionGenerator.generate()` most `config.denseGridMode`-ot olvassa,
és a substrip→waypoint lépésnél (korábban: csak 2 végpont) egy közös
`addStripWaypoints()` metódust hív, ami `denseMode` esetén `photoDistM`
lépésközzel helyez el waypointokat:

```java
private static void addStripWaypoints(List<WaypointData> waypoints,
        double xEnter, double xExit, double scanY,
        double cosB, double sinB,
        double centLat, double centLon, double mPerDegLat, double mPerDegLon,
        double altM, double photoDistM, boolean denseMode) {

    if (!denseMode) {
        addWaypointAtX(waypoints, xEnter, scanY, ..., 0f);  // hoverSeconds=0
        addWaypointAtX(waypoints, xExit, scanY, ..., 0f);
        return;
    }

    double length = Math.abs(xExit - xEnter);
    int n = Math.max(1, (int) Math.round(length / Math.max(0.1, photoDistM)));
    double step = (xExit - xEnter) / n;
    for (int i = 0; i <= n; i++) {
        double x = xEnter + step * i;
        addWaypointAtX(waypoints, x, scanY, ..., DENSE_GRID_HOVER_SECONDS);
    }
}
```

**Miért ez, nem önálló osztály:** a transzformáció, scanline-metszés és
akadály-clip logika (`runGridPass()` nagy része) **teljesen változatlan**
maradt — csak az utolsó, waypoint-emissziós lépés ágazik el. Egy önálló
`DenseGridMissionGenerator` osztály ezt a közös logikát vagy duplikálná,
vagy egy köztes absztrakciót igényelne — a paraméterezés egyszerűbb és
kevesebb kódot eredményez ugyanahhoz a viselkedéshez.

**Konstansok** (`GridMissionGenerator.java`):
```java
private static final float  DENSE_GRID_HOVER_SECONDS = 1.2f;  // STAY minden ponton
private static final double DENSE_GRID_OVERHEAD_SEC   = 3.0;  // becslés: fékezés+gyorsítás
private static final double TYPICAL_BATTERY_MINUTES   = 16.0; // M07_L1 §1 alapján
```

---

## Megvalósítási döntés — nincs `uploadDenseGridMission()`, `uploadSamplingMission()` újrahasznosítva

A M10_L3 korábbi vázlata egy önálló `uploadDenseGridMission()` metódust
tervezett a `MissionUploader`-ben. Kiderült, hogy erre **nincs szükség**:
a `uploadSamplingMission()` (M04 §18 javítás után) pontosan azt csinálja,
amire a sűrű rácsnak is szüksége van — NORMAL flightPathMode, STAY akció
minden `hoverSeconds > 0` waypointon, **nincs** benne semmi mintavétel-
specifikus logika. A `MissionPlannerActivity` egyszerűen kiterjesztette a
feltétlt:

```java
boolean needsNormalMode = lastResult != null
        && (lastResult.isSamplingMission || lastResult.isDenseGridMission);
if (needsNormalMode) {
    uploader.uploadSamplingMission(segment, buildConfig(), uploadCallback);
} else {
    uploader.uploadMission(segment, buildConfig(), uploadCallback);
}
```

---

## `MissionPlannerActivity` — állapotgép a végrehajtás alatt

```
onWaypointReached(index, total):
  actualIndex = offset + index
  ...
  IF isSamplingMission:
      actualIndex % 3 == 1 esetén → triggerSamplePointPhoto(point)  (M04 §18)
  ELSE IF isDenseGridMission:
      MINDEN hívásnál → triggerDenseGridPhoto()
      (nincs %3 szabály — minden waypoint egy fotópozíció, nincs
       érkezés/mintavétel/emelkedés hármas szerkezet, mint a mintavételi
       misszióban)
  ELSE:
      CURVED + intervallum-fotózás (M02, változatlan)
```

```java
private void triggerDenseGridPhoto() {
    CameraConfigurator.triggerSamplePhoto(new CameraConfigurator.PhotoTriggerListener() {
        @Override public void onPhotoConfirmed() {
            runOnUiThread(() -> denseGridPhotosConfirmed++);
        }
        @Override public void onPhotoFailed(String reason) {
            runOnUiThread(() -> {
                denseGridPhotosFailed++;
                Toast.makeText(..., "⚠ Waypoint fotó sikertelen: " + reason, ...).show();
            });
        }
    });
}
```

**Miért nincs GeoPoint-korreláció (szemben a mintavételi misszióval,
M04_L3):** a sűrű rács fotói **nem** a mintavételi session-flow-n (M04 §16,
`MediaSessionDownloader`) mennek keresztül — a cél egy teljes SD-kártyás
fotósorozat, amit a pilóta a szokásos módon (számítógépre másolva) told be
egy ODM/Pix4D/Metashape feldolgozásba, pontosan úgy, mint egy normál CURVED
grid-misszió után. Egyszerű számlálók (`denseGridPhotosConfirmed/Failed`)
elegendők a UI-visszajelzéshez.

---

## UI — feltétel és megerősítés

```
switchDenseGridMode (Switch, activity_mission_planner.xml):
  - kölcsönösen kizárja a switchSamplingMode-ot (csak egy lehet aktív)
  - checked change → lastResult = null, autoGenerateIfReady()

generateMission() / autoGenerateIfReady() stats-panel:
  isDenseGridMission → "Terulet | N foto (pontos) | Magassag | Fototav |
                         Ido | Becsult akku — 🐢 Sűrű rács mód"
  cameraIntervalLimited (CURVED mód, nem dense) → figyelmeztetés, ami MOST
    már explicit utal a 🐢 Sűrű rács mód bekapcsolására mint megoldásra

uploadCurrentSegment() — kötelező megerősítés (M10_L2 §2):
  isDenseGridMission && currentSegmentIndex==0 &&
  (estimatedMinutes>20 || estimatedBatteryCount>3)
    → a feltöltési dialog szövege kiegészül a becsült idő/akkuszámmal
