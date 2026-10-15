package iovi.lobby1.flow;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.IntConsumer;

import iovi.lobby1.game.Game;
import iovi.lobby1.game.Protocol;
import iovi.lobby1.game.Role;
import iovi.lobby1.text.Texts;

/**
 * Партия целиком через {@link GameFlow} с поддельным интерфейсом и виртуальным временем:
 * бот нажимает кнопки, как игроки за столом.
 */
public class GameFlowTest {
    private static final List<Role> ROLES = Arrays.asList(
            Role.DON, Role.MAFIA, Role.MAFIA, Role.SHERIFF,
            Role.CIVILIAN, Role.CIVILIAN, Role.CIVILIAN, Role.CIVILIAN, Role.CIVILIAN, Role.CIVILIAN);

    /**
     * День 1: выставлен один 5 — голосования нет. Ночь 1: мафия убивает 6, лучший ход 1, 2, 3.
     * День 2: выставлены 7, 8, 9; дважды поровну (3/3/3) → «поднять всех», 5 голосов «за» —
     * уходят трое, за столом 3 чёрных и 3 красных: победа мафии.
     */
    @Test
    public void fullGameWithTieAndLiftAll() {
        Map<Integer, Map<Integer, Integer>> nominations = new HashMap<>();
        nominations.put(1, map(1, 5));
        nominations.put(2, map(2, 7, 3, 8, 4, 9));
        Table table = new Table(nominations, Arrays.asList(3, 3, 3, 3, 5), 6);
        // Четвёртый чёрный и второй шериф не принимаются — протокол не противоречит раздаче.
        table.protocols.put(6, Arrays.asList(
                new Mark(1, Protocol.Mark.BLACK), new Mark(2, Protocol.Mark.BLACK), new Mark(3, Protocol.Mark.BLACK),
                new Mark(7, Protocol.Mark.BLACK), new Mark(4, Protocol.Mark.SHERIFF), new Mark(5, Protocol.Mark.SHERIFF),
                new Mark(5, Protocol.Mark.RED), new Mark(9, Protocol.Mark.RED), new Mark(9, Protocol.Mark.RED)));
        table.play();

        String log = table.game.log().toString();
        assertTrue(log, log.contains("Голосование не проводилось"));
        assertTrue(log, log.contains("Дон мафии (игрок 1) стреляет в игрока 6"));
        assertTrue(log, log.contains("Дон проверил игрока 2: не шериф"));
        assertTrue(log, log.contains("Шериф проверил игрока 1: МАФИЯ"));
        assertTrue(log, log.contains("Убит игрок 6"));
        assertTrue(log, log.contains("Голосование: 7 — 3, 8 — 3, 9 — 3"));
        assertTrue(log, log.contains("Ничья: 7, 8, 9"));
        assertTrue(log, log.contains("Переголосование: 7 — 3, 8 — 3, 9 — 3"));
        assertTrue(log, log.contains("Поднять всех (7, 8, 9): за 5 из 9 — покидают стол"));
        assertTrue(log, log.contains("Лучший ход игрока 6: 1, 2, 3 — угадано мафии: 3 из 3"));
        assertTrue(log, log.contains("Протокол игрока 6: красные — 5; чёрные — 1, 2, 3; шериф — 4"));
        assertTrue(log, log.contains("Игрок 7 не оставил протокол"));
        assertTrue(log, log.contains("Игрок 9 не оставил протокол"));
        assertTrue(log, log.endsWith("Победила мафия\n  Лучший ход игрока 6: 1, 2, 3 — угадано мафии: 3 из 3"
                + "\n  Протокол игрока 6: красные — 5; чёрные — 1, 2, 3; шериф — 4. Верно: 5 из 5"));
        assertEquals("Победила мафия", table.ui.gameOverTitle);

        List<String> said = table.voice.said;
        assertTrue(said.contains("Игрок номер 10"));
        assertTrue(said.contains("Наступает ночь. Мафия просыпается. У вас минута на договорку."));
        assertTrue(said.contains("Свободная посадка. 40 секунд."));
        assertTrue(said.contains("10 секунд."));
        assertTrue(said.contains("Игрок 7, у вас 30 секунд."));
        assertTrue(said.contains("7, кто за 7?"));
        assertTrue(said.contains("Кто за то, чтобы все игроки 7, 8, 9 покинули стол?"));
        assertTrue(said.contains("Лучший ход игрока 6: номера 1, 2, 3."));
        assertTrue(said.contains("Игрок 6, ваш протокол."));
        int protocolAt = said.indexOf("Протокол.");
        assertEquals(Arrays.asList("Протокол.",
                        PAUSE, "5 — красный.", PAUSE, "1 — чёрный.", PAUSE, "2 — чёрный.", PAUSE, "3 — чёрный.",
                        PAUSE, "4 — шериф."),
                said.subList(protocolAt, protocolAt + 11));
        assertTrue(said.contains("Протокол не оставлен."));
        assertTrue(said.indexOf("Спасибо, игрок 6.") < said.indexOf("Игрок 6, ваш протокол."));
        assertEquals("Игра окончена. Победила мафия!", said.get(said.size() - 1));
        assertFalse(table.music.playing);
    }

    /**
     * Чёрные стреляют в разных игроков — промахи в ночи 1 и 2. Со второго дня единственная
     * кандидатура уходит без голосования; день 3 — голосование 5:4; ночью 3 оставшийся
     * мафиози убивает 4; день 4 — уходит последний чёрный, победа мирных.
     */
    @Test
    public void missesSingleNomineeAndVote() {
        Map<Integer, Map<Integer, Integer>> nominations = new HashMap<>();
        nominations.put(2, map(2, 1));
        nominations.put(3, map(3, 2, 5, 3));
        nominations.put(4, map(5, 3));
        Table table = new Table(nominations, Arrays.asList(5), 0);
        table.play();

        String log = table.game.log().toString();
        assertTrue(log, log.contains("Голосование не проводилось"));
        assertTrue(log, log.contains("Промах — никто не убит"));
        assertTrue(log, log.contains("Единственная кандидатура — игрок 1 покидает стол"));
        assertTrue(log, log.contains("Голосование: 2 — 5, 3 — 4"));
        assertTrue(log, log.contains("Стол покидает игрок 2"));
        assertTrue(log, log.contains("Убит игрок 4"));
        assertTrue(log, log.contains("Единственная кандидатура — игрок 3 покидает стол"));
        assertFalse(log, log.contains("Лучший ход"));
        assertEquals("Победили мирные жители", table.ui.gameOverTitle);
    }

    /** День 2: все 10 голосов за дона — вторую кандидатуру не называют, у неё 0 голосов. */
    @Test
    public void allVotesGivenSkipsRemainingCandidates() {
        Map<Integer, Map<Integer, Integer>> nominations = blacksLeaveOneByOne();
        nominations.put(2, map(5, 1, 6, 2));
        Table table = new Table(nominations, Arrays.asList(10), 0);
        table.play();

        String log = table.game.log().toString();
        assertTrue(log, log.contains("Голосование: 1 — 10, 2 — 0"));
        assertTrue(log, log.contains("Стол покидает игрок 1"));
        List<String> said = table.voice.said;
        assertTrue(said.contains("1, кто за 1?"));
        assertFalse(said.contains("2, кто за 2?"));
        assertEquals("Победили мирные жители", table.ui.gameOverTitle);
    }

    /** Чёрные выходят по одному со второго дня, начиная с дона; говорящий 5 выставляет их. */
    private static Map<Integer, Map<Integer, Integer>> blacksLeaveOneByOne() {
        Map<Integer, Map<Integer, Integer>> nominations = new HashMap<>();
        nominations.put(2, map(5, 1));
        nominations.put(3, map(5, 2));
        nominations.put(4, map(5, 3));
        return nominations;
    }

    /** Ночью никто не действует: после отсчёта выстрел и проверка не принимаются — промах. */
    @Test
    public void nightActionsCloseAfterCountdown() {
        Table table = new Table(blacksLeaveOneByOne(), Arrays.asList(), 6);
        table.sleepy = true;
        table.play();

        String log = table.game.log().toString();
        assertTrue(log, log.contains("Дон мафии (игрок 1) не выстрелил — время вышло"));
        assertTrue(log, log.contains("Мафия (игрок 2) не выстрелил — время вышло"));
        assertTrue(log, log.contains("Шериф (игрок 4) не сделал проверку — время вышло"));
        assertTrue(log, log.contains("Промах — никто не убит"));
        assertFalse(log, log.contains("Убит игрок"));
        assertEquals("Победили мирные жители", table.ui.gameOverTitle);
    }

    /**
     * Дон и мафия 3 выбирают промах (дон после этого всё равно проверяет), мафия 2 стреляет в 6 —
     * каждую ночь промах.
     */
    @Test
    public void oneMissMeansMiss() {
        Table table = new Table(blacksLeaveOneByOne(), Arrays.asList(), 6);
        table.missers.addAll(Arrays.asList(1, 3));
        table.play();

        String log = table.game.log().toString();
        assertTrue(log, log.contains("Дон мафии (игрок 1) выбрал промах"));
        assertTrue(log, log.contains("Мафия (игрок 2) стреляет в игрока 6"));
        assertTrue(log, log.contains("Дон проверил игрока"));
        assertTrue(log, log.contains("Промах — никто не убит"));
        assertFalse(log, log.contains("Убит игрок"));
        assertFalse(log, log.contains("время вышло"));
        assertEquals("Победили мирные жители", table.ui.gameOverTitle);
    }

    @Test
    public void plural() {
        assertEquals("1 голос", Texts.plural("votes", 1));
        assertEquals("3 голоса", Texts.plural("votes", 3));
        assertEquals("5 голосов", Texts.plural("votes", 5));
        assertEquals("11 голосов", Texts.plural("votes", 11));
        assertEquals("21 голос", Texts.plural("votes", 21));
        assertEquals("0 голосов", Texts.plural("votes", 0));
    }

    private static final class Mark {
        final int player;
        final Protocol.Mark mark;

        Mark(int player, Protocol.Mark mark) {
            this.player = player;
            this.mark = mark;
        }
    }

    private static Map<Integer, Integer> map(int... pairs) {
        Map<Integer, Integer> m = new HashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            m.put(pairs[i], pairs[i + 1]);
        }
        return m;
    }

    // ───────────────────────────── Стол с ботом ─────────────────────────────

    private static final class Table {
        final Game game = new Game(ROLES);
        final FakeScheduler scheduler = new FakeScheduler();
        final FakeVoice voice = new FakeVoice();
        final FakeMusic music = new FakeMusic();
        final FakeUi ui = new FakeUi();
        final GameFlow flow = new GameFlow(game, ui, voice, music, scheduler);
        /** День → (говорящий → кого выставляет). */
        final Map<Integer, Map<Integer, Integer>> nominations;
        final Deque<Integer> votes;
        /** Цель мафии ночью; 0 — каждый стреляет в первого доступного, кроме себя (промах). */
        final int shotTarget;
        /** Чёрные, которые ночью выбирают промах. */
        final List<Integer> missers = new ArrayList<>();
        /** Ночью никто не стреляет и не проверяет — ждут конца отсчёта. */
        boolean sleepy;
        /** Автор протокола → нажатия по порядку; кого нет — оставляет протокол пустым. */
        final Map<Integer, List<Mark>> protocols = new HashMap<>();

        Table(Map<Integer, Map<Integer, Integer>> nominations, List<Integer> votes, int shotTarget) {
            this.nominations = nominations;
            this.votes = new ArrayDeque<>(votes);
            this.shotTarget = shotTarget;
        }

        void play() {
            flow.start();
            for (int step = 0; !flow.isOver(); step++) {
                if (step > 200_000) {
                    fail("Партия не закончилась; экран: " + ui.current.title);
                }
                if (!act() && !scheduler.runNext()) {
                    fail("Игра зависла на экране: " + ui.current.title);
                }
            }
        }

        /** Одно действие игрока на текущем экране; false — ждать (времени или голоса). */
        boolean act() {
            Screen s = ui.current;
            switch (s.kind) {
                case "speech": {
                    Integer nominee = nominations.getOrDefault(game.dayNumber(), new HashMap<>()).get(ui.handoffPlayer);
                    if (!s.done && nominee != null) {
                        s.done = true;
                        s.grid.click(nominee);
                        return s.confirm.click();
                    }
                    return false;
                }
                case "vote": {
                    if (!s.action.clickable()) {
                        return false;
                    }
                    int target = votes.removeFirst();
                    while (s.count < target) {
                        s.plus.run();
                    }
                    return s.action.click();
                }
                case "night": {
                    boolean nightAction = s.title.equals(Texts.get("shot.title"))
                            || s.title.equals(Texts.get("don.title"))
                            || s.title.equals(Texts.get("sheriff.title"));
                    if (s.grid != null && !s.done && !(sleepy && nightAction)) {
                        s.done = true;
                        if (s.extra != null && missers.contains(ui.handoffPlayer)) {
                            return s.extra.click();
                        }
                        pick(s);
                    }
                    return s.action.click();
                }
                case "protocol": {
                    if (!s.done) {
                        s.done = true;
                        for (Mark m : protocols.getOrDefault(ui.handoffPlayer, new ArrayList<>())) {
                            // Недопустимую отметку бот всё равно нажимает — экран её не примет.
                            s.markListener.accept(m.player, m.mark);
                        }
                    }
                    return s.action.click();
                }
                default:
                    return s.action != null && s.action.click();
            }
        }

        private void pick(Screen s) {
            List<Integer> enabled = new ArrayList<>(s.grid.enabled);
            if (s.title.equals(Texts.get("shot.title"))) {
                int shooter = ui.handoffPlayer;
                if (shotTarget != 0) {
                    s.grid.click(shotTarget);
                } else {
                    // Каждый стреляет в соседа справа — цели разные, промах.
                    s.grid.click(shooter % Game.PLAYER_COUNT + 1);
                }
            } else if (s.title.equals(Texts.get("bestMove.title"))) {
                for (int k = 0; k < 3; k++) {
                    s.grid.click(enabled.get(k));
                }
            } else {
                s.grid.click(enabled.get(0));
            }
        }
    }

    // ───────────────────────────── Подделки ─────────────────────────────

    private static final class FakeScheduler implements Scheduler {
        private static final class Task {
            final long at;
            final long seq;
            final Runnable action;

            Task(long at, long seq, Runnable action) {
                this.at = at;
                this.seq = seq;
                this.action = action;
            }
        }

        private final List<Task> tasks = new ArrayList<>();
        private long now = 0;
        private long seq = 0;

        @Override
        public void post(Runnable action, long delayMs) {
            tasks.add(new Task(now + delayMs, seq++, action));
        }

        @Override
        public void cancel(Runnable action) {
            tasks.removeIf(t -> t.action == action);
        }

        @Override
        public void cancelAll() {
            tasks.clear();
        }

        @Override
        public long now() {
            return now;
        }

        /** Выполняет ближайшее отложенное действие, передвигая время. */
        boolean runNext() {
            Task next = null;
            for (Task t : tasks) {
                if (next == null || t.at < next.at || (t.at == next.at && t.seq < next.seq)) {
                    next = t;
                }
            }
            if (next == null) {
                return false;
            }
            tasks.remove(next);
            now = next.at;
            next.action.run();
            return true;
        }
    }

    /** Голос, который договаривает фразу мгновенно. */
    private static final class FakeVoice implements Voice {
        final List<String> said = new ArrayList<>();

        @Override
        public void say(String text) {
            say(text, null);
        }

        @Override
        public void say(String text, Runnable onDone) {
            assertFalse("Пустая фраза", text == null || text.isEmpty());
            said.add(text);
            if (onDone != null) {
                onDone.run();
            }
        }

        @Override
        public void pause(long ms) {
            said.add(PAUSE);
        }

        @Override
        public void beep() {
        }

        @Override
        public void endSignal() {
        }
    }

    /** Пауза в очереди голоса — так она записывается в {@link FakeVoice#said}. */
    private static final String PAUSE = "[пауза]";

    private static final class FakeMusic implements Music {
        boolean playing;

        @Override
        public void start() {
            playing = true;
        }

        @Override
        public void stop() {
            playing = false;
        }
    }

    private static final class FakeButton implements GameUi.Button {
        String text;
        boolean enabled = true;
        Runnable onClick;

        @Override
        public void setText(String text) {
            this.text = text;
        }

        @Override
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        @Override
        public void setOnClick(Runnable action) {
            onClick = action;
        }

        boolean clickable() {
            return enabled && onClick != null;
        }

        /** Нажатие срабатывает один раз, как у настоящей кнопки. */
        boolean click() {
            if (!clickable()) {
                return false;
            }
            Runnable action = onClick;
            onClick = null;
            action.run();
            return true;
        }
    }

    private static final class FakeGrid implements GameUi.Grid {
        final Collection<Integer> enabled;
        IntConsumer listener;

        FakeGrid(Collection<Integer> enabled) {
            this.enabled = new ArrayList<>(enabled);
        }

        void click(int number) {
            assertTrue("Номер " + number + " недоступен", enabled.contains(number));
            listener.accept(number);
        }

        @Override
        public void setListener(IntConsumer onNumber) {
            listener = onNumber;
        }

        @Override
        public void select(Collection<Integer> numbers) {
        }

        @Override
        public void lock(Collection<Integer> numbers) {
            enabled.clear();
        }
    }

    /** Текущий экран: всё, что на нём видно, и чем можно нажать. */
    private static final class Screen
            implements GameUi.SpeechView, GameUi.VoteView, GameUi.NightView, GameUi.ProtocolView {
        final String kind;
        final String title;
        FakeButton action;
        FakeButton extra;
        FakeButton confirm;
        FakeGrid grid;
        Runnable plus;
        int count;
        boolean done;
        BiConsumer<Integer, Protocol.Mark> markListener;

        @Override
        public void setListener(BiConsumer<Integer, Protocol.Mark> onMark) {
            markListener = onMark;
        }

        @Override
        public void setRow(int player, Protocol.Mark mark, Set<Protocol.Mark> allowed) {
        }

        @Override
        public void lock() {
        }

        @Override
        public void setSummary(String text) {
        }

        Screen(String kind, String title) {
            assertFalse("Пустой заголовок экрана " + kind, title == null || title.isEmpty());
            this.kind = kind;
            this.title = title;
        }

        @Override
        public void setTime(long remainingMs) {
        }

        @Override
        public void setPaused(boolean paused) {
        }

        @Override
        public void setControlsEnabled(boolean enabled) {
        }

        @Override
        public GameUi.Grid grid() {
            return grid;
        }

        @Override
        public GameUi.Button confirm() {
            return confirm;
        }

        @Override
        public void setHint(String text) {
        }

        @Override
        public void setNominees(String text) {
        }

        @Override
        public void setOnMinus(Runnable action) {
        }

        @Override
        public void setOnPlus(Runnable action) {
            plus = action;
        }

        @Override
        public void setCount(int count, String hint, boolean canMinus, boolean canPlus) {
            this.count = count;
        }

        @Override
        public GameUi.Button action() {
            return action;
        }

        @Override
        public GameUi.Button showExtra(String text) {
            extra = new FakeButton();
            extra.setText(text);
            return extra;
        }

        @Override
        public void hideExtra() {
            extra = null;
        }

        @Override
        public void hideGrid() {
        }

        @Override
        public void showResult(String text) {
        }
    }

    private static final class FakeUi implements GameUi {
        Screen current = new Screen("start", "—");
        int handoffPlayer;
        String gameOverTitle;

        @Override
        public void setPhase(String label) {
        }

        @Override
        public Button showMessage(String title, String text, String button, Runnable next) {
            current = new Screen("message", title);
            current.action = new FakeButton();
            if (button != null) {
                current.action.setText(button);
                current.action.setOnClick(next);
            }
            return current.action;
        }

        @Override
        public void showHandoff(String title, int player, String hint, String button, Runnable next) {
            handoffPlayer = player;
            current = new Screen("handoff", title);
            current.action = new FakeButton();
            current.action.setOnClick(next);
        }

        @Override
        public void showCard(String player, String roleTitle, Role role, long durationMs) {
            current = new Screen("card", roleTitle);
        }

        @Override
        public TimerView showTimer(String title, String subtitle, TimerControls controls) {
            current = new Screen("timer", title);
            return current;
        }

        @Override
        public SpeechView showSpeech(String title, String hint, Collection<Integer> enabled, TimerControls controls) {
            current = new Screen("speech", title);
            current.grid = new FakeGrid(enabled);
            current.confirm = new FakeButton();
            current.confirm.enabled = false;
            return current;
        }

        @Override
        public VoteView showVoteInput(String title, String question) {
            current = new Screen("vote", title);
            current.action = new FakeButton();
            return current;
        }

        @Override
        public NightView showNight(String header, String title, String hint, Collection<Integer> enabled) {
            current = new Screen("night", title);
            current.action = new FakeButton();
            if (enabled != null) {
                current.grid = new FakeGrid(enabled);
            }
            return current;
        }

        @Override
        public ProtocolView showProtocol(String header, String title, String hint, List<Integer> players,
                                         Map<Protocol.Mark, String> labels) {
            current = new Screen("protocol", title);
            current.action = new FakeButton();
            return current;
        }

        @Override
        public void showGameOver(String title, String log) {
            current = new Screen("over", title);
            gameOverTitle = title;
        }
    }
}