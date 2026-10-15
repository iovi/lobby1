package iovi.lobby1.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Протокол выбывшего игрока: кого из других игроков он считает красным, чёрным или шерифом.
 * Отметки не могут противоречить раздаче: шериф один, чёрных не больше трёх,
 * красных вместе с шерифом не больше семи.
 */
public final class Protocol {
    public enum Mark { RED, BLACK, SHERIFF }

    public static final int MAX_SHERIFF = 1;
    public static final int MAX_BLACK = 3;
    /** Красные вместе с шерифом. */
    public static final int MAX_RED = 7;

    private final int author;
    /** Номер игрока → отметка, по возрастанию номеров. */
    private final Map<Integer, Mark> marks = new TreeMap<>();

    public Protocol(int author) {
        this.author = author;
    }

    public int author() {
        return author;
    }

    public Mark mark(int player) {
        return marks.get(player);
    }

    public boolean isEmpty() {
        return marks.isEmpty();
    }

    /** Отметки, которые можно поставить игроку, не нарушив ограничений (текущая отметка всегда допустима). */
    public Set<Mark> allowed(int player) {
        Set<Mark> result = EnumSet.noneOf(Mark.class);
        if (player == author) {
            return result;
        }
        Mark current = marks.get(player);
        for (Mark m : Mark.values()) {
            if (m == current || fits(player, m)) {
                result.add(m);
            }
        }
        return result;
    }

    /** Ставит отметку; повторная та же отметка снимает её. false — отметка противоречит остальным. */
    public boolean toggle(int player, Mark mark) {
        if (marks.get(player) == mark) {
            marks.remove(player);
            return true;
        }
        if (!allowed(player).contains(mark)) {
            return false;
        }
        marks.put(player, mark);
        return true;
    }

    /** Отмеченные номера по возрастанию. */
    public List<Integer> players(Mark mark) {
        List<Integer> result = new ArrayList<>();
        for (Map.Entry<Integer, Mark> e : marks.entrySet()) {
            if (e.getValue() == mark) {
                result.add(e.getKey());
            }
        }
        return Collections.unmodifiableList(result);
    }

    public int size() {
        return marks.size();
    }

    /** Сколько отметок совпало с настоящими ролями; шериф, отмеченный красным, — тоже попадание. */
    public int correct(Game game) {
        int correct = 0;
        for (Map.Entry<Integer, Mark> e : marks.entrySet()) {
            Role role = game.player(e.getKey()).role();
            boolean hit;
            switch (e.getValue()) {
                case BLACK:
                    hit = role.isBlack();
                    break;
                case SHERIFF:
                    hit = role == Role.SHERIFF;
                    break;
                case RED:
                default:
                    hit = !role.isBlack();
                    break;
            }
            if (hit) {
                correct++;
            }
        }
        return correct;
    }

    /** Поместится ли отметка mark игроку player вместо его текущей. */
    private boolean fits(int player, Mark mark) {
        int sheriff = 0;
        int black = 0;
        int red = 0;
        for (Map.Entry<Integer, Mark> e : marks.entrySet()) {
            if (e.getKey() == player) {
                continue;
            }
            switch (e.getValue()) {
                case SHERIFF:
                    sheriff++;
                    red++;
                    break;
                case BLACK:
                    black++;
                    break;
                case RED:
                default:
                    red++;
                    break;
            }
        }
        switch (mark) {
            case SHERIFF:
                return sheriff < MAX_SHERIFF && red < MAX_RED;
            case BLACK:
                return black < MAX_BLACK;
            case RED:
            default:
                return red < MAX_RED;
        }
    }
}