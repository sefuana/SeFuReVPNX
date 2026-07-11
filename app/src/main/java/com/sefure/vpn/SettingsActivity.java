package com.sefure.vpn;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.ImageView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.SwitchCompat;

public class SettingsActivity extends AppCompatActivity {
    private SwitchCompat autoConnectSwitch, darkModeSwitch;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        prefs = getSharedPreferences("SeFuRePrefs", MODE_PRIVATE);

        autoConnectSwitch = findViewById(R.id.auto_connect_switch);
        darkModeSwitch = findViewById(R.id.dark_mode_switch);

        autoConnectSwitch.setChecked(prefs.getBoolean("autoConnect", false));
        darkModeSwitch.setChecked(prefs.getBoolean("darkMode", true));

        autoConnectSwitch.setOnCheckedChangeListener((buttonView, isChecked) ->
            prefs.edit().putBoolean("autoConnect", isChecked).apply());

        darkModeSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("darkMode", isChecked).apply();
            if (isChecked) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
            } else {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
            }
        });

        ImageView backButton = findViewById(R.id.back_button);
        backButton.setOnClickListener(v -> finish());
    }
}
