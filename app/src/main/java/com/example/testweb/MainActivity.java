package com.example.testweb;

import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {

    private Fragment homeFragment = new HomeFragment();
    private Fragment settingsFragment = new SettingsFragment();
    private Fragment currentFragment = homeFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        BottomNavigationView navView = findViewById(R.id.bottom_navigation);

        // 添加默认 Fragment（只添加一次）
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .add(R.id.nav_host_fragment, homeFragment)
                    .add(R.id.nav_host_fragment, settingsFragment)
                    .hide(settingsFragment)
                    .commit();
        }

        navView.setOnItemSelectedListener(item -> {
            Fragment targetFragment = null;
            int id = item.getItemId();
            if (id == R.id.nav_home) {
                targetFragment = homeFragment;
            } else if (id == R.id.nav_settings) {
                targetFragment = settingsFragment;
            }

            if (targetFragment != null && targetFragment != currentFragment) {
                switchFragment(targetFragment);
            }

            return true;
        });
    }

    private void switchFragment(Fragment targetFragment) {
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        transaction.hide(currentFragment);
        transaction.show(targetFragment);
        transaction.commit();
        currentFragment = targetFragment;
    }
}
