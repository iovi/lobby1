package iovi.lobby1.flow.phase;

import static iovi.lobby1.flow.phase.Phrases.t;

import java.util.Map;
import java.util.function.Consumer;

/** Утро: итог ночных выстрелов. Результат — убитый (null — промах). */
public final class MorningPhase extends Phase {
    public MorningPhase(Host host) {
        super(host);
    }

    /**
     * Промах — done(null) по кнопке «День». Убитому done(номер) по кнопке «Прощальная минута»;
     * убитому в первую ночь — сразу: перед прощальной речью он делает лучший ход, телефон
     * передаётся без промежуточной кнопки.
     */
    public void run(Map<Integer, Integer> shots, Consumer<Integer> done) {
        music.stop();
        ui.setPhase(t("phase.morning"));
        Integer killed = game.resolveShots(shots);
        if (killed == null) {
            game.log().add(t("log.miss"));
            String text = t("morning.miss");
            voice.say(t("voice.morning", text));
            screens.message(t("morning.title"), text, t("button.day"), () -> done.accept(null));
        } else {
            game.log().add(t("log.killed", killed));
            String text = t("morning.killed", killed);
            voice.say(t("voice.morning", text));
            if (game.dayNumber() == 1) {
                done.accept(killed);
            } else {
                screens.message(t("morning.title"), text, t("button.farewell"), () -> done.accept(killed));
            }
        }
    }
}