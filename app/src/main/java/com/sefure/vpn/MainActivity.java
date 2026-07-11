package com.sefure.vpn;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.VpnService;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.ScaleAnimation;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.widget.ImageViewCompat;
import android.graphics.Color;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {
    private static final int VPN_REQUEST_CODE = 100;
    private MaterialButton connectButton;
    private TextView timerText, uploadSpeed, downloadSpeed, totalData;
    private ImageView adminIcon;
    private VPNManager vpnManager;
    private Handler handler = new Handler();
    private long connectedTime = 0;
    private boolean isConnected = false;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        prefs = getSharedPreferences("SeFuRePrefs", MODE_PRIVATE);
        vpnManager = new VPNManager(this);
        initViews();
        setupListeners();
        startSpeedMonitor();
    }

    private void initViews() {
        connectButton = findViewById(R.id.connect_button);
        timerText = findViewById(R.id.timer_text);
        uploadSpeed = findViewById(R.id.upload_speed);
        downloadSpeed = findViewById(R.id.download_speed);
        totalData = findViewById(R.id.total_data);
        adminIcon = findViewById(R.id.admin_icon);
        MaterialCardView statsCard = findViewById(R.id.stats_card);
        statsCard.setBackgroundResource(R.drawable.glassmorphism_bg);
    }

    private void setupListeners() {
        connectButton.setOnClickListener(v -> toggleVPN());
        adminIcon.setOnClickListener(v -> {
            if (prefs.getBoolean("isAdmin", false)) {
                startActivity(new Intent(this, AdminPanelActivity.class));
            } else {
                showAdminPasswordDialog();
            }
        });
    }

    private void toggleVPN() {
        if (!isConnected) {
            Intent intent = VpnService.prepare(this);
            if (intent != null) {
                startActivityForResult(intent, VPN_REQUEST_CODE);
            } else {
                startVPN();
            }
        } else {
            stopVPN();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == VPN_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            startVPN();
        }
    }

    private void startVPN() {
        Intent intent = new Intent(this, SeFuReVPNService.class);
        intent.setAction("CONNECT");
        ContextCompat.startForegroundService(this, intent);
        isConnected = true;
        connectedTime = System.currentTimeMillis();
        animateConnection(true);
        startTimer();
        FirebaseManager.logConnection(true, fetchDeviceId());
    }

    private void stopVPN() {
        Intent intent = new Intent(this, SeFuReVPNService.class);
        intent.setAction("DISCONNECT");
        startService(intent);
        isConnected = false;
        animateConnection(false);
        handler.removeCallbacks(timerRunnable);
        timerText.setText("00:00:00");
        FirebaseManager.logConnection(false, fetchDeviceId());
    }

    private void animateConnection(boolean connected) {
        ScaleAnimation anim = new ScaleAnimation(
            1f, connected ? 1.1f : 1f,
            1f, connected ? 1.1f : 1f,
            Animation.RELATIVE_TO_SELF, 0.5f,
            Animation.RELATIVE_TO_SELF, 0.5f);
        anim.setDuration(500);
        anim.setFillAfter(true);
        connectButton.startAnimation(anim);
        if (connected) {
            connectButton.setText("DISCONNECT");
            connectButton.setBackgroundColor(
                ContextCompat.getColor(this, android.R.color.holo_red_dark));
        } else {
            connectButton.setText("CONNECT");
            connectButton.setBackgroundColor(
                ContextCompat.getColor(this, R.color.accent_red));
        }
    }

    private void startTimer() {
        handler.post(timerRunnable);
    }

    private Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            if (isConnected) {
                long millis = System.currentTimeMillis() - connectedTime;
                String time = String.format("%02d:%02d:%02d",
                    TimeUnit.MILLISECONDS.toHours(millis),
                    TimeUnit.MILLISECONDS.toMinutes(millis) % 60,
                    TimeUnit.MILLISECONDS.toSeconds(millis) % 60);
                timerText.setText(time);
                handler.postDelayed(this, 1000);
            }
        }
    };

    private void startSpeedMonitor() {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (isConnected) {
                    float[] speeds = vpnManager.getCurrentSpeed();
                    uploadSpeed.setText(String.format("%.1f KB/s", speeds[0]));
                    downloadSpeed.setText(String.format("%.1f KB/s", speeds[1]));
                    totalData.setText(vpnManager.getTotalDataUsage());
                    FirebaseManager.updateSpeedData(fetchDeviceId(), speeds[0], speeds[1]);
                }
                handler.postDelayed(this, 1000);
            }
        }, 1000);
    }

    private void showAdminPasswordDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = getLayoutInflater().inflate(R.layout.dialog_admin_password, null);
        EditText passwordInput = view.findViewById(R.id.password_input);
        builder.setView(view)
            .setTitle("Admin Access")
            .setPositiveButton("Login", (dialog, which) -> {
                if (passwordInput.getText().toString().equals("sefuax@tg")) {
                    prefs.edit().putBoolean("isAdmin", true).apply();
                    startActivity(new Intent(this, AdminPanelActivity.class));
                } else {
                    Toast.makeText(this, "Invalid password", Toast.LENGTH_SHORT).show();
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private String fetchDeviceId() {
        return Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
    }
}
