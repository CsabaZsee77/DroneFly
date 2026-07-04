package com.dronefly.app.ai;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import ai.onnxruntime.NodeInfo;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OnnxValue;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import ai.onnxruntime.TensorInfo;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * ONNX Runtime Mobile (`OrtSession`) wrapper — YOLO (Ultralytics v8/11 onnx
 * export) inferencia mintavételi session képein (M09_L2 §1, §4, §5, M09_L3).
 *
 * A Dronterápia rendszer natívan ONNX-et tanít be — nincs konverziós lépés
 * (ONNX→TF→TFLite) a modell-előállítás és a tableten futtatás között
 * (M09_L2 §1, 2026-07-03-i döntés-felülvizsgálat).
 *
 * Szekvenciális feldolgozás egyetlen háttérszálon (M09_L2 §4 döntés) —
 * a Crystal Sky korlátozott RAM-ja miatt nincs párhuzamos kép-feldolgozás,
 * a session saját szálszáma (setIntraOpNumThreads) adja a gyorsítást.
 */
public class YoloInferenceEngine {

    private static final String TAG = "YoloInferenceEngine";
    private static final int SESSION_THREADS = 4;

    public interface InferenceListener {
        void onImageStart(int index, int total, String fileName);
        void onImageDone(int index, int total, int detectedCount, long inferenceMs);
        void onImageError(int index, String error);
        void onCancelled(List<PointResult> partialResults);
        void onAllDone(List<PointResult> results);
        /** Blokkoló hiba — pl. rossz modell-kimenet, session.json hiányzik (M09_L2 §6). */
        void onFatalError(String message);
    }

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    /**
     * @param sessionDir  sampling_sessions/{sessionId}/ mappa (point_*.jpg + session.json)
     * @param modelFile   kiválasztott .onnx fájl
     * @param meta        ModelMetadata (sidecar .json betöltve)
     * @param cancelFlag  true esetén a ciklus a következő kép előtt megáll
     */
    public void runOnSession(File sessionDir, File modelFile, ModelMetadata meta,
                             AtomicBoolean cancelFlag, InferenceListener listener) {
        executor.execute(() -> runInternal(sessionDir, modelFile, meta, cancelFlag, listener));
    }

    private void runInternal(File sessionDir, File modelFile, ModelMetadata meta,
                             AtomicBoolean cancelFlag, InferenceListener listener) {
        List<SessionPoint> points;
        try {
            points = readSessionPoints(sessionDir);
        } catch (JSONException | IOException e) {
            postFatal(listener, "session.json olvasási hiba: " + e.getMessage());
            return;
        }
        if (points.isEmpty()) {
            postFatal(listener, "A session nem tartalmaz mintapontot.");
            return;
        }

        OrtEnvironment env = OrtEnvironment.getEnvironment();
        OrtSession session;
        int expectedChannels = meta.expectedOutputChannels();
        try {
            OrtSession.SessionOptions options = new OrtSession.SessionOptions();
            options.setIntraOpNumThreads(SESSION_THREADS);
            session = env.createSession(modelFile.getAbsolutePath(), options);

            NodeInfo outInfo = session.getOutputInfo().values().iterator().next();
            long[] outputShape = ((TensorInfo) outInfo.getInfo()).getShape();
            if (outputShape.length != 3 || outputShape[1] != expectedChannels) {
                postFatal(listener, "Nem támogatott modell-kimenet — csak Ultralytics YOLOv8/11 "
                        + "onnx export támogatott (várt csatornaszám: " + expectedChannels
                        + ", kapott: " + (outputShape.length == 3 ? outputShape[1] : -1) + ")");
                closeQuietly(session);
                return;
            }
        } catch (OrtException e) {
            postFatal(listener, "Modell betöltési hiba: " + e.getMessage());
            return;
        }

        List<PointResult> results = new ArrayList<>();
        int total = points.size();

        for (int i = 0; i < total; i++) {
            if (cancelFlag != null && cancelFlag.get()) {
                closeQuietly(session);
                List<PointResult> partial = new ArrayList<>(results);
                mainHandler.post(() -> listener.onCancelled(partial));
                return;
            }

            SessionPoint sp = points.get(i);
            final int idx = i;
            mainHandler.post(() -> listener.onImageStart(idx, total, sp.localFile));

            PointResult pr = new PointResult();
            pr.index = sp.index;
            pr.lat = sp.lat;
            pr.lon = sp.lon;
            pr.localFile = sp.localFile;

            long startMs = System.currentTimeMillis();
            File imgFile = new File(sessionDir, sp.localFile);
            Bitmap bitmap = null;
            try {
                bitmap = decodeDownsampled(imgFile, meta.inputSize);
                if (bitmap == null) throw new IOException("decodeFile null eredmény");

                List<Detection> detections = detectSingle(session, env, bitmap, meta,
                        expectedChannels);
                pr.count = detections.size();
                pr.detections = detections;
                pr.inferenceMs = System.currentTimeMillis() - startMs;

                final int count = pr.count;
                final long ms = pr.inferenceMs;
                mainHandler.post(() -> listener.onImageDone(idx, total, count, ms));
            } catch (Exception e) {
                Log.e(TAG, sp.localFile + " feldolgozási hiba: " + e.getMessage());
                pr.count = 0;
                pr.warning = "Kép nem feldolgozható: " + e.getMessage();
                final String err = pr.warning;
                mainHandler.post(() -> listener.onImageError(idx, err));
            } finally {
                if (bitmap != null) bitmap.recycle();
            }

            results.add(pr);
        }

        closeQuietly(session);
        List<PointResult> finalResults = results;
        mainHandler.post(() -> listener.onAllDone(finalResults));
    }

    private void closeQuietly(OrtSession session) {
        try {
            session.close();
        } catch (OrtException e) {
            Log.e(TAG, "OrtSession close hiba: " + e.getMessage());
        }
    }

    /**
     * JPEG dekódolás inSampleSize-zal (M09_L4 §3): a 20 MP-es P4P fotó teljes
     * felbontású dekódolása ~80 MB lenne — a modell bemenetéhez (320/640 px)
     * elég a 2-hatvány szerinti legközelebbi downsample, a maradék skálázást
     * a preprocess() végzi. Publikus, mert a UI-előnézetek (thumbnail, dialog)
     * is ezt használják.
     */
    public static Bitmap decodeDownsampled(File f, int targetSize) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(f.getAbsolutePath(), bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;

        int inSample = 1;
        while (bounds.outWidth / (inSample * 2) >= targetSize
                && bounds.outHeight / (inSample * 2) >= targetSize) {
            inSample *= 2;
        }
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = inSample;
        return BitmapFactory.decodeFile(f.getAbsolutePath(), opts);
    }

    // ── Egyetlen kép feldolgozása ─────────────────────────────────────────

    private List<Detection> detectSingle(OrtSession session, OrtEnvironment env, Bitmap bitmap,
                                         ModelMetadata meta, int channels) throws OrtException {
        FloatBuffer input = preprocess(bitmap, meta.inputSize);
        long[] inputShape = {1, 3, meta.inputSize, meta.inputSize};

        String inputName = session.getInputNames().iterator().next();

        try (OnnxTensor inputTensor = OnnxTensor.createTensor(env, input, inputShape);
             OrtSession.Result result = session.run(Collections.singletonMap(inputName, inputTensor))) {
            // FONTOS (Android 5.1 / API 22): a result.get(name) java.util.Optional-t
            // ad vissza, ami API 24+ osztály → ClassNotFoundException Crystal Sky-n
            // (terepi teszt, 2026-07-03). Az iterátoros hozzáférés nem használ
            // Optional-t; egyetlen output tenzor van (alak-ellenőrzés a betöltésnél).
            OnnxValue value = result.iterator().next().getValue();
            float[][][] output = (float[][][]) value.getValue();
            return postprocess(output, meta, channels);
        }
    }

    /**
     * Bitmap → NCHW (csatorna-előbb) float32 FloatBuffer, [0,1] normalizált,
     * meta.inputSize×meta.inputSize-ra skálázva — az Ultralytics ONNX export
     * alapértelmezett bemeneti rétegsorrendje (M09_L2 §1, §3.3), szemben a
     * korábbi tflite-tervezet NHWC feltételezésével.
     */
    private FloatBuffer preprocess(Bitmap bitmap, int inputSize) {
        Bitmap scaled = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true);
        int size = inputSize;

        FloatBuffer buffer = ByteBuffer.allocateDirect(4 * 3 * size * size)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();

        int[] pixels = new int[size * size];
        scaled.getPixels(pixels, 0, size, 0, 0, size, size);

        float[] rPlane = new float[size * size];
        float[] gPlane = new float[size * size];
        float[] bPlane = new float[size * size];
        for (int i = 0; i < pixels.length; i++) {
            int p = pixels[i];
            rPlane[i] = ((p >> 16) & 0xFF) / 255f;
            gPlane[i] = ((p >> 8) & 0xFF) / 255f;
            bPlane[i] = (p & 0xFF) / 255f;
        }
        buffer.put(rPlane);
        buffer.put(gPlane);
        buffer.put(bPlane);
        buffer.rewind();

        if (scaled != bitmap) scaled.recycle();
        return buffer;
    }

    /**
     * Raw ONNX Runtime output [1, 4+nc, N] → Detection lista (M09_L2 §3.3).
     * Csak a modelMeta.targetClassIndex szerinti osztály detektálásait tartja
     * meg (M09_L2 §5), majd Java-oldali NMS-t alkalmaz (iouThreshold alapján).
     */
    private List<Detection> postprocess(float[][][] output, ModelMetadata meta, int channels) {
        int numCandidates = output[0][0].length;
        int nc = channels - 4;
        int targetClass = meta.targetClassIndex;
        List<Detection> candidates = new ArrayList<>();

        for (int n = 0; n < numCandidates; n++) {
            float bestConf = -1f;
            int bestClass = -1;
            for (int c = 0; c < nc; c++) {
                float conf = output[0][4 + c][n];
                if (conf > bestConf) {
                    bestConf = conf;
                    bestClass = c;
                }
            }
            if (bestClass != targetClass) continue;
            if (bestConf < meta.confThreshold) continue;

            float cx = output[0][0][n] / meta.inputSize;
            float cy = output[0][1][n] / meta.inputSize;
            float w  = output[0][2][n] / meta.inputSize;
            float h  = output[0][3][n] / meta.inputSize;
            candidates.add(new Detection(bestClass, bestConf, cx, cy, w, h));
        }

        return nonMaxSuppression(candidates, meta.iouThreshold);
    }

    private List<Detection> nonMaxSuppression(List<Detection> candidates, float iouThreshold) {
        List<Detection> sorted = new ArrayList<>(candidates);
        Collections.sort(sorted, new Comparator<Detection>() {
            @Override
            public int compare(Detection a, Detection b) {
                return Float.compare(b.confidence, a.confidence);
            }
        });

        List<Detection> kept = new ArrayList<>();
        boolean[] suppressed = new boolean[sorted.size()];
        for (int i = 0; i < sorted.size(); i++) {
            if (suppressed[i]) continue;
            Detection a = sorted.get(i);
            kept.add(a);
            for (int j = i + 1; j < sorted.size(); j++) {
                if (suppressed[j]) continue;
                if (Detection.iou(a, sorted.get(j)) > iouThreshold) suppressed[j] = true;
            }
        }
        return kept;
    }

    // ── session.json olvasás ─────────────────────────────────────────────

    private static class SessionPoint {
        int index;
        double lat, lon;
        String localFile;
    }

    private List<SessionPoint> readSessionPoints(File sessionDir) throws JSONException, IOException {
        File jsonFile = new File(sessionDir, "session.json");
        if (!jsonFile.exists()) throw new IOException("session.json nem található");

        byte[] buffer = new byte[(int) jsonFile.length()];
        try (FileInputStream fis = new FileInputStream(jsonFile)) {
            int read = 0;
            while (read < buffer.length) {
                int n = fis.read(buffer, read, buffer.length - read);
                if (n < 0) break;
                read += n;
            }
        }
        JSONObject root = new JSONObject(new String(buffer, java.nio.charset.StandardCharsets.UTF_8));
        JSONArray pointsArr = root.getJSONArray("points");

        List<SessionPoint> points = new ArrayList<>();
        for (int i = 0; i < pointsArr.length(); i++) {
            JSONObject p = pointsArr.getJSONObject(i);
            SessionPoint sp = new SessionPoint();
            sp.index = p.optInt("index", i);
            sp.lat = p.optDouble("lat", 0);
            sp.lon = p.optDouble("lon", 0);
            sp.localFile = p.optString("local_file",
                    String.format(Locale.US, "point_%03d.jpg", i));
            points.add(sp);
        }
        return points;
    }

    private void postFatal(InferenceListener listener, String message) {
        mainHandler.post(() -> listener.onFatalError(message));
    }
}
