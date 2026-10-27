package iovi.lobby1.flow.phase;

import static iovi.lobby1.flow.phase.Phrases.t;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import iovi.lobby1.flow.GameUi;
import iovi.lobby1.game.Game;
import iovi.lobby1.game.Protocol;

/** После прощальной минуты выбывший отмечает, кого из игроков считает красным, чёрным или шерифом. */
public final class ProtocolPhase extends Phase {
    /** Пауза между игроками при оглашении протокола — чтобы стол успевал записать. */
    private static final long PROTOCOL_PAUSE_MS = 700;

    public ProtocolPhase(Host host) {
        super(host);
    }

    /** done — «Дальше» после протокола или переход в мануальный режим. */
    public void run(int n, Consumer<FarewellPhase.Outcome> done) {
        voice.say(t("voice.protocol", n));
        GameUi.MessageView view = screens.handoff(t("protocol.title"), n, t("protocol.handoffHint"),
                t("protocol.start"), () -> showProtocol(n, done));
        fouls.offer(view.showExtra(t("foul.button")), t("foul.at.protocolHandoff", n), () -> { });
    }

    private void showProtocol(int n, Consumer<FarewellPhase.Outcome> done) {
        List<Integer> players = new ArrayList<>();
        for (int k = 1; k <= Game.PLAYER_COUNT; k++) {
            if (k != n) {
                players.add(k);
            }
        }
        Map<Protocol.Mark, String> labels = new EnumMap<>(Protocol.Mark.class);
        for (Protocol.Mark mark : Protocol.Mark.values()) {
            labels.put(mark, t("protocol.mark." + mark.name()));
        }
        screens.newScreen();
        GameUi.ProtocolView view = ui.showProtocol(t("player", n), t("protocol.title"), t("protocol.hint"),
                players, labels);
        GameUi.Button action = view.action();
        Protocol protocol = new Protocol(n);
        boolean[] confirmed = {false};
        Runnable refresh = () -> {
            for (int k : players) {
                view.setRow(k, protocol.mark(k), protocol.allowed(k));
            }
            view.setSummary(protocol.isEmpty() ? t("protocol.nobody") : protocolParts(protocol, "protocol.part.", "\n"));
            action.setText(protocol.isEmpty() ? t("protocol.empty") : t("protocol.confirm"));
        };
        view.setListener((player, mark) -> {
            if (!confirmed[0] && protocol.toggle(player, mark)) {
                refresh.run();
            }
        });
        refresh.run();

        action.setOnClick(() -> {
            confirmed[0] = true;
            view.lock();
            if (protocol.isEmpty()) {
                game.log().add(t("log.protocolEmpty", n));
                voice.say(t("voice.protocolEmpty"));
            } else {
                String logParts = protocolParts(protocol, "log.protocol.part.", "; ");
                game.log().add(t("log.protocol", n, logParts));
                summaries.addProtocol(t("log.protocolSummary", n, logParts, protocol.correct(game), protocol.size()));
                announceProtocol(protocol);
            }
            action.setText(t("protocol.next"));
            action.setOnClick(() -> done.accept(FarewellPhase.Outcome.NEXT));
            // После выбывания можно перейти в мануальный режим; если победитель уже есть, игра закончится и так.
            if (game.winner() == Game.Winner.NONE) {
                screens.offerManual(view.showExtra(t("manual.button")),
                        () -> done.accept(FarewellPhase.Outcome.MANUAL));
            }
        });
    }

    /**
     * «Протокол.» (номер автора не называется), затем каждый отмеченный отдельной фразой с паузой:
     * сначала красные, затем чёрные, затем шериф. Паузы стоят в очереди голоса, поэтому озвучка
     * не обрывается при переходе к следующему экрану.
     */
    private void announceProtocol(Protocol protocol) {
        voice.say(t("voice.protocolResult"));
        for (Protocol.Mark mark : Protocol.Mark.values()) {
            for (int n : protocol.players(mark)) {
                voice.pause(PROTOCOL_PAUSE_MS);
                voice.say(t("voice.protocol.mark." + mark.name(), n));
            }
        }
    }

    /** Отметки протокола по ключам prefix + RED/BLACK/SHERIFF; пустые группы пропускаются. */
    private static String protocolParts(Protocol protocol, String prefix, String separator) {
        StringBuilder sb = new StringBuilder();
        for (Protocol.Mark mark : Protocol.Mark.values()) {
            List<Integer> marked = protocol.players(mark);
            if (marked.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(separator);
            }
            sb.append(t(prefix + mark.name(), Game.joinNumbers(marked)));
        }
        return sb.toString();
    }
}