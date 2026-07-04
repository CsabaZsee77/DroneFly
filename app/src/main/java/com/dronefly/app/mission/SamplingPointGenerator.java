package com.dronefly.app.mission;

import org.osmdroid.util.GeoPoint;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Mintavételi pontok generálása parcella polygonra.
 *
 * A Dronterapia projekt Python-oldali utils/sampling_plan.py logikájának Java
 * portja — offline működik, nincs függőség a Dronterapia-szinkronra a misszió
 * generálásához, csak a már megrajzolt/importált polygon geometria kell.
 *
 * Algoritmusok:
 *   - "stratified": rács (ceil(sqrt(n)) x ceil(sqrt(n))) + jitter minden cellán belül
 *   - "halton": kvázi-véletlen Halton-szekvencia (bázis 2, 3)
 *   - "random": egyenletes véletlen
 *
 * v1 hatókör: nincs előzetes heterogenitás-alapú rétegzés (NDVI-zóna stb.) —
 * mindhárom módszer egyenletesen mintavételezi a teljes polygont.
 *
 * Megjegyzés: a GridMissionGenerator saját, private GPS<->helyi-XY transzformációt
 * és Shoelace terület-számítást használ scanline-alapú algoritmushoz. Ez az
 * osztály egy önálló, kis méretű koordináta-transzformációt és egy point-in-polygon
 * (ray casting) tesztet tartalmaz — ez utóbbi a Grid Engine-ben nem létezik
 * (más algoritmust használ), ezért nem indokolt a két generátor közös
 * geometria-kódra való refaktorálása egy ilyen kis, önálló darabnál.
 */
public class SamplingPointGenerator {

    /** Halton-szekvencia adott indexre és bázisra (0..1 között). */
    private static double halton(int index, int base) {
        double f = 1.0;
        double r = 0.0;
        int i = index;
        while (i > 0) {
            f /= base;
            r += f * (i % base);
            i /= base;
        }
        return r;
    }

    /** Visszafelé kompatibilis változat — nincs min-távolság korlát (minDistM = 0). */
    public static List<GeoPoint> generate(List<GeoPoint> polygon, int nPoints,
                                          String method, long seed) {
        return generate(polygon, nPoints, method, seed, 0.0);
    }

    /**
     * Mintapontok generálása polygonon belül (M02_L1 §9 — backfill +
     * átfedés-mentesség + útvonal-rendezés, 2026-07-04).
     *
     * @param polygon  legalább 3 GPS pont
     * @param nPoints  kívánt mintapontszám (>0)
     * @param method   "stratified" | "halton" | "random"
     * @param seed     reprodukálhatóság ("random" és "stratified" jitterhez)
     * @param minDistM minimális távolság két pont közt méterben (átfedés-mentesség,
     *                 pl. a footprint szélessége); 0 = nincs korlát
     * @return legfeljebb nPoints db GeoPoint, bejárási sorrendbe rendezve
     *         (legközelebbi-szomszéd); ha a terület a min-távolság mellett
     *         kevesebbre elég, annyit ad — a hívó összevetheti nPoints-szal
     */
    public static List<GeoPoint> generate(List<GeoPoint> polygon, int nPoints,
                                          String method, long seed, double minDistM) {
        List<GeoPoint> result = new ArrayList<>();
        if (polygon == null || polygon.size() < 3 || nPoints <= 0) {
            return result;
        }

        // GPS -> helyi XY méter (centroid körüli), ugyanaz a konvenció, mint a
        // GridMissionGenerator-ben (mPerDeg konstansok), hogy a két generátor
        // konzisztens területet/távolságot számoljon.
        double centLat = 0, centLon = 0;
        for (GeoPoint p : polygon) { centLat += p.getLatitude(); centLon += p.getLongitude(); }
        centLat /= polygon.size();
        centLon /= polygon.size();

        final double mPerDegLat = 111000.0;
        final double mPerDegLon = 111000.0 * Math.cos(Math.toRadians(centLat));

        int n = polygon.size();
        double[] px = new double[n];
        double[] py = new double[n];
        for (int i = 0; i < n; i++) {
            px[i] = (polygon.get(i).getLongitude() - centLon) * mPerDegLon;
            py[i] = (polygon.get(i).getLatitude() - centLat) * mPerDegLat;
        }

        double xMin = px[0], xMax = px[0], yMin = py[0], yMax = py[0];
        for (int i = 1; i < n; i++) {
            if (px[i] < xMin) xMin = px[i];
            if (px[i] > xMax) xMax = px[i];
            if (py[i] < yMin) yMin = py[i];
            if (py[i] > yMax) yMax = py[i];
        }

        Random rng = new Random(seed);
        List<double[]> pointsXY = new ArrayList<>();
        double minDist2 = minDistM > 0 ? minDistM * minDistM : 0;

        if ("halton".equals(method)) {
            int idx = 1;
            int attempts = 0;
            int maxAttempts = nPoints * 200;
            while (pointsXY.size() < nPoints && attempts < maxAttempts) {
                double x = xMin + (xMax - xMin) * halton(idx, 2);
                double y = yMin + (yMax - yMin) * halton(idx, 3);
                if (pointInPolygon(px, py, x, y) && farEnough(pointsXY, x, y, minDist2)) {
                    pointsXY.add(new double[]{x, y});
                }
                idx++;
                attempts++;
            }
        } else if ("random".equals(method)) {
            int attempts = 0;
            int maxAttempts = nPoints * 200;
            while (pointsXY.size() < nPoints && attempts < maxAttempts) {
                double x = xMin + rng.nextDouble() * (xMax - xMin);
                double y = yMin + rng.nextDouble() * (yMax - yMin);
                if (pointInPolygon(px, py, x, y) && farEnough(pointsXY, x, y, minDist2)) {
                    pointsXY.add(new double[]{x, y});
                }
                attempts++;
            }
        } else {
            // "stratified" (alapértelmezett) — rács + jitter
            int side = Math.max(1, (int) Math.ceil(Math.sqrt(nPoints)));
            double dx = (xMax - xMin) / side;
            double dy = (yMax - yMin) / side;
            outer:
            for (int i = 0; i < side; i++) {
                for (int j = 0; j < side; j++) {
                    if (pointsXY.size() >= nPoints) break outer;
                    double cx = xMin + dx * (i + 0.5 + (rng.nextDouble() - 0.5) * 0.6);
                    double cy = yMin + dy * (j + 0.5 + (rng.nextDouble() - 0.5) * 0.6);
                    if (pointInPolygon(px, py, cx, cy) && farEnough(pointsXY, cx, cy, minDist2)) {
                        pointsXY.add(new double[]{cx, cy});
                    }
                }
            }
        }

        // ── Backfill a kért pontszámig (M02_L1 §9.2) ────────────────────────
        // A rács/Halton a polygon szabálytalansága miatt kevesebb pontot adhat,
        // mint amennyit kértek — elutasításos mintavétellel töltjük fel,
        // ugyanazzal a min-távolság feltétellel (átfedés-mentesség).
        int backfillAttempts = 0;
        int maxBackfill = nPoints * 500;
        while (pointsXY.size() < nPoints && backfillAttempts < maxBackfill) {
            double x = xMin + rng.nextDouble() * (xMax - xMin);
            double y = yMin + rng.nextDouble() * (yMax - yMin);
            if (pointInPolygon(px, py, x, y) && farEnough(pointsXY, x, y, minDist2)) {
                pointsXY.add(new double[]{x, y});
            }
            backfillAttempts++;
        }

        // ── Bejárási útvonal rendezése (M02_L1 §9.2, 3. lépés) ──────────────
        // Legközelebbi-szomszéd a bal-alsó saroktól — hogy a drón ne ugráljon
        // összevissza a rács + backfill pontok közt.
        List<double[]> ordered = orderNearestNeighbor(pointsXY);

        for (double[] xy : ordered) {
            double lat = centLat + xy[1] / mPerDegLat;
            double lon = centLon + xy[0] / mPerDegLon;
            result.add(new GeoPoint(lat, lon));
            if (result.size() >= nPoints) break;
        }
        return result;
    }

    /** Igaz, ha (x,y) minden meglévő ponttól legalább sqrt(minDist2) távol van. */
    private static boolean farEnough(List<double[]> pts, double x, double y, double minDist2) {
        if (minDist2 <= 0) return true;
        for (double[] p : pts) {
            double dx = p[0] - x, dy = p[1] - y;
            if (dx * dx + dy * dy < minDist2) return false;
        }
        return true;
    }

    /**
     * Legközelebbi-szomszéd útvonal-rendezés (nem teljes TSP — O(n²), n ≤ ~60).
     * Kiindulás: a bal-alsó sarokhoz (min x+y) legközelebbi pont.
     */
    private static List<double[]> orderNearestNeighbor(List<double[]> pts) {
        List<double[]> ordered = new ArrayList<>();
        if (pts.isEmpty()) return ordered;
        boolean[] used = new boolean[pts.size()];

        int start = 0;
        double best = Double.MAX_VALUE;
        for (int i = 0; i < pts.size(); i++) {
            double s = pts.get(i)[0] + pts.get(i)[1];
            if (s < best) { best = s; start = i; }
        }
        used[start] = true;
        ordered.add(pts.get(start));
        double curX = pts.get(start)[0], curY = pts.get(start)[1];

        for (int k = 1; k < pts.size(); k++) {
            int next = -1;
            double bestD = Double.MAX_VALUE;
            for (int i = 0; i < pts.size(); i++) {
                if (used[i]) continue;
                double dx = pts.get(i)[0] - curX, dy = pts.get(i)[1] - curY;
                double d = dx * dx + dy * dy;
                if (d < bestD) { bestD = d; next = i; }
            }
            if (next < 0) break;
            used[next] = true;
            ordered.add(pts.get(next));
            curX = pts.get(next)[0]; curY = pts.get(next)[1];
        }
        return ordered;
    }

    /**
     * Ajánlott mintapontszám statisztikai megfontolás alapján.
     * Azonos képlet, mint a Dronterapia recommended_n_points(area_hectares, cv_estimate=0.3):
     *   n = ceil((1.96 * cv / 0.1)^2)
     *   n_area = clamp(area_ha * 3, 15, 60)
     *   return max(n, n_area)
     */
    public static int recommendedNPoints(double areaHectares, double cvEstimate) {
        if (areaHectares <= 0) return 20;
        double z = 1.96;
        double margin = 0.1;
        int nStat = (int) Math.ceil(Math.pow(z * cvEstimate / margin, 2));
        int nArea = (int) Math.max(15, Math.min(60, areaHectares * 3));
        return Math.max(nStat, nArea);
    }

    public static int recommendedNPoints(double areaHectares) {
        return recommendedNPoints(areaHectares, 0.3);
    }

    /**
     * Point-in-polygon teszt ray-casting algoritmussal (helyi XY koordinátákban).
     *
     * @param px, py polygon csúcspontjai (helyi méter)
     * @param x, y   vizsgált pont
     */
    private static boolean pointInPolygon(double[] px, double[] py, double x, double y) {
        boolean inside = false;
        int n = px.length;
        for (int i = 0, j = n - 1; i < n; j = i++) {
            boolean intersects = ((py[i] > y) != (py[j] > y))
                    && (x < (px[j] - px[i]) * (y - py[i]) / (py[j] - py[i]) + px[i]);
            if (intersects) inside = !inside;
        }
        return inside;
    }
}
