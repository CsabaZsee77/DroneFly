package com.dronefly.app.ai;

/**
 * Egy mintapont footprint területének forrása (M09_L1 §10.4).
 *
 * EXIF      — a magasságból számolt (jelenlegi/alapértelmezett viselkedés)
 * MANUAL    — képenkénti kézi vonalzós GSD-mérés
 * INHERITED — egy másik kép mérésének átvétele (örökítés minden képre)
 * ANCHORED  — egy mérés + EXIF-relatív korrekció (lehorgonyzott mód)
 */
public enum FootprintSource {
    EXIF,
    MANUAL,
    INHERITED,
    ANCHORED
}
