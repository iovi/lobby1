package iovi.lobby1.text;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Тексты партии из texts.properties (UTF-8): экран, голос ведущего и протокол.
 * Плейсхолдеры {0}, {1}… заменяются аргументами по порядку.
 */
public final class Texts {
    private static final Properties TEXTS = load();

    private Texts() {
    }

    private static Properties load() {
        Properties texts = new Properties();
        try (InputStream in = Texts.class.getResourceAsStream("texts.properties")) {
            if (in == null) {
                throw new IllegalStateException("Не найден texts.properties");
            }
            texts.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return texts;
    }

    /** Текст по ключу с подставленными аргументами; неизвестный ключ — ошибка. */
    public static String get(String key, Object... args) {
        String text = TEXTS.getProperty(key);
        if (text == null) {
            throw new IllegalArgumentException("Нет текста с ключом " + key);
        }
        for (int i = 0; i < args.length; i++) {
            text = text.replace("{" + i + "}", String.valueOf(args[i]));
        }
        return text;
    }

    /** Число с согласованным словом по ключам key.one / key.few / key.many: «1 голос», «3 голоса», «5 голосов». */
    public static String plural(String key, int n) {
        int mod100 = Math.abs(n) % 100;
        int mod10 = mod100 % 10;
        String form;
        if (mod100 >= 11 && mod100 <= 14) {
            form = "many";
        } else if (mod10 == 1) {
            form = "one";
        } else if (mod10 >= 2 && mod10 <= 4) {
            form = "few";
        } else {
            form = "many";
        }
        return get(key + "." + form, n);
    }

    /** Все тексты, чьи ключи начинаются с prefix; в результате ключи без prefix. */
    public static Map<String, String> withPrefix(String prefix) {
        Map<String, String> result = new LinkedHashMap<>();
        for (String key : TEXTS.stringPropertyNames()) {
            if (key.startsWith(prefix)) {
                result.put(key.substring(prefix.length()), TEXTS.getProperty(key));
            }
        }
        return result;
    }
}