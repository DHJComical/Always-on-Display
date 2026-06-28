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
import android.os.IBinder;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;

import androidx.core.app.NotificationCompat;

import com.dhj.always_on_display.data.AppSelectorStore;
import com.dhj.always_on_display.data.ForegroundAppStore;
import com.dhj.always_on_display.logging.DebugLog;
import com.dhj.always_on_display.monitor.OverlayPermissionMonitor;
import com.dhj.always_on_display.ui.activity.MainActivity;

import java.util.Set;

public class KeepAwakeOverlayService extends Service {
    public static final String ACTION_START = "com.dhj.always_on_display.action.START_KEEP_AWAKE_OVERLAY";
    public static final String ACTION_STOP = "com.dhj.always_on_display.action.STOP_KEEP_AWAKE_OVERLAY";

    private static final long FOREGROUND_EVENT_FRESHNESS_MS = 10_000L;
    private static final String NOTIFICATION_CHANNEL_ID = "keep_awake_monitor";
    private static final int NOTIFICATION_ID = 1001;

    private WindowManager windowManager;
    private View overlayView;
    private String lastLoggedForegroundPackage;
    private boolean lastLoggedShouldKeepAwake;
    private int lastLoggedSelectionHash;
    private boolean hasLoggedDecision;
    private boolean stopRequested;

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
            stopRequested = true;
            stopOverlayWork();
            return START_NOT_STICKY;
        }
        stopRequested = false;

        if (!KeepAwakeServiceController.shouldServiceBeRunning(this)) {
            DebugLog.w(this, "Cannot start keep-awake monitor because accessibility scope is not ready");
            removeOverlay();
            AppSelectorStore.setOverlayActive(this, false);
            stopSelf();
            return START_NOT_STICKY;
        }

        startInForeground();
        KeepAwakeRestartScheduler.cancelRestart(this, "service_started");
        DebugLog.i(this, "Keep-awake monitor started with "
                + AppSelectorStore.readSelectedPackages(this).size()
                + " selected packages");
        AppSelectorStore.setOverlayActive(this, true);
        String reason = intent == null ? "service_start" : intent.getStringExtra("start_reason");
        refreshOverlayState(reason == null ? "service_start" : reason);
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        DebugLog.i(this, "Service destroyed");
        removeOverlay();
        AppSelectorStore.setOverlayActive(this, false);
        maybeScheduleRestartOnDestroy();
        super.onDestroy();
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        DebugLog.w(this, "Task removed, scheduling monitor restart");
        KeepAwakeRestartScheduler.scheduleRestart(this, "task_removed");
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void refreshOverlayState(String reason) {
        if (!KeepAwakeServiceController.shouldServiceBeRunning(this)) {
            DebugLog.w(this, "Stopping keep-awake monitor because required scope is no longer valid");
            removeOverlay();
            AppSelectorStore.setOverlayActive(this, false);
            stopSelf();
            return;
        }

        if (!OverlayPermissionMonitor.canDrawOverlays(this)) {
            DebugLog.w(this, "Stopping keep-awake monitor because overlay permission is missing");
            removeOverlay();
            AppSelectorStore.setOverlayActive(this, false);
            stopSelf();
            return;
        }

        Set<String> selectedPackages = AppSelectorStore.readSelectedPackages(this);
        String foregroundPackage = ForegroundAppStore.readForegroundPackage(this);
        long lastEventUptimeMillis = ForegroundAppStore.readLastEventUptimeMillis(this);
        long nowUptimeMillis = android.os.SystemClock.uptimeMillis();
        boolean foregroundFresh = nowUptimeMillis - lastEventUptimeMillis <= FOREGROUND_EVENT_FRESHNESS_MS;
        if (!foregroundFresh) {
            foregroundPackage = null;
        }

        boolean shouldKeepAwake = foregroundPackage != null && selectedPackages.contains(foregroundPackage);
        logDecisionIfNeeded(
                selectedPackages,
                foregroundPackage,
                shouldKeepAwake,
                reason,
                lastEventUptimeMillis,
                nowUptimeMillis,
                foregroundFresh
        );

        if (shouldKeepAwake) {
            showOverlay();
        } else {
            removeOverlay();
        }
    }

    private void showOverlay() {
        if (overlayView != null) {
            return;
        }

        windowManager = getSystemService(WindowManager.class);
        if (windowManager == null) {
            DebugLog.w(this, "WindowManager unavailable, cannot attach overlay");
            return;
        }

        View view = new View(this);
        view.setBackgroundColor(Color.TRANSPARENT);
        view.setKeepScreenOn(true);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                1,
                1,
                getOverlayWindowType(),
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                        | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.START | Gravity.TOP;
        params.alpha = 0.01f;
        params.setTitle(getPackageName() + ":keep-awake-overlay");

        try {
            windowManager.addView(view, params);
            overlayView = view;
            DebugLog.i(this, "Overlay attached with keep-screen-on flag");
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
        removeOverlay();
        AppSelectorStore.setOverlayActive(this, false);
        hasLoggedDecision = false;
        lastLoggedForegroundPackage = null;
        lastLoggedShouldKeepAwake = false;
        lastLoggedSelectionHash = 0;
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    private void maybeScheduleRestartOnDestroy() {
        hasLoggedDecision = false;
        lastLoggedForegroundPackage = null;
        lastLoggedShouldKeepAwake = false;
        lastLoggedSelectionHash = 0;

        if (stopRequested) {
            DebugLog.i(this, "Skipping destroy restart scheduling because stop was requested explicitly");
            return;
        }

        if (!KeepAwakeServiceController.shouldServiceBeRunning(this)) {
            DebugLog.i(this, "Skipping destroy restart scheduling because monitor conditions are no longer satisfied");
            return;
        }

        DebugLog.w(this, "Service destroyed unexpectedly, scheduling restart fallback");
        KeepAwakeRestartScheduler.scheduleRestart(this, "service_destroyed");
    }

    private void logDecisionIfNeeded(
            Set<String> selectedPackages,
            String foregroundPackage,
            boolean shouldKeepAwake,
            String reason,
            long lastEventUptimeMillis,
            long nowUptimeMillis,
            boolean foregroundFresh
    ) {
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
                + shouldKeepAwake
                + ", reason="
                + reason
                + ", lastEventUptimeMillis="
                + lastEventUptimeMillis
                + ", nowUptimeMillis="
                + nowUptimeMillis
                + ", foregroundFresh="
                + foregroundFresh);
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
        Intent stopIntent = new Intent(this, KeepAwakeOverlayService.class)
                .setAction(ACTION_STOP);
        PendingIntent contentIntent = PendingIntent.getActivity(
                this,
                0,
                notificationIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        PendingIntent stopPendingIntent = PendingIntent.getService(
                this,
                1,
                stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        return new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(com.dhj.always_on_display.R.drawable.ic_check_circle_24)
                .setContentTitle(getString(com.dhj.always_on_display.R.string.foreground_service_notification_title))
                .setContentText(getString(com.dhj.always_on_display.R.string.foreground_service_notification_text))
                .setContentIntent(contentIntent)
                .addAction(
                        0,
                        getString(com.dhj.always_on_display.R.string.foreground_service_notification_action_stop),
                        stopPendingIntent
                )
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
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
