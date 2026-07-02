package com.dronefly.app.model;

/**
 * Blokk-felosztás (M07) paraméterei.
 *
 * Lásd: docs/M07_BLOKK_FELOSZTAS/M07_L3_ALLAPOTGEP_ES_ENGINE.md §BlockGridConfig
 */
public class BlockGridConfig {

    public static final String ORIGIN_CENTROID     = "centroid";
    public static final String ORIGIN_FIRST_VERTEX = "first_vertex";
    public static final String ORIGIN_MANUAL       = "manual";

    public double cellWidthM         = 120.0;
    public double cellHeightM        = 120.0;
    public double rotationDeg        = 0.0;       // 0..179.99
    public double overlapBufferM     = 40.0;      // szomszéd átfedés
    public double minCoveragePercent = 15.0;      // 0..100, szűrési küszöb

    /** Fix origó mód — mentés után stabil, hogy újragenerálásnál ne csússzon. */
    public String originMode = ORIGIN_CENTROID;

    /** A számított vagy felhasználó által megadott origó GPS koordinátája.
     *  NaN = még nem számított; első generálásnál fog beíródni. */
    public double originLat = Double.NaN;
    public double originLon = Double.NaN;

    public BlockGridConfig() { }

    /** Klón a dialog UI-hoz, hogy ne módosítsuk az élő konfigot Cancel előtt. */
    public BlockGridConfig copy() {
        BlockGridConfig c = new BlockGridConfig();
        c.cellWidthM         = cellWidthM;
        c.cellHeightM        = cellHeightM;
        c.rotationDeg        = rotationDeg;
        c.overlapBufferM     = overlapBufferM;
        c.minCoveragePercent = minCoveragePercent;
        c.originMode         = originMode;
        c.originLat          = originLat;
        c.originLon          = originLon;
        return c;
    }

    /** A geometriai mezők változtak az előzőhöz képest? Ha igen → újragenerálás. */
    public boolean geometryDiffers(BlockGridConfig other) {
        if (other == null) return true;
        return cellWidthM         != other.cellWidthM
            || cellHeightM        != other.cellHeightM
            || rotationDeg        != other.rotationDeg
            || overlapBufferM     != other.overlapBufferM
            || minCoveragePercent != other.minCoveragePercent
            || !originMode.equals(other.originMode);
    }
}
