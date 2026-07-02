package com.dronefly.app.mission;

import com.dronefly.app.model.MissionConfig;
import com.dronefly.app.model.ObstacleData;
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
        public int estimatedPhotoCount; // kamera intervallum alapján becsült fotószám
        public double areaM2;
        public double estimatedMinutes;
        public double altitudeM;
        public double stripSpacingM;
        public double photoDistM;
        public String errorMessage;
        /** Ha domborzatkövetés aktív: min/max waypoint magasság */
        public float terrainMinAlt = Float.NaN;
        public float terrainMaxAlt = Float.NaN;
        /** Domborzatkövetés alkalmazva? */
        public boolean terrainCorrected = false;
        /** Akadályok miatt kihagyott waypointok száma */
        public int skippedByObstacle = 0;

        // ── Mintavételi misszió mezők (M02 §7) ──────────────────────────
        /** Igaz, ha ezt az eredményt a SamplingMissionGenerator hozta létre
         *  (nem a kígyózó Grid Engine) — az Activity ez alapján dönti el,
         *  hogy uploadMission() vagy uploadSamplingMission() kell-e. */
        public boolean isSamplingMission = false;
        /** Mintapontok száma (0, ha nem mintavételi misszió) */
        public int sampleCount = 0;
    }

    public static GeneratorResult generate(List<GeoPoint> polygon, MissionConfig config) {
        GeneratorResult result = new GeneratorResult();

        if (polygon == null || polygon.size() < 3) {
            result.errorMessage = "Legalább 3 pont szükséges a területhez";
            return result;
        }

        // Magasság, sávköz, fotótávolság
        // A felhasználó által beírt magasság (config.altitudeM) az "igazság forrása",
        // mert a buildConfig ezt veszi az etAltitude-ból. Ha valamilyen ok miatt
        // nincs beállítva (régi MissionConfig <=0 érték), fallback a GSD-ből
        // számolásra (backward kompatibilitás).
        double altM;
        if (config.altitudeM > 0) {
            altM = config.altitudeM;
        } else {
            altM = (config.droneProfile != null)
                ? GsdCalculator.altitudeFromGsd(config.gsdCm, config.droneProfile)
                : GsdCalculator.altitudeFromGsd(config.gsdCm);
        }
        // Alsó határ 3 m (összhangban a UI clamp-pel), felső 300 m a generátor
        // matematikai biztonságához (a UI 120 m EU max-nál figyelmeztet).
        altM = Math.max(3, Math.min(300, altM));
        result.altitudeM = altM;

        double stripSpacing = (config.droneProfile != null)
            ? GsdCalculator.stripSpacingM(altM, config.sidelapPercent, config.droneProfile)
            : GsdCalculator.stripSpacingM(altM, config.sidelapPercent);
        double photoDist = (config.droneProfile != null)
            ? GsdCalculator.photoDistanceM(altM, config.frontlapPercent, config.droneProfile)
            : GsdCalculator.photoDistanceM(altM, config.frontlapPercent);
        result.stripSpacingM = stripSpacing;
        result.photoDistM    = photoDist;

        // Centroid
        double centLat = 0, centLon = 0;
        for (GeoPoint p : polygon) { centLat += p.getLatitude(); centLon += p.getLongitude(); }
        centLat /= polygon.size();
        centLon /= polygon.size();

        final double mPerDegLat = 111000.0;
        final double mPerDegLon = 111000.0 * Math.cos(Math.toRadians(centLat));

        // GPS → lokális XY (m)
        double[] px = new double[polygon.size()];
        double[] py = new double[polygon.size()];
        for (int i = 0; i < polygon.size(); i++) {
            px[i] = (polygon.get(i).getLongitude() - centLon) * mPerDegLon;
            py[i] = (polygon.get(i).getLatitude()  - centLat) * mPerDegLat;
        }
        result.areaM2 = polygonAreaM2(px, py);

        double off = (config.offsetM > 0) ? config.offsetM : 0.0;

        // Első rács (flightAngleDeg)
        List<WaypointData> allWaypoints = new ArrayList<>(
                runGridPass(px, py, centLat, centLon, mPerDegLat, mPerDegLon,
                        stripSpacing, altM, off, config.obstacles,
                        config.flightAngleDeg, result));

        // Keresztrács (crosshatch) — második rács
        if ("crosshatch".equals(config.gridMode)) {
            GeneratorResult dummy = new GeneratorResult();
            allWaypoints.addAll(runGridPass(px, py, centLat, centLon, mPerDegLat, mPerDegLon,
                    stripSpacing, altM, off, config.obstacles,
                    config.crosshatchHeadingDeg, dummy));
            result.skippedByObstacle += dummy.skippedByObstacle;
        }

        if (allWaypoints.isEmpty()) {
            result.errorMessage = "Nem sikerult waypointokat generalni. Ellenőrizd a teruletet es a beallitasokat.";
            return result;
        }

        result.totalWaypoints = allWaypoints.size();
        result.estimatedPhotoCount = (int)(result.areaM2 / stripSpacing / photoDist);
        if ("crosshatch".equals(config.gridMode)) result.estimatedPhotoCount *= 2;
        result.estimatedMinutes = GsdCalculator.estimatedFlightMinutes(
                result.areaM2, altM, config.sidelapPercent, config.speedMs, config.droneProfile);
        if ("crosshatch".equals(config.gridMode)) result.estimatedMinutes *= 2;

        for (int start = 0; start < allWaypoints.size(); start += MAX_WAYPOINTS_PER_MISSION) {
            int end = Math.min(start + MAX_WAYPOINTS_PER_MISSION, allWaypoints.size());
            result.segments.add(new ArrayList<>(allWaypoints.subList(start, end)));
        }

        return result;
    }

    /**
     * Egy rács-pass futtatása adott repülési szöggel.
     * Visszatér a lapos waypoint listával (szegmentálás előtt).
     */
    private static List<WaypointData> runGridPass(
            double[] px, double[] py,
            double centLat, double centLon,
            double mPerDegLat, double mPerDegLon,
            double stripSpacing, double altM, double off,
            List<ObstacleData> obstacles,
            double angleDeg,
            GeneratorResult result) {

        double angle = Math.toRadians(angleDeg);
        double cosA  = Math.cos(-angle);
        double sinA  = Math.sin(-angle);

        // Polygon elforgatása
        double[] rx = new double[px.length];
        double[] ry = new double[py.length];
        for (int i = 0; i < px.length; i++) {
            rx[i] = px[i] * cosA - py[i] * sinA;
            ry[i] = px[i] * sinA + py[i] * cosA;
        }

        // Bounding box
        double xMin = rx[0], xMax = rx[0], yMin = ry[0], yMax = ry[0];
        for (int i = 1; i < rx.length; i++) {
            if (rx[i] < xMin) xMin = rx[i];
            if (rx[i] > xMax) xMax = rx[i];
            if (ry[i] < yMin) yMin = ry[i];
            if (ry[i] > yMax) yMax = ry[i];
        }

        // Offset alkalmazása
        double[] rxOff = rx, ryOff = ry;
        if (off > 0) {
            double cxR = 0, cyR = 0;
            for (int i = 0; i < rx.length; i++) { cxR += rx[i]; cyR += ry[i]; }
            cxR /= rx.length; cyR /= ry.length;
            rxOff = new double[rx.length];
            ryOff = new double[ry.length];
            for (int i = 0; i < rx.length; i++) {
                double dx = rx[i] - cxR, dy = ry[i] - cyR;
                double dist = Math.sqrt(dx * dx + dy * dy);
                if (dist > 0) {
                    rxOff[i] = rx[i] + dx / dist * off;
                    ryOff[i] = ry[i] + dy / dist * off;
                } else {
                    rxOff[i] = rx[i]; ryOff[i] = ry[i];
                }
            }
            xMin = rxOff[0]; xMax = rxOff[0]; yMin = ryOff[0]; yMax = ryOff[0];
            for (int i = 1; i < rxOff.length; i++) {
                if (rxOff[i] < xMin) xMin = rxOff[i];
                if (rxOff[i] > xMax) xMax = rxOff[i];
                if (ryOff[i] < yMin) yMin = ryOff[i];
                if (ryOff[i] > yMax) yMax = ryOff[i];
            }
        }

        // Akadályok transzformálása ebbe a szögtartományba
        List<double[]> rotatedObstacles = new ArrayList<>();
        if (obstacles != null) {
            for (ObstacleData obs : obstacles) {
                if (!obs.isDangerousAt((float) altM)) continue;
                double ox = (obs.longitude - centLon) * mPerDegLon;
                double oy = (obs.latitude  - centLat) * mPerDegLat;
                double orx = ox * cosA - oy * sinA;
                double ory = ox * sinA + oy * cosA;
                rotatedObstacles.add(new double[]{orx, ory, obs.radiusM});
            }
        }

        // Scan vonalak generálása
        final double cosB = Math.cos(angle);
        final double sinB = Math.sin(angle);
        List<WaypointData> waypoints = new ArrayList<>();
        int stripIndex = 0;

        for (double scanY = yMin + stripSpacing / 2.0;
             scanY <= yMax + stripSpacing / 2.0;
             scanY += stripSpacing) {

            List<Double> intersections = computeIntersections(rxOff, ryOff, scanY);
            if (intersections.size() < 2) { stripIndex++; continue; }
            Collections.sort(intersections);

            for (int k = 0; k + 1 < intersections.size(); k += 2) {
                List<double[]> substrips = clipStripAgainstObstacles(
                        intersections.get(k), intersections.get(k + 1),
                        scanY, rotatedObstacles, result);

                boolean reverse = (stripIndex % 2 != 0);
                if (reverse) Collections.reverse(substrips);

                for (double[] sub : substrips) {
                    double xEnter = reverse ? sub[1] : sub[0];
                    double xExit  = reverse ? sub[0] : sub[1];
                    if (Math.abs(xExit - xEnter) < 0.5) continue;

                    double lx1 = xEnter * cosB - scanY * sinB;
                    double ly1 = xEnter * sinB + scanY * cosB;
                    waypoints.add(new WaypointData(
                            centLat + ly1 / mPerDegLat,
                            centLon + lx1 / mPerDegLon,
                            (float) altM, false));

                    double lx2 = xExit * cosB - scanY * sinB;
                    double ly2 = xExit * sinB + scanY * cosB;
                    waypoints.add(new WaypointData(
                            centLat + ly2 / mPerDegLat,
                            centLon + lx2 / mPerDegLon,
                            (float) altM, false));
                }
            }
            stripIndex++;
        }
        return waypoints;
    }

    /**
     * Egy vízszintes sáv [xLeft, xRight] levágása az akadály körök ellen.
     * A körök metszéseit megkeresi, és visszaadja az akadályokon KÍVÜL eső
     * részsávokat bal → jobb sorrendben.
     * Az akadályok által "elnyelt" sávhosszat a skippedByObstacle számlálóba gyűjti.
     *
     * @param xLeft  sáv bal határa (forgatott koordinátarendszerben)
     * @param xRight sáv jobb határa
     * @param scanY  sáv Y koordinátája
     * @param obs    akadályok: [cx, cy, r] (forgatott lokális koordinátákban)
     */
    private static List<double[]> clipStripAgainstObstacles(
            double xLeft, double xRight, double scanY,
            List<double[]> obs, GeneratorResult result) {

        // Induló részsávlista: az egész sáv
        List<double[]> segments = new ArrayList<>();
        segments.add(new double[]{xLeft, xRight});

        for (double[] o : obs) {
            double cx = o[0], cy = o[1], r = o[2];
            double dy = scanY - cy;
            double disc = r * r - dy * dy;
            if (disc <= 0) continue; // az akadály nem érinti ezt a sávot

            double half = Math.sqrt(disc);
            double obsL = cx - half; // akadály belépési X
            double obsR = cx + half; // akadály kilépési X

            List<double[]> clipped = new ArrayList<>();
            for (double[] seg : segments) {
                double sL = seg[0], sR = seg[1];
                if (obsR <= sL || obsL >= sR) {
                    clipped.add(seg); // nincs átfedés, marad
                    continue;
                }
                // Bal oldali részsáv (akadálytól balra)
                if (sL < obsL - 0.5) {
                    clipped.add(new double[]{sL, obsL});
                }
                // A kihagyott rész arányos wp-számát becsüljük
                double skippedLen = Math.min(sR, obsR) - Math.max(sL, obsL);
                if (skippedLen > 0) result.skippedByObstacle++;

                // Jobb oldali részsáv (akadálytól jobbra)
                if (sR > obsR + 0.5) {
                    clipped.add(new double[]{obsR, sR});
                }
            }
            segments = clipped;
        }
        return segments;
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
