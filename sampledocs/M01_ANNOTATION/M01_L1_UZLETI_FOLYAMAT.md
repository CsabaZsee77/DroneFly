# L1 – Üzleti Folyamat – Annotation (Képcímkézés és Dataset Kezelés)

**Modul:** M01
**Szint:** L1 – Üzleti Folyamat
**Forrásdokumentumok:** ANNOTATION_TOOL.md, FELHASZNALOI_UTMUTATO.md, MULTI_DATASET_SYSTEM.md, ANNOTATION_UI_V3.md, DATASET_MANAGER_V3_CHANGES.md

---

## Fő cél

Nagy TIFF drónfelvételek felosztása kisebb, kezelhető csempékre (tile-okra), ezek kézi annotálása YOLO formátumban (bounding box **vagy szegmentációs polygon**), majd training verziók létrehozása a betanítási folyamathoz. Az annotáció eredménye: annotált dataset (képek + YOLO-kompatibilis label fájlok), amelyből training job indítható.

**Annotáció típusok:**
- **Bounding box (detection):** Téglalap rajzolása az objektum köré — YOLO detection formátum (`class_id cx cy w h`)
- **Szegmentációs polygon (segmentation):** Sokszög rajzolása az objektum körvonalára — YOLO segmentation formátum (`class_id x1 y1 x2 y2 ... xn yn`)

---

## Szereplők

| Szereplő | Szerepkör |
|----------|-----------|
| **Felhasználó (Annotátor)** | Drónképeket tölt fel, csempéz, kézzel annotál, training verziókat hoz létre |
| **Admin** | Hozzáférhet más felhasználók projektjeihez (jogosultság alapján) |
| **Rendszer (Streamlit)** | Automatikusan végzi a csempézést, tárolja az annotációkat, validálja az adatokat |

---

## Mi indítja a folyamatot

1. **Új projekt létrehozása:** A felhasználó új annotációs projektet hoz létre névvel, leírással, osztályokkal
2. **Kép feltöltése meglévő projektbe:** TIFF/GeoTIFF fájl feltöltése csempézéshez
3. **Meglévő projekt megnyitása:** Korábbi projekt folytatása – meglévő csempék annotálása

---

## Képernyő sorrend (webes felület – 1_Annotation.py)

```
Bal oldal: Projektek        Közép: Annotálás         Jobb oldal: Verziók
─────────────────────────────────────────────────────────────────────
Projektek listája       │  Workflow választó         │  Verziók listája
  - Csempe szám         │  ─────────────────          │  - Képek száma
  - Annotált szám       │  [Meglévő annotálása]       │  - Split arány
  - [📂 Megnyit]        │  [Új kép + csempézés]       │  - [🎓 Training]
                        │                             │
[➕ Új Project]         │  Canvas (rajzolás)          │  [➕ Új Version]
                        │  Navigáció ◀️ ▶️            │
                        │  [💾 Mentés]                │
```

---

## Üzleti események (végponttól végpontig)

### 1. Projekt létrehozása

- A felhasználó megnyomja az "➕ Új Project" gombot
- Kitölti az űrlapot:
  - **Projekt név** (kötelező)
  - **Leírás** (opcionális)
  - **Osztályok** (vesszővel elválasztva, pl. "kukorica_to")
  - **Tagek** (opcionális, pl. "kukorica, 2026")
  - **Tile méret** (320-1280 px, alapértelmezett: 640)
- Rendszer létrehozza a projekt könyvtárat és a metadata JSON-t
- Projekt megjelenik a bal oldali listában

### 2. Kép feltöltése és csempézés

- Felhasználó "Új kép hozzáadása és csempézés" módot választ
- TIFF/.tif fájl feltöltése (méretkorlát: Streamlit upload limit)
- Rendszer megmutatja a fájl adatait (név, méret)
- Felhasználó megnyomja "🔪 Csempézés és hozzáadás"
- Rendszer `core/tiling.py` segítségével feldarabolja a képet:
  - Tile méret: sidebar beállítás szerint
  - Átfedés: sidebar beállítás szerint (0-50%)
  - Formátum: PNG vagy TIFF
- Progress bar jelzi az előrehaladást
- Befejezés: "✅ XXX csempe létrehozva!" üzenet

### 3. Csempék annotálása

- Felhasználó "Meglévő csempék annotálása" módba vált
- Az első (vagy utoljára nézett) csempe betöltődik a canvas-ra
- Osztály kiválasztása a dropdown-ból
- **Annotálási mód (sidebar):**
  - **Bounding box** (detection): egér húzás → téglalap — YOLO `class_id cx cy w h` formátum
  - **Polygon** (segmentation): kattintásos sokszög rajzolás — YOLO `class_id x1 y1 x2 y2 ... xn yn` formátum
- "💾 Annotáció mentése" megnyomása → YOLO format .txt mentés
- Navigáció: ◀️ Előző / Következő ▶️ gombok
- Ismétlés minden csempére

### 4. Training Version létrehozása

- Elegendő annotáció után (min. 10-20 db ajánlott) felhasználó az "➕ Új Version" gombot nyomja
- Kitölti az űrlapot:
  - **Version név** (pl. "v1")
  - **Csempék kiválasztása:** összes annotált vagy egyedi kiválasztás
  - **Train/Val split:** Train (alapért. 80%), Val (alapért. 15%), Test (automatikus: 5%)
  - **Target tile méret** (640 px ajánlott)
- Rendszer létrehozza a training verziót (másolás, meta frissítés)
- Verzió megjelenik a jobb oldali listában
- "🎓 Training" gombra kattintva átirányítás a Training oldalra

### 5. Projekt másolása (Save As)

- Felhasználó megnyitja a projektet
- "💾 Mentés másként" gomb → új projekt neve
- Rendszer másolja: csempéket, annotációkat, forrásképeket, osztályokat
- NEM másolódik: training verziók
- Automatikus átváltás az új projektre

---

## Állapotok (üzleti nézet)

### Projekt állapotok:

| Állapot | Jelentés |
|---------|----------|
| Üres | Projekt létezik, de nincs csempe (képfeltöltés előtt) |
| Csempézett | Van csempe, de nincs vagy kevés annotáció |
| Annotálás alatt | Legalább 1 annotált csempe, de még nem kész |
| Kész | Elegendő annotáció, training verzió létrehozható |
| Verzióval rendelkező | Legalább 1 training verzió elkészült |

### Training verzió állapotok:

| Állapot | Jelentés |
|---------|----------|
| Létrehozva | Version elkészült, training még nem indult |
| Trainingben | Training folyamatban erre a verzióra |
| Betanítva | Legalább 1 sikeres training erre a verzióra |

---

## Végállapotok

1. **Sikeres dataset előkészítés:** Training version létrejött, elegendő annotált csempe van → Training oldal
2. **Hiányos annotáció:** Projekt létezik, de nincs elegendő annotáció (< 10 db) → Folytatni kell
3. **Megszakított csempézés:** TIFF feltöltve, de csempézés nem futott le → Újra kell csempézni
4. **Üres projekt:** Projekt létrehozva, de kép még nem töltötték fel

---

## Döntési pontok (üzleti szinten)

| Döntés | Opció A | Opció B | Jelenlegi irány |
|--------|---------|---------|-----------------|
| Projekt láthatóság | Felhasználóhoz kötött | Globális | Felhasználóhoz kötött |
| Csempe átfedés | Kötelező | Opcionális | Opcionális (0-50%) |
| Osztályok kezelése | Projekt-szintű | Globális | Projekt-szintű |
| Training verzió | Csak annotált csempéket tartalmaz | Összes csempe | Konfigurálható (összes vs. kiválasztott) |
| Annotálatlan csempe kezelése | Kizárva | Negatív példaként | Kizárva (csak annotált kerül be) |
| Kép formátum | Csak PNG | PNG és TIFF | PNG és TIFF (sidebar beállítás) |
| Annotáció típus | Csak bounding box | Bbox + polygon szegmentáció | Mindkettő (sidebar mód választó) |

---

## Kapcsolódó modulok

- → **M02 Training:** A training verzió átadja a datasetet a betanítási folyamatnak
- → **M05 Modell Nyilvántartás:** A training után a modell ide kerül regisztrálásra
- → **M04 Felhasználókezelés:** A projekt a bejelentkezett felhasználóhoz van rendelve
