package iovi.lobby1.flow;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntConsumer;

import iovi.lobby1.game.Game;
import iovi.lobby1.game.Role;
import iovi.lobby1.game.Voting;
import iovi.lobby1.text.Texts;

/**
 * Ход партии: раздача карт → договорка → свободная посадка → дни и ночи до победы.
 * Решает, что показать, что объявить и что записать в протокол; экраны рисует {@link GameUi},
 * тексты берутся из texts.properties. Каждая фаза переходит к следующей через продолжение (Runnable).
 */
public final class GameFlow {
    private static final long CARD_SHOW_MS = 3_000;
    /** Пауза между исчезновением карты и вызовом следующего игрока. */
    private static final long CARD_PASS_PAUSE_MS = 3_000;
    private static final long AGREEMENT_MS = 60_000;
    private static final long FREE_SEATING_MS = 40_000;
    private static final long SPEECH_MS = 60_000;
    private static final long TIE_SPEECH_MS = 30_000;
    private static final long FAREWELL_MS = 60_000;
    /**
     * Отсчёт ночного хода, одинаковый для всех ролей: действия принимаются только до его конца,
     * а передать ход можно только после. По времени хода роль не вычислить.
     */
    private static final long NIGHT_TURN_MS = 8_000;
    /** Пауза после последнего ночного хода — положить телефон на стол. */
    private static final long NIGHT_END_PAUSE_MS = 5_000;
    /** Время на поднятие рук между «кто за X?» и «Спасибо». */
    private static final long VOTE_WINDOW_MS = 1_000;

    private final Game game;
    private final GameUi ui;
    private final Voice voice;
    private final Music music;
    private final Scheduler scheduler;
    private final PhaseTimer timer;
    private boolean over = false;
    /** Номер текущего экрана: по нему запоздавшее продолжение узнаёт, что экран уже сменился. */
    private int screenId = 0;
    /** Таймер текущего экрана; null — таймер не запущен. */
    private GameUi.TimerView timerView;
    private Runnable timerDone;

    /** Выставленные кандидатуры текущего дня в порядке выставления. */
    private final List<Integer> nominations = new ArrayList<>();
    /** Кто кого выставил: номер говорившего → номер выставленного. */
    private final Map<Integer, Integer> nominatedBy = new HashMap<>();
    /** Ночные выстрелы: номер стрелявшего → цель. */
    private final Map<Integer, Integer> shots = new HashMap<>();
    /** Идёт отсчёт ночного хода — выстрел и проверка принимаются. */
    private boolean turnOpen;
    /** Экран текущего ночного хода; у дона после выстрела сменяется экраном проверки. */
    private GameUi.NightView turnView;
    private long turnRemainingMs;
    private boolean turnChecked;
    /** Подтверждённый выстрел — остаётся подсвеченным, когда сетка блокируется в конце хода. */
    private final List<Integer> turnLocked = new ArrayList<>();
    /** Чёрные, выбравшие промах этой ночью. */
    private final Set<Integer> missed = new HashSet<>();
    /** Строка лучшего хода для итога партии; null — лучшего хода не было. */
    private String bestMoveSummary;

    private final GameUi.TimerControls timerControls = new GameUi.TimerControls() {
        @Override
        public void onPauseToggle() {
            if (timerDone == null) {
                return;
            }
            if (timer.isRunning()) {
                timer.pause();
                timerView.setPaused(true);
            } else {
                timer.resume();
                timerView.setPaused(false);
            }
        }

        @Override
        public void onFinish() {
            if (timerDone != null) {
                timerDone.run();
            }
        }
    };

    public GameFlow(Game game, GameUi ui, Voice voice, Music music, Scheduler scheduler) {
        this.game = game;
        this.ui = ui;
        this.voice = voice;
        this.music = music;
        this.scheduler = scheduler;
        this.timer = new PhaseTimer(scheduler);
    }

    public void start() {
        startDealing();
    }

    public boolean isOver() {
        return over;
    }

    /** Отменяет таймер и отложенные действия — экран игры закрывается. */
    public void stop() {
        newScreen();
    }

    // ───────────────────────────── Раздача карт ─────────────────────────────

    private void startDealing() {
        ui.setPhase(t("phase.dealing"));
        game.log().section(t("log.dealing"));
        for (int n = 1; n <= Game.PLAYER_COUNT; n++) {
            game.log().add(t("log.dealt", n, game.player(n).role().title()));
        }
        music.start();
        voice.say(t("voice.dealing"));
        dealCard(1);
    }

    private void dealCard(int n) {
        callPlayer(n);
        showHandoff(t("dealing.title"), n,
                t("dealing.hint", seconds(CARD_SHOW_MS)),
                t("dealing.button"), () -> showCard(n));
    }

    private void showCard(int n) {
        newScreen();
        Role role = game.player(n).role();
        ui.showCard(t("player", n), role.title(), role, CARD_SHOW_MS);
        scheduler.post(() -> {
            if (n < Game.PLAYER_COUNT) {
                passAfterCard(n + 1);
            } else {
                // Договорка начинается сама: последний игрок кладёт телефон, кнопок не нужно.
                showMessage(t("dealing.done.title"), t("dealing.done.text"), null, null);
                scheduler.post(this::mafiaAgreement, CARD_PASS_PAUSE_MS);
            }
        }, CARD_SHOW_MS);
    }

    /** Пауза после исчезновения карты: телефон передают дальше, затем вызывается следующий игрок. */
    private void passAfterCard(int next) {
        showMessage(t("dealing.pass"), null, null, null);
        scheduler.post(() -> dealCard(next), CARD_PASS_PAUSE_MS);
    }

    // ─────────────────────── Нулевая ночь: договорка и посадка ───────────────────────

    private void mafiaAgreement() {
        ui.setPhase(t("phase.night", 0));
        game.log().section(t("log.night", 0));
        game.log().add(t("log.agreement"));
        newScreen();
        int screen = screenId;
        GameUi.TimerView view = ui.showTimer(t("agreement.title"), t("agreement.subtitle"), timerControls);
        view.setTime(AGREEMENT_MS);
        view.setControlsEnabled(false);
        // Минута договорки идёт с момента, когда телефон закончит объявление.
        voice.say(t("voice.agreement", duration(AGREEMENT_MS)), () -> {
            if (screen != screenId) {
                return;
            }
            view.setControlsEnabled(true);
            runTimer(view, AGREEMENT_MS, true, () -> {
                voice.say(t("voice.agreementEnd"));
                freeSeating();
            });
        });
    }

    private void freeSeating() {
        voice.say(t("voice.seating", duration(FREE_SEATING_MS)));
        showTimer(t("seating.title"), t("seating.subtitle", duration(FREE_SEATING_MS)), FREE_SEATING_MS, false, null, this::startDay);
    }

    // ───────────────────────────────── День ─────────────────────────────────

    private void startDay() {
        music.stop();
        game.startNewDay();
        int day = game.dayNumber();
        ui.setPhase(t("phase.day", day, game.aliveCount()));
        game.log().section(t("log.day", day));
        nominations.clear();
        nominatedBy.clear();

        List<Integer> order = game.speechOrder();
        voice.say(day == 1 ? t("voice.firstDay", order.get(0)) : t("voice.day", order.get(0), day));
        speech(order, 0);
    }

    private void speech(List<Integer> order, int i) {
        if (i >= order.size()) {
            startVoting();
            return;
        }
        int n = order.get(i);
        showHandoff(t("speech.handoff", i + 1, order.size()), n, t("speech.handoffHint"),
                t("speech.start"), () -> showSpeech(order, i));
    }

    private void showSpeech(List<Integer> order, int i) {
        int n = order.get(i);
        List<Integer> allowed = new ArrayList<>(game.alive());
        allowed.removeAll(nominations);
        newScreen();
        GameUi.SpeechView view = ui.showSpeech(t("speech.title", n), t("speech.hint"), allowed, timerControls);
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

        voice.say(t("voice.speech", n));
        runTimer(view, SPEECH_MS, true, () -> {
            Integer nominee = nominatedBy.get(n);
            if (nominee != null) {
                game.log().add(t("log.nomination", n, nominee));
            }
            voice.say(t("voice.speechEnd"));
            speech(order, i + 1);
        });
    }

    private String nomineesText() {
        return nominations.isEmpty()
                ? t("speech.nobody")
                : t("speech.nominees", Game.joinNumbers(nominations));
    }

    // ───────────────────────────── Голосование ─────────────────────────────

    private void startVoting() {
        Voting voting = new Voting(nominations, game.aliveCount(), game.dayNumber() == 1);
        String list = Game.joinNumbers(nominations);
        switch (voting.phase()) {
            case NO_VOTE: {
                String text = nominations.isEmpty() ? t("vote.none") : t("vote.singleFirstDay");
                game.log().add(t("log.noVote"));
                voice.say(text);
                showMessage(t("vote.title"), text, t("button.night"), this::startNight);
                break;
            }
            case FINISHED: {
                int x = voting.eliminated().get(0);
                String text = t("vote.single", x);
                game.log().add(t("log.single", x));
                voice.say(text);
                showMessage(t("vote.title"), text, t("button.farewell"),
                        () -> farewells(voting.eliminated(), this::startNight));
                break;
            }
            default: {
                voice.say(t("voice.voting", list));
                showMessage(t("vote.title"), t("vote.intro", list, voting.voters()), t("vote.begin"),
                        () -> voteRound(voting, new int[voting.candidates().size()], 0));
                break;
            }
        }
    }

    private void voteRound(Voting voting, int[] votes, int i) {
        List<Integer> candidates = voting.candidates();
        int used = 0;
        for (int k = 0; k < i; k++) {
            used += votes[k];
        }
        int remaining = voting.voters() - used;
        if (remaining == 0) {
            // Все голоса уже отданы — оставшиеся кандидатуры не называем, у них по нулю.
            finishVoteRound(voting, votes);
            return;
        }
        int x = candidates.get(i);
        String title = voting.isRevote() ? t("vote.revoteTitle") : t("vote.title");
        String question = t("vote.question", x);
        if (i == candidates.size() - 1) {
            // Не проголосовавшие отдают голос последней кандидатуре — её тоже называем, но голоса не вводим.
            showMessage(title, t("vote.lastCandidate", question, remaining), null, null);
            announceCandidate(x, () -> {
                votes[i] = remaining;
                finishVoteRound(voting, votes);
            });
            return;
        }
        GameUi.Button action = voteInput(title, question, remaining, value -> {
            votes[i] = value;
            voteRound(voting, votes, i + 1);
        });
        // Число голосов вводится после «Спасибо» — к этому моменту голосование за кандидатуру закончено.
        action.setEnabled(false);
        announceCandidate(x, () -> action.setEnabled(true));
    }

    /** «X, кто за X?», через секунду «Спасибо.»; thanked вызывается после «Спасибо». */
    private void announceCandidate(int x, Runnable thanked) {
        announceVote(t("voice.candidate", x), thanked);
    }

    /** Вопрос голосования, через секунду «Спасибо.»; thanked вызывается после «Спасибо». */
    private void announceVote(String question, Runnable thanked) {
        voice.say(question,
                () -> scheduler.post(() -> voice.say(t("voice.thanks"), thanked), VOTE_WINDOW_MS));
    }

    private void finishVoteRound(Voting voting, int[] votes) {
        List<Integer> candidates = new ArrayList<>(voting.candidates());
        StringBuilder summary = new StringBuilder();
        StringBuilder tally = new StringBuilder();
        for (int k = 0; k < candidates.size(); k++) {
            if (k > 0) {
                summary.append('\n');
                tally.append(", ");
            }
            summary.append(t("vote.summaryLine", candidates.get(k), votesText(votes[k])));
            tally.append(t("log.voteItem", candidates.get(k), votes[k]));
        }
        game.log().add(t(voting.isRevote() ? "log.revote" : "log.vote", tally));

        voting.submitVotes(votes);
        switch (voting.phase()) {
            case FINISHED: {
                int x = voting.eliminated().get(0);
                String text = t("vote.eliminated", x);
                game.log().add(t("log.eliminated", x));
                voice.say(text);
                showMessage(t("vote.resultTitle"), t("vote.result", summary, text), t("button.farewell"),
                        () -> farewells(voting.eliminated(), this::startNight));
                break;
            }
            case TIE_SPEECHES: {
                String tied = Game.joinNumbers(voting.candidates());
                String text = t("vote.tie", tied, duration(TIE_SPEECH_MS));
                game.log().add(t("log.tie", tied));
                voice.say(text);
                showMessage(t("vote.resultTitle"), t("vote.result", summary, text), t("vote.tieButton"),
                        () -> tieSpeeches(voting, 0));
                break;
            }
            case LIFT_ALL: {
                String text = t("vote.liftAll", Game.joinNumbers(voting.candidates()));
                voice.say(text);
                showMessage(t("vote.resultTitle"), t("vote.result", summary, text), t("vote.liftAllButton"),
                        () -> liftAllVote(voting));
                break;
            }
            default:
                throw new IllegalStateException("Неожиданная фаза голосования: " + voting.phase());
        }
    }

    private void tieSpeeches(Voting voting, int i) {
        List<Integer> candidates = voting.candidates();
        if (i >= candidates.size()) {
            voting.tieSpeechesDone();
            voice.say(t("voice.revote"));
            voteRound(voting, new int[candidates.size()], 0);
            return;
        }
        int x = candidates.get(i);
        voice.say(t("voice.tieSpeech", x, duration(TIE_SPEECH_MS)));
        showTimer(t("tie.title"), t("player", x), TIE_SPEECH_MS, true, t("voice.thanks"),
                () -> tieSpeeches(voting, i + 1));
    }

    private void liftAllVote(Voting voting) {
        String list = Game.joinNumbers(voting.candidates());
        String question = t("vote.liftAllQuestion", list);
        GameUi.Button action = voteInput(t("vote.liftAllTitle"), question, voting.voters(), value -> {
            boolean lifted = voting.submitLiftAll(value);
            String tally = t("vote.liftAllTally", votesText(value), voting.voters());
            game.log().add(t(lifted ? "log.liftAll.lifted" : "log.liftAll.stayed", list, value, voting.voters()));
            if (lifted) {
                String text = t("vote.lifted", list);
                voice.say(text);
                showMessage(t("vote.resultTitle"), t("vote.result", tally, text), t("button.farewell"),
                        () -> farewells(voting.eliminated(), this::startNight));
            } else {
                String text = t("vote.notLifted");
                voice.say(text);
                showMessage(t("vote.resultTitle"), t("vote.result", tally, text), t("button.night"),
                        this::startNight);
            }
        });
        action.setEnabled(false);
        announceVote(question, () -> action.setEnabled(true));
    }

    private static String votesText(int n) {
        return Texts.plural("votes", n);
    }

    // ──────────────────────── Выбывание и прощальная минута ────────────────────────

    /** Выводит игроков из игры, даёт каждому прощальную минуту и проверяет победу. */
    private void farewells(List<Integer> players, Runnable then) {
        List<Integer> leaving = new ArrayList<>(players);
        for (int x : leaving) {
            game.eliminate(x);
        }
        farewell(leaving, 0, () -> {
            Game.Winner winner = game.winner();
            if (winner == Game.Winner.NONE) {
                then.run();
            } else {
                showGameOver(winner);
            }
        });
    }

    private void farewell(List<Integer> leaving, int i, Runnable then) {
        if (i >= leaving.size()) {
            then.run();
            return;
        }
        int x = leaving.get(i);
        voice.say(t("voice.farewell", x));
        showTimer(t("farewell.title"), t("farewell.subtitle", x), FAREWELL_MS, true,
                t("voice.farewellEnd", x), () -> farewell(leaving, i + 1, then));
    }

    // ──────────────────────────────── Ночь ────────────────────────────────

    private void startNight() {
        int night = game.dayNumber();
        ui.setPhase(t("phase.night", night));
        game.log().section(t("log.night", night));
        shots.clear();
        missed.clear();
        music.start();
        List<Integer> order = game.nightOrder();
        voice.say(t("voice.night", order.get(0)));
        nightTurn(order, 0);
    }

    private void nightTurn(List<Integer> order, int i) {
        int n = order.get(i);
        callPlayer(n);
        showHandoff(t("night.handoff", i + 1, order.size()), n,
                t("night.handoffHint", seconds(NIGHT_TURN_MS)),
                t("night.iAm", n), () -> startNightTurn(order, i));
    }

    /** Отсчёт хода идёт с нажатия «Я игрок N»; после него выстрел и проверка не принимаются. */
    private void startNightTurn(List<Integer> order, int i) {
        int n = order.get(i);
        newScreen();
        turnOpen = true;
        turnChecked = false;
        turnLocked.clear();
        turnRemainingMs = NIGHT_TURN_MS;
        nightAction(n);
        timer.start(NIGHT_TURN_MS, new PhaseTimer.Listener() {
            @Override
            public void onTick(long remainingMs) {
                turnRemainingMs = remainingMs;
                turnView.setTime(remainingMs);
            }

            @Override
            public void onWarning() {
            }

            @Override
            public void onFinish() {
                closeNightTurn(order, i);
            }
        });
    }

    private void nightAction(int n) {
        switch (game.player(n).role()) {
            case MAFIA:
                pickShot(n, this::awaitTurnEnd);
                break;
            case DON:
                // В первую ночь дон не может проверить того, в кого стрелял.
                pickShot(n, () -> check(n, game.dayNumber() == 1 ? shots.getOrDefault(n, 0) : 0,
                        t("don.title"), t("don.hint"),
                        target -> game.player(target).role() == Role.SHERIFF
                                ? t("don.sheriff")
                                : t("don.notSheriff"),
                        "log.donCheck"));
                break;
            case SHERIFF:
                check(n, 0, t("sheriff.title"), t("sheriff.hint"),
                        target -> game.player(target).role().isBlack()
                                ? t("sheriff.mafia")
                                : t("sheriff.civilian"),
                        "log.sheriffCheck");
                break;
            case CIVILIAN:
            default:
                civilianTurn(n);
                break;
        }
    }

    /** Экран ночного хода; отсчёт продолжается и при смене экрана (выстрел дона → проверка). */
    private GameUi.NightView nightScreen(int n, String title, String hint, Collection<Integer> enabled) {
        turnView = ui.showNight(t("night.header", n, game.player(n).role().title()), title, hint, enabled);
        turnView.setTime(turnRemainingMs);
        return turnView;
    }

    /** Выстрел или промах. Если промах выбрал хотя бы один чёрный, ночь проходит без убийства. */
    private void pickShot(int n, Runnable next) {
        GameUi.NightView view = nightScreen(n, t("shot.title"), t("shot.hint"), game.alive());
        GameUi.Button action = view.action();
        action.setText(t("shot.confirm"));
        action.setEnabled(false);
        GameUi.Button miss = view.showExtra(t("shot.miss"));
        int[] target = {0};
        view.grid().setListener(number -> {
            if (!turnOpen) {
                return;
            }
            target[0] = number;
            view.grid().select(Collections.singleton(number));
            action.setEnabled(true);
        });
        action.setOnClick(() -> {
            if (!turnOpen) {
                return;
            }
            shots.put(n, target[0]);
            turnLocked.add(target[0]);
            view.grid().lock(turnLocked);
            view.hideExtra();
            game.log().add(t("log.shot", game.player(n).role().title(), n, target[0]));
            next.run();
        });
        miss.setOnClick(() -> {
            if (!turnOpen) {
                return;
            }
            // Промах не попадает в shots: game.resolveShots без выстрела хотя бы одного чёрного даёт промах.
            missed.add(n);
            view.grid().lock(turnLocked);
            view.hideExtra();
            view.showResult(t("shot.missed"));
            game.log().add(t("log.missShot", game.player(n).role().title(), n));
            next.run();
        });
    }

    private interface Verdict {
        String of(int target);
    }

    /**
     * Ночная проверка; excluded — номер, который проверять нельзя (0 — без ограничения).
     * Вердикт показывается игроку и пишется в протокол по ключу logKey.
     */
    private void check(int n, int excluded, String title, String hint, Verdict verdict, String logKey) {
        List<Integer> checkable = new ArrayList<>();
        for (int k = 1; k <= Game.PLAYER_COUNT; k++) {
            if (k != n && k != excluded) {
                checkable.add(k);
            }
        }
        GameUi.NightView view = nightScreen(n, title, hint, checkable);
        GameUi.Button action = view.action();
        action.setText(t("check.button"));
        action.setEnabled(false);
        int[] target = {0};
        view.grid().setListener(number -> {
            if (!turnOpen) {
                return;
            }
            target[0] = number;
            view.grid().select(Collections.singleton(number));
            action.setEnabled(true);
        });
        action.setOnClick(() -> {
            if (!turnOpen) {
                return;
            }
            turnChecked = true;
            String word = verdict.of(target[0]);
            game.log().add(t(logKey, target[0], word));
            view.hideGrid();
            view.showResult(t("check.result", target[0], word));
            awaitTurnEnd();
        });
    }

    private void civilianTurn(int n) {
        nightScreen(n, t("civilian.title"), t("civilian.hint"), null);
        disableEndTurn();
    }

    /** Действие сделано — кнопка «Завершить ход» ждёт конца отсчёта. */
    private void awaitTurnEnd() {
        turnView.setHint(t("night.waitTimer"));
        disableEndTurn();
    }

    private void disableEndTurn() {
        GameUi.Button action = turnView.action();
        action.setText(t("night.endTurn"));
        action.setEnabled(false);
    }

    /** Конец отсчёта: действия больше не принимаются, ход можно завершить. */
    private void closeNightTurn(List<Integer> order, int i) {
        int n = order.get(i);
        turnOpen = false;
        GameUi.NightView view = turnView;
        if (view.grid() != null) {
            view.grid().lock(turnLocked);
        }
        view.hideExtra();
        if (!logMissedActions(n)) {
            view.setHint(t("night.timeUp"));
        }
        GameUi.Button action = view.action();
        action.setText(t("night.endTurn"));
        action.setEnabled(true);
        action.setOnClick(() -> {
            if (i < order.size() - 1) {
                nightTurn(order, i + 1);
            } else {
                // Утро наступает само: последний игрок кладёт телефон, кнопок не нужно.
                showMessage(t("night.over.title"), t("night.over.text"), null, null);
                scheduler.post(this::morning, NIGHT_END_PAUSE_MS);
            }
        });
    }

    /** Пишет в протокол действия, не сделанные до конца отсчёта; true — игрок всё успел. */
    private boolean logMissedActions(int n) {
        Role role = game.player(n).role();
        String title = role.title();
        if (role.isBlack() && !shots.containsKey(n) && !missed.contains(n)) {
            game.log().add(t("log.noShot", title, n));
            return false;
        }
        if ((role == Role.DON || role == Role.SHERIFF) && !turnChecked) {
            game.log().add(t("log.noCheck", title, n));
            return false;
        }
        return true;
    }

    private void morning() {
        music.stop();
        ui.setPhase(t("phase.morning"));
        Integer killed = game.resolveShots(shots);
        if (killed == null) {
            game.log().add(t("log.miss"));
            String text = t("morning.miss");
            voice.say(t("voice.morning", text));
            showMessage(t("morning.title"), text, t("button.day"), this::startDay);
        } else {
            game.log().add(t("log.killed", killed));
            String text = t("morning.killed", killed);
            voice.say(t("voice.morning", text));
            Runnable farewell = () -> farewells(Collections.singletonList(killed), this::startDay);
            if (game.dayNumber() == 1) {
                // Убитый в первую ночь перед прощальной речью делает лучший ход.
                showMessage(t("morning.title"), text, t("button.bestMove"), () -> bestMove(killed, farewell));
            } else {
                showMessage(t("morning.title"), text, t("button.farewell"), farewell);
            }
        }
    }

    /** Лучший ход: убитый в первую ночь называет трёх игроков, которых считает мафией. */
    private void bestMove(int n, Runnable then) {
        voice.say(t("voice.bestMove", n));
        showHandoff(t("bestMove.title"), n, t("bestMove.handoffHint"), t("bestMove.start"),
                () -> showBestMove(n, then));
    }

    private void showBestMove(int n, Runnable then) {
        List<Integer> candidates = new ArrayList<>();
        for (int k = 1; k <= Game.PLAYER_COUNT; k++) {
            if (k != n) {
                candidates.add(k);
            }
        }
        newScreen();
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
            bestMoveSummary = t("log.bestMoveSummary", n, list, black);
            voice.say(t("voice.bestMoveResult", n, list));
            view.showResult(list);
            action.setText(t("button.farewell"));
            action.setOnClick(then);
        });
    }

    private static List<Integer> sorted(List<Integer> numbers) {
        List<Integer> copy = new ArrayList<>(numbers);
        Collections.sort(copy);
        return copy;
    }

    // ───────────────────────────── Конец игры ─────────────────────────────

    private void showGameOver(Game.Winner winner) {
        over = true;
        music.stop();
        String title = winner == Game.Winner.MAFIA ? t("result.mafia") : t("result.civilians");
        game.log().section(t("log.result"));
        game.log().add(title);
        if (bestMoveSummary != null) {
            game.log().add(bestMoveSummary);
        }
        voice.say(t("voice.gameOver", title));
        ui.setPhase(t("phase.over"));
        newScreen();
        ui.showGameOver(title, game.log().toString());
    }

    // ───────────────────────────── Общие экраны ─────────────────────────────

    /** Перед сменой экрана: таймер и отложенные действия прошлого экрана отменяются. */
    private void newScreen() {
        timer.cancel();
        scheduler.cancelAll();
        timerView = null;
        timerDone = null;
        screenId++;
    }

    /** Вызов игрока к телефону при раздаче карт и ночью. */
    private void callPlayer(int n) {
        voice.say(t("voice.callPlayer", n));
    }

    private GameUi.Button showMessage(String title, String text, String button, Runnable next) {
        newScreen();
        return ui.showMessage(title, text, button, next);
    }

    private void showHandoff(String title, int player, String hint, String button, Runnable next) {
        newScreen();
        ui.showHandoff(t("handoff.title", title), player, hint, button, next);
    }

    private void showTimer(String title, String subtitle, long durationMs, boolean speech,
                           String endPhrase, Runnable next) {
        newScreen();
        GameUi.TimerView view = ui.showTimer(title, subtitle, timerControls);
        runTimer(view, durationMs, speech, () -> {
            if (endPhrase != null) {
                voice.say(endPhrase);
            }
            next.run();
        });
    }

    /** «8 секунд», «40 секунд»: длительность в секундах для экрана и голоса. */
    private static String seconds(long ms) {
        return Texts.plural("seconds", (int) (ms / 1000));
    }

    /** Длительность фазы в именительном падеже: «минута» или «40 секунд». */
    private static String duration(long ms) {
        return ms == 60_000 ? Texts.get("duration.minute") : seconds(ms);
    }

    /** В речах и договорке за {@link PhaseTimer#WARNING_MS} до конца звучит «10 секунд», в остальных фазах — короткий сигнал. */
    private void runTimer(GameUi.TimerView view, long durationMs, boolean speech, Runnable onDone) {
        timerView = view;
        timerDone = onceRun(() -> {
            timer.cancel();
            voice.endSignal();
            onDone.run();
        });
        Runnable done = timerDone;
        timer.start(durationMs, new PhaseTimer.Listener() {
            @Override
            public void onTick(long remainingMs) {
                view.setTime(remainingMs);
            }

            @Override
            public void onWarning() {
                if (speech) {
                    voice.say(t("voice.warning", duration(PhaseTimer.WARNING_MS)));
                } else {
                    voice.beep();
                }
            }

            @Override
            public void onFinish() {
                done.run();
            }
        });
    }

    /** Ввод числа голосов от 0 до max; возвращает кнопку подтверждения. */
    private GameUi.Button voteInput(String title, String question, int max, IntConsumer onSubmit) {
        newScreen();
        GameUi.VoteView view = ui.showVoteInput(title, question);
        int[] count = {0};
        Runnable refresh = () -> view.setCount(count[0], t("vote.left", max - count[0]),
                count[0] > 0, count[0] < max);
        view.setOnMinus(() -> {
            if (count[0] > 0) {
                count[0]--;
                refresh.run();
            }
        });
        view.setOnPlus(() -> {
            if (count[0] < max) {
                count[0]++;
                refresh.run();
            }
        });
        refresh.run();
        GameUi.Button action = view.action();
        action.setOnClick(() -> onSubmit.accept(count[0]));
        return action;
    }

    // ───────────────────────────── Утилиты ─────────────────────────────

    private static String t(String key, Object... args) {
        return Texts.get(key, args);
    }

    /** Обёртка от двойного срабатывания: действие выполняется не больше одного раза. */
    private static Runnable onceRun(Runnable action) {
        boolean[] done = {false};
        return () -> {
            if (!done[0]) {
                done[0] = true;
                action.run();
            }
        };
    }
}
