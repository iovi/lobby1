package iovi.lobby1.game;

/** Игровая роль. Мафия и дон — «чёрные», мирные и шериф — «красные». */
public enum Role {
    CIVILIAN("Мирный житель", false),
    SHERIFF("Шериф", false),
    MAFIA("Мафия", true),
    DON("Дон мафии", true);

    private final String title;
    private final boolean black;

    Role(String title, boolean black) {
        this.title = title;
        this.black = black;
    }

    public String title() {
        return title;
    }

    public boolean isBlack() {
        return black;
    }
}
