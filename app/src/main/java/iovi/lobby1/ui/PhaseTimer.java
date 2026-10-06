package iovi.lobby1.ui;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

/** Обратный отсчёт с паузой и сигналом «осталось 10 секунд». Работает в главном потоке. */
final class PhaseTimer {
    static final long WARNING_MS = 10_000;
    private static final long TICK_MS = 200;

    interface Listener {
        void onTick(long remainingMs);

        void onWarning();

        void onFinish();
    }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Listener listener;
    private long remainingMs;
    private long lastTickAt;
    private boolean running = false;
    private boolean warned = false;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            long now = SystemClock.elapsedRealtime();
            remainingMs -= now - lastTickAt;
            lastTickAt = now;
            if (remainingMs <= 0) {
                running = false;
                Listener l = listener;
                listener = null;
                l.onTick(0);
                l.onFinish();
                return;
            }
            if (!warned && remainingMs <= WARNING_MS) {
                warned = true;
                listener.onWarning();
            }
            listener.onTick(remainingMs);
            handler.postDelayed(this, TICK_MS);
        }
    };

    void start(long durationMs, Listener listener) {
        cancel();
        this.listener = listener;
        this.remainingMs = durationMs;
        // Для коротких отрезков (оправдательные 30 с) предупреждение тоже уместно, для 20 с — нет.
        this.warned = durationMs <= 2 * WARNING_MS;
        listener.onTick(durationMs);
        resume();
    }

    void pause() {
        if (!running) {
            return;
        }
        handler.removeCallbacks(tick);
        remainingMs -= SystemClock.elapsedRealtime() - lastTickAt;
        running = false;
    }

    void resume() {
        if (running || listener == null) {
            return;
        }
        running = true;
        lastTickAt = SystemClock.elapsedRealtime();
        handler.postDelayed(tick, TICK_MS);
    }

    boolean isRunning() {
        return running;
    }

    void cancel() {
        handler.removeCallbacks(tick);
        running = false;
        listener = null;
    }

    static String format(long ms) {
        long seconds = (ms + 999) / 1000;
        return String.format(java.util.Locale.US, "%d:%02d", seconds / 60, seconds % 60);
    }
}
