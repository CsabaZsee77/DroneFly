# L1 – Üzleti Folyamat – ODM Ortomozaik Pipeline

**Modul:** M08
**Szint:** L1 – Üzleti Folyamat
**Verzió:** v1.0.0
**Létrehozva:** 2026-03-16
**Státusz:** ✅ Implementálva (2026-03-18)

---

## 1. Modul célja

Az M08 modul lehetővé teszi, hogy a felhasználó **nyers drónfelvételekből
(JPG/TIFF képcsomag) georeferált ortomozaikot állítson elő** a rendszeren belül,
külső szoftver (DJI Terra, Pix4D, Agisoft) nélkül. A feldolgozást az
**OpenDroneMap (ODM)** nyílt forráskódú fotogrammetria motor végzi,
Docker konténerként futtatva a szerveren.

A kész ortomozaik automatikusan a megfelelő parcellához rendelődik, és azonnal
elérhetővé válik a meglévő elemző modulokban:
- **M03 Counting** – tőszámlálás az ortomozaikon
- **M06 Spectral** – NDVI / VARI elemzés
- **M06 Térkép** – georeferált overlay a parcella térképen

**Üzleti értékek:**
- Nincs szükség drága desktop fotogrammetria szoftverre
- DJI Mini 3/4 Pro felvételeiből közvetlenül ortomozaik
- Az M07 repülési tervvel készített felvételek közvetlen folytatása
- Méretlimit szabályok: kis területek a szerveren, nagy területek külső szoftverrel
- Csak a **`odm` modul** engedélyezésével érhető el (M04 modul rendszer)

---

## 2. Szereplők

| Szereplő | Szerepkör |
|----------|-----------|
| Drónos operátor | Feltölti a nyers képeket, elindítja a feldolgozást |
| Rendszer | Validálja a képeket, ODM jobot futtat, kész GeoTIFF-et parcellához rendel |
| OpenDroneMap (Docker) | A tényleges fotogrammetria számítás elvégzője |
| M03 / M06 modulok | A kész ortomozaik fogyasztói |

---

## 3. Fő folyamat

```
[Felhasználó] → ODM Processing oldal megnyitása
      │
      ▼
Előfeltétel: require_module("odm") — `odm` modul engedélyezve?
  │ NEM → "Ez a funkció a 🛠️ Ortomozaik modulban érhető el." + stop
  │ IGEN
  ▼
Képek feltöltése (ZIP vagy multi-file upload)
  - JPG / TIFF fájlok, min. 20 kép
  - GPS EXIF adatok kötelezők
      │
      ▼
Validáció:
  - Képszám ellenőrzés (min. 20, max. limit tárhely kvóta szerint)
  - GPS EXIF ellenőrzés (minden képnek kell koordináta)
  - Becsült terület megjelenítése a GPS koordinátákból
  - Becsült feldolgozási idő és tárhelyigény megjelenítése
      │
      ▼
Parcella hozzárendelés:
  - Automatikus: GPS alapján melyik parcellával fed át?
  - Manuális: felhasználó választ a listából
  - Nincs parcella: "Hozz létre parcellát az M06-ban" üzenet
      │
      ▼
Feldolgozási preset választás:
  [Gyors]    – alacsonyabb minőség, ~20-40 perc
  [Standard] – alapértelmezett, ~40-90 perc
  [Minőségi] – maximális pontosság, ~90-180 perc
      │
      ▼
[Feldolgozás indítása] gomb
      │
      ▼
ODM job létrehozva → státusz: "queued"
  - Email értesítés beállítható (befejezéskor)
  - Oldal elhagyható, a job fut a háttérben
      │
      ▼
Feldolgozás folyamat (ODM Docker):
  [uploading] → [queued] → [processing] → [completed / failed]
      │
      ▼
Sikeres befejezés:
  - GeoTIFF ortomozaik generálva
  - Automatikus csatolás a parcellához (parcel["images"][] kibővítve)
  - M06 Parcel Analysis oldalon azonnal elemezható
  - Email értesítés küldve
      │
      ▼
Meghiúsult feldolgozás:
  - Hibaüzenet + ODM log megjelenítése
  - Újrafuttatás lehetősége módosított preset-tel
  - Feltöltött képek X napig megmaradnak (újrafuttatáshoz)
```

---

## 4. Méretlimit szabályok

```
Becsült terület < 5 ha  ÉS  Képszám < 200:
  → ODM a szerveren futtatható, zöld jelzés

Becsült terület 5–20 ha  VAGY  Képszám 200–500:
  → Sárga jelzés: "Nagy feldolgozás — becsült idő: X perc, X GB tárhely"
  → Felhasználó megerősítés után indul

Becsült terület > 20 ha  VAGY  Képszám > 500:
  → Piros jelzés: "Ezt a méretet javasoljuk külső szoftverrel feldolgozni
    (DJI Terra, Pix4D, Agisoft Metashape), majd a kész GeoTIFF-et
    közvetlenül a Parcellakezeles modulba feltölteni."
  → ODM nem indítható (méret alapú feature gate)
```

---

## 5. Feldolgozási presetjek

| Preset | ODM paraméterek | Minőség | Idő (5 ha, 150 kép) | RAM igény |
|--------|----------------|---------|---------------------|-----------|
| Gyors | `--fast-orthophoto` | Közepes | ~20-30 perc | ~4 GB |
| Standard | alapértelmezett | Jó | ~40-60 perc | ~8 GB |
| Minőségi | `--pc-quality ultra` | Kiváló | ~90-120 perc | ~12 GB |

---

## 6. Belépési útvonalak — M08 két forrásból táplálható

Az M08 **nem kizárólag M07-ből** indítható. Két egyenértékű belépési pont létezik:

```
╔══════════════════════════════════════════════════════════════════╗
║               M08 BELÉPÉSI ÚTVONALAK                            ║
╚══════════════════════════════════════════════════════════════════╝

A ÚTVONAL: Integrált workflow (M07 → M08)
─────────────────────────────────────────
[M07 Repülési Terv]
  KMZ letöltve → drón repült → "Repülés elvégezve" visszajelzés
        │
        │ M07 ajánlja: "Feldolgozod a képeket?"
        ▼
[M08 ODM Ortomozaik]  ← parcella előre kitöltve M07-ből
  Képek feltöltése → ODM → GeoTIFF
        │
        ▼
  M07 flight_plan státusza: "processed"
  M06 parcellánál automatikusan megjelenik a GeoTIFF

B ÚTVONAL: Önálló (külső mission planning → M08)
──────────────────────────────────────────────────
[Külső szoftver: DJI Terra / Hammer Missions / Dronelink]
  Felhasználó saját eszközzel tervezte a repülést
        │
        ▼
[M08 ODM Ortomozaik]  ← parcella manuálisan választva
  Képek feltöltése → ODM → GeoTIFF
        │
        ▼
  M06 parcellánál automatikusan megjelenik a GeoTIFF
  (M07 flight_plan NEM frissül — nincs összekapcsolt terv)

C ÚTVONAL: Közvetlen GeoTIFF import (M08 megkerülésével)
──────────────────────────────────────────────────────────
[Külső szoftver: Pix4D / Agisoft / DJI Terra]
  Felhasználó maga készítette az ortomozaikot, GeoTIFF-et ad
        │
        ▼
[M06 Parcel Analysis — képek tab]
  GeoTIFF közvetlen feltöltés → azonnal elemezható
  (ODM feldolgozás nem szükséges)
```

**Megjegyzés:** A C útvonal az M06-ban már implementált (✅).
Az A és B útvonal az M08 implementálásával válik elérhetővé.

---

## 7. Kapcsolódó modulok

| Modul | Kapcsolat típusa |
|-------|-----------------|
| M07 Repülési Terv | **Opcionális előzmény** — A útvonal esetén flight_plan_id csatolva |
| M06 Parcel Analysis | **Közvetlen kimenet** — GeoTIFF automatikusan parcellához rendelve |
| M03 Counting | **Közvetett kimenet** — GeoTIFF tőszámláláshoz azonnal felhasználható |
| M04 Felhasználókezelés | **Kapuzás** — `require_module("odm")` + tárhely kvóta ellenőrzés |

---

## 8. Végállapotok

| Állapot | Leírás |
|---------|--------|
| `uploading` | Képek feltöltés alatt |
| `validating` | GPS EXIF és képszám ellenőrzés |
| `queued` | ODM sor, várakozás más jobra |
| `processing` | ODM aktívan feldolgoz |
| `completed` | GeoTIFF kész, parcellához csatolva |
| `failed` | Hiba történt, log elérhető |
| `cancelled` | Felhasználó törölte |
