package com.masterminds.character;

public enum CharacterRole {
    MAFIA("Mafia", "Eliminate citizens at night and avoid execution during the day."),
    DETECTIVE("Detective", "Investigate one player at night to learn whether they are mafia."),
    DOCTOR("Doctor", "Protect one player at night from a mafia kill."),
    CITIZEN("Citizen", "Find and execute every mafia through discussion and voting."),
    FOOL("Fool", "Try to be executed during the day to win alone."),
    HYPNOTIST("Hypnotist", "Submit replacement day messages for another player with limited uses."),
    LAWYER("Lawyer", "Survive with the assigned client to share a special victory.");

    private final String displayName;
    private final String description;

    CharacterRole(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }
}
