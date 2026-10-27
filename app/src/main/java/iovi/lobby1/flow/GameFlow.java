package iovi.lobby1.flow;

import static iovi.lobby1.flow.phase.Phrases.t;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import iovi.lobby1.flow.phase.DayPhase;
import iovi.lobby1.flow.phase.DealingPhase;
import iovi.lobby1.flow.phase.FarewellPhase;
import iovi.lobby1.flow.phase.Host;
import iovi.lobby1.flow.phase.ManualPhase;
import iovi.lobby1.flow.phase.MorningPhase;
import iovi.lobby1.flow.phase.NightPhase;
import iovi.lobby1.flow.phase.VotingPhase;
import iovi.lobby1.flow.phase.ZeroNightPhase;
import iovi.lobby1.game.Game;

/**
 * Ход партии: раздача карт → договорка и свободная посадка → дни и ночи до победы.
 * Сами фазы — в пакете {@link iovi.lobby1.flow.phase}; здесь только их порядок: каждая получает
 * нужные данные, а её результат решает, какая фаза следующая. Мимо порядка партию заканчивают
 * удаление по фолам с победой и переход в мануальный режим.
 */
public final class GameFlow {
    private final Game game;
    private final GameUi ui;
    private final Voice voice;
    private final Music music;
    private final Host host;
    private boolean over = false;
    /** Мануальный режим: игру ведёт человек, телефон помогает. */
    private boolean manual;
    /** Идёт день — в строке фазы число игроков за столом. */
    private boolean inDay;

    public GameFlow(Game game, GameUi ui, Voice voice, Music music, Scheduler scheduler) {
        this.game = game;
        this.ui = ui;
        this.voice = voice;
        this.music = music;
        this.host = new Host(game, ui, voice, music, scheduler, this::onFoulRemoval);
    }

    public void start() {
        new DealingPhase(host).run(() -> new ZeroNightPhase(host).run(this::day));
    }

    public boolean isOver() {
        return over;
    }

    /** Партия переведена в мануальный режим — продолжить её в приложении нельзя. */
    public boolean isManual() {
        return manual;
    }

    /** Отменяет таймер и отложенные действия — экран игры закрывается. */
    public void stop() {
        host.screens.newScreen();
    }

    /** День: речи → голосование → проводы заголосованных → ночь. */
    private void day() {
        inDay = true;
        new DayPhase(host).run(nominations -> new VotingPhase(host).run(nominations, this::votedOut));
    }

    /**
     * Единственный заголосованный в нулевом круге делает лучший ход и оставляет протокол; остальные —
     * только прощальная минута. Удалённый по фолам на экране итогов уже покинул стол — его не провожают.
     */
    private void votedOut(List<Integer> players) {
        if (players.isEmpty()) {
            night();
            return;
        }
        boolean lastWords = FarewellPhase.lastWords(game, players);
        List<Integer> leaving = new ArrayList<>();
        for (int x : players) {
            if (game.isAlive(x)) {
                leaving.add(x);
            }
        }
        farewell(leaving, lastWords, lastWords, this::night);
    }

    /** Ночь → утро. Убитый ночью оставляет протокол, убитый в первую ночь ещё и делает лучший ход. */
    private void night() {
        inDay = false;
        host.fouls.newNight();
        new NightPhase(host).run(shots -> new MorningPhase(host).run(shots, killed -> {
            if (killed == null) {
                day();
            } else {
                farewell(Collections.singletonList(killed), game.dayNumber() == 1, true, this::day);
            }
        }));
    }

    /** Проводы выбывших, затем проверка победы; next — если игра продолжается. */
    private void farewell(List<Integer> players, boolean withBestMove, boolean withProtocol, Runnable next) {
        new FarewellPhase(host).run(players, withBestMove, withProtocol, outcome -> {
            if (outcome == FarewellPhase.Outcome.MANUAL) {
                startManual();
                return;
            }
            Game.Winner winner = game.winner();
            if (winner == Game.Winner.NONE) {
                next.run();
            } else {
                showGameOver(winner);
            }
        });
    }

    /**
     * Кого-то удалили по фолам: днём обновляется число игроков за столом, при победе игра заканчивается.
     * false — партия закончилась. В мануальном режиме удаление ничего не меняет.
     */
    private boolean onFoulRemoval() {
        if (manual) {
            return true;
        }
        if (inDay) {
            ui.setPhase(t("phase.day", game.dayNumber(), game.aliveCount()));
        }
        Game.Winner winner = game.winner();
        if (winner != Game.Winner.NONE) {
            showGameOver(winner);
            return false;
        }
        return true;
    }

    private void startManual() {
        manual = true;
        inDay = false;
        new ManualPhase(host).run();
    }

    /** Итог партии: раздел «Итог» в протоколе, объявление и экран с логом. */
    private void showGameOver(Game.Winner winner) {
        String title = winner == Game.Winner.MAFIA ? t("result.mafia") : t("result.civilians");
        over = true;
        music.stop();
        game.log().section(t("log.result"));
        game.log().add(title);
        host.summaries.writeTo(game.log());
        voice.say(t("voice.gameOver", title));
        ui.setPhase(t("phase.over"));
        host.screens.newScreen();
        ui.showGameOver(title, game.log().toString());
    }
}