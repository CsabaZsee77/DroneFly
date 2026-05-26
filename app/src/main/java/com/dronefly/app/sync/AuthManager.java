package com.dronefly.app.sync;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URL;
import java.security.cert.X509Certificate;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/**
 * Dronterapia API autentikáció — közvetlen felhasználónév + jelszó alapú bejelentkezés.
 *
 * POST /api/auth/login → {"access_token": "...", "username": "..."}
 * Token tárolás: SharedPreferences (cleartext — Crystal Sky zárt eszköz, nem Play Store).
 */
public class AuthManager {

    private static final String TAG = "AuthManager";

    private static final String API_BASE   = "https://app.dronterapia.hu/api";
    private static final String PREFS_NAME = "dronefly_auth";
    private static final String KEY_TOKEN  = "bearer_token";
    private static final String KEY_USER   = "username";

    private static final int TIMEOUT_MS = 15000;

    private static volatile AuthManager instance;

    private final SharedPreferences prefs;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // Android 5.1 SSL fix — Let's Encrypt gyökér nem ismert Crystal Sky-n
    private static SSLContext trustAllSslContext;
    private static final HostnameVerifier trustAllHostnames =
            new HostnameVerifier() {
                @Override public boolean verify(String hostname, SSLSession session) { return true; }
            };

    static {
        try {
            TrustManager[] trustAll = new TrustManager[]{
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                    public void checkClientTrusted(X509Certificate[] c, String t) {}
                    public void checkServerTrusted(X509Certificate[] c, String t) {}
                }
            };
            trustAllSslContext = SSLContext.getInstance("TLS");
            trustAllSslContext.init(null, trustAll, new java.security.SecureRandom());
        } catch (Exception e) {
            Log.e(TAG, "SSL context init hiba: " + e.getMessage());
        }
    }

    // ---- Callback interfészek ----

    public interface LoginCallback {
        void onSuccess(String token, String username);
        void onError(String message);
    }

    public interface LogoutCallback {
        void onDone();
    }

    // ---- Singleton ----

    private AuthManager(Context context) {
        prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static AuthManager getInstance(Context context) {
        if (instance == null) {
            synchronized (AuthManager.class) {
                if (instance == null) {
                    instance = new AuthManager(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    // ---- Publikus API ----

    /** Van-e érvényes (tárolt) token? */
    public boolean isAuthenticated() {
        return prefs.getString(KEY_TOKEN, null) != null;
    }

    /** Tárolt Bearer token, vagy null. */
    public String getToken() {
        return prefs.getString(KEY_TOKEN, null);
    }

    /** Tárolt felhasználónév, vagy null. */
    public String getUsername() {
        return prefs.getString(KEY_USER, null);
    }

    /**
     * Bejelentkezés felhasználónév + jelszóval.
     * POST /api/auth/login — ha sikeres, elmenti a tokent SharedPreferences-be.
     */
    public void login(final String username, final String password, final LoginCallback callback) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    JSONObject body = new JSONObject();
                    body.put("username", username);
                    body.put("password", password);
                    byte[] bodyBytes = body.toString().getBytes("UTF-8");

                    HttpsURLConnection conn = openHttps(API_BASE + "/auth/login");
                    conn.setRequestMethod("POST");
                    conn.setRequestProperty("Content-Type", "application/json");
                    conn.setDoOutput(true);
                    conn.setDoInput(true);
                    OutputStream out = conn.getOutputStream();
                    out.write(bodyBytes);
                    out.flush();

                    int status = conn.getResponseCode();
                    String responseBody = readBody(conn, status < 400);
                    conn.disconnect();

                    if (status == 200) {
                        JSONObject json = new JSONObject(responseBody);
                        final String token    = json.getString("access_token");
                        final String user     = json.optString("username", username);
                        saveToken(token, user);
                        deliverOnMain(new Runnable() {
                            @Override public void run() { callback.onSuccess(token, user); }
                        });
                    } else {
                        String detail = responseBody;
                        try { detail = new JSONObject(responseBody).optString("detail", responseBody); } catch (Exception ignored) {}
                        final String errMsg = detail;
                        deliverOnMain(new Runnable() {
                            @Override public void run() { callback.onError(errMsg); }
                        });
                    }
                } catch (final Exception e) {
                    Log.e(TAG, "login hiba", e);
                    deliverOnMain(new Runnable() {
                        @Override public void run() { callback.onError(e.getMessage()); }
                    });
                }
            }
        });
    }

    /** Token törlése (kijelentkezés). */
    public void logout(final LogoutCallback callback) {
        prefs.edit().remove(KEY_TOKEN).remove(KEY_USER).apply();
        if (callback != null) {
            mainHandler.post(new Runnable() {
                @Override public void run() { callback.onDone(); }
            });
        }
    }

    // ---- Belső segédek ----

    private void saveToken(String token, String username) {
        prefs.edit()
                .putString(KEY_TOKEN, token)
                .putString(KEY_USER, username)
                .apply();
    }

    private HttpsURLConnection openHttps(String urlStr) throws Exception {
        URL url = new URL(urlStr);
        HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
        if (trustAllSslContext != null) {
            conn.setSSLSocketFactory(trustAllSslContext.getSocketFactory());
            conn.setHostnameVerifier(trustAllHostnames);
        }
        conn.setConnectTimeout(TIMEOUT_MS);
        conn.setReadTimeout(TIMEOUT_MS);
        return conn;
    }

    private String readBody(HttpsURLConnection conn, boolean success) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(
                success ? conn.getInputStream() : conn.getErrorStream(), "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) sb.append(line);
        reader.close();
        return sb.toString();
    }

    private void deliverOnMain(Runnable r) {
        mainHandler.post(r);
    }
}
