package com.dronefly.app.dji;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.util.Log;
import android.view.TextureView;

/**
 * DroneVideoWidget — DJI live kamera feed kezelő.
 *
 * Felelős:
 *   - VideoFeeder primary feed feliratkozás / leiratkozás
 *   - DJICodecManager életciklus (TextureView Surface alapján)
 *   - Toggle (start/stop) a kamera ablakhoz
 *
 * Használat:
 *   widget = new DroneVideoWidget(context, textureView);
 *   widget.start();   // CAM gomb bekapcsolva
 *   widget.stop();    // CAM gomb kikapcsolva / Activity pause
 *   widget.destroy(); // Activity onDestroy
 *
 * MSDK v4 API:
 *   VideoFeeder.getInstance().getPrimaryVideoFeed().addVideoDataListener(...)
 *   DJICodecManager(context, surface, width, height)
 */
public class DroneVideoWidget implements TextureView.SurfaceTextureListener {

    private static final String TAG = "DroneVideoWidget";

    private final Context context;
    private final TextureView textureView;

    // Reflexióval kezelt MSDK objektumok — elkerüli a compile-time linkelési hibát
    // emulátoros build esetén (dji-sdk-provided stub nem tartalmazza ezeket)
    private Object codecManager;      // dji.sdk.codec.DJICodecManager
    private Object videoDataListener; // dji.sdk.camera.VideoFeeder.VideoDataListener
    private Object videoFeed;         // dji.sdk.camera.VideoFeeder.VideoFeed

    private boolean running = false;
    private SurfaceTexture activeSurface;
    private int surfaceWidth;
    private int surfaceHeight;

    public DroneVideoWidget(Context context, TextureView textureView) {
        this.context = context.getApplicationContext();
        this.textureView = textureView;
        textureView.setSurfaceTextureListener(this);
    }

    // ── Publikus API ──────────────────────────────────────────────────────────

    /** CAM gomb bekapcsol — megpróbálja elindítani a feed-et. */
    public void start() {
        running = true;
        if (activeSurface != null) {
            attachCodecAndFeed(activeSurface, surfaceWidth, surfaceHeight);
        }
        // Ha a Surface még nem áll rendelkezésre, az onSurfaceTextureAvailable fogja
        // meghívni az attachCodecAndFeed-et amikor készen lesz.
    }

    /** CAM gomb kikapcsol — leállítja a feedet, de megtartja a widget-et. */
    public void stop() {
        running = false;
        detachFeed();
        releaseCodec();
    }

    /** Activity onDestroy — teljes takarítás. */
    public void destroy() {
        stop();
        textureView.setSurfaceTextureListener(null);
    }

    public boolean isRunning() {
        return running;
    }

    // ── TextureView.SurfaceTextureListener ───────────────────────────────────

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        activeSurface = surface;
        surfaceWidth = width;
        surfaceHeight = height;
        if (running) {
            attachCodecAndFeed(surface, width, height);
        }
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
        surfaceWidth = width;
        surfaceHeight = height;
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        detachFeed();
        releaseCodec();
        activeSurface = null;
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) { }

    // ── Belső logika ──────────────────────────────────────────────────────────

    /**
     * DJICodecManager inicializálás + VideoFeeder feliratkozás.
     * Reflexióval, hogy emulátoros build ne crasheljen.
     */
    private void attachCodecAndFeed(SurfaceTexture surface, int width, int height) {
        try {
            // 1. DJICodecManager létrehozás
            Class<?> codecClass = Class.forName("dji.sdk.codec.DJICodecManager");
            codecManager = codecClass
                    .getConstructor(Context.class, SurfaceTexture.class, int.class, int.class)
                    .newInstance(context, surface, width, height);

            // 2. VideoDataListener proxy
            Class<?> listenerClass = Class.forName(
                    "dji.sdk.camera.VideoFeeder$VideoDataListener");
            videoDataListener = java.lang.reflect.Proxy.newProxyInstance(
                    getClass().getClassLoader(),
                    new Class[]{listenerClass},
                    (proxy, method, args) -> {
                        if ("onReceive".equals(method.getName())
                                && args != null && args.length >= 2
                                && codecManager != null) {
                            byte[] data = (byte[]) args[0];
                            int size = (int) args[1];
                            codecManager.getClass()
                                    .getMethod("sendDataToDecoder", byte[].class, int.class)
                                    .invoke(codecManager, data, size);
                        }
                        return null;
                    });

            // 3. Primary feed lekérés + listener regisztrálás
            Object feeder = Class.forName("dji.sdk.camera.VideoFeeder")
                    .getMethod("getInstance").invoke(null);
            videoFeed = feeder.getClass()
                    .getMethod("getPrimaryVideoFeed").invoke(feeder);
            videoFeed.getClass()
                    .getMethod("addVideoDataListener", listenerClass)
                    .invoke(videoFeed, videoDataListener);

            Log.i(TAG, "Kamera feed elindítva");

        } catch (ClassNotFoundException e) {
            // Emulátoros build — DJI SDK stub nem tartalmazza ezeket az osztályokat
            Log.d(TAG, "Kamera feed nem elérhető (emulátor stub): " + e.getMessage());
        } catch (Throwable t) {
            Log.e(TAG, "Kamera feed hiba: " + t.getMessage());
        }
    }

    /** VideoDataListener leiratkozás. */
    private void detachFeed() {
        if (videoFeed == null || videoDataListener == null) return;
        try {
            Class<?> listenerClass = Class.forName(
                    "dji.sdk.camera.VideoFeeder$VideoDataListener");
            videoFeed.getClass()
                    .getMethod("removeVideoDataListener", listenerClass)
                    .invoke(videoFeed, videoDataListener);
        } catch (Throwable t) {
            Log.d(TAG, "Feed leiratkozás hiba: " + t.getMessage());
        }
        videoDataListener = null;
        videoFeed = null;
    }

    /** DJICodecManager felszabadítás. */
    private void releaseCodec() {
        if (codecManager == null) return;
        try {
            codecManager.getClass().getMethod("cleanSurface").invoke(codecManager);
        } catch (Throwable t) {
            Log.d(TAG, "CodecManager cleanSurface hiba: " + t.getMessage());
        }
        codecManager = null;
    }
}
