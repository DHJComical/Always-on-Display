package com.dhj.always_on_display.ui.fragment;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.dhj.always_on_display.R;
import com.dhj.always_on_display.data.AppSelectorStore;
import com.dhj.always_on_display.logging.DebugLog;
import com.dhj.always_on_display.monitor.AccessibilityMonitor;
import com.dhj.always_on_display.monitor.OverlayPermissionMonitor;
import com.dhj.always_on_display.service.KeepAwakeServiceController;
import com.dhj.always_on_display.system.BackgroundLaunchHelper;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.materialswitch.MaterialSwitch;

public class SettingsFragment extends Fragment {
    private TextView overlayPermissionStatus;
    private TextView accessibilityPermissionStatus;
    private TextView notificationPermissionStatus;
    private TextView exactAlarmPermissionStatus;
    private TextView batteryOptimizationStatus;
    private TextView monitorStatus;
    private MaterialSwitch debugLoggingSwitch;
    private final ActivityResultLauncher<String> notificationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                KeepAwakeServiceController.syncService(requireContext(), "notification_permission_result");
                updateStatus();
            });

    public SettingsFragment() {
        super(R.layout.fragment_settings);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        bindViews(view);
        setupActions(view);
        KeepAwakeServiceController.syncService(requireContext(), "settings_view_created");
        updateStatus();
    }

    @Override
    public void onResume() {
        super.onResume();
        KeepAwakeServiceController.syncService(requireContext(), "settings_resume");
        updateStatus();
    }

    private void bindViews(@NonNull View view) {
        overlayPermissionStatus = view.findViewById(R.id.overlayPermissionStatus);
        accessibilityPermissionStatus = view.findViewById(R.id.accessibilityPermissionStatus);
        notificationPermissionStatus = view.findViewById(R.id.notificationPermissionStatus);
        exactAlarmPermissionStatus = view.findViewById(R.id.exactAlarmPermissionStatus);
        batteryOptimizationStatus = view.findViewById(R.id.batteryOptimizationStatus);
        monitorStatus = view.findViewById(R.id.monitorStatus);
        debugLoggingSwitch = view.findViewById(R.id.debugLoggingSwitch);
    }

    private void setupActions(@NonNull View view) {
        MaterialButton overlayPermissionButton = view.findViewById(R.id.overlayPermissionButton);
        MaterialButton accessibilityPermissionButton = view.findViewById(R.id.accessibilityPermissionButton);
        MaterialButton notificationPermissionButton = view.findViewById(R.id.notificationPermissionButton);
        MaterialButton exactAlarmPermissionButton = view.findViewById(R.id.exactAlarmPermissionButton);
        MaterialButton batteryOptimizationButton = view.findViewById(R.id.batteryOptimizationButton);

        overlayPermissionButton.setOnClickListener(v -> requestOverlayPermission());
        accessibilityPermissionButton.setOnClickListener(v -> requestAccessibilityPermission());
        notificationPermissionButton.setOnClickListener(v -> requestNotificationPermission());
        exactAlarmPermissionButton.setOnClickListener(v -> requestExactAlarmPermission());
        batteryOptimizationButton.setOnClickListener(v -> requestBatteryOptimizationExemption());
        debugLoggingSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> onDebugLoggingChanged(isChecked));
    }

    private void requestAccessibilityPermission() {
        startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
    }

    private void requestOverlayPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return;
        }
        Intent intent = new Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + requireContext().getPackageName())
        );
        startActivity(intent);
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission()) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            return;
        }
        openNotificationSettings();
    }

    private void openNotificationSettings() {
        Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, requireContext().getPackageName());
        startActivity(intent);
    }

    private void requestExactAlarmPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !BackgroundLaunchHelper.canScheduleExactAlarms(requireContext())) {
            Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
            startActivity(intent);
            return;
        }
        openAlarmsAndRemindersSettings();
    }

    private void openAlarmsAndRemindersSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM));
        }
    }

    private void requestBatteryOptimizationExemption() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && !BackgroundLaunchHelper.isIgnoringBatteryOptimizations(requireContext())) {
            Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                    .setData(android.net.Uri.parse("package:" + requireContext().getPackageName()));
            startActivity(intent);
            return;
        }
        openBatteryOptimizationSettings();
    }

    private void openBatteryOptimizationSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
        }
    }

    private void updateStatus() {
        boolean overlayEnabled = OverlayPermissionMonitor.canDrawOverlays(requireContext());
        boolean accessibilityEnabled = AccessibilityMonitor.isAccessibilityServiceEnabled(requireContext());
        boolean notificationsGranted = hasNotificationPermission();
        boolean exactAlarmGranted = BackgroundLaunchHelper.canScheduleExactAlarms(requireContext());
        boolean batteryOptimizationIgnored = BackgroundLaunchHelper.isIgnoringBatteryOptimizations(requireContext());

        overlayPermissionStatus.setText(overlayEnabled
                ? getString(R.string.permission_granted)
                : getString(R.string.permission_required));
        accessibilityPermissionStatus.setText(accessibilityEnabled
                ? getString(R.string.permission_granted)
                : getString(R.string.permission_required));
        notificationPermissionStatus.setText(getString(
                notificationsGranted
                        ? R.string.permission_granted
                        : (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                        ? R.string.permission_recommended
                        : R.string.permission_not_required)
        ));
        exactAlarmPermissionStatus.setText(getString(
                exactAlarmGranted
                        ? R.string.permission_granted
                        : (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                        ? R.string.permission_recommended
                        : R.string.permission_not_required)
        ));
        batteryOptimizationStatus.setText(getString(
                batteryOptimizationIgnored
                        ? R.string.permission_granted
                        : (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                        ? R.string.permission_recommended
                        : R.string.permission_not_required)
        ));
        monitorStatus.setText(getString(
                R.string.monitor_status,
                getString(KeepAwakeServiceController.getStatusLabelResId(requireContext()))
        ));
        debugLoggingSwitch.setChecked(AppSelectorStore.isDebugLoggingEnabled(requireContext()));
    }

    private void onDebugLoggingChanged(boolean enabled) {
        boolean current = AppSelectorStore.isDebugLoggingEnabled(requireContext());
        if (current == enabled) {
            return;
        }

        if (!enabled) {
            DebugLog.i(requireContext(), "Debug logging disabled by user");
        }
        AppSelectorStore.setDebugLoggingEnabled(requireContext(), enabled);
        if (enabled) {
            DebugLog.i(requireContext(), "Debug logging enabled by user");
        }
        Toast.makeText(
                requireContext(),
                enabled ? R.string.toast_debug_logging_enabled : R.string.toast_debug_logging_disabled,
                Toast.LENGTH_SHORT
        ).show();
    }

    private boolean hasNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return true;
        }
        return ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED;
    }
}
