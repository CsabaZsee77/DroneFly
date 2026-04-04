# L2 – Döntési Logika – Annotation (Képcímkézés és Dataset Kezelés)

**Modul:** M01
**Szint:** L2 – Döntési Logika
**Forrásdokumentumok:** ANNOTATION_TOOL.md, MULTI_DATASET_SYSTEM.md, ANNOTATION_METHOD_IMPLEMENTATION.md, DATASET_MANAGER_V3_CHANGES.md, BUGFIX_ANNOTATION_UI.md

---

## Validációs szabályok

### Projekt létrehozásakor

| Feltétel | Érvényes | Érvénytelen | Kezelés |
|----------|----------|-------------|---------|
| Projekt neve | Nem üres | Üres string | Hibaüzenet, blokkolás |
| Osztályok | Legalább 1 osztály | Üres lista | Hibaüzenet |
| Osztály neve | Alfanumerikus, alávonás engedélyezett | Szóköz, speciális karakter | Figyelmeztetés |
| Tile méret | 320-1280 px közötti egész | < 320 vagy > 1280 | Slider korlátoz |
| Átfedés | 0-50% | > 50% | Slider korlátoz |

### Kép feltöltésekor

| Feltétel | Érvényes | Érvénytelen | Kezelés |
|----------|----------|-------------|---------|
| Fájl formátum | .tif, .tiff | Más kiterjesztés | Nem kerül a feltöltőbe |
| Fájl méret | Streamlit limit (200MB default) | > limit | Streamlit alapértelmezett hiba |
| GeoTIFF | Elfogadott | Georeferencia nélküli TIFF | Elfogadott, GeoJSON export nem lesz |
| Csatornák száma | 1-4 csatorna | > 4 csatorna | Általában elfogadott |

### Csempézéskor

| Feltétel | Viselkedés |
|----------|-----------|
| Tile méret > kép méret | 1 tile jön létre (a kép maga) |
| Utolsó sor/oszlop nem teljes | Padding vagy kihagyás |
| Átfedés beállítása | Szomszédos tile-ok részben fedik egymást |
| Formátum: PNG | Veszteségmentes, kisebb méret |
| Formátum: TIFF | Georeferencia megőrzés (ha volt) |

### Annotáció mentésekor

| Feltétel | Érvényes | Érvénytelen | Kezelés |
|----------|----------|-------------|---------|
| Canvas tartalom | Van legalább 1 alakzat (bbox vagy polygon) | Üres canvas | Mentés figyelmeztető üzenettel lehetséges (üres annotáció = 0 objektum) |
| Bounding box koordináták | Normalizált 0.0-1.0 között | Kívül esik | Automatikus korrekció/kihagyás |
| Polygon koordináták | Min. 3 pont, normalizált 0.0-1.0 | 2 vagy kevesebb pont | Kihagyás |
| Class ID | 0 és (osztályok száma-1) közötti egész | Nemegész vagy tartományon kívül | Kihagyás |
| Annotáció típus konzisztencia | Projekt egészén azonos típus (bbox vagy polygon) | Kevert típusok | Figyelmeztetés (training kompatibilitás) |

### Training version létrehozásakor

| Feltétel | Érvényes | Érvénytelen | Kezelés |
|----------|----------|-------------|---------|
| Annotált csempék száma | ≥ 1 | 0 | Gomb disabled, hibaüzenet |
| Train + Val arány | ≤ 100% | > 100% | Slider korlátoz, hibaüzenet |
| Test arány | Automatikus (100% - Train - Val) | — | Automatikus számítás |
| Version neve | Nem üres | Üres | Hibaüzenet |

---

## Döntési logika – Csempézés

### Tile generálás algoritmusa (core/tiling.py)

```
INPUT: TIFF fájl, tile_size (px), overlap (%)

overlap_px = tile_size * (overlap / 100)
stride = tile_size - overlap_px

x_positions = range(0, image_width, stride)
y_positions = range(0, image_height, stride)

FOR each (x, y) position:
    tile = image[y:y+tile_size, x:x+tile_size]
    IF tile.width < tile_size OR tile.height < tile_size:
        tile = pad(tile, tile_size)  # Zero-padding
    SAVE tile to data/datasets/{project_id}/tiles/

RETURN tile_count
```

### Átfedés (overlap) döntési pontok

| Átfedés értéke | Hatás | Ajánlott |
|----------------|-------|---------|
| 0% | Nincs átfedés, széleken lévő objektumok elveszhetnek | Nem ajánlott |
| 10% | Kis átfedés, gyors csempézés | Általános használathoz |
| 20% | Közepes átfedés, jobb szélkezelés | Sűrű annotációhoz |
| 50% | Nagy átfedés, sok duplikált tile | Nem ajánlott (lassú) |

---

## Döntési logika – Annotáció

### Canvas-ról YOLO formátumra konverzió (detection – bounding box)

```
INPUT: Bounding box pixelkoordináták (x_min, y_min, x_max, y_max), tile_size

x_center = (x_min + x_max) / 2 / tile_size
y_center = (y_min + y_max) / 2 / tile_size
width    = (x_max - x_min) / tile_size
height   = (y_max - y_min) / tile_size

OUTPUT: "class_id x_center y_center width height"
```

### Canvas-ról YOLO formátumra konverzió (segmentation – polygon)

```
INPUT: Polygon pontok listája [(x1_px, y1_px), (x2_px, y2_px), ...], tile_size

FOR each point (xi_px, yi_px):
    xi_norm = xi_px / tile_size
    yi_norm = yi_px / tile_size

OUTPUT: "class_id x1_norm y1_norm x2_norm y2_norm ... xn_norm yn_norm"
```

### Annotáció típus döntési fa

```
Ha sidebar.annotation_mode == "rect":
    → drawing_mode = "rect"
    → Label format: "class_id cx cy w h"
    → Training: detection modell (yolo11n.pt)

Ha sidebar.annotation_mode == "polygon":
    → drawing_mode = "polygon"
    → Label format: "class_id x1 y1 x2 y2 ... xn yn"
    → Training: segmentation modell (yolo11n-seg.pt)
```

### Annotáció betöltési logika (meglévő szerkesztéshez)

```
IF label_file exists for current tile:
    LOAD coordinates from label_file
    CONVERT from normalized to pixel coordinates
    DISPLAY on canvas as existing boxes
ELSE:
    Start with empty canvas
```

### Osztály kezelési szabályok

| Szabály | Részlet |
|---------|---------|
| Osztályok sorrendje | 0-tól indexelve, megadás sorrendjében |
| Osztály törlése | Csak ha min. 1 osztály marad |
| Osztály szerkesztése | Visszamenőleg NEM frissíti az annotációkat (class ID változhat) |
| Új osztály hozzáadása | A meglévő annotációk érintetlenek maradnak |

---

## Döntési logika – Dataset felosztás (Training Version)

### Train/Val/Test split algoritmusa

```
annotated_tiles = [tiles where label_file exists AND has ≥1 annotation]

IF user_selected == "all_annotated":
    selected_tiles = annotated_tiles
ELSE:
    selected_tiles = user_selected_list

SHUFFLE(selected_tiles, random_seed=42)

n = len(selected_tiles)
train_n = floor(n * train_ratio)
val_n   = floor(n * val_ratio)
test_n  = n - train_n - val_n

train_tiles = selected_tiles[:train_n]
val_tiles   = selected_tiles[train_n:train_n+val_n]
test_tiles  = selected_tiles[train_n+val_n:]
```

### Split arányok validációja

| Feltétel | Érvényes | Kezelés |
|----------|----------|---------|
| Train + Val ≤ 95% | OK | — |
| Train + Val > 95% | Túl kevés test | Figyelmeztetés |
| Train + Val = 100% | Test = 0% | Engedélyezett (teszt set nélkül) |
| Train < 50% | Kevés training adat | Figyelmeztetés |

---

## Döntési logika – Preprocessing Preset annotációban

Ha a felhasználó preprocessing presetet használt az annotáció során:
- **Kézi annotáció:** A presetet az annotátor fejben tartja
- **Fontos:** A Counting oldalon **ugyanazt a presetet** kell választani!
- A rendszer jelenleg nem kényszeríti ki a konzisztenciát (figyelmeztetés van)

---

## Hibaágak

### "Projekt metadata hiányos (nincs dataset_id)"

**Ok:** Sérült JSON metadata fájl (pl. régi formátum)
**Döntés:** Rendszer új projektet javasol
**Kezelés:** Hozz létre új projektet; a régi csempék manuálisan átmásolhatók

### "Csempézés lefagy"

**Ok:** Túl nagy TIFF vagy túl kis tile méret → sok tile
**Döntés:** Rendszer nem állítja le, a böngésző timeout-olhat
**Kezelés:** Tile méret növelése, átfedés csökkentése

### "Annotáció nem mentődik"

**Ok:** Üres canvas (nincs rajzolt bounding box)
**Döntés:** A rendszer engedélyezi az üres mentést (0 objektum = negatív példa)
**Kezelés:** Rajzolj legalább egy téglalapot, ha objektum van a képen

---

## Üzleti szabályok összefoglalója

1. Egy projekt = egy felhasználóhoz rendelt
2. Egy projektben több kép is csempézhető (egymáshoz adódnak)
3. Training version csak annotált csempékből áll (annotálatlanok kizárva)
4. Osztályok projekt-szintűek, minden tile-ra érvényesek
5. "Save As" másolja a csempéket és annotációkat, de nem a training verziókat
6. Az annotáció bármikor szerkeszthető (nincs lezárás)
7. Törölt tile esetén a hozzá tartozó annotáció is törlődik
