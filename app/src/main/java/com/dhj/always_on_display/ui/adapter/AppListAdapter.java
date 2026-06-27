package com.dhj.always_on_display.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.dhj.always_on_display.R;
import com.dhj.always_on_display.model.AppInfo;
import com.google.android.material.checkbox.MaterialCheckBox;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class AppListAdapter extends RecyclerView.Adapter<AppListAdapter.ViewHolder> {
    public interface OnSelectionChangedListener {
        void onSelectionChanged(Set<String> selectedPackages);
    }

    private final List<AppInfo> items = new ArrayList<>();
    private final Set<String> selectedPackages = new HashSet<>();
    private final OnSelectionChangedListener listener;

    public AppListAdapter(Set<String> initialSelection, OnSelectionChangedListener listener) {
        if (initialSelection != null) {
            selectedPackages.addAll(initialSelection);
        }
        this.listener = listener;
    }

    public void submitList(List<AppInfo> appInfos) {
        items.clear();
        items.addAll(appInfos);
        notifyDataSetChanged();
    }

    public int getSelectedCount() {
        return selectedPackages.size();
    }

    public Set<String> getSelectedPackages() {
        return new HashSet<>(selectedPackages);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_app, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        AppInfo appInfo = items.get(position);
        boolean checked = selectedPackages.contains(appInfo.getPackageName());

        holder.iconView.setImageDrawable(appInfo.getIcon());
        holder.nameView.setText(appInfo.getAppName());
        holder.packageView.setText(holder.itemView.getContext().getString(R.string.package_name, appInfo.getPackageName()));

        holder.checkBox.setOnCheckedChangeListener(null);
        holder.checkBox.setChecked(checked);

        holder.itemView.setOnClickListener(view -> holder.checkBox.toggle());
        holder.checkBox.setOnCheckedChangeListener(
                (buttonView, isChecked) -> updateSelection(appInfo.getPackageName(), isChecked)
        );
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    private void updateSelection(String packageName, boolean selected) {
        boolean changed;
        if (selected) {
            changed = selectedPackages.add(packageName);
        } else {
            changed = selectedPackages.remove(packageName);
        }

        if (changed && listener != null) {
            listener.onSelectionChanged(getSelectedPackages());
        }
    }

    static final class ViewHolder extends RecyclerView.ViewHolder {
        final ImageView iconView;
        final TextView nameView;
        final TextView packageView;
        final MaterialCheckBox checkBox;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            iconView = itemView.findViewById(R.id.appIcon);
            nameView = itemView.findViewById(R.id.appName);
            packageView = itemView.findViewById(R.id.appPackage);
            checkBox = itemView.findViewById(R.id.appCheckBox);
        }
    }
}
