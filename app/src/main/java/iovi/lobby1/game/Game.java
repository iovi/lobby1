package iovi.lobby1.game;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Состояние партии спортивной мафии на 10 игроков: роли, кто ещё за столом,
 * номер дня и очерёдность речей и ночных ходов.
 */
public final class Game {
    public static final int PLAYER_COUNT = 10;

    public enum Winner { NONE, CIVILIANS, MAFIA }

    private final List<Player> players = new ArrayList<>();
    private final GameLog log = new GameLog();
    private int dayNumber = 0;
    /** Номер игрока, открывавшего речи текущего дня; 0 — дней ещё не было. */
    private int dayStarter = 0;

    /** Новая партия со случайной раздачей: дон, 2 мафии, шериф, 6 мирных. */
    public Game(Random random) {
        this(shuffledRoles(random));
    }

    /** Партия с заданной раздачей; роль с индексом i получает игрок i + 1. */
    public Game(List<Role> roles) {
        if (roles.size() != PLAYER_COUNT) {
            throw new IllegalArgumentException("Нужно ровно " + PLAYER_COUNT + " ролей");
        }
        for (int i = 0; i < roles.size(); i++) {
            players.add(new Player(i + 1, roles.get(i)));
        }
    }

    private static List<Role> shuffledRoles(Random random) {
        List<Role> roles = new ArrayList<>(Arrays.asList(
                Role.DON, Role.MAFIA, Role.MAFIA, Role.SHERIFF,
                Role.CIVILIAN, Role.CIVILIAN, Role.CIVILIAN,
                Role.CIVILIAN, Role.CIVILIAN, Role.CIVILIAN));
        Collections.shuffle(roles, random);
        return roles;
    }

    public GameLog log() {
        return log;
    }

    public int dayNumber() {
        return dayNumber;
    }

    public Player player(int number) {
        return players.get(number - 1);
    }

    public boolean isAlive(int number) {
        return player(number).isAlive();
    }

    public List<Integer> alive() {
        List<Integer> result = new ArrayList<>();
        for (Player p : players) {
            if (p.isAlive()) {
                result.add(p.number());
            }
        }
        return result;
    }

    public int aliveCount() {
        return alive().size();
    }

    public void eliminate(int number) {
        player(number).leave();
    }

    /** Мирные побеждают, когда мафии не осталось; мафия — когда её не меньше, чем красных. */
    public Winner winner() {
        int black = 0;
        int red = 0;
        for (Player p : players) {
            if (!p.isAlive()) {
                continue;
            }
            if (p.role().isBlack()) {
                black++;
            } else {
                red++;
            }
        }
        if (black == 0) {
            return Winner.CIVILIANS;
        }
        if (black >= red) {
            return Winner.MAFIA;
        }
        return Winner.NONE;
    }

    /**
     * Начинает следующий день. Первый день открывает игрок 1, каждый следующий —
     * ближайший живой игрок после того, кто открывал предыдущий день.
     */
    public void startNewDay() {
        dayNumber++;
        dayStarter = nextAliveAfter(dayStarter);
    }

    /** Порядок речей текущего дня: живые игроки по кругу начиная с открывающего. */
    public List<Integer> speechOrder() {
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < PLAYER_COUNT; i++) {
            int number = (dayStarter - 1 + i) % PLAYER_COUNT + 1;
            if (isAlive(number)) {
                order.add(number);
            }
        }
        return order;
    }

    /** Порядок передачи телефона ночью: живые игроки по номерам. */
    public List<Integer> nightOrder() {
        return alive();
    }

    /** Живые игроки, которые стреляют ночью: мафия и дон. */
    public List<Integer> shooters() {
        List<Integer> result = new ArrayList<>();
        for (Player p : players) {
            if (p.isAlive() && p.role().isBlack()) {
                result.add(p.number());
            }
        }
        return result;
    }

    /**
     * Итог ночного отстрела: игрок убит, только если все живые стрелявшие
     * указали один и тот же номер живого игрока. Иначе промах — null.
     */
    public Integer resolveShots(Map<Integer, Integer> shots) {
        Integer target = null;
        for (int shooter : shooters()) {
            Integer shot = shots.get(shooter);
            if (shot == null || (target != null && !target.equals(shot))) {
                return null;
            }
            target = shot;
        }
        if (target == null || !isAlive(target)) {
            return null;
        }
        return target;
    }

    private int nextAliveAfter(int number) {
        for (int i = 1; i <= PLAYER_COUNT; i++) {
            int candidate = (number - 1 + i + PLAYER_COUNT) % PLAYER_COUNT + 1;
            if (isAlive(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("За столом никого нет");
    }

    /** «3, 7, 10» — для объявлений и протокола. */
    public static String joinNumbers(List<Integer> numbers) {
        StringBuilder sb = new StringBuilder();
        for (int n : numbers) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(n);
        }
        return sb.toString();
    }
}
