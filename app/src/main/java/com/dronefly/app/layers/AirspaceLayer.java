package com.dronefly.app.layers;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.Projection;
import org.osmdroid.views.overlay.Overlay;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Légtér adatok (CTR, ATZ, Danger, Restricted, Prohibited, RMZ, TMZ) megjelenítése.
 * Forrás: OpenAIP Core API v1.
 *
 * Teljesítmény: egyetlen egyedi Overlay rajzolja az összes légterét egy draw() hívásban –
 * sokkal gyorsabb mint egyenként 100+ Polygon overlay.
 *
 * TMA (type=1) kizárva: óriási polygonok, rendszer szintű lassulást okoznak.
 *
 * Magassági szűrés: csak azok a légterek jelennek meg, amelyek GND-től mért alsó határa
 * legfeljebb akkora, mint a beállított tervezett repülési magasság.
 * altitudeFilter=0 → minden légtér látszik.
 */
public class AirspaceLayer {

    private static final String TAG       = "AirspaceLayer";
    private static final String API_BASE  = "https://api.core.openaip.net/api/airspaces";
    private static final int CONNECT_TIMEOUT_MS = 20000;
    private static final int READ_TIMEOUT_MS    = 30000;
    private static final int MAX_POINTS         = 80; // pontritkítás nagy polygonokhoz

    /** Előre beállított magassági szűrő értékek méterben (0 = összes) */
    public static final int[] ALT_PRESETS = {0, 30, 40, 50, 60, 80, 100, 120, 150};

    private final MapView        mapView;
    private final String         apiKey;
    private boolean              visible  = false;
    private AirspaceDrawOverlay  drawOverlay = null;
    private int                  altitudeFilter = 0; // 0 = mutassa az összeset

    private final Handler        mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor   = Executors.newSingleThreadExecutor();

    public AirspaceLayer(MapView mapView, String apiKey) {
        this.mapView = mapView;
        this.apiKey  = apiKey;
    }

    public void toggle(final BoundingBox bbox, final Runnable onDone) {
        if (apiKey == null || apiKey.isEmpty()) {
            Toast.makeText(mapView.getContext(),
                    "OpenAIP API kulcs szükséges", Toast.LENGTH_LONG).show();
            if (onDone != null) mainHandler.post(onDone);
            return;
        }
        if (visible) {
            clear();
            visible = false;
            if (onDone != null) mainHandler.post(onDone);
            return;
        }
        fetchAndShow(bbox, onDone);
    }

    public boolean isVisible() { return visible; }

    /**
     * Magassági szűrő beállítása méterben.
     * 0 = nincs szűrés (minden légtér látszik).
     * Pl. 40 → csak azok a légterek látszanak, amelyek alsó határa ≤ 40 m AGL.
     * Azonnal újrarajzolja az overlay-t, nincs szükség újabb API hívásra.
     */
    public void setAltitudeFilter(int meters) {
        this.altitudeFilter = meters;
        if (drawOverlay != null) {
            drawOverlay.altitudeFilter = meters;
            mapView.invalidate();
        }
    }

    public int getAltitudeFilter() { return altitudeFilter; }

    public void clear() {
        if (drawOverlay != null) {
            mapView.getOverlays().remove(drawOverlay);
            drawOverlay = null;
        }
        mapView.invalidate();
    }

    // ── Belső segédek ─────────────────────────────────────────────────────────

    private void fetchAndShow(final BoundingBox bbox, final Runnable onDone) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                HttpURLConnection conn = null;
                try {
                    String bboxParam = bbox.getLonWest() + "," + bbox.getLatSouth()
                            + "," + bbox.getLonEast() + "," + bbox.getLatNorth();
                    String urlStr = API_BASE + "?bbox=" + bboxParam + "&limit=200";

                    URL url = new URL(urlStr);
                    conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("GET");
                    conn.setRequestProperty("Accept", "application/json");
                    conn.setRequestProperty("x-openaip-api-key", apiKey);
                    conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
                    conn.setReadTimeout(READ_TIMEOUT_MS);

                    int responseCode = conn.getResponseCode();
                    if (responseCode != 200) {
                        final String msg = "OpenAIP API hiba: HTTP " + responseCode;
                        Log.e(TAG, msg);
                        mainHandler.post(new Runnable() { @Override public void run() {
                            Toast.makeText(mapView.getContext(), msg, Toast.LENGTH_LONG).show();
                            if (onDone != null) onDone.run();
                        }});
                        return;
                    }

                    StringBuilder sb = new StringBuilder();
                    BufferedReader br = new BufferedReader(
                            new InputStreamReader(conn.getInputStream(), "UTF-8"));
                    String line;
                    while ((line = br.readLine()) != null) sb.append(line);
                    br.close();

                    final String json = sb.toString();
                    Log.d(TAG, "OpenAIP válasz: " + json.length() + " karakter");

                    // Parse háttérszálon
                    final List<ParsedAirspace> parsed = parseJson(json);
                    Log.d(TAG, "Parsed: " + parsed.size() + " légtér");

                    mainHandler.post(new Runnable() { @Override public void run() {
                        drawOverlay = new AirspaceDrawOverlay(parsed);
                        drawOverlay.altitudeFilter = altitudeFilter;
                        mapView.getOverlays().add(drawOverlay);
                        mapView.invalidate();
                        visible = true;
                        // Szűrt vs. összes darabszám Toast-ban
                        int shown = countVisible(parsed, altitudeFilter);
                        String msg = altitudeFilter > 0
                                ? shown + "/" + parsed.size() + " légtér (" + altitudeFilter + "m alatt)"
                                : parsed.size() + " légtér betöltve";
                        Toast.makeText(mapView.getContext(), msg, Toast.LENGTH_SHORT).show();
                        if (onDone != null) onDone.run();
                    }});

                } catch (final Throwable t) {
                    Log.e(TAG, "Hiba: " + t.getMessage(), t);
                    mainHandler.post(new Runnable() { @Override public void run() {
                        Toast.makeText(mapView.getContext(),
                                "Légtér hiba: " + t.getMessage(), Toast.LENGTH_LONG).show();
                        if (onDone != null) onDone.run();
                    }});
                } finally {
                    if (conn != null) conn.disconnect();
                }
            }
        });
    }

    private int countVisible(List<ParsedAirspace> list, int filter) {
        if (filter <= 0) return list.size();
        int n = 0;
        for (ParsedAirspace pa : list) {
            if (!pa.lowerIsGnd || pa.lowerMeters <= filter) n++;
        }
        return n;
    }

    private List<ParsedAirspace> parseJson(String json) {
        List<ParsedAirspace> result = new ArrayList<>();
        try {
            JSONObject root = new JSONObject(json);
            JSONArray items = root.optJSONArray("items");
            if (items == null) return result;

            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.getJSONObject(i);
                int type = item.optInt("type", -1);
                if (type == 1) continue; // TMA kizárva

                // ── Alsó határmagasság parse ──────────────────────────────────
                JSONObject lowerObj = item.optJSONObject("lowerLimit");
                int lowerValue = 0;
                int lowerUnit  = 0; // 0=láb, 1=méter, 6=FL
                int lowerRef   = 0; // 0=GND, 1=MSL, 2=STD
                if (lowerObj != null) {
                    lowerValue = lowerObj.optInt("value", 0);
                    lowerUnit  = lowerObj.optInt("unit", 0);
                    lowerRef   = lowerObj.optInt("referenceDatum", 0);
                }
                // Egységváltás méterre
                double lowerMeters;
                switch (lowerUnit) {
                    case 1:  lowerMeters = lowerValue; break;                    // méter
                    case 6:  lowerMeters = lowerValue * 100.0 * 0.3048; break;  // FL
                    default: lowerMeters = lowerValue * 0.3048; break;           // láb (default)
                }
                boolean lowerIsGnd = (lowerRef == 0); // GND-referencia → megbízható szűrés

                // ── Geometria parse ───────────────────────────────────────────
                JSONObject geometry = item.optJSONObject("geometry");
                if (geometry == null) continue;
                if (!"Polygon".equals(geometry.optString("type", ""))) continue;

                JSONArray rings = geometry.optJSONArray("coordinates");
                if (rings == null || rings.length() == 0) continue;

                JSONArray ring = rings.getJSONArray(0);
                if (ring.length() < 3) continue;

                List<GeoPoint> pts = new ArrayList<>(ring.length());
                for (int j = 0; j < ring.length(); j++) {
                    JSONArray coord = ring.getJSONArray(j);
                    pts.add(new GeoPoint(coord.getDouble(1), coord.getDouble(0)));
                }
                result.add(new ParsedAirspace(subsample(pts), type,
                        (int) Math.round(lowerMeters), lowerIsGnd));
            }
        } catch (Throwable t) {
            Log.e(TAG, "Parse hiba: " + t.getMessage(), t);
        }
        return result;
    }

    private List<GeoPoint> subsample(List<GeoPoint> pts) {
        if (pts.size() <= MAX_POINTS) return pts;
        int step = pts.size() / MAX_POINTS + 1;
        List<GeoPoint> result = new ArrayList<>(MAX_POINTS + 1);
        for (int i = 0; i < pts.size(); i += step) result.add(pts.get(i));
        GeoPoint last = pts.get(pts.size() - 1);
        if (!result.get(result.size() - 1).equals(last)) result.add(last);
        return result;
    }

    // ── Egyedi overlay – egyetlen draw() hívásban rajzol mindent ──────────────

    private class AirspaceDrawOverlay extends Overlay {
        private final List<ParsedAirspace> airspaces;
        volatile int altitudeFilter = 0; // 0 = minden látszik
        private final Paint fillPaint   = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path  path        = new Path();
        private final Point pixel       = new Point();

        AirspaceDrawOverlay(List<ParsedAirspace> airspaces) {
            this.airspaces = airspaces;
            strokePaint.setStyle(Paint.Style.STROKE);
            fillPaint.setStyle(Paint.Style.FILL);
        }

        @Override
        public void draw(Canvas canvas, MapView mv, boolean shadow) {
            if (shadow) return;
            Projection proj = mv.getProjection();
            float density = mv.getContext().getResources().getDisplayMetrics().density;

            for (ParsedAirspace pa : airspaces) {
                if (pa.points.size() < 3) continue;

                // Magassági szűrés:
                // Ha van szűrő (>0) ÉS az alsó határ GND-referenciájú (megbízható)
                // ÉS az alsó határ magasabb mint a tervezett repülési magasság
                // → kihagyjuk (nem ütközünk ezzel a légtérrel)
                if (altitudeFilter > 0 && pa.lowerIsGnd && pa.lowerMeters > altitudeFilter) {
                    continue;
                }

                path.reset();
                boolean first = true;
                for (GeoPoint gp : pa.points) {
                    proj.toPixels(gp, pixel);
                    if (first) { path.moveTo(pixel.x, pixel.y); first = false; }
                    else        path.lineTo(pixel.x, pixel.y);
                }
                path.close();

                fillPaint.setColor(getFillColor(pa.type));
                canvas.drawPath(path, fillPaint);

                strokePaint.setColor(getStrokeColor(pa.type));
                strokePaint.setStrokeWidth(getStrokeWidth(pa.type) * density);
                canvas.drawPath(path, strokePaint);
            }
        }
    }

    // ── Szín kódok ────────────────────────────────────────────────────────────

    private int getFillColor(int type) {
        switch (type) {
            case 0: return Color.parseColor("#4DFF2200"); // CTR – piros
            case 2: return Color.parseColor("#26FF6600"); // ATZ – narancs
            case 3: return Color.parseColor("#40FF0000"); // Danger
            case 4: return Color.parseColor("#33FF4400"); // Restricted
            case 5: return Color.parseColor("#4DCC0000"); // Prohibited
            case 7: return Color.parseColor("#26FFAA00"); // TMZ – sárga
            case 8: return Color.parseColor("#1A4444FF"); // RMZ – kék halvány
            default: return Color.parseColor("#1AFFCC00"); // egyéb
        }
    }

    private int getStrokeColor(int type) {
        switch (type) {
            case 0: return Color.parseColor("#FFFF2200");
            case 2: return Color.parseColor("#FFFF6600");
            case 3: return Color.parseColor("#FFFF0000");
            case 4: return Color.parseColor("#FFFF4400");
            case 5: return Color.parseColor("#FFCC0000");
            case 7: return Color.parseColor("#FFFFAA00");
            case 8: return Color.parseColor("#FF4444FF");
            default: return Color.parseColor("#FFFFCC00");
        }
    }

    private float getStrokeWidth(int type) {
        switch (type) {
            case 0: case 5: return 3.0f;
            case 3: case 4: return 2.5f;
            case 8: return 1.5f;
            default: return 2.0f;
        }
    }

    // ── Adatstruktúra ─────────────────────────────────────────────────────────

    static class ParsedAirspace {
        final List<GeoPoint> points;
        final int type;
        final int lowerMeters;   // alsó határmagasság méterre konvertálva
        final boolean lowerIsGnd; // true = GND referencia → szűrhető; false = MSL/FL → mindig látszik

        ParsedAirspace(List<GeoPoint> points, int type, int lowerMeters, boolean lowerIsGnd) {
            this.points      = points;
            this.type        = type;
            this.lowerMeters = lowerMeters;
            this.lowerIsGnd  = lowerIsGnd;
        }
    }
}
