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
 * Natura 2000 és nemzeti park területek megjelenítése OSMDroid MapView-on.
 * Forrás: OSM Overpass API, boundary=protected_area, protect_class 2/4/5/6.
 *
 * Megjelenítési logika:
 *  - Zárt way (első == utolsó pont): Polygon fillel + körvonallal
 *  - Nyitott way / relation member szegmens: Polyline (csak körvonal, fill nélkül)
 *
 * Szín kód (protect_class):
 *  - 2 nemzeti park:    zöld   #FF00AA44
 *  - 4 Natura 2000 SAC: narancs #FFFF8800
 *  - 5/6 egyéb:         barna  #FFAA6600
 *  - default:           sárga  #FFEECC00
 */
public class ProtectedAreasLayer {

    private static final String TAG      = "ProtectedAreasLayer";
    private static final int    MIN_ZOOM = 10;

    private final MapView          mapView;
    private final List<Overlay>    overlays      = new ArrayList<>();
    private boolean                visible       = false;
    private final OverpassClient   client        = OverpassClient.getInstance();
    private final Handler          mainHandler   = new Handler(Looper.getMainLooper());
    private final ExecutorService  parseExecutor = Executors.newSingleThreadExecutor();

    public ProtectedAreasLayer(MapView mapView) {
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
                    "Kérlek nagyíts be (zoom ≥ 10) a védett területek betöltéséhez",
                    Toast.LENGTH_LONG).show();
            if (onDone != null) mainHandler.post(onDone);
            return;
        }

        Toast.makeText(mapView.getContext(), "Védett területek betöltése...", Toast.LENGTH_SHORT).show();

        client.fetch(buildQuery(bbox), new OverpassClient.OverpassCallback() {
            @Override
            public void onSuccess(final String json) {
                parseExecutor.execute(new Runnable() {
                    @Override
                    public void run() {
                        final List<ParsedArea> parsed = parseJson(json);
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                addParsedAreas(parsed);
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
                        "Védett területek betöltési hiba: " + message,
                        Toast.LENGTH_LONG).show();
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
        // Egyszerű query – protect_class szűrés a parse-ban történik (gyorsabb Overpass feldolg.)
        return "[out:json][timeout:60];\n(\n"
             + "  relation[\"boundary\"=\"protected_area\"](" + b + ");\n"
             + "  way[\"boundary\"=\"protected_area\"](" + b + ");\n"
             + ");\nout geom;";
    }

    private List<ParsedArea> parseJson(String json) {
        List<ParsedArea> result = new ArrayList<>();
        try {
            JSONArray elements = new JSONObject(json).getJSONArray("elements");
            for (int i = 0; i < elements.length(); i++) {
                JSONObject el = elements.getJSONObject(i);

                String protectClass = "";
                JSONObject tags = el.optJSONObject("tags");
                if (tags != null) protectClass = tags.optString("protect_class", "");

                // Szűrés: csak 2, 4, 5, 6 protect_class érdekes
                if (!isRelevantClass(protectClass)) continue;

                String elType = el.optString("type", "");
                if ("way".equals(elType)) {
                    List<GeoPoint> pts = toPoints(el.optJSONArray("geometry"), 2);
                    if (pts != null) {
                        boolean closed = isClosed(pts);
                        result.add(new ParsedArea(pts, protectClass, closed));
                    }
                } else if ("relation".equals(elType)) {
                    JSONArray members = el.optJSONArray("members");
                    if (members == null) continue;
                    for (int m = 0; m < members.length(); m++) {
                        JSONObject member = members.getJSONObject(m);
                        if (!"outer".equals(member.optString("role", ""))) continue;
                        List<GeoPoint> pts = toPoints(member.optJSONArray("geometry"), 2);
                        // Relation member way = nyitott szegmens → isClosed=false
                        if (pts != null) result.add(new ParsedArea(pts, protectClass, false));
                    }
                }
            }
        } catch (Throwable t) {
            Log.e(TAG, "JSON parse hiba: " + t.getMessage(), t);
        }
        return result;
    }

    private boolean isRelevantClass(String pc) {
        return "2".equals(pc) || "4".equals(pc) || "5".equals(pc) || "6".equals(pc);
    }

    private boolean isClosed(List<GeoPoint> pts) {
        if (pts.size() < 3) return false;
        GeoPoint first = pts.get(0);
        GeoPoint last  = pts.get(pts.size() - 1);
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

    private void addParsedAreas(List<ParsedArea> parsed) {
        int polygonCount = 0, lineCount = 0;
        for (ParsedArea pa : parsed) {
            int stroke = getStrokeColor(pa.protectClass);
            float sw   = dpToPx(getStrokeWidth(pa.protectClass));

            if (pa.closed) {
                // Zárt way → Polygon fillel
                Polygon polygon = new Polygon();
                polygon.setPoints(pa.points);
                polygon.getFillPaint().setColor(getFillColor(pa.protectClass));
                polygon.getOutlinePaint().setColor(stroke);
                polygon.getOutlinePaint().setStrokeWidth(sw);
                mapView.getOverlays().add(polygon);
                overlays.add(polygon);
                polygonCount++;
            } else {
                // Nyitott szegmens (relation member) → Polyline
                Polyline polyline = new Polyline();
                polyline.setPoints(pa.points);
                polyline.getOutlinePaint().setColor(stroke);
                polyline.getOutlinePaint().setStrokeWidth(sw);
                mapView.getOverlays().add(polyline);
                overlays.add(polyline);
                lineCount++;
            }
        }
        mapView.invalidate();
        Log.d(TAG, "Védett területek: " + polygonCount + " polygon, " + lineCount + " vonal");
        if (!overlays.isEmpty()) {
            Toast.makeText(mapView.getContext(),
                    (polygonCount + lineCount) + " védett terület betöltve",
                    Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(mapView.getContext(),
                    "Ezen a területen nincs védett terület az OSM-ben",
                    Toast.LENGTH_SHORT).show();
        }
    }

    private int getFillColor(String pc) {
        if ("2".equals(pc)) return Color.parseColor("#8000AA44");
        if ("4".equals(pc)) return Color.parseColor("#80FF8800");
        if ("5".equals(pc) || "6".equals(pc)) return Color.parseColor("#66AA6600");
        return Color.parseColor("#66EECC00");
    }

    private int getStrokeColor(String pc) {
        if ("2".equals(pc)) return Color.parseColor("#FF00AA44");
        if ("4".equals(pc)) return Color.parseColor("#FFFF8800");
        if ("5".equals(pc) || "6".equals(pc)) return Color.parseColor("#FFAA6600");
        return Color.parseColor("#FFEECC00");
    }

    private float getStrokeWidth(String pc) { return "2".equals(pc) ? 2.5f : 2.0f; }

    private float dpToPx(float dp) {
        return dp * mapView.getContext().getResources().getDisplayMetrics().density;
    }

    private static class ParsedArea {
        final List<GeoPoint> points;
        final String protectClass;
        final boolean closed;
        ParsedArea(List<GeoPoint> points, String protectClass, boolean closed) {
            this.points = points;
            this.protectClass = protectClass;
            this.closed = closed;
        }
    }
}
