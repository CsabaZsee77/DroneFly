package com.dronefly.app;

import android.animation.ObjectAnimator;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.location.Location;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.text.InputType;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import com.dronefly.app.dji.CameraConfigurator;
import com.dronefly.app.dji.DJIHelper;
import com.dronefly.app.dji.MissionUploader;
import com.dronefly.app.model.CameraSettings;

// DJI kapcsolat listener – valós idejű státusz frissítés
// (az Activity implements DJIHelper.ConnectionListener)
import com.dronefly.app.mission.CsvMissionParser;
import com.dronefly.app.mission.ElevationProvider;
import com.dronefly.app.mission.GridMissionGenerator;
import com.dronefly.app.mission.GsdCalculator;
import com.dronefly.app.mission.MissionExporter;
import com.dronefly.app.model.DroneProfile;
import com.dronefly.app.model.DroneProfiles;
import com.dronefly.app.model.MissionConfig;
import com.dronefly.app.model.ObstacleData;
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
    private final List<GeoPoint> polygonPoints  = new ArrayList<>();
    private final List<Marker>   polygonMarkers = new ArrayList<>(); // külön nyilvántartás, removeIf kiváltása
    private Polygon  polygonOverlay;
    private Polyline missionOverlay;
    private Marker   startMarker;
    private GeoPoint startPoint = null;
    private boolean  startPointMode = false;

    // Beállítások widgetek
    private TextView tvGsd, tvSidelap, tvFrontlap, tvSpeed, tvAngle, tvOffset, tvStats;
    private SeekBar  sbGsd, sbSidelap, sbFrontlap, sbSpeed, sbAngle, sbOffset;
    private Spinner  spinnerDrone;
    private Button   btnUndoPoint, btnClear, btnGenerate, btnUpload, btnStart,
                     btnImportCsv, btnExport, btnSetStart, btnMyLocation,
                     btnMapToggle, btnPauseMission, btnStopMission;
    private boolean  missionUploaded = false;
    private boolean  missionRunning = false;
    private boolean  missionPaused  = false;

    // Kamera beállítások
    private LinearLayout cameraSettingsBody, cameraManualControls;
    private Switch switchCameraAuto;
    private Spinner spinnerPhotoMode, spinnerIso, spinnerShutter, spinnerWhiteBalance, spinnerFileFormat;
    private TextView tvCameraExpand;
    private boolean cameraExpanded = false;

    // Domborzatkövetés
    private Switch switchTerrain;
    private TextView tvTerrainInfo;

    // Panel csúsztatás
    private android.widget.FrameLayout sidePanelContainer;
    private ScrollView sidePanel;
    private Button btnTogglePanel;
    private boolean panelVisible = true;

    // Akadályok
    private final List<ObstacleData> obstacleList    = new ArrayList<>();
    private final List<Marker>       obstacleMarkers = new ArrayList<>();
    private final List<Polygon>      obstacleOverlays = new ArrayList<>();
    private boolean obstacleMode = false;
    private Button  btnObstacle, btnClearObstacles;

    // Státuszsáv
    private TextView sbDrone, sbRc, sbRcBatt, sbGps, sbDroneBatt, sbTabletBatt;
    private final Handler statusHandler = new Handler(Looper.getMainLooper());
    private static final int STATUS_INTERVAL_MS = 2000;

    private static final float MAX_ALTITUDE_LEGAL = 120f; // EU Open kategória limit (m)

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
        showDisclaimerIfNeeded();
    }

    // ── Térkép ─────────────────────────────────────────────────────────────

    private void initMap() {
        mapView = findViewById(R.id.mapView);
        // Próbáljuk HTTPS tile source-szal (néhány emulátor a HTTP-t blokkolja)
        // Google Satellite — megbízható, API kulcs nélkül
        mapView.setTileSource(buildSatelliteTileSource());
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
                } else if (obstacleMode) {
                    showAddObstacleDialog(p);
                } else if (!missionRunning) {
                    // Mindig aktív pontlerakás (kivéve ha misszió fut)
                    addPolygonPoint(p);
                }
                return true;
            }
            @Override
            public boolean longPressHelper(GeoPoint p) {
                // Hosszú érintés a térképen: nem csinál semmit.
                // A marker drag az OSMDroid saját long-press mechanizmusa,
                // azt NEM szabad itt elkapni – különben ütközik a szerkesztéssel.
                // Start pontot csak a dedikált gombbal lehet beállítani.
                return false;
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
        tvOffset   = findViewById(R.id.tvOffset);
        tvStats    = findViewById(R.id.tvStats);

        sbGsd      = findViewById(R.id.sbGsd);      // 0–95 → 0.5–10.0 cm/px
        sbSidelap  = findViewById(R.id.sbSidelap);  // 0–40 → 50–90%
        sbFrontlap = findViewById(R.id.sbFrontlap); // 0–30 → 60–90%
        sbSpeed    = findViewById(R.id.sbSpeed);    // 0–12 → 3–15 m/s
        sbAngle    = findViewById(R.id.sbAngle);    // 0–179°
        sbOffset   = findViewById(R.id.sbOffset);  // 0–30 m

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
        sbOffset.setOnSeekBarChangeListener(listener);

        btnUndoPoint      = findViewById(R.id.btnUndoPoint);
        btnObstacle       = findViewById(R.id.btnObstacle);
        btnClearObstacles = findViewById(R.id.btnClearObstacles);
        btnClear          = findViewById(R.id.btnClear);
        btnGenerate       = findViewById(R.id.btnGenerate);
        btnUpload         = findViewById(R.id.btnUpload);
        btnStart          = findViewById(R.id.btnStart);
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

        // Panel csúsztatás
        sidePanelContainer = findViewById(R.id.sidePanelContainer);
        sidePanel          = findViewById(R.id.sidePanel);
        btnTogglePanel     = findViewById(R.id.btnTogglePanel);
        btnTogglePanel.setOnClickListener(v -> toggleSidePanel());

        btnPauseMission = findViewById(R.id.btnPauseMission);
        btnStopMission  = findViewById(R.id.btnStopMission);
        btnPauseMission.setOnClickListener(v -> togglePauseMission());
        btnStopMission.setOnClickListener(v -> confirmStopMission());

        btnUndoPoint.setOnClickListener(v -> removeLastPolygonPoint());
        btnObstacle.setOnClickListener(v -> toggleObstacleMode());
        btnClearObstacles.setOnClickListener(v -> clearAllObstacles());
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
        initCameraControls();
        initTerrainControls();
        initStatusBar();
        updateLabels();
    }

    // ── Kamera beállítások panel ───────────────────────────────────────

    private void initCameraControls() {
        cameraSettingsBody   = findViewById(R.id.cameraSettingsBody);
        cameraManualControls = findViewById(R.id.cameraManualControls);
        switchCameraAuto     = findViewById(R.id.switchCameraAuto);
        tvCameraExpand       = findViewById(R.id.tvCameraExpand);

        spinnerPhotoMode     = findViewById(R.id.spinnerPhotoMode);
        spinnerIso           = findViewById(R.id.spinnerIso);
        spinnerShutter       = findViewById(R.id.spinnerShutter);
        spinnerWhiteBalance  = findViewById(R.id.spinnerWhiteBalance);
        spinnerFileFormat    = findViewById(R.id.spinnerFileFormat);

        // Összecsukás/kinyitás
        findViewById(R.id.cameraHeader).setOnClickListener(v -> toggleCameraPanel());

        // Auto/Manuális kapcsoló
        switchCameraAuto.setOnCheckedChangeListener((btn, isChecked) -> {
            switchCameraAuto.setText(isChecked ? "Auto" : "Manuális");
            cameraManualControls.setVisibility(isChecked ? View.GONE : View.VISIBLE);
        });

        // Spinnerek feltöltése
        spinnerFileFormat.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, CameraSettings.FileFormat.values()));
        spinnerFileFormat.setSelection(2); // JPEG+RAW

        spinnerPhotoMode.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, CameraSettings.PhotoMode.values()));

        spinnerIso.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, CameraSettings.IsoValue.values()));

        spinnerShutter.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, CameraSettings.ShutterSpeed.values()));

        spinnerWhiteBalance.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, CameraSettings.WhiteBalanceValue.values()));
    }

    private void toggleCameraPanel() {
        cameraExpanded = !cameraExpanded;
        cameraSettingsBody.setVisibility(cameraExpanded ? View.VISIBLE : View.GONE);
        tvCameraExpand.setText(cameraExpanded ? "\u25B2" : "\u25BC");
    }

    // ── Státuszsáv ────────────────────────────────────────────────────

    private void initStatusBar() {
        sbDrone     = findViewById(R.id.sbDrone);
        sbRc        = findViewById(R.id.sbRc);
        sbRcBatt    = findViewById(R.id.sbRcBatt);
        sbGps       = findViewById(R.id.sbGps);
        sbDroneBatt = findViewById(R.id.sbDroneBatt);
        sbTabletBatt= findViewById(R.id.sbTabletBatt);
        updateStatusBar();
    }

    private final Runnable statusRunnable = new Runnable() {
        @Override public void run() {
            updateStatusBar();
            statusHandler.postDelayed(this, STATUS_INTERVAL_MS);
        }
    };

    private void updateStatusBar() {
        if (sbDrone == null) return;

        // ── Tablet akkumulátor (Android BatteryManager) ──
        try {
            Intent bi = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (bi != null) {
                int lvl   = bi.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = bi.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                int pct   = (scale > 0) ? (lvl * 100 / scale) : -1;
                boolean charging = bi.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                        == BatteryManager.BATTERY_STATUS_CHARGING;
                if (pct >= 0) {
                    sbTabletBatt.setText("TAB: " + pct + "%" + (charging ? "+" : ""));
                    sbTabletBatt.setTextColor(pct < 20 ? 0xFFFF4444 : pct < 40 ? 0xFFFFAA00 : 0xFF88FF88);
                }
            }
        } catch (Throwable t) { /* ignore */ }

        // ── DJI telemetria ──
        try {
            DJIHelper dji = DJIHelper.getInstance();
            if (!dji.isConnected()) {
                sbDrone.setText("DRON: nincs");
                sbDrone.setTextColor(0xFFFF4444);
                sbRc.setText("RC: --");    sbRc.setTextColor(0xFF888888);
                sbRcBatt.setText("");
                sbGps.setText("SAT: --"); sbGps.setTextColor(0xFF888888);
                sbDroneBatt.setText("AKKU: --"); sbDroneBatt.setTextColor(0xFF888888);
                return;
            }

            // Drón neve
            String name = dji.getConnectedProductName();
            sbDrone.setText(name != null ? name : "Dron");
            sbDrone.setTextColor(0xFF44FF88);

            // RC kapcsolat
            boolean rcOk = dji.isRcConnected();
            sbRc.setText("RC: " + (rcOk ? "OK" : "nincs"));
            sbRc.setTextColor(rcOk ? 0xFF44FF88 : 0xFFFF4444);

            // RC akku (async)
            dji.getRcBatteryPercent(pct -> runOnUiThread(() -> {
                if (sbRcBatt == null) return;
                if (pct >= 0) {
                    sbRcBatt.setText(pct + "%");
                    sbRcBatt.setTextColor(pct < 20 ? 0xFFFF4444 : pct < 40 ? 0xFFFFAA00 : 0xFF44FF88);
                } else {
                    sbRcBatt.setText("");
                }
            }));

            // Drón akku (async)
            dji.getDroneBatteryPercent(pct -> runOnUiThread(() -> {
                if (sbDroneBatt == null) return;
                if (pct >= 0) {
                    sbDroneBatt.setText("AKKU: " + pct + "%");
                    sbDroneBatt.setTextColor(pct < 20 ? 0xFFFF4444 : pct < 40 ? 0xFFFFAA00 : 0xFF44FF88);
                } else {
                    sbDroneBatt.setText("AKKU: --");
                    sbDroneBatt.setTextColor(0xFF888888);
                }
            }));

            // GPS műholdak (Flight Controller callback – csak egyszer regisztráljuk)
            dji.setFlightStateCallback((sats, homeSet) -> runOnUiThread(() -> {
                if (sbGps == null) return;
                sbGps.setText("SAT: " + sats + (homeSet ? " H" : ""));
                sbGps.setTextColor(sats >= 10 ? 0xFF44FF88 : sats >= 6 ? 0xFFFFAA00 : 0xFFFF4444);
            }));

        } catch (Throwable t) { /* DJI SDK nem elérhető */ }
    }

    // ── Panel csúsztatás ──────────────────────────────────────────────

    private void toggleSidePanel() {
        panelVisible = !panelVisible;
        // Az egész konténert (gomb + panel) animáljuk együtt
        // A konténer szélessége = panel (320dp) + gomb (36dp)
        // Elrejtéskor csak a panel tolódik ki, a gomb látható marad
        if (panelVisible) {
            // Panel most látható → kinyitás (húzás bal felé)
            sidePanel.setVisibility(View.VISIBLE);
            ObjectAnimator anim = ObjectAnimator.ofFloat(sidePanel, "translationX",
                    sidePanel.getWidth(), 0f);
            anim.setDuration(250);
            anim.setInterpolator(new DecelerateInterpolator());
            anim.start();
            btnTogglePanel.setText("»"); // » = zárás (jobbra tolás)
        } else {
            // Panel rejtett → zárás (tolás jobbra)
            float panelW = sidePanel.getWidth();
            ObjectAnimator anim = ObjectAnimator.ofFloat(sidePanel, "translationX", 0f, panelW);
            anim.setDuration(250);
            anim.setInterpolator(new DecelerateInterpolator());
            anim.addListener(new android.animation.AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(android.animation.Animator a) {
                    sidePanel.setVisibility(View.GONE);
                }
            });
            anim.start();
            btnTogglePanel.setText("«"); // « = nyitás (bal felé húzás)
        }
    }

    // ── Jogi disclaimer ───────────────────────────────────────────────

    private void showDisclaimerIfNeeded() {
        SharedPreferences prefs = getPreferences(MODE_PRIVATE);
        boolean accepted = prefs.getBoolean("disclaimer_accepted", false);
        if (accepted) return;

        new AlertDialog.Builder(this)
            .setTitle("Fontos figyelmeztetés")
            .setMessage(
                "A DroneFly GCS egy tervezo eszköz.\n\n" +
                "A felhasznalo kizarolagos felelossege:\n" +
                "  - az ervenyes drónrepülési szabalyok betartasa\n" +
                "  - repülési tiltott zonak ellenorzese\n" +
                "  - szükseges engedelyek bemutatasa\n" +
                "  - a max. 120 m magassagi korlat betartasa\n" +
                "    (EU Open kategoria, A2/A3)\n\n" +
                "Magyarorszagon a dron uzemelteteshez\n" +
                "pilota-tanusitvany es regisztracio szükseges.\n\n" +
                "A fejleszto nem vallal felelossegt a nem\n" +
                "szabalyszeru repülesbol eredo kovetkezményekert.")
            .setPositiveButton("Megértettem, elfogadom", (d, w) -> {
                prefs.edit().putBoolean("disclaimer_accepted", true).apply();
            })
            .setCancelable(false)
            .show();
    }

    private CameraSettings buildCameraSettings() {
        CameraSettings s = new CameraSettings();
        s.autoMode = switchCameraAuto.isChecked();
        s.fileFormat = (CameraSettings.FileFormat) spinnerFileFormat.getSelectedItem();
        if (!s.autoMode) {
            s.photoMode    = (CameraSettings.PhotoMode) spinnerPhotoMode.getSelectedItem();
            s.iso          = (CameraSettings.IsoValue) spinnerIso.getSelectedItem();
            s.shutterSpeed = (CameraSettings.ShutterSpeed) spinnerShutter.getSelectedItem();
            s.whiteBalance = (CameraSettings.WhiteBalanceValue) spinnerWhiteBalance.getSelectedItem();
        }
        return s;
    }

    // ── Domborzatkövetés panel ────────────────────────────────────────

    private void initTerrainControls() {
        switchTerrain = findViewById(R.id.switchTerrain);
        tvTerrainInfo = findViewById(R.id.tvTerrainInfo);

        switchTerrain.setOnCheckedChangeListener((btn, isChecked) -> {
            tvTerrainInfo.setText(isChecked
                ? "Aktiv: waypointok magassaga DEM alapjan korrigalva"
                : "Magassag korrekció DEM (SRTM) domborzati modell alapjan");
            tvTerrainInfo.setTextColor(isChecked ? 0xFF44FF44 : 0xFF888888);
        });
    }

    /**
     * Domborzatkövetés alkalmazása a generált waypointokra.
     * Open-Elevation API-ból lekéri a tengerszint feletti magasságokat,
     * majd korrigálja az egyes waypoint magasságokat a felszállási pont
     * (start pont vagy első waypoint) terepszintjéhez képest.
     */
    private void applyTerrainFollowing(GridMissionGenerator.GeneratorResult result,
                                        double baseAGL) {
        // Összegyűjtjük az összes waypointot
        List<WaypointData> allWaypoints = new ArrayList<>();
        for (List<WaypointData> seg : result.segments) allWaypoints.addAll(seg);

        if (allWaypoints.isEmpty()) return;

        tvTerrainInfo.setText("Domborzati adatok letoltese...");
        tvTerrainInfo.setTextColor(0xFFFFAA00);
        btnUpload.setEnabled(false);

        // A felszállási pont: startPoint vagy az első waypoint
        final double takeoffLat = startPoint != null ? startPoint.getLatitude()
                : allWaypoints.get(0).latitude;
        final double takeoffLon = startPoint != null ? startPoint.getLongitude()
                : allWaypoints.get(0).longitude;

        // Hozzáadjuk a felszállási pontot is a lekérdezéshez (utolsó elem)
        WaypointData takeoffWp = new WaypointData(takeoffLat, takeoffLon, 0f);
        List<WaypointData> queryList = new ArrayList<>(allWaypoints);
        queryList.add(takeoffWp);

        ElevationProvider.fetchElevations(queryList, new ElevationProvider.ElevationCallback() {
            @Override
            public void onSuccess(double[] elevations) {
                double takeoffElev = elevations[elevations.length - 1]; // utolsó = felszállási pont

                // Korrekció alkalmazása az eredeti waypointokra
                int idx = 0;
                float minAlt = Float.MAX_VALUE, maxAlt = Float.MIN_VALUE;
                for (List<WaypointData> seg : result.segments) {
                    for (WaypointData wp : seg) {
                        double terrainDelta = elevations[idx] - takeoffElev;
                        float correctedAlt = (float) (baseAGL + terrainDelta);
                        correctedAlt = Math.max(10f, correctedAlt); // minimum 10m
                        wp.altitudeM = correctedAlt;
                        wp.terrainElevation = elevations[idx];
                        wp.hasTerrainCorrection = true;
                        if (correctedAlt < minAlt) minAlt = correctedAlt;
                        if (correctedAlt > maxAlt) maxAlt = correctedAlt;
                        idx++;
                    }
                }

                result.terrainCorrected = true;
                result.terrainMinAlt = minAlt;
                result.terrainMaxAlt = maxAlt;

                tvTerrainInfo.setText(String.format(
                    "Domborzat korrigalva: %.0fm - %.0fm (takeoff: %.0fm tszf)",
                    minAlt, maxAlt, takeoffElev));
                tvTerrainInfo.setTextColor(0xFF44FF44);
                btnUpload.setEnabled(true);
                btnExport.setEnabled(true);

                // Stats frissítés
                updateStatsWithTerrain(result);
            }

            @Override
            public void onError(String message) {
                tvTerrainInfo.setText("Domborzat hiba: " + message);
                tvTerrainInfo.setTextColor(0xFFFF4444);
                btnUpload.setEnabled(true);
                btnExport.setEnabled(true);
                Toast.makeText(MissionPlannerActivity.this,
                    "Domborzati adat nem elerheto: " + message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void updateStatsWithTerrain(GridMissionGenerator.GeneratorResult result) {
        if (tvStats == null || result == null) return;
        MissionConfig config = buildConfig();
        double recSpd = GsdCalculator.recommendedSpeedMs(config.gsdCm);
        String stats = String.format(
            "Terulet: %.2f ha | Waypoint: %d (%d szegmens)\n" +
            "Bázis mag.: %.0f m | Savkoz: %.1f m | Fototav: %.1f m\n" +
            "Sebesseg: %.1f m/s (ajanl.: %.1f m/s) | Ido: ~%.0f perc\n" +
            "Domborzat: %.0f m – %.0f m (korrigalt)",
            result.areaM2 / 10000.0,
            result.totalWaypoints, result.segments.size(),
            result.altitudeM, result.stripSpacingM, result.photoDistM,
            (double) config.speedMs, recSpd,
            result.estimatedMinutes,
            result.terrainMinAlt, result.terrainMaxAlt);
        tvStats.setText(stats);
    }

    private DroneProfile getSelectedDrone() {
        if (spinnerDrone == null) return DroneProfiles.getDefault();
        int pos = spinnerDrone.getSelectedItemPosition();
        if (pos < 0 || pos >= DroneProfiles.ALL.size()) return DroneProfiles.getDefault();
        return DroneProfiles.ALL.get(pos);
    }

    private org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase buildSatelliteTileSource() {
        // ESRI World Imagery – ingyenes, API kulcs nélkül, Crystal Sky-on is működik
        return new org.osmdroid.tileprovider.tilesource.XYTileSource(
                "ESRIsat", 1, 19, 256, "",
                new String[]{
                    "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/"
                }) {
            @Override
            public String getTileURLString(long pMapTileIndex) {
                int zoom = org.osmdroid.util.MapTileIndex.getZoom(pMapTileIndex);
                int x    = org.osmdroid.util.MapTileIndex.getX(pMapTileIndex);
                int y    = org.osmdroid.util.MapTileIndex.getY(pMapTileIndex);
                return "https://server.arcgisonline.com/ArcGIS/rest/services/" +
                       "World_Imagery/MapServer/tile/" + zoom + "/" + y + "/" + x;
            }
        };
    }

    private void toggleMapSource() {
        isSatelliteMap = !isSatelliteMap;
        if (isSatelliteMap) {
            // ESRI műhold
            mapView.setTileSource(buildSatelliteTileSource());
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

        tvGsd.setText(String.format("GSD: %.1f cm/px  →  magassag: %.0f m  (ajanl. v: %.1f m/s)",
                gsd, alt, recSpd));
        tvSidelap.setText(String.format("Oldalsó átfedes: %.0f%%", side));
        tvFrontlap.setText(String.format("Menetirany átfedes: %.0f%%", front));
        tvSpeed.setText(String.format("Sebesseg: %.0f m/s", speed));
        tvAngle.setText(String.format("Repülési irany: %d°", angle));
        int offsetM = getOffset();
        if (offsetM == 0) {
            tvOffset.setText("Tulrepüles (offset): kikapcsolva");
        } else {
            // Auto ajánlott érték: fél sávköz + fél fotótáv
            double autoOff = (GsdCalculator.stripSpacingM(alt, side, drone)
                           + GsdCalculator.photoDistanceM(alt, front, drone)) / 2.0;
            tvOffset.setText(String.format("Tulrepüles: %d m  (ajanl: %.0f m)", offsetM, autoOff));
        }
    }

    // ── Polygon rajzolás ───────────────────────────────────────────────────

    private void toggleStartPointMode() {
        startPointMode = !startPointMode;
        btnSetStart.setText(startPointMode ? "Kattints a start pontra..." : "Start/Home pont");
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
        m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM); // hegy csúcsa = a tényleges koordináta
        m.setDraggable(true);

        // Húzás: valós idejű sokszög-frissítés, végén auto-generálás
        m.setOnMarkerDragListener(new Marker.OnMarkerDragListener() {
            @Override public void onMarkerDragStart(Marker marker) { }
            @Override public void onMarkerDrag(Marker marker) {
                int i = polygonMarkers.indexOf(marker);
                if (i >= 0 && i < polygonPoints.size()) {
                    polygonPoints.set(i, marker.getPosition());
                    refreshPolygonOverlay();
                }
            }
            @Override public void onMarkerDragEnd(Marker marker) {
                int i = polygonMarkers.indexOf(marker);
                if (i >= 0 && i < polygonPoints.size()) {
                    polygonPoints.set(i, marker.getPosition());
                    refreshPolygonOverlay();
                    autoGenerateIfReady(); // újragenerálás húzás után
                }
            }
        });

        // Kattintásra: törlés lehetőség
        m.setOnMarkerClickListener((marker, mv) -> {
            int i = polygonMarkers.indexOf(marker);
            GeoPoint pos = marker.getPosition();
            new AlertDialog.Builder(MissionPlannerActivity.this)
                .setTitle("P" + (i + 1) + " jelölő")
                .setMessage(String.format("%.6f, %.6f\n\nTöröljem ezt a pontot?",
                        pos.getLatitude(), pos.getLongitude()))
                .setPositiveButton("Törlés", (d, w) -> removePolygonPoint(i))
                .setNegativeButton("Mégse", null)
                .show();
            return true;
        });

        polygonMarkers.add(m);
        mapView.getOverlays().add(m);
        mapView.invalidate();

        // Ha már legalább 3 pont van, auto-generálás
        autoGenerateIfReady();
    }

    /** Egy sarokpont törlése index alapján – újraszámozza a többi jelölőt */
    private void removePolygonPoint(int index) {
        if (index < 0 || index >= polygonPoints.size()) return;
        polygonPoints.remove(index);
        Marker removed = polygonMarkers.remove(index);
        mapView.getOverlays().remove(removed);
        for (int i = 0; i < polygonMarkers.size(); i++) {
            polygonMarkers.get(i).setTitle("P" + (i + 1));
        }
        refreshPolygonOverlay();
        mapView.invalidate();
        autoGenerateIfReady();
    }

    /** Utolsó lerakott pont visszavonása */
    private void removeLastPolygonPoint() {
        if (polygonPoints.isEmpty()) {
            Toast.makeText(this, "Nincs visszavonható pont", Toast.LENGTH_SHORT).show();
            return;
        }
        removePolygonPoint(polygonPoints.size() - 1);
    }

    // ── Akadály kezelés ───────────────────────────────────────────────────

    /** Akadály-elhelyezés mód be/ki */
    private void toggleObstacleMode() {
        obstacleMode = !obstacleMode;
        if (obstacleMode) {
            btnObstacle.setText("Akadaly mod: BE  (Kattints a terkepen!)");
            btnObstacle.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(0xFFCC4400));
            Toast.makeText(this,
                "Akadály mód: kattints a térképen az akadály helyére",
                Toast.LENGTH_SHORT).show();
        } else {
            btnObstacle.setText("+ Akadaly jelolese");
            btnObstacle.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(0xFF662200));
        }
    }

    /** Dialog az akadály sugarának és magasságának megadásához */
    private void showAddObstacleDialog(final GeoPoint p) {
        // Két EditText egy LinearLayout-ban
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = (int)(16 * getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad / 2, pad, 0);

        TextView tvInfo = new TextView(this);
        tvInfo.setText(String.format("Pozicio: %.5f, %.5f\n\nAdd meg az akadaly meret adatait:",
                p.getLatitude(), p.getLongitude()));
        tvInfo.setTextColor(0xFFCCCCCC);
        tvInfo.setTextSize(12f);
        layout.addView(tvInfo);

        TextView tvR = new TextView(this);
        tvR.setText("Biztonsagi zona sugara (m):");
        tvR.setTextColor(0xFFAAAAAA);
        tvR.setTextSize(12f);
        tvR.setPadding(0, pad / 2, 0, 0);
        layout.addView(tvR);

        EditText etRadius = new EditText(this);
        etRadius.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        etRadius.setHint("pl. 15");
        etRadius.setText("15");
        etRadius.setTextColor(0xFFFFFFFF);
        etRadius.setHintTextColor(0xFF666666);
        layout.addView(etRadius);

        TextView tvH = new TextView(this);
        tvH.setText("Akadaly magassaga (m, talajtol):");
        tvH.setTextColor(0xFFAAAAAA);
        tvH.setTextSize(12f);
        tvH.setPadding(0, pad / 2, 0, 0);
        layout.addView(tvH);

        EditText etHeight = new EditText(this);
        etHeight.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        etHeight.setHint("pl. 25");
        etHeight.setText("25");
        etHeight.setTextColor(0xFFFFFFFF);
        etHeight.setHintTextColor(0xFF666666);
        layout.addView(etHeight);

        new AlertDialog.Builder(this)
            .setTitle("Akadaly hozzaadasa")
            .setView(layout)
            .setPositiveButton("Hozzaad", (d, w) -> {
                try {
                    float radius = Float.parseFloat(etRadius.getText().toString().trim());
                    float height = Float.parseFloat(etHeight.getText().toString().trim());
                    if (radius <= 0 || height <= 0) {
                        Toast.makeText(this, "Ertekeknek pozitivnak kell lenniuk!", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    addObstacle(p, radius, height);
                    // Marad az akadály módban – egymás után több akadály is letehető
                } catch (NumberFormatException e) {
                    Toast.makeText(this, "Ervenytelen szam!", Toast.LENGTH_SHORT).show();
                }
            })
            .setNegativeButton("Megse", null)
            .show();
    }

    /** Akadály hozzáadása a listához, marker és kör overlay rajzolásával */
    private void addObstacle(GeoPoint p, float radiusM, float heightM) {
        ObstacleData obs = new ObstacleData(p.getLatitude(), p.getLongitude(), radiusM, heightM);
        obstacleList.add(obs);

        // Kör overlay rajzolása (30 szegmenssel közelített kör)
        Polygon circle = buildCircleOverlay(p.getLatitude(), p.getLongitude(), radiusM);
        obstacleOverlays.add(circle);
        mapView.getOverlays().add(0, circle);

        // Marker az akadály közepére
        Marker m = new Marker(mapView);
        m.setPosition(p);
        m.setTitle("Akadaly #" + obstacleList.size());
        m.setSnippet(String.format("Sugar: %.0fm | Mag: %.0fm", radiusM, heightM));
        m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
        // Kattintásra: info + törlés lehetőség
        final int obsIndex = obstacleList.size() - 1;
        m.setOnMarkerClickListener((marker, mv) -> {
            int idx = obstacleMarkers.indexOf(marker);
            ObstacleData o = (idx >= 0 && idx < obstacleList.size()) ? obstacleList.get(idx) : obs;
            new AlertDialog.Builder(MissionPlannerActivity.this)
                .setTitle("Akadaly #" + (idx + 1))
                .setMessage(String.format(
                    "Pozicio: %.5f, %.5f\nBiztonsagi zona: %.0f m\nMagassag: %.0f m\n\n" +
                    "Ha a repülési magassag <= %.0f m, az akadaly aktiv es\n" +
                    "a kozeteben levo waypointokat kihagyjuk.",
                    o.latitude, o.longitude, o.radiusM, o.heightM, o.heightM))
                .setPositiveButton("Torles", (d2, w2) -> removeObstacle(idx))
                .setNegativeButton("Bezaras", null)
                .show();
            return true;
        });
        obstacleMarkers.add(m);
        mapView.getOverlays().add(m);
        mapView.invalidate();

        // Ha van aktív misszió, újragenerálás az akadályokkal
        autoGenerateIfReady();

        Toast.makeText(this, String.format(
                "Akadaly hozzaadva: %.0fm zona, %.0fm magas", radiusM, heightM),
                Toast.LENGTH_SHORT).show();
    }

    /** Egy akadály törlése index alapján */
    private void removeObstacle(int index) {
        if (index < 0 || index >= obstacleList.size()) return;
        obstacleList.remove(index);

        // Overlay eltávolítása
        if (index < obstacleOverlays.size()) {
            mapView.getOverlays().remove(obstacleOverlays.remove(index));
        }
        // Marker eltávolítása
        if (index < obstacleMarkers.size()) {
            mapView.getOverlays().remove(obstacleMarkers.remove(index));
        }
        // Újracímkézés
        for (int i = 0; i < obstacleMarkers.size(); i++) {
            obstacleMarkers.get(i).setTitle("Akadaly #" + (i + 1));
        }
        mapView.invalidate();
        autoGenerateIfReady();
    }

    /** Összes akadály törlése */
    private void clearAllObstacles() {
        if (obstacleList.isEmpty()) {
            Toast.makeText(this, "Nincs akadaly a terkepen", Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(this)
            .setTitle("Akadalyok torlese")
            .setMessage(obstacleList.size() + " akadaly torlesehez biztosan?")
            .setPositiveButton("Torles", (d, w) -> {
                obstacleList.clear();
                for (Polygon ov : obstacleOverlays) mapView.getOverlays().remove(ov);
                obstacleOverlays.clear();
                for (Marker mk : obstacleMarkers) mapView.getOverlays().remove(mk);
                obstacleMarkers.clear();
                mapView.invalidate();
                autoGenerateIfReady();
            })
            .setNegativeButton("Megse", null)
            .show();
    }

    /**
     * Közelített kör OSMDroid Polygon-ként (30 szegmens).
     * Az akadály területét jelöli a térképen.
     */
    private Polygon buildCircleOverlay(double lat, double lon, float radiusM) {
        List<GeoPoint> pts = new ArrayList<>();
        final int STEPS = 36;
        final double mPerDegLat = 111000.0;
        final double mPerDegLon = 111000.0 * Math.cos(Math.toRadians(lat));
        for (int i = 0; i <= STEPS; i++) {
            double angle = 2.0 * Math.PI * i / STEPS;
            double dLat = radiusM * Math.cos(angle) / mPerDegLat;
            double dLon = radiusM * Math.sin(angle) / mPerDegLon;
            pts.add(new GeoPoint(lat + dLat, lon + dLon));
        }
        Polygon p = new Polygon();
        p.setPoints(pts);
        p.getFillPaint().setColor(0x44FF3300);     // áttetsző piros kitöltés
        p.getOutlinePaint().setColor(0xCCFF4400);  // narancsvörös körvonal
        p.getOutlinePaint().setStrokeWidth(3f);
        return p;
    }

    /**
     * Ha van legalább 3 pont, automatikusan generálja a missziót a jelenlegi
     * beállításokkal. Domborzatkövetés NEM fut automatikusan (API hívás lenne),
     * azt a "Misszió generálása" gomb indítja manuálisan.
     */
    private void autoGenerateIfReady() {
        if (polygonPoints.size() < 3) {
            // 3 pontnál kevesebb: töröljük az esetleges régi útvonalat
            if (missionOverlay != null) {
                mapView.getOverlays().remove(missionOverlay);
                missionOverlay = null;
                mapView.invalidate();
            }
            lastResult = null;
            btnUpload.setEnabled(false);
            btnExport.setEnabled(false);
            tvStats.setText(polygonPoints.size() + " pont – még " + (3 - polygonPoints.size()) + " kell a területhez");
            return;
        }

        MissionConfig config = buildConfig();
        config.terrainFollowing = false; // auto-generálásban nincs terrain (túl lassú lenne)
        lastResult = GridMissionGenerator.generate(polygonPoints, config);

        if (lastResult.errorMessage != null) {
            tvStats.setText("Hiba: " + lastResult.errorMessage);
            return;
        }

        drawMissionPath(lastResult.segments);
        currentSegmentIndex = 0;
        btnUpload.setEnabled(DJIHelper.getInstance().isConnected() && !missionRunning);
        btnExport.setEnabled(true);

        double recSpd = GsdCalculator.recommendedSpeedMs(config.gsdCm, config.droneProfile);
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(
            "Terulet: %.2f ha | %d pont (%d szegmens)\n" +
            "Magassag: %.0f m | Savkoz: %.1f m | Fototav: %.1f m\n" +
            "Sebesseg: %.0f m/s | Ido: ~%.0f perc",
            lastResult.areaM2 / 10000.0,
            lastResult.totalWaypoints, lastResult.segments.size(),
            lastResult.altitudeM, lastResult.stripSpacingM, lastResult.photoDistM,
            (double) config.speedMs, lastResult.estimatedMinutes));
        if (lastResult.skippedByObstacle > 0) {
            sb.append(String.format("\n⚠ Akadaly miatt kihagyva: %d wp", lastResult.skippedByObstacle));
        }
        tvStats.setText(sb.toString());

        // 120m figyelmeztetés
        if (lastResult.altitudeM > MAX_ALTITUDE_LEGAL) {
            Toast.makeText(this,
                String.format("Figyelem: %.0fm > 120m (EU határ)! Csokkentsd a GSD-t.",
                    lastResult.altitudeM),
                Toast.LENGTH_LONG).show();
        }
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
        startMarker = null;
        startPoint  = null;
        // Sarokpont jelölők törlése – removeIf nem elérhető API 22-n!
        for (Marker m : polygonMarkers) {
            mapView.getOverlays().remove(m);
        }
        polygonMarkers.clear();
        // Akadályok is törlődnek
        obstacleList.clear();
        for (Polygon ov : obstacleOverlays) mapView.getOverlays().remove(ov);
        obstacleOverlays.clear();
        for (Marker mk : obstacleMarkers) mapView.getOverlays().remove(mk);
        obstacleMarkers.clear();
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

    /**
     * Manuális újragenerálás – akkor kell, ha:
     * 1. Megváltozott a GSD / átfedés / szög / sebesség beállítás
     * 2. Domborzatkövetést szeretnénk alkalmazni (API hívással)
     * A pontlerakás és húzás automatikusan frissíti a missziót (autoGenerateIfReady).
     */
    private void generateMission() {
        if (polygonPoints.size() < 3) {
            Toast.makeText(this, "Legalabb 3 pont szukseges!", Toast.LENGTH_SHORT).show();
            return;
        }
        MissionConfig config = buildConfig();
        config.terrainFollowing = switchTerrain != null && switchTerrain.isChecked();
        lastResult = GridMissionGenerator.generate(polygonPoints, config);

        if (lastResult.errorMessage != null) {
            Toast.makeText(this, lastResult.errorMessage, Toast.LENGTH_LONG).show();
            return;
        }

        drawMissionPath(lastResult.segments);
        currentSegmentIndex = 0;
        boolean connected = false;
        try { connected = DJIHelper.getInstance().isConnected(); } catch (Throwable t) { }
        btnUpload.setEnabled(connected && !missionRunning);
        btnExport.setEnabled(true);

        double recSpd = GsdCalculator.recommendedSpeedMs(config.gsdCm, config.droneProfile);
        StringBuilder sbStats = new StringBuilder();
        sbStats.append(String.format(
            "Terulet: %.2f ha | %d pont (%d szegmens)\n" +
            "Magassag: %.0f m | Savkoz: %.1f m | Fototav: %.1f m\n" +
            "Sebesseg: %.0f m/s (ajanl: %.0f m/s) | Ido: ~%.0f perc",
            lastResult.areaM2 / 10000.0,
            lastResult.totalWaypoints, lastResult.segments.size(),
            lastResult.altitudeM, lastResult.stripSpacingM, lastResult.photoDistM,
            (double) config.speedMs, (double) recSpd, lastResult.estimatedMinutes));
        if (lastResult.skippedByObstacle > 0) {
            sbStats.append(String.format("\n⚠ Akadaly miatt kihagyva: %d wp", lastResult.skippedByObstacle));
        }
        tvStats.setText(sbStats.toString());

        if (lastResult.altitudeM > MAX_ALTITUDE_LEGAL) {
            Toast.makeText(this,
                String.format("Figyelem: %.0fm > 120m (EU határ)! Csokkentsd a GSD-t.",
                    lastResult.altitudeM),
                Toast.LENGTH_LONG).show();
        }

        if (config.terrainFollowing) {
            applyTerrainFollowing(lastResult, lastResult.altitudeM);
        }
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
                // Kamera beállítások alkalmazása, utána misszió indítás
                Toast.makeText(this, "Kamera beállítása...", Toast.LENGTH_SHORT).show();
                CameraSettings camSettings = buildCameraSettings();
                CameraConfigurator.applySettings(camSettings, (success, msg) ->
                    runOnUiThread(() -> {
                        if (!success) {
                            Toast.makeText(this,
                                "Kamera hiba: " + msg + " – indítás folytatódik",
                                Toast.LENGTH_SHORT).show();
                        }
                        uploader.startMission(new MissionUploader.UploadCallback() {
                            @Override public void onSuccess() {
                                runOnUiThread(() -> {
                                    Toast.makeText(MissionPlannerActivity.this,
                                        "Repülés elindítva!", Toast.LENGTH_LONG).show();
                                    setMissionRunning(true);
                                });
                            }
                            @Override public void onError(String errMsg) {
                                runOnUiThread(() -> Toast.makeText(MissionPlannerActivity.this,
                                    "Indítás hiba: " + errMsg, Toast.LENGTH_LONG).show());
                            }
                        });
                    })
                );
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
        btnPauseMission.setText("Szünet");
        btnUpload.setEnabled(!running && lastResult != null);
        btnStart.setEnabled(false);
    }

    private void togglePauseMission() {
        if (!missionPaused) {
            uploader.pauseMission(new MissionUploader.UploadCallback() {
                @Override public void onSuccess() {
                    runOnUiThread(() -> {
                        missionPaused = true;
                        btnPauseMission.setText("Folytatás");
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
                        btnPauseMission.setText("Szünet");
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
        c.droneProfile      = getSelectedDrone();
        c.gsdCm             = getGsd();
        c.altitudeM         = GsdCalculator.altitudeFromGsd(c.gsdCm, c.droneProfile);
        c.sidelapPercent    = getSidelap();
        c.frontlapPercent   = getFrontlap();
        c.speedMs           = getSpeed();
        c.flightAngleDeg    = getAngle();
        c.terrainFollowing  = switchTerrain != null && switchTerrain.isChecked();
        c.offsetM           = getOffset();
        c.obstacles         = new ArrayList<>(obstacleList); // akadályok másolata
        c.cameraSettings    = buildCameraSettings();
        return c;
    }

    private double getGsd()      { return 0.5 + sbGsd.getProgress() * 0.1; }
    private double getSidelap()  { return 50.0 + sbSidelap.getProgress(); }
    private double getFrontlap() { return 60.0 + sbFrontlap.getProgress(); }
    private float  getSpeed()    { return 3.0f + sbSpeed.getProgress(); }
    private int    getAngle()    { return sbAngle.getProgress(); }
    private int    getOffset()   { return sbOffset != null ? sbOffset.getProgress() : 0; }

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
        statusHandler.removeCallbacks(statusRunnable);
        statusHandler.post(statusRunnable);
    }

    @Override protected void onPause() {
        super.onPause();
        mapView.onPause();
        statusHandler.removeCallbacks(statusRunnable);
    }
    @Override protected void onDestroy() {
        super.onDestroy();
        mapView.onDetach();
        statusHandler.removeCallbacks(statusRunnable);
    }

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
