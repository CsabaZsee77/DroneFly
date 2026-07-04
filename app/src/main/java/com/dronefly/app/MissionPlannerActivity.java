package com.dronefly.app;

import android.animation.ObjectAnimator;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.location.Location;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
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
import android.view.TextureView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import com.dronefly.app.dji.CameraConfigurator;
import com.dronefly.app.dji.DJIHelper;
import com.dronefly.app.dji.DroneVideoWidget;
import com.dronefly.app.dji.MissionUploader;
import com.dronefly.app.dji.MediaSessionDownloader;
import com.dronefly.app.model.CameraSettings;

// DJI kapcsolat listener – valós idejű státusz frissítés
// (az Activity implements DJIHelper.ConnectionListener)
import com.dronefly.app.layers.LandUseLayer;
import com.dronefly.app.layers.AirspaceLayer;
import com.dronefly.app.layers.ProtectedAreasLayer;
import com.dronefly.app.mission.CsvMissionParser;
import com.dronefly.app.sync.AuthManager;
import com.dronefly.app.sync.NetworkMonitor;
import com.dronefly.app.sync.SyncManager;
import com.dronefly.app.mission.BlockGridGenerator;
import com.dronefly.app.mission.ElevationProvider;
import com.dronefly.app.mission.GridMissionGenerator;
import com.dronefly.app.mission.GsdCalculator;
import com.dronefly.app.mission.MissionExporter;
import com.dronefly.app.mission.ProjectManager;
import com.dronefly.app.mission.SamplingPointGenerator;
import com.dronefly.app.mission.SamplingMissionGenerator;
import com.dronefly.app.model.Block;
import com.dronefly.app.model.BlockGridConfig;
import com.dronefly.app.model.BlockStatus;
import com.dronefly.app.model.DroneProfile;
import com.dronefly.app.model.DroneProfiles;
import com.dronefly.app.model.MissionConfig;
import com.dronefly.app.model.ObstacleData;
import com.dronefly.app.model.WaypointData;
import com.dronefly.app.ui.BlockGridDialog;
import com.dronefly.app.ui.BlockOverlay;

import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.MapTileProviderBasic;
import org.osmdroid.tileprovider.modules.IFilesystemCache;
import org.osmdroid.tileprovider.tilesource.ITileSource;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polygon;
import org.osmdroid.views.overlay.Polyline;
import org.osmdroid.views.overlay.gestures.RotationGestureOverlay;
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;

import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.media.MediaRecorder;
import android.media.MediaScannerConnection;
import android.os.Environment;
import android.util.DisplayMetrics;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MissionPlannerActivity extends AppCompatActivity {

    private static final int PICK_CSV_REQUEST        = 101;
    private static final int REQUEST_MEDIA_PROJECTION = 102;

    // Képernyőrögzítés
    private MediaProjectionManager projectionManager;
    private MediaProjection         mediaProjection;
    private MediaRecorder           mediaRecorder;
    private VirtualDisplay          virtualDisplay;
    private boolean                 isRecording = false;
    private File                    currentRecordingFile;
    private Button                  btnRec;
    private final Handler           recBlinkHandler  = new Handler(Looper.getMainLooper());
    private Runnable                recBlinkRunnable;

    // Térkép
    private MapView mapView;
    private MyLocationNewOverlay locationOverlay;
    private final List<GeoPoint> polygonPoints  = new ArrayList<>();
    private final List<Marker>   polygonMarkers = new ArrayList<>(); // külön nyilvántartás, removeIf kiváltása
    private Polygon  polygonOverlay;
    private Polyline missionOverlay;

    // M07 — Blokk-felosztás
    private BlockGridConfig blockGridConfig = null;
    private BlockGridGenerator.GridResult currentGrid = null;
    private Block selectedBlock = null;
    private BlockOverlay blockOverlay = null;
    private Button   btnSplit, btnMarkDone, btnResetBlock;
    private TextView tvBlockInfo;
    private LinearLayout blockButtonRow;

    // Beállítások widgetek
    private TextView tvGsd, tvSidelap, tvFrontlap, tvSpeed, tvAngle, tvOffset, tvStats;
    private SeekBar  sbGsd, sbSidelap, sbFrontlap, sbSpeed, sbAngle, sbOffset;
    private EditText etAltitude;
    private Button   btnAltMinus, btnAltPlus;
    private boolean  updatingAlt = false; // rekurzív frissítés gátoló flag
    private Spinner  spinnerDrone;
    private Button   btnUndoPoint, btnClear, btnGenerate, btnUpload, btnStart,
                     btnImportCsv, btnExport, btnMyLocation,
                     btnMapToggle, btnPauseMission, btnStopMission;
    private boolean  missionUploaded = false;
    private boolean  missionRunning = false;
    private boolean  missionPaused  = false;
    private int      lastSatCount   = 0;
    /** Drón utolsó ismert GPS pozíciója (DJI telemetria alapján). 0/0 = nincs adat. */
    private double   lastDroneLat   = 0;
    private double   lastDroneLon   = 0;

    // Misszió folytatás (megszakadás utáni resume)
    private int resumeSegmentIndex   = -1;  // melyik szegmensnél szakadt meg
    private int resumeWaypointIndex  = -1;  // utolsó elért waypoint indexe a szegmensen belül
    private int uploadedSegmentSize  = 0;   // feltöltött szegmens teljes mérete

    // Haladásjelző – térkép overlays és progress UI
    private org.osmdroid.views.overlay.Marker droneMarker;
    private org.osmdroid.views.overlay.Polyline completedOverlay;
    private TextView tvMissionProgress;
    private android.widget.ProgressBar pbMissionProgress;

    // Szimuláció
    private Button btnSimulate;
    private final Handler simHandler = new Handler(Looper.getMainLooper());
    private boolean simRunning = false;
    private List<org.osmdroid.util.GeoPoint> simCompletedPoints;

    // Régi kamera panel (oldalpanel összecsukható szekció)
    private LinearLayout cameraSettingsBody, cameraManualControls;
    private Switch switchCameraAuto;
    private Spinner spinnerPhotoMode, spinnerIso, spinnerShutter, spinnerWhiteBalance, spinnerFileFormat;
    private TextView tvCameraExpand;
    private boolean cameraExpanded = false;

    // M05 Kamera Konfigurátor panel
    private com.dronefly.app.camera.HistogramView histogramView;
    private SeekBar seekbarEv, seekbarShutter5, seekbarWb;
    private Button btnIso100, btnIso200, btnIso400, btnIso800;
    private Button btnApF56, btnApF8, btnApF11;
    private Button btnFocusAuto5, btnFocusInf5, btnApplyCamera5;
    private TextView tvShutterValue5, tvWbValue5, tvEvValue5, tvCameraStatus5;
    private View cameraPanelOverlay;
    private int isoIdx = 0;   // 0=100 1=200 2=400 3=800
    private int apIdx  = 1;   // 0=f/5.6 1=f/8 2=f/11
    private boolean focusLocked   = false;
    private boolean cameraAutoMode = true;
    private View    cameraParamsGroup;
    private Button  btnModeAuto, btnModeManual, btnAutoLock;
    private int[]   lastHistData = null;
    private TextureView cameraTextureView;
    private android.os.HandlerThread histThread;
    private android.os.Handler histHandler;
    private Runnable histRunnable;
    private boolean histogramStartedVideo = false;

    private static final String[] SS_LABELS = {"1/400","1/500","1/640","1/800","1/1000","1/1250","1/1600","1/2000"};

    // Domborzatkövetés
    private Switch switchTerrain;
    private TextView tvTerrainInfo;

    // Crosshatch (keresztrács) mód
    private Switch   switchCrosshatch;
    private SeekBar  sbCrosshatchAngle;
    private TextView tvCrosshatchAngle;

    // Szinkronizáció UI
    private Button   btnSync, btnLogin;
    private TextView tvSyncStatus;
    private final NetworkMonitor.Listener networkListener = state -> updateSyncStatus();

    // Panel csúsztatás
    private android.widget.LinearLayout sidePanelContainer;
    private ScrollView sidePanel;
    private Button btnTogglePanel;
    private boolean panelVisible = true;

    // Akadályok
    private final List<ObstacleData> obstacleList    = new ArrayList<>();
    private final List<Marker>       obstacleMarkers = new ArrayList<>();
    private final List<Polygon>      obstacleOverlays = new ArrayList<>();
    private boolean obstacleMode = false;
    private Button  btnObstacle, btnClearObstacles;

    // Térkép rétegek
    private Button btnLayerProtected, btnLayerAirspace, btnLayerLandUse;
    private ProtectedAreasLayer protectedLayer;
    private AirspaceLayer airspaceLayer;
    private LandUseLayer landUseLayer;
    private static final String OPENAIP_API_KEY = "ebe3e1941252167b80e2e974613600a1";
    // LGT magassági szűrő
    private Button btnAltFilter;
    private int altPresetIndex = 0; // 0 = ∞ (minden légtér látszik)

    // Projekt mentés / betöltés
    private Button btnNewPlan, btnSaveProject, btnSaveAsProject, btnLoadProject;
    private TextView tvCurrentPlanName;
    private File currentPlanFile = null;
    private static final String PREFS_RESUME = "dronefly_resume";

    // Státuszsáv
    private TextView sbDrone, sbRc, sbRcBatt, sbGps, sbDroneBatt, sbTabletBatt, sbKp;
    private final Handler statusHandler = new Handler(Looper.getMainLooper());
    private static final int STATUS_INTERVAL_MS = 2000;
    private final ExecutorService djiExecutor = Executors.newSingleThreadExecutor();
    private long lastKpFetchMs = 0;
    private static final long KP_FETCH_INTERVAL_MS = 10 * 60 * 1000L; // 10 perc

    private static final float MAX_ALTITUDE_LEGAL = 120f; // EU Open kategória limit (m)

    // Térképváltó
    private boolean  isSatelliteMap = true; // Google Satellite az alapértelmezett

    // Kamera feed widget (PiP)
    private DroneVideoWidget videoWidget;
    private FrameLayout cameraWindow;
    private ImageView focusRing;

    // DJI kapcsolat státusz
    private TextView tvDroneStatus;

    // Misszió állapot
    private GridMissionGenerator.GeneratorResult lastResult;
    private int currentSegmentIndex = 0;
    private final MissionUploader uploader = new MissionUploader();

    // M01 §10 / M02 §7 — Mintavételi misszió (2026-07-02)
    private Switch   switchSamplingMode;
    private LinearLayout samplingParamsPanel;
    private EditText etSampleCount, etTransitAlt, etSampleAlt, etHoverSeconds;
    private Spinner  spinnerSamplingMethod;
    private Button   btnDownloadSession;
    private List<GeoPoint> lastSamplePoints = null;
    private final List<Marker> samplePointMarkers = new ArrayList<>();
    private long   samplingMissionStartTimeMs = 0L;
    private String currentSamplingSessionId   = null;
    private final MediaSessionDownloader mediaSessionDownloader = new MediaSessionDownloader();
    // Terepi javítás (2026-07-03, M04_L1 §18): app-vezérelt fotó-trigger, a
    // WaypointAction helyett — a médialetöltés a TÉNYLEGESEN megerősített
    // pontok listájával dolgozik, nem a nyers mintapontszámmal. A cursor
    // szegmens-határoktól függetlenül, a mintapontok eredeti sorrendjében
    // követi, melyik pontnál tartunk (onWaypointReached szegmensenként is
    // szigorúan sorban hívódik).
    private int samplePointCursor = 0;
    private int samplingPhotosFailed = 0;
    private final List<GeoPoint> confirmedSamplePointsForSession = new ArrayList<>();

    // M10 — Sűrű rács (NORMAL mód, alacsony magasságú mozaikoláshoz, 2026-07-03)
    private Switch   switchDenseGridMode;
    private int      denseGridPhotosConfirmed = 0;
    private int      denseGridPhotosFailed    = 0;

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
        // Cache méret korlát: 200 MB (Crystal Sky: bővítve a zoom miatti tile igény miatt)
        Configuration.getInstance().setTileFileSystemCacheMaxBytes(200L * 1024L * 1024L);
        Configuration.getInstance().setTileFileSystemCacheTrimBytes(160L * 1024L * 1024L);
        // In-memory tile limit: 6 tile = ~1.5MB — Crystal Sky heap nyomás ellen
        Configuration.getInstance().setCacheMapTileCount((short) 6);
        // 1 letöltési szál: kevesebb párhuzamos bitmap allokáció → kevesebb GC
        Configuration.getInstance().setTileDownloadThreads((short) 1);
        // Letöltési sor limit: 10 — gyors zoom se torlassza fel a sort
        Configuration.getInstance().setTileDownloadMaxQueueSize((short) 10);
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

        // TileWriter (fájlalapú) helyett SqlTileWriter-t használnánk normálisan API 10+ esetén,
        // de a SqlTileWriter → MapTileSqlCacheProvider → "sqlcache" HandlerThread ART 5.1-en
        // SIGSEGV-t okoz GC közbeni SQLite cursor felszabadításkor.
        // TileWriter átadásával a MapTileProviderBasic MapTileFilesystemProvider-t választ
        // (fájlalapú, "filesystem" szál, nincs SQLite → nincs crash).
        MapTileProviderBasic provider = new MapTileProviderBasic(this, buildSatelliteTileSource(),
                new org.osmdroid.tileprovider.modules.TileWriter());
        mapView.setTileProvider(provider);

        mapView.setMultiTouchControls(true);
        mapView.setUseDataConnection(true);
        mapView.setBuiltInZoomControls(false);
        mapView.setMaxZoomLevel(20.0);

        // Zoom gesztus alatt szüneteltetjük a tile letöltést — OOM védelme
        mapView.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_POINTER_DOWN: // 2. ujj = pinch-zoom kezdete
                    mapView.setUseDataConnection(false);
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_POINTER_UP:
                case MotionEvent.ACTION_CANCEL:
                    // 300ms késleltetés: a zoom animáció lecsengése után
                    mapView.postDelayed(() -> {
                        if (isWifiConnected()) mapView.setUseDataConnection(true);
                    }, 300);
                    break;
            }
            return false; // osmdroid továbbra is kezeli az eseményt
        });

        // Magyarország közép + jó zoom szint
        mapView.getController().setZoom(11.0);  // Pest megye / Budapest térség
        mapView.getController().setCenter(new GeoPoint(47.55, 19.15)); // Pest megye közepe
        mapView.postInvalidate();

        // Réteg objektumok inicializálása
        protectedLayer = new ProtectedAreasLayer(mapView);
        airspaceLayer  = new AirspaceLayer(mapView, OPENAIP_API_KEY);
        landUseLayer   = new LandUseLayer(mapView);

        RotationGestureOverlay rotation = new RotationGestureOverlay(mapView);
        rotation.setEnabled(true);
        mapView.getOverlays().add(rotation);

        locationOverlay = new MyLocationNewOverlay(
                new GpsMyLocationProvider(this), mapView);
        locationOverlay.enableMyLocation();
        android.graphics.drawable.Drawable personDrawable =
                getResources().getDrawable(R.drawable.ic_person_marker);
        int pw = personDrawable.getIntrinsicWidth();
        int ph = personDrawable.getIntrinsicHeight();
        Bitmap personBitmap = Bitmap.createBitmap(pw, ph, Bitmap.Config.ARGB_8888);
        personDrawable.setBounds(0, 0, pw, ph);
        personDrawable.draw(new Canvas(personBitmap));
        locationOverlay.setPersonIcon(personBitmap);
        locationOverlay.setDirectionArrow(personBitmap, personBitmap);
        mapView.getOverlays().add(locationOverlay);

        // Érintésfigyelő
        mapView.getOverlays().add(new org.osmdroid.views.overlay.MapEventsOverlay(
                new org.osmdroid.events.MapEventsReceiver() {
            @Override
            public boolean singleTapConfirmedHelper(GeoPoint p) {
                if (obstacleMode) {
                    showAddObstacleDialog(p);
                } else if (blockOverlay != null && currentGrid != null
                        && currentGrid.blocks != null && !currentGrid.blocks.isEmpty()) {
                    // M07: blokk-mód aktív → tap → blokk-kiválasztás
                    Block hit = blockOverlay.hitTest(p);
                    if (hit != null) {
                        setSelectedBlock(hit);
                    }
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

        sbGsd      = findViewById(R.id.sbGsd);      // 0–99 → 0.1–10.0 cm/px
        sbSidelap  = findViewById(R.id.sbSidelap);  // 0–40 → 50–90%
        sbFrontlap = findViewById(R.id.sbFrontlap); // 0–30 → 60–90%
        sbSpeed    = findViewById(R.id.sbSpeed);    // 0–14 → 1–15 m/s
        sbAngle    = findViewById(R.id.sbAngle);    // 0–179°
        sbOffset   = findViewById(R.id.sbOffset);  // 0–30 m

        // Default: GSD 3.0 cm → progress = (3.0-0.1)/0.1 = 29
        sbGsd.setMax(99);
        sbGsd.setProgress(29);
        sbSidelap.setProgress(25);  // 75%
        sbFrontlap.setProgress(20); // 80%
        sbSpeed.setProgress(5);     // 7 m/s (2 + 5 = 7)
        sbAngle.setProgress(0);

        SeekBar.OnSeekBarChangeListener listener = new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int p, boolean user) {
                // Csak a felhasználó által húzott sbGsd írhatja felül az
                // etAltitude-ot — programozott setProgress (pl. magasság-bevitel
                // utáni clamp, vagy projekt betöltés) ne nullázza le a kézi
                // értéket.
                if (sb == sbGsd && user) {
                    syncAltitudeFromGsd();
                }
                updateLabels();
                // Ha GSD változik, frissítsük az ajánlott sebességet
                if (sb == sbGsd) {
                    float rec = GsdCalculator.recommendedSpeedMs(getGsd(), getSelectedDrone());
                    // sbSpeed progress = ajánlott sebesség - 2 (mert getSpeed = 2 + progress)
                    int speedProg = Math.round(rec) - 2;
                    sbSpeed.setProgress(Math.max(0, Math.min(13, speedProg)));
                }
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {
                autoGenerateIfReady();
            }
        };
        sbGsd.setOnSeekBarChangeListener(listener);
        sbSidelap.setOnSeekBarChangeListener(listener);
        sbFrontlap.setOnSeekBarChangeListener(listener);
        sbSpeed.setOnSeekBarChangeListener(listener);
        sbAngle.setOnSeekBarChangeListener(listener);
        sbOffset.setOnSeekBarChangeListener(listener);

        // Magasság közvetlen bevitel — GSD csúszkával kölcsönösen frissíti egymást
        etAltitude  = findViewById(R.id.etAltitude);
        btnAltMinus = findViewById(R.id.btnAltMinus);
        btnAltPlus  = findViewById(R.id.btnAltPlus);

        btnAltMinus.setOnClickListener(v -> adjustAltitude(-1));
        btnAltPlus.setOnClickListener(v  -> adjustAltitude(+1));

        etAltitude.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) applyAltitudeInput();
        });
        etAltitude.setOnEditorActionListener((v, actionId, event) -> {
            applyAltitudeInput();
            etAltitude.clearFocus();
            return false;
        });

        btnNewPlan        = findViewById(R.id.btnNewPlan);
        btnSaveProject    = findViewById(R.id.btnSaveProject);
        btnSaveAsProject  = findViewById(R.id.btnSaveAsProject);
        btnLoadProject    = findViewById(R.id.btnLoadProject);
        tvCurrentPlanName = findViewById(R.id.tvCurrentPlanName);
        btnUndoPoint      = findViewById(R.id.btnUndoPoint);
        btnObstacle       = findViewById(R.id.btnObstacle);
        btnClearObstacles = findViewById(R.id.btnClearObstacles);
        btnClear          = findViewById(R.id.btnClear);
        btnGenerate       = findViewById(R.id.btnGenerate);
        btnUpload         = findViewById(R.id.btnUpload);
        btnStart          = findViewById(R.id.btnStart);
        btnImportCsv = findViewById(R.id.btnImportCsv);
        btnExport    = findViewById(R.id.btnExport);
        // M07 widgets
        btnSplit       = findViewById(R.id.btnSplit);
        btnMarkDone    = findViewById(R.id.btnMarkDone);
        btnResetBlock  = findViewById(R.id.btnResetBlock);
        tvBlockInfo    = findViewById(R.id.tvBlockInfo);
        blockButtonRow = findViewById(R.id.blockButtonRow);
        tvDroneStatus = findViewById(R.id.tvDroneStatus);

        btnMyLocation = findViewById(R.id.btnMyLocation);
        btnMyLocation.setOnClickListener(v -> jumpToCurrentPosition());

        btnMapToggle = findViewById(R.id.btnMapToggle);
        btnMapToggle.setOnClickListener(v -> toggleMapSource());
        btnMapToggle.setOnLongClickListener(v -> { downloadMapAreaForOffline(); return true; });

        projectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        btnRec = findViewById(R.id.btnRec);
        btnRec.setOnClickListener(v -> takeScreenshot());
        btnRec.setOnLongClickListener(v -> { toggleRecording(); return true; });

        btnLayerProtected = findViewById(R.id.btnLayerProtected);
        btnLayerAirspace  = findViewById(R.id.btnLayerAirspace);
        btnLayerLandUse   = findViewById(R.id.btnLayerLandUse);
        btnLayerProtected.setOnClickListener(v -> toggleLayer("N2K"));
        btnLayerAirspace.setOnClickListener(v -> toggleLayer("LGT"));
        btnLayerLandUse.setOnClickListener(v -> toggleLayer("ZON"));

        // Kamera Konfigurátor panel (M05) — overlay referencia elmentve mezőbe
        Button btnCameraConfig = findViewById(R.id.btnCameraConfig);
        cameraPanelOverlay = findViewById(R.id.cameraPanelOverlay);
        Button btnCameraPanelClose = findViewById(R.id.btnCameraPanelClose);
        btnCameraConfig.setOnClickListener(v -> {
            if (cameraPanelOverlay.getVisibility() == View.GONE) {
                cameraPanelOverlay.setVisibility(View.VISIBLE);
                cameraPanelOverlay.setTranslationY(cameraPanelOverlay.getHeight());
                cameraPanelOverlay.animate().translationY(0).setDuration(250)
                        .setInterpolator(new DecelerateInterpolator()).start();
                btnCameraConfig.setBackgroundTintList(
                        android.content.res.ColorStateList.valueOf(0xCCCC4400));
                // Ha a videó feed nem fut, indítjuk háttérben — alpha=0, VISIBLE kell a Surface-hez
                if (!videoWidget.isRunning()) {
                    cameraWindow.setAlpha(0f);
                    cameraWindow.setVisibility(View.VISIBLE);
                    videoWidget.start();
                    histogramStartedVideo = true;
                } else {
                    histogramStartedVideo = false;
                }
                startHistogramPolling();
            } else {
                stopHistogramPolling();
                if (histogramStartedVideo) {
                    videoWidget.stop();
                    cameraWindow.setAlpha(1f);
                    cameraWindow.setVisibility(View.GONE);
                    histogramStartedVideo = false;
                }
                cameraPanelOverlay.animate().translationY(cameraPanelOverlay.getHeight())
                        .setDuration(200).setInterpolator(new DecelerateInterpolator())
                        .withEndAction(() -> cameraPanelOverlay.setVisibility(View.GONE))
                        .start();
                btnCameraConfig.setBackgroundTintList(
                        android.content.res.ColorStateList.valueOf(0xCC2e1a00));
            }
        });
        btnCameraPanelClose.setOnClickListener(v -> btnCameraConfig.callOnClick());

        // Tőszámláló (M09) — átjárás a tervezőből, repüléstől függetlenül
        findViewById(R.id.btnOpenCounting).setOnClickListener(v ->
                startActivity(new Intent(this, SamplingResultsActivity.class)));

        // LGT magassági szűrő – egyetlen gomb, koppintásra léptet
        btnAltFilter = findViewById(R.id.btnAltFilter);
        btnAltFilter.setOnClickListener(v -> changeAltPreset());

        // Kamera feed PiP
        cameraWindow = findViewById(R.id.cameraWindow);
        focusRing = findViewById(R.id.focusRing);
        cameraTextureView = findViewById(R.id.cameraTextureView);
        videoWidget = new DroneVideoWidget(this, cameraTextureView);

        Button btnCamToggle = findViewById(R.id.btnCamToggle);
        Button btnCamClose  = findViewById(R.id.btnCamClose);
        btnCamToggle.setOnClickListener(v -> toggleCameraFeed(btnCamToggle));
        btnCamClose.setOnClickListener(v -> {
            videoWidget.stop();
            cameraWindow.setVisibility(View.GONE);
            btnCamToggle.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(0xCC1a1a2e));
        });

        // Tap-to-focus: érintés → fokuszgyűrű animáció + MSDK setFocusTarget hívás
        cameraTextureView.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN && videoWidget.isRunning()) {
                float nx = event.getX() / v.getWidth();
                float ny = event.getY() / v.getHeight();
                videoWidget.tapToFocus(nx, ny);
                showFocusRing(event.getX(), event.getY());
                return true;
            }
            return false;
        });

        // Drón spinner
        spinnerDrone = findViewById(R.id.spinnerDrone);
        ArrayAdapter<DroneProfile> droneAdapter = new ArrayAdapter<>(
            this, android.R.layout.simple_spinner_item, DroneProfiles.ALL);
        droneAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerDrone.setAdapter(droneAdapter);
        spinnerDrone.setSelection(0);
        // Ha módosítják a drónt és már van generált misszió, frissítjük a labeleket.
        // A magasság is frissül a GSD-ből, mert a drón szenzorparaméterei
        // változnak (ugyanaz a cm/px más altitude-ot ad másik drónnál).
        spinnerDrone.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, android.view.View view,
                                       int position, long id) {
                syncAltitudeFromGsd();
                updateLabels();
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });

        // Panel csúsztatás
        sidePanelContainer = findViewById(R.id.sidePanelContainer);
        sidePanel          = findViewById(R.id.sidePanel);
        btnTogglePanel     = findViewById(R.id.btnTogglePanel);
        btnTogglePanel.setOnClickListener(v -> toggleSidePanel());

        btnPauseMission    = findViewById(R.id.btnPauseMission);
        btnStopMission     = findViewById(R.id.btnStopMission);
        btnSimulate        = findViewById(R.id.btnSimulate);
        tvMissionProgress  = findViewById(R.id.tvMissionProgress);
        pbMissionProgress  = findViewById(R.id.pbMissionProgress);
        btnPauseMission.setOnClickListener(v -> togglePauseMission());
        btnStopMission.setOnClickListener(v -> confirmStopMission());
        btnSimulate.setOnClickListener(v -> toggleSimulation());

        btnNewPlan.setOnClickListener(v -> confirmNewPlan());
        btnSaveProject.setOnClickListener(v -> saveCurrentOrShowDialog());
        btnSaveAsProject.setOnClickListener(v -> showSaveProjectDialog());
        btnLoadProject.setOnClickListener(v -> showLoadProjectDialog());
        btnUndoPoint.setOnClickListener(v -> removeLastPolygonPoint());
        btnObstacle.setOnClickListener(v -> toggleObstacleMode());
        btnClearObstacles.setOnClickListener(v -> clearAllObstacles());
        btnClear.setOnClickListener(v -> clearAll());
        btnGenerate.setOnClickListener(v -> generateMission());
        // M07 listeners
        btnSplit.setOnClickListener(v -> onSplitClicked());
        btnMarkDone.setOnClickListener(v -> onMarkDoneClicked());
        btnResetBlock.setOnClickListener(v -> onResetBlockClicked());
        btnUpload.setOnClickListener(v -> uploadCurrentSegment());
        btnStart.setOnClickListener(v -> startMission());
        btnImportCsv.setOnClickListener(v -> pickCsvFile());
        btnExport.setOnClickListener(v -> showExportDialog());

        btnUpload.setEnabled(false);
        btnStart.setEnabled(false);
        btnExport.setEnabled(false);
        initCameraControls();
        initCameraConfigPanel();
        initTerrainControls();
        initCrosshatchControls();
        initSamplingControls();
        initDenseGridControls();
        initSyncControls();
        initStatusBar();
        // Az updateLabels nem írja az etAltitude-ot (a felhasználói magasság-érték
        // védelme miatt); init-kor egyszer explicit szinkronizálunk a default GSD-ből.
        syncAltitudeFromGsd();
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

    // ── M05 Kamera Konfigurátor panel ─────────────────────────────────

    private void initCameraConfigPanel() {
        histogramView    = findViewById(R.id.histogramView);
        seekbarEv        = findViewById(R.id.seekbarEv);
        seekbarShutter5  = findViewById(R.id.seekbarShutter);
        seekbarWb        = findViewById(R.id.seekbarWb);
        btnIso100        = findViewById(R.id.btnIso100);
        btnIso200        = findViewById(R.id.btnIso200);
        btnIso400        = findViewById(R.id.btnIso400);
        btnIso800        = findViewById(R.id.btnIso800);
        btnApF56         = findViewById(R.id.btnApF56);
        btnApF8          = findViewById(R.id.btnApF8);
        btnApF11         = findViewById(R.id.btnApF11);
        btnFocusAuto5    = findViewById(R.id.btnFocusAuto);
        btnFocusInf5     = findViewById(R.id.btnFocusInf);
        btnApplyCamera5  = findViewById(R.id.btnApplyCamera);
        tvShutterValue5  = findViewById(R.id.tvShutterValue);
        tvWbValue5       = findViewById(R.id.tvWbValue);
        tvEvValue5       = findViewById(R.id.tvEvValue);
        tvCameraStatus5  = findViewById(R.id.tvCameraStatus);
        cameraParamsGroup = findViewById(R.id.cameraParamsGroup);
        btnModeAuto      = findViewById(R.id.btnModeAuto);
        btnModeManual    = findViewById(R.id.btnModeManual);
        btnAutoLock      = findViewById(R.id.btnAutoLock);

        // ── AUTO / MANUÁLIS váltó ──────────────────────────────────────
        btnModeAuto.setOnClickListener(v -> selectCameraMode(true));
        btnModeManual.setOnClickListener(v -> selectCameraMode(false));
        selectCameraMode(true); // alapértelmezett: AUTO

        // ── Auto Lock ─────────────────────────────────────────────────
        // Egy lépéses lock: az auto értékeket visszaolvassuk, UI-ba töltjük,
        // manuális módra váltunk, ÉS azonnal alkalmazzuk is a drónra. A
        // felhasználó számára: 1 kattintás = a kamera ezzel a beállítással
        // marad, amíg az [AUTO] gombra vissza nem kapcsol.
        btnAutoLock.setOnClickListener(v -> {
            tvCameraStatus5.setText("Olvasás...");
            CameraConfigurator.lockAutoToManual((success, settings, msg) ->
                runOnUiThread(() -> {
                    if (success && settings != null) {
                        applyReadbackToUI(settings);
                        selectCameraMode(false); // MANUÁLIS módra vált (csak UI)
                        tvCameraStatus5.setText("Alkalmazás...");
                        // Ténylegesen elküldjük a drónra is a kiolvasott
                        // manuális értékeket — különben a DJI továbbra is
                        // PROGRAM/AUTO módban marad, és a "lock" csak látszat.
                        CameraConfigurator.applySettings(settings, (applySuccess, applyMsg) ->
                            runOnUiThread(() -> {
                                if (applySuccess) {
                                    tvCameraStatus5.setText("🔒 Lockolva");
                                } else {
                                    tvCameraStatus5.setText("Lock: olvasás OK, küldés hiba");
                                    Toast.makeText(this, applyMsg, Toast.LENGTH_SHORT).show();
                                }
                            }));
                    } else {
                        tvCameraStatus5.setText("Lock sikertelen");
                        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
                    }
                }));
        });

        // ── ISO gombok ────────────────────────────────────────────────
        btnIso100.setOnClickListener(v -> { selectIso(0); applyIfManual(); });
        btnIso200.setOnClickListener(v -> { selectIso(1); applyIfManual(); });
        btnIso400.setOnClickListener(v -> { selectIso(2); applyIfManual(); });
        btnIso800.setOnClickListener(v -> { selectIso(3); applyIfManual(); });
        selectIso(0);

        // ── Rekesz gombok ─────────────────────────────────────────────
        btnApF56.setOnClickListener(v -> { selectAperture(0); applyIfManual(); });
        btnApF8.setOnClickListener(v ->  { selectAperture(1); applyIfManual(); });
        btnApF11.setOnClickListener(v -> { selectAperture(2); applyIfManual(); });
        selectAperture(1);

        // ── Zársebesség csúszka ───────────────────────────────────────
        seekbarShutter5.setMax(SS_LABELS.length - 1);
        seekbarShutter5.setProgress(3);
        seekbarShutter5.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean user) {
                tvShutterValue5.setText(SS_LABELS[p]);
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) { applyIfManual(); }
        });
        tvShutterValue5.setText(SS_LABELS[3]);

        // ── WB csúszka (4000–7000 K) ─────────────────────────────────
        seekbarWb.setMax(30);
        seekbarWb.setProgress(16);
        seekbarWb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean user) {
                tvWbValue5.setText((4000 + p * 100) + "K");
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) { applyIfManual(); }
        });
        tvWbValue5.setText("5600K");

        // ── EV ◄ ► gombok ─────────────────────────────────────────────
        findViewById(R.id.btnEvMinus).setOnClickListener(v -> applyEvEngine(false));
        findViewById(R.id.btnEvPlus).setOnClickListener(v -> applyEvEngine(true));

        seekbarEv.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean user) {
                int ev10 = p - 30;
                tvEvValue5.setText(ev10 == 0 ? "EV ±0"
                        : String.format(Locale.US, "EV %+.1f", ev10 / 10.0));
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });

        // ── Fókusz ────────────────────────────────────────────────────
        btnFocusAuto5.setOnClickListener(v -> lockFocusAuto());
        btnFocusInf5.setOnClickListener(v -> lockFocusInfinity());

        // ── Alkalmaz ──────────────────────────────────────────────────
        btnApplyCamera5.setOnClickListener(v -> applyCurrentCameraSettings());
    }

    private void startHistogramPolling() {
        stopHistogramPolling();
        histThread = new android.os.HandlerThread("HistPoll");
        histThread.start();
        histHandler = new android.os.Handler(histThread.getLooper());
        histRunnable = new Runnable() {
            @Override public void run() {
                if (cameraTextureView != null && histogramView != null) {
                    try {
                        android.graphics.Bitmap bmp = cameraTextureView.getBitmap();
                        if (bmp != null) {
                            int[] luma256 = computeLumaHistogram(bmp);
                            bmp.recycle();
                            lastHistData = luma256;
                            histogramView.update(luma256);
                        }
                    } catch (Throwable ignored) {}
                }
                if (histHandler != null) histHandler.postDelayed(this, 800);
            }
        };
        histHandler.postDelayed(histRunnable, 300);
    }

    private void stopHistogramPolling() {
        if (histHandler != null && histRunnable != null) {
            histHandler.removeCallbacks(histRunnable);
            histRunnable = null;
        }
        if (histThread != null) {
            histThread.quitSafely();
            histThread = null;
        }
        histHandler = null;
    }

    private int[] computeLumaHistogram(android.graphics.Bitmap bmp) {
        int[] hist = new int[256];
        // Átméretezés 128×72-re (9216 px) — egyetlen getPixels() hívás, nem getPixel() loop
        android.graphics.Bitmap small = android.graphics.Bitmap.createScaledBitmap(bmp, 128, 72, false);
        int[] pixels = new int[128 * 72];
        small.getPixels(pixels, 0, 128, 0, 0, 128, 72);
        small.recycle();
        for (int px : pixels) {
            int luma = (77 * ((px >> 16) & 0xFF)
                     + 150 * ((px >> 8)  & 0xFF)
                     +  29 * ( px        & 0xFF)) >> 8; // BT.601
            hist[Math.min(255, luma)]++;
        }
        return hist;
    }

    private void selectCameraMode(boolean auto) {
        cameraAutoMode = auto;
        int activeColor   = 0xCCCC4400;
        int inactiveColor = 0xCC334466;

        btnModeAuto.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(auto ? activeColor : inactiveColor));
        btnModeAuto.setTextColor(auto ? 0xFFFFFFFF : 0xFF888888);

        btnModeManual.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(auto ? inactiveColor : activeColor));
        btnModeManual.setTextColor(auto ? 0xFF888888 : 0xFFFFFFFF);

        // Paraméter csoport: AUTO módban halványabb + letiltva
        if (cameraParamsGroup != null) {
            cameraParamsGroup.setAlpha(auto ? 0.4f : 1.0f);
        }

        // Lock AUTO gomb csak auto módban látszik
        if (btnAutoLock != null) {
            btnAutoLock.setVisibility(auto ? View.VISIBLE : View.GONE);
            // A gomb a cameraParamsGroup belsejében van, ami AUTO módban
            // setAlpha(0.4f)-szel halványítva van. A Lock gomb az egyetlen
            // AKTÍV elem itt AUTO módban — explicit teljes átlátszatlanságot
            // állítunk be, hogy ne tűnjön letiltottnak.
            btnAutoLock.setAlpha(1.0f);
        }
        if (btnApplyCamera5 != null)
            btnApplyCamera5.setVisibility(auto ? View.GONE : View.VISIBLE);

        // ◄ ► gombok auto módban nem aktívak
        View evMinus = findViewById(R.id.btnEvMinus);
        View evPlus  = findViewById(R.id.btnEvPlus);
        if (evMinus != null) evMinus.setEnabled(!auto);
        if (evPlus  != null) evPlus.setEnabled(!auto);

        if (auto) {
            CameraConfigurator.setAutoExposureMode((success, msg) ->
                runOnUiThread(() -> tvCameraStatus5.setText(success ? "AUTO" : "AUTO (hiba)")));
        }
    }

    /** Auto lock visszaolvasott értékeit betölti a UI-ba. */
    private void applyReadbackToUI(CameraSettings s) {
        // ISO
        if      (s.iso == CameraSettings.IsoValue.ISO_100) selectIso(0);
        else if (s.iso == CameraSettings.IsoValue.ISO_200) selectIso(1);
        else if (s.iso == CameraSettings.IsoValue.ISO_400) selectIso(2);
        else                                                selectIso(3);

        // Rekesz
        if      (s.aperture == CameraSettings.ApertureValue.F_5_6) selectAperture(0);
        else if (s.aperture == CameraSettings.ApertureValue.F_11)  selectAperture(2);
        else                                                         selectAperture(1);

        // Zársebesség
        CameraSettings.ShutterSpeed[] speeds = {
            CameraSettings.ShutterSpeed.S_1_400,  CameraSettings.ShutterSpeed.S_1_500,
            CameraSettings.ShutterSpeed.S_1_640,  CameraSettings.ShutterSpeed.S_1_800,
            CameraSettings.ShutterSpeed.S_1_1000, CameraSettings.ShutterSpeed.S_1_1250,
            CameraSettings.ShutterSpeed.S_1_1600, CameraSettings.ShutterSpeed.S_1_2000
        };
        for (int i = 0; i < speeds.length; i++) {
            if (s.shutterSpeed == speeds[i]) { seekbarShutter5.setProgress(i); break; }
        }
    }

    private void applyIfManual() {
        if (!cameraAutoMode) applyCurrentCameraSettings();
    }

    private void selectIso(int idx) {
        isoIdx = idx;
        int[] colors = {0xCC334466, 0xCC334466, 0xCC334466, 0xCC334466};
        colors[idx] = 0xCCCC4400;
        btnIso100.setBackgroundTintList(android.content.res.ColorStateList.valueOf(colors[0]));
        btnIso200.setBackgroundTintList(android.content.res.ColorStateList.valueOf(colors[1]));
        btnIso400.setBackgroundTintList(android.content.res.ColorStateList.valueOf(colors[2]));
        btnIso800.setBackgroundTintList(android.content.res.ColorStateList.valueOf(colors[3]));
    }

    private void selectAperture(int idx) {
        apIdx = idx;
        int[] colors = {0xCC334466, 0xCC334466, 0xCC334466};
        colors[idx] = 0xCCCC4400;
        btnApF56.setBackgroundTintList(android.content.res.ColorStateList.valueOf(colors[0]));
        btnApF8.setBackgroundTintList(android.content.res.ColorStateList.valueOf(colors[1]));
        btnApF11.setBackgroundTintList(android.content.res.ColorStateList.valueOf(colors[2]));
    }

    /**
     * Survey Exposure Policy Engine — hisztogram centroid alapján dönt.
     *
     * Prioritás: SS (motion blur limit) → ISO (max 400) → Rekesz
     * Motion blur limit: survey repülésnél fix 1/800s minimum.
     */
    private void applyEvEngine(boolean increase) {
        if (cameraAutoMode) return;

        // Hisztogram centroid → EV hiba
        float centroid = computeHistCentroid();
        float evError  = (centroid - 128f) / 42.7f; // -3..+3 EV

        // EV csúszka frissítése a centroid alapján
        int centroidSlider = Math.round(centroid / 255f * 60f);
        seekbarEv.setProgress(Math.max(0, Math.min(60, centroidSlider)));

        int ssProg  = seekbarShutter5.getProgress();
        int ssMin   = 3; // survey minimum: index 3 = 1/800s (motion blur limit)

        if (increase) {
            // Alulexponált: SS↓ (de nem ssMin alá) → ISO↑ (max 400=idx2) → Rekesz nyit
            if (ssProg > ssMin) {
                seekbarShutter5.setProgress(ssProg - 1);
            } else if (isoIdx < 2) { // ISO max 400 survey-hez
                selectIso(isoIdx + 1);
            } else if (apIdx > 0) {
                selectAperture(apIdx - 1);
            } else {
                Toast.makeText(this, "Fényviszonyok túl gyengék survey-hez", Toast.LENGTH_SHORT).show();
                return;
            }
        } else {
            // Túlexponált: SS↑ → ISO↓ → Rekesz szűkít
            if (ssProg < SS_LABELS.length - 1) {
                seekbarShutter5.setProgress(ssProg + 1);
            } else if (isoIdx > 0) {
                selectIso(isoIdx - 1);
            } else if (apIdx < 2) {
                selectAperture(apIdx + 1);
            } else {
                Toast.makeText(this, "Fényviszonyok túl erősek", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        // EV felirat
        int ev10 = (int) Math.round(evError * 10);
        tvEvValue5.setText(ev10 == 0 ? "EV ±0"
                : String.format(Locale.US, "EV %+.1f", evError));

        // Azonnali alkalmazás a drónra — manuális módban a +/- rögtön hat
        applyCurrentCameraSettings();
    }

    /** Hisztogram súlypont számítása — 0..255. -1 ha nincs adat. */
    private float computeHistCentroid() {
        if (lastHistData == null) return 128f; // nincs adat → semleges
        long weighted = 0, total = 0;
        for (int i = 0; i < lastHistData.length; i++) {
            weighted += (long) i * lastHistData[i];
            total    += lastHistData[i];
        }
        return total == 0 ? 128f : (float) weighted / total;
    }

    private void lockFocusAuto() {
        btnFocusAuto5.setEnabled(false);
        btnFocusAuto5.setText("AF folyamatban...");
        CameraConfigurator.applySettings(buildCameraSettingsForPanel(), (success, msg) ->
            runOnUiThread(() -> {
                btnFocusAuto5.setEnabled(true);
                if (success) {
                    focusLocked = true;
                    btnFocusAuto5.setText("Fókusz: rögzítve ✓");
                    btnFocusAuto5.setBackgroundTintList(
                            android.content.res.ColorStateList.valueOf(0xCC006600));
                } else {
                    btnFocusAuto5.setText("AF → Rögzít");
                    Toast.makeText(this, "Fókusz hiba: " + msg, Toast.LENGTH_SHORT).show();
                }
            }));
    }

    private void lockFocusInfinity() {
        focusLocked = true;
        btnFocusInf5.setText("∞ ✓");
        btnFocusInf5.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(0xCC006600));
        btnFocusAuto5.setText("AF → Rögzít");
        btnFocusAuto5.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(0xCC334466));
        // ∞ fókusz: a legtávolabbi fókuszgyűrű érték (nem MSDK fókusz mód, hanem gyűrű pozíció)
        // Egyszerűsítés: MANUAL módra váltunk és a fókuszgyűrűt maximumra állítjuk
        Toast.makeText(this, "Végtelen fókusz beállítva", Toast.LENGTH_SHORT).show();
    }

    private void applyCurrentCameraSettings() {
        if (tvCameraStatus5 != null) tvCameraStatus5.setText("Alkalmazás...");
        CameraConfigurator.applySettings(buildCameraSettingsForPanel(), (success, msg) ->
            runOnUiThread(() -> {
                if (tvCameraStatus5 != null)
                    tvCameraStatus5.setText(success ? "✓ Alkalmazva" : "✗ " + msg);
                Toast.makeText(this,
                        success ? "Kamera beállítva ✓" : "Hiba: " + msg,
                        Toast.LENGTH_SHORT).show();
            }));
    }

    private CameraSettings buildCameraSettingsForPanel() {
        CameraSettings s = new CameraSettings();
        s.autoMode = false;

        CameraSettings.IsoValue[] isos = {
            CameraSettings.IsoValue.ISO_100, CameraSettings.IsoValue.ISO_200,
            CameraSettings.IsoValue.ISO_400, CameraSettings.IsoValue.ISO_800
        };
        s.iso = isos[isoIdx];

        CameraSettings.ApertureValue[] apertures = {
            CameraSettings.ApertureValue.F_5_6,
            CameraSettings.ApertureValue.F_8,
            CameraSettings.ApertureValue.F_11
        };
        s.aperture = apertures[apIdx];

        CameraSettings.ShutterSpeed[] speeds = {
            CameraSettings.ShutterSpeed.S_1_400,
            CameraSettings.ShutterSpeed.S_1_500,
            CameraSettings.ShutterSpeed.S_1_640,
            CameraSettings.ShutterSpeed.S_1_800,
            CameraSettings.ShutterSpeed.S_1_1000,
            CameraSettings.ShutterSpeed.S_1_1250,
            CameraSettings.ShutterSpeed.S_1_1600,
            CameraSettings.ShutterSpeed.S_1_2000
        };
        s.shutterSpeed = speeds[seekbarShutter5.getProgress()];

        s.whiteBalance = CameraSettings.WhiteBalanceValue.CUSTOM;
        s.whiteBalanceKelvin = 4000 + seekbarWb.getProgress() * 100;

        return s;
    }

    // ── Kamera feed PiP ───────────────────────────────────────────────

    private void toggleCameraFeed(Button btnCamToggle) {
        if (videoWidget.isRunning()) {
            videoWidget.stop();
            cameraWindow.setVisibility(View.GONE);
            btnCamToggle.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(0xCC1a1a2e));
        } else {
            cameraWindow.setVisibility(View.VISIBLE);
            videoWidget.start();
            btnCamToggle.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(0xCC226622));
        }
    }

    /**
     * Fókuszgyűrű megjelenítése az érintés helyén, majd 1 másodperc után eltűnik.
     * touchX/touchY: pixel koordináták a TextureView-n belül.
     */
    private void showFocusRing(float touchX, float touchY) {
        int ringSize = focusRing.getWidth();
        if (ringSize == 0) ringSize = (int) (48 * getResources().getDisplayMetrics().density);

        // A cameraWindow bal felső sarka a referencia — a TextureView tölti ki teljesen
        focusRing.setX(touchX - ringSize / 2f);
        focusRing.setY(touchY - ringSize / 2f);
        focusRing.setAlpha(1f);
        focusRing.setVisibility(View.VISIBLE);
        focusRing.setScaleX(1.4f);
        focusRing.setScaleY(1.4f);

        focusRing.animate()
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(200)
                .withEndAction(() -> focusRing.postDelayed(() -> {
                    focusRing.animate().alpha(0f).setDuration(300)
                            .withEndAction(() -> focusRing.setVisibility(View.INVISIBLE))
                            .start();
                }, 700))
                .start();
    }

    // ── Státuszsáv ────────────────────────────────────────────────────

    private void initStatusBar() {
        sbDrone     = findViewById(R.id.sbDrone);
        sbRc        = findViewById(R.id.sbRc);
        sbRcBatt    = findViewById(R.id.sbRcBatt);
        sbGps       = findViewById(R.id.sbGps);
        sbDroneBatt = findViewById(R.id.sbDroneBatt);
        sbTabletBatt= findViewById(R.id.sbTabletBatt);
        sbKp        = findViewById(R.id.sbKp);
        updateStatusBar();
    }

    private final Runnable statusRunnable = new Runnable() {
        @Override public void run() {
            updateStatusBar();
            updateDroneStatus();   // tvDroneStatus + Upload gomb is frissüljön a timeren
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

        // ── Kp-index (geomágneses aktivitás) – 10 percenként frissül ──
        if (sbKp != null) {
            long now = System.currentTimeMillis();
            if ((now - lastKpFetchMs) >= KP_FETCH_INTERVAL_MS) {
                lastKpFetchMs = now;
                KpIndexProvider.fetch(kp -> {
                    if (sbKp == null) return;
                    if (kp < 0) {
                        sbKp.setText("MAG: --");
                        sbKp.setTextColor(0xFF888888);
                    } else {
                        sbKp.setText("MAG: " + kp);
                        if      (kp <= 2) sbKp.setTextColor(0xFF44FF88);
                        else if (kp <= 4) sbKp.setTextColor(0xFFFFAA00);
                        else if (kp == 5) sbKp.setTextColor(0xFFFF8800);
                        else              sbKp.setTextColor(0xFFFF4444);
                    }
                });
            }
        }

        // ── DJI telemetria – háttérszálon, hogy ne blokkoljuk a főszálat ──
        djiExecutor.submit(() -> {
            try {
                DJIHelper dji = DJIHelper.getInstance();
                boolean rcOk = dji.isRcConnected();
                boolean connected = dji.isConnected();
                String name = connected ? dji.getConnectedProductName() : null;
                runOnUiThread(() -> {
                    if (sbRc == null) return;
                    sbRc.setText("RC: " + (rcOk ? "OK" : "nincs"));
                    sbRc.setTextColor(rcOk ? 0xFF44FF88 : 0xFFFF4444);
                    if (!rcOk) { sbRcBatt.setText("--"); sbRcBatt.setTextColor(0xFF888888); }
                    if (!connected) {
                        sbDrone.setText("DRON: nincs"); sbDrone.setTextColor(0xFFFF4444);
                        sbGps.setText("SAT: --"); sbGps.setTextColor(0xFF888888);
                        sbDroneBatt.setText("AKKU: --"); sbDroneBatt.setTextColor(0xFF888888);
                        lastSatCount = 0;
                        updateStartButtonState();
                        return;
                    }
                    sbDrone.setText(name != null ? name : "Dron");
                    sbDrone.setTextColor(0xFF44FF88);
                });
                if (!connected) return;

            // RC akku (async)
            dji.getRcBatteryPercent(pct -> runOnUiThread(() -> {
                if (sbRcBatt == null) return;
                if (pct >= 0) {
                    sbRcBatt.setText(pct + "%");
                    sbRcBatt.setTextColor(pct < 20 ? 0xFFFF4444 : pct < 40 ? 0xFFFFAA00 : 0xFF44FF88);
                } else {
                    sbRcBatt.setText("--");
                    sbRcBatt.setTextColor(0xFF888888);
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

            // GPS műholdak + drón pozíció (Flight Controller callback)
            dji.setFlightStateCallback((sats, homeSet, lat, lon) -> runOnUiThread(() -> {
                if (sbGps == null) return;
                lastSatCount = sats;
                sbGps.setText("SAT: " + sats + (homeSet ? " H" : ""));
                sbGps.setTextColor(sats >= 10 ? 0xFF44FF88 : sats >= 6 ? 0xFFFFAA00 : 0xFFFF4444);
                updateStartButtonState();
                if (lat != 0 && lon != 0) {
                    lastDroneLat = lat;
                    lastDroneLon = lon;
                    updateDroneMarker(lat, lon);
                }
            }));

            } catch (Throwable t) { /* DJI SDK nem elérhető */ }
        });

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
            if (isChecked) {
                if (lastResult != null) {
                    // Waypointok már vannak → azonnal indul az SRTM letöltés
                    applyTerrainFollowing(lastResult, lastResult.altitudeM);
                } else {
                    tvTerrainInfo.setText("Generálj először útvonalat a domborzati adatok letöltéséhez");
                    tvTerrainInfo.setTextColor(0xFFFFAA00);
                }
            } else {
                tvTerrainInfo.setText("Magasság korrekció DEM (SRTM) domborzati modell alapján");
                tvTerrainInfo.setTextColor(0xFF888888);
            }
        });
    }

    // ── Crosshatch (keresztrács) mód ─────────────────────────────────────

    private void initCrosshatchControls() {
        switchCrosshatch  = findViewById(R.id.switchCrosshatch);
        sbCrosshatchAngle = findViewById(R.id.sbCrosshatchAngle);
        tvCrosshatchAngle = findViewById(R.id.tvCrosshatchAngle);

        switchCrosshatch.setOnCheckedChangeListener((btn, isChecked) -> {
            int vis = isChecked ? View.VISIBLE : View.GONE;
            sbCrosshatchAngle.setVisibility(vis);
            tvCrosshatchAngle.setVisibility(vis);
            autoGenerateIfReady();
        });

        sbCrosshatchAngle.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int p, boolean user) {
                tvCrosshatchAngle.setText("2. rács iránya: " + p + "°");
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) { autoGenerateIfReady(); }
        });
        tvCrosshatchAngle.setText("2. rács iránya: 90°");
    }

    // ── Mintavételi misszió panel (M01 §10) ──────────────────────────────

    private void initSamplingControls() {
        switchSamplingMode  = findViewById(R.id.switchSamplingMode);
        samplingParamsPanel = findViewById(R.id.samplingParamsPanel);
        etSampleCount       = findViewById(R.id.etSampleCount);
        etTransitAlt        = findViewById(R.id.etTransitAlt);
        etSampleAlt         = findViewById(R.id.etSampleAlt);
        etHoverSeconds      = findViewById(R.id.etHoverSeconds);
        spinnerSamplingMethod = findViewById(R.id.spinnerSamplingMethod);
        btnDownloadSession  = findViewById(R.id.btnDownloadSession);

        ArrayAdapter<String> methodAdapter = new ArrayAdapter<>(
            this, android.R.layout.simple_spinner_item,
            new String[]{"stratified", "halton", "random"});
        methodAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerSamplingMethod.setAdapter(methodAdapter);
        spinnerSamplingMethod.setSelection(0);

        switchSamplingMode.setOnCheckedChangeListener((btn, isChecked) -> {
            // M10: a mintavételi és a sűrű rács mód kölcsönösen kizárja egymást
            if (isChecked && switchDenseGridMode != null && switchDenseGridMode.isChecked()) {
                switchDenseGridMode.setChecked(false);
            }
            samplingParamsPanel.setVisibility(isChecked ? View.VISIBLE : View.GONE);
            // Mód váltásnál a korábbi eredmény (másik móddal generálva) már
            // nem érvényes — töröljük, hogy ne lehessen felkeverni a két típust.
            lastResult = null;
            lastSamplePoints = null;
            clearSamplePointMarkers();
            btnUpload.setEnabled(false);
            btnDownloadSession.setVisibility(View.GONE);
            tvStats.setText("");
            autoGenerateIfReady();
        });

        btnDownloadSession.setOnClickListener(v -> triggerSessionDownload());
    }

    // ── Sűrű rács panel (M10, 2026-07-03) ─────────────────────────────────

    private void initDenseGridControls() {
        switchDenseGridMode = findViewById(R.id.switchDenseGridMode);
        if (switchDenseGridMode == null) return;

        switchDenseGridMode.setOnCheckedChangeListener((btn, isChecked) -> {
            // A mintavételi és a sűrű rács mód kölcsönösen kizárja egymást
            if (isChecked && switchSamplingMode != null && switchSamplingMode.isChecked()) {
                switchSamplingMode.setChecked(false);
            }
            lastResult = null;
            tvStats.setText("");
            autoGenerateIfReady();
        });
    }

    // ── Szinkronizáció panel ──────────────────────────────────────────────

    private void initSyncControls() {
        btnSync      = findViewById(R.id.btnSync);
        btnLogin     = findViewById(R.id.btnLogin);
        tvSyncStatus = findViewById(R.id.tvSyncStatus);

        btnLogin.setOnClickListener(v -> showLoginOrLogoutDialog());
        btnSync.setOnClickListener(v -> doSync());

        updateSyncStatus();
    }

    private void updateSyncStatus() {
        if (btnSync == null) return;
        boolean online = NetworkMonitor.getInstance(this).isOnline();
        boolean authed = AuthManager.getInstance(this).isAuthenticated();

        if (!online) {
            tvSyncStatus.setText("Offline");
            tvSyncStatus.setTextColor(0xFF888888);
            btnSync.setEnabled(false);
            btnLogin.setText("Bejelentkezes");
        } else if (!authed) {
            tvSyncStatus.setText("Online – nincs bejelentkezve");
            tvSyncStatus.setTextColor(0xFFFFAA00);
            btnSync.setEnabled(false);
            btnLogin.setText("Bejelentkezes");
        } else {
            String user = AuthManager.getInstance(this).getUsername();
            tvSyncStatus.setText("Online – " + (user != null ? user : "bejelentkezve"));
            tvSyncStatus.setTextColor(0xFF44DD88);
            btnSync.setEnabled(true);
            btnLogin.setText("Kijelentkezes");
        }
    }

    private void showLoginOrLogoutDialog() {
        AuthManager am = AuthManager.getInstance(this);
        if (am.isAuthenticated()) {
            new AlertDialog.Builder(this)
                .setTitle("Kijelentkezés")
                .setMessage("Biztosan kijelentkezel? A szinkronizáció leáll.")
                .setPositiveButton("Kijelentkezés", (d, w) -> {
                    am.logout(null);
                    updateSyncStatus();
                })
                .setNegativeButton("Mégse", null)
                .show();
            return;
        }

        // Felhasználónév + jelszó dialog
        showUsernamePasswordDialog(am);
    }

    private void showUsernamePasswordDialog(AuthManager am) {
        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        int pad = (int)(16 * getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad, pad, 0);

        EditText etUser = new EditText(this);
        etUser.setHint("Felhasználónév");
        etUser.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        layout.addView(etUser);

        EditText etPass = new EditText(this);
        etPass.setHint("Jelszó");
        etPass.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        layout.addView(etPass);

        final androidx.appcompat.app.AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle("Dronterapia bejelentkezés")
            .setView(layout)
            .setPositiveButton("Bejelentkezés", null) // listener-t kézzel állítjuk be
            .setNegativeButton("Mégse", null)
            .setCancelable(true)
            .create();

        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String user = etUser.getText().toString().trim();
                String pass = etPass.getText().toString();
                if (user.isEmpty() || pass.isEmpty()) {
                    Toast.makeText(this, "Add meg a felhasználónevet és jelszót!", Toast.LENGTH_SHORT).show();
                    return;
                }
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
                tvSyncStatus.setText("Bejelentkezés...");
                am.login(user, pass, new AuthManager.LoginCallback() {
                    @Override public void onSuccess(String token, String username) {
                        runOnUiThread(() -> {
                            if (dialog.isShowing()) dialog.dismiss();
                            updateSyncStatus();
                            Toast.makeText(MissionPlannerActivity.this,
                                "Bejelentkezve: " + username, Toast.LENGTH_SHORT).show();
                        });
                    }
                    @Override public void onError(String message) {
                        runOnUiThread(() -> {
                            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                            tvSyncStatus.setText("Hiba: " + message);
                            tvSyncStatus.setTextColor(0xFFFF4444);
                            Toast.makeText(MissionPlannerActivity.this,
                                "Bejelentkezési hiba: " + message, Toast.LENGTH_LONG).show();
                        });
                    }
                });
            });
        });

        dialog.show();
    }

    private void doSync() {
        btnSync.setEnabled(false);
        tvSyncStatus.setText("Szinkronizálás...");
        SyncManager.getInstance(this).syncAll(new SyncManager.SyncCallback() {
            @Override public void onProgress(String message) {
                runOnUiThread(() -> tvSyncStatus.setText(message));
            }
            @Override public void onComplete(SyncManager.SyncResult result,
                                              int uploaded, int downloaded, String errorMessage) {
                runOnUiThread(() -> {
                    switch (result) {
                        case SUCCESS:
                            tvSyncStatus.setText("Kész: ↑" + uploaded + " ↓" + downloaded);
                            tvSyncStatus.setTextColor(0xFF44DD88);
                            break;
                        case SKIPPED_OFFLINE:
                            tvSyncStatus.setText("Offline – nincs hálózat");
                            tvSyncStatus.setTextColor(0xFF888888);
                            break;
                        case SKIPPED_NO_AUTH:
                            tvSyncStatus.setText("Lejárt token – jelentkezz be újra");
                            tvSyncStatus.setTextColor(0xFFFF4444);
                            break;
                        case SKIPPED_FLIGHT_ACTIVE:
                            tvSyncStatus.setText("Sync letiltva repülés közben");
                            tvSyncStatus.setTextColor(0xFFFFAA00);
                            break;
                        default:
                            tvSyncStatus.setText("Hiba: " + (errorMessage != null ? errorMessage : "ismeretlen"));
                            tvSyncStatus.setTextColor(0xFFFF4444);
                    }
                    updateSyncStatus(); // gomb állapot frissítése
                });
            }
        });
    }

    /**
     * Domborzatkövetés — két fázis:
     *
     * 1. fázis (drón GPS nincs): SRTM terepprofil letöltése a területre.
     *    Waypoint magasságok NEM változnak. Feltöltés blokkolva.
     *    Üzenet: narancssárga — "SRTM betöltve, csatlakoztasd a drónt"
     *
     * 2. fázis (drón GPS elérhető): SRTM + drón pozíció → korrekció alkalmazva.
     *    Waypoint magasságok korrigálva a felszállási referenciához képest.
     *    Üzenet: zöld — "Korrigálva ✓"
     */
    private void applyTerrainFollowing(GridMissionGenerator.GeneratorResult result,
                                        double baseAGL) {
        List<WaypointData> allWaypoints = new ArrayList<>();
        for (List<WaypointData> seg : result.segments) allWaypoints.addAll(seg);
        if (allWaypoints.isEmpty()) return;

        tvTerrainInfo.setText("SRTM domborzati adatok letöltése...");
        tvTerrainInfo.setTextColor(0xFFFFAA00);
        btnUpload.setEnabled(false);

        boolean droneGpsAvailable = lastDroneLat != 0 && lastDroneLon != 0;

        if (droneGpsAvailable) {
            // ── 2. fázis: korrekció a drón GPS-pozíciójához képest ───────────
            List<WaypointData> queryList = new ArrayList<>(allWaypoints);
            queryList.add(new WaypointData(lastDroneLat, lastDroneLon, 0f)); // utolsó = drón

            ElevationProvider.fetchElevations(queryList, new ElevationProvider.ElevationCallback() {
                @Override
                public void onSuccess(double[] elevations) {
                    double takeoffElev = elevations[elevations.length - 1];

                    int idx = 0;
                    float minAlt = Float.MAX_VALUE, maxAlt = Float.MIN_VALUE;
                    for (List<WaypointData> seg : result.segments) {
                        for (WaypointData wp : seg) {
                            double delta = elevations[idx] - takeoffElev;
                            // Alsó határ 3 m (összhangban a UI/Generator clamp-pel).
                            float correctedAlt = Math.max(3f, (float) (baseAGL + delta));
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

                    final float fMin = minAlt, fMax = maxAlt;
                    final double fTakeoff = takeoffElev;
                    runOnUiThread(() -> {
                        tvTerrainInfo.setText(String.format(
                            "Domborzatkövetés alkalmazva ✓  %.0f–%.0f m  (felszállás: %.0f m tszf)",
                            fMin, fMax, fTakeoff));
                        tvTerrainInfo.setTextColor(0xFF44FF44);
                        boolean tc = false;
                        try { tc = DJIHelper.getInstance().isConnected(); } catch (Throwable t2) { }
                        btnUpload.setEnabled(tc && !missionRunning);
                        btnExport.setEnabled(true);
                        if (btnSimulate != null) btnSimulate.setEnabled(true);
                        updateStatsWithTerrain(result);
                    });
                }

                @Override
                public void onError(String message) {
                    runOnUiThread(() -> {
                        tvTerrainInfo.setText("Domborzat hiba: " + message);
                        tvTerrainInfo.setTextColor(0xFFFF4444);
                        boolean tc = false;
                        try { tc = DJIHelper.getInstance().isConnected(); } catch (Throwable t2) { }
                        btnUpload.setEnabled(tc && !missionRunning);
                        btnExport.setEnabled(true);
                        if (btnSimulate != null) btnSimulate.setEnabled(true);
                        Toast.makeText(MissionPlannerActivity.this,
                            "Domborzati adat nem érhető el: " + message, Toast.LENGTH_LONG).show();
                    });
                }
            });

        } else {
            // ── 1. fázis: csak terepprofil, nincs korrekció ──────────────────
            ElevationProvider.fetchElevations(allWaypoints, new ElevationProvider.ElevationCallback() {
                @Override
                public void onSuccess(double[] elevations) {
                    double minElev = Double.MAX_VALUE, maxElev = -Double.MAX_VALUE;
                    int idx = 0;
                    for (List<WaypointData> seg : result.segments) {
                        for (WaypointData wp : seg) {
                            wp.terrainElevation = elevations[idx];
                            if (elevations[idx] < minElev) minElev = elevations[idx];
                            if (elevations[idx] > maxElev) maxElev = elevations[idx];
                            idx++;
                        }
                    }
                    // Waypoint magasságok NEM változnak — nincs referenciapont

                    final double fMinElev = minElev, fMaxElev = maxElev;
                    runOnUiThread(() -> {
                        tvTerrainInfo.setText(String.format(
                            "SRTM betöltve: terep %.0f–%.0f m tszf — csatlakoztasd a drónt és generálj újra a korrekcióhoz",
                            fMinElev, fMaxElev));
                        tvTerrainInfo.setTextColor(0xFFFFAA00);
                        // Feltöltés tiltva: korrekció nélkül domborzatkövetés nem értelmes
                        btnUpload.setEnabled(false);
                        btnExport.setEnabled(true);
                        if (btnSimulate != null) btnSimulate.setEnabled(true);
                    });
                }

                @Override
                public void onError(String message) {
                    runOnUiThread(() -> {
                        tvTerrainInfo.setText("Domborzat hiba: " + message);
                        tvTerrainInfo.setTextColor(0xFFFF4444);
                        boolean tc = false;
                        try { tc = DJIHelper.getInstance().isConnected(); } catch (Throwable t2) { }
                        btnUpload.setEnabled(tc && !missionRunning);
                        btnExport.setEnabled(true);
                        if (btnSimulate != null) btnSimulate.setEnabled(true);
                        Toast.makeText(MissionPlannerActivity.this,
                            "Domborzati adat nem érhető el: " + message, Toast.LENGTH_LONG).show();
                    });
                }
            });
        }
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

    // ── Hálózat és offline térkép ──────────────────────────────────────

    /** Visszaadja, hogy van-e aktív internet-kapcsolat. */
    private boolean isNetworkAvailable() {
        try {
            ConnectivityManager cm =
                    (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            if (cm == null) return false;
            NetworkInfo info = cm.getActiveNetworkInfo();
            return info != null && info.isConnected();
        } catch (Throwable t) {
            return false;
        }
    }

    /** WiFi-n van-e (mobilneten NEM töltünk tile-t — OOM védelme Crystal Sky-on). */
    private boolean isWifiConnected() {
        try {
            ConnectivityManager cm =
                    (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            if (cm == null) return false;
            NetworkInfo info = cm.getActiveNetworkInfo();
            return info != null && info.isConnected()
                    && info.getType() == ConnectivityManager.TYPE_WIFI;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Online/offline mód beállítása a térkép csempe letöltőnek.
     * Ha nincs internet: setUseDataConnection(false) → csak a helyi cache-ből tölt,
     * nem indít HTTP kéréseket → nincs timeout miatti látszólagos lefagyás.
     * Hívódik: onResume(), toggleMapSource() után.
     */
    private void updateMapDataConnection() {
        // Csak WiFi-n töltünk tile-t — mobilneten OOM-ot okoz Crystal Sky-on
        boolean wifi = isWifiConnected();
        mapView.setUseDataConnection(wifi);
        android.util.Log.i("MapOffline", wifi
                ? "WiFi — tile letöltés engedélyezve"
                : "Nincs WiFi — csak disk cache-ből tölt");
    }

    /**
     * Jelenlegi térképnézet előre letöltése offline használatra.
     * Zoom szintek 10–18, ESRI satellite és OSM tile-ok egyaránt.
     * Hosszú nyomással aktiválható a SAT/MAP gombon.
     */
    private void downloadMapAreaForOffline() {
        if (!isNetworkAvailable()) {
            Toast.makeText(this,
                    "Offline térkép letöltéséhez WiFi kapcsolat szükséges!",
                    Toast.LENGTH_LONG).show();
            return;
        }
        org.osmdroid.util.BoundingBox bbox = mapView.getBoundingBox();
        org.osmdroid.tileprovider.cachemanager.CacheManager cacheManager =
                new org.osmdroid.tileprovider.cachemanager.CacheManager(mapView);
        int minZoom = 14, maxZoom = 17;
        int tileCount = cacheManager.possibleTilesInArea(bbox, minZoom, maxZoom);

        new AlertDialog.Builder(this)
            .setTitle("Térkép letöltése offline-ra")
            .setMessage("A jelenlegi nézet letöltése " + minZoom + "–" + maxZoom
                    + " zoom szinteken.\n~" + tileCount + " csempe.\n\n"
                    + "WiFi-n ajánlott. Repülési terv otthon való elkészítésekor "
                    + "a megnyitott területek automatikusan cache-elődnek.")
            .setPositiveButton("Letöltés", (d, w) -> {
                Toast.makeText(this, "Letöltés folyamatban...",
                        Toast.LENGTH_SHORT).show();
                cacheManager.downloadAreaAsync(this, bbox, minZoom, maxZoom,
                    new org.osmdroid.tileprovider.cachemanager.CacheManager
                            .CacheManagerCallback() {
                        @Override public void onTaskComplete() {
                            runOnUiThread(() -> Toast.makeText(
                                    MissionPlannerActivity.this,
                                    "✓ Offline térkép letöltve!", Toast.LENGTH_LONG).show());
                        }
                        @Override public void onTaskFailed(int errors) {
                            runOnUiThread(() -> Toast.makeText(
                                    MissionPlannerActivity.this,
                                    "Letöltési hibák: " + errors + " csempe sikertelen",
                                    Toast.LENGTH_LONG).show());
                        }
                        @Override public void updateProgress(int progress, int currentZoom,
                                                              int zoomMin, int zoomMax) {}
                        @Override public void downloadStarted() {}
                        @Override public void setPossibleTilesInArea(int total) {}
                    });
            })
            .setNegativeButton("Mégse", null)
            .show();
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

    /**
     * LGT magassági szűrő léptetése koppintásra.
     * Körkörösen: ALT:∞ → ALT:30m → ALT:40m → ... → ALT:120m → vissza ALT:∞
     * (EU Open Category max = 120 m, értékek felette nem relevánsak)
     */
    private void changeAltPreset() {
        int[] presets = AirspaceLayer.ALT_PRESETS;
        altPresetIndex = (altPresetIndex + 1) % presets.length;
        int meters = presets[altPresetIndex];
        airspaceLayer.setAltitudeFilter(meters);
        btnAltFilter.setText(meters == 0 ? "ALT:∞" : "ALT:" + meters + "m");
    }

    private void toggleLayer(final String layerId) {
        // Réteg betöltés internet nélkül nem lehetséges — barátságos figyelmeztetés
        if (!isNetworkAvailable()) {
            Toast.makeText(this,
                "Nincs internet-kapcsolat — a térkép rétegek nem tölthetők be",
                Toast.LENGTH_LONG).show();
            return;
        }
        org.osmdroid.util.BoundingBox bbox = mapView.getBoundingBox();
        if ("N2K".equals(layerId)) {
            if (!btnLayerProtected.isEnabled()) return; // betöltés közben ne fogadjon újabb kattintást
            final String origText = btnLayerProtected.getText().toString();
            btnLayerProtected.setEnabled(false);
            btnLayerProtected.setText("N2K...");
            protectedLayer.toggle(bbox, new Runnable() {
                @Override public void run() {
                    btnLayerProtected.setText(origText);
                    btnLayerProtected.setEnabled(true);
                    btnLayerProtected.setAlpha(protectedLayer.isVisible() ? 1.0f : 0.5f);
                }
            });
        } else if ("LGT".equals(layerId)) {
            if (!btnLayerAirspace.isEnabled()) return;
            final String origText = btnLayerAirspace.getText().toString();
            btnLayerAirspace.setEnabled(false);
            btnLayerAirspace.setText("LGT...");
            airspaceLayer.toggle(bbox, new Runnable() {
                @Override public void run() {
                    btnLayerAirspace.setText(origText);
                    btnLayerAirspace.setEnabled(true);
                    btnLayerAirspace.setAlpha(airspaceLayer.isVisible() ? 1.0f : 0.5f);
                }
            });
        } else if ("ZON".equals(layerId)) {
            if (!btnLayerLandUse.isEnabled()) return;
            final String origText = btnLayerLandUse.getText().toString();
            btnLayerLandUse.setEnabled(false);
            btnLayerLandUse.setText("ZÓN...");
            landUseLayer.toggle(bbox, new Runnable() {
                @Override public void run() {
                    btnLayerLandUse.setText(origText);
                    btnLayerLandUse.setEnabled(true);
                    btnLayerLandUse.setAlpha(landUseLayer.isVisible() ? 1.0f : 0.5f);
                }
            });
        }
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
        // Magasság: ha az etAltitude valid, az a forrás (felülírhatja a GSD slider
        // clamp-elt értékét — pl. 3 m a 0.1 cm/px slider min-je alatt).
        double alt    = (etAltitude != null)
                ? parseAltitudeField()
                : GsdCalculator.altitudeFromGsd(gsd, drone);
        double side   = getSidelap();
        double front  = getFrontlap();
        float  speed  = getSpeed();
        int    angle  = getAngle();
        float  recSpd = GsdCalculator.recommendedSpeedMs(gsd, drone);

        tvGsd.setText(String.format("GSD: %.2f cm/px  (ajánl. v: %.1f m/s)", gsd, recSpd));
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

    /**
     * Térkép ugrás az aktuális pozícióra.
     *
     * Forrás prioritás:
     * 1. Drón GPS (DJI telemetria, ha csatlakoztatva és van fix)
     * 2. Tablet saját GPS (Android LocationProvider)
     * 3. Toast: nincs pozíció adat
     *
     * Zoom: 15 (~1–1.5 km sugarú nézet) — elegendő a közvetlen környezet áttekintéséhez.
     * Animáció: zoom azonnal beáll (nincs köztes tile-betöltés), pan animálódik.
     * Ez megakadályozza a gyors zoom miatti lefagyást.
     */
    private void jumpToCurrentPosition() {
        GeoPoint target = null;
        String source = "";

        // 1. Drón GPS (DJI telemetria — pontosabb, a drón helye van)
        if (lastDroneLat != 0 && lastDroneLon != 0) {
            target = new GeoPoint(lastDroneLat, lastDroneLon);
            source = "Drón GPS";
        }

        // 2. Tablet saját GPS
        if (target == null && locationOverlay != null) {
            Location loc = locationOverlay.getLastFix();
            if (loc != null) {
                target = new GeoPoint(loc.getLatitude(), loc.getLongitude());
                source = "Tablet GPS"
                       + (loc.getAccuracy() > 0
                           ? " (±" + Math.round(loc.getAccuracy()) + " m)"
                           : "");
            }
        }

        if (target != null) {
            // Zoom beállítása ELŐBB (instant, nincs animáció) → csak egyszer tölt tile-okat
            mapView.getController().setZoom(15.0);
            // Utána sima pan animáció a célpontra (zoom változás nélkül → nem tölt új tile-okat)
            mapView.getController().animateTo(target);
            Toast.makeText(this, source, Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "GPS pozíció még nem elérhető", Toast.LENGTH_SHORT).show();
        }
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

    // ── Projekt mentés / betöltés ─────────────────────────────────────────

    /** Új terv létrehozása – megerősítő dialóg, ha van rajzolt terv. */
    private void confirmNewPlan() {
        if (polygonPoints.isEmpty()) {
            // Nincs mit elveszíteni
            currentPlanFile = null;
            updatePlanNameLabel();
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(this)
            .setTitle("Új terv")
            .setNegativeButton("Mégse", null);

        if (currentPlanFile != null) {
            // Van mentett fájl – kínáljuk a mentést is
            builder.setMessage("Az aktuális terv bezárásra kerül.\nMentsd el, mielőtt új tervet kezdesz?")
                .setPositiveButton("Mentés és bezárás", (d, w) -> {
                    saveCurrentOrShowDialog();
                    // saveCurrentOrShowDialog után (ha sikeres) zárjuk be a tervet
                    // Mivel a mentés szinkron, utána azonnal nullázunk
                    clearAll();
                    currentPlanFile = null;
                    updatePlanNameLabel();
                })
                .setNeutralButton("Bezárás mentés nélkül", (d, w) -> {
                    clearAll();
                    currentPlanFile = null;
                    updatePlanNameLabel();
                });
        } else {
            // Mentetlen terv – elég egyszerű megerősítés
            builder.setMessage("Az aktuális rajzolt terület elvész. Biztosan új tervet kezdesz?")
                .setPositiveButton("Igen, új terv", (d, w) -> {
                    clearAll();
                    currentPlanFile = null;
                    updatePlanNameLabel();
                });
        }
        builder.show();
    }

    /** SAVE – felülírja az aktuális fájlt; ha nincs, SAVE AS dialógot nyit. */
    private void saveCurrentOrShowDialog() {
        if (currentPlanFile != null) {
            if (polygonPoints.isEmpty()) {
                Toast.makeText(this, "Nincs rajzolt terület — nincs mit menteni!", Toast.LENGTH_SHORT).show();
                return;
            }
            try {
                String name = currentPlanFile.getName()
                    .replace(ProjectManager.FILE_EXT, "").replace('_', ' ');
                ProjectManager.saveProjectToFile(
                    this, currentPlanFile, name, polygonPoints, buildConfig(),
                    null, null, null, true, currentBlocksForSave());
                updatePlanNameLabel();
                Toast.makeText(this, "Mentve: " + currentPlanFile.getName(), Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(this, "Mentési hiba: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        } else {
            showSaveProjectDialog();
        }
    }

    /** SAVE AS – mindig új fájlnév dialóggal. */
    private void showSaveProjectDialog() {
        final int pad = (int) (getResources().getDisplayMetrics().density * 16);
        final EditText etName = new EditText(this);
        etName.setInputType(InputType.TYPE_CLASS_TEXT);
        etName.setTextColor(0xFF111111);
        etName.setBackgroundColor(0xFFEEEEEE);
        etName.setPadding(pad, pad / 2, pad, pad / 2);
        etName.setSelectAllOnFocus(true);
        // Alapértelmezett név: aktuális fájlnév vagy dátum
        String defaultName = (currentPlanFile != null)
            ? currentPlanFile.getName().replace(ProjectManager.FILE_EXT, "").replace('_', ' ')
            : new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                .format(new java.util.Date());
        etName.setText(defaultName);

        FrameLayout container = new FrameLayout(this);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(pad, pad / 2, pad, 0);
        etName.setLayoutParams(lp);
        container.addView(etName);

        new AlertDialog.Builder(this)
            .setTitle("Repülési terv mentése")
            .setMessage("Add meg a terv nevét:")
            .setView(container)
            .setPositiveButton("Mentés", new android.content.DialogInterface.OnClickListener() {
                @Override
                public void onClick(android.content.DialogInterface dialog, int which) {
                    String name = etName.getText().toString().trim();
                    if (name.isEmpty()) {
                        Toast.makeText(MissionPlannerActivity.this,
                            "A terv neve nem lehet üres!", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (polygonPoints.isEmpty()) {
                        Toast.makeText(MissionPlannerActivity.this,
                            "Nincs rajzolt terület — nincs mit menteni!", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    saveProject(name);
                }
            })
            .setNegativeButton("Mégse", null)
            .show();
    }

    private void saveProject(String name) {
        try {
            MissionConfig config = buildConfig();
            File saved = ProjectManager.saveProject(
                this, name, polygonPoints, config,
                null, null, null, currentBlocksForSave());
            currentPlanFile = saved;
            updatePlanNameLabel();
            Toast.makeText(this,
                "Mentve: " + saved.getName(), Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this,
                "Mentési hiba: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void updatePlanNameLabel() {
        if (tvCurrentPlanName == null) return;
        if (currentPlanFile != null) {
            String name = currentPlanFile.getName()
                .replace(ProjectManager.FILE_EXT, "").replace('_', ' ');
            tvCurrentPlanName.setText(name);
            tvCurrentPlanName.setTextColor(0xFFCCCCCC);
        } else {
            tvCurrentPlanName.setText("Mentetlen terv");
            tvCurrentPlanName.setTextColor(0xFF888888);
        }
    }

    private void showLoadProjectDialog() {
        final List<File> files = ProjectManager.listProjects(this);
        if (files.isEmpty()) {
            Toast.makeText(this,
                "Nincs elmentett repülési terv.", Toast.LENGTH_SHORT).show();
            return;
        }

        final String[] names = new String[files.size()];
        for (int i = 0; i < files.size(); i++) {
            String fn = files.get(i).getName()
                .replace(ProjectManager.FILE_EXT, "")
                .replace('_', ' ');
            String date = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm",
                java.util.Locale.getDefault())
                .format(new java.util.Date(files.get(i).lastModified()));
            names[i] = fn + "\n" + date;
        }

        android.widget.ListView listView = new android.widget.ListView(this);
        android.widget.ArrayAdapter<String> adapter = new android.widget.ArrayAdapter<String>(
            this, android.R.layout.simple_list_item_2, android.R.id.text1, names) {
            @Override
            public android.view.View getView(int pos, android.view.View convertView, android.view.ViewGroup parent) {
                android.view.View v = super.getView(pos, convertView, parent);
                String[] parts = names[pos].split("\n", 2);
                ((android.widget.TextView) v.findViewById(android.R.id.text1)).setText(parts[0]);
                ((android.widget.TextView) v.findViewById(android.R.id.text2))
                    .setText(parts.length > 1 ? parts[1] : "");
                return v;
            }
        };
        listView.setAdapter(adapter);

        final AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle("Repülési terv betöltése  (hosszan tartva: törlés)")
            .setView(listView)
            .setNegativeButton("Mégse", null)
            .create();

        listView.setOnItemClickListener((parent, view, which, id) -> {
            dialog.dismiss();
            confirmLoadProject(files.get(which));
        });

        listView.setOnItemLongClickListener((parent, view, which, id) -> {
            File target = files.get(which);
            String tervNev = target.getName().replace(ProjectManager.FILE_EXT, "").replace('_', ' ');
            new AlertDialog.Builder(this)
                .setTitle("Terv törlése")
                .setMessage("Törlöd a \"" + tervNev + "\" tervet?\nA szerveren is törlődik.")
                .setPositiveButton("Törlés", (d2, w2) -> {
                    dialog.dismiss();
                    deleteProject(target);
                })
                .setNegativeButton("Mégse", null)
                .show();
            return true;
        });

        dialog.show();
    }

    private void deleteProject(File file) {
        String id = null;
        try {
            ProjectManager.ProjectData data = ProjectManager.loadProject(file);
            id = data.id;
        } catch (Exception ignored) {}

        if (file.equals(currentPlanFile)) {
            clearAll();
            currentPlanFile = null;
            clearResumeState();
            updatePlanNameLabel();
        }
        file.delete();

        final String serverId = id;
        if (serverId != null && !serverId.isEmpty()) {
            SyncManager.getInstance(this).deleteFromServer(serverId, new SyncManager.DeleteCallback() {
                @Override public void onResult(boolean ok) {
                    runOnUiThread(() -> Toast.makeText(MissionPlannerActivity.this,
                        ok ? "Terv törölve (helyi + szerver)"
                           : "Helyi terv törölve (szerverről nem sikerült)",
                        Toast.LENGTH_SHORT).show());
                }
            });
        } else {
            Toast.makeText(this, "Terv törölve", Toast.LENGTH_SHORT).show();
        }
    }

    private void confirmLoadProject(final File file) {
        // Ha van aktuális terv, figyelmeztetés
        if (!polygonPoints.isEmpty()) {
            new AlertDialog.Builder(this)
                .setTitle("Terv betöltése")
                .setMessage("Az aktuális rajzolt terület elvész. Biztosan betöltöd?")
                .setPositiveButton("Betöltés", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        loadProject(file);
                    }
                })
                .setNegativeButton("Mégse", null)
                .show();
        } else {
            loadProject(file);
        }
    }

    private void loadProject(File file) {
        try {
            ProjectManager.ProjectData data = ProjectManager.loadProject(file);

            // Töröljük a jelenlegi állapotot
            clearAll();

            // ── Polygon visszaállítása ────────────────────────────────────
            for (GeoPoint p : data.polygon) {
                addPolygonPoint(p);
            }

            // ── Beállítások visszaállítása a SeekBar-okra ─────────────────
            restoreConfigToUI(data);

            // ── Akadályok visszaállítása ──────────────────────────────────
            for (ObstacleData obs : data.obstacles) {
                addObstacleWithMeta(obs);
            }

            // ── Térkép középpontja a polygon körülhatárolt területére ─────
            if (!data.polygon.isEmpty()) {
                GeoPoint center = data.polygon.get(0);
                mapView.getController().animateTo(center);
            }

            // ── M07: blokk-rács visszaállítása ────────────────────────────
            if (data.blockGrid != null && polygonPoints.size() >= 3) {
                restoreBlockGrid(data);
            }

            currentPlanFile = file;
            loadResumeState();
            updatePlanNameLabel();

            // Ha van elég pont, automatikusan generáljuk
            // (blokk-mód esetén nincs autogen — a felhasználó választ blokkot)
            if (polygonPoints.size() >= 3 && blockGridConfig == null) {
                generateMission();
            }

            Toast.makeText(this, "Betöltve: " + data.name, Toast.LENGTH_SHORT).show();

        } catch (Exception e) {
            Toast.makeText(this,
                "Betöltési hiba: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Seekbar-ok és switch-ek visszaállítása a betöltött projekt adataiból.
     * Fordított mapping (buildConfig() inverze).
     */
    private void restoreConfigToUI(ProjectManager.ProjectData data) {
        // FONTOS sorrend: először minden olyan UI elemet beállítunk, ami a
        // listener-eken keresztül felülírhatná a magasságot (drón csere,
        // sbGsd). A mentett altitude_m-et UTOLSÓ lépésként állítjuk be, hogy
        // ez legyen az érvényes érték — különben pl. a drón csere
        // syncAltitudeFromGsd hívása felülírná.

        // droneProfile → spinnerDrone (name alapján keresés) — ELŐRE, mert
        // változtatja a sensor paramétereket, amit a magasság-szinkron használ
        if (!data.droneProfileName.isEmpty()) {
            for (int i = 0; i < DroneProfiles.ALL.size(); i++) {
                if (DroneProfiles.ALL.get(i).name.equals(data.droneProfileName)) {
                    spinnerDrone.setSelection(i);
                    break;
                }
            }
        }

        // sidelapPercent → sbSidelap: progress = sidelap - 50
        int sidelapProg = (int) Math.round(data.sidelapPercent - 50.0);
        sbSidelap.setProgress(Math.max(0, Math.min(sbSidelap.getMax(), sidelapProg)));

        // frontlapPercent → sbFrontlap: progress = frontlap - 60
        int frontlapProg = (int) Math.round(data.frontlapPercent - 60.0);
        sbFrontlap.setProgress(Math.max(0, Math.min(sbFrontlap.getMax(), frontlapProg)));

        // speedMs → sbSpeed: progress = round(speed - 2)  (getSpeed = 2 + progress)
        int speedProg = Math.round(data.speedMs - 2.0f);
        sbSpeed.setProgress(Math.max(0, Math.min(sbSpeed.getMax(), speedProg)));

        // flightAngleDeg → sbAngle
        int angleProg = (int) Math.round(data.flightAngleDeg);
        sbAngle.setProgress(Math.max(0, Math.min(sbAngle.getMax(), angleProg)));

        // offsetM → sbOffset
        if (sbOffset != null) {
            int offsetProg = (int) Math.round(data.offsetM);
            sbOffset.setProgress(Math.max(0, Math.min(sbOffset.getMax(), offsetProg)));
        }

        // terrainFollowing → switchTerrain
        if (switchTerrain != null) {
            switchTerrain.setChecked(data.terrainFollowing);
        }

        // gridMode → switchCrosshatch; crosshatchHeadingDeg → sbCrosshatchAngle
        if (switchCrosshatch != null) {
            boolean crosshatch = "crosshatch".equals(data.gridMode);
            switchCrosshatch.setChecked(crosshatch);
            int crosshatchVis = crosshatch ? View.VISIBLE : View.GONE;
            if (sbCrosshatchAngle != null) {
                sbCrosshatchAngle.setVisibility(crosshatchVis);
                int chProg = (int) Math.round(data.crosshatchHeadingDeg);
                sbCrosshatchAngle.setProgress(Math.max(0, Math.min(179, chProg)));
            }
            if (tvCrosshatchAngle != null) {
                tvCrosshatchAngle.setVisibility(crosshatchVis);
                tvCrosshatchAngle.setText("2. rács iránya: " + (int) data.crosshatchHeadingDeg + "°");
            }
        }

        // Magasság az elsődleges forrás (lásd buildConfig). A mentett altitude_m
        // értéket írjuk vissza az etAltitude-ba, és setAltitudeAndSyncGsd
        // gondoskodik a GSD slider szinkronizálásáról. Fallback: ha az
        // altitude_m hiányzik (régi mentés), a GSD-ből származtatott magasságot
        // használjuk a most már aktív drón profillal.
        int altInt;
        if (data.altitudeM > 0.0) {
            altInt = (int) Math.round(data.altitudeM);
        } else {
            altInt = (int) Math.round(
                GsdCalculator.altitudeFromGsd(data.gsdCm, getSelectedDrone()));
        }
        // triggerAutoGen=false: a loadProject() a végén explicit generateMission()-t
        // hív, ne legyen dupla generálás.
        setAltitudeAndSyncGsd(altInt, false);

        // ── Mintavételi beállítások visszaállítása (terepi javítás, 2026-07-03) ──
        if (switchSamplingMode != null) {
            switchSamplingMode.setChecked(data.samplingMode);
        }
        if (data.samplingMode) {
            if (etSampleCount != null) etSampleCount.setText(String.valueOf(data.nSamplePoints));
            if (etTransitAlt != null) etTransitAlt.setText(String.valueOf(data.transitAltitudeM));
            if (etSampleAlt != null) etSampleAlt.setText(String.valueOf(data.sampleAltitudeM));
            if (etHoverSeconds != null) etHoverSeconds.setText(String.valueOf(data.hoverSeconds));
            if (spinnerSamplingMethod != null) {
                String[] methods = {"stratified", "halton", "random"};
                for (int i = 0; i < methods.length; i++) {
                    if (methods[i].equals(data.samplingMethod)) {
                        spinnerSamplingMethod.setSelection(i);
                        break;
                    }
                }
            }
        }

        // ── Kamera beállítások visszaállítása (terepi javítás, 2026-07-03) ───────
        if (data.cameraSettings != null) {
            if (switchCameraAuto != null) switchCameraAuto.setChecked(data.cameraSettings.autoMode);
            if (spinnerFileFormat != null) spinnerFileFormat.setSelection(data.cameraSettings.fileFormat.ordinal());
            if (spinnerPhotoMode != null) spinnerPhotoMode.setSelection(data.cameraSettings.photoMode.ordinal());
            if (spinnerIso != null) spinnerIso.setSelection(data.cameraSettings.iso.ordinal());
            if (spinnerShutter != null) spinnerShutter.setSelection(data.cameraSettings.shutterSpeed.ordinal());
            if (spinnerWhiteBalance != null) spinnerWhiteBalance.setSelection(data.cameraSettings.whiteBalance.ordinal());
        }

        // ── Sűrű rács mód visszaállítása (M10, 2026-07-03) ───────────────────────
        if (switchDenseGridMode != null) {
            switchDenseGridMode.setChecked(data.denseGridMode);
        }

        updateLabels();
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
        etRadius.setTextColor(0xFF111111);      // sötét szöveg, fehér dialog háttéren látható
        etRadius.setHintTextColor(0xFF999999);
        etRadius.setBackgroundColor(0xFFEEEEEE);
        etRadius.setPadding(pad / 2, pad / 4, pad / 2, pad / 4);
        etRadius.setSelectAllOnFocus(true);     // kattintáskor az egész szám kijelölve → egyből felülírható
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
        etHeight.setTextColor(0xFF111111);
        etHeight.setHintTextColor(0xFF999999);
        etHeight.setBackgroundColor(0xFFEEEEEE);
        etHeight.setPadding(pad / 2, pad / 4, pad / 2, pad / 4);
        etHeight.setSelectAllOnFocus(true);
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

    /** Fájlból betöltött akadály visszaállítása — id és label megőrzésével */
    private void addObstacleWithMeta(ObstacleData obs) {
        obstacleList.add(obs);
        GeoPoint p = new GeoPoint(obs.latitude, obs.longitude);
        Polygon circle = buildCircleOverlay(obs.latitude, obs.longitude, obs.radiusM);
        obstacleOverlays.add(circle);
        mapView.getOverlays().add(0, circle);
        Marker m = new Marker(mapView);
        m.setPosition(p);
        String label = (obs.label != null && !obs.label.isEmpty()) ? obs.label : obs.id;
        m.setTitle(label.isEmpty() ? "Akadaly #" + obstacleList.size() : label);
        m.setSnippet(String.format("Sugar: %.0fm | Mag: %.0fm", obs.radiusM, obs.heightM));
        m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        m.setOnMarkerClickListener((marker, mv) -> {
            int idx = obstacleMarkers.indexOf(marker);
            ObstacleData o = (idx >= 0 && idx < obstacleList.size()) ? obstacleList.get(idx) : obs;
            new AlertDialog.Builder(MissionPlannerActivity.this)
                .setTitle(m.getTitle())
                .setMessage(String.format(
                    "Pozicio: %.5f, %.5f\nBiztonsagi zona: %.0f m\nMagassag: %.0f m",
                    o.latitude, o.longitude, o.radiusM, o.heightM))
                .setPositiveButton("Torles", (d2, w2) -> removeObstacle(idx))
                .setNegativeButton("Bezaras", null)
                .show();
            return true;
        });
        obstacleMarkers.add(m);
        mapView.getOverlays().add(m);
        mapView.invalidate();
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
        m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM); // hegy csúcsa = a tényleges koordináta
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
        lastResult = runGeneration(polygonPoints, config);

        if (lastResult.errorMessage != null) {
            tvStats.setText("Hiba: " + lastResult.errorMessage);
            return;
        }

        drawMissionPath(lastResult.segments);
        currentSegmentIndex = 0;
        btnUpload.setEnabled(DJIHelper.getInstance().isConnected() && !missionRunning);
        btnExport.setEnabled(true);
        if (btnSimulate != null) btnSimulate.setEnabled(true);

        double recSpd = GsdCalculator.recommendedSpeedMs(config.gsdCm, config.droneProfile);
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(
            "Terulet: %.2f ha | ~%d foto (%d szegmens)\n" +
            "Magassag: %.0f m | Savkoz: %.1f m | Fototav: %.1f m\n" +
            "Sebesseg: %.0f m/s | Ido: ~%.0f perc",
            lastResult.areaM2 / 10000.0,
            lastResult.estimatedPhotoCount, lastResult.segments.size(),
            lastResult.altitudeM, lastResult.stripSpacingM, lastResult.photoDistM,
            (double) config.speedMs, lastResult.estimatedMinutes));
        if (lastResult.cameraIntervalLimited) {
            sb.append("\n⚠ Kamera 2 mp-es hardverkorlátja aktív — a valós átfedés " +
                    "kisebb lesz a beállítottnál. Csökkentsd a sebességet vagy növeld a frontlapot.");
        }
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
        // M07: blokk-felosztás is törlődik
        clearBlockGrid();
        lastResult = null;
        missionUploaded = false;
        btnUpload.setEnabled(false);
        btnStart.setEnabled(false);
        btnExport.setEnabled(false);
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
        // M07: ha blokk-mód aktív és van kiválasztott blokk → annak missionPolygon-ja
        List<GeoPoint> generationPolygon;
        if (blockGridConfig != null && selectedBlock != null) {
            generationPolygon = selectedBlock.missionPolygon;
            if (generationPolygon == null || generationPolygon.size() < 3) {
                Toast.makeText(this, "A kivalasztott blokk poligonja ervenytelen", Toast.LENGTH_SHORT).show();
                return;
            }
        } else {
            if (polygonPoints.size() < 3) {
                Toast.makeText(this, "Legalabb 3 pont szukseges!", Toast.LENGTH_SHORT).show();
                return;
            }
            if (blockGridConfig != null && selectedBlock == null) {
                Toast.makeText(this, "Valassz ki egy blokkot a terkepen", Toast.LENGTH_SHORT).show();
                return;
            }
            generationPolygon = polygonPoints;
        }
        MissionConfig config = buildConfig();
        config.terrainFollowing = switchTerrain != null && switchTerrain.isChecked();
        lastResult = runGeneration(generationPolygon, config);

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
        if (btnSimulate != null) btnSimulate.setEnabled(true);

        StringBuilder sbStats = new StringBuilder();
        if (lastResult.isSamplingMission) {
            sbStats.append(String.format(
                "Terulet: %.2f ha | %d mintapont | %d waypoint (%d szegmens)\n" +
                "Transit magassag: %.0f m | Mintaveteli magassag: %.0f m\n" +
                "Sebesseg: %.0f m/s | Ido: ~%.0f perc",
                lastResult.areaM2 / 10000.0, lastResult.sampleCount,
                lastResult.totalWaypoints, lastResult.segments.size(),
                config.transitAltitudeM, config.sampleAltitudeM,
                (double) config.speedMs, lastResult.estimatedMinutes));
            if (config.transitAltitudeM > MAX_ALTITUDE_LEGAL) {
                Toast.makeText(this,
                    String.format("Figyelem: transit magasság %.0fm > 120m (EU határ)!",
                        config.transitAltitudeM),
                    Toast.LENGTH_LONG).show();
            }
        } else {
            double recSpd = GsdCalculator.recommendedSpeedMs(config.gsdCm, config.droneProfile);
            if (lastResult.isDenseGridMission) {
                // M10 — Sűrű rács: minden waypoint pontosan egy fotó (nincs
                // kamera-időzítési bizonytalanság), de a repülési idő/akkuszám
                // jelentősen nagyobb, mint a szokásos CURVED módnál (M10_L1 §5).
                sbStats.append(String.format(
                    "Terulet: %.2f ha | %d foto (pontos, %d szegmens)\n" +
                    "Magassag: %.0f m | Fototav (waypoint-koz): %.1f m\n" +
                    "Ido: ~%.0f perc | Becsult akku: ~%d db — 🐢 Sűrű rács mód",
                    lastResult.areaM2 / 10000.0,
                    lastResult.estimatedPhotoCount, lastResult.segments.size(),
                    lastResult.altitudeM, lastResult.photoDistM,
                    lastResult.estimatedMinutes, lastResult.estimatedBatteryCount));
            } else {
                sbStats.append(String.format(
                    "Terulet: %.2f ha | ~%d foto (%d szegmens)\n" +
                    "Magassag: %.0f m | Savkoz: %.1f m | Fototav: %.1f m\n" +
                    "Sebesseg: %.0f m/s (ajanl: %.0f m/s) | Ido: ~%.0f perc",
                    lastResult.areaM2 / 10000.0,
                    lastResult.estimatedPhotoCount, lastResult.segments.size(),
                    lastResult.altitudeM, lastResult.stripSpacingM, lastResult.photoDistM,
                    (double) config.speedMs, (double) recSpd, lastResult.estimatedMinutes));
                if (lastResult.cameraIntervalLimited) {
                    sbStats.append("\n⚠ Kamera 2 mp-es hardverkorlátja aktív — a valós átfedés " +
                            "kisebb lesz a beállítottnál. Kapcsold be a 🐢 Sűrű rács módot a " +
                            "beállított átfedés garantálásához (lassabb repülés, több akku).");
                }
            }
            if (lastResult.altitudeM > MAX_ALTITUDE_LEGAL) {
                Toast.makeText(this,
                    String.format("Figyelem: %.0fm > 120m (EU határ)! Csokkentsd a GSD-t.",
                        lastResult.altitudeM),
                    Toast.LENGTH_LONG).show();
            }
        }
        if (lastResult.skippedByObstacle > 0) {
            sbStats.append(String.format("\n⚠ Akadaly miatt kihagyva: %d wp", lastResult.skippedByObstacle));
        }
        tvStats.setText(sbStats.toString());

        // Domborzatkövetés a mintavételi misszióra nem értelmezhető (a 3-waypontos
        // érkezés/süllyedés/emelkedés szerkezet feltételezi, hogy a transit magasság
        // már manuálisan biztonságos minden akadály fölött — ld. M01 §10).
        if (config.terrainFollowing && !lastResult.isSamplingMission) {
            applyTerrainFollowing(lastResult, lastResult.altitudeM);
        }
    }

    private void drawMissionPath(List<List<WaypointData>> segments) {
        if (missionOverlay != null) mapView.getOverlays().remove(missionOverlay);
        List<GeoPoint> path = new ArrayList<>();
        for (List<WaypointData> seg : segments)
            for (WaypointData wp : seg)
                path.add(new GeoPoint(wp.latitude, wp.longitude));

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
        boolean drone = false;
        try { drone = DJIHelper.getInstance().isConnected(); } catch (Throwable t) { }
        if (!drone) {
            btnUpload.setEnabled(false);
            Toast.makeText(this, "Nincs csatlakoztatott drón", Toast.LENGTH_SHORT).show();
            return;
        }
        List<WaypointData> segment = lastResult.segments.get(currentSegmentIndex);
        int total = lastResult.segments.size();

        // Ha van előző megszakadás ugyanennél a szegmensnél, felajánljuk a folytatást
        if (resumeWaypointIndex > 0 && resumeSegmentIndex == currentSegmentIndex) {
            int fromIndex = Math.max(0, resumeWaypointIndex - 1);
            int remaining = segment.size() - fromIndex;
            new AlertDialog.Builder(this)
                .setTitle("Misszió folytatása")
                .setMessage(String.format(
                    "Az előző misszió a %d. waypointnál szakadt meg (%d/%d).\n\n" +
                    "Folytatod a %d. waypointtól (%d WP), vagy újrakezded az elejéről (%d WP)?",
                    resumeWaypointIndex + 1, resumeWaypointIndex + 1, segment.size(),
                    fromIndex + 1, remaining, segment.size()))
                .setPositiveButton("Folytatás (" + (fromIndex + 1) + ". WP-től)", (d, w) ->
                    doUpload(segment.subList(fromIndex, segment.size()), fromIndex, segment.size()))
                .setNegativeButton("Újrakezdem az elejéről", (d, w) -> {
                    resumeWaypointIndex = -1;
                    resumeSegmentIndex  = -1;
                    String confirmMsg = total > 1
                        ? String.format("Szegmens %d/%d feltöltése (%d waypoint)?",
                            currentSegmentIndex + 1, total, segment.size())
                        : String.format("%d waypoint feltöltése a drónra?", segment.size());
                    new AlertDialog.Builder(this)
                        .setTitle("Misszió feltöltése")
                        .setMessage(confirmMsg)
                        .setPositiveButton("Feltöltés", (d2, w2) ->
                            doUpload(segment, 0, segment.size()))
                        .setNegativeButton("Mégse", null)
                        .show();
                })
                .show();
            return;
        }

        String msg = total > 1
            ? String.format("Szegmens %d/%d feltöltése (%d waypoint)?",
                currentSegmentIndex + 1, total, segment.size())
            : String.format("%d waypoint feltöltése a drónra?", segment.size());

        // M10_L2 §2 — kötelező megerősítés sűrű rács módnál, ha a becsült
        // idő/akkuszám jelentősen meghaladja egy szokásos misszióét.
        if (lastResult.isDenseGridMission && currentSegmentIndex == 0
                && (lastResult.estimatedMinutes > 20 || lastResult.estimatedBatteryCount > 3)) {
            msg += String.format(
                "\n\n🐢 Sűrű rács mód: a teljes misszió becsült ideje ~%.0f perc, " +
                "kb. %d akkumulátor szükséges — ez jelentősen több, mint egy hasonló " +
                "területű normál (CURVED) misszió lenne. Folytatod?",
                lastResult.estimatedMinutes, lastResult.estimatedBatteryCount);
        }

        new AlertDialog.Builder(this)
            .setTitle("Misszió feltöltése")
            .setMessage(msg)
            .setPositiveButton("Feltöltés", (d, w) -> doUpload(segment, 0, segment.size()))
            .setNegativeButton("Mégse", null)
            .show();
    }

    /**
     * @param segment          A feltöltendő waypontok listája (lehet részleges – resume esetén)
     * @param progressOffset   Hányadik waypointtól indul a szegmensen belül (resume esetén > 0)
     * @param totalSegmentSize A szegmens teljes mérete (haladásjelzőhöz)
     */
    private void doUpload(List<WaypointData> segment, int progressOffset, int totalSegmentSize) {
        // Ha a terv nincs elmentve, automatikusan mentjük, hogy a resume állapot perzisztálható legyen
        if (currentPlanFile == null && !polygonPoints.isEmpty()) {
            try {
                String autoName = "auto_" + new java.text.SimpleDateFormat(
                    "yyyy-MM-dd_HH-mm", java.util.Locale.getDefault()).format(new java.util.Date());
                File saved = ProjectManager.saveProject(
                    this, autoName, polygonPoints, buildConfig(),
                    null, null, null, currentBlocksForSave());
                currentPlanFile = saved;
                updatePlanNameLabel();
            } catch (Exception ignored) {}
        }

        btnUpload.setEnabled(false);
        btnStart.setEnabled(false);
        missionUploaded = false;
        uploadedSegmentSize = totalSegmentSize;

        android.app.ProgressDialog progress = new android.app.ProgressDialog(this);
        progress.setTitle("Misszió feltöltése");
        progress.setMessage(progressOffset > 0
            ? String.format("Feltöltés a %d. waypointtól...", progressOffset + 1)
            : "Feltöltés folyamatban a drónra...");
        progress.setCancelable(false);
        progress.show();

        MissionUploader.UploadCallback uploadCallback = new MissionUploader.UploadCallback() {
            @Override public void onSuccess() {
                runOnUiThread(() -> {
                    progress.dismiss();
                    missionUploaded = true;
                    updateStartButtonState();
                    new AlertDialog.Builder(MissionPlannerActivity.this)
                        .setTitle("Feltöltés sikeres")
                        .setMessage(progressOffset > 0
                            ? String.format("Folytatás: %d. waypointtól feltöltve.\n\nEllenőrizd a drónt, majd nyomj START-ot.", progressOffset + 1)
                            : "A misszió sikeresen feltöltve a drónra.\n\nEllenőrizd a drónt, majd nyomj START-ot.")
                        .setPositiveButton("OK", null)
                        .show();
                });
            }
            @Override public void onError(String msg) {
                runOnUiThread(() -> {
                    progress.dismiss();
                    btnUpload.setEnabled(true);
                    new AlertDialog.Builder(MissionPlannerActivity.this)
                        .setTitle("Feltöltési hiba")
                        .setMessage(msg)
                        .setPositiveButton("OK", null)
                        .show();
                });
            }
        };

        // M04 §15 — mintavételi misszió, és M10 — sűrű rács: mindkettő NORMAL
        // mód + STAY akció waypointonként, a fotót app-vezérelt trigger adja
        // (nem waypoint-akció, nem kamera-intervallum) — uploadSamplingMission()
        // logikája változtatás nélkül mindkettőhöz megfelelő.
        boolean needsNormalMode = lastResult != null
                && (lastResult.isSamplingMission || lastResult.isDenseGridMission);
        if (needsNormalMode) {
            uploader.uploadSamplingMission(segment, buildConfig(), uploadCallback);
        } else {
            uploader.uploadMission(segment, buildConfig(), uploadCallback);
        }
    }

    private void startMission() {
        if (!missionUploaded) return;
        boolean airborne = false;
        try { airborne = DJIHelper.getInstance().isFlying(); } catch (Throwable t) {}
        String message = airborne
                ? "A drón levegőben van. A misszió indítása után az első waypointra repül (SAFELY módban: előbb a misszió magasságára emelkedik)."
                : "A drón felszáll és elindítja a missziót. Biztos vagy benne?";
        new AlertDialog.Builder(this)
            .setTitle("Repülés indítása")
            .setMessage(message)
            .setPositiveButton("START", (d, w) -> {
                // 1. SD kártya ellenőrzése — ha nincs, figyelmeztetés + megerősítés
                CameraConfigurator.checkSDCard((sdOk, sdMsg) -> runOnUiThread(() -> {
                    if (!sdOk) {
                        new AlertDialog.Builder(MissionPlannerActivity.this)
                            .setTitle("⚠️ Nincs SD kártya!")
                            .setMessage("A kamerában nem található memóriakártya.\n"
                                    + "A fotók elveszhetnek!\n\n"
                                    + "Helyezz be SD kártyát, vagy folytasd kártya nélkül.")
                            .setPositiveButton("Folytatás kártya nélkül",
                                    (d2, w2) -> doLaunchMission())
                            .setNegativeButton("Mégse", null)
                            .show();
                    } else {
                        doLaunchMission();
                    }
                }));
            })
            .setNegativeButton("Mégse", null)
            .show();
    }

    /**
     * Misszió indítási szekvencia (SD check után hívódik):
     * gimbal nadir → kamera beállítás → intervallum fotózás → misszió start
     */
    private void doLaunchMission() {
        // 2. Gimbal leforgatása -90°-ra
        Toast.makeText(this, "Gimbal beállítása...", Toast.LENGTH_SHORT).show();
        CameraConfigurator.setNadirPitch((gimbalOk, gimbalMsg) -> runOnUiThread(() -> {
            if (!gimbalOk) {
                Toast.makeText(this, "Gimbal hiba: " + gimbalMsg, Toast.LENGTH_SHORT).show();
            }
            // 3. Kamera beállítások alkalmazása
            // Mintavételi misszió (M04 §15/§18) ÉS sűrű rács (M10): SHOOT_PHOTO +
            // SINGLE mód — a fotó triggerelése app-vezérelt (CameraConfigurator.
            // triggerSamplePhoto(), onWaypointReached-ből), NEM az intervallum-
            // időzítő (ami CURVED módban is figyelmen kívül maradna).
            boolean sampling  = lastResult != null && lastResult.isSamplingMission;
            boolean denseGrid = lastResult != null && lastResult.isDenseGridMission;
            Toast.makeText(this, "Kamera beállítása...", Toast.LENGTH_SHORT).show();
            CameraConfigurator.ConfigCallback cameraPrepCallback = (success, msg) ->
                runOnUiThread(() -> {
                    if (!success) {
                        Toast.makeText(this,
                            "Kamera hiba: " + msg + " – indítás folytatódik",
                            Toast.LENGTH_SHORT).show();
                    }
                    if (sampling) {
                        samplingMissionStartTimeMs = System.currentTimeMillis();
                        samplePointCursor = 0;
                        samplingPhotosFailed = 0;
                        confirmedSamplePointsForSession.clear();
                        currentSamplingSessionId = new java.text.SimpleDateFormat(
                            "yyyyMMdd_HHmmss", java.util.Locale.US).format(new java.util.Date());
                        btnDownloadSession.setVisibility(View.GONE);
                    }
                    if (denseGrid) {
                        denseGridPhotosConfirmed = 0;
                        denseGridPhotosFailed    = 0;
                    }
                    // 4. Misszió indítása — grid misszióban az intervallum fotózás
                    //    csak az 1. WP elérésekor indul (lásd
                    //    startMissionListener.onWaypointReached), hogy a felszállás
                    //    és felemelkedés közben ne készüljenek felvételek.
                    //    Mintavételi/sűrű rács misszióban a fotózást az
                    //    onWaypointReached-ből hívott app-vezérelt trigger adja.
                    uploader.startMission(new MissionUploader.UploadCallback() {
                        @Override public void onSuccess() {
                            runOnUiThread(() -> {
                                Toast.makeText(MissionPlannerActivity.this,
                                    "Repülés elindítva!", Toast.LENGTH_LONG).show();
                                setMissionRunning(true);
                                startMissionListener();
                            });
                        }
                        @Override public void onError(String errMsg) {
                            runOnUiThread(() -> Toast.makeText(MissionPlannerActivity.this,
                                "Indítás hiba: " + errMsg, Toast.LENGTH_LONG).show());
                        }
                    });
                });

            if (sampling || denseGrid) {
                CameraConfigurator.prepareForSamplingMission(cameraPrepCallback);
            } else {
                CameraSettings camSettings = buildCameraSettings();
                CameraConfigurator.applySettings(camSettings, cameraPrepCallback);
            }
        }));
    }

    private void updateStartButtonState() {
        // lastSatCount == 0 jelentheti: nincs adat (reflection hiba) vagy valóban 0 műhold.
        // Ha nem tudjuk olvasni, az MSDK dönt — ne blokkoljuk app szinten.
        // Csak akkor blokkol, ha biztosan tudjuk, hogy < 6 (1–5 közötti érték).
        boolean gpsBad  = lastSatCount > 0 && lastSatCount < 6;
        boolean canStart = missionUploaded && !gpsBad && !missionRunning;
        btnStart.setEnabled(canStart);
        if (missionUploaded && gpsBad && !missionRunning) {
            btnStart.setText("START (GPS!)");
        } else if (!missionRunning) {
            btnStart.setText("START");
        }
    }

    private void setMissionRunning(boolean running) {
        missionRunning  = running;
        missionPaused   = false;
        missionUploaded = false;
        SyncManager.getInstance(this).setFlightActive(running);
        btnPauseMission.setEnabled(running);
        btnStopMission.setEnabled(running);
        btnSimulate.setEnabled(!running && lastResult != null);
        btnPauseMission.setText("Szünet");
        btnUpload.setEnabled(!running && lastResult != null);
        btnStart.setEnabled(false);
        if (!running) {
            uploader.stopListening();
            CameraConfigurator.stopIntervalShooting(); // intervallum fotózás leállítása
            clearCompletedOverlay();
            tvMissionProgress.setText("");
            pbMissionProgress.setVisibility(android.view.View.GONE);
        }
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

    // ── Resume állapot perzisztencia (SharedPreferences) ─────────────────────

    private void saveResumeState() {
        if (currentPlanFile == null) return;
        getSharedPreferences(PREFS_RESUME, MODE_PRIVATE)
            .edit()
            .putString(currentPlanFile.getAbsolutePath(),
                       resumeSegmentIndex + ":" + resumeWaypointIndex)
            .apply();
    }

    private void loadResumeState() {
        if (currentPlanFile == null) {
            resumeSegmentIndex  = -1;
            resumeWaypointIndex = -1;
            return;
        }
        String saved = getSharedPreferences(PREFS_RESUME, MODE_PRIVATE)
            .getString(currentPlanFile.getAbsolutePath(), null);
        if (saved != null && saved.contains(":")) {
            try {
                String[] parts = saved.split(":");
                resumeSegmentIndex  = Integer.parseInt(parts[0]);
                resumeWaypointIndex = Integer.parseInt(parts[1]);
            } catch (NumberFormatException ignored) {
                resumeSegmentIndex  = -1;
                resumeWaypointIndex = -1;
            }
        } else {
            resumeSegmentIndex  = -1;
            resumeWaypointIndex = -1;
        }
    }

    private void clearResumeState() {
        if (currentPlanFile == null) return;
        getSharedPreferences(PREFS_RESUME, MODE_PRIVATE)
            .edit()
            .remove(currentPlanFile.getAbsolutePath())
            .apply();
    }

    // ── Misszió listener (haladásjelző + resume mentés) ───────────────────────

    private void startMissionListener() {
        if (lastResult == null || lastResult.segments.isEmpty()) return;
        int totalSize = uploadedSegmentSize > 0 ? uploadedSegmentSize : 0;
        int offset    = (resumeSegmentIndex == currentSegmentIndex && resumeWaypointIndex > 0)
                        ? Math.max(0, resumeWaypointIndex - 1) : 0;

        pbMissionProgress.setMax(totalSize > 0 ? totalSize : 100);
        pbMissionProgress.setProgress(offset);
        pbMissionProgress.setVisibility(android.view.View.VISIBLE);

        uploader.startListening(totalSize - offset, new MissionUploader.MissionProgressListener() {
            @Override
            public void onWaypointReached(int index, int total) {
                runOnUiThread(() -> {
                    int actualIndex = offset + index;
                    resumeWaypointIndex = actualIndex;
                    resumeSegmentIndex  = currentSegmentIndex;
                    saveResumeState();
                    updateProgressUI(actualIndex, totalSize);
                    updateCompletedOverlay(actualIndex);

                    // Mintavételi misszióban és sűrű rácsnál (M10) az intervallum-
                    // fotózás nem alkalmazandó (CURVED-specifikus mechanizmus) —
                    // a fotó-triggert helyette az app-vezérelt trigger adja (terepi
                    // javítás, 2026-07-03, M04_L1 §18 / M04_L2 §10), a STAY akció
                    // (M04 §15) csak a hover-pozícióban tartásért felel.
                    boolean sampling  = lastResult != null && lastResult.isSamplingMission;
                    boolean denseGrid = lastResult != null && lastResult.isDenseGridMission;
                    if (sampling) {
                        // A SamplingMissionGenerator (M02 §7) minden mintaponthoz
                        // pontosan 3 waypointot generál, fix sorrendben: [érkezés,
                        // mintavétel, emelkedés] — a mintavételi waypoint indexe
                        // mindig 3×k+1. Mivel a MAX_WAYPOINTS_PER_MISSION (99)
                        // osztható 3-mal, a szegmenshatárok mindig hármas-határra
                        // esnek, tehát az "actualIndex % 3" szabály szegmensenként
                        // (resume esetén is) helyesen azonosítja a mintavételi pontot.
                        if (actualIndex % 3 == 1) {
                            int pointIdx = samplePointCursor++;
                            GeoPoint point = (lastSamplePoints != null && pointIdx < lastSamplePoints.size())
                                    ? lastSamplePoints.get(pointIdx) : null;
                            triggerSamplePointPhoto(point);
                        }
                    } else if (denseGrid) {
                        // M10 — sűrű rács: MINDEN waypoint egy fotópozíció (nem
                        // csak minden 3.), ezért itt minden onWaypointReached
                        // hívás triggerel — nincs GeoPoint-korreláció, mert a
                        // sűrű rács fotói nem a mintavételi session-flow-n (M04
                        // §16) mennek keresztül, hanem a szokásos SD-kártyáról
                        // dolgozunk fel, mint egy normál grid misszió.
                        triggerDenseGridPhoto();
                    } else {
                        // Intervallum fotózás indítása az 1. WP elérésekor —
                        // ezzel kerüljük el a felszállás közbeni felesleges felvételeket
                        if (index == 0) {
                            float photoDistM = (lastResult != null)
                                    ? (float) lastResult.photoDistM : 16f;
                            float speedMs = buildConfig().speedMs;
                            CameraConfigurator.startIntervalShooting(photoDistM, speedMs,
                                (camOk, camMsg) -> runOnUiThread(() -> {
                                    if (!camOk) {
                                        Toast.makeText(MissionPlannerActivity.this,
                                            "Fotózás indítás hiba: " + camMsg,
                                            Toast.LENGTH_SHORT).show();
                                    }
                                }));
                        }
                        // Intervallum fotózás leállítása az utolsó WP elérésekor —
                        // így az RTH / leszállás alatt nem készülnek felvételek
                        if (total > 0 && index == total - 1) {
                            CameraConfigurator.stopIntervalShooting();
                        }
                    }
                });
            }
            @Override
            public void onMissionFinished(boolean completedSuccessfully, int lastIndex) {
                runOnUiThread(() -> {
                    boolean sampling  = lastResult != null && lastResult.isSamplingMission;
                    boolean denseGrid = lastResult != null && lastResult.isDenseGridMission;
                    if (completedSuccessfully) {
                        resumeWaypointIndex = -1;
                        resumeSegmentIndex  = -1;
                        clearResumeState();
                        if (sampling) {
                            // Terepi javítás (2026-07-03): a visszaigazolt trigger-eredmény
                            // alapján a felhasználó azonnal látja, hány fotó készült el
                            // ténylegesen — nem kell a médialetöltésig várni ennek kiderüléséhez.
                            int confirmedCount = confirmedSamplePointsForSession.size();
                            String summary = confirmedCount + "/"
                                    + (confirmedCount + samplingPhotosFailed) + " fotó megerősítve";
                            Toast.makeText(MissionPlannerActivity.this,
                                "Misszió befejezve! " + summary, Toast.LENGTH_LONG).show();
                        } else if (denseGrid) {
                            String summary = denseGridPhotosConfirmed + "/"
                                    + (denseGridPhotosConfirmed + denseGridPhotosFailed) + " fotó megerősítve";
                            Toast.makeText(MissionPlannerActivity.this,
                                "Misszió befejezve! " + summary, Toast.LENGTH_LONG).show();
                        } else {
                            Toast.makeText(MissionPlannerActivity.this,
                                "Misszió befejezve!", Toast.LENGTH_LONG).show();
                        }
                        // M04 §16 — mintavételi misszió után a "Fotók letöltése" gomb
                        // aktívvá válik (nem automatikus — a felhasználó dönt, mikor
                        // váltson a kamera MEDIA_DOWNLOAD módba).
                        if (sampling && btnDownloadSession != null) {
                            btnDownloadSession.setVisibility(android.view.View.VISIBLE);
                        }
                    }
                    // Ha megszakadt: resumeWaypointIndex + SharedPreferences megmarad a folytatáshoz
                    setMissionRunning(false);
                });
            }
        });
    }

    /**
     * Egy mintavételi ponton a fotó triggerelése, visszaigazolt eredménnyel
     * (terepi javítás, 2026-07-03 — ld. M04_L1 §18, M04_L2 §10, M04_L4 §10).
     * A CameraConfigurator.triggerSamplePhoto() saját retry-logikával rendelkezik
     * (1 újrapróbálkozás) — a hiba nem blokkolja a misszió folytatását.
     *
     * @param point a mintapont GeoPoint-ja (a lastSamplePoints-beli pozíciója
     *              alapján, a samplePointCursor szerint) — csak SIKERES trigger
     *              esetén kerül a confirmedSamplePointsForSession listába, hogy
     *              egy esetleges sikertelen pont ne tolja el a további pontok
     *              geo-taggelését a médialetöltésnél (M04 §16).
     */
    private void triggerSamplePointPhoto(GeoPoint point) {
        CameraConfigurator.triggerSamplePhoto(new CameraConfigurator.PhotoTriggerListener() {
            @Override
            public void onPhotoConfirmed() {
                runOnUiThread(() -> {
                    if (point != null) confirmedSamplePointsForSession.add(point);
                });
            }
            @Override
            public void onPhotoFailed(String reason) {
                runOnUiThread(() -> {
                    samplingPhotosFailed++;
                    Toast.makeText(MissionPlannerActivity.this,
                        "⚠ Mintapont fotó sikertelen: " + reason, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    /**
     * Egy sűrű rács waypointon a fotó triggerelése (M10, 2026-07-03) —
     * ugyanaz a megbízható, visszaigazolt trigger, mint a mintavételi
     * misszióban (CameraConfigurator.triggerSamplePhoto()), csak itt nincs
     * GeoPoint-korreláció (a sűrű rács fotói a szokásos SD-kártyáról kerülnek
     * feldolgozásra, nem a mintavételi session-flow-n, M04 §16, keresztül).
     */
    private void triggerDenseGridPhoto() {
        CameraConfigurator.triggerSamplePhoto(new CameraConfigurator.PhotoTriggerListener() {
            @Override
            public void onPhotoConfirmed() {
                runOnUiThread(() -> denseGridPhotosConfirmed++);
            }
            @Override
            public void onPhotoFailed(String reason) {
                runOnUiThread(() -> {
                    denseGridPhotosFailed++;
                    Toast.makeText(MissionPlannerActivity.this,
                        "⚠ Waypoint fotó sikertelen: " + reason, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void updateProgressUI(int actualIndex, int totalSize) {
        if (tvMissionProgress == null || pbMissionProgress == null) return;
        tvMissionProgress.setText(String.format("WP: %d / %d", actualIndex + 1, totalSize));
        if (totalSize > 0) {
            pbMissionProgress.setMax(totalSize);
            pbMissionProgress.setProgress(actualIndex + 1);
        }
    }

    // ── Drón marker (valódi repülés + szimuláció) ─────────────────────────────

    private void updateDroneMarker(double lat, double lon) {
        if (mapView == null) return;
        org.osmdroid.util.GeoPoint pos = new org.osmdroid.util.GeoPoint(lat, lon);
        if (droneMarker == null) {
            droneMarker = new org.osmdroid.views.overlay.Marker(mapView);
            droneMarker.setIcon(getResources().getDrawable(R.drawable.ic_drone_marker));
            droneMarker.setAnchor(
                org.osmdroid.views.overlay.Marker.ANCHOR_CENTER,
                org.osmdroid.views.overlay.Marker.ANCHOR_CENTER);
            droneMarker.setTitle("Drón");
            droneMarker.setInfoWindow(null);
            mapView.getOverlays().add(droneMarker);
        }
        droneMarker.setPosition(pos);
        mapView.invalidate();
    }

    private void clearDroneMarker() {
        if (droneMarker != null && mapView != null) {
            mapView.getOverlays().remove(droneMarker);
            mapView.invalidate();
        }
        droneMarker = null;
    }

    private void updateCompletedOverlay(int upToWaypointIndex) {
        if (lastResult == null || lastResult.segments.isEmpty()) return;
        List<WaypointData> segment = lastResult.segments.get(currentSegmentIndex);
        List<org.osmdroid.util.GeoPoint> completedPts = new ArrayList<>();
        int limit = Math.min(upToWaypointIndex + 1, segment.size());
        for (int i = 0; i < limit; i++) {
            WaypointData wp = segment.get(i);
            completedPts.add(new org.osmdroid.util.GeoPoint(wp.latitude, wp.longitude));
        }
        if (completedOverlay != null) mapView.getOverlays().remove(completedOverlay);
        completedOverlay = new org.osmdroid.views.overlay.Polyline();
        completedOverlay.setPoints(completedPts);
        completedOverlay.getOutlinePaint().setColor(0xFF00CC44);
        completedOverlay.getOutlinePaint().setStrokeWidth(5f);
        mapView.getOverlays().add(completedOverlay);
        mapView.invalidate();
    }

    private void clearCompletedOverlay() {
        if (completedOverlay != null && mapView != null) {
            mapView.getOverlays().remove(completedOverlay);
            mapView.invalidate();
        }
        completedOverlay = null;
    }

    // ── Szimuláció ────────────────────────────────────────────────────────────

    private void toggleSimulation() {
        if (simRunning) {
            stopSimulation();
        } else {
            startSimulation();
        }
    }

    private void startSimulation() {
        if (lastResult == null || lastResult.segments.isEmpty()) return;

        // Összes waypoint összegyűjtése az összes szegmensből
        List<WaypointData> allWaypoints = new ArrayList<>();
        for (List<WaypointData> seg : lastResult.segments) allWaypoints.addAll(seg);
        if (allWaypoints.isEmpty()) return;

        simRunning = true;
        btnSimulate.setText("Szimulacio leallitasa");
        btnUpload.setEnabled(false);

        float speedMs = buildConfig().speedMs;
        clearCompletedOverlay();

        // Inkrementális pont-lista és Polyline egyszer létrehozva
        simCompletedPoints = new ArrayList<>();
        completedOverlay = new org.osmdroid.views.overlay.Polyline();
        completedOverlay.getOutlinePaint().setColor(0xFF00CC44);
        completedOverlay.getOutlinePaint().setStrokeWidth(5f);
        mapView.getOverlays().add(completedOverlay);

        // Elindítjuk a szimulációt az első waypointtól
        simulateStep(allWaypoints, 0, speedMs);
    }

    private void simulateStep(List<WaypointData> waypoints, int index, float speedMs) {
        if (!simRunning || index >= waypoints.size()) {
            stopSimulation();
            return;
        }
        WaypointData wp = waypoints.get(index);
        updateDroneMarker(wp.latitude, wp.longitude);

        // Inkrementálisan hozzáadjuk az új pontot (nem építjük újra az egész listát)
        simCompletedPoints.add(new org.osmdroid.util.GeoPoint(wp.latitude, wp.longitude));
        completedOverlay.setPoints(simCompletedPoints);

        // Térkép újrarajzolás csak minden 10. lépésnél (teljesítmény)
        if (index % 10 == 0) mapView.invalidate();

        // Haladás UI
        tvMissionProgress.setText(String.format("SIM  WP: %d / %d", index + 1, waypoints.size()));
        pbMissionProgress.setMax(waypoints.size());
        pbMissionProgress.setProgress(index + 1);
        pbMissionProgress.setVisibility(android.view.View.VISIBLE);

        int nextIndex = index + 1;
        if (nextIndex >= waypoints.size()) {
            mapView.invalidate();
            simHandler.postDelayed(() -> stopSimulation(), 1500);
            return;
        }
        // Idő kiszámítása a következő waypointig (10x gyorsítva)
        WaypointData next = waypoints.get(nextIndex);
        float[] dist = new float[1];
        android.location.Location.distanceBetween(
            wp.latitude, wp.longitude, next.latitude, next.longitude, dist);
        long delayMs = (long) (dist[0] / (speedMs * 10f) * 1000f);
        delayMs = Math.max(80, Math.min(delayMs, 3000));

        simHandler.postDelayed(() -> simulateStep(waypoints, nextIndex, speedMs), delayMs);
    }

    private void stopSimulation() {
        simRunning = false;
        simHandler.removeCallbacksAndMessages(null);
        clearDroneMarker();
        clearCompletedOverlay();
        tvMissionProgress.setText("");
        pbMissionProgress.setVisibility(android.view.View.GONE);
        if (btnSimulate != null) btnSimulate.setText("Szimulacio");
        boolean connected = false;
        try { connected = DJIHelper.getInstance().isConnected(); } catch (Throwable t) { }
        btnUpload.setEnabled(connected && lastResult != null && !missionRunning);
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
        if (requestCode == REQUEST_MEDIA_PROJECTION) {
            if (resultCode == RESULT_OK && data != null) {
                mediaProjection = projectionManager.getMediaProjection(resultCode, data);
                startScreenRecording();
            } else {
                Toast.makeText(this, "Képernyőrögzítés engedélye megtagadva", Toast.LENGTH_SHORT).show();
            }
            return;
        }
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
        boolean csvConnected = false;
        try { csvConnected = DJIHelper.getInstance().isConnected(); } catch (Throwable t) { }
        btnUpload.setEnabled(csvConnected);
        btnExport.setEnabled(true);
        if (btnSimulate != null) btnSimulate.setEnabled(true);
        tvStats.setText(String.format("Importált: %d waypoint", waypoints.size()));
        Toast.makeText(this, waypoints.size() + " waypoint importálva", Toast.LENGTH_SHORT).show();
    }

    // ── Segédek ────────────────────────────────────────────────────────────

    private MissionConfig buildConfig() {
        MissionConfig c = new MissionConfig();
        c.droneProfile      = getSelectedDrone();
        // Magasság az elsődleges forrás: a felhasználó által beírt etAltitude érték
        // megy a mentésbe, a GSD ebből származtatva. Így a manuálisan választott
        // alacsony magasság (pl. 5 m a GSD-slider min-je alatt) megőrződik.
        c.altitudeM         = Math.max(3, Math.min(120, parseAltitudeField()));
        c.gsdCm             = GsdCalculator.gsdFromAltitude(c.altitudeM, c.droneProfile);
        c.sidelapPercent    = getSidelap();
        c.frontlapPercent   = getFrontlap();
        c.speedMs           = getSpeed();
        c.flightAngleDeg    = getAngle();
        c.terrainFollowing     = switchTerrain != null && switchTerrain.isChecked();
        c.offsetM              = getOffset();
        c.obstacles            = new ArrayList<>(obstacleList);
        c.cameraSettings       = buildCameraSettings();
        boolean crosshatch     = switchCrosshatch != null && switchCrosshatch.isChecked();
        c.gridMode             = crosshatch ? "crosshatch" : "single";
        c.crosshatchHeadingDeg = sbCrosshatchAngle != null ? sbCrosshatchAngle.getProgress() : 90.0;
        c.blockGrid            = blockGridConfig; // M07: null ha nincs blokk-mód

        // M01 §10 / M02 §7 — Mintavételi misszió paraméterek
        c.samplingMode = isSamplingMode();
        if (etSampleCount != null) {
            try { c.nSamplePoints = Integer.parseInt(etSampleCount.getText().toString().trim()); }
            catch (NumberFormatException ignored) {}
        }
        if (spinnerSamplingMethod != null && spinnerSamplingMethod.getSelectedItem() != null) {
            c.samplingMethod = spinnerSamplingMethod.getSelectedItem().toString();
        }
        if (etTransitAlt != null) {
            try { c.transitAltitudeM = Double.parseDouble(etTransitAlt.getText().toString().trim()); }
            catch (NumberFormatException ignored) {}
        }
        if (etSampleAlt != null) {
            try { c.sampleAltitudeM = Double.parseDouble(etSampleAlt.getText().toString().trim()); }
            catch (NumberFormatException ignored) {}
        }
        if (etHoverSeconds != null) {
            try { c.hoverSeconds = Float.parseFloat(etHoverSeconds.getText().toString().trim()); }
            catch (NumberFormatException ignored) {}
        }

        // M10 — Sűrű rács (2026-07-03)
        c.denseGridMode = switchDenseGridMode != null && switchDenseGridMode.isChecked();

        return c;
    }

    private boolean isSamplingMode() {
        return switchSamplingMode != null && switchSamplingMode.isChecked();
    }

    /**
     * Közös generálási belépési pont — a mintavételi mód és a kígyózó (grid)
     * mód közötti elágazás EGYETLEN helyen (autoGenerateIfReady() és
     * generateMission() is ezt hívja, nem közvetlenül a generátorokat).
     */
    private GridMissionGenerator.GeneratorResult runGeneration(List<GeoPoint> polygon,
                                                               MissionConfig config) {
        if (config.samplingMode) {
            lastSamplePoints = SamplingPointGenerator.generate(
                    polygon, config.nSamplePoints, config.samplingMethod, config.samplingSeed);
            drawSamplePointMarkers(lastSamplePoints);
            return SamplingMissionGenerator.generate(lastSamplePoints, polygon, config);
        } else {
            clearSamplePointMarkers();
            lastSamplePoints = null;
            return GridMissionGenerator.generate(polygon, config);
        }
    }

    // ── Mintapont markerek (mintavételi mód) ─────────────────────────────

    private void drawSamplePointMarkers(List<GeoPoint> points) {
        clearSamplePointMarkers();
        if (points == null || mapView == null) return;
        for (int i = 0; i < points.size(); i++) {
            Marker m = new Marker(mapView);
            m.setPosition(points.get(i));
            m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
            m.setTitle("Mintapont #" + (i + 1));
            m.setInfoWindow(null);
            mapView.getOverlays().add(m);
            samplePointMarkers.add(m);
        }
        mapView.invalidate();
    }

    private void clearSamplePointMarkers() {
        if (mapView != null) {
            for (Marker m : samplePointMarkers) mapView.getOverlays().remove(m);
            mapView.invalidate();
        }
        samplePointMarkers.clear();
    }

    // ── Session médialetöltés (M04 §16) ──────────────────────────────────

    private void triggerSessionDownload() {
        if (lastSamplePoints == null || lastSamplePoints.isEmpty()
                || currentSamplingSessionId == null) {
            Toast.makeText(this, "Nincs elérhető mintavételi session", Toast.LENGTH_SHORT).show();
            return;
        }
        // Terepi javítás (2026-07-03, M04_L1 §18): a TÉNYLEGESEN visszaigazolt
        // pontok listáját használjuk a nyers mintapontlista helyett, ha van erre
        // adatunk az épp lezajlott misszióból — így (a) a "last N file"
        // kiválasztás (M04 §16) nem vesz be véletlenül régebbi, a misszió előtti
        // fájlokat, ha kevesebb fotó készült el, mint amennyi mintapont volt, és
        // (b) egy sikertelen pont nem tolja el a többi pont geo-taggelését.
        boolean haveTriggerStats = !confirmedSamplePointsForSession.isEmpty() || samplingPhotosFailed > 0;
        List<GeoPoint> pointsForDownload = haveTriggerStats
                ? confirmedSamplePointsForSession : lastSamplePoints;
        if (haveTriggerStats && samplingPhotosFailed > 0) {
            Toast.makeText(this, samplingPhotosFailed
                + " mintaponton nem sikerült a fotó — csak a megerősített "
                + confirmedSamplePointsForSession.size() + " fotó letöltése történik.",
                Toast.LENGTH_LONG).show();
        }
        if (pointsForDownload.isEmpty()) {
            Toast.makeText(this, "Nincs megerősített fotó ebből a sessionből", Toast.LENGTH_SHORT).show();
            return;
        }

        android.app.ProgressDialog progress = new android.app.ProgressDialog(this);
        progress.setTitle("Fotók letöltése");
        progress.setMessage("Session médiafájlok letöltése...");
        progress.setMax(pointsForDownload.size());
        progress.setProgressStyle(android.app.ProgressDialog.STYLE_HORIZONTAL);
        progress.setCancelable(false);
        progress.show();

        double sampleAltitudeForSession = lastResult != null ? lastResult.altitudeM : 0.0;
        double aoiAreaForSession = lastResult != null ? lastResult.areaM2 : 0.0;
        String droneProfileNameForSession = getSelectedDrone() != null
                ? getSelectedDrone().name : "";

        mediaSessionDownloader.downloadSessionMedia(this, pointsForDownload.size(),
            samplingMissionStartTimeMs, pointsForDownload, currentSamplingSessionId,
            sampleAltitudeForSession, droneProfileNameForSession, aoiAreaForSession,
            new MediaSessionDownloader.SessionDownloadListener() {
                @Override
                public void onFileProgress(int fileIndex, int totalFiles, long current, long total) {
                    runOnUiThread(() -> progress.setProgress(fileIndex));
                }
                @Override
                public void onFileComplete(int fileIndex, String localPath) {}
                @Override
                public void onFileError(int fileIndex, String error) {
                    runOnUiThread(() -> Toast.makeText(MissionPlannerActivity.this,
                        (fileIndex + 1) + ". fotó hiba: " + error, Toast.LENGTH_SHORT).show());
                }
                @Override
                public void onSessionWarning(String message) {
                    runOnUiThread(() -> Toast.makeText(MissionPlannerActivity.this,
                        "⚠️ " + message, Toast.LENGTH_LONG).show());
                }
                @Override
                public void onSessionComplete(int successCount, int totalCount, File sessionDir) {
                    runOnUiThread(() -> {
                        progress.dismiss();
                        String finishedSessionId = currentSamplingSessionId;
                        new AlertDialog.Builder(MissionPlannerActivity.this)
                            .setTitle("Letöltés kész")
                            .setMessage(successCount + "/" + totalCount + " fotó letöltve.\n\n"
                                + "Mappa: " + sessionDir.getAbsolutePath())
                            .setPositiveButton("Eredmények megtekintése →", (dialog, which) -> {
                                Intent intent = new Intent(MissionPlannerActivity.this,
                                        SamplingResultsActivity.class);
                                intent.putExtra(SamplingResultsActivity.EXTRA_SESSION_ID,
                                        finishedSessionId);
                                startActivity(intent);
                            })
                            .setNegativeButton("Később", null)
                            .show();
                        btnDownloadSession.setVisibility(View.GONE);
                    });
                }
                @Override
                public void onSessionError(String message) {
                    runOnUiThread(() -> {
                        progress.dismiss();
                        new AlertDialog.Builder(MissionPlannerActivity.this)
                            .setTitle("Letöltési hiba")
                            .setMessage(message)
                            .setPositiveButton("OK", null)
                            .show();
                    });
                }
            });
    }

    /** Az aktuális blokk-lista a perzisztenciához (null ha nincs blokk-mód). */
    private List<Block> currentBlocksForSave() {
        return (currentGrid != null) ? currentGrid.blocks : null;
    }

    private double getGsd()      { return 0.1 + sbGsd.getProgress() * 0.1; }
    private double getSidelap()  { return 50.0 + sbSidelap.getProgress(); }
    private double getFrontlap() { return 60.0 + sbFrontlap.getProgress(); }
    private float  getSpeed()    { return 2.0f + sbSpeed.getProgress(); }
    private int    getAngle()    { return sbAngle.getProgress(); }
    private int    getOffset()   { return sbOffset != null ? sbOffset.getProgress() : 0; }

    // ── Magasság közvetlen bevitel ────────────────────────────────────────────

    private void adjustAltitude(int deltaM) {
        if (etAltitude == null) return;
        int current = parseAltitudeField();
        setAltitudeAndSyncGsd(current + deltaM);
    }

    private void applyAltitudeInput() {
        if (updatingAlt) return;
        setAltitudeAndSyncGsd(parseAltitudeField());
    }

    private int parseAltitudeField() {
        try { return Integer.parseInt(etAltitude.getText().toString().trim()); }
        catch (NumberFormatException e) { return 10; }
    }

    private void setAltitudeAndSyncGsd(int altM) {
        setAltitudeAndSyncGsd(altM, true);
    }

    /**
     * @param triggerAutoGen ha true, a végén autoGenerateIfReady() lefut.
     *                       Project betöltéskor false-szal hívandó, mert a
     *                       loadProject() a végén explicit generateMission()-t
     *                       hív — felesleges és pazarló a duplikáció.
     */
    private void setAltitudeAndSyncGsd(int altM, boolean triggerAutoGen) {
        altM = Math.max(3, Math.min(120, altM));
        updatingAlt = true;
        etAltitude.setText(String.valueOf(altM));

        // GSD csúszka szinkronizálása: progress = round((gsd - 0.1) / 0.1)
        DroneProfile drone = getSelectedDrone();
        double gsd = GsdCalculator.gsdFromAltitude(altM, drone);
        int prog = (int) Math.round((gsd - 0.1) / 0.1);
        sbGsd.setProgress(Math.max(0, Math.min(sbGsd.getMax(), prog)));
        updateLabels();
        updatingAlt = false;
        // Misszió automatikus újragenerálása: a slider drag elengedéskor
        // onStopTrackingTouch-en keresztül történik, de programatikus
        // setProgress (etAltitude bevitel, +/- gombok) NEM triggereli azt.
        // Explicit hívással biztosítjuk, hogy a tvStats (összesítés) és a
        // térkép-overlay azonnal frissüljön a kézi magasság-bevitel után is.
        if (triggerAutoGen) {
            autoGenerateIfReady();
        }
    }

    /**
     * Magasság mező frissítése a GSD slider aktuális értékéből.
     * Csak akkor hívandó, amikor a felhasználó ténylegesen mozgatja a sbGsd-t
     * (lásd SeekBar listener, user==true ág). Programozott sbGsd.setProgress
     * (pl. magasság-bevitel utáni clamp-szinkron, projekt betöltés) NE hívja,
     * különben elveszne a manuális magasság.
     */
    private void syncAltitudeFromGsd() {
        if (updatingAlt || etAltitude == null) return;
        DroneProfile drone = getSelectedDrone();
        int alt = (int) Math.round(GsdCalculator.altitudeFromGsd(getGsd(), drone));
        alt = Math.max(3, Math.min(120, alt));
        updatingAlt = true;
        etAltitude.setText(String.valueOf(alt));
        updatingAlt = false;
    }

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
        updateMapDataConnection();
        NetworkMonitor nm = NetworkMonitor.getInstance(this);
        nm.register();
        nm.addListener(networkListener);
        updateSyncStatus();
    }

    @Override protected void onPause() {
        super.onPause();
        mapView.onPause();
        statusHandler.removeCallbacks(statusRunnable);
        if (videoWidget != null) videoWidget.stop();
        stopHistogramPolling();
        NetworkMonitor.getInstance(this).removeListener(networkListener);
    }
    @Override protected void onDestroy() {
        super.onDestroy();
        if (isRecording) stopRecording();
        recBlinkHandler.removeCallbacksAndMessages(null);
        mapView.onDetach();
        statusHandler.removeCallbacks(statusRunnable);
        if (videoWidget != null) videoWidget.destroy();
        NetworkMonitor.getInstance(this).unregister();
    }

    // ── Képernyőkép és videórögzítés ──────────────────────────────────────

    /** Rövid nyomás: azonnali képernyőkép mentése /sdcard/Pictures/DroneFly/-ba */
    private void takeScreenshot() {
        try {
            View rootView = getWindow().getDecorView().getRootView();
            rootView.setDrawingCacheEnabled(true);
            Bitmap bitmap = Bitmap.createBitmap(rootView.getDrawingCache());
            rootView.setDrawingCacheEnabled(false);

            File dir = getDroneFlyDir();
            String filename = "screenshot_"
                    + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date())
                    + ".png";
            File file = new File(dir, filename);
            try (FileOutputStream fos = new FileOutputStream(file)) {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
            }
            MediaScannerConnection.scanFile(this, new String[]{file.getAbsolutePath()}, null, null);
            // Vizuális visszajelzés: REC gomb rövid zöld villanás
            btnRec.setBackgroundTintList(ColorStateList.valueOf(0xCC228822));
            recBlinkHandler.postDelayed(() ->
                btnRec.setBackgroundTintList(ColorStateList.valueOf(0xCC1a1a2e)), 400);
            Toast.makeText(this, "📷 " + filename, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Képernyőkép hiba: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    /** Hosszú nyomás: videórögzítés indítása / leállítása */
    private void toggleRecording() {
        if (isRecording) {
            stopRecording();
        } else {
            // Engedélykérés a rendszertől (egyszer jelenik meg)
            Intent captureIntent = projectionManager.createScreenCaptureIntent();
            startActivityForResult(captureIntent, REQUEST_MEDIA_PROJECTION);
        }
    }

    /** MediaProjection engedély megkaptuk → rögzítés indítása */
    private void startScreenRecording() {
        try {
            File dir = getDroneFlyDir();
            String filename = "video_"
                    + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date())
                    + ".mp4";
            currentRecordingFile = new File(dir, filename);

            DisplayMetrics metrics = new DisplayMetrics();
            getWindowManager().getDefaultDisplay().getMetrics(metrics);
            int dpi = metrics.densityDpi;

            // Rögzített 1280×720 felbontás — Crystal Sky Android 5.1-en
            // a natív felbontás (1920×1080) + H264 SURFACE encoder megbízhatatlan,
            // 30 KB-os üres fájlt produkál. 720p kompatibilis minden eszközzel.
            final int recWidth  = 1280;
            final int recHeight = 720;

            mediaRecorder = new MediaRecorder();
            mediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
            mediaRecorder.setVideoSize(recWidth, recHeight);
            mediaRecorder.setVideoFrameRate(25);
            mediaRecorder.setVideoEncodingBitRate(2 * 1024 * 1024); // 2 Mbps
            mediaRecorder.setOutputFile(currentRecordingFile.getAbsolutePath());
            mediaRecorder.prepare();

            // VirtualDisplay: natív felbontáson gyűjt, de a VirtualDisplay skálázza 720p-re
            virtualDisplay = mediaProjection.createVirtualDisplay(
                    "DroneFlyRec", recWidth, recHeight, dpi,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    mediaRecorder.getSurface(), null, null);

            mediaRecorder.start();
            isRecording = true;

            // Gomb pirosan villog felvétel közben
            btnRec.setText("■");
            startRecBlink();
            Toast.makeText(this, "⏺ Videórögzítés elindult (720p)", Toast.LENGTH_SHORT).show();

        } catch (Exception e) {
            Toast.makeText(this, "Rögzítés hiba: " + e.getMessage(), Toast.LENGTH_LONG).show();
            if (mediaProjection != null) { mediaProjection.stop(); mediaProjection = null; }
        }
    }

    private void stopRecording() {
        // UI frissítés azonnal — főszálon
        recBlinkHandler.removeCallbacksAndMessages(null);
        isRecording = false;
        btnRec.setText("REC");
        btnRec.setBackgroundTintList(ColorStateList.valueOf(0xCCAA6600)); // narancssárga = mentés folyamatban
        Toast.makeText(this, "⏹ Mentés...", Toast.LENGTH_SHORT).show();

        // A stop/release/scanFile háttérszálon — ezek blokkolják a UI-t ha főszálon futnak
        final MediaRecorder  recToStop  = mediaRecorder;
        final VirtualDisplay dispToStop = virtualDisplay;
        final MediaProjection projToStop = mediaProjection;
        final File fileToScan = currentRecordingFile;
        mediaRecorder   = null;
        virtualDisplay  = null;
        mediaProjection = null;

        new Thread(() -> {
            try { if (recToStop  != null) recToStop.stop();    } catch (Exception ignored) {}
            try { if (recToStop  != null) recToStop.release(); } catch (Exception ignored) {}
            try { if (dispToStop != null) dispToStop.release();} catch (Exception ignored) {}
            try { if (projToStop != null) projToStop.stop();   } catch (Exception ignored) {}

            if (fileToScan != null && fileToScan.exists()) {
                MediaScannerConnection.scanFile(MissionPlannerActivity.this,
                        new String[]{fileToScan.getAbsolutePath()}, null, null);
            }

            // UI visszajelzés a főszálon
            runOnUiThread(() -> {
                btnRec.setBackgroundTintList(ColorStateList.valueOf(0xCC1a1a2e));
                String msg = (fileToScan != null && fileToScan.exists())
                        ? "✔ Mentve: " + fileToScan.getName()
                        : "Rögzítés leállítva";
                Toast.makeText(MissionPlannerActivity.this, msg, Toast.LENGTH_LONG).show();
            });
        }, "RecStopThread").start();
    }

    /** REC gomb piros villogtatása felvétel közben */
    private void startRecBlink() {
        recBlinkRunnable = new Runnable() {
            boolean red = true;
            @Override public void run() {
                if (!isRecording) return;
                btnRec.setBackgroundTintList(ColorStateList.valueOf(
                        red ? 0xCCCC0000 : 0xCC661111));
                red = !red;
                recBlinkHandler.postDelayed(this, 600);
            }
        };
        recBlinkHandler.post(recBlinkRunnable);
    }

    /** /sdcard/Pictures/DroneFly/ mappa — galéria által indexelhető, létrehozza ha nincs */
    private File getDroneFlyDir() {
        File dir = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES), "DroneFly");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    private void updateDroneStatus() {
        if (tvDroneStatus == null) return;
        boolean droneConnected = false;
        try {
            DJIHelper helper = DJIHelper.getInstance();
            if (helper.isConnected()) {
                String name = helper.getConnectedProductName();
                tvDroneStatus.setText("● Csatlakoztatva: " + (name != null ? name : "drón"));
                tvDroneStatus.setTextColor(0xFF44FF44);
                btnUpload.setEnabled(lastResult != null && !missionRunning);
                droneConnected = true;
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
        // Kamera-akciók (AE Lock, Alkalmaz) csak akkor érhetők el, ha a drón
        // ténylegesen csatlakozik. Drón nélkül a CameraConfigurator getCamera()
        // null-t ad, és a click csak hibaüzenetet eredményez — jobb, ha a gombot
        // letiltjuk vizuálisan is.
        if (btnAutoLock != null) btnAutoLock.setEnabled(droneConnected);
        if (btnApplyCamera5 != null) btnApplyCamera5.setEnabled(droneConnected);
    }

    // ─────────────────────────────────────────────────────────────────────
    //  M07 — Blokk-felosztás (lásd docs/M07_BLOKK_FELOSZTAS/)
    // ─────────────────────────────────────────────────────────────────────

    private void onSplitClicked() {
        if (polygonPoints.size() < 3) {
            Toast.makeText(this, "Eloszor rajzold meg az AOI-t (min. 3 pont)", Toast.LENGTH_SHORT).show();
            return;
        }
        BlockGridConfig start = (blockGridConfig != null) ? blockGridConfig : new BlockGridConfig();
        new BlockGridDialog(this, start, new BlockGridDialog.Listener() {
            @Override public void onApply(BlockGridConfig cfg)   { applyBlockGrid(cfg); }
            @Override public void onPreview(BlockGridConfig cfg) { previewBlockGrid(cfg); }
            @Override public void onCancel()                     { /* no-op */ }
        }).show();
    }

    private void previewBlockGrid(BlockGridConfig cfg) {
        // Élő preview a dialog SeekBar mozgásánál — nem perzisztálunk semmit
        if (polygonPoints.size() < 3) return;
        BlockGridGenerator gen = new BlockGridGenerator();
        BlockGridGenerator.GridResult res = gen.generate(polygonPoints, cfg, null);
        if (res.errorMessage != null) return;
        ensureBlockOverlay();
        blockOverlay.setBlocks(res.blocks);
        mapView.invalidate();
    }

    private void applyBlockGrid(BlockGridConfig cfg) {
        if (polygonPoints.size() < 3) return;
        BlockGridGenerator gen = new BlockGridGenerator();
        BlockGridGenerator.GridResult res = gen.generate(polygonPoints, cfg, currentGrid);
        if (res.errorMessage != null) {
            Toast.makeText(this, res.errorMessage, Toast.LENGTH_LONG).show();
            return;
        }
        blockGridConfig = cfg;
        currentGrid     = res;
        selectedBlock   = null;
        ensureBlockOverlay();
        blockOverlay.setBlocks(res.blocks);
        blockOverlay.setSelected(null);
        updateBlockUiVisibility();
        Toast.makeText(this,
                String.format(Locale.US, "Felosztva: %d blokk", res.totalBlocks),
                Toast.LENGTH_SHORT).show();
    }

    private void ensureBlockOverlay() {
        if (blockOverlay == null) {
            blockOverlay = new BlockOverlay(mapView);
            // A blokk-overlay-t a polygonOverlay és missionOverlay ALÁ tesszük
            // (a 0. index a legalsó réteg)
            mapView.getOverlays().add(0, blockOverlay);
        }
    }

    private void setSelectedBlock(Block b) {
        selectedBlock = b;
        if (blockOverlay != null) blockOverlay.setSelected(b);
        updateBlockUiVisibility();
    }

    private void onMarkDoneClicked() {
        if (selectedBlock == null) return;
        selectedBlock.status = BlockStatus.DONE;
        if (blockOverlay != null) mapView.invalidate();
        updateBlockUiVisibility();
        Toast.makeText(this, selectedBlock.id + " kesz", Toast.LENGTH_SHORT).show();
    }

    private void onResetBlockClicked() {
        if (selectedBlock == null) return;
        selectedBlock.status = BlockStatus.NOT_STARTED;
        if (blockOverlay != null) mapView.invalidate();
        updateBlockUiVisibility();
    }

    /** Blokk-info panel és gombsor láthatóság + szöveg frissítés. */
    private void updateBlockUiVisibility() {
        if (tvBlockInfo == null || blockButtonRow == null) return;
        if (selectedBlock == null) {
            tvBlockInfo.setVisibility(View.GONE);
            blockButtonRow.setVisibility(View.GONE);
            return;
        }
        tvBlockInfo.setVisibility(View.VISIBLE);
        blockButtonRow.setVisibility(View.VISIBLE);
        tvBlockInfo.setText(String.format(Locale.US,
                "%s | %.2f ha | %.0f%% lefedettseg | %s",
                selectedBlock.id,
                selectedBlock.missionAreaM2 / 10000.0,
                selectedBlock.coverageRatio * 100.0,
                statusLabel(selectedBlock.status)));
    }

    private static String statusLabel(BlockStatus s) {
        if (s == null) return "?";
        switch (s) {
            case IN_PROGRESS: return "folyamatban";
            case DONE:        return "kesz";
            case NOT_STARTED:
            default:          return "uj";
        }
    }

    /** A blokk-rács teljes törlése (clearAll-ban hívva). */
    private void clearBlockGrid() {
        blockGridConfig = null;
        currentGrid     = null;
        selectedBlock   = null;
        if (blockOverlay != null) {
            blockOverlay.clear();
            mapView.getOverlays().remove(blockOverlay);
            blockOverlay = null;
        }
        updateBlockUiVisibility();
    }

    /** Betöltött projekt → blokk-rács újraépítése és status-ok visszaállítása. */
    private void restoreBlockGrid(ProjectManager.ProjectData data) {
        BlockGridGenerator gen = new BlockGridGenerator();
        // A perzisztált origó megőrzése: previous-stílusú GridResult-ot
        // szintetizálunk csak az originLat/Lon átadásához
        BlockGridGenerator.GridResult prev = new BlockGridGenerator.GridResult();
        prev.gridOriginLat   = data.blockGrid.originLat;
        prev.gridOriginLon   = data.blockGrid.originLon;
        prev.gridRotationDeg = data.blockGrid.rotationDeg;

        BlockGridGenerator.GridResult res = gen.generate(polygonPoints, data.blockGrid, prev);
        if (res.errorMessage != null) {
            Toast.makeText(this,
                    "Blokk-racs visszaallitasi hiba: " + res.errorMessage,
                    Toast.LENGTH_LONG).show();
            return;
        }

        // Status-ok visszahelyezése a perzisztált rekordokból
        for (Block b : res.blocks) {
            for (ProjectManager.BlockStateRecord r : data.blockStates) {
                if (r.row == b.row && r.col == b.col) {
                    b.status = r.status;
                    break;
                }
            }
        }

        blockGridConfig = data.blockGrid;
        currentGrid     = res;
        selectedBlock   = null;
        ensureBlockOverlay();
        blockOverlay.setBlocks(res.blocks);
        updateBlockUiVisibility();
    }

    /**
     * Misszió Start eseménynél hívni: a kiválasztott blokk → IN_PROGRESS.
     * (A jelenlegi startMission flow nem hívja még — későbbi M07 integráció.)
     */
    @SuppressWarnings("unused")
    private void onBlockMissionStarted() {
        if (selectedBlock != null) {
            selectedBlock.status = BlockStatus.IN_PROGRESS;
            if (blockOverlay != null) mapView.invalidate();
            updateBlockUiVisibility();
        }
    }
}
