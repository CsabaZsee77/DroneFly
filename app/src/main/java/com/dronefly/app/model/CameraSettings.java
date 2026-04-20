package com.dronefly.app.model;

/**
 * Kamera beállítások a misszióhoz.
 * Auto módban a mezőgazdasági survey ajánlott értékeket használja.
 */
public class CameraSettings {

    public boolean autoMode = true;
    public PhotoMode photoMode = PhotoMode.SINGLE_SHOT;
    public IsoValue iso = IsoValue.AUTO;
    public ApertureValue aperture = ApertureValue.F_8;
    public ShutterSpeed shutterSpeed = ShutterSpeed.AUTO;
    public WhiteBalanceValue whiteBalance = WhiteBalanceValue.AUTO;
    public int whiteBalanceKelvin = 5600;
    public FileFormat fileFormat = FileFormat.JPEG_AND_RAW;

    // ── Foto mód ──────────────────────────────────────────────────────

    public enum PhotoMode {
        SINGLE_SHOT("Egyedi fotó"),
        INTERVAL("Időközi fotó");

        public final String displayName;
        PhotoMode(String displayName) { this.displayName = displayName; }
        @Override public String toString() { return displayName; }
    }

    // ── Rekesz ────────────────────────────────────────────────────────

    public enum ApertureValue {
        F_5_6("f/5.6"),
        F_8("f/8"),
        F_11("f/11");

        public final String displayName;
        ApertureValue(String displayName) { this.displayName = displayName; }
        @Override public String toString() { return displayName; }
    }

    // ── ISO ────────────────────────────────────────────────────────────

    public enum IsoValue {
        AUTO("Auto"),
        ISO_100("100"),
        ISO_200("200"),
        ISO_400("400"),
        ISO_800("800");

        public final String displayName;
        IsoValue(String displayName) { this.displayName = displayName; }
        @Override public String toString() { return displayName; }
    }

    // ── Záridő ─────────────────────────────────────────────────────────

    public enum ShutterSpeed {
        AUTO("Auto"),
        S_1_100("1/100"),
        S_1_200("1/200"),
        S_1_400("1/400"),
        S_1_500("1/500"),
        S_1_640("1/640"),
        S_1_800("1/800"),
        S_1_1000("1/1000"),
        S_1_1250("1/1250"),
        S_1_1600("1/1600"),
        S_1_2000("1/2000");

        public final String displayName;
        ShutterSpeed(String displayName) { this.displayName = displayName; }
        @Override public String toString() { return displayName; }
    }

    // ── Fehéregyensúly ─────────────────────────────────────────────────

    public enum WhiteBalanceValue {
        AUTO("Auto"),
        SUNNY("Napos"),
        CLOUDY("Felhős"),
        CUSTOM("Egyéni");

        public final String displayName;
        WhiteBalanceValue(String displayName) { this.displayName = displayName; }
        @Override public String toString() { return displayName; }
    }

    // ── Fájlformátum ───────────────────────────────────────────────────

    public enum FileFormat {
        JPEG("JPEG"),
        DNG_RAW("DNG RAW"),
        JPEG_AND_RAW("JPEG + RAW");

        public final String displayName;
        FileFormat(String displayName) { this.displayName = displayName; }
        @Override public String toString() { return displayName; }
    }

    // ── Gyári alapértelmezés mezőgazdasági survey-hez ───────────────────

    public static CameraSettings getAgricultureDefaults() {
        CameraSettings s = new CameraSettings();
        s.autoMode = true;
        s.photoMode = PhotoMode.SINGLE_SHOT;
        s.iso = IsoValue.AUTO;
        s.shutterSpeed = ShutterSpeed.AUTO;
        s.whiteBalance = WhiteBalanceValue.AUTO;
        s.fileFormat = FileFormat.JPEG_AND_RAW;
        return s;
    }
}
