# L3 – Állapotgép és Engine – Edge AI Tőszámlálás

**Modul:** M09
**Szint:** L3 – Állapotgép és Engine (fájl-szintű leképzés, **implementált** állapot)
**Verzió:** v1.0.0 (implementálva)
**Létrehozva:** 2026-07-02
**Utolsó módosítás:** 2026-07-03
**Státusz:** ✅ Implementálva — az alábbi fájlok mindegyike létezik, build zöld

---

## Tervezett forrásfájlok

| Fájl | Szerepkör |
|------|-----------|
| `ai/YoloModelManager.java` | `/sdcard/DroneFly/models/` szkennelése, `.onnx` + sidecar `.json` párosítás, `ModelMetadata` betöltés/validáció |
| `ai/ModelMetadata.java` | POJO: label, cropType, inputSize, confThreshold, iouThreshold, targetClassIndex, classNames |
| `ai/YoloInferenceEngine.java` | ONNX Runtime (`OrtSession`) wrapper — preprocess (resize+normalize, NCHW), `run()`, raw output → `Detection` lista, NMS |
| `ai/Detection.java` | POJO: classIndex, confidence, bbox (cx, cy, w, h — normalizált 0–1) |
| `ai/SamplingResultCalculator.java` | `PointResult` lista → `SamplingCountResult` (M09_L1 §5 képletek) |
| `ai/SamplingCountResult.java` / `PointResult.java` | Eredmény POJO-k, `results.json` (de)szerializáció |
| `SamplingResultsActivity.java` | UI: session-választó, modell-spinner, [Számlálás indítása], progress, összefoglaló kártya, pontonkénti táblázat, export, [📂 Fotók importálása] gomb |
| `res/layout/activity_sampling_results.xml` | Az `SamplingResultsActivity` layoutja |
| `PhotoImportActivity.java` | Fotóimport repülés nélkül (M09_L1 §9): forrásválasztás (drón SD / tablet böngésző), checkbox-lista, repülési terv kontextus-spinner, EXIF GPS, session.json írás |
| `res/layout/activity_photo_import.xml` | A `PhotoImportActivity` layoutja |

**Tervezett új fájlok — GSD kalibráció (M09_L1 §10, M09_L2 §10, TERVEZETT):**

| Fájl | Szerepkör |
|------|-----------|
| `ui/GsdRulerView.java` | Egyéni `View` a teljes képernyős képnézetben: két húzható végpont, vonalrajzolás, pixelhossz kijelzés, zoom/nagyító a pontos illesztéshez, **középkereszt + figyelmeztetés, ha a vonal a szélek felé csúszik** (M09_L1 §10.3 4. szabály — optikai torzítás a széleken). A `PhotoPreview`-ba ágyazva jelenik meg (nem külön Activity) |
| `ai/GsdCalibration.java` | POJO + tiszta számítás: `refDistanceM`, `refUnitCount`, `linePixelLength`, `displayImageWidthPx` → `gsdMetersPerPx`, `footprintAreaM2`. Nincs Android-függés, egységtesztelhető |
| `ai/FootprintSource.java` | Enum: `EXIF`, `MANUAL`, `INHERITED`, `ANCHORED` (M09_L1 §10.4) |

**Módosított meglévő fájlok:**

| Fájl | Módosítás |
|------|-----------|
| `dji/MediaSessionDownloader.java` | `downloadSessionMedia()` új paraméterek (`sampleAltitudeM`, `droneProfileName`, `aoiAreaM2`) + `session.json` 3 új mező (M09_L1 §6); fotóimporthoz: `listSdCardPhotos()` + `downloadSelectedMedia()` (session.json írás nélkül, `samplePoints == null` jelzéssel) |
| `MissionPlannerActivity.java` | Letöltés-befejező dialógus: "Eredmények megtekintése →" gomb → `SamplingResultsActivity` a friss `sessionId`-vel; bal gombsor "AI" gomb (`btnOpenCounting`) — átjárás a tőszámlálóba bármikor |
| `MainActivity.java` + `activity_main.xml` | "Tőszámlálás (Edge AI)" gomb — belépési pont app-indításkor |
| `mission/SamplingMissionGenerator.java` | `polygonAreaM2()` privátból publikussá — a fotóimport terv-kontextusa használja |

**GSD kalibrációhoz módosítandó meglévő fájlok (TERVEZETT):**

| Fájl | Módosítás |
|------|-----------|
| `ai/PointResult.java` | Új mezők: `footprintSource` (FootprintSource), opcionális `gsdCmPx`, `refDistanceM`, `refPixelLength` (reprodukálhatóság); a `footprintAreaM2` már létezik. JSON (de)szerializáció bővítése |
| `ai/SamplingResultCalculator.java` | A footprint ne egyetlen session-szintű konstans legyen: ha egy pontnak van felülírt `footprintAreaM2` (source ≠ EXIF), azt használja a densityPerM2 számításához; az FPC `N_plot`-ja is a pontonkénti footprint átlagával számol |
| `ui/PhotoPreview.java` | Új `[📏 Skála kalibrálása]` gomb; a `GsdRulerView` overlay megjelenítése; a mérés eredményének visszaadása a hívó `SamplingResultsActivity`-nek (callback) |
| `SamplingResultsActivity.java` | A kalibráció alkalmazása (per-kép / örökítés / lehorgonyzás), a `SamplingCountResult` újraszámítása inferencia nélkül, `results.json` frissítés, összefoglaló kártya update |

---

## Állapotgép — `SamplingResultsActivity`

```
IDLE (session + modell kiválasztva, vagy alapértelmezett)
  │
  │ [Számlálás indítása]
  ▼
VALIDATING
  │  session.json ellenőrzés (sample_altitude_m megvan-e)
  │  modell + sidecar JSON betöltés, kimeneti alak ellenőrzés
  │
  ├─ hiba ──────────────────────────────► ERROR (üzenet, vissza IDLE-be)
  │
  ▼ ok
RUNNING (per-kép ciklus, 1 worker thread)
  │  progress: "Feldolgozás: i/N (point_XXX.jpg)"
  │  minden képnél: preprocess → interpreter.run() → NMS → count
  │
  ├─ [Mégse] ──────────────────────────► CANCELLED (részleges eredmény megjelenítve, ha van már ≥1 pont)
  │
  ▼ minden kép feldolgozva (vagy CANCELLED, ha volt már részeredmény)
CALCULATING
  │  SamplingResultCalculator.compute() — statisztika (M09_L1 §5)
  ▼
DONE
  │  Összefoglaló kártya + táblázat megjelenítve
  │  results.json mentve a session mappába
  │  [Export CSV] elérhető
  │
  │ [Újrafuttatás másik modellel]
  └──────────────────────────────────────► IDLE (session megmarad, modell újraválasztható)
```

---

## `YoloInferenceEngine` — implementálva (2026-07-03)

```java
public class YoloInferenceEngine {

    public interface InferenceListener {
        void onImageStart(int index, int total, String fileName);
        void onImageDone(int index, int total, int detectedCount, long inferenceMs);
        void onImageError(int index, String error);
        void onCancelled(List<PointResult> partialResults);   // eltérés a tervtől: a lista
                                                                // hasznosabb, mint a puszta darabszám
        void onAllDone(List<PointResult> results);
        void onFatalError(String message);   // eltérés a tervtől: blokkoló hibák (M09_L2 §6
                                              // táblázat) külön csatornán, nem Toast-ból derül ki
    }

    /**
     * @param sessionDir   sampling_sessions/{sessionId}/ mappa (point_*.jpg + session.json)
     * @param modelFile    kiválasztott .onnx fájl
     * @param meta         ModelMetadata (sidecar .json betöltve)
     * @param cancelFlag   AtomicBoolean — true esetén a ciklus a következő kép előtt megáll
     */
    public void runOnSession(File sessionDir, File modelFile, ModelMetadata meta,
                             AtomicBoolean cancelFlag, InferenceListener listener);

    /** Egyetlen bitmap feldolgozása — NCHW preprocess + session.run() + NMS. */
    private List<Detection> detectSingle(OrtSession session, Bitmap bitmap, ModelMetadata meta,
                                         int channels, int numCandidates);

    /** Raw ONNX Runtime output [1, 4+nc, N] → Detection lista, class-szűrés + NMS. */
    private List<Detection> postprocess(float[][][] rawOutput, ModelMetadata meta,
                                        int channels, int numCandidates);
}
```

A `session.json` pontjait (index, lat, lon, local_file) az engine saját maga olvassa
a `sessionDir`-ből — nem kell külön `samplePoints` paramétert átadni.

---

## `SamplingResultCalculator` — implementálva (2026-07-03, FPC + Student-t)

```java
public class SamplingResultCalculator {

    /**
     * @param pointResults    YoloInferenceEngine kimenete (index/lat/lon/count/warning már
     *                        beállítva — a calculator tölti ki rajtuk a footprintAreaM2/
     *                        densityPerM2/densityPerHa mezőket, eltérés a tervtől: nem
     *                        külön int[]+GeoPoint lista, hanem ugyanazok a PointResult
     *                        objektumok mutálva, kevesebb duplikált adat)
     * @param sampleAltitudeM session.json-ból (M09_L1 §6 kiegészítés)
     * @param drone           DroneProfile (droneProfileName alapján DroneProfiles.getByName())
     * @param aoiAreaM2       session.json-ból (teljes tábla területe)
     * @param partial         igaz, ha megszakított futtatásból származó részleges eredmény
     */
    public static SamplingCountResult compute(List<PointResult> pointResults,
                                              double sampleAltitudeM,
                                              DroneProfile drone,
                                              double aoiAreaM2,
                                              String sessionId,
                                              String modelUsed,
                                              String targetClass,
                                              boolean partial);

    /** Student-t kritikus érték (95%, kétoldali), lineáris interpolációval (M09_L1 §5.3 tábla). */
    static double tValue95(int df);
}
```

**Megjegyzés a footprint-számításról:** a `GsdCalculator.imageCoverageWidthM()`
/ `imageCoverageHeightM()` (M02, már implementálva) közvetlenül újrahasznosítható
— nincs szükség új GSD-logikára, csak a `sampleAltitudeM` + `DroneProfile`
behelyettesítésére.

---

## `results.json` séma (tervezett)

```json
{
  "session_id": "20260702_143012",
  "model_used": "corn_yolo11n_v3.onnx",
  "target_class": "corn_plant",
  "computed_at": "2026-07-02T15:04:00Z",
  "conf_threshold_used": 0.35,
  "iou_threshold_used": 0.45,
  "total_area_ha": 10.0,
  "sample_count": 34,
  "mean_density_per_ha": 84200.0,
  "stdev_per_ha": 15150.0,
  "cv_percent": 18.0,
  "n_plot": 2380.5,
  "fpc_applied": false,
  "t95_used": 2.035,
  "estimated_total_count": 842000,
  "estimated_total_count_ci95": 68000,
  "points": [
    {
      "index": 0, "lat": 47.123, "lon": 19.456,
      "count": 21, "footprint_area_m2": 4.2,
      "density_per_ha": 50000.0,
      "inference_ms": 1840,
      "warning": null,
      "detections": [[0.1234, 0.5678, 0.02, 0.03, 0.4123]]
    }
  ]
}
```

A `detections` elemei: `[cx, cy, w, h, confidence]` — normalizált (0–1)
koordináták, 4 tizedesre kerekítve. A bounding boxos előnézet
(`ui/PhotoPreview`) és a jövőbeli Dronterapia-oldali vizualizáció/
utófeldolgozás is ebből dolgozhat.

**GSD kalibráció után (M09_L1 §10, TERVEZETT) a pontok bővülnek:**

```json
    {
      "index": 0, "lat": 47.123, "lon": 19.456,
      "count": 21,
      "footprint_area_m2": 4.2,
      "footprint_source": "MANUAL",
      "gsd_cm_px": 0.66,
      "ref_distance_m": 7.6,
      "ref_pixel_length": 1152.0,
      "density_per_ha": 50000.0,
      "inference_ms": 1840,
      "warning": null,
      "detections": [[0.1234, 0.5678, 0.02, 0.03, 0.4123]]
    }
```

A `footprint_source` értéke `EXIF | MANUAL | INHERITED | ANCHORED`. A
`gsd_cm_px` / `ref_*` mezők csak kézi mérésnél kerülnek ki (a mérés
reprodukálhatóságához). A `count` és a `detections` **változatlan** marad
kalibráció után — csak a `footprint_area_m2` és a belőle számolt
`density_per_ha` frissül (M09_L2 §10.4).

---

## `GsdCalibration` — tervezett interfész (nincs implementálva)

```java
public class GsdCalibration {

    /**
     * @param refDistanceM        egy referencia-egység valós hossza méterben (pl. sortáv 0.76)
     * @param refUnitCount        hány egységet fog át a vonal (pl. 10 sortáv) — a bázis = szorzat
     * @param linePixelLength     a húzott vonal hossza a MEGJELENÍTETT képen (px)
     * @param displayImageWidthPx a megjelenített kép szélessége (px) — ugyanabban a px-térben
     * @param imageAspect         kép_magasság / kép_szélesség (a footprint másik oldalához)
     */
    public static Result compute(double refDistanceM, int refUnitCount,
                                 double linePixelLength, double displayImageWidthPx,
                                 double imageAspect);

    public static class Result {
        public double gsdMetersPerPx;   // = (refDistanceM * refUnitCount) / linePixelLength
        public double footprintWidthM;  // = gsdMetersPerPx * displayImageWidthPx
        public double footprintHeightM; // = footprintWidthM * imageAspect
        public double footprintAreaM2;  // = footprintWidthM * footprintHeightM
    }

    /** Lehorgonyzott mód: egy kép mért footprintje → korrekciós arány az EXIF-footprintekre. */
    public static double anchorRatio(double measuredFootprintM2, double exifFootprintM2);
}
```

**Miért felbontás-invariáns (M09_L2 §10.2):** a `gsdMetersPerPx` a
megjelenített px-térben értendő, és a `displayImageWidthPx`-szel szorzunk —
a footprint (talajszélesség) `= ground_width`, ami független attól, hány
pixelen jelenítjük meg. Ezért nem kell az eredeti 5472 px-es felbontásra
visszakonvertálni; a lényeg, hogy a mérés és a szorzás **ugyanabban** a
px-térben történik.

---

## Kalibráció-újraszámítás — állapotgép (TERVEZETT)

```
DONE (számlálás kész, results.json elmentve — M09 v1 jelenlegi végállapota)
  │
  │ [📏 Skála kalibrálása] (a bounding boxos képnézetben)
  ▼
CALIBRATING (GsdRulerView — vonalzó húzása, referencia-távolság bevitele)
  │  élő kijelzés: GSD, footprint, EXIF-kereszt-ellenőrzés (M09_L1 §10.6)
  │
  ├─ [Mégse] ─────────────────────────────► vissza DONE-ba (nincs változás)
  │
  ▼ [Alkalmaz] — elsődlegesen "csak erre a képre" (M09_L1 §10.4: a hiba
  │             pontonként eltér; örökítés/lehorgonyzás elvetve, interpoláció
  │             csak opcionális sima-drift esetén)
  │  A referencia-távolság megmarad → "Következő kalibrálatlan kép" gyors lépés
RECOMPUTING (SamplingResultCalculator újrafut — INFERENCIA NÉLKÜL)
  │  a pontonkénti footprint-felülírásokkal újraszámol density/CV/CI
  ▼
DONE (frissített összefoglaló kártya + results.json felülírva)
```

A `RECOMPUTING` a main thread-en is lefuthat (csak aritmetika, N ≤ ~60 pont) —
nincs szükség háttérszálra, szemben a YOLO `RUNNING` állapottal.

---

## Build-konfiguráció kiegészítés (Gradle, implementálva)

```gradle
android {
    compileOptions {
        // ONNX Runtime java.util.Optional-t (API 24+) használ — Android 5.1-en
        // (API 22) ClassNotFoundException nélkül csak desugaring-gal fut
        coreLibraryDesugaringEnabled true
    }
}
dependencies {
    implementation 'com.microsoft.onnxruntime:onnxruntime-android:1.17.3'
    coreLibraryDesugaring 'com.android.tools:desugar_jdk_libs:2.0.4'
}
```

**API 22 kompatibilitási tanulság (terepi teszt, 2026-07-03):** az első
eszközön futtatás `ClassNotFoundException: java.util.Optional` hibával
összeomlott — az ONNX Runtime Java API-ja `Optional`-t ad vissza a
`Result.get(name)` hívásból, ami csak API 24-től létezik. Két rétegű javítás:
(1) a `YoloInferenceEngine` az iterátoros `result.iterator().next().getValue()`
hozzáférést használja (nem Optional-t), (2) core library desugaring
engedélyezve biztonsági hálóként az ORT belső API 24+ osztályhasználataira.
Eszközön verifikálva: az inferencia sikeresen lefut a Crystal Sky-n
(kukorica-modell, 640px, 178 detektálás/kép).
