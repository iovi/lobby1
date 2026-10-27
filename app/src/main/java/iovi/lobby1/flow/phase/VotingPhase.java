package iovi.lobby1.flow.phase;

import static iovi.lobby1.flow.phase.Phrases.duration;
import static iovi.lobby1.flow.phase.Phrases.t;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import iovi.lobby1.flow.GameUi;
import iovi.lobby1.game.Game;
import iovi.lobby1.game.Voting;
import iovi.lobby1.text.Texts;

/**
 * Голосование по выставленным кандидатурам: ничья → оправдательные речи → переголосование →
 * «поднять всех». Результат — заголосованные (пусто — никто не уходит, наступает ночь).
 */
public final class VotingPhase extends Phase {
    private static final long TIE_SPEECH_MS = 30_000;
    /** Время на поднятие рук между «кто за X?» и «Спасибо». */
    private static final long VOTE_WINDOW_MS = 500;

    private Consumer<List<Integer>> done;

    public VotingPhase(Host host) {
        super(host);
    }

    public void run(List<Integer> nominations, Consumer<List<Integer>> done) {
        this.done = done;
        if (fouls.removedToday()) {
            cancel();
            return;
        }
        Voting voting = new Voting(nominations, game.aliveCount(), game.dayNumber() == 1);
        String list = Game.joinNumbers(nominations);
        switch (voting.phase()) {
            case NO_VOTE: {
                String text = nominations.isEmpty() ? t("vote.none") : t("vote.singleFirstDay");
                game.log().add(t("log.noVote"));
                voice.say(text);
                offerResultFouls(screens.message(t("vote.title"), text, t("button.night"), this::nobodyLeaves), null);
                break;
            }
            case FINISHED: {
                int x = voting.eliminated().get(0);
                String text = t("vote.single", x);
                game.log().add(t("log.single", x));
                voice.say(text);
                showVotedOut(t("vote.title"), text, voting.eliminated());
                break;
            }
            default: {
                // Голосование начинается само, как только прозвучит объявление (и закроют экран фолов).
                GameUi.MessageView view = screens.message(t("vote.title"), t("vote.intro", list, voting.voters()), null, null);
                fouls.offer(view.showExtra(t("foul.button")), t("foul.at.votingIntro"), this::cancel);
                int screen = screens.id();
                voice.say(t("voice.voting", list), () -> {
                    if (screen == screens.id()) {
                        fouls.whenClosed(() -> voteRound(voting, new int[voting.candidates().size()], 0));
                    }
                });
                break;
            }
        }
    }

    private void nobodyLeaves() {
        done.accept(Collections.emptyList());
    }

    /** Игрок удалён по фолам — голосования в этот день нет, наступает ночь. */
    private void cancel() {
        String text = t("vote.cancelled");
        game.log().add(t("log.voteCancelled"));
        voice.say(text);
        screens.message(t("vote.title"), text, t("button.night"), this::nobodyLeaves);
    }

    /**
     * «Фол» на экране итогов голосования. Если кого-то удалили, а голосование ещё не закончено
     * (ничья, впереди оправдательные речи), голосование отменяется; afterRemoval == null — итог остаётся.
     */
    private void offerResultFouls(GameUi.MessageView view, Runnable afterRemoval) {
        fouls.offer(view.showExtra(t("foul.button")), t("foul.at.votingResult"),
                afterRemoval == null ? () -> { } : afterRemoval);
    }

    private void voteRound(Voting voting, int[] votes, int i) {
        List<Integer> candidates = voting.candidates();
        int used = 0;
        for (int k = 0; k < i; k++) {
            used += votes[k];
        }
        int remaining = voting.voters() - used;
        if (remaining == 0) {
            // Все голоса уже отданы — оставшиеся кандидатуры не называем, у них по нулю.
            finishVoteRound(voting, votes);
            return;
        }
        int x = candidates.get(i);
        String title = voting.isRevote() ? t("vote.revoteTitle") : t("vote.title");
        String question = t("vote.question", x);
        if (i == candidates.size() - 1) {
            // Не проголосовавшие отдают голос последней кандидатуре — её тоже называем, но голоса не вводим.
            screens.message(title, t("vote.lastCandidate", question, remaining), null, null);
            announceCandidate(x, () -> {
                votes[i] = remaining;
                finishVoteRound(voting, votes);
            });
            return;
        }
        GameUi.Button action = screens.voteInput(title, question, remaining, value -> {
            votes[i] = value;
            voteRound(voting, votes, i + 1);
        });
        // Число голосов вводится после «Спасибо» — к этому моменту голосование за кандидатуру закончено.
        action.setEnabled(false);
        announceCandidate(x, () -> action.setEnabled(true));
    }

    /** «X, кто за X?», через {@link #VOTE_WINDOW_MS} «Спасибо.»; thanked вызывается после «Спасибо». */
    private void announceCandidate(int x, Runnable thanked) {
        announceVote(t("voice.candidate", x), thanked);
    }

    /** Вопрос голосования, через {@link #VOTE_WINDOW_MS} «Спасибо.»; thanked вызывается после «Спасибо». */
    private void announceVote(String question, Runnable thanked) {
        voice.say(question,
                () -> scheduler.post(() -> voice.say(t("voice.thanks"), thanked), VOTE_WINDOW_MS));
    }

    private void finishVoteRound(Voting voting, int[] votes) {
        List<Integer> candidates = new ArrayList<>(voting.candidates());
        StringBuilder summary = new StringBuilder();
        StringBuilder tally = new StringBuilder();
        for (int k = 0; k < candidates.size(); k++) {
            if (k > 0) {
                summary.append('\n');
                tally.append(", ");
            }
            summary.append(t("vote.summaryLine", candidates.get(k), votesText(votes[k])));
            tally.append(t("log.voteItem", candidates.get(k), votes[k]));
        }
        game.log().add(t(voting.isRevote() ? "log.revote" : "log.vote", tally));

        voting.submitVotes(votes);
        switch (voting.phase()) {
            case FINISHED: {
                int x = voting.eliminated().get(0);
                String text = t("vote.eliminated", x);
                game.log().add(t("log.eliminated", x));
                voice.say(text);
                showVotedOut(t("vote.resultTitle"), t("vote.result", summary, text), voting.eliminated());
                break;
            }
            case TIE_SPEECHES: {
                String tied = Game.joinNumbers(voting.candidates());
                String text = t("vote.tie", tied, duration(TIE_SPEECH_MS));
                game.log().add(t("log.tie", tied));
                voice.say(text);
                offerResultFouls(screens.message(t("vote.resultTitle"), t("vote.result", summary, text),
                        t("vote.tieButton"), () -> tieSpeeches(voting, 0)), this::cancel);
                break;
            }
            case LIFT_ALL:
                // Снова поровну — без объявления сразу вопрос «поднять всех».
                liftAllVote(voting);
                break;
            default:
                throw new IllegalStateException("Неожиданная фаза голосования: " + voting.phase());
        }
    }

    private void tieSpeeches(Voting voting, int i) {
        List<Integer> candidates = voting.candidates();
        if (i >= candidates.size()) {
            voting.tieSpeechesDone();
            voice.say(t("voice.revote", Game.joinNumbers(candidates)));
            voteRound(voting, new int[candidates.size()], 0);
            return;
        }
        int x = candidates.get(i);
        voice.say(t("voice.tieSpeech", x, duration(TIE_SPEECH_MS)));
        screens.timer(t("tie.title"), t("player", x), TIE_SPEECH_MS, true, t("voice.thanks"),
                () -> tieSpeeches(voting, i + 1));
        fouls.onPause(t("foul.at.tieSpeech", x), this::cancel);
    }

    private void liftAllVote(Voting voting) {
        String list = Game.joinNumbers(voting.candidates());
        String question = t("vote.liftAllQuestion", list);
        GameUi.Button action = screens.voteInput(t("vote.liftAllTitle"), question, voting.voters(), value -> {
            boolean lifted = voting.submitLiftAll(value);
            String tally = t("vote.liftAllTally", votesText(value), voting.voters());
            game.log().add(t(lifted ? "log.liftAll.lifted" : "log.liftAll.stayed", list, value, voting.voters()));
            if (lifted) {
                String text = t("vote.lifted", list);
                voice.say(text);
                showVotedOut(t("vote.resultTitle"), t("vote.result", tally, text), voting.eliminated());
            } else {
                String text = t("vote.notLifted");
                voice.say(text);
                offerResultFouls(screens.message(t("vote.resultTitle"), t("vote.result", tally, text),
                        t("button.night"), this::nobodyLeaves), null);
            }
        });
        action.setEnabled(false);
        announceVote(question, () -> action.setEnabled(true));
    }

    /**
     * Итог голосования с выбыванием и кнопка «Прощальная минута». Единственному заголосованному
     * в нулевом круге телефон передаётся сразу — экран лучшего хода без промежуточной кнопки.
     */
    private void showVotedOut(String title, String text, List<Integer> players) {
        if (FarewellPhase.lastWords(game, players)) {
            done.accept(players);
        } else {
            offerResultFouls(screens.message(title, text, t("button.farewell"), () -> done.accept(players)), null);
        }
    }

    private static String votesText(int n) {
        return Texts.plural("votes", n);
    }
}