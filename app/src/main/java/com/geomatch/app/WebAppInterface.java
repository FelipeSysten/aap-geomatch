package com.geomatch.app;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.util.Log;
import android.webkit.JavascriptInterface;

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
        Intent serviceIntent = new Intent(mContext, LocationService.class);
        serviceIntent.putExtra("USER_ID", userId);
        serviceIntent.putExtra("API_TOKEN", apiToken);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            mContext.startForegroundService(serviceIntent);
        } else {
            mContext.startService(serviceIntent);
        }
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