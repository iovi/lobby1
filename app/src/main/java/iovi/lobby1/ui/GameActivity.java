package iovi.lobby1.ui;

import android.animation.ObjectAnimator;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;

import java.security.SecureRandom;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.IntConsumer;

import iovi.lobby1.R;
import iovi.lobby1.audio.Announcer;
import iovi.lobby1.audio.NightMusic;
import iovi.lobby1.flow.GameFlow;
import iovi.lobby1.flow.GameUi;
import iovi.lobby1.flow.PhaseTimer;
import iovi.lobby1.game.Game;
import iovi.lobby1.game.Protocol;
import iovi.lobby1.game.Role;

/**
 * Экран партии. Ходом игры управляет {@link GameFlow}; активность только рисует
 * экраны, которые он показывает, и передаёт ему нажатия.
 */
public class GameActivity extends AppCompatActivity implements GameUi, Announcer.Listener {
    private GameFlow flow;
    private Announcer announcer;
    private NightMusic music;
    private FrameLayout screen;
    private TextView phaseLabel;

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

        flow = new GameFlow(new Game(new SecureRandom()), this, announcer, music, new HandlerScheduler());
        flow.start();
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
        flow.stop();
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
        if (flow.isOver()) {
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

    // ───────────────────────────── Экраны партии ─────────────────────────────

    @Override
    public void setPhase(String label) {
        phaseLabel.setText(label);
    }

    @Override
    public MessageView showMessage(String title, String text, String button, Runnable next) {
        View v = show(R.layout.screen_message);
        this.<TextView>find(v, R.id.title).setText(title);
        TextView textView = v.findViewById(R.id.text);
        if (text == null) {
            textView.setVisibility(View.GONE);
        } else {
            textView.setText(text);
        }
        ActionButton action = new ActionButton(v.findViewById(R.id.action));
        if (button == null) {
            action.view.setVisibility(View.GONE);
        } else {
            action.setText(button);
            action.setOnClick(next);
        }
        ActionButton extra = new ActionButton(v.findViewById(R.id.extra));
        return new MessageView() {
            @Override
            public Button action() {
                return action;
            }

            @Override
            public Button showExtra(String extraText) {
                extra.setText(extraText);
                extra.view.setVisibility(View.VISIBLE);
                return extra;
            }
        };
    }

    @Override
    public void showHandoff(String title, int player, String hint, String button, Runnable next) {
        View v = show(R.layout.screen_handoff);
        this.<TextView>find(v, R.id.title).setText(title);
        this.<TextView>find(v, R.id.number).setText(String.valueOf(player));
        this.<TextView>find(v, R.id.hint).setText(hint);
        ActionButton action = new ActionButton(v.findViewById(R.id.action));
        action.setText(button);
        action.setOnClick(next);
    }

    @Override
    public void showCard(String player, String roleTitle, Role role, long durationMs) {
        View v = show(R.layout.screen_card);

        GradientDrawable card = new GradientDrawable();
        card.setCornerRadius(dp(20));
        card.setColor(ContextCompat.getColor(this, role.isBlack() ? R.color.card_black : R.color.card_red));
        card.setStroke(dp(2), ContextCompat.getColor(this, R.color.card_border));
        v.findViewById(R.id.card).setBackground(card);

        this.<TextView>find(v, R.id.playerNumber).setText(player);
        this.<TextView>find(v, R.id.role).setText(roleTitle);
        int icon = role == Role.SHERIFF ? R.drawable.ic_card_star
                : role == Role.DON ? R.drawable.ic_card_ring : 0;
        if (icon != 0) {
            ImageView roleIcon = v.findViewById(R.id.roleIcon);
            roleIcon.setImageResource(icon);
            roleIcon.setVisibility(View.VISIBLE);
        }

        ProgressBar progress = v.findViewById(R.id.progress);
        ObjectAnimator.ofInt(progress, "progress", progress.getMax(), 0)
                .setDuration(durationMs)
                .start();
    }

    @Override
    public TimerView showTimer(String title, String subtitle, TimerControls controls) {
        View v = show(R.layout.screen_timer);
        this.<TextView>find(v, R.id.title).setText(title);
        this.<TextView>find(v, R.id.subtitle).setText(subtitle);
        return new TimerScreen(v, controls);
    }

    @Override
    public SpeechView showSpeech(String title, String hint, Collection<Integer> enabled, TimerControls controls) {
        View v = show(R.layout.screen_speech);
        this.<TextView>find(v, R.id.title).setText(title);
        this.<TextView>find(v, R.id.hint).setText(hint);
        return new SpeechScreen(v, enabled, controls);
    }

    @Override
    public VoteView showVoteInput(String title, String question) {
        View v = show(R.layout.screen_vote);
        this.<TextView>find(v, R.id.title).setText(title);
        this.<TextView>find(v, R.id.question).setText(question);
        return new VoteScreen(v);
    }

    @Override
    public NightView showNight(String header, String title, String hint, Collection<Integer> enabled) {
        View v = show(R.layout.screen_night);
        this.<TextView>find(v, R.id.role).setText(header);
        this.<TextView>find(v, R.id.title).setText(title);
        this.<TextView>find(v, R.id.hint).setText(hint);
        return new NightScreen(v, enabled);
    }

    @Override
    public ProtocolView showProtocol(String header, String title, String hint, List<Integer> players,
                                     Map<Protocol.Mark, String> labels) {
        View v = show(R.layout.screen_protocol);
        this.<TextView>find(v, R.id.role).setText(header);
        this.<TextView>find(v, R.id.title).setText(title);
        this.<TextView>find(v, R.id.hint).setText(hint);
        return new ProtocolScreen(v, players, labels);
    }

    @Override
    public void confirm(String title, String message, String yes, String no, Runnable onYes, Runnable onNo) {
        // Ответ ровно один: кнопка «да», кнопка «нет» или закрытие диалога (назад, тап мимо).
        boolean[] answered = {false};
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(yes, (d, w) -> {
                    answered[0] = true;
                    onYes.run();
                })
                .setNegativeButton(no, null)
                .setOnDismissListener(d -> {
                    if (!answered[0]) {
                        answered[0] = true;
                        onNo.run();
                    }
                })
                .show();
    }

    @Override
    public void showGameOver(String title, String log) {
        View v = show(R.layout.screen_game_over);
        this.<TextView>find(v, R.id.title).setText(title);
        this.<TextView>find(v, R.id.log).setText(log);
        v.findViewById(R.id.action).setOnClickListener(v1 -> finish());
    }

    /** Заменяет текущий экран. */
    private View show(int layout) {
        screen.removeAllViews();
        View v = LayoutInflater.from(this).inflate(layout, screen, false);
        screen.addView(v);
        return v;
    }

    // ───────────────────────────── Элементы экранов ─────────────────────────────

    private static final class ActionButton implements Button {
        final MaterialButton view;

        ActionButton(MaterialButton view) {
            this.view = view;
        }

        @Override
        public void setText(String text) {
            view.setText(text);
        }

        @Override
        public void setEnabled(boolean enabled) {
            view.setEnabled(enabled);
        }

        @Override
        public void setOnClick(Runnable action) {
            view.setOnClickListener(once(action));
        }
    }

    private static final class NumberGridView implements Grid {
        private final NumberGrid grid;

        NumberGridView(GridLayout layout, Collection<Integer> enabled) {
            grid = new NumberGrid(layout, enabled);
        }

        @Override
        public void setListener(IntConsumer onNumber) {
            grid.setListener(onNumber::accept);
        }

        @Override
        public void select(Collection<Integer> numbers) {
            grid.select(numbers);
        }

        @Override
        public void lock(Collection<Integer> numbers) {
            grid.lock(numbers);
        }
    }

    private static class TimerScreen implements TimerView {
        private final TextView time;
        private final MaterialButton pause;
        private final View finish;

        TimerScreen(View v, TimerControls controls) {
            time = v.findViewById(R.id.time);
            pause = v.findViewById(R.id.pause);
            finish = v.findViewById(R.id.finish);
            pause.setOnClickListener(b -> controls.onPauseToggle());
            finish.setOnClickListener(b -> controls.onFinish());
        }

        @Override
        public void setTime(long remainingMs) {
            time.setText(PhaseTimer.format(remainingMs));
        }

        @Override
        public void setPaused(boolean paused) {
            pause.setText(paused ? R.string.resume : R.string.pause);
        }

        @Override
        public void setControlsEnabled(boolean enabled) {
            pause.setEnabled(enabled);
            finish.setEnabled(enabled);
        }
    }

    private static final class SpeechScreen extends TimerScreen implements SpeechView {
        private final Grid grid;
        private final Button confirm;
        private final TextView hint;
        private final TextView nominees;

        SpeechScreen(View v, Collection<Integer> enabled, TimerControls controls) {
            super(v, controls);
            grid = new NumberGridView(v.findViewById(R.id.grid), enabled);
            confirm = new ActionButton(v.findViewById(R.id.confirm));
            hint = v.findViewById(R.id.hint);
            nominees = v.findViewById(R.id.nominees);
        }

        @Override
        public Grid grid() {
            return grid;
        }

        @Override
        public Button confirm() {
            return confirm;
        }

        @Override
        public void setHint(String text) {
            hint.setText(text);
        }

        @Override
        public void setNominees(String text) {
            nominees.setText(text);
        }
    }

    private static final class VoteScreen implements VoteView {
        private final TextView value;
        private final TextView hint;
        private final View minus;
        private final View plus;
        private final Button action;

        VoteScreen(View v) {
            value = v.findViewById(R.id.value);
            hint = v.findViewById(R.id.hint);
            minus = v.findViewById(R.id.minus);
            plus = v.findViewById(R.id.plus);
            action = new ActionButton(v.findViewById(R.id.action));
        }

        @Override
        public void setOnMinus(Runnable action) {
            minus.setOnClickListener(b -> action.run());
        }

        @Override
        public void setOnPlus(Runnable action) {
            plus.setOnClickListener(b -> action.run());
        }

        @Override
        public void setCount(int count, String hintText, boolean canMinus, boolean canPlus) {
            value.setText(String.valueOf(count));
            hint.setText(hintText);
            minus.setEnabled(canMinus);
            plus.setEnabled(canPlus);
        }

        @Override
        public Button action() {
            return action;
        }
    }

    private static final class NightScreen implements NightView {
        private final GridLayout gridLayout;
        private final Grid grid;
        private final TextView hint;
        private final TextView time;
        private final TextView result;
        private final ActionButton extra;
        private final Button action;

        NightScreen(View v, Collection<Integer> enabled) {
            gridLayout = v.findViewById(R.id.grid);
            if (enabled == null) {
                gridLayout.setVisibility(View.GONE);
                grid = null;
            } else {
                grid = new NumberGridView(gridLayout, enabled);
            }
            hint = v.findViewById(R.id.hint);
            time = v.findViewById(R.id.time);
            result = v.findViewById(R.id.result);
            extra = new ActionButton(v.findViewById(R.id.extra));
            action = new ActionButton(v.findViewById(R.id.action));
        }

        @Override
        public Grid grid() {
            return grid;
        }

        @Override
        public Button action() {
            return action;
        }

        @Override
        public void setTime(long remainingMs) {
            time.setVisibility(View.VISIBLE);
            time.setText(PhaseTimer.format(remainingMs));
        }

        @Override
        public void setHint(String text) {
            hint.setText(text);
        }

        @Override
        public Button showExtra(String text) {
            extra.setText(text);
            extra.view.setVisibility(View.VISIBLE);
            return extra;
        }

        @Override
        public void hideExtra() {
            extra.view.setVisibility(View.GONE);
        }

        @Override
        public void hideGrid() {
            gridLayout.setVisibility(View.GONE);
        }

        @Override
        public void showResult(String text) {
            result.setVisibility(View.VISIBLE);
            result.setText(text);
        }
    }

    /** Строка на игрока: номер и кнопки отметок; выбранная отметка закрашена цветом карты. */
    private final class ProtocolScreen implements ProtocolView {
        private final Map<Integer, Map<Protocol.Mark, MaterialButton>> rows = new HashMap<>();
        private final TextView summary;
        private final Button action;
        private final ActionButton extra;
        private BiConsumer<Integer, Protocol.Mark> listener;

        ProtocolScreen(View v, List<Integer> players, Map<Protocol.Mark, String> labels) {
            summary = v.findViewById(R.id.summary);
            action = new ActionButton(v.findViewById(R.id.action));
            extra = new ActionButton(v.findViewById(R.id.extra));
            LinearLayout container = v.findViewById(R.id.rows);
            int margin = dp(4);
            for (int player : players) {
                LinearLayout row = new LinearLayout(GameActivity.this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);

                TextView number = new TextView(GameActivity.this);
                number.setText(String.valueOf(player));
                number.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
                number.setTypeface(Typeface.DEFAULT_BOLD);
                number.setTextColor(ContextCompat.getColor(GameActivity.this, R.color.text_primary));
                number.setGravity(Gravity.CENTER);
                row.addView(number, new LinearLayout.LayoutParams(dp(44), LinearLayout.LayoutParams.WRAP_CONTENT));

                Map<Protocol.Mark, MaterialButton> buttons = new EnumMap<>(Protocol.Mark.class);
                for (Protocol.Mark mark : Protocol.Mark.values()) {
                    MaterialButton b = new MaterialButton(GameActivity.this);
                    b.setText(labels.get(mark));
                    b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
                    b.setAllCaps(false);
                    b.setLetterSpacing(0f);
                    b.setInsetTop(0);
                    b.setInsetBottom(0);
                    b.setPadding(0, 0, 0, 0);
                    b.setMinWidth(0);
                    b.setMinimumWidth(0);
                    b.setSingleLine(true);
                    b.setCornerRadius(dp(12));
                    b.setStrokeWidth(dp(1));
                    b.setOnClickListener(x -> {
                        if (listener != null) {
                            listener.accept(player, mark);
                        }
                    });
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(52), 1f);
                    lp.setMargins(margin, margin, margin, margin);
                    row.addView(b, lp);
                    buttons.put(mark, b);
                }
                rows.put(player, buttons);
                container.addView(row, new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            }
        }

        @Override
        public void setListener(BiConsumer<Integer, Protocol.Mark> onMark) {
            listener = onMark;
        }

        @Override
        public void setRow(int player, Protocol.Mark mark, Set<Protocol.Mark> allowed) {
            for (Map.Entry<Protocol.Mark, MaterialButton> e : rows.get(player).entrySet()) {
                MaterialButton b = e.getValue();
                boolean on = e.getKey() == mark;
                int fill = !on ? R.color.number_idle
                        : e.getKey() == Protocol.Mark.BLACK ? R.color.card_black : R.color.card_red;
                b.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(GameActivity.this, fill)));
                b.setStrokeColor(ColorStateList.valueOf(ContextCompat.getColor(GameActivity.this,
                        on ? R.color.card_border : R.color.number_idle)));
                b.setEnabled(allowed.contains(e.getKey()));
                b.setAlpha(b.isEnabled() ? 1f : 0.25f);
            }
        }

        @Override
        public void lock() {
            for (Map<Protocol.Mark, MaterialButton> buttons : rows.values()) {
                for (MaterialButton b : buttons.values()) {
                    b.setClickable(false);
                }
            }
        }

        @Override
        public void setSummary(String text) {
            summary.setText(text);
        }

        @Override
        public Button action() {
            return action;
        }

        @Override
        public Button showExtra(String text) {
            extra.setText(text);
            extra.view.setVisibility(View.VISIBLE);
            return extra;
        }
    }

    // ───────────────────────────── Утилиты ─────────────────────────────

    /** Обработчик нажатия, защищённый от двойного тапа: действие выполняется не больше одного раза. */
    private static View.OnClickListener once(Runnable action) {
        boolean[] done = {false};
        return v -> {
            if (!done[0]) {
                done[0] = true;
                action.run();
            }
        };
    }

    @SuppressWarnings("unchecked")
    private <T extends View> T find(View root, int id) {
        return (T) root.findViewById(id);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}