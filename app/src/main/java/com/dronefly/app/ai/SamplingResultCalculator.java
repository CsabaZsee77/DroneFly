package com.dronefly.app.ai;

import com.dronefly.app.mission.GsdCalculator;
import com.dronefly.app.model.DroneProfile;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Mintaponti darabszámokból teljes táblára extrapolált tőszám-becslés
 * (M09_L1 §5 — 2026-07-03: véges populáció korrekcióval (FPC) és
 * Student-t eloszlással pontosítva).
 *
 * Ez KLASSZIKUS MINTAVÉTELI STATISZTIKA (design-based sampling inference),
 * NEM térbeli interpoláció — a mintapontok közötti szórásból von le
 * következtetést a teljes tábla ÖSSZESÍTETT becslésének megbízhatóságáról,
 * nem az egyes nem-mintázott helyek értékéről (ahhoz IDW/kriging kellene,
 * ld. M09_L1 §5.4 — kikerül a v1 hatóköréből).
 */
public class SamplingResultCalculator {

    /** Student-t kritikus érték (95%, kétoldali) df szerint — lineáris interpolációval. */
    private static final int[] T_DF    = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 15, 20, 25, 30, 40, 60, 120};
    private static final double[] T_VAL = {12.706, 4.303, 3.182, 2.776, 2.571, 2.447, 2.365,
            2.306, 2.262, 2.228, 2.131, 2.086, 2.060, 2.042, 2.021, 2.000, 1.980};
    private static final double T_INF = 1.960;

    /**
     * @param pointResults   YoloInferenceEngine kimenete (index, lat, lon, count, warning
     *                       már beállítva; footprintAreaM2/density még nem)
     * @param sampleAltitudeM session.json-ból (M09_L1 §6)
     * @param drone           DroneProfile (droneProfileName alapján DroneProfiles.getByName())
     * @param aoiAreaM2       session.json-ból (teljes tábla területe)
     * @param partial         igaz, ha a felhasználó megszakította a futtatást (M09_L2 §6)
     */
    public static SamplingCountResult compute(List<PointResult> pointResults,
                                              double sampleAltitudeM,
                                              DroneProfile drone,
                                              double aoiAreaM2,
                                              String sessionId,
                                              String modelUsed,
                                              String targetClass,
                                              boolean partial) {
        SamplingCountResult result = new SamplingCountResult();
        result.sessionId = sessionId;
        result.modelUsed = modelUsed;
        result.targetClass = targetClass;
        result.partial = partial;
        result.processedCount = pointResults.size();
        result.totalAreaHa = aoiAreaM2 / 10000.0;

        SimpleDateFormat isoFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        isoFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        result.computedAt = isoFormat.format(new java.util.Date());

        // EXIF-alapú (magasságból számolt) footprint — az alapértelmezett minden
        // olyan pontra, aminek nincs kézi/örökített GSD-felülírása (M09_L1 §10.4).
        double exifFootprintAreaM2 = GsdCalculator.imageCoverageWidthM(sampleAltitudeM, drone)
                * GsdCalculator.imageCoverageHeightM(sampleAltitudeM, drone);

        // Sűrűség pontonként (M09_L1 §5.1) — a warning-gal jelölt (feldolgozhatatlan)
        // pontok bekerülnek a kimeneti listába (UI-táblázat), de a statisztikából
        // kimaradnak (M09_L2 §6: "kihagyás, a többi pont folytatódik").
        // GSD kalibráció (M09_L1 §10): ha egy pontnak van footprint-felülírása
        // (source ≠ EXIF, footprintAreaM2 > 0), azt használjuk, különben az EXIF-et.
        double sumDensity = 0.0;
        double sumFootprint = 0.0;
        int validCount = 0;
        for (PointResult p : pointResults) {
            double fp = (p.footprintSource != null
                    && p.footprintSource != FootprintSource.EXIF
                    && p.footprintAreaM2 > 0)
                    ? p.footprintAreaM2 : exifFootprintAreaM2;
            p.footprintAreaM2 = fp;
            if (p.warning == null) {
                p.densityPerM2 = fp > 0 ? p.count / fp : 0;
                p.densityPerHa = p.densityPerM2 * 10000.0;
                sumDensity += p.densityPerM2;
                sumFootprint += fp;
                validCount++;
            }
        }
        result.perPoint = pointResults;
        result.sampleCount = validCount;

        if (validCount == 0) {
            return result; // nincs érvényes pont — minden statisztikai mező 0/alapérték marad
        }

        double meanDensityM2 = sumDensity / validCount;
        result.meanDensityPerHa = meanDensityM2 * 10000.0;

        double sumSqDiff = 0.0;
        for (PointResult p : pointResults) {
            if (p.warning != null) continue;
            double diff = p.densityPerM2 - meanDensityM2;
            sumSqDiff += diff * diff;
        }
        double stdevM2 = validCount > 1 ? Math.sqrt(sumSqDiff / (validCount - 1)) : 0.0;
        result.stdDevPerHa = stdevM2 * 10000.0;
        result.cvPercent = meanDensityM2 > 0 ? (stdevM2 / meanDensityM2) * 100.0 : 0.0;

        result.estimatedTotalCount = meanDensityM2 * aoiAreaM2;

        // ── 95% CI — FPC + Student-t (M09_L1 §5.3, 2026-07-03 pontosítás) ──
        // N_plot a pontonkénti footprintek átlagával (M09_L4 §7.4) — heterogén
        // (kézi kalibrált) footprint mellett is konzisztens mintavételi arány.
        double meanFootprint = validCount > 0 ? sumFootprint / validCount : exifFootprintAreaM2;
        double nPlot = meanFootprint > 0 ? aoiAreaM2 / meanFootprint : validCount;
        result.nPlot = nPlot;

        if (validCount > 1) {
            double se = stdevM2 / Math.sqrt(validCount);
            double seFpc = se;
            if (nPlot > validCount) {
                seFpc = se * Math.sqrt((nPlot - validCount) / (nPlot - 1));
                result.fpcApplied = true;
            }
            double t95 = tValue95(validCount - 1);
            result.t95Used = t95;
            result.estimatedTotalCountCI95 = t95 * seFpc * aoiAreaM2;
        } else {
            result.t95Used = 0.0;
            result.estimatedTotalCountCI95 = 0.0;
        }

        return result;
    }

    /** Student-t kritikus érték (95%, kétoldali), lineáris interpolációval a táblázat pontjai közt. */
    static double tValue95(int df) {
        if (df < 1) return T_VAL[0];
        if (df >= 120) return T_INF;
        for (int i = 0; i < T_DF.length; i++) {
            if (T_DF[i] == df) return T_VAL[i];
            if (T_DF[i] > df) {
                if (i == 0) return T_VAL[0];
                int dfLo = T_DF[i - 1], dfHi = T_DF[i];
                double tLo = T_VAL[i - 1], tHi = T_VAL[i];
                double frac = (double) (df - dfLo) / (dfHi - dfLo);
                return tLo + frac * (tHi - tLo);
            }
        }
        return T_INF;
    }
}
