package com.dronefly.app.ai;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Mintavételi tőszámlálás összesített eredménye (M09_L1 §2, M09_L1 §5.3 —
 * FPC + Student-t pontosítással, 2026-07-03).
 */
public class SamplingCountResult {

    public String sessionId;
    public String modelUsed;
    public String targetClass;
    public String computedAt;

    /** A futtatáshoz ténylegesen használt küszöbök (a UI-ban felülírhatók a sidecar alapértékei). */
    public float confThresholdUsed;
    public float iouThresholdUsed;

    public List<PointResult> perPoint = new ArrayList<>();

    public double totalAreaHa;
    public int sampleCount;

    public double meanDensityPerHa;
    public double stdDevPerHa;
    public double cvPercent;

    /** Hány mintaponti "parcella" férne el a teljes táblán (mintavételi arány, M09_L1 §5.3). */
    public double nPlot;
    /** Igaz, ha a véges populáció korrekció (FPC) érdemben csökkentette a hibasávot. */
    public boolean fpcApplied;
    /** A számításhoz használt Student-t kritikus érték (df = sampleCount - 1). */
    public double t95Used;

    public double estimatedTotalCount;
    public double estimatedTotalCountCI95;

    /** true, ha a session megszakadt és csak részleges eredmény áll rendelkezésre. */
    public boolean partial;
    public int processedCount;

    public JSONObject toJson() throws JSONException {
        JSONObject root = new JSONObject();
        root.put("session_id", sessionId);
        root.put("model_used", modelUsed);
        root.put("target_class", targetClass);
        root.put("computed_at", computedAt);
        root.put("conf_threshold_used", confThresholdUsed);
        root.put("iou_threshold_used", iouThresholdUsed);
        root.put("total_area_ha", totalAreaHa);
        root.put("sample_count", sampleCount);
        root.put("mean_density_per_ha", meanDensityPerHa);
        root.put("stdev_per_ha", stdDevPerHa);
        root.put("cv_percent", cvPercent);
        root.put("n_plot", nPlot);
        root.put("fpc_applied", fpcApplied);
        root.put("t95_used", t95Used);
        root.put("estimated_total_count", estimatedTotalCount);
        root.put("estimated_total_count_ci95", estimatedTotalCountCI95);
        root.put("partial", partial);
        root.put("processed_count", processedCount);

        JSONArray points = new JSONArray();
        for (PointResult p : perPoint) points.put(p.toJson());
        root.put("points", points);
        return root;
    }

    public void saveToFile(File sessionDir) throws JSONException, IOException {
        File out = new File(sessionDir, "results.json");
        try (FileWriter writer = new FileWriter(out)) {
            writer.write(toJson().toString(2));
        }
    }
}
