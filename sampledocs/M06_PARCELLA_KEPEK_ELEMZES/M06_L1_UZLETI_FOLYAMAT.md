# L1 – Üzleti Folyamat – Parcella Képek és Spektrális Analízis

**Modul:** M06
**Szint:** L1 – Üzleti Folyamat
**Verzió:** v2.0.0
**Létrehozva:** 2026-03-07
**Frissítve:** 2026-03-07 (Sentinel-2 CDSE letöltés)

---

## 1. Modul célja

A M06 modul lehetővé teszi, hogy a felhasználó **drón- és műholdképeket rendeljen konkrét
parcellákhoz**, majd ezeken **spektrális elemzéseket** végezzen (NDVI, NDRE, EVI, SAVI).

**Üzleti értékek:**
- Parcella szintű képnyilvántartás: melyik felvétel, mikor, milyen típusú
- Növényzet-egészség monitorozás spektrális indexekkel
- Drón (2-10 cm GSD) és műhold (10 m GSD, Sentinel-2) adatok együtt kezelhetők
- Időbeli NDVI trend a parcellán (vetéstől aratásig)
- **Sentinel-2 L2A képek letöltése közvetlenül az appból** (Copernicus CDSE API)

---

## 2. Szereplők

| Szereplő | Szerepkör |
|----------|-----------|
| Gazdálkodó felhasználó | Feltölt képet, keresést indít, letöltést vezérel, elemzést megtekint |
| Rendszer | Képet tárol, spektrális indexet számít, vizualizál, CDSE-vel kommunikál |
| Drón GeoTIFF | Helyi feltöltött fájl, georeferált |
| CDSE (Copernicus Data Space) | Külső rendszer: Sentinel-2 L2A termékek keresése és letöltése |

---

## 3. Fő folyamat: Kép hozzárendelése és elemzése

```
[Felhasználó] → Parcel Analysis oldal megnyitása
      │
      ▼
[M06] Parcella kiválasztás (sidebar selectbox)
      │
      ▼
[Tab 1: Képek]
      ├─ Meglévő képek megtekintése (lista + thumbnail)
      ├─ Kép feltöltés (drag & drop GeoTIFF)
      │    └─ Metaadat: típus (drón/műhold), dátum, megjegyzés
      └─ Kép eltávolítása (link törlés, fizikai fájl marad)
      │
      ▼
[Tab 2: Spektrális elemzés] (ha van multi-band kép)
      ├─ Kép kiválasztása az elemzéshez
      ├─ Band elrendezés: auto-felismerés + manual override
      ├─ Index kiválasztás: NDVI / NDRE / EVI / SAVI
      ├─ Számítás → Bal: RGB kép, Jobb: színezett index térkép
      ├─ Statisztikák: átlag, szórás, percentilisek, kategória megoszlás
      └─ Export: PNG letöltés
      │
      ▼
[Tab 3: Térkép]
      ├─ Parcella polygon a térképen
      ├─ Kép overlay (georeferált képek)
      └─ Opacity slider
      │
      ▼
[Tab 4: Idősor] (ha ≥2 kép)
      └─ NDVI átlag idősorban → st.line_chart
      │
      ▼
[Tab 5: Sentinel-2 letöltés]
      ├─ CDSE hitelesítés (email + jelszó; .env-ből ha be van állítva)
      ├─ Keresési paraméterek: dátumtól, dátumig, max felhőborítottság
      ├─ [Keresés] → CDSE OData API → találatok listája
      │    (dátum, tile ID, felhőborítottság %, fájlméret MB)
      ├─ [Letöltés] gomb soronként → SAFE zip letöltés + kicsomagolás
      ├─ Band stacking → 12-sávos GeoTIFF (parcella területre vágva)
      └─ Automatikus csatolás a parcellához → rögtön elemezhető
```

---

## 4. Előfeltételek

- Felhasználó be van jelentkezve (M04)
- Legalább egy parcella létezik (M06 Parcellák oldal, 7_Parcels.py)
- Multi-band GeoTIFF szükséges a spektrális elemzéshez (≥4 sáv)
- Rasterio és PyProj telepítve
- **Sentinel-2 letöltéshez:** ingyenes CDSE fiók (dataspace.copernicus.eu), internet-hozzáférés, ~3× a SAFE méretének szabad lemezterület a `data/temp/` mappában

---

## 5. Végállapotok

| Állapot | Leírás |
|---------|--------|
| Kép csatolva (manuális) | A parcella JSON-ban megjelenik az `images` listában |
| Sentinel-2 letöltve és csatolva | 12-sávos GeoTIFF a `data/uploads/`-ban, parcellához kötve |
| Elemzés kész | Index értékek számítva, vizualizáció látható |
| Export kész | PNG fájl letöltve |
| Kép eltávolítva | Az `images` listából eltávolítva (fájl megmarad) |
| CDSE keresés sikertelen | Hitelesítési hiba vagy nincs találat – hibaüzenet, rendszer stabil |
| Letöltés megszakadt | Temp könyvtár takarítva, kép nem csatolva, hibaüzenet |

---

## 6. Kapcsolódó modulok

| Modul | Kapcsolat |
|-------|-----------|
| M03 Counting | A YOLO detekció eredményei és a kép overlay együtt jeleníthetők meg |
| M04 Felhasználókezelés | `require_module("spectral")` kapuzás — `spectral` modul szükséges |
| M06 Parcellák (7_Parcels) | Parcellák kezelése, link az analízis oldalra |
| M07 Repülési Terv | A flight plan GeoTIFF-jét közvetlenül fogadja idősor elemzéshez |
| M08 ODM Ortomozaik | Az ODM kész GeoTIFF-je automatikusan megjelenik itt |

---

## 7. Tervezett jövőbeli bővítés

- **Drón ortofotó és Sentinel-2 összehasonlítás** ugyanazon dátumon
- **Anomália detekció**: NDVI alapú betegség/stresszterület jelölés
- **Ajánlott beavatkozási zóna** generálás
- **Automatikus Sentinel-2 ütemezés**: hetente automatikus keresés + letöltés ha felhőborítottság < küszöb

---

*L2 döntési logika: [M06_L2_DONTESI_LOGIKA.md](M06_L2_DONTESI_LOGIKA.md)*
