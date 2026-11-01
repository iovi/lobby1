package iovi.lobby1.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import java.util.function.IntConsumer;

import iovi.lobby1.R;
import iovi.lobby1.audio.MusicPrefs;
import iovi.lobby1.audio.NightMusic;

/** Настройки: музыка для ночных фаз и её громкость, время ночного хода. */
public class SettingsActivity extends AppCompatActivity {
    /** Кнопки вариантов → проценты; порядок — как в разметке. */
    private static final int[] LEVEL_IDS = {R.id.level100, R.id.level75, R.id.level50, R.id.level25};
    private static final int[] LEVELS = {100, 75, 50, 25};
    private static final int[] DUCK_IDS = {R.id.duck20, R.id.duck50, R.id.duck80};
    private static final int[] DUCKS = {20, 50, 80};

    private TextView musicName;
    private Button preview;
    private Button clear;
    private TextView nightTurn;
    private Button nightLess;
    private Button nightMore;
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

        // Громкость слышна сразу, если включено прослушивание.
        bindOptions(findViewById(R.id.level), LEVEL_IDS, LEVELS, MusicPrefs.level(this), percent -> {
            MusicPrefs.saveLevel(this, percent);
            music.applyVolume();
        });
        bindOptions(findViewById(R.id.duck), DUCK_IDS, DUCKS, MusicPrefs.duck(this),
                percent -> MusicPrefs.saveDuck(this, percent));

        nightTurn = findViewById(R.id.nightTurn);
        nightLess = findViewById(R.id.nightLess);
        nightMore = findViewById(R.id.nightMore);
        nightLess.setOnClickListener(v -> changeNightTurn(-1));
        nightMore.setOnClickListener(v -> changeNightTurn(1));
        refresh();
    }

    /** Отмечает сохранённый вариант (если его нет среди вариантов — ничего) и сохраняет выбор. */
    private static void bindOptions(RadioGroup group, int[] ids, int[] values, int current, IntConsumer save) {
        for (int i = 0; i < ids.length; i++) {
            if (values[i] == current) {
                group.check(ids[i]);
            }
        }
        group.setOnCheckedChangeListener((g, checkedId) -> {
            for (int i = 0; i < ids.length; i++) {
                if (ids[i] == checkedId) {
                    save.accept(values[i]);
                }
            }
        });
    }

    private void changeNightTurn(int delta) {
        GamePrefs.saveNightTurnSeconds(this, GamePrefs.nightTurnSeconds(this) + delta);
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
        int seconds = GamePrefs.nightTurnSeconds(this);
        nightTurn.setText(getString(R.string.settings_night_value, seconds));
        nightLess.setEnabled(seconds > GamePrefs.MIN_NIGHT_TURN);
        nightMore.setEnabled(seconds < GamePrefs.MAX_NIGHT_TURN);
    }

    @Override
    protected void onStop() {
        super.onStop();
        music.stop();
        refresh();
    }
}
