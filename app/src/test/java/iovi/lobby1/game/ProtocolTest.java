package iovi.lobby1.game;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.EnumSet;

import iovi.lobby1.game.Protocol.Mark;

public class ProtocolTest {
    @Test
    public void atMostOneSheriff() {
        Protocol p = new Protocol(10);
        assertTrue(p.toggle(1, Mark.SHERIFF));
        assertFalse(p.toggle(2, Mark.SHERIFF));
        assertEquals(EnumSet.of(Mark.RED, Mark.BLACK), p.allowed(2));
        // Шерифа можно перенести, сняв отметку с прежнего.
        assertTrue(p.toggle(1, Mark.SHERIFF));
        assertNull(p.mark(1));
        assertTrue(p.toggle(2, Mark.SHERIFF));
    }

    @Test
    public void atMostThreeBlack() {
        Protocol p = new Protocol(10);
        for (int n = 1; n <= 3; n++) {
            assertTrue(p.toggle(n, Mark.BLACK));
        }
        assertFalse(p.toggle(4, Mark.BLACK));
        assertFalse(p.allowed(4).contains(Mark.BLACK));
        // У уже отмеченного чёрного отметка остаётся доступной и меняется на другую.
        assertTrue(p.allowed(1).contains(Mark.BLACK));
        assertTrue(p.toggle(1, Mark.RED));
        assertTrue(p.toggle(4, Mark.BLACK));
        assertEquals(Arrays.asList(2, 3, 4), p.players(Mark.BLACK));
    }

    @Test
    public void atMostSevenRedWithSheriff() {
        Protocol p = new Protocol(10);
        assertTrue(p.toggle(1, Mark.SHERIFF));
        for (int n = 2; n <= 7; n++) {
            assertTrue(p.toggle(n, Mark.RED));
        }
        assertFalse(p.toggle(8, Mark.RED));
        assertEquals(EnumSet.of(Mark.BLACK), p.allowed(8));
    }

    @Test
    public void authorCannotMarkHimself() {
        Protocol p = new Protocol(5);
        assertTrue(p.allowed(5).isEmpty());
        assertFalse(p.toggle(5, Mark.RED));
        assertTrue(p.isEmpty());
    }

    @Test
    public void correctCountsSheriffAsRed() {
        Game game = new Game(Arrays.asList(
                Role.DON, Role.MAFIA, Role.MAFIA, Role.SHERIFF,
                Role.CIVILIAN, Role.CIVILIAN, Role.CIVILIAN, Role.CIVILIAN, Role.CIVILIAN, Role.CIVILIAN));
        Protocol p = new Protocol(10);
        p.toggle(1, Mark.BLACK);   // верно
        p.toggle(4, Mark.RED);     // верно: шериф красный
        p.toggle(5, Mark.SHERIFF); // неверно
        p.toggle(6, Mark.BLACK);   // неверно
        assertEquals(2, p.correct(game));
        assertEquals(4, p.size());
    }
}