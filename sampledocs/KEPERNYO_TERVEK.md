# DrónTerápia – Képernyőtervek (UI Mockup)

**Verzió:** v0.3.0 | **Utolsó frissítés:** 2026-03-25
**Típus:** ASCII karakteres képernyő-elrendezés tervek

> A tervek kódblokkban jelennek meg, monospace betűtípussal. Minden panel, gomb, beviteli mező és elrendezés a tényleges Streamlit forráskódból van kielemezve.

---

## Jelölések

| Jel | Jelentés |
|-----|---------|
| `[gomb szövege]` | Kattintható gomb |
| `[szöveg beviteli mező   ]` | Text input |
| `[legördülő lista      ▼]` | Selectbox / Dropdown |
| `0──●───100` | Csúszka (slider), ● = jelenlegi érték |
| `(●) opció` | Kiválasztott radio button |
| `( ) opció` | Nem kiválasztott radio button |
| `[✓] jelölőnégyzet` | Bejelölt checkbox |
| `[ ] jelölőnégyzet` | Üres checkbox |
| `▶ szekció neve` | Összecsukható expander (összecsukva) |
| `🔢 érték` | Csak olvasható metrika |
| `│ │` / `┌─┐└─┘` | Panel / keret határ |
| `║ ║` / `╔═╗╚═╝` | Főablak határ |

---

## 1. Home oldal – Bejelentkezés

*Bejelentkezés előtti állapot (nem autentikált felhasználó)*

```
╔══════════════════════════════════════════════════════════════════════╗
║  🌾 DrónTerápia - Mezőgazdasági Tőszámláló                          ║
║  🔐 Bejelentkezés vagy Regisztráció                                  ║
║  ────────────────────────────────────────────────────────────────── ║
║                                                                      ║
║    ╔═══════════════════════════════════════════════════════════╗     ║
║    ║                                                           ║     ║
║    ║  ┌──────────────────────┬───────────────────────────┐    ║     ║
║    ║  │ 🔑 Bejelentkezés     │   📝 Regisztráció          │    ║     ║
║    ║  └──────────────────────┴───────────────────────────┘    ║     ║
║    ║                                                           ║     ║
║    ║  Bejelentkezés                                           ║     ║
║    ║                                                           ║     ║
║    ║  Felhasználónév                                          ║     ║
║    ║  ┌───────────────────────────────────────────────────┐  ║     ║
║    ║  │  Add meg a felhasználóneved                        │  ║     ║
║    ║  └───────────────────────────────────────────────────┘  ║     ║
║    ║                                                           ║     ║
║    ║  Jelszó                                                  ║     ║
║    ║  ┌───────────────────────────────────────────────────┐  ║     ║
║    ║  │  ••••••••••••                                      │  ║     ║
║    ║  └───────────────────────────────────────────────────┘  ║     ║
║    ║                                                           ║     ║
║    ║  ┌───────────────────────────────────────────────────┐  ║     ║
║    ║  │              🔓 Bejelentkezés                      │  ║     ║
║    ║  └───────────────────────────────────────────────────┘  ║     ║
║    ║                                                           ║     ║
║    ║  💡 Első látogatás? Regisztrálj a 'Regisztráció' fülön!  ║     ║
║    ║                                                           ║     ║
║    ╚═══════════════════════════════════════════════════════════╝     ║
║                                                                      ║
║  v0.2.0 | © 2025-2026 DrónTerápia                                   ║
╚══════════════════════════════════════════════════════════════════════╝
```

---

## 2. Home oldal – Bejelentkezve

*Bejelentkezett állapot – sidebar + fő tartalom*

```
╔══════════════════╦═══════════════════════════════════════════════════════╗
║  SIDEBAR         ║  FŐOLDAL                                              ║
║──────────────────║                                                        ║
║  👤 Felhasználó  ║  🌾 DrónTerápia - Egy-két-há', Itt a határ!          ║
║  ┌─────────────┐ ║  ──────────────────────────────────────────────────── ║
║  │Zsigmond Cs. │ ║                                                        ║
║  │📧 email@g.. │ ║  🚀 Workflow                                           ║
║  │@zsigmond    │ ║                                                        ║
║  └─────────────┘ ║  ┌──────────────┐    ┌──────────────┐    ┌──────────┐ ║
║                  ║  │      1       │    │      2       │    │    3     │ ║
║  ▶ 🔑 Jelszó     ║  │ 🎨 Annotation│ →  │ 🎓 Training  │ →  │🔍 Counting║
║     Módosítás    ║  │              │    │              │    │          │ ║
║                  ║  │• TIFF tiling │    │• Modell      │    │• Kép fel-│ ║
║  ┌─────────────┐ ║  │• BBox rajzolás    │  training    │    │  töltés  │ ║
║  │ 🚪 Kilépés  │ ║  │• Osztályok   │    │• Hyperparams │    │• Inference║
║  └─────────────┘ ║  │• YOLO export │    │• Monitoring  │    │• Export  │ ║
║                  ║  └──────────────┘    └──────────────┘    └──────────┘ ║
║  ── Verzió ───── ║                                                        ║
║  📦 v0.2.0       ║  ──────────────────────────────────────────────────── ║
║                  ║  📊 Statisztikák                                       ║
║  ── Rendszer ─── ║                                                        ║
║  Python 3.11+    ║  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐ ║
║  Streamlit 1.52  ║  │📦 Tile-ok│ │✅ Annotált│ │🤖 Model.│ │📈 Haladás║
║  ONNX 1.20       ║  │   120    │ │    45    │ │    3    │ │   37%   │ ║
║  YOLO 8.x        ║  └──────────┘ └──────────┘ └──────────┘ └──────────┘ ║
║                  ║                                                        ║
║  ── Kapcsolat ── ║  ──────────────────────────────────────────────────── ║
║  ┌─────────────┐ ║  ⚡ Gyors Műveletek                                    ║
║  │✉️ Levél a   │ ║                                                        ║
║  │ fejlesztőnek│ ║  ┌─────────────────────────┬──────────────────────┐  ║
║  └─────────────┘ ║  │ 🔄 Legutóbbi tevékenység│ 📢 Értesítések        │  ║
║                  ║  │                         │                      │  ║
║                  ║  │ • tile_0045.png          │ 📝 75 tile vár       │  ║
║                  ║  │ • tile_0044.png          │ ✅ Elegendő adat     │  ║
║                  ║  │ • tile_0043.png          │ 🤖 3 modell kész     │  ║
║                  ║  └─────────────────────────┴──────────────────────┘  ║
╚══════════════════╩═══════════════════════════════════════════════════════╝
```

---

## 3. Annotation oldal

*Háromoszlopos elrendezés: Projects | Annotálás (canvas) | Versions*

```
╔════════════════════╦════════════╦════════════════════════════════╦═════════════╗
║ SIDEBAR            ║ PROJEKTEK  ║  ANNOTÁLÁS                     ║  VERZIÓK   ║
║────────────────────║────────────║────────────────────────────────║─────────────║
║ ⚙️ Csempézési      ║ 📁 Projects║ 🎨 Annotation                  ║             ║
║ Beállítások        ║            ║ Dataset annotálás és export    ║ 📦 Versions ║
║                    ║[➕ Új Proj.]║                                ║             ║
║ Átfedés (%)        ║            ║ ┌──────────────────────────┐  ║[➕ Új Verzió║
║ 0─────●────── 50   ║ ┌────────┐ ║ │ Workflow mód:            │  ║             ║
║  25%               ║ │Kukorica│ ║ │ (●) 🖼️ Meglévők          │  ║ ┌─────────┐ ║
║                    ║ │2026    │ ║ │ ( ) ➕ Új kép + csempézés │  ║ │   v1    │ ║
║ Tile formátum      ║ │        │ ║ └──────────────────────────┘  ║ │ 180 kép │ ║
║ [PNG          ▼]   ║ │120 tile│ ║                                ║ │ 80/15/5%│ ║
║                    ║ │ 45 ann.│ ║ Osztály: [kukorica_to     ▼]  ║ │         │ ║
║ ── Képmanipuláció ─║ │        │ ║                                ║ │[🎓 Train]║
║ ✨ Preset          ║ │[📂Megnyit║ ║ Csempe 5 / 120                ║ └─────────┘ ║
║ [None         ▼]   ║ └────────┘ ║                                ║             ║
║                    ║            ║ ┌──────────────────────────┐  ║ ┌─────────┐ ║
║ ── Rajzolás ───────║ ┌────────┐ ║ │                          │  ║ │   v2    │ ║
║ 🎨 Vonal v.: 3     ║ │Repce   │ ║ │                          │  ║ │ 220 kép │ ║
║ 0──●──── 10        ║ │2026    │ ║ │   🖼️ [csempe képe]        │  ║ │ 80/15/5%│ ║
║                    ║ │ 45 tile│ ║ │                          │  ║ │         │ ║
║ Szín: [██ piros]   ║ │  0 ann.│ ║ │   (drawable canvas)     │  ║ │[🎓 Train]║
║                    ║ │        │ ║ │                          │  ║ └─────────┘ ║
║ ── Osztályok ──────║ │[📂Megnyit║ ║ │   [húzd az egeret →    │  ║             ║
║ 🏷️ Osztályok       ║ └────────┘ ║ │    bounding box rajzol] │  ║             ║
║ [repce_to      ]   ║            ║ └──────────────────────────┘  ║             ║
║ [➕ Hozzáadás]      ║            ║                                ║             ║
║                    ║            ║ [◀️ Előző]   [Következő ▶️]   ║             ║
║ Meglévő osztályok: ║            ║                                ║             ║
║ 0: kukorica_to     ║            ║ [💾 Annotáció mentése]         ║             ║
║ 1: repce  [🗑️]     ║            ║                                ║             ║
║                    ║            ║ ✅ Annotáció mentve: 7 obj.    ║             ║
║                    ║            ║                                ║             ║
║                    ║            ║ ─────────────────────────────  ║             ║
║                    ║            ║ [💾 Mentés másként...]          ║             ║
╚════════════════════╩════════════╩════════════════════════════════╩═════════════╝
```

**Annotation – Új kép + csempézés mód:**

```
┌────────────────────────────────────────────────────────────┐
│  Workflow mód: ( ) 🖼️ Meglévők  (●) ➕ Új kép + csempézés │
├────────────────────────────────────────────────────────────┤
│                                                            │
│  Válassz egy TIFF fájlt:                                   │
│  ┌────────────────────────────────────────────────────┐   │
│  │  Húzz ide egy fájlt, vagy kattints a tallózáshoz   │   │
│  │  Limit: 200MB / TIFF, TIF                          │   │
│  └────────────────────────────────────────────────────┘   │
│                                                            │
│  📄 Fájl: KukoricaDron_2026.tif   (45.2 MB)              │
│                                                            │
│  ┌────────────────────────────────────────────────────┐   │
│  │           🔪 Csempézés és hozzáadás                 │   │
│  └────────────────────────────────────────────────────┘   │
│                                                            │
│  ████████████████░░░░░░░░░░░  68% – 82 / 120 csempe       │
│                                                            │
│  ✅ 120 csempe létrehozva!                                 │
└────────────────────────────────────────────────────────────┘
```

---

## 4. Training oldal

*Teljes szélességű, vertikális szekciók*

```
╔══════════════════════════════════════════════════════════════════════════╗
║  🎓 Training                                                             ║
║  YOLOv8/v11 modell betanítás annotált adatokkal                         ║
║  ──────────────────────────────────────────────────────────────────────  ║
║                                                                          ║
║  📂 1. Dataset Választás                                                 ║
║  ┌──────────────────────────────────────────────────────────────────┐  ║
║  │  Válassz Training Version-t:                                      │  ║
║  │  [📦 Kukorica 2026 - v1                                        ▼] │  ║
║  │                                                                   │  ║
║  │  ┌────────────┐  ┌────────────┐  ┌────────────┐  ┌────────────┐  │  ║
║  │  │Total képek │  │   Train    │  │    Val     │  │    Test    │  │  ║
║  │  │    180     │  │    144     │  │     27     │  │     9      │  │  ║
║  │  └────────────┘  └────────────┘  └────────────┘  └────────────┘  │  ║
║  │                                                                   │  ║
║  │  ℹ️ 📦 Detection Training – Bounding box alapú objektum detektálás│  ║
║  │  ▶ 📋 Training Version információk                                │  ║
║  └──────────────────────────────────────────────────────────────────┘  ║
║                                                                          ║
║  ⚙️ 2. Training Konfiguráció                                             ║
║  ┌───────────────────────────────────┬────────────────────────────────┐ ║
║  │  Alap beállítások                 │  Optimizer & Learning Rate     │ ║
║  │                                   │                                │ ║
║  │  YOLO verzió:                     │  Optimizer:                    │ ║
║  │  [YOLOv11                     ▼]  │  [AdamW                    ▼]  │ ║
║  │                                   │                                │ ║
║  │  Modell méret:                    │  Learning rate (kezdő):        │ ║
║  │  [Nano (leggyorsabb, CPU-hoz  ▼]  │  [0.001                      ] │ ║
║  │                                   │                                │ ║
║  │  Epochs (tanítási körök):         │  Early stopping patience:      │ ║
║  │  10 ──────────────────●─── 300   │  10 ─────────────────●── 100   │ ║
║  │  300 epoch                        │  100 epoch                     │ ║
║  │                                   │                                │ ║
║  │  Batch size:                      │                                │ ║
║  │  [ 4 ] [8✓] [ 16 ] [ 32 ]        │                                │ ║
║  │                                   │                                │ ║
║  │  Kép méret (px):                  │                                │ ║
║  │  [640                         ▼]  │                                │ ║
║  └───────────────────────────────────┴────────────────────────────────┘ ║
║                                                                          ║
║  ▶ ⚙️ Haladó beállítások (Data Augmentation & Regularizáció)            ║
║                                                                          ║
║  📂 3. Dataset Felosztás                                                 ║
║  ┌─────────────────────┐  ┌─────────────────────┐  ┌─────────────────┐ ║
║  │  Train (%)           │  │  Validation (%)      │  │  Test (%)       │ ║
║  │  50 ──●──────── 90   │  │  5 ──────●──── 30   │  │  🔢 5           │ ║
║  │   80 %               │  │   15 %               │  │  (automatikus)  │ ║
║  └─────────────────────┘  └─────────────────────┘  └─────────────────┘ ║
║  ℹ️ Dataset split: 80% train / 15% val / 5% test                        ║
║                                                                          ║
║  📝 4. Modell Információk                                                ║
║  ┌───────────────────────────────────┬────────────────────────────────┐ ║
║  │  Modell neve:                     │  Láthatóság:                   │ ║
║  │  [Kukorica Tő - YOLOv11n       ]  │  (●) 🔒 Privát                 │ ║
║  │                                   │  ( ) 🌍 Nyilvános              │ ║
║  │  Kulcsszavak:                     │                                │ ║
║  │  [kukorica, barna_talaj,száraz ]  │  Leírás:                       │ ║
║  │                                   │  [Kukoricatő számolás         │ ║
║  │                                   │   annotált képeken betanítva] │ ║
║  └───────────────────────────────────┴────────────────────────────────┘ ║
║                                                                          ║
║  🚀 5. Training Indítása                                                 ║
║  ┌──────────────────────────────────────────────────────────────────┐  ║
║  │                                                                   │  ║
║  │  [ ] 📧 Email küldése a training befejezésekor                    │  ║
║  │                                                                   │  ║
║  │  ┌─────────────────────────────────────────────────────────┐     │  ║
║  │  │               🎓 Training indítása                       │     │  ║
║  │  └─────────────────────────────────────────────────────────┘     │  ║
║  └──────────────────────────────────────────────────────────────────┘  ║
╚══════════════════════════════════════════════════════════════════════════╝
```

**Training – Folyamatban állapot:**

```
┌──────────────────────────────────────────────────────────────────┐
│  🎓 Training indítása                                             │
│                                                                   │
│  ✅ Dataset előkészítve (180 kép → train/val/test)                │
│                                                                   │
│  ⏳ Training folyamatban...                                       │
│  ████████████░░░░░░░░░░  Epoch 87/300                            │
│                                                                   │
│  ⚠️ NE zárd be a böngészőt! (CPU-n több órát vehet igénybe)     │
│                                                                   │
│  💡 Beállíthatod az email értesítést: GYORS_UTMUTATO.md         │
└──────────────────────────────────────────────────────────────────┘
```

**Training – Kész állapot:**

```
┌──────────────────────────────────────────────────────────────────┐
│  🎉 Training befejezve!                                           │
│  ──────────────────────────────────────────────────────────────  │
│                                                                   │
│  📊 6. Training Eredmények                                        │
│                                                                   │
│  ┌────────────┐ ┌────────────┐ ┌────────────┐ ┌──────────────┐  │
│  │ Best Epoch │ │   mAP50    │ │ Precision  │ │    Recall    │  │
│  │     87     │ │   0.923    │ │    0.91    │ │     0.88     │  │
│  └────────────┘ └────────────┘ └────────────┘ └──────────────┘  │
│                                                                   │
│  ▶ 📈 Grafikonok (results.png, confusion_matrix.png, ...)        │
│  ▶ 🖼️ Training/Validation batch példák                           │
│  ▶ 📸 Sample képek (5 db)                                        │
│                                                                   │
│  ONNX modell: models/model_2026_01_15_1030_a1b2c3d4.onnx        │
│                                                                   │
│  ┌─────────────────────────────────┐                             │
│  │      🔄 Új Training Indítása    │                             │
│  └─────────────────────────────────┘                             │
└──────────────────────────────────────────────────────────────────┘
```

---

## 5. Counting oldal

*Sidebar + teljes szélességű kép szekció + két oszlopos eredmény nézet*

```
╔══════════════════╦═══════════════════════════════════════════════════════╗
║ SIDEBAR          ║  🔍 Counting                                          ║
║──────────────────║  Mezőgazdasági tőszámlálás YOLO modellel              ║
║ ⚙️ Detekció      ║  ────────────────────────────────────────────────────  ║
║ beállítások      ║                                                        ║
║                  ║  📤 Drónfelvétel feltöltése                            ║
║ 💳 Kreditek:     ║                                                        ║
║ ┌─────────────┐  ║  Képforrás: (●)Új felt.  ( )Korábbi  ( )Minta képek  ║
║ │100 db kred. │  ║                                                        ║
║ └─────────────┘  ║  ┌─────────────────────────────────────────────────┐ ║
║                  ║  │                                                   │ ║
║ Konfidencia küsz.║  │  Húzz ide egy képet, vagy kattints a tallózáshoz │ ║
║ 0.0 ──●───── 1.0 ║  │  Limit: 200 MB  │  JPG, PNG, TIFF, GeoTIFF      │ ║
║  0.25            ║  └─────────────────────────────────────────────────┘ ║
║                  ║                                                        ║
║ IoU küszöb (NMS) ║  [Kép betöltés után megjelenik az előnézet + info]   ║
║ 0.0 ────●─── 1.0 ║  ┌─────────────────────────────────────────────────┐ ║
║  0.45            ║  │                                                   │ ║
║                  ║  │   🖼️  [feltöltött drónfelvétel előnézete]         │ ║
║ 🔲 Sliding Window║  │                                                   │ ║
║ [✓] Bekapcsolva  ║  │   Méret: 2304 × 2304 px                          │ ║
║                  ║  │   Formátum: GeoTIFF (EPSG:4326)                  │ ║
║ Átfedés (%):     ║  │   Fájlméret: 1.2 MB                              │ ║
║ 0 ─────●──── 50  ║  │   Csatornák: 3 (RGB)                             │ ║
║  25%             ║  │   Bounds: [47.12°N – 47.14°N, 19.32°E – 19.35°E] │ ║
║                  ║  └─────────────────────────────────────────────────┘ ║
║ [ ] Egyedi       ║                                                        ║
║   ablakméret     ║  ────────────────────────────────────────────────────  ║
║                  ║                                                        ║
║ ✨ Képmanipuláció║  🤖 Modell Választás                                   ║
║ [None        ▼]  ║                                                        ║
║                  ║  ┌──────────────────────────┬──────────────────────┐  ║
║ 📧 Értesítés     ║  │  Kukorica YOLOv11n       │  Repce v2            │  ║
║ [ ] Riport email ║  │  ⭐⭐⭐⭐☆  (4.0)          │  ⭐⭐⭐☆☆  (3.2)     │  ║
║                  ║  │                          │                      │  ║
║ ──────────────── ║  │  [🖼️ sample kép]          │  [🖼️ sample kép]     │  ║
║                  ║  │                          │                      │  ║
║ ┌──────────────┐ ║  │  🏷️kukorica 🏷️barna_talaj │  🏷️repce 🏷️2026     │  ║
║ │▶️ Predikció  │ ║  │                          │                      │  ║
║ │  futtatása   │ ║  │  mAP50:     0.923        │  mAP50:     0.847    │  ║
║ └──────────────┘ ║  │  Precision: 0.910        │  Precision: 0.883    │  ║
║  (aktív, ha van  ║  │  Recall:    0.880        │  Recall:    0.801    │  ║
║  kép + modell)   ║  │  YOLOv11 nano / 640px    │  YOLOv11 small/640px │  ║
║                  ║  │  Haszn.: 47 alkalom      │  Haszn.: 12 alkalom  │  ║
║                  ║  │                          │                      │  ║
║                  ║  │  [✅ Kiválasztva]         │  [  Kiválasztás  ]   │  ║
║                  ║  └──────────────────────────┴──────────────────────┘  ║
╚══════════════════╩═══════════════════════════════════════════════════════╝
```

**Counting – Eredmények (detekció után):**

```
╔══════════════════════════════════════════════════════════════════════════╗
║  ✅ Kész! Talált objektumok: 247                                         ║
║  ──────────────────────────────────────────────────────────────────────  ║
║                                                                          ║
║  ┌───────────────┐ ┌───────────────┐ ┌──────────────────────────────┐   ║
║  │Talált obj.    │ │Kép méret      │ │Modell input                  │   ║
║  │    247        │ │2304 × 2304 px │ │ 640 × 640 px                 │   ║
║  └───────────────┘ └───────────────┘ └──────────────────────────────┘   ║
║                                                                          ║
║  ┌───────────────────────────────────────────────────────────────────┐  ║
║  │                                                                   │  ║
║  │   🖼️  [annotált kép bounding box-okkal – zöld téglalapok]        │  ║
║  │        Minden tő körül egy piros/zöld téglalap + konfidencia     │  ║
║  │                                                                   │  ║
║  └───────────────────────────────────────────────────────────────────┘  ║
║                                                                          ║
║  ▶ 📋 Detekciók részletei (247 sor)                                      ║
║     #   │ Konfidencia │ Osztály     │ X_min │ Y_min │ X_max │ Y_max     ║
║     1   │ 0.923       │ kukorica_to │ 120.5 │ 340.2 │ 185.3 │ 425.8    ║
║     2   │ 0.876       │ kukorica_to │ 450.1 │ 210.9 │ 512.7 │ 289.3    ║
║     ... │ ...         │ ...         │ ...   │ ...   │ ...   │ ...       ║
║                                                                          ║
║  📊 Statisztikák:                                                        ║
║  Átlag konfidencia: 0.872 │ Min: 0.251 │ Max: 0.966                     ║
║                                                                          ║
║  ── Export ──────────────────────────────────────────────────────────── ║
║  [📄 CSV letöltés]  [📋 JSON letöltés]  [🖼️ Kép letöltés]  [🌍 GeoJSON]║
║                     (GeoJSON csak GeoTIFF esetén aktív)                  ║
╚══════════════════════════════════════════════════════════════════════════╝
```

---

## 6. Admin oldal (10_Admin.py)

*Csak admin jogosultságú felhasználónak látható*

```
╔══════════════════════════════════════════════════════════════════════════╗
║  🔧 Admin Panel                                                          ║
║  ──────────────────────────────────────────────────────────────────────  ║
║                                                                          ║
║  👥 Felhasználók kezelése                                                ║
║  ┌──────────────────────────────────────────────────────────────────┐  ║
║  │                                                                   │  ║
║  │  Felhasználó     │ Email              │ Regisztrált │ Szerepkör  │  ║
║  │  ────────────────┼────────────────────┼─────────────┼──────────  │  ║
║  │  zsigmond        │ csaba@logpilot.hu  │ 2026-01-01  │ 🔧 admin  │  ║
║  │  [🔑 Reset] [🗑️]│                    │             │ [👑 Admin] │  ║
║  │  ────────────────┼────────────────────┼─────────────┼──────────  │  ║
║  │  test_user       │ test@email.hu      │ 2026-01-15  │ 👤 user   │  ║
║  │  [🔑 Reset] [🗑️]│                    │             │ [👑 Admin] │  ║
║  │                                                                   │  ║
║  └──────────────────────────────────────────────────────────────────┘  ║
║                                                                          ║
║  📊 Rendszer statisztikák                                                ║
║  ┌──────────────┐ ┌──────────────┐ ┌──────────────────────────────┐    ║
║  │ Összes user  │ │   Adminok    │ │ Legutóbbi regisztráció       │    ║
║  │      2       │ │      1       │ │ 2026-01-15 (test_user)       │    ║
║  └──────────────┘ └──────────────┘ └──────────────────────────────┘    ║
║                                                                          ║
║  ▶ 📋 Operációs napló                                                    ║
╚══════════════════════════════════════════════════════════════════════════╝
```

---

## 7. Haladó beállítások (Expander – Training oldalon)

*A Training oldal "Haladó beállítások" panele kinyitva*

```
┌────────────────────────────────────────────────────────────────────────┐
│ ▼ ⚙️ Haladó beállítások (Data Augmentation & Regularizáció)            │
├─────────────────────────────────┬──────────────────────────────────────┤
│ Data Augmentation               │ Szín & Geometriai transzformációk    │
│                                 │                                      │
│ [✓] Data augmentation           │ HSV Hue:                             │
│                                 │ 0.0 ●─────────────── 0.1            │
│ Mosaic augmentation:            │  0.015                               │
│ 0.0 ─────────────────●── 1.0   │                                      │
│  1.0 (always on)               │ HSV Saturation:                      │
│                                 │ 0.0 ──────●─────────── 1.0          │
│ MixUp augmentation:             │  0.7                                 │
│ 0.0 ●──────────────────── 1.0  │                                      │
│  0.0 (kikapcsolva)              │ HSV Value:                           │
│                                 │ 0.0 ────●─────────────── 1.0        │
│ Vízszintes tükrözés:            │  0.4                                 │
│ 0.0 ──────●────────── 1.0      │                                      │
│  0.5                            │ Forgatás (fok):                      │
│                                 │ 0.0 ●──────────────── 45.0          │
│ Függőleges tükrözés:            │  0.0 (kikapcsolva)                   │
│ 0.0 ●──────────────── 1.0      │                                      │
│  0.0 (kikapcsolva)              │ Eltolás:                             │
│                                 │ 0.0 ──●──────────────── 0.5         │
│                                 │  0.1                                 │
│                                 │                                      │
│                                 │ Skálázás:                            │
│                                 │ 0.0 ──────●──────────── 1.0         │
│                                 │  0.5                                 │
├─────────────────────────────────┼──────────────────────────────────────┤
│ Regularizáció                   │ CPU optimalizáció + Email            │
│                                 │                                      │
│ Weight decay:                   │ Workers (CPU cores):                 │
│ [0.0005                      ]  │ 1 ──────●────────── 8               │
│                                 │  4                                   │
│                                 │                                      │
│                                 │ [ ] Cache képek (RAM)                │
│                                 │                                      │
│                                 │ [ ] Email küldése befejezéskor       │
└─────────────────────────────────┴──────────────────────────────────────┘
```

---

## 8. Preprocessing Preview (Counting / Annotation oldal)

*Expanderban megjelenő Before/After összehasonlítás*

```
▼ 🔍 Preprocessing Preview (ExGreen)

┌──────────────────────────────────┬──────────────────────────────────┐
│  BEFORE (Eredeti)                │  AFTER (ExGreen alkalmazva)      │
│                                  │                                  │
│  [🖼️ eredeti RGB kép]            │  [🖼️ feldolgozott kép]           │
│                                  │                                  │
│  Természetes színek              │  Zöld csatorna kiemelve:         │
│  R: piros, G: zöld, B: kék      │  Növények: fehér                 │
│                                  │  Talaj: fekete/szürke            │
└──────────────────────────────────┴──────────────────────────────────┘

ℹ️ ExGreen: 2×G - R - B  │  Kukoricatőkhöz ajánlott
```

---

## Összefoglalás – Navigáció áttekintése

```
                         ╔══════════════╗
                         ║  HOME oldal   ║
                         ║  (Belépési   ║
                         ║   pont)      ║
                         ╚══════════════╝
                                │
               ┌────────────────┼────────────────┐
               ▼                ▼                ▼
      ╔════════════════╗ ╔══════════════╗ ╔════════════════╗
      ║  1_Annotation  ║ ║  2_Training  ║ ║   3_Counting   ║
      ║                ║ ║              ║ ║                ║
      ║ Projektek      ║ ║ Dataset      ║ ║ Kép feltöltés  ║
      ║ Csempézés      ║ ║ Konfiguráció ║ ║ Modell választ.║
      ║ Canvas rajzolás║ ║ Indítás      ║ ║ Predikció      ║
      ║ Verziók        ║ ║ Eredmények   ║ ║ Export         ║
      ╚════════════════╝ ╚══════════════╝ ╚════════════════╝
               │                │                │
               └────────────────┼────────────────┘
                                │ (admin link)
                                ▼
                     ╔═══════════════════╗
                     ║  10_Admin panel   ║
                     ║  (Admin only)     ║
                     ╚═══════════════════╝
```

---

## 14. AI Segéd (💬 AI_Seged)

*Chat felület dokumentáció- és szaktudás-alapú AI asszisztenssel. Modul kulcs: `assistant`*

### Lebegő gomb (minden oldalon)

```
╔══════════════════════════════════════════════════════════════════════╗
║                                                                      ║
║              [bármely oldal tartalma]                                ║
║                                                                      ║
║                                                      ┌─────────────┐║
║                                                      │ 💬 Segéd    │║
║                                                      └─────────────┘║
╚══════════════════════════════════════════════════════════════════════╝
  Jobb alsó sarok, position: fixed, z-index: 9999
  Kattintás → navigáció /AI_Seged oldalra
```

### Chat felület

```
╔══════════════════════════════════════════════════════════════════════╗
║ ┌──────────────────┐                                                ║
║ │ SIDEBAR          │  💬 DrónTerápia Segéd                          ║
║ │                  │  Kérdezz bátran a rendszer használatáról...     ║
║ │ 💬 AI Segéd      │  ──────────────────────────────────────────── ║
║ │                  │                                                ║
║ │ Hátralévő:      │  ℹ️ Kontextus: Counting oldalról érkeztél      ║
║ │ **28** / óra     │                                                ║
║ │                  │  ┌──────────────────────────────────────────┐ ║
║ │ Beszélgetés:     │  │ 🤖 Szia! Miben segíthetek?               │ ║
║ │ 3 kérdés         │  │    Kérdezhetsz például:                   │ ║
║ │                  │  │    - Hogyan töltsek fel ONNX modellt?    │ ║
║ │ [🗑️ Beszélgetés  │  │    - Mi az a GSD?                        │ ║
║ │     törlése    ] │  │    - Hogyan indítsak batch feldolgozást?  │ ║
║ │                  │  └──────────────────────────────────────────┘ ║
║ │ [↩️ Vissza:      │                                                ║
║ │   Counting     ] │  🧑 Hogyan állítom be a konfidencia küszöböt? ║
║ └──────────────────┘                                                ║
║                       🤖 A konfidencia küszöb a detekció            ║
║                          érzékenységét szabályozza...▌ (streaming)  ║
║                                                                      ║
║  ┌──────────────────────────────────────────────────────────────┐  ║
║  │  Írj ide...                                                    │  ║
║  └──────────────────────────────────────────────────────────────┘  ║
╚══════════════════════════════════════════════════════════════════════╝
  st.chat_input() a .stBottom containerben
  Streaming válasz: st.write_stream(chat_stream(...))
  Rate limit: 30 kérdés/óra/felhasználó
```
