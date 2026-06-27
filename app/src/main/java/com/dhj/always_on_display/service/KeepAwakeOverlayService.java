package com.dhj.always_on_display.service;

import android.app.Service;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;

import com.dhj.always_on_display.data.AppSelectorStore;
import com.dhj.always_on_display.monitor.ForegroundAppMonitor;

import java.util.Set;

public class KeepAwakeOverlayService extends Service {
    public static final String ACTION_START = "com.dhj.always_on_display.action.START_KEEP_AWAKE_OVERLAY";
    public static final String ACTION_STOP = "com.dhj.always_on_display.action.STOP_KEEP_AWAKE_OVERLAY";

    private static final long CHECK_INTERVAL_MS = 1200L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable foregroundCheck = this::refreshOverlayState;

    private WindowManager windowManager;
    private View overlayView;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? ACTION_START : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            stopOverlayWork();
            return START_NOT_STICKY;
        }

        if (!Settings.canDrawOverlays(this) || !ForegroundAppMonitor.hasUsageAccess(this)) {
            AppSelectorStore.setOverlayActive(this, false);
            stopSelf();
            return START_NOT_STICKY;
        }

        AppSelectorStore.setOverlayActive(this, true);
        handler.removeCallbacks(foregroundCheck);
        handler.post(foregroundCheck);
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        stopOverlayWork();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void refreshOverlayState() {
        if (!Settings.canDrawOverlays(this) || !ForegroundAppMonitor.hasUsageAccess(this)) {
            removeOverlay();
            AppSelectorStore.setOverlayActive(this, false);
            stopSelf();
            return;
        }

        Set<String> selectedPackages = AppSelectorStore.readSelectedPackages(this);
        String foregroundPackage = ForegroundAppMonitor.getForegroundPackageName(this);
        boolean shouldKeepAwake = foregroundPackage != null && selectedPackages.contains(foregroundPackage);

        if (shouldKeepAwake) {
            showOverlay();
        } else {
            removeOverlay();
        }

        handler.postDelayed(foregroundCheck, CHECK_INTERVAL_MS);
    }

    private void showOverlay() {
        if (overlayView != null) {
            return;
        }

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        if (windowManager == null) {
            return;
        }

        overlayView = new View(this);
        overlayView.setBackgroundColor(Color.TRANSPARENT);
        overlayView.setKeepScreenOn(true);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                1,
                1,
                getOverlayWindowType(),
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                        | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.START | Gravity.TOP;
        params.alpha = 0.01f;

        try {
            windowManager.addView(overlayView, params);
        } catch (RuntimeException e) {
            overlayView = null;
        }
    }

    private void removeOverlay() {
        if (windowManager != null && overlayView != null) {
            try {
                windowManager.removeView(overlayView);
            } catch (RuntimeException ignored) {
            }
        }
        overlayView = null;
    }

    private void stopOverlayWork() {
        handler.removeCallbacks(foregroundCheck);
        removeOverlay();
        AppSelectorStore.setOverlayActive(this, false);
        stopSelf();
    }

    @SuppressWarnings("deprecation")
    private int getOverlayWindowType() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        }
        return WindowManager.LayoutParams.TYPE_PHONE;
    }
}
