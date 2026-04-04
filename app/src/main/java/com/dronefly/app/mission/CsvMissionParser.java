package com.dronefly.app.mission;

import com.dronefly.app.model.WaypointData;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * Litchi-formátumú CSV fájl beolvasása.
 * Fejléc: latitude,longitude,altitude(m),heading(deg),curvesize(m),rotationdir,
 *         gimbalmode,gimbalpitchangle,actiontype1,actionparam1,...
 */
public class CsvMissionParser {

    public static class ParseResult {
        public List<WaypointData> waypoints = new ArrayList<>();
        public String errorMessage;
        public int skippedRows;
    }

    public static ParseResult parse(InputStream inputStream) {
        ParseResult result = new ParseResult();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            String line = reader.readLine(); // fejléc sor kihagyása
            if (line == null) {
                result.errorMessage = "Üres fájl";
                return result;
            }

            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                String[] cols = line.split(",");
                if (cols.length < 8) {
                    result.skippedRows++;
                    continue;
                }

                try {
                    double lat     = Double.parseDouble(cols[0].trim());
                    double lon     = Double.parseDouble(cols[1].trim());
                    float  alt     = Float.parseFloat(cols[2].trim());
                    float  gimbal  = Float.parseFloat(cols[7].trim());

                    WaypointData wp = new WaypointData(lat, lon, alt);
                    wp.gimbalPitch = gimbal;
                    wp.heading = Float.parseFloat(cols[3].trim());

                    // actiontype1 = 1 → SHOOT_PHOTO
                    if (cols.length > 8) {
                        int actionType = Integer.parseInt(cols[8].trim());
                        wp.shootPhoto = (actionType == 1);
                    }

                    result.waypoints.add(wp);
                } catch (NumberFormatException e) {
                    result.skippedRows++;
                }
            }
        } catch (IOException e) {
            result.errorMessage = "Fájl olvasási hiba: " + e.getMessage();
        }
        return result;
    }
}
