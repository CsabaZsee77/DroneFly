package com.dronefly.app.model;

public class MissionConfig {
    public double gsdCm = 3.0;           // cm/pixel
    public double altitudeM = 80.0;      // méter (GSD-ből számítva)
    public double sidelapPercent = 75.0; // oldalsó átfedés %
    public double frontlapPercent = 80.0;// menetirány átfedés %
    public float speedMs = 7.0f;         // repülési sebesség m/s
    public double flightAngleDeg = 0.0;  // 0 = K-Ny sávok (É-D irányban halad)
    public boolean returnHome = true;
    public boolean terrainFollowing = false; // domborzatkövetés engedélyezése
    public DroneProfile droneProfile;    // kiválasztott drón kamera profil
    public CameraSettings cameraSettings = CameraSettings.getAgricultureDefaults();

    public MissionConfig() {
        this.droneProfile = DroneProfiles.getDefault();
    }

    public MissionConfig(double gsdCm, double sidelapPercent, double frontlapPercent,
                         float speedMs, double flightAngleDeg, DroneProfile droneProfile) {
        this.gsdCm = gsdCm;
        this.sidelapPercent = sidelapPercent;
        this.frontlapPercent = frontlapPercent;
        this.speedMs = speedMs;
        this.flightAngleDeg = flightAngleDeg;
        this.droneProfile = droneProfile != null ? droneProfile : DroneProfiles.getDefault();
    }
}
