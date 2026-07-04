package com.geomatch.app;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.util.Log;
import android.webkit.JavascriptInterface;

import androidx.core.content.ContextCompat;

public class WebAppInterface {

    private static final String TAG = "GeoMatch_JSInterface";
    private static final String TRUSTED_HOST = "geomatch-cvtv.onrender.com";

    Context mContext;
    private MainActivity mainActivity;

    // Atualizado atomicamente pela UI thread via setCurrentUrl(); lido pela JS thread.
    private volatile String currentUrl = "";

    WebAppInterface(MainActivity activity) {
        mContext = activity;
        mainActivity = activity;
    }

    /** Chamado pelo WebViewClient na UI thread ao iniciar/terminar cada navegação. */
    void setCurrentUrl(String url) {
        currentUrl = (url != null) ? url : "";
    }

    /** Retorna true apenas quando a página carregada vem do nosso domínio via HTTPS. */
    private boolean isOriginTrusted() {
        try {
            Uri uri = Uri.parse(currentUrl);
            return "https".equals(uri.getScheme()) && TRUSTED_HOST.equals(uri.getHost());
        } catch (Exception e) {
            return false;
        }
    }

    @JavascriptInterface
    public void mostrarNotificacao(String title, String body, String path) {
        if (!isOriginTrusted()) {
            Log.w(TAG, "mostrarNotificacao bloqueada: origem não confiável (" + currentUrl + ")");
            return;
        }
        if (mainActivity != null) {
            mainActivity.criarNotificacaoNativa(title, body, path);
        }
    }

    @JavascriptInterface
    public void iniciarRastreioSegundoPlano(String userId, String apiToken) {
        if (!isOriginTrusted()) {
            Log.w(TAG, "iniciarRastreioSegundoPlano bloqueado: origem não confiável (" + currentUrl + ")");
            return;
        }

        // Android 14+ (API 34) lança SecurityException ao iniciar um Foreground Service do tipo
        // "location" sem a permissão de localização concedida no momento do start. Verificamos
        // antes de iniciar o serviço para evitar o crash; o web app deve garantir o grant primeiro.
        if (ContextCompat.checkSelfPermission(mContext, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "iniciarRastreioSegundoPlano bloqueado: ACCESS_FINE_LOCATION não concedida.");
            return;
        }

        Intent serviceIntent = new Intent(mContext, LocationService.class);
        serviceIntent.putExtra("USER_ID", userId);
        serviceIntent.putExtra("API_TOKEN", apiToken);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            mContext.startForegroundService(serviceIntent);
        } else {
            mContext.startService(serviceIntent);
        }

        // Fallback: aproveita a sessão recebida aqui para registrar o token FCM no
        // backend, caso o web app ainda não tenha chamado registrarTokenFCM(apiToken).
        FcmRegistrar.registrarComSessao(mContext, apiToken);
    }

    /**
     * Recebe a sessão do usuário (apiToken) após o login no web app e registra o
     * token FCM já obtido pelo dispositivo no backend. Deve ser chamado pelo web
     * app logo após o login: Android.registrarTokenFCM(apiToken).
     */
    @JavascriptInterface
    public void registrarTokenFCM(String apiToken) {
        if (!isOriginTrusted()) {
            Log.w(TAG, "registrarTokenFCM bloqueado: origem não confiável (" + currentUrl + ")");
            return;
        }
        FcmRegistrar.registrarComSessao(mContext, apiToken);
    }

    @JavascriptInterface
    public void pararRastreioSegundoPlano() {
        if (!isOriginTrusted()) {
            Log.w(TAG, "pararRastreioSegundoPlano bloqueado: origem não confiável (" + currentUrl + ")");
            return;
        }
        Intent serviceIntent = new Intent(mContext, LocationService.class);
        mContext.stopService(serviceIntent);
    }
}