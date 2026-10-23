package com.volume.limiter;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;
        boolean enabled = context.getSharedPreferences("VolumeLimiterPrefs", Context.MODE_PRIVATE)
                .getBoolean("enabled", false);
        if (enabled) {
            context.startForegroundService(new Intent(context, VolumeClampService.class));
        }
    }
}
