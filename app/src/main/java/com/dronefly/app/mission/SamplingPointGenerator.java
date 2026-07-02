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

    /**
     * Mintapontok generálása polygonon belül.
     *
     * @param polygon  legalább 3 GPS pont
     * @param nPoints  kívánt mintapontszám (>0)
     * @param method   "stratified" | "halton" | "random"
     * @param seed     reprodukálhatóság ("random" és "stratified" jitterhez)
     * @return legfeljebb nPoints db GeoPoint, mind a polygonon belül
     */
    public static List<GeoPoint> generate(List<GeoPoint> polygon, int nPoints,
                                          String method, long seed) {
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

        if ("halton".equals(method)) {
            int idx = 1;
            int attempts = 0;
            int maxAttempts = nPoints * 200;
            while (pointsXY.size() < nPoints && attempts < maxAttempts) {
                double x = xMin + (xMax - xMin) * halton(idx, 2);
                double y = yMin + (yMax - yMin) * halton(idx, 3);
                if (pointInPolygon(px, py, x, y)) {
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
                if (pointInPolygon(px, py, x, y)) {
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
                    if (pointInPolygon(px, py, cx, cy)) {
                        pointsXY.add(new double[]{cx, cy});
                    }
                }
            }
        }

        for (double[] xy : pointsXY) {
            double lat = centLat + xy[1] / mPerDegLat;
            double lon = centLon + xy[0] / mPerDegLon;
            result.add(new GeoPoint(lat, lon));
            if (result.size() >= nPoints) break;
        }
        return result;
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
