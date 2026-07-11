package com.sefure.vpn;

import android.content.Context;

public class VPNManager {
    private Context context;

    public VPNManager(Context context) {
        this.context = context;
    }

    public float[] getCurrentSpeed() {
        return new float[]{
            SeFuReVPNService.currentUploadSpeed,
            SeFuReVPNService.currentDownloadSpeed
        };
    }

    public String getTotalDataUsage() {
        long totalBytes = SeFuReVPNService.totalDownload + SeFuReVPNService.totalUpload;
        if (totalBytes < 1024 * 1024) {
            return String.format("%.1f KB", totalBytes / 1024f);
        } else {
            return String.format("%.2f MB", totalBytes / (1024f * 1024f));
        }
    }
}
