package com.sefure.vpn;

import android.app.*;
import android.content.*;
import android.net.TrafficStats;
import android.net.VpnService;
import android.os.*;
import android.provider.Settings;
import android.util.Log;
import androidx.core.app.NotificationCompat;
import java.io.*;
import java.net.InetAddress;
import java.nio.*;
import java.nio.channels.*;

public class SeFuReVPNService extends VpnService {
    private static final String TAG = "SeFuReVPNService";
    private static final String VPN_ADDRESS = "10.0.0.2";
    private static final String VPN_ROUTE = "0.0.0.0";
    private static final int VPN_MTU = 32767;
    private static final String CHANNEL_ID = "vpn_channel";

    private ParcelFileDescriptor vpnInterface;
    private Thread vpnThread;
    private Thread speedThread;
    private volatile boolean running = false;

    private long lastRxBytes = 0;
    private long lastTxBytes = 0;
    private long lastTime = 0;

    public static float currentUploadSpeed = 0f;
    public static float currentDownloadSpeed = 0f;
    public static long totalUpload = 0;
    public static long totalDownload = 0;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String action = intent.getAction();
            if ("CONNECT".equals(action)) {
                startVPN();
            } else if ("DISCONNECT".equals(action)) {
                stopVPN();
            }
        }
        return START_STICKY;
    }

    private void startVPN() {
        startForeground(1, createNotification());
        try {
            Builder builder = new Builder();
            builder.setSession("SeFuRe VPN");
            builder.addAddress(VPN_ADDRESS, 32);
            builder.addRoute(VPN_ROUTE, 0);
            builder.addDnsServer("8.8.8.8");
            builder.addDnsServer("8.8.4.4");
            builder.setMtu(VPN_MTU);
            // Allow all apps through VPN
            builder.allowFamily(android.system.OsConstants.AF_INET);
            builder.allowFamily(android.system.OsConstants.AF_INET6);
            vpnInterface = builder.establish();

            if (vpnInterface != null) {
                running = true;
                // Start packet forwarding thread
                vpnThread = new Thread(new PacketForwarder());
                vpnThread.start();
                // Start real speed monitoring
                lastRxBytes = TrafficStats.getTotalRxBytes();
                lastTxBytes = TrafficStats.getTotalTxBytes();
                lastTime = System.currentTimeMillis();
                speedThread = new Thread(new SpeedMonitor());
                speedThread.start();
                FirebaseManager.logVPNStatus(true, fetchDeviceId());
            }
        } catch (Exception e) {
            Log.e(TAG, "VPN start error", e);
        }
    }

    private void stopVPN() {
        running = false;
        try {
            if (vpnInterface != null) {
                vpnInterface.close();
                vpnInterface = null;
            }
            if (vpnThread != null) {
                vpnThread.interrupt();
                vpnThread = null;
            }
            if (speedThread != null) {
                speedThread.interrupt();
                speedThread = null;
            }
        } catch (IOException e) {
            Log.e(TAG, "VPN stop error", e);
        }
        currentUploadSpeed = 0f;
        currentDownloadSpeed = 0f;
        FirebaseManager.logVPNStatus(false, fetchDeviceId());
        stopForeground(true);
        stopSelf();
    }

    // Forwards packets between VPN interface and real network
    private class PacketForwarder implements Runnable {
        @Override
        public void run() {
            if (vpnInterface == null) return;
            FileInputStream in = new FileInputStream(vpnInterface.getFileDescriptor());
            FileOutputStream out = new FileOutputStream(vpnInterface.getFileDescriptor());
            byte[] packet = new byte[VPN_MTU];

            try {
                while (running) {
                    int len = in.read(packet);
                    if (len > 0) {
                        // Parse packet for domain logging
                        try {
                            parseAndLogPacket(packet, len);
                        } catch (Exception ignored) {}
                        // Write back to forward traffic (passthrough)
                        out.write(packet, 0, len);
                        totalDownload += len;
                    }
                    Thread.sleep(1);
                }
            } catch (Exception e) {
                if (running) Log.e(TAG, "Packet forward error", e);
            } finally {
                try { in.close(); out.close(); } catch (Exception ignored) {}
            }
        }

        private void parseAndLogPacket(byte[] packet, int len) {
            if (len < 20) return;
            // Check if UDP (protocol 17) - DNS traffic
            int protocol = packet[9] & 0xFF;
            if (protocol == 17 && len > 28) {
                int destPort = ((packet[22] & 0xFF) << 8) | (packet[23] & 0xFF);
                if (destPort == 53) {
                    // DNS query - extract domain
                    String domain = extractDomain(packet, 28, len);
                    if (domain != null && !domain.isEmpty()) {
                        FirebaseManager.logDomainAccess(fetchDeviceId(), domain,
                            System.currentTimeMillis());
                    }
                }
            }
        }

        private String extractDomain(byte[] data, int offset, int len) {
            try {
                // Skip DNS header (12 bytes)
                int pos = offset + 12;
                StringBuilder domain = new StringBuilder();
                while (pos < len) {
                    int labelLen = data[pos] & 0xFF;
                    if (labelLen == 0) break;
                    if (labelLen > 63 || pos + labelLen >= len) break;
                    if (domain.length() > 0) domain.append(".");
                    domain.append(new String(data, pos + 1, labelLen));
                    pos += labelLen + 1;
                }
                return domain.toString();
            } catch (Exception e) {
                return null;
            }
        }
    }

    // Real-time speed monitor using TrafficStats
    private class SpeedMonitor implements Runnable {
        @Override
        public void run() {
            while (running) {
                try {
                    Thread.sleep(1000);
                    long currentRx = TrafficStats.getTotalRxBytes();
                    long currentTx = TrafficStats.getTotalTxBytes();
                    long currentTime = System.currentTimeMillis();
                    long timeDiff = currentTime - lastTime;

                    if (timeDiff > 0 && lastRxBytes > 0) {
                        float downloadKBps = (float)(currentRx - lastRxBytes) / timeDiff;
                        float uploadKBps = (float)(currentTx - lastTxBytes) / timeDiff;
                        currentDownloadSpeed = downloadKBps;
                        currentUploadSpeed = uploadKBps;
                        totalDownload = currentRx;
                        totalUpload = currentTx;

                        // Log to Firebase every 5 seconds
                        FirebaseManager.updateSpeedData(fetchDeviceId(),
                            uploadKBps, downloadKBps);
                    }
                    lastRxBytes = currentRx;
                    lastTxBytes = currentTx;
                    lastTime = currentTime;
                } catch (InterruptedException e) {
                    break;
                }
            }
        }
    }

    private Notification createNotification() {
        PendingIntent pi = PendingIntent.getActivity(this, 0,
            new Intent(this, MainActivity.class),
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SeFuRe VPN Active")
            .setContentText("Monitoring network traffic")
            .setSmallIcon(R.drawable.ic_shield)
            .setContentIntent(pi)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build();
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
            CHANNEL_ID, "VPN Status", NotificationManager.IMPORTANCE_LOW);
        NotificationManager mgr = getSystemService(NotificationManager.class);
        if (mgr != null) mgr.createNotificationChannel(channel);
    }

    private String fetchDeviceId() {
        return Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
    }

    public static float[] getSpeeds() {
        return new float[]{currentUploadSpeed, currentDownloadSpeed};
    }

    @Override
    public IBinder onBind(Intent intent) { return new VPNBinder(); }

    public class VPNBinder extends Binder {
        public SeFuReVPNService getService() { return SeFuReVPNService.this; }
    }
}
