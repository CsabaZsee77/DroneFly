package com.dronefly.app.model;

/**
 * Drón kamera profil — GSD számításhoz szükséges optikai paraméterek.
 * toString() = Spinner megjelenítés.
 */
public class DroneProfile {

    public final String name;
    public final double sensorWidthMm;
    public final double sensorHeightMm;
    public final double focalLengthMm;
    public final int    imageWidthPx;
    public final int    imageHeightPx;
    public final float  maxSpeedMs;    // MSDK / drón hardware limit
    public final int    shutterSpeed;  // mechanikus záridő nevező (pl. 800 = 1/800s)
    public final String notes;

    public DroneProfile(String name,
                        double sensorWidthMm, double sensorHeightMm,
                        double focalLengthMm,
                        int imageWidthPx,     int imageHeightPx,
                        float maxSpeedMs,     int shutterSpeed,
                        String notes) {
        this.name           = name;
        this.sensorWidthMm  = sensorWidthMm;
        this.sensorHeightMm = sensorHeightMm;
        this.focalLengthMm  = focalLengthMm;
        this.imageWidthPx   = imageWidthPx;
        this.imageHeightPx  = imageHeightPx;
        this.maxSpeedMs     = maxSpeedMs;
        this.shutterSpeed   = shutterSpeed;
        this.notes          = notes;
    }

    /** Spinner és Toast megjelenítéshez */
    @Override
    public String toString() {
        return name;
    }
}
