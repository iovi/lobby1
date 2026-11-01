package iovi.lobby1.ui;

import android.content.Context;
import android.content.SharedPreferences;

/** Настройки хода партии. */
public final class GamePrefs {
    private static final String FILE = "settings";
    private static final String KEY_NIGHT_TURN = "night_turn_seconds";
    public static final int DEFAULT_NIGHT_TURN = 8;
    public static final int MIN_NIGHT_TURN = 5;
    public static final int MAX_NIGHT_TURN = 30;

    private GamePrefs() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    /** Отсчёт ночного хода в секундах. */
    public static int nightTurnSeconds(Context context) {
        int value = prefs(context).getInt(KEY_NIGHT_TURN, DEFAULT_NIGHT_TURN);
        return Math.max(MIN_NIGHT_TURN, Math.min(MAX_NIGHT_TURN, value));
    }

    public static void saveNightTurnSeconds(Context context, int seconds) {
        prefs(context).edit().putInt(KEY_NIGHT_TURN, seconds).apply();
    }
}