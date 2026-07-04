package com.dronefly.app.ai;

import com.dronefly.app.mission.GsdCalculator;
import com.dronefly.app.model.DroneProfile;

/**
 * GSD vonalzós kalibráció — mért skálából footprint terület, magasság nélkül
 * (M09_L1 §10, M09_L2 §10, M09_L3 `GsdCalibration`).
 *
 * Tiszta számítás, nincs Android-függés — egységtesztelhető.
 *
 * Felbontás-invariancia (M09_L4 §7): a `gsdMetersPerPx` és a `displayImageWidthPx`
 * UGYANABBAN a px-térben értendő (a megjelenített/mért bitmap pixelei), így a
 * footprint (talajszélesség) független a megjelenítési felbontástól.
 */
public final class GsdCalibration {

    private GsdCalibration() {}

    public static class Result {
        public double gsdMetersPerPx;   // = baseline / linePixelLength
        public double footprintWidthM;  // = gsdMetersPerPx * displayImageWidthPx
        public double footprintHeightM; // = footprintWidthM * imageAspect
        public double footprintAreaM2;  // = footprintWidthM * footprintHeightM
    }

    /**
     * @param refDistanceM        egy referencia-egység valós hossza méterben (pl. sortáv 0.76)
     * @param refUnitCount        hány egységet fog át a vonal (pl. 10 sortáv); a bázis = szorzat
     * @param linePixelLength     a húzott vonal hossza a MEGJELENÍTETT (mért) képen, px
     * @param displayImageWidthPx a mért bitmap szélessége, ugyanabban a px-térben
     * @param imageAspect         kép_magasság / kép_szélesség
     */
    public static Result compute(double refDistanceM, int refUnitCount,
                                 double linePixelLength, double displayImageWidthPx,
                                 double imageAspect) {
        Result r = new Result();
        double baseline = refDistanceM * Math.max(1, refUnitCount);
        if (linePixelLength <= 0 || baseline <= 0) return r;
        r.gsdMetersPerPx = baseline / linePixelLength;
        r.footprintWidthM = r.gsdMetersPerPx * displayImageWidthPx;
        r.footprintHeightM = r.footprintWidthM * imageAspect;
        r.footprintAreaM2 = r.footprintWidthM * r.footprintHeightM;
        return r;
    }

    /**
     * Lehorgonyzott mód (M09_L1 §10.4): egy kép mért footprintjéből korrekciós
     * arány az EXIF-footprintekre. Csak akkor érvényes, ha a magasság-hiba
     * rendszeres (konstans arány) — ezt a kereszt-ellenőrzés dönti el.
     */
    public static double anchorRatio(double measuredFootprintM2, double exifFootprintM2) {
        return exifFootprintM2 > 0 ? measuredFootprintM2 / exifFootprintM2 : 1.0;
    }

    /**
     * A mért footprint-szélességből visszaszámolt "valódi" repülési magasság —
     * az EXIF-kereszt-ellenőrzéshez (M09_L1 §10.6). A GsdCalculator inverze:
     * coverageWidthM = sensorWidthMm * alt / focalMm → alt = coverageWidthM * focalMm / sensorWidthMm.
     */
    public static double impliedAltitudeM(double footprintWidthM, DroneProfile drone) {
        if (drone == null || drone.sensorWidthMm <= 0) return 0;
        return footprintWidthM * drone.focalLengthMm / drone.sensorWidthMm;
    }

    /** Az adott magasságból (EXIF/terv) számolt footprint terület — összevetéshez. */
    public static double exifFootprintAreaM2(double altitudeM, DroneProfile drone) {
        return GsdCalculator.imageCoverageWidthM(altitudeM, drone)
                * GsdCalculator.imageCoverageHeightM(altitudeM, drone);
    }
}
