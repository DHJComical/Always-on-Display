package com.dhj.always_on_display.ui.fragment;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.dhj.always_on_display.R;
import com.dhj.always_on_display.data.AppSelectorStore;
import com.dhj.always_on_display.model.AppInfo;
import com.dhj.always_on_display.ui.adapter.AppListAdapter;
import com.google.android.material.textfield.TextInputEditText;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class AppsFragment extends Fragment {
    private final List<AppInfo> allApps = new ArrayList<>();

    private AppListAdapter adapter;
    private TextView selectionCount;
    private TextView emptyState;
    private TextInputEditText searchInput;

    public AppsFragment() {
        super(R.layout.fragment_apps);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        bindViews(view);
        setupRecyclerView(view);
        setupSearch();
        loadInstalledApps();
    }

    @Override
    public void onResume() {
        super.onResume();
        updateSelectionCount(adapter == null ? 0 : adapter.getSelectedCount());
        filterApps(getSearchQuery());
    }

    private void bindViews(@NonNull View view) {
        selectionCount = view.findViewById(R.id.selectionCount);
        emptyState = view.findViewById(R.id.emptyState);
        searchInput = view.findViewById(R.id.searchInput);
    }

    private void setupRecyclerView(@NonNull View view) {
        Set<String> selectedPackages = AppSelectorStore.readSelectedPackages(requireContext());
        adapter = new AppListAdapter(selectedPackages, updatedSelection -> {
            AppSelectorStore.writeSelectedPackages(requireContext(), updatedSelection);
            updateSelectionCount(updatedSelection.size());
        });

        RecyclerView recyclerView = view.findViewById(R.id.appsRecyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setAdapter(adapter);
        updateSelectionCount(selectedPackages.size());
    }

    private void setupSearch() {
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterApps(s == null ? "" : s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
    }

    private void loadInstalledApps() {
        emptyState.setText(R.string.loading_apps);
        emptyState.setVisibility(View.VISIBLE);
        PackageManager packageManager = requireContext().getPackageManager();
        String selfPackageName = requireContext().getPackageName();

        new Thread(() -> {
            List<AppInfo> loadedApps = new ArrayList<>();

            List<ApplicationInfo> installedApplications = getInstalledApplications(packageManager);
            for (ApplicationInfo applicationInfo : installedApplications) {
                String packageName = applicationInfo.packageName;
                if (selfPackageName.equals(packageName)) {
                    continue;
                }

                CharSequence label = applicationInfo.loadLabel(packageManager);
                loadedApps.add(new AppInfo(
                        label == null ? packageName : label.toString(),
                        packageName,
                        applicationInfo.loadIcon(packageManager)
                ));
            }

            Collator collator = Collator.getInstance(Locale.getDefault());
            loadedApps.sort(Comparator.comparing(AppInfo::getAppName, collator));

            if (!isAdded()) {
                return;
            }

            requireActivity().runOnUiThread(() -> {
                if (!isAdded()) {
                    return;
                }
                allApps.clear();
                allApps.addAll(loadedApps);
                filterApps(getSearchQuery());
            });
        }).start();
    }

    @SuppressWarnings("deprecation")
    private List<ApplicationInfo> getInstalledApplications(@NonNull PackageManager packageManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return packageManager.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0));
        }
        return packageManager.getInstalledApplications(0);
    }

    private void updateSelectionCount(int count) {
        if (count == 0) {
            selectionCount.setText(R.string.selection_none);
        } else {
            selectionCount.setText(getString(R.string.selection_count, count));
        }
    }

    private void filterApps(@NonNull String query) {
        if (adapter == null) {
            return;
        }

        String normalizedQuery = query.trim().toLowerCase(Locale.ROOT);
        List<AppInfo> filteredApps = new ArrayList<>();

        for (AppInfo appInfo : allApps) {
            if (normalizedQuery.isEmpty()
                    || appInfo.getAppName().toLowerCase(Locale.ROOT).contains(normalizedQuery)
                    || appInfo.getPackageName().toLowerCase(Locale.ROOT).contains(normalizedQuery)) {
                filteredApps.add(appInfo);
            }
        }

        adapter.submitList(filteredApps);
        emptyState.setVisibility(filteredApps.isEmpty() ? View.VISIBLE : View.GONE);
        if (filteredApps.isEmpty()) {
            emptyState.setText(allApps.isEmpty() ? R.string.loading_apps : R.string.empty_apps);
        }
    }

    private String getSearchQuery() {
        Editable editable = searchInput.getText();
        return editable == null ? "" : editable.toString();
    }
}
