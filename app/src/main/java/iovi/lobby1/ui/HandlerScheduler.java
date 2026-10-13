package iovi.lobby1.ui;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import iovi.lobby1.flow.Scheduler;

/** Отложенные действия в главном потоке. */
final class HandlerScheduler implements Scheduler {
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    public void post(Runnable action, long delayMs) {
        handler.postDelayed(action, delayMs);
    }

    @Override
    public void cancel(Runnable action) {
        handler.removeCallbacks(action);
    }

    @Override
    public void cancelAll() {
        handler.removeCallbacksAndMessages(null);
    }

    @Override
    public long now() {
        return SystemClock.elapsedRealtime();
    }
}