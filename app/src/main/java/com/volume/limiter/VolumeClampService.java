package com.volume.limiter;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

public class VolumeClampService extends Service {

    private static final String TAG = "VolClamp";
    private static final String PREFS_NAME = "VolumeLimiterPrefs";
    private static final String KEY_MAX_VOLUME = "max_volume";
    private static final int DEFAULT_MAX_VOLUME = 50;
    private static final long POLL_INTERVAL_MS = 200;
    private static final String CHANNEL_ID = "vol_limiter";

    private AudioManager audioManager;
    private SharedPreferences prefs;
    private Handler handler;
    private Runnable poller;
    private BroadcastReceiver volumeReceiver;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate");
        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        handler = new Handler(Looper.getMainLooper());

        startForeground(1, buildNotification());
        registerVolumeReceiver();

        poller = () -> {
            clamp();
            handler.postDelayed(poller, POLL_INTERVAL_MS);
        };
        handler.post(poller);
    }

    private void registerVolumeReceiver() {
        volumeReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent == null) return;
                Log.d(TAG, "onReceive VOLUME_CHANGED: stream=" + intent.getIntExtra("android.media.EXTRA_VOLUME_STREAM", -1));
                clamp();
            }
        };
        registerReceiver(volumeReceiver, new IntentFilter("android.media.VOLUME_CHANGED_ACTION"));
    }

    private void clamp() {
        if (audioManager == null || prefs == null) return;
        int maxVolume = prefs.getInt(KEY_MAX_VOLUME, DEFAULT_MAX_VOLUME);
        int[] streams = {
                AudioManager.STREAM_VOICE_CALL,
                AudioManager.STREAM_MUSIC,
                AudioManager.STREAM_NOTIFICATION,
                AudioManager.STREAM_SYSTEM
        };
        for (int stream : streams) {
            int current = audioManager.getStreamVolume(stream);
            if (current > maxVolume) {
                Log.d(TAG, "clamp: stream=" + stream + " was=" + current + " -> max=" + maxVolume);
                audioManager.setStreamVolume(stream, maxVolume, 0);
            }
        }
        updateNotification();
    }

    private void updateNotification() {
        if (audioManager == null || prefs == null) return;
        int maxVolume = prefs.getInt(KEY_MAX_VOLUME, DEFAULT_MAX_VOLUME);
        int musicVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        int notifVol = audioManager.getStreamVolume(AudioManager.STREAM_NOTIFICATION);
        int systemVol = audioManager.getStreamVolume(AudioManager.STREAM_SYSTEM);

        StringBuilder sb = new StringBuilder()
                .append("Limiter: ").append(maxVolume)
                .append(" | Music: ").append(musicVol)
                .append(" Notif: ").append(notifVol).append(" System: ").append(systemVol);

        NotificationManager nm = getSystemService(NotificationManager.class);
        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            b = new Notification.Builder(this, CHANNEL_ID);
        } else {
            b = new Notification.Builder(this);
        }
        b.setContentTitle(getString(R.string.app_name))
                .setContentText(sb.toString())
                .setSmallIcon(R.drawable.ic_notification)
                .setOngoing(true);
        nm.notify(1, b.build());
    }

    private Notification buildNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.notification_channel_name),
                    NotificationManager.IMPORTANCE_LOW);
            getSystemService(NotificationManager.class).createNotificationChannel(ch);
        }

        updateNotification();

        Intent tapIntent = new Intent(this, SettingsActivity.class);
        PendingIntent tapPi = PendingIntent.getActivity(
                this, 0, tapIntent, PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            b = new Notification.Builder(this, CHANNEL_ID);
        } else {
            b = new Notification.Builder(this);
        }
        b.setContentTitle(getString(R.string.app_name))
                .setContentText(getString(R.string.notification_text))
                .setSmallIcon(R.drawable.ic_notification)
                .setContentIntent(tapPi)
                .setOngoing(true);
        updateNotification();
        return b.build();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand: startId=" + startId);
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");
        if (handler != null && poller != null) handler.removeCallbacks(poller);
        if (volumeReceiver != null) unregisterReceiver(volumeReceiver);
    }
}
