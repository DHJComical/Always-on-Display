package com.dhj.always_on_display.ui.fragment;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.dhj.always_on_display.R;
import com.dhj.always_on_display.data.AppSelectorStore;
import com.dhj.always_on_display.ui.activity.MainActivity;
import com.google.android.material.button.MaterialButton;

public class IntroFragment extends Fragment {
    public IntroFragment() {
        super(R.layout.fragment_intro);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        bindActions(view);
        updateSummary(view);
    }

    @Override
    public void onResume() {
        super.onResume();
        View view = getView();
        if (view != null) {
            updateSummary(view);
        }
    }

    private void updateSummary(@NonNull View view) {
        TextView introSelectionCount = view.findViewById(R.id.introSelectionCount);
        TextView introCompatibilityStatus = view.findViewById(R.id.introCompatibilityStatus);
        TextView statusSummary = view.findViewById(R.id.statusSummary);

        int selectedCount = AppSelectorStore.readSelectedPackages(requireContext()).size();
        if (selectedCount == 0) {
            introSelectionCount.setText(R.string.selection_none);
            statusSummary.setText(R.string.intro_status_idle);
        } else {
            introSelectionCount.setText(getString(R.string.selection_count, selectedCount));
            statusSummary.setText(getString(R.string.intro_status_active, selectedCount));
        }

        introCompatibilityStatus.setText(
                AppSelectorStore.isOverlayActive(requireContext())
                        ? R.string.compat_enabled
                        : R.string.compat_disabled
        );
    }

    private void bindActions(@NonNull View view) {
        MaterialButton openAppsButton = view.findViewById(R.id.openAppsButton);
        MaterialButton openSettingsButton = view.findViewById(R.id.openSettingsButton);

        openAppsButton.setOnClickListener(v -> {
            if (requireActivity() instanceof MainActivity) {
                ((MainActivity) requireActivity()).openAppsPage();
            }
        });
        openSettingsButton.setOnClickListener(v -> {
            if (requireActivity() instanceof MainActivity) {
                ((MainActivity) requireActivity()).openSettingsPage();
            }
        });
    }
}
