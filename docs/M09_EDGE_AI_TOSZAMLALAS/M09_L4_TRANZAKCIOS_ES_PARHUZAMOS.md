# L4 – Tranzakciós és Párhuzamos Kezelés – Edge AI Tőszámlálás

**Modul:** M09
**Szint:** L4 – Tranzakciós és Párhuzamos Kezelés
**Verzió:** v1.0.0 (implementálva)
**Létrehozva:** 2026-07-02
**Utolsó módosítás:** 2026-07-03
**Státusz:** ✅ Implementálva

---

## 1. Szálkezelés

- **UI thread:** csak a felhasználói interakció (session/modell választás,
  gombnyomás) és a progress-frissítés megjelenítése.
- **1 db háttér worker thread** (`ExecutorService`, `newSingleThreadExecutor()`):
  a teljes `runOnSession()` ciklus ezen fut — fájl beolvasás, bitmap
  dekódolás, ONNX Runtime inferencia, NMS.
- **`OrtSession.SessionOptions.setIntraOpNumThreads()`:** a worker szálon
  belül a session maga több CPU magot használhat egyetlen inferencián
  belül — ez különbözik a "több kép párhuzamosan" mintától (ld. M09_L2 §4
  döntés indoklása).
- Minden progress/eredmény callback `runOnUiThread()` / `Handler.post()`
  hívással jut vissza a UI-ra — soha nem közvetlenül a worker szálról
  módosítunk View-t.

---

## 2. Megszakítás (cancellation)

- `AtomicBoolean cancelFlag` — a `[Mégse]` gomb `true`-ra állítja.
- A ciklus **csak képek között** ellenőrzi a flaget (nem szakítja meg
  a folyamatban lévő `session.run()` hívást — az ONNX Runtime Java API
  nem ad biztonságos megszakítási pontot futás közben, és egy inferencia
  legfeljebb pár másodperc, elfogadható várakozás).
- Megszakítás után: a már kész `PointResult`-ok megmaradnak, a
  `SamplingResultCalculator` a rendelkezésre álló (N < teljes) pontokból
  számol — a UI jelzi: **"Részleges eredmény (18/34 pont feldolgozva)"**,
  és a CV%/CI95 számítás ugyanúgy fut, csak kisebb mintaszámmal (ami
  szélesebb konfidencia-intervallumot eredményez — ez helyesen tükrözi a
  csökkent megbízhatóságot, nem kell külön kezelni).

---

## 3. Memóriakezelés

- **Egyszerre 1 bitmap** van dekódolva a memóriában — minden kép
  feldolgozása után explicit `bitmap.recycle()`.
- **Bitmap előfeldolgozás (✅ implementálva, `YoloInferenceEngine.
  decodeDownsampled()`):** a mintaponti fotó natív felbontása (P4P v1:
  5472×3648, ~80 MB teljes dekódolva) nagyságrendekkel nagyobb, mint a
  modell bemenete (320/640 px) — a dekódolás `inSampleSize`-zal történik
  (2-hatvány downsample a célméretig), a maradék skálázást a preprocess
  végzi. Ugyanezt a metódust használják a UI-előnézetek (thumbnail-sáv,
  teljes képernyős dialog) is.
- **Egyetlen `OrtSession` példány** a teljes session feldolgozása alatt
  (nem minden képhez újat létrehozni — a modell betöltése/parseolása
  maga is időt/memóriát igényel).
- **Bemeneti buffer újrafelhasználás:** a preprocess minden képnél új
  `FloatBuffer`-t tölt fel (az `OnnxTensor.createTensor()` az adott
  hívásra épít tenzort) — a `bitmap.recycle()` viszont ugyanúgy minden
  kép után kötelező, ez nem változott.

---

## 4. Részleges hiba kezelése (nem blokkoló)

| Hiba | Kezelés |
|------|---------|
| Egy `point_XXX.jpg` nem dekódolható (sérült fájl) | `onImageError()` → a pont `warning` mezővel kerül a `results.json`-ba, `count = null`, kimarad az átlagszámításból (nem 0-ként számoljuk — a hiányzó adat nem "nulla tő") |
| Az interpreter `run()` kivételt dob egy adott képen | Ugyanúgy kezelve, mint a dekódolási hiba — a ciklus folytatódik a következő képpel |
| Minden kép hibás (pl. rossz modell-formátum már az első képnél kiderül) | Az első 2-3 hiba után a ciklus **leáll** és `ERROR` állapotba vált (nem futtatja végig hiába az összes képet) — küszöb: ha az első 3 kép mindegyike hibázik, feltételezzük, hogy a modell/formátum hibás, nem egyedi képhiba |

---

## 5. Idempotencia és újrafuttathatóság

- A `runOnSession()` **nem módosítja** a `point_*.jpg` fájlokat vagy a
  `session.json`-t — csak olvas, majd a `results.json`-t írja/felülírja.
- Ez lehetővé teszi, hogy ugyanazt a session-t **többször, más modellel**
  is le lehessen futtatni (pl. összehasonlítás céljából) — minden futás
  felülírja az előző `results.json`-t (nincs verziózás v1-ben; ha ez
  igény lesz, a fájlnév kiegészíthető `results_{modelName}.json` mintára).

---

## 6. Konzisztencia a session adatokkal

- A `SamplingResultCalculator` **nem** fogadja el a session.json-t, ha
  a `sample_count` mező (M04-ből) nem egyezik a ténylegesen feldolgozott
  `point_*.jpg` fájlok számával — ez jelezné, hogy a médialetöltés
  hiányos volt (ld. M04 §16 `onSessionWarning` eset), és a torzított
  extrapoláció helyett inkább figyelmeztetést ad: *"A session csak
  {N}/{expected} fotót tartalmaz — az eredmény ennek megfelelően
  torzulhat."*

---

## 7. GSD kalibráció — számítási korrektség és újraszámítás (2026-07-04, TERVEZETT)

Az M09_L1 §10 / M09_L2 §10 tranzakciós és számítási háttere.

### 7.1 A footprint felbontás-invarianciájának levezetése

A kép a valós `ground_width` (m) talajszélességet fedi. Akár az eredeti
(`W_orig` = 5472 px), akár a megjelenített (`W_disp`, pl. 1280 px) felbontásban
mérünk, ugyanazt a footprintet kapjuk:

```
GSD_orig = ground_width / W_orig       GSD_disp = ground_width / W_disp
footprint = GSD_orig × W_orig = GSD_disp × W_disp = ground_width   ✓
```

**Következmény:** a kalibráció bármelyik px-térben elvégezhető, HA a mérés és a
szorzás **ugyanabban** történik. A `GsdRulerView` a megjelenített képen mér, a
`GsdCalibration.compute()` a `displayImageWidthPx`-szel szoroz — konzisztens.
**Hibaforrás, amit el kell kerülni:** ha a vonalzót a megjelenített képen mérnék,
de az eredeti `imageWidthPx`-szel (drónprofil 5472) szoroznánk, a footprint a
downsample-faktorral **elcsúszna** — ezt a `compute()` szignatúrája
(`displayImageWidthPx` paraméter) szándékosan kizárja.

### 7.2 Az újraszámítás nem érinti az inferenciát

- A kalibráció-újraszámítás (`RECOMPUTING`, M09_L3) **csak** a `PointResult`
  `footprintAreaM2` / `densityPerM2` / `densityPerHa` mezőit és a belőlük
  aggregált `SamplingCountResult`-ot írja át — a `count` és `detections`
  **változatlan** (a footprint a darabszámot nem befolyásolja, M09_L2 §10.4).
- Ezért az újraszámítás **olcsó és determinisztikus** (tiszta aritmetika,
  N ≤ ~60 pont), main thread-en futhat, nem kell háttérszál vagy `OrtSession`.

### 7.3 Idempotencia kalibráció mellett

- A kalibráció a `results.json`-t írja felül (a `session.json` misszió-metaadata
  érintetlen — M09_L2 §10.5), így a session **eredeti** magasság/terület adata
  megmarad; a kalibráció bármikor visszavonható „EXIF módra” állítással.
- Többszöri kalibráció: minden alkalmazás a **legutóbbi** footprint-forrásból
  számol újra (nem halmozódik) — a `footprint_source` + nyers mérési mezők
  (`ref_distance_m`, `ref_pixel_length`) rögzítik, honnan jött az érték.

### 7.4 Az FPC és a pontonként eltérő footprint

A jelenlegi `N_plot = totalAreaM2 / footprintAreaM2` (M09_L1 §5.3) egyetlen
footprintet feltételez. Ha a footprint pontonként eltér (képenkénti kézi mód),
az `N_plot`-hoz a pontonkénti footprintek **átlagát** használjuk
(`totalAreaM2 / mean(footprint_i)`) — ez a mintavételi arány konzisztens
közelítése heterogén footprint mellett is. (A pontonkénti `densityPerM2` viszont
mindig a saját footprintjéből számol — csak az FPC skalár közelítése átlagol.)

---

## 8. GSD-tudatos csempézés — futásidő, memória, cross-tile NMS (2026-07-04, TERVEZETT)

Az M09_L1 §11 / M09_L3 GSD-tudatos futtatás tranzakciós háttere.

### 8.1 Futásidő — a csempézés N-szeres inferenciát jelent

- A resized kép (trainGsd-en) `ceil(W'/stride) × ceil(H'/stride)` csempére bomlik.
  Pl. 5472 px @ 0,3 cm/px → 1 cm-re méretezve 1642 px → 640-es csempe, 20%
  átfedés (stride 512) → ⌈1642/512⌉² ≈ 4×3 = **~12 csempe/kép**.
- Ez képenként ~12× annyi `OrtSession.run()`, mint a mostani egyetlen inferencia.
  A Crystal Sky gyenge CPU-ján ez érdemi lassulás (a jelenlegi ~pár mp/kép
  → tízesével nő) — de a mintavételnél kevés a kép (20–50), és a helyes
  eredmény ezt megéri. **Progress-jelzés csempe-szinten is** (pl. „kép 3/34,
  csempe 5/12"), hogy a felhasználó lássa a haladást.
- **A szekvenciális elv marad** (M09_L2 §4): egyszerre egy csempe a memóriában,
  egy `OrtSession`, nem párhuzamos.

### 8.2 Memória — egyszerre egy csempe + a resized kép

- A resized kép (pl. 1642×1095 ARGB ≈ 7 MB) a feldolgozás alatt a memóriában
  van; ebből vágjuk ki egyenként a csempéket (inputSize×inputSize ≈ 1,6 MB),
  minden csempe után `recycle()`. A resized kép a kép végén `recycle()`.
- **Dekódolási stratégia:** a natív fotót `inSampleSize`-zal a resized célméret
  KÖZELÉBE dekódoljuk (2-hatvány), majd pontos `f`-re finomítjuk — így soha nem
  dekódoljuk a teljes 5472×3648-at (OOM-védelem, a meglévő
  `decodeDownsampled()` elvének kiterjesztése).

### 8.3 Cross-tile NMS — a duplikátumok korrektsége

- A 20%-os átfedés miatt egy csempehatáron ülő tő **két csempében is**
  detektálódhat. A csempe-lokális detekciókat a **resized kép globális**
  koordinátáira vetítjük (tx/ty offset), majd **egyetlen globális NMS**-t
  futtatunk az `iouThreshold`-dal — ez szűri a határ-duplikátumokat.
- **Fontos:** a NMS a resized kép koordinátáiban (0–1 normalizálva a resized
  kép méretére) fut, nem csempénként — csak így ismeri fel a két szomszédos
  csempe azonos tövét.
- Az átfedés (20%) elég nagy kell legyen, hogy egy egész tő elférjen a
  határsávban (különben mindkét csempében csonka a bbox, és az NMS nem
  ismeri fel a duplikátumot). A 20% a P4P mintaponti tő-méretéhez terepi
  validációval hangolható.

### 8.4 A GSD-mérés mint futtatási előfeltétel (állapotgép)

- A `[Számlálás indítása]` csak akkor enged futni, ha van érvényes
  session-GSD (`GSD_REQUIRED` kapu, M09_L3). A GSD forrása: mért (GsdRulerView,
  futtatás előtt) vagy EXIF-fallback.
- Egy **GSD-változás újrafuttatást** igényel (nem csak density-újraszámítást,
  szemben a §7.2 footprint-újraszámítással) — mert a resize-skála, tehát a
  detektálás is változik. A UI ezt jelezze: „A GSD módosult — újra kell futtatni
  a számlálást."
