package iovi.lobby1.game;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.Locale;

/**
 * Статистика раздачи: 100 000 партий с тем же генератором, что в приложении.
 * Таблица «место × роль» пишется в build/deal-stats.txt; каждая роль проверяется
 * на равномерность по местам критерием хи-квадрат.
 */
public class DealStatsTest {
    private static final int DEALS = 100_000;
    /** Хи-квадрат для 9 степеней свободы при уровне значимости 0,001. */
    private static final double CHI2_CRITICAL = 27.877;

    @Test
    public void rolesAreUniformAcrossSlots() throws IOException {
        Role[] roles = Role.values();
        long[][] counts = new long[Game.PLAYER_COUNT][roles.length];
        SecureRandom random = new SecureRandom();
        for (int d = 0; d < DEALS; d++) {
            Game game = new Game(random);
            for (int n = 1; n <= Game.PLAYER_COUNT; n++) {
                counts[n - 1][game.player(n).role().ordinal()]++;
            }
        }

        StringBuilder out = new StringBuilder();
        out.append(String.format(Locale.ROOT, "Раздач: %d%n%nМесто", DEALS));
        for (Role r : roles) {
            out.append(String.format(Locale.ROOT, " | %-14s", r.title()));
        }
        out.append('\n');
        for (int s = 0; s < Game.PLAYER_COUNT; s++) {
            out.append(String.format(Locale.ROOT, "%5d", s + 1));
            for (Role r : roles) {
                long c = counts[s][r.ordinal()];
                out.append(String.format(Locale.ROOT, " | %6d %6.2f%%", c, 100.0 * c / DEALS));
            }
            out.append('\n');
        }

        out.append(String.format(Locale.ROOT, "%nХи-квадрат (9 ст. св., порог %.2f при p = 0,001):%n", CHI2_CRITICAL));
        boolean uniform = true;
        for (Role r : roles) {
            long total = 0;
            for (int s = 0; s < Game.PLAYER_COUNT; s++) {
                total += counts[s][r.ordinal()];
            }
            double expected = (double) total / Game.PLAYER_COUNT;
            double chi2 = 0;
            for (int s = 0; s < Game.PLAYER_COUNT; s++) {
                double diff = counts[s][r.ordinal()] - expected;
                chi2 += diff * diff / expected;
            }
            boolean ok = chi2 < CHI2_CRITICAL;
            uniform &= ok;
            out.append(String.format(Locale.ROOT, "  %-14s %7.2f  %s%n", r.title(), chi2, ok ? "равномерно" : "ПЕРЕКОС"));
        }

        File report = new File("build/deal-stats.txt");
        try (FileWriter w = new FileWriter(report)) {
            w.write(out.toString());
        }
        assertTrue("Раздача неравномерна, см. " + report.getAbsolutePath(), uniform);
    }
}
