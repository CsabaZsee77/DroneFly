# L4 – Tranzakciós és Párhuzamos Kezelés – Grid Engine

**Modul:** M02
**Szint:** L4 – Tranzakciós és Párhuzamos Kezelés
**Verzió:** v1.0.0
**Létrehozva:** 2026-04-02
**Státusz:** ✅ Implementálva (v1.0.0)

---

## 1. Állapotmentesség

A GridMissionGenerator és GsdCalculator **állapotmentes, tiszta számítás** —
nem módosítanak adatbázist, nem tartanak fenn belső állapotot.

```
Bemenetek:  polygonPoints (read-only), config (read-only)
Kimenet:    GeneratorResult (új objektum minden hívásnál)
Mellékhatás: nincs
```

Ez garantálja, hogy:
- Ugyanazokkal a bemenetekkel mindig ugyanazt az eredményt adják
- Párhuzamosan bármikor hívhatók (thread-safe)
- Újrahívás bármikor biztonságos (pl. paraméter módosítás után)

---

## 2. Számítási pontosság

```
double típus (64-bit IEEE 754):
  Pontosság: ~15-16 tizedesjegy
  GPS koordináta: 1e-7 fok ≈ 1 cm → elegendő

Koordináta transzformáció halmozódó hiba:
  GPS → XY méter:     < 0.1 m (kis területen, ~100 km sugarú körön belül)
  XY rotáció:         < 1 mm (lebegőpontos rotáció)
  XY → GPS vissza:    < 0.1 m
  Összesített hiba:   < 0.2 m
  → Mezőgazdasági felmérési célra elhanyagolható
```

---

## 3. Memóriahasználat

```
WaypointData mérete: ~64 byte (4× double + float + boolean + float)

| Waypontok | Memória |
|-----------|---------|
| 99        | ~6 KB   |
| 500       | ~32 KB  |
| 3000      | ~192 KB |
| 10000     | ~640 KB |

Crystal Sky (4 GB RAM) → nincs memóriagond még nagy területeknél sem.
android:largeHeap="true" biztosítja a rendelkezésre álló heap méretet.
```

---

## 4. Újrahívási biztonság

```
generateMission() az M01-ből:
  │
  ├─ Paraméter változás → újrahívás → régi GeneratorResult eldobva
  │   (Java GC kezeli)
  │
  └─ Polygon változás → újrahívás → azonos logika
```

A korábbi `currentSegments` és `currentWaypoints` listák felülíródnak
az M01-ben — nincs referencia-megtartási probléma, ha az M03/M04
egyszerre nem tartja referenciában.

---

## 5. Matematikai sarokesetek

### Konvex vs. konkáv polygon

```
Konvex polygon (tipikus mezőgazdasági tábla):
  → scanline minden Y értéknél pontosan 2 metszéspontot ad
  → Egyszerű, megbízható

Konkáv polygon (L alakú terület stb.):
  → scanline 4+ metszéspontot adhat
  → Algoritmus páronként kezeli: [X1,X2], [X3,X4] = 2 sáv a scanline-on
  → Helyes lefedettség, de több sávra törheti a missziót

Önmetsző polygon:
  → Meghatározatlan viselkedés
  → Az M01 UI nem ellenőriz önmetszést
  → Ajánlás: önmetsző polygon elkerülése a rajzolásnál
```

### Nagyon kis terület (< 100 m²)

```
→ stripSpacingM > terület átmérő → 0 scanline metszés
→ totalWaypoints = 0 → errorMessage = "túl kicsi terület"
```

### Egyetlen sáv (nagyon kicsi terület, nagy sávköz)

```
→ Lehetséges: 1 sáv, néhány waypoint
→ Helyes működés — nem hibaállapot
```

### flightAngleDeg = 0° és 90°

```
0°:  sávok É–D irányban (x tengely K–Ny) → szokásos eset
90°: sávok K–Ny irányban → rotáció 90° → matematikailag azonos logika
179°: közel 90° elforgatás — rendben
```
