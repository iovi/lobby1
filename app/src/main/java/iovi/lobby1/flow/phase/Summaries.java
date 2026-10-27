package iovi.lobby1.flow.phase;

import java.util.ArrayList;
import java.util.List;

import iovi.lobby1.game.GameLog;

/** Строки лучших ходов и протоколов для итога партии, в порядке выбывания. */
public final class Summaries {
    private final List<String> bestMoves = new ArrayList<>();
    private final List<String> protocols = new ArrayList<>();

    void addBestMove(String summary) {
        bestMoves.add(summary);
    }

    void addProtocol(String summary) {
        protocols.add(summary);
    }

    /** В раздел «Итог»: сначала лучшие ходы, затем протоколы. */
    public void writeTo(GameLog log) {
        for (String summary : bestMoves) {
            log.add(summary);
        }
        for (String summary : protocols) {
            log.add(summary);
        }
    }
}