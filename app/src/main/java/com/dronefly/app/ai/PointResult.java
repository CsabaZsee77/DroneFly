package com.dronefly.app.ai;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

/** Egy mintapont YOLO-számlálási eredménye (M09_L1 §2). */
public class PointResult {
    public int index;
    public double lat;
    public double lon;
    public int count;
    public double footprintAreaM2;
    public double densityPerM2;
    public double densityPerHa;
    public long inferenceMs;
    /**
     * A pont tényleges képfájlneve a session mappában (session.json local_file).
     * NEM képezhető az indexből (point_%03d.jpg): a drónról letöltött fájl az
     * eredeti DJI nevén (pl. DJI_0007.JPG) maradhat, ha az átnevezés nem sikerült
     * — az előnézetnek ugyanazt a nevet kell használnia, amit az inferencia is.
     */
    public String localFile;
    /** Nem null, ha a képet nem sikerült feldolgozni (⚠ jelzés az UI-ban). */
    public String warning;
    /**
     * A pont detekciói (normalizált bbox + konfidencia) — a bounding boxos
     * előnézethez (PhotoPreview) és a results.json-ba mentve (a Dronterapia
     * oldali utófeldolgozáshoz/vizualizációhoz is használható).
     */
    public List<Detection> detections;

    // ── GSD kalibráció (M09_L1 §10) ─────────────────────────────────────────
    /** A footprint forrása; alapértelmezetten EXIF (magasság-alapú). */
    public FootprintSource footprintSource = FootprintSource.EXIF;
    /** Kézi mérésnél a mért GSD (cm/px); 0 ha EXIF-alapú. */
    public double gsdCmPx;
    /** Kézi mérésnél a referencia-alapvonal valós hossza (m); a reprodukálhatósághoz. */
    public double refDistanceM;
    /** Kézi mérésnél a húzott vonal pixelhossza (a mért bitmap px-terében). */
    public double refPixelLength;

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("index", index);
        o.put("lat", lat);
        o.put("lon", lon);
        o.put("count", count);
        o.put("footprint_area_m2", footprintAreaM2);
        o.put("footprint_source", footprintSource != null ? footprintSource.name() : "EXIF");
        if (footprintSource != null && footprintSource != FootprintSource.EXIF) {
            o.put("gsd_cm_px", round4((float) gsdCmPx));
            o.put("ref_distance_m", refDistanceM);
            o.put("ref_pixel_length", refPixelLength);
        }
        o.put("density_per_ha", densityPerHa);
        o.put("inference_ms", inferenceMs);
        o.put("local_file", localFile);
        o.put("warning", warning);
        if (detections != null) {
            // [cx, cy, w, h, conf] — normalizált (0-1), 4 tizedesre kerekítve
            JSONArray arr = new JSONArray();
            for (Detection d : detections) {
                JSONArray b = new JSONArray();
                b.put(round4(d.cx));
                b.put(round4(d.cy));
                b.put(round4(d.w));
                b.put(round4(d.h));
                b.put(round4(d.confidence));
                arr.put(b);
            }
            o.put("detections", arr);
        }
        return o;
    }

    private static double round4(float v) {
        return Math.round(v * 10000.0) / 10000.0;
    }
}
