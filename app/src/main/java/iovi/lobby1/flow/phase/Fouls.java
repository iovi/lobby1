package iovi.lobby1.flow.phase;

import static iovi.lobby1.flow.phase.Phrases.t;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import iovi.lobby1.flow.GameUi;
import iovi.lobby1.flow.Voice;
import iovi.lobby1.game.Game;

/**
 * Фолы — сквозная механика дневных фаз: кнопка «Фол» открывает экран фолов поверх текущего экрана,
 * четвёртый фол удаляет игрока без прощальной минуты, и голосования в этот день уже нет.
 * Сюда же — доп. время за 2 фола и сокращённая речь у игрока с тремя фолами.
 */
public final class Fouls {
    /** Сколько добавляет «Доп. время» (за 2 фола). */
    private static final long EXTRA_TIME_MS = 30_000;

    /** Кого-то удалили по фолам — решает партия целиком (победа, строка фазы). */
    public interface Listener {
        /** false — партия на этом закончилась, экран под фолами больше не нужен. */
        boolean onRemoved();
    }

    private final Game game;
    private final GameUi ui;
    private final Voice voice;
    private final Screens screens;
    private final Listener listener;
    /** На каком экране открыт экран фолов; -1 — закрыт. */
    private int openOn = -1;
    /** Отложено до закрытия экрана фолов (начало голосования); null — ничего. */
    private Runnable pending;
    private int pendingOn;
    /** Игрок удалён по фолам после начала ночи — голосования в этот день нет. */
    private boolean removedToday;
    /** Игроки с тремя фолами, у которых уже была сокращённая дневная речь. */
    private final Set<Integer> shortSpeeches = new HashSet<>();

    Fouls(Game game, GameUi ui, Voice voice, Screens screens, Listener listener) {
        this.game = game;
        this.ui = ui;
        this.voice = voice;
        this.screens = screens;
        this.listener = listener;
    }

    /** С наступлением ночи удаления прошлого дня больше не отменяют голосование. */
    public void newNight() {
        removedToday = false;
    }

    /** С прошлой ночи кого-то удалили по фолам. */
    public boolean removedToday() {
        return removedToday;
    }

    /**
     * Кнопка «Фол» открывает экран фолов поверх текущего экрана. at — где выставлен фол (для лога);
     * afterRemoval — что делать после «Назад», если кого-то удалили по фолам (может сменить экран
     * или оставить текущий).
     */
    public void offer(GameUi.Button button, String at, Runnable afterRemoval) {
        button.setOnClick(() -> open(at, () -> offer(button, at, afterRemoval), afterRemoval));
    }

    /** Во время речи кнопка «Фол» появляется на паузе и пропадает при продолжении. */
    public void onPause(String at, Runnable afterRemoval) {
        screens.onPause(view -> offer(view.showExtra(t("foul.button")), at, afterRemoval));
    }

    /** Выполняет действие сразу или, если открыт экран фолов, — после «Назад». */
    public void whenClosed(Runnable action) {
        if (openOn == screens.id()) {
            pending = action;
            pendingOn = openOn;
        } else {
            action.run();
        }
    }

    /** Перед началом дня — у кого 2 и 3 фола. */
    public void announce() {
        for (int count = 2; count <= Game.MAX_FOULS; count++) {
            List<Integer> players = game.withFouls(count);
            if (!players.isEmpty()) {
                voice.say(t(players.size() == 1 ? "voice.foulsOne" : "voice.foulsMany",
                        count, Game.joinNumbers(players)));
            }
        }
    }

    /** У игрока с тремя фолами короче только ближайшая дневная речь; true — эта речь сокращённая. */
    public boolean takeShortSpeech(int n) {
        return game.fouls(n) == Game.MAX_FOULS && shortSpeeches.add(n);
    }

    /**
     * «Доп. время» у игрока с 0 или 1 фолом: за 2 фола текущая речь удлиняется на {@link #EXTRA_TIME_MS}.
     * Только на дневной (кроме нулевого круга) и прощальной речи; на оправдательной — нет.
     */
    public void offerExtraTime(int n, GameUi.TimerView view, String at, boolean farewell) {
        if ((game.dayNumber() == 1 && !farewell) || game.fouls(n) > 1) {
            return;
        }
        GameUi.Button bonus = view.showBonus(t("extraTime.button"));
        bonus.setOnClick(() -> {
            view.hideBonus();
            game.addFoul(n);
            int count = game.addFoul(n);
            screens.timer().extend(EXTRA_TIME_MS);
            game.log().add(t("log.extraTime", n, count, at));
            voice.say(t("voice.extraTime"));
        });
    }

    private void open(String at, Runnable rearm, Runnable afterRemoval) {
        openOn = screens.id();
        List<Integer> removed = new ArrayList<>();
        GameUi.FoulView view = ui.showFouls(t("foul.title"), game.alive());
        for (int n = 1; n <= Game.PLAYER_COUNT; n++) {
            view.setRow(n, game.fouls(n), false);
        }
        GameUi.Button action = view.action();
        // Игрок с неподтверждённым фолом; 0 — никого.
        int[] chosen = {0};
        Runnable reset = () -> {
            chosen[0] = 0;
            action.setText(t("foul.pick"));
            action.setEnabled(false);
        };
        reset.run();
        // Нажатие на строку ставит неподтверждённый фол, повторное — снимает; другая строка — переносит.
        view.setListener(number -> {
            int previous = chosen[0];
            if (previous != 0) {
                view.setRow(previous, game.fouls(previous), false);
            }
            if (previous == number) {
                reset.run();
                return;
            }
            chosen[0] = number;
            view.setRow(number, game.fouls(number), true);
            action.setText(t("foul.confirm", number));
            action.setEnabled(true);
        });
        // Кнопка срабатывает один раз — после каждого фола обработчик ставится заново.
        Runnable[] confirm = new Runnable[1];
        confirm[0] = () -> {
            action.setOnClick(confirm[0]);
            int n = chosen[0];
            int count = game.addFoul(n);
            view.setRow(n, count, false);
            if (count > Game.MAX_FOULS) {
                game.eliminate(n);
                removed.add(n);
                removedToday = true;
                view.disable(n);
                game.log().add(t("log.foulRemoved", n, count, at));
                voice.say(t("voice.foulRemoved", n));
            } else {
                game.log().add(t("log.foul", n, count, at));
                voice.say(t("voice.foul." + count, n));
            }
            reset.run();
        };
        action.setOnClick(confirm[0]);
        GameUi.Button back = view.back();
        back.setText(t("foul.back"));
        back.setOnClick(() -> {
            view.close();
            openOn = -1;
            int screen = screens.id();
            if (!removed.isEmpty()) {
                if (!listener.onRemoved()) {
                    return;
                }
                afterRemoval.run();
            }
            if (screen == screens.id()) {
                // Остались на том же экране — «Фол» снова работает, отложенное продолжается.
                rearm.run();
                Runnable next = pending;
                pending = null;
                if (next != null && pendingOn == screen) {
                    next.run();
                }
            }
        });
    }
}