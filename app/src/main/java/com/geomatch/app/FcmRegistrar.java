package com.geomatch.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Registro do token FCM no backend (geomatch-cvtv.onrender.com).
 *
 * Integração isolada: reutiliza a mesma sessão (apiToken) que o LocationService
 * já usa como Bearer, persistida em SharedPreferences("GeoMatchSession").
 *
 * O token FCM existe assim que o app abre (é do dispositivo), mas o apiToken só
 * existe após o login no WebView. Por isso persistimos o token FCM localmente e
 * o enviamos ao backend assim que a sessão estiver disponível.
 */
public final class FcmRegistrar {

    private static final String TAG = "GeoMatch_FCM";

    // Mesma store de sessão usada por LocationService/WebAppInterface.
    private static final String PREFS_NAME = "GeoMatchSession";
    private static final String KEY_API_TOKEN = "apiToken";
    private static final String KEY_FCM_TOKEN = "fcmToken";

    private static final String ENDPOINT =
            "https://geomatch-cvtv.onrender.com/push_subscriptions/fcm";

    private FcmRegistrar() {
        // Utilitário estático — não instanciável.
    }

    /**
     * Persiste o token FCM localmente e o registra no backend caso já exista uma
     * sessão ativa. Chamado quando o token é obtido/renovado (MainActivity e
     * MyFirebaseMessagingService.onNewToken).
     */
    public static void salvarERegistrar(Context context, String fcmToken) {
        if (context == null || fcmToken == null || fcmToken.isEmpty()) return;

        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_FCM_TOKEN, fcmToken).apply();

        String apiToken = prefs.getString(KEY_API_TOKEN, null);
        if (apiToken != null && !apiToken.isEmpty()) {
            enviarParaBackend(fcmToken, apiToken);
        } else {
            Log.d(TAG, "Token FCM salvo; aguardando login para registrar no backend.");
        }
    }

    /**
     * Recebe a sessão do WebView (após o login) e registra no backend o token FCM
     * previamente salvo. Chamado por WebAppInterface.registrarTokenFCM(apiToken).
     */
    public static void registrarComSessao(Context context, String apiToken) {
        if (context == null || apiToken == null || apiToken.isEmpty()) return;

        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_API_TOKEN, apiToken).apply();

        String fcmToken = prefs.getString(KEY_FCM_TOKEN, null);
        if (fcmToken != null && !fcmToken.isEmpty()) {
            enviarParaBackend(fcmToken, apiToken);
        } else {
            Log.d(TAG, "Sessão recebida; token FCM ainda não disponível para registro.");
        }
    }

    /**
     * POST { "fcm_token": "..." } para /push_subscriptions/fcm, autenticado por
     * Bearer. Executa em thread própria para não bloquear a UI.
     */
    private static void enviarParaBackend(String fcmToken, String apiToken) {
        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(ENDPOINT);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json; utf-8");
                conn.setRequestProperty("Accept", "application/json");
                conn.setRequestProperty("Authorization", "Bearer " + apiToken);
                conn.setDoOutput(true);

                // Tokens FCM contêm apenas [A-Za-z0-9_:.-]; não requerem escape JSON.
                String jsonInputString = "{\"fcm_token\": \"" + fcmToken + "\"}";

                try (OutputStream os = conn.getOutputStream()) {
                    byte[] input = jsonInputString.getBytes("utf-8");
                    os.write(input, 0, input.length);
                }

                int code = conn.getResponseCode();
                if (code >= 200 && code < 300) {
                    Log.d(TAG, "Token FCM registrado no backend com sucesso (HTTP " + code + ").");
                } else {
                    Log.w(TAG, "Falha ao registrar token FCM no backend (HTTP " + code + ").");
                }
            } catch (Exception e) {
                Log.e(TAG, "Erro ao registrar token FCM no backend", e);
            } finally {
                if (conn != null) conn.disconnect();
            }
        }).start();
    }
}
