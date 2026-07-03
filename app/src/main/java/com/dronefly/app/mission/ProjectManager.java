package com.dronefly.app.mission;

import android.content.Context;

import com.dronefly.app.model.Block;
import com.dronefly.app.model.BlockGridConfig;
import com.dronefly.app.model.BlockStatus;
import com.dronefly.app.model.CameraSettings;
import com.dronefly.app.model.MissionConfig;
import com.dronefly.app.model.ObstacleData;

import org.json.JSONArray;
import org.json.JSONObject;
import org.osmdroid.util.GeoPoint;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.UUID;

/**
 * Repülési terv mentése és betöltése .flightprogram.json formátumban.
 *
 * Séma: dronterapia_flight_program v1.0 — közös Dronterapia / DroneFly / Mission Hub formátum.
 * Visszafelé-kompatibilis: a régi .dronefly.json fájlok automatikusan konvertálódnak betöltéskor.
 *
 * Elérési út: <app external files>/missions/<névvel>.flightprogram.json
 */
public class ProjectManager {

    public static final String FORMAT_VERSION = "1.1"; // 1.1: M07 block_grid + block_states (opt.)
    public static final String FORMAT_NAME    = "dronterapia_flight_program";
    public static final String FILE_EXT       = ".flightprogram.json";
    public static final String LEGACY_EXT     = ".dronefly.json";
    public static final String MISSIONS_DIR   = "missions";

    // ─────────────────────────────────────────────────────────────────────────
    //  Könyvtár
    // ─────────────────────────────────────────────────────────────────────────

    public static File getProjectsDir(Context ctx) {
        File dir = new File(ctx.getExternalFilesDir(null), MISSIONS_DIR);
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Mentés — új fájl
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Új repülési programot ment fájlba.
     * A fájlnév a projekt nevéből képződik; az ID automatikusan generálódik.
     */
    public static File saveProject(Context ctx,
                                   String name,
                                   List<GeoPoint> polygon,
                                   MissionConfig config) throws Exception {
        return saveProject(ctx, name, polygon, config, null, null, null);
    }

    /**
     * Új repülési programot ment fájlba, opcionális Dronterapia parcella-hivatkozással.
     *
     * @param existingId  Ha nem null: meglévő UUID megtartása (sync után visszaírás).
     * @param parcelId    Dronterapia parcella UUID, null ha tableten rajzolt polygon.
     * @param parcelName  Parcella neve, null ha ismeretlen.
     */
    public static File saveProject(Context ctx,
                                   String name,
                                   List<GeoPoint> polygon,
                                   MissionConfig config,
                                   String existingId,
                                   String parcelId,
                                   String parcelName) throws Exception {
        return saveProject(ctx, name, polygon, config, existingId, parcelId, parcelName, null);
    }

    /**
     * Új repülési programot ment fájlba, opcionális blokk-lista perzisztálással (M07).
     *
     * @param blocks  jelenlegi blokk-lista (status-okhoz); null ha nincs blokk-mód
     */
    public static File saveProject(Context ctx,
                                   String name,
                                   List<GeoPoint> polygon,
                                   MissionConfig config,
                                   String existingId,
                                   String parcelId,
                                   String parcelName,
                                   List<Block> blocks) throws Exception {
        String id  = (existingId != null && !existingId.isEmpty()) ? existingId : UUID.randomUUID().toString();
        String now = utcNow();
        JSONObject root = buildJson(id, name, now, now, polygon, config, parcelId, parcelName, true, blocks);

        String safeName = sanitizeFilename(name);
        File file = new File(getProjectsDir(ctx), safeName + FILE_EXT);
        writeJson(file, root);
        return file;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Mentés — meglévő fájl felülírása
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Meglévő repülési programot ment vissza a fájlba (SAVE / felülírás).
     * A created_at és az ID megmarad az eredeti fájlból.
     */
    public static void saveProjectToFile(Context ctx,
                                          File targetFile,
                                          String name,
                                          List<GeoPoint> polygon,
                                          MissionConfig config) throws Exception {
        saveProjectToFile(ctx, targetFile, name, polygon, config, null, null, null, true);
    }

    public static void saveProjectToFile(Context ctx,
                                          File targetFile,
                                          String name,
                                          List<GeoPoint> polygon,
                                          MissionConfig config,
                                          String existingId,
                                          String parcelId,
                                          String parcelName,
                                          boolean syncPending) throws Exception {
        saveProjectToFile(ctx, targetFile, name, polygon, config,
                          existingId, parcelId, parcelName, syncPending, null);
    }

    /**
     * Meglévő fájl felülírása opcionális blokk-lista perzisztálással (M07).
     */
    public static void saveProjectToFile(Context ctx,
                                          File targetFile,
                                          String name,
                                          List<GeoPoint> polygon,
                                          MissionConfig config,
                                          String existingId,
                                          String parcelId,
                                          String parcelName,
                                          boolean syncPending,
                                          List<Block> blocks) throws Exception {
        String createdAt = utcNow();
        String id = (existingId != null && !existingId.isEmpty()) ? existingId : UUID.randomUUID().toString();
        try {
            ProjectData existing = loadProject(targetFile);
            if (existing.createdAt != null && !existing.createdAt.isEmpty()) createdAt = existing.createdAt;
            if (existing.id != null && !existing.id.isEmpty() && (existingId == null || existingId.isEmpty())) {
                id = existing.id;
            }
        } catch (Exception ignored) {}

        JSONObject root = buildJson(id, name, createdAt, utcNow(),
                                     polygon, config, parcelId, parcelName, syncPending, blocks);
        writeJson(targetFile, root);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  JSON építés (belső)
    // ─────────────────────────────────────────────────────────────────────────

    private static JSONObject buildJson(String id,
                                         String name,
                                         String createdAt,
                                         String updatedAt,
                                         List<GeoPoint> polygon,
                                         MissionConfig config,
                                         String parcelId,
                                         String parcelName,
                                         boolean syncPending,
                                         List<Block> blocks) throws Exception {
        JSONObject root = new JSONObject();
        root.put("version", FORMAT_VERSION);
        root.put("format",  FORMAT_NAME);

        // ── Metadata ──────────────────────────────────────────────────────────
        JSONObject meta = new JSONObject();
        meta.put("id",            id);
        meta.put("name",          name);
        meta.put("created_at",    createdAt);
        meta.put("updated_at",    updatedAt);
        meta.put("source_system", "dronefly");
        meta.put("sync_pending",  syncPending);
        root.put("metadata", meta);

        // ── Parcella ──────────────────────────────────────────────────────────
        JSONObject parcel = new JSONObject();
        parcel.put("parcel_id", parcelId   != null ? parcelId   : JSONObject.NULL);
        parcel.put("name",      parcelName != null ? parcelName : JSONObject.NULL);

        // GeoJSON Polygon — koordináták [lon, lat] sorrendben; zárt gyűrű (első == utolsó)
        JSONArray ring = new JSONArray();
        for (GeoPoint p : polygon) {
            JSONArray pt = new JSONArray();
            pt.put(p.getLongitude());
            pt.put(p.getLatitude());
            ring.put(pt);
        }
        if (!polygon.isEmpty()) {
            JSONArray closing = new JSONArray();
            closing.put(polygon.get(0).getLongitude());
            closing.put(polygon.get(0).getLatitude());
            ring.put(closing);
        }
        JSONArray rings = new JSONArray();
        rings.put(ring);
        JSONObject geometry = new JSONObject();
        geometry.put("type",        "Polygon");
        geometry.put("coordinates", rings);
        parcel.put("geometry", geometry);
        root.put("parcel", parcel);

        // ── Drone ─────────────────────────────────────────────────────────────
        JSONObject drone = new JSONObject();
        if (config.droneProfile != null) {
            drone.put("drone_id", sanitizeFilename(config.droneProfile.name));
            drone.put("name",     config.droneProfile.name);
            JSONObject cam = new JSONObject();
            cam.put("sensor_width_mm",  config.droneProfile.sensorWidthMm);
            cam.put("sensor_height_mm", config.droneProfile.sensorHeightMm);
            cam.put("focal_length_mm",  config.droneProfile.focalLengthMm);
            cam.put("image_width_px",   config.droneProfile.imageWidthPx);
            cam.put("image_height_px",  config.droneProfile.imageHeightPx);
            drone.put("camera", cam);
        } else {
            drone.put("drone_id", "unknown");
            drone.put("name",     "Ismeretlen drón");
            drone.put("camera",   JSONObject.NULL);
        }
        root.put("drone", drone);

        // ── Flight settings ───────────────────────────────────────────────────
        JSONObject fs = new JSONObject();
        fs.put("gsd_cm",                config.gsdCm);
        fs.put("altitude_m",            config.altitudeM);
        fs.put("speed_ms",              config.speedMs);
        fs.put("front_overlap_percent", config.frontlapPercent);
        fs.put("side_overlap_percent",  config.sidelapPercent);
        fs.put("heading_deg",           config.flightAngleDeg);
        fs.put("grid_mode",             config.gridMode);
        fs.put("crosshatch_heading_deg",config.crosshatchHeadingDeg);
        fs.put("terrain_following",     config.terrainFollowing);
        fs.put("offset_m",              config.offsetM);
        fs.put("return_home",           config.returnHome);
        fs.put("start_corner",          config.startCorner);
        fs.put("dense_grid_mode",       config.denseGridMode); // M10, 2026-07-03
        root.put("flight_settings", fs);

        // ── Mintavétel (M01 §10 / M02 §7) — opcionális, csak samplingMode esetén ─
        // Terepi javítás (2026-07-03): korábban ezek a mezők NEM kerültek mentésre.
        if (config.samplingMode) {
            JSONObject sampling = new JSONObject();
            sampling.put("enabled",            true);
            sampling.put("n_sample_points",    config.nSamplePoints);
            sampling.put("method",             config.samplingMethod);
            sampling.put("seed",               config.samplingSeed);
            sampling.put("transit_altitude_m", config.transitAltitudeM);
            sampling.put("sample_altitude_m",  config.sampleAltitudeM);
            sampling.put("hover_seconds",      config.hoverSeconds);
            root.put("sampling", sampling);
        }

        // ── Kamera beállítások — opcionális, mindig mentve ────────────────────
        // Terepi javítás (2026-07-03): korábban a fájlformátum (JPEG/RAW) és a
        // többi kamera-beállítás sem került mentésre.
        if (config.cameraSettings != null) {
            JSONObject cam = new JSONObject();
            cam.put("auto_mode",            config.cameraSettings.autoMode);
            cam.put("photo_mode",           config.cameraSettings.photoMode.name());
            cam.put("iso",                  config.cameraSettings.iso.name());
            cam.put("aperture",             config.cameraSettings.aperture.name());
            cam.put("shutter_speed",        config.cameraSettings.shutterSpeed.name());
            cam.put("white_balance",        config.cameraSettings.whiteBalance.name());
            cam.put("white_balance_kelvin", config.cameraSettings.whiteBalanceKelvin);
            cam.put("file_format",          config.cameraSettings.fileFormat.name());
            root.put("camera_settings", cam);
        }

        // ── Akadályok ─────────────────────────────────────────────────────────
        JSONArray obsArr = new JSONArray();
        for (int i = 0; i < config.obstacles.size(); i++) {
            ObstacleData o = config.obstacles.get(i);
            String obsId = (o.id != null && !o.id.isEmpty()) ? o.id : "obst" + (i + 1);
            JSONObject oj = new JSONObject();
            oj.put("id",    obsId);
            oj.put("label", (o.label != null && !o.label.isEmpty()) ? o.label : obsId);
            JSONObject center = new JSONObject();
            center.put("lat", o.latitude);
            center.put("lon", o.longitude);
            oj.put("center",   center);
            oj.put("radius_m", o.radiusM);
            oj.put("height_m", o.heightM);
            obsArr.put(oj);
        }
        root.put("obstacles", obsArr);

        // ── Block grid (M07) — opcionális ─────────────────────────────────────
        if (config.blockGrid != null) {
            BlockGridConfig bg = config.blockGrid;
            JSONObject bgJson = new JSONObject();
            bgJson.put("enabled",              true);
            bgJson.put("cell_width_m",         bg.cellWidthM);
            bgJson.put("cell_height_m",        bg.cellHeightM);
            bgJson.put("rotation_deg",         bg.rotationDeg);
            bgJson.put("overlap_buffer_m",     bg.overlapBufferM);
            bgJson.put("min_coverage_percent", bg.minCoveragePercent);
            bgJson.put("origin_mode",          bg.originMode);
            if (!Double.isNaN(bg.originLat)) bgJson.put("origin_lat", bg.originLat);
            if (!Double.isNaN(bg.originLon)) bgJson.put("origin_lon", bg.originLon);
            root.put("block_grid", bgJson);

            // block_states — csak ha van legalább egy blokk
            if (blocks != null && !blocks.isEmpty()) {
                JSONArray statesArr = new JSONArray();
                for (Block b : blocks) {
                    JSONObject bj = new JSONObject();
                    bj.put("id",     b.id);
                    bj.put("row",    b.row);
                    bj.put("col",    b.col);
                    bj.put("status", b.status.name());
                    statesArr.put(bj);
                }
                root.put("block_states", statesArr);
            }
        }

        return root;
    }

    private static void writeJson(File file, JSONObject root) throws Exception {
        FileWriter fw = new FileWriter(file);
        fw.write(root.toString(2));
        fw.close();
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Betöltés
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Betölt egy repülési programot fájlból.
     * Automatikusan felismeri a régi .dronefly.json és az új .flightprogram.json formátumot.
     */
    public static ProjectData loadProject(File file) throws Exception {
        BufferedReader br = new BufferedReader(new FileReader(file));
        StringBuilder sb  = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line).append('\n');
        br.close();

        JSONObject root   = new JSONObject(sb.toString());
        String     format = root.optString("format", "");
        return FORMAT_NAME.equals(format) ? loadNew(root, file) : loadLegacy(root, file);
    }

    /** Új séma betöltése (.flightprogram.json) */
    private static ProjectData loadNew(JSONObject root, File file) throws Exception {
        ProjectData data = new ProjectData();

        JSONObject meta = root.optJSONObject("metadata");
        if (meta != null) {
            data.id          = meta.optString("id", "");
            data.name        = meta.optString("name", basename(file));
            data.createdAt   = meta.optString("created_at", "");
            data.updatedAt   = meta.optString("updated_at", "");
            data.syncPending = meta.optBoolean("sync_pending", false);
        } else {
            data.name = basename(file);
        }

        JSONObject parcel = root.optJSONObject("parcel");
        if (parcel != null) {
            data.parcelId   = parcel.isNull("parcel_id") ? null : parcel.optString("parcel_id", null);
            data.parcelName = parcel.isNull("name")      ? null : parcel.optString("name", null);
            JSONObject geom = parcel.optJSONObject("geometry");
            if (geom != null) {
                JSONArray outer = geom.optJSONArray("coordinates");
                if (outer != null && outer.length() > 0) {
                    JSONArray ring = outer.getJSONArray(0);
                    // GeoJSON: [lon, lat]; utolsó pont duplikált (zárt gyűrű) — kihagyjuk
                    int n = ring.length();
                    for (int i = 0; i < n - 1; i++) {
                        JSONArray pt = ring.getJSONArray(i);
                        data.polygon.add(new GeoPoint(pt.getDouble(1), pt.getDouble(0)));
                    }
                }
            }
        }

        JSONObject drone = root.optJSONObject("drone");
        if (drone != null) data.droneProfileName = drone.optString("name", "");

        JSONObject fs = root.optJSONObject("flight_settings");
        if (fs != null) {
            data.gsdCm               = fs.optDouble("gsd_cm",                3.0);
            data.altitudeM           = fs.optDouble("altitude_m",            80.0);
            data.frontlapPercent     = fs.optDouble("front_overlap_percent", 80.0);
            data.sidelapPercent      = fs.optDouble("side_overlap_percent",  75.0);
            data.speedMs             = (float) fs.optDouble("speed_ms",       7.0);
            data.flightAngleDeg      = fs.optDouble("heading_deg",            0.0);
            data.gridMode            = fs.optString("grid_mode",          "single");
            data.crosshatchHeadingDeg= fs.optDouble("crosshatch_heading_deg", 90.0);
            data.terrainFollowing    = fs.optBoolean("terrain_following",    false);
            data.offsetM             = fs.optDouble("offset_m",               0.0);
            data.returnHome          = fs.optBoolean("return_home",           true);
            data.startCorner         = fs.optString("start_corner",          "auto");
            data.denseGridMode       = fs.optBoolean("dense_grid_mode",       false); // M10, 2026-07-03
        }

        JSONArray obsArr = root.optJSONArray("obstacles");
        if (obsArr != null) {
            for (int i = 0; i < obsArr.length(); i++) {
                JSONObject oj     = obsArr.getJSONObject(i);
                JSONObject center = oj.optJSONObject("center");
                if (center == null) continue;
                String obsId = oj.optString("id", "obst" + (i + 1));
                data.obstacles.add(new ObstacleData(
                    obsId,
                    oj.optString("label", obsId),
                    center.getDouble("lat"),
                    center.getDouble("lon"),
                    (float) oj.optDouble("radius_m", 10.0),
                    (float) oj.optDouble("height_m",  5.0)
                ));
            }
        }

        // ── Mintavétel (M01 §10 / M02 §7) — opcionális, régi tervekben hiányzik ─
        // Terepi javítás (2026-07-03). Ha a blokk hiányzik (régi mentés),
        // data.samplingMode az alapértéken (false) marad — hibamentes betöltés.
        JSONObject sampling = root.optJSONObject("sampling");
        if (sampling != null && sampling.optBoolean("enabled", false)) {
            data.samplingMode     = true;
            data.nSamplePoints    = sampling.optInt("n_sample_points", 30);
            data.samplingMethod   = sampling.optString("method", "stratified");
            data.samplingSeed     = sampling.optLong("seed", 42L);
            data.transitAltitudeM = sampling.optDouble("transit_altitude_m", 60.0);
            data.sampleAltitudeM  = sampling.optDouble("sample_altitude_m", 8.0);
            data.hoverSeconds     = (float) sampling.optDouble("hover_seconds", 2.5);
        }

        // ── Kamera beállítások — opcionális, régi tervekben hiányzik ────────────
        // Terepi javítás (2026-07-03). Ha a blokk hiányzik, az agro-alapértelmezés
        // marad érvényben (ugyanaz, amit a MissionConfig is használna).
        JSONObject camSettings = root.optJSONObject("camera_settings");
        if (camSettings != null) {
            CameraSettings cs = new CameraSettings();
            cs.autoMode           = camSettings.optBoolean("auto_mode", true);
            cs.photoMode          = parseEnum(CameraSettings.PhotoMode.class,
                    camSettings.optString("photo_mode", null), CameraSettings.PhotoMode.SINGLE_SHOT);
            cs.iso                = parseEnum(CameraSettings.IsoValue.class,
                    camSettings.optString("iso", null), CameraSettings.IsoValue.AUTO);
            cs.aperture           = parseEnum(CameraSettings.ApertureValue.class,
                    camSettings.optString("aperture", null), CameraSettings.ApertureValue.F_8);
            cs.shutterSpeed       = parseEnum(CameraSettings.ShutterSpeed.class,
                    camSettings.optString("shutter_speed", null), CameraSettings.ShutterSpeed.AUTO);
            cs.whiteBalance       = parseEnum(CameraSettings.WhiteBalanceValue.class,
                    camSettings.optString("white_balance", null), CameraSettings.WhiteBalanceValue.AUTO);
            cs.whiteBalanceKelvin = camSettings.optInt("white_balance_kelvin", 5600);
            cs.fileFormat         = parseEnum(CameraSettings.FileFormat.class,
                    camSettings.optString("file_format", null), CameraSettings.FileFormat.JPEG_AND_RAW);
            data.cameraSettings = cs;
        }

        // ── Block grid (M07) — opcionális, v1.1+ ──────────────────────────────
        JSONObject bgJson = root.optJSONObject("block_grid");
        if (bgJson != null && bgJson.optBoolean("enabled", false)) {
            BlockGridConfig bg = new BlockGridConfig();
            bg.cellWidthM         = bgJson.optDouble("cell_width_m",         120.0);
            bg.cellHeightM        = bgJson.optDouble("cell_height_m",        120.0);
            bg.rotationDeg        = bgJson.optDouble("rotation_deg",           0.0);
            bg.overlapBufferM     = bgJson.optDouble("overlap_buffer_m",      40.0);
            bg.minCoveragePercent = bgJson.optDouble("min_coverage_percent",  15.0);
            bg.originMode         = bgJson.optString("origin_mode", BlockGridConfig.ORIGIN_CENTROID);
            bg.originLat          = bgJson.has("origin_lat") ? bgJson.optDouble("origin_lat", Double.NaN) : Double.NaN;
            bg.originLon          = bgJson.has("origin_lon") ? bgJson.optDouble("origin_lon", Double.NaN) : Double.NaN;
            data.blockGrid = bg;
        }

        JSONArray statesArr = root.optJSONArray("block_states");
        if (statesArr != null) {
            for (int i = 0; i < statesArr.length(); i++) {
                JSONObject bj = statesArr.optJSONObject(i);
                if (bj == null) continue;
                String statusStr = bj.optString("status", BlockStatus.NOT_STARTED.name());
                BlockStatus status;
                try {
                    status = BlockStatus.valueOf(statusStr);
                } catch (IllegalArgumentException ignored) {
                    status = BlockStatus.NOT_STARTED;
                }
                data.blockStates.add(new BlockStateRecord(
                        bj.optString("id", ""),
                        bj.optInt("row", 0),
                        bj.optInt("col", 0),
                        status));
            }
        }

        return data;
    }

    /** Legacy séma betöltése (.dronefly.json) — auto-konverzió új mezőkre */
    private static ProjectData loadLegacy(JSONObject root, File file) throws Exception {
        ProjectData data = new ProjectData();
        data.id        = UUID.randomUUID().toString();
        data.name      = root.optString("name", basename(file));
        data.createdAt = root.optString("savedAt", utcNow());
        data.updatedAt = utcNow();

        JSONArray polyArr = root.optJSONArray("polygon");
        if (polyArr != null) {
            for (int i = 0; i < polyArr.length(); i++) {
                JSONObject pt = polyArr.getJSONObject(i);
                data.polygon.add(new GeoPoint(pt.getDouble("lat"), pt.getDouble("lon")));
            }
        }

        JSONObject sp = root.optJSONObject("startPoint");
        if (sp != null) data.startPoint = new GeoPoint(sp.getDouble("lat"), sp.getDouble("lon"));

        JSONObject cfg = root.optJSONObject("config");
        if (cfg != null) {
            data.gsdCm            = cfg.optDouble("gsdCm",            3.0);
            data.sidelapPercent   = cfg.optDouble("sidelapPercent",   75.0);
            data.frontlapPercent  = cfg.optDouble("frontlapPercent",  80.0);
            data.speedMs          = (float) cfg.optDouble("speedMs",   7.0);
            data.flightAngleDeg   = cfg.optDouble("flightAngleDeg",    0.0);
            data.offsetM          = cfg.optDouble("offsetM",            0.0);
            data.returnHome       = cfg.optBoolean("returnHome",        true);
            data.terrainFollowing = cfg.optBoolean("terrainFollowing", false);
            data.droneProfileName = cfg.optString("droneProfileName",    "");
        }

        JSONArray obsArr = root.optJSONArray("obstacles");
        if (obsArr != null) {
            for (int i = 0; i < obsArr.length(); i++) {
                JSONObject oj  = obsArr.getJSONObject(i);
                String     oid = "obst" + (i + 1);
                data.obstacles.add(new ObstacleData(
                    oid, oid,
                    oj.getDouble("lat"),
                    oj.getDouble("lon"),
                    (float) oj.optDouble("radiusM", 10.0),
                    (float) oj.optDouble("heightM",  5.0)
                ));
            }
        }

        return data;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Fájllista
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Visszaadja az összes elmentett projektet (legújabb először).
     * Tartalmazza az új .flightprogram.json és a régi .dronefly.json fájlokat is.
     */
    public static List<File> listProjects(Context ctx) {
        File   dir   = getProjectsDir(ctx);
        List<File> files = new ArrayList<>();
        File[] all   = dir.listFiles();
        if (all != null) {
            for (File f : all) {
                String n = f.getName();
                if (n.endsWith(FILE_EXT) || n.endsWith(LEGACY_EXT)) files.add(f);
            }
        }
        // Legújabb elöl — bubble sort (API 22: lambda nem elérhető)
        for (int i = 0; i < files.size() - 1; i++) {
            for (int j = i + 1; j < files.size(); j++) {
                if (files.get(j).lastModified() > files.get(i).lastModified()) {
                    File tmp = files.get(i);
                    files.set(i, files.get(j));
                    files.set(j, tmp);
                }
            }
        }
        return files;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Segédmetódusok
    // ─────────────────────────────────────────────────────────────────────────

    private static String utcNow() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        return sdf.format(new Date());
    }

    private static String basename(File file) {
        return file.getName()
                   .replace(FILE_EXT, "")
                   .replace(LEGACY_EXT, "");
    }

    public static String sanitizeFilename(String name) {
        return name.trim()
                   .replace(' ', '_')
                   .replaceAll("[/\\\\:*?\"<>|]", "_");
    }

    /** Enum biztonságos feloldása — ismeretlen/hiányzó érték esetén alapérték, nem kivétel. */
    private static <T extends Enum<T>> T parseEnum(Class<T> type, String value, T fallback) {
        if (value == null) return fallback;
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Adatstruktúra
    // ─────────────────────────────────────────────────────────────────────────

    /** A betöltött repülési program összes adata. */
    public static class ProjectData {
        // Metadata
        public String  id          = "";
        public String  name        = "";
        public String  createdAt   = "";
        public String  updatedAt   = "";
        public boolean syncPending = false;
        // Parcella
        public String parcelId   = null;
        public String parcelName = null;
        // Polygon
        public List<GeoPoint> polygon    = new ArrayList<>();
        public GeoPoint       startPoint = null;   // legacy compat
        // Repülési beállítások
        public double  gsdCm               = 3.0;
        public double  altitudeM           = 80.0;
        public double  sidelapPercent      = 75.0;
        public double  frontlapPercent     = 80.0;
        public float   speedMs             = 7.0f;
        public double  flightAngleDeg      = 0.0;
        public String  gridMode            = "single";
        public double  crosshatchHeadingDeg= 90.0;
        public String  startCorner         = "auto";
        public double  offsetM             = 0.0;
        public boolean returnHome          = true;
        public boolean terrainFollowing    = false;
        public boolean denseGridMode       = false; // M10, 2026-07-03
        public String  droneProfileName    = "";
        // Mintavétel (M01 §10 / M02 §7) — opcionális, terepi javítás 2026-07-03
        public boolean samplingMode      = false;
        public int     nSamplePoints     = 30;
        public String  samplingMethod    = "stratified";
        public long    samplingSeed      = 42L;
        public double  transitAltitudeM  = 60.0;
        public double  sampleAltitudeM   = 8.0;
        public float   hoverSeconds      = 2.5f;
        // Kamera beállítások — opcionális, terepi javítás 2026-07-03
        public CameraSettings cameraSettings = CameraSettings.getAgricultureDefaults();
        // Akadályok
        public List<ObstacleData> obstacles = new ArrayList<>();
        // M07 — blokk-felosztás (opcionális)
        public BlockGridConfig          blockGrid    = null;
        public List<BlockStateRecord>   blockStates  = new ArrayList<>();
    }

    /** Egy blokk perzisztált state-rekordja a .flightprogram.json-ban (M07). */
    public static class BlockStateRecord {
        public String      id;
        public int         row;
        public int         col;
        public BlockStatus status;

        public BlockStateRecord(String id, int row, int col, BlockStatus status) {
            this.id     = id;
            this.row    = row;
            this.col    = col;
            this.status = status;
        }
    }
}
