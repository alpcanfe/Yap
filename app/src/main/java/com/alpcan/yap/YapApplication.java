package com.alpcan.yap;

import android.Manifest;
import android.app.Activity;
import android.app.Application;
import android.os.Build;
import android.os.Bundle;
import android.content.pm.PackageManager;

import com.google.firebase.messaging.FirebaseMessaging;

public class YapApplication extends Application {
    private boolean permissionRequested = false;

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
            }
            @Override public void onActivityPaused(Activity activity) {}
            @Override public void onActivityStopped(Activity activity) {}
            @Override public void onActivitySaveInstanceState(Activity activity, Bundle outState) {}
            @Override public void onActivityDestroyed(Activity activity) {}
        });
    }
}
