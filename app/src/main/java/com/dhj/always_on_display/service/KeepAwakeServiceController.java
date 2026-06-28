package com.dhj.always_on_display.service;

import android.content.Context;
import android.content.Intent;

import androidx.core.content.ContextCompat;

import com.dhj.always_on_display.R;
import com.dhj.always_on_display.data.AppSelectorStore;
import com.dhj.always_on_display.logging.DebugLog;
import com.dhj.always_on_display.monitor.AccessibilityMonitor;
import com.dhj.always_on_display.monitor.OverlayPermissionMonitor;

public final class KeepAwakeServiceController {
    private KeepAwakeServiceController() {
    }

    public static boolean shouldServiceBeRunning(Context context) {
        Context appContext = context.getApplicationContext();
        return AppSelectorStore.isAppEnabled(appContext)
                && AccessibilityMonitor.isAccessibilityServiceEnabled(appContext)
                && OverlayPermissionMonitor.canDrawOverlays(appContext)
                && !AppSelectorStore.readSelectedPackages(appContext).isEmpty();
    }

    public static void syncService(Context context, String reason) {
        Context appContext = context.getApplicationContext();
        boolean appEnabled = AppSelectorStore.isAppEnabled(appContext);
        boolean accessibilityEnabled = AccessibilityMonitor.isAccessibilityServiceEnabled(appContext);
        boolean overlayEnabled = OverlayPermissionMonitor.canDrawOverlays(appContext);
        int selectedCount = AppSelectorStore.readSelectedPackages(appContext).size();
        boolean activeFlag = AppSelectorStore.isOverlayActive(appContext);
        boolean serviceRunning = KeepAwakeServiceStatus.isRunning(appContext);
        boolean shouldBeRunning = appEnabled && accessibilityEnabled && overlayEnabled && selectedCount > 0;

        DebugLog.i(appContext, "Sync keep-awake monitor: reason="
                + reason
                + ", appEnabled="
                + appEnabled
                + ", accessibility="
                + accessibilityEnabled
                + ", overlay="
                + overlayEnabled
                + ", selectedCount="
                + selectedCount
                + ", active="
                + activeFlag
                + ", serviceRunning="
                + serviceRunning
                + ", shouldBeRunning="
                + shouldBeRunning);

        if (shouldBeRunning) {
            if (serviceRunning) {
                if (!activeFlag) {
                    AppSelectorStore.setOverlayActive(appContext, true);
                }
                DebugLog.d(appContext, "Keep-awake monitor already running, skipping start request");
                return;
            }
            requestStart(appContext, reason);
        } else {
            if (!serviceRunning && !activeFlag) {
                DebugLog.d(appContext, "Keep-awake monitor already stopped, skipping stop request");
                return;
            }
            requestStop(appContext, reason);
        }
    }

    public static void refreshServiceState(Context context, String reason) {
        Context appContext = context.getApplicationContext();
        boolean appEnabled = AppSelectorStore.isAppEnabled(appContext);
        boolean accessibilityEnabled = AccessibilityMonitor.isAccessibilityServiceEnabled(appContext);
        boolean overlayEnabled = OverlayPermissionMonitor.canDrawOverlays(appContext);
        int selectedCount = AppSelectorStore.readSelectedPackages(appContext).size();
        boolean shouldBeRunning = appEnabled && accessibilityEnabled && overlayEnabled && selectedCount > 0;

        DebugLog.d(appContext, "Refresh keep-awake monitor: reason="
                + reason
                + ", appEnabled="
                + appEnabled
                + ", accessibility="
                + accessibilityEnabled
                + ", overlay="
                + overlayEnabled
                + ", selectedCount="
                + selectedCount
                + ", serviceRunning="
                + KeepAwakeServiceStatus.isRunning(appContext));

        if (shouldBeRunning) {
            requestStart(appContext, reason);
        } else {
            requestStop(appContext, reason);
        }
    }

    public static int getStatusLabelResId(Context context) {
        Context appContext = context.getApplicationContext();
        if (!AppSelectorStore.isAppEnabled(appContext)) {
            return R.string.monitor_status_disabled;
        }
        if (!AccessibilityMonitor.isAccessibilityServiceEnabled(appContext)) {
            return R.string.monitor_status_accessibility_required;
        }
        if (!OverlayPermissionMonitor.canDrawOverlays(appContext)) {
            return R.string.monitor_status_overlay_required;
        }
        if (AppSelectorStore.readSelectedPackages(appContext).isEmpty()) {
            return R.string.monitor_status_no_apps;
        }
        return AppSelectorStore.isOverlayActive(appContext)
                ? R.string.monitor_status_running
                : R.string.monitor_status_starting;
    }

    public static void setAppEnabled(Context context, boolean enabled, String reason) {
        Context appContext = context.getApplicationContext();
        AppSelectorStore.setAppEnabled(appContext, enabled);
        DebugLog.i(appContext, "Application enabled state changed: enabled=" + enabled + ", reason=" + reason);
        syncService(appContext, reason);
    }

    private static void requestStart(Context context, String reason) {
        try {
            KeepAwakeRestartScheduler.cancelRestart(context, "start_request:" + reason);
            ContextCompat.startForegroundService(
                    context,
                    new Intent(context, KeepAwakeOverlayService.class)
                            .setAction(KeepAwakeOverlayService.ACTION_START)
                            .putExtra("start_reason", reason)
            );
            AppSelectorStore.setOverlayActive(context, true);
            DebugLog.i(context, "Start request sent: reason=" + reason);
        } catch (RuntimeException e) {
            AppSelectorStore.setOverlayActive(context, false);
            DebugLog.e(context, "Failed to start keep-awake monitor: reason=" + reason, e);
        }
    }

    private static void requestStop(Context context, String reason) {
        KeepAwakeRestartScheduler.cancelRestart(context, "stop_request:" + reason);
        boolean stopped = context.stopService(new Intent(context, KeepAwakeOverlayService.class));
        AppSelectorStore.setOverlayActive(context, false);
        DebugLog.i(context, "Stop request processed: reason=" + reason + ", serviceStopped=" + stopped);
    }
}
