package iovi.lobby1.flow.phase;

import static iovi.lobby1.flow.phase.Phrases.t;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import iovi.lobby1.flow.GameUi;
import iovi.lobby1.game.Game;

/** Лучший ход: убитый в первую ночь или заголосованный в нулевом круге называет трёх игроков, которых считает мафией. */
public final class BestMovePhase extends Phase {
    public BestMovePhase(Host host) {
        super(host);
    }

    /** done — после нажатия «Прощальная минута» на экране с результатом. */
    public void run(int n, Runnable done) {
        voice.say(t("voice.bestMove", n));
        GameUi.MessageView view = screens.handoff(t("bestMove.title"), n, t("bestMove.handoffHint"),
                t("bestMove.start"), () -> showBestMove(n, done));
        fouls.offer(view.showExtra(t("foul.button")), t("foul.at.bestMoveHandoff", n), () -> { });
    }

    private void showBestMove(int n, Runnable done) {
        List<Integer> candidates = new ArrayList<>();
        for (int k = 1; k <= Game.PLAYER_COUNT; k++) {
            if (k != n) {
                candidates.add(k);
            }
        }
        screens.newScreen();
        GameUi.NightView view = ui.showNight(t("player", n), t("bestMove.title"), t("bestMove.hint"), candidates);
        GameUi.Grid grid = view.grid();
        GameUi.Button action = view.action();

        List<Integer> picked = new ArrayList<>();
        Runnable refresh = () -> {
            grid.select(picked);
            action.setEnabled(picked.size() == 3);
            action.setText(picked.size() == 3
                    ? t("bestMove.confirm", Game.joinNumbers(sorted(picked)))
                    : t("bestMove.progress", picked.size()));
        };
        grid.setListener(number -> {
            if (picked.contains(number)) {
                picked.remove(Integer.valueOf(number));
            } else if (picked.size() < 3) {
                picked.add(number);
            }
            refresh.run();
        });
        refresh.run();

        action.setOnClick(() -> {
            String list = Game.joinNumbers(sorted(picked));
            grid.lock(picked);
            game.log().add(t("log.bestMove", n, list));
            int black = 0;
            for (int x : picked) {
                if (game.player(x).role().isBlack()) {
                    black++;
                }
            }
            summaries.addBestMove(t("log.bestMoveSummary", n, list, black));
            voice.say(t("voice.bestMoveResult", n, list));
            view.showResult(list);
            action.setText(t("button.farewell"));
            action.setOnClick(done);
        });
    }

    private static List<Integer> sorted(List<Integer> numbers) {
        List<Integer> copy = new ArrayList<>(numbers);
        Collections.sort(copy);
        return copy;
    }
}