package uno;

public class GameRulesViolated extends RuntimeException {

    public GameRulesViolated(String message) {
        super(message);
    }
}
