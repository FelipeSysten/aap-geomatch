package com.geomatch.app;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.location.Location;
import android.os.Build;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

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

    private static final String PREFS_NAME = "GeoMatchSession";
    private static final String KEY_USER_ID = "userId";
    private static final String KEY_API_TOKEN = "apiToken";

    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private String userId;
    private String apiToken;

    @Override
    public void onCreate() {
        super.onCreate();
        // Canal criado em onCreate() para garantir que exista antes do startForeground()
        criarCanalNotificacao();
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
        // A sessão (userId/apiToken) chega via iniciarRastreioSegundoPlano no primeiro start.
        // Persistimos em SharedPreferences para que um restart por START_STICKY — quando o
        // sistema reentrega o intent como null — ainda tenha os dados e continue enviando à API.
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        if (intent != null && intent.hasExtra("USER_ID")) {
            userId = intent.getStringExtra("USER_ID");
            prefs.edit().putString(KEY_USER_ID, userId).apply();
        } else {
            userId = prefs.getString(KEY_USER_ID, null);
        }

        if (intent != null && intent.hasExtra("API_TOKEN")) {
            apiToken = intent.getStringExtra("API_TOKEN");
            prefs.edit().putString(KEY_API_TOKEN, apiToken).apply();
        } else {
            apiToken = prefs.getString(KEY_API_TOKEN, null);
        }

        Notification notification = new NotificationCompat.Builder(this, "GeoMatchChannel")
                .setContentTitle("GeoMatch Ativo")
                .setContentText("Compartilhando localização em tempo real...")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .build();

        // Inicia o serviço em primeiro plano (obrigatório para não ser morto pelo Android).
        // A partir da API 29 é obrigatório declarar o tipo explicitamente; na API 35 isso
        // é aplicado de forma estrita — deve bater com foregroundServiceType no Manifest.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
        } else {
            startForeground(1, notification);
        }
        solicitarLocalizacao();

        return START_STICKY; // Reinicia o serviço se o sistema o matar por falta de memória
    }

    private void solicitarLocalizacao() {
        // Verificação explícita antes de chamar requestLocationUpdates().
        // Em dispositivos API 24-25, a ausência desse guarda pode causar crash
        // antes que o try-catch tenha chance de interceptar a SecurityException.
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            Log.e("GeoMatch_Service", "ACCESS_FINE_LOCATION não concedida. Serviço encerrado.");
            stopSelf();
            return;
        }

        LocationRequest locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000)
                .setMinUpdateIntervalMillis(5000)
                .build();

        try {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
        } catch (SecurityException e) {
            Log.e("GeoMatch_Service", "Permissão de localização revogada durante execução.", e);
            stopSelf();
        }
    }

    private void enviarParaAPI(double lat, double lng) {
        if (userId == null || apiToken == null) return;

        new Thread(() -> {
            try {
                URL url = new URL("https://geomatch-cvtv.onrender.com/users/update_location");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json; utf-8");
                conn.setRequestProperty("Accept", "application/json");
                conn.setRequestProperty("Authorization", "Bearer " + apiToken);
                conn.setDoOutput(true);

                String jsonInputString = String.format("{\"latitude\": %s, \"longitude\": %s}", lat, lng);

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