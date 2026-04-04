package com.dronefly.app.mission;

import com.dronefly.app.model.DroneProfile;

/**
 * GSD/magasság számítások tetszőleges DroneProfile alapján.
 *
 * Az eredeti P4P v1-es statikus metódusok visszafelé kompatibilisek maradnak.
 * Az új DroneProfile-os változatok bármely drónhoz használhatók.
 */
public class GsdCalculator {

    // ── Phantom 4 Pro v1 konstansok (visszafelé kompatibilitás) ─────────────

    public static final double SENSOR_WIDTH_MM  = 13.2;
    public static final double SENSOR_HEIGHT_MM = 8.8;
    public static final double FOCAL_LENGTH_MM  = 8.8;
    public static final int    IMAGE_WIDTH_PX   = 5472;
    public static final int    IMAGE_HEIGHT_PX  = 3648;

    // ── DroneProfile alapú metódusok (ajánlott) ──────────────────────────────

    /** GSD (cm/px) → repülési magasság (m) — drón profil alapján */
    public static double altitudeFromGsd(double gsdCm, DroneProfile drone) {
        return (gsdCm * drone.focalLengthMm * drone.imageWidthPx)
               / (drone.sensorWidthMm * 100.0);
    }

    /** Repülési magasság (m) → GSD (cm/px) — drón profil alapján */
    public static double gsdFromAltitude(double altitudeM, DroneProfile drone) {
        return (drone.sensorWidthMm * altitudeM * 100.0)
               / (drone.focalLengthMm * drone.imageWidthPx);
    }

    /** Egy kép talaj-lefedettsége szélességben (m) — drón profil alapján */
    public static double imageCoverageWidthM(double altitudeM, DroneProfile drone) {
        return (drone.sensorWidthMm * altitudeM) / drone.focalLengthMm;
    }

    /** Egy kép talaj-lefedettsége hosszában (m) — drón profil alapján */
    public static double imageCoverageHeightM(double altitudeM, DroneProfile drone) {
        return (drone.sensorHeightMm * altitudeM) / drone.focalLengthMm;
    }

    /** Sávköz az oldalsó átfedés figyelembevételével (m) — drón profil alapján */
    public static double stripSpacingM(double altitudeM, double sidelapPercent,
                                       DroneProfile drone) {
        return imageCoverageWidthM(altitudeM, drone) * (1.0 - sidelapPercent / 100.0);
    }

    /** Fotótávolság a menetirányban az átfedés figyelembevételével (m) — drón profil alapján */
    public static double photoDistanceM(double altitudeM, double frontlapPercent,
                                        DroneProfile drone) {
        return imageCoverageHeightM(altitudeM, drone) * (1.0 - frontlapPercent / 100.0);
    }

    /**
     * Ajánlott sebesség a drón zárideje alapján (mozgási blur elkerülése).
     * Képlet: max_v = 0.5 × GSD_m × shutterSpeed
     * Korlát: 3 m/s – drone.maxSpeedMs
     */
    public static float recommendedSpeedMs(double gsdCm, DroneProfile drone) {
        double gsdM = gsdCm / 100.0;
        double maxV = 0.5 * gsdM * drone.shutterSpeed;
        return (float) Math.min(drone.maxSpeedMs, Math.max(3.0, maxV));
    }

    /** Becsült repülési idő (perc) — drón profil alapján */
    public static double estimatedFlightMinutes(double areaM2, double altitudeM,
                                                double sidelapPercent, float speedMs,
                                                DroneProfile drone) {
        double stripSpacing = stripSpacingM(altitudeM, sidelapPercent, drone);
        double totalLength  = areaM2 / stripSpacing;
        return totalLength / speedMs / 60.0;
    }

    // ── P4P v1 visszafelé kompatibilis metódusok ────────────────────────────

    /** @deprecated Használd az altitudeFromGsd(gsdCm, drone) változatot */
    public static double altitudeFromGsd(double gsdCm) {
        return (gsdCm * FOCAL_LENGTH_MM * IMAGE_WIDTH_PX) / (SENSOR_WIDTH_MM * 100.0);
    }

    /** @deprecated Használd a stripSpacingM(altM, sidelap, drone) változatot */
    public static double stripSpacingM(double altitudeM, double sidelapPercent) {
        return (SENSOR_WIDTH_MM * altitudeM / FOCAL_LENGTH_MM) * (1.0 - sidelapPercent / 100.0);
    }

    /** @deprecated Használd a photoDistanceM(altM, frontlap, drone) változatot */
    public static double photoDistanceM(double altitudeM, double frontlapPercent) {
        return (SENSOR_HEIGHT_MM * altitudeM / FOCAL_LENGTH_MM) * (1.0 - frontlapPercent / 100.0);
    }

    /** @deprecated Használd a recommendedSpeedMs(gsdCm, drone) változatot */
    public static float recommendedSpeedMs(double gsdCm) {
        double maxV = 0.5 * (gsdCm / 100.0) * 800.0;
        return (float) Math.min(12.0, Math.max(3.0, maxV));
    }
}
