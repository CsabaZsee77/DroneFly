package com.dronefly.app.model;

/**
 * Egy blokk repülési állapota az M07 blokk-felosztásban.
 *
 * Állapotátmenetek (lásd L3 §Állapotgép):
 *   NOT_STARTED → IN_PROGRESS (misszió Start)
 *   IN_PROGRESS → DONE        ([Kész] gomb)
 *   IN_PROGRESS → NOT_STARTED (kézi reset — nem szokványos)
 *   DONE        → NOT_STARTED ([Visszaállít] gomb, újrarepüléshez)
 */
public enum BlockStatus {
    NOT_STARTED,
    IN_PROGRESS,
    DONE
}
