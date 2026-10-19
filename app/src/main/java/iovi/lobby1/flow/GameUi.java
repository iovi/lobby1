package iovi.lobby1.flow;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.IntConsumer;

import iovi.lobby1.game.Protocol;
import iovi.lobby1.game.Role;

/**
 * Экраны партии. Каждый show… заменяет текущий экран. Тексты приходят готовыми —
 * интерфейс только показывает их и сообщает о нажатиях.
 */
public interface GameUi {
    /** Кнопка действия. Обработчик срабатывает не больше одного раза — защита от двойного тапа. */
    interface Button {
        void setText(String text);

        void setEnabled(boolean enabled);

        void setOnClick(Runnable action);
    }

    /** Сетка номеров игроков 1–10. */
    interface Grid {
        void setListener(IntConsumer onNumber);

        /** Подсвечивает выбранные номера. */
        void select(Collection<Integer> numbers);

        /** Фиксирует выбор: подсвечивает номера и запрещает дальнейшие нажатия. */
        void lock(Collection<Integer> numbers);
    }

    /** Нажатия кнопок «Пауза» и «Завершить» под таймером. */
    interface TimerControls {
        void onPauseToggle();

        void onFinish();
    }

    interface TimerView {
        void setTime(long remainingMs);

        void setPaused(boolean paused);

        void setControlsEnabled(boolean enabled);
    }

    /** Речь игрока: таймер, сетка для выставления и кнопка подтверждения. */
    interface SpeechView extends TimerView {
        Grid grid();

        Button confirm();

        void setHint(String text);

        void setNominees(String text);
    }

    /** Ввод числа голосов кнопками «−» и «+». */
    interface VoteView {
        void setOnMinus(Runnable action);

        void setOnPlus(Runnable action);

        void setCount(int count, String hint, boolean canMinus, boolean canPlus);

        Button action();
    }

    /** Ночной ход или лучший ход: сетка номеров, результат и кнопка действия. */
    interface NightView {
        /** null, если экран показан без сетки. */
        Grid grid();

        Button action();

        /** Отсчёт хода; пока не задан, его на экране нет. */
        void setTime(long remainingMs);

        void setHint(String text);

        /** Показывает дополнительную кнопку над основной. */
        Button showExtra(String text);

        void hideExtra();

        void hideGrid();

        void showResult(String text);
    }

    /** Протокол: строка на каждого игрока с кнопками отметок, итог и кнопка действия. */
    interface ProtocolView {
        void setListener(BiConsumer<Integer, Protocol.Mark> onMark);

        /** Отметка игрока (null — без отметки) и какие отметки ему сейчас можно поставить. */
        void setRow(int player, Protocol.Mark mark, Set<Protocol.Mark> allowed);

        /** Запрещает дальнейшие отметки. */
        void lock();

        void setSummary(String text);

        Button action();

        /** Показывает дополнительную кнопку под основной. */
        Button showExtra(String text);
    }

    void setPhase(String label);

    /** text или button равны null — соответствующая часть экрана скрыта. */
    Button showMessage(String title, String text, String button, Runnable next);

    void showHandoff(String title, int player, String hint, String button, Runnable next);

    /** Карта роли; полоска показа убывает за durationMs. */
    void showCard(String player, String roleTitle, Role role, long durationMs);

    TimerView showTimer(String title, String subtitle, TimerControls controls);

    SpeechView showSpeech(String title, String hint, Collection<Integer> enabled, TimerControls controls);

    VoteView showVoteInput(String title, String question);

    /** enabled == null — экран без сетки номеров. */
    NightView showNight(String header, String title, String hint, Collection<Integer> enabled);

    /** players — строки протокола по порядку; labels — подписи кнопок отметок. */
    ProtocolView showProtocol(String header, String title, String hint, List<Integer> players,
                              Map<Protocol.Mark, String> labels);

    /** Диалог поверх экрана: onYes или onNo (в том числе при закрытии диалога) вызывается ровно один раз. */
    void confirm(String title, String message, String yes, String no, Runnable onYes, Runnable onNo);

    void showGameOver(String title, String log);
}