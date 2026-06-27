package com.dhj.always_on_display.ui.fragment;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.dhj.always_on_display.R;
import com.dhj.always_on_display.data.AppSelectorStore;
import com.dhj.always_on_display.logging.DebugLog;
import com.dhj.always_on_display.monitor.ForegroundAppMonitor;
import com.dhj.always_on_display.service.KeepAwakeOverlayService;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.materialswitch.MaterialSwitch;

public class SettingsFragment extends Fragment {
    private TextView overlayPermissionStatus;
    private TextView usagePermissionStatus;
    private TextView compatibilityStatus;
    private MaterialButton startOverlayButton;
    private MaterialButton stopOverlayButton;
    private MaterialSwitch debugLoggingSwitch;

    public SettingsFragment() {
        super(R.layout.fragment_settings);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        bindViews(view);
        setupActions(view);
        updateStatus();
    }

    @Override
    public void onResume() {
        super.onResume();
        updateStatus();
    }

    private void bindViews(@NonNull View view) {
        overlayPermissionStatus = view.findViewById(R.id.overlayPermissionStatus);
        usagePermissionStatus = view.findViewById(R.id.usagePermissionStatus);
        compatibilityStatus = view.findViewById(R.id.compatibilityStatus);
        startOverlayButton = view.findViewById(R.id.startOverlayButton);
        stopOverlayButton = view.findViewById(R.id.stopOverlayButton);
        debugLoggingSwitch = view.findViewById(R.id.debugLoggingSwitch);
    }

    private void setupActions(@NonNull View view) {
        MaterialButton overlayPermissionButton = view.findViewById(R.id.overlayPermissionButton);
        MaterialButton usagePermissionButton = view.findViewById(R.id.usagePermissionButton);

        overlayPermissionButton.setOnClickListener(v -> requestOverlayPermission());
        usagePermissionButton.setOnClickListener(v -> requestUsageAccessPermission());
        startOverlayButton.setOnClickListener(v -> startOverlayCompatibilityMode());
        stopOverlayButton.setOnClickListener(v -> stopOverlayCompatibilityMode());
        debugLoggingSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> onDebugLoggingChanged(isChecked));
    }

    private void requestOverlayPermission() {
        Intent intent = new Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + requireContext().getPackageName())
        );
        startActivity(intent);
    }

    private void requestUsageAccessPermission() {
        startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
    }

    private void startOverlayCompatibilityMode() {
        boolean overlayGranted = Settings.canDrawOverlays(requireContext());
        boolean usageGranted = ForegroundAppMonitor.hasUsageAccess(requireContext());
        int selectedCount = AppSelectorStore.readSelectedPackages(requireContext()).size();
        DebugLog.i(requireContext(), "Start requested: overlay="
                + overlayGranted
                + ", usageAccess="
                + usageGranted
                + ", selectedCount="
                + selectedCount);

        if (!overlayGranted) {
            DebugLog.w(requireContext(), "Start blocked because overlay permission is missing");
            requestOverlayPermission();
            return;
        }
        if (!usageGranted) {
            DebugLog.w(requireContext(), "Start blocked because usage access is missing");
            requestUsageAccessPermission();
            return;
        }
        if (selectedCount == 0) {
            DebugLog.w(requireContext(), "Start blocked because no trigger apps are selected");
            Toast.makeText(requireContext(), R.string.toast_select_apps_first, Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            ContextCompat.startForegroundService(
                    requireContext(),
                    new Intent(requireContext(), KeepAwakeOverlayService.class)
                            .setAction(KeepAwakeOverlayService.ACTION_START)
            );
            DebugLog.i(requireContext(), "Foreground service start request sent");
            AppSelectorStore.setOverlayActive(requireContext(), true);
            Toast.makeText(requireContext(), R.string.toast_compat_enabled, Toast.LENGTH_SHORT).show();
            updateStatus();
        } catch (RuntimeException e) {
            DebugLog.e(requireContext(), "Failed to request foreground service start", e);
            AppSelectorStore.setOverlayActive(requireContext(), false);
            Toast.makeText(requireContext(), R.string.toast_compat_start_failed, Toast.LENGTH_SHORT).show();
            updateStatus();
        }
    }

    private void stopOverlayCompatibilityMode() {
        DebugLog.i(requireContext(), "User requested compatibility mode stop");
        try {
            requireContext().startService(
                    new Intent(requireContext(), KeepAwakeOverlayService.class)
                            .setAction(KeepAwakeOverlayService.ACTION_STOP)
            );
        } catch (RuntimeException e) {
            DebugLog.e(requireContext(), "Failed to request compatibility mode stop", e);
        }
        AppSelectorStore.setOverlayActive(requireContext(), false);
        Toast.makeText(requireContext(), R.string.toast_compat_stopped, Toast.LENGTH_SHORT).show();
        updateStatus();
    }

    private void updateStatus() {
        boolean overlayGranted = Settings.canDrawOverlays(requireContext());
        boolean usageGranted = ForegroundAppMonitor.hasUsageAccess(requireContext());
        boolean compatibilityActive = AppSelectorStore.isOverlayActive(requireContext());
        int selectedCount = AppSelectorStore.readSelectedPackages(requireContext()).size();

        overlayPermissionStatus.setText(overlayGranted
                ? getString(R.string.permission_granted)
                : getString(R.string.permission_required));
        usagePermissionStatus.setText(usageGranted
                ? getString(R.string.permission_granted)
                : getString(R.string.permission_required));
        compatibilityStatus.setText(getString(
                R.string.compat_status,
                getString(compatibilityActive ? R.string.compat_enabled : R.string.compat_disabled)
        ));
        debugLoggingSwitch.setChecked(AppSelectorStore.isDebugLoggingEnabled(requireContext()));

        startOverlayButton.setEnabled(overlayGranted && usageGranted && selectedCount > 0 && !compatibilityActive);
        stopOverlayButton.setEnabled(compatibilityActive);
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
}
