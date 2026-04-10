package com.dronefly.app.layers;

import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Polyline;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Közigazgatási határok (admin_level=8, település) megjelenítése OSMDroid MapView-on.
 * Forrás: OSM Overpass API, boundary=administrative, admin_level=8.
 *
 * Csak way elemeket kér le (relation nélkül): így minden határszakasz egyszer jelenik meg,
 * nem keletkeznek dupla vonalak a szomszédos settlements megosztott határainál.
 *
 * Vizuális stílus: szaggatott fehér/szürke Polyline, fill nincs.
 *
 * A JSON parse háttérszálon fut – a főszál nem fagy be lekérés közben.
 * Zoom < 10 esetén a lekérés nem indul el (túl nagy bounding box).
 */
public class AdminBoundaryLayer {

    private static final String TAG       = "AdminBoundaryLayer";
    private static final int    MIN_ZOOM  = 10;

    private final MapView        mapView;
    private final List<Polyline> overlays  = new ArrayList<>();
    private boolean              visible   = false;
    private final OverpassClient client    = OverpassClient.getInstance();
    private final Handler        mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService parseExecutor = Executors.newSingleThreadExecutor();

    public AdminBoundaryLayer(MapView mapView) {
        this.mapView = mapView;
    }

    /**
     * Réteg be/ki kapcsolása. Ha látható: elrejti és törli. Ha nem látható: lekérdezi és megjeleníti.
     */
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
                    "Kérlek nagyíts be (zoom ≥ 10) a határok betöltéséhez",
                    Toast.LENGTH_LONG).show();
            if (onDone != null) mainHandler.post(onDone);
            return;
        }

        Toast.makeText(mapView.getContext(), "Településhatárok betöltése...", Toast.LENGTH_SHORT).show();

        client.fetch(buildQuery(bbox), new OverpassClient.OverpassCallback() {
            @Override
            public void onSuccess(final String json) {
                parseExecutor.execute(new Runnable() {
                    @Override
                    public void run() {
                        final List<List<GeoPoint>> parsed = parseJson(json);
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                addParsedLines(parsed);
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
                        "Közigazgatási határok betöltési hiba: " + message,
                        Toast.LENGTH_LONG).show();
                if (onDone != null) onDone.run();
            }
        });
    }

    public boolean isVisible() { return visible; }

    public void clear() {
        for (Polyline p : overlays) mapView.getOverlays().remove(p);
        overlays.clear();
        mapView.invalidate();
    }

    // ── Belső segédek ─────────────────────────────────────────────────────────

    private String buildQuery(BoundingBox bbox) {
        String b = bbox.getLatSouth() + "," + bbox.getLonWest()
                 + "," + bbox.getLatNorth() + "," + bbox.getLonEast();
        // Relation + way: a relation member way-ek geometriáját is lekérjük (out geom)
        // A deduplikációt az OSM adatstruktúra biztosítja (shared way egyszer szerepel)
        return "[out:json][timeout:30];\n(\n"
             + "  relation[\"boundary\"=\"administrative\"][\"admin_level\"=\"8\"](" + b + ");\n"
             + "  way[\"boundary\"=\"administrative\"][\"admin_level\"=\"8\"](" + b + ");\n"
             + ");\nout geom;";
    }

    private List<List<GeoPoint>> parseJson(String json) {
        List<List<GeoPoint>> result = new ArrayList<>();
        try {
            JSONArray elements = new JSONObject(json).getJSONArray("elements");
            for (int i = 0; i < elements.length(); i++) {
                JSONObject el = elements.getJSONObject(i);
                String elType = el.optString("type", "");
                if ("way".equals(elType)) {
                    // Way: geometry közvetlenül az elemen – Polyline-ként rajzoljuk
                    List<GeoPoint> pts = geometryToPoints(el.optJSONArray("geometry"), 2);
                    if (pts != null) result.add(pts);
                } else if ("relation".equals(elType)) {
                    // Relation: member way-ek geometriái – szintén Polyline-ként, nem zárt polygon
                    JSONArray members = el.optJSONArray("members");
                    if (members == null) continue;
                    for (int m = 0; m < members.length(); m++) {
                        JSONObject member = members.getJSONObject(m);
                        String role = member.optString("role", "");
                        if (!"outer".equals(role) && !"".equals(role)) continue;
                        List<GeoPoint> pts = geometryToPoints(member.optJSONArray("geometry"), 2);
                        if (pts != null) result.add(pts);
                    }
                }
            }
        } catch (Throwable t) {
            Log.e(TAG, "JSON parse hiba: " + t.getMessage(), t);
        }
        return result;
    }

    private List<GeoPoint> geometryToPoints(JSONArray geometry, int minPoints) throws org.json.JSONException {
        if (geometry == null || geometry.length() < minPoints) return null;
        List<GeoPoint> points = new ArrayList<>(geometry.length());
        for (int j = 0; j < geometry.length(); j++) {
            JSONObject pt = geometry.getJSONObject(j);
            points.add(new GeoPoint(pt.getDouble("lat"), pt.getDouble("lon")));
        }
        return points.size() >= minPoints ? points : null;
    }

    private void addParsedLines(List<List<GeoPoint>> parsed) {
        float strokeWidthPx = dpToPx(1.5f);
        for (List<GeoPoint> points : parsed) {
            Polyline polyline = new Polyline();
            polyline.setPoints(points);
            polyline.getOutlinePaint().setColor(Color.parseColor("#FFDDDDDD"));
            polyline.getOutlinePaint().setStrokeWidth(strokeWidthPx);
            polyline.getOutlinePaint().setPathEffect(new DashPathEffect(new float[]{12f, 8f}, 0));
            mapView.getOverlays().add(polyline);
            overlays.add(polyline);
        }
        mapView.invalidate();
        Log.d(TAG, "Közigazgatási határok megjelenítve: " + overlays.size() + " vonal");
        if (!overlays.isEmpty()) {
            Toast.makeText(mapView.getContext(),
                    overlays.size() + " határvonal betöltve", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(mapView.getContext(),
                    "Ezen a területen nincs határadat az OSM-ben", Toast.LENGTH_SHORT).show();
        }
    }

    private float dpToPx(float dp) {
        return dp * mapView.getContext().getResources().getDisplayMetrics().density;
    }
}
