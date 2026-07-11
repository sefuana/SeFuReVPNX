package com.sefure.vpn;

import com.google.firebase.database.*;
import java.util.*;

public class FirebaseManager {
    private static DatabaseReference database;

    static {
        database = FirebaseDatabase.getInstance().getReference();
    }

    public static void logConnection(boolean connected, String deviceId) {
        DatabaseReference deviceRef = database.child("devices").child(deviceId);
        Map<String, Object> updates = new HashMap<>();
        updates.put("connected", connected);
        updates.put("lastOnline", System.currentTimeMillis());
        updates.put("deviceId", deviceId);
        updates.put("deviceName", android.os.Build.MODEL);
        updates.put("brand", android.os.Build.BRAND);
        updates.put("androidVersion", android.os.Build.VERSION.RELEASE);
        deviceRef.updateChildren(updates);
    }

    public static void logVPNStatus(boolean active, String deviceId) {
        database.child("devices").child(deviceId).child("vpnActive").setValue(active);
    }

    public static void logDomainAccess(String deviceId, String domain, long timestamp) {
        DatabaseReference domainRef = database.child("domains").child(deviceId).push();
        Map<String, Object> data = new HashMap<>();
        data.put("domain", domain);
        data.put("timestamp", timestamp);
        domainRef.setValue(data);

        // Also update last domain in device info
        database.child("devices").child(deviceId).child("lastDomain").setValue(domain);
    }

    public static void logAppUsage(String deviceId, String packageName,
                                   long downloadBytes, long uploadBytes, long timestamp) {
        DatabaseReference usageRef = database.child("usage").child(deviceId).push();
        Map<String, Object> usageData = new HashMap<>();
        usageData.put("packageName", packageName);
        usageData.put("downloadBytes", downloadBytes);
        usageData.put("uploadBytes", uploadBytes);
        usageData.put("timestamp", timestamp);
        usageRef.setValue(usageData);
    }

    public static void updateSpeedData(String deviceId, float uploadSpeed, float downloadSpeed) {
        Map<String, Object> speedData = new HashMap<>();
        speedData.put("uploadSpeed", uploadSpeed);
        speedData.put("downloadSpeed", downloadSpeed);
        speedData.put("timestamp", System.currentTimeMillis());
        database.child("speeds").child(deviceId).setValue(speedData);
    }

    public static DatabaseReference getDeviceReference(String deviceId) {
        return database.child("devices").child(deviceId);
    }

    public static DatabaseReference getUsageReference(String deviceId) {
        return database.child("usage").child(deviceId);
    }

    public static DatabaseReference getDomainsReference(String deviceId) {
        return database.child("domains").child(deviceId);
    }

    public static DatabaseReference getAllDevices() {
        return database.child("devices");
    }
}
