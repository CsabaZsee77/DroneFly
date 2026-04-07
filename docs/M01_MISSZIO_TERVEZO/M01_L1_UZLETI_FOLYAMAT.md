# L1 – Üzleti Folyamat – Misszió Tervező

**Modul:** M01
**Szint:** L1 – Üzleti Folyamat
**Verzió:** v1.6.0
**Létrehozva:** 2026-04-02
**Utolsó módosítás:** 2026-04-07
**Státusz:** ✅ Implementálva (v1.6.0)

---

## 1. Modul célja

Az M01 modul egy **mezőgazdasági survey drón misszió teljes tervezési folyamatát**
valósítja meg Android eszközön, közvetlenül a DJI Crystal Sky kijelzőn futva.

A felhasználó:
1. Térképen megrajzolja a felmérni kívánt területet
2. Beállítja a kívánt GSD értéket és az átfedési paramétereket
3. Az app automatikusan kiszámítja a repülési magasságot és generálja a kígyózó útvonalat
4. Az útvonalat feltölti a drónra, vagy exportálja CSV/KMZ formátumban

**Üzleti értékek:**
- Nincs szükség külső mission planning szoftverre (DroneDeploy, Dronelink stb.)
- GSD alapú pontos magasságszámítás az adott drón kameraparaméterei alapján
- Több drón profil támogatása (P4P v1, Mavic 2 Pro, Mini 3, Air 2S stb.)
- Kígyózó (lawnmower) minta garantálja a teljes területlefedettséget
- **Auto-generálás:** minden pont lerakásakor/mozgatásakor/törlésekor azonnal újragenerál
- **Szerkeszthető polygon markerek:** ujjal húzható csúcspontok, kattintásra törölhetők
- **Offset (túlrepülési határ):** a drón a megrajzolt területhatáron túl is kap waypointot
- **Akadályjelölés:** veszélyes akadályok (fa, torony) jelölhetők körrel, a generátor kihagyja a waypontokat az akadály zónájában
- **Domborzatkövetés:** SRTM DEM alapján a waypointok magassága korrigálható
- **Státuszsáv:** drón neve, RC kapcsolat+akkumulátor, GPS műholdak, drón akkumulátor, tablet akkumulátor
- Litchi CSV export — a meglévő webes misszió-tervező adatai importálhatók
- KMZ export — DJI Pilot appban megnyitható
- Akkumulátorcsere kezelés: 99 waypontnál nagyobb misszió auto-felosztás
- Közvetlenül a Crystal Sky-on futtatható, nincs szükség számítógépre

---

## 2. Szereplők

| Szereplő | Szerepkör |
|----------|-----------|
| Drónos operátor | Területet rajzol, paramétereket állít, missziót indít/szüntet/állít le |
| DroneFly app | Paramétereket számít, útvonalat generál, feltölt, exportál |
| DJI MSDK v4 | A waypoint missziót végrehajtja a drónon |
| DJI Pilot app (külső) | KMZ importálása (alternatív végrehajtás) |
| Litchi app (külső) | CSV importálása (alternatív végrehajtás) |

---

## 3. Fő folyamat

```
[Operátor] → MissionPlannerActivity megnyitása
      │
      ▼
Státuszsáv (lebegő sziget, képernyő tetején):
  Drón neve | RC: OK/nincs + akku% | SAT: N H | AKKU: N% | TAB: N%
  2 másodpercenként frissül
      │
      ▼
Térkép betöltése (OSMDroid, ESRI World Imagery műhold tiles)
  - Alapértelmezett nézet: Magyarország (47.1°N, 19.5°E), zoom 7
  - GPS gomb: aktuális pozícióra ugrik
  - SAT/MAP gomb: műhold ↔ utcatérkép váltás
      │
      ▼
Terület meghatározása — két lehetőség:
  A) Polygon rajzolása:
     - Egyszerű érintés → csúcspont lerakás (azonnal!)
     - Hosszan tartva húzható → csúcspont szerkesztés
     - Csúcspontra kattintás → törlés dialog
     - "Utolsó pont törlése" gomb → undo
     - 3+ pontnál: AZONNAL auto-generálás (nincs külön "generálás" gomb szükséges)
  B) CSV importálás: "CSV importálása" → meglévő Litchi misszió betöltése
      │
      ▼
Paraméterek beállítása (jobb oldali csúsztatható panel):
  - Drón profil: Spinner (P4P v1, Mavic 2 Pro, Mini 3 Pro, Air 2S, stb.)
  - GSD (cm/px): 0.5–10.0 cm/px (lépés: 0.1), default: 3.0 cm/px
  - Sidelap (%): 50–90%, default: 75%
  - Frontlap (%): 60–90%, default: 80%
  - Sebesség (m/s): 3–15 m/s, default: auto (GSD-ből számított)
  - Repülési irány (°): 0–179°, default: 0° (É–D irány)
  - Offset (m): 0–30 m, default: 0 (túlrepülési határ)
  → Minden változás után az auto-generálás lefut, ha ≥3 pont van
      │
      ▼
(Opcionális) Akadályjelölés:
  - "+ Akadály jelölése" gomb → obstacle mód aktív
  - Kattintás a térképen → dialog: biztonsági sugár (m) + magasság (m)
  - Piros kör overlay megjelenik a térképen
  - "Akadályok törlése" gomb → összes akadály törlése
  - A generátor kihagyja a veszélyes akadályok körzetébe eső waypontokat
  - Statisztikában jelzi: "⚠ Akadály miatt kihagyva: N wp"
      │
      ▼
(Opcionális) Domborzatkövetés:
  - "Domborzatkövetés" kapcsoló → BE
  - "Újragenerálás" gomb → Open-Elevation API → magasság korrekció
  - Statisztikában: min/max korrigált magasság
      │
      ▼
Start/Home pont beállítása:
  - "Start/Home pont" gomb → térképre kattintás → marker elhelyezés
  - Alapértelmezett: az első waypont
      │
      ▼
Auto-generált misszió eredménye:
  - Narancs polyline a térképen
  - Statisztikák (panel alján):
      Terület: X ha | X pont (N szegmens)
      Magasság: X m | Sávköz: X m | Fotóköz: X m
      Sebesség: X m/s | Idő: ~X perc
      [⚠ Akadály miatt kihagyva: N wp]  ← ha van akadály
      │
      ▼
Döntés — három lehetőség:
  ├─ A) [Feltöltés] + [START] → M04 DJI feltöltés → misszió végrehajtás
  ├─ B) [Exportálás (CSV / KMZ)] → M03 export → DJI Pilot / Litchi
  └─ C) Paraméterek módosítása → auto-újragenerálás
      │
      ▼  (A útvonal esetén)
Misszió közbeni vezérlés:
  - [Szünet]: drón lebeg az aktuális pozícióban
  - [Stop]: misszió leáll, kézi vezérlés átvehető
  - Következő szegmens indítása (ha akkumulátorcsere után folytatás szükséges)
```

---

## 4. Polygon rajzolás részletei

```
Nincs külön "Draw mód" gomb — a térkép érintése MINDIG csúcspontot rak le
(kivéve ha obstacle mód vagy start pont mód aktív, vagy misszió fut)
  │
  ▼
Képernyő érintések:
  - Egyszerű érintés (rövid): új csúcspont hozzáadása
      → kék marker (ANCHOR_CENTER, ANCHOR_BOTTOM: csúcs = a koordináta)
      → polygon vonal rajzolódik (zárt, 3+ pontnál)
      → autoGenerateIfReady() azonnali hívás
  - Hosszan nyomva + húzás: csúcspont szerkesztése (marker drag)
      → valós idejű polygon-frissítés húzás közben
      → autoGenerateIfReady() a húzás végén
  - Csúcspont kattintás: törlés dialog
      → "P1 jelölő | 47.12345, 19.12345 | Töröljem?"
      → Törlés után: újraszámozás + autoGenerateIfReady()
  │
  ▼
Auto-generálás (autoGenerateIfReady):
  - Feltétel: polygonPoints.size() >= 3
  - Terrain following: NEM fut automatikusan (manuális "Újragenerálás" kell)
  - Offset és akadályok: minden auto-generálásnál figyelembe veszi
  │
  ▼
"Utolsó pont törlése" gomb → removeLastPolygonPoint()
"Összes törlés" gomb → polygon + akadályok + útvonal + markerek mind törlődnek
```

---

## 5. Eseményindítók

| Esemény | Következmény |
|---------|-------------|
| Térkép érintés (normál mód) | Csúcspont lerakás + autoGenerateIfReady() |
| Csúcspont húzás | Valós idejű polygon frissítés; drag végén autoGenerateIfReady() |
| Csúcspont kattintás | Törlés dialog |
| Bármely SeekBar módosítva | updateLabels() — label frissítés (auto-generálás NEM fut) |
| GSD SeekBar módosítva | + javasolt sebesség újraszámítása |
| "Újragenerálás" megnyomva | generateMission() + terrain following ha aktív |
| "+ Akadály jelölése" megnyomva | obstacle mód be/ki (gomb szín + szöveg változik) |
| Térkép érintés (obstacle mód) | Akadály dialog (sugár + magasság) → addObstacle() |
| "Akadályok törlése" megnyomva | Megerősítő dialog → összes obstacle eltávolítás |
| "Feltöltés" megnyomva | Megerősítő dialog → MSDK upload → ProgressDialog → AlertDialog (siker/hiba) |
| "START" megnyomva | GPS ellenőrzés (≥6 SAT) → Kamera konfig → mission start, Pause/Stop gombok engedélyezve |
| "Szünet" megnyomva (aktív misszió) | pauseMission() hívás, gomb → "Folytatás" |
| "Folytatás" megnyomva | resumeMission() hívás, gomb → "Szünet" |
| "Stop" megnyomva | Megerősítő dialog → stopMission() |
| GPS gomb megnyomva | locationOverlay.getLastFix() → térkép animáció |
| SAT/MAP gomb megnyomva | Térképforrás váltás (ESRI ↔ OSM) |
| "» / «" toggle gomb (alul) | Oldalpanel animált csúsztatás (250ms, DecelerateInterpolator) — gomb a panel bal szélén, alul (véletlen bezárás ellen) |
| "CSV importálása" megnyomva | Fájlválasztó → CsvMissionParser → waypontok megjelenítése |
| "Exportálás" megnyomva | CSV / KMZ választó dialog → fájl mentés + megosztás |
| 2 mp státusz timer | updateStatusBar() → DJI telemetria + tablet akku frissítés |

---

## 6. Végállapotok

| Állapot | Leírás |
|---------|--------|
| `idle` | Nincs polygon, nincs generált misszió |
| `polygon_drawn` | Polygon megrajzolva, misszió nincs generálva |
| `mission_ready` | Misszió generálva, feltöltésre / exportra kész |
| `mission_running` | Feltöltve és fut, Pause/Stop gombok aktívak |
| `mission_paused` | Szüneteltetett, drón lebeg |
| `exported` | CSV vagy KMZ exportálva |

---

## 7. Akkumulátorcsere kezelés

A MSDK v4 egyetlen misszióban maximum 99 waypointot kezel megbízhatóan.
Nagy területek esetén az app automatikusan szegmensekre osztja a missziót:

```
Generált waypontok > 99?
  │ IGEN → automatikus szegmentálás (GridMissionGenerator)
  │        Statisztika: "342 waypoint (4 szegmens)"
  │        Minden szegmens < 99 waypoint
  │ NEM → egyetlen misszió szegmens
  │
  ▼
[Feltöltés + Start] → 1. szegmens feltöltése és indítása
  │
  ▼
Drón repüli az 1. szegmenst
  │
  ▼  (akkumulátor csere után)
[Feltöltés + Start] → 2. szegmens feltöltése és indítása
  │
  ...N szegmensig
```

---

## 8. Kapcsolódó modulok

| Modul | Kapcsolat típusa |
|-------|-----------------|
| M02 Grid Engine | **Közvetlen hívás** — generateMission() → GridMissionGenerator |
| M03 Export/Import | **Közvetlen hívás** — exportCsv(), exportKmz(), importCsv() |
| M04 DJI Integráció | **Közvetlen hívás** — uploadMission(), pauseMission(), stopMission() |
