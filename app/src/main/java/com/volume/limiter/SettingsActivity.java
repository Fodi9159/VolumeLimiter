package com.volume.limiter;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

public class SettingsActivity extends Activity {

    private static final String PREFS_NAME = "VolumeLimiterPrefs";
    private static final String KEY_MAX_VOLUME = "max_volume";
    private static final String KEY_ENABLED = "enabled";
    private static final int DEFAULT_MAX_VOLUME = 50;
    private static final int MAX_SEEKBAR = 100;

    private SharedPreferences prefs;
    private AudioManager audioManager;
    private SeekBar seekBar;
    private EditText editText;
    private TextView currentVolumeText;
    private Button startStopButton;
    private Button openA11yButton;
    private Switch iconSwitch;
    private boolean ignoreChanges = false;
    private int pendingVolume = DEFAULT_MAX_VOLUME;
    private final Handler saveHandler = new Handler(Looper.getMainLooper());
    private final Runnable saveRunnable = () -> saveVolume(pendingVolume);

    private boolean isTv() {
        return (getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_TYPE_MASK) == Configuration.UI_MODE_TYPE_TELEVISION;
    }

    private boolean isClampRunning() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
            for (ActivityManager.RunningServiceInfo svc : am.getRunningServices(Integer.MAX_VALUE)) {
                if (VolumeClampService.class.getName().equals(svc.service.getClassName())) {
                    return true;
                }
            }
        }
        return prefs.getBoolean(KEY_ENABLED, false);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if (isTv()) {
            setTheme(R.style.Theme_Leanback);
        } else {
            setTheme(R.style.Theme_VolumeLimiter_Phone);
        }
        super.onCreate(savedInstanceState);
        setContentView(isTv() ? R.layout.activity_settings : R.layout.activity_settings_phone);

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);

        seekBar = findViewById(R.id.seekBar);
        editText = findViewById(R.id.editText);
        currentVolumeText = findViewById(R.id.currentVolumeText);
        startStopButton = findViewById(R.id.startStopButton);
        openA11yButton = findViewById(R.id.openA11yButton);
        iconSwitch = findViewById(R.id.iconSwitch);

        int savedVolume = prefs.getInt(KEY_MAX_VOLUME, DEFAULT_MAX_VOLUME);
        seekBar.setProgress(savedVolume);
        editText.setText(String.valueOf(savedVolume));

        int currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        currentVolumeText.setText("Current volume: " + currentVolume + " / " + MAX_SEEKBAR);

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                if (!ignoreChanges) {
                    ignoreChanges = true;
                    editText.setText(String.valueOf(progress));
                    ignoreChanges = false;
                }
                if (fromUser) {
                    pendingVolume = progress;
                    saveHandler.removeCallbacks(saveRunnable);
                    saveHandler.postDelayed(saveRunnable, 200);
                    if (!isClampRunning()) {
                        prefs.edit().putBoolean(KEY_ENABLED, true).apply();
                        startClamp();
                    }
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar sb) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar sb) {
                saveVolume(sb.getProgress());
            }
        });

        editText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (ignoreChanges) return;
                try {
                    int value = Integer.parseInt(s.toString());
                    if (value < 0) value = 0;
                    if (value > MAX_SEEKBAR) value = MAX_SEEKBAR;
                    ignoreChanges = true;
                    seekBar.setProgress(value);
                    ignoreChanges = false;
                    saveVolume(value);
                } catch (NumberFormatException ignored) {
                }
            }
        });

        updateStartStopButton();
        startStopButton.setOnClickListener(v -> toggleClamp());

        openA11yButton.setOnClickListener(v ->
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));

        iconSwitch.setChecked(isLauncherIconEnabled());
        iconSwitch.setOnCheckedChangeListener((btn, checked) -> setLauncherIconVisible(checked));
    }

    @Override
    protected void onResume() {
        super.onResume();
        int currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        currentVolumeText.setText("Current volume: " + currentVolume + " / " + MAX_SEEKBAR);
        updateStartStopButton();
        int savedVolume = prefs.getInt(KEY_MAX_VOLUME, DEFAULT_MAX_VOLUME);
        if (savedVolume > 0 && !isClampRunning()) {
            prefs.edit().putBoolean(KEY_ENABLED, true).apply();
            startClamp();
            updateStartStopButton();
        }
    }

    private void saveVolume(int volume) {
        prefs.edit().putInt(KEY_MAX_VOLUME, volume).apply();
    }

    private void toggleClamp() {
        if (isClampRunning()) {
            prefs.edit().putBoolean(KEY_ENABLED, false).apply();
            stopService(new Intent(this, VolumeClampService.class));
        } else {
            prefs.edit().putBoolean(KEY_ENABLED, true).apply();
            startClamp();
        }
        updateStartStopButton();
    }

    private void startClamp() {
        Intent i = new Intent(this, VolumeClampService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(i);
        } else {
            startService(i);
        }
    }

    private void updateStartStopButton() {
        startStopButton.setText(isClampRunning()
                ? R.string.stop_protection : R.string.start_protection);
    }

    private boolean isLauncherIconEnabled() {
        int state = getPackageManager().getComponentEnabledSetting(
                new ComponentName(this, LauncherActivity.class));
        return state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                || state == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT;
    }

    private void setLauncherIconVisible(boolean visible) {
        int state = visible ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                : PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
        getPackageManager().setComponentEnabledSetting(
                new ComponentName(this, LauncherActivity.class), state,
                PackageManager.DONT_KILL_APP);
    }
}
