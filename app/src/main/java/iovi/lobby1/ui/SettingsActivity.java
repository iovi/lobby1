package iovi.lobby1.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import java.util.Arrays;
import java.util.function.IntConsumer;

import iovi.lobby1.R;
import iovi.lobby1.audio.MusicPrefs;
import iovi.lobby1.flow.Rules;

/** Настройки: музыка для ночных фаз и её приглушение под объявления, версия правил, раздача, время ночного хода. */
public class SettingsActivity extends AppCompatActivity {
    /** Кнопки вариантов → значения; порядок — как в разметке. */
    private static final int[] RULES_IDS = {R.id.rulesFsm, R.id.rulesMsl};
    private static final Rules[] RULES = {Rules.FSM, Rules.MSL};
    private static final int[] RULES_INDEXES = {0, 1};
    private static final int[] DUCK_IDS = {R.id.duck20, R.id.duck50, R.id.duck80};
    private static final int[] DUCKS = {20, 50, 80};

    private TextView musicName;
    private TextView nightTurn;
    private Button nightLess;
    private Button nightMore;

    private final ActivityResultLauncher<String[]> picker = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(), this::onFilePicked);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        musicName = findViewById(R.id.musicName);

        findViewById(R.id.pick).setOnClickListener(v -> picker.launch(new String[]{"audio/*"}));

        bindOptions(findViewById(R.id.rules), RULES_IDS, RULES_INDEXES,
                Arrays.asList(RULES).indexOf(GamePrefs.rules(this)),
                index -> GamePrefs.saveRules(this, RULES[index]));
        CheckBox autoDeal = findViewById(R.id.autoDeal);
        autoDeal.setChecked(GamePrefs.autoDeal(this));
        autoDeal.setOnCheckedChangeListener((box, checked) -> GamePrefs.saveAutoDeal(this, checked));
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
        try {
            getContentResolver().releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException ignored) {
            // Разрешения и так не было.
        }
    }

    private void refresh() {
        String name = MusicPrefs.name(this);
        musicName.setText(name != null ? name : getString(R.string.music_none));
        int seconds = GamePrefs.nightTurnSeconds(this);
        nightTurn.setText(getString(R.string.settings_night_value, seconds));
        nightLess.setEnabled(seconds > GamePrefs.MIN_NIGHT_TURN);
        nightMore.setEnabled(seconds < GamePrefs.MAX_NIGHT_TURN);
    }
}