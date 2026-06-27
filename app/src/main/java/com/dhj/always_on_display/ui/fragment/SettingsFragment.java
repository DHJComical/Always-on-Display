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
import androidx.fragment.app.Fragment;

import com.dhj.always_on_display.R;
import com.dhj.always_on_display.data.AppSelectorStore;
import com.dhj.always_on_display.monitor.ForegroundAppMonitor;
import com.dhj.always_on_display.service.KeepAwakeOverlayService;
import com.google.android.material.button.MaterialButton;

public class SettingsFragment extends Fragment {
    private TextView overlayPermissionStatus;
    private TextView usagePermissionStatus;
    private TextView compatibilityStatus;
    private MaterialButton startOverlayButton;
    private MaterialButton stopOverlayButton;

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
    }

    private void setupActions(@NonNull View view) {
        MaterialButton overlayPermissionButton = view.findViewById(R.id.overlayPermissionButton);
        MaterialButton usagePermissionButton = view.findViewById(R.id.usagePermissionButton);

        overlayPermissionButton.setOnClickListener(v -> requestOverlayPermission());
        usagePermissionButton.setOnClickListener(v -> requestUsageAccessPermission());
        startOverlayButton.setOnClickListener(v -> startOverlayCompatibilityMode());
        stopOverlayButton.setOnClickListener(v -> stopOverlayCompatibilityMode());
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
        if (!Settings.canDrawOverlays(requireContext())) {
            requestOverlayPermission();
            return;
        }
        if (!ForegroundAppMonitor.hasUsageAccess(requireContext())) {
            requestUsageAccessPermission();
            return;
        }
        if (AppSelectorStore.readSelectedPackages(requireContext()).isEmpty()) {
            Toast.makeText(requireContext(), R.string.toast_select_apps_first, Toast.LENGTH_SHORT).show();
            return;
        }

        requireContext().startService(
                new Intent(requireContext(), KeepAwakeOverlayService.class)
                        .setAction(KeepAwakeOverlayService.ACTION_START)
        );
        AppSelectorStore.setOverlayActive(requireContext(), true);
        Toast.makeText(requireContext(), R.string.toast_compat_enabled, Toast.LENGTH_SHORT).show();
        updateStatus();
    }

    private void stopOverlayCompatibilityMode() {
        requireContext().startService(
                new Intent(requireContext(), KeepAwakeOverlayService.class)
                        .setAction(KeepAwakeOverlayService.ACTION_STOP)
        );
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

        startOverlayButton.setEnabled(overlayGranted && usageGranted && selectedCount > 0 && !compatibilityActive);
        stopOverlayButton.setEnabled(compatibilityActive);
    }
}
