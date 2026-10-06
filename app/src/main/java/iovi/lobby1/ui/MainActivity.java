package iovi.lobby1.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import iovi.lobby1.R;
import iovi.lobby1.audio.MusicPrefs;

/** Главное меню: новая игра и настройки. */
public class MainActivity extends AppCompatActivity {
    private TextView musicStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        musicStatus = findViewById(R.id.musicStatus);
        findViewById(R.id.newGame).setOnClickListener(
                v -> startActivity(new Intent(this, GameActivity.class)));
        findViewById(R.id.settings).setOnClickListener(
                v -> startActivity(new Intent(this, SettingsActivity.class)));
    }

    @Override
    protected void onResume() {
        super.onResume();
        String name = MusicPrefs.name(this);
        musicStatus.setText(name == null
                ? getString(R.string.music_none)
                : getString(R.string.music_selected, name));
    }
}
