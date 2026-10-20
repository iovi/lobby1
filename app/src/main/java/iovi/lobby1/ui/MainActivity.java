package iovi.lobby1.ui;

import android.app.ActivityManager;
import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;

import iovi.lobby1.R;
import iovi.lobby1.audio.MusicPrefs;

/** Главное меню: новая игра, настройки и закрепление приложения. */
public class MainActivity extends AppCompatActivity {
    private static final int PIN_CHECKS = 20;
    private static final long PIN_CHECK_MS = 300;

    private TextView musicStatus;
    private MaterialButton pin;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        musicStatus = findViewById(R.id.musicStatus);
        pin = findViewById(R.id.pin);
        findViewById(R.id.newGame).setOnClickListener(
                v -> startActivity(new Intent(this, GameActivity.class)));
        findViewById(R.id.settings).setOnClickListener(
                v -> startActivity(new Intent(this, SettingsActivity.class)));
        // Закреплённое приложение нельзя случайно свернуть, и система не выгрузит партию из памяти.
        // Android сам спросит подтверждение; открепляет пользователь средствами системы.
        pin.setOnClickListener(v -> {
            try {
                startLockTask();
            } catch (RuntimeException e) {
                // Закрепление недоступно на этом телефоне.
                return;
            }
            watchPin(PIN_CHECKS);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        String name = MusicPrefs.name(this);
        musicStatus.setText(name == null
                ? getString(R.string.music_none)
                : getString(R.string.music_selected, name));
        updatePin();
    }

    /**
     * Система закрепляет не сразу и не сообщает об этом, поэтому после нажатия несколько секунд
     * проверяем состояние, пока кнопка не покажет «Приложение закреплено».
     */
    private void watchPin(int checksLeft) {
        if (!updatePin() && checksLeft > 0) {
            pin.postDelayed(() -> watchPin(checksLeft - 1), PIN_CHECK_MS);
        }
    }

    /** Обновляет кнопку по текущему состоянию; true — приложение закреплено. */
    private boolean updatePin() {
        ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        boolean pinned = am != null && am.getLockTaskModeState() != ActivityManager.LOCK_TASK_MODE_NONE;
        pin.setEnabled(!pinned);
        pin.setText(pinned ? R.string.pinned : R.string.pin);
        return pinned;
    }
}
