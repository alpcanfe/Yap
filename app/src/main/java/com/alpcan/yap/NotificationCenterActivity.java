package com.alpcan.yap;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class NotificationCenterActivity extends Activity {
    private static final int BG = Color.rgb(17, 16, 26);
    private static final int CARD = Color.rgb(29, 27, 42);
    private static final int CARD_UNREAD = Color.rgb(44, 37, 74);
    private static final int PURPLE = Color.rgb(124, 58, 237);
    private static final int MUTED = Color.rgb(170, 165, 189);
    private LinearLayout list;
    private TextView empty;
    private FirebaseFirestore db;
    private FirebaseUser user;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        db = FirebaseFirestore.getInstance();
        user = FirebaseAuth.getInstance().getCurrentUser();
        buildUi();
        loadNotifications();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (list != null) loadNotifications();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(28), dp(18), dp(36));
        scroll.addView(root, new ScrollView.LayoutParams(-1, -2));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(top, new LinearLayout.LayoutParams(-1, -2));

        Button back = new Button(this);
        back.setText("‹");
        back.setTextColor(Color.WHITE);
        back.setTextSize(26);
        back.setAllCaps(false);
        back.setBackground(rounded(CARD, dp(14)));
        back.setOnClickListener(v -> finish());
        LinearLayout.LayoutParams backParams = new LinearLayout.LayoutParams(dp(50), dp(48));
        backParams.setMarginEnd(dp(12));
        top.addView(back, backParams);

        TextView title = new TextView(this);
        title.setText("Bildirim Merkezi");
        title.setTextColor(Color.WHITE);
        title.setTextSize(23);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        top.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));

        Button readAll = new Button(this);
        readAll.setText("Tümünü okundu yap");
        readAll.setTextColor(Color.WHITE);
        readAll.setTextSize(12);
        readAll.setAllCaps(false);
        readAll.setBackground(rounded(PURPLE, dp(14)));
        readAll.setOnClickListener(v -> markAllRead());
        top.addView(readAll, new LinearLayout.LayoutParams(-2, dp(48)));

        TextView sub = new TextView(this);
        sub.setText("Görevler, mesajlar, ekip hareketleri ve hatırlatmalar burada saklanır.");
        sub.setTextColor(MUTED);
        sub.setTextSize(13);
        LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(-1, -2);
        subParams.topMargin = dp(12);
        subParams.bottomMargin = dp(14);
        root.addView(sub, subParams);

        empty = new TextView(this);
        empty.setText("Henüz bildirimin yok.");
        empty.setTextColor(MUTED);
        empty.setTextSize(15);
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(dp(18), dp(44), dp(18), dp(44));
        empty.setBackground(rounded(CARD, dp(18)));
        root.addView(empty, new LinearLayout.LayoutParams(-1, -2));

        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        root.addView(list, new LinearLayout.LayoutParams(-1, -2));

        setContentView(scroll);
    }

    private void loadNotifications() {
        if (user == null) {
            empty.setText("Bildirimleri görmek için giriş yapmalısın.");
            empty.setVisibility(View.VISIBLE);
            return;
        }
        db.collection("notifications").whereEqualTo("recipientUid", user.getUid()).get()
                .addOnSuccessListener(snapshot -> {
                    List<DocumentSnapshot> docs = new ArrayList<>(snapshot.getDocuments());
                    docs.sort(Comparator.comparingLong(this::createdAtMillis).reversed());
                    render(docs);
                })
                .addOnFailureListener(e -> {
                    empty.setText("Bildirimler yüklenemedi.");
                    empty.setVisibility(View.VISIBLE);
                });
    }

    private void render(List<DocumentSnapshot> docs) {
        list.removeAllViews();
        empty.setVisibility(docs.isEmpty() ? View.VISIBLE : View.GONE);
        for (DocumentSnapshot doc : docs) list.addView(notificationCard(doc));
    }

    private View notificationCard(DocumentSnapshot doc) {
        Map<String, Object> data = doc.getData() == null ? new HashMap<>() : doc.getData();
        boolean read = Boolean.TRUE.equals(data.get("read"));
        String title = text(data.get("title"), "Yap!");
        String body = text(data.get("body"), "");
        String type = text(data.get("type"), "general");

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(15), dp(14), dp(13), dp(13));
        card.setBackground(rounded(read ? CARD : CARD_UNREAD, dp(18)));
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(-1, -2);
        cardParams.bottomMargin = dp(9);
        card.setLayoutParams(cardParams);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        card.addView(header, new LinearLayout.LayoutParams(-1, -2));

        TextView icon = new TextView(this);
        icon.setText(iconFor(type));
        icon.setTextSize(22);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(38), -2);
        iconParams.setMarginEnd(dp(7));
        header.addView(icon, iconParams);

        TextView titleView = new TextView(this);
        titleView.setText(title);
        titleView.setTextColor(Color.WHITE);
        titleView.setTextSize(15);
        titleView.setTypeface(Typeface.DEFAULT, read ? Typeface.NORMAL : Typeface.BOLD);
        header.addView(titleView, new LinearLayout.LayoutParams(0, -2, 1f));

        Button delete = new Button(this);
        delete.setText("×");
        delete.setTextColor(Color.rgb(251, 113, 133));
        delete.setTextSize(20);
        delete.setAllCaps(false);
        delete.setBackgroundColor(Color.TRANSPARENT);
        delete.setOnClickListener(v -> doc.getReference().delete().addOnSuccessListener(x -> loadNotifications()));
        header.addView(delete, new LinearLayout.LayoutParams(dp(46), dp(42)));

        TextView bodyView = new TextView(this);
        bodyView.setText(body);
        bodyView.setTextColor(Color.rgb(231, 228, 242));
        bodyView.setTextSize(14);
        bodyView.setLineSpacing(0f, 1.12f);
        LinearLayout.LayoutParams bodyParams = new LinearLayout.LayoutParams(-1, -2);
        bodyParams.leftMargin = dp(45);
        bodyParams.rightMargin = dp(8);
        bodyParams.topMargin = dp(5);
        card.addView(bodyView, bodyParams);

        TextView time = new TextView(this);
        time.setText(formatDate(createdAtMillis(doc)) + (read ? "" : "  •  Okunmadı"));
        time.setTextColor(read ? MUTED : Color.rgb(189, 156, 255));
        time.setTextSize(11);
        LinearLayout.LayoutParams timeParams = new LinearLayout.LayoutParams(-1, -2);
        timeParams.leftMargin = dp(45);
        timeParams.topMargin = dp(8);
        card.addView(time, timeParams);

        card.setOnClickListener(v -> {
            if (!read) {
                Map<String, Object> change = new HashMap<>();
                change.put("read", true);
                change.put("readAt", FieldValue.serverTimestamp());
                doc.getReference().update(change).addOnSuccessListener(x -> loadNotifications());
            }
        });
        return card;
    }

    private void markAllRead() {
        if (user == null) return;
        db.collection("notifications").whereEqualTo("recipientUid", user.getUid()).get()
                .addOnSuccessListener(snapshot -> {
                    com.google.firebase.firestore.WriteBatch batch = db.batch();
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        if (!Boolean.TRUE.equals(doc.getBoolean("read"))) {
                            batch.update(doc.getReference(), "read", true, "readAt", FieldValue.serverTimestamp());
                        }
                    }
                    batch.commit().addOnSuccessListener(v -> loadNotifications());
                });
    }

    private long createdAtMillis(DocumentSnapshot doc) {
        com.google.firebase.Timestamp t = doc.getTimestamp("createdAt");
        return t == null ? 0L : t.toDate().getTime();
    }

    private String formatDate(long millis) {
        if (millis <= 0) return "Şimdi";
        return new SimpleDateFormat("dd MMM HH:mm", new Locale("tr", "TR")).format(new Date(millis));
    }

    private String iconFor(String type) {
        if (type.contains("message") || type.equals("mention")) return "💬";
        if (type.contains("urgent")) return "🚨";
        if (type.contains("overdue") || type.contains("deadline")) return "⏰";
        if (type.contains("member") || type.contains("role") || type.contains("permission")) return "👥";
        if (type.contains("invite")) return "✉️";
        if (type.contains("summary")) return "📊";
        return "✓";
    }

    private String text(Object value, String fallback) {
        String s = value == null ? "" : String.valueOf(value);
        return s.trim().isEmpty() ? fallback : s;
    }

    private GradientDrawable rounded(int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radius);
        return d;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
