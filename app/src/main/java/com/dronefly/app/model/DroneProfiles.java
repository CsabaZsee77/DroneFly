package com.dronefly.app.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Támogatott drón profilok nyilvántartása.
 *
 * Új drón hozzáadása:
 *   ALL.add(new DroneProfile("Drón neve", sensorW, sensorH, focal, imgW, imgH, maxV, shutter, "megjegyzés"));
 *
 * Jelenleg csak Phantom 4 Pro v1 aktív.
 */
public class DroneProfiles {

    public static final List<DroneProfile> ALL;

    static {
        List<DroneProfile> list = new ArrayList<>();

        // ── Aktív drónok ──────────────────────────────────────────────────────

        list.add(new DroneProfile(
            "Phantom 4 Pro v1",
            13.2, 8.8,   // szenzor: 1" CMOS, 13.2×8.8 mm
            8.8,          // fókusz: 8.8 mm
            5472, 3648,   // felbontás: 20 MP
            12f,          // MSDK waypoint max sebesség
            800,          // 1/800s mechanikus záridő
            "1\" CMOS 20MP, mechanikus záridő, MSDK v4"
        ));

        // ── Előkészített profilok (egyelőre inaktív) ─────────────────────────
        // Komment eltávolításával aktiválható:

        // list.add(new DroneProfile(
        //     "Phantom 4 Pro v2",
        //     13.2, 8.8, 8.8, 5472, 3648,
        //     12f, 800,
        //     "1\" CMOS 20MP, OcuSync 2.0, MSDK v4"
        // ));

        // list.add(new DroneProfile(
        //     "DJI Mini 4 Pro",
        //     9.6, 7.2, 8.4, 4000, 3000,
        //     8f, 800,
        //     "1/1.3\" CMOS 12MP, MSDK v5"
        // ));

        // list.add(new DroneProfile(
        //     "Mavic 3 Enterprise",
        //     17.3, 13.0, 12.3, 5280, 3956,
        //     15f, 1000,
        //     "4/3\" CMOS 20MP, MSDK v5"
        // ));

        ALL = Collections.unmodifiableList(list);
    }

    /** Alapértelmezett drón (lista első eleme) */
    public static DroneProfile getDefault() {
        return ALL.get(0);
    }
}
