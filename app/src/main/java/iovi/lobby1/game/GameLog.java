package iovi.lobby1.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Протокол партии: заголовки фаз и события внутри них. */
public final class GameLog {
    private final List<String> lines = new ArrayList<>();

    public void section(String title) {
        if (!lines.isEmpty()) {
            lines.add("");
        }
        lines.add("■ " + title);
    }

    public void add(String event) {
        lines.add("  " + event);
    }

    public List<String> lines() {
        return Collections.unmodifiableList(lines);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(line);
        }
        return sb.toString();
    }
}
