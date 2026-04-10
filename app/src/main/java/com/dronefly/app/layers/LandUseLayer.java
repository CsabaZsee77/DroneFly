package com.dronefly.app.layers;

import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Overlay;
import org.osmdroid.views.overlay.Polygon;
import org.osmdroid.views.overlay.Polyline;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Területhasználati zónák (landuse) megjelenítése OSMDroid MapView-on.
 * Drónozás szempontjából releváns zónákat kér le az Overpass API-tól.
 *
 * Szín kód:
 *  - residential/commercial/retail  → piros   (#FFEE3333) – lakott/kereskedelmi terület
 *  - industrial                     → szürke  (#FF888888)
 *  - military                       → sötétpiros (#FFAA0000) – katonai, tiltott
 *  - aerodrome / repülőtér          → lila    (#FFAA00CC) – CTR / repülőtér körzet
 *  - forest                         → zöld    (#FF336633)
 *  - farmland / meadow / grass      → sárgászöld (#FF99BB44)
 *  - egyéb                          → semleges szürke (#FF999999)
 *
 * Zoom < 10 esetén a lekérés nem indul el.
 * JSON parse háttérszálon fut.
 */
public class LandUseLayer {

    private static final String TAG           = "LandUseLayer";
    private static final int    MIN_ZOOM      = 10;
    private static final int    MAX_OVERLAYS  = 300;  // ennél több overlay lefagyasztja a renderert
    private static final int    MAX_POINTS    = 150;  // nagy polygonok pontjait ritkítjuk

    private final MapView         mapView;
    private final List<Overlay>   overlays      = new ArrayList<>();
    private boolean               visible       = false;
    private final OverpassClient  client        = OverpassClient.getInstance();
    private final Handler         mainHandler   = new Handler(Looper.getMainLooper());
    private final ExecutorService parseExecutor = Executors.newSingleThreadExecutor();

    public LandUseLayer(MapView mapView) {
        this.mapView = mapView;
    }

    public void toggle(final BoundingBox bbox, final Runnable onDone) {
        if (visible) {
            clear();
            visible = false;
            if (onDone != null) mainHandler.post(onDone);
            return;
        }

        double zoomLevel = mapView.getZoomLevelDouble();
        if (zoomLevel < MIN_ZOOM) {
            Toast.makeText(mapView.getContext(),
                    "Kérlek nagyíts be (zoom ≥ 10) a zónák betöltéséhez",
                    Toast.LENGTH_LONG).show();
            if (onDone != null) mainHandler.post(onDone);
            return;
        }

        Toast.makeText(mapView.getContext(), "Területzónák betöltése...", Toast.LENGTH_SHORT).show();

        client.fetch(buildQuery(bbox), new OverpassClient.OverpassCallback() {
            @Override
            public void onSuccess(final String json) {
                parseExecutor.execute(new Runnable() {
                    @Override
                    public void run() {
                        final List<ParsedZone> parsed = parseJson(json);
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                addParsedZones(parsed);
                                visible = true;
                                if (onDone != null) onDone.run();
                            }
                        });
                    }
                });
            }

            @Override
            public void onError(String message) {
                Log.e(TAG, "Fetch hiba: " + message);
                Toast.makeText(mapView.getContext(),
                        "Zóna betöltési hiba: " + message, Toast.LENGTH_LONG).show();
                if (onDone != null) onDone.run();
            }
        });
    }

    public boolean isVisible() { return visible; }

    public void clear() {
        for (Overlay o : overlays) mapView.getOverlays().remove(o);
        overlays.clear();
        mapView.invalidate();
    }

    // ── Belső segédek ─────────────────────────────────────────────────────────

    private String buildQuery(BoundingBox bbox) {
        String b = bbox.getLatSouth() + "," + bbox.getLonWest()
                 + "," + bbox.getLatNorth() + "," + bbox.getLonEast();
        // Csak drónozás szempontjából kritikus zónák – a mező/erdő/rét kimarad (túl sok adat)
        return "[out:json][timeout:45];\n(\n"
             + "  way[\"landuse\"~\"^(residential|commercial|retail|industrial|military)$\"](" + b + ");\n"
             + "  relation[\"landuse\"~\"^(residential|commercial|retail|industrial|military)$\"](" + b + ");\n"
             + "  way[\"aeroway\"=\"aerodrome\"](" + b + ");\n"
             + "  relation[\"aeroway\"=\"aerodrome\"](" + b + ");\n"
             + ");\nout geom;";
    }

    private List<ParsedZone> parseJson(String json) {
        List<ParsedZone> result = new ArrayList<>();
        try {
            JSONArray elements = new JSONObject(json).getJSONArray("elements");
            for (int i = 0; i < elements.length(); i++) {
                JSONObject el = elements.getJSONObject(i);
                JSONObject tags = el.optJSONObject("tags");
                if (tags == null) continue;

                String landuse = tags.optString("landuse", "");
                String aeroway = tags.optString("aeroway", "");
                String zoneType = !aeroway.isEmpty() ? "aerodrome" : landuse;
                if (zoneType.isEmpty()) continue;

                String elType = el.optString("type", "");
                if ("way".equals(elType)) {
                    List<GeoPoint> pts = toPoints(el.optJSONArray("geometry"), 2);
                    if (pts != null) result.add(new ParsedZone(pts, zoneType, isClosed(pts)));
                } else if ("relation".equals(elType)) {
                    JSONArray members = el.optJSONArray("members");
                    if (members == null) continue;
                    for (int m = 0; m < members.length(); m++) {
                        JSONObject member = members.getJSONObject(m);
                        if (!"outer".equals(member.optString("role", ""))) continue;
                        List<GeoPoint> pts = toPoints(member.optJSONArray("geometry"), 2);
                        if (pts != null) result.add(new ParsedZone(pts, zoneType, false));
                    }
                }
            }
        } catch (Throwable t) {
            Log.e(TAG, "JSON parse hiba: " + t.getMessage(), t);
        }
        return result;
    }

    private boolean isClosed(List<GeoPoint> pts) {
        if (pts.size() < 3) return false;
        GeoPoint first = pts.get(0), last = pts.get(pts.size() - 1);
        return Math.abs(first.getLatitude()  - last.getLatitude())  < 1e-7
            && Math.abs(first.getLongitude() - last.getLongitude()) < 1e-7;
    }

    private List<GeoPoint> toPoints(JSONArray geometry, int minPts) throws org.json.JSONException {
        if (geometry == null || geometry.length() < minPts) return null;
        List<GeoPoint> pts = new ArrayList<>(geometry.length());
        for (int j = 0; j < geometry.length(); j++) {
            JSONObject pt = geometry.getJSONObject(j);
            pts.add(new GeoPoint(pt.getDouble("lat"), pt.getDouble("lon")));
        }
        return pts.size() >= minPts ? pts : null;
    }

    private void addParsedZones(List<ParsedZone> parsed) {
        float sw = dpToPx(1.5f);
        int added = 0;
        for (ParsedZone pz : parsed) {
            if (added >= MAX_OVERLAYS) {
                Log.w(TAG, "MAX_OVERLAYS (" + MAX_OVERLAYS + ") elérve, további zónák kihagyva");
                break;
            }
            int fill   = getFillColor(pz.zoneType);
            int stroke = getStrokeColor(pz.zoneType);
            List<GeoPoint> pts = subsample(pz.points);

            if (pz.closed && pts.size() >= 3) {
                Polygon polygon = new Polygon();
                polygon.setPoints(pts);
                polygon.getFillPaint().setColor(fill);
                polygon.getOutlinePaint().setColor(stroke);
                polygon.getOutlinePaint().setStrokeWidth(sw);
                mapView.getOverlays().add(polygon);
                overlays.add(polygon);
                added++;
            } else if (!pz.closed && pts.size() >= 2) {
                Polyline polyline = new Polyline();
                polyline.setPoints(pts);
                polyline.getOutlinePaint().setColor(stroke);
                polyline.getOutlinePaint().setStrokeWidth(sw);
                mapView.getOverlays().add(polyline);
                overlays.add(polyline);
                added++;
            }
        }
        mapView.invalidate();
        Log.d(TAG, "Területzónák: " + overlays.size() + " overlay");
    }

    /** Nagy polygonokat ritkít: ha több pont van mint MAX_POINTS, minden N-ediket tartja meg. */
    private List<GeoPoint> subsample(List<GeoPoint> pts) {
        if (pts.size() <= MAX_POINTS) return pts;
        int step = pts.size() / MAX_POINTS + 1;
        List<GeoPoint> result = new ArrayList<>(MAX_POINTS + 1);
        for (int i = 0; i < pts.size(); i += step) result.add(pts.get(i));
        // Zárt polygon esetén az utolsó pontot is hozzáadjuk
        GeoPoint last = pts.get(pts.size() - 1);
        if (!result.get(result.size() - 1).equals(last)) result.add(last);
        return result;
    }

    private int getFillColor(String type) {
        switch (type) {
            case "residential":
            case "commercial":
            case "retail":     return Color.parseColor("#66EE3333"); // piros ~40%
            case "military":   return Color.parseColor("#80AA0000"); // sötétpiros ~50%
            case "industrial": return Color.parseColor("#66888888"); // szürke ~40%
            case "aerodrome":  return Color.parseColor("#80AA00CC"); // lila ~50%
            case "forest":     return Color.parseColor("#66336633"); // zöld ~40%
            case "farmland":
            case "meadow":
            case "grass":
            case "orchard":
            case "vineyard":   return Color.parseColor("#4499BB44"); // sárgászöld ~27%
            default:           return Color.parseColor("#33999999"); // szürke ~20%
        }
    }

    private int getStrokeColor(String type) {
        switch (type) {
            case "residential":
            case "commercial":
            case "retail":     return Color.parseColor("#FFEE3333");
            case "military":   return Color.parseColor("#FFAA0000");
            case "industrial": return Color.parseColor("#FF666666");
            case "aerodrome":  return Color.parseColor("#FFAA00CC");
            case "forest":     return Color.parseColor("#FF336633");
            case "farmland":
            case "meadow":
            case "grass":
            case "orchard":
            case "vineyard":   return Color.parseColor("#FF88AA33");
            default:           return Color.parseColor("#FF888888");
        }
    }

    private float dpToPx(float dp) {
        return dp * mapView.getContext().getResources().getDisplayMetrics().density;
    }

    private static class ParsedZone {
        final List<GeoPoint> points;
        final String zoneType;
        final boolean closed;
        ParsedZone(List<GeoPoint> points, String zoneType, boolean closed) {
            this.points = points;
            this.zoneType = zoneType;
            this.closed = closed;
        }
    }
}
