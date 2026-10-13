package iovi.lobby1.game;

import iovi.lobby1.text.Texts;

/** Игровая роль. Мафия и дон — «чёрные», мирные и шериф — «красные». */
public enum Role {
    CIVILIAN(false),
    SHERIFF(false),
    MAFIA(true),
    DON(true);

    private final boolean black;

    Role(boolean black) {
        this.black = black;
    }

    /** Название роли из texts.properties (ключ role.ИМЯ). */
    public String title() {
        return Texts.get("role." + name());
    }

    public boolean isBlack() {
        return black;
    }
}
