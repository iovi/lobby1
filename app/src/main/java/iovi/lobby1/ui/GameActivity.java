package iovi.lobby1.ui;

import android.animation.ObjectAnimator;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntConsumer;

import iovi.lobby1.R;
import iovi.lobby1.audio.Announcer;
import iovi.lobby1.audio.NightMusic;
import iovi.lobby1.game.Game;
import iovi.lobby1.game.Role;
import iovi.lobby1.game.Voting;

/**
 * Ведёт партию: раздача карт → договорка → свободная посадка → дни и ночи до победы.
 * Каждая фаза показывает свой экран; переход к следующей — через продолжение (Runnable).
 */
public class GameActivity extends AppCompatActivity implements Announcer.Listener {
    private static final long CARD_SHOW_MS = 3_000;
    /** Пауза между исчезновением карты и вызовом следующего игрока. */
    private static final long CARD_PASS_PAUSE_MS = 3_000;
    private static final long AGREEMENT_MS = 60_000;
    private static final long FREE_SEATING_MS = 20_000;
    private static final long SPEECH_MS = 60_000;
    private static final long TIE_SPEECH_MS = 30_000;
    private static final long FAREWELL_MS = 60_000;
    /**
     * Минимальная длительность ночного хода. Мирный нажимает одну кнопку, дон стреляет и
     * проверяет — выравниваем время, чтобы по нему нельзя было вычислить роль.
     */
    private static final long MIN_NIGHT_TURN_MS = 8_000;
    /** Пауза после последнего ночного хода — положить телефон на стол. */
    private static final long NIGHT_END_PAUSE_MS = 5_000;
    /** Время на поднятие рук между «кто за X?» и «Спасибо». */
    private static final long VOTE_WINDOW_MS = 1_000;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final PhaseTimer timer = new PhaseTimer();
    private Game game;
    private Announcer announcer;
    private NightMusic music;
    private FrameLayout screen;
    private TextView phaseLabel;
    private boolean gameOver = false;

    /** Выставленные кандидатуры текущего дня в порядке выставления. */
    private final List<Integer> nominations = new ArrayList<>();
    /** Кто кого выставил: номер говорившего → номер выставленного. */
    private final Map<Integer, Integer> nominatedBy = new HashMap<>();
    /** Ночные выстрелы: номер стрелявшего → цель. */
    private final Map<Integer, Integer> shots = new HashMap<>();
    private long turnStartedAt;
    /** Строка лучшего хода для итога партии; null — лучшего хода не было. */
    private String bestMoveSummary;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        screen = findViewById(R.id.screen);
        phaseLabel = findViewById(R.id.phaseLabel);
        announcer = new Announcer(this, this);
        music = new NightMusic(this,
                () -> Toast.makeText(this, R.string.music_error, Toast.LENGTH_LONG).show());
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                confirmExit();
            }
        });

        game = new Game(new SecureRandom());
        startDealing();
    }

    @Override
    protected void onStart() {
        super.onStart();
        music.resume();
    }

    @Override
    protected void onStop() {
        super.onStop();
        music.pause();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        timer.cancel();
        announcer.shutdown();
        music.stop();
        super.onDestroy();
    }

    @Override
    public void onSpeakingChanged(boolean speaking) {
        music.setDucked(speaking);
    }

    @Override
    public void onVoiceUnavailable() {
        Toast.makeText(this, R.string.voice_unavailable, Toast.LENGTH_LONG).show();
    }

    private void confirmExit() {
        if (gameOver) {
            finish();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.abort_title)
                .setMessage(R.string.abort_message)
                .setPositiveButton(R.string.abort_yes, (d, w) -> finish())
                .setNegativeButton(R.string.abort_no, null)
                .show();
    }

    // ───────────────────────────── Раздача карт ─────────────────────────────

    private void startDealing() {
        setPhase("Раздача карт");
        game.log().section("Раздача карт");
        for (int n = 1; n <= Game.PLAYER_COUNT; n++) {
            game.log().add("Игрок " + n + " — " + game.player(n).role().title());
        }
        music.start();
        announcer.say("Начинается раздача карт. Передавайте телефон по кругу, начиная с первого игрока.");
        dealCard(1);
    }

    private void dealCard(int n) {
        callPlayer(n);
        showHandoff("Раздача карт", n,
                "Держите телефон так, чтобы экран видели только вы.\nКарта покажется на "
                        + CARD_SHOW_MS / 1000 + " секунды.",
                "Получить карту", () -> showCard(n));
    }

    private void showCard(int n) {
        View v = show(R.layout.screen_card);
        Role role = game.player(n).role();

        GradientDrawable card = new GradientDrawable();
        card.setCornerRadius(dp(20));
        card.setColor(ContextCompat.getColor(this, role.isBlack() ? R.color.card_black : R.color.card_red));
        card.setStroke(dp(2), ContextCompat.getColor(this, R.color.card_border));
        v.findViewById(R.id.card).setBackground(card);

        this.<TextView>find(v, R.id.playerNumber).setText("Игрок " + n);
        this.<TextView>find(v, R.id.role).setText(role.title());
        this.<TextView>find(v, R.id.description).setText(describe(role));

        ProgressBar progress = v.findViewById(R.id.progress);
        ObjectAnimator.ofInt(progress, "progress", progress.getMax(), 0)
                .setDuration(CARD_SHOW_MS)
                .start();

        handler.postDelayed(() -> {
            if (n < Game.PLAYER_COUNT) {
                passAfterCard(n + 1);
            } else {
                // Договорка начинается сама: последний игрок кладёт телефон, кнопок не нужно.
                View done = show(R.layout.screen_message);
                this.<TextView>find(done, R.id.title).setText("Карты выданы");
                this.<TextView>find(done, R.id.text).setText("Положите телефон на стол.");
                done.findViewById(R.id.action).setVisibility(View.GONE);
                handler.postDelayed(this::mafiaAgreement, CARD_PASS_PAUSE_MS);
            }
        }, CARD_SHOW_MS);
    }

    /** Пауза после исчезновения карты: телефон передают дальше, затем вызывается следующий игрок. */
    private void passAfterCard(int next) {
        View v = show(R.layout.screen_message);
        this.<TextView>find(v, R.id.title).setText("Передайте телефон следующему игроку");
        v.findViewById(R.id.text).setVisibility(View.GONE);
        v.findViewById(R.id.action).setVisibility(View.GONE);
        handler.postDelayed(() -> dealCard(next), CARD_PASS_PAUSE_MS);
    }

    private static String describe(Role role) {
        switch (role) {
            case SHERIFF:
                return "Красный игрок. Каждую ночь проверяйте одного игрока: мафия он или нет.";
            case MAFIA:
                return "Чёрный игрок. Ночью стреляйте вместе с доном и другой мафией.";
            case DON:
                return "Глава мафии. Ночью стреляйте вместе с мафией и ищите шерифа.";
            case CIVILIAN:
            default:
                return "Красный игрок. Ночью у вас нет действий — найдите мафию днём.";
        }
    }

    // ─────────────────────── Нулевая ночь: договорка и посадка ───────────────────────

    private void mafiaAgreement() {
        setPhase("Ночь 0");
        game.log().section("Ночь 0");
        game.log().add("Договорка мафии, свободная посадка");
        View v = show(R.layout.screen_timer);
        this.<TextView>find(v, R.id.title).setText("Договорка мафии");
        this.<TextView>find(v, R.id.subtitle).setText("Мафия договаривается жестами.\nОстальные игроки спят.");
        this.<TextView>find(v, R.id.time).setText(PhaseTimer.format(AGREEMENT_MS));
        View pause = v.findViewById(R.id.pause);
        View finish = v.findViewById(R.id.finish);
        pause.setEnabled(false);
        finish.setEnabled(false);
        // Минута договорки идёт с момента, когда телефон закончит объявление.
        announcer.say("Наступает ночь. Мафия просыпается. У вас минута на договорку.", () -> {
            if (v.getParent() == null) {
                return;
            }
            pause.setEnabled(true);
            finish.setEnabled(true);
            runTimer(v, AGREEMENT_MS, false, () -> {
                announcer.say("Мафия засыпает.");
                freeSeating();
            });
        });
    }

    private void freeSeating() {
        announcer.say("Свободная посадка. Двадцать секунд.");
        showTimer("Свободная посадка", "Двадцать секунд, чтобы устроиться поудобнее.",
                FREE_SEATING_MS, false, null, this::startDay);
    }

    // ───────────────────────────────── День ─────────────────────────────────

    private void startDay() {
        music.stop();
        game.startNewDay();
        int day = game.dayNumber();
        setPhase("День " + day + " · за столом " + game.aliveCount());
        game.log().section("День " + day);
        nominations.clear();
        nominatedBy.clear();

        List<Integer> order = game.speechOrder();
        String intro = day == 1 ? "Доброе утро! Начинается первый день." : "Начинается день " + day + ".";
        announcer.say(intro + " Первым говорит игрок " + order.get(0) + ".");
        speech(order, 0);
    }

    private void speech(List<Integer> order, int i) {
        if (i >= order.size()) {
            startVoting();
            return;
        }
        int n = order.get(i);
        showHandoff("Речь " + (i + 1) + " из " + order.size(), n,
                "Во время речи можно выставить одну кандидатуру.",
                "Начать речь", () -> showSpeech(order, i));
    }

    private void showSpeech(List<Integer> order, int i) {
        int n = order.get(i);
        View v = show(R.layout.screen_speech);
        this.<TextView>find(v, R.id.title).setText("Речь игрока " + n);
        TextView hint = v.findViewById(R.id.hint);
        hint.setText("Выберите номер и подтвердите выставление (отменить его нельзя):");
        TextView nomineesView = v.findViewById(R.id.nominees);
        MaterialButton confirm = v.findViewById(R.id.confirm);

        List<Integer> allowed = new ArrayList<>(game.alive());
        allowed.removeAll(nominations);
        NumberGrid grid = new NumberGrid(v.findViewById(R.id.grid), allowed);
        int[] chosen = {0};
        grid.setListener(number -> {
            chosen[0] = number;
            grid.select(number);
            confirm.setEnabled(true);
            confirm.setText("Выставить игрока " + number);
        });
        confirm.setOnClickListener(once(() -> {
            int number = chosen[0];
            nominations.add(number);
            nominatedBy.put(n, number);
            grid.lock(number);
            confirm.setEnabled(false);
            confirm.setText("Выставлен игрок " + number);
            hint.setText("Выставление принято: игрок " + number);
            nomineesView.setText(nomineesText());
            announcer.say("Номер " + number + " принят.");
        }));
        nomineesView.setText(nomineesText());

        announcer.say("Игрок " + n + ", ваша речь.");
        runTimer(v, SPEECH_MS, true, () -> {
            Integer nominee = nominatedBy.get(n);
            if (nominee != null) {
                game.log().add("Игрок " + n + " выставил игрока " + nominee);
            }
            announcer.say("Спасибо, игрок " + n + ".");
            speech(order, i + 1);
        });
    }

    private String nomineesText() {
        return nominations.isEmpty()
                ? "Пока никто не выставлен"
                : "Выставлены: " + Game.joinNumbers(nominations);
    }

    // ───────────────────────────── Голосование ─────────────────────────────

    private void startVoting() {
        Voting voting = new Voting(nominations, game.aliveCount(), game.dayNumber() == 1);
        String list = Game.joinNumbers(nominations);
        switch (voting.phase()) {
            case NO_VOTE: {
                String text = nominations.isEmpty()
                        ? "Ни одной кандидатуры не выставлено. Голосование не проводится."
                        : "В первый день выставлена только одна кандидатура. Голосование не проводится.";
                game.log().add("Голосование не проводилось");
                announcer.say(text);
                showMessage("Голосование", text, "Наступает ночь", this::startNight);
                break;
            }
            case FINISHED: {
                int x = voting.eliminated().get(0);
                String text = "Выставлен только игрок " + x + ". Он покидает стол без голосования.";
                game.log().add("Единственная кандидатура — игрок " + x + " покидает стол");
                announcer.say(text);
                showMessage("Голосование", text, "Прощальная минута",
                        () -> farewells(voting.eliminated(), this::startNight));
                break;
            }
            default: {
                announcer.say("Объявляется голосование. Выставлены игроки: " + list + ".");
                showMessage("Голосование",
                        "Выставлены игроки: " + list + ".\n\nГолосуют все " + voting.voters()
                                + " игроков за столом. Кто не проголосовал — голосует за последнюю кандидатуру.",
                        "Начать голосование",
                        () -> voteRound(voting, new int[voting.candidates().size()], 0));
                break;
            }
        }
    }

    private void voteRound(Voting voting, int[] votes, int i) {
        List<Integer> candidates = voting.candidates();
        int used = 0;
        for (int k = 0; k < i; k++) {
            used += votes[k];
        }
        int remaining = voting.voters() - used;
        int x = candidates.get(i);
        String title = voting.isRevote() ? "Переголосование" : "Голосование";
        String question = "Кто за то, чтобы игрок " + x + " покинул стол?";
        if (i == candidates.size() - 1) {
            // Не проголосовавшие отдают голос последней кандидатуре — её тоже называем, но голоса не вводим.
            View v = show(R.layout.screen_message);
            this.<TextView>find(v, R.id.title).setText(title);
            this.<TextView>find(v, R.id.text).setText(question + "\n\nОставшиеся голоса: " + remaining);
            v.findViewById(R.id.action).setVisibility(View.GONE);
            announceCandidate(x, () -> {
                votes[i] = remaining;
                finishVoteRound(voting, votes);
            });
            return;
        }
        View v = showVoteInput(title, question, remaining, value -> {
            votes[i] = value;
            voteRound(voting, votes, i + 1);
        });
        // Число голосов вводится после «Спасибо» — к этому моменту голосование за кандидатуру закончено.
        View action = v.findViewById(R.id.action);
        action.setEnabled(false);
        announceCandidate(x, () -> action.setEnabled(true));
    }

    /** «X, кто за X?», через секунду «Спасибо.»; thanked вызывается после «Спасибо». */
    private void announceCandidate(int x, Runnable thanked) {
        announceVote(x + ", кто за " + x + "?", thanked);
    }

    /** Вопрос голосования, через секунду «Спасибо.»; thanked вызывается после «Спасибо». */
    private void announceVote(String question, Runnable thanked) {
        announcer.say(question,
                () -> handler.postDelayed(() -> announcer.say("Спасибо.", thanked), VOTE_WINDOW_MS));
    }

    private void finishVoteRound(Voting voting, int[] votes) {
        List<Integer> candidates = new ArrayList<>(voting.candidates());
        StringBuilder summary = new StringBuilder();
        StringBuilder logLine = new StringBuilder(voting.isRevote() ? "Переголосование: " : "Голосование: ");
        for (int k = 0; k < candidates.size(); k++) {
            if (k > 0) {
                summary.append('\n');
                logLine.append(", ");
            }
            summary.append("Игрок ").append(candidates.get(k)).append(" — ").append(votesText(votes[k]));
            logLine.append(candidates.get(k)).append(" — ").append(votes[k]);
        }
        game.log().add(logLine.toString());

        voting.submitVotes(votes);
        switch (voting.phase()) {
            case FINISHED: {
                int x = voting.eliminated().get(0);
                game.log().add("Стол покидает игрок " + x);
                announcer.say("Стол покидает игрок " + x + ".");
                showMessage("Итоги голосования", summary + "\n\nСтол покидает игрок " + x + ".",
                        "Прощальная минута", () -> farewells(voting.eliminated(), this::startNight));
                break;
            }
            case TIE_SPEECHES: {
                String tied = Game.joinNumbers(voting.candidates());
                String text = "Голоса разделились поровну между игроками " + tied
                        + ". Им даётся по 30 секунд на оправдательную речь.";
                game.log().add("Ничья: " + tied);
                announcer.say(text);
                showMessage("Итоги голосования", summary + "\n\n" + text,
                        "Начать речи", () -> tieSpeeches(voting, 0));
                break;
            }
            case LIFT_ALL: {
                String tied = Game.joinNumbers(voting.candidates());
                String text = "Снова поровну. Голосуем, покинут ли стол все игроки: " + tied + ".";
                announcer.say(text);
                showMessage("Итоги голосования", summary + "\n\n" + text,
                        "Голосовать", () -> liftAllVote(voting));
                break;
            }
            default:
                throw new IllegalStateException("Неожиданная фаза голосования: " + voting.phase());
        }
    }

    private void tieSpeeches(Voting voting, int i) {
        List<Integer> candidates = voting.candidates();
        if (i >= candidates.size()) {
            voting.tieSpeechesDone();
            announcer.say("Переголосование.");
            voteRound(voting, new int[candidates.size()], 0);
            return;
        }
        int x = candidates.get(i);
        announcer.say("Игрок " + x + ", у вас тридцать секунд.");
        showTimer("Оправдательная речь", "Игрок " + x, TIE_SPEECH_MS, true, "Спасибо.",
                () -> tieSpeeches(voting, i + 1));
    }

    private void liftAllVote(Voting voting) {
        List<Integer> candidates = new ArrayList<>(voting.candidates());
        String list = Game.joinNumbers(candidates);
        String question = "Кто за то, чтобы все игроки " + list + " покинули стол?";
        View v = showVoteInput("Поднять всех?", question, voting.voters(), value -> {
            boolean lifted = voting.submitLiftAll(value);
            String tally = "За — " + votesText(value) + " из " + voting.voters() + ".";
            game.log().add("Поднять всех (" + list + "): за " + value + " из " + voting.voters()
                    + (lifted ? " — покидают стол" : " — остаются"));
            if (lifted) {
                String text = "Стол покидают игроки " + list + ".";
                announcer.say(text);
                showMessage("Итоги голосования", tally + "\n\n" + text, "Прощальная минута",
                        () -> farewells(voting.eliminated(), this::startNight));
            } else {
                String text = "Большинства нет — все остаются за столом.";
                announcer.say(text);
                showMessage("Итоги голосования", tally + "\n\n" + text, "Наступает ночь", this::startNight);
            }
        });
        View action = v.findViewById(R.id.action);
        action.setEnabled(false);
        announceVote(question, () -> action.setEnabled(true));
    }

    private static String votesText(int n) {
        int mod100 = n % 100;
        int mod10 = n % 10;
        String word;
        if (mod100 >= 11 && mod100 <= 14) {
            word = "голосов";
        } else if (mod10 == 1) {
            word = "голос";
        } else if (mod10 >= 2 && mod10 <= 4) {
            word = "голоса";
        } else {
            word = "голосов";
        }
        return n + " " + word;
    }

    // ──────────────────────── Выбывание и прощальная минута ────────────────────────

    /** Выводит игроков из игры, даёт каждому прощальную минуту и проверяет победу. */
    private void farewells(List<Integer> players, Runnable then) {
        List<Integer> leaving = new ArrayList<>(players);
        for (int x : leaving) {
            game.eliminate(x);
        }
        farewell(leaving, 0, () -> {
            Game.Winner winner = game.winner();
            if (winner == Game.Winner.NONE) {
                then.run();
            } else {
                showGameOver(winner);
            }
        });
    }

    private void farewell(List<Integer> leaving, int i, Runnable then) {
        if (i >= leaving.size()) {
            then.run();
            return;
        }
        int x = leaving.get(i);
        announcer.say("Игрок " + x + ", ваша прощальная минута.");
        showTimer("Прощальная минута", "Игрок " + x + " покидает стол", FAREWELL_MS, true,
                "Спасибо, игрок " + x + ".", () -> farewell(leaving, i + 1, then));
    }

    // ──────────────────────────────── Ночь ────────────────────────────────

    private void startNight() {
        int night = game.dayNumber();
        setPhase("Ночь " + night);
        game.log().section("Ночь " + night);
        shots.clear();
        music.start();
        List<Integer> order = game.nightOrder();
        announcer.say("Наступает ночь. Город засыпает. Передавайте телефон по кругу, начиная с игрока "
                + order.get(0) + ".");
        nightTurn(order, 0);
    }

    private void nightTurn(List<Integer> order, int i) {
        int n = order.get(i);
        callPlayer(n);
        showHandoff("Ночной ход " + (i + 1) + " из " + order.size(), n,
                "Держите телефон так, чтобы экран видели только вы.",
                "Я игрок " + n, () -> {
                    turnStartedAt = SystemClock.elapsedRealtime();
                    nightAction(n, () -> finishNightTurn(order, i));
                });
    }

    private void nightAction(int n, Runnable done) {
        switch (game.player(n).role()) {
            case MAFIA:
                pickShot(n, done);
                break;
            case DON:
                pickShot(n, () -> check(n, "Проверка дона",
                        "Выберите игрока, которого проверяете на шерифство.",
                        target -> game.player(target).role() == Role.SHERIFF
                                ? "Игрок " + target + " — ШЕРИФ"
                                : "Игрок " + target + " — не шериф",
                        "Дон проверил", done));
                break;
            case SHERIFF:
                check(n, "Проверка шерифа",
                        "Выберите игрока, которого проверяете.",
                        target -> game.player(target).role().isBlack()
                                ? "Игрок " + target + " — МАФИЯ"
                                : "Игрок " + target + " — мирный",
                        "Шериф проверил", done);
                break;
            case CIVILIAN:
            default:
                civilianTurn(n, done);
                break;
        }
    }

    private View nightScreen(int n, String title, String hint) {
        View v = show(R.layout.screen_night);
        this.<TextView>find(v, R.id.role).setText("Игрок " + n + " · " + game.player(n).role().title());
        this.<TextView>find(v, R.id.title).setText(title);
        this.<TextView>find(v, R.id.hint).setText(hint);
        return v;
    }

    private void pickShot(int n, Runnable next) {
        View v = nightScreen(n, "Выстрел", "Выберите номер игрока, в которого стреляете.");
        MaterialButton action = v.findViewById(R.id.action);
        action.setText("Подтвердить выстрел");
        action.setEnabled(false);
        int[] target = {0};
        NumberGrid grid = new NumberGrid(v.findViewById(R.id.grid), game.alive());
        grid.setListener(number -> {
            target[0] = number;
            grid.select(number);
            action.setEnabled(true);
        });
        action.setOnClickListener(once(() -> {
            shots.put(n, target[0]);
            game.log().add(game.player(n).role().title() + " (игрок " + n + ") стреляет в игрока " + target[0]);
            next.run();
        }));
    }

    private interface Verdict {
        String of(int target);
    }

    private void check(int n, String title, String hint, Verdict verdict, String logPrefix, Runnable next) {
        View v = nightScreen(n, title, hint);
        MaterialButton action = v.findViewById(R.id.action);
        action.setText("Проверить");
        action.setEnabled(false);
        GridLayout gridLayout = v.findViewById(R.id.grid);
        TextView result = v.findViewById(R.id.result);
        int[] target = {0};

        List<Integer> checkable = new ArrayList<>();
        for (int k = 1; k <= Game.PLAYER_COUNT; k++) {
            if (k != n) {
                checkable.add(k);
            }
        }
        NumberGrid grid = new NumberGrid(gridLayout, checkable);
        grid.setListener(number -> {
            target[0] = number;
            grid.select(number);
            action.setEnabled(true);
        });
        action.setOnClickListener(once(() -> {
            String text = verdict.of(target[0]);
            game.log().add(logPrefix + " игрока " + target[0] + ": " + text.substring(text.indexOf('—') + 2));
            gridLayout.setVisibility(View.GONE);
            result.setVisibility(View.VISIBLE);
            result.setText(text);
            action.setText("Понятно");
            action.setOnClickListener(once(next));
        }));
    }

    private void civilianTurn(int n, Runnable next) {
        View v = nightScreen(n, "Ночь", "Ночью у вас нет действия.\nНажмите кнопку и передайте телефон дальше.");
        v.findViewById(R.id.grid).setVisibility(View.GONE);
        MaterialButton action = v.findViewById(R.id.action);
        action.setText("Действие не требуется");
        action.setOnClickListener(once(next));
    }

    private void finishNightTurn(List<Integer> order, int i) {
        boolean last = i == order.size() - 1;
        long left = MIN_NIGHT_TURN_MS - (SystemClock.elapsedRealtime() - turnStartedAt);
        View v = show(R.layout.screen_message);
        MaterialButton action = v.findViewById(R.id.action);
        if (last) {
            // Утро наступает само: последний игрок кладёт телефон, кнопок не нужно.
            this.<TextView>find(v, R.id.title).setText("Ночь окончена");
            this.<TextView>find(v, R.id.text).setText("Положите телефон на стол.\nСкоро наступит утро.");
            action.setVisibility(View.GONE);
            handler.postDelayed(this::morning, Math.max(left, NIGHT_END_PAUSE_MS));
            return;
        }
        this.<TextView>find(v, R.id.title).setText("Ход завершён");
        this.<TextView>find(v, R.id.text).setText("Не показывайте экран соседям.");
        String label = "Передать телефон дальше";
        action.setOnClickListener(once(() -> nightTurn(order, i + 1)));
        if (left > 0) {
            action.setEnabled(false);
            action.setText("Подождите…");
            handler.postDelayed(() -> {
                action.setEnabled(true);
                action.setText(label);
            }, left);
        } else {
            action.setText(label);
        }
    }

    private void morning() {
        music.stop();
        setPhase("Утро");
        Integer killed = game.resolveShots(shots);
        if (killed == null) {
            game.log().add("Промах — никто не убит");
            String text = "Ночь прошла без потерь: мафия промахнулась.";
            announcer.say("Наступает утро. " + text);
            showMessage("Утро", text, "Начать день", this::startDay);
        } else {
            game.log().add("Убит игрок " + killed);
            String text = "Ночью убит игрок " + killed + ".";
            announcer.say("Наступает утро. " + text);
            Runnable farewell = () -> farewells(Collections.singletonList(killed), this::startDay);
            if (game.dayNumber() == 1) {
                // Убитый в первую ночь перед прощальной речью делает лучший ход.
                showMessage("Утро", text, "Лучший ход", () -> bestMove(killed, farewell));
            } else {
                showMessage("Утро", text, "Прощальная минута", farewell);
            }
        }
    }

    /** Лучший ход: убитый в первую ночь называет трёх игроков, которых считает мафией. */
    private void bestMove(int n, Runnable then) {
        announcer.say("Игрок " + n + ", ваш лучший ход.");
        showHandoff("Лучший ход", n,
                "Выберите трёх игроков, которых считаете мафией.\nПосле подтверждения изменить выбор нельзя.",
                "Сделать лучший ход", () -> showBestMove(n, then));
    }

    private void showBestMove(int n, Runnable then) {
        View v = show(R.layout.screen_night);
        this.<TextView>find(v, R.id.role).setText("Игрок " + n);
        this.<TextView>find(v, R.id.title).setText("Лучший ход");
        this.<TextView>find(v, R.id.hint).setText("Отметьте три номера предполагаемой мафии.");
        MaterialButton action = v.findViewById(R.id.action);
        TextView result = v.findViewById(R.id.result);

        List<Integer> candidates = new ArrayList<>();
        for (int k = 1; k <= Game.PLAYER_COUNT; k++) {
            if (k != n) {
                candidates.add(k);
            }
        }
        List<Integer> picked = new ArrayList<>();
        NumberGrid grid = new NumberGrid(v.findViewById(R.id.grid), candidates);
        Runnable refresh = () -> {
            grid.select(picked);
            action.setEnabled(picked.size() == 3);
            action.setText(picked.size() == 3
                    ? "Подтвердить: " + Game.joinNumbers(sorted(picked))
                    : "Выбрано " + picked.size() + " из 3");
        };
        grid.setListener(number -> {
            if (picked.contains(number)) {
                picked.remove(Integer.valueOf(number));
            } else if (picked.size() < 3) {
                picked.add(number);
            }
            refresh.run();
        });
        refresh.run();

        action.setOnClickListener(once(() -> {
            String list = Game.joinNumbers(sorted(picked));
            grid.lock(picked);
            game.log().add("Лучший ход игрока " + n + ": " + list);
            int black = 0;
            for (int x : picked) {
                if (game.player(x).role().isBlack()) {
                    black++;
                }
            }
            bestMoveSummary = "Лучший ход игрока " + n + ": " + list
                    + " — угадано мафии: " + black + " из 3";
            announcer.say("Лучший ход игрока " + n + ": номера " + list + ".");
            result.setVisibility(View.VISIBLE);
            result.setText(list);
            action.setText("Прощальная минута");
            action.setOnClickListener(once(then));
        }));
    }

    private static List<Integer> sorted(List<Integer> numbers) {
        List<Integer> copy = new ArrayList<>(numbers);
        Collections.sort(copy);
        return copy;
    }

    // ───────────────────────────── Конец игры ─────────────────────────────

    private void showGameOver(Game.Winner winner) {
        gameOver = true;
        music.stop();
        String title = winner == Game.Winner.MAFIA ? "Победила мафия" : "Победили мирные жители";
        game.log().section("Итог");
        game.log().add(title);
        if (bestMoveSummary != null) {
            game.log().add(bestMoveSummary);
        }
        announcer.say("Игра окончена. " + title + "!");
        setPhase("Игра окончена");
        View v = show(R.layout.screen_game_over);
        this.<TextView>find(v, R.id.title).setText(title);
        this.<TextView>find(v, R.id.log).setText(game.log().toString());
        v.findViewById(R.id.action).setOnClickListener(v1 -> finish());
    }

    // ───────────────────────────── Общие экраны ─────────────────────────────

    /** Заменяет текущий экран; таймеры и отложенные действия прошлого экрана отменяются. */
    private View show(int layout) {
        handler.removeCallbacksAndMessages(null);
        timer.cancel();
        screen.removeAllViews();
        View v = LayoutInflater.from(this).inflate(layout, screen, false);
        screen.addView(v);
        return v;
    }

    /** Вызов игрока к телефону при раздаче карт и ночью. «Номер» словом — «№» синтез читает ненадёжно. */
    private void callPlayer(int n) {
        announcer.say("Игрок номер " + n);
    }

    private void setPhase(String label) {
        phaseLabel.setText(label);
    }

    private void showMessage(String title, String text, String button, Runnable next) {
        View v = show(R.layout.screen_message);
        this.<TextView>find(v, R.id.title).setText(title);
        this.<TextView>find(v, R.id.text).setText(text);
        MaterialButton action = v.findViewById(R.id.action);
        action.setText(button);
        action.setOnClickListener(once(next));
    }

    private void showHandoff(String title, int player, String hint, String button, Runnable next) {
        View v = show(R.layout.screen_handoff);
        this.<TextView>find(v, R.id.title).setText(title + "\n\nПередайте телефон игроку");
        this.<TextView>find(v, R.id.number).setText(String.valueOf(player));
        this.<TextView>find(v, R.id.hint).setText(hint);
        MaterialButton action = v.findViewById(R.id.action);
        action.setText(button);
        action.setOnClickListener(once(next));
    }

    private void showTimer(String title, String subtitle, long durationMs, boolean speech,
                           String endPhrase, Runnable next) {
        View v = show(R.layout.screen_timer);
        this.<TextView>find(v, R.id.title).setText(title);
        this.<TextView>find(v, R.id.subtitle).setText(subtitle);
        runTimer(v, durationMs, speech, () -> {
            if (endPhrase != null) {
                announcer.say(endPhrase);
            }
            next.run();
        });
    }

    /**
     * Запускает отсчёт на экране с полем time и кнопками pause/finish.
     * В речах за 10 секунд до конца звучит «Десять секунд», в остальных фазах — короткий сигнал.
     */
    private void runTimer(View v, long durationMs, boolean speech, Runnable onDone) {
        TextView time = v.findViewById(R.id.time);
        MaterialButton pause = v.findViewById(R.id.pause);
        Runnable done = onceRun(() -> {
            timer.cancel();
            announcer.endSignal();
            onDone.run();
        });
        timer.start(durationMs, new PhaseTimer.Listener() {
            @Override
            public void onTick(long remainingMs) {
                time.setText(PhaseTimer.format(remainingMs));
            }

            @Override
            public void onWarning() {
                if (speech) {
                    announcer.say("Десять секунд.");
                } else {
                    announcer.beep();
                }
            }

            @Override
            public void onFinish() {
                done.run();
            }
        });
        pause.setOnClickListener(b -> {
            if (timer.isRunning()) {
                timer.pause();
                pause.setText(R.string.resume);
            } else {
                timer.resume();
                pause.setText(R.string.pause);
            }
        });
        v.findViewById(R.id.finish).setOnClickListener(b -> done.run());
    }

    private View showVoteInput(String title, String question, int max, IntConsumer onSubmit) {
        View v = show(R.layout.screen_vote);
        this.<TextView>find(v, R.id.title).setText(title);
        this.<TextView>find(v, R.id.question).setText(question);
        TextView value = v.findViewById(R.id.value);
        TextView hint = v.findViewById(R.id.hint);
        View minus = v.findViewById(R.id.minus);
        View plus = v.findViewById(R.id.plus);
        int[] count = {0};
        Runnable refresh = () -> {
            value.setText(String.valueOf(count[0]));
            hint.setText("Ещё могут проголосовать: " + (max - count[0]));
            minus.setEnabled(count[0] > 0);
            plus.setEnabled(count[0] < max);
        };
        minus.setOnClickListener(b -> {
            count[0]--;
            refresh.run();
        });
        plus.setOnClickListener(b -> {
            count[0]++;
            refresh.run();
        });
        refresh.run();
        v.findViewById(R.id.action).setOnClickListener(once(() -> onSubmit.accept(count[0])));
        return v;
    }

    // ───────────────────────────── Утилиты ─────────────────────────────

    /** Обёртка от двойного срабатывания: действие выполняется не больше одного раза. */
    private static Runnable onceRun(Runnable action) {
        boolean[] done = {false};
        return () -> {
            if (!done[0]) {
                done[0] = true;
                action.run();
            }
        };
    }

    /** Обработчик нажатия, защищённый от двойного тапа. */
    private static View.OnClickListener once(Runnable action) {
        Runnable guarded = onceRun(action);
        return v -> guarded.run();
    }

    @SuppressWarnings("unchecked")
    private <T extends View> T find(View root, int id) {
        return (T) root.findViewById(id);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
