package com.dronefly.app.mission;

import android.content.Context;

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

/**
 * Repülési terv mentése és betöltése JSON formátumban (.dronefly.json).
 *
 * A mentett fájl tartalmazza:
 *   - polygon csúcsai (GeoPoint lista)
 *   - start/home pont (opcionális)
 *   - összes beállítás (GSD, sidelap, frontlap, sebesség, szög, offset, stb.)
 *   - akadályok listája
 *
 * Elérési út: <app external files>/missions/<névvel>.dronefly.json
 */
public class ProjectManager {

    private static final int    FORMAT_VERSION = 1;
    public  static final String FILE_EXT       = ".dronefly.json";
    public  static final String MISSIONS_DIR   = "missions";

    // ─────────────────────────────────────────────────────────────────────────
    //  Könyvtár
    // ─────────────────────────────────────────────────────────────────────────

    public static File getProjectsDir(Context ctx) {
        File dir = new File(ctx.getExternalFilesDir(null), MISSIONS_DIR);
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Mentés
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Repülési tervet ment JSON fájlba.
     *
     * @param ctx         Context (fájleléréshez)
     * @param name        Felhasználó által adott név (pl. "Északi tábla")
     * @param polygon     Poligon csúcsainak listája
     * @param startPoint  Start/Home pont, null ha nincs megadva
     * @param config      Teljes missziókonfiguráció (beállítások + akadályok)
     * @return            A létrehozott fájl
     */
    public static File saveProject(Context ctx,
                                   String name,
                                   List<GeoPoint> polygon,
                                   GeoPoint startPoint,
                                   MissionConfig config) throws Exception {
        JSONObject root = new JSONObject();
        root.put("version", FORMAT_VERSION);
        root.put("name", name);
        root.put("savedAt",
            new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
                .format(new Date()));

        // ── Polygon ──────────────────────────────────────────────────────────
        JSONArray polyArr = new JSONArray();
        for (GeoPoint p : polygon) {
            JSONObject pt = new JSONObject();
            pt.put("lat", p.getLatitude());
            pt.put("lon", p.getLongitude());
            polyArr.put(pt);
        }
        root.put("polygon", polyArr);

        // ── Start/Home pont ──────────────────────────────────────────────────
        if (startPoint != null) {
            JSONObject sp = new JSONObject();
            sp.put("lat", startPoint.getLatitude());
            sp.put("lon", startPoint.getLongitude());
            root.put("startPoint", sp);
        }

        // ── Beállítások ──────────────────────────────────────────────────────
        JSONObject cfg = new JSONObject();
        cfg.put("gsdCm",            config.gsdCm);
        cfg.put("sidelapPercent",   config.sidelapPercent);
        cfg.put("frontlapPercent",  config.frontlapPercent);
        cfg.put("speedMs",          config.speedMs);
        cfg.put("flightAngleDeg",   config.flightAngleDeg);
        cfg.put("offsetM",          config.offsetM);
        cfg.put("returnHome",       config.returnHome);
        cfg.put("terrainFollowing", config.terrainFollowing);
        if (config.droneProfile != null) {
            cfg.put("droneProfileName", config.droneProfile.name);
        }
        root.put("config", cfg);

        // ── Akadályok ────────────────────────────────────────────────────────
        JSONArray obsArr = new JSONArray();
        for (ObstacleData o : config.obstacles) {
            JSONObject oj = new JSONObject();
            oj.put("lat",     o.latitude);
            oj.put("lon",     o.longitude);
            oj.put("radiusM", o.radiusM);
            oj.put("heightM", o.heightM);
            obsArr.put(oj);
        }
        root.put("obstacles", obsArr);

        // ── Írás fájlba ──────────────────────────────────────────────────────
        String safeName = sanitizeFilename(name);
        File file = new File(getProjectsDir(ctx), safeName + FILE_EXT);
        FileWriter fw = new FileWriter(file);
        fw.write(root.toString(2));   // szép, behúzott JSON
        fw.close();
        return file;
    }

    /**
     * Repülési tervet ment egy meglévő fájlba (felülírás, SAVE).
     * A fájl neve nem változik, csak a tartalma frissül.
     */
    public static void saveProjectToFile(Context ctx,
                                          File targetFile,
                                          String name,
                                          List<GeoPoint> polygon,
                                          GeoPoint startPoint,
                                          MissionConfig config) throws Exception {
        JSONObject root = new JSONObject();
        root.put("version", FORMAT_VERSION);
        root.put("name", name);
        root.put("savedAt",
            new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
                .format(new Date()));

        JSONArray polyArr = new JSONArray();
        for (GeoPoint p : polygon) {
            JSONObject pt = new JSONObject();
            pt.put("lat", p.getLatitude());
            pt.put("lon", p.getLongitude());
            polyArr.put(pt);
        }
        root.put("polygon", polyArr);

        if (startPoint != null) {
            JSONObject sp = new JSONObject();
            sp.put("lat", startPoint.getLatitude());
            sp.put("lon", startPoint.getLongitude());
            root.put("startPoint", sp);
        }

        JSONObject cfg = new JSONObject();
        cfg.put("gsdCm",            config.gsdCm);
        cfg.put("sidelapPercent",   config.sidelapPercent);
        cfg.put("frontlapPercent",  config.frontlapPercent);
        cfg.put("speedMs",          config.speedMs);
        cfg.put("flightAngleDeg",   config.flightAngleDeg);
        cfg.put("offsetM",          config.offsetM);
        cfg.put("returnHome",       config.returnHome);
        cfg.put("terrainFollowing", config.terrainFollowing);
        if (config.droneProfile != null) cfg.put("droneProfileName", config.droneProfile.name);
        root.put("config", cfg);

        JSONArray obsArr = new JSONArray();
        for (ObstacleData o : config.obstacles) {
            JSONObject oj = new JSONObject();
            oj.put("lat",     o.latitude);
            oj.put("lon",     o.longitude);
            oj.put("radiusM", o.radiusM);
            oj.put("heightM", o.heightM);
            obsArr.put(oj);
        }
        root.put("obstacles", obsArr);

        FileWriter fw = new FileWriter(targetFile);
        fw.write(root.toString(2));
        fw.close();
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Betöltés
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Visszaolvas egy .dronefly.json fájlt és visszaadja a projekt adatait.
     */
    public static ProjectData loadProject(File file) throws Exception {
        // Fájl beolvasása
        BufferedReader br = new BufferedReader(new FileReader(file));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) {
            sb.append(line).append('\n');
        }
        br.close();

        JSONObject root = new JSONObject(sb.toString());
        ProjectData data = new ProjectData();
        data.name = root.optString("name", file.getName().replace(FILE_EXT, ""));

        // ── Polygon ──────────────────────────────────────────────────────────
        JSONArray polyArr = root.optJSONArray("polygon");
        if (polyArr != null) {
            for (int i = 0; i < polyArr.length(); i++) {
                JSONObject pt = polyArr.getJSONObject(i);
                data.polygon.add(new GeoPoint(pt.getDouble("lat"), pt.getDouble("lon")));
            }
        }

        // ── Start pont ───────────────────────────────────────────────────────
        JSONObject sp = root.optJSONObject("startPoint");
        if (sp != null) {
            data.startPoint = new GeoPoint(sp.getDouble("lat"), sp.getDouble("lon"));
        }

        // ── Beállítások ──────────────────────────────────────────────────────
        JSONObject cfg = root.optJSONObject("config");
        if (cfg != null) {
            data.gsdCm            = cfg.optDouble("gsdCm",           3.0);
            data.sidelapPercent   = cfg.optDouble("sidelapPercent",  75.0);
            data.frontlapPercent  = cfg.optDouble("frontlapPercent", 80.0);
            data.speedMs          = (float) cfg.optDouble("speedMs", 7.0);
            data.flightAngleDeg   = cfg.optDouble("flightAngleDeg",  0.0);
            data.offsetM          = cfg.optDouble("offsetM",          0.0);
            data.returnHome       = cfg.optBoolean("returnHome",      true);
            data.terrainFollowing = cfg.optBoolean("terrainFollowing", false);
            data.droneProfileName = cfg.optString("droneProfileName", "");
        }

        // ── Akadályok ────────────────────────────────────────────────────────
        JSONArray obsArr = root.optJSONArray("obstacles");
        if (obsArr != null) {
            for (int i = 0; i < obsArr.length(); i++) {
                JSONObject oj = obsArr.getJSONObject(i);
                data.obstacles.add(new ObstacleData(
                    oj.getDouble("lat"),
                    oj.getDouble("lon"),
                    (float) oj.getDouble("radiusM"),
                    (float) oj.getDouble("heightM")
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
     */
    public static List<File> listProjects(Context ctx) {
        File dir = getProjectsDir(ctx);
        List<File> files = new ArrayList<>();
        File[] all = dir.listFiles();
        if (all != null) {
            for (File f : all) {
                if (f.getName().endsWith(FILE_EXT)) {
                    files.add(f);
                }
            }
        }
        // Legújabb elöl — bubble sort (API 22: lambda/Comparator.comparing nem elérhető)
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
    //  Segédmetódus
    // ─────────────────────────────────────────────────────────────────────────

    /** Fájlnévben nem megengedett karakterek cseréje aláhúzásra. */
    private static String sanitizeFilename(String name) {
        return name.trim()
                   .replace(' ', '_')
                   .replaceAll("[/\\\\:*?\"<>|]", "_");
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Adatstruktúra
    // ─────────────────────────────────────────────────────────────────────────

    /** A betöltött projekt összes adata egy helyen. */
    public static class ProjectData {
        public String         name            = "";
        public List<GeoPoint> polygon         = new ArrayList<>();
        public GeoPoint       startPoint      = null;
        // config mezők
        public double  gsdCm            = 3.0;
        public double  sidelapPercent   = 75.0;
        public double  frontlapPercent  = 80.0;
        public float   speedMs          = 7.0f;
        public double  flightAngleDeg   = 0.0;
        public double  offsetM          = 0.0;
        public boolean returnHome       = true;
        public boolean terrainFollowing = false;
        public String  droneProfileName = "";
        // akadályok
        public List<ObstacleData> obstacles = new ArrayList<>();
    }
}
