# L1 – Üzleti Folyamat – Edge AI Tőszámlálás

**Modul:** M09
**Szint:** L1 – Üzleti Folyamat
**Verzió:** v1.0.0 (implementálva, eszközön még nem tesztelve)
**Létrehozva:** 2026-07-02
**Utolsó módosítás:** 2026-07-03
**Státusz:** ✅ Implementálva — build zöld (assembleDebug), terepi/eszköz-teszt hátravan

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
  modelFile: File                     (.onnx, a felhasználó által importált/kiválasztott —
                                        pl. a Dronterápia modell-regiszteréből kézzel másolva)
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
      │  Spinner: /sdcard/DroneFly/models/ tartalma (.onnx fájlok neve/címkéje)
      │  Ha nincs modell: "Nincs importált modell — másold a .onnx fájlt ide: ..."
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
      │  │  Modell: corn_yolo11n_v3.onnx              │
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

## 4. UI-folyam elhelyezése és belépési pontok

Az "Eredmények" képernyő (`SamplingResultsActivity`) **nem** az M01 térképes
fő nézetbe épül be, hanem önálló Activity. Ez a szétválasztás azért fontos,
mert a `MissionPlannerActivity` már 2 797+ soros monolit (ld. projekt
memória) — egy YOLO-futtató UI-t **nem** érdemes ebbe zsúfolni.

**Belépési pontok (2026-07-03-tól, mind implementálva):**

1. **Misszió után:** a mintavételi misszió médialetöltésének befejező
   dialógusa ajánlja fel ("Letöltés kész — [Eredmények megtekintése →]"),
   a friss session előválasztva.
2. **A misszió tervezőből bármikor:** a bal oldali gombsor "AI" gombja —
   ez a fő munka közbeni átjárás, mert a főképernyőre futó appból nem
   lehet visszalépni. A tőszámláló fejlécének "🗺 Tervező" gombja
   visszavisz (REORDER_TO_FRONT — a futó tervező-példányt hozza előre,
   nem hoz létre újat).
3. **A főképernyőről (app-indításkor):** "Tőszámlálás (Edge AI)" gomb.

Korábbi session-ök bármikor feldolgozhatók, akár más modellel/paraméterekkel
újra (pl. ha frissült a betanított modell); a fotóimport (9. fejezet) a
tőszámláló képernyőről érhető el.

**Futtatási paraméterek a UI-n (a Dronterapia webes Counting felület
mintájára):** a konfidencia küszöb és az IoU (NMS) küszöb futtatás előtt
módosítható — alapértékük a kiválasztott modell sidecar `.json`-jából
töltődik, a ténylegesen használt értékek a `results.json`-ba
(`conf_threshold_used`, `iou_threshold_used`) és az összefoglaló kártyára
is kikerülnek, így az eredmény mindig reprodukálható. A webes felület
további paraméterei (sliding window, TTA, sor-detekció, preprocessing
presetek) v1-ben nem kerülnek a tabletre — ld. M09_L2 §8a döntés.

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

### 5.3 Extrapoláció a teljes területre (v1 — mintavételi statisztika, nem térbeli interpoláció)

**Fontos fogalmi elhatárolás:** ez a lépés **nem interpoláció** (nem becsül
értéket egyes nem-mintázott pontokon), hanem klasszikus **mintavételi
statisztika (design-based sampling inference)** — a mintapontok közti
szórásból von le következtetést a *teljes tábla összesített* becslésének
megbízhatóságáról. Ha valaki a mintapontok közötti *térbeli* értékeket is
szeretné becsülni (hőtérkép), az IDW/kriging kell — ld. 5.4.

```
mean  = Σ(density_i) / N                              [db/m²]
stdev = sqrt( Σ(density_i - mean)² / (N - 1) )
CV%   = (stdev / mean) × 100

estimatedTotalCount = mean × totalAreaM2

95% CI — véges populáció korrekcióval (FPC) és Student-t eloszlással:
  N_plot = totalAreaM2 / footprintAreaM2   (hány mintaponti "parcella" férne
                                             el összesen a táblán — ez fejezi
                                             ki a mintavételi arányt)
  SE      = stdev / sqrt(N)
  SE_fpc  = SE × sqrt( (N_plot − N) / (N_plot − 1) )    ha N_plot > N
          = SE                                          egyébként (N_plot ≤ N esetén nincs korrekció)
  t95     = Student-t kritikus érték, df = N − 1, 95%-os szint (ld. lookup
            tábla lent — nagy df-nél tart 1.96-hoz)
  CI95    = t95 × SE_fpc × totalAreaM2
```

**Miért ez a két pontosítás a korábbi (fix 1.96, FPC nélküli) képlethez
képest:**
- **FPC:** a `SE` önmagában feltételezi, hogy a minta a populáció
  elhanyagolható hányada. Mezőgazdasági mintavételnél ez tipikusan igaz
  (pár tucat, egyenként pár m²-es fotó egy több hektáros táblán, tehát
  `N/N_plot` << 1, a korrekció szinte nem csökkenti a CI-t) — de kisebb
  táblánál vagy sűrűbb mintavételnél (pl. M10 sűrű rács jellegű mintavétel)
  a korrekció érdemben szűkíti a hibasávot, tehát nem szabad kihagyni.
- **Student-t normál (z=1.96) helyett:** 15–40 mintapontos tartományban
  (a tipikus mintavételi misszió mérete) a t-eloszlás szélesebb, tehát
  **óvatosabb** (nem alábecsült) CI-t ad, mint a nagy mintára érvényes
  normál közelítés.

**t95 lookup tábla (df = N − 1):**

| df | 5 | 10 | 15 | 20 | 25 | 30 | 40 | ≥60 |
|----|---|----|----|----|----|----|----|----|
| t95 | 2.571 | 2.228 | 2.131 | 2.086 | 2.060 | 2.042 | 2.021 | 2.000→1.96 |

(A `SamplingResultCalculator` a legközelebbi táblázatbeli df-et használja,
60 fölött a normál 1.96 közelítéssel.)

**Fontos korlát, amit az UI-nak jeleznie kell:** ez a becslés **homogén
eloszlást tételez fel** a mintapontok között — a CI a *teljes tábla összegére*
ad megbízhatósági sávot, nem arra, hogy a tábla melyik része sűrűbb/ritkább.
Ha CV% > 30%, az UI figyelmeztet: *"A tábla heterogén lehet (CV {cv}%) —
fontold meg a sűrűbb mintavételt vagy zónánkénti (stratifikált) elemzést."*
— ez a statisztikai megfontolás már szerepel a `FEJLESZTESI_OTLETEK.md`-ben,
itt csak a konkrét UI-visszajelzésként valósul meg.

### 5.4 Térbeli interpoláció (IDW heatmap) — **kikerül a v1 hatóköréből**

**Mikor kellene IDW, és mikor nem:** az 5.3 alatti mintavételi statisztika
egyetlen számot ad (± hibasáv) a *teljes táblára összesítve* — ez a v1 cél
("azonnal megmondani a tőszámot"). Az IDW ezzel szemben egy **térképet**
(pixelenkénti/rácsonkénti sűrűségbecslést) ad — az kell, ha a felhasználó
azt akarja *látni*, hogy a tábla melyik része sűrűbb/ritkább (pl.
prescription map változó dózisú beavatkozáshoz). A két eszköz különböző
kérdésre válaszol; a v1 csak az elsőt implementálja.

A Dronterapia (Python) oldalon már létezik IDW-interpoláció
(`Dronterapia/utils/interpolation.py`). Ennek Java-portja **későbbi
fázis** (ld. 8. fejezet) — csak akkor éri meg, ha terepi visszajelzés
igazolja a hőtérkép-igényt. Ha mégis szükséges helyszíni heatmap,
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
2. **Modell-előállítás nem az app feladata:** a modellt a Dronterápia
   rendszer tanítja be (Ultralytics YOLO, `export format=onnx`) — az app
   csak **futtatja**, nem tanítja a modellt. A betanított `.onnx` fájlt
   kézzel (fájlkezelő/ADB) kell a tabletre másolni a sidecar `.json`
   metaadattal együtt.
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
| M03 Export/Import (`ProjectManager`) | **Kontextus forrás a fotóimportnál** (9. fejezet) — a mentett repülési terv adja a polygont (terület), a mintavételi magasságot és a drónprofilt |
| M04 DJI Integráció (`MediaSessionDownloader`) | **Bemenet forrás** — session.json + fotók; kiegészítve: fájllista-lekérés + kiválasztott fájlok letöltése (fotóimport) |
| M06 Dronterapia Szinkron | **Jövőbeli, opcionális kimenet** — results.json archiválás/megosztás, esetleg modell-terjesztés a tabletre |
| M08 Akku-statisztika (jövőbeli, M07 dokumentumban lefoglalva) | Nincs közvetlen kapcsolat |
| `FEJLESZTESI_OTLETEK.md` → "Mintavételezéses tőszámlálás" | **Szülő koncepció** — ez a dokumentum ennek a 4. ("Edge AI") lépését bontja ki részletes tervvé |

---

## 9. Fotóimport — tőszámlálás repülés nélkül (2026-07-03)

A felhasználói igény: a számlálás **ne csak** a mintavételi misszió
lezárása után legyen elérhető — bármikor lehessen (a) a drón SD kártyájáról
fotókat áttölteni, vagy (b) a tablet tárolójából fotókat kiválasztani, és
azokon futtatni a modellt.

**Kritikus követelmény — a kontextus:** egy fotóhalom önmagában nem elég a
db/ha + extrapolált összlétszám számításához: tudni kell, hogy a képek
**milyen magasságból** készültek (footprint terület) és **mekkora táblát**
reprezentálnak (extrapolációs cél). Ezért az import kötelező lépése a
**repülési terv kiválasztása** — a mentett `.flightprogram.json` tervek
(M03 `ProjectManager`) tartalmazzák a polygont (→ terület), a mintavételi
magasságot (`sampling.sample_altitude_m`, vagy grid tervnél `altitude_m`)
és a drónprofilt. Ha nincs megfelelő terv: kézi megadás (magasság + terület).

```
PhotoImportActivity (a SamplingResultsActivity "📂 Fotók importálása" gombjáról)
      │
      ▼
1. Forrás választása
      │  [🛸 Drón SD kártya]  — MediaManager fájllista (JPG, legújabb elöl)
      │  [📱 Tablet tároló]   — beépített mappaböngésző (Crystal Sky-on nincs
      │                         megbízható rendszer-fájlválasztó)
      ▼
2. Fotók kijelölése (checkbox)
      ▼
3. Kontextus: repülési terv kiválasztása (Spinner, mentett tervek)
      │  → terület = polygonAreaM2(terv.polygon), magasság, drónprofil
      │  → vagy "Kézi megadás": magasság [m] + terület [ha] kézzel
      ▼
   (Fejléc: "← Vissza" gomb — importálás nélkül visszalép a tőszámlálóhoz,
    a tablet fizikai vissza gombjától függetlenül; 2026-07-04)
      │
4. Import
      │  Drón forrás: kiválasztott MediaFile-ok letöltése (M04 bővítés)
      │  Tablet forrás: fájlok másolása
      │  → sampling_sessions/import_{yyyyMMdd_HHmmss}/point_000.jpg..
      ▼
5. session.json összeállítása
      │  Pontkoordináták a képek EXIF GPS adataiból (ha nincs geotag: 0,0
      │  + a pont a táblázatban koordináta nélkül jelenik meg)
      │  source: "import_drone_sd" | "import_tablet", flight_plan_name
      ▼
6. SamplingResultsActivity nyílik az új sessionnel — innen a folyamat
   azonos a missziós úttal (modellválasztás → számlálás → eredmény)
```

**Fontos statisztikai megjegyzés:** az importált fotóknál a rendszer nem
tudja garantálni, hogy a képek a tábla **reprezentatív mintavételével**
készültek (szemben a generált mintavételi misszióval, ahol a pontkiosztás
stratifikált/Halton). Ha a felhasználó "kézzel" fotózott pontokat importál,
az extrapoláció torzulhat — ez felhasználói felelősség, a dokumentáció/súgó
szintjén jelezni kell.

**Fotó-előnézetek (2026-07-03, terepi visszajelzés alapján):** a felhasználó
látni akarja, mely képeken fut a számlálás. Három helyen van előnézet:
- **Tőszámláló képernyő:** a kiválasztott session fotóiból thumbnail-sáv a
  session-választó alatt (aszinkron betöltés, inSampleSize-zal) — koppintásra
  teljes képernyős nagyítás.
- **Eredmény-táblázat — bounding boxos annotált kép:** a pontsorra koppintva
  a pont fotója a **detektált egyedek zöld bekeretezésével** nyílik meg
  (a Dronterapia webes Counting felület annotált képének megfelelője),
  bal felső sarokban a detektálás-számmal. Ez a terepi kalibráció fő
  eszköze: a felhasználó vizuálisan ellenőrizheti, hogy a konfidencia
  küszöb jól van-e belőve (kimaradó tövek → küszöb csökkentése; dupla/hamis
  detektálások → küszöb emelése). A detekciók (normalizált bbox + konfidencia)
  a `results.json`-ba is mentésre kerülnek (`points[].detections`).
- **Fotóimport (tablet forrás):** minden fájlsorban thumbnail; a thumbnailre
  koppintva nagyítás, a sor többi részére koppintva kijelölés. A drón SD
  fájljaihoz letöltés előtt nem érhető el előnézet (a MediaFile tartalma
  csak letöltés után olvasható).

**Terepi kalibrációs eredmény (2026-07-03/04):** a Dronterápia
`kukorica_640px_20260109_221301.onnx` modellel a felhasználó előbb 0.05-ös,
majd finomítva **0.20 konfidencia + 0.30 IoU** küszöbbel kapott a valósággal
egyező tőszámot — a modell sidecar `.json` alapértéke ennek megfelelően
0.20 / 0.30 lett. (A conf 0.05→0.20 közti háromszoros detektálás-különbség
jelzi, hogy ez a modell nagyon érzékeny a küszöbre ezen az állományon — a
bounding boxos annotált nézet a vizuális kalibráció eszköze.)

---

## 10. GSD kalibráció — vonalzós skálamérés (2026-07-04)

> **⚠ Kiterjesztve a §11-ben (2026-07-04):** ez a fejezet a GSD-mérést mint a
> *footprint/sűrűség* korrekcióját írja le, a **számlálás UTÁN** (eredmény-sorra
> kattintva). A §11 ezt **kiterjeszti**: a GSD-t a **futtatás ELŐTT**, a
> betöltött mintaképeken kell mérni, mert a GSD nemcsak a sűrűséget, hanem a
> **detektálást is** befolyásolja (a modell adott betanítási GSD-hez kötött).
> A vonalzó-eszköz (`GsdRulerView`) ugyanaz, csak korábban hívjuk, és a mért
> GSD innentől kettős célt szolgál: (a) a kép átméretezése a betanítási GSD-re
> a detektáláshoz, (b) a footprint a sűrűséghez. A §10 „kattints az
> eredmény-sorra, kalibrálj utólag" útvonala a §11 bevezetésével elavul (a
> footprint utólagos finomítása megmaradhat, de a fő mérés előre kerül).

**Státusz:** ✅ **Implementálva és eszközön verifikálva** (Crystal Sky, 2026-07-04).
Terepi teszt: importált kukoricatábla-képen a vonalzós mérés footprint 41,6 m²,
GSD 0,289 cm/px, EXIF-kereszt-ellenőrzés „10,0 m → mérés ~5,3 m (−72%)"; a
„Csak erre a képre" alkalmazás után a `results.json` pontja `footprint_source:
MANUAL`, `gsd_cm_px`, `ref_distance_m`, `ref_pixel_length` mezőkkel, a db/ha
inferencia nélkül újraszámolt (327/41,58 m² = 78 636/ha), a többi pont EXIF-en
maradt, az összesített CV/CI és a heterogenitás-figyelmeztetés helyesen frissült.

### 10.1 A megoldandó probléma

A db/ha sűrűség és a teljes-területi extrapoláció a **footprint területtől**
(egy kép valós talaj-lefedettsége) függ. Ezt jelenleg a `sample_altitude_m`
(EXIF vagy repülési terv) + drónprofil GSD-képletéből számoljuk (§5.1). Az
alacsony (mintavételi) repüléseknél viszont a **Phantom 4 Pro barometrikus
magasság-tartása bizonytalan** — a terepi tapasztalat szerint néha a beállított
magasság **kétszeresére** emelkedett. Mivel a footprint a magasság
**négyzetével** arányos (szélesség és hosszúság is lineárisan nő), egy 2×-es
magasság-hiba **4×-es footprint-hibát**, tehát a db/ha becslés **negyedelését**
okozza. A YOLO számláló akármilyen pontos: rossz footprinttel a hektáronkénti
eredmény használhatatlan.

> **Megjegyzés a §7.3 korábbi állításához:** ott az szerepel, hogy „a
> footprint session-szinten egyetlen konstans” — ez a **beállított** magasság
> feltevése mellett igaz, de a barometrikus hiba pontonként eltérhet, ezért a
> GSD kalibráció **pontonként felülírható** footprintet vezet be (§10.4).

### 10.2 A módszer: mért skála a következtetett helyett

A GSD közvetlenül **mérhető**, magasság nélkül: a betöltött fotón a felhasználó
egy **vonalzót** húz ki két pont közé (mint a Google Maps távolságmérője), és
megadja a valós távolságot (pl. sortáv). A rendszer a vonal **pixelhosszából**
számol:

```
GSD [m/px]        = valós_távolság_m / vonal_pixelhossz
footprint_szél_m  = GSD × kép_szélesség_px
footprint_hossz_m = GSD × kép_magasság_px
footprintAreaM2   = footprint_szél_m × footprint_hossz_m
```

Ez a magasságot teljesen megkerüli — a talaj-skálát közvetlenül egy ismert
referenciából méri.

### 10.3 Négy korrektsági szabály (a pontosságot ezek döntik el)

1. **A referencia VÍZSZINTES talaj-távolság legyen, ne függőleges.** Nadír
   képen egy függőleges tárgy (pl. a növény magassága) fentről pontnak látszik,
   nem képezhető le skálára. A **sortáv** az ideális referencia: a vetőgép
   pontosan beállította, nadírból jól látszik, és vízszintes. Az UI-nak ezt
   egyértelműen jeleznie kell (a mező súgója: „vízszintes talaj-távolság, pl.
   sortáv — NEM növénymagasság”).
2. **Hosszú, több egységet átfogó alapvonal.** Egyetlen sortáv (~76 cm)
   pixelben mérve zajos. Ezért a UI ösztönzi a hosszú bázist: a felhasználó
   kihúzhat pl. 10 sortávot, megadja a sortávot ÉS a darabszámot (10), a
   rendszer a szorzattal (7.6 m) számol. A fix pixelhiba így nagyobb bázisra
   oszlik → pontosabb GSD.
3. **Felbontás-invariancia.** A képet kicsinyítve jelenítjük meg (inSampleSize).
   A számítás a *méter / megjelenített-pixel* skálát használja, és a
   *megjelenített* képszélességgel szoroz — a footprint (talajszélesség) így
   független a megjelenítési felbontástól, nem kell az eredeti 5472 px-re
   visszakonvertálni. (Ld. M09_L2 §10 döntés és M09_L4 §7 levezetés.)

4. **A mérés a kép KÖZEPÉN történjen (2026-07-04, optikai megfontolás).**
   Fontos, hogy a képen HOL mérünk. Egy gyakori (intuitív, de pontatlan)
   indoklás szerint „a szél messzebb van a kamerától, ezért torzít" — valójában
   **ideális nadír + sík talaj + torzításmentes objektív esetén a GSD az egész
   képen ÁLLANDÓ**, mert a szenzorsík párhuzamos a talajsíkkal, és két
   párhuzamos sík között a centrális vetítés egyszerű egyenletes nagyítás
   (`GSD = H/f` mindenhol); a „távolabbi szél" hatást pontosan kioltja a ferdébb
   beesési szög. A szélen mérés valós hibaforrásai NEM a távolság, hanem:
   - **objektív radiális torzítás** — a valódi lencse a sarkokban torzít
     (a P4P is), ez helyi skálahibát ad;
   - **maradék gimbal-dőlés** — a gimbal sosem tökéletesen nadír, pár fok dőlés
     a képen át skála-gradienst okoz (közeli/távoli oldal);
   - **növénymagasság-parallaxis** — a széleken a növény oldalát, középen a
     tetejét látjuk, a referencia elcsúszhat.

   **Szintézis (feloldja a 2. szabállyal való feszültséget):** a referenciavonal
   legyen **hosszú ÉS a képközépre szimmetrikusan elhelyezett** — hosszú a
   pixelpontosságért, középre szimmetrikus, mert a maradék torzítás/dőlés a
   közép körül nagyjából antiszimmetrikus, így részben kioltja magát. A
   `GsdRulerView` mutasson középkeresztet, és figyelmeztessen, ha a vonal a
   szélek felé csúszik. (Jövőbeli finomítás: a P4P ismert torzítási
   együtthatóival / az XMP `DewarpData`-val a kép előzetesen kiegyenesíthető,
   és akkor a szélen mérés is érvényes — v1-ben a „középen mérj" a pragmatikus út.)

### 10.4 Több kép — footprint-módok

**Terepi megfigyelés (fontos, de NEM végleges):** a felhasználó látta, hogy a
drón repülés közben méterről méterre emelkedett, a hiba képről képre változott
→ a hiba **NEM rendszeres** volt. **DE ez a megfigyelés IMU-kalibráció ELŐTT
történt** — azóta a magasság-tartás vélhetően pontosabb, bár ez még nincs
igazolva. Ezért **egyik módot sem zárjuk ki véglegesen**; a mód-viabilitást a
poszt-IMU-kalibrációs EXIF-kereszt-ellenőrzés (§10.6) dönti el egy friss
repülésen.

| Mód | Mit csinál | Státusz |
|-----|-----------|---------|
| **EXIF** (jelenlegi) | A magasságból számol (§5.1) | Alapértelmezett; **HA a poszt-IMU-cal kereszt-ellenőrzés kicsi eltérést mutat, ez elég** és alig kell kézi kalibráció |
| **Képenkénti kézi** | Minden képen külön vonalzós mérés | A **legpontosabb** mód, a biztos tartalék, ha az EXIF megbízhatatlan marad; ergonómia teszi vállalhatóvá (§10.4b) |
| **Lehorgonyzott (anchor)** | Egy mérés + EXIF-relatív korrekció | 🔲 **Függőben** — ha a hiba poszt-cal *rendszeres* (konstans arány), ez lesz a hatékony mód |
| **Többpontos interpoláció** | 3–4 kép kézi kalibrációja → GSD interpoláció idő/sorszám mentén | 🔲 **Függőben** — ha a drift *sima* (progresszív emelkedés); a hover-süllyed mintavételi repülésnél kérdéses |
| **Első kép örökítése** | Egy kép GSD-jét mindre | Csak akkor, ha a magasság stabilan tartott — a kereszt-ellenőrzés igazolja |

**Vagyis: a döntési fa nem előre rögzített, hanem mérés-vezérelt.** Az első
lépés minden esetben az EXIF-kereszt-ellenőrzés lefuttatása egy poszt-IMU-cal
repülésen — az eredménye választja ki, melyik mód az optimális.

### 10.4b Hogyan tegyük a képenkénti kézi módot gyorssá (kulcs-ergonómia)

A képenkénti kalibráció 30+ pontnál csak akkor vállalható, ha gyors. A trükk:
**a referencia-távolság (sortáv) a tábla egészén állandó.** Ezért:

- A felhasználó **egyszer** megadja a referenciát (pl. „76 cm × 10 sortáv”), és
  ez **megmarad** a következő képekre.
- Képenként csak **egy vonalat kell húzni** ugyanazon N sortávra → ~2 koppintás/kép.
- „Következő kép” gomb/swipe a képnézetben → gyors végigpörgetés.
- 30 pont így ~2–3 perc, teljes pontossággal.

### 10.4c Hibrid gyorsítás: EXIF-kereszt-ellenőrzés az outlierekre

Nem feltétlenül kell mind a 30-at kézzel mérni. Az EXIF-kereszt-ellenőrzés
(§10.6) minden képnél megmutatja a mért vs. EXIF-GSD eltérést. Munkamenet:
kalibrálj néhány képet → a rendszer megjelöli, mely további képek EXIF-je
tér el gyanúsan → csak azokat kalibráld. A „jó” EXIF-ű képeknél marad az EXIF.

### 10.5 A kalibráció szétválik a számlálástól (fontos folyamati döntés)

A footprint **nem befolyásolja a nyers darabszámot** — a YOLO ugyanannyi tövet
talál, akármekkora a footprint; csak a **sűrűség** (db/terület) és az
extrapoláció függ tőle. Ezért:

- A kalibráció **a számlálás UTÁN** is elvégezhető, és a db/ha + összlétszám
  **azonnal újraszámolható** új inferencia nélkül (a drága YOLO-lépés és az
  olcsó skála-kalibráció szétválik).
- A felhasználó a footprintet addig hangolhatja, amíg reális eredményt nem kap,
  másodperc alatt, a bounding boxos képen mérve.

### 10.6 Bónusz: EXIF-kereszt-ellenőrzés

Ha megvan a mért GSD, összevethetjük az EXIF-magasságból számolttal, és
számszerűsítjük a magasság-hibát:

```
EXIF szerint: 12 m → GSD 0.33 cm/px
Mérés szerint: GSD 0.66 cm/px → a drón valójában ~24 m-en repült (+100%)
```

Ez egyrészt igazolja a felhasználó megfigyelését, másrészt figyelmeztethet, ha
egy adott kép EXIF-je gyanús (nagy eltérés a mért és következtetett GSD közt).

### 10.7 UI-folyam

```
Eredmény-táblázat pontsora → bounding boxos előnézet (már kész)
      │  Új: [📏 Skála kalibrálása] gomb a képnézetben
      ▼
1. Vonalzó mód: két húzható végpont a képen (zoom/nagyító a pontos illesztéshez)
      │  Élőben: vonal pixelhossza
      ▼
2. Valós távolság megadása:
      │  [ 76 ] cm  ×  [ 10 ] egység   (= 7.6 m alapvonal)
      │  Súgó: "vízszintes talaj-távolság, pl. sortáv"
      ▼
3. Élő kijelzés: GSD (cm/px), footprint (m × m = m²), és
      │  "EXIF szerint X m → mérés szerint ~Y m (±Z%)"
      ▼
4. Alkalmazás módja (a §10.4 megfigyelés után egyszerűsítve):
      │  (•) Csak erre a képre         ← elsődleges (a hiba pontonként eltér)
      │  ( ) Következő kalibrálatlan képre lépés (referencia megmarad)
      │  ( ) [haladó] Interpoláció a kalibrált pontok közé (csak sima drift)
      │  A referencia-távolság MEGMARAD a következő képre (§10.4b)
      ▼
5. A db/ha és az összlétszám azonnal újraszámol, results.json frissül
   (footprint_source + footprint_area_m2 pontonként mentve)
```

### 10.8 Prioritás

Ez **fontosabb, mint a zónásítás**: a zónásítás a mintavétel *hatékonyságát*
javítja, ez viszont a végeredmény *helyességét*. Pontatlan footprinttel a
legszebb zónásítás is rossz db/ha-t ad. Ezért ez az elsőként megvalósítandó
fejlesztés a jelenlegi M09 v1 után.

### 10.9 A magasság-hiba jellege — ÚJRA NYITOTT (IMU-kalibráció miatt)

A korábbi „erratikus → csak képenkénti" következtetést **fel kell oldani**: az a
megfigyelés **IMU-kalibráció előtt** történt, azóta a magasság-tartás vélhetően
javult (de nem igazolt). Ezért a footprint-mód nincs előre rögzítve — az első
lépés egy **poszt-IMU-cal repülés képein az EXIF-kereszt-ellenőrzés** (§10.6)
lefuttatása:
- kis eltérés (EXIF ≈ mért) → az **EXIF mód elég**, alig kell kézi kalibráció;
- rendszeres eltérés (konstans arány) → a **lehorgonyzott** mód a hatékony;
- sima drift → a **többpontos interpoláció**;
- erratikus → a **képenkénti kézi** a biztos, pontos tartalék (§10.4b ergonómia).

Ezért a mérőeszközt (§10.6 kereszt-ellenőrzés) úgy kell megépíteni, hogy elsőként
ezt a diagnózist adja meg — a kalibrációs UI a mérés mellé mindig kiírja a mért
vs. EXIF eltérést, több képen összesítve is.

### 10.10 Mélyebb következmény — a mintavételi repülés maga (jövőbeli megfontolás)

Ha a magasság ennyire megbízhatatlan alacsonyan, felvetődik, hogy a *repülési*
oldalon is javítani kellene (M01/M04 hatókör, nem M09): pl. terepkövetés
pontosabb magasság-referenciával, lassabb süllyedés a stabilabb hover-magasságért,
vagy a mintavételi magasság emelése oda, ahol a barométer megbízhatóbb (a footprint
nő, de a GSD kalibrációval korrigálható). Ez nem ennek a modulnak a feladata, de
rögzítjük, mert a gyökér-ok a repülésvezérlésben van, nem a kiértékelésben.

---

## 11. GSD-tudatos futtatás — mérés ELŐBB, csempézett inferencia (2026-07-04)

**Státusz:** ✅ **Implementálva** (2026-07-04), eszközön még nem tesztelve. Ez a
korábbi naiv (teljes-kép → 640-re skálázott) futtatás átdolgozása. A
`YoloInferenceEngine` GSD-tudatos resize + sliding window csempézés + cross-tile
NMS; a `ModelMetadata` kapott `trainGsdCmPx` mezőt; a `SamplingResultsActivity`
kapott „📏 GSD mérése" (futtatás előtt) és „⚙ Modell metaadat" (sidecar
létrehozás/szerkesztés, auto-fill a modellből) gombokat.

### 11.1 A megoldandó probléma — GSD-konzisztencia a betanítás és a futtatás közt

Egy YOLO objektumdetektor a **betanítási GSD-hez** kötött: ha 1 cm/px csempéken
tanult, a tövek egy adott **pixelméretben** jelennek meg számára, és csak akkor
detektál jól, ha a bemenet is ugyanabban a pixelméretben (GSD-ben) érkezik. A
teljes annotáció → tanítás → futtatás láncnak GSD-konzisztensnek kell lennie.

**A jelenlegi hiba (M09_L3 `YoloInferenceEngine.preprocess()`):** az egész
mintaképet (pl. 5472×3648) naivan a modell bemenetére (640×640) skálázza. Ez
két dolgot ront:
- **GSD-eltérés:** a mintakép a mintavétel miatt jellemzően **nagyobb
  felbontású** (kisebb GSD, pl. 0,3 cm/px) a betanító csempéknél (pl. 1 cm/px).
  Az egész kép 640-re nyomásával a tövek radikálisan más pixelméretben
  jelennek meg, mint tanításkor → a modell nem ismeri fel a méretet.
- **Csempézés hiánya:** a modell csempéken tanult és sliding window-val detektál;
  egyetlen agresszíven kicsinyített képen a kis (kelő) tövek eltűnnek.

(Az, hogy a kukoricánál mégis „jó" számot kaptunk, a **kézi konfidencia-hangolás**
műve — elfedi a skálahibát, de nem javítja; más magasságon/táblán újrahangolás
kellene.)

### 11.2 A helyes futtatási pipeline

```
Bemenet: mintakép (W×H px, actualGsd cm/px MÉRVE)
         modell (inputSize pl. 640, trainGsdCmPx pl. 1,0)

1. Átméretezési faktor a betanítási GSD-re (NEM 640-re):
      f = actualGsd / trainGsd            (pl. 0,3 / 1,0 = 0,3)
      → a kép f-szeresére kicsinyítve most trainGsd cm/px, mint a tanításnál
      újGsd = actualGsd / f = trainGsd    (ellenőrzés)
      resizedW = W × f,  resizedH = H × f

2. Sliding window csempézés a resized képen:
      inputSize×inputSize csempék, átfedéssel (pl. 20% → stride = inputSize×0,8)
      csempénként: preprocess (NCHW) → OrtSession.run() → detekciók a csempe
      koordinátáiban → visszavetítés a resized kép globális koordinátáira

3. Cross-tile NMS: a csempehatárokon átnyúló (két csempében megjelenő) tövek
      duplikátumainak kiszűrése — globális NMS a resized kép összes detekcióján

4. Count = a merge-elt detekciók száma
5. Density = count / footprint  (a footprint UGYANEZ a mért GSD-ből — §11.5)
```

### 11.3 A munkafolyamat átrendezése — GSD MÉRÉS ELŐBB

Mivel a GSD a **detektálást** befolyásolja (nem csak a sűrűséget), a mérésnek a
futtatás ELŐTT kell megtörténnie — nem az eredmény-sorra kattintva utólag (§10),
hanem a **betöltött mintaképeken**:

```
1. Session választás
2. Modell választás  (sidecar: inputSize, trainGsdCmPx, confThreshold, ...)
3. 📏 GSD MÉRÉSE (a futtatás ELŐTT) — egy reprezentatív mintaképen a
      vonalzóval (meglévő GsdRulerView): a felhasználó kihúz egy ismert
      vízszintes talaj-távolságot (pl. sortáv) → footprint szélesség →
         actualGsd_cmPerPx = footprintSzélesség_cm / eredetiKépSzélesség_px
      EXIF-fallback: ha nem mér, az EXIF-magasságból számolt GSD, de a UI
      jelzi, hogy a mért érték pontosabb (kereszt-ellenőrzés: §10.6)
4. [Számlálás indítása] — csak ha van érvényes GSD.
      GSD-tudatos inferencia a §11.2 szerint (resize a trainGsd-re + csempézés)
5. Eredmény: count, db/ha (footprint a mért GSD-ből), extrapoláció
```

A GSD-mérés így **egy méréssel kettős célt** szolgál (ahogy a felhasználó
megfogalmazta: „minden mindennel összefügg"): (a) a detektálási resize-skálát,
(b) a footprint-területet a sűrűséghez — konzisztensen.

### 11.4 A betanítási GSD (`trainGsdCmPx`) forrása

- **A modell sidecar `.json`-ja új mezőt kap:** `trainGsdCmPx` — milyen GSD-re
  méretezett csempéken tanult a modell. Ezt a **Dronterápia webes oldalon** kell
  a betanításkor rögzíteni és a modell-metaadatba tenni (külön egyeztetés tárgya).
- **Fallback a jelenlegi modellre:** a most használt `kukorica_640px` modellhez
  a betanítási GSD nem ismert; a felhasználó feltételezése **~1 cm/px**. Ha a
  sidecar-ból hiányzik a `trainGsdCmPx`, a rendszer **1,0 cm/px-t feltételez**,
  és a UI-ban **jelzi, hogy ez feltételezés** (a pontossághoz a valós betanítási
  GSD-t a modellbe kell írni).

### 11.4a Per-kép GSD-feloldás — saját / átlag / EXIF (2026-07-04, felhasználói logika)

A GSD-mérés **per-kép** tárolódik (nem session-szintű egyetlen érték). Egy adott
kép effektív GSD-je (a detektálási resize-hoz ÉS a footprinthez egyaránt):

| Helyzet | Az adott kép GSD-je |
|---------|---------------------|
| A képen **van** mérés | a **saját** mért GSD-je |
| A képen **nincs** mérés, de más képeken **van** | a **mért képek átlaga** |
| **Egyetlen** képen sincs mérés | **EXIF**-fallback (a sample magasságból) |

Példa (3 kép): 1 képen mérve → az a kép a saját, a másik 2 az (egyetlen)
mérést kapja (= átlag); 2 képen mérve → az a 2 a saját, a 3. a két mérés
átlagát; 3 képen mérve → mind a saját mérését. A felhasználó a „📏 GSD mérése"
gombbal többször is mérhet (más-más reprezentatív képen); a státusz mutatja,
hány kép van mérve és mennyi az átlag. Így a magasság pontonkénti eltérése
(barometrikus drift) képenként korrigálható, ahol a felhasználó mér, a többi
pedig a legjobb elérhető becslést (a mért képek átlaga) kapja.

Implementáció: `SamplingResultsActivity.measuredGsdByFile` (localFile → cm/px),
`resolveGsdForFile()`, `averageMeasuredGsd()`; az engine `runOnSession(...,
gsdByFile, fallbackGsd, ...)`-t kap, és képenként a saját vagy a fallback
GSD-vel méretez.

### 11.5 Kihat a korábbi döntésekre (revízió)

| Korábbi döntés | Revízió a §11 fényében |
|----------------|------------------------|
| M09_L2 §8a: „sliding window kimarad a v1-ből" | **Visszavonva** — a csempézés a GSD-konzisztencia miatt NEM opcionális, hanem a helyes detektálás feltétele, ha a mintakép GSD-je eltér a tanításitól |
| M09_L2 §10.4: „kalibráció a számlálás UTÁN, azonnali density-újraszámítással" | **Módosítva** — a GSD a **futtatás előtt** kell (a detektálást is befolyásolja); egy GSD-változás **újrafuttatást** igényel, nem csak density-újraszámítást. A footprint utólagos finomítása (density) megmaradhat, de a fő GSD-t előre mérjük |

### 11.6 Pragmatikus enyhítés — YOLO skála-tűrés

A YOLO némileg tűri a skálahibát (~±20-30%). Ezért:
- Ha a mért GSD az EXIF-hez közel van (kereszt-ellenőrzés kicsi eltérést mutat),
  az EXIF-GSD-vel futtatott inferencia is elfogadható — a mérés a biztonság.
- Ha nagy az eltérés (pl. 2×, a barometrikus hiba miatt), a mérés + a helyes
  resize elengedhetetlen, különben a detektálás romlik.

### 11.7 Teljesítmény-következmény (részletek: M09_L4)

A csempézés miatt egy kép **több inferenciát** jelent (pl. egy 0,3 cm-es kép
1 cm-re méretezve ~1642 px → ~6 csempe 640-nel → 6× inferencia/kép). A
Crystal Sky gyenge CPU-ján ez érdemi lassulás, de a mintavételnél kevés kép van
(20–50), és a helyes eredmény ezt megéri. Memória: egyszerre egy csempe
(M09_L4 §3 elve kiterjesztve).
