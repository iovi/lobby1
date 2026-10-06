package iovi.lobby1.game;

/** Игрок за столом. Номер места — от 1 до {@link Game#PLAYER_COUNT}. */
public final class Player {
    private final int number;
    private final Role role;
    private boolean alive = true;

    Player(int number, Role role) {
        this.number = number;
        this.role = role;
    }

    public int number() {
        return number;
    }

    public Role role() {
        return role;
    }

    public boolean isAlive() {
        return alive;
    }

    void leave() {
        alive = false;
    }
}
