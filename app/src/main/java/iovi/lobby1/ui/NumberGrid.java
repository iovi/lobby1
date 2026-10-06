package iovi.lobby1.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.util.TypedValue;
import android.widget.GridLayout;

import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;

import java.util.Collection;

import iovi.lobby1.R;
import iovi.lobby1.game.Game;

/** Сетка 5×2 с номерами игроков 1–10 и выбором одного номера. */
final class NumberGrid {
    interface Listener {
        void onNumberClicked(int number);
    }

    private final MaterialButton[] buttons = new MaterialButton[Game.PLAYER_COUNT + 1];
    private final ColorStateList idle;
    private final ColorStateList selected;
    private Listener listener;

    NumberGrid(GridLayout grid, Collection<Integer> enabled) {
        Context context = grid.getContext();
        idle = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.number_idle));
        selected = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.number_selected));
        int height = dp(context, 72);
        int margin = dp(context, 4);
        grid.removeAllViews();
        for (int n = 1; n <= Game.PLAYER_COUNT; n++) {
            MaterialButton b = new MaterialButton(context);
            b.setText(String.valueOf(n));
            b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26);
            b.setInsetTop(0);
            b.setInsetBottom(0);
            // Стандартные отступы MaterialButton не дают «10» уместиться в узкой кнопке.
            b.setPadding(0, 0, 0, 0);
            b.setMinWidth(0);
            b.setMinimumWidth(0);
            b.setMaxLines(1);
            b.setSingleLine(true);
            b.setCornerRadius(dp(context, 12));
            b.setBackgroundTintList(idle);
            boolean on = enabled.contains(n);
            b.setEnabled(on);
            b.setAlpha(on ? 1f : 0.25f);
            final int number = n;
            b.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onNumberClicked(number);
                }
            });

            GridLayout.LayoutParams lp = new GridLayout.LayoutParams(
                    GridLayout.spec(GridLayout.UNDEFINED),
                    GridLayout.spec(GridLayout.UNDEFINED, 1f));
            lp.width = 0;
            lp.height = height;
            lp.setMargins(margin, margin, margin, margin);
            grid.addView(b, lp);
            buttons[n] = b;
        }
    }

    void setListener(Listener listener) {
        this.listener = listener;
    }

    /** Подсвечивает выбранный номер; 0 — снять выбор. */
    void select(int number) {
        for (int n = 1; n <= Game.PLAYER_COUNT; n++) {
            buttons[n].setBackgroundTintList(n == number ? selected : idle);
        }
    }

    /** Фиксирует выбор: подсвечивает номер и запрещает дальнейшие нажатия. */
    void lock(int number) {
        select(number);
        for (int n = 1; n <= Game.PLAYER_COUNT; n++) {
            buttons[n].setEnabled(false);
            buttons[n].setAlpha(n == number ? 1f : 0.25f);
        }
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
