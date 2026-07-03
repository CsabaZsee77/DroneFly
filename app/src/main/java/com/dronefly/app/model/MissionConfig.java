package com.dronefly.app.model;

import java.util.ArrayList;
import java.util.List;

public class MissionConfig {
    public double gsdCm = 3.0;           // cm/pixel
    public double altitudeM = 80.0;      // méter (GSD-ből számítva)
    public double sidelapPercent = 75.0; // oldalsó átfedés %
    public double frontlapPercent = 80.0;// menetirány átfedés %
    public float speedMs = 7.0f;         // repülési sebesség m/s
    public double flightAngleDeg = 0.0;       // 0 = K-Ny sávok (É-D irányban halad)
    public String gridMode = "single";        // "single" | "crosshatch"
    public double crosshatchHeadingDeg = 90.0;// második rács iránya (crosshatch módban)
    public String startCorner = "auto";       // "auto" | "0" | "1" | "2" | "3"
    public boolean returnHome = true;
    public boolean terrainFollowing = false;  // domborzatkövetés engedélyezése
    public double offsetM = 0.0;              // túlrepülési határ méterben (0 = nincs)
    public DroneProfile droneProfile;    // kiválasztott drón kamera profil
    public CameraSettings cameraSettings = CameraSettings.getAgricultureDefaults();
    public List<ObstacleData> obstacles = new ArrayList<>(); // akadályok listája

    /**
     * M07 — Blokk-felosztás konfiguráció.
     * null = nincs blokk-mód, az AOI egyetlen misszióként megy (régi viselkedés).
     * nem-null = blokk-mód aktív; a rács paraméterei.
     */
    public BlockGridConfig blockGrid = null;

    // ── Mintavételi misszió (M01 §10 / M02 §7) ─────────────────────────
    // Ha true, a SamplingMissionGenerator-t kell hívni a GridMissionGenerator
    // helyett — a paraméterpanel is a mintavételi mezőket mutatja.
    public boolean samplingMode = false;
    public int nSamplePoints = 30;                 // mintapontok száma
    public String samplingMethod = "stratified";   // "stratified" | "halton" | "random"
    public long samplingSeed = 42L;                // reprodukálhatóság
    public double transitAltitudeM = 60.0;          // akadálybiztos utazó-magasság
    public double sampleAltitudeM = 8.0;            // fotózási magasság (GSD-ből számítható)
    public float hoverSeconds = 2.5f;               // megállás időtartama fotózás előtt

    // ── Sűrű rács (M10, 2026-07-03) ─────────────────────────────────────
    // Ha true, a GridMissionGenerator minden fotópozícióhoz külön waypointot
    // generál (NORMAL flightPathMode + app-vezérelt trigger minden ponton),
    // a CURVED+intervallum mód helyett — alacsony magasságon (< kb. 20 m,
    // 80% frontlapnál) ez garantálja a beállított átfedést, a CURVED mód
    // strukturális korlátja (M02_L1 §8.3) miatt.
    public boolean denseGridMode = false;

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
