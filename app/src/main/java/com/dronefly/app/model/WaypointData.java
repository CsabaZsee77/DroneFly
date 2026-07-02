package com.dronefly.app.model;

public class WaypointData {
    public double latitude;
    public double longitude;
    public float altitudeM;
    public float gimbalPitch = -90f; // nadir
    public boolean shootPhoto = true;
    public float heading = 0f;

    // ── Domborzatkövetés mezők ─────────────────────────────────────────
    /** Tengerszint feletti magasság a waypoint pozíciójában (m, SRTM) */
    public double terrainElevation = Double.NaN;
    /** Igaz, ha az altitudeM domborzati korrekcióval lett módosítva */
    public boolean hasTerrainCorrection = false;

    // ── Mintavételi misszió mező (M02 §7 / M04 §15) ────────────────────
    /**
     * Hover időtartam másodpercben, mielőtt a shootPhoto akció végrehajtódik.
     * 0 = nincs megállás (grid misszió waypontjai, CURVED módban úgyis
     * figyelmen kívül maradna). >0 = a mintavételi misszió alacsony
     * (fotózó) waypontjai — NORMAL flightPathMode + WaypointAction
     * (STAY + START_TAKE_PHOTO) szükséges a végrehajtáshoz.
     */
    public float hoverSeconds = 0f;

    public WaypointData(double lat, double lon, float altM) {
        this.latitude = lat;
        this.longitude = lon;
        this.altitudeM = altM;
    }

    public WaypointData(double lat, double lon, float altM, boolean shootPhoto) {
        this(lat, lon, altM);
        this.shootPhoto = shootPhoto;
    }
}
