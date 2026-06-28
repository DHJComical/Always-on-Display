package com.dhj.always_on_display.ui.fragment;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.dhj.always_on_display.R;
import com.dhj.always_on_display.data.AppSelectorStore;
import com.dhj.always_on_display.model.AppInfo;
import com.dhj.always_on_display.service.KeepAwakeServiceController;
import com.dhj.always_on_display.ui.adapter.AppListAdapter;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class AppsFragment extends Fragment {
    private enum FilterMode {
        USER,
        SELECTED,
        SYSTEM
    }

    private final List<AppInfo> allApps = new ArrayList<>();

    private AppListAdapter adapter;
    private View emptyState;
    private TextView emptyTitle;
    private TextView emptySubtitle;
    private EditText searchInput;
    private ImageButton clearSearchButton;
    private LinearLayout filterAllCard;
    private LinearLayout filterSelectedCard;
    private LinearLayout filterSystemCard;
    private TextView filterAllCount;
    private TextView filterSelectedCount;
    private TextView filterSystemCount;
    private boolean isLoadingApps;
    private FilterMode currentFilterMode = FilterMode.USER;

    public AppsFragment() {
        super(R.layout.fragment_apps);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        bindViews(view);
        setupRecyclerView(view);
        setupSearch();
        setupFilterCards();
        loadInstalledApps();
    }

    @Override
    public void onResume() {
        super.onResume();
        String query = getSearchQuery();
        updateFilterCards(query);
        filterApps(query);
    }

    private void bindViews(@NonNull View view) {
        emptyState = view.findViewById(R.id.emptyState);
        emptyTitle = view.findViewById(R.id.emptyTitle);
        emptySubtitle = view.findViewById(R.id.emptySubtitle);
        searchInput = view.findViewById(R.id.searchInput);
        clearSearchButton = view.findViewById(R.id.clearSearchButton);
        filterAllCard = view.findViewById(R.id.filterAllCard);
        filterSelectedCard = view.findViewById(R.id.filterSelectedCard);
        filterSystemCard = view.findViewById(R.id.filterSystemCard);
        filterAllCount = view.findViewById(R.id.filterAllCount);
        filterSelectedCount = view.findViewById(R.id.filterSelectedCount);
        filterSystemCount = view.findViewById(R.id.filterSystemCount);
    }

    private void setupRecyclerView(@NonNull View view) {
        Set<String> selectedPackages = AppSelectorStore.readSelectedPackages(requireContext());
        adapter = new AppListAdapter(selectedPackages, updatedSelection -> {
            AppSelectorStore.writeSelectedPackages(requireContext(), updatedSelection);
            KeepAwakeServiceController.syncService(requireContext(), "app_selection_changed");
            String query = getSearchQuery();
            updateFilterCards(query);
            filterApps(query);
        });

        RecyclerView recyclerView = view.findViewById(R.id.appsRecyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setAdapter(adapter);
    }

    private void setupSearch() {
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String query = s == null ? "" : s.toString();
                clearSearchButton.setVisibility(query.isEmpty() ? View.GONE : View.VISIBLE);
                updateFilterCards(query);
                filterApps(query);
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        clearSearchButton.setOnClickListener(v -> searchInput.setText(""));
    }

    private void setupFilterCards() {
        filterAllCard.setOnClickListener(v -> setFilterMode(FilterMode.USER));
        filterSelectedCard.setOnClickListener(v -> setFilterMode(FilterMode.SELECTED));
        filterSystemCard.setOnClickListener(v -> setFilterMode(FilterMode.SYSTEM));
        updateFilterCardAppearance();
    }

    private void setFilterMode(@NonNull FilterMode mode) {
        if (currentFilterMode == mode) {
            return;
        }
        currentFilterMode = mode;
        updateFilterCardAppearance();
        String query = getSearchQuery();
        updateFilterCards(query);
        filterApps(query);
    }

    private void loadInstalledApps() {
        isLoadingApps = true;
        showLoadingState();
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
                        applicationInfo.loadIcon(packageManager),
                        (applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0
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
                isLoadingApps = false;
                allApps.clear();
                allApps.addAll(loadedApps);
                String query = getSearchQuery();
                updateFilterCards(query);
                filterApps(query);
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

    private void filterApps(@NonNull String query) {
        if (adapter == null) {
            return;
        }

        if (isLoadingApps) {
            showLoadingState();
            return;
        }

        String normalizedQuery = query.trim().toLowerCase(Locale.ROOT);
        Set<String> selectedPackages = AppSelectorStore.readSelectedPackages(requireContext());
        List<AppInfo> filteredApps = new ArrayList<>();

        for (AppInfo appInfo : allApps) {
            boolean matchesMode =
                    (currentFilterMode == FilterMode.USER && !appInfo.isSystemApp())
                            || (currentFilterMode == FilterMode.SELECTED
                            && selectedPackages.contains(appInfo.getPackageName()))
                            || (currentFilterMode == FilterMode.SYSTEM && appInfo.isSystemApp());
            if (!matchesMode) {
                continue;
            }

            boolean matchesQuery = normalizedQuery.isEmpty()
                    || appInfo.getAppName().toLowerCase(Locale.ROOT).contains(normalizedQuery)
                    || appInfo.getPackageName().toLowerCase(Locale.ROOT).contains(normalizedQuery);
            if (matchesQuery) {
                filteredApps.add(appInfo);
            }
        }

        adapter.submitList(filteredApps);
        if (filteredApps.isEmpty()) {
            emptyState.setVisibility(View.VISIBLE);
            emptyTitle.setText(R.string.apps_empty_title);
            emptySubtitle.setText(R.string.apps_empty_subtitle);
        } else {
            emptyState.setVisibility(View.GONE);
        }
    }

    private String getSearchQuery() {
        Editable editable = searchInput.getText();
        return editable == null ? "" : editable.toString();
    }

    private void showLoadingState() {
        emptyState.setVisibility(View.VISIBLE);
        emptyTitle.setText(R.string.loading_apps);
        emptySubtitle.setText("");
    }

    private void updateFilterCards(@NonNull String query) {
        if (!isAdded()) {
            return;
        }

        String normalizedQuery = query.trim().toLowerCase(Locale.ROOT);
        Set<String> selectedPackages = AppSelectorStore.readSelectedPackages(requireContext());
        int userCount = 0;
        int selectedCount = 0;
        int systemCount = 0;
        for (AppInfo appInfo : allApps) {
            boolean matchesQuery = normalizedQuery.isEmpty()
                    || appInfo.getAppName().toLowerCase(Locale.ROOT).contains(normalizedQuery)
                    || appInfo.getPackageName().toLowerCase(Locale.ROOT).contains(normalizedQuery);
            if (!matchesQuery) {
                continue;
            }

            if (!appInfo.isSystemApp()) {
                userCount++;
            }
            if (selectedPackages.contains(appInfo.getPackageName())) {
                selectedCount++;
            }
            if (appInfo.isSystemApp()) {
                systemCount++;
            }
        }

        filterAllCount.setText(getString(R.string.apps_filter_count, userCount));
        filterSelectedCount.setText(getString(R.string.apps_filter_count, selectedCount));
        filterSystemCount.setText(getString(R.string.apps_filter_count, systemCount));
        updateFilterCardAppearance();
    }

    private void updateFilterCardAppearance() {
        updateSingleFilterCard(filterAllCard, currentFilterMode == FilterMode.USER);
        updateSingleFilterCard(filterSelectedCard, currentFilterMode == FilterMode.SELECTED);
        updateSingleFilterCard(filterSystemCard, currentFilterMode == FilterMode.SYSTEM);
    }

    private void updateSingleFilterCard(@NonNull LinearLayout cardView, boolean selected) {
        boolean darkMode = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        int selectedColor = ContextCompat.getColor(
                requireContext(),
                darkMode ? R.color.md_theme_dark_primary : R.color.md_theme_light_primary
        );
        int unselectedColor = ContextCompat.getColor(
                requireContext(),
                darkMode ? R.color.md_theme_dark_onSurfaceVariant : R.color.md_theme_light_onSurfaceVariant
        );
        int titleColor = selected ? selectedColor : unselectedColor;
        int countColor = selected ? selectedColor : unselectedColor;

        for (int i = 0; i < cardView.getChildCount(); i++) {
            View child = cardView.getChildAt(i);
            if (child instanceof TextView) {
                ((TextView) child).setTextColor(i == 0 ? titleColor : countColor);
            }
        }
    }
}
