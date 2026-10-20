package iovi.lobby1.flow;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
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
        // Заголосованные не в нулевом круге протокол не оставляют.
        assertFalse(log, log.contains("Игрок 7 не оставил протокол"));
        assertFalse(log, log.contains("Игрок 9 не оставил протокол"));
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
        assertTrue(said.contains("Переголосование. На голосовании игроки: 7, 8, 9."));
        assertTrue(said.contains("Кто за то, чтобы все игроки 7, 8, 9 покинули стол?"));
        // После второй ничьей сразу вопрос «поднять всех» — сразу после «Спасибо» за последнюю кандидатуру.
        assertEquals("Кто за то, чтобы все игроки 7, 8, 9 покинули стол?",
                said.get(said.lastIndexOf("9, кто за 9?") + 2));
        assertTrue(said.contains("Лучший ход игрока 6: номера 1, 2, 3."));
        // Убитому в первую ночь телефон передаётся сразу после утреннего объявления, без кнопки «Лучший ход».
        assertFalse(table.ui.messageButtons.contains("Лучший ход"));
        assertTrue(said.indexOf("Игрок 6, ваш лучший ход.") > said.indexOf("Наступает утро. Ночью убит игрок 6."));
        assertTrue(said.contains("Игрок 6, ваш протокол."));
        int protocolAt = said.indexOf("Протокол.");
        assertEquals(Arrays.asList("Протокол.",
                        PAUSE, "5 — красный.", PAUSE, "1 — чёрный.", PAUSE, "2 — чёрный.", PAUSE, "3 — чёрный.",
                        PAUSE, "4 — шериф."),
                said.subList(protocolAt, protocolAt + 11));
        assertFalse(said.contains("Протокол не оставлен."));
        assertFalse(said.contains("Игрок 7, ваш протокол."));
        assertTrue(said.indexOf("Спасибо, игрок 6.") < said.indexOf("Игрок 6, ваш протокол."));
        assertEquals("Игра окончена. Победила мафия!", said.get(said.size() - 1));
        // На оправдательных речах доп. время не предлагается, хотя фолов у 7, 8, 9 нет.
        int tieSpeeches = 0;
        for (Screen s : table.ui.timers) {
            if (s.title.equals(Texts.get("tie.title"))) {
                tieSpeeches++;
                assertEquals(null, s.bonus);
            }
        }
        assertEquals(3, tieSpeeches);
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

    /**
     * День 1: выставлены 5 и 7, 6 голосов против 4 — уходит 5: лучший ход, прощальная минута, протокол.
     * Ночами промахи; со второго дня чёрные уходят по одному без лучшего хода и протокола.
     */
    @Test
    public void votedOutOnFirstDayGetsBestMoveAndProtocol() {
        Map<Integer, Map<Integer, Integer>> nominations = new HashMap<>();
        nominations.put(1, map(1, 5, 2, 7));
        nominations.put(2, map(8, 1));
        nominations.put(3, map(8, 2));
        nominations.put(4, map(8, 3));
        Table table = new Table(nominations, Arrays.asList(6), 0);
        table.protocols.put(5, Arrays.asList(new Mark(1, Protocol.Mark.BLACK)));
        // В нулевой круг доп. время можно взять только на прощальной речи.
        table.extraTime.add("1:5");
        table.play();

        String log = table.game.log().toString();
        assertTrue(log, log.contains("Голосование: 5 — 6, 7 — 4"));
        assertTrue(log, log.contains("Лучший ход игрока 5: 1, 2, 3"));
        assertTrue(log, log.contains("Протокол игрока 5: чёрные — 1"));
        assertTrue(log, log.contains("Игрок 5 взял доп. время: +2 фола (теперь 2) — прощальная речь игрока 5"));
        assertFalse(log, log.contains("Лучший ход игрока 1"));
        assertFalse(log, log.contains("Игрок 1 не оставил протокол"));
        assertTrue(log, log.endsWith("Победили мирные жители\n  Лучший ход игрока 5: 1, 2, 3 — угадано мафии: 3 из 3"
                + "\n  Протокол игрока 5: чёрные — 1. Верно: 1 из 1"));
        List<String> said = table.voice.said;
        assertTrue(said.indexOf("Лучший ход игрока 5: номера 1, 2, 3.") < said.indexOf("Игрок 5, ваша прощальная минута."));
        assertTrue(said.indexOf("Спасибо, игрок 5.") < said.indexOf("Игрок 5, ваш протокол."));
        // После голосования сразу передача телефона для лучшего хода, без кнопки «Лучший ход».
        assertFalse(table.ui.messageButtons.contains("Лучший ход"));
        assertEquals("Победили мирные жители", table.ui.gameOverTitle);
    }

    /**
     * День 1: 5 и 7 дважды поровну (5/5), «поднять всех» — 6 из 10, уходят оба: без лучшего хода
     * и протокола, только прощальные минуты.
     */
    @Test
    public void liftedTogetherOnFirstDayLeaveNoBestMoveOrProtocol() {
        Map<Integer, Map<Integer, Integer>> nominations = new HashMap<>();
        nominations.put(1, map(1, 5, 2, 7));
        nominations.put(2, map(8, 1));
        nominations.put(3, map(8, 2));
        nominations.put(4, map(8, 3));
        Table table = new Table(nominations, Arrays.asList(5, 5, 6), 0);
        table.play();

        String log = table.game.log().toString();
        assertTrue(log, log.contains("Поднять всех (5, 7): за 6 из 10 — покидают стол"));
        assertFalse(log, log.contains("Лучший ход"));
        assertFalse(log, log.contains("Игрок 5 не оставил протокол"));
        assertFalse(log, log.contains("Игрок 7 не оставил протокол"));
        // Убитый ночью протокол оставляет по-прежнему.
        assertTrue(log, log.contains("Игрок 4 не оставил протокол"));
        List<String> said = table.voice.said;
        assertTrue(said.contains("Игрок 5, ваша прощальная минута."));
        assertTrue(said.contains("Игрок 7, ваша прощальная минута."));
        assertFalse(said.contains("Игрок 5, ваш лучший ход."));
        assertFalse(said.contains("Игрок 5, ваш протокол."));
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

    /**
     * Ночью 1 убит 6; после протокола он нажимает «Завершить игру», сначала отменяет, потом
     * подтверждает — партия заканчивается без победителя, показывается итоговый лог.
     */
    @Test
    public void stopGameAfterElimination() {
        Table table = new Table(blacksLeaveOneByOne(), Arrays.asList(), 6);
        table.stopBy = 6;
        table.ui.confirmAnswers.addAll(Arrays.asList(false, true));
        table.play();

        assertEquals(2, table.ui.confirmations);
        assertEquals("Игра завершена досрочно", table.ui.gameOverTitle);
        String log = table.game.log().toString();
        assertTrue(log, log.contains("Убит игрок 6"));
        assertTrue(log, log.contains("Игрок 6 не оставил протокол"));
        assertTrue(log, log.endsWith("Итог\n  Игра завершена досрочно\n  Лучший ход игрока 6: 1, 2, 3 — угадано мафии: 3 из 3"));
        assertFalse(log, log.contains("День 2"));
        List<String> said = table.voice.said;
        assertEquals("Игра завершена.", said.get(said.size() - 1));
    }

    /** День 2: заголосован дон — протокола нет, «Завершить игру» на экране «покинул стол». */
    @Test
    public void stopGameAfterEliminationWithoutProtocol() {
        Table table = new Table(blacksLeaveOneByOne(), Arrays.asList(), 0);
        table.stopOnLeft = true;
        table.ui.confirmAnswers.add(true);
        table.play();

        assertEquals(1, table.ui.confirmations);
        assertEquals("Игра завершена досрочно", table.ui.gameOverTitle);
        String log = table.game.log().toString();
        assertTrue(log, log.contains("Единственная кандидатура — игрок 1 покидает стол"));
        assertFalse(log, log.contains("протокол"));
        assertFalse(log, log.contains("Ночь 2"));
    }

    /**
     * День 1: перед речью 2 — два фола игроку 3 и по три игрокам 7 и 8; на паузе речи 4 — фол игроку 6.
     * У 7 и 8 ближайшая речь — 30 секунд, у 8 следующие снова минута.
     * День 2: перед ним объявляют 2 и 3 фола; перед речью 5 — четвёртый фол 7: он удалён без
     * прощальной минуты, его речь пропускается, голосования нет, хотя 5 выставил 1.
     * Дальше чёрные уходят по одному, победа мирных.
     */
    @Test
    public void foulsAndRemoval() {
        Map<Integer, Map<Integer, Integer>> nominations = new HashMap<>();
        nominations.put(2, map(5, 1));
        nominations.put(3, map(5, 1));
        nominations.put(4, map(5, 2));
        nominations.put(5, map(5, 3));
        Table table = new Table(nominations, Arrays.asList(), 0);
        table.fouls.put("handoff:1:2", Arrays.asList(3, 3, 7, 7, 7, 8, 8, 8));
        table.fouls.put("speech:1:4", Arrays.asList(6));
        table.fouls.put("handoff:2:5", Arrays.asList(7));
        table.play();

        String log = table.game.log().toString();
        assertTrue(log, log.contains("1-й фол игроку 3 — перед речью игрока 2"));
        assertTrue(log, log.contains("2-й фол игроку 3 — перед речью игрока 2"));
        assertTrue(log, log.contains("3-й фол игроку 7 — перед речью игрока 2"));
        assertTrue(log, log.contains("1-й фол игроку 6 — речь игрока 4"));
        assertTrue(log, log.contains("4-й фол игроку 7 — перед речью игрока 5. Игрок 7 удалён из-за стола"));
        assertTrue(log, log.contains("Голосование не проводилось: игрок удалён по фолам"));
        assertFalse(log, log.contains("Игрок 7 не оставил протокол"));
        assertEquals("Победили мирные жители", table.ui.gameOverTitle);

        List<String> said = table.voice.said;
        assertTrue(said.contains("Первый фол, игрок номер 3."));
        assertTrue(said.contains("Второй фол, игрок номер 3."));
        assertTrue(said.contains("Третий фол, игрок номер 7."));
        assertTrue(said.contains("Первый фол, игрок номер 6."));
        String removed = "Четвёртый фол, игрок номер 7, покиньте игровой стол.";
        assertTrue(said.contains(removed));
        // Перед вторым днём — у кого 2 и 3 фола.
        int day2 = said.indexOf("Начинается день 2. Первым говорит игрок 2.");
        assertEquals(Arrays.asList("2 фола у игрока 3.", "3 фола у игроков — 7, 8."), said.subList(day2 - 2, day2));
        // У удалённого нет ни прощальной минуты, ни речи.
        assertFalse(said.contains("Игрок 7, ваша прощальная минута."));
        assertTrue(said.lastIndexOf("Игрок 7") < said.indexOf(removed));
        // С тремя фолами ближайшая дневная речь — 30 секунд, следующие — снова минута; с двумя — как обычно.
        assertEquals(30_000, speechTime(table, "Речь игрока 7", 0));
        assertEquals(30_000, speechTime(table, "Речь игрока 8", 0));
        assertEquals(60_000, speechTime(table, "Речь игрока 8", 1));
        assertEquals(60_000, speechTime(table, "Речь игрока 8", 2));
        assertEquals(60_000, speechTime(table, "Речь игрока 3", 0));
    }

    /** Длительность речи с таким заголовком; index — какая по счёту за партию. */
    private static long speechTime(Table table, String title, int index) {
        int k = 0;
        for (Screen s : table.ui.speeches) {
            if (s.title.equals(title) && k++ == index) {
                return s.startTime;
            }
        }
        throw new AssertionError("Нет речи: " + title + " #" + index);
    }

    /**
     * День 2: игрок 6 (без фолов) берёт доп. время — 2 фола, речь 90 секунд, «10 секунд» звучит снова.
     * День 3: у 6 два фола — кнопки нет. В первый день кнопки нет ни у кого.
     */
    @Test
    public void extraTime() {
        Table table = new Table(blacksLeaveOneByOne(), Arrays.asList(), 0);
        table.extraTime.add("2:6");
        table.play();

        String log = table.game.log().toString();
        assertTrue(log, log.contains("Игрок 6 взял доп. время: +2 фола (теперь 2) — речь игрока 6"));
        List<String> said = table.voice.said;
        int taken = said.indexOf("Взято доп время.");
        assertTrue(taken > 0);
        assertEquals(2, table.game.fouls(6));
        assertTrue(said.contains("2 фола у игрока 6."));
        // После доп. времени снова прозвучит «10 секунд» — до конца речи 6.
        int end = said.subList(taken, said.size()).indexOf("Спасибо, время.") + taken;
        assertTrue(said.subList(taken, end).contains("10 секунд."));

        List<Screen> speeches = table.ui.speeches;
        for (Screen s : speeches.subList(0, Game.PLAYER_COUNT)) {
            assertEquals("Доп. время в нулевой круг: " + s.title, null, s.bonus);
        }
        Screen day2 = speeches.get(Game.PLAYER_COUNT + 4);
        assertEquals("Речь игрока 6", day2.title);
        assertEquals(90_000, day2.maxTime);
        Screen day3 = speeches.stream().skip(2 * Game.PLAYER_COUNT).filter(s -> s.title.equals("Речь игрока 6"))
                .findFirst().get();
        assertEquals(null, day3.bonus);
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
        /** Выбывший, который после своего протокола нажимает «Завершить игру»; 0 — никто. */
        int stopBy;
        /** На экране «покинул стол» (выбывание без протокола) нажимают «Завершить игру». */
        boolean stopOnLeft;
        /**
         * Кому ставят фолы: «handoff:день:игрок» — на передаче телефона игроку, «speech:день:игрок» —
         * на паузе его речи. Номера по порядку, каждый — отдельный фол.
         */
        final Map<String, List<Integer>> fouls = new HashMap<>();
        /**
         * «день:игрок» — на своей речи игрок берёт доп. время; для прощальной речи игрок —
         * тот, кому последним передавали телефон (лучший ход или речь).
         */
        final Set<String> extraTime = new HashSet<>();
        /** Фолы, которые бот вводит на открытом экране фолов. */
        List<Integer> activeFouls;

        /** Есть фолы для этого момента — они станут текущими (один раз). */
        boolean startFouls(String key) {
            activeFouls = fouls.remove(key);
            return activeFouls != null;
        }

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
                case "handoff": {
                    if (s.extra != null && startFouls("handoff:" + game.dayNumber() + ":" + ui.handoffPlayer)) {
                        return s.extra.click();
                    }
                    return s.action.click();
                }
                case "fouls": {
                    for (int n : activeFouls) {
                        // Неподтверждённый фол снимается повторным нажатием и переносится на другую строку.
                        s.grid.click(n);
                        s.grid.click(n);
                        assertEquals(0, s.pendingFoul);
                        assertFalse(s.action.enabled);
                        int other = s.grid.enabled.get(0) == n ? s.grid.enabled.get(1) : s.grid.enabled.get(0);
                        s.grid.click(other);
                        s.grid.click(n);
                        assertEquals(n, s.pendingFoul);
                        int before = s.foulRows.get(n);
                        assertTrue(s.action.click());
                        assertEquals(before + 1, (int) s.foulRows.get(n));
                        assertEquals(0, s.pendingFoul);
                    }
                    activeFouls = null;
                    return s.back.click();
                }
                case "speech": {
                    // Фол на паузе речи: пауза → «Фол» → … → «Назад» → продолжение.
                    if (s.paused) {
                        s.controls.onPauseToggle();
                        assertEquals("«Фол» не пропал после продолжения", null, s.extra);
                        return true;
                    }
                    if (startFouls("speech:" + game.dayNumber() + ":" + ui.handoffPlayer)) {
                        s.controls.onPauseToggle();
                        return s.extra.click();
                    }
                    if (s.bonus != null && extraTime.remove(game.dayNumber() + ":" + ui.handoffPlayer)) {
                        return s.bonus.click();
                    }
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
                            // Сначала номер, потом передумал — промах; выбор принимается только подтверждением.
                            s.grid.click(s.grid.enabled.iterator().next());
                            assertTrue(s.extra.click());
                            assertNotNull("Промах принят без подтверждения", s.extra);
                            assertEquals(Texts.get("shot.confirmMiss"), s.action.text);
                        } else {
                            pick(s);
                        }
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
                    if (s.extra != null && ui.handoffPlayer == stopBy) {
                        return s.extra.click();
                    }
                    return s.action.click();
                }
                case "timer": {
                    // Доп. время на прощальной речи; остальное время таймер идёт сам.
                    if (s.bonus != null && extraTime.remove(game.dayNumber() + ":" + ui.handoffPlayer)) {
                        return s.bonus.click();
                    }
                    return false;
                }
                case "message": {
                    if (s.extra != null && stopOnLeft && Texts.get("stop.button").equals(s.extra.text)) {
                        return s.extra.click();
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
        final List<Integer> enabled;
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
            implements GameUi.SpeechView, GameUi.VoteView, GameUi.NightView, GameUi.ProtocolView, GameUi.MessageView,
            GameUi.FoulView {
        final String kind;
        final String title;
        FakeButton action;
        FakeButton extra;
        FakeButton confirm;
        FakeButton back;
        FakeGrid grid;
        Runnable plus;
        int count;
        boolean done;
        BiConsumer<Integer, Protocol.Mark> markListener;
        GameUi.TimerControls controls;
        boolean paused;
        /** Первое показанное время таймера — длительность речи; -1 — таймера не было. */
        long startTime = -1;
        /** Наибольшее показанное время — растёт после «Доп. время». */
        long maxTime;
        FakeButton bonus;
        /** Экран фолов: закрытие возвращает экран под ним. */
        Runnable onClose;

        /** Строки таблицы фолов: игрок → подтверждённые фолы; pendingFoul — у кого неподтверждённый. */
        final Map<Integer, Integer> foulRows = new HashMap<>();
        int pendingFoul;

        @Override
        public void setListener(IntConsumer onPlayer) {
            grid.listener = onPlayer;
        }

        @Override
        public void setRow(int player, int fouls, boolean pending) {
            foulRows.put(player, fouls);
            if (pending) {
                pendingFoul = player;
            } else if (pendingFoul == player) {
                pendingFoul = 0;
            }
        }

        @Override
        public void disable(int player) {
            grid.enabled.remove(Integer.valueOf(player));
        }

        @Override
        public GameUi.Button back() {
            return back;
        }

        @Override
        public void close() {
            onClose.run();
        }

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
            if (startTime < 0) {
                startTime = remainingMs;
            }
            maxTime = Math.max(maxTime, remainingMs);
        }

        @Override
        public GameUi.Button showBonus(String text) {
            bonus = new FakeButton();
            bonus.setText(text);
            return bonus;
        }

        @Override
        public void hideBonus() {
            bonus = null;
        }

        @Override
        public void setPaused(boolean paused) {
            this.paused = paused;
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
        /** Ответы на диалоги подтверждения по порядку: true — «да». */
        final Deque<Boolean> confirmAnswers = new ArrayDeque<>();
        int confirmations;
        /** Кнопки показанных сообщений по порядку; null — сообщение без кнопки. */
        final List<String> messageButtons = new ArrayList<>();
        /** Экраны с таймером (договорка, прощальные и оправдательные речи…) по порядку. */
        final List<Screen> timers = new ArrayList<>();
        /** Экраны дневных речей по порядку. */
        final List<Screen> speeches = new ArrayList<>();

        @Override
        public void setPhase(String label) {
        }

        @Override
        public MessageView showMessage(String title, String text, String button, Runnable next) {
            current = new Screen("message", title);
            current.action = new FakeButton();
            messageButtons.add(button);
            if (button != null) {
                current.action.setText(button);
                current.action.setOnClick(next);
            }
            return current;
        }

        @Override
        public MessageView showHandoff(String title, int player, String hint, String button, Runnable next) {
            handoffPlayer = player;
            current = new Screen("handoff", title);
            current.action = new FakeButton();
            current.action.setOnClick(next);
            return current;
        }

        @Override
        public void showCard(String player, String roleTitle, Role role, long durationMs) {
            current = new Screen("card", roleTitle);
        }

        @Override
        public TimerView showTimer(String title, String subtitle, TimerControls controls) {
            current = new Screen("timer", title);
            timers.add(current);
            return current;
        }

        @Override
        public SpeechView showSpeech(String title, String hint, Collection<Integer> enabled, TimerControls controls) {
            current = new Screen("speech", title);
            speeches.add(current);
            current.controls = controls;
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
        public FoulView showFouls(String title, String hint, Collection<Integer> enabled) {
            Screen below = current;
            Screen fouls = new Screen("fouls", title);
            fouls.grid = new FakeGrid(enabled);
            fouls.action = new FakeButton();
            fouls.back = new FakeButton();
            fouls.onClose = () -> current = below;
            current = fouls;
            return fouls;
        }

        @Override
        public void confirm(String title, String message, String yes, String no, Runnable onYes, Runnable onNo) {
            confirmations++;
            if (confirmAnswers.removeFirst()) {
                onYes.run();
            } else {
                onNo.run();
            }
        }

        @Override
        public void showGameOver(String title, String log) {
            assertFalse("Экран фолов не закрыт", current.kind.equals("fouls"));
            current = new Screen("over", title);
            gameOverTitle = title;
        }
    }
}