package com.dhj.always_on_display.model;

import android.graphics.drawable.Drawable;

public final class AppInfo {
    private final String appName;
    private final String packageName;
    private final Drawable icon;
    private final boolean systemApp;

    public AppInfo(String appName, String packageName, Drawable icon, boolean systemApp) {
        this.appName = appName;
        this.packageName = packageName;
        this.icon = icon;
        this.systemApp = systemApp;
    }

    public String getAppName() {
        return appName;
    }

    public String getPackageName() {
        return packageName;
    }

    public Drawable getIcon() {
        return icon;
    }

    public boolean isSystemApp() {
        return systemApp;
    }
}
