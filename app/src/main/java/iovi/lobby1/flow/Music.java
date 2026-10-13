package iovi.lobby1.flow;

/** Фоновая музыка ночных фаз. */
public interface Music {
    /** Запускает музыку; повторный вызов, пока она играет, ничего не делает. */
    void start();

    void stop();
}