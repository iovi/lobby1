package iovi.lobby1.flow;

/** Отложенные действия и часы. На Android — главный поток, в тестах — виртуальное время. */
public interface Scheduler {
    void post(Runnable action, long delayMs);

    void cancel(Runnable action);

    /** Отменяет все отложенные действия. */
    void cancelAll();

    /** Монотонное время в миллисекундах. */
    long now();
}