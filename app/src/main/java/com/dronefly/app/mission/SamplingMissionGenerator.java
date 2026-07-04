package com.dronefly.app.mission;

import com.dronefly.app.model.MissionConfig;
import com.dronefly.app.model.WaypointData;
import com.dronefly.app.mission.GridMissionGenerator.GeneratorResult;

import org.osmdroid.util.GeoPoint;

import java.util.ArrayList;
import java.util.List;

/**
 * Mintavételi misszió generálása — mintapontokból "érkezés-süllyedés-emelkedés"
 * waypoint-szekvencia (M01 §10, M02 §7).
 *
 * Minden mintaponthoz 3 WaypointData jön létre:
 *   1. Érkezés  — transit magasságon, nincs fotó, nincs hover
 *   2. Mintavétel — leszállás ugyanarra a lat/lon-ra, sample magasságon, fotó + hover
 *   3. Emelkedés — vissza transit magasságra, nincs fotó, nincs hover
 *
 * A süllyedés/emelkedés mindig FÜGGŐLEGESEN, a mintapont fölött történik (nem
 * oldalról, ferdén közelítve) — ez adja az akadálybiztonságot, feltéve hogy a
 * transitAltitudeM már biztonságos magasság minden ismert akadály fölött.
 *
 * A hoverSeconds > 0 waypontokhoz a végrehajtás (MissionUploader.uploadSamplingMission,
 * M04 §15) NORMAL flightPathMode + WaypointAction (STAY + START_TAKE_PHOTO) szükséges —
 * ez CURVED módban figyelmen kívül maradna.
 *
 * MEGJEGYZÉS a visszatérési típusról: szándékosan a GridMissionGenerator.GeneratorResult
 * osztályt hasznosítja újra (nem önálló SamplingResult típust), hogy a MissionPlannerActivity
 * meglévő, sok helyen (feltöltés, export, szimuláció, domborzatkövetés, GPS-ellenőrzés)
 * használt lastResult-alapú logikája változtatás nélkül működjön mindkét misszió-típusra —
 * a ténylegesen mintavételi-specifikus elágazás csak az uploadMission()/uploadSamplingMission()
 * választásnál szükséges (isSamplingMission flag alapján).
 */
public class SamplingMissionGenerator {

    private static final int MAX_WAYPOINTS_PER_MISSION = 99;

    /**
     * @param samplePoints     SamplingPointGenerator.generate() kimenete
     * @param polygon          az eredeti AOI polygon (terület-számításhoz), lehet null
     * @param config           transitAltitudeM, sampleAltitudeM, hoverSeconds, speedMs
     */
    public static GeneratorResult generate(List<GeoPoint> samplePoints,
                                           List<GeoPoint> polygon,
                                           MissionConfig config) {
        GeneratorResult result = new GeneratorResult();
        result.isSamplingMission = true;

        if (samplePoints == null || samplePoints.isEmpty()) {
            result.errorMessage = "Nincsenek mintapontok — generálj előbb pontokat a polygonra.";
            return result;
        }

        double transitAlt = Math.max(3, Math.min(300, config.transitAltitudeM));
        double sampleAlt = Math.max(2, Math.min(transitAlt - 1, config.sampleAltitudeM));
        float hover = Math.max(0f, config.hoverSeconds);

        // altitudeM-ként a fotózási (sample) magasságot tesszük — ez jelenik meg a
        // meglévő UI-ban (pl. EU 120m jogszabályi figyelmeztetés), ami helyes, mert
        // a legtöbb repülési idő a transit magasságon telik, de a fotó a sample
        // magasságon készül, ami a lényegi "repülési magasság" ebben a kontextusban.
        result.altitudeM = sampleAlt;
        result.sampleCount = samplePoints.size();

        if (polygon != null && polygon.size() >= 3) {
            result.areaM2 = polygonAreaM2(polygon);
        }

        List<WaypointData> all = new ArrayList<>();
        double totalHorizontalM = 0.0;
        GeoPoint prevTransitPoint = null;

        for (GeoPoint p : samplePoints) {
            if (prevTransitPoint != null) {
                totalHorizontalM += haversineM(prevTransitPoint, p);
            }

            WaypointData arrive = new WaypointData(p.getLatitude(), p.getLongitude(),
                    (float) transitAlt, false);
            WaypointData sample = new WaypointData(p.getLatitude(), p.getLongitude(),
                    (float) sampleAlt, true);
            sample.hoverSeconds = hover;
            WaypointData climb = new WaypointData(p.getLatitude(), p.getLongitude(),
                    (float) transitAlt, false);

            all.add(arrive);
            all.add(sample);
            all.add(climb);

            prevTransitPoint = p;
        }

        result.totalWaypoints = all.size();
        result.estimatedPhotoCount = samplePoints.size();

        // Becsült repülési idő: vízszintes transit + minden ponton 2x függőleges
        // (süllyedés+emelkedés) + hover összesen. A függőleges sebességet a
        // vízszintessel azonosnak becsüljük (egyszerűsített UI-becslés, nem
        // repülésvezérlési paraméter).
        float speedMs = Math.max(1f, config.speedMs);
        double verticalPerPointM = 2.0 * (transitAlt - sampleAlt);
        double totalVerticalM = verticalPerPointM * samplePoints.size();
        double flightSeconds = (totalHorizontalM + totalVerticalM) / speedMs;
        double hoverSecondsTotal = hover * samplePoints.size();
        result.estimatedMinutes = (flightSeconds + hoverSecondsTotal) / 60.0;

        for (int start = 0; start < all.size(); start += MAX_WAYPOINTS_PER_MISSION) {
            int end = Math.min(start + MAX_WAYPOINTS_PER_MISSION, all.size());
            result.segments.add(new ArrayList<>(all.subList(start, end)));
        }

        return result;
    }

    /**
     * Sokszög területe négyzetméterben (Gauss-képlet, helyi XY vetületen).
     * Publikus, mert az M09 fotóimport (PhotoImportActivity) is ezt használja
     * a kiválasztott repülési terv polygonjából AOI-területet számolni.
     */
    public static double polygonAreaM2(List<GeoPoint> polygon) {
        double centLat = 0, centLon = 0;
        for (GeoPoint p : polygon) { centLat += p.getLatitude(); centLon += p.getLongitude(); }
        centLat /= polygon.size();
        centLon /= polygon.size();

        double mPerDegLat = 111000.0;
        double mPerDegLon = 111000.0 * Math.cos(Math.toRadians(centLat));

        int n = polygon.size();
        double[] x = new double[n];
        double[] y = new double[n];
        for (int i = 0; i < n; i++) {
            x[i] = (polygon.get(i).getLongitude() - centLon) * mPerDegLon;
            y[i] = (polygon.get(i).getLatitude() - centLat) * mPerDegLat;
        }
        double area = 0;
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            area += x[i] * y[j];
            area -= x[j] * y[i];
        }
        return Math.abs(area) / 2.0;
    }

    /** Két GPS pont közti távolság méterben (haversine-formula). */
    private static double haversineM(GeoPoint a, GeoPoint b) {
        double R = 6371000.0;
        double lat1 = Math.toRadians(a.getLatitude());
        double lat2 = Math.toRadians(b.getLatitude());
        double dLat = Math.toRadians(b.getLatitude() - a.getLatitude());
        double dLon = Math.toRadians(b.getLongitude() - a.getLongitude());
        double h = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(h), Math.sqrt(1 - h));
        return R * c;
    }
}
