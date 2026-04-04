# L1 – Üzleti Folyamat – Misszió Tervező

**Modul:** M01
**Szint:** L1 – Üzleti Folyamat
**Verzió:** v1.0.0
**Létrehozva:** 2026-04-02
**Státusz:** ✅ Implementálva (v1.0.0)

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
- GSD alapú pontos magasságszámítás a P4P v1 kameraparaméterei alapján
- Kígyózó (lawnmower) minta garantálja a teljes területlefedettséget
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
Térkép betöltése (OSMDroid, OpenStreetMap tiles)
  - Alapértelmezett nézet: Magyarország (47.1°N, 19.5°E), zoom 7
  - GPS gomb (📍): aktuális pozícióra ugrik
      │
      ▼
Terület meghatározása — két lehetőség:
  A) Polygon rajzolása: "Terület rajzolása" gomb → tegyen érintésre csúcspontokat
  B) CSV importálás: "CSV importálása" → meglévő Litchi misszió betöltése
      │
      ▼
Paraméterek beállítása (SeekBar-ok a jobb oldali panelen):
  - GSD (cm/px): 0.5–9.5 cm/px (lépés: 0.1), default: 3.0 cm/px
  - Sidelap (%): 50–90%, default: 75%
  - Frontlap (%): 50–80%, default: 80%
  - Sebesség (m/s): 3–15 m/s, default: auto (GSD-ből számított)
  - Repülési irány (°): 0–179°, default: 0° (É–D irány)
      │
      ▼
Start/Home pont beállítása:
  - Hosszan érintés a térképen → Home pont elhelyezése (zöld marker)
  - Alapértelmezett: az első waypont
      │
      ▼
"Misszió generálása" → GridMissionGenerator futtatása
  - Eredmény: narancs polyline a térképen
  - Statisztikák megjelenítése (lent a panelen):
      Terület: X ha
      Magasság: X m
      Waypontok: X db (N szegmens)
      Képek: ~X db
      Becsült idő: ~X perc
      Sávköz: X m | Fotóköz: X m
      │
      ▼
Döntés — három lehetőség:
  ├─ A) [Feltöltés + Start] → M04 DJI feltöltés → misszió végrehajtás
  ├─ B) [Exportálás (CSV / KMZ)] → M03 export → DJI Pilot / Litchi
  └─ C) Paraméterek módosítása → vissza a generáláshoz
      │
      ▼  (A útvonal esetén)
Misszió közbeni vezérlés:
  - [⏸ Szünet]: drón lebeg az aktuális pozícióban
  - [⏹ Stop]: misszió leáll, kézi vezérlés átvehető
  - Következő szegmens indítása (ha akkumulátorcsere után folytatás szükséges)
```

---

## 4. Polygon rajzolás részletei

```
"Terület rajzolása" gomb megnyomva
  │
  ▼
Draw mód aktív (gomb szövege: "Rajzolás AKTÍV")
  │
  ▼
Képernyő érintések:
  - Egyszerű érintés: új csúcspont hozzáadása (kék marker + vonal)
  - Minimum 3 pont után: zárt polygon megjelenítve
  │
  ▼
Hosszú érintés bármikor:
  → Start/Home pont beállítása a térképen (zöld marker)
  (Draw módtól független — bármikor elérhető)
  │
  ▼
"Terület rajzolása" gomb újbóli megnyomása → Draw mód kikapcs
"Törlés" gomb → polygon + útvonal + markerek törlése
```

---

## 5. Eseményindítók

| Esemény | Következmény |
|---------|-------------|
| GSD SeekBar módosítva | Javasolt sebesség újraszámítása, label frissítése |
| Sidelap / Frontlap módosítva | Label frissítése |
| "Misszió generálása" megnyomva | GridMissionGenerator futtatása, térkép frissítés, statisztikák |
| "Feltöltés + Start" megnyomva | MSDK upload + mission start, Pause/Stop gombok engedélyezve |
| "⏸ Szünet" megnyomva (aktív misszió) | pauseMission() hívás, gomb szövege "▶ Folytatás"-ra vált |
| "▶ Folytatás" megnyomva | resumeMission() hívás, gomb szövege "⏸ Szünet"-re vált |
| "⏹ Stop" megnyomva | Megerősítő dialóg → stopMission() |
| 📍 gomb megnyomva | locationOverlay.getLastFix() → térkép animáció |
| "CSV importálása" megnyomva | Fájlválasztó → CsvMissionParser → waypontok megjelenítése |
| "Exportálás" megnyomva | CSV / KMZ választó dialóg → fájl mentés + megosztás |

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
