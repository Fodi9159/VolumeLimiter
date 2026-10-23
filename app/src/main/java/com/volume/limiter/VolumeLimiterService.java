package com.volume.limiter;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.os.Build;
import android.util.Log;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

public class VolumeLimiterService extends AccessibilityService {

    private static final String TAG = "VolLimiter";
    private static final String PREFS_NAME = "VolumeLimiterPrefs";
    private static final String KEY_MAX_VOLUME = "max_volume";
    private static final String KEY_ENABLED = "enabled";
    private static final int DEFAULT_MAX_VOLUME = 50;

    private AudioManager audioManager;
    private SharedPreferences prefs;

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        Log.d(TAG, "onAccessibilityEvent: type=" + event.getEventType() + " source=" + (event.getSource() != null ? event.getSource().getClassName() : "null"));
    }

    @Override
    public void onInterrupt() {
        Log.d(TAG, "onInterrupt");
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        Log.d(TAG, "onServiceConnected: max=" + prefs.getInt(KEY_MAX_VOLUME, DEFAULT_MAX_VOLUME) + " enabled=" + prefs.getBoolean(KEY_ENABLED, false));

        // Enabling accessibility = full protection + high priority (Android won't kill it).
        prefs.edit().putBoolean(KEY_ENABLED, true).apply();
        Intent clamp = new Intent(this, VolumeClampService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(clamp);
        } else {
            startService(clamp);
        }
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        Log.d(TAG, "onKeyEvent: keyCode=" + event.getKeyCode() + " action=" + event.getAction() + " source=" + event.getSource());
        if (audioManager == null || prefs == null) {
            return super.onKeyEvent(event);
        }

        int keyCode = event.getKeyCode();
        if (keyCode != KeyEvent.KEYCODE_VOLUME_UP && keyCode != KeyEvent.KEYCODE_VOLUME_DOWN) {
            return super.onKeyEvent(event);
        }

        int maxVolume = prefs.getInt(KEY_MAX_VOLUME, DEFAULT_MAX_VOLUME);
        int currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        Log.d(TAG, "onKeyEvent: max=" + maxVolume + " musicVol=" + currentVolume);

        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            int[] streams = {
                    AudioManager.STREAM_VOICE_CALL,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.STREAM_NOTIFICATION,
                    AudioManager.STREAM_SYSTEM
            };
            boolean over = false;
            for (int stream : streams) {
                if (audioManager.getStreamVolume(stream) >= maxVolume) {
                    over = true;
                    break;
                }
            }
            Log.d(TAG, "onKeyEvent: over=" + over);
            if (over) {
                for (int stream : streams) {
                    audioManager.setStreamVolume(stream, maxVolume, 0);
                }
                return true;
            }
        }

        return super.onKeyEvent(event);
    }
}
