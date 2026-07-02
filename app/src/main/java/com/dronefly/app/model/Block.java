package com.dronefly.app.model;

import org.osmdroid.util.GeoPoint;

import java.util.ArrayList;
import java.util.List;

/**
 * Egy blokk az M07 blokk-felosztásban.
 *
 * Lásd: docs/M07_BLOKK_FELOSZTAS/M07_L3_ALLAPOTGEP_ES_ENGINE.md §Block adatmodell
 */
public class Block {

    /** Blokk azonosító, pl. "B-2-3" (1-től indexelve a felhasználónak). */
    public String id;

    public int row;
    public int col;

    /** 4-pontos rotált cella poligon, GPS koordinátákban. */
    public List<GeoPoint> cellPolygon = new ArrayList<>();

    /** A tényleges repülési poligon = (cella + overlapBuffer) ∩ AOI. */
    public List<GeoPoint> missionPolygon = new ArrayList<>();

    /** Cella geometriai közepe (felirat helye). */
    public GeoPoint cellCenter;

    public double cellAreaM2;
    public double missionAreaM2;

    /** missionAreaM2 / cellAreaM2 — 0..1+ (puffer miatt lehet 1 felett is). */
    public double coverageRatio;

    public BlockStatus status = BlockStatus.NOT_STARTED;

    public Block(int row, int col) {
        this.row = row;
        this.col = col;
        this.id  = "B-" + (row + 1) + "-" + (col + 1);
    }
}
