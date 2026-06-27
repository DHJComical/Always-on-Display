package com.dhj.always_on_display.ui.activity;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.Lifecycle;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.dhj.always_on_display.R;
import com.dhj.always_on_display.ui.fragment.AppsFragment;
import com.dhj.always_on_display.ui.fragment.IntroFragment;
import com.dhj.always_on_display.ui.fragment.SettingsFragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.util.Arrays;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private ViewPager2 viewPager;
    private BottomNavigationView bottomNavigationView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setupNavigation();
    }

    private void setupNavigation() {
        viewPager = findViewById(R.id.viewPager);
        bottomNavigationView = findViewById(R.id.bottomNavigation);

        List<Fragment> fragments = Arrays.asList(
                new IntroFragment(),
                new AppsFragment(),
                new SettingsFragment()
        );

        viewPager.setAdapter(new BottomPagerAdapter(getSupportFragmentManager(), getLifecycle(), fragments));
        viewPager.setOffscreenPageLimit(fragments.size());
        viewPager.setUserInputEnabled(false);

        bottomNavigationView.setOnItemSelectedListener(item -> {
            if (item.getItemId() == R.id.nav_apps) {
                viewPager.setCurrentItem(1, false);
            } else if (item.getItemId() == R.id.nav_settings) {
                viewPager.setCurrentItem(2, false);
            } else {
                viewPager.setCurrentItem(0, false);
            }
            return true;
        });

        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                bottomNavigationView.getMenu().getItem(position).setChecked(true);
            }
        });

        bottomNavigationView.setSelectedItemId(R.id.nav_intro);
    }

    public void openAppsPage() {
        bottomNavigationView.setSelectedItemId(R.id.nav_apps);
    }

    public void openSettingsPage() {
        bottomNavigationView.setSelectedItemId(R.id.nav_settings);
    }

    private static final class BottomPagerAdapter extends FragmentStateAdapter {
        private final List<Fragment> fragments;

        BottomPagerAdapter(
                @NonNull FragmentManager fragmentManager,
                @NonNull Lifecycle lifecycle,
                @NonNull List<Fragment> fragments
        ) {
            super(fragmentManager, lifecycle);
            this.fragments = fragments;
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            return fragments.get(position);
        }

        @Override
        public int getItemCount() {
            return fragments.size();
        }
    }
}
