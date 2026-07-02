package com.dronefly.app.mission;

import com.dronefly.app.model.Block;
import com.dronefly.app.model.BlockGridConfig;
import com.dronefly.app.model.BlockStatus;

import org.osmdroid.util.GeoPoint;

import java.util.ArrayList;
import java.util.List;

/**
 * AOI poligon → blokk-rács generálása (M07).
 *
 * Algoritmus:
 *  1. Origó meghatározása (centroid / first_vertex / manual) az `originMode` szerint
 *  2. AOI csúcsok GPS → lokális XY (m, origó körül)
 *  3. AOI elforgatása -rotationDeg-gel → tengely-igazított rendszer
 *  4. Bounding box → cellatartomány (col_min..col_max, row_min..row_max)
 *  5. Minden (row, col) cellára:
 *      a. Cella téglalap + overlapBuffer pufferes téglalap a rotált rendszerben
 *      b. (cella + puffer) ∩ AOI Sutherland-Hodgman-nel
 *      c. Cella ∩ AOI a coverageRatio-hoz (puffer nélkül)
 *      d. Ha coverageRatio < minCoveragePercent → cella eldobva
 *      e. Visszaforgatás + lokális XY → GPS
 *  6. Ha previous != null → status átvitele (row, col) szerint
 *
 * Lásd: docs/M07_BLOKK_FELOSZTAS/M07_L3_ALLAPOTGEP_ES_ENGINE.md
 */
public class BlockGridGenerator {

    /** Föld átlag-sugár alapú "egyszerűsített" konverzió, mint a GridMissionGenerator-ban. */
    private static final double M_PER_DEG_LAT = 111000.0;

    public static class GridResult {
        public List<Block> blocks = new ArrayList<>();
        public int    totalBlocks;
        public double aoiAreaM2;
        public double gridOriginLat;
        public double gridOriginLon;
        public double gridRotationDeg;
        public String errorMessage; // null = sikeres
    }

    /**
     * Fő belépési pont.
     *
     * @param aoiPoints az AOI poligon (≥ 3 GPS pont)
     * @param config    rács paraméterek (módosulhat: ha originLat/Lon NaN, kitöltődik)
     * @param previous  null ha új rács; nem-null ha létező rács újragenerálása
     *                  (fix origó/rotation megtartás + állapot átvitel)
     */
    public GridResult generate(List<GeoPoint> aoiPoints,
                                BlockGridConfig config,
                                GridResult previous) {
        GridResult res = new GridResult();

        if (aoiPoints == null || aoiPoints.size() < 3) {
            res.errorMessage = "Legalább 3 pontból álló AOI poligon szükséges";
            return res;
        }
        if (config == null) {
            res.errorMessage = "Hiányzó BlockGridConfig";
            return res;
        }
        if (config.cellWidthM <= 0 || config.cellHeightM <= 0) {
            res.errorMessage = "A cella méretnek pozitívnak kell lennie";
            return res;
        }
        if (config.overlapBufferM < 0) {
            res.errorMessage = "Az átfedési puffer nem lehet negatív";
            return res;
        }

        // 1. Origó meghatározása (vagy átvétel a previous-tól)
        double originLat;
        double originLon;
        double rotationDeg = config.rotationDeg;

        if (previous != null
                && !Double.isNaN(previous.gridOriginLat)
                && !Double.isNaN(previous.gridOriginLon)) {
            originLat = previous.gridOriginLat;
            originLon = previous.gridOriginLon;
        } else if (BlockGridConfig.ORIGIN_MANUAL.equals(config.originMode)
                && !Double.isNaN(config.originLat)
                && !Double.isNaN(config.originLon)) {
            originLat = config.originLat;
            originLon = config.originLon;
        } else if (BlockGridConfig.ORIGIN_FIRST_VERTEX.equals(config.originMode)) {
            originLat = aoiPoints.get(0).getLatitude();
            originLon = aoiPoints.get(0).getLongitude();
        } else { // centroid (alapért.)
            double sumLat = 0, sumLon = 0;
            for (GeoPoint p : aoiPoints) {
                sumLat += p.getLatitude();
                sumLon += p.getLongitude();
            }
            originLat = sumLat / aoiPoints.size();
            originLon = sumLon / aoiPoints.size();
        }

        // Visszaírjuk a configba is, hogy a perzisztenciánál mentődjön
        config.originLat = originLat;
        config.originLon = originLon;

        res.gridOriginLat   = originLat;
        res.gridOriginLon   = originLon;
        res.gridRotationDeg = rotationDeg;

        final double mPerDegLon = M_PER_DEG_LAT * Math.cos(Math.toRadians(originLat));

        // 2. GPS → lokális XY (origó körül)
        double[][] aoiLocal = toLocalXY(aoiPoints, originLat, originLon, mPerDegLon);

        // 3. AOI elforgatása -rotationDeg-gel (tengely-igazítás)
        double[][] aoiRotated = rotate(aoiLocal, -rotationDeg);

        res.aoiAreaM2 = polygonArea(aoiRotated);

        // 4. Bounding box → cellatartomány
        int[] bounds = cellBounds(aoiRotated, config.cellWidthM, config.cellHeightM);
        int rowMin = bounds[0], rowMax = bounds[1];
        int colMin = bounds[2], colMax = bounds[3];

        // 5. Cellák építése
        // A row/col 0-tól indexelődik, de a bbox lehet negatív tartományban is —
        // ezért normalizáljuk: az első érvényes cella legyen (0, 0) a felhasználónak.
        for (int row = rowMin; row <= rowMax; row++) {
            for (int col = colMin; col <= colMax; col++) {
                Block b = buildCell(row - rowMin, col - colMin,
                                    row, col,
                                    aoiRotated, aoiLocal, config,
                                    rotationDeg, originLat, originLon, mPerDegLon);
                if (b != null) res.blocks.add(b);
            }
        }

        res.totalBlocks = res.blocks.size();
        if (res.totalBlocks == 0) {
            res.errorMessage = "Nincs egyetlen érvényes blokk sem — "
                + "ellenőrizd a cella-méretet és a min. lefedettséget";
        }

        // 6. Status átvitele
        if (previous != null) carryStatus(res, previous);

        return res;
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Geometria
    // ─────────────────────────────────────────────────────────────────────

    private double[][] toLocalXY(List<GeoPoint> pts,
                                  double originLat, double originLon,
                                  double mPerDegLon) {
        double[][] xy = new double[pts.size()][2];
        for (int i = 0; i < pts.size(); i++) {
            GeoPoint p = pts.get(i);
            xy[i][0] = (p.getLongitude() - originLon) * mPerDegLon;
            xy[i][1] = (p.getLatitude()  - originLat) * M_PER_DEG_LAT;
        }
        return xy;
    }

    private double[][] rotate(double[][] xy, double angleDeg) {
        double angle = Math.toRadians(angleDeg);
        double c = Math.cos(angle), s = Math.sin(angle);
        double[][] out = new double[xy.length][2];
        for (int i = 0; i < xy.length; i++) {
            out[i][0] = xy[i][0] * c - xy[i][1] * s;
            out[i][1] = xy[i][0] * s + xy[i][1] * c;
        }
        return out;
    }

    /** Befoglaló rács cellaindexei (zárt intervallumok). */
    private int[] cellBounds(double[][] aoiRotated, double cellW, double cellH) {
        double xMin = aoiRotated[0][0], xMax = xMin;
        double yMin = aoiRotated[0][1], yMax = yMin;
        for (int i = 1; i < aoiRotated.length; i++) {
            if (aoiRotated[i][0] < xMin) xMin = aoiRotated[i][0];
            if (aoiRotated[i][0] > xMax) xMax = aoiRotated[i][0];
            if (aoiRotated[i][1] < yMin) yMin = aoiRotated[i][1];
            if (aoiRotated[i][1] > yMax) yMax = aoiRotated[i][1];
        }
        int colMin = (int) Math.floor(xMin / cellW);
        int colMax = (int) Math.floor(xMax / cellW);
        int rowMin = (int) Math.floor(yMin / cellH);
        int rowMax = (int) Math.floor(yMax / cellH);
        return new int[]{rowMin, rowMax, colMin, colMax};
    }

    /**
     * Egyetlen cella build-elése.
     *
     * @param userRow,userCol  a felhasználói (normalizált, 0-tól induló) sor/oszlop
     * @param gridRow,gridCol  a belső rács sor/oszlop (lehet negatív is)
     * @param aoiRotated       AOI poligon a rotált lokális rendszerben
     * @param aoiLocal         AOI poligon a rotálatlan lokális rendszerben (nem használt itt)
     * @return Block vagy null ha a coverageRatio túl kicsi
     */
    private Block buildCell(int userRow, int userCol,
                             int gridRow, int gridCol,
                             double[][] aoiRotated,
                             double[][] aoiLocal,
                             BlockGridConfig cfg,
                             double rotationDeg,
                             double originLat, double originLon,
                             double mPerDegLon) {

        double xL = gridCol * cfg.cellWidthM;
        double xR = xL + cfg.cellWidthM;
        double yB = gridRow * cfg.cellHeightM;
        double yT = yB + cfg.cellHeightM;

        // Cella téglalap (rotált rendszerben, CCW: BL → BR → TR → TL)
        List<double[]> cellRect = new ArrayList<>(4);
        cellRect.add(new double[]{xL, yB});
        cellRect.add(new double[]{xR, yB});
        cellRect.add(new double[]{xR, yT});
        cellRect.add(new double[]{xL, yT});

        // Puffer téglalap = cella minden oldalon kifelé overlapBufferM-mel
        double buf = cfg.overlapBufferM;
        List<double[]> bufferRect = new ArrayList<>(4);
        bufferRect.add(new double[]{xL - buf, yB - buf});
        bufferRect.add(new double[]{xR + buf, yB - buf});
        bufferRect.add(new double[]{xR + buf, yT + buf});
        bufferRect.add(new double[]{xL - buf, yT + buf});

        // AOI poligon mint List<double[]>
        List<double[]> aoiList = new ArrayList<>(aoiRotated.length);
        for (double[] p : aoiRotated) aoiList.add(new double[]{p[0], p[1]});

        // Misszió poligon = puffer ∩ AOI (puffer a clip, AOI a subject — DE
        // a SH algoritmus megköveteli, hogy a clip konvex legyen → bufferRect az,
        // és a subject (AOI) lehet konkáv → ez a helyes irány)
        List<double[]> missionRotated = PolygonClipper.clip(aoiList, bufferRect);

        if (missionRotated.size() < 3) return null; // nincs metszet

        // Cella ∩ AOI a coverageRatio-hoz (puffer nélkül)
        List<double[]> cellOnlyRotated = PolygonClipper.clip(aoiList, cellRect);
        double cellInAoiArea = polygonArea(cellOnlyRotated);
        double cellAreaFull  = cfg.cellWidthM * cfg.cellHeightM;
        double coverage      = cellInAoiArea / cellAreaFull;

        if (coverage * 100.0 < cfg.minCoveragePercent) return null;

        // ─── Itt már biztosan érvényes blokk ─────────────────────────────
        Block b = new Block(userRow, userCol);

        // Visszaforgatás GPS-be:
        //   1. lokális rotált → lokális XY (forgatás +rotationDeg-gel)
        //   2. lokális XY → GPS

        // Cella poligon (rotált rendszerben sarkai már megvannak, csak vissza kell forgatni)
        b.cellPolygon = toGeoPolygon(cellRect, rotationDeg,
                                      originLat, originLon, mPerDegLon);
        b.missionPolygon = toGeoPolygon(missionRotated, rotationDeg,
                                         originLat, originLon, mPerDegLon);

        // Cella középpont (rotált rendszerben (xL+xR)/2, (yB+yT)/2)
        double cxR = (xL + xR) / 2.0;
        double cyR = (yB + yT) / 2.0;
        double[][] centerArr = rotate(new double[][]{{cxR, cyR}}, rotationDeg);
        b.cellCenter = new GeoPoint(
                originLat + centerArr[0][1] / M_PER_DEG_LAT,
                originLon + centerArr[0][0] / mPerDegLon);

        b.cellAreaM2    = cellAreaFull;
        b.missionAreaM2 = polygonArea(missionRotated);
        b.coverageRatio = coverage;
        b.status        = BlockStatus.NOT_STARTED;

        return b;
    }

    /** Shoelace képlet — sokszög területe, abszolút érték. */
    private double polygonArea(List<double[]> poly) {
        if (poly == null || poly.size() < 3) return 0;
        double area = 0;
        int n = poly.size();
        for (int i = 0; i < n; i++) {
            double[] a = poly.get(i);
            double[] b = poly.get((i + 1) % n);
            area += a[0] * b[1] - b[0] * a[1];
        }
        return Math.abs(area) / 2.0;
    }

    private double polygonArea(double[][] poly) {
        if (poly == null || poly.length < 3) return 0;
        double area = 0;
        int n = poly.length;
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            area += poly[i][0] * poly[j][1] - poly[j][0] * poly[i][1];
        }
        return Math.abs(area) / 2.0;
    }

    /** Rotált lokális XY → GPS (visszaforgatás +angleDeg-gel, majd origó-eltolás). */
    private List<GeoPoint> toGeoPolygon(List<double[]> localRotated,
                                         double rotationDeg,
                                         double originLat, double originLon,
                                         double mPerDegLon) {
        double angle = Math.toRadians(rotationDeg);
        double c = Math.cos(angle), s = Math.sin(angle);
        List<GeoPoint> out = new ArrayList<>(localRotated.size());
        for (double[] p : localRotated) {
            double x = p[0] * c - p[1] * s;
            double y = p[0] * s + p[1] * c;
            out.add(new GeoPoint(
                    originLat + y / M_PER_DEG_LAT,
                    originLon + x / mPerDegLon));
        }
        return out;
    }

    /** A previous-ban tárolt blokk-státuszokat átviszi a next-be (row, col) szerint. */
    private void carryStatus(GridResult next, GridResult previous) {
        if (previous == null || previous.blocks == null) return;
        for (Block nb : next.blocks) {
            for (Block pb : previous.blocks) {
                if (pb.row == nb.row && pb.col == nb.col) {
                    nb.status = pb.status;
                    break;
                }
            }
        }
    }
}
