package com.geomatch.app;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

/**
 * Serviço de recebimento de mensagens do Firebase Cloud Messaging (FCM).
 *
 * Integração isolada: NÃO interfere no WebView, permissões ou nas notificações
 * nativas já existentes na MainActivity. Apenas trata o token do dispositivo e
 * exibe notificações locais para mensagens recebidas via FCM.
 */
public class MyFirebaseMessagingService extends FirebaseMessagingService {

    private static final String TAG = "GeoMatch_FCM";

    // Canal dedicado ao FCM para manter a integração isolada dos demais canais.
    private static final String FCM_CHANNEL_ID = "geomatch_fcm_channel";

    /**
     * Chamado quando um novo token de registro do FCM é gerado (primeira execução,
     * reinstalação, limpeza de dados ou rotação de token pelo próprio Firebase).
     */
    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);
        Log.d(TAG, "Novo token FCM gerado: " + token);

        // Persiste o token e o registra no backend se já houver sessão ativa.
        FcmRegistrar.salvarERegistrar(getApplicationContext(), token);
    }

    /**
     * Chamado quando uma mensagem FCM é recebida com o app em primeiro plano
     * (ou sempre, no caso de mensagens do tipo "data").
     */
    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);

        String title = null;
        String body = null;

        // Extrai título e corpo do payload de notificação do FCM.
        RemoteMessage.Notification notification = remoteMessage.getNotification();
        if (notification != null) {
            title = notification.getTitle();
            body = notification.getBody();
        }

        Log.d(TAG, "Mensagem FCM recebida | title=" + title + " | body=" + body);

        exibirNotificacao(title, body);
    }

    /**
     * Cria e exibe uma notificação local a partir do conteúdo recebido via FCM.
     */
    private void exibirNotificacao(String title, String body) {
        // Cria o canal de notificação em Android O+ (obrigatório a partir da API 26).
        criarCanalDeNotificacao();

        // Abre a MainActivity ao tocar na notificação.
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, FCM_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(title != null ? title : getString(R.string.app_name))
                .setContentText(body)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
        try {
            notificationManager.notify((int) System.currentTimeMillis(), builder.build());
        } catch (SecurityException e) {
            // POST_NOTIFICATIONS pode não ter sido concedida (Android 13+).
            Log.w(TAG, "Sem permissão para exibir notificação FCM", e);
        }
    }

    private void criarCanalDeNotificacao() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    FCM_CHANNEL_ID,
                    "Notificações Push do GeoMatch",
                    NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("Canal para notificações push recebidas via Firebase.");

            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }
}
