package com.geomatch.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.location.Location;
import android.os.Build;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class LocationService extends Service {

    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private String userId; // Receberemos isso da WebView

    @Override
    public void onCreate() {
        super.onCreate();
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (locationResult == null) return;
                for (Location location : locationResult.getLocations()) {
                    // Aqui pegamos a localização! Agora enviamos para o Rails.
                    enviarParaAPI(location.getLatitude(), location.getLongitude());
                }
            }
        };
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.hasExtra("USER_ID")) {
            userId = intent.getStringExtra("USER_ID");
        }

        criarCanalNotificacao();
        Notification notification = new NotificationCompat.Builder(this, "GeoMatchChannel")
                .setContentTitle("GeoMatch Ativo")
                .setContentText("Compartilhando localização em tempo real...")
                .setSmallIcon(android.R.drawable.ic_menu_mylocation) // Troque pelo ícone do seu app
                .build();

        // Inicia o serviço em primeiro plano (obrigatório para não ser morto pelo Android)
        startForeground(1, notification);
        solicitarLocalizacao();

        return START_STICKY; // Reinicia o serviço se o sistema o matar por falta de memória
    }

    private void solicitarLocalizacao() {
        LocationRequest locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000) // Pega a cada 10 segundos
                .setMinUpdateIntervalMillis(5000)
                .build();

        try {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
        } catch (SecurityException e) {
            Log.e("GeoMatch", "Permissão de localização negada", e);
        }
    }

    private void enviarParaAPI(double lat, double lng) {
        if (userId == null) return;

        // Executa a requisição HTTP em uma thread separada (não pode rodar na principal)
        new Thread(() -> {
            try {
                URL url = new URL("https://geomatch-cvtv.onrender.com/api/atualizar_localizacao");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json; utf-8");
                conn.setRequestProperty("Accept", "application/json");
                conn.setDoOutput(true);

                String jsonInputString = String.format("{\"user_id\": \"%s\", \"latitude\": %s, \"longitude\": %s}", userId, lat, lng);

                try (OutputStream os = conn.getOutputStream()) {
                    byte[] input = jsonInputString.getBytes("utf-8");
                    os.write(input, 0, input.length);
                }

                int code = conn.getResponseCode();
                Log.d("GeoMatch", "Resposta da API: " + code);
                conn.disconnect();
            } catch (Exception e) {
                Log.e("GeoMatch", "Erro ao enviar para API", e);
            }
        }).start();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (fusedLocationClient != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void criarCanalNotificacao() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    "GeoMatchChannel",
                    "Canal de Localização GeoMatch",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
            }
        }
    }
}