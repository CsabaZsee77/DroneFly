# L1 – Üzleti Folyamat – Modell Nyilvántartás (Model Registry)

**Modul:** M05
**Szint:** L1 – Üzleti Folyamat
**Forrásdokumentumok:** TRAINING_MODULE.md, MODEL_EXPORT_GUIDE.md, ONNX_CLASS_MAPPING.md, pages/5_Models.py, core/model_manager.py

---

## Fő cél

A betanított ONNX modellek centralizált nyilvántartása, metaadatainak (teljesítmény metrikák, tagek, leírás, láthatóság) kezelése, és a Counting oldalon való megjelenítése a felhasználók számára. A registry biztosítja, hogy a modellek megtalálhatók, összehasonlíthatók és kiválaszthatók legyenek.

---

## Szereplők

| Szereplő | Szerepkör |
|----------|-----------|
| **Training rendszer (M02)** | Betanítás után automatikusan regisztrál |
| **Felhasználó** | Megtekinti, szerkeszti, törli a saját modelljeit; megtekinti a nyilvános modelleket |
| **Admin** | Minden modellt kezelhet |
| **ModelMetadata (utils/model_metadata.py)** | Registry olvasás/írás |
| **ModelManager (core/model_manager.py)** | ONNX fájlok és registry szinkronizálása, láthatóság váltás, törlés |

---

## Modell életciklus (végponttól végpontig)

### 1. Modell regisztrálása (Training után automatikus)

Training befejezésekor a TrainingWrapper automatikusan regisztrálja:
1. ONNX fájl neve és elérési útja
2. Training metrikák (mAP50, Precision, Recall, best_epoch)
3. Technikai adatok (YOLO verzió, modell méret, input méret, osztályok)
4. Felhasználói metaadatok (megjelenítendő név, tagek, leírás, láthatóság)
5. Sample képek listája (5 db thumbnail)
6. Tulajdonos (user_id, username)
7. Létrehozás dátuma és UUID

### 2. Modell megtekintése (Counting oldalon)

A Counting oldal jobb panelén modell kártyák jelennek meg:
- Megjelenítendő neve
- Rating (csillagok: 0-5, jelenleg 0 az alapértelmezett)
- 1 db sample kép (thumbnail)
- Tagek (🏷️ kukorica, 🏷️ barna_talaj)
- Technikai adatok (mAP50, Precision, Recall, YOLO verzió, modell méret, input méret)
- Leírás
- Használatok száma
- [Kiválasztás] gomb

### 3. Modell kiválasztása

- Felhasználó "Kiválasztás" gombra kattint
- Gomb "✅ Kiválaszt"-ra változik (zöld)
- `stats.usage_count` növekszik +1-el
- Modell betöltődik a YOLOPredictor-ba

### 4. Modell kezelése – Models oldal (5_Models.py) ✅ IMPLEMENTÁLVA (2026-03-11)

Dedikált oldal a modellek kezeléséhez, két szekció:

**Saját modellek:**
- Kártyás megjelenítés (megjelenítendő név, tagek, mAP50, leírás, sample képek)
- **Szerkesztés:** megjelenítendő név, leírás, tagek, osztálynevek módosítása (inline form)
- **Láthatóság toggle:** Privát ↔ Nyilvános váltás egy kattintással (megerősítés nélkül, azonnali registry mentés)
- **Törlés:** megerősítő dialóg → ONNX fájl, PT weights, sample képek és registry bejegyzés törlése
- **Szöveges keresés:** naam, tagek, leírás alapján szűrés

**Publikus modellek (más felhasználóké):**
- Csak olvasható nézet
- `Fork` gomb (hamarosan — funkcionalitás tervezés alatt)

**ONNX feltöltés:**
- Felhasználó saját ONNX modellt tölthet fel (Training-en kívül is)
- Megadható: név, leírás, tagek, osztálynevek, láthatóság
- Registry-be kerül automatikusan

**Cache invalidáció:**
- A modell registry mtime-alapon cache-elődik
- Ha a `models/model_registry.json` módosult → automatikus újratöltés

### 5. Modell törlése (opcionális)

- A Models oldalon: "🗑️ Törlés" gomb → megerősítő dialóg
- Törli: ONNX fájl, PT weights, sample képek
- Törli: registry bejegyzés
- NEM törli: a modellel készített korábbi detekció eredményeket

---

## Modell láthatóság szabályok

| Láthatóság | Megjelenik kinek |
|------------|-----------------|
| `private` | Csak a tulajdonos felhasználónak |
| `public` | Minden bejelentkezett felhasználónak |

**Döntés:** A Training oldalon beállítható (Privát / Nyilvános radio button).

---

## Állapotok

### Modell állapotok

| Állapot | Leírás |
|---------|--------|
| Regisztrált | ONNX fájl és registry bejegyzés létezik |
| Sérült | ONNX fájl létezik, de registry hiányos (vagy fordítva) |
| Törölt | Registry bejegyzés és ONNX fájl eltávolítva |

---

## Kapcsolódó modulok

- ← **M02 Training:** Minden betanítás után automatikusan regisztrál
- → **M03 Counting:** A registry-ből tölt be modell kártyákat
- → **M04 Felhasználókezelés:** Modell tulajdonosa és láthatósága
