package com.dronefly.app;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.graphics.Bitmap;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import com.dronefly.app.ai.FootprintSource;
import com.dronefly.app.ai.GsdCalibration;
import com.dronefly.app.ai.ModelMetadata;
import com.dronefly.app.ai.PointResult;
import com.dronefly.app.ai.SamplingCountResult;
import com.dronefly.app.ai.SamplingResultCalculator;
import com.dronefly.app.ai.YoloInferenceEngine;
import com.dronefly.app.ai.YoloModelManager;
import com.dronefly.app.dji.MediaSessionDownloader;
import com.dronefly.app.mission.GsdCalculator;
import com.dronefly.app.model.DroneProfile;
import com.dronefly.app.model.DroneProfiles;
import com.dronefly.app.ui.GsdRulerView;
import com.dronefly.app.ui.PhotoPreview;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * M09 — Edge AI Tőszámlálás eredmény-képernyő (M09_L1 §4, M09_L3).
 *
 * Önálló Activity, NEM a MissionPlannerActivity zsúfolt panelébe épül be
 * (ld. M09_L1 §4 indoklás — a fő képernyő már 2797 soros monolit).
 */
public class SamplingResultsActivity extends AppCompatActivity {

    public static final String EXTRA_SESSION_ID = "session_id";

    private Spinner spinnerSession;
    private Spinner spinnerModel;
    private Spinner spinnerTargetClass;
    private EditText etConfThreshold;
    private EditText etIouThreshold;
    private TextView tvModelHint;
    private Button btnStart;
    private Button btnCancel;
    private Button btnExportCsv;
    private ProgressBar progressBar;
    private TextView tvProgressStatus;
    private LinearLayout summaryCard;
    private TextView tvEstimatedTotal;
    private TextView tvSummaryDetail;
    private TextView tvCvWarning;
    private LinearLayout tablePoints;

    private HorizontalScrollView thumbScroll;
    private LinearLayout thumbStrip;
    private Button btnMeasureGsd;
    private TextView tvGsdStatus;
    private Button btnModelMeta;

    // ── Per-kép GSD (M09_L1 §11) — a futtatás ELŐTT mérve ───────────────────
    // Kulcs: a kép fájlneve (localFile), érték: mért GSD (cm/px, eredeti-felbontás).
    // Feloldás futtatáskor/densitynél: saját mérés, ha van; különben a mért képek
    // ÁTLAGA; ha egyáltalán nincs mérés: EXIF-fallback.
    private final java.util.Map<String, Double> measuredGsdByFile = new java.util.HashMap<>();

    private final YoloInferenceEngine engine = new YoloInferenceEngine();
    private final java.util.concurrent.ExecutorService thumbExecutor =
            java.util.concurrent.Executors.newSingleThreadExecutor();
    /** Session-váltáskor lép — a régi session még futó thumbnail-dekódjai eldobódnak. */
    private int thumbGeneration = 0;

    private List<File> sessionDirs = new ArrayList<>();
    private List<YoloModelManager.ModelEntry> models = new ArrayList<>();
    private AtomicBoolean cancelFlag;
    private SamplingCountResult lastResult;
    private File currentSessionDir;

    // ── GSD kalibráció újraszámítási kontextusa (M09_L1 §10.5) ──────────────
    private SessionMeta lastMeta;
    private DroneProfile lastDrone;
    private String lastModelUsed;
    private String lastTargetClass;
    private float lastConfUsed;
    private float lastIouUsed;
    /** A referencia-távolság megmarad a képek közt (M09_L1 §10.4b) — sortáv állandó. */
    private String lastRefDistanceCm = "76";
    private String lastRefUnitCount = "10";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sampling_results);

        spinnerSession = findViewById(R.id.spinnerSession);
        spinnerModel = findViewById(R.id.spinnerModel);
        spinnerTargetClass = findViewById(R.id.spinnerTargetClass);
        etConfThreshold = findViewById(R.id.etConfThreshold);
        etIouThreshold = findViewById(R.id.etIouThreshold);
        tvModelHint = findViewById(R.id.tvModelHint);
        btnStart = findViewById(R.id.btnStart);
        btnCancel = findViewById(R.id.btnCancel);
        btnExportCsv = findViewById(R.id.btnExportCsv);
        progressBar = findViewById(R.id.progressBar);
        tvProgressStatus = findViewById(R.id.tvProgressStatus);
        summaryCard = findViewById(R.id.summaryCard);
        tvEstimatedTotal = findViewById(R.id.tvEstimatedTotal);
        tvSummaryDetail = findViewById(R.id.tvSummaryDetail);
        tvCvWarning = findViewById(R.id.tvCvWarning);
        tablePoints = findViewById(R.id.tablePoints);
        thumbScroll = findViewById(R.id.thumbScroll);
        thumbStrip = findViewById(R.id.thumbStrip);
        btnMeasureGsd = findViewById(R.id.btnMeasureGsd);
        tvGsdStatus = findViewById(R.id.tvGsdStatus);
        btnModelMeta = findViewById(R.id.btnModelMeta);

        loadSessions();
        loadModels();

        spinnerSession.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                loadThumbnails();
                resetSessionGsd();  // új session → a mért GSD nem érvényes
            }
            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
        btnMeasureGsd.setOnClickListener(v -> openGsdMeasureDialog());
        btnModelMeta.setOnClickListener(v -> showModelMetaPicker());

        spinnerModel.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                updateTargetClassSpinner();
            }
            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });

        btnStart.setOnClickListener(v -> onStartClicked());
        btnCancel.setOnClickListener(v -> {
            if (cancelFlag != null) cancelFlag.set(true);
            btnCancel.setEnabled(false);
        });
        btnExportCsv.setOnClickListener(v -> exportCsv());
        findViewById(R.id.btnImportPhotos).setOnClickListener(v ->
                startActivity(new Intent(this, PhotoImportActivity.class)));
        // Vissza a tervezőbe — REORDER_TO_FRONT: ha már fut egy példány (innen
        // jöttünk), azt hozza előre, nem hoz létre új (nehéz) Activity-t.
        findViewById(R.id.btnOpenPlanner).setOnClickListener(v -> {
            Intent intent = new Intent(this, MissionPlannerActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            startActivity(intent);
        });
    }

    /**
     * A PhotoImportActivity CLEAR_TOP-pal tér vissza (launchMode singleTop) —
     * itt frissítjük a session-listát és előválasztjuk az újonnan importáltat.
     */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        loadSessions();
        loadModels();
    }

    // ── Session + modell lista betöltés ───────────────────────────────────

    private void loadSessions() {
        File root = MediaSessionDownloader.getSessionsRootDir(this);
        File[] dirs = root.exists() ? root.listFiles(File::isDirectory) : null;
        sessionDirs = dirs != null ? new ArrayList<>(Arrays.asList(dirs)) : new ArrayList<>();
        // session_id formátuma yyyyMMdd_HHmmss — a névsorrend = időrend, legutóbbi legfelül
        Collections.sort(sessionDirs, (a, b) -> b.getName().compareTo(a.getName()));

        List<String> names = new ArrayList<>();
        for (File f : sessionDirs) names.add(f.getName());
        if (names.isEmpty()) {
            names.add("(nincs elérhető session)");
            btnStart.setEnabled(false);
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, names);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerSession.setAdapter(adapter);

        String preselect = getIntent().getStringExtra(EXTRA_SESSION_ID);
        if (preselect != null) {
            for (int i = 0; i < sessionDirs.size(); i++) {
                if (sessionDirs.get(i).getName().equals(preselect)) {
                    spinnerSession.setSelection(i);
                    break;
                }
            }
        }
    }

    private void loadModels() {
        List<String> missing = new ArrayList<>();
        models = YoloModelManager.scanModels(missing);

        if (models.isEmpty()) {
            btnStart.setEnabled(false);
            tvModelHint.setText("Nincs importált modell — másold a .onnx + .json fájlt ide: "
                    + YoloModelManager.getModelsDir().getAbsolutePath());
        } else if (!missing.isEmpty()) {
            tvModelHint.setText("Hiányzó sidecar .json: " + String.join(", ", missing));
        } else {
            tvModelHint.setText("");
        }

        ArrayAdapter<YoloModelManager.ModelEntry> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, models);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerModel.setAdapter(adapter);
        updateTargetClassSpinner();
    }

    private void updateTargetClassSpinner() {
        YoloModelManager.ModelEntry entry = selectedModel();
        // Futtatási paraméterek alapértéke a kiválasztott modell sidecar-jából —
        // a felhasználó felülírhatja futtatás előtt (Dronterapia web UI mintájára)
        if (entry != null) {
            etConfThreshold.setText(String.format(Locale.US, "%.2f", entry.metadata.confThreshold));
            etIouThreshold.setText(String.format(Locale.US, "%.2f", entry.metadata.iouThreshold));
        }
        List<String> classNames = new ArrayList<>();
        if (entry != null && entry.metadata.classNames != null && !entry.metadata.classNames.isEmpty()) {
            classNames.addAll(entry.metadata.classNames);
        } else {
            classNames.add("osztály 0");
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, classNames);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerTargetClass.setAdapter(adapter);
        if (entry != null) {
            int def = Math.max(0, Math.min(classNames.size() - 1, entry.metadata.targetClassIndex));
            spinnerTargetClass.setSelection(def);
        }
    }

    // ── Fotó-előnézetek ───────────────────────────────────────────────────

    /** A kiválasztott session fotóiból thumbnail-sáv — koppintásra nagyítás. */
    private void loadThumbnails() {
        thumbStrip.removeAllViews();
        File session = selectedSession();
        File[] photos = session != null
                ? session.listFiles(f -> f.getName().toLowerCase(Locale.US).endsWith(".jpg"))
                : null;
        if (photos == null || photos.length == 0) {
            thumbScroll.setVisibility(View.GONE);
            return;
        }
        Arrays.sort(photos, (a, b) -> a.getName().compareTo(b.getName()));
        thumbScroll.setVisibility(View.VISIBLE);

        final int generation = ++thumbGeneration;
        final float density = getResources().getDisplayMetrics().density;
        for (File photo : photos) {
            thumbExecutor.execute(() -> {
                if (generation != thumbGeneration) return;
                Bitmap thumb = YoloInferenceEngine.decodeDownsampled(photo, 160);
                if (thumb == null) return;
                runOnUiThread(() -> {
                    if (generation != thumbGeneration) {
                        thumb.recycle();
                        return;
                    }
                    ImageView iv = new ImageView(this);
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                            (int) (96 * density), LinearLayout.LayoutParams.MATCH_PARENT);
                    lp.rightMargin = (int) (4 * density);
                    iv.setLayoutParams(lp);
                    iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
                    iv.setImageBitmap(thumb);
                    iv.setOnClickListener(v -> PhotoPreview.show(this, photo));
                    thumbStrip.addView(iv);
                });
            });
        }
    }

    private YoloModelManager.ModelEntry selectedModel() {
        int pos = spinnerModel.getSelectedItemPosition();
        return (pos >= 0 && pos < models.size()) ? models.get(pos) : null;
    }

    private File selectedSession() {
        int pos = spinnerSession.getSelectedItemPosition();
        return (pos >= 0 && pos < sessionDirs.size()) ? sessionDirs.get(pos) : null;
    }

    // ── VALIDATING → RUNNING ──────────────────────────────────────────────

    private void onStartClicked() {
        File sessionDir = selectedSession();
        YoloModelManager.ModelEntry entry = selectedModel();
        if (sessionDir == null || entry == null) {
            Toast.makeText(this, "Válassz sessiont és modellt", Toast.LENGTH_SHORT).show();
            return;
        }

        SessionMeta meta;
        try {
            meta = readSessionMeta(sessionDir);
        } catch (JSONException | IOException e) {
            new AlertDialog.Builder(this)
                    .setTitle("Session hiba")
                    .setMessage("A session.json nem olvasható: " + e.getMessage())
                    .setPositiveButton("OK", null)
                    .show();
            return;
        }

        if (meta.sampleAltitudeM <= 0 || meta.aoiAreaM2 <= 0) {
            promptManualMeta(sessionDir, meta, entry);
        } else {
            startInference(sessionDir, entry, meta);
        }
    }

    /**
     * M09_L2 §6: régi (M09 előtti) session esetén a session.json nem tartalmaz
     * sample_altitude_m / aoi_area_m2 mezőt — blokkoló dialog kéri be kézzel,
     * nincs hallgatólagos alapérték.
     */
    private void promptManualMeta(File sessionDir, SessionMeta meta,
                                  YoloModelManager.ModelEntry entry) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(48, 24, 48, 0);

        TextView label1 = new TextView(this);
        label1.setText("Mintavételi (fotózási) magasság [m]:");
        EditText inputAlt = new EditText(this);
        inputAlt.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        if (meta.sampleAltitudeM > 0) inputAlt.setText(String.valueOf(meta.sampleAltitudeM));

        TextView label2 = new TextView(this);
        label2.setText("Tábla (AOI) területe [ha]:");
        EditText inputArea = new EditText(this);
        inputArea.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        if (meta.aoiAreaM2 > 0) inputArea.setText(String.valueOf(meta.aoiAreaM2 / 10000.0));

        layout.addView(label1);
        layout.addView(inputAlt);
        layout.addView(label2);
        layout.addView(inputArea);

        new AlertDialog.Builder(this)
                .setTitle("Hiányzó session-adat (régi mintavétel)")
                .setMessage("Ez a session a magasság/terület adatok bevezetése előtt készült — "
                        + "add meg kézzel a footprint-terület számításához.")
                .setView(layout)
                .setPositiveButton("Folytatás", (d, w) -> {
                    try {
                        meta.sampleAltitudeM = Double.parseDouble(inputAlt.getText().toString().trim());
                        meta.aoiAreaM2 = Double.parseDouble(inputArea.getText().toString().trim()) * 10000.0;
                    } catch (NumberFormatException e) {
                        Toast.makeText(this, "Érvénytelen érték", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    startInference(sessionDir, entry, meta);
                })
                .setNegativeButton("Mégse", null)
                .show();
    }

    private void startInference(File sessionDir, YoloModelManager.ModelEntry entry, SessionMeta meta) {
        currentSessionDir = sessionDir;
        int targetClassIndex = spinnerTargetClass.getSelectedItemPosition();
        entry.metadata.targetClassIndex = Math.max(0, targetClassIndex);

        // Futtatási paraméterek a UI-mezőkből (0–1 tartományra korlátozva) —
        // felülírják a sidecar alapértékeket erre a futtatásra
        entry.metadata.confThreshold = parseThreshold(etConfThreshold,
                entry.metadata.confThreshold);
        entry.metadata.iouThreshold = parseThreshold(etIouThreshold,
                entry.metadata.iouThreshold);
        etConfThreshold.setText(String.format(Locale.US, "%.2f", entry.metadata.confThreshold));
        etIouThreshold.setText(String.format(Locale.US, "%.2f", entry.metadata.iouThreshold));
        final float confUsed = entry.metadata.confThreshold;
        final float iouUsed = entry.metadata.iouThreshold;

        cancelFlag = new AtomicBoolean(false);
        btnStart.setEnabled(false);
        btnCancel.setVisibility(View.VISIBLE);
        btnCancel.setEnabled(true);
        btnExportCsv.setVisibility(View.GONE);
        summaryCard.setVisibility(View.GONE);
        tablePoints.removeAllViews();
        progressBar.setProgress(0);
        tvProgressStatus.setText("Indítás...");

        DroneProfile drone = resolveDrone(meta.droneProfileName);
        String targetClassName = (entry.metadata.classNames != null
                && targetClassIndex < entry.metadata.classNames.size())
                ? entry.metadata.classNames.get(targetClassIndex) : ("osztály " + targetClassIndex);

        // GSD-tudatos futtatás (M09_L1 §11): per-kép GSD (saját mérés / mért
        // képek átlaga / EXIF-fallback). A modell trainGsdCmPx-éhez méretez az engine.
        double exifGsd = GsdCalculator.gsdFromAltitude(meta.sampleAltitudeM, drone);
        double avgMeasured = averageMeasuredGsd();
        double fallbackGsd = avgMeasured > 0 ? avgMeasured : exifGsd;
        java.util.Map<String, Double> gsdByFile = new java.util.HashMap<>(measuredGsdByFile);
        tvProgressStatus.setText(String.format(Locale.getDefault(),
                "Indítás... (%d kép mérve, a többi %s %.3f cm/px → betanítás %.2f cm/px)",
                measuredGsdByFile.size(), avgMeasured > 0 ? "átlag" : "EXIF",
                fallbackGsd, entry.metadata.trainGsdCmPx));

        engine.runOnSession(sessionDir, entry.modelFile, entry.metadata, gsdByFile, fallbackGsd,
                cancelFlag, new YoloInferenceEngine.InferenceListener() {
                    @Override
                    public void onImageStart(int index, int total, String fileName) {
                        progressBar.setMax(total);
                        progressBar.setProgress(index);
                        tvProgressStatus.setText("Feldolgozás: " + (index + 1) + "/" + total
                                + " (" + fileName + ")");
                    }
                    @Override
                    public void onImageDone(int index, int total, int detectedCount, long inferenceMs) {
                        progressBar.setProgress(index + 1);
                    }
                    @Override
                    public void onImageError(int index, String error) {
                        Toast.makeText(SamplingResultsActivity.this,
                                (index + 1) + ". kép hiba: " + error, Toast.LENGTH_SHORT).show();
                    }
                    @Override
                    public void onCancelled(List<PointResult> partialResults) {
                        onRunFinished(partialResults, meta, drone, sessionDir,
                                entry.modelFile.getName(), targetClassName,
                                confUsed, iouUsed, true);
                    }
                    @Override
                    public void onAllDone(List<PointResult> results) {
                        onRunFinished(results, meta, drone, sessionDir,
                                entry.modelFile.getName(), targetClassName,
                                confUsed, iouUsed, false);
                    }
                    @Override
                    public void onFatalError(String message) {
                        btnStart.setEnabled(true);
                        btnCancel.setVisibility(View.GONE);
                        tvProgressStatus.setText("");
                        new AlertDialog.Builder(SamplingResultsActivity.this)
                                .setTitle("Hiba")
                                .setMessage(message)
                                .setPositiveButton("OK", null)
                                .show();
                    }
                });
    }

    // ── CALCULATING → DONE ────────────────────────────────────────────────

    private void onRunFinished(List<PointResult> pointResults, SessionMeta meta, DroneProfile drone,
                               File sessionDir, String modelUsed, String targetClassName,
                               float confUsed, float iouUsed, boolean partial) {
        btnStart.setEnabled(true);
        btnCancel.setVisibility(View.GONE);
        tvProgressStatus.setText(partial ? "Megszakítva" : "Kész");

        if (pointResults.isEmpty()) {
            Toast.makeText(this, "Nincs feldolgozott pont", Toast.LENGTH_SHORT).show();
            return;
        }

        // Per-kép footprint a feloldott GSD-ből (M09_L1 §11.4a): saját mérés /
        // mért képek átlaga / EXIF — ugyanaz a GSD, ami a detektálási resize-t is adta.
        double exifGsd = GsdCalculator.gsdFromAltitude(meta.sampleAltitudeM, drone);
        boolean anyMeasured = !measuredGsdByFile.isEmpty();
        // Kép-dimenziók (session-szinten azonosak) — az első pont képéből
        int origW = drone.imageWidthPx, origH = drone.imageHeightPx;
        int[] dims = readImageDims(new File(sessionDir, pointResults.get(0).localFile != null
                ? pointResults.get(0).localFile
                : String.format(Locale.US, "point_%03d.jpg", pointResults.get(0).index)));
        if (dims[0] > 0) { origW = dims[0]; origH = dims[1]; }
        for (PointResult p : pointResults) {
            String file = p.localFile != null ? p.localFile
                    : String.format(Locale.US, "point_%03d.jpg", p.index);
            double gsd = resolveGsdForFile(file, exifGsd);
            double wM = gsd / 100.0 * origW, hM = gsd / 100.0 * origH;
            p.gsdCmPx = gsd;
            p.footprintAreaM2 = wM * hM;
            boolean own = measuredGsdByFile.containsKey(file);
            p.footprintSource = own ? FootprintSource.MANUAL
                    : (anyMeasured ? FootprintSource.INHERITED : FootprintSource.EXIF);
        }

        SamplingCountResult result = SamplingResultCalculator.compute(pointResults,
                meta.sampleAltitudeM, drone, meta.aoiAreaM2, sessionDir.getName(),
                modelUsed, targetClassName, partial);
        result.confThresholdUsed = confUsed;
        result.iouThresholdUsed = iouUsed;

        // Kontextus mentése a kalibráció-utáni újraszámításhoz (M09_L1 §10.5)
        lastMeta = meta;
        lastDrone = drone;
        lastModelUsed = modelUsed;
        lastTargetClass = targetClassName;
        lastConfUsed = confUsed;
        lastIouUsed = iouUsed;

        try {
            result.saveToFile(sessionDir);
        } catch (JSONException | IOException e) {
            Toast.makeText(this, "results.json mentési hiba: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }

        lastResult = result;
        renderResult(result);
    }

    /**
     * Újraszámítás GSD-kalibráció után — INFERENCIA NÉLKÜL (M09_L2 §10.4).
     * A pontok count/detections mezői változatlanok; csak a footprint-felülírásokból
     * számol újra density/CV/CI. A már meglévő PointResult objektumokat mutálja.
     */
    private void recomputeAfterCalibration() {
        if (lastResult == null || lastMeta == null) return;
        SamplingCountResult result = SamplingResultCalculator.compute(lastResult.perPoint,
                lastMeta.sampleAltitudeM, lastDrone, lastMeta.aoiAreaM2, currentSessionDir.getName(),
                lastModelUsed, lastTargetClass, lastResult.partial);
        result.confThresholdUsed = lastConfUsed;
        result.iouThresholdUsed = lastIouUsed;
        try {
            result.saveToFile(currentSessionDir);
        } catch (JSONException | IOException e) {
            Toast.makeText(this, "results.json mentési hiba: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
        lastResult = result;
        renderResult(result);
    }

    // ── Modell metaadat-űrlap (sidecar létrehozás/szerkesztés, M09_L1 §11.4) ──

    /** Az összes .onnx listája (sidecar nélkülieket is) → metaadat-szerkesztő. */
    private void showModelMetaPicker() {
        List<File> all = YoloModelManager.listAllOnnx();
        if (all.isEmpty()) {
            new AlertDialog.Builder(this)
                    .setTitle("Nincs modell")
                    .setMessage("Másolj .onnx modellt ide: "
                            + YoloModelManager.getModelsDir().getAbsolutePath())
                    .setPositiveButton("OK", null)
                    .show();
            return;
        }
        String[] names = new String[all.size()];
        for (int i = 0; i < all.size(); i++) {
            boolean hasMeta = YoloModelManager.sidecarFor(all.get(i)).exists();
            names[i] = all.get(i).getName() + (hasMeta ? "  ✓" : "  ⚠ nincs metaadat");
        }
        new AlertDialog.Builder(this)
                .setTitle("Modell metaadat szerkesztése")
                .setItems(names, (d, which) -> openModelMetadataDialog(all.get(which)))
                .setNegativeButton("Mégse", null)
                .show();
    }

    /**
     * Metaadat-űrlap egy .onnx-hoz: a meglévő sidecar-ból vagy alapértékből tölt,
     * a bemeneti méretet és osztályszámot a modellből olvassa (auto-fill), a
     * felhasználó megadja a GSD-t stb., és menti a .json sidecart.
     */
    private void openModelMetadataDialog(File onnxFile) {
        File sidecar = YoloModelManager.sidecarFor(onnxFile);
        ModelMetadata m = new ModelMetadata();
        if (sidecar.exists()) {
            try {
                m = ModelMetadata.fromJson(readFileText(sidecar));
            } catch (Exception ignored) {}
        } else {
            m.label = onnxFile.getName().replace(".onnx", "");
        }
        // Auto-fill a modellből (inputSize, osztályszám)
        int[] info = YoloInferenceEngine.readModelInfo(onnxFile);
        if (info[0] > 0) m.inputSize = info[0];
        if (m.classNames.isEmpty()) {
            int nc = info[1] > 0 ? info[1] : 1;
            for (int i = 0; i < nc; i++) m.classNames.add("osztály_" + i);
        }

        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(48, 16, 48, 0);

        final EditText etLabel = addFormField(form, "Név / címke", m.label);
        final EditText etInput = addFormField(form, "Bemeneti méret (px, a modellből)",
                String.valueOf(m.inputSize));
        final EditText etClasses = addFormField(form, "Osztálynevek (vesszővel)",
                android.text.TextUtils.join(", ", m.classNames));
        final EditText etTarget = addFormField(form, "Cél-osztály index",
                String.valueOf(m.targetClassIndex));
        final EditText etConf = addFormField(form, "Konfidencia küszöb",
                String.format(Locale.US, "%.2f", m.confThreshold));
        final EditText etIou = addFormField(form, "IoU küszöb",
                String.format(Locale.US, "%.2f", m.iouThreshold));
        final EditText etGsd = addFormField(form, "Betanítási GSD (cm/px) — FONTOS!",
                String.format(Locale.US, "%.2f", m.trainGsdCmPx));

        android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        scroll.addView(form);

        final ModelMetadata base = m;
        new AlertDialog.Builder(this)
                .setTitle("Metaadat: " + onnxFile.getName())
                .setView(scroll)
                .setPositiveButton("Mentés", (d, w) -> {
                    ModelMetadata out = new ModelMetadata();
                    out.label = etLabel.getText().toString().trim();
                    out.cropType = base.cropType;
                    try { out.inputSize = Integer.parseInt(etInput.getText().toString().trim()); }
                    catch (NumberFormatException e) { out.inputSize = base.inputSize; }
                    out.classNames = new ArrayList<>();
                    for (String s : etClasses.getText().toString().split(",")) {
                        String t = s.trim();
                        if (!t.isEmpty()) out.classNames.add(t);
                    }
                    if (out.classNames.isEmpty()) out.classNames.add("objektum");
                    try { out.targetClassIndex = Integer.parseInt(etTarget.getText().toString().trim()); }
                    catch (NumberFormatException e) { out.targetClassIndex = 0; }
                    out.confThreshold = (float) parseDefault(etConf, base.confThreshold);
                    out.iouThreshold = (float) parseDefault(etIou, base.iouThreshold);
                    out.trainGsdCmPx = parseDefault(etGsd, base.trainGsdCmPx);
                    try {
                        try (java.io.FileWriter fw = new java.io.FileWriter(sidecar)) {
                            fw.write(out.toJson());
                        }
                        Toast.makeText(this, "Metaadat mentve: " + sidecar.getName(),
                                Toast.LENGTH_SHORT).show();
                        loadModels(); // frissül a spinner
                    } catch (Exception e) {
                        Toast.makeText(this, "Mentési hiba: " + e.getMessage(),
                                Toast.LENGTH_LONG).show();
                    }
                })
                .setNegativeButton("Mégse", null)
                .show();
    }

    private EditText addFormField(LinearLayout parent, String label, String value) {
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextColor(0xFFAAAACC);
        tv.setTextSize(12);
        parent.addView(tv);
        EditText et = new EditText(this);
        et.setText(value);
        et.setTextColor(0xFFFFFFFF);
        et.setTextSize(14);
        parent.addView(et);
        return et;
    }

    private static double parseDefault(EditText f, double fallback) {
        try { return Double.parseDouble(f.getText().toString().trim().replace(',', '.')); }
        catch (NumberFormatException e) { return fallback; }
    }

    private static String readFileText(File f) throws IOException {
        byte[] buf = new byte[(int) f.length()];
        try (FileInputStream fis = new FileInputStream(f)) {
            int r = 0; while (r < buf.length) { int n = fis.read(buf, r, buf.length - r); if (n < 0) break; r += n; }
        }
        return new String(buf, StandardCharsets.UTF_8);
    }

    // ── GSD mérése a futtatás ELŐTT (M09_L1 §11.3) ────────────────────────

    private void resetSessionGsd() {
        measuredGsdByFile.clear();
        if (tvGsdStatus != null)
            tvGsdStatus.setText("GSD: nincs mérve — a pontos detektáláshoz mérd meg");
    }

    /** Kép szélessége/magassága px-ben (inJustDecodeBounds); {0,0} ha nem olvasható. */
    private static int[] readImageDims(File f) {
        android.graphics.BitmapFactory.Options o = new android.graphics.BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        android.graphics.BitmapFactory.decodeFile(f.getAbsolutePath(), o);
        return new int[]{Math.max(0, o.outWidth), Math.max(0, o.outHeight)};
    }

    /** A mért képek GSD-átlaga (cm/px); 0 ha egyetlen kép sincs mérve. */
    private double averageMeasuredGsd() {
        if (measuredGsdByFile.isEmpty()) return 0;
        double sum = 0;
        for (double g : measuredGsdByFile.values()) sum += g;
        return sum / measuredGsdByFile.size();
    }

    /**
     * Egy kép effektív GSD-je (M09_L1 §11.4a, a felhasználó logikája szerint):
     * saját mérés, ha van; különben a mért képek átlaga; ha egyáltalán nincs
     * mérés: exifGsd fallback.
     */
    private double resolveGsdForFile(String localFile, double exifGsd) {
        Double own = measuredGsdByFile.get(localFile);
        if (own != null) return own;
        double avg = averageMeasuredGsd();
        return avg > 0 ? avg : exifGsd;
    }

    private void updateGsdStatus() {
        if (tvGsdStatus == null) return;
        int m = measuredGsdByFile.size();
        if (m == 0) {
            tvGsdStatus.setText("GSD: nincs mérve — EXIF-fallback (mérés ajánlott)");
        } else {
            tvGsdStatus.setText(String.format(Locale.getDefault(),
                    "GSD: %d kép mérve · a nem mértek a mért képek átlagát kapják (%.3f cm/px)",
                    m, averageMeasuredGsd()));
        }
    }

    /**
     * A session GSD-jének mérése egy reprezentatív mintaképen a futtatás ELŐTT
     * (M09_L1 §11.3). A mért GSD kettős célt szolgál: a detektálási resize-t (a
     * kép a betanítási GSD-re méretezve) és a footprintet (density).
     */
    private void openGsdMeasureDialog() {
        File session = selectedSession();
        if (session == null) {
            Toast.makeText(this, "Válassz sessiont", Toast.LENGTH_SHORT).show();
            return;
        }
        File[] photos = session.listFiles(f ->
                f.getName().toLowerCase(Locale.US).endsWith(".jpg"));
        if (photos == null || photos.length == 0) {
            Toast.makeText(this, "Nincs kép a sessionben", Toast.LENGTH_SHORT).show();
            return;
        }
        Arrays.sort(photos, (a, b) -> a.getName().compareTo(b.getName()));
        // Egyetlen kép → egyből mérés; több kép → válaszd ki a reprezentatívat.
        // A mérés MINDEN képre érvényes (session-szintű GSD, M09_L1 §11.3).
        if (photos.length == 1) {
            showGsdRuler(photos[0]);
            return;
        }
        final File[] fPhotos = photos;
        String[] names = new String[photos.length];
        for (int i = 0; i < photos.length; i++) names[i] = photos[i].getName();
        new AlertDialog.Builder(this)
                .setTitle("Melyik képen mérsz? (a GSD minden képre érvényes)")
                .setItems(names, (d, which) -> showGsdRuler(fPhotos[which]))
                .setNegativeButton("Mégse", null)
                .show();
    }

    /** A GSD-vonalzó megnyitása egy adott képen; a mért érték session-szintű. */
    private void showGsdRuler(final File photo) {
        android.graphics.BitmapFactory.Options bo = new android.graphics.BitmapFactory.Options();
        bo.inJustDecodeBounds = true;
        android.graphics.BitmapFactory.decodeFile(photo.getAbsolutePath(), bo);
        final int origW = bo.outWidth > 0 ? bo.outWidth : 5472;

        Bitmap bmp = YoloInferenceEngine.decodeDownsampled(photo, 1280);
        if (bmp == null) {
            Toast.makeText(this, "A kép nem olvasható", Toast.LENGTH_SHORT).show();
            return;
        }

        // EXIF-kereszt-ellenőrzéshez a session magasság + drón
        double exifFootprint = 0;
        double exifAltM = 0;
        DroneProfile drone = DroneProfiles.getDefault();
        try {
            SessionMeta sm = readSessionMeta(photo.getParentFile());
            exifAltM = sm.sampleAltitudeM;
            drone = resolveDrone(sm.droneProfileName);
            exifFootprint = GsdCalibration.exifFootprintAreaM2(exifAltM, drone);
        } catch (Exception ignored) {}
        final double fExifFootprint = exifFootprint;
        final double fExifAlt = exifAltM;
        final DroneProfile fDrone = drone;

        android.app.Dialog dialog = new android.app.Dialog(this,
                android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setBackgroundColor(0xFF000000);

        GsdRulerView ruler = new GsdRulerView(this);
        ruler.setBitmap(bmp);
        root.addView(ruler, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.MATCH_PARENT, 2f));

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundColor(0xFF12122a);
        panel.setPadding(24, 24, 24, 24);
        root.addView(panel, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.MATCH_PARENT, 1f));

        TextView title = new TextView(this);
        title.setText("📏 GSD mérése (futtatás előtt)");
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(18);
        panel.addView(title);

        TextView hint = new TextView(this);
        hint.setText("Húzd a vonalat egy ismert VÍZSZINTES talaj-távolságra (pl. sortáv), "
                + "a kép KÖZEPÉN. A mért GSD adja a detektálási méretezést ÉS a footprintet.");
        hint.setTextColor(0xFFAAAACC);
        hint.setTextSize(12);
        panel.addView(hint);

        LinearLayout refRow = new LinearLayout(this);
        refRow.setOrientation(LinearLayout.HORIZONTAL);
        refRow.setPadding(0, 16, 0, 0);
        final EditText etDist = new EditText(this);
        etDist.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        etDist.setText(lastRefDistanceCm);
        etDist.setTextColor(0xFFFFFFFF);
        addLabeled(refRow, "cm/egység", etDist);
        final EditText etCount = new EditText(this);
        etCount.setInputType(InputType.TYPE_CLASS_NUMBER);
        etCount.setText(lastRefUnitCount);
        etCount.setTextColor(0xFFFFFFFF);
        addLabeled(refRow, "× egység", etCount);
        panel.addView(refRow);

        final TextView live = new TextView(this);
        live.setTextColor(0xFF00FF88);
        live.setTextSize(13);
        live.setPadding(0, 16, 0, 16);
        panel.addView(live);

        Runnable update = () -> {
            double dist = parseD(etDist, 0) / 100.0;
            int count = (int) parseD(etCount, 1);
            double px = ruler.getLinePixelLength();
            GsdCalibration.Result res = GsdCalibration.compute(dist, count, px,
                    ruler.getBitmapWidth(), ruler.getImageAspect());
            double gsdCmPerOrigPx = res.footprintWidthM * 100.0 / origW;
            StringBuilder sb = new StringBuilder();
            sb.append(String.format(Locale.getDefault(), "Vonal: %.0f px\n", px));
            sb.append(String.format(Locale.getDefault(), "GSD: %.3f cm/px\n", gsdCmPerOrigPx));
            sb.append(String.format(Locale.getDefault(), "Footprint: %.2f × %.2f m = %.1f m²\n",
                    res.footprintWidthM, res.footprintHeightM, res.footprintAreaM2));
            if (fExifFootprint > 0 && res.footprintWidthM > 0) {
                double impliedAlt = GsdCalibration.impliedAltitudeM(res.footprintWidthM, fDrone);
                double pct = (res.footprintAreaM2 / fExifFootprint - 1.0) * 100.0;
                sb.append(String.format(Locale.getDefault(),
                        "EXIF: %.1f m → mérés: ~%.1f m (%+.0f%% terület)",
                        fExifAlt, impliedAlt, pct));
            }
            if (!ruler.isCentered()) sb.append("\n⚠ Mérj a kép közepén");
            live.setText(sb.toString());
        };
        ruler.setOnChangeListener(update::run);
        android.text.TextWatcher tw = new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { update.run(); }
            @Override public void afterTextChanged(android.text.Editable s) {}
        };
        etDist.addTextChangedListener(tw);
        etCount.addTextChangedListener(tw);
        ruler.post(update);

        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        Button btnApply = new Button(this);
        btnApply.setText("GSD beállítása");
        btnApply.setBackgroundColor(0xFFFF6600);
        btnApply.setTextColor(0xFFFFFFFF);
        Button btnCancel2 = new Button(this);
        btnCancel2.setText("Mégse");
        btnCancel2.setBackgroundColor(0xFF663333);
        btnCancel2.setTextColor(0xFFFFFFFF);
        btnRow.addView(btnApply);
        btnRow.addView(btnCancel2);
        panel.addView(btnRow);

        btnApply.setOnClickListener(v -> {
            double dist = parseD(etDist, 0) / 100.0;
            int count = (int) parseD(etCount, 1);
            double px = ruler.getLinePixelLength();
            GsdCalibration.Result res = GsdCalibration.compute(dist, count, px,
                    ruler.getBitmapWidth(), ruler.getImageAspect());
            if (res.footprintAreaM2 <= 0) {
                Toast.makeText(this, "Érvénytelen mérés", Toast.LENGTH_SHORT).show();
                return;
            }
            lastRefDistanceCm = etDist.getText().toString().trim();
            lastRefUnitCount = etCount.getText().toString().trim();
            double gsdCmPerOrigPx = res.footprintWidthM * 100.0 / origW;
            measuredGsdByFile.put(photo.getName(), gsdCmPerOrigPx);  // per-kép tárolás
            updateGsdStatus();
            dialog.dismiss();
        });
        btnCancel2.setOnClickListener(v -> dialog.dismiss());

        dialog.setContentView(root);
        dialog.setOnDismissListener(d -> bmp.recycle());
        dialog.show();
    }

    // ── GSD kalibrációs dialog (M09_L1 §10.7) ─────────────────────────────

    private void openCalibrationDialog(PointResult point, File photo) {
        Bitmap bmp = YoloInferenceEngine.decodeDownsampled(photo, 1280);
        if (bmp == null) {
            Toast.makeText(this, "A kép nem olvasható", Toast.LENGTH_SHORT).show();
            return;
        }

        android.app.Dialog dialog = new android.app.Dialog(this,
                android.R.style.Theme_Black_NoTitleBar_Fullscreen);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setBackgroundColor(0xFF000000);

        GsdRulerView ruler = new GsdRulerView(this);
        ruler.setBitmap(bmp);
        root.addView(ruler, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.MATCH_PARENT, 2f));

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundColor(0xFF12122a);
        panel.setPadding(24, 24, 24, 24);
        root.addView(panel, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.MATCH_PARENT, 1f));

        TextView title = new TextView(this);
        title.setText("📏 Skála kalibrálása");
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(18);
        panel.addView(title);

        TextView hint = new TextView(this);
        hint.setText("Húzd a vonalat egy ismert VÍZSZINTES talaj-távolságra (pl. sortáv), "
                + "a kép KÖZEPÉN. Hosszú, több sortávot átfogó vonal pontosabb.");
        hint.setTextColor(0xFFAAAACC);
        hint.setTextSize(12);
        panel.addView(hint);

        LinearLayout refRow = new LinearLayout(this);
        refRow.setOrientation(LinearLayout.HORIZONTAL);
        refRow.setPadding(0, 16, 0, 0);
        final EditText etDist = new EditText(this);
        etDist.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        etDist.setText(lastRefDistanceCm);
        etDist.setTextColor(0xFFFFFFFF);
        addLabeled(refRow, "cm/egység", etDist);
        final EditText etCount = new EditText(this);
        etCount.setInputType(InputType.TYPE_CLASS_NUMBER);
        etCount.setText(lastRefUnitCount);
        etCount.setTextColor(0xFFFFFFFF);
        addLabeled(refRow, "× egység", etCount);
        panel.addView(refRow);

        final TextView live = new TextView(this);
        live.setTextColor(0xFF00FF88);
        live.setTextSize(13);
        live.setPadding(0, 16, 0, 16);
        panel.addView(live);

        final double exifFootprint = GsdCalibration.exifFootprintAreaM2(
                lastMeta.sampleAltitudeM, lastDrone);

        Runnable update = () -> {
            double dist = parseD(etDist, 0) / 100.0;   // cm → m
            int count = (int) parseD(etCount, 1);
            double px = ruler.getLinePixelLength();
            GsdCalibration.Result res = GsdCalibration.compute(dist, count, px,
                    ruler.getBitmapWidth(), ruler.getImageAspect());
            StringBuilder sb = new StringBuilder();
            sb.append(String.format(Locale.getDefault(), "Vonal: %.0f px\n", px));
            sb.append(String.format(Locale.getDefault(), "GSD: %.3f cm/px\n", res.gsdMetersPerPx * 100));
            sb.append(String.format(Locale.getDefault(), "Footprint: %.2f × %.2f m = %.1f m²\n",
                    res.footprintWidthM, res.footprintHeightM, res.footprintAreaM2));
            if (exifFootprint > 0 && res.footprintWidthM > 0) {
                double impliedAlt = GsdCalibration.impliedAltitudeM(res.footprintWidthM, lastDrone);
                double pct = (res.footprintAreaM2 / exifFootprint - 1.0) * 100.0;
                sb.append(String.format(Locale.getDefault(),
                        "EXIF: %.1f m → mérés: ~%.1f m (%+.0f%% terület)",
                        lastMeta.sampleAltitudeM, impliedAlt, pct));
            }
            if (!ruler.isCentered()) {
                sb.append("\n⚠ A vonal a szélek felé csúszik — mérj a kép közepén");
            }
            live.setText(sb.toString());
        };
        ruler.setOnChangeListener(update::run);
        android.text.TextWatcher tw = new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { update.run(); }
            @Override public void afterTextChanged(android.text.Editable s) {}
        };
        etDist.addTextChangedListener(tw);
        etCount.addTextChangedListener(tw);
        ruler.post(update);

        // Gombok
        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        Button btnThis = new Button(this);
        btnThis.setText("Csak erre a képre");
        btnThis.setBackgroundColor(0xFFFF6600);
        btnThis.setTextColor(0xFFFFFFFF);
        Button btnAll = new Button(this);
        btnAll.setText("Minden képre");
        btnAll.setBackgroundColor(0xFF4444AA);
        btnAll.setTextColor(0xFFFFFFFF);
        Button btnCancel2 = new Button(this);
        btnCancel2.setText("Mégse");
        btnCancel2.setBackgroundColor(0xFF663333);
        btnCancel2.setTextColor(0xFFFFFFFF);
        btnRow.addView(btnThis);
        btnRow.addView(btnAll);
        btnRow.addView(btnCancel2);
        panel.addView(btnRow);

        View.OnClickListener apply = v -> {
            boolean all = (v == btnAll);
            double dist = parseD(etDist, 0) / 100.0;
            int count = (int) parseD(etCount, 1);
            double px = ruler.getLinePixelLength();
            GsdCalibration.Result res = GsdCalibration.compute(dist, count, px,
                    ruler.getBitmapWidth(), ruler.getImageAspect());
            if (res.footprintAreaM2 <= 0) {
                Toast.makeText(this, "Érvénytelen mérés", Toast.LENGTH_SHORT).show();
                return;
            }
            lastRefDistanceCm = etDist.getText().toString().trim();
            lastRefUnitCount = etCount.getText().toString().trim();

            point.footprintSource = FootprintSource.MANUAL;
            point.footprintAreaM2 = res.footprintAreaM2;
            point.gsdCmPx = res.gsdMetersPerPx * 100;
            point.refDistanceM = dist * Math.max(1, count);
            point.refPixelLength = px;

            if (all) {
                for (PointResult other : lastResult.perPoint) {
                    if (other == point) continue;
                    other.footprintSource = FootprintSource.INHERITED;
                    other.footprintAreaM2 = res.footprintAreaM2;
                }
            }
            dialog.dismiss();
            recomputeAfterCalibration();
        };
        btnThis.setOnClickListener(apply);
        btnAll.setOnClickListener(apply);
        btnCancel2.setOnClickListener(v -> dialog.dismiss());

        dialog.setContentView(root);
        dialog.setOnDismissListener(d -> bmp.recycle());
        dialog.show();
    }

    private void addLabeled(LinearLayout parent, String label, EditText field) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(0, 0, 16, 0);
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextColor(0xFFAAAACC);
        tv.setTextSize(11);
        col.addView(tv);
        col.addView(field);
        parent.addView(col, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
    }

    private static double parseD(EditText field, double fallback) {
        try {
            return Double.parseDouble(field.getText().toString().trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private void renderResult(SamplingCountResult r) {
        summaryCard.setVisibility(View.VISIBLE);
        btnExportCsv.setVisibility(View.VISIBLE);

        tvEstimatedTotal.setText(String.format(Locale.getDefault(),
                "Becsült összlétszám: %,.0f tő (± %,.0f, 95%% CI)",
                r.estimatedTotalCount, r.estimatedTotalCountCI95));

        StringBuilder detail = new StringBuilder();
        detail.append(String.format(Locale.getDefault(),
                "Átlagsűrűség: %,.0f tő/ha\nVariabilitás (CV): %.1f%%\nTerület: %.2f ha · %d mintapont"
                + "\nModell: %s · konf %.2f · IoU %.2f",
                r.meanDensityPerHa, r.cvPercent, r.totalAreaHa, r.sampleCount,
                r.modelUsed, r.confThresholdUsed, r.iouThresholdUsed));
        if (r.partial) {
            detail.append(String.format(Locale.getDefault(),
                    "\n⚠ Részleges eredmény (%d/%d pont feldolgozva)",
                    r.sampleCount, r.processedCount));
        }
        if (r.fpcApplied) {
            detail.append("\n(véges populáció korrekció alkalmazva)");
        }
        int calibrated = 0;
        for (PointResult p : r.perPoint) {
            if (p.footprintSource != null && p.footprintSource != FootprintSource.EXIF) calibrated++;
        }
        if (calibrated > 0) {
            detail.append(String.format(Locale.getDefault(),
                    "\n📏 Footprint: %d/%d pont kézi GSD-kalibrációval",
                    calibrated, r.perPoint.size()));
        }
        tvSummaryDetail.setText(detail.toString());

        if (r.cvPercent > 30) {
            tvCvWarning.setText(String.format(Locale.getDefault(),
                    "⚠ A tábla heterogén lehet (CV %.0f%%) — fontold meg a sűrűbb mintavételt "
                    + "vagy zónánkénti (stratifikált) elemzést.", r.cvPercent));
        } else {
            tvCvWarning.setText("");
        }

        tablePoints.removeAllViews();
        addTableRow("#", "GPS", "db", "db/ha", true, null);
        for (PointResult p : r.perPoint) {
            String gps = String.format(Locale.US, "%.5f, %.5f", p.lat, p.lon);
            String warn = p.warning != null ? " ⚠" : "";
            final File photo = new File(currentSessionDir, p.localFile != null
                    ? p.localFile
                    : String.format(Locale.US, "point_%03d.jpg", p.index));
            final PointResult point = p;
            // Bounding boxos előnézet (a GSD-mérés a futtatás ELŐTT történik,
            // M09_L1 §11 — az utólagos per-pont skálakalibráció megszűnt).
            addTableRow(String.valueOf(p.index + 1), gps, String.valueOf(p.count),
                    String.format(Locale.getDefault(), "%,.0f%s", p.densityPerHa, warn), false,
                    photo.exists()
                        ? v -> PhotoPreview.show(this, photo, point.detections)
                        : null);
        }
    }

    private void addTableRow(String col1, String col2, String col3, String col4, boolean header,
                             View.OnClickListener onClick) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(4, 4, 4, 4);
        if (onClick != null) row.setOnClickListener(onClick);
        int color = header ? 0xFFFFAA00 : 0xFFFFFFFF;
        int textSize = header ? 13 : 13;

        String[] cols = {col1, col2, col3, col4};
        float[] weights = {0.7f, 2f, 1f, 1.3f};
        for (int i = 0; i < cols.length; i++) {
            TextView tv = new TextView(this);
            tv.setText(cols[i]);
            tv.setTextColor(color);
            tv.setTextSize(textSize);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, weights[i]);
            tv.setLayoutParams(lp);
            row.addView(tv);
        }
        tablePoints.addView(row);
    }

    // ── CSV export ─────────────────────────────────────────────────────────

    private void exportCsv() {
        if (lastResult == null || currentSessionDir == null) return;
        StringBuilder sb = new StringBuilder("pont,lat,lon,db,db_per_ha,footprint_m2,warning\n");
        for (PointResult p : lastResult.perPoint) {
            sb.append(p.index + 1).append(',')
              .append(p.lat).append(',')
              .append(p.lon).append(',')
              .append(p.count).append(',')
              .append(String.format(Locale.US, "%.1f", p.densityPerHa)).append(',')
              .append(String.format(Locale.US, "%.2f", p.footprintAreaM2)).append(',')
              .append(p.warning != null ? p.warning.replace(",", ";") : "")
              .append('\n');
        }
        File csvFile = new File(currentSessionDir, "results_export.csv");
        try (FileOutputStream fos = new FileOutputStream(csvFile)) {
            fos.write(sb.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            Toast.makeText(this, "CSV export hiba: " + e.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }

        Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".provider", csvFile);
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("text/csv");
        share.putExtra(Intent.EXTRA_STREAM, uri);
        share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(share, "CSV küldése / mentése"));
    }

    // ── session.json meta olvasás ──────────────────────────────────────────

    private static class SessionMeta {
        double sampleAltitudeM;
        String droneProfileName;
        double aoiAreaM2;
    }

    private SessionMeta readSessionMeta(File sessionDir) throws JSONException, IOException {
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
        JSONObject root = new JSONObject(new String(buffer, StandardCharsets.UTF_8));
        SessionMeta meta = new SessionMeta();
        meta.sampleAltitudeM = root.optDouble("sample_altitude_m", 0);
        meta.droneProfileName = root.optString("drone_profile_name", "");
        meta.aoiAreaM2 = root.optDouble("aoi_area_m2", 0);
        return meta;
    }

    /** 0–1 tartományra korlátozott küszöbérték a mezőből; hibás bevitelnél a fallback. */
    private static float parseThreshold(EditText field, float fallback) {
        try {
            float v = Float.parseFloat(field.getText().toString().trim().replace(',', '.'));
            return Math.max(0f, Math.min(1f, v));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private DroneProfile resolveDrone(String name) {
        if (name != null && !name.isEmpty()) {
            for (DroneProfile d : DroneProfiles.ALL) {
                if (d.name.equals(name)) return d;
            }
        }
        return DroneProfiles.getDefault();
    }
}
