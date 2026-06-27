package com.dhj.always_on_display.ui.fragment;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.dhj.always_on_display.R;
import com.dhj.always_on_display.data.AppSelectorStore;

public class IntroFragment extends Fragment {
    public IntroFragment() {
        super(R.layout.fragment_intro);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
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

        int selectedCount = AppSelectorStore.readSelectedPackages(requireContext()).size();
        if (selectedCount == 0) {
            introSelectionCount.setText(R.string.selection_none);
        } else {
            introSelectionCount.setText(getString(R.string.selection_count, selectedCount));
        }

        introCompatibilityStatus.setText(
                AppSelectorStore.isOverlayActive(requireContext())
                        ? R.string.compat_enabled
                        : R.string.compat_disabled
        );
    }
}
