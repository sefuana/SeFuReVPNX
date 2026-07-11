package com.sefure.vpn;

import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.firebase.database.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class AdminPanelActivity extends AppCompatActivity {
    private RecyclerView devicesRecyclerView;
    private LinearLayout detailPanel;
    private TextView detailTitle, usageDetails, domainsDetails;
    private DatabaseReference devicesRef;
    private List<DeviceInfo> deviceList = new ArrayList<>();
    private DeviceAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin);

        devicesRecyclerView = findViewById(R.id.devices_list);
        detailPanel = findViewById(R.id.detail_panel);
        detailTitle = findViewById(R.id.detail_title);
        usageDetails = findViewById(R.id.usage_details);
        domainsDetails = findViewById(R.id.domains_details);

        ImageView backBtn = findViewById(R.id.back_button);
        if (backBtn != null) backBtn.setOnClickListener(v -> finish());

        devicesRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new DeviceAdapter(deviceList, this::showDeviceDetails);
        devicesRecyclerView.setAdapter(adapter);

        devicesRef = FirebaseManager.getAllDevices();
        loadDevices();
    }

    private void loadDevices() {
        devicesRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                deviceList.clear();
                for (DataSnapshot child : snapshot.getChildren()) {
                    DeviceInfo device = child.getValue(DeviceInfo.class);
                    if (device != null) deviceList.add(device);
                }
                adapter.notifyDataSetChanged();
            }

            @Override
            public void onCancelled(DatabaseError error) {
                Toast.makeText(AdminPanelActivity.this,
                    "Error loading devices", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showDeviceDetails(DeviceInfo device) {
        detailPanel.setVisibility(View.VISIBLE);
        detailTitle.setText(device.getDeviceName() + " (" + device.getBrand() + ")");
        loadDomains(device.getDeviceId());
        loadAppUsage(device.getDeviceId());
    }

    private void loadDomains(String deviceId) {
        FirebaseManager.getDomainsReference(deviceId)
            .limitToLast(50)
            .addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot snapshot) {
                    StringBuilder sb = new StringBuilder("🌐 Domains Visited:\n\n");
                    SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
                    for (DataSnapshot snap : snapshot.getChildren()) {
                        Map<String, Object> data = (Map<String, Object>) snap.getValue();
                        if (data != null) {
                            String domain = (String) data.get("domain");
                            Object ts = data.get("timestamp");
                            String time = ts != null ?
                                sdf.format(new Date(((Number) ts).longValue())) : "";
                            sb.append("• ").append(domain).append("  ").append(time).append("\n");
                        }
                    }
                    domainsDetails.setText(sb.toString());
                }

                @Override
                public void onCancelled(DatabaseError error) {}
            });
    }

    private void loadAppUsage(String deviceId) {
        FirebaseManager.getUsageReference(deviceId)
            .limitToLast(30)
            .addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot snapshot) {
                    StringBuilder sb = new StringBuilder("📱 App Usage:\n\n");
                    for (DataSnapshot snap : snapshot.getChildren()) {
                        Map<String, Object> usage = (Map<String, Object>) snap.getValue();
                        if (usage != null) {
                            String pkg = (String) usage.get("packageName");
                            Object dl = usage.get("downloadBytes");
                            Object ul = usage.get("uploadBytes");
                            if (pkg != null) {
                                sb.append("• ").append(pkg).append("\n");
                                sb.append("  ↓ ").append(formatBytes(dl))
                                  .append("  ↑ ").append(formatBytes(ul)).append("\n\n");
                            }
                        }
                    }
                    usageDetails.setText(sb.toString());
                }

                @Override
                public void onCancelled(DatabaseError error) {}
            });
    }

    private String formatBytes(Object bytes) {
        if (bytes == null) return "0 B";
        long b = ((Number) bytes).longValue();
        if (b < 1024) return b + " B";
        if (b < 1024 * 1024) return String.format("%.1f KB", b / 1024f);
        return String.format("%.2f MB", b / (1024f * 1024f));
    }

    public static class DeviceInfo {
        private String deviceId, deviceName, brand, androidVersion, lastDomain;
        private boolean connected, vpnActive;
        private long lastOnline;

        public DeviceInfo() {}
        public String getDeviceId() { return deviceId; }
        public void setDeviceId(String d) { this.deviceId = d; }
        public String getDeviceName() { return deviceName; }
        public void setDeviceName(String d) { this.deviceName = d; }
        public String getBrand() { return brand != null ? brand : ""; }
        public void setBrand(String b) { this.brand = b; }
        public String getAndroidVersion() { return androidVersion; }
        public void setAndroidVersion(String a) { this.androidVersion = a; }
        public String getLastDomain() { return lastDomain; }
        public void setLastDomain(String d) { this.lastDomain = d; }
        public boolean isConnected() { return connected; }
        public void setConnected(boolean c) { this.connected = c; }
        public boolean isVpnActive() { return vpnActive; }
        public void setVpnActive(boolean v) { this.vpnActive = v; }
        public long getLastOnline() { return lastOnline; }
        public void setLastOnline(long l) { this.lastOnline = l; }
    }

    class DeviceAdapter extends RecyclerView.Adapter<DeviceAdapter.ViewHolder> {
        private List<DeviceInfo> devices;
        private OnDeviceClickListener listener;

        DeviceAdapter(List<DeviceInfo> devices, OnDeviceClickListener listener) {
            this.devices = devices;
            this.listener = listener;
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = getLayoutInflater().inflate(R.layout.item_device, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            DeviceInfo device = devices.get(position);
            holder.deviceName.setText(device.getDeviceName() + " • " + device.getBrand());
            holder.statusIcon.setImageResource(
                device.isConnected() ? R.drawable.ic_online : R.drawable.ic_offline);
            if (device.getLastDomain() != null) {
                holder.lastDomain.setText("Last: " + device.getLastDomain());
                holder.lastDomain.setVisibility(View.VISIBLE);
            }
            holder.itemView.setOnClickListener(v -> listener.onDeviceClick(device));
        }

        @Override
        public int getItemCount() { return devices.size(); }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView deviceName, lastDomain;
            ImageView statusIcon;

            ViewHolder(View itemView) {
                super(itemView);
                deviceName = itemView.findViewById(R.id.device_name);
                statusIcon = itemView.findViewById(R.id.status_icon);
                lastDomain = itemView.findViewById(R.id.last_domain);
            }
        }
    }

    interface OnDeviceClickListener {
        void onDeviceClick(DeviceInfo device);
    }
}
