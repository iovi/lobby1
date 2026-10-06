package iovi.lobby1.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Дневное голосование по правилам ФИИМ.
 * <ul>
 *   <li>Нет кандидатур, или в первый день выставлен один игрок — голосования нет.</li>
 *   <li>Со второго дня единственный выставленный покидает стол без голосования.</li>
 *   <li>Голосуют все живые игроки; кто не проголосовал, отдаёт голос последней кандидатуре.</li>
 *   <li>Ничья («автокатастрофа»): оправдательные речи по 30 секунд и переголосование
 *       между делящими первое место. Если снова ничья между теми же игроками — голосование
 *       «поднять всех»: при голосах больше половины стола уходят все, иначе никто.</li>
 * </ul>
 */
public final class Voting {
    public enum Phase { NO_VOTE, VOTE, TIE_SPEECHES, LIFT_ALL, FINISHED }

    private final int voters;
    private List<Integer> candidates;
    private Phase phase;
    private boolean revote = false;
    private List<Integer> eliminated = Collections.emptyList();

    /**
     * @param nominees выставленные игроки в порядке выставления
     * @param voters   число живых игроков за столом
     * @param firstDay идёт ли первый игровой день
     */
    public Voting(List<Integer> nominees, int voters, boolean firstDay) {
        this.voters = voters;
        this.candidates = new ArrayList<>(nominees);
        if (nominees.isEmpty() || (firstDay && nominees.size() == 1)) {
            phase = Phase.NO_VOTE;
        } else if (nominees.size() == 1) {
            eliminated = new ArrayList<>(nominees);
            phase = Phase.FINISHED;
        } else {
            phase = Phase.VOTE;
        }
    }

    public Phase phase() {
        return phase;
    }

    public int voters() {
        return voters;
    }

    /** Кандидатуры текущего круга голосования (или участники ничьей). */
    public List<Integer> candidates() {
        return Collections.unmodifiableList(candidates);
    }

    public boolean isRevote() {
        return revote;
    }

    /** Игроки, покидающие стол; заполнено в фазе {@link Phase#FINISHED}. */
    public List<Integer> eliminated() {
        return Collections.unmodifiableList(eliminated);
    }

    /** Голоса за каждую кандидатуру в порядке {@link #candidates()}; сумма — все голосующие. */
    public void submitVotes(int[] votes) {
        requirePhase(Phase.VOTE);
        if (votes.length != candidates.size()) {
            throw new IllegalArgumentException("Голоса нужны за каждую кандидатуру");
        }
        int total = 0;
        int max = -1;
        for (int v : votes) {
            if (v < 0) {
                throw new IllegalArgumentException("Отрицательное число голосов");
            }
            total += v;
            max = Math.max(max, v);
        }
        if (total != voters) {
            throw new IllegalArgumentException("Голосов " + total + ", а голосующих " + voters);
        }
        List<Integer> leaders = new ArrayList<>();
        for (int i = 0; i < votes.length; i++) {
            if (votes[i] == max) {
                leaders.add(candidates.get(i));
            }
        }
        if (leaders.size() == 1) {
            eliminated = leaders;
            phase = Phase.FINISHED;
        } else if (revote && leaders.equals(candidates)) {
            phase = Phase.LIFT_ALL;
        } else {
            candidates = leaders;
            revote = true;
            phase = Phase.TIE_SPEECHES;
        }
    }

    public void tieSpeechesDone() {
        requirePhase(Phase.TIE_SPEECHES);
        phase = Phase.VOTE;
    }

    /** @return true, если стол поднят целиком (голосов больше половины). */
    public boolean submitLiftAll(int votes) {
        requirePhase(Phase.LIFT_ALL);
        if (votes < 0 || votes > voters) {
            throw new IllegalArgumentException("Неверное число голосов: " + votes);
        }
        boolean lifted = votes * 2 > voters;
        eliminated = lifted ? new ArrayList<>(candidates) : Collections.<Integer>emptyList();
        phase = Phase.FINISHED;
        return lifted;
    }

    private void requirePhase(Phase expected) {
        if (phase != expected) {
            throw new IllegalStateException("Ожидалась фаза " + expected + ", сейчас " + phase);
        }
    }
}
