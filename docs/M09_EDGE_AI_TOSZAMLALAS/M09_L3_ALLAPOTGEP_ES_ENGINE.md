# L3 – Állapotgép és Engine – Edge AI Tőszámlálás

**Modul:** M09
**Szint:** L3 – Állapotgép és Engine (fájl-szintű leképzés, **tervezett** állapot)
**Verzió:** v0.1.0 (terv, implementáció nem kezdődött el)
**Létrehozva:** 2026-07-02
**Utolsó módosítás:** 2026-07-02
**Státusz:** 🔲 Tervezve — az alábbi fájlok egyike sem létezik még

---

## Tervezett forrásfájlok

| Fájl | Szerepkör |
|------|-----------|
| `ai/YoloModelManager.java` | `/sdcard/DroneFly/models/` szkennelése, `.tflite` + sidecar `.json` párosítás, `ModelMetadata` betöltés/validáció |
| `ai/ModelMetadata.java` | POJO: label, cropType, inputSize, confThreshold, iouThreshold, targetClassIndex, classNames |
| `ai/YoloInferenceEngine.java` | TFLite `Interpreter` wrapper — preprocess (resize+normalize), `run()`, raw output → `Detection` lista, NMS |
| `ai/Detection.java` | POJO: classIndex, confidence, bbox (cx, cy, w, h — normalizált 0–1) |
| `ai/SamplingResultCalculator.java` | `PointResult` lista → `SamplingCountResult` (M09_L1 §5 képletek) |
| `ai/SamplingCountResult.java` / `PointResult.java` | Eredmény POJO-k, `results.json` (de)szerializáció |
| `SamplingResultsActivity.java` | UI: session-választó, modell-spinner, [Számlálás indítása], progress, összefoglaló kártya, pontonkénti táblázat, export |
| `res/layout/activity_sampling_results.xml` | Az `SamplingResultsActivity` layoutja |

**Módosítandó meglévő fájl:**

| Fájl | Módosítás |
|------|-----------|
| `dji/MediaSessionDownloader.java` | `downloadSessionMedia()` új paraméterek (`sampleAltitudeM`, `droneProfileName`, `aoiAreaM2`) + `session.json` 3 új mező (M09_L1 §6) |
| `MissionPlannerActivity.java` | Misszió-befejezéskor toast/gomb: "Eredmények megtekintése →" → `SamplingResultsActivity` indítása a friss `sessionId`-vel |

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

## `YoloInferenceEngine` — tervezett interfész (nincs implementálva)

```java
public class YoloInferenceEngine {

    public interface InferenceListener {
        void onImageStart(int index, int total, String fileName);
        void onImageDone(int index, int total, int detectedCount, long inferenceMs);
        void onImageError(int index, String error);
        void onCancelled(int completedCount);
        void onAllDone(List<PointResult> results);
    }

    /**
     * @param sessionDir   sampling_sessions/{sessionId}/ mappa (point_*.jpg + session.json)
     * @param modelFile    kiválasztott .tflite fájl
     * @param meta         ModelMetadata (sidecar .json betöltve)
     * @param cancelFlag   AtomicBoolean — true esetén a ciklus a következő kép előtt megáll
     */
    public void runOnSession(File sessionDir, File modelFile, ModelMetadata meta,
                             AtomicBoolean cancelFlag, InferenceListener listener);

    /** Egyetlen bitmap feldolgozása — preprocess + interpreter.run() + NMS. */
    private List<Detection> detectSingle(Bitmap bitmap, ModelMetadata meta);

    /** Raw TFLite output [1, 4+nc, N] → Detection lista, class-szűrés + NMS. */
    private List<Detection> postprocess(float[][][] rawOutput, ModelMetadata meta);
}
```

---

## `SamplingResultCalculator` — tervezett interfész (nincs implementálva)

```java
public class SamplingResultCalculator {

    /**
     * @param perPointCounts  YoloInferenceEngine kimenete (pontonkénti darabszám)
     * @param samplePoints    lat/lon session.json-ból
     * @param sampleAltitudeM session.json-ból (M09_L1 §6 kiegészítés)
     * @param drone           DroneProfile (droneProfileName alapján DroneProfiles.getByName())
     * @param aoiAreaM2       session.json-ból (teljes tábla területe)
     */
    public static SamplingCountResult compute(int[] perPointCounts,
                                              List<GeoPoint> samplePoints,
                                              double sampleAltitudeM,
                                              DroneProfile drone,
                                              double aoiAreaM2);
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
  "model_used": "corn_yolo11n_v3.tflite",
  "target_class": "corn_plant",
  "computed_at": "2026-07-02T15:04:00Z",
  "total_area_ha": 10.0,
  "sample_count": 34,
  "mean_density_per_ha": 84200.0,
  "stdev_per_ha": 15150.0,
  "cv_percent": 18.0,
  "estimated_total_count": 842000,
  "estimated_total_count_ci95": 68000,
  "points": [
    {
      "index": 0, "lat": 47.123, "lon": 19.456,
      "count": 21, "footprint_area_m2": 4.2,
      "density_per_ha": 50000.0,
      "inference_ms": 1840,
      "warning": null
    }
  ]
}
```

---

## Build-konfiguráció kiegészítés (Gradle, tervezett)

```gradle
dependencies {
    implementation 'org.tensorflow:tensorflow-lite:2.14.0'
    implementation 'org.tensorflow:tensorflow-lite-support:0.4.4'
}
```

**Megjegyzés:** a pontos, Android 5.1-gyel (API 22) még kompatibilis
legfrissebb TFLite verziószám terepi/build-próbával ellenőrizendő —
a fenti csak irányadó, implementáció előtt validálandó, hasonlóan ahhoz,
ahogy az M04 §16 dokumentációja is jelzi a MediaFile API viselkedésének
dekompilált-kód-alapú bizonytalanságát.
