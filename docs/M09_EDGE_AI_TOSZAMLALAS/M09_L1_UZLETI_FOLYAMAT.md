# L1 – Üzleti Folyamat – Edge AI Tőszámlálás

**Modul:** M09
**Szint:** L1 – Üzleti Folyamat
**Verzió:** v0.1.0 (terv, implementáció nem kezdődött el)
**Létrehozva:** 2026-07-02
**Utolsó módosítás:** 2026-07-02
**Státusz:** 🔲 Tervezve

---

## 1. Modul célja

Az M09 modul a mintavételi misszió (M01 §10 / M02 §7 / M04 §15–16) során
lefotózott mintapont-képeken **közvetlenül a tableten** (Crystal Sky, offline)
futtat egy YOLO objektumdetektáló modellt, és a detektált egyedszámból
**azonnal** (szerver-kör nélkül) kiszámítja:

- mintapontonkénti darabszám és db/ha sűrűség,
- a teljes tábla becsült összlétszáma,
- a becslés megbízhatósága (variabilitási koefficiens, CI).

**Miért tabletes és miért nem szerveres:** a mintavételes misszió (ld.
`FEJLESZTESI_OTLETEK.md` → "Mintavételezéses tőszámlálás") pontosan azért
létezik, hogy a pilóta a helyszínen, internet nélkül, percek alatt választ
kapjon a tőszámra — ha az eredményhez fel kellene tölteni a Dronterapia
szerverre és onnan visszavárni, ez a gyorsasági előny elveszne. Az M09 tehát
**önálló, offline képes** számítási útvonal; a Dronterapia-szinkron (M06) csak
opcionális, utólagos archiválás/megosztás céljából jön szóba.

**Mi van már készen, ami ehhez a modulhoz szükséges:**

| Előfeltétel | Modul | Státusz |
|-------------|-------|---------|
| Mintapont-generálás (stratifikált/Halton/random) | M02 `SamplingPointGenerator` | ✅ Kész |
| Mintavételi misszió generálás (hover + fotó minden ponton) | M02 `SamplingMissionGenerator` | ✅ Kész |
| NORMAL-módú végrehajtás (waypoint-akció alapú fotózás) | M04 §15 | ✅ Kész |
| Session-alapú médialetöltés (csak az adott repülés N fotója, nem a teljes SD kártya) | M04 §16 `MediaSessionDownloader` | ✅ Kész |
| **Session-metaadat kiegészítés (magasság, drón profil)** | M04 `MediaSessionDownloader` | 🔲 **Hiányzik — M09 előfeltétele, ld. 6. fejezet** |
| YOLO modell betöltés + futtatás a képeken | **M09 (ez a modul)** | 🔲 Tervezve |
| Sűrűség → hektáronkénti + teljes területi extrapoláció | **M09 (ez a modul)** | 🔲 Tervezve |

---

## 2. Bemenetek és kimenetek

```
[Bemenet]
  sessionDir: File                    (sampling_sessions/{sessionId}/, M04 kimenete)
    → session.json  (pontok lat/lon, local_file, capture_time)
    → point_000.jpg .. point_NNN.jpg
  modelFile: File                     (.tflite, a felhasználó által importált/kiválasztott)
  modelMeta: ModelMetadata            (sidecar .json: label, inputSize, confThreshold,
                                        iouThreshold, targetClassIndex, cropType)

      │
      ▼
[Feldolgozás]
  1. YoloInferenceEngine.detectAll(sessionDir images, modelFile, modelMeta)
       → per-kép: List<Detection> (bbox, class, confidence)
  2. SamplingResultCalculator.compute(detections, sessionMeta, aoiAreaM2)
       → per-pont db, db/m², db/ha
       → átlagsűrűség, szórás, CV%
       → teljes területre extrapolált összlétszám + 95% CI

      │
      ▼
[Kimenet]
  SamplingCountResult:
    perPoint:        List<PointResult>
      PointResult: index, lat, lon, count, densityPerM2, densityPerHa,
                   footprintAreaM2, inferenceMs, warning (pl. "elmosódott kép")
    meanDensityPerHa: double
    stdDevPerHa:      double
    cvPercent:        double
    totalAreaHa:      double
    estimatedTotalCount: double
    estimatedTotalCountCI95: double   (± érték)
    modelUsed:        String
    computedAt:       timestamp

  → results.json a session mappába mentve
  → UI: összefoglaló kártya + pontonkénti táblázat
```

---

## 3. Teljes folyamat — repüléstől az eredményig

```
1. Mintavételi misszió lerepül, médialetöltés lefut (M04 §16 — már kész)
      │  sampling_sessions/{sessionId}/session.json + point_000.jpg..NNN.jpg
      ▼
2. Felhasználó megnyitja az "Eredmények" képernyőt
      │  Session-választó (alapértelmezett: legutóbbi), lista a mappákból
      ▼
3. YOLO modell kiválasztása
      │  Spinner: /sdcard/DroneFly/models/ tartalma (.tflite fájlok neve/címkéje)
      │  Ha nincs modell: "Nincs importált modell — másold a .tflite fájlt ide: ..."
      ▼
4. [Számlálás indítása] gomb
      │  Validáció: session.json tartalmazza-e sample_altitude_m + drone_profile_name-t
      │  Ha hiányzik (régi session, M09 előtti) → dialog: "Add meg a repülési magasságot"
      ▼
5. YoloInferenceEngine — szekvenciális feldolgozás, progress bar
      │  "Feldolgozás: 12/34 kép (point_011.jpg)"
      │  Minden képre: preprocess → interpreter.run() → NMS → detektálásszám
      │  Megszakítható (Mégse gomb)
      ▼
6. SamplingResultCalculator.compute()
      │  footprintAreaM2 = imageCoverageWidthM(sampleAlt, drone) × imageCoverageHeightM(...)
      │  density_i = count_i / footprintAreaM2
      │  mean, stdev, CV%, extrapolált összlétszám (ld. 5. fejezet képletek)
      ▼
7. Eredmény képernyő
      │  ┌─────────────────────────────────────────┐
      │  │  📊 Mintavételi eredmény — 2026-07-02     │
      │  │  Modell: corn_yolo11n_v3.tflite           │
      │  │                                           │
      │  │  Becsült összlétszám: 842 000 tő          │
      │  │  (± 68 000, 95% CI)                       │
      │  │  Átlagsűrűség: 84 200 tő/ha                │
      │  │  Variabilitás (CV): 18% — homogén tábla    │
      │  │  Terület: 10.0 ha · 34 mintapont           │
      │  └─────────────────────────────────────────┘
      │  Pontonkénti táblázat: # | GPS | db | db/ha | (⚠ jelzés, ha releváns)
      ▼
8. Mentés
      │  results.json a session mappában
      │  Export gomb: CSV (pont, lat, lon, db, db/ha) — megrendelőnek átadható
      ▼
9. (Opcionális, jövőbeli) Dronterapia szinkron (M06 csatorna)
      Ha van net: results.json + session.json feltöltés archiválásra/megosztásra
```

---

## 4. UI-folyam elhelyezése

Az "Eredmények" képernyő **nem** az M01 térképes fő nézetbe épül be, hanem
önálló Activity/Fragment, amit:

- a mintavételi misszió befejezése után egy toast/gomb ajánl fel
  ("Misszió kész — 34 fotó letöltve. [Eredmények megtekintése →]"),
- vagy a főmenüből bármikor elérhető (korábbi session-ök is feldolgozhatók,
  akár más modellel újra — pl. ha frissült a betanított modell).

Ez a szétválasztás azért fontos, mert a `MissionPlannerActivity` már 2 797
soros monolit (ld. projekt memória) — egy YOLO-futtató UI-t **nem** érdemes
ebbe zsúfolni.

---

## 5. Statisztikai képletek

### 5.1 Mintaponti sűrűség

```
footprintAreaM2 = imageCoverageWidthM(sampleAltitudeM, drone)
                × imageCoverageHeightM(sampleAltitudeM, drone)

  (GsdCalculator meglévő metódusai — M02 — újrahasznosítva, ld. 6. fejezet)

density_i [db/m²] = count_i / footprintAreaM2
density_i [db/ha] = density_i [db/m²] × 10 000
```

### 5.2 Átlag, szórás, CV%

```
mean = Σ(density_i) / N
stdev = sqrt( Σ(density_i - mean)² / (N - 1) )
CV%  = (stdev / mean) × 100
```

### 5.3 Extrapoláció a teljes területre (v1 — homogén becslés)

```
estimatedTotalCount = mean [db/m²] × totalAreaM2

95% CI (normál közelítés, N ≥ ~15 mintánál elfogadható):
  SE = stdev / sqrt(N)
  CI95 = 1.96 × SE × totalAreaM2
```

**Fontos korlát, amit az UI-nak jeleznie kell:** ez a becslés **homogén
eloszlást tételez fel** a mintapontok között (nincs térbeli interpoláció/
IDW). Ha CV% > 30%, az UI figyelmeztet: *"A tábla heterogén lehet (CV
{cv}%) — fontold meg a sűrűbb mintavételt vagy zónánkénti (stratifikált)
elemzést."* — ez a statisztikai megfontolás már szerepel a
`FEJLESZTESI_OTLETEK.md`-ben, itt csak a konkrét UI-visszajelzésként
valósul meg.

### 5.4 Térbeli interpoláció (IDW heatmap) — **kikerül a v1 hatóköréből**

A Dronterapia (Python) oldalon már létezik IDW-interpoláció
(`Dronterapia/utils/interpolation.py`). Ennek Java-portja **későbbi
fázis** (ld. 8. fejezet) — a v1 cél az "azonnali szám", nem a
prescription-map vizualizáció. Ha mégis szükséges helyszíni heatmap,
egyszerűbb megoldás: a `results.json`-t szinkronizálni a Dronterapia felé
(M06 csatorna), és ott a meglévő IDW logikával generálni.

---

## 6. Session.json kiegészítés — M04 előfeltétel

A jelenlegi `MediaSessionDownloader.writeSessionJson()` (M04 §16) **nem**
menti el a mintavételi magasságot és a drón profilt — enélkül az M09 nem
tudja kiszámítani a `footprintAreaM2`-t anélkül, hogy a felhasználót
manuálisan megkérdezné (ami pont az a kézi-GSD-bevitel korlát, amit a
Dronterapia POC-nál is azonosítottunk, és amit a DroneFly oldali pontos
misszió-adatokkal ki lehetne küszöbölni).

**Szükséges módosítás (M04, kis kiegészítés, nem új modul):**

```java
// MediaSessionDownloader.downloadSessionMedia() új paraméterek:
double sampleAltitudeM,      // SamplingMissionGenerator.GeneratorResult.altitudeM-ből
String droneProfileName,     // MissionConfig-ból (DroneProfile.name)
double aoiAreaM2             // GeneratorResult.areaM2-ből (már számolva, csak át kell adni)

// session.json új mezői:
{
  "sample_altitude_m": 6.0,
  "drone_profile_name": "Phantom 4 Pro v1",
  "aoi_area_m2": 100000.0,
  ...
}
```

Ez a legkisebb, legkorábban elvégzendő lépés az M09 megvalósítás előtt —
gyakorlatilag egyetlen módosított metódus-szignatúra és 3 új JSON mező.

---

## 7. Akadályok / kihívások

1. **Crystal Sky CPU/RAM korlát:** régi, gyenge hardver (Android 5.1) —
   csak könnyű, kvantált (INT8) nano-méretű YOLO modell (YOLOv8n/YOLO11n)
   futtatható elfogadható idő alatt. Részletek: M09_L2.
2. **Modell-előállítás nem az app feladata:** a felhasználónak a modellt
   külön (asztali gépen/felhőben, pl. Ultralytics YOLO train + `export
   format=tflite int8=True`) kell betanítania és a tabletre másolnia. Az
   app csak **futtatja**, nem tanítja a modellt.
2. **Elmosódott/rossz minőségű mintaponti fotó:** hovering pontatlanság
   vagy szél miatt egy-egy kép használhatatlan lehet — az eredmény
   táblázatban ⚠ jelzéssel, de nem blokkolja a többi pont feldolgozását.
3. **GSD-konzisztencia:** minden mintapont azonos (sample) magasságon
   készül egy session-ön belül, így a footprintAreaM2 session-szinten
   egyetlen konstans — nem kell képenként újraszámolni. Lejtős táblánál ez
   torzíthat (ld. "Pre-flight DTM" ötlet a FEJLESZTESI_OTLETEK.md-ben),
   de ez már ismert, dokumentált korlát, nem M09-specifikus.
4. **Célosztály kiválasztás:** ha a modell több osztályt is detektál
   (pl. "kukorica" + "gyom"), a `modelMeta.targetClassIndex` határozza meg,
   melyiket számoljuk — UI-ban módosítható legyen futtatás előtt.

---

## 8. Kapcsolódó modulok

| Modul | Kapcsolat típusa |
|-------|-----------------|
| M01 Misszió Tervező | **Belépési pont** — "Eredmények megtekintése" gomb a mintavételi misszió befejezése után |
| M02 Grid Engine (`GsdCalculator`) | **Újrahasznosított logika** — footprint terület számítás (`imageCoverageWidthM/HeightM`) |
| M04 DJI Integráció (`MediaSessionDownloader`) | **Bemenet forrás** — session.json + fotók; **kis kiegészítés szükséges** (6. fejezet) |
| M06 Dronterapia Szinkron | **Jövőbeli, opcionális kimenet** — results.json archiválás/megosztás, esetleg modell-terjesztés a tabletre |
| M08 Akku-statisztika (jövőbeli, M07 dokumentumban lefoglalva) | Nincs közvetlen kapcsolat |
| `FEJLESZTESI_OTLETEK.md` → "Mintavételezéses tőszámlálás" | **Szülő koncepció** — ez a dokumentum ennek a 4. ("Edge AI") lépését bontja ki részletes tervvé |
