package iovi.lobby1.flow.phase;

import iovi.lobby1.flow.GameUi;
import iovi.lobby1.flow.Music;
import iovi.lobby1.flow.Scheduler;
import iovi.lobby1.flow.Voice;
import iovi.lobby1.game.Game;

/**
 * Фаза партии. Получает входные данные в run(…) и сообщает результат колбэком, когда закончится;
 * что будет дальше, не знает — порядок фаз собран в {@link iovi.lobby1.flow.GameFlow}. Составная фаза
 * (проводы) может запускать свои части как вложенные фазы. Экземпляр — на один запуск.
 */
abstract class Phase {
    /** Для вложенных фаз. */
    final Host host;
    final Game game;
    final GameUi ui;
    final Voice voice;
    final Music music;
    final Scheduler scheduler;
    final Screens screens;
    final Fouls fouls;
    final Summaries summaries;

    Phase(Host host) {
        this.host = host;
        this.game = host.game;
        this.ui = host.ui;
        this.voice = host.voice;
        this.music = host.music;
        this.scheduler = host.scheduler;
        this.screens = host.screens;
        this.fouls = host.fouls;
        this.summaries = host.summaries;
    }
}