package iovi.lobby1.flow.phase;

import static iovi.lobby1.flow.phase.Phrases.t;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import iovi.lobby1.flow.GameUi;
import iovi.lobby1.game.Game;

/**
 * Выбывание: игроки выходят из игры, и каждого по очереди провожают — лучший ход (если положен),
 * прощальная минута, протокол (если положен). После выбывания можно перейти в мануальный режим.
 */
public final class FarewellPhase extends Phase {
    private static final long FAREWELL_MS = 60_000;

    /** Чем закончились проводы. */
    public enum Outcome {
        /** Все проводы закончены — партия продолжается (или победитель уже есть). */
        NEXT,
        /** Ведущий перевёл игру в мануальный режим. */
        MANUAL
    }

    private List<Integer> leaving;
    private boolean withBestMove;
    private boolean withProtocol;
    private Consumer<Outcome> done;

    public FarewellPhase(Host host) {
        super(host);
    }

    /**
     * Единственный заголосованный в нулевом круге (первый день) делает лучший ход и оставляет протокол;
     * поднятые вместе в первый день и заголосованные в остальные дни — только прощальная минута.
     */
    public static boolean lastWords(Game game, List<Integer> votedOut) {
        return game.dayNumber() == 1 && votedOut.size() == 1;
    }

    public void run(List<Integer> players, boolean withBestMove, boolean withProtocol, Consumer<Outcome> done) {
        this.leaving = new ArrayList<>(players);
        this.withBestMove = withBestMove;
        this.withProtocol = withProtocol;
        this.done = done;
        for (int x : leaving) {
            game.eliminate(x);
        }
        farewell(0);
    }

    private void farewell(int i) {
        if (i >= leaving.size()) {
            done.accept(Outcome.NEXT);
            return;
        }
        int x = leaving.get(i);
        Consumer<Outcome> next = outcome -> {
            if (outcome == Outcome.MANUAL) {
                done.accept(outcome);
            } else {
                farewell(i + 1);
            }
        };
        Runnable speech = () -> farewellSpeech(x, withProtocol
                ? () -> new ProtocolPhase(host).run(x, next)
                : () -> left(x, next));
        if (withBestMove) {
            new BestMovePhase(host).run(x, speech);
        } else {
            speech.run();
        }
    }

    private void farewellSpeech(int x, Runnable then) {
        voice.say(t("voice.farewell", x));
        GameUi.TimerView view = screens.timer(t("farewell.title"), t("farewell.subtitle", x), FAREWELL_MS, true,
                t("voice.farewellEnd", x), then);
        fouls.offerExtraTime(x, view, t("foul.at.farewell", x), true);
        // Фол на паузе — только оставшимся за столом: уходящий уже выбыл.
        fouls.onPause(t("foul.at.farewell", x), () -> { });
    }

    /**
     * Выбывший без протокола покинул стол: здесь, как и после протокола, можно перейти в мануальный режим.
     * Если победитель уже есть, экран не нужен — игра закончится и так.
     */
    private void left(int x, Consumer<Outcome> next) {
        if (game.winner() != Game.Winner.NONE) {
            next.accept(Outcome.NEXT);
            return;
        }
        GameUi.MessageView view = screens.message(t("left.title"), t("left.text", x), t("left.next"),
                () -> next.accept(Outcome.NEXT));
        screens.offerManual(view.showExtra(t("manual.button")), () -> next.accept(Outcome.MANUAL));
    }
}