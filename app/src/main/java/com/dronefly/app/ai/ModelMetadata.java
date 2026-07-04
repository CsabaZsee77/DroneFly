package com.dronefly.app.ai;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * YOLO modell sidecar metaadat (M09_L2 §3.2) — a .tflite fájl mellett
 * elvárt azonos névtövű .json fájl tartalma.
 */
public class ModelMetadata {

    public String label;
    public String cropType;
    public int inputSize = 320;
    public float confThreshold = 0.35f;
    public float iouThreshold = 0.45f;
    public int targetClassIndex = 0;
    public List<String> classNames = new ArrayList<>();

    public static ModelMetadata fromJson(String json) throws JSONException {
        JSONObject o = new JSONObject(json);
        ModelMetadata m = new ModelMetadata();
        m.label = o.optString("label", "");
        m.cropType = o.optString("cropType", "");
        m.inputSize = o.optInt("inputSize", 320);
        m.confThreshold = (float) o.optDouble("confThreshold", 0.35);
        m.iouThreshold = (float) o.optDouble("iouThreshold", 0.45);
        m.targetClassIndex = o.optInt("targetClassIndex", 0);
        JSONArray names = o.optJSONArray("classNames");
        if (names != null) {
            for (int i = 0; i < names.length(); i++) {
                m.classNames.add(names.getString(i));
            }
        }
        return m;
    }

    /** M09_L2 §3.3 — az egyetlen v1-ben támogatott kimeneti alak: [1, 4+nc, N] */
    public int expectedOutputChannels() {
        return 4 + Math.max(1, classNames.size());
    }

    @Override
    public String toString() {
        return label != null && !label.isEmpty() ? label : cropType;
    }
}
