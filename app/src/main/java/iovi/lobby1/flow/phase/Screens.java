package iovi.lobby1.flow.phase;

import static iovi.lobby1.flow.phase.Phrases.duration;
import static iovi.lobby1.flow.phase.Phrases.t;

import java.util.function.Consumer;
import java.util.function.IntConsumer;

import iovi.lobby1.flow.GameUi;
import iovi.lobby1.flow.PhaseTimer;
import iovi.lobby1.flow.Scheduler;
import iovi.lobby1.flow.Voice;

/**
 * Смена экранов и таймер текущего экрана. При смене экрана таймер и отложенные действия прошлого
 * отменяются, а номер экрана растёт: по нему запоздавшее продолжение узнаёт, что экран уже сменился.
 */
public final class Screens {
    private final GameUi ui;
    private final Voice voice;
    private final Scheduler scheduler;
    private final PhaseTimer timer;
    private int id = 0;
    /** Таймер текущего экрана; null — таймер не запущен. */
    private GameUi.TimerView timerView;
    private Runnable timerDone;
    /** Что показать на паузе текущей речи (кнопку «Фол»); null — ничего. */
    private Consumer<GameUi.TimerView> onPause;

    private final GameUi.TimerControls controls = new GameUi.TimerControls() {
        @Override
        public void onPauseToggle() {
            if (timerDone == null) {
                return;
            }
            if (timer.isRunning()) {
                timer.pause();
                timerView.setPaused(true);
                if (onPause != null) {
                    onPause.accept(timerView);
                }
            } else {
                timer.resume();
                timerView.setPaused(false);
                timerView.hideExtra();
            }
        }

        @Override
        public void onFinish() {
            if (timerDone != null) {
                timerDone.run();
            }
        }
    };

    Screens(GameUi ui, Voice voice, Scheduler scheduler) {
        this.ui = ui;
        this.voice = voice;
        this.scheduler = scheduler;
        this.timer = new PhaseTimer(scheduler);
    }

    /** Перед сменой экрана: таймер и отложенные действия прошлого экрана отменяются. */
    public void newScreen() {
        timer.cancel();
        scheduler.cancelAll();
        timerView = null;
        timerDone = null;
        onPause = null;
        id++;
    }

    /** Номер текущего экрана. */
    public int id() {
        return id;
    }

    public PhaseTimer timer() {
        return timer;
    }

    /** Кнопки «Пауза» и «Завершить» для экранов с таймером. */
    public GameUi.TimerControls controls() {
        return controls;
    }

    /** На паузе таймера текущего экрана — дополнительное действие; при продолжении кнопка пропадает. */
    void onPause(Consumer<GameUi.TimerView> action) {
        onPause = action;
    }

    public GameUi.MessageView message(String title, String text, String button, Runnable next) {
        newScreen();
        return ui.showMessage(title, text, button, next);
    }

    public GameUi.MessageView handoff(String title, int player, String hint, String button, Runnable next) {
        newScreen();
        return ui.showHandoff(t("handoff.title", title), player, hint, button, next);
    }

    /** Экран с таймером; endPhrase звучит по окончании (null — без фразы). */
    public GameUi.TimerView timer(String title, String subtitle, long durationMs, boolean speech,
                                  String endPhrase, Runnable next) {
        newScreen();
        GameUi.TimerView view = ui.showTimer(title, subtitle, controls);
        runTimer(view, durationMs, speech, () -> {
            if (endPhrase != null) {
                voice.say(endPhrase);
            }
            next.run();
        });
        return view;
    }

    /** В речах и договорке за {@link PhaseTimer#WARNING_MS} до конца звучит «10 секунд», в остальных фазах — короткий сигнал. */
    public void runTimer(GameUi.TimerView view, long durationMs, boolean speech, Runnable onDone) {
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
    public GameUi.Button voteInput(String title, String question, int max, IntConsumer onSubmit) {
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

    /** Кнопка «Мануальный режим»: onYes после подтверждения; при отказе кнопка снова работает. */
    public void offerManual(GameUi.Button button, Runnable onYes) {
        button.setOnClick(() -> ui.confirm(t("manual.confirmTitle"), t("manual.confirmMessage"),
                t("manual.yes"), t("manual.no"), onYes, () -> offerManual(button, onYes)));
    }

    /** Вызов игрока к телефону при раздаче карт и ночью. */
    public void callPlayer(int n) {
        voice.say(t("voice.callPlayer", n));
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