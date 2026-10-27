package iovi.lobby1.flow.phase;

import static iovi.lobby1.flow.phase.Phrases.duration;
import static iovi.lobby1.flow.phase.Phrases.t;

import java.util.Arrays;
import java.util.List;

import iovi.lobby1.flow.GameUi;

/**
 * Мануальный режим: игру дальше ведёт человек, телефон только помогает — лог, фолы, ночная музыка
 * и таймеры. Последняя фаза: вернуться к игре нельзя.
 */
public final class ManualPhase extends Phase {
    /** Играет ночная музыка. */
    private boolean night;

    public ManualPhase(Host host) {
        super(host);
    }

    /** В логе — «Итог» с переходом и затем раздел мануального режима. */
    public void run() {
        music.stop();
        game.log().section(t("log.result"));
        game.log().add(t("manual.result"));
        summaries.writeTo(game.log());
        game.log().section(t("manual.title"));
        voice.say(t("voice.manual"));
        ui.setPhase(t("manual.title"));
        menu();
    }

    private void menu() {
        screens.newScreen();
        List<GameUi.Button> buttons = ui.showMenu(t("manual.title"), t("manual.text"), Arrays.asList(
                t("manual.log"), t("foul.button"), t(night ? "manual.nightOff" : "manual.night"),
                t("manual.minute"), t("manual.half")));
        buttons.get(0).setOnClick(() -> {
            screens.newScreen();
            ui.showLog(t("manual.log"), game.log().toString(), t("foul.back"), this::menu);
        });
        fouls.offer(buttons.get(1), t("foul.at.manual"), () -> { });
        buttons.get(2).setOnClick(() -> {
            // Ночь — только музыка, без объявлений; повторное нажатие её останавливает.
            night = !night;
            if (night) {
                music.start();
            } else {
                music.stop();
            }
            menu();
        });
        buttons.get(3).setOnClick(() -> timer(DayPhase.SPEECH_MS));
        buttons.get(4).setOnClick(() -> timer(DayPhase.SHORT_SPEECH_MS));
    }

    /** Обратный отсчёт с «10 секунд» до конца; по окончании — снова меню. */
    private void timer(long ms) {
        screens.timer(t("manual.timer"), duration(ms), ms, true, null, this::menu);
    }
}