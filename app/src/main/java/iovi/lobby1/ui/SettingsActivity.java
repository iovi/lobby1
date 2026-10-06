package iovi.lobby1.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import iovi.lobby1.R;
import iovi.lobby1.audio.MusicPrefs;
import iovi.lobby1.audio.NightMusic;

/** Настройки: выбор музыкального файла для ночных фаз. */
public class SettingsActivity extends AppCompatActivity {
    private TextView musicName;
    private Button preview;
    private Button clear;
    private NightMusic music;

    private final ActivityResultLauncher<String[]> picker = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(), this::onFilePicked);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        musicName = findViewById(R.id.musicName);
        preview = findViewById(R.id.preview);
        clear = findViewById(R.id.clear);
        music = new NightMusic(this, () -> {
            Toast.makeText(this, R.string.music_error, Toast.LENGTH_LONG).show();
            refresh();
        });

        findViewById(R.id.pick).setOnClickListener(v -> picker.launch(new String[]{"audio/*"}));
        preview.setOnClickListener(v -> {
            if (music.isPlaying()) {
                music.stop();
            } else {
                music.start();
            }
            refresh();
        });
        clear.setOnClickListener(v -> {
            music.stop();
            releasePermission(MusicPrefs.uri(this));
            MusicPrefs.clear(this);
            refresh();
        });
        refresh();
    }

    private void onFilePicked(Uri uri) {
        if (uri == null) {
            return;
        }
        music.stop();
        Uri previous = MusicPrefs.uri(this);
        try {
            // Без постоянного разрешения доступ к файлу пропадёт после перезапуска приложения.
            getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException ignored) {
            // Некоторые провайдеры не дают постоянного доступа — попробуем работать как есть.
        }
        if (previous != null && !previous.equals(uri)) {
            releasePermission(previous);
        }
        MusicPrefs.save(this, uri);
        refresh();
    }

    private void releasePermission(Uri uri) {
        if (uri == null) {
            return;
        }
        try {
            getContentResolver().releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException ignored) {
            // Разрешения и так не было.
        }
    }

    private void refresh() {
        String name = MusicPrefs.name(this);
        boolean hasMusic = name != null;
        musicName.setText(hasMusic ? name : getString(R.string.music_none));
        preview.setEnabled(hasMusic);
        clear.setEnabled(hasMusic);
        preview.setText(music.isPlaying() ? R.string.settings_stop : R.string.settings_preview);
    }

    @Override
    protected void onStop() {
        super.onStop();
        music.stop();
        refresh();
    }
}
