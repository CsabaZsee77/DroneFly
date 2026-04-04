# Model Registry és Universe — Elemzés és Fejlesztési Irányok

**Dokumentum típusa:** Kompetitív elemzés + funkció-specifikáció
**Elkészült:** 2026-03-07
**Kapcsolódó dokumentumok:** DEEPNESS_COMPETITIVE_ANALYSIS.md, docs/M05_MODELL_NYILVANTARTAS/

---

## Tartalomjegyzék

1. [Kiindulópont: Mi a probléma?](#i-kiindulópont-mi-a-probléma)
2. [A mi jelenlegi implementációnk](#ii-a-mi-jelenlegi-implementációnk)
3. [Iparági model repository megoldások](#iii-iparági-model-repository-megoldások)
4. [Összehasonlítás](#iv-összehasonlítás)
5. [Mit vegyünk át? — Prioritizált javaslatok](#v-mit-vegyünk-át--prioritizált-javaslatok)
6. [Technikai megvalósítási terv](#vi-technikai-megvalósítási-terv)

---

## I. Kiindulópont: Mi a probléma?

A Dronterapia Model Registry jelenleg **zárt, lokális rendszer**: csak azokat a modelleket tartalmazza, amelyeket a felhasználó maga tanított be, vagy maga töltött fel. Nincs lehetőség:

- más felhasználók által betanított, jól működő modelleket felfedezni és megszerezni
- a saját modellt megosztani a közösséggel (a "nyilvános" láthatóság csak ugyanazon a szerverpéldányon belül érvényes)
- külső forrásból (Roboflow Universe, Hugging Face, Deepness Model Zoo) közvetlenül ONNX modellt importálni

A **DEEPNESS_COMPETITIVE_ANALYSIS.md** dokumentumban azonosított hiányosságok egyike:

> *Universe model repository: Ezres nagyságrendű közösségi modell, kereshető. Nálunk: A Model Registry jelenleg csak a saját modelleket tartalmazza.*

Ez a dokumentum ezt a pontot fejti ki részletesen.

---

## II. A mi jelenlegi implementációnk

### Architektúra áttekintés

| Réteg | Megvalósítás |
|-------|-------------|
| **Tárolás** | `models/model_registry.json` + ONNX fájlok a `models/` mappában |
| **Formátum** | Kizárólag ONNX (`.onnx`), PyTorch weights (`.pt`) training-hez |
| **Felfedezés** | Fájlrendszer scan + registry JSON olvasás |
| **Cache** | `@st.cache_resource` mtime-alapú invalidálással |
| **API** | Nincs REST API — csak Streamlit UI |
| **Jelenlegi modellek** | ~10 modell (saját betanítások + feltöltések) |

### Egy modell metaadatstruktúrája (`model_registry.json`)

```json
{
  "id": "uuid-v4",
  "file_name": "model_2026_01_15_1430_a1b2c3d4.onnx",
  "display_name": "Kukorica - YOLOv11s v3",
  "version": "1.0",
  "visibility": "private" | "public",
  "owner": {
    "user_id": "uuid-v4",
    "username": "zsigmond"
  },
  "created_at": "2026-01-15T14:30:00",
  "technical": {
    "yolo_version": "YOLOv11",
    "model_size": "s",
    "input_size": 640,
    "classes": [
      { "id": 0, "name": "kukorica_to", "avg_bbox_size_cm2": null }
    ],
    "mAP50": 0.923,
    "precision": 0.91,
    "recall": 0.88,
    "training_images": 360
  },
  "preprocessing_preset": "exg",
  "tags": ["kukorica", "barna_talaj", "száraz"],
  "description": "...",
  "ratings": { "average": 0.0, "count": 0, "distribution": {...} },
  "reviews": [],
  "stats": { "usage_count": 5, "unique_users": 0 },
  "sample_images": ["models/samples/..._sample1.jpg"]
}
```

### Jelenleg működő funkciók

✅ Saját modellek listázása és kiválasztása (Counting oldal)
✅ Nyilvános modellek megosztása ugyanazon a szerverpéldányon belül
✅ Modell feltöltés (külső ONNX file) a Counting oldalon
✅ Modell törlés (csak tulajdonos)
✅ Csillagozás és review mezők (`ratings`, `reviews`) — **adatstruktúra létezik, de UI nem mutatja**
✅ Használati statisztika (`usage_count`) — **számlálódik, de nem jelenik meg**
✅ Mintaképek (5 db thumbnail modellenkent)
✅ Kalibrálható osztályparaméterek (`avg_bbox_size_cm2`, `cluster_threshold`)

### Jelenlegi hiányosságok

❌ Nincs REST API — programmatikus elérés nem lehetséges
❌ A "nyilvános" modell csak helyi szerverpéldányon belül látható — valódi megosztás nem létezik
❌ Nincs keresés, szűrés a modelleken (tag, növény, mAP, osztály szerint)
❌ A `ratings` és `reviews` mezők kitöltve vannak, de az UI nem jeleníti meg őket
❌ Nincs modell letöltés / exportálás más felhasználónak
❌ Külső forrásokból (Roboflow, Hugging Face, Deepness Zoo) nem importálható modell
❌ Nincs versioning — egy modellnek nincs v1 → v2 → v3 lineáris leszármazása
❌ Nincs "team" láthatóság — csak private / public
❌ Sample image-ek feltöltött modelleknél placeholder-esek (nem valódi training sample)

---

## III. Iparági model repository megoldások

### 1. Roboflow Universe — universe.roboflow.com

**Skála:** 250 000+ dataset és modell (a világ legnagyobb nyílt CV repository-ja)

#### Hogyan működik?

A Roboflow Universe a Roboflow platform **közösségi modelltára**. Bárki, aki Roboflow-on annotál és tanít modellt, egy kattintással publikálhatja azt. A folyamat:

```
Roboflow Annotate → AutoML Training → "Publish to Universe" → Azonnal kereshető
```

Egy Universe modell oldalán megtalálható:
- Modell neve, leírása, licenc
- Osztályok listája, példa detekciók képeken
- mAP, Precision, Recall metrikák
- Download gomb (ONNX, PyTorch, TFLite, CoreML, OpenVINO stb.)
- REST API hívás kódrészlet (HTTP POST, 3 sor Python)
- "Fork" gomb — a dataset klónozható saját projektbe

#### Keresés és szűrés

| Szűrő | Értékek |
|-------|---------|
| Task type | Detection, Segmentation, Classification, Keypoint |
| Domain tag | agriculture, aerial, drone, satellite, medical... |
| Dataset méret | képszám szerinti sávok |
| Licenc | CC BY, MIT, proprietary |
| Model teljesítmény | mAP szerint rendezhető |

Full-text keresés modellnévre, leírásra, tag-ekre.

#### Modell formátumok

YOLO (v5/v8/v9/v11), **ONNX**, TensorFlow Lite, TensorFlow SavedModel, PyTorch, CoreML, OpenVINO, PaddlePaddle — **egy modellt egyszerre több formátumban exportál**.

#### API

```python
# Hosted inference — 3 sor, REST API-n
import requests
result = requests.post(
    "https://detect.roboflow.com/my-model/1?api_key=KEY",
    files={"file": open("image.jpg", "rb")}
).json()

# SDK
from roboflow import Roboflow
rf = Roboflow(api_key="KEY")
model = rf.workspace().project("corn-detection").version(1).model
model.predict("field.jpg").save("result.jpg")
```

#### Minőségbiztosítás

- Nincs kötelező technikai review
- mAP, képszám, license-transzparencia mint minőségjelző
- Roboflow staff "Featured" szekció kurál
- Community: like, download count mint minőségszignál

#### Árazás

| Csomag | Ár | Limit |
|--------|----|-------|
| Ingyenes | $0 | 1 000 API hívás/hó, 3 privát projekt |
| Growth | ~$249/hó | Több API hívás, csapatalkalmazás |
| Enterprise | Egyedi | SLA, SSO |

**Publikus modellek letöltése: ingyenes, korlátlan.**

---

### 2. QGIS Deepness Model Zoo

**Skála:** ~20–50 kézzel kurált modell

#### Hogyan működik?

A Deepness Model Zoo egy **dokumentációs oldalon lévő táblázat** (`readthedocs.io`), amely modell neveket, leírásokat és Google Drive / GitHub letöltési linkeket tartalmaz. Nincs webUI, nincs keresés, nincs API.

Egy modell Zoo-bejegyzés:
```
Modell: Corn field damage detection
Architektúra: UNet++
Osztályok: healthy_crop, damaged_crop, out_of_field
Felbontás: 512×512 px
Letöltés: [Google Drive link]
Forrás: PUT Vision Lab, 2023
```

#### Hozzájárulás mechanizmusa

1. Felhasználó ONNX modellt készít
2. GitHub Pull Request-et nyit a Deepness repo-ban
3. Maintainerek manuálisan reviewolják és merge-elik
4. A modell megjelenik a docs oldalon

Ez a **legszigorúbb minőségbiztosítás** az összes összehasonlított platform közül — de egyben a **leglassabb és legkisebb léptékű**.

#### Korlátok a Zoo-val

- Nincs web UI — csak egy docs oldal
- Nincs keresés, nincs szűrés
- Nincs API — manuális letöltés + manuális ONNX betöltés Deepness-be
- Kizárólag ONNX formátum
- PR-alapú contribution → nagyon lassú növekedés
- Google Drive linkek időnként elérhetővé válnak (link rot)

---

### 3. Hugging Face Hub — huggingface.co

**Skála:** 1 000 000+ modell összesen; geospatial / drone / remote sensing szegmens: néhány száz

#### Releváns geospatial modellek

| Szervezet / Modell | Tartalom |
|--------------------|----------|
| `ibm-nasa-geospatial/Prithvi-100M` | Foundation model Sentinel-2 képekre, multispektrális |
| `torchgeo/*` | ResNet, EfficientNet, ViT — remote sensing adatokra pretrain |
| DOTA fine-tuned modellek | Légi objektumdetekció (repülő, hajó, autó) |
| iSAID fine-tuned modellek | Légi instance szegmentáció |
| Crop type classification | Mezőgazdasági parcella osztályozás |

#### Modell formátumok

Elsődlegesen PyTorch (`.safetensors`), de ONNX export egyszerűen végrehajtható:

```python
import torch
from transformers import AutoModel
model = AutoModel.from_pretrained("ibm-nasa-geospatial/Prithvi-100M")
torch.onnx.export(model, dummy_input, "prithvi.onnx")
```

#### Keresés és szűrés

```
Feladat: image-segmentation
Library: onnx
Dataset: sentinel-2
Tag: remote-sensing, aerial, drone
License: mit
```

#### Árazás

- Ingyenes publikus modellek letöltése: **korlátlan, ingyenes**
- Hosted Inference API: ingyenes tier rate limit-tel, fizetős magasabb terhelésre
- Pro account: $9/hó (nagyobb storage, privát modellek)

#### Minőségbiztosítás

- Nincs kötelező review
- "Model Card" (README.md) ajánlott, de nem kötelező
- Intézményi accountok (IBM, NASA, ESA) mint megbízhatóság-jelző

---

### 4. ESRI ArcGIS DLPK Library

**Skála:** ~50–100 hivatalos ESRI modell + néhány száz közösségi

#### Hogyan működik?

A Deep Learning Package (`.dlpk`) egy ZIP fájl, amely tartalmaz:
- Model weights (PyTorch `.pth` vagy TF SavedModel)
- `model.emd` metaadat JSON
- Python raszter inferencia függvény
- Opcionálisan: minta adatok

ArcGIS Pro-n belül a "Deep Learning Models" panelből böngészhető és letölthető.

#### Elérhető architektúrák (amit a Deepness nem tud)

UNet, DeepLab, **Mask RCNN**, **RetinaNet**, **SingleShotDetector**, EfficientDet, PSPNet, Feature Pyramid Network — és YOLO.

#### Minőségbiztosítás

- Hivatalos ESRI modellek: belső QA folyamat, dokumentált benchmark metrikák
- Közösségi Hub modellek: nincs kötelező review

#### Korlátok

- ArcGIS Pro licenc szükséges ($1 500+/év)
- `.dlpk` formátum — csak ArcGIS-ben használható közvetlenül
- Nem ingyenes

---

### 5. Egyéb releváns repozitóriumok

| Repozitórium | Modell szám | Formátum | Ingyenes? | Drone-releváns? |
|---|---|---|---|---|
| **Ultralytics Hub** | Száz | ONNX, PT, TFLite | Freemium | ✅ Magas |
| **TorchGeo (HF-en)** | ~30–50 | PyTorch → ONNX export | ✅ Igen | ✅ Közepes (műholdas) |
| **OpenMMLab Zoo** | Több száz | PyTorch → ONNX export | ✅ Igen | ✅ Közepes (légifotó) |
| **Zenodo / OpenGeoHub** | Tíz | Vegyes | ✅ Igen | ⚠️ Alacsony (talaj, éghajlat) |
| **ModelScope (Alibaba)** | Tízezer | Vegyes | ✅ Részben | ⚠️ Alacsony (kínai adat) |
| **segment-geospatial (SAM)** | SAM2 alapú | PyTorch | ✅ Igen | ✅ Magas (zero-shot) |

---

## IV. Összehasonlítás

### Modell repository feature-mátrix

| Funkció | Dronterapia (jelenlegi) | Roboflow Universe | Deepness Zoo | Hugging Face | ArcGIS DLPK |
|---------|------------------------|------------------|--------------|-------------|-------------|
| **Modellszám** | ~10 (lokális) | 250 000+ | ~20–50 | 1M+ (száz geo) | ~50–100 |
| **Keresés szöveg alapján** | ✗ | ✅ | ✗ | ✅ | ⚠️ Korlátolt |
| **Szűrés tag/osztály szerint** | ✗ | ✅ | ✗ | ✅ | ⚠️ Korlátolt |
| **Szűrés mAP/metrika szerint** | ✗ | ✅ | ✗ | ✗ | ✗ |
| **REST API** | ✗ | ✅ | ✗ | ✅ | ✅ |
| **Közvetlen ONNX letöltés** | ✅ (helyi) | ✅ | ✅ | ⚠️ Export kell | ⚠️ DLPK konverzió |
| **Közösségi feltöltés** | ✅ (helyi) | ✅ | ✅ (PR review) | ✅ | ✅ (Hub) |
| **Csillagozás / review** | ✅ Struct létezik, nincs UI | ✗ (like/download) | ✗ | ✅ Discussions | ✗ |
| **Mintaképek** | ✅ 5 db/modell | ✅ | ✅ | ⚠️ Modell cardon | ⚠️ Opcionális |
| **Versioning** | ✗ | ✅ (verziószám) | ✗ | ✅ (branch/tag) | ✗ |
| **Modell megosztás URL-lel** | ✗ | ✅ | ✅ (docs link) | ✅ | ✅ |
| **Fizetős** | ✗ (ingyenes) | Freemium | ✗ | Freemium | ✅ Licencdíjas |
| **ONNX-only** | ✅ | ✗ (multi-format) | ✅ | ✗ | ✗ |
| **Offline használható** | ✅ | ✗ | ✅ | ✅ (letöltés után) | ✅ |

### Amit mi jobban csinálunk mint a Deepness Zoo

| Aspektus | Deepness Zoo | Dronterapia |
|----------|-------------|-------------|
| Metaadat gazdagsága | Minimális (classes, resolution, link) | 30+ mező (metrics, tags, reviews, stats, calibration) |
| Mintaképek | 1–2 példakép a docs oldalon | 5 JPEG thumbnail modellenkent |
| Kalibrációs paraméterek | ✗ | ✅ (`avg_bbox_size_cm2`, `cluster_threshold`) |
| Preprocessing preset meta | ✗ | ✅ |
| Használati statisztika | ✗ | ✅ (`usage_count`) |
| UI modellkártyák | ✗ | ✅ (grid layout, csillagok, metrikák) |

### Amit a Roboflow Universe jobban csinál mint mi

| Aspektus | Roboflow Universe | Dronterapia |
|----------|------------------|-------------|
| Modellek száma | 250 000+ | ~10 |
| Keresés | ✅ Full-text + filter | ✗ |
| REST API | ✅ | ✗ |
| Multi-format export | ONNX, PT, TFLite, CoreML... | ONNX |
| Valódi megosztás (URL) | ✅ Publikus URL | ✗ Csak lokális |
| Versioning | ✅ | ✗ |
| Dataset klónozás | ✅ | ✗ |

---

## V. Mit vegyünk át? — Prioritizált javaslatok

A Roboflow Universe-t 1:1-ben lemásolni nem realisztikus célkitűzés — ez egy milliós felhasználóbázison épített, felhőalapú platform. A mi kontextusunkban az alábbi **három fejlesztési iránnyal** érhető el a legtöbb értéknövelés minimális fejlesztési idő alatt:

---

### 🔴 1. Keresés és szűrés a meglévő modelleken

**Mi az?** Az aktuális modellkártya grid statikus — nincs keresőmező, nincs szűrő. Minden modell megjelenik, ami a felhasználónak látható.

**Mit adjunk hozzá:**

```
┌─────────────────────────────────────────────────────┐
│  🔍 Keresés:  [kukorica                        ]    │
│  🏷️ Tag:      [kukorica ×] [barna_talaj ×]          │
│  📊 mAP min:  0.80 ─────●──────── 1.00              │
│  🌿 Növény:   [Összes ▼]  Kukorica / Napraforgó / ...│
│  🔃 Rendezés: [Leggyakrabban használt ▼]            │
└─────────────────────────────────────────────────────┘
```

**Technikai megvalósítás:**
- Streamlit `st.text_input`, `st.multiselect`, `st.slider` szűrők
- Szűrés a már betöltött `model_manager.get_model_list()` listán — nincs szükség adatbázis-változtatásra
- Rendezési opciók: mAP, usage_count, created_at, display_name

**Becsült fejlesztési idő:** ~0.5 nap
**Hatás:** Nagy — sok modell esetén elengedhetetlen

---

### 🔴 2. Rating és review UI — az adatstruktúra már létezik

**Mi az?** A `ratings` és `reviews` mezők minden modellben léteznek a `model_registry.json`-ban — de az UI sehol sem jeleníti meg, és nem lehet csillagozni.

**Mit adjunk hozzá a modellkártyára:**

```
┌─────────────────────────────────────────────────────────┐
│  Kukorica - YOLOv11s v3                                  │
│  ⭐⭐⭐⭐☆  4.2 (17 értékelés)                           │
│                                                          │
│  💬 Legutóbbi review:                                    │
│  "Barna talajon kiváló, zöld talajon gyengébb." — user1 │
│  [📝 Értékelés írása]                                    │
└─────────────────────────────────────────────────────────┘
```

**Technikai megvalósítás:**
- `ModelMetadata.add_review()` már implementálva van
- `ModelMetadata.increment_usage()` már fut
- Csak UI szükséges: `st.slider(1, 5)` + `st.text_area` + mentés gomb
- Az átlagcsillag kalkuláció már a struktúrában van

**Becsült fejlesztési idő:** ~1 nap
**Hatás:** Közepes — de megkülönböztető funkció a Deepness Zoo-val szemben

---

### 🟡 3. Modell export URL — valódi megosztás

**Mi az?** Jelenleg egy "nyilvános" modell csak azon a szerverpéldányon érhető el, ahol fut. Ha valaki más is szeretné használni, manuálisan kell átküldeni az ONNX fájlt.

**Két megközelítés:**

**A) Modell letöltés gomb (gyors megoldás, ~0.5 nap)**

```python
# A modellkártyán egy letöltés gomb:
with open(model_path, "rb") as f:
    st.download_button(
        label="⬇️ ONNX letöltés",
        data=f,
        file_name=f"{model.display_name}.onnx",
        mime="application/octet-stream"
    )
```

Ez lehetővé teszi, hogy a felhasználó kimentse az ONNX fájlt, és egy másik Dronterapia példányba feltöltse.

**B) Közösségi model hub oldal (hosszabb fejlesztés, ~5 nap)**

Egy dedikált `/Community` oldal, ahol a publikus modellek böngészhetők és letölthetők — akár nem bejelentkezett felhasználók számára is. Ez egy "mini Roboflow Universe" a mi drón-specifikus kontextusunkban.

```
╔══════════════════════════════════════════════════════════╗
║  🌍 Közösségi Modellek                                    ║
║  ─────────────────────────────────────────────────────── ║
║  🔍 [kukorica              ]  🏷️ [barna_talaj ×]          ║
║                                                          ║
║  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐   ║
║  │ Kukorica v3  │  │ Napraforgó   │  │ Repce 2025   │   ║
║  │ ⭐⭐⭐⭐☆    │  │ ⭐⭐⭐☆☆    │  │ ⭐⭐⭐⭐⭐   │   ║
║  │ mAP: 0.923   │  │ mAP: 0.847   │  │ mAP: 0.891   │   ║
║  │ 47 használat │  │ 12 használat │  │ 8 használat  │   ║
║  │ [⬇️ Letöltés]│  │ [⬇️ Letöltés]│  │ [⬇️ Letöltés]│   ║
║  └──────────────┘  └──────────────┘  └──────────────┘   ║
╚══════════════════════════════════════════════════════════╝
```

---

### 🟡 4. Külső modell importálás — Roboflow Universe integráció

**Mi az?** Ahelyett, hogy minden felhasználó saját maga tanítja a modelleket, importálhatna közvetlenül a Roboflow Universe-ből ONNX formátumban.

**Megvalósítási koncepció:**

```
┌─────────────────────────────────────────────────────────┐
│  📦 Modell importálás külső forrásból                    │
│                                                          │
│  Forrás: [Roboflow Universe ▼] / [Hugging Face] / [URL] │
│                                                          │
│  Roboflow projekt ID: [corn-detection-xyz123      ]     │
│  Verzió: [3                                       ]     │
│                                                          │
│  [🔍 Keresés]                                            │
│                                                          │
│  Találat: "Corn Plant Detection" — mAP: 0.891           │
│  Classes: corn_plant (1 osztály) | Képek: 1 240 db      │
│  [⬇️ Importálás ONNX-ként]                              │
└─────────────────────────────────────────────────────────┘
```

**Roboflow API-n keresztül:**
```python
from roboflow import Roboflow
rf = Roboflow(api_key=st.secrets["ROBOFLOW_API_KEY"])
project = rf.workspace().project("corn-detection-xyz123")
version = project.version(3)
version.download("onnx", location="models/")
```

**Becsült fejlesztési idő:** ~2 nap
**Hatás:** Magas — azonnal elér ezres modell-könyvtárat anélkül, hogy saját közösséget kellene felépíteni

---

### 🟢 5. Versioning — modell leszármazási lánc

**Mi az?** Jelenleg ha egy modellt újratanítunk, egy teljesen új bejegyzés keletkezik a registryben. Nincs "v1 → v2 → v3" lánc, nincs összehasonlítás.

**Adatstruktúra bővítés:**

```json
{
  "id": "uuid-v4",
  "parent_model_id": "uuid-v4-of-previous-version",
  "version": "3",
  "version_notes": "Több training kép, ExGreen preset hozzáadva",
  "changelog": [
    { "version": "1", "mAP50": 0.812, "training_images": 120 },
    { "version": "2", "mAP50": 0.871, "training_images": 240 },
    { "version": "3", "mAP50": 0.923, "training_images": 360 }
  ]
}
```

**UI megjelenítés a modellkártyán:**
```
Kukorica - YOLOv11s  [v3 ▼]   ← verzióválasztó dropdown
 ├─ v1: mAP 0.812 (2026-01-02)
 ├─ v2: mAP 0.871 (2026-01-20)
 └─ v3: mAP 0.923 (2026-02-15) ← jelenlegi
```

**Becsült fejlesztési idő:** ~2 nap
**Hatás:** Közepes — elsősorban power user funkció

---

## VI. Technikai megvalósítási terv

### A meglévő struktúra kompatibilitása

A jelenlegi `model_registry.json` struktúra **rendkívül jól előkészített** — a `ratings`, `reviews`, `stats`, `use_cases` mezők már léteznek, csak UI hiányzik hozzájuk. Ez azt jelenti, hogy az 1. és 2. prioritású fejlesztés **visszafelé kompatibilis**, adatmigráció nélkül végrehajtható.

### Fejlesztési sorrend és becsült idők

| Prioritás | Feature | Érintett fájlok | Becsült idő |
|-----------|---------|-----------------|-------------|
| 🔴 1 | Keresés + szűrés a Counting oldalon | `pages/3_Counting.py` | 0.5 nap |
| 🔴 2 | Rating / review UI | `pages/3_Counting.py`, `utils/model_metadata.py` | 1 nap |
| 🔴 3a | ONNX letöltés gomb | `pages/3_Counting.py` | 0.5 nap |
| 🟡 3b | Közösségi modellek oldal | `pages/new_Community.py` | 3–5 nap |
| 🟡 4 | Roboflow Universe importálás | `pages/3_Counting.py`, `utils/model_metadata.py` | 2 nap |
| 🟢 5 | Model versioning | `utils/model_metadata.py`, `model_registry.json` schema | 2 nap |

### Adatstruktúra-változtatások a 3b és 4 featurekhöz

A `model_registry.json`-ban egyetlen új mező szükséges a közösségi hub és import funkciókhoz:

```json
{
  "external_source": {
    "provider": "roboflow" | "huggingface" | "deepness_zoo" | null,
    "source_url": "https://universe.roboflow.com/...",
    "source_model_id": "corn-detection/3",
    "imported_at": "2026-03-07T10:00:00"
  }
}
```

A visszafelé kompatibilitás biztosítva: `external_source: null` az összes jelenleg létező modellnél.

### Roboflow API kulcs kezelése

```bash
# .env fájlban (már létezik a projekt emailes értesítéshez):
ROBOFLOW_API_KEY=rf_xxxxxxxxxxxxxxxx
```

```python
# Streamlit secrets alternatíva (production-ready):
# .streamlit/secrets.toml
# [roboflow]
# api_key = "rf_xxxxxxxxxxxxxxxx"
```

---

## Összefoglalás

| Kérdés | Válasz |
|--------|--------|
| **Hol van az iparágban a legjobb model repo?** | Roboflow Universe (250 000+ modell, REST API, multi-format, keresés) |
| **Mi a Deepness Zoo gyengesége?** | ~20–50 modell, nincs keresés, nincs API, PR-alapú lassú bővítés, link rot |
| **Mi a mi előnyünk a Deepness Zoo-val szemben?** | Gazdagabb metaadat (30+ mező), 5 mintakép/modell, kalibrációs paraméterek, modellkártya UI |
| **Mi a mi hátrányunk a Roboflow Universe-szel szemben?** | ~10 vs. 250 000 modell, nincs keresés, nincs REST API, nincs valódi megosztás |
| **Melyik fejlesztés hozza a legtöbbet legkevesebb munkával?** | Keresés/szűrés (0.5 nap), Rating UI (1 nap), ONNX letöltés (0.5 nap) |
| **Hogyan érjük el az ezres modellszámot saját fejlesztés nélkül?** | Roboflow Universe API integráció — importálás ONNX-ként |
