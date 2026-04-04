# QGIS Deepness — Gyengeségek, Megoldások és Iparági Alternatívák

**Dokumentum típusa:** Kompetitív elemzés + fejlesztési roadmap
**Elkészült:** 2026-03-07
**Utolsó frissítés:** 2026-03-30
**Forrásdokumentumok:** DEEPNESS_ISSUES_AND_SOLUTIONS.md, GSD_ADAPTIVE_SLIDING_WINDOW.md, GitHub Issues (PUTvision/qgis-plugin-deepness), iparági kutatás

---

## Tartalomjegyzék

1. [Kontextus](#i-kontextus-miért-releváns-számunkra)
2. [A Deepness ismert gyengeségei](#ii-a-deepness-ismert-gyengeségei--teljes-térkép)
3. [Mit csinál jól a Deepness?](#iii-mit-csinál-jól-a-deepness-hogy-fair-legyünk)
4. [Iparági megoldások és átvehető funkciók](#iv-iparági-megoldások-és-átvehető-funkciók)
5. [A három legfontosabb megoldatlan probléma](#v-a-három-legfontosabb-megoldatlan-probléma-nálunk-és-a-megoldási-irány)
6. [Összefoglaló táblázat](#vi-összefoglaló-deepness-vs-dronterapia-vs-iparág)
7. [Fejlesztési roadmap](#vii-javaslat-prioritizált-fejlesztési-roadmap)

---

## I. Kontextus: Miért releváns számunkra

A **QGIS Deepness** (PUTvision/qgis-plugin-deepness, Poznańi Műszaki Egyetem) volt az a referenciapont, amellyel szemben a **Dronterapia Counting modulját terveztük**. A plugin georeferált raszteres képekre futtat ONNX formátumú modelleket QGIS-en belül, sliding window módszerrel.

Saját `DEEPNESS_ISSUES_AND_SOLUTIONS.md` dokumentumunkban **9 ismert hibamintát** azonosítottunk a fejlesztés során. Ez az elemzés egyszerre retrospektív (*mit nem tud a Deepness?*) és előremutató (*mit csináljunk jobban mi is?*), kiegészítve az iparági megoldások kutatásával.

### A Deepness rövid jellemzője

| Jellemző | Érték |
|----------|-------|
| **Verzió** | 0.6.5 (2024 december); docs: 0.7.0 |
| **Licenc** | Apache 2.0 (nyílt forráskód) |
| **Platform** | QGIS desktop plugin |
| **Modell formátum** | ONNX Runtime |
| **Támogatott feladatok** | Szemantikus szegmentáció, objektumdetekció (YOLO), instance szegmentáció, regresszió, szuper-felbontás |
| **Model Zoo** | ~15 előre betanított modell (épületek, utak, mezőgazdaság, autók, hajók, fák) |
| **Betanítás** | ❌ Csak inferencia, nincs training |
| **GPU** | ⚠️ Elméletben igen, valójában csak Linux-on, törékenyen |

---

## II. A Deepness ismert gyengeségei — teljes térkép

### 🔴 Kritikus problémák

#### 1. GPU-inferencia: papíron van, valójában nem működik

**Forrás:** GitHub Issues [#192](https://github.com/PUTvision/qgis-plugin-deepness/issues/192), Discussion [#88](https://github.com/PUTvision/qgis-plugin-deepness/discussions/88)

A plugin az `onnxruntime-gpu==1.12.1` csomagot telepíti, amely pontosan egyező CUDA verziót igényel a rendszeren. Windows-on **csendesen visszaesik CPU-ra** anélkül, hogy ezt a felhasználónak jelezné.

- Egy 5000×5000 pixeles ortofotón: **80+ perc CPU-n vs. 61 másodperc GPU-val**
- Nincs UI jelzés arról, hogy CPU vagy GPU fut éppen
- GPU dokumentáció nem létezik
- A maintainerek válasza alapján GPU csak Linux-on tesztelt

> *A mi rendszerünkben ugyanez a probléma fennáll — szintén CPU-only, de ezt kommunikáljuk a felhasználó felé (progress bar, email értesítés).*

---

#### 2. Nincs betanítási loop — teljes eszközlánc-hiány

A Deepness **kizárólag inferencia**. A felhasználónak a következő lépéseket kell elvégeznie a Deepness-en kívül:

```
Külső annotáció       Külső training       ONNX export      Deepness
(CVAT / Label Studio) → (PyTorch / TF) → (torch.onnx) → Inferencia
```

Ez egy agrónómus vagy ökológus számára **elérhetetlen belépési küszöb**.

> *Ez a Dronterapia legfontosabb versenyelőnye: egy platformon belül van az*
> *Annotation → Training → Counting teljes pipeline.*

---

#### 3. UInt16 (16 bites) multispektrális input nem támogatott

**Forrás:** GitHub Issue [#219](https://github.com/PUTvision/qgis-plugin-deepness/issues/219)

A drónos multispektrális kamerák (MicaSense RedEdge, Parrot Sequoia, DJI P1) szinte kizárólag **16 bites képet** adnak. A Deepness ezeket nem tudja beolvasni — ez az agrár szegmens teljes multispektrális ágát blokkolta.

Egyéb input-korlátok:
- Egysávos raszter nem támogatott szegmentációban (Issue [#125](https://github.com/PUTvision/qgis-plugin-deepness/issues/125))
- Normalizáció [0,1]-re hardcoded, nem konfigurálható (Issue [#175](https://github.com/PUTvision/qgis-plugin-deepness/issues/175))

> *Nálunk is hiányzik a 16 bites multispektrális support. A GSD_ADAPTIVE_SLIDING_WINDOW.md szerint NIR/RedEdge sáv kezelés tervezett.*

---

#### 4. Folyamat nem megszakítható

**Forrás:** GitHub Issue [#230](https://github.com/PUTvision/qgis-plugin-deepness/issues/230)

Nincs **abort gomb**. Ha elindult egy inferencia és valami nem stimmel (pl. rossz modell, túl sok tile), a felhasználónak force-quit-elni kell a QGIS-t. Production-use esetén elfogadhatatlan.

Kapcsolódó: QGIS crash-ek feldolgozás közben (Issue [#229](https://github.com/PUTvision/qgis-plugin-deepness/issues/229), megoldatlan 2025 december óta).

> *Nálunk is ez a helyzet — a Streamlit egyszálú futtatási modellje ugyanezt a problémát hozza. A `▶️ Predikció futtatása` gomb után nincs leállítási lehetőség.*

---

#### 5. Egységes globális confidence threshold — nincs adaptáció

**Saját dokumentum hivatkozás:** DEEPNESS_ISSUES_AND_SOLUTIONS.md §5 — `KÖZEPES PRIORITÁS, MEGOLDATLAN`

Egyetlen globális confidence küszöb az egész képre. A valóságban:

```
Árnyékos terület    → alacsonyabb confidence, de valódi tövek vannak
Fényes, visszaverő  → magas confidence, de false positive-ok
Sorköz              → zaj, amit szűrni kellene
```

Nincs lokális adaptáció, nincs uncertainty térkép, nincs automatikus threshold-javaslat.

> *A mi rendszerünk jelenleg szintén globális slider-t alkalmaz. A preprocessing preset (ExGreen, VARI stb.) részlegesen kompenzálja, de nem old meg minden esetet.*

---

#### 6. Csempehatár-effektus és nem-intelligens NMS

**Saját dokumentum hivatkozás:** DEEPNESS_ISSUES_AND_SOLUTIONS.md §3, §4

Két összefüggő probléma:

**a) Csempehatár-effektus (edge blindness):**
- Kemény vágás az ablak szélén → confidence leesés, hiányzó detekciók a határon

**b) Biológiai NMS hiánya:**
- IoU-alapú box-összevonás nem tudja megkülönböztetni:
  - "Ez ugyanaz a biológiai tő" → össze kell vonni
  - "Ez két különböző, de közeli tő" → mindkettőt meg kell tartani
- Eredmény: sűrű növényállományban alulámlálás, vagy duplikátumok

> *Nálunk a 25%-os overlap + globális NMS megoldja a csempehatár-effektust ✅.*
> *A biológiai NMS azonban megoldatlan marad 🔴 — ez az egyik három legfontosabb hiányosságunk.*

---

#### 7. Nincs headless/CLI/batch mód

Headless (GUI nélküli) batch feldolgozást megkérdőjeleztek a maintainerek, de csak a roadmapen szerepel. Következmények:

- Nem illeszthető automatizált pipeline-ba
- Nem ütemelhető (pl. éjszakai feldolgozás)
- Minden futtatáshoz QGIS GUI-t kell nyitni
- Szerver-oldali deployment nem lehetséges

> *Nálunk ugyanez a korlát. A Streamlit szerver futtatható headless-en, de a Counting oldal interakciót igényel.*

---

#### 8. Telepítési fragilitás

28 nyitott issue vonatkozik installációs problémákra:

| Platform | Probléma |
|----------|----------|
| macOS Sequoia | Telepítés sikertelen (Issue [#209](https://github.com/PUTvision/qgis-plugin-deepness/issues/209), [#180](https://github.com/PUTvision/qgis-plugin-deepness/issues/180)) |
| Windows | `No module named 'cv2'` (Issue [#202](https://github.com/PUTvision/qgis-plugin-deepness/issues/202)), plugin settings hiba (Issue [#197](https://github.com/PUTvision/qgis-plugin-deepness/issues/197)) |
| Linux | CUDA provider hiba Ubuntu 22.04 + megfelelő CUDA telepítés esetén is |
| Általános | Package validation hibák QGIS install-nál (Issue [#193](https://github.com/PUTvision/qgis-plugin-deepness/issues/193)) |

---

#### 9. Nincs idősoros / change detection funkció

A Deepness egyetlen képre korlátozott, statikus elemzést végez. Teljesen hiányzik:

- Két időpontban készült felvétel összehasonlítása
- Növényzeti változás (NDVI trend) követése
- Multi-date analízis
- Anomália riasztás (ha valami megváltozott)

---

### 🟡 Közepes problémák — saját dokumentációval keresztellenőrizve

| Probléma | Deepness | Dronterapia státusz |
|----------|----------|---------------------|
| Tile size ↔ objektumméret arány | Nincs adaptáció | ✅ Megoldva (64–2048px + warning) |
| Kontextusvesztés hosszú mintázatoknál (sorok) | Nincs sor-felismerés | ⚠️ Megoldatlan — Hough tervezett |
| GIS-szintű affine torzítások | Nincs kompenzáció | ⚠️ Részleges (GeoTIFF CRS olvasás) |
| Model architektúra support (RetinaNet, FasterRCNN) | Nem támogatott | ⚠️ Szintén csak YOLO |
| Multi-scale inference | Nincs | ⚠️ Tervezett |
| Test-time augmentation | Nincs | ⚠️ Tervezett |
| Uncertainty becslés | Nincs | ⚠️ Tervezett |
| Biológiai NMS (távolságalapú) | Nincs | ✅ Implementált (🧬 Haladó NMS beállítások) |
| Adaptive confidence | Nincs | ✅ Implementált (🧬 Haladó NMS beállítások) |
| CRS kezelés dokumentált | Kérdéses (Issue [#211](https://github.com/PUTvision/qgis-plugin-deepness/issues/211)) | ✅ Dokumentált |

---

### 🟢 Kisebb problémák

| Probléma | Megjegyzés |
|----------|------------|
| Pixelhatár eltolás (+1 px két élen) | Issue [#123](https://github.com/PUTvision/qgis-plugin-deepness/issues/123), maintainer által nyitva tartott 2023 óta |
| "Recognition" modell teljesen dokumentálatlan | Issue [#213](https://github.com/PUTvision/qgis-plugin-deepness/issues/213) |
| Nincs felhő-integráció | Lokális desktop only |
| Nincs csapatalkalmazás | Egy felhasználó, egy gép |

---

## III. Mit csinál jól a Deepness? (Hogy fair legyünk)

- Az egyetlen **ingyenes, nyílt forráskódú** QGIS-natív megoldás georeferált rasteres ONNX inferenciára
- **Adatszuverenitás:** semmi sem kerül ki a felhasználó gépéről
- A **tile export eszköz** valóban hasznos training adatkészlet-készítéshez
- A model zoo ~15 modellje lefed valós remote sensing use case-eket (épületek, utak, hulladék, fák, olajvezetékek)
- **Super-resolution** funkció (2x/3x/4x) nincs máshol QGIS-ben ingyenesen
- A plugin-architektúra QGIS-be épülve **teljes GIS workflow-ba illeszthető** (rétegek, CRS, projektek)

---

## IV. Iparági megoldások és átvehető funkciók

### DroneDeploy — cloud-alapú, end-to-end platform

**Célközönség:** Építőipar, napelem, mezőgazdaság, infrastruktúra-inspekció
**Modell:** SaaS előfizetés

| Funkció | Deepness | DroneDeploy | Átvehető? |
|---------|----------|-------------|-----------|
| Cloud feldolgozás | ✗ | ✅ | ✗ (helyi feldolgozás előny nálunk) |
| AI agents (Progress AI, Safety AI) | ✗ | ✅ | ⚠️ Inspiráció: domain-specifikus eredmény-értelmezés |
| Több felhasználó, kollaboráció | ✗ | ✅ | 🔜 Multi-user funkció tervezett |
| Automatikus repülés-tervezés | ✗ | ✅ | ✗ (nem releváns) |
| Defect severity ranking | ✗ | ✅ | ⚠️ Inspiráció: detekciók automatikus osztályozása súlyosság szerint |

**Átvehető ötlet:** A detekciós eredmények automatikus **severity/confidence-alapú kategorizálása** (pl. "kritikus területek" kiemelése a térképen) — ez nálunk is megvalósítható a CountingCalculator kibővítésével.

---

### Pix4Dfields — agrár-specifikus feldolgozás

**Célközönség:** Precíziós mezőgazdaság
**Modell:** SaaS + offline edge device feldolgozás

| Funkció | Deepness | Pix4Dfields | Átvehető? |
|---------|----------|-------------|-----------|
| Multispektrális (UInt16) | ✗ | ✅ | 🔴 Kritikus hiányosság nálunk is |
| NDVI, NDRE, GNDVI automatikus | ✗ | ✅ | ✅ Részben megvan (spectral_indices.py) |
| Prescription map (szántóföldi beavatkozási térkép) | ✗ | ✅ | 🟡 Megvalósítható post-processing lépéssel |
| Weed detection workflow | ✗ | ✅ | 🟡 Speciális modell + sor-alapú post-processing |
| Offline edge processing | ✗ | ✅ | ✅ Megvan — Streamlit offline futtatható |
| Biológiai NMS / sor-detekció | ✗ | ✅ | ✅ Implementált |

**Átvehető ötlet — Prescription map:**
A Counting eredményből közvetlenül generálható egy zónatérkép:
```
Kis sűrűségű terület   → "Pótlás szükséges" zóna (piros)
Normál sűrűség         → "OK" zóna (zöld)
Túlsűrű terület        → "Ritkulás várható" zóna (narancssárga)
```
Ez CSV/GeoJSON-ból automatikusan lekérdezhető és exportálható.

---

### ArcGIS Pro + arcgis.learn — ML pipeline GIS-ben

**Célközönség:** Enterprise GIS, önkormányzat, közüzem
**Modell:** Licencdíjas desktop + ArcGIS Online

| Funkció | Deepness | ArcGIS Pro | Átvehető? |
|---------|----------|------------|-----------|
| Training GIS-en belül | ✗ | ✅ | ✅ Megvan nálunk (Training modul) |
| Mask R-CNN, RetinaNet support | ✗ | ✅ | ⚠️ ONNX-on keresztül megvalósítható |
| SAM integráció (DLPK) | ✗ | ✅ | 🟡 Annotation oldalon releváns |
| GPU training (PyTorch natív) | ✗ | ✅ | 🔜 GPU support tervezett |
| Python API (headless automation) | ✗ | ✅ | 🟡 CLI mód tervezett |
| Uncertainty estimation | ✗ | ✅ | 🟢 Nice-to-have |

**Átvehető ötlet — SAM pre-annotation:**
Az ArcGIS-ben a Segment Anything Model egy kattintásra auto-annotálja az objektumokat. Az Annotation oldalunkon jelenleg nincs ilyen — egy SAM-alapú "kattints a tőre → automatikus polygon" funkció **3–5×-re gyorsítaná a szegmentációs annotálást**.

---

### Roboflow — annotáció + training + deployment platform

**Célközönség:** Computer vision fejlesztők, kutatók
**Modell:** Freemium SaaS

| Funkció | Deepness | Roboflow | Átvehető? |
|---------|----------|----------|-----------|
| AI-asszisztált annotáció (SAM-alapú) | ✗ | ✅ | 🟡 Annotation oldalon |
| Automatikus adataugmentáció | ✗ | ✅ | ✅ Részben megvan (Training haladó beállítások) |
| Roboflow Workflows (pipeline láncolás) | ✗ | ✅ | 🟡 Counting post-processing pipeline |
| Universe model repository (1000+ modell) | ✗ (15 db zoo) | ✅ | 🟢 Model Registry bővítése |
| Hosted Inference API (HTTP POST) | ✗ | ✅ | 🟢 API endpoint hosszú távon |
| Edge deployment (Docker) | ✗ | ✅ | 🟢 Containerizáció |
| YOLO26 support (2026) | ⚠️ Részleges | ✅ | ⚠️ ONNX-on keresztül |
| Abort / process control | ✗ | ✅ | 🔴 Azonnali fejlesztés |

**Átvehető ötlet — Roboflow Workflows:**
Vizuális, kompozálható inferencia-pipeline: modellek és post-processing lépések láncolhatók. Nálunk ez azt jelentené, hogy a Counting eredmény automatikusan továbblép pl.:
```
Predikció → NMS → Biológiai szűrés → Sor-detekció → Prescription map → GeoJSON export
```
...egy konfigurálható, vizuális pipeline-ban.

---

### FlyPix AI — geospatial AI platform

**Célközönség:** Drón/műhold elemzés, mezőgazdaság, kormányzat
**Modell:** SaaS

| Funkció | Deepness | FlyPix AI | Átvehető? |
|---------|----------|-----------|-----------|
| No-code custom model training | ✗ | ✅ | ✅ Megvan nálunk |
| Multi-sensor (hyperspectral, LiDAR, SAR) | ✗ | ✅ | 🟢 Hosszú távú |
| Heatmap és anomália-követés | ✗ | ✅ | 🟡 Uncertainty heatmap |
| Change tracking (két időpont) | ✗ | ✅ | 🟢 Idősor fejlesztés |
| Multi-user, role-based access | ✗ | ✅ | 🔜 Multi-user tervezett |

---

### QGIS-natív alternatívák (direkt versenytársak a plugin térben)

Ezek nem helyettesítik a Dronterapiát, de jelzik, hogy a QGIS ökoszisztéma hova tart:

| Plugin | Kulcsfunkció | Relevancia számunkra |
|--------|-------------|---------------------|
| **GeoOSAM** | SAM 2.1 + SAM3, text prompt, GPU auto-select, 5–10× gyorsabb CPU-n | Annotation: SAM-alapú polygon auto-generálás |
| **Geo-SAM** | Interaktív pont-kattintás szegmentáció milliszekundumos válaszidővel | Annotation: kattintós pre-annotation |
| **SamGeo** | Szöveg-prompt ("water bodies", "buildings") → raszteres keresés | Annotáció: "kattints és leírd" |
| **GeoAI** | SAM1/2/3, Mask R-CNN, DeepForest (fa-detekció), Moondream (vision-language kérdezés) | Counting: multimodális elemzés |

**Következtetés:** A QGIS közösség a foundation modelek (SAM, CLIP, vision-language) integrációja felé halad. A Deepness ebből teljesen kimaradt, mi sem integráltunk ilyeneket még.

---

## V. A három legfontosabb megoldatlan probléma nálunk és a megoldási irány

*(A saját DEEPNESS_ISSUES_AND_SOLUTIONS.md prioritási listájából kiemelve, iparági best practice-szel kiegészítve)*

---

### 🔴 1. Biológiai NMS (Distance-based NMS)

**Probléma részletezve:**
A hagyományos IoU-alapú NMS geometriailag gondolkodik, nem biológiailag. Ha két kukoricatő egymáshoz közel van (pl. 12 cm sortávolságon belül), a boxaik átfednek → az NMS eldobja az egyiket. Eredmény: **alulámlálás sűrű állományban**, ami akár 15–30%-os hibát okoz.

Fordított eset: Ha egy nagy tő két részre detektálódott (pl. a csempehatáron), és a két box nem fed át eléggé → mindkettő megmarad → **túlámlálás**.

**Megoldási irány — GSD-kalibrált tőtávolság-alapú NMS:**

```python
# Pszeudokód — biológiai NMS
def biological_nms(detections, gsd_cm_per_pixel, min_plant_spacing_cm):
    """
    Ha két detekció középpontja közelebb van mint a várható minimális
    tőtávolság, összeolvadnak (azonos tőnek tekintjük).
    Ha messzebb — mindkettőt megtartjuk (két külön tő).
    """
    min_distance_px = min_plant_spacing_cm / gsd_cm_per_pixel

    centers = [(d.cx, d.cy) for d in detections]
    # Páronkénti euklidészi távolság < min_distance_px → merge (a magasabb confidence-ű marad)
    # Páronkénti távolság >= min_distance_px → mindkettő marad
```

**Konfigurálható paraméter a Counting oldalon:**
```
Várható tőtávolság (cm): [18] ─●──────── 50
  Kukorica: 18–22 cm | Napraforgó: 25–30 cm | Repce: 15–18 cm
```

**Iparági referencia:** Pix4Dfields per-crop spacing kalibráció, Roboflow per-class distance threshold

---

### 🔴 2. Adaptive Confidence Threshold

**Probléma részletezve:**
Egységes confidence küszöb az egész képre. Valós drónfelvételen:

| Terület típus | Probléma |
|---------------|----------|
| Árnyékos foltok (felhő, fa árnyék) | Alacsonyabb confidence → false negative |
| Visszaverő (nedves talaj, reflexió) | Magas noise confidence → false positive |
| Sorköz (semmi sem nő ott) | Zaj, amit az alacsony küszöb átenged |
| Növényzetmentes terület (dűlőút) | False positive-ok |

**Megoldási irány — Tile-szintű brightness/variance alapú adaptáció:**

```python
def get_adaptive_threshold(tile_array, global_threshold):
    """
    Tile-szintű fényesség és szórás alapján módosítja a confidence küszöböt.
    """
    mean_brightness = tile_array.mean() / 255.0
    variance = tile_array.std() / 255.0

    if mean_brightness < 0.3:   # Sötét terület (árnyék)
        return max(0.05, global_threshold - 0.08)
    elif mean_brightness > 0.8: # Nagyon fényes (visszaverődés)
        return min(0.95, global_threshold + 0.10)
    elif variance < 0.05:       # Egyenletes, valószínűleg üres terület
        return min(0.95, global_threshold + 0.15)
    else:
        return global_threshold
```

**Alternatív megközelítés:** A **preprocessing preset** (ExGreen, VARI stb.) alkalmazása inferencia előtt normalizálja a kontraszt-különbségeket — ez nálunk már részben megvan, és indirekt módon csökkenti az adaptív threshold szükségességét.

**Iparági referencia:** Pix4Dfields adaptive thresholding per image zone; FlyPix anomaly zone detection

---

### 🔴 3. Sor-detekció és kontextus-tudatos post-processing

**Probléma részletezve:**
A YOLO lokálisan lát — egy tile-on belüli mintázatokat. Az agrár képeken azonban a növények **sorokba szerveződnek**, és ez globális struktúra. A sliding window szétszabdalja ezt a struktúrát → a YOLO soronként csak töredékeket lát → hibás sűrűség-becslés.

Eredmény:
- Sorközökben false positive-ok (talaj-textúra tő-szerű megjelenés)
- Sorok mentén hiányzó detekciók a sorhatáron
- "Phantom" tövek a kép szélein, ahol a sor nem teljes

**Megoldási irány — Hough Transform-alapú sor-felismerés:**

```python
def detect_crop_rows(detections, image_height, image_width):
    """
    A detektált középpontokból Hough-egyeneseket illeszt,
    majd az egyenestől túl messze lévő detekciók valószínűleg zaj.
    """
    centers = np.array([(d.cx, d.cy) for d in detections])

    # Hough-egyenes illesztés a középpontokra
    lines = cv2.HoughLines(centers_as_binary_image, rho=1, theta=np.pi/180, threshold=5)

    # Minden detekció: mekkora a távolsága a legközelebbi sorhoz?
    for det in detections:
        dist_to_nearest_row = min_distance_to_lines(det.center, lines)
        if dist_to_nearest_row > MAX_ROW_DEVIATION_PX:
            det.confidence *= 0.5  # Csökkentett confidence, nem törlés
```

**Konfigurálható paraméter:**
```
Sor-szűrés erőssége: Kikapcsolva / Enyhe / Erős
Várható sor-irány (°): [0°=vízszintes, 90°=függőleges, Auto]
```

**Iparági referencia:** Pix4Dfields crop row following, FlyPix row-based analysis; RANSAC-alapú megközelítés (precíziósabb, de lassabb)

---

## VI. Összefoglaló: Deepness vs. Dronterapia vs. Iparág

```
Funkcióterület                   Deepness   Dronterapia   Roboflow   Pix4Dfields   ArcGIS Pro
──────────────────────────────────────────────────────────────────────────────────────────────
Annotáció                          ✗            ✅            ✅           ✗              ✗
SAM-alapú pre-annotáció            ✗            ✗             ✅           ✗              ✅
Training                           ✗            ✅            ✅           ✗              ✅
ONNX inferencia                    ✅            ✅            ✅           ✅             ✅
GPU inferencia                     ⚠️ törött    ✗             ☁️          ✅             ✅
Multispektrális (16 bit UInt16)    ✗            ✗             ✗            ✅            ✅
GSD-tudatos feldolgozás            ✗            ✅            ✗            ✅            ✗
Sliding window + overlap           ✅            ✅            ✗            ✅            ✗
Biológiai NMS                      ✗            ✅            ✗            ✅            ✗
Adaptive confidence                ✗            ✅            ✗            ✅            ✗
Sor-detekció (crop row)            ✗            ✅            ✗            ✅            ✗
Abort / process control            ✗            ✅            ✅           ✅            ✅
Headless / CLI / batch mód         ✗            ✗             ✅           ✅            ✅
Prescription map (agrár output)    ✗            ✅            ✗            ✅            ✗
Idősor / change detection          ✗            ✗             ✗            ✗             ⚠️
Foundation model (SAM, CLIP)       ✗            ✗             ✅           ✗             ✅
Uncertainty heatmap                ✗            ✗             ✗            ✗             ✅
Multi-user kollaboráció            ✗            ✗             ✅           ✅            ✅
Open source / ingyenes             ✅            ✅            ⚠️ freemium  ✗             ✗
Adatszuverenitás (offline)         ✅            ✅            ✗            ✅            ✗
```

**Legenda:** ✅ Megvan | ⚠️ Részleges/törött | ✗ Nincs | ☁️ Cloud only

---

## VII. Javaslat: Prioritizált fejlesztési roadmap

### 🔴 Azonnali fejlesztések (1–2 sprint)

| # | Feature | Komplexitás | Iparági referencia | Várható hatás |
|---|---------|-------------|-------------------|---------------|
| 1 | **Biológiai NMS** — GSD-alapú tőtávolság konfigurálható paraméterrel | ✅ KÉSZ | Pix4Dfields | Sűrű állományban 15–30%-os alulámlálás megszűnik |
| 2 | **Adaptive confidence** — tile brightness-alapú küszöb-módosítás | ✅ KÉSZ | Pix4Dfields | Árnyékos területen kevesebb hiányzó detekció |
| 3 | **Abort gomb** — inferencia megszakítása | ✅ KÉSZ | Roboflow | UX alapelvárás, production-use blokker |

### 🟡 Középtávú fejlesztések (következő 1–2 hónap)

| # | Feature | Komplexitás | Iparági referencia | Várható hatás |
|---|---------|-------------|-------------------|---------------|
| 4 | **SAM-alapú pre-annotation** az Annotation oldalon | ~5 nap | Roboflow Annotate, GeoOSAM | 3–5× gyorsabb szegmentációs annotálás |
| 5 | **Sor-detekció** — PCA/SVD alapú post-processing | ✅ KÉSZ (2026-03-29) | Pix4Dfields | False positive szűrés sorközi területen |
| 6 | **Prescription map export** — zónatérkép CSV/GeoJSON | ✅ KÉSZ (2026-03-27) | Pix4Dfields | Közvetlen agrár döntéstámogatás |
| 7 | **Multiprocessing** — párhuzamos tile-feldolgozás | ~3 nap | Belső | 2–4× gyorsabb CPU inferencia |

### 🟢 Hosszú távú fejlesztések (negyedéves roadmap)

| # | Feature | Komplexitás | Iparági referencia | Várható hatás |
|---|---------|-------------|-------------------|---------------|
| 8 | **Model Library / Universe** — közösségi modell megosztás | ~5 nap | Roboflow Universe | Platform hálózati hatás |
| 9 | **Headless / CLI mód** — automatizált pipeline support | ~3 nap | Roboflow, ArcGIS | Integráció külső rendszerekbe |
| 10 | **UInt16 multispektrális support** — MicaSense, Sequoia | ~4 nap | Pix4Dfields | Drón multispektrális piaci szegmens |
| 11 | **Uncertainty heatmap** — vizualizáció a bizonytalanságról | ~2 nap | ArcGIS arcgis.learn | Minőségbiztosítás |
| 12 | **Idősor / change detection** — két kép összehasonlítása | ~5 nap | FlyPix AI | Szezonális monitoring |

---

## Hivatkozások

### Deepness GitHub
- [Repository](https://github.com/PUTvision/qgis-plugin-deepness)
- [Dokumentáció](https://qgis-plugin-deepness.readthedocs.io/)
- [QGIS Plugin Page](https://plugins.qgis.org/plugins/deepness/)
- [Tudományos cikk (SoftwareX 2023)](https://www.sciencedirect.com/science/article/pii/S2352711023001917)

### Deepness kritikus GitHub Issues
- [#192 — GPU nem detektálva](https://github.com/PUTvision/qgis-plugin-deepness/issues/192)
- [#219 — UInt16 nem támogatott](https://github.com/PUTvision/qgis-plugin-deepness/issues/219)
- [#230 — Nincs abort gomb](https://github.com/PUTvision/qgis-plugin-deepness/issues/230)
- [#207 — YOLOv11 support kérdéses](https://github.com/PUTvision/qgis-plugin-deepness/issues/207)
- [#125 — Egysávos raszter nem támogatott](https://github.com/PUTvision/qgis-plugin-deepness/issues/125)

### Iparági megoldások
- [DroneDeploy AI at DroneDeploy](https://www.dronedeploy.com/product/ai-at-dronedeploy)
- [Pix4Dfields](https://www.pix4d.com/product/pix4dfields)
- [ArcGIS Pro — Deep Learning](https://pro.arcgis.com/en/pro-app/latest/help/analysis/deep-learning/what-is-deep-learning-.htm)
- [Roboflow Deploy](https://roboflow.com/deploy)
- [FlyPix AI Platform](https://flypix.ai/platform/)

### QGIS-natív alternatívák
- [GeoOSAM plugin](https://plugins.qgis.org/plugins/GeoOSAM/)
- [GeoAI plugin](https://plugins.qgis.org/plugins/geoai/)
- [SamGeo plugin](https://plugins.qgis.org/plugins/samgeo/)

### Belső dokumentumok
- `DEEPNESS_ISSUES_AND_SOLUTIONS.md` — 9 Deepness hibaminta és megoldásaik
- `GSD_ADAPTIVE_SLIDING_WINDOW.md` — GSD-tudatos sliding window technikai spec
- `docs/M03_COUNTING/M03_L3_ALLAPOTGEP_ES_ENGINE.md` — Counting engine állapotgép
