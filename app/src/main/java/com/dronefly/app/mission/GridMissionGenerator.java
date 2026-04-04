package com.dronefly.app.mission;

import com.dronefly.app.model.MissionConfig;
import com.dronefly.app.model.WaypointData;

import org.osmdroid.util.GeoPoint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Kaszáló (lawnmower) repülési terv generálása egy tetszőleges sokszög felett.
 *
 * Algoritmus:
 * 1. A sokszöget helyi XY koordinátákba konvertáljuk (méter, centroid körül)
 * 2. A flightAngleDeg szöggel elforgatjuk (0° = K-Ny sávok, É-D haladás)
 * 3. Vízszintes scan-vonalakat generálunk a sávköz szerint
 * 4. Minden scan-vonalat metsszük a sokszöggel
 * 5. Kaszáló sorrendbe rendezzük a waypointokat
 * 6. Visszaforgatjuk, majd visszakonvertáljuk GPS koordinátákba
 * 7. Ha >99 waypoint, automatikusan misszió szegmensekre bontjuk
 */
public class GridMissionGenerator {

    private static final int MAX_WAYPOINTS_PER_MISSION = 99;

    public static class GeneratorResult {
        public List<List<WaypointData>> segments = new ArrayList<>();
        public int totalWaypoints;
        public double areaM2;
        public double estimatedMinutes;
        public double altitudeM;
        public double stripSpacingM;
        public double photoDistM;
        public String errorMessage;
    }

    public static GeneratorResult generate(List<GeoPoint> polygon, MissionConfig config) {
        GeneratorResult result = new GeneratorResult();

        if (polygon == null || polygon.size() < 3) {
            result.errorMessage = "Legalább 3 pont szükséges a területhez";
            return result;
        }

        // Magasság számítása GSD-ből (drón profil alapján, ha elérhető)
        double altM = (config.droneProfile != null)
            ? GsdCalculator.altitudeFromGsd(config.gsdCm, config.droneProfile)
            : GsdCalculator.altitudeFromGsd(config.gsdCm);
        altM = Math.max(10, Math.min(300, altM)); // 10–300m korlát
        result.altitudeM = altM;

        double stripSpacing = (config.droneProfile != null)
            ? GsdCalculator.stripSpacingM(altM, config.sidelapPercent, config.droneProfile)
            : GsdCalculator.stripSpacingM(altM, config.sidelapPercent);
        double photoDist = (config.droneProfile != null)
            ? GsdCalculator.photoDistanceM(altM, config.frontlapPercent, config.droneProfile)
            : GsdCalculator.photoDistanceM(altM, config.frontlapPercent);
        result.stripSpacingM = stripSpacing;
        result.photoDistM    = photoDist;

        // Centroid számítás
        double centLat = 0, centLon = 0;
        for (GeoPoint p : polygon) { centLat += p.getLatitude(); centLon += p.getLongitude(); }
        centLat /= polygon.size();
        centLon /= polygon.size();

        // Méter/fok konverziós faktorok a centroid szélességénél
        final double mPerDegLat = 111000.0;
        final double mPerDegLon = 111000.0 * Math.cos(Math.toRadians(centLat));

        // GPS → lokális XY (m)
        double[] px = new double[polygon.size()];
        double[] py = new double[polygon.size()];
        for (int i = 0; i < polygon.size(); i++) {
            px[i] = (polygon.get(i).getLongitude() - centLon) * mPerDegLon;
            py[i] = (polygon.get(i).getLatitude()  - centLat) * mPerDegLat;
        }

        // Elforgatás -angle irányba (hogy a sávok vízszintesek legyenek)
        double angle = Math.toRadians(config.flightAngleDeg);
        double cosA =  Math.cos(-angle);
        double sinA =  Math.sin(-angle);
        double[] rx = new double[px.length];
        double[] ry = new double[py.length];
        for (int i = 0; i < px.length; i++) {
            rx[i] = px[i] * cosA - py[i] * sinA;
            ry[i] = px[i] * sinA + py[i] * cosA;
        }

        // Bounding box a forgatott koordinátákban
        double xMin = rx[0], xMax = rx[0], yMin = ry[0], yMax = ry[0];
        for (int i = 1; i < rx.length; i++) {
            if (rx[i] < xMin) xMin = rx[i];
            if (rx[i] > xMax) xMax = rx[i];
            if (ry[i] < yMin) yMin = ry[i];
            if (ry[i] > yMax) yMax = ry[i];
        }

        // Terület becslése (bounding box közelítés)
        result.areaM2 = polygonAreaM2(rx, ry);

        // Scan vonalak generálása (Y irány = sávköz)
        double yStart = yMin + stripSpacing / 2.0;
        List<WaypointData> allWaypoints = new ArrayList<>();

        int stripIndex = 0;
        for (double scanY = yStart; scanY <= yMax + stripSpacing / 2.0; scanY += stripSpacing) {
            // Metszéspontok a sokszög éleivel
            List<Double> intersections = computeIntersections(rx, ry, scanY);
            if (intersections.size() < 2) { stripIndex++; continue; }
            Collections.sort(intersections);

            // Sávonként párban vesszük a metszéspontokat
            for (int k = 0; k + 1 < intersections.size(); k += 2) {
                double xEnter = intersections.get(k);
                double xExit  = intersections.get(k + 1);

                // Kaszáló: páros sávban balról jobbra, páratlanban jobbra balra
                if (stripIndex % 2 != 0) {
                    double tmp = xEnter; xEnter = xExit; xExit = tmp;
                }

                // Fotópontok generálása a sáv mentén
                double dx = xExit - xEnter;
                double len = Math.abs(dx);
                int photoCount = Math.max(1, (int) Math.ceil(len / photoDist));
                double step = dx / photoCount;

                for (int n = 0; n <= photoCount; n++) {
                    double wx = xEnter + n * step;
                    double wy = scanY;

                    // Visszaforgatás
                    double cosB = Math.cos(angle);
                    double sinB = Math.sin(angle);
                    double localX = wx * cosB - wy * sinB;
                    double localY = wx * sinB + wy * cosB;

                    // Lokális XY → GPS
                    double lat = centLat + localY / mPerDegLat;
                    double lon = centLon + localX / mPerDegLon;

                    boolean shoot = true; // minden pont fotóz
                    allWaypoints.add(new WaypointData(lat, lon, (float) altM, shoot));
                }
            }
            stripIndex++;
        }

        if (allWaypoints.isEmpty()) {
            result.errorMessage = "Nem sikerült waypointokat generálni. Ellenőrizd a területet és a beállításokat.";
            return result;
        }

        result.totalWaypoints = allWaypoints.size();
        result.estimatedMinutes = GsdCalculator.estimatedFlightMinutes(
                result.areaM2, altM, config.sidelapPercent, config.speedMs, config.droneProfile);

        // Szegmensekre bontás ha >99 waypoint
        for (int start = 0; start < allWaypoints.size(); start += MAX_WAYPOINTS_PER_MISSION) {
            int end = Math.min(start + MAX_WAYPOINTS_PER_MISSION, allWaypoints.size());
            result.segments.add(new ArrayList<>(allWaypoints.subList(start, end)));
        }

        return result;
    }

    /**
     * Megkeresi, hol metszi a scanY vízszintes vonal a sokszög éleit.
     */
    private static List<Double> computeIntersections(double[] rx, double[] ry, double scanY) {
        List<Double> xs = new ArrayList<>();
        int n = rx.length;
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            double y1 = ry[i], y2 = ry[j];
            double x1 = rx[i], x2 = rx[j];
            if ((y1 <= scanY && scanY < y2) || (y2 <= scanY && scanY < y1)) {
                double t = (scanY - y1) / (y2 - y1);
                xs.add(x1 + t * (x2 - x1));
            }
        }
        return xs;
    }

    /** Sokszög területe négyzetméterben (Gauss-képlet) */
    private static double polygonAreaM2(double[] x, double[] y) {
        double area = 0;
        int n = x.length;
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            area += x[i] * y[j];
            area -= x[j] * y[i];
        }
        return Math.abs(area) / 2.0;
    }
}
