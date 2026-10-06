package iovi.lobby1.audio;

import android.content.Context;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Голос судьи: озвучивает объявления через системный синтез речи (русский язык)
 * и подаёт короткие звуковые сигналы. Сообщает, когда говорит, — чтобы приглушать музыку.
 */
public final class Announcer {
    private static final String TAG = "Announcer";

    public interface Listener {
        /** Голос начал или закончил говорить; вызывается в главном потоке. */
        void onSpeakingChanged(boolean speaking);

        /** Русский голос недоступен — объявления будут только текстом. */
        void onVoiceUnavailable();
    }

    private final Handler main = new Handler(Looper.getMainLooper());
    private final Listener listener;
    private final List<String> pending = new ArrayList<>();
    private final TextToSpeech tts;
    private ToneGenerator tones;
    private boolean ready = false;
    private boolean failed = false;
    private int activeUtterances = 0;
    private int utteranceSeq = 0;

    public Announcer(Context context, Listener listener) {
        this.listener = listener;
        try {
            tones = new ToneGenerator(AudioManager.STREAM_MUSIC, 100);
        } catch (RuntimeException e) {
            Log.w(TAG, "ToneGenerator недоступен", e);
        }
        tts = new TextToSpeech(context.getApplicationContext(), status -> main.post(() -> onInit(status)));
    }

    private void onInit(int status) {
        if (status != TextToSpeech.SUCCESS) {
            fail();
            return;
        }
        int lang = tts.setLanguage(Locale.forLanguageTag("ru-RU"));
        if (lang == TextToSpeech.LANG_MISSING_DATA || lang == TextToSpeech.LANG_NOT_SUPPORTED) {
            fail();
            return;
        }
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override
            public void onStart(String id) {
            }

            @Override
            public void onDone(String id) {
                main.post(Announcer.this::utteranceFinished);
            }

            @Override
            public void onError(String id) {
                main.post(Announcer.this::utteranceFinished);
            }

            @Override
            public void onStop(String id, boolean interrupted) {
                main.post(Announcer.this::utteranceFinished);
            }
        });
        ready = true;
        for (String text : pending) {
            speak(text, TextToSpeech.QUEUE_ADD);
        }
        pending.clear();
    }

    private void fail() {
        failed = true;
        pending.clear();
        listener.onVoiceUnavailable();
    }

    /** Добавляет фразу в очередь объявлений. */
    public void say(String text) {
        if (failed || text == null || text.isEmpty()) {
            return;
        }
        if (!ready) {
            pending.add(text);
            return;
        }
        speak(text, TextToSpeech.QUEUE_ADD);
    }

    /** Прерывает текущие объявления и сразу произносит фразу. */
    public void sayNow(String text) {
        if (failed || text == null || text.isEmpty()) {
            return;
        }
        if (!ready) {
            pending.clear();
            pending.add(text);
            return;
        }
        speak(text, TextToSpeech.QUEUE_FLUSH);
    }

    private void speak(String text, int mode) {
        String id = "u" + (utteranceSeq++);
        // При QUEUE_FLUSH сброшенные фразы отчитаются через onStop, так что счётчик сойдётся.
        if (tts.speak(text, mode, new Bundle(), id) == TextToSpeech.SUCCESS) {
            activeUtterances++;
            if (activeUtterances == 1) {
                listener.onSpeakingChanged(true);
            }
        }
    }

    private void utteranceFinished() {
        if (activeUtterances == 0) {
            return;
        }
        activeUtterances--;
        if (activeUtterances == 0) {
            listener.onSpeakingChanged(false);
        }
    }

    /** Короткий сигнал — например, «осталось 10 секунд». */
    public void beep() {
        if (tones != null) {
            tones.startTone(ToneGenerator.TONE_PROP_BEEP, 150);
        }
    }

    /** Длинный сигнал окончания отведённого времени. */
    public void endSignal() {
        if (tones != null) {
            tones.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 600);
        }
    }

    public void shutdown() {
        main.removeCallbacksAndMessages(null);
        tts.stop();
        tts.shutdown();
        if (tones != null) {
            tones.release();
            tones = null;
        }
    }
}
