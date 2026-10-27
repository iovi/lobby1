package iovi.lobby1.flow.phase;

import static iovi.lobby1.flow.phase.Phrases.duration;
import static iovi.lobby1.flow.phase.Phrases.t;

import iovi.lobby1.flow.GameUi;

/** Нулевая ночь: минута договорки мафии, затем свободная посадка. */
public final class ZeroNightPhase extends Phase {
    private static final long AGREEMENT_MS = 60_000;
    private static final long FREE_SEATING_MS = 40_000;

    public ZeroNightPhase(Host host) {
        super(host);
    }

    /** done — когда закончилась свободная посадка. */
    public void run(Runnable done) {
        ui.setPhase(t("phase.night", 0));
        game.log().section(t("log.night", 0));
        game.log().add(t("log.agreement"));
        screens.newScreen();
        int screen = screens.id();
        GameUi.TimerView view = ui.showTimer(t("agreement.title"), t("agreement.subtitle"), screens.controls());
        view.setTime(AGREEMENT_MS);
        view.setControlsEnabled(false);
        // Минута договорки идёт с момента, когда телефон закончит объявление.
        voice.say(t("voice.agreement", duration(AGREEMENT_MS)), () -> {
            if (screen != screens.id()) {
                return;
            }
            view.setControlsEnabled(true);
            screens.runTimer(view, AGREEMENT_MS, true, () -> {
                voice.say(t("voice.agreementEnd"));
                freeSeating(done);
            });
        });
    }

    private void freeSeating(Runnable done) {
        voice.say(t("voice.seating", duration(FREE_SEATING_MS)));
        screens.timer(t("seating.title"), t("seating.subtitle", duration(FREE_SEATING_MS)), FREE_SEATING_MS,
                false, null, done);
    }
}