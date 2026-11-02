package iovi.lobby1.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.MotionEvent;
import android.view.View;

import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import iovi.lobby1.R;

/**
 * Рубашка карты: тёмная карта с узором из маленьких эмблем приложения. Вид занимает ячейку колоды,
 * карта в нём — наибольшая с пропорциями карты, прижатая к краю, заданному {@link #align}.
 * В карту укладывается целое число эмблем, равномерно по всей площади: на любом экране ни одна не обрезана.
 */
final class CardBackView extends View {
    /** Высота карты относительно ширины. */
    private static final float ASPECT = 1.45f;
    /** Высота эмблемы в узоре. */
    private static final int EMBLEM_DP = 20;
    /** Наименьшие промежутки между эмблемами — по горизонтали и вертикали — и отступ от края карты. */
    private static final int GAP_X_DP = 12;
    private static final int GAP_Y_DP = 6;
    private static final int PADDING_DP = 4;
    /**
     * Где в значке приложения (поле 108×108) сама эмблема: по краям значка — пустые поля,
     * их отрезаем, чтобы эмблемы раскладывались по своему настоящему размеру.
     */
    private static final float ICON_SIZE = 108;
    private static final float EMBLEM_LEFT = 31;
    private static final float EMBLEM_RIGHT = 86;
    private static final float EMBLEM_TOP = 0;
    private static final float EMBLEM_BOTTOM = 84;

    private static Bitmap emblem;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint emblemPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final RectF rect = new RectF();
    /** Левые верхние углы эмблем узора. */
    private final List<PointF> emblems = new ArrayList<>();
    private final float radius;
    private final float density;
    /** Куда прижата карта в ячейке по горизонтали: -1 — вправо (к соседу справа), 1 — влево, 0 — по центру. */
    private int align;

    /** Конструктор, которым пользуются инструменты разметки; прижатие — {@link #setAlign}. */
    public CardBackView(Context context) {
        super(context);
        density = context.getResources().getDisplayMetrics().density;
        radius = 8 * density;
        fill.setColor(ContextCompat.getColor(context, R.color.card_back));
        border.setStyle(Paint.Style.STROKE);
        border.setStrokeWidth(2 * density);
        border.setColor(ContextCompat.getColor(context, R.color.card_border));
        emblem(context);
    }

    /** Куда прижать карту в ячейке: -1 — вправо, 1 — влево, 0 — по центру. Задаётся до раскладки. */
    void setAlign(int align) {
        this.align = align;
        requestLayout();
    }

    /** Эмблема приложения в цвете узора, без пустых полей значка; общая для всех рубашек. */
    private static Bitmap emblem(Context context) {
        if (emblem == null) {
            float scale = EMBLEM_DP * context.getResources().getDisplayMetrics().density
                    / (EMBLEM_BOTTOM - EMBLEM_TOP);
            int width = Math.round((EMBLEM_RIGHT - EMBLEM_LEFT) * scale);
            int height = Math.round((EMBLEM_BOTTOM - EMBLEM_TOP) * scale);
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            Drawable icon = Objects.requireNonNull(
                    ContextCompat.getDrawable(context, R.drawable.ic_launcher_monochrome)).mutate();
            icon.setTint(ContextCompat.getColor(context, R.color.card_back_pattern));
            int left = Math.round(-EMBLEM_LEFT * scale);
            int top = Math.round(-EMBLEM_TOP * scale);
            int size = Math.round(ICON_SIZE * scale);
            icon.setBounds(left, top, left + size, top + size);
            icon.draw(new Canvas(bitmap));
            emblem = bitmap;
        }
        return emblem;
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
        layoutEmblems();
        // Переворот — вокруг середины самой карты, а не ячейки.
        setPivotX(rect.centerX());
        setPivotY(rect.centerY());
    }

    /**
     * Сколько целых эмблем помещается с наименьшими промежутками — столько и ставим, а оставшееся место
     * делим поровну: каждая эмблема посередине своей клетки, клетки заполняют карту целиком.
     */
    private void layoutEmblems() {
        emblems.clear();
        float pad = border.getStrokeWidth() + PADDING_DP * density;
        float areaLeft = rect.left + pad;
        float areaTop = rect.top + pad;
        float areaWidth = rect.width() - 2 * pad;
        float areaHeight = rect.height() - 2 * pad;
        float gapX = GAP_X_DP * density;
        float gapY = GAP_Y_DP * density;
        int columns = (int) ((areaWidth + gapX) / (emblem.getWidth() + gapX));
        int rows = (int) ((areaHeight + gapY) / (emblem.getHeight() + gapY));
        if (columns <= 0 || rows <= 0) {
            return;
        }
        float cellWidth = areaWidth / columns;
        float cellHeight = areaHeight / rows;
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < columns; col++) {
                emblems.add(new PointF(
                        areaLeft + col * cellWidth + (cellWidth - emblem.getWidth()) / 2,
                        areaTop + row * cellHeight + (cellHeight - emblem.getHeight()) / 2));
            }
        }
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
        for (PointF at : emblems) {
            canvas.drawBitmap(emblem, at.x, at.y, emblemPaint);
        }
        canvas.drawRoundRect(rect, radius, radius, border);
    }
}