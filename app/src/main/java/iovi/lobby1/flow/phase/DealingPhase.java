package iovi.lobby1.flow.phase;

import static iovi.lobby1.flow.phase.Phrases.seconds;
import static iovi.lobby1.flow.phase.Phrases.t;

import iovi.lobby1.game.Game;
import iovi.lobby1.game.Role;

/** Раздача карт: телефон по кругу, каждый игрок смотрит свою роль. Ночная музыка начинается здесь. */
public final class DealingPhase extends Phase {
    private static final long CARD_SHOW_MS = 3_000;
    /** Пауза между исчезновением карты и вызовом следующего игрока. */
    private static final long CARD_PASS_PAUSE_MS = 3_000;

    private Runnable done;

    public DealingPhase(Host host) {
        super(host);
    }

    /** done — когда последний игрок посмотрел карту и положил телефон. */
    public void run(Runnable done) {
        this.done = done;
        ui.setPhase(t("phase.dealing"));
        game.log().section(t("log.dealing"));
        for (int n = 1; n <= Game.PLAYER_COUNT; n++) {
            game.log().add(t("log.dealt", n, game.player(n).role().title()));
        }
        music.start();
        voice.say(t("voice.dealing"));
        dealCard(1);
    }

    private void dealCard(int n) {
        screens.callPlayer(n);
        screens.handoff(t("dealing.title"), n,
                t("dealing.hint", seconds(CARD_SHOW_MS)),
                t("dealing.button"), () -> showCard(n));
    }

    private void showCard(int n) {
        screens.newScreen();
        Role role = game.player(n).role();
        ui.showCard(t("player", n), role.title(), role, CARD_SHOW_MS);
        scheduler.post(() -> {
            if (n < Game.PLAYER_COUNT) {
                passAfterCard(n + 1);
            } else {
                // Договорка начинается сама: последний игрок кладёт телефон, кнопок не нужно.
                screens.message(t("dealing.done.title"), t("dealing.done.text"), null, null);
                scheduler.post(done, CARD_PASS_PAUSE_MS);
            }
        }, CARD_SHOW_MS);
    }

    /** Пауза после исчезновения карты: телефон передают дальше, затем вызывается следующий игрок. */
    private void passAfterCard(int next) {
        screens.message(t("dealing.pass"), null, null, null);
        scheduler.post(() -> dealCard(next), CARD_PASS_PAUSE_MS);
    }
}