package iovi.lobby1.flow.phase;

import static iovi.lobby1.flow.phase.Phrases.seconds;
import static iovi.lobby1.flow.phase.Phrases.t;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import iovi.lobby1.flow.GameUi;
import iovi.lobby1.flow.PhaseTimer;
import iovi.lobby1.game.Game;
import iovi.lobby1.game.Role;

/**
 * Ночь: телефон по кругу, каждый делает свой ход — выстрел, проверку или ничего. Результат — выстрелы
 * (номер стрелявшего → цель); промах в них не попадает.
 */
public final class NightPhase extends Phase {
    /**
     * Отсчёт ночного хода, одинаковый для всех ролей: действия принимаются только до его конца,
     * а передать ход можно только после. По времени хода роль не вычислить.
     */
    private static final long NIGHT_TURN_MS = 8_000;
    /** Пауза после последнего ночного хода — положить телефон на стол. */
    private static final long NIGHT_END_PAUSE_MS = 5_000;
    /** Выбор промаха вместо номера цели в {@link #pickShot}. */
    private static final int MISS = -1;

    /** Ночные выстрелы: номер стрелявшего → цель. */
    private final Map<Integer, Integer> shots = new HashMap<>();
    /** Чёрные, выбравшие промах этой ночью. */
    private final Set<Integer> missed = new HashSet<>();
    private List<Integer> order;
    private Consumer<Map<Integer, Integer>> done;
    /** Идёт отсчёт ночного хода — выстрел и проверка принимаются. */
    private boolean turnOpen;
    /** Экран текущего ночного хода; у дона после выстрела сменяется экраном проверки. */
    private GameUi.NightView turnView;
    private long turnRemainingMs;
    private boolean turnChecked;
    /** Подтверждённый выстрел — остаётся подсвеченным, когда сетка блокируется в конце хода. */
    private final List<Integer> turnLocked = new ArrayList<>();

    public NightPhase(Host host) {
        super(host);
    }

    /** done — после паузы за последним ходом, когда телефон уже на столе. */
    public void run(Consumer<Map<Integer, Integer>> done) {
        this.done = done;
        int night = game.dayNumber();
        ui.setPhase(t("phase.night", night));
        game.log().section(t("log.night", night));
        music.start();
        order = game.nightOrder();
        voice.say(t("voice.night", order.get(0)));
        nightTurn(0);
    }

    private void nightTurn(int i) {
        int n = order.get(i);
        screens.callPlayer(n);
        screens.handoff(t("night.handoff", i + 1, order.size()), n,
                t("night.handoffHint", seconds(NIGHT_TURN_MS)),
                t("night.iAm", n), () -> startNightTurn(i));
    }

    /** Отсчёт хода идёт с нажатия «Я игрок N»; после него выстрел и проверка не принимаются. */
    private void startNightTurn(int i) {
        int n = order.get(i);
        screens.newScreen();
        turnOpen = true;
        turnChecked = false;
        turnLocked.clear();
        turnRemainingMs = NIGHT_TURN_MS;
        nightAction(n);
        screens.timer().start(NIGHT_TURN_MS, new PhaseTimer.Listener() {
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
                closeNightTurn(i);
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

    /**
     * Выстрел или промах: выбор номера или кнопки «Промах», затем подтверждение одной и той же кнопкой.
     * Если промах выбрал хотя бы один чёрный, ночь проходит без убийства.
     */
    private void pickShot(int n, Runnable next) {
        GameUi.NightView view = nightScreen(n, t("shot.title"), t("shot.hint"), game.alive());
        GameUi.Button action = view.action();
        action.setText(t("shot.confirm"));
        action.setEnabled(false);
        GameUi.Button miss = view.showExtra(t("shot.miss"));
        // Номер цели; MISS — выбран промах, 0 — ещё ничего не выбрано.
        int[] target = {0};
        view.grid().setListener(number -> {
            if (!turnOpen) {
                return;
            }
            target[0] = number;
            view.grid().select(Collections.singleton(number));
            action.setText(t("shot.confirm"));
            action.setEnabled(true);
        });
        // Кнопка срабатывает один раз — после выбора промаха её обработчик ставится заново,
        // чтобы можно было переключаться между промахом и номером.
        Runnable[] pickMiss = new Runnable[1];
        pickMiss[0] = () -> {
            miss.setOnClick(pickMiss[0]);
            if (!turnOpen) {
                return;
            }
            target[0] = MISS;
            view.grid().select(Collections.emptyList());
            action.setText(t("shot.confirmMiss"));
            action.setEnabled(true);
        };
        miss.setOnClick(pickMiss[0]);
        action.setOnClick(() -> {
            if (!turnOpen) {
                return;
            }
            view.hideExtra();
            if (target[0] == MISS) {
                // Промах не попадает в shots: game.resolveShots без выстрела хотя бы одного чёрного даёт промах.
                missed.add(n);
                view.grid().lock(turnLocked);
                view.showResult(t("shot.missed"));
                game.log().add(t("log.missShot", game.player(n).role().title(), n));
            } else {
                shots.put(n, target[0]);
                turnLocked.add(target[0]);
                view.grid().lock(turnLocked);
                game.log().add(t("log.shot", game.player(n).role().title(), n, target[0]));
            }
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
    private void closeNightTurn(int i) {
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
                nightTurn(i + 1);
            } else {
                // Утро наступает само: последний игрок кладёт телефон, кнопок не нужно.
                screens.message(t("night.over.title"), t("night.over.text"), null, null);
                scheduler.post(() -> done.accept(shots), NIGHT_END_PAUSE_MS);
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
}