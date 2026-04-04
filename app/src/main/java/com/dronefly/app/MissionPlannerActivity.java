package com.dronefly.app;

import android.content.Intent;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import com.dronefly.app.dji.DJIHelper;
import com.dronefly.app.dji.MissionUploader;

// DJI kapcsolat listener – valós idejű státusz frissítés
// (az Activity implements DJIHelper.ConnectionListener)
import com.dronefly.app.mission.CsvMissionParser;
import com.dronefly.app.mission.GridMissionGenerator;
import com.dronefly.app.mission.GsdCalculator;
import com.dronefly.app.mission.MissionExporter;
import com.dronefly.app.model.DroneProfile;
import com.dronefly.app.model.DroneProfiles;
import com.dronefly.app.model.MissionConfig;
import com.dronefly.app.model.WaypointData;

import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polygon;
import org.osmdroid.views.overlay.Polyline;
import org.osmdroid.views.overlay.gestures.RotationGestureOverlay;
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class MissionPlannerActivity extends AppCompatActivity {

    private static final int PICK_CSV_REQUEST = 101;

    // Térkép
    private MapView mapView;
    private MyLocationNewOverlay locationOverlay;
    private final List<GeoPoint> polygonPoints = new ArrayList<>();
    private Polygon  polygonOverlay;
    private Polyline missionOverlay;
    private Marker   startMarker;
    private GeoPoint startPoint = null;
    private boolean  drawMode = false;
    private boolean  startPointMode = false;

    // Beállítások widgetek
    private TextView tvGsd, tvSidelap, tvFrontlap, tvSpeed, tvAngle, tvStats;
    private SeekBar  sbGsd, sbSidelap, sbFrontlap, sbSpeed, sbAngle;
    private Spinner  spinnerDrone;
    private Button   btnDrawMode, btnClear, btnGenerate, btnUpload, btnStart,
                     btnImportCsv, btnExport, btnSetStart, btnMyLocation,
                     btnMapToggle, btnPauseMission, btnStopMission;
    private boolean  missionUploaded = false;
    private boolean  missionRunning = false;
    private boolean  missionPaused  = false;

    // Térképváltó
    private boolean  isSatelliteMap = true; // Google Satellite az alapértelmezett

    // DJI kapcsolat státusz
    private TextView tvDroneStatus;

    // Misszió állapot
    private GridMissionGenerator.GeneratorResult lastResult;
    private int currentSegmentIndex = 0;
    private final MissionUploader uploader = new MissionUploader();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // OSMDroid konfiguráció
        // SD kártya vagy external storage használata a cache-hez (nagyobb kapacitás)
        File extDir = getExternalFilesDir(null);
        File osmBase  = new File(extDir != null ? extDir : getCacheDir(), "osmdroid");
        File tileCache = new File(osmBase, "tiles");
        osmBase.mkdirs();
        tileCache.mkdirs();
        Configuration.getInstance().setOsmdroidBasePath(osmBase);
        Configuration.getInstance().setOsmdroidTileCache(tileCache);
        // Cache méret korlát: 100 MB (Crystal Sky védelmére)
        Configuration.getInstance().setTileFileSystemCacheMaxBytes(100L * 1024L * 1024L);
        Configuration.getInstance().setTileFileSystemCacheTrimBytes(80L * 1024L * 1024L);
        // Egyedi user agent – az OSM tile szerver megköveteli
        Configuration.getInstance().setUserAgentValue("DroneFlyApp/1.0 (Android dronefly.app)");

        setContentView(R.layout.activity_mission_planner);
        initMap();
        initControls();
    }

    // ── Térkép ─────────────────────────────────────────────────────────────

    private void initMap() {
        mapView = findViewById(R.id.mapView);
        // Próbáljuk HTTPS tile source-szal (néhány emulátor a HTTP-t blokkolja)
        // Google Satellite — megbízható, API kulcs nélkül
        mapView.setTileSource(buildGoogleSatelliteTileSource());
        mapView.setMultiTouchControls(true);
        mapView.setUseDataConnection(true);
        mapView.setBuiltInZoomControls(false);

        // Magyarország közép + jó zoom szint
        mapView.getController().setZoom(7.0);
        mapView.getController().setCenter(new GeoPoint(47.1, 19.5));
        mapView.postInvalidate();

        RotationGestureOverlay rotation = new RotationGestureOverlay(mapView);
        rotation.setEnabled(true);
        mapView.getOverlays().add(rotation);

        locationOverlay = new MyLocationNewOverlay(
                new GpsMyLocationProvider(this), mapView);
        locationOverlay.enableMyLocation();
        mapView.getOverlays().add(locationOverlay);

        // Érintésfigyelő
        mapView.getOverlays().add(new org.osmdroid.views.overlay.MapEventsOverlay(
                new org.osmdroid.events.MapEventsReceiver() {
            @Override
            public boolean singleTapConfirmedHelper(GeoPoint p) {
                if (startPointMode) {
                    setStartPoint(p);
                } else if (drawMode) {
                    addPolygonPoint(p);
                }
                return true;
            }
            @Override
            public boolean longPressHelper(GeoPoint p) {
                // Hosszú érintés = start/home pont beállítása
                setStartPoint(p);
                return true;
            }
        }));
    }

    // ── Vezérlők ───────────────────────────────────────────────────────────

    private void initControls() {
        tvGsd      = findViewById(R.id.tvGsd);
        tvSidelap  = findViewById(R.id.tvSidelap);
        tvFrontlap = findViewById(R.id.tvFrontlap);
        tvSpeed    = findViewById(R.id.tvSpeed);
        tvAngle    = findViewById(R.id.tvAngle);
        tvStats    = findViewById(R.id.tvStats);

        sbGsd      = findViewById(R.id.sbGsd);      // 0–95 → 0.5–10.0 cm/px
        sbSidelap  = findViewById(R.id.sbSidelap);  // 0–40 → 50–90%
        sbFrontlap = findViewById(R.id.sbFrontlap); // 0–30 → 60–90%
        sbSpeed    = findViewById(R.id.sbSpeed);    // 0–12 → 3–15 m/s
        sbAngle    = findViewById(R.id.sbAngle);    // 0–179°

        // Default: GSD 3.0 cm → progress = (3.0-0.5)/0.1 = 25
        sbGsd.setMax(95);
        sbGsd.setProgress(25);
        sbSidelap.setProgress(25);  // 75%
        sbFrontlap.setProgress(20); // 80%
        sbSpeed.setProgress(4);     // 7 m/s
        sbAngle.setProgress(0);

        SeekBar.OnSeekBarChangeListener listener = new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int p, boolean user) {
                updateLabels();
                // Ha GSD változik, frissítsük az ajánlott sebességet
                if (sb == sbGsd) {
                    float rec = GsdCalculator.recommendedSpeedMs(getGsd(), getSelectedDrone());
                    int speedProg = Math.round(rec) - 3;
                    sbSpeed.setProgress(Math.max(0, Math.min(12, speedProg)));
                }
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        };
        sbGsd.setOnSeekBarChangeListener(listener);
        sbSidelap.setOnSeekBarChangeListener(listener);
        sbFrontlap.setOnSeekBarChangeListener(listener);
        sbSpeed.setOnSeekBarChangeListener(listener);
        sbAngle.setOnSeekBarChangeListener(listener);

        btnDrawMode  = findViewById(R.id.btnDrawMode);
        btnClear     = findViewById(R.id.btnClear);
        btnGenerate  = findViewById(R.id.btnGenerate);
        btnUpload    = findViewById(R.id.btnUpload);
        btnStart     = findViewById(R.id.btnStart);
        btnImportCsv = findViewById(R.id.btnImportCsv);
        btnExport    = findViewById(R.id.btnExport);
        btnSetStart  = findViewById(R.id.btnSetStart);

        tvDroneStatus = findViewById(R.id.tvDroneStatus);

        btnMyLocation = findViewById(R.id.btnMyLocation);
        btnMyLocation.setOnClickListener(v -> jumpToCurrentPosition());

        btnMapToggle = findViewById(R.id.btnMapToggle);
        btnMapToggle.setOnClickListener(v -> toggleMapSource());

        // Drón spinner
        spinnerDrone = findViewById(R.id.spinnerDrone);
        ArrayAdapter<DroneProfile> droneAdapter = new ArrayAdapter<>(
            this, android.R.layout.simple_spinner_item, DroneProfiles.ALL);
        droneAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerDrone.setAdapter(droneAdapter);
        spinnerDrone.setSelection(0);
        // Ha módosítják a drónt és már van generált misszió, frissítjük a labeleket
        spinnerDrone.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, android.view.View view,
                                       int position, long id) {
                updateLabels();
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });

        btnPauseMission = findViewById(R.id.btnPauseMission);
        btnStopMission  = findViewById(R.id.btnStopMission);
        btnPauseMission.setOnClickListener(v -> togglePauseMission());
        btnStopMission.setOnClickListener(v -> confirmStopMission());

        btnDrawMode.setOnClickListener(v -> toggleDrawMode());
        btnClear.setOnClickListener(v -> clearAll());
        btnGenerate.setOnClickListener(v -> generateMission());
        btnUpload.setOnClickListener(v -> uploadCurrentSegment());
        btnStart.setOnClickListener(v -> startMission());
        btnImportCsv.setOnClickListener(v -> pickCsvFile());
        btnExport.setOnClickListener(v -> showExportDialog());
        btnSetStart.setOnClickListener(v -> toggleStartPointMode());

        btnUpload.setEnabled(false);
        btnStart.setEnabled(false);
        btnExport.setEnabled(false);
        updateLabels();
    }

    private DroneProfile getSelectedDrone() {
        if (spinnerDrone == null) return DroneProfiles.getDefault();
        int pos = spinnerDrone.getSelectedItemPosition();
        if (pos < 0 || pos >= DroneProfiles.ALL.size()) return DroneProfiles.getDefault();
        return DroneProfiles.ALL.get(pos);
    }

    private org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase buildGoogleSatelliteTileSource() {
        final String[] servers = {
            "https://mt0.google.com/vt/lyrs=s&",
            "https://mt1.google.com/vt/lyrs=s&",
            "https://mt2.google.com/vt/lyrs=s&",
            "https://mt3.google.com/vt/lyrs=s&"
        };
        // OnlineTileSourceBase direkt – a XYTileSource subclassnál bizonyos
        // verziókban nem hívódik meg az override, ez megbízhatóbb
        return new org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase(
                "GoogleSat", 1, 19, 256, ".jpg", servers) {
            @Override
            public String getTileURLString(long pMapTileIndex) {
                int zoom = (int)((pMapTileIndex >> 40) & 0xffff);
                int x    = (int)((pMapTileIndex >> 20) & 0xfffff);
                int y    = (int)(pMapTileIndex & 0xfffff);
                // Tile szerver váltogatás terhelosztáshoz
                String base = servers[(int)((x + y) % servers.length)];
                return base + "x=" + x + "&y=" + y + "&z=" + zoom;
            }
        };
    }

    private void toggleMapSource() {
        isSatelliteMap = !isSatelliteMap;
        if (isSatelliteMap) {
            // ESRI műhold
            mapView.setTileSource(buildGoogleSatelliteTileSource());
            mapView.getTileProvider().clearTileCache();
            btnMapToggle.setText("MAP");
            Toast.makeText(this, "Műholdas nézet", Toast.LENGTH_SHORT).show();
        } else {
            // OpenStreetMap utcatérkép
            org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase osm =
                new org.osmdroid.tileprovider.tilesource.XYTileSource(
                    "OpenStreetMap", 1, 18, 256, ".png",
                    new String[]{
                        "https://a.tile.openstreetmap.org/",
                        "https://b.tile.openstreetmap.org/",
                        "https://c.tile.openstreetmap.org/"
                    });
            mapView.setTileSource(osm);
            mapView.getTileProvider().clearTileCache();
            btnMapToggle.setText("SAT");
            Toast.makeText(this, "Utcatérkép nézet", Toast.LENGTH_SHORT).show();
        }
        mapView.postInvalidate();
    }

    private void updateLabels() {
        double gsd    = getGsd();
        DroneProfile drone = getSelectedDrone();
        double alt    = GsdCalculator.altitudeFromGsd(gsd, drone);
        double side   = getSidelap();
        double front  = getFrontlap();
        float  speed  = getSpeed();
        int    angle  = getAngle();
        float  recSpd = GsdCalculator.recommendedSpeedMs(gsd, drone);

        tvGsd.setText(String.format("GSD: %.1f cm/px  →  magasság: %.0f m  (ajánlott v: %.1f m/s)",
                gsd, alt, recSpd));
        tvSidelap.setText(String.format("Oldalsó átfedés: %.0f%%", side));
        tvFrontlap.setText(String.format("Menetirány átfedés: %.0f%%", front));
        tvSpeed.setText(String.format("Sebesség: %.0f m/s", speed));
        tvAngle.setText(String.format("Repülési irány: %d°", angle));
    }

    // ── Polygon rajzolás ───────────────────────────────────────────────────

    private void toggleDrawMode() {
        startPointMode = false;
        drawMode = !drawMode;
        btnDrawMode.setText(drawMode ? "Rajzolás KÉSZ" : "Terület rajzolása");
        btnSetStart.setAlpha(1.0f);
        if (!drawMode && polygonPoints.size() >= 3) {
            Toast.makeText(this, polygonPoints.size() + " pont – terület kész", Toast.LENGTH_SHORT).show();
        }
    }

    private void toggleStartPointMode() {
        drawMode = false;
        startPointMode = !startPointMode;
        btnSetStart.setText(startPointMode ? "Kattints a start pontra..." : "Start/Home pont");
        btnDrawMode.setText("Terület rajzolása");
        if (startPointMode)
            Toast.makeText(this, "Kattints a térképre a start/felszállási ponthoz", Toast.LENGTH_SHORT).show();
    }

    private void jumpToCurrentPosition() {
        // 1. Ha drón csatlakoztatva: drón GPS pozíciója (valódi eszközön)
        // 2. Fallback: készülék GPS (emulátor / kézi tesztelés)
        GeoPoint target = null;

        // Drón pozíció (MSDK – valódi eszközön aktív)
        // Ha a DJI stub le van cserélve valódi implementációra, itt jön a drón koordináta:
        // Aircraft aircraft = DJIHelper.getInstance().getAircraft();
        // if (aircraft != null) { ... aircraft.getFlightController().getState()... }

        // Készülék GPS fallback
        if (target == null && locationOverlay != null) {
            Location loc = locationOverlay.getLastFix();
            if (loc != null) {
                target = new GeoPoint(loc.getLatitude(), loc.getLongitude());
            }
        }

        if (target != null) {
            mapView.getController().animateTo(target);
            mapView.getController().setZoom(17.0);
        } else {
            Toast.makeText(this, "GPS pozíció még nem elérhető", Toast.LENGTH_SHORT).show();
        }
    }

    private void setStartPoint(GeoPoint p) {
        startPoint = p;
        startPointMode = false;
        btnSetStart.setText("Start/Home pont ✓");
        btnSetStart.setAlpha(1.0f);

        if (startMarker != null) mapView.getOverlays().remove(startMarker);
        startMarker = new Marker(mapView);
        startMarker.setPosition(p);
        startMarker.setTitle("Start / Home");
        startMarker.setSnippet(String.format("%.6f, %.6f", p.getLatitude(), p.getLongitude()));
        startMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        mapView.getOverlays().add(startMarker);
        mapView.invalidate();
        Toast.makeText(this, "Start pont beállítva", Toast.LENGTH_SHORT).show();
    }

    private void addPolygonPoint(GeoPoint p) {
        polygonPoints.add(p);
        refreshPolygonOverlay();
        Marker m = new Marker(mapView);
        m.setPosition(p);
        m.setTitle("P" + polygonPoints.size());
        m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
        mapView.getOverlays().add(m);
        mapView.invalidate();
    }

    private void refreshPolygonOverlay() {
        if (polygonOverlay != null) mapView.getOverlays().remove(polygonOverlay);
        if (polygonPoints.size() < 2) { mapView.invalidate(); return; }
        polygonOverlay = new Polygon();
        polygonOverlay.setPoints(polygonPoints);
        polygonOverlay.getFillPaint().setColor(0x330000FF);
        polygonOverlay.getOutlinePaint().setColor(0xFF0000FF);
        polygonOverlay.getOutlinePaint().setStrokeWidth(3f);
        mapView.getOverlays().add(0, polygonOverlay);
        mapView.invalidate();
    }

    private void clearAll() {
        polygonPoints.clear();
        if (polygonOverlay != null) mapView.getOverlays().remove(polygonOverlay);
        if (missionOverlay != null) mapView.getOverlays().remove(missionOverlay);
        if (startMarker != null)    mapView.getOverlays().remove(startMarker);
        startPoint = null;
        // Sarokpontok törlése
        mapView.getOverlays().removeIf(o -> o instanceof Marker);
        lastResult = null;
        missionUploaded = false;
        btnUpload.setEnabled(false);
        btnStart.setEnabled(false);
        btnExport.setEnabled(false);
        btnSetStart.setText("Start/Home pont");
        tvStats.setText("");
        mapView.invalidate();
    }

    // ── Misszió generálás ──────────────────────────────────────────────────

    private void generateMission() {
        if (polygonPoints.size() < 3) {
            Toast.makeText(this, "Rajzolj legalább 3 pontot a területhez!", Toast.LENGTH_SHORT).show();
            return;
        }
        MissionConfig config = buildConfig();
        lastResult = GridMissionGenerator.generate(polygonPoints, config);

        if (lastResult.errorMessage != null) {
            Toast.makeText(this, lastResult.errorMessage, Toast.LENGTH_LONG).show();
            return;
        }

        drawMissionPath(lastResult.segments);
        currentSegmentIndex = 0;
        btnUpload.setEnabled(true);
        btnExport.setEnabled(true);

        double recSpd = GsdCalculator.recommendedSpeedMs(config.gsdCm);
        String stats = String.format(
            "Terület: %.2f ha | Waypoint: %d (%d szegmens)\n" +
            "Magasság: %.0f m | Sávköz: %.1f m | Fotótáv: %.1f m\n" +
            "Sebesség: %.1f m/s (ajánlott: %.1f m/s) | Idő: ~%.0f perc",
            lastResult.areaM2 / 10000.0,
            lastResult.totalWaypoints, lastResult.segments.size(),
            lastResult.altitudeM, lastResult.stripSpacingM, lastResult.photoDistM,
            (double) config.speedMs, recSpd,
            lastResult.estimatedMinutes);
        tvStats.setText(stats);
    }

    private void drawMissionPath(List<List<WaypointData>> segments) {
        if (missionOverlay != null) mapView.getOverlays().remove(missionOverlay);
        List<GeoPoint> path = new ArrayList<>();
        if (startPoint != null) path.add(startPoint);
        for (List<WaypointData> seg : segments)
            for (WaypointData wp : seg)
                path.add(new GeoPoint(wp.latitude, wp.longitude));
        if (startPoint != null) path.add(startPoint); // visszatérés

        missionOverlay = new Polyline();
        missionOverlay.setPoints(path);
        missionOverlay.getOutlinePaint().setColor(0xFFFF6600);
        missionOverlay.getOutlinePaint().setStrokeWidth(2f);
        mapView.getOverlays().add(missionOverlay);
        mapView.invalidate();
    }

    // ── Export ─────────────────────────────────────────────────────────────

    private void showExportDialog() {
        if (lastResult == null || lastResult.segments.isEmpty()) return;
        new AlertDialog.Builder(this)
            .setTitle("Exportálás")
            .setItems(new String[]{"Litchi CSV (webes app)", "KMZ (Google Earth / DJI Pilot)"}, (d, which) -> {
                if (which == 0) exportCsv();
                else            exportKmz();
            })
            .show();
    }

    private void exportCsv() {
        try {
            List<WaypointData> all = new ArrayList<>();
            for (List<WaypointData> seg : lastResult.segments) all.addAll(seg);
            String csv = MissionExporter.toLitchiCsv(all, getSpeed());
            File file = new File(getExternalFilesDir(null), "dronefly_mission.csv");
            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(csv.getBytes("UTF-8"));
            }
            shareFile(file, "text/csv");
        } catch (IOException e) {
            Toast.makeText(this, "CSV export hiba: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void exportKmz() {
        try {
            List<WaypointData> all = new ArrayList<>();
            for (List<WaypointData> seg : lastResult.segments) all.addAll(seg);
            byte[] kmz = MissionExporter.toKmz(all, "DroneFly misszió");
            File file = new File(getExternalFilesDir(null), "dronefly_mission.kmz");
            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(kmz);
            }
            shareFile(file, "application/vnd.google-earth.kmz");
        } catch (IOException e) {
            Toast.makeText(this, "KMZ export hiba: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void shareFile(File file, String mimeType) {
        Uri uri = FileProvider.getUriForFile(this,
                getPackageName() + ".provider", file);
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType(mimeType);
        share.putExtra(Intent.EXTRA_STREAM, uri);
        share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(share, "Küldés / Mentés"));
    }

    // ── Feltöltés ──────────────────────────────────────────────────────────

    private void uploadCurrentSegment() {
        if (lastResult == null || lastResult.segments.isEmpty()) return;
        if (!DJIHelper.getInstance().isConnected()) {
            Toast.makeText(this, "Nincs csatlakoztatott drón", Toast.LENGTH_SHORT).show();
            return;
        }
        List<WaypointData> segment = lastResult.segments.get(currentSegmentIndex);
        int total = lastResult.segments.size();
        String msg = total > 1
            ? String.format("Szegmens %d/%d feltöltése (%d waypoint)?",
                currentSegmentIndex + 1, total, segment.size())
            : String.format("%d waypoint feltöltése a drónra?", segment.size());

        new AlertDialog.Builder(this)
            .setTitle("Misszió feltöltése")
            .setMessage(msg)
            .setPositiveButton("Feltöltés", (d, w) -> doUpload(segment))
            .setNegativeButton("Mégse", null)
            .show();
    }

    private void doUpload(List<WaypointData> segment) {
        btnUpload.setEnabled(false);
        btnStart.setEnabled(false);
        missionUploaded = false;
        Toast.makeText(this, "Feltöltés folyamatban...", Toast.LENGTH_SHORT).show();
        uploader.uploadMission(segment, buildConfig(), new MissionUploader.UploadCallback() {
            @Override public void onSuccess() {
                runOnUiThread(() -> {
                    missionUploaded = true;
                    btnStart.setEnabled(true);
                    Toast.makeText(MissionPlannerActivity.this,
                        "Feltöltve! Ellenőrizd a drónt, majd nyomj START-ot.",
                        Toast.LENGTH_LONG).show();
                });
            }
            @Override public void onError(String msg) {
                runOnUiThread(() -> {
                    btnUpload.setEnabled(true);
                    Toast.makeText(MissionPlannerActivity.this,
                        "Feltöltési hiba: " + msg, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void startMission() {
        if (!missionUploaded) return;
        new AlertDialog.Builder(this)
            .setTitle("Repülés indítása")
            .setMessage("A drón elindítja a missziót. Biztos vagy benne?")
            .setPositiveButton("START", (d, w) -> {
                uploader.startMission(new MissionUploader.UploadCallback() {
                    @Override public void onSuccess() {
                        runOnUiThread(() -> {
                            Toast.makeText(MissionPlannerActivity.this,
                                "Repülés elindítva!", Toast.LENGTH_LONG).show();
                            setMissionRunning(true);
                        });
                    }
                    @Override public void onError(String msg) {
                        runOnUiThread(() -> Toast.makeText(MissionPlannerActivity.this,
                            "Indítás hiba: " + msg, Toast.LENGTH_LONG).show());
                    }
                });
            })
            .setNegativeButton("Mégse", null)
            .show();
    }

    private void setMissionRunning(boolean running) {
        missionRunning  = running;
        missionPaused   = false;
        missionUploaded = false;
        btnPauseMission.setEnabled(running);
        btnStopMission.setEnabled(running);
        btnPauseMission.setText("⏸ Szünet");
        btnUpload.setEnabled(!running && lastResult != null);
        btnStart.setEnabled(false);
    }

    private void togglePauseMission() {
        if (!missionPaused) {
            uploader.pauseMission(new MissionUploader.UploadCallback() {
                @Override public void onSuccess() {
                    runOnUiThread(() -> {
                        missionPaused = true;
                        btnPauseMission.setText("▶ Folytatás");
                        Toast.makeText(MissionPlannerActivity.this,
                            "Misszió szüneteltetve – drón lebeg", Toast.LENGTH_SHORT).show();
                    });
                }
                @Override public void onError(String msg) {
                    runOnUiThread(() -> Toast.makeText(MissionPlannerActivity.this,
                        "Szünet hiba: " + msg, Toast.LENGTH_SHORT).show());
                }
            });
        } else {
            uploader.resumeMission(new MissionUploader.UploadCallback() {
                @Override public void onSuccess() {
                    runOnUiThread(() -> {
                        missionPaused = false;
                        btnPauseMission.setText("⏸ Szünet");
                        Toast.makeText(MissionPlannerActivity.this,
                            "Misszió folytatva", Toast.LENGTH_SHORT).show();
                    });
                }
                @Override public void onError(String msg) {
                    runOnUiThread(() -> Toast.makeText(MissionPlannerActivity.this,
                        "Folytatás hiba: " + msg, Toast.LENGTH_SHORT).show());
                }
            });
        }
    }

    private void confirmStopMission() {
        new AlertDialog.Builder(this)
            .setTitle("Misszió leállítása")
            .setMessage("Leállítod a missziót? A drón az aktuális pozícióban lebeg, kézi vezérlés átvehető.")
            .setPositiveButton("Stop", (d, w) -> {
                uploader.stopMission(new MissionUploader.UploadCallback() {
                    @Override public void onSuccess() {
                        runOnUiThread(() -> {
                            setMissionRunning(false);
                            Toast.makeText(MissionPlannerActivity.this,
                                "Misszió leállítva – kézi vezérlés átvehető", Toast.LENGTH_LONG).show();
                        });
                    }
                    @Override public void onError(String msg) {
                        runOnUiThread(() -> Toast.makeText(MissionPlannerActivity.this,
                            "Stop hiba: " + msg, Toast.LENGTH_SHORT).show());
                    }
                });
            })
            .setNegativeButton("Mégse", null)
            .show();
    }

    // ── CSV Import ─────────────────────────────────────────────────────────

    private void pickCsvFile() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(intent, PICK_CSV_REQUEST);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_CSV_REQUEST && resultCode == RESULT_OK && data != null) {
            try (InputStream is = getContentResolver().openInputStream(data.getData())) {
                CsvMissionParser.ParseResult parsed = CsvMissionParser.parse(is);
                if (parsed.errorMessage != null) {
                    Toast.makeText(this, parsed.errorMessage, Toast.LENGTH_LONG).show();
                    return;
                }
                showImportedWaypoints(parsed.waypoints);
            } catch (IOException e) {
                Toast.makeText(this, "Fájl megnyitási hiba", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void showImportedWaypoints(List<WaypointData> waypoints) {
        clearAll();
        List<GeoPoint> path = new ArrayList<>();
        for (WaypointData wp : waypoints)
            path.add(new GeoPoint(wp.latitude, wp.longitude));

        missionOverlay = new Polyline();
        missionOverlay.setPoints(path);
        missionOverlay.getOutlinePaint().setColor(0xFF00AA00);
        missionOverlay.getOutlinePaint().setStrokeWidth(2f);
        mapView.getOverlays().add(missionOverlay);
        if (!path.isEmpty()) {
            mapView.getController().setCenter(path.get(0));
            mapView.getController().setZoom(16.0);
        }
        mapView.invalidate();

        lastResult = new GridMissionGenerator.GeneratorResult();
        lastResult.segments.add(waypoints);
        lastResult.totalWaypoints = waypoints.size();
        currentSegmentIndex = 0;
        btnUpload.setEnabled(true);
        btnExport.setEnabled(true);
        tvStats.setText(String.format("Importált: %d waypoint", waypoints.size()));
        Toast.makeText(this, waypoints.size() + " waypoint importálva", Toast.LENGTH_SHORT).show();
    }

    // ── Segédek ────────────────────────────────────────────────────────────

    private MissionConfig buildConfig() {
        MissionConfig c = new MissionConfig();
        c.droneProfile    = getSelectedDrone();
        c.gsdCm           = getGsd();
        c.altitudeM       = GsdCalculator.altitudeFromGsd(c.gsdCm, c.droneProfile);
        c.sidelapPercent  = getSidelap();
        c.frontlapPercent = getFrontlap();
        c.speedMs         = getSpeed();
        c.flightAngleDeg  = getAngle();
        return c;
    }

    private double getGsd()      { return 0.5 + sbGsd.getProgress() * 0.1; }
    private double getSidelap()  { return 50.0 + sbSidelap.getProgress(); }
    private double getFrontlap() { return 60.0 + sbFrontlap.getProgress(); }
    private float  getSpeed()    { return 3.0f + sbSpeed.getProgress(); }
    private int    getAngle()    { return sbAngle.getProgress(); }

    @Override
    protected void onResume() {
        super.onResume();
        mapView.onResume();
        // Valós idejű kapcsolat visszajelzés
        try {
            DJIHelper.getInstance().setListener(new DJIHelper.ConnectionListener() {
                @Override public void onRegistered(boolean success, String message) {
                    runOnUiThread(() -> updateDroneStatus());
                }
                @Override public void onProductConnected(String productName) {
                    runOnUiThread(() -> updateDroneStatus());
                }
                @Override public void onProductDisconnected() {
                    runOnUiThread(() -> updateDroneStatus());
                }
            });
        } catch (Throwable t) { /* DJI SDK nem elérhető */ }
        updateDroneStatus();
    }

    @Override protected void onPause()   { super.onPause();   mapView.onPause(); }
    @Override protected void onDestroy() { super.onDestroy(); mapView.onDetach(); }

    private void updateDroneStatus() {
        if (tvDroneStatus == null) return;
        try {
            DJIHelper helper = DJIHelper.getInstance();
            if (helper.isConnected()) {
                String name = helper.getConnectedProductName();
                tvDroneStatus.setText("● Csatlakoztatva: " + (name != null ? name : "drón"));
                tvDroneStatus.setTextColor(0xFF44FF44);
                btnUpload.setEnabled(lastResult != null && !missionRunning);
            } else if (helper.isRegistered()) {
                tvDroneStatus.setText("● SDK regisztrálva – drón keresése...");
                tvDroneStatus.setTextColor(0xFFFFAA00);
                btnUpload.setEnabled(false);
                btnStart.setEnabled(false);
            } else {
                tvDroneStatus.setText("● Drón: nem csatlakoztatva");
                tvDroneStatus.setTextColor(0xFFFF4444);
                btnUpload.setEnabled(false);
                btnStart.setEnabled(false);
            }
        } catch (Throwable t) {
            tvDroneStatus.setText("● DJI SDK nem elérhető");
            tvDroneStatus.setTextColor(0xFF888888);
            btnUpload.setEnabled(false);
            btnStart.setEnabled(false);
        }
    }
}
