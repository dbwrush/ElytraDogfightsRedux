package net.sudologic.elytraDogfightsRedux.game;

public enum SessionState {
    WAITING,    // Waiting for players to join
    COUNTDOWN,  // Enough players joined, countdown started
    ACTIVE,     // Game is actively running
    FINISHED    // Game is over
}
