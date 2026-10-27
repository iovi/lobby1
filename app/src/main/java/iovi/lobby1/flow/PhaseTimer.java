package iovi.lobby1.flow;

import java.util.Locale;

/** Обратный отсчёт с паузой и сигналом «осталось 10 секунд». */
public final class PhaseTimer {
    public static final long WARNING_MS = 10_000;
    private static final long TICK_MS = 200;

    public interface Listener {
        void onTick(long remainingMs);

        void onWarning();

        void onFinish();
    }

    private final Scheduler scheduler;
    private Listener listener;
    private long remainingMs;
    private long lastTickAt;
    private boolean running = false;
    private boolean warned = false;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            long now = scheduler.now();
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
            scheduler.post(this, TICK_MS);
        }
    };

    public PhaseTimer(Scheduler scheduler) {
        this.scheduler = scheduler;
    }

    public void start(long durationMs, Listener listener) {
        cancel();
        this.listener = listener;
        this.remainingMs = durationMs;
        // Для коротких отрезков (оправдательные 30 с) предупреждение тоже уместно, для 20 с — нет.
        this.warned = durationMs <= 2 * WARNING_MS;
        listener.onTick(durationMs);
        resume();
    }

    public void pause() {
        if (!running) {
            return;
        }
        scheduler.cancel(tick);
        remainingMs -= scheduler.now() - lastTickAt;
        running = false;
    }

    public void resume() {
        if (running || listener == null) {
            return;
        }
        running = true;
        lastTickAt = scheduler.now();
        scheduler.post(tick, TICK_MS);
    }

    /** Добавляет время к идущему или стоящему на паузе отсчёту; «10 секунд» прозвучит снова. */
    public void extend(long ms) {
        if (listener == null) {
            return;
        }
        remainingMs += ms;
        long current = running ? remainingMs - (scheduler.now() - lastTickAt) : remainingMs;
        if (current > WARNING_MS) {
            warned = false;
        }
        listener.onTick(current);
    }

    public boolean isRunning() {
        return running;
    }

    public void cancel() {
        scheduler.cancel(tick);
        running = false;
        listener = null;
    }

    /** «1:05» — оставшееся время для экрана, с округлением вверх до секунды. */
    public static String format(long ms) {
        long seconds = (ms + 999) / 1000;
        return String.format(Locale.US, "%d:%02d", seconds / 60, seconds % 60);
    }
}