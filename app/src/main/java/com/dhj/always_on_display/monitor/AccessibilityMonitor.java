package com.dhj.always_on_display.monitor;

import android.content.ComponentName;
import android.content.Context;
import android.provider.Settings;
import android.text.TextUtils;

import androidx.annotation.NonNull;

import com.dhj.always_on_display.logging.DebugLog;
import com.dhj.always_on_display.service.ForegroundTrackingAccessibilityService;

public final class AccessibilityMonitor {
    private AccessibilityMonitor() {
    }

    public static boolean isAccessibilityServiceEnabled(@NonNull Context context) {
        int accessibilityEnabled = 0;
        try {
            accessibilityEnabled = Settings.Secure.getInt(
                    context.getContentResolver(),
                    Settings.Secure.ACCESSIBILITY_ENABLED
            );
        } catch (Settings.SettingNotFoundException e) {
            DebugLog.w(context, "Accessibility enabled setting not found");
        }

        if (accessibilityEnabled != 1) {
            DebugLog.d(context, "Accessibility is disabled globally");
            return false;
        }

        ComponentName componentName = new ComponentName(context, ForegroundTrackingAccessibilityService.class);
        String shortId = componentName.flattenToShortString();
        String fullId = componentName.flattenToString();
        String enabledServices = Settings.Secure.getString(
                context.getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        );
        if (enabledServices == null || enabledServices.isEmpty()) {
            DebugLog.d(context, "No enabled accessibility services were found");
            return false;
        }

        for (String serviceId : enabledServices.split(":")) {
            if (TextUtils.equals(serviceId, shortId) || TextUtils.equals(serviceId, fullId)) {
                return true;
            }
        }

        DebugLog.d(context, "Target accessibility service is not enabled");
        return false;
    }
}
