# L2 – Döntési Logika – Parcella Képek és Spektrális Analízis

**Modul:** M06
**Szint:** L2 – Döntési Logika
**Verzió:** v2.0.0
**Létrehozva:** 2026-03-07
**Frissítve:** 2026-03-07 (Sentinel-2 CDSE döntési logika)

---

## 1. Kép hozzárendelhetőség döntés

```
Kép feltöltve?
  │ NEM → hibaüzenet, stop
  │ IGEN
  ▼
Fájl típusa .tif/.tiff/.jpg/.jpeg/.png?
  │ NEM → "Nem támogatott formátum" warning
  │ IGEN
  ▼
Kép mentve (ImageManager.save_uploaded_image)
  │
  ▼
is_geotiff(file_path)?
  │ IGEN → is_georeferenced=True, band_count=rasterio.count
  │ NEM  → is_georeferenced=False, band_count=0 (vagy 3 ha JPEG/PNG)
  ▼
parcel_manager.attach_image_to_parcel(...)
→ Képrekord hozzáadva az images[] listához
```

---

## 2. Band layout felismerés döntési fa

```
dataset.count (rasterio) ==
  1  → "grayscale"
  3  → "rgb"
  4  → "rgb_nir"    # 4-sávos drón (pl. DJI Phantom 4 Multispectral)
  5  → "micasense"  # MicaSense RedEdge-MX
  12 → "sentinel2"  # CDSE letöltő által generált 12-sávos stack (B10 nélkül)
  13 → "sentinel2"  # Egyéb 13-sávos Sentinel-2 stack
  *  → f"unknown_{count}"

Ha layout == "unknown_N":
  → Manual override szükséges (felhasználói selectbox)
  → NDVI stb. csak ha manuálisan "rgb_nir" / "micasense" / "sentinel2" választva
```

**Megjegyzés a 12-sávos formatumhoz:** A `CDSEClient.stack_bands()` által
létrehozott GeoTIFF 12 sávot tartalmaz (B10/Cirrus kihagyva), amelyek pozíciói
pontosan illeszkednek a `BAND_MAPS["sentinel2"]` értékeire:
`blue:2, green:3, red:4, rededge:5, nir:8, swir1:11, swir2:12`.

---

## 3. Spektrális index számíthatóság

| Index | Szükséges sávok | rgb (3 sáv) | rgb_nir (4) | micasense (5) | sentinel2 (12/13) |
|-------|----------------|-------------|------------|--------------|-------------------|
| NDVI  | red, nir       | ❌ | ✅ | ✅ | ✅ |
| NDRE  | red, rededge   | ❌ | ❌ | ✅ | ✅ |
| EVI   | red, nir, blue | ❌ | ✅ | ✅ | ✅ |
| SAVI  | red, nir       | ❌ | ✅ | ✅ | ✅ |

Ha az index NEM számítható a layout-tal:
→ Selectboxból kihagyjuk (nem mutatjuk disabled-ként, csak el sem érhetőek)

---

## 4. Érték validáció szabályok

### Képrekord
- `image_type`: csak `"drone"` vagy `"satellite"` fogadható el
- `date_acquired`: ISO 8601 formátum (`YYYY-MM-DD`), jövőbeli dátum nem engedett
- `file_path`: csak `data/uploads/` mappán belüli útvonal

### Spektrális indexek
- Input band array-ek: float32, dimenzió egyezés kötelező
- Ha NIR + Red = 0 → NaN (nem 0, nem crash)
- Output clip: NDVI és NDRE [-1, 1] tartományra (matematikailag garantált)
- EVI és SAVI: [-1, 1] tartományra clip a szélső értékek miatt

---

## 5. Kategória küszöbértékek (NDVI)

| Kategória | Küszöb | Értelmezés |
|-----------|--------|------------|
| Stresszes | NDVI < 0.2 | Elpusztult, kopár, vízhiányos |
| Közepes | 0.2 ≤ NDVI < 0.4 | Gyér növényzet, stresszjelei |
| Egészséges | 0.4 ≤ NDVI < 0.6 | Közepes-jó állapot |
| Nagyon egészséges | NDVI ≥ 0.6 | Optimális növekedés |

*Megjegyzés: NDRE és EVI különböző skálán mozog, de az NDVI kategóriák hasznosak
referenciának drón 4-sávos adatoknál is.*

---

## 6. Térkép overlay döntés

```
Parcella Analízis Tab 3:
  képek listája → foreach kép:
    is_georeferenced == True?
      IGEN → overlay cache elkészítés + folium ImageOverlay hozzáadás
      NEM  → kép kihagyva (nem overlay-elhető), info üzenet

  Ha nincs georeferált kép:
    → "Nincs georeferálható kép ehhez a parcellához" info
```

---

## 7. Idősor számíthatóság

```
Parcella összes képe → filter: band_count >= 4 AND is_georeferenced == True
  │
  < 2 kép → st.info("Legalább 2 multi-band GeoTIFF szükséges az idősorhoz")
  │
  ≥ 2 kép → "NDVI idősor számítása" gomb megjelenik
    gomb megnyomva:
      foreach kép: NDVI számítás → mean_ndvi
      chart: [(date_acquired, mean_ndvi), ...] sorrendben
```

---

## 8. Cache-elési szabályok

| Adat | Cache helye | Érvényesség |
|------|------------|-------------|
| PNG overlay (Tab 3) | `data/temp/map_overlays/{stem}_overlay.png` | Fájl létezéséig |
| NDVI array | Nincs perzisztált cache | Session state-ban tartható |

---

---

## 9. Sentinel-2 CDSE keresési döntési logika

```
CDSE keresés paraméterei:
  date_from > date_to?
    IGEN → "Az kezdődátum nem lehet późniejszy a záródátumnál" – keresés megakadályozva
    NEM  → folytatás

  max_cloud_cover == 0?
    → Valószínűleg nem lesz találat, de nem blokkoló – csak tájékoztató

  Keresés eredménye:
    0 találat → "Nincs találat a megadott feltételekkel. Bővítsd a dátumtartományt
                  vagy emeld a felhőborítottság küszöbét."
    ≥1 találat → lista megjelenik

  Letöltés döntés:
    [Letöltés] gomb lenyomva →
      st.session_state["s2_selected_product"] = raw_product
      Megerősítő gomb jelenik meg a mérettel és figyelmeztetéssel
    [Letöltés és csatolás] megerősítés →
      Letöltés megkezdve
```

## 10. CDSE hitelesítési döntési logika

```
.env-ben CDSE_USER és CDSE_PASSWORD megvan?
  IGEN → automatikusan használt, form rejtve (collapsed expander)
  NEM  → form megjelenik (expanded expander)

authenticate() sikertelen?
  401 Unauthorized → "Hibás CDSE belépési adatok"
  Hálózati hiba   → "Nem elérhető a CDSE szerver – ellenőrizd az internetkapcsolatot"

Token 401 letöltés közben:
  → Automatikus újrahitelesítés, letöltés folytatódik
```

---

*L3 implementáció: [M06_L3_ALLAPOTGEP_ES_ENGINE.md](M06_L3_ALLAPOTGEP_ES_ENGINE.md)*
