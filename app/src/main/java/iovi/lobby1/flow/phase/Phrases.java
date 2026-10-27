package iovi.lobby1.flow.phase;

import iovi.lobby1.text.Texts;

/** Тексты из texts.properties и длительности словами — общие для всех фаз. */
public final class Phrases {
    private Phrases() {
    }

    public static String t(String key, Object... args) {
        return Texts.get(key, args);
    }

    /** «8 секунд», «40 секунд»: длительность в секундах для экрана и голоса. */
    public static String seconds(long ms) {
        return Texts.plural("seconds", (int) (ms / 1000));
    }

    /** Длительность фазы в именительном падеже: «минута» или «40 секунд». */
    public static String duration(long ms) {
        return ms == 60_000 ? Texts.get("duration.minute") : seconds(ms);
    }
}