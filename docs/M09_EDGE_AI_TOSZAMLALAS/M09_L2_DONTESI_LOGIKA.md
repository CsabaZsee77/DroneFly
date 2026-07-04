# L2 – Döntési Logika – Edge AI Tőszámlálás

**Modul:** M09
**Szint:** L2 – Döntési Logika
**Verzió:** v1.0.0 (implementálva)
**Létrehozva:** 2026-07-02
**Utolsó módosítás:** 2026-07-03
**Státusz:** ✅ Implementálva

---

## 1. Inferencia keretrendszer választása

| Szempont | TensorFlow Lite | ONNX Runtime Mobile |
|----------|-----------------|----------------------|
| Android 5.1 (API 22) kompatibilitás | ✅ API 21+ támogatott | ✅ API 21+ támogatott |
| Illeszkedés a tényleges modell-előállítási folyamathoz | ❌ **konverzió szükséges** — a Dronterápia rendszer natívan **ONNX**-et állít elő a betanításkor, tflite-hoz ONNX→TF→TFLite konverzió kellene (op-támogatási rések, NCHW↔NHWC layout, kvantálási eltérések — YOLO-fejnél gyakran törékeny) | ✅ **nincs konverzió** — a Dronterápia kimenete közvetlenül betölthető |
| Java API érettsége | `org.tensorflow:tensorflow-lite` + `tensorflow-lite-support` — sok Android object-detection minta | `onnxruntime-android` (`ai.onnxruntime`) — működik, kevesebb Android-specifikus minta kód, de a postprocess/NMS logika platform-független (Java-oldali, nem a futtatókörnyezet feladata) |
| APK méret hozzáadás | ~1–2 MB (csak CPU delegate) | ~3–5 MB |
| CPU-only teljesítmény | Jó, ARM-optimalizált (XNNPACK) | Jó, hasonló (beépített CPU EP) |
| GPU/NNAPI delegate | Opcionális, de Crystal Sky-on **nem támogatott biztonsággal** (NNAPI API 27+ kell, Crystal Sky API 22) | Ugyanez a korlát |

**Döntés (2026-07-03, felülvizsgálva): ONNX Runtime Mobile, CPU-only.**

**Indoklás — miért fordult meg a korábbi TFLite-döntés:** az eredeti (2026-07-02-i)
döntés azon a feltételezésen alapult, hogy a modelleket Ultralytics YOLO
`tflite` exporttal állítják elő. Ez téves feltételezés volt — a **Dronterápia
rendszer ténylegesen ONNX-et tanít be** (ld. `Dronterápia/models/*.onnx`),
ez a valós, már működő modell-előállítási folyamat. Ha az app TFLite-ot
követelne meg, minden egyes betanított modellhez szükség lenne egy
ONNX→TF→TFLite konverziós lépésre — ez YOLO-fejű modelleknél (egyedi opok,
NCHW↔NHWC réteg-sorrend eltérés, kvantálási különbségek) tapasztalat szerint
törékeny, és állandó karbantartási terhet jelentene minden újratanításnál.
Az ONNX Runtime Mobile ezt a konverziós lépést teljesen kiiktatja — a
Dronterápia kimenete közvetlenül, módosítás nélkül betölthető a tableten.

**Mi marad változatlan:** az Ultralytics export (akár `tflite`, akár `onnx`
formátumban) ugyanazt a kimeneti tenzor-konvenciót használja
(`[1, 4+nc, N]`), tehát a Java-oldali postprocess/NMS logika (M09_L2 §5)
gyakorlatilag változatlan marad — csak a modell **betöltése és futtatása**
(`OrtSession` az eddigi TFLite `Interpreter` helyett) és a **bemeneti
tenzor rétegsorrendje** változik: az Ultralytics ONNX export alapértelmezetten
**NCHW** (csatorna-előbb) bemenetet vár, szemben a tflite export NHWC
(csatorna-utolsó) konvenciójával — a preprocess-lépésnek ezt figyelembe
kell vennie.

---

## 2. Modell méret és felbontás választása

| Modell | Paraméterszám | Becsült inferencia idő (Crystal Sky, 1 kép, 320×320, CPU) |
|--------|---------------|-----------------------------------------------------------|
| YOLOv8n / YOLO11n (nano) | ~3 M | **Ajánlott** — becsülhetően 1–3 mp/kép |
| YOLOv8s / YOLO11s (small) | ~11 M | Elfogadható, de 3–6 mp/kép várható — 30–50 mintaponton 3–5 perc összesen |
| YOLOv8m+ (medium+) | 25 M+ | **Nem ajánlott** Crystal Sky-on — várhatóan túl lassú, és a régi Android 5.1 + korlátozott RAM miatt OOM-kockázat |

**Döntés:** az app dokumentációja és a modell-import UI **nano vagy small**
méretű, INT8-kvantált modellt javasol; a `ModelMetadata` sidecar JSON-ban az
`inputSize` mező (pl. 320 vagy 640) írja elő a preprocess célméretét.

**Bemeneti felbontás:** 320×320 ajánlott alapértelmezés (gyorsabb, és a
mintaponti fotó eleve nagy felbontású/nagy GSD, tehát a downscale nem
veszélyezteti a detektálhatóságot — a cél a darabszám, nem a pixel-pontos
határvonal). 640×640 választható, ha a modellt úgy tanították.

**Terepi validáció szükséges lesz** (nincs mért adat Crystal Sky-on) — a
fenti idők egyelőre becslések, eszközön még nem mértük.

---

## 3. Modell-kezelés (import, validáció, kiválasztás)

### 3.1 Hol tárolódnak a modellek

```
/sdcard/DroneFly/models/
  ├── corn_yolo11n_v3.onnx
  ├── corn_yolo11n_v3.json        ← sidecar metaadat (azonos névtő)
  ├── sunflower_yolov8n_v1.onnx
  └── sunflower_yolov8n_v1.json
```

A Dronterápia rendszer modell-regisztere (`Dronterápia/models/*.onnx`) már
tartalmaz valós, betanított modelleket (pl. kukorica tőszámláló) — ezekhez
egyelőre kézzel kell elkészíteni a sidecar `.json`-t (3.2), mert a
Dronterápia oldalon a modell-metaadat jelenleg más formában (adatbázis/
modell-registry) tárolódik, nem ebben a sidecar-sémában.

**Döntés: nincs bundle-özött (APK-ba épített) modell.** A tőszámláló modell
növénykultúránként (kukorica, napraforgó, stb.) és régiónként (fajta,
sortáv) eltérő, gyakran a felhasználó saját betanított modellje —
hardcoded modell az APK-ban feleslegesen növelné a méretet és gyorsan
elavulna. Az app csak **futtatókörnyezet**, a modell külső, cserélhető fájl.

### 3.2 Sidecar metaadat (ModelMetadata)

```json
{
  "label": "Kukorica tőszámláló v3",
  "cropType": "corn",
  "inputSize": 320,
  "confThreshold": 0.35,
  "iouThreshold": 0.45,
  "targetClassIndex": 0,
  "classNames": ["corn_plant"],
  "trainGsdCmPx": 1.0
}
```

**`trainGsdCmPx` (új mező, 2026-07-04, M09_L1 §11.4):** a GSD, amelyre méretezett
csempéken a modell tanult (cm/px). A GSD-tudatos futtatás ehhez méretezi a
mintaképet a detektálás előtt. **A Dronterápia webes oldalon** kell a
betanításkor rögzíteni (külön egyeztetés). **Ha hiányzik a sidecar-ból:** a
rendszer **1,0 cm/px-t feltételez**, és a UI jelzi, hogy ez feltételezés (a
jelenlegi `kukorica_640px` modellhez a valós érték nem ismert).

**Döntés:** ha a `.json` hiányzik egy `.onnx` mellett, az app **nem**
próbál találgatni (nincs "ésszerű alapértelmezés" egy ismeretlen kimeneti
formátumú modellhez) — hibaüzenetet ad: *"Hiányzó metaadat: {fájlnév}.json
— hozz létre egy sidecar JSON-t (ld. dokumentáció)."* Ez a "fail loudly"
elv követi a projekt általános hibakezelési szemléletét (ld. M04 SD kártya
ellenőrzés — inkább blokkoló figyelmeztetés, mint csendes hibás működés).

### 3.3 Kimeneti formátum feltételezés

**Döntés:** a v1 kizárólag az Ultralytics YOLOv8/YOLO11 `export
format=onnx` szabvány kimenetét támogatja: egyetlen output tensor,
alakja `[1, 4+nc, N]` (N = anchor-jelöltek száma, pl. 2100 @ 320×320),
ahol az első 4 sor a bbox (cx, cy, w, h), a további `nc` sor pedig
osztályonkénti konfidencia. Ez a **de facto szabvány** az Ultralytics
export pipeline-ból (a Dronterápia is ezt használja) — más forrásból
(pl. kézzel exportált TF/PyTorch modell, YOLOv5 legacy formátum) származó
`.onnx` fájlok **nem garantáltan** kompatibilisek, és ezt a dokumentációban
(felhasználói súgó szinten is) jelezni kell.

**Bemeneti tenzor rétegsorrend (NCHW):** az Ultralytics ONNX export
alapértelmezett bemenete `[1, 3, inputSize, inputSize]` (csatorna-előbb),
szemben a korábbi tflite-tervezet NHWC feltételezésével — a preprocess
lépés (`YoloInferenceEngine.preprocess()`) ezt a rétegsorrendet állítja elő.

---

## 4. Feldolgozási szekvencia — szekvenciális, nem párhuzamos

**Döntés: a képek feldolgozása szigorúan szekvenciális** (egy `OrtSession`
példány, egyszerre egy kép a memóriában), **nem** párhuzamos szálakon.

**Indoklás:**
- Crystal Sky korlátozott RAM-ja mellett egyszerre több nagy felbontású
  bitmap + több `OrtSession` példány OOM-kockázatot jelentene.
- Az ONNX Runtime session maga is beállítható több szálas
  (`SessionOptions.setIntraOpNumThreads()`) CPU-kernel-végrehajtásra
  egyetlen inferencián belül — ez ad érdemi gyorsítást anélkül, hogy több
  képet kellene egyszerre memóriában tartani.
- 20–50 mintaponton a szekvenciális feldolgozás így is percek alatt
  lefut (ld. 2. fejezet becslés), ami megfelel az "azonnali" elvárásnak.

**Végrehajtás:** egyetlen háttérszálú `ExecutorService` (1 worker thread),
minden kép feldolgozása után `Handler.post()` a progress UI frissítéséhez
a main thread-en. A bitmap `recycle()`-özése minden kép után kötelező.

---

## 5. Célosztály és zajszűrés

**Döntés:** ha a `modelMeta.classNames` mérete > 1 (több osztályos modell,
pl. "növény" + "gyom"), a számláláshoz **csak** a
`modelMeta.targetClassIndex` szerinti osztály detektálásait vesszük
figyelembe. Ha a felhasználó futás előtt módosítja a UI-ban a célosztályt
(pl. gyomborítottság becsléséhez ugyanazzal a modellel), az felülírja a
sidecar alapértelmezést — de a session `results.json`-ban rögzíteni kell,
melyik osztályt számoltuk.

**NMS (Non-Max Suppression):** a `iouThreshold` alapján Java-oldali NMS
szükséges (az Ultralytics `.onnx` export önmagában nem tartalmaz NMS
lépést, csak a raw detekciós tenzort adja vissza) — ez a
`YoloInferenceEngine` felelőssége, nem a modell fájlé.

---

## 6. Hibakezelési döntések

| Helyzet | Döntés |
|---------|--------|
| Egy kép nem tölthető be (sérült fájl) | Kihagyás, ⚠ jelölés a pontnál, a többi pont feldolgozása folytatódik |
| A session.json nem tartalmaz `sample_altitude_m`-et (régi, M09 előtti session) | Blokkoló dialog: kérje be kézzel a magasságot, mielőtt számol — **nem** hallgatólagos alapérték |
| A kiválasztott modellhez nincs `.json` sidecar | Hibaüzenet, nem fut le a számítás (ld. 3.2) |
| A modell kimeneti tenzor alakja nem `[1, 4+nc, N]` | Hibaüzenet: "Nem támogatott modell-kimenet — csak Ultralytics YOLOv8/11 onnx export támogatott" |
| Felhasználó megszakítja futás közben | A már feldolgozott pontok eredménye megmarad, "Részleges eredmény (18/34 pont)" felirattal megjeleníthető |
| CV% > 30% (heterogén tábla gyanú) | Nem blokkol, csak figyelmeztető szöveg az összefoglalóban (ld. M09_L1 §5.3) |

---

## 7. Extrapolációs módszer — v1 vs jövőbeli

**Döntés:** a v1 a mintavételi statisztikán (átlag, szórás, CV%) alapuló
extrapolációt használja, **véges populáció korrekcióval (FPC)** és
**Student-t eloszlással** a 95%-os konfidenciaintervallumhoz (M09_L1 §5.3,
2026-07-03-i pontosítás). **Nincs** térbeli interpoláció (IDW/kriging) a
tableten.

**Indoklás — miért mintavételi statisztika, és nem térbeli interpoláció:**
a felhasználói igény ("azonnal megadni... a teljes területre átszámolt
találatokat, és mekkora hibával kell számolni") egy **teljes táblára
összesített szám + megbízhatósági sáv**, ami definíció szerint a
mintapontok közötti *szórásból* vezethető le (minél homogénebbek a
mintapontok, annál megbízhatóbb az összesített becslés) — ez klasszikus
design-based sampling inference, nem térbeli interpoláció. A két módszer
más kérdésre válaszol:

| Kérdés | Módszer |
|--------|---------|
| "Összesen kb. mennyi van a táblán, ± mekkora hibával?" | Mintavételi statisztika (FPC + t95, M09_L1 §5.3) — **ez a v1** |
| "A tábla mely része sűrűbb/ritkább — hol mutasson a hőtérkép?" | Térbeli interpoláció (IDW/kriging, M09_L1 §5.4) — **jövőbeli, nem v1** |

**Miért FPC + t-eloszlás, és nem a korábbi fix 1.96-os normál közelítés:**
a fix `1.96 × SE` képlet két helyen egyszerűsít túlzottan: (1) nem veszi
figyelembe, hogy a minta mekkora hányadát fedi a teljes területnek (FPC
korrekció nélkül a CI enyhén szélesebb a valós bizonytalanságnál, ha a
mintavételi arány nem elhanyagolható — pl. sűrű mintavételnél vagy kisebb
táblánál); (2) 15–40 mintaponton (a tipikus mintavételi misszió mérete) a
normál közelítés alábecsüli a bizonytalanságot a Student-t eloszláshoz
képest. Mindkét korrekció olcsó (nincs extra bemenet, csak egy lookup
tábla + egy szorzó), ezért nincs ok kihagyni — nem térbeli
interpoláció-jellegű komplexitás, csak a meglévő statisztika pontosítása.

**IDW-heatmap külön fázisként:** az IDW-alapú vizualizáció (Dronterapia
Python oldalán már létezik) Java-portja jelentős többletmunka
(interpolációs rács, színskála-vizualizáció OSMDroid overlay-ként), és
csak akkor ad érdemi extra értéket, ha a tábla **térben strukturáltan**
heterogén (nem csak véletlenszerűen szóró) és a felhasználónak vizuális
prescription map kell, nem csak összegzett szám. Ezt külön fázisként
érdemes kezelni, csak ha terepi visszajelzés igazolja az igényt (ld.
M09_L1 §8, jövőbeli fázisok).

---

## 8. Fotóimport döntések (M09_L1 §9, 2026-07-03)

| Kérdés | Döntés | Indoklás |
|--------|--------|----------|
| Honnan jön a kontextus (magasság, terület, drón)? | **Repülési terv kiválasztása kötelező** (Spinner a mentett `.flightprogram.json` tervekből), kézi megadás fallback | A terv már tartalmaz minden szükséges adatot validált formában — kézi bevitel hibalehetőségét minimalizálja; pont ez a "kézi GSD-bevitel" korlát, amit a Dronterapia POC-nál azonosítottunk |
| Milyen magasságot vegyünk a tervből? | Mintavételi tervnél `sampling.sample_altitude_m`, grid tervnél `altitude_m` | A fotók a terv szerinti fotózási magasságon készültek |
| Tablet oldali fájlválasztó | **Beépített mappaböngésző**, nem rendszer-picker (`ACTION_GET_CONTENT`) | Crystal Sky (Android 5.1, letisztított DJI-firmware) — nem garantált, hogy van telepített dokumentum-választó; a beépített böngésző mindig működik |
| Pontkoordináták importált fotóknál | **EXIF GPS** kiolvasás képenként (P4P geotaggel minden fotót); ha nincs geotag → 0,0 | A drónfotók megbízhatóan geotaggeltek; a koordináta a táblázathoz/jövőbeli heatmaphez kell, a db/ha számításhoz nem |
| session.json írása import útvonalon | A `PhotoImportActivity` írja (nem a `MediaSessionDownloader`) | Az EXIF-feldolgozás a letöltés UTÁN történik; a downloader `samplePoints == null` esetén kihagyja a json-írást |
| Reprezentativitás | Nem ellenőrizhető — dokumentált felhasználói felelősség | Az app nem tudhatja, hogy a kézzel készített/válogatott fotók lefedik-e a táblát reprezentatívan (M09_L1 §9 megjegyzés) |

---

## 8a. Futtatási paraméterek a tableten (2026-07-03)

**Döntés:** a Dronterapia webes Counting felület paraméterei közül a tabletre
a **konfidencia küszöb** és az **IoU (NMS) küszöb** kerül át állítható
formában (EditText, 0–1 clamp, alapérték a sidecar-ból, használt érték a
`results.json`-ba mentve). A többi webes paraméter **kimarad a v1-ből**:

| Webes paraméter | v1 tablet | Indoklás |
|-----------------|-----------|----------|
| Konfidencia küszöb | ✅ állítható | A detektálási érzékenység alap-hangolása — enélkül a modell nem kalibrálható terepen |
| IoU küszöb (NMS) | ✅ állítható | Sűrű állományban (pl. korai kukorica) kritikus a dupla-detektálás szűréséhez |
| Sliding window + átfedés | ✅ **v1-be került (2026-07-04, ld. M09_L1 §11)** | **A korábbi „későbbi fázis" döntés visszavonva.** Nem információvesztési kérdés, hanem **GSD-konzisztencia**: a modell adott betanítási GSD-hez kötött, ezért a mintaképet a betanítási GSD-re kell méretezni, majd sliding window-val csempézni — enélkül a detektálás a skálahiba miatt megbízhatatlan (csak kézi konfidencia-hangolással „stimmel"). A futásidő-többlet valós, de az eredmény helyessége ezt megéri (M09_L1 §11.7) |
| TTA (Test-Time Augmentation) | ❌ későbbi fázis | 3–8× futásidő-szorzó — Crystal Sky-on percekből tízpercek lennének |
| Sor-detekció + penalty | ❌ későbbi fázis | Python-oldali (numpy) logika portolása; az azonnali szám-becsléshez nem szükséges |
| Preprocessing presetek (ExGreen stb.) | ❌ későbbi fázis | Csak akkor releváns, ha a modellt preset-elt képeken tanították — a Dronterapia modellek jellemzően RGB-n tanulnak |

---

## 9. Modell-terjesztés — jövőbeli, nem v1 hatókör

**Nyitott kérdés (nem v1 döntés, csak rögzítve):** hogyan jutnak el a
betanított `.onnx` modellek a felhasználóhoz? Kézi USB-másolás/ADB push
(v1, egyszerű), vagy a Dronterapia M06 szinkroncsatornán keresztüli
letöltés (jövőbeli). A Dronterápia oldalon **már létezik** modell-registry
(`Dronterápia/models/*.onnx`, adatbázisban nyilvántartva) — a jövőbeli M06
integráció ebből tölthetne le közvetlenül, de ehhez a Dronterápia oldali
modell-metaadatot át kellene alakítani a DroneFly sidecar-sémára (3.2), és
validálni kellene, hogy az adott modell mobil ONNX Runtime-mal futtatható
(opset-verzió, egyedi opok). A v1 kizárólag a kézi másolást (fájlkezelőn
vagy `adb push`-sal `/sdcard/DroneFly/models/`-be) támogatja — ez nem
igényel új kódot, csak dokumentált felhasználói lépés.

---

## 10. GSD kalibráció — döntési logika (2026-07-04, TERVEZETT)

Az M09_L1 §10 üzleti folyamat döntési háttere. A cél: a footprint területet a
következtetett (magasság-alapú) helyett **mért** skálából határozni meg, mert a
P4P barometrikus magasság-hibája alacsony repülésen a db/ha becslést
elronthatja (M09_L1 §10.1).

### 10.1 Miért vonalzós mérés, és nem automatikus sor-detektálás?

| Opció | Döntés | Indoklás |
|-------|--------|----------|
| **Kézi vonalzó** (választott) | ✅ | Robusztus, felhasználó által ellenőrizhető, minden növénykultúrán/stádiumban működik; a felhasználó bízik a saját mérésében |
| Automatikus sortáv-detektálás | ❌ v1 | Törékeny (gyomok, hiányos sorok, korai állomány alig látszik) — a fő hibaforrás cseréje egy másik bizonytalan lépésre nem javít |
| GCP / ismert méretű tárgy a képen | 🔲 opcionális kiegészítés | Ugyanaz a vonalzó-mechanizmus, csak más referencia — nem külön funkció |

### 10.2 A referencia-távolság szabályai (korrektség)

| Szabály | Döntés | Következmény, ha megsértik |
|---------|--------|----------------------------|
| Vízszintes talaj-távolság | Kötelező (UI-súgó figyelmeztet) | Függőleges tárgy (növénymagasság) nadírból pontnak látszik → értelmetlen skála |
| Hosszú alapvonal (több egység) | Ösztönzött (egység × darabszám bevitel) | Egyetlen sortáv pixelhibája nagy relatív GSD-hibát ad |
| Számítás a megjelenített felbontáson | Döntés: méter/megjelenített-px skála | A footprint felbontás-invariáns (M09_L4 §7) — nincs szükség eredeti-felbontásra konvertálásra, de a *következetesség* kötelező (ugyanabban a px-térben mérni és szorozni) |
| Mérés a képközépre szimmetrikusan | Középkereszt + figyelmeztetés, ha a vonal a szélek felé csúszik | Nadír+sík+ideális lencsénél a GSD állandó, de a valódi objektív-torzítás (sarkok), maradék gimbal-dőlés és növénymagasság-parallaxis a széleken hibát ad; a középre szimmetrikus hosszú vonal egyszerre pixelpontos és optikailag a legkevésbé torzított (M09_L1 §10.3 4. szabály) |

### 10.3 A footprint-mód közti választás (2026-07-04 terepi megfigyelés után)

**Döntés:** a session alapértelmezett módja **EXIF** marad (nincs regresszió,
amíg a felhasználó nem kalibrál), és minden pontnak lehet **egyedi felülírása**
(`footprintSource` a `PointResult`-on).

**A hiba jellege ÚJRA NYITOTT (M09_L1 §10.4/§10.9):** az „erratikus" megfigyelés
IMU-kalibráció ELŐTT történt; azóta a magasság-tartás vélhetően javult. Ezért
**egyik módot sem zárjuk ki előre** — a footprint-mód **mérés-vezérelt**:

1. Az első lépés mindig az **EXIF-kereszt-ellenőrzés** (§10.4c) egy poszt-IMU-cal
   repülés képein — ez adja a diagnózist.
2. Az eredménye választ: kicsi eltérés → EXIF elég; rendszeres → lehorgonyzott;
   sima drift → interpoláció; erratikus → képenkénti kézi.

**A tervezési hangsúly ezért kettős:** (a) a diagnosztikai kereszt-ellenőrzés
legyen az elsődleges kimenet (mindig kiírja a mért vs. EXIF eltérést), és (b) a
képenkénti kézi mód — mint a biztos tartalék — legyen ergonomikus (§10.3b:
állandó referencia + gyors képlépegetés).

### 10.3b A képenkénti mód ergonómiai döntései

| Kérdés | Döntés | Indoklás |
|--------|--------|----------|
| Referencia-távolság perzisztálása | A megadott érték (sortáv × darab) **megmarad** a következő képre | A sortáv a tábla egészén állandó — csak a vonalat kell újrahúzni |
| Képek közti lépés | „Következő kalibrálatlan képre" gomb a kalibrációs nézetben | Ne kelljen a táblázatba visszalépni képenként |
| Outlier-megjelölés | Az EXIF-kereszt-ellenőrzés (M09_L1 §10.6) megjelöli a gyanús EXIF-ű pontokat | A felhasználó csak az eltérőket kalibrálja, nem mind a 30-at |
| Nyers mérés tárolása | `ref_distance_m`, `ref_pixel_length`, `gsd_cm_px` a results.json-ba | Reprodukálhatóság + a mérés utólag ellenőrizhető |

### 10.4 A kalibráció időzítése — REVIDEÁLVA (2026-07-04, ld. M09_L1 §11)

> **⚠ A korábbi „számlálás UTÁN kalibrálunk" döntés módosítva.** Az akkori
> indoklás azon állt, hogy a footprint csak a *sűrűséget* befolyásolja, a
> *darabszámot* nem. Ez a **naiv teljes-kép-resize** mellett igaz volt, de a
> GSD-tudatos csempézés (M09_L1 §11) mellett a GSD a **detektálási resize-skálát
> is** meghatározza → befolyásolja a darabszámot. Ezért:

**Új döntés:** a **fő GSD-mérés a futtatás ELŐTT** történik (a betöltött
mintaképeken, nem az eredmény-sorra kattintva). A mért GSD kettős célt szolgál:
(a) a kép átméretezése a betanítási GSD-re a detektáláshoz, (b) a footprint a
sűrűséghez. Egy GSD-változás **újrafuttatást** igényel (nem csak
density-újraszámítást).

**Ami megmaradhat:** a footprint *utólagos* finomítása a sűrűséghez (ha a
felhasználó egy pont footprintjét pontosítja, a density újraszámolható inferencia
nélkül) — de ez másodlagos; a fő GSD-t előre mérjük, mert az a detektálás
feltétele.

**Pragmatikus enyhítés (M09_L1 §11.6):** ha a mért GSD az EXIF-hez közel van
(kereszt-ellenőrzés kicsi eltérést mutat), az EXIF-GSD elég a futtatáshoz — a
YOLO ±20-30% skálát tűr. A mérés a biztonság a nagy barometrikus hiba ellen.

### 10.5 Kerekítés és tárolás

- A mért GSD-t és a footprintet a `results.json` pontonként tárolja
  (`footprint_area_m2`, `footprint_source`, opcionálisan `gsd_cm_px` és a mérés
  nyers adatai: `ref_distance_m`, `ref_pixel_length` a reprodukálhatósághoz).
- A session-szintű `sample_altitude_m` / `aoi_area_m2` (session.json)
  **változatlan** marad — a kalibráció a `results.json`-ban él, nem írja felül
  az eredeti misszió-metaadatot (idempotencia, újrafuttathatóság: M09_L4 §5).
