package iovi.lobby1.audio;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;
import android.util.Log;

/** Зацикленная фоновая музыка ночных фаз из файла, выбранного в настройках. */
public final class NightMusic {
    private static final String TAG = "NightMusic";
    private static final float FULL_VOLUME = 1.0f;
    private static final float DUCKED_VOLUME = 0.2f;

    public interface ErrorListener {
        void onMusicError();
    }

    private final Context context;
    private final ErrorListener errorListener;
    private MediaPlayer player;
    private boolean prepared = false;
    private boolean ducked = false;
    private boolean paused = false;

    public NightMusic(Context context, ErrorListener errorListener) {
        this.context = context.getApplicationContext();
        this.errorListener = errorListener;
    }

    /** Запускает музыку, если файл выбран; повторный вызов во время игры ничего не делает. */
    public void start() {
        start(MusicPrefs.uri(context));
    }

    public void start(Uri uri) {
        if (player != null || uri == null) {
            return;
        }
        MediaPlayer mp = new MediaPlayer();
        try {
            mp.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build());
            mp.setDataSource(context, uri);
            mp.setLooping(true);
            mp.setOnPreparedListener(p -> {
                prepared = true;
                applyVolume();
                if (!paused) {
                    p.start();
                }
            });
            mp.setOnErrorListener((p, what, extra) -> {
                Log.w(TAG, "Ошибка воспроизведения: " + what + "/" + extra);
                stop();
                errorListener.onMusicError();
                return true;
            });
            mp.prepareAsync();
            player = mp;
            paused = false;
        } catch (Exception e) {
            Log.w(TAG, "Не удалось открыть " + uri, e);
            mp.release();
            errorListener.onMusicError();
        }
    }

    public boolean isPlaying() {
        return player != null;
    }

    public void stop() {
        if (player != null) {
            player.release();
            player = null;
        }
        prepared = false;
        paused = false;
    }

    /** Пауза на время, пока приложение свёрнуто. */
    public void pause() {
        paused = true;
        if (player != null && prepared && player.isPlaying()) {
            player.pause();
        }
    }

    public void resume() {
        paused = false;
        if (player != null && prepared && !player.isPlaying()) {
            player.start();
        }
    }

    /** Приглушает музыку на время объявлений. */
    public void setDucked(boolean ducked) {
        this.ducked = ducked;
        applyVolume();
    }

    private void applyVolume() {
        if (player != null && prepared) {
            float v = ducked ? DUCKED_VOLUME : FULL_VOLUME;
            player.setVolume(v, v);
        }
    }
}
