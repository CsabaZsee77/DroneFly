# L2 – Döntési Logika – Edge AI Tőszámlálás

**Modul:** M09
**Szint:** L2 – Döntési Logika
**Verzió:** v0.1.0 (terv)
**Létrehozva:** 2026-07-02
**Utolsó módosítás:** 2026-07-02
**Státusz:** 🔲 Tervezve

---

## 1. Inferencia keretrendszer választása

| Szempont | TensorFlow Lite | ONNX Runtime Mobile |
|----------|-----------------|----------------------|
| Android 5.1 (API 22) kompatibilitás | ✅ API 21+ támogatott | ✅ API 21+ támogatott |
| Ultralytics YOLO export útvonal | `yolo export format=tflite int8=True` — hivatalos, dokumentált, kvantálást is végez | `format=onnx` — futtatható, de mobil NMS/postprocess kevésbé dokumentált Java oldalon |
| Java API érettsége | `org.tensorflow:tensorflow-lite` + `tensorflow-lite-support` — sok Android object-detection minta | `onnxruntime-android` — működik, de kevesebb Android-specifikus minta kód |
| APK méret hozzáadás | ~1–2 MB (csak CPU delegate) | ~3–5 MB |
| CPU-only (XNNPACK) teljesítmény | Jó, ARM-optimalizált | Jó, hasonló |
| GPU/NNAPI delegate | Opcionális, de Crystal Sky-on **nem támogatott biztonsággal** (NNAPI API 27+ kell, Crystal Sky API 22) | Ugyanez a korlát |

**Döntés: TensorFlow Lite, CPU-only (XNNPACK delegate), INT8 kvantált modell.**

**Indoklás:** a célközönség (a felhasználó és a jövőbeli ügyfelek) jellemzően
Ultralytics YOLO-val (YOLOv8/YOLO11) tanítja a modelleket asztali gépen vagy
felhőben — ennek a `tflite` export célformátuma natívan támogatott, jól
dokumentált, kvantálást is végez a konverzió során. Az ONNX Runtime nem ad
érdemi előnyt cserébe a nagyobb binárisméretért és a kevésbé kitaposott
Android-integrációért.

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

**Terepi validáció szükséges lesz** (nincs mért adat Crystal Sky-on) — az
L1 dokumentum "🔲 Tervezve" státusza ezt tükrözi; a fenti idők becslések.

---

## 3. Modell-kezelés (import, validáció, kiválasztás)

### 3.1 Hol tárolódnak a modellek

```
/sdcard/DroneFly/models/
  ├── corn_yolo11n_v3.tflite
  ├── corn_yolo11n_v3.json        ← sidecar metaadat (azonos névtő)
  ├── sunflower_yolov8n_v1.tflite
  └── sunflower_yolov8n_v1.json
```

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
  "classNames": ["corn_plant"]
}
```

**Döntés:** ha a `.json` hiányzik egy `.tflite` mellett, az app **nem**
próbál találgatni (nincs "ésszerű alapértelmezés" egy ismeretlen kimeneti
formátumú modellhez) — hibaüzenetet ad: *"Hiányzó metaadat: {fájlnév}.json
— hozz létre egy sidecar JSON-t (ld. dokumentáció)."* Ez a "fail loudly"
elv követi a projekt általános hibakezelési szemléletét (ld. M04 SD kártya
ellenőrzés — inkább blokkoló figyelmeztetés, mint csendes hibás működés).

### 3.3 Kimeneti formátum feltételezés

**Döntés:** a v1 kizárólag az Ultralytics YOLOv8/YOLO11 `export
format=tflite` szabvány kimenetét támogatja: egyetlen output tensor,
alakja `[1, 4+nc, N]` (N = anchor-jelöltek száma, pl. 2100 @ 320×320),
ahol az első 4 sor a bbox (cx, cy, w, h), a további `nc` sor pedig
osztályonkénti konfidencia. Ez a **de facto szabvány** az Ultralytics
export pipeline-ból — más forrásból (pl. kézzel exportált TF modell,
YOLOv5 legacy formátum) származó `.tflite` fájlok **nem garantáltan**
kompatibilisek, és ezt a dokumentációban (felhasználói súgó szinten is)
jelezni kell.

---

## 4. Feldolgozási szekvencia — szekvenciális, nem párhuzamos

**Döntés: a képek feldolgozása szigorúan szekvenciális** (egy `Interpreter`
példány, egyszerre egy kép a memóriában), **nem** párhuzamos szálakon.

**Indoklás:**
- Crystal Sky korlátozott RAM-ja mellett egyszerre több nagy felbontású
  bitmap + több `Interpreter` példány OOM-kockázatot jelentene.
- A TFLite `Interpreter` maga is beállítható több szálas (`setNumThreads()`)
  CPU-kernel-végrehajtásra egyetlen inferencián belül — ez ad érdemi
  gyorsítást anélkül, hogy több képet kellene egyszerre memóriában tartani.
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
szükséges (a TFLite `.tflite` export önmagában nem tartalmaz NMS lépést,
csak a raw detekciós tenzort adja vissza) — ez a `YoloInferenceEngine`
felelőssége, nem a modell fájlé.

---

## 6. Hibakezelési döntések

| Helyzet | Döntés |
|---------|--------|
| Egy kép nem tölthető be (sérült fájl) | Kihagyás, ⚠ jelölés a pontnál, a többi pont feldolgozása folytatódik |
| A session.json nem tartalmaz `sample_altitude_m`-et (régi, M09 előtti session) | Blokkoló dialog: kérje be kézzel a magasságot, mielőtt számol — **nem** hallgatólagos alapérték |
| A kiválasztott modellhez nincs `.json` sidecar | Hibaüzenet, nem fut le a számítás (ld. 3.2) |
| A modell kimeneti tenzor alakja nem `[1, 4+nc, N]` | Hibaüzenet: "Nem támogatott modell-kimenet — csak Ultralytics YOLOv8/11 tflite export támogatott" |
| Felhasználó megszakítja futás közben | A már feldolgozott pontok eredménye megmarad, "Részleges eredmény (18/34 pont)" felirattal megjeleníthető |
| CV% > 30% (heterogén tábla gyanú) | Nem blokkol, csak figyelmeztető szöveg az összefoglalóban (ld. M09_L1 §5.3) |

---

## 7. Extrapolációs módszer — v1 vs jövőbeli

**Döntés:** a v1 **kizárólag** az egyszerű átlagsűrűség × teljes terület
képletet használja (M09_L1 §5.3), **nincs** térbeli interpoláció (IDW/
kriging) a tableten.

**Indoklás:** a felhasználói igény ("azonnal megadni... a teljes területre
átszámolt találatokat") elsősorban egy gyors, megbízható **szám**, nem egy
vizuális prescription map. Az IDW-alapú heatmap már létezik a Dronterapia
Python oldalán — ennek Java-portja jelentős többletmunka (interpolációs
rács, színskála-vizualizáció OSMDroid overlay-ként), és csak akkor ad
érdemi extra értéket, ha a tábla **térben strukturáltan** heterogén (nem
csak véletlenszerűen szóró). Ezt külön fázisként érdemes kezelni, csak ha
terepi visszajelzés igazolja az igényt (ld. M09_L1 §8, jövőbeli fázisok).

---

## 8. Modell-terjesztés — jövőbeli, nem v1 hatókör

**Nyitott kérdés (nem v1 döntés, csak rögzítve):** hogyan jutnak el a
betanított `.tflite` modellek a felhasználóhoz? Kézi USB-másolás
(v1, egyszerű), vagy a Dronterapia M06 szinkroncsatornán keresztüli
letöltés (jövőbeli, ha a Dronterapia oldalon lesz modell-registry).
A v1 kizárólag a kézi másolást (fájlkezelőn keresztül `/sdcard/DroneFly/
models/`-be) támogatja — ez nem igényel új kódot, csak dokumentált
felhasználói lépés.
