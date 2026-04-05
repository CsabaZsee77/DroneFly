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
