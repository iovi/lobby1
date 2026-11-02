package iovi.lobby1.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.view.MotionEvent;
import android.view.View;

import androidx.core.content.ContextCompat;

import java.util.Objects;

import iovi.lobby1.R;

/**
 * Рубашка карты: тёмная карта с узором из маленьких эмблем приложения. Вид занимает ячейку колоды,
 * карта в нём — наибольшая с пропорциями карты, прижатая к краю, заданному {@link #align}.
 */
final class CardBackView extends View {
    /** Высота карты относительно ширины. */
    private static final float ASPECT = 1.45f;
    /** Шаг узора; сама эмблема занимает примерно половину клетки — остальное промежутки. */
    private static final int TILE_DP = 26;

    private static Bitmap tile;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pattern = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final float radius;
    /** Куда прижата карта в ячейке по горизонтали: -1 — вправо (к соседу справа), 1 — влево, 0 — по центру. */
    private int align;

    /** Конструктор, которым пользуются инструменты разметки; прижатие — {@link #setAlign}. */
    public CardBackView(Context context) {
        super(context);
        float density = context.getResources().getDisplayMetrics().density;
        radius = 8 * density;
        fill.setColor(ContextCompat.getColor(context, R.color.card_back));
        pattern.setShader(new BitmapShader(tile(context), Shader.TileMode.REPEAT, Shader.TileMode.REPEAT));
        border.setStyle(Paint.Style.STROKE);
        border.setStrokeWidth(2 * density);
        border.setColor(ContextCompat.getColor(context, R.color.card_border));
    }

    /** Куда прижать карту в ячейке: -1 — вправо, 1 — влево, 0 — по центру. Задаётся до раскладки. */
    void setAlign(int align) {
        this.align = align;
        requestLayout();
    }

    /** Одна клетка узора — эмблема приложения в цвете узора; общая для всех рубашек. */
    private static Bitmap tile(Context context) {
        if (tile == null) {
            int size = Math.round(TILE_DP * context.getResources().getDisplayMetrics().density);
            Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            Drawable icon = Objects.requireNonNull(
                    ContextCompat.getDrawable(context, R.drawable.ic_launcher_monochrome)).mutate();
            icon.setTint(ContextCompat.getColor(context, R.color.card_back_pattern));
            icon.setBounds(0, 0, size, size);
            icon.draw(new Canvas(bitmap));
            tile = bitmap;
        }
        return tile;
    }

    /** Где нарисована карта внутри вида. */
    RectF cardRect() {
        return rect;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        float height = Math.min(h, w * ASPECT);
        float width = height / ASPECT;
        float left = align < 0 ? w - width : align > 0 ? 0 : (w - width) / 2;
        float top = (h - height) / 2;
        float inset = border.getStrokeWidth() / 2;
        rect.set(left + inset, top + inset, left + width - inset, top + height - inset);
        // Переворот — вокруг середины самой карты, а не ячейки.
        setPivotX(rect.centerX());
        setPivotY(rect.centerY());
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // Нажатие мимо карты (в пустой части ячейки) — не выбор.
        if (event.getAction() == MotionEvent.ACTION_DOWN && !rect.contains(event.getX(), event.getY())) {
            return false;
        }
        return super.onTouchEvent(event);
    }

    /** Нажатие идёт через стандартный путь — его видят и службы доступности. */
    @Override
    public boolean performClick() {
        return super.performClick();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawRoundRect(rect, radius, radius, fill);
        canvas.drawRoundRect(rect, radius, radius, pattern);
        canvas.drawRoundRect(rect, radius, radius, border);
    }
}