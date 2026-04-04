# L4 – Tranzakciós és Párhuzamos Kezelés – Parcella Képek és Spektrális Analízis

**Modul:** M06
**Szint:** L4 – Tranzakciós és Párhuzamos Kezelés
**Verzió:** v2.0.0
**Létrehozva:** 2026-03-07
**Frissítve:** 2026-03-07 (Sentinel-2 CDSE letöltési tranzakció)

---

## 1. Adatperzisztencia

### parcels.json írás

Az `_save_parcels()` metódus **atomic** módon ír (`json.dump` egyetlen hívás).
A meglévő `FileLocker` (filelock library) a projekten belül más fájloknál már
alkalmazott — a `parcels.json`-nál jelenleg nincs lock, de egyszerre csak egy
Streamlit session ír (egyszeri felhasználó rendszer).

**Ha jövőben multi-user:** `filelock.FileLock("data/parcels.json.lock")` alkalmazandó
az `_save_parcels()` körül (azonos pattern mint a meglévő `session_manager.py`).

### Kép feltöltés tranzakció

```
1. ImageManager.save_uploaded_image() → fájl a lemezre kerül
2. parcel_manager.attach_image_to_parcel() → JSON frissítés
```

Ha 2. lépés sikertelen (pl. parcel_id nem létezik): a fájl a lemezen marad árván.
**Elfogadott kompromisszum:** kép törlése manuálisan (admin funkció vagy cleanup_old_uploads).

---

## 2. Spektrális indexek memóriakezelés

### Nagy képek

Egy Sentinel-2 patch (~100 MB rasterio betöltve) és egy drón GeoTIFF (~500 MB)
egyszerre a memóriában tartásával számolni kell.

**Stratégia:**
- `get_all_bands()` csak a szükséges sávokat tölti be (opcionálisan)
- Vizualizációhoz: `max_size=512` resize a `get_image()` hívásnál
- Az index array (float32) a `st.session_state`-ben cache-elhető

```python
# session_state cache a Tab 2-ben
cache_key = f"ndvi_{image_id}_{index_name}"
if cache_key not in st.session_state:
    index_array, error = SpectralIndices.compute_index_from_bands(...)
    if error is None:
        st.session_state[cache_key] = index_array
```

### Vizualizáció cache

A `colorize_index()` matplotlib hívást igényel, ami ~0.5-2 másodperc.
Ha az index array cache-elt, a colorize mindig lefut (nincs cache-elve, mert
a PIL Image bytes nem hatékonyan JSON-szerializálható).

---

## 3. PNG overlay cache (Tab 3)

Az overlay PNG-k `data/temp/map_overlays/` mappában tárolódnak.

**Invalidáció:** Jelenleg fájl létezésig érvényes. Ha a képet detacholja és más képet
csatol ugyanolyan névvel: ugyanazt az overlay PNG-t fogja mutatni.

**Fix (ha szükséges):** overlay neve tartalmazza az `image_id`-t:
```
f"{Path(file_path).stem}_{image_id[:8]}_overlay.png"
```
Ez a jelenlegi implementációs specifikációban **nem kötelező**, de ajánlott.

---

## 4. Párhuzamos session kezelés

A M06 oldal (8_Parcel_Analysis.py) ugyanolyan Streamlit session-alapú működésű
mint a többi oldal. Egyidejű felhasználói interakcióknál a `parcels.json` olvasás
nem ütközik (olvasás mindig szinkron). Írás (attach/detach) race condition lehetséges
ha több tab egyidejűleg ír.

**Jelenlegi státusz:** Egyszeri felhasználó rendszer → nincs kritikus.
**Jövőbeli bővítésnél:** filelock bevezetése kötelező.

---

## 5. Hibakezelés

| Hiba | Kezelés |
|------|---------|
| rasterio nem elérhető | `GEOTIFF_AVAILABLE = False`, Tab 2 és 3 disabled |
| matplotlib nem elérhető | `MATPLOTLIB_AVAILABLE = False`, grayscale fallback |
| Band számítás NaN-t ad | Vizualizáció transparent pixelekkel, statisztika valid_pixels=0 |
| Fájl nem található a lemezen | `detach_image_from_parcel()` ajánlott + user üzenet |
| GeoTIFF megnyitás hiba | try/except → hibaüzenet, oldal nem omlik le |

---

## 6. Adatintegritás ellenőrzések

### Parcella betöltéskor
```python
# _load_parcels() kibővítve
for parcel in parcels:
    if 'images' not in parcel:
        parcel['images'] = []
    # Opcionális: file_path ellenőrzés
    for img in parcel['images']:
        if not Path(img.get('file_path', '')).exists():
            img['_file_missing'] = True  # flag, nem törlés
```

### Megjelenítéskor
- Ha `_file_missing == True`: sárga figyelmeztetés az UI-ban ("Fájl nem található")
- Detach gomb elérhető marad, hogy a linket eltávolíthassa

---

## 7. Export biztonság

- PNG export (Tab 2): PIL Image → `io.BytesIO()` → `st.download_button()`
  - Nincs fájl a szerveren, csak memory buffer
  - Fájlnév: `{index_name}_{parcel_name}_{date}.png`

---

---

## 8. Sentinel-2 CDSE letöltési tranzakció

### Tranzakció lépései és visszagörgetési pontok

```
1. CDSEClient.authenticate()
   HIBA → st.error("Hibás CDSE belépési adatok") – semmi nem történt

2. CDSEClient.download_product() → data/temp/sentinel2/{product_id}/{uuid}.zip
   HIBA (hálózat) → zip fájl törlendő ha részleges
                    shutil.rmtree(temp_dir, ignore_errors=True)
                    st.error("Letöltési hiba") – semmi nem csatolva

3. CDSEClient.extract_safe() → data/temp/sentinel2/{product_id}/*.SAFE/
   HIBA → shutil.rmtree(temp_dir, ignore_errors=True) + st.error

4. CDSEClient.stack_bands() → data/uploads/{timestamp}_{tile}_{date}_s2.tif
   HIBA → shutil.rmtree(temp_dir, ignore_errors=True)
           output_path fájl törlendő ha részleges:
           Path(output_path).unlink(missing_ok=True)
           st.error

5. shutil.rmtree(temp_dir) – temp takarítás (siker esetén)

6. parcel_manager.attach_image_to_parcel()
   HIBA → A TIF fájl a lemezen marad (árva – ugyanaz a kompromisszum
           mint a manuális feltöltésnél). st.error.
   SIKER → st.success + st.rerun()
```

**Finally blokk:** A temp_dir takarítása mindig fut, siker és hiba esetén egyaránt:
```python
finally:
    shutil.rmtree(temp_dir, ignore_errors=True)
```

### Lemezterület-számítás

| Fázis | Szükséges hely |
|-------|---------------|
| ZIP letöltve | ~500 MB – 2 GB (teljes Sentinel-2 L2A tile) |
| SAFE kibontva | ZIP méret × 1.1 (tömörítetlen) |
| Összesen temp-ben | ~1 GB – 4 GB |
| Stacked GeoTIFF (kimenet) | ~50-300 MB (parcella területre vágva, LZW) |
| Temp törlés után | Csak a kimenet GeoTIFF marad |

**Ajánlott minimum szabad hely:** 5 GB a `data/` partíción a letöltés ideje alatt.

### OAuth2 token életciklus

- Token érvényessége: ~600 másodperc (10 perc)
- Nagy fájloknál a token lejárhat letöltés közben
- Kezelés: ha `download_product()` 401-et kap, `authenticate()` újrahív, majd retry
- Token nem perzisztált – session-onként újrakérhető

### Párhuzamosság

A letöltés szinkron a Streamlit sessionben (a teljes oldal blokkolva).
Egyszerre csak egy letöltés lehetséges per session.
Több felhasználó esetén a `data/temp/sentinel2/{product_id}/` mappák izoláltak
(product_id egyedi UUID → nincs ütközés).

### Hibakezelési táblázat (kibővítve)

| Hiba | Kezelés |
|------|---------|
| CDSE 401 (auth) | st.error("Hibás belépési adatok") – nincs retry |
| CDSE 401 (letöltés közben) | Automatikus re-auth + retry |
| CDSE 503/timeout | st.error("Szerver nem elérhető") + temp cleanup |
| Nincs .SAFE a zip-ben | st.error + temp cleanup |
| JP2 sáv nem olvasható | Nullák az adott sávban (nem fatális) |
| Lemez tele (OSError) | st.error + temp cleanup |
| parcel_id nem létezik | st.error – TIF árva marad a lemezen |

---

*Ez a dokumentum a M06 modul végső adatintegritási és párhuzamos kezelési specifikációja.*
