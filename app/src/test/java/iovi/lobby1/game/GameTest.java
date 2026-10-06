package iovi.lobby1.game;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class GameTest {
    private static final Role C = Role.CIVILIAN;

    /** Мафия на местах 2 и 5, дон на 8, шериф на 3. */
    private static Game fixedGame() {
        return new Game(Arrays.asList(C, Role.MAFIA, Role.SHERIFF, C, Role.MAFIA, C, C, Role.DON, C, C));
    }

    @Test
    public void randomDealHasStandardLineup() {
        for (int seed = 0; seed < 50; seed++) {
            Game game = new Game(new Random(seed));
            Map<Role, Integer> counts = new EnumMap<>(Role.class);
            for (int n = 1; n <= Game.PLAYER_COUNT; n++) {
                Role role = game.player(n).role();
                Integer prev = counts.get(role);
                counts.put(role, prev == null ? 1 : prev + 1);
            }
            assertEquals(Integer.valueOf(1), counts.get(Role.DON));
            assertEquals(Integer.valueOf(2), counts.get(Role.MAFIA));
            assertEquals(Integer.valueOf(1), counts.get(Role.SHERIFF));
            assertEquals(Integer.valueOf(6), counts.get(Role.CIVILIAN));
        }
    }

    @Test
    public void speechOrderRotatesAndSkipsEliminated() {
        Game game = fixedGame();
        game.startNewDay();
        assertEquals(Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9, 10), game.speechOrder());

        game.eliminate(2);
        game.startNewDay();
        // Игрок 2 выбыл — второй день открывает игрок 3.
        assertEquals(Arrays.asList(3, 4, 5, 6, 7, 8, 9, 10, 1), game.speechOrder());

        game.eliminate(4);
        game.startNewDay();
        assertEquals(Arrays.asList(5, 6, 7, 8, 9, 10, 1, 3), game.speechOrder());
    }

    @Test
    public void dayStarterWrapsAroundTable() {
        Game game = fixedGame();
        for (int i = 0; i < 10; i++) {
            game.startNewDay();
        }
        assertEquals(Integer.valueOf(10), game.speechOrder().get(0));
        game.startNewDay();
        assertEquals(Integer.valueOf(1), game.speechOrder().get(0));
    }

    @Test
    public void nightOrderIsAlivePlayersBySeat() {
        Game game = fixedGame();
        game.eliminate(1);
        game.eliminate(6);
        assertEquals(Arrays.asList(2, 3, 4, 5, 7, 8, 9, 10), game.nightOrder());
    }

    @Test
    public void unanimousShotKills() {
        Game game = fixedGame();
        assertEquals(Integer.valueOf(3), game.resolveShots(shots(2, 3, 5, 3, 8, 3)));
    }

    @Test
    public void splitShotMisses() {
        Game game = fixedGame();
        assertNull(game.resolveShots(shots(2, 3, 5, 3, 8, 4)));
    }

    @Test
    public void eliminatedShooterDoesNotCount() {
        Game game = fixedGame();
        game.eliminate(5);
        assertEquals(Integer.valueOf(7), game.resolveShots(shots(2, 7, 8, 7)));
    }

    @Test
    public void winConditions() {
        Game game = fixedGame();
        assertEquals(Game.Winner.NONE, game.winner());

        List<Integer> reds = Arrays.asList(1, 3, 4, 6);
        for (int n : reds) {
            game.eliminate(n);
        }
        // 3 чёрных против 3 красных — паритет, мафия побеждает.
        assertEquals(Game.Winner.MAFIA, game.winner());

        Game other = fixedGame();
        other.eliminate(2);
        other.eliminate(5);
        other.eliminate(8);
        assertEquals(Game.Winner.CIVILIANS, other.winner());
    }

    private static Map<Integer, Integer> shots(int... pairs) {
        Map<Integer, Integer> map = new HashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put(pairs[i], pairs[i + 1]);
        }
        return map;
    }
}
