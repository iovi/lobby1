package iovi.lobby1.flow.phase;

import static iovi.lobby1.flow.phase.Phrases.seconds;
import static iovi.lobby1.flow.phase.Phrases.t;

import iovi.lobby1.game.Game;
import iovi.lobby1.game.Role;

/**
 * Раздача карт: телефон по кругу, каждый игрок смотрит свою роль. Ночная музыка начинается здесь.
 * При автораздаче карта показывается по кнопке; без неё игрок сам выбирает карту из колоды рубашкой вверх.
 */
public final class DealingPhase extends Phase {
    private static final long CARD_SHOW_MS = 3_000;
    /** Показ карты, выбранной из колоды, на весь экран — короче: игрок уже видел её при перевороте. */
    private static final long DECK_CARD_SHOW_MS = 2_000;
    /** Карта из колоды сначала переворачивается и вырастает на весь экран — это время добавляется к показу. */
    private static final long CARD_REVEAL_MS = 1_000;
    /** Пауза между исчезновением карты и вызовом следующего игрока. */
    private static final long CARD_PASS_PAUSE_MS = 3_000;

    /**
     * Места колоды, с которых карту уже взяли. Роли розданы случайно заранее, а рубашки одинаковы,
     * поэтому на выбранной карте — роль игрока: для каждого это та же случайная карта, что и из настоящей колоды.
     */
    private final boolean[] taken = new boolean[Game.PLAYER_COUNT];
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
        if (host.autoDeal) {
            screens.handoff(t("dealing.title"), n,
                    t("dealing.hint", seconds(CARD_SHOW_MS)),
                    t("dealing.button"), () -> showCard(n, false));
            return;
        }
        screens.newScreen();
        int screen = screens.id();
        ui.showDeck(t("dealing.deckTitle", n), t("dealing.deckHint"), taken.clone(),
                slot -> {
                    // Переворот закончился, когда экран уже сменился (игру закрыли) — ничего не делаем.
                    if (screens.id() != screen || taken[slot]) {
                        return;
                    }
                    taken[slot] = true;
                    showCard(n, true);
                });
    }

    private void showCard(int n, boolean flipIn) {
        screens.newScreen();
        Role role = game.player(n).role();
        long showMs = flipIn ? DECK_CARD_SHOW_MS + CARD_REVEAL_MS : CARD_SHOW_MS;
        ui.showCard(t("player", n), role.title(), role, showMs, flipIn);
        scheduler.post(() -> {
            if (n < Game.PLAYER_COUNT) {
                passAfterCard(n + 1);
            } else {
                // Договорка начинается сама: последний игрок кладёт телефон, кнопок не нужно.
                screens.message(t("dealing.done.title"), t("dealing.done.text"), null, null);
                scheduler.post(done, CARD_PASS_PAUSE_MS);
            }
        }, showMs);
    }

    /** Пауза после исчезновения карты: телефон передают дальше, затем вызывается следующий игрок. */
    private void passAfterCard(int next) {
        screens.message(t("dealing.pass"), null, null, null);
        scheduler.post(() -> dealCard(next), CARD_PASS_PAUSE_MS);
    }
}