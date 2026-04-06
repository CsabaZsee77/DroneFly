package com.dronefly.app.dji;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.util.Log;
import android.view.TextureView;

import dji.sdk.base.BaseProduct;

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

    /**
     * Tap-to-expose: a P4P v1 fix fókuszú, de az expozíciót az érintett területre igazítja.
     * normalizedX / normalizedY: 0.0–1.0, a TextureView-n belüli relatív pozíció.
     * MSDK v4 hívások reflexióval: Camera.setFocusMode(AF) + Camera.setFocusTarget(PointF).
     */
    public void tapToFocus(float normalizedX, float normalizedY) {
        try {
            BaseProduct p = dji.sdk.sdkmanager.DJISDKManager.getInstance().getProduct();
            if (p == null || !p.isConnected()) return;
            Object camera = p.getClass().getMethod("getCamera").invoke(p);
            if (camera == null) return;

            // FocusMode.AUTO enum érték lekérése
            Class<?> focusModeClass = Class.forName(
                    "dji.common.camera.SettingsDefinitions$FocusMode");
            Object autoMode = focusModeClass.getField("AUTO").get(null);

            // CompletionCallback proxy setFocusMode-hoz
            Class<?> completionCbClass = Class.forName(
                    "dji.common.commontype.DJIError");
            // A setFocusMode aláírása: setFocusMode(FocusMode, CommonCallbacks.CompletionCallback)
            // Megkeressük a metódust dinamikusan
            java.lang.reflect.Method setFocusModeMethod = null;
            for (java.lang.reflect.Method m : camera.getClass().getMethods()) {
                if ("setFocusMode".equals(m.getName()) && m.getParameterTypes().length == 2) {
                    setFocusModeMethod = m;
                    break;
                }
            }
            if (setFocusModeMethod == null) {
                Log.d(TAG, "setFocusMode metódus nem található");
                return;
            }

            Class<?> cb1Class = setFocusModeMethod.getParameterTypes()[1];
            final float nx = normalizedX;
            final float ny = normalizedY;
            final Object cam = camera;

            Object focusModeCallback = java.lang.reflect.Proxy.newProxyInstance(
                    getClass().getClassLoader(),
                    new Class[]{cb1Class},
                    (proxy, method, args) -> {
                        switch (method.getName()) {
                            case "hashCode": return System.identityHashCode(proxy);
                            case "equals":   return proxy == (args != null ? args[0] : null);
                            case "toString": return "DroneVideoWidget$FocusModeCallback";
                        }
                        // onResult(DJIError) — ha nincs hiba, setFocusTarget
                        if ("onResult".equals(method.getName())) {
                            boolean hasError = (args != null && args[0] != null);
                            if (!hasError) {
                                setFocusTarget(cam, nx, ny);
                            } else {
                                // Hiba esetén próbáljuk meg közvetlenül a setFocusTarget-et
                                setFocusTarget(cam, nx, ny);
                            }
                        }
                        return null;
                    });

            setFocusModeMethod.invoke(camera, autoMode, focusModeCallback);

        } catch (ClassNotFoundException e) {
            Log.d(TAG, "tapToFocus: DJI osztály nem elérhető (stub): " + e.getMessage());
        } catch (Throwable t) {
            Throwable cause = (t.getCause() != null) ? t.getCause() : t;
            Log.d(TAG, "tapToFocus hiba: " + cause.getClass().getSimpleName()
                    + " – " + cause.getMessage());
        }
    }

    private void setFocusTarget(Object camera, float nx, float ny) {
        try {
            android.graphics.PointF point = new android.graphics.PointF(nx, ny);

            java.lang.reflect.Method setFocusTargetMethod = null;
            for (java.lang.reflect.Method m : camera.getClass().getMethods()) {
                if ("setFocusTarget".equals(m.getName()) && m.getParameterTypes().length == 2) {
                    setFocusTargetMethod = m;
                    break;
                }
            }
            if (setFocusTargetMethod == null) {
                Log.d(TAG, "setFocusTarget metódus nem található");
                return;
            }
            Class<?> cb2Class = setFocusTargetMethod.getParameterTypes()[1];
            Object focusTargetCallback = java.lang.reflect.Proxy.newProxyInstance(
                    getClass().getClassLoader(),
                    new Class[]{cb2Class},
                    (proxy, method, args) -> {
                        switch (method.getName()) {
                            case "hashCode": return System.identityHashCode(proxy);
                            case "equals":   return proxy == (args != null ? args[0] : null);
                            case "toString": return "DroneVideoWidget$FocusTargetCallback";
                        }
                        return null;
                    });
            setFocusTargetMethod.invoke(camera, point, focusTargetCallback);
            Log.d(TAG, "setFocusTarget → (" + nx + ", " + ny + ")");
        } catch (Throwable t) {
            Throwable cause = (t.getCause() != null) ? t.getCause() : t;
            Log.d(TAG, "setFocusTarget hiba: " + cause.getClass().getSimpleName()
                    + " – " + cause.getMessage());
        }
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
            Log.d(TAG, "DJICodecManager létrehozva");

            // 2. Primary feed lekérés — null check: kamera még nincs kész?
            Class<?> listenerClass = Class.forName(
                    "dji.sdk.camera.VideoFeeder$VideoDataListener");
            Object feeder = Class.forName("dji.sdk.camera.VideoFeeder")
                    .getMethod("getInstance").invoke(null);
            if (feeder == null) {
                Log.w(TAG, "VideoFeeder.getInstance() null – SDK nem inicializált");
                return;
            }
            videoFeed = feeder.getClass()
                    .getMethod("getPrimaryVideoFeed").invoke(feeder);
            if (videoFeed == null) {
                Log.w(TAG, "getPrimaryVideoFeed() null – kamera még nem aktív, próbáld újra");
                releaseCodec();
                return;
            }

            // 3. VideoDataListener proxy
            videoDataListener = java.lang.reflect.Proxy.newProxyInstance(
                    getClass().getClassLoader(),
                    new Class[]{listenerClass},
                    (proxy, method, args) -> {
                        switch (method.getName()) {
                            case "hashCode":  return System.identityHashCode(proxy);
                            case "equals":    return proxy == (args != null ? args[0] : null);
                            case "toString":  return "DroneVideoWidget$VideoDataListener";
                        }
                        if ("onReceive".equals(method.getName())
                                && args != null && args.length >= 2
                                && codecManager != null) {
                            try {
                                byte[] data = (byte[]) args[0];
                                int size = (int) args[1];
                                codecManager.getClass()
                                        .getMethod("sendDataToDecoder", byte[].class, int.class)
                                        .invoke(codecManager, data, size);
                            } catch (Throwable t2) {
                                Log.e(TAG, "sendDataToDecoder hiba: " + t2.getClass().getSimpleName());
                            }
                        }
                        return null;
                    });

            // 4. Listener regisztrálás
            videoFeed.getClass()
                    .getMethod("addVideoDataListener", listenerClass)
                    .invoke(videoFeed, videoDataListener);

            Log.i(TAG, "Kamera feed elindítva");

        } catch (ClassNotFoundException e) {
            // Emulátoros build — DJI SDK stub nem tartalmazza ezeket az osztályokat
            Log.d(TAG, "Kamera feed nem elérhető (emulátor stub): " + e.getMessage());
        } catch (Throwable t) {
            Throwable cause = (t.getCause() != null) ? t.getCause() : t;
            Log.e(TAG, "Kamera feed hiba: " + cause.getClass().getSimpleName()
                    + " – " + cause.getMessage());
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
