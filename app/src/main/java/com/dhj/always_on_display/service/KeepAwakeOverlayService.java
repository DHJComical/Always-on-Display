package com.dhj.always_on_display.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;

import com.dhj.always_on_display.data.AppSelectorStore;
import com.dhj.always_on_display.logging.DebugLog;
import com.dhj.always_on_display.monitor.ForegroundAppMonitor;
import com.dhj.always_on_display.ui.activity.MainActivity;

import androidx.core.app.NotificationCompat;

import java.util.Set;

public class KeepAwakeOverlayService extends Service {
    public static final String ACTION_START = "com.dhj.always_on_display.action.START_KEEP_AWAKE_OVERLAY";
    public static final String ACTION_STOP = "com.dhj.always_on_display.action.STOP_KEEP_AWAKE_OVERLAY";

    private static final long CHECK_INTERVAL_MS = 1200L;
    private static final String NOTIFICATION_CHANNEL_ID = "keep_awake_monitor";
    private static final int NOTIFICATION_ID = 1001;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable foregroundCheck = this::refreshOverlayState;

    private WindowManager windowManager;
    private View overlayView;
    private String lastLoggedForegroundPackage;
    private boolean lastLoggedShouldKeepAwake;
    private int lastLoggedSelectionHash;
    private boolean hasLoggedDecision;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        DebugLog.i(this, "Service created");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? ACTION_START : intent.getAction();
        DebugLog.i(this, "Service onStartCommand: action=" + action + ", startId=" + startId);
        if (ACTION_STOP.equals(action)) {
            DebugLog.i(this, "Received stop action");
            stopOverlayWork();
            return START_NOT_STICKY;
        }

        boolean overlayGranted = Settings.canDrawOverlays(this);
        boolean usageGranted = ForegroundAppMonitor.hasUsageAccess(this);
        if (!overlayGranted || !usageGranted) {
            DebugLog.w(this, "Cannot start compatibility mode: overlay="
                    + overlayGranted
                    + ", usageAccess="
                    + usageGranted);
            AppSelectorStore.setOverlayActive(this, false);
            stopSelf();
            return START_NOT_STICKY;
        }

        startInForeground();
        DebugLog.i(this, "Compatibility mode started with "
                + AppSelectorStore.readSelectedPackages(this).size()
                + " selected packages");
        AppSelectorStore.setOverlayActive(this, true);
        handler.removeCallbacks(foregroundCheck);
        handler.post(foregroundCheck);
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        DebugLog.i(this, "Service destroyed");
        stopOverlayWork();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void refreshOverlayState() {
        boolean overlayGranted = Settings.canDrawOverlays(this);
        boolean usageGranted = ForegroundAppMonitor.hasUsageAccess(this);
        if (!overlayGranted || !usageGranted) {
            DebugLog.w(this, "Stopping compatibility mode because required permissions are missing: overlay="
                    + overlayGranted
                    + ", usageAccess="
                    + usageGranted);
            removeOverlay();
            AppSelectorStore.setOverlayActive(this, false);
            stopSelf();
            return;
        }

        Set<String> selectedPackages = AppSelectorStore.readSelectedPackages(this);
        String foregroundPackage = ForegroundAppMonitor.getForegroundPackageName(this);
        boolean shouldKeepAwake = foregroundPackage != null && selectedPackages.contains(foregroundPackage);
        logDecisionIfNeeded(selectedPackages, foregroundPackage, shouldKeepAwake);

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
            DebugLog.w(this, "WindowManager unavailable, cannot attach overlay");
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
            DebugLog.i(this, "Overlay attached");
        } catch (RuntimeException e) {
            DebugLog.e(this, "Failed to attach overlay", e);
            overlayView = null;
        }
    }

    private void removeOverlay() {
        if (windowManager != null && overlayView != null) {
            try {
                windowManager.removeView(overlayView);
                DebugLog.i(this, "Overlay removed");
            } catch (RuntimeException e) {
                DebugLog.w(this, "Overlay remove failed because view was already detached: " + e.getMessage());
            }
        }
        overlayView = null;
    }

    private void stopOverlayWork() {
        handler.removeCallbacks(foregroundCheck);
        removeOverlay();
        AppSelectorStore.setOverlayActive(this, false);
        hasLoggedDecision = false;
        lastLoggedForegroundPackage = null;
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    private void logDecisionIfNeeded(Set<String> selectedPackages, String foregroundPackage, boolean shouldKeepAwake) {
        int selectionHash = selectedPackages.hashCode();
        boolean changed = !hasLoggedDecision
                || shouldKeepAwake != lastLoggedShouldKeepAwake
                || selectionHash != lastLoggedSelectionHash
                || !TextUtils.equals(foregroundPackage, lastLoggedForegroundPackage);
        if (!changed) {
            return;
        }

        DebugLog.d(this, "Refresh decision: foreground="
                + (foregroundPackage == null ? "<none>" : foregroundPackage)
                + ", selectedCount="
                + selectedPackages.size()
                + ", overlayAttached="
                + (overlayView != null)
                + ", shouldKeepAwake="
                + shouldKeepAwake);
        lastLoggedForegroundPackage = foregroundPackage;
        lastLoggedShouldKeepAwake = shouldKeepAwake;
        lastLoggedSelectionHash = selectionHash;
        hasLoggedDecision = true;
    }

    @SuppressWarnings("deprecation")
    private int getOverlayWindowType() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        }
        return WindowManager.LayoutParams.TYPE_PHONE;
    }

    private void startInForeground() {
        Notification notification = buildNotification();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            );
            return;
        }
        startForeground(NOTIFICATION_ID, notification);
    }

    private Notification buildNotification() {
        Intent notificationIntent = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent contentIntent = PendingIntent.getActivity(
                this,
                0,
                notificationIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        return new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(com.dhj.always_on_display.R.drawable.ic_check_circle_24)
                .setContentTitle(getString(com.dhj.always_on_display.R.string.foreground_service_notification_title))
                .setContentText(getString(com.dhj.always_on_display.R.string.foreground_service_notification_text))
                .setContentIntent(contentIntent)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }

        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        if (notificationManager == null) {
            DebugLog.w(this, "NotificationManager unavailable, foreground notification channel not created");
            return;
        }

        NotificationChannel channel = new NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                getString(com.dhj.always_on_display.R.string.foreground_service_channel_name),
                NotificationManager.IMPORTANCE_LOW
        );
        channel.setDescription(getString(com.dhj.always_on_display.R.string.foreground_service_channel_description));
        notificationManager.createNotificationChannel(channel);
    }
}
