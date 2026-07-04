package com.dronefly.app;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.media.ExifInterface;
import android.os.Bundle;
import android.os.Environment;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.dronefly.app.ai.YoloInferenceEngine;
import com.dronefly.app.dji.MediaSessionDownloader;
import com.dronefly.app.mission.ProjectManager;
import com.dronefly.app.mission.SamplingMissionGenerator;
import com.dronefly.app.ui.PhotoPreview;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import dji.sdk.media.MediaFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * M09 — Fotóimport tőszámláláshoz, repüléstől függetlenül (M09_L1 §9).
 *
 * Két forrás:
 *  - Drón SD kártya: MediaManager fájllista, kiválasztott fájlok letöltése
 *  - Tablet tároló: beépített mappaböngésző (Crystal Sky-on nincs megbízható
 *    rendszer-fájlválasztó), kiválasztott JPG-k másolása
 *
 * Kötelező kontextus: a fotókhoz repülési tervet (mentett .flightprogram.json)
 * kell választani — ebből jön a tábla területe (polygon), a fotózási magasság
 * és a drónprofil, amik nélkül a footprint/extrapoláció nem számolható.
 * Alternatíva: kézi megadás (magasság + terület).
 *
 * Az import kimenete egy szabványos session mappa (sampling_sessions/import_*)
 * session.json-nal — a SamplingResultsActivity ugyanúgy dolgozza fel, mint a
 * missziós letöltéseket. A pontkoordináták a képek EXIF GPS adataiból jönnek.
 */
public class PhotoImportActivity extends AppCompatActivity {

    private static final String MANUAL_PLAN_LABEL = "— Kézi megadás —";

    private Button btnSourceDrone;
    private Button btnSourceTablet;
    private TextView tvSourceStatus;
    private ListView listPhotos;
    private Spinner spinnerFlightPlan;
    private TextView tvPlanInfo;
    private LinearLayout manualContextPanel;
    private EditText etManualAltitude;
    private EditText etManualAreaHa;
    private Button btnImport;
    private ProgressBar importProgress;
    private TextView tvImportStatus;

    private final MediaSessionDownloader downloader = new MediaSessionDownloader();

    private boolean droneSource = true;
    private File currentDir;
    private final List<PhotoRow> rows = new ArrayList<>();
    private PhotoRowAdapter adapter;

    private List<File> planFiles = new ArrayList<>();
    private ProjectManager.ProjectData selectedPlan;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_photo_import);

        btnSourceDrone = findViewById(R.id.btnSourceDrone);
        btnSourceTablet = findViewById(R.id.btnSourceTablet);
        tvSourceStatus = findViewById(R.id.tvSourceStatus);
        listPhotos = findViewById(R.id.listPhotos);
        spinnerFlightPlan = findViewById(R.id.spinnerFlightPlan);
        tvPlanInfo = findViewById(R.id.tvPlanInfo);
        manualContextPanel = findViewById(R.id.manualContextPanel);
        etManualAltitude = findViewById(R.id.etManualAltitude);
        etManualAreaHa = findViewById(R.id.etManualAreaHa);
        btnImport = findViewById(R.id.btnImport);
        importProgress = findViewById(R.id.importProgress);
        tvImportStatus = findViewById(R.id.tvImportStatus);

        adapter = new PhotoRowAdapter();
        listPhotos.setAdapter(adapter);
        listPhotos.setOnItemClickListener((parent, view, position, id) -> onRowClicked(position));

        btnSourceDrone.setOnClickListener(v -> switchSource(true));
        btnSourceTablet.setOnClickListener(v -> switchSource(false));
        btnImport.setOnClickListener(v -> onImportClicked());
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        loadFlightPlans();
        switchSource(true);
    }

    // ── Forrásváltás ───────────────────────────────────────────────────────

    private void switchSource(boolean drone) {
        droneSource = drone;
        btnSourceDrone.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(drone ? 0xFFFF6600 : 0xFF444466));
        btnSourceTablet.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(drone ? 0xFF444466 : 0xFFFF6600));
        rows.clear();
        adapter.notifyDataSetChanged();
        if (drone) {
            loadDronePhotos();
        } else {
            currentDir = Environment.getExternalStorageDirectory();
            loadTabletDir(currentDir);
        }
    }

    private void loadDronePhotos() {
        tvSourceStatus.setText("Drón SD kártya fájllistájának lekérése...");
        downloader.listSdCardPhotos(new MediaSessionDownloader.PhotoListListener() {
            @Override
            public void onPhotoList(List<MediaFile> photos) {
                runOnUiThread(() -> {
                    rows.clear();
                    SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);
                    for (MediaFile mf : photos) {
                        PhotoRow r = new PhotoRow();
                        r.mediaFile = mf;
                        r.label = mf.getFileName() + "   ("
                                + df.format(new Date(mf.getTimeCreated())) + ")";
                        rows.add(r);
                    }
                    adapter.notifyDataSetChanged();
                    tvSourceStatus.setText(photos.size() + " fotó a drón SD kártyáján"
                            + (photos.isEmpty() ? "" : " — jelöld ki az importálandókat"));
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> tvSourceStatus.setText("⚠ " + message));
            }
        });
    }

    private void loadTabletDir(File dir) {
        currentDir = dir;
        rows.clear();

        File parent = dir.getParentFile();
        if (parent != null && parent.canRead()) {
            PhotoRow up = new PhotoRow();
            up.dirFile = parent;
            up.label = "📁 ..";
            rows.add(up);
        }

        File[] children = dir.listFiles();
        List<PhotoRow> dirs = new ArrayList<>();
        List<PhotoRow> files = new ArrayList<>();
        if (children != null) {
            for (File f : children) {
                if (f.isDirectory()) {
                    PhotoRow r = new PhotoRow();
                    r.dirFile = f;
                    r.label = "📁 " + f.getName();
                    dirs.add(r);
                } else {
                    String name = f.getName().toLowerCase(Locale.US);
                    if (name.endsWith(".jpg") || name.endsWith(".jpeg")) {
                        PhotoRow r = new PhotoRow();
                        r.localFile = f;
                        r.label = f.getName();
                        files.add(r);
                    }
                }
            }
        }
        Comparator<PhotoRow> byLabel = (a, b) -> a.label.compareToIgnoreCase(b.label);
        Collections.sort(dirs, byLabel);
        Collections.sort(files, byLabel);
        rows.addAll(dirs);
        rows.addAll(files);
        adapter.notifyDataSetChanged();
        tvSourceStatus.setText(currentDir.getAbsolutePath() + " — " + files.size() + " JPG");
    }

    private void onRowClicked(int position) {
        PhotoRow r = rows.get(position);
        if (r.dirFile != null) {
            loadTabletDir(r.dirFile);
        } else {
            r.checked = !r.checked;
            adapter.notifyDataSetChanged();
        }
    }

    // ── Repülési terv kontextus ───────────────────────────────────────────

    private void loadFlightPlans() {
        planFiles = ProjectManager.listProjects(this);
        List<String> names = new ArrayList<>();
        for (File f : planFiles) {
            names.add(f.getName()
                    .replace(ProjectManager.FILE_EXT, "")
                    .replace(ProjectManager.LEGACY_EXT, ""));
        }
        names.add(MANUAL_PLAN_LABEL);

        ArrayAdapter<String> planAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, names);
        planAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerFlightPlan.setAdapter(planAdapter);

        spinnerFlightPlan.setOnItemSelectedListener(
                new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view,
                                       int position, long id) {
                onPlanSelected(position);
            }
            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
    }

    private void onPlanSelected(int position) {
        if (position >= planFiles.size()) {
            // Kézi megadás
            selectedPlan = null;
            manualContextPanel.setVisibility(View.VISIBLE);
            tvPlanInfo.setText("");
            return;
        }
        manualContextPanel.setVisibility(View.GONE);
        try {
            selectedPlan = ProjectManager.loadProject(planFiles.get(position));
            double areaHa = selectedPlan.polygon.size() >= 3
                    ? SamplingMissionGenerator.polygonAreaM2(selectedPlan.polygon) / 10000.0 : 0;
            double alt = contextAltitude(selectedPlan);
            tvPlanInfo.setText(String.format(Locale.getDefault(),
                    "%.2f ha · %.1f m · %s", areaHa, alt,
                    selectedPlan.droneProfileName.isEmpty()
                            ? "ismeretlen drón" : selectedPlan.droneProfileName));
        } catch (Exception e) {
            selectedPlan = null;
            tvPlanInfo.setText("⚠ Terv nem olvasható: " + e.getMessage());
        }
    }

    /**
     * A fotózási magasság a tervből: mintavételi tervnél a sample-magasság,
     * hagyományos (grid) tervnél a repülési magasság.
     */
    private double contextAltitude(ProjectManager.ProjectData plan) {
        return plan.samplingMode ? plan.sampleAltitudeM : plan.altitudeM;
    }

    // ── Import ─────────────────────────────────────────────────────────────

    private void onImportClicked() {
        List<PhotoRow> selected = new ArrayList<>();
        for (PhotoRow r : rows) {
            if (r.checked) selected.add(r);
        }
        if (selected.isEmpty()) {
            Toast.makeText(this, "Nincs kijelölt fotó", Toast.LENGTH_SHORT).show();
            return;
        }

        final double sampleAltitudeM;
        final double aoiAreaM2;
        final String droneProfileName;
        final String flightPlanName;

        if (selectedPlan != null) {
            if (selectedPlan.polygon.size() < 3) {
                Toast.makeText(this, "A kiválasztott terv nem tartalmaz polygont",
                        Toast.LENGTH_LONG).show();
                return;
            }
            sampleAltitudeM = contextAltitude(selectedPlan);
            aoiAreaM2 = SamplingMissionGenerator.polygonAreaM2(selectedPlan.polygon);
            droneProfileName = selectedPlan.droneProfileName;
            flightPlanName = selectedPlan.name;
        } else {
            try {
                sampleAltitudeM = Double.parseDouble(etManualAltitude.getText().toString().trim());
                aoiAreaM2 = Double.parseDouble(etManualAreaHa.getText().toString().trim()) * 10000.0;
            } catch (NumberFormatException e) {
                Toast.makeText(this, "Add meg a magasságot és a területet (kézi megadás)",
                        Toast.LENGTH_LONG).show();
                return;
            }
            droneProfileName = "";
            flightPlanName = null;
        }

        String sessionId = "import_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
                .format(new Date());
        File sessionDir = new File(MediaSessionDownloader.getSessionsRootDir(this), sessionId);
        if (!sessionDir.exists() && !sessionDir.mkdirs()) {
            Toast.makeText(this, "Session mappa létrehozási hiba", Toast.LENGTH_LONG).show();
            return;
        }

        btnImport.setEnabled(false);
        importProgress.setVisibility(View.VISIBLE);
        importProgress.setMax(selected.size());
        importProgress.setProgress(0);

        if (droneSource) {
            importFromDrone(selected, sessionDir, sessionId,
                    sampleAltitudeM, droneProfileName, aoiAreaM2, flightPlanName);
        } else {
            importFromTablet(selected, sessionDir, sessionId,
                    sampleAltitudeM, droneProfileName, aoiAreaM2, flightPlanName);
        }
    }

    private void importFromDrone(List<PhotoRow> selected, File sessionDir, String sessionId,
                                 double sampleAltitudeM, String droneProfileName,
                                 double aoiAreaM2, String flightPlanName) {
        List<MediaFile> mediaFiles = new ArrayList<>();
        for (PhotoRow r : selected) mediaFiles.add(r.mediaFile);

        tvImportStatus.setText("Letöltés a drónról...");
        downloader.downloadSelectedMedia(this, mediaFiles, sessionId,
                new MediaSessionDownloader.SessionDownloadListener() {
            @Override
            public void onFileProgress(int fileIndex, int totalFiles, long current, long total) {
                runOnUiThread(() -> importProgress.setProgress(fileIndex));
            }
            @Override
            public void onFileComplete(int fileIndex, String localPath) {
                runOnUiThread(() -> importProgress.setProgress(fileIndex + 1));
            }
            @Override
            public void onFileError(int fileIndex, String error) {
                runOnUiThread(() -> Toast.makeText(PhotoImportActivity.this,
                        (fileIndex + 1) + ". fotó hiba: " + error, Toast.LENGTH_SHORT).show());
            }
            @Override
            public void onSessionWarning(String message) {
                runOnUiThread(() -> Toast.makeText(PhotoImportActivity.this,
                        "⚠ " + message, Toast.LENGTH_LONG).show());
            }
            @Override
            public void onSessionComplete(int successCount, int totalCount, File dir) {
                runOnUiThread(() -> finalizeImport(dir, sessionId, "import_drone_sd",
                        sampleAltitudeM, droneProfileName, aoiAreaM2, flightPlanName));
            }
            @Override
            public void onSessionError(String message) {
                runOnUiThread(() -> {
                    btnImport.setEnabled(true);
                    importProgress.setVisibility(View.GONE);
                    new AlertDialog.Builder(PhotoImportActivity.this)
                            .setTitle("Letöltési hiba")
                            .setMessage(message)
                            .setPositiveButton("OK", null)
                            .show();
                });
            }
        });
    }

    private void importFromTablet(List<PhotoRow> selected, File sessionDir, String sessionId,
                                  double sampleAltitudeM, String droneProfileName,
                                  double aoiAreaM2, String flightPlanName) {
        tvImportStatus.setText("Másolás...");
        new Thread(() -> {
            int copied = 0;
            for (int i = 0; i < selected.size(); i++) {
                File src = selected.get(i).localFile;
                File dst = new File(sessionDir,
                        String.format(Locale.US, "point_%03d.jpg", i));
                try {
                    copyFile(src, dst);
                    copied++;
                } catch (IOException e) {
                    final int idx = i;
                    runOnUiThread(() -> Toast.makeText(this,
                            (idx + 1) + ". fotó másolási hiba: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show());
                }
                final int progress = i + 1;
                runOnUiThread(() -> importProgress.setProgress(progress));
            }
            final int copiedFinal = copied;
            runOnUiThread(() -> {
                if (copiedFinal == 0) {
                    btnImport.setEnabled(true);
                    importProgress.setVisibility(View.GONE);
                    Toast.makeText(this, "Egy fotót sem sikerült átmásolni",
                            Toast.LENGTH_LONG).show();
                    return;
                }
                finalizeImport(sessionDir, sessionId, "import_tablet",
                        sampleAltitudeM, droneProfileName, aoiAreaM2, flightPlanName);
            });
        }).start();
    }

    /**
     * Közös lezárás mindkét forrásnál: a session mappában lévő point_*.jpg
     * fájlok EXIF GPS adataiból pontlista, session.json írás, majd a
     * SamplingResultsActivity megnyitása az új sessionnel.
     */
    private void finalizeImport(File sessionDir, String sessionId, String source,
                                double sampleAltitudeM, String droneProfileName,
                                double aoiAreaM2, String flightPlanName) {
        tvImportStatus.setText("session.json írása...");
        File[] photos = sessionDir.listFiles(f ->
                f.getName().toLowerCase(Locale.US).endsWith(".jpg"));
        if (photos == null) photos = new File[0];
        List<File> sorted = new ArrayList<>();
        Collections.addAll(sorted, photos);
        Collections.sort(sorted, (a, b) -> a.getName().compareTo(b.getName()));

        try {
            JSONObject root = new JSONObject();
            root.put("session_id", sessionId);
            root.put("source", source);
            if (flightPlanName != null) root.put("flight_plan_name", flightPlanName);
            root.put("sample_count", sorted.size());
            root.put("downloaded_count", sorted.size());
            SimpleDateFormat isoFormat =
                    new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
            isoFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
            root.put("created_at", isoFormat.format(new Date()));
            root.put("sample_altitude_m", sampleAltitudeM);
            root.put("drone_profile_name", droneProfileName);
            root.put("aoi_area_m2", aoiAreaM2);

            JSONArray points = new JSONArray();
            for (int i = 0; i < sorted.size(); i++) {
                File photo = sorted.get(i);
                JSONObject p = new JSONObject();
                p.put("index", i);
                double[] latLon = readExifGps(photo);
                p.put("lat", latLon != null ? latLon[0] : 0.0);
                p.put("lon", latLon != null ? latLon[1] : 0.0);
                p.put("local_file", photo.getName());
                points.put(p);
            }
            root.put("points", points);

            File jsonFile = new File(sessionDir, "session.json");
            try (FileWriter writer = new FileWriter(jsonFile)) {
                writer.write(root.toString(2));
            }
        } catch (JSONException | IOException e) {
            btnImport.setEnabled(true);
            importProgress.setVisibility(View.GONE);
            Toast.makeText(this, "session.json írási hiba: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
            return;
        }

        Intent intent = new Intent(this, SamplingResultsActivity.class);
        intent.putExtra(SamplingResultsActivity.EXTRA_SESSION_ID, sessionId);
        // Ha az importot a SamplingResultsActivity-ből nyitottuk, a meglévő
        // példányhoz térünk vissza (CLEAR_TOP + singleTop), nem duplázzuk.
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
        finish();
    }

    /** EXIF GPS kiolvasás — null, ha a képben nincs geotag. */
    private double[] readExifGps(File photo) {
        try {
            ExifInterface exif = new ExifInterface(photo.getAbsolutePath());
            float[] latLon = new float[2];
            if (exif.getLatLong(latLon)) {
                return new double[]{latLon[0], latLon[1]};
            }
        } catch (IOException ignored) {}
        return null;
    }

    private static void copyFile(File src, File dst) throws IOException {
        byte[] buffer = new byte[64 * 1024];
        try (FileInputStream in = new FileInputStream(src);
             FileOutputStream out = new FileOutputStream(dst)) {
            int n;
            while ((n = in.read(buffer)) > 0) {
                out.write(buffer, 0, n);
            }
        }
    }

    // ── Lista sor + adapter ────────────────────────────────────────────────

    private static class PhotoRow {
        MediaFile mediaFile;   // drón forrás
        File localFile;        // tablet forrás (fájl)
        File dirFile;          // tablet forrás (mappa — navigáció, nem jelölhető)
        String label;
        boolean checked;
    }

    private final java.util.concurrent.ExecutorService thumbExecutor =
            java.util.concurrent.Executors.newSingleThreadExecutor();
    private final java.util.HashMap<String, Bitmap> thumbCache = new java.util.HashMap<>();

    private class PhotoRowAdapter extends BaseAdapter {
        @Override public int getCount() { return rows.size(); }
        @Override public Object getItem(int position) { return rows.get(position); }
        @Override public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            LinearLayout row;
            CheckBox check;
            ImageView thumb;
            TextView label;
            if (convertView instanceof LinearLayout) {
                row = (LinearLayout) convertView;
                check = (CheckBox) row.getChildAt(0);
                thumb = (ImageView) row.getChildAt(1);
                label = (TextView) row.getChildAt(2);
            } else {
                row = new LinearLayout(PhotoImportActivity.this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(8, 12, 8, 12);
                check = new CheckBox(PhotoImportActivity.this);
                check.setClickable(false);     // a sor-katt kezeli (onRowClicked)
                check.setFocusable(false);
                thumb = new ImageView(PhotoImportActivity.this);
                float density = getResources().getDisplayMetrics().density;
                LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                        (int) (72 * density), (int) (54 * density));
                tlp.rightMargin = (int) (8 * density);
                thumb.setLayoutParams(tlp);
                thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
                label = new TextView(PhotoImportActivity.this);
                label.setTextSize(14);
                row.addView(check);
                row.addView(thumb);
                row.addView(label);
            }
            PhotoRow r = rows.get(position);
            boolean isDir = r.dirFile != null;
            check.setVisibility(isDir ? View.GONE : View.VISIBLE);
            check.setChecked(r.checked);
            label.setText(r.label);
            label.setTextColor(isDir ? Color.parseColor("#FFAA00") : Color.WHITE);

            // Thumbnail csak tablet-forrású (helyi) fájlokhoz — a drón SD fájljai
            // letöltés előtt nem olvashatók. Koppintás a thumbnailre: nagyítás.
            if (r.localFile != null) {
                thumb.setVisibility(View.VISIBLE);
                final File photoFile = r.localFile;
                thumb.setOnClickListener(v -> PhotoPreview.show(PhotoImportActivity.this, photoFile));
                bindThumbnail(thumb, photoFile);
            } else {
                thumb.setVisibility(View.GONE);
                thumb.setOnClickListener(null);
            }
            return row;
        }
    }

    /** Aszinkron thumbnail-betöltés cache-sel; a tag védi az újrahasznosított sorokat. */
    private void bindThumbnail(ImageView iv, File photoFile) {
        String key = photoFile.getAbsolutePath();
        iv.setTag(key);
        Bitmap cached = thumbCache.get(key);
        if (cached != null) {
            iv.setImageBitmap(cached);
            return;
        }
        iv.setImageDrawable(null);
        thumbExecutor.execute(() -> {
            Bitmap bmp = YoloInferenceEngine.decodeDownsampled(photoFile, 144);
            if (bmp == null) return;
            runOnUiThread(() -> {
                if (thumbCache.size() > 200) thumbCache.clear(); // memória-plafon böngészéskor
                thumbCache.put(key, bmp);
                if (key.equals(iv.getTag())) iv.setImageBitmap(bmp);
            });
        });
    }
}
