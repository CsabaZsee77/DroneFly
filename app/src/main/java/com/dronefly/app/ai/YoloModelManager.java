package com.dronefly.app.ai;

import android.os.Environment;
import android.util.Log;

import org.json.JSONException;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * YOLO modell-fájlok kezelése — /sdcard/DroneFly/models/ szkennelése,
 * .onnx + sidecar .json párosítás és validáció (M09_L2 §3).
 *
 * Nincs bundle-özött (APK-ba épített) modell — a modell külső, cserélhető
 * fájl, amit a felhasználó másol a tabletre, jellemzően a Dronterápia
 * modell-regiszteréből (M09_L2 §3.1, §8).
 */
public class YoloModelManager {

    private static final String TAG = "YoloModelManager";
    private static final String MODELS_DIR = "DroneFly/models";
    private static final String MODEL_EXT = ".onnx";

    public static class ModelEntry {
        public final File modelFile;
        public final File jsonFile;
        public final ModelMetadata metadata;

        public ModelEntry(File modelFile, File jsonFile, ModelMetadata metadata) {
            this.modelFile = modelFile;
            this.jsonFile = jsonFile;
            this.metadata = metadata;
        }

        @Override
        public String toString() {
            return metadata.toString();
        }
    }

    public static File getModelsDir() {
        return new File(Environment.getExternalStorageDirectory(), MODELS_DIR);
    }

    /**
     * A modellek mappájának .onnx fájljait párosítja az azonos névtövű
     * .json sidecar-ral. Ha egy .onnx mellett nincs (vagy hibás) .json,
     * az a modell KIMARAD a listából (M09_L2 §3.2 "fail loudly" elv — nem
     * találgat alapértelmezett metaadatot), de bekerül a
     * {@code missingSidecarNames} listába, hogy a UI jelezhesse.
     *
     * @param missingSidecarNames kimeneti lista (lehet null), a hiányos
     *                            párosítású .onnx fájlnevekkel
     */
    public static List<ModelEntry> scanModels(List<String> missingSidecarNames) {
        List<ModelEntry> result = new ArrayList<>();
        File dir = getModelsDir();
        File[] files = dir.exists() ? dir.listFiles() : null;
        if (files == null) return result;

        for (File f : files) {
            if (!f.getName().toLowerCase(Locale.US).endsWith(MODEL_EXT)) continue;
            File jsonFile = sidecarFor(f);
            if (!jsonFile.exists()) {
                if (missingSidecarNames != null) missingSidecarNames.add(f.getName());
                continue;
            }
            try {
                ModelMetadata meta = ModelMetadata.fromJson(readFile(jsonFile));
                result.add(new ModelEntry(f, jsonFile, meta));
            } catch (JSONException | IOException e) {
                Log.e(TAG, "Sidecar JSON olvasási hiba (" + jsonFile.getName() + "): "
                        + e.getMessage());
                if (missingSidecarNames != null) missingSidecarNames.add(f.getName());
            }
        }

        Collections.sort(result, new Comparator<ModelEntry>() {
            @Override
            public int compare(ModelEntry a, ModelEntry b) {
                return a.modelFile.getName().compareToIgnoreCase(b.modelFile.getName());
            }
        });
        return result;
    }

    private static File sidecarFor(File modelFile) {
        String name = modelFile.getName();
        String base = name.substring(0, name.length() - MODEL_EXT.length());
        return new File(modelFile.getParentFile(), base + ".json");
    }

    private static String readFile(File f) throws IOException {
        byte[] buffer = new byte[(int) f.length()];
        try (FileInputStream fis = new FileInputStream(f)) {
            int readTotal = 0;
            while (readTotal < buffer.length) {
                int n = fis.read(buffer, readTotal, buffer.length - readTotal);
                if (n < 0) break;
                readTotal += n;
            }
        }
        return new String(buffer, StandardCharsets.UTF_8);
    }
}
