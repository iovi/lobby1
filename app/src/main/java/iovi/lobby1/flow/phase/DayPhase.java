package iovi.lobby1.flow.phase;

import static iovi.lobby1.flow.phase.Phrases.t;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import iovi.lobby1.flow.GameUi;
import iovi.lobby1.game.Game;

/** День: речи по кругу, на каждой можно выставить кандидатуру. Результат — выставленные по порядку. */
public final class DayPhase extends Phase {
    static final long SPEECH_MS = 60_000;
    /** Дневная речь игрока с тремя фолами по ФСМ. */
    static final long SHORT_SPEECH_MS = 30_000;

    /** Выставленные кандидатуры в порядке выставления. */
    private final List<Integer> nominations = new ArrayList<>();
    /** Кто кого выставил: номер говорившего → номер выставленного. */
    private final Map<Integer, Integer> nominatedBy = new HashMap<>();
    private List<Integer> order;
    private Consumer<List<Integer>> done;

    public DayPhase(Host host) {
        super(host);
    }

    /** done — после последней речи, с выставленными кандидатурами (возможно, пустыми). */
    public void run(Consumer<List<Integer>> done) {
        this.done = done;
        music.stop();
        game.startNewDay();
        int day = game.dayNumber();
        ui.setPhase(t("phase.day", day, game.aliveCount()));
        game.log().section(t("log.day", day));

        order = game.speechOrder();
        fouls.announce();
        voice.say(day == 1 ? t("voice.firstDay", order.get(0)) : t("voice.day", order.get(0), day));
        speech(0);
    }

    private void speech(int i) {
        if (i >= order.size()) {
            done.accept(nominations);
            return;
        }
        int n = order.get(i);
        if (!game.isAlive(n)) {
            // Удалён по фолам — его речь пропускается.
            speech(i + 1);
            return;
        }
        GameUi.MessageView view = screens.handoff(t("speech.handoff", i + 1, order.size()), n,
                t("speech.handoffHint"), t("speech.start"), () -> showSpeech(i));
        fouls.offer(view.showExtra(t("foul.button")), t("foul.at.speechHandoff", n), () -> speech(i));
    }

    private void showSpeech(int i) {
        int n = order.get(i);
        // У игрока с тремя фолами меняется только ближайшая дневная речь; оправдательные речи не меняются.
        boolean penalty = fouls.takePenaltySpeech(n);
        boolean silent = penalty && rules.silentAfterThreeFouls();
        List<Integer> allowed = new ArrayList<>(game.alive());
        allowed.removeAll(nominations);
        screens.newScreen();
        GameUi.SpeechView view = ui.showSpeech(t(silent ? "speech.silentTitle" : "speech.title", n),
                t(silent ? "speech.silentHint" : "speech.hint"), allowed, screens.controls());
        GameUi.Grid grid = view.grid();
        GameUi.Button confirm = view.confirm();
        int[] chosen = {0};
        grid.setListener(number -> {
            chosen[0] = number;
            grid.select(Collections.singleton(number));
            confirm.setEnabled(true);
            confirm.setText(t("speech.nominate", number));
        });
        confirm.setOnClick(() -> {
            int number = chosen[0];
            nominations.add(number);
            nominatedBy.put(n, number);
            grid.lock(Collections.singleton(number));
            confirm.setEnabled(false);
            confirm.setText(t("speech.nominated", number));
            view.setHint(t("speech.accepted", number));
            view.setNominees(nomineesText());
            voice.say(t("voice.nominated", number));
        });
        view.setNominees(nomineesText());

        String at = t("foul.at.speech", n);
        // Удалённый по фолам говорящий заканчивает речь сразу.
        Runnable afterRemoval = () -> {
            if (!game.isAlive(n)) {
                speech(i + 1);
            }
        };
        if (silent) {
            // По МСЛ слова нет: таймера тоже нет, игрок только выставляет и сам завершает.
            voice.say(t("voice.silentSpeech", n));
            game.log().add(t("log.silentSpeech", n));
            screens.untimed(view, () -> endSpeech(i));
            fouls.offer(view.showExtra(t("foul.button")), at, afterRemoval);
            return;
        }
        voice.say(t("voice.speech", n));
        fouls.onPause(at, afterRemoval);
        screens.runTimer(view, penalty ? SHORT_SPEECH_MS : SPEECH_MS, true, () -> {
            voice.say(t("voice.speechEnd"));
            endSpeech(i);
        });
        fouls.offerExtraTime(n, view, at, false);
    }

    private void endSpeech(int i) {
        int n = order.get(i);
        Integer nominee = nominatedBy.get(n);
        if (nominee != null) {
            game.log().add(t("log.nomination", n, nominee));
        }
        speech(i + 1);
    }

    private String nomineesText() {
        return nominations.isEmpty()
                ? t("speech.nobody")
                : t("speech.nominees", Game.joinNumbers(nominations));
    }
}