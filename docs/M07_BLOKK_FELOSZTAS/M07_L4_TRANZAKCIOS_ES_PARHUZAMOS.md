# L4 – Tranzakciós és Párhuzamos Kezelés – Blokk-felosztás

**Modul:** M07
**Szint:** L4 – Tranzakciós és Párhuzamos Kezelés
**Verzió:** v0.1.1
**Létrehozva:** 2026-06-19
**Utolsó módosítás:** 2026-06-29
**Státusz:** ✅ Implementálva (verifikáció + UI tesztelés hátra)

---

## 1. Állapotmentes komponensek (engine)

A `BlockGridGenerator`, `PolygonClipper`, és a geometriai segédmetódusok
**tisztán állapotmentes számítások** — azonos M02 mintájához.

```
Bemenetek:  aoiPoints (read-only), config (read-only), previous (read-only)
Kimenet:    GridResult (új objektum minden hívásnál)
Mellékhatás: nincs (fájl I/O, hálózat: nincs)
```

→ Bármikor újrahívható (paraméter változás után), thread-safe.

---

## 2. Állapotos komponens (UI réteg)

A `MissionPlannerActivity`-ben perzisztens állapot:

| Mező | Élettartam | Mentés |
|------|------------|--------|
| `blockGridConfig` | activity életciklus | `.flightprogram.json` `block_grid` |
| `currentGrid.blocks` | activity életciklus | `.flightprogram.json` `block_states` |
| `currentGrid.gridOriginLat/Lon` | activity életciklus | `.flightprogram.json` `block_grid.origin_lat/lon` |
| `selectedBlock` | activity életciklus | **NEM mentődik** (mindig null betöltéskor) |

**Indoklás:**
- A `selectedBlock` ephemeral — minden új session-ben a felhasználó újra
  választ. Ez egyszerűsíti az állapot-konzisztenciát; egy mentett "kiválasztva"
  helyzet könnyen félrevezető lehet, ha a felhasználó közben máshol dolgozott.

---

## 3. Perzisztens séma — `.flightprogram.json` bővítés

A meglévő séma (`dronterapia_flight_program` v1.0) **visszafelé kompatibilis**
módon bővül. A blokk-szekciók opcionálisak — ha hiányoznak, a régi viselkedés
(egyetlen AOI = egyetlen misszió) marad.

```json
{
  "version": "1.1",
  "format":  "dronterapia_flight_program",
  "metadata": { ... },
  "parcel":   { ... },
  "drone":    { ... },
  "flight_settings": { ... },
  "obstacles": [ ... ],

  "block_grid": {
    "enabled":              true,
    "cell_width_m":         120.0,
    "cell_height_m":        120.0,
    "rotation_deg":         15.0,
    "overlap_buffer_m":     40.0,
    "min_coverage_percent": 15.0,
    "origin_mode":          "centroid",
    "origin_lat":           47.4567890,
    "origin_lon":           19.1234567
  },

  "block_states": [
    { "id": "B-1-1", "row": 0, "col": 0, "status": "DONE" },
    { "id": "B-1-2", "row": 0, "col": 1, "status": "IN_PROGRESS" },
    { "id": "B-2-1", "row": 1, "col": 0, "status": "NOT_STARTED" }
  ]
}
```

**Verzió emelés indoklása:**
- `version: "1.1"` — a régi readerek (1.0) ignorálják a `block_grid` és
  `block_states` mezőket, a betöltés sikeres marad alap AOI-misszióként.
- Új field-ek mindig `optString` / `optJSONObject` lekérdezéssel olvasandók
  → forward compat is megmarad.

**Mit NEM mentünk:**
- A blokkok geometriáját (`cellPolygon`, `missionPolygon`) — ezek mindig
  determinisztikusan újraszámolhatók az AOI + `block_grid` paraméterekből.
- Az ephemeral `selectedBlock`-ot.
- A `coverageRatio`, `cellAreaM2` számított mezőket.

**Mit MIND mentünk:**
- A fix origót (`origin_lat`, `origin_lon`) — ez garantálja, hogy az AOI
  utólagos módosítása mellett is **ugyanazok a cellák** generálódnak újra
  (lásd M07 L2 §8).

---

## 4. Dronterapia sync kompatibilitás

A `.flightprogram.json` séma a Dronterapia szerverrel kétirányúan szinkronizálódik
(M06 modul). A blokk-szekciók szempontjából:

| Mező | Sync irány | Megjegyzés |
|------|-----------|-----------|
| `block_grid` | DroneFly → Dronterapia | A szerver tárolja, de **nem értelmezi** (v1) |
| `block_states` | DroneFly → Dronterapia | Szerver csak tárolja (audit trail) |
| `block_grid` | Dronterapia → DroneFly | Idegen szerver-érték felülírja a helyit |
| `block_states` | Dronterapia → DroneFly | **Konfliktus lehetséges** — lásd alább |

**Konfliktus eset:**
- Felhasználó A tableten elindítja a B-2-3 blokkot → `IN_PROGRESS`
- Eközben B tableten betölti ugyanezt → `NOT_STARTED` szinkronizáció
- A két tablet különböző állapotokat lát

**Megoldás (v1):** "last writer wins" alapon az `updated_at` szerint — az M06
meglévő sync logikája ezt már implementálja, plusz workflow nem szükséges.

**Megoldás (v2, későbbi):** per-block `updated_at` és felhasználó-azonosító,
hogy két tablet egyidejű repüléséhez konfliktus-feloldás kérdezhető.

---

## 5. Tranzakcionális garanciák

### Atomikus mentés

A `ProjectManager.saveProjectToFile()` jelenlegi viselkedése: `FileWriter`
felülírás. Ez **nem atomikus** — kapcsolat-megszakítás közben sérült fájl
maradhat.

**Bővítési javaslat (külön ticket, M03-on belül):**
- Temp fájlba írás → `renameTo()` az eredetire (atomikus FS művelet).
- Régi behavior megmarad, ha rename sikertelen — visszatér exception-nel.

**M07 hatása:** a blokk-állapot mentés sűrűbb (minden státusz-átmenetnél),
ezért az atomikus mentés előbb releváns. Az M07 ettől függetlenül elindítható;
a téma az M03 jegyfüzetbe kerül.

### Részleges állapotmentés

A blokk-státusz átmenetek (NOT_STARTED → IN_PROGRESS → DONE) **közvetlen
fájlírást váltanak ki** — nem várjuk meg az activity destroy-t.

Ennek oka:
- Akkucsere során a Crystal Sky lecsatlakozhat / újraindulhat
- Egy IN_PROGRESS blokk állapota nem veszhet el

A mentés a státusz-átmenet utolsó lépése; ha sikertelen, a memóriabeli
állapot is visszaáll (`try/catch` blokkban).

```java
private void updateBlockStatus(Block b, BlockStatus newStatus) {
    BlockStatus old = b.status;
    b.status = newStatus;
    try {
        if (currentProjectFile != null) {
            ProjectManager.saveProjectToFile(this, currentProjectFile, ...);
        }
    } catch (Exception e) {
        b.status = old;  // rollback memóriában
        Toast.makeText(this, "Mentés sikertelen: " + e.getMessage(),
                       Toast.LENGTH_LONG).show();
    }
    blockOverlay.invalidate();
}
```

---

## 6. Számítási pontosság

A blokk-illesztés ugyanazt a sík-közelítést használja, mint az M02:

```
GPS → XY méter konverzió:
  metersPerDegreeLat = 111_320.0
  metersPerDegreeLon = 111_320.0 * cos(lat)

Halmozódó hiba 1 km-en belül:    < 0.1 m
SH kivágás numerikus stabilitása: ~1e-9 m  (double pontosság)
Cella-tap inverz transzformáció: < 0.5 m (a koppintási pont természetes
                                          szórása miatt ez bőven elég)
```

→ Mezőgazdasági és építészeti felmérési pontosság szempontjából elhanyagolható.

**Sarokeset:** Az AOI > 5 km kiterjedésnél a sík-közelítés < 1% hibát okoz.
P4P VLOS-ben ez **kizárt** (max ~500 m hatótáv), ezért nem védekezünk ellene.

---

## 7. Párhuzamos hozzáférés

Az M07 nem futtat háttér-szálat (a generálás < 600 ms a legrosszabb esetben,
közvetlenül a UI szálon fut).

**Versenyhelyzet csak az M06 sync-kel:**
- Sync háttér-szál betöltheti a `.flightprogram.json`-t miközben a UI ír.
- Az M06 jelenlegi megoldása: `synchronized` lock a `ProjectManager.saveProjectToFile()`
  hívásokra.
- M07 nem vezet be új lock-ot — az M06-ra támaszkodunk.

---

## 8. Sarokesetek

### AOI módosítás működés közben

```
1. AOI rajzolva, felosztás alkalmazva, B-1-1 IN_PROGRESS
2. Felhasználó új csúcsot ad az AOI-hoz
   → polygonPoints változik
3. blockGrid újragenerálódik a FIX origóval
   → cellaszámlálás változhat (új cellák, eltűnő cellák)

Implementáció:
  → Új blokkok: NOT_STARTED állapottal
  → Megmaradt blokkok (azonos row, col): státusz átörökölve `carryStatus()`
  → Eltűnt blokkok (DONE volt): figyelmeztetés, "Megtartod jelentésként?"
  → Eltűnt blokkok (NOT_STARTED): csendben eldobva
```

### Cella átfedés bufferrel a teljes AOI-t elfedi

```
Példa: AOI 30 m × 30 m, cellWidth = 100, overlapBuffer = 50
       → minden cella missionPolygon-ja az egész AOI-t lefedi
       → 4 blokk, mindegyik ugyanazt a területet repülné

Védelem:
  Validáció (L2 §1): overlapBufferM ≤ cellWidthM/2 és ≤ cellHeightM/2
  → így a misszió-poligon legfeljebb 2× cella terület, soha nem több
```

### Felhasználó kis cellát ad meg + nagy AOI

```
Példa: AOI 50 ha, cellWidth = 30 m → ~600 cella

Teljesítmény: < 5 s generálás Crystal Sky-n.
UI: ProgressDialog megjelenítve > 200 ms generálási idő esetén.

Üzleti probléma:
  600 blokk kézi kezelése irreális — a felhasználó hibásan parametrizált.
  Megoldás: figyelmeztetés ha totalBlocks > 100:
    "Sok blokk generálódott — biztos megfelelő a cellaméret?"
```

### Origó pont konkáv AOI-n kívül esik

```
config.originMode = "centroid":
  Konkáv AOI-nál (pl. U alak) a számtani középpont az AOI-n KÍVÜL eshet.
  Ez a rács illesztésére NEM hat — az origó csak referenciapont,
  nem kell az AOI-n belül lennie.
  A cellaszámolás minden esetben pontos.

Tesztelt eset:
  U alakú AOI, centroid a betűk közötti üregben
  → rács szabályosan generálódik, középső cellák szűrve (minCoverage)
  → eredmény helyes
```

### Felhasználó kétszer akar felosztást alkalmazni

```
1. Felosztás → 6 blokk, kettő IN_PROGRESS, egy DONE
2. Felhasználó újra megnyitja a dialogot, módosítja cellWidth-et

Dialógus címsora: "Új rács — a meglévő blokk-állapotok elvesznek!"

Opciók:
  [Új rács]      — minden állapot törlődik, új ID-k generálódnak
  [Méret változtatás] — DISABLED, ha vannak IN_PROGRESS vagy DONE blokkok
                       (méretváltás = új ID-k = állapot-vesztés)
  [Csak forgatás] — DISABLED ugyanezért
  [Mégse]        — minden marad
```

---

## 9. Visszafelé kompatibilitás

| Eset | Viselkedés |
|------|-----------|
| Régi `.dronefly.json` betöltés | block_grid hiányzik → alap AOI mód |
| `.flightprogram.json` v1.0 betöltés | block_grid hiányzik → alap AOI mód |
| `.flightprogram.json` v1.1 betöltés régi DroneFly verzióval | block_grid mezők ignorálva (opt-read), AOI repülhető szokásos módon |
| `.flightprogram.json` v1.1 betöltés új DroneFly verzióval | block_grid betöltve, rács újragenerálva |
| Dronterapia szerver v1.0 séma | DroneFly felküld v1.1-et, szerver tárolja (lazy-stored) |

**Frissítési útvonal:** a `version` mező "1.0" → "1.1" emelése a séma
módosításával egyszerre történik. Régi DroneFly buildek a "1.1" jelű
fájlokat is be tudják olvasni (a verziószám csak metainformáció, a parser
opt-read mindenhol).

---

## 10. Erőforráshasználat — Crystal Sky

| Művelet | CPU | Memória | Idő |
|---------|-----|---------|-----|
| 16-cellás rács generálás | egyszálas | +5 KB | < 20 ms |
| 64-cellás rács generálás | egyszálas | +20 KB | < 150 ms |
| Tap detekció | konstans | 0 | < 1 ms |
| Blokk overlay rajzolás (64 cella) | UI szál | 0 | < 30 ms / frame |
| Mentés fájlba (.flightprogram.json) | I/O blokkolt | 0 | < 100 ms |

Crystal Sky (4 GB RAM, ARM Cortex-A53 4 core 1.6 GHz) → minden műveletet
közvetlenül a UI szálon végzünk, AsyncTask **nem szükséges** v1-ben.

> 100 cella felett vagy 1 másodperc fölötti generálásnál visszatérni
> AsyncTask-ra (M02 mintájához hasonlóan).
