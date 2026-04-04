package com.dronefly.app.mission;

import com.dronefly.app.model.WaypointData;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Misszió exportálás Litchi CSV és KMZ formátumban.
 * A KMZ Google Earth-ben és DJI Pilot-ban is megnyitható.
 */
public class MissionExporter {

    /**
     * Litchi-kompatibilis CSV – ugyanaz a formátum amit a webes app is exportál.
     * Közvetlenül importálható Litchi-ba vagy visszatölthető a webes appba.
     */
    public static String toLitchiCsv(List<WaypointData> waypoints, float speedMs) {
        StringBuilder sb = new StringBuilder();
        // Fejléc
        sb.append("latitude,longitude,altitude(m),heading(deg),curvesize(m),rotationdir,")
          .append("gimbalmode,gimbalpitchangle,")
          .append("actiontype1,actionparam1,actiontype2,actionparam2,")
          .append("actiontype3,actionparam3,actiontype4,actionparam4,")
          .append("actiontype5,actionparam5,actiontype6,actionparam6,")
          .append("actiontype7,actionparam7,actiontype8,actionparam8,")
          .append("actiontype9,actionparam9,actiontype10,actionparam10,")
          .append("actiontype11,actionparam11,actiontype12,actionparam12,")
          .append("actiontype13,actionparam13,actiontype14,actionparam14,")
          .append("actiontype15,actionparam15,")
          .append("altitudemode,speed(m/s),")
          .append("poi_latitude,poi_longitude,poi_altitude(m),poi_altitudemode,")
          .append("photo_timeinterval,photo_distinterval\n");

        for (WaypointData wp : waypoints) {
            int action = wp.shootPhoto ? 1 : -1;
            sb.append(String.format("%.8f,%.8f,%.1f,%.0f,0,0,2,%.0f,",
                    wp.latitude, wp.longitude, wp.altitudeM,
                    wp.heading, wp.gimbalPitch));
            // 15 action slot: első fotó, többi -1
            sb.append(action).append(",0,");
            for (int i = 1; i < 15; i++) sb.append("-1,0,");
            sb.append(String.format("0,%.1f,0,0,0,0,-1,-1\n", speedMs));
        }
        return sb.toString();
    }

    /**
     * KMZ fájl generálása (KML + zip).
     * Megnyitható Google Earth-ben és DJI Pilot-ban.
     */
    public static byte[] toKmz(List<WaypointData> waypoints, String missionName)
            throws IOException {
        String kml = buildKml(waypoints, missionName);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            ZipEntry entry = new ZipEntry("doc.kml");
            zos.putNextEntry(entry);
            zos.write(kml.getBytes("UTF-8"));
            zos.closeEntry();
        }
        return baos.toByteArray();
    }

    private static String buildKml(List<WaypointData> waypoints, String name) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
          .append("<kml xmlns=\"http://www.opengis.net/kml/2.2\">\n")
          .append("<Document>\n")
          .append("  <name>").append(name).append("</name>\n")
          // Útvonal
          .append("  <Placemark>\n")
          .append("    <name>Útvonal</name>\n")
          .append("    <LineString>\n")
          .append("      <altitudeMode>relativeToGround</altitudeMode>\n")
          .append("      <coordinates>\n");

        for (WaypointData wp : waypoints) {
            sb.append("        ")
              .append(wp.longitude).append(",")
              .append(wp.latitude).append(",")
              .append(wp.altitudeM).append("\n");
        }
        sb.append("      </coordinates>\n")
          .append("    </LineString>\n")
          .append("  </Placemark>\n");

        // Egyedi waypointok
        for (int i = 0; i < waypoints.size(); i++) {
            WaypointData wp = waypoints.get(i);
            sb.append("  <Placemark>\n")
              .append("    <name>WP").append(i + 1).append("</name>\n")
              .append("    <Point>\n")
              .append("      <altitudeMode>relativeToGround</altitudeMode>\n")
              .append("      <coordinates>")
              .append(wp.longitude).append(",")
              .append(wp.latitude).append(",")
              .append(wp.altitudeM)
              .append("</coordinates>\n")
              .append("    </Point>\n")
              .append("  </Placemark>\n");
        }

        sb.append("</Document>\n</kml>");
        return sb.toString();
    }
}
