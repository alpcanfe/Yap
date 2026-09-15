package com.alpcan.yap;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;

public class YapMessagingService extends FirebaseMessagingService {
    public static final String CHANNEL_ID = "yap_general";

    @Override
    public void onNewToken(String token) {
        super.onNewToken(token);
        saveToken(token);
    }

    @Override
    public void onMessageReceived(RemoteMessage message) {
        super.onMessageReceived(message);
        String title = "Yap!";
        String body = "Yeni bildirimin var.";
        if (message.getNotification() != null) {
            if (message.getNotification().getTitle() != null) title = message.getNotification().getTitle();
            if (message.getNotification().getBody() != null) body = message.getNotification().getBody();
        }
        if (message.getData().get("title") != null) title = message.getData().get("title");
        if (message.getData().get("body") != null) body = message.getData().get("body");
        showNotification(title, body, message.getData());
    }

    public static void saveCurrentToken(Context context, String token) {
        if (token == null || token.trim().isEmpty()) return;
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        Map<String, Object> device = new HashMap<>();
        device.put("token", token);
        device.put("platform", "android");
        device.put("updatedAt", FieldValue.serverTimestamp());
        device.put("appVersion", "1.7.0");
        db.collection("users").document(user.getUid()).collection("devices")
                .document(hashToken(token)).set(device, SetOptions.merge());
    }

    private void saveToken(String token) {
        saveCurrentToken(this, token);
    }

    private void showNotification(String title, String body, Map<String, String> data) {
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Yap! Bildirimleri", NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("Görev, mesaj, ekip ve hatırlatma bildirimleri");
            manager.createNotificationChannel(channel);
        }

        Intent intent = new Intent(this, MainActivityV156.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        if (data != null) {
            for (Map.Entry<String, String> e : data.entrySet()) intent.putExtra(e.getKey(), e.getValue());
        }
        PendingIntent pending = PendingIntent.getActivity(this, (int) System.currentTimeMillis(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pending);
        manager.notify((int) (System.currentTimeMillis() & 0x7fffffff), builder.build());
    }

    private static String hashToken(String token) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder b = new StringBuilder();
            for (byte value : bytes) b.append(String.format("%02x", value));
            return b.toString();
        } catch (Exception e) {
            return String.valueOf(token.hashCode());
        }
    }
}
