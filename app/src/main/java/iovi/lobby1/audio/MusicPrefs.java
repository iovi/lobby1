package iovi.lobby1.audio;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

/** Выбранный в настройках музыкальный файл для ночных фаз. */
public final class MusicPrefs {
    private static final String FILE = "settings";
    private static final String KEY_URI = "night_music_uri";
    private static final String KEY_NAME = "night_music_name";

    private MusicPrefs() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    /** null, если музыка не выбрана. */
    public static Uri uri(Context context) {
        String value = prefs(context).getString(KEY_URI, null);
        return value == null ? null : Uri.parse(value);
    }

    /** Имя файла для показа в интерфейсе; null, если музыка не выбрана. */
    public static String name(Context context) {
        return prefs(context).getString(KEY_NAME, null);
    }

    public static void save(Context context, Uri uri) {
        prefs(context).edit()
                .putString(KEY_URI, uri.toString())
                .putString(KEY_NAME, displayName(context, uri))
                .apply();
    }

    public static void clear(Context context) {
        prefs(context).edit().remove(KEY_URI).remove(KEY_NAME).apply();
    }

    private static String displayName(Context context, Uri uri) {
        try (Cursor c = context.getContentResolver().query(
                uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst() && !c.isNull(0)) {
                return c.getString(0);
            }
        } catch (RuntimeException ignored) {
            // Имя — только для показа; при ошибке возьмём хвост URI.
        }
        String last = uri.getLastPathSegment();
        return last != null ? last : uri.toString();
    }
}
