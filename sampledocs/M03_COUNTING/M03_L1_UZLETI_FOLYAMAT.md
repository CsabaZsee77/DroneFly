# L1 – Üzleti Folyamat – Counting (Tőszámlálás / Inference)

**Modul:** M03
**Szint:** L1 – Üzleti Folyamat
**Forrásdokumentumok:** FELHASZNALOI_UTMUTATO.md, GSD_ADAPTIVE_SLIDING_WINDOW.md, FEATURE_PLAN_IMAGE_PREPROCESSING_PRESETS.md, DETECTION_RESULTS_MISSING_DIAGNOSIS.md

---

## Fő cél

Drónfelvételek automatikus elemzése betanított YOLO ONNX modellekkel, a növényi tövek (pl. kukorica, napraforgó) megszámlálása és a detekciók exportálása különböző formátumokban. A Counting a teljes ML pipeline végterméke.

---

## Szereplők

| Szereplő | Szerepkör |
|----------|-----------|
| **Felhasználó** | Képet tölt fel, modellt választ, detekciót futtat, eredményt exportál |
| **YOLOPredictor (core/predictor.py)** | ONNX modell betöltése, inference futtatása |
| **SlidingWindowDetector** | Nagy képek ablakos feldolgozása |
| **GeoTIFFHandler** | GeoTIFF metaadatok kezelése, WGS84 koordináta transzformáció |
| **DetectionResultsManager** | Eredmények cache-elése és perzisztenciája |
| **CountingCalculator** | Statisztikák számítása |
| **CreditManager** | Kredit egyenleg kezelése (SaaS funkció) |
| **UserManager** | Felhasználói beállítások kezelése (require_geotiff flag) |

---

## Mi indítja a folyamatot

1. **Kép feltöltése:** Felhasználó JPG/PNG/TIFF képet tölt fel
2. **Korábbi feltöltés kiválasztása:** Már feltöltött kép újrahasználata
3. **Minta kép kiválasztása:** Előre betöltött teszt kép
4. **Modell kiválasztása:** Felhasználó a jobb oldali panelből választ

---

## Képernyő elrendezés (3_Counting.py)

```
Bal oldal: Kép feltöltés          │  Jobb oldal: Modell választás
──────────────────────────────────┼────────────────────────────────
Képforrás választó:                │  Modell csempék (card-ok):
  [Új feltöltés]                   │  ┌─────────────────────────┐
  [Korábbi feltöltések]            │  │ Modell neve             │
  [Minta képek]                    │  │ ⭐⭐⭐⭐⭐ Rating        │
                                   │  │ Sample kép              │
Kép előnézet                      │  │ 🏷️ kukorica 🏷️ 2026     │
Kép információ:                    │  │ mAP50: 0.923            │
  - méret, formátum, fájlméret    │  │ [Kiválasztás] gomb      │
  - GeoTIFF metaadatok (ha van)   │  └─────────────────────────┘
                                   │  ...további modellek...
Sidebar beállítások:               │
  - Konfidencia küszöb (0.0-1.0)  │
  - IoU küszöb (0.0-1.0)          │
  - Sliding window on/off          │
  - Preprocessing preset           │
  - Email értesítés                │
  [▶️ Predikció futtatása]         │
──────────────────────────────────┴────────────────────────────────
Eredmények terület (alul):
  Metrikák | Annotált kép | Detekciók részletei | Export gombok
```

---

## Üzleti események (végponttól végpontig)

### 1. Kép betöltése

**Új feltöltés:**
1. Felhasználó képet tölt fel (JPG/PNG/TIFF/GeoTIFF)
2. Rendszer elmenti az `ImageManager`-be (`data/uploads/`)
3. GeoTIFF esetén: CRS, Bounds, csatornaszám megjelenítése
4. GeoTIFF esetén: területszámítás (`calculate_area_ha()`) és info megjelenítése:
   - `🌍 GeoTIFF fájl detektálva — X.XX ha terület (N kredit szükséges)`
5. Kép előnézet megjelenítése

**Korábbi feltöltés:**
1. Dropdown: `data/uploads/` tartalma
2. Kiválasztott kép betöltése memóriába
3. GeoTIFF esetén: területinfo megjelenítése (ld. fent)

**Minta kép:**
1. Dropdown: `data/samples/` tartalma
2. Minta kép betöltése memóriába
3. GeoTIFF esetén: területinfo megjelenítése (ld. fent)

### 2. Preprocessing preset alkalmazása (opcionális)

A Counting oldalon preprocessing presetet választhat:
- **None** – Nincs feldolgozás (alapértelmezett)
- **ExGreen** – Excess Green Index: `2G - R - B`
- **ExRed** – Excess Red Index: `2R - G - B`
- **ExBlue** – Excess Blue Index: `2B - R - G`
- **VARI** – Visible Atmospherically Resistant Index: `(G-R)/(G+R-B)`
- **NGRDI** – Normalized Green-Red Difference Index: `(G-R)/(G+R)`
- **GLI** – Green Leaf Index: `(2G-R-B)/(2G+R+B)`

> **Fontos:** Ugyanazt a presetet kell választani, ami az annotáció során is volt alkalmazva!

**Preview:** Before/After összehasonlítás expandable blokkban.

### 3. Modell kiválasztása

A modell szekció egy összecsukható expanderben (`🤖 Modell választás — aktív: {modell neve}`) található.
A címsor mutatja a kiválasztott modell nevét, így összecsukott állapotban is látható melyik aktív.

- Felhasználó átnézi a modell kártyákat (sample képek, metrikák, tagek)
- "Kiválasztás" gombra kattint → gomb zöldül: "✅ Kiválaszt"
- Modellek láthatóság szerint szűrve: saját (private) + nyilvános (public)

### 4. Detekció paraméterek beállítása

**Konfidencia küszöb (0.0-1.0):**
- Alacsony (0.15): fontos minden objektumot megtalálni
- Közepes (0.25): általános (alapértelmezett)
- Magas (0.35-0.50): csak biztos detekciók

**IoU küszöb (NMS) (0.0-1.0):**
- Non-Maximum Suppression – átfedő detekciók szűrése
- Alapértelmezett: 0.45

**Sliding Window (nagy képekhez):**
- Be/ki kapcsoló
- Átfedés (%): 0-50% (alapértelmezett: 25%)
- Opcionális egyedi ablak méret
- **TTA (Test-Time Augmentation) (✅ IMPLEMENTÁLVA 2026-03-10):** vízszintes tükrözés, függőleges tükrözés, 90°-os forgatás — tile szinten, kombinálható; szegmentációs modellnél nem elérhető

**Sor-detekció (opcionális, ✅ IMPLEMENTÁLVA 2026-03-10):**
- `🌾 Sor-detekció` be/ki kapcsoló
- Ha aktív:
  - `Min. pont/sor` slider (2–10, alapért. 3)
  - `Soron kívüli detekciók kezelése` (radio gomb):
    - **Confidence büntetés (ajánlott):** kiugró detekciók confidence-ét szorzóval csökkenti (outlier × szorzó); ha az eredmény a konfidencia küszöb alá esik, a detekció törlődik
    - **Teljes eltávolítás:** minden soron kívüli detekció törlődik
  - Penalty módban: `Büntetési szorzó` slider (0.1–0.9, alapért. 0.5)

### 4b. GT Paraméter-kereső mód (opcionális)

**Státusz:** ✅ IMPLEMENTÁLVA (2026-03-24)

Ha a felhasználó az üzemmód választónál a **"Paraméter-kereső (GT régió)"** módot választja:

1. **Régió kijelölés:** 4 sliderrel (bal, felső, szélesség, magasság %-ban) kijelöl egy területet
   - Narancssárga keret + sötétített külső mutatja a kijelölést
   - Zoom nézet + letöltés gomb a kijelölt régióra
2. **Kézi tőszám megadás:** A felhasználó megszámolja a régióban lévő növényeket
3. **Sweep tartomány beállítás:**
   - Konfidencia: min, max, lépésköz (alapért.: 0.05–0.90, 0.05 lépés)
   - IoU: min, max, lépésköz (alapért.: 0.10–0.90, 0.10 lépés)
   - Kombináció-szám előzetes kijelzés
4. **Inference + 2D Sweep:** A rendszer lefuttatja az inference-t alacsony küszöbbel (0.01),
   majd végigpróbálja az összes conf × IoU kombinációt
5. **Eredmény:**
   - 2D heatmap tábla (mely paraméterkombinációnál hány detekció van)
   - Legjobb kombináció kiemelése (legkisebb eltérés a kézi számtól)
   - Annotált kép a legjobb paraméterekkel
   - **"Mentés presetként"** gomb → egyetlen kattintással elmenthetők az optimális beállítások
6. **Használat:** Preset betöltése → "Teljes terület" módra váltás → teljes képen futtatás

> **Iteratív finomítás:** Először széles tartomány + nagy lépésköz, majd szűkítés az optimum környékére.

### 5. Predikció futtatása

**Fázis 1 — Előellenőrzés (▶️ gomb megnyomásakor):**
1. GeoTIFF követelmény ellenőrzés:
   - `user_manager.get_require_geotiff(user_id)` → ha True és a kép nem GeoTIFF → hiba, megállás
   - Ha a felhasználónál `require_geotiff = False` (admin által beállítva) → GeoTIFF nélkül is folytatható
2. Területszámítás GeoTIFF esetén: `calculate_area_ha()` → szükséges kreditek = `math.ceil(terület_ha)`
3. Kredit ellenőrzés: `check_credits(credits, required_credits)` → ha nincs elég kredit → `InsufficientCreditsError`
4. Pending state beállítása: `st.session_state['_area_confirm_pending'] = True`

**Fázis 2 — Megerősítő dialog (GeoTIFF esetén):**
1. Megjelenik: `⚠️ A detekció X.XX hektárnyi területet dolgoz fel — N kredit kerül levonásra.`
2. Két gomb: `[✅ Igen, futtatom]` / `[❌ Mégsem]`
3. Igen → `_area_confirm_done = True`, pending = False
4. Mégsem → pending = False, megállás

**Fázis 3 — Detekció indítása (confirm_done esetén):**
1. Kredit levonás: `deduct_credits(user_id, amount=required_credits)`
2. Modell betöltése (ONNX Runtime)
3. Preprocessing (ha preset van)
4. Ha sliding window: `SlidingWindowDetector.detect()`
5. Ha nem: `YOLOPredictor.predict()`
6. NMS alkalmazása (IoU alapján)
7. Eredmény megjelenítése
8. Eredmény cache-elése (`DetectionResultsManager`)

> **Megjegyzés:** Kredit levonás a detekció ELŐTT történik (a megerősítés után), nem utána.

### 6. Eredmények megtekintése

**Metrikák:**
- Talált objektumok száma
- Kép méret (px)
- Modell input méret (px)
- Átlag/Min/Max konfidencia

**Vizualizáció — két fül:**

| Fül | Tartalom |
|-----|----------|
| **Egyedi detekciók** | YOLO annotált kép — minden bbox egyenként, a modell saját jelölésével |
| **Cluster alapú detekciók** | Színkódolt bbox overlay: 🟢 Zöld = egyedi tő, 🟠 Narancs + szám = cluster (becsült tövek), 🟡 Sárga = nincs kalibráció |

- **Detection modell:** Bounding box-ok
- **Segmentation modell:** Maszk overlay + bounding box

**Cluster alapú tőszám becslés:**
- Ha a GSD be van állítva (GeoTIFF-ből vagy kézi megadással) és a kalibrációs avg. méretek meg vannak adva, a rendszer elkülöníti az egyedi töveket a cluster-ektől
- `bbox_area_cm² > avg_size × cluster_threshold (alap: 2.0)` → cluster
- Cluster becsült tövek száma: `terület_cm² / avg_méret_cm²`
- Összesített becslés: egyedi tövek + cluster-ekben becsült tövek

**🔄 Paraméterek finomhangolása (expander):**
- Megjelenik a counting eredmény után
- GSD, kalibrációs avg. méretek módosíthatók
- **"🔄 Újraszámol"** gomb — csak gombnyomásra fut újra (nincs automatikus újraszámítás minden változtatásra)
- Ez lehetővé teszi a GSD pontos beállítását és a cluster becslés valósághoz igazítását

**Detekciók részletei (expander):**
- Sorszám, konfidencia, osztály, bounding box koordináták

**Maszk terület statisztikák (csak szegmentációs modellnél):**
- Maszk terület: átlag / min / max / összesen (cm²-ben, GSD alapján)
- Pontosabb mint a bbox-alapú területszámítás

**Sliding window statisztika + Confidence eloszlás elemzés (csak sliding window módban):**
- Feldolgozott ablakok, nyers/NMS utáni/végső detekciók száma
- TTA aktív volt-e (és mely transzformációk)
- `📊 Confidence eloszlás elemzés` expander (ha ≥ 10 nyers detekcióra van adat):
  - Hisztogram: nyers confidence értékek eloszlása (tile-onként, NMS előtt)
  - Optimális threshold javaslat: a bimodális eloszlás (zaj vs. valódi detekciók) völgypontja
  - 4 metrika: nyers detekciók / min / átlag / max confidence

### 7. Export

| Export típus | Trigger | Fájlnév | Formátum |
|--------------|---------|----------|----------|
| CSV | [📄 CSV letöltés] gomb | `detections_{kép_neve}.csv` | Táblázat |
| JSON | [📋 JSON letöltés] gomb | `detections_{kép_neve}.json` | Strukturált |
| PNG | [🖼️ Kép letöltés] gomb | `annotated_{kép_neve}.png` | Annotált kép |
| GeoJSON | [🌍 GeoJSON] gomb (csak GeoTIFF) | `detections_{kép_neve}.geojson` | WGS84 koordináták |
| PDF | Email küldés (opcionális) | `report_{kép_neve}.pdf` | Riport |

### 8. Prescription map (növénysűrűség-térkép) — ✅ BŐVÍTVE (2026-03-27)

Az eredmények után elérhető `🗺️ Prescription map` expander három alkategóriával:

**Közös beállítások:**
1. Cellaméret (méterben, ha GSD ismert)
2. Statisztika: cellák száma, foglalt cellák, max. detekció/cella, kelési arány

**Kelési arány beállítás (opcionális, GeoTIFF vagy kézi GSD esetén):**
- Forrás: **Parcellából** (a parcella nyilvántartásban tárolt vetési paraméterekből) vagy **kézi megadás**
- Megadás módja: **Sortáv × Tőtáv** (cm) → automatikus sűrűség-számítás, vagy **közvetlen sűrűség (tő/ha)**
- Ha megadva: az összes detekció / összes várható × 100% = **globális kelési arány %** megjelenik a statisztika sorban
- Ha nem adja meg: relatív módban működnek a térképek
- **Megjegyzés:** GSD nélküli képeknél (nincs GeoTIFF és nincs manuális GSD) ez a szekció rejtve marad

**1. Raszteres heatmap** (`📊 Raszteres heatmap`):
- Kelési arány módban: piros (0–50%) → sárga (50–80%) → zöld (80%+)
- Relatív módban: zöld (alacsony) → piros (magas) — max count normalizált
- Export: **PNG heatmap**, **CSV** (count, density, zone), **GeoJSON** (csak GeoTIFF)

**2. Folyamatos KDE heatmap** (`🌊 Folyamatos heatmap (KDE)`):
- Gaussian kernel sűrűségbecslés minden detekció körül
- Beállítható sávszélesség (méterben, ha GSD ismert)
- Vizuálisan simított, trendek megjelenítéséhez ideális
- **"🌊 KDE generálása" gomb** — nem fut automatikusan, csak kattintásra (teljesítmény okokból)
- Cache: session state-ben tárolt, csak sávszélesség/detekciószám változásnál szükséges újragenerálás
- Export: **PNG** + **💾 Médiatárba** mentés

**3. Cluster zónatérkép** (`🔵 Cluster zónatérkép (K-means)`):
- K-means klaszterezés a rácscellákon (2/3/4 zóna választható)
- Zöld = alacsony sűrűség → piros = magas sűrűség
- Döntéstámogatáshoz: beavatkozandó területek azonosítása
- **"🔵 Cluster generálása" gomb** — nem fut automatikusan (teljesítmény okokból)
- Export: **PNG** + **💾 Médiatárba** mentés

**Mentés médiatárba:**
- Minden heatmap (raszter, KDE, cluster) mellett `💾 Médiatárba` gomb
- GeoTIFF forráskép esetén a `geo_bounds` (WGS84 határoló koordináták) is eltárolódik
- A mentett heatmapek a Médiatárban `🌡️ Heatmap` típusként jelennek meg
- A Map oldalon georeferált heatmapek `ImageOverlay`-ként megjeleníthetők a térképen

**Megjelenítés a térképen (Map oldal):**
- `🌡️ Heatmap rétegek` checkbox → expander a kiválasztáshoz
- Médiatárból: `image_type == "heatmap"` ÉS `geo_bounds` kitöltve ÉS fájl létezik
- Saját opacity slider (alapértelmezett: 0.65)
- Ha nincs GeoTIFF kiválasztva, a térkép a heatmap koordinátáira ugrik

### 9. Sor-detekció és confidence penalty — ✅ FRISSÍTVE (2026-03-29)

**Sidebar beállítások (Sor-detekció expander):**
- `Min. pont/sor` slider (2–10, alapért. 3)
- `Görbe illesztés foka` radio (0 = egyenes, 1 = lineáris, 2 = másodfokú görbe)
- `Max. törési szög` slider (5–45°, alapért. 15°): a görbe mennyire "hajolhat" az illeszkedés során
- `Soron kívüli detekciók kezelése` radio: Confidence büntetés / Teljes eltávolítás
- Penalty módban: `Büntetési szorzó` slider (0.1–0.9)

Az eredmények után, ha a `🌾 Sor-detekció` be volt kapcsolva, megjelenik a `🌾 Sor-detekció eredmény` expander:
1. Metrikák: Sorok száma | Szög (°) | Sor-köz (px) | Sor-köz (cm, ha GSD ismert)
2. Overlay kép: zöld **polilinek** (enyhén görbe sor-vonalak) + piros kiugró keretek
3. `🌾 Sor-overlay PNG letöltés` gomb
4. Soron kívüli detekciók kezelése (a kiválasztott módtól függően):
   - **Penalty mód:** `apply_row_confidence_penalty()` fut — kiugró detekciók confidence-e csökken;
     ha az eredmény a küszöb alá kerül, a detekció törlődik. Az eltávolított db száma megjelenik.
   - **Remove mód:** `filter_by_rows()` fut — minden outlier detekció törlődik.
5. A módosított detekciós lista alapján frissül az annotált kép és az export is.

**Algoritmus változás (2026-03-29):**
- Korábbi: Hough-transzformáció egyenes vonalakkal
- Jelenlegi: PCA (SVD) alapú domináns szögmeghatározás + Tukey-kerítés gap-alapú sordeteciós csúcskeresés + polinomiális polilinek per-sorhoz
- Eredmény: enyhén görbe sorok is megbízhatóan detektálhatók, kevesebb hamis sor

> **Fontos:** Mindkét mód csak vizuális / export módosítás — a `result['count']` metrikát nem módosítja.

---

## Állapotok (üzleti nézet)

| Állapot | Feltétel | Teendő |
|---------|----------|--------|
| Várakozás | Nincs kép vagy nincs modell | Kép feltöltés és/vagy modell választás |
| Kész | Van kép ÉS van modell | "▶️ Predikció futtatása" aktív |
| Megerősítésre vár | GeoTIFF képnél ▶️ megnyomva | Területinfo + kredit szükséglet jóváhagyása |
| Detekció folyamatban | Háttérszál fut, progress bar látható | Várj, vagy kattints ⛔ Leállítás |
| Eredmény kész | Detekció lefutott (vagy megszakítva) | Megtekintés, export, prescription map |

---

## Végállapotok

1. **Sikeres detekció:** Eredmények megjelenítve, export elérhető; kreditek levonva (hektáronként 1 kredit)
2. **0 detekció:** "Nem találtam objektumokat" → konfidencia csökkentése vagy más modell
3. **Modell betöltési hiba:** ONNX file sérült → modell újratanítás szükséges
4. **Kredit hiány:** InsufficientCreditsError → kredit vásárlás
5. **GeoTIFF követelmény megsértése:** Nem GeoTIFF kép feltöltve + require_geotiff = True → hibaüzenet, detekció nem indul
6. **Megerősítés elutasítva:** Felhasználó a dialog-ban Mégsem-et választ → detekció nem indul, kreditek nem vonódnak le

---

## Kapcsolódó modulok

- ← **M02 Training:** A detekciónál használt ONNX modellek innen érkeznek
- ← **M05 Modell Nyilvántartás:** Modellek láthatóság és metaadatok
- ← **M08 ODM Ortomozaik:** GeoTIFF input a tőszámláláshoz (A és B útvonal)
- → **M04 Felhasználókezelés:** Kredit egyenleg, session, `require_module("counting")` kapuzás
