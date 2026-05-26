package com.dronefly.app.model;

/**
 * Akadály megjelölés a térképen.
 * A misszió generátor kihagyja azokat a waypointokat, amelyek az akadály
 * körzetébe esnek ÉS a repülési magasság <= az akadály magassága.
 */
public class ObstacleData {
    public String id    = "";   // "obst1", "obst2", ... — belső azonosító, nem változtatható
    public String label = "";   // szerkeszthető megnevezés, alap: azonos az id-vel
    public double latitude;
    public double longitude;
    public float radiusM;   // biztonsági zóna sugara (m) – minimum clearance
    public float heightM;   // akadály becsült magassága a talajhoz képest (m)

    public ObstacleData(double lat, double lon, float radiusM, float heightM) {
        this.latitude  = lat;
        this.longitude = lon;
        this.radiusM   = radiusM;
        this.heightM   = heightM;
    }

    public ObstacleData(String id, String label, double lat, double lon, float radiusM, float heightM) {
        this.id        = id != null ? id : "";
        this.label     = label != null ? label : this.id;
        this.latitude  = lat;
        this.longitude = lon;
        this.radiusM   = radiusM;
        this.heightM   = heightM;
    }

    /**
     * Veszélyes-e ez az akadály az adott repülési magasságon?
     * Ha a repülési magasság > akadály magassága: nem veszélyes.
     */
    public boolean isDangerousAt(float flightAltitudeM) {
        return flightAltitudeM <= heightM;
    }

    /**
     * A waypoint a veszélyzónában van-e? (2D távolság alapján)
     * @param lat waypoint szélességi foka
     * @param lon waypoint hosszúsági foka
     */
    public boolean containsPoint(double lat, double lon) {
        double dLat = (lat - latitude) * 111000.0;
        double dLon = (lon - longitude) * 111000.0 * Math.cos(Math.toRadians(latitude));
        double dist = Math.sqrt(dLat * dLat + dLon * dLon);
        return dist <= radiusM;
    }
}
