package com.dhj.always_on_display.ui.fragment;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.dhj.always_on_display.R;
import com.dhj.always_on_display.data.AppSelectorStore;
import com.dhj.always_on_display.service.KeepAwakeServiceController;
import com.google.android.material.card.MaterialCardView;

public class IntroFragment extends Fragment {
    public IntroFragment() {
        super(R.layout.fragment_intro);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        bindToolbar(view);
        bindStatusCard(view);
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

    private void bindToolbar(@NonNull View view) {
        com.google.android.material.appbar.MaterialToolbar toolbar = view.findViewById(R.id.toolbar);
        toolbar.setTitle(R.string.app_name);
    }

    private void bindStatusCard(@NonNull View view) {
        MaterialCardView statusCard = view.findViewById(R.id.statusCard);
        statusCard.setOnClickListener(v -> {
            boolean nextEnabled = !AppSelectorStore.isAppEnabled(requireContext());
            KeepAwakeServiceController.setAppEnabled(requireContext(), nextEnabled, "intro_status_card_toggled");
            updateSummary(view);
        });
    }

    private void updateSummary(@NonNull View view) {
        MaterialCardView statusCard = view.findViewById(R.id.statusCard);
        ImageView statusIcon = view.findViewById(R.id.statusIcon);
        TextView statusTitle = view.findViewById(R.id.statusTitle);
        TextView statusSummary = view.findViewById(R.id.statusSummary);
        TextView introSelectionCount = view.findViewById(R.id.introSelectionCount);
        TextView introMonitorStatus = view.findViewById(R.id.introMonitorStatus);

        boolean appEnabled = AppSelectorStore.isAppEnabled(requireContext());
        int selectedCount = AppSelectorStore.readSelectedPackages(requireContext()).size();

        applyStatusCardColors(statusCard, statusIcon, statusTitle, statusSummary, appEnabled);

        if (!appEnabled) {
            statusSummary.setText(R.string.software_status_disabled_summary);
        } else if (selectedCount == 0) {
            statusSummary.setText(R.string.software_status_enabled_idle_summary);
        } else {
            statusSummary.setText(getString(R.string.software_status_enabled_summary, selectedCount));
        }

        if (selectedCount == 0) {
            introSelectionCount.setText(R.string.selection_none);
        } else {
            introSelectionCount.setText(getString(R.string.selection_count, selectedCount));
        }

        introMonitorStatus.setText(
                KeepAwakeServiceController.getStatusLabelResId(requireContext())
        );
    }

    private void applyStatusCardColors(
            @NonNull MaterialCardView statusCard,
            @NonNull ImageView statusIcon,
            @NonNull TextView statusTitle,
            @NonNull TextView statusSummary,
            boolean enabled
    ) {
        @ColorInt int backgroundColor = ContextCompat.getColor(
                requireContext(),
                enabled ? R.color.md_theme_light_primary : R.color.md_theme_light_errorContainer
        );
        @ColorInt int foregroundColor = ContextCompat.getColor(
                requireContext(),
                enabled ? R.color.md_theme_light_onPrimary : R.color.md_theme_light_onErrorContainer
        );
        statusCard.setCardBackgroundColor(backgroundColor);
        statusIcon.setImageTintList(ColorStateList.valueOf(foregroundColor));
        statusTitle.setTextColor(foregroundColor);
        statusSummary.setTextColor(foregroundColor);
    }
}
