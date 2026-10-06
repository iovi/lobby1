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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    /** Предел ожидания конца фразы, после которого игра продолжается без него. */
    private static final long DONE_TIMEOUT_MS = 15_000;

    private static final class Pending {
        final String text;
        final Runnable onDone;

        Pending(String text, Runnable onDone) {
            this.text = text;
            this.onDone = onDone;
        }
    }

    private final List<Pending> pending = new ArrayList<>();
    /** Колбэки окончания фраз по id высказывания. */
    private final Map<String, Runnable> callbacks = new HashMap<>();
    private final TextToSpeech tts;
    private ToneGenerator tones;
    private boolean ready = false;
    private boolean failed = false;
    private boolean shutDown = false;
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
        if (shutDown) {
            return;
        }
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
                main.post(() -> utteranceFinished(id));
            }

            @Override
            public void onError(String id) {
                main.post(() -> utteranceFinished(id));
            }

            @Override
            public void onStop(String id, boolean interrupted) {
                main.post(() -> utteranceFinished(id));
            }
        });
        ready = true;
        for (Pending p : pending) {
            speak(p.text, p.onDone);
        }
        pending.clear();
    }

    private void fail() {
        failed = true;
        // Голоса не будет — те, кто ждал конца фразы, продолжают сразу.
        for (Pending p : pending) {
            if (p.onDone != null) {
                p.onDone.run();
            }
        }
        pending.clear();
        listener.onVoiceUnavailable();
    }

    /** Добавляет фразу в очередь объявлений. */
    public void say(String text) {
        say(text, null);
    }

    /**
     * Добавляет фразу в очередь и вызывает onDone (в главном потоке), когда она прозвучит.
     * Если голос недоступен или движок не ответил за {@link #DONE_TIMEOUT_MS}, onDone
     * всё равно вызывается — игра не должна зависнуть из-за синтеза речи.
     */
    public void say(String text, Runnable onDone) {
        Runnable guarded = onDone == null ? null : onceWithTimeout(onDone);
        if (failed || text == null || text.isEmpty()) {
            if (guarded != null) {
                main.post(guarded);
            }
            return;
        }
        if (!ready) {
            pending.add(new Pending(text, guarded));
            return;
        }
        speak(text, guarded);
    }

    private Runnable onceWithTimeout(Runnable action) {
        boolean[] done = {false};
        Runnable guarded = () -> {
            if (!done[0]) {
                done[0] = true;
                action.run();
            }
        };
        main.postDelayed(guarded, DONE_TIMEOUT_MS);
        return guarded;
    }

    private void speak(String text, Runnable onDone) {
        String id = "u" + (utteranceSeq++);
        if (tts.speak(text, TextToSpeech.QUEUE_ADD, new Bundle(), id) == TextToSpeech.SUCCESS) {
            if (onDone != null) {
                callbacks.put(id, onDone);
            }
            activeUtterances++;
            if (activeUtterances == 1) {
                listener.onSpeakingChanged(true);
            }
        } else if (onDone != null) {
            main.post(onDone);
        }
    }

    private void utteranceFinished(String id) {
        if (shutDown) {
            return;
        }
        Runnable onDone = callbacks.remove(id);
        if (activeUtterances > 0) {
            activeUtterances--;
            if (activeUtterances == 0) {
                listener.onSpeakingChanged(false);
            }
        }
        if (onDone != null) {
            onDone.run();
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
        // Ждущие продолжения после закрытия экрана не нужны: onStop от tts.stop() придёт позже.
        shutDown = true;
        callbacks.clear();
        pending.clear();
        main.removeCallbacksAndMessages(null);
        tts.stop();
        tts.shutdown();
        if (tones != null) {
            tones.release();
            tones = null;
        }
    }
}
