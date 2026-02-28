package com.geomatch.app;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.webkit.JavascriptInterface;

public class WebAppInterface {
    Context mContext;

    WebAppInterface(Context c) {
        mContext = c;
    }

    // O Javascript no Rails vai chamar essa função passando o ID do usuário
    @JavascriptInterface
    public void iniciarRastreioSegundoPlano(String userId) {
        Intent serviceIntent = new Intent(mContext, LocationService.class);
        serviceIntent.putExtra("USER_ID", userId);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            mContext.startForegroundService(serviceIntent);
        } else {
            mContext.startService(serviceIntent);
        }
    }

    @JavascriptInterface
    public void pararRastreioSegundoPlano() {
        Intent serviceIntent = new Intent(mContext, LocationService.class);
        mContext.stopService(serviceIntent);
    }
}