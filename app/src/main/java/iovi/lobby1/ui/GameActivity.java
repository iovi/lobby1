package iovi.lobby1.ui;

import android.animation.ObjectAnimator;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
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
import java.util.function.IntConsumer;

import iovi.lobby1.R;
import iovi.lobby1.audio.Announcer;
import iovi.lobby1.audio.NightMusic;
import iovi.lobby1.flow.GameFlow;
import iovi.lobby1.flow.GameUi;
import iovi.lobby1.flow.PhaseTimer;
import iovi.lobby1.game.Game;
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
    public Button showMessage(String title, String text, String button, Runnable next) {
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
        return action;
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