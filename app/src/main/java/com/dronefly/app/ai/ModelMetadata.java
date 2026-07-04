package com.dronefly.app.ai;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * YOLO modell sidecar metaadat (M09_L2 §3.2) — a .onnx fájl mellett
 * elvárt azonos névtövű .json fájl tartalma.
 */
public class ModelMetadata {

    public String label = "";
    public String cropType = "";
    public int inputSize = 640;
    public float confThreshold = 0.35f;
    public float iouThreshold = 0.45f;
    public int targetClassIndex = 0;
    public List<String> classNames = new ArrayList<>();
    /**
     * A GSD (cm/px), amelyre méretezett csempéken a modell tanult (M09_L1 §11.4).
     * A GSD-tudatos futtatás ehhez méretezi a mintaképet a detektálás előtt.
     * 0 vagy hiányzó → a rendszer 1,0 cm/px-t feltételez (UI-jelzéssel).
     */
    public double trainGsdCmPx = 1.0;

    public static ModelMetadata fromJson(String json) throws JSONException {
        JSONObject o = new JSONObject(json);
        ModelMetadata m = new ModelMetadata();
        m.label = o.optString("label", "");
        m.cropType = o.optString("cropType", "");
        m.inputSize = o.optInt("inputSize", 640);
        m.confThreshold = (float) o.optDouble("confThreshold", 0.35);
        m.iouThreshold = (float) o.optDouble("iouThreshold", 0.45);
        m.targetClassIndex = o.optInt("targetClassIndex", 0);
        m.trainGsdCmPx = o.optDouble("trainGsdCmPx", 1.0);
        JSONArray names = o.optJSONArray("classNames");
        if (names != null) {
            for (int i = 0; i < names.length(); i++) {
                m.classNames.add(names.getString(i));
            }
        }
        return m;
    }

    /** Sidecar .json írásához (metaadat-űrlap — M09_L1 §11 / M09_L2 §3.2). */
    public String toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("label", label != null ? label : "");
        o.put("cropType", cropType != null ? cropType : "");
        o.put("inputSize", inputSize);
        o.put("confThreshold", confThreshold);
        o.put("iouThreshold", iouThreshold);
        o.put("targetClassIndex", targetClassIndex);
        o.put("trainGsdCmPx", trainGsdCmPx);
        JSONArray names = new JSONArray();
        for (String n : classNames) names.put(n);
        o.put("classNames", names);
        return o.toString(2);
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
