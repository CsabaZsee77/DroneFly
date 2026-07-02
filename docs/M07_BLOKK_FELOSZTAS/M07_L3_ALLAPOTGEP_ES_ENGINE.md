# L3 – Állapotgép és Engine – Blokk-felosztás

**Modul:** M07
**Szint:** L3 – Állapotgép és Engine
**Verzió:** v0.1.1
**Létrehozva:** 2026-06-19
**Utolsó módosítás:** 2026-06-29
**Státusz:** ✅ Implementálva (verifikáció + UI tesztelés hátra)

---

## Forrásfájlok

| Fájl | Szerepkör | Státusz |
|------|-----------|---------|
| `mission/BlockGridGenerator.java` | Rács illesztés, SH polygon kivágás, blokk lista generálás | ✅ Implementálva |
| `mission/PolygonClipper.java` | Sutherland-Hodgman polygon ∩ konvex clip implementáció | ✅ Implementálva |
| `model/Block.java` | Blokk adatmodell (id, row, col, polygons, status) | ✅ Implementálva |
| `model/BlockStatus.java` | Enum: NOT_STARTED, IN_PROGRESS, DONE | ✅ Implementálva |
| `model/BlockGridConfig.java` | Rács paraméterek (W, H, rot, buffer, minCov, origin) | ✅ Implementálva |
| `model/MissionConfig.java` | **Bővítés** — `BlockGridConfig blockGrid` mező opcionális | ✅ Bővítve |
| `mission/ProjectManager.java` | **Bővítés** — block_grid + block_states szekciók JSON-ban; FORMAT_VERSION 1.0 → 1.1 | ✅ Bővítve |
| `MissionPlannerActivity.java` | **Bővítés** — [Felosztás] gomb, BlockOverlay, blokk-tap handler | ✅ Bővítve |
| `ui/BlockOverlay.java` | OSMDroid Overlay — cellák rajzolása, színkód, kiválasztás kerete | ✅ Implementálva |
| `ui/BlockGridDialog.java` | Dialog helper — Java-ból épített AlertDialog (lásd "Implementációs eltérések") | ✅ Implementálva |
| `res/layout/activity_mission_planner.xml` | **Bővítés** — btnSplit, btnMarkDone, btnResetBlock, tvBlockInfo, blockButtonRow | ✅ Bővítve |
| ~~`res/layout/dialog_block_grid.xml`~~ | ~~Dialog XML~~ — **nem készült el**, lásd "Implementációs eltérések" | ❌ Elhagyva |
| ~~`res/values/strings.xml`~~ | ~~M07 feliratok~~ — **inline szövegek** (a meglévő Activity-stílus szerint) | ❌ Elhagyva |

---

## Implementációs eltérések (2026-06-29)

A megvalósítás során két ponton tértünk el az eredeti tervezéstől, hogy a
projekt meglévő stílusához igazodjon. Ezek a változások **funkcionálisan
ekvivalensek** a doc-beli tervvel.

### 1. Dialog Java-ban épült, nem XML-ben

**Eredeti terv:** `res/layout/dialog_block_grid.xml` Android layout fájl.

**Megvalósult:** `ui/BlockGridDialog.java` osztály, amely programatikusan
építi az `AlertDialog`-ot LinearLayout + SeekBar + RadioGroup elemekből.

**Indok:** A `MissionPlannerActivity` többi dialog-ja (Save / Load / New Plan)
mind Java-ból építve készül, nincs külön dialog_*.xml a projektben. A
következetes stílus miatt ezt követtük.

### 2. Inline szövegek a strings.xml helyett

**Eredeti terv:** `res/values/strings.xml` bővítése M07 feliratokkal.

**Megvalósult:** A BlockGridDialog és a layout XML inline szövegeket
használ ("Blokk-felosztás", "Felosztás", "Kész", "Visszaállít" stb.).

**Indok:** A projekt jelenleg kizárólag magyar nyelvű, és a meglévő
Activity is inline szövegekkel dolgozik. Lokalizáció bevezetése esetén ez
egyszerre, projekt-szintű refactor lesz, nem M07-en belül.

### 3. ProjectManager API változás

A `buildJson` privát metódus aláírása egy új paraméterrel bővült:
`List<Block> blocks` (lehet null). Két új public overload készült:
- `saveProject(... List<Block> blocks)`
- `saveProjectToFile(... boolean syncPending, List<Block> blocks)`

A régi 7-paraméteres `saveProject` és 9-paraméteres `saveProjectToFile`
overload-ok megmaradtak, belül a blocks=null változatot hívják meg →
**visszafelé kompatibilis**, a meglévő hívók nem törnek.

### 4. dialog_block_grid.xml — opcionális későbbi átalakítás

Ha később felmerül a lokalizációs igény, a BlockGridDialog átalakítható
LayoutInflater-rel egy `dialog_block_grid.xml` fájlra. Ez kis refactor,
a Listener interface változatlan maradhat.

---

**Függőségek:**
- `mission/GridMissionGenerator.java` (M02) — fogyasztja a `block.missionPolygon`-t
- `mission/ProjectManager.java` (M03) — kibővített séma
- `dji/MissionUploader.java` (M04) — változatlan
- OSMDroid `Overlay`, `Polygon`, `GeoPoint`

---

## BlockGridGenerator — teljes API

```java
package com.dronefly.app.mission;

public class BlockGridGenerator {

    public static class GridResult {
        public List<Block> blocks = new ArrayList<>();
        public int    totalBlocks;
        public double aoiAreaM2;
        public double gridOriginLat;   // a rács fix origója (perzisztens!)
        public double gridOriginLon;
        public double gridRotationDeg; // a rács fix rotációja
        public String errorMessage;    // null = sikeres
    }

    /**
     * Fő belépési pont.
     * @param aoiPoints   az AOI poligon (≥ 3 GPS pont)
     * @param config      rács paraméterek
     * @param previous    null ha új rács; nem-null ha létező rács
     *                    újragenerálása (fix origó megtartás + állapot átvitel)
     */
    public GridResult generate(List<GeoPoint> aoiPoints,
                                BlockGridConfig config,
                                GridResult previous);

    // Belső metódusok:

    /** GPS → helyi XY (méter, az origó körül) */
    private double[][] toLocalXY(List<GeoPoint> pts,
                                  double originLat, double originLon);

    /** 2D rotáció (fokban) */
    private double[][] rotate(double[][] xy, double angleDeg);

    /** Cellaindex-tartomány a befoglaló téglalapból */
    private int[] cellBounds(double[][] aoiRotated,
                              double cellW, double cellH);

    /** Egyetlen cella build-elése (row, col → Block vagy null ha szűrve) */
    private Block buildCell(int row, int col,
                             double[][] aoiRotated,
                             BlockGridConfig cfg,
                             double originLat, double originLon);

    /** Shoelace területszámítás */
    private double polygonArea(double[][] xy);

    /** Visszaforgatás + helyi XY → GPS lista */
    private List<GeoPoint> toGeoPolygon(double[][] localRotated,
                                         double rotationDeg,
                                         double originLat, double originLon);

    /** Korábbi állapotok átvitele azonos (row, col) blokkokra */
    private void carryStatus(GridResult next, GridResult previous);
}
```

---

## PolygonClipper — teljes API

```java
package com.dronefly.app.mission;

/**
 * Sutherland-Hodgman polygon kivágás konvex clip-pel.
 * A subject lehet konkáv, a clip-nek konvexnek kell lennie.
 */
public class PolygonClipper {

    /**
     * @param subject  vágandó poligon (konkáv megengedett)
     * @param clip     vágó konvex poligon (CCW orientáció)
     * @return         a metszet poligon csúcsai, vagy üres lista ha nincs metszet
     */
    public static List<double[]> clip(List<double[]> subject,
                                       List<double[]> clip);

    /** Pont egy él bal-félsíkjában van? (CCW orientáció esetén = bent) */
    private static boolean isInside(double[] p,
                                     double[] edgeStart, double[] edgeEnd);

    /** Két szakasz metszéspontja (S→E vs. edgeStart→edgeEnd, paraméteres) */
    private static double[] intersect(double[] s, double[] e,
                                       double[] edgeStart, double[] edgeEnd);
}
```

---

## Block adatmodell

```java
package com.dronefly.app.model;

public class Block {
    public String id;                   // pl. "B-2-3"
    public int    row;
    public int    col;
    public List<GeoPoint> cellPolygon;      // 4-pont, GPS, rotált cella
    public List<GeoPoint> missionPolygon;   // (cell+buffer) ∩ AOI, GPS
    public GeoPoint cellCenter;             // súlypont, felirat helye
    public double cellAreaM2;               // teljes cella terület
    public double missionAreaM2;            // misszió poligon terület
    public double coverageRatio;            // missionAreaM2 / cellAreaM2
    public BlockStatus status;              // NOT_STARTED default

    public Block(int row, int col) {
        this.row = row;
        this.col = col;
        this.id  = "B-" + (row + 1) + "-" + (col + 1);
        this.status = BlockStatus.NOT_STARTED;
    }
}
```

```java
public enum BlockStatus {
    NOT_STARTED,
    IN_PROGRESS,
    DONE
}
```

---

## BlockGridConfig adatmodell

```java
package com.dronefly.app.model;

public class BlockGridConfig {
    public double cellWidthM = 120.0;
    public double cellHeightM = 120.0;
    public double rotationDeg = 0.0;          // 0–179.99
    public double overlapBufferM = 40.0;      // szomszéd átfedés
    public double minCoveragePercent = 15.0;  // 0–100, szűrési küszöb

    // Fix origó — mentés után stabil
    public String originMode = "centroid";    // "centroid" | "first_vertex" | "manual"
    public double originLat = Double.NaN;     // számított, NEM bemenet (kivéve manual)
    public double originLon = Double.NaN;

    public BlockGridConfig() {}

    /** Klón a dialog UI-hoz, hogy ne módosítsuk az élő konfigot */
    public BlockGridConfig copy() {
        BlockGridConfig c = new BlockGridConfig();
        c.cellWidthM = cellWidthM;
        c.cellHeightM = cellHeightM;
        c.rotationDeg = rotationDeg;
        c.overlapBufferM = overlapBufferM;
        c.minCoveragePercent = minCoveragePercent;
        c.originMode = originMode;
        c.originLat = originLat;
        c.originLon = originLon;
        return c;
    }
}
```

---

## BlockOverlay (OSMDroid)

```java
package com.dronefly.app.ui;

public class BlockOverlay extends Overlay {

    private List<Block> blocks;
    private Block selectedBlock;
    private MapView mapView;
    private Paint fillNotStarted, fillInProgress, fillDone;
    private Paint strokeNormal, strokeSelected;
    private Paint textPaint;

    public BlockOverlay(MapView mapView) {
        this.mapView = mapView;
        initPaints();
    }

    public void setBlocks(List<Block> blocks) {
        this.blocks = blocks;
        mapView.invalidate();
    }

    public void setSelected(Block b) {
        this.selectedBlock = b;
        mapView.invalidate();
    }

    /** Tap detekció — a felhasználói koppintás GeoPoint-ja → Block vagy null */
    public Block hitTest(GeoPoint tap,
                         double originLat, double originLon,
                         double rotationDeg,
                         double cellWidthM, double cellHeightM) {
        // 1. GPS → lokális XY
        // 2. rotálás -rotationDeg-gel
        // 3. col = floor(xR / cellW), row = floor(yR / cellH)
        // 4. blokkok közt keresés (row, col) szerint
        // → lásd L2 §5
    }

    @Override
    public void draw(Canvas c, MapView m, boolean shadow) {
        if (shadow || blocks == null) return;
        Projection proj = m.getProjection();
        Point pt = new Point();
        Path path = new Path();
        for (Block b : blocks) {
            // Kitöltés státusz szerint
            Paint fill = paintForStatus(b.status);
            // Path építése b.cellPolygon-ból
            // canvas.drawPath(path, fill)
            // canvas.drawPath(path, isSelected ? strokeSelected : strokeNormal)
            // Felirat középre: b.id
        }
    }

    private Paint paintForStatus(BlockStatus s) {
        switch (s) {
            case IN_PROGRESS: return fillInProgress;
            case DONE:        return fillDone;
            default:          return fillNotStarted;
        }
    }
}
```

**Szín konstansok:**

| Státusz | RGBA |
|---------|------|
| NOT_STARTED | `#80808080` (szürke, 50% áttetsző) |
| IN_PROGRESS | `#FFA50080` (narancs, 50% áttetsző) |
| DONE | `#00C85080` (zöld, 50% áttetsző) |
| Stroke normal | `#404040FF` 2 px |
| Stroke selected | `#FFFF00FF` 4 px (sárga, vastagabb) |
| Text | `#FFFFFFFF` fehér, fekete árnyékkal, 14sp |

---

## MissionPlannerActivity — bővítés

### Új mezők

```java
// M07 Blokk-felosztás
BlockGridConfig blockGridConfig = new BlockGridConfig();
BlockGridGenerator.GridResult currentGrid = null;
Block selectedBlock = null;
BlockOverlay blockOverlay;

Button btnSplit;        // "Felosztás" gomb
Button btnMarkDone;     // "B-2-3 kész" — csak ha selectedBlock != null
Button btnResetBlock;   // "Visszaállít" — DONE blokk újrarepüléséhez
TextView tvBlockInfo;   // "B-2-3 | 1.42 ha | 78% lefedettség"
```

### Új metódusok

```java
/** [Felosztás] gomb → dialog megnyitása */
private void onSplitClicked();

/** A dialog OK gombja → rács generálása és overlay frissítése */
private void applyBlockGrid(BlockGridConfig newConfig);

/** Térkép tap handler bővítés — blokk-tap detekció */
private boolean onMapTap(GeoPoint geo);

/** Új kiválasztott blokk beállítása */
private void setSelectedBlock(Block b);

/** [Generate] gomb bővítés — blokk vagy AOI alapú misszió */
private void generateMission();
//    if (selectedBlock != null) polygon = selectedBlock.missionPolygon
//    else polygon = polygonPoints  (a régi viselkedés)

/** Blokk-státusz átmenet a misszió-vezérlésben */
private void onMissionStarted();   // → selectedBlock.status = IN_PROGRESS
private void onMissionCompleted(); // → felhasználó döntése, [Kész] gomb

/** [Kész] gomb */
private void onMarkDoneClicked();  // → selectedBlock.status = DONE

/** [Visszaállít] gomb */
private void onResetBlockClicked(); // → selectedBlock.status = NOT_STARTED
```

### UI flow — Felosztás dialog

```
res/layout/dialog_block_grid.xml:
  ScrollView
    LinearLayout (vertical)
      TextView: "Cella méret"
      Slider: cellWidthM   (30..500, 10-es lépés)
      TextView: "120 m × 120 m"
      Slider: cellHeightM  (30..500, 10-es lépés)

      TextView: "Dőlésszög"
      Slider: rotationDeg  (0..179, 1-es lépés)
      TextView: "15°"

      TextView: "Átfedési puffer"
      Slider: overlapBufferM  (0..min(cellW,cellH)/2, 5-ös lépés)
      TextView: "40 m"

      TextView: "Min. lefedettség"
      Slider: minCoveragePercent  (0..50, 5-ös lépés)
      TextView: "15%"

      RadioGroup: originMode
        - "Középpont (ajánlott)"
        - "Első csúcs"

      [Mégse]  [Alkalmaz]

Élő előnézet:
  Minden slider-változáskor a térkép overlay frissül (debounced 200 ms),
  hogy a felhasználó látja a rácsot Apply nyomás előtt.
```

---

## Állapotgép — UI szempontból

```
                   ┌──────────────────┐
                   │ Polygon DRAW mód │
                   │ (M01 meglévő)    │
                   └────────┬─────────┘
                            │ [Felosztás] gomb
                            ▼
                   ┌──────────────────┐
                   │ Block grid dialog│
                   └────────┬─────────┘
                            │ [Alkalmaz]
                            ▼
                   ┌──────────────────┐
                   │ Grid overlay     │←──┐
                   │ (cellák látsz.)  │   │
                   └────────┬─────────┘   │ [Felosztás] újra
                            │ térkép tap  │ → új dialog
                            ▼             │
                   ┌──────────────────┐   │
                   │ Block kiválasztva│───┘
                   │ (sárga keret)    │
                   └────────┬─────────┘
                            │ [Generate]
                            ▼
                   ┌──────────────────┐
                   │ Misszió generálva│
                   │ a blokkra        │
                   └────────┬─────────┘
                            │ [Feltöltés + Start]
                            │ → block.status = IN_PROGRESS
                            ▼
                   ┌──────────────────┐
                   │ Repülés          │
                   └────────┬─────────┘
                            │ [Kész]
                            │ → block.status = DONE
                            ▼
                   ┌──────────────────┐
                   │ Visszatérés a    │
                   │ Grid overlay-re  │
                   │ (más blokk vál.) │
                   └──────────────────┘
```

---

## Teljesítmény jellemzők (Crystal Sky, ARM Cortex-A53)

| Cellaszám | SH kivágás / cella | Teljes generálás |
|-----------|-------------------|------------------|
| 4 (kis terület) | < 1 ms | < 5 ms |
| 16 (közepes) | < 1 ms | < 20 ms |
| 64 (nagy) | < 2 ms | < 150 ms |
| 256 (extrém) | < 2 ms | < 600 ms |

**Memória per Block:**
- `cellPolygon` 4 pont × 16 byte = 64 B
- `missionPolygon` átlag ~8 pont × 16 byte = 128 B
- Egyéb mezők: ~80 B
- **Összesen: ~270 B / Block**

64 blokkos rács → ~17 KB. Crystal Sky-n elhanyagolható.

---

## Mértékegységek és koordináta-konvenciók

- Minden szög **fokban** (nem radián) — a felhasználó és a perzisztens
  séma is fokban dolgozik.
- `rotationDeg` 0–179.99 tartomány — 180° azonos 0°-kal, ezért kizárva.
- Minden hossz **méterben** (m), kivéve a `minCoveragePercent` (%-ban).
- Koordináták: WGS84 lat/lon (osmdroid `GeoPoint`).
- A "rotált XY" rendszer kizárólag belső használatra, a perzisztens
  fájlban nem jelenik meg — csak GPS koordináták mentődnek.
