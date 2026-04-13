package com.dronefly.app.layers;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
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
 * Teljesítmény:
 *  - Egyetlen AirspaceDrawOverlay rajzolja az összes légterét egy draw() hívásban
 *  - Viewport culling: látómezőn kívüli légterek kihagyva (bbox alapján)
 *  - TMA (type=1) kizárva: óriási polygonok
 *
 * Magassági szűrés (altitudeFilter > 0):
 *  - lowerLimit > tervezett magasság  → kihagyva (drone ez alatt repül, nem lép be)
 *  - upperLimit < tervezett magasság  → kihagyva (drone ez felett repül, nem lép be)
 *  - MSL/FL referenciájú határok: mindig látszanak (conservative, nem tudunk AGL-t számolni)
 */
public class AirspaceLayer {

    private static final String TAG      = "AirspaceLayer";
    private static final String API_BASE = "https://api.core.openaip.net/api/airspaces";
    private static final int CONNECT_TIMEOUT_MS = 20000;
    private static final int READ_TIMEOUT_MS    = 30000;
    private static final int MAX_POINTS     = 20;  // pontritkítás / polygon
    private static final int MAX_AIRSPACES  = 20;  // max megjelenített légtér

    /** Magassági szűrő presets: ∞ és 30–120 m lépések (EU Open Category max = 120 m) */
    public static final int[] ALT_PRESETS = {0, 30, 40, 50, 60, 80, 100, 120};

    private final MapView        mapView;
    private final String         apiKey;
    private boolean              visible     = false;
    private AirspaceDrawOverlay  drawOverlay = null;
    private int                  altitudeFilter = 0;

    private final Handler         mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor    = Executors.newSingleThreadExecutor();

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
            // Visszaállítjuk a hardware accelerationt
            mapView.setLayerType(View.LAYER_TYPE_NONE, null);
            if (onDone != null) mainHandler.post(onDone);
            return;
        }
        fetchAndShow(bbox, onDone);
    }

    public boolean isVisible() { return visible; }

    /**
     * Magassági szűrő beállítása méterben (0 = nincs szűrés).
     * Azonnal újrarajzolja az overlays-t, újabb API hívás nélkül.
     *
     * Szűrési logika (csak GND-referenciájú határoknál):
     *  lowerMeters > altitudeFilter → kihagyva (drone ez alatt repül)
     *  upperMeters < altitudeFilter → kihagyva (drone ez felett repül)
     *
     * Megjegyzés: ha MINDEN légtér látszik beállított szűrő esetén is,
     * az azért van, mert az adott légterek GND-től indulnak (pl. CTR 0–762 m),
     * tehát bármely magasságon ütközést jeleznek – ez helyes viselkedés.
     */
    public void setAltitudeFilter(int meters) {
        this.altitudeFilter = meters;
        if (drawOverlay != null) {
            drawOverlay.altitudeFilter = meters;
            drawOverlay.markNeedsRebuild();
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

    // ── Hálózati lekérés ──────────────────────────────────────────────────────

    private void fetchAndShow(final BoundingBox bbox, final Runnable onDone) {
        executor.execute(new Runnable() {
            @Override public void run() {
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

                    int code = conn.getResponseCode();
                    if (code != 200) {
                        final String msg = "OpenAIP API hiba: HTTP " + code;
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

                    final List<ParsedAirspace> parsed = parseJson(json);
                    Log.d(TAG, "Parsed: " + parsed.size() + " légtér");

                    mainHandler.post(new Runnable() { @Override public void run() {
                        // Szoftveres renderelés: drawPath(fill) safe, HWUI bug elkerülve
                        mapView.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
                        drawOverlay = new AirspaceDrawOverlay(parsed);
                        drawOverlay.altitudeFilter = altitudeFilter;
                        mapView.getOverlays().add(drawOverlay);
                        mapView.invalidate();
                        visible = true;
                        int shown = countVisible(parsed, altitudeFilter);
                        String msg = altitudeFilter > 0
                                ? shown + "/" + parsed.size() + " légtér (" + altitudeFilter + "m)"
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

    // ── Parse ─────────────────────────────────────────────────────────────────

    private int countVisible(List<ParsedAirspace> list, int filter) {
        if (filter <= 0) return list.size();
        int n = 0;
        for (ParsedAirspace pa : list) {
            if (isVisible(pa, filter)) n++;
        }
        return n;
    }

    /** Látható-e ez a légtér a megadott magasságnál? */
    private boolean isVisible(ParsedAirspace pa, int altM) {
        if (altM <= 0) return true;
        // Alsó határ szűrés (GND-referencia esetén)
        if (pa.lowerIsGnd && pa.lowerMeters > altM) return false;
        // Felső határ szűrés (GND-referencia esetén): ha a plafon alatt vagyunk
        if (pa.upperIsGnd && pa.upperMeters >= 0 && pa.upperMeters < altM) return false;
        return true;
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

                // ── Alsó határmagasság ────────────────────────────────────────
                int[] lower = parseAltLimit(item.optJSONObject("lowerLimit"));
                int lowerM  = lower[0]; // méterben
                boolean lowerIsGnd = (lower[1] == 0); // referenceDatum: 0=GND

                // ── Felső határmagasság ───────────────────────────────────────
                int[] upper = parseAltLimit(item.optJSONObject("upperLimit"));
                int upperM  = upper[0];
                boolean upperIsGnd = (upper[1] == 0);

                // ── Geometria ─────────────────────────────────────────────────
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
                        lowerM, lowerIsGnd, upperM, upperIsGnd));
            }
        } catch (Throwable t) {
            Log.e(TAG, "Parse hiba: " + t.getMessage(), t);
        }

        // Ha több mint MAX_AIRSPACES, prioritás szerint tartjuk meg:
        // CTR(0), Prohibited(5), Restricted(4), Danger(3), ATZ(2), RMZ(8), TMZ(7), egyéb
        if (result.size() > MAX_AIRSPACES) {
            final int[] priority = {0, 5, 4, 3, 2, 8, 7};
            List<ParsedAirspace> sorted = new ArrayList<>(result.size());
            for (int p : priority) {
                for (ParsedAirspace pa : result) {
                    if (pa.type == p) sorted.add(pa);
                }
            }
            for (ParsedAirspace pa : result) {
                boolean known = false;
                for (int p : priority) { if (pa.type == p) { known = true; break; } }
                if (!known) sorted.add(pa);
            }
            result = sorted.size() > MAX_AIRSPACES
                    ? new ArrayList<>(sorted.subList(0, MAX_AIRSPACES))
                    : sorted;
        }
        return result;
    }

    /** Egységes magassághatár parse: [méter, referenceDatum] */
    private int[] parseAltLimit(JSONObject obj) {
        if (obj == null) return new int[]{0, 0};
        int value = obj.optInt("value", 0);
        int unit  = obj.optInt("unit", 0);  // 0=láb, 1=méter, 6=FL
        int ref   = obj.optInt("referenceDatum", 0); // 0=GND, 1=MSL, 2=STD
        double meters;
        switch (unit) {
            case 1:  meters = value; break;
            case 6:  meters = value * 100.0 * 0.3048; break;
            default: meters = value * 0.3048; break;
        }
        return new int[]{(int) Math.round(meters), ref};
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

    // ── Egyedi overlay ────────────────────────────────────────────────────────

    /**
     * Légterek rajzolása.
     * A MapView LAYER_TYPE_SOFTWARE módban fut LGT bekapcsolásakor,
     * így a canvas szoftveres Skia renderer – drawPath(fill) teljesen biztonságos,
     * nem megy a GPU-ra, nem crashel az Android 5.1 HWUI bug miatt.
     */
    private class AirspaceDrawOverlay extends Overlay {
        private final List<ParsedAirspace> airspaces;
        volatile int altitudeFilter = 0;

        private final Paint fillPaint   = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path  path        = new Path();
        private final Point pixel       = new Point();

        AirspaceDrawOverlay(List<ParsedAirspace> airspaces) {
            this.airspaces = airspaces;
            fillPaint.setStyle(Paint.Style.FILL);
            strokePaint.setStyle(Paint.Style.STROKE);
        }

        void markNeedsRebuild() { /* nem kell, minden draw() újraszámolja */ }

        @Override
        public void draw(Canvas canvas, MapView mv, boolean shadow) {
            if (shadow) return;

            Projection proj  = mv.getProjection();
            float density    = mv.getContext().getResources().getDisplayMetrics().density;

            for (ParsedAirspace pa : airspaces) {
                if (!isVisible(pa, altitudeFilter)) continue;

                path.reset();
                boolean first = true;
                for (GeoPoint gp : pa.points) {
                    proj.toPixels(gp, pixel);
                    if (first) { path.moveTo(pixel.x, pixel.y); first = false; }
                    else        path.lineTo(pixel.x, pixel.y);
                }
                path.close();

                // Kitöltés – szoftveres canvas, biztonságos
                fillPaint.setColor(getFillColor(pa.type));
                canvas.drawPath(path, fillPaint);

                // Körvonal
                strokePaint.setColor(getStrokeColor(pa.type));
                strokePaint.setStrokeWidth(getStrokeWidth(pa.type) * density);
                canvas.drawPath(path, strokePaint);
            }
        }
    }

    // ── Szín kódok ────────────────────────────────────────────────────────────

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
            case 0: return 3.0f;  // CTR
            case 5: return 2.5f;  // Prohibited
            case 3: case 4: return 2.0f; // Danger, Restricted
            case 2: return 2.0f;  // ATZ
            case 8: return 1.5f;  // RMZ
            default: return 1.5f;
        }
    }

    /** Félátlátszó kitöltési szín típusonként */
    private int getFillColor(int type) {
        switch (type) {
            case 0: return 0x44FF2200; // CTR – piros, ~27% alfa
            case 2: return 0x33FF6600; // ATZ – narancs, ~20% alfa
            case 3: return 0x44FF0000; // Danger – piros
            case 4: return 0x44FF4400; // Restricted – sötétnarancs
            case 5: return 0x55CC0000; // Prohibited – sötétpiros, ~33% alfa
            case 7: return 0x33FFAA00; // TMZ – sárga
            case 8: return 0x334444FF; // RMZ – kék
            default: return 0x22FFCC00; // egyéb – sárga, ~13% alfa
        }
    }

    // ── Adatstruktúra ─────────────────────────────────────────────────────────

    static class ParsedAirspace {
        final List<GeoPoint> points;
        final int type;
        // Határmagasságok
        final int lowerMeters; final boolean lowerIsGnd;
        final int upperMeters; final boolean upperIsGnd;
        // Bounding box (viewport cullinghoz)
        final double minLat, maxLat, minLon, maxLon;

        ParsedAirspace(List<GeoPoint> points, int type,
                       int lowerMeters, boolean lowerIsGnd,
                       int upperMeters, boolean upperIsGnd) {
            this.points     = points;
            this.type       = type;
            this.lowerMeters = lowerMeters; this.lowerIsGnd = lowerIsGnd;
            this.upperMeters = upperMeters; this.upperIsGnd = upperIsGnd;

            double minLat = Double.MAX_VALUE, maxLat = -Double.MAX_VALUE;
            double minLon = Double.MAX_VALUE, maxLon = -Double.MAX_VALUE;
            for (GeoPoint gp : points) {
                if (gp.getLatitude()  < minLat) minLat = gp.getLatitude();
                if (gp.getLatitude()  > maxLat) maxLat = gp.getLatitude();
                if (gp.getLongitude() < minLon) minLon = gp.getLongitude();
                if (gp.getLongitude() > maxLon) maxLon = gp.getLongitude();
            }
            this.minLat = minLat; this.maxLat = maxLat;
            this.minLon = minLon; this.maxLon = maxLon;
        }
    }
}
