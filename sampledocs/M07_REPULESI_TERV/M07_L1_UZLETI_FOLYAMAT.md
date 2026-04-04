# L1 – Üzleti Folyamat – Repülési Terv Generátor

**Modul:** M07
**Szint:** L1 – Üzleti Folyamat
**Verzió:** v1.2.0
**Létrehozva:** 2026-03-16
**Utolsó frissítés:** 2026-03-31
**Státusz:** ✅ Implementálva (v1.2.0)

---

## 1. Modul célja

Az M07 modul lehetővé teszi, hogy a felhasználó egy **meglévő parcellájához repülési tervet
generáljon**, amelyet KMZ formátumban tölt le és közvetlenül a DJI Fly appba importálva
futtat. A rendszer a drón kameraprofilja és a kívánt felbontás (GSD) alapján automatikusan
kiszámítja a szükséges repülési magasságot, sebességet és sávközöket.

**Üzleti értékek:**
- Nincs szükség külső mission planning szoftverre (Dronelink, DroneDeploy stb.)
- Fogyasztói drónnal (DJI Mini 3, Mini 4 Pro) és professzionális drónnal (Phantom 4 Pro V2) is elvégezhető felmérés
- **Litchi app kompatibilis CSV export** — Phantom 4 Pro V2 és más, DJI Fly-t nem támogató drónok számára
- **Repülési terv mentése** — poligon + paraméterek elmenthetők névvel, újra betölthetők és bármely formátumban exportálhatók
- Parcella-tudatos tervezés: a repülési terv a meglévő parcellaadatokhoz kötődik
- Idősor-tudatos ismétlés: ugyanaz a terv újra letölthető ugyanolyan paraméterekkel
- Csak a **`flight` modul** engedélyezésével érhető el (M04 modul rendszer)

---

## 2. Szereplők

| Szereplő | Szerepkör |
|----------|-----------|
| Gazdálkodó / drónos operátor | Drónt választ, paramétereket állít be, terveket ment/tölt, exportál |
| Rendszer | Kiszámítja a repülési paramétereket, generálja a grid-et, KMZ / Litchi CSV-t állít elő, terveket perzisztál |
| DJI Fly app (külső) | A KMZ-t fogadja (DJI Mini 3/4 Pro, Air 3, Mavic 3 sorozat) |
| Litchi app (külső) | A CSV-t fogadja (Phantom 4 Pro V2 és más régebbi DJI drónok) |

---

## 3. Fő folyamat

```
[Felhasználó] → Flight Planning oldal megnyitása
      │
      ▼
Előfeltétel: require_module("flight") — `flight` modul engedélyezve?
  │ NEM → "Ez a funkció a ✈️ Repülési Terv modulban érhető el." + stop
  │ IGEN
  ▼
Parcella kiválasztás (meglévő parcellák listájából)
      │
      ▼
Drón profil kiválasztás (dropdown)
  Pl.: DJI Mini 3 / DJI Mini 3 Pro / DJI Mini 4 Pro / egyéb
      │
      ▼
Paraméterek megadása:
  - Kívánt GSD (cm/px) — csúszka + szám bevitel, ajánlott: 1.0–2.5
  - Front overlap % (default: 75)
  - Side overlap % (default: 65)
  - Repülési sebesség (default: auto, 4–6 m/s)
      │
      ▼
Számított paraméterek megjelenítése (valós idejű):
  - Repülési magasság: X m
  - Sávszélesség a talajon: X m
  - Sávok száma: X
  - Becsült képek száma: ~X db
  - Becsült repülési idő: ~X perc (akkumulátorigény jelzéssel)
  - Terület: X ha (parcellából)
      │
      ▼
Térkép preview:
  - Parcella polygon megjelenítve
  - Generált repülési sávok overlay-ként
  - Indulási pont jelölve
      │
      ▼
Export szekció — két gomb egymás mellett:
  ├─ [⬇ KMZ letöltése] — DJI Fly WPML formátum (Mini 3/4 Pro, Air 3, Mavic 3)
  └─ [⬇ Litchi CSV letöltése] — Litchi Mission Hub CSV (Phantom 4 Pro V2, stb.)
      │
      ▼
[💾 Repülési terv mentése] expander — opcionális
  - Névvel ment: poligon + összes paraméter → data/users/{user_id}/flight_plans.json
  - Mentett tervek az oldal tetején betölthetők → bármelyik formátumban újra exportálhatók
      │
      ▼
[Repülés elvégezve] visszajelzés (opcionális manuális visszajelzés)
  → Státusz: "flown" → az idősor nézet megjelenik a parcellánál
```

---

## 4. Idősor-tudatos ismételt felmérés

```
Parcella → "Repülési előzmények" tab
  │
  ├─ 2026-05-10: DJI Mini 4 Pro, GSD 1.5 cm, 52m, 342 kép — ✅ flown
  ├─ 2026-05-24: [Újrafuttatás] → azonos paraméterek → KMZ újragenerálva
  └─ ...

Az újrafuttatás garantálja, hogy az idősor összehasonlítás érvényes:
azonos GSD → azonos felbontás → összehasonlítható tőszám
```

---

## 5. Eseményindítók

| Esemény | Következmény |
|---------|-------------|
| Parcella kiválasztva | Térkép beállítódik, terület kiszámítva |
| GSD vagy overlap módosítva | Paraméterek valós idejű újraszámítása |
| [KMZ Letöltés] kattintva | KMZ generálva, letöltve (DJI Fly) |
| [Litchi CSV Letöltés] kattintva | Litchi CSV generálva, letöltve (Litchi app) |
| [💾 Terv mentése] kattintva | Poligon + paraméterek elmentve user-scoped JSON-be |
| [⬇️ Betöltés] (mentett terv) kattintva | Poligon + paraméterek visszaállítva, oldal újratölt |
| [Repülés elvégezve] kattintva | Státusz: flown, M08 ODM feldolgozás indítható |

---

## 6. Végállapotok

| Állapot | Leírás |
|---------|--------|
| `exported` | KMZ letöltve, repülés még nem történt |
| `flown` | Felhasználó visszajelezte, hogy elvégezte a repülést |
| `processed` | A repüléshez tartozó képek ODM-en átmentek (M08 kapcsolat) |

---

## 7. Teljes Felmérési Workflow — M07 mint belépési pont

Az M07 a **`flight` + `odm` modul integrált workflow-jának első lépése**. A teljes folyamat:

```
╔══════════════════════════════════════════════════════════════════╗
║                   INTEGRÁLT FELMÉRÉSI WORKFLOW                   ║
╚══════════════════════════════════════════════════════════════════╝

[M06 Parcellakezeles]
  Parcella polygon megrajzolva / importálva
        │
        ▼
[M07 Repülési Terv]  ← TE VAGY ITT
  Drón profil + GSD → KMZ generálva és letöltve
        │
        ▼  (KMZ → DJI Fly app)
[TEREPI REPÜLÉS]
  Drón végigrepüli a sávokat, képek mentve a memóriakártyára
        │
        ▼  (felhasználó: "Repülés elvégezve" visszajelzés M07-ben)
[M08 ODM Ortomozaik]
  Nyers JPG képek feltöltve → NodeODM feldolgozás → GeoTIFF kész
        │
        ▼  (automatikus parcella-csatolás)
[M06 Spektrális Elemzés]        [M03 Tőszámlálás]
  NDVI / VARI elemzés               Sliding window inference
  Stressz térkép                    Tőszám, GPS koordináták
  Idősor (következő felméréshez)    CSV / GeoJSON export
```

### M07 státuszátmenet a teljes workflow-ban

```
[exported]                     KMZ letöltve, drón felkészítve
    │
    │ (felhasználó visszajelez: repülés kész)
    ▼
[flown]                        Képek a memóriakártyán, feltöltés M08-ba
    │
    │ (M08 job befejezése után automatikus frissítés)
    ▼
[processed]                    GeoTIFF kész, parcellához csatolva,
                               M03/M06 elemzés elvégezhető
```

---

## 8. Kapcsolódó modulok

| Modul | Kapcsolat típusa |
|-------|-----------------|
| M06 Parcellakezeles | **Bemeneti függőség** — parcella polygon és növényfajta forrása |
| M08 ODM Ortomozaik | **Közvetlen folytatás** — M07 `flown` státusz triggereli az M08 ajánlatot |
| M03 Counting | **Közvetett kimenet** — M08 GeoTIFF-jén fut a tőszámlálás |
| M04 Felhasználókezelés | **Kapuzás** — `require_module("flight")` hívás az oldal tetején |
