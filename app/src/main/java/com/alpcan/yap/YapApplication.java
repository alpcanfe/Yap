package com.alpcan.yap;

import android.Manifest;
import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.messaging.FirebaseMessaging;

public class YapApplication extends Application {
    private boolean permissionRequested = false;
    private static final String BELL_TAG = "yap_notification_bell";

    @Override
    public void onCreate() {
        super.onCreate();
        FirebaseMessaging.getInstance().getToken().addOnSuccessListener(token ->
                YapMessagingService.saveCurrentToken(this, token));

        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override public void onActivityCreated(Activity activity, Bundle state) {}
            @Override public void onActivityStarted(Activity activity) {}
            @Override public void onActivityResumed(Activity activity) {
                FirebaseMessaging.getInstance().getToken().addOnSuccessListener(token ->
                        YapMessagingService.saveCurrentToken(activity, token));
                if (!permissionRequested && Build.VERSION.SDK_INT >= 33
                        && activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    permissionRequested = true;
                    activity.requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1701);
                }
                if (activity instanceof MainActivityApp156) addOrRefreshBell(activity);
            }
            @Override public void onActivityPaused(Activity activity) {}
            @Override public void onActivityStopped(Activity activity) {}
            @Override public void onActivitySaveInstanceState(Activity activity, Bundle outState) {}
            @Override public void onActivityDestroyed(Activity activity) {}
        });
    }

    private void addOrRefreshBell(Activity activity) {
        FrameLayout root = activity.findViewById(android.R.id.content);
        if (root == null) return;
        TextView bell = root.findViewWithTag(BELL_TAG);
        if (bell == null) {
            bell = new TextView(activity);
            bell.setTag(BELL_TAG);
            bell.setText("🔔");
            bell.setTextColor(Color.WHITE);
            bell.setTextSize(17);
            bell.setGravity(Gravity.CENTER);
            bell.setPadding(dp(activity, 10), 0, dp(activity, 10), 0);
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(Color.rgb(39, 36, 58));
            bg.setCornerRadius(dp(activity, 14));
            bg.setStroke(dp(activity, 1), Color.argb(28, 255, 255, 255));
            bell.setBackground(bg);
            bell.setElevation(dp(activity, 8));
            bell.setOnClickListener(v -> activity.startActivity(new Intent(activity, NotificationCenterActivity.class)));
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(activity, 60), dp(activity, 42), Gravity.TOP | Gravity.END);
            lp.topMargin = dp(activity, 18);
            lp.rightMargin = dp(activity, 70);
            root.addView(bell, lp);
        }
        refreshUnread(bell);
    }

    private void refreshUnread(TextView bell) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            bell.setText("🔔");
            return;
        }
        FirebaseFirestore.getInstance().collection("notifications")
                .whereEqualTo("recipientUid", user.getUid()).get()
                .addOnSuccessListener(snapshot -> {
                    int unread = 0;
                    for (com.google.firebase.firestore.DocumentSnapshot doc : snapshot.getDocuments()) {
                        if (!Boolean.TRUE.equals(doc.getBoolean("read"))) unread++;
                    }
                    bell.setText(unread > 0 ? "🔔 " + (unread > 99 ? "99+" : unread) : "🔔");
                });
    }

    private int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
