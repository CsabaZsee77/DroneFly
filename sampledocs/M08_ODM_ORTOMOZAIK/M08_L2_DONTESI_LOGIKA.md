# L2 – Döntési Logika – ODM Ortomozaik Pipeline

**Modul:** M08
**Szint:** L2 – Döntési Logika
**Verzió:** v1.0.0
**Létrehozva:** 2026-03-16
**Státusz:** ✅ Implementálva (2026-03-18)

---

## 1. Modul ellenőrzés

```
Felhasználó megnyitja az ODM Processing oldalt
  │
  ▼
require_module("odm")  [utils/auth_helpers.py]
  │
  ├─ role == "admin" → folytatás
  ├─ "odm" IN user["modules"] → folytatás
  └─ "odm" NOT IN user["modules"]
       → st.warning("⚠️ Ez a funkció a 🛠️ Ortomozaik modulban érhető el.")
       → st.stop()
```

---

## 2. Képvalidáció döntési fa

```
Feltöltött fájl lista
  │
  ▼
Fájlok száma >= 20?
  │ NEM → "Minimum 20 kép szükséges megbízható ortomozaik előállításához.
  │         Feltöltve: X kép."  → STOP
  │ IGEN
  ▼
Minden fájl JPG / TIFF / DNG?
  │ NEM → "Nem támogatott formátum: {fájlnév}. Csak JPG/TIFF/DNG fogadható el."
  │ IGEN
  ▼
GPS EXIF kinyerés minden képből (exifread / piexif)
  │
  ▼
GPS koordináta megtalálható minden képben?
  │ NEM (> 10% hiányzó) → "GPS adatok hiányoznak X képből.
  │                         Georeferált ortomozaik nem állítható elő.
  │                         Ellenőrizd, hogy a drón GPS módban repült."
  │                         → STOP
  │ NEM (≤ 10% hiányzó) → "X képből hiányzik GPS adat — ezeket kihagyjuk."
  │                         → folytatás a többi képpel
  │ IGEN → folytatás
  ▼
Becsült területszámítás GPS koordinátákból:
  → Konvex burok (convex hull) a GPS pontokból
  → Terület ha-ban
```

---

## 2. Méretlimit döntés

```
Becsült terület ÉS képszám alapján:

terület <= 5 ha ÉS képszám <= 200:
  → ✅ Zöld: "Feldolgozható a szerveren"

5 < terület <= 20 ha VAGY 200 < képszám <= 500:
  → ⚠️ Sárga figyelmeztetés:
      "Nagy feldolgozás. Becsült idő: ~X perc, ~X GB tárhely szükséges.
       Szerver foglalt lehet — a job sorba áll."
  → [Mégis feldolgozom] gomb szükséges a folytatáshoz

terület > 20 ha VAGY képszám > 500:
  → 🔴 Piros tiltás:
      "Ez a méret meghaladja a szerveren feldolgozható korlátot.
       Javasolt megoldás: DJI Terra / Pix4D / Agisoft Metashape —
       majd a kész GeoTIFF-et töltsd fel közvetlenül a Parcellakezeles modulba."
  → ODM nem indítható

Jövőbeli döntési pont (ha szerver bővül):
  → A limitek a szerver RAM és tárhely függvényében konfigurálhatók
     (environment variable: MAX_ODM_AREA_HA, MAX_ODM_IMAGES)
```

---

## 3. Parcella hozzárendelés döntés

```
GPS alapú automatikus egyeztetés:
  Képek GPS konvex hullja átfed valamely user parcellával?
    │ IGEN (>= 50% átfedés) → automatikus javaslat: "Ez a felvétel valószínűleg
    │                          a {parcella_név} felett készült. Helyes?"
    │                          [Igen] | [Más parcellát választok]
    │ IGEN (< 50% átfedés) → "Részleges átfedés észlelve — kérlek válassz manuálisan"
    │ NEM → "Nem található egyező parcella. Válassz parcellát vagy hozz létre újat."
    │ Nincs parcella a usernek → "Hozz létre parcellát a Parcellakezeles modulban."
```

---

## 4. ODM job sorkezelés döntés

```
Job indítása kattintva
  │
  ▼
Van jelenleg futó ODM job a szerveren?
  │ NEM → job azonnal indul (státusz: processing)
  │ IGEN → job sorba áll (státusz: queued)
  │         "Jelenleg egy feldolgozás fut. A tiéd X. a sorban.
  │          Becsült várakozási idő: ~X perc."
  ▼
Maximum párhuzamos ODM job = 1
(RAM korlát miatt — egyszerre csak 1 ODM futhat)
```

---

## 5. Feldolgozási preset kiválasztási szabályok

```
Képszám < 100 ÉS terület < 2 ha:
  → Alapértelmezett preset ajánlott: "Gyors"
  → Standard és Minőségi is választható

Képszám 100-300 VAGY terület 2-10 ha:
  → Alapértelmezett: "Standard"
  → Minőségi elérhető, de figyelmeztető: "~X perc feldolgozás"

Képszám > 300 VAGY terület > 10 ha:
  → Alapértelmezett: "Standard"
  → Minőségi letiltva: "Ennél a méretnél a Minőségi preset nem ajánlott —
     szerver erőforrás korlát miatt megbízhatatlan lehet."
```

---

## 6. Kész ortomozaik minőség döntés

```
ODM feldolgozás befejezése után:
  │
  ▼
output/odm_orthophoto/odm_orthophoto.tif létezik?
  │ NEM → job státusz: "failed", hibaüzenet ODM log-ból
  │ IGEN
  ▼
GeoTIFF felbontás ellenőrzés:
  pixel size <= 0.05 m/px? (5 cm/px küszöb)
  │ NEM → figyelmeztetés: "Az ortomozaik felbontása alacsony ({X} cm/px).
  │         Tőszámláláshoz legalább 2 cm/px ajánlott.
  │         Javasolt: repülj alacsonyabban vagy válts Minőségi presetre."
  │ IGEN → ✅ "Ortomozaik kész: {X} cm/px felbontás"
  ▼
GeoTIFF automatikusan csatolva a parcellához
→ M06 "Spektrális elemzés" tab-ban azonnal látható
→ M03 Counting oldalon feltöltési forrásként megjelenik
```

---

## 7. Tárhely kvóta döntés

```
Feldolgozás előtt:
  user_used_storage + estimated_output_size > user_quota?
    │ IGEN → "Nincs elég tárhely. Szükséges: ~X GB, Szabad: Y GB.
    │          Törölj korábbi fájlokat vagy bővítsd a csomagod."
    │          → ODM nem indítható
    │ NEM → folytatás

Becsült output méret:
  ~200-500 MB / 1 ha ortomozaik (standard GSD, GeoTIFF)
```

---

## 8. Nyersképek törlési döntés

```
ODM feldolgozás SIKERES befejezése után:
  │
  ▼
Nyers feltöltött JPG/TIFF képek:
  Alapértelmezés: 24 óra után automatikusan törlődnek
  (csak az ortomozaik GeoTIFF marad meg)

  Kivétel: ha a felhasználó "Képek megtartása" opciót választott
  → képek a tárhely kvótájába beleszámítanak
  → felhasználó manuálisan törölheti később

ODM feldolgozás SIKERTELEN:
  → Nyers képek 72 óráig megmaradnak (újrafuttatáshoz)
  → 72 óra után automatikusan törlődnek
```
