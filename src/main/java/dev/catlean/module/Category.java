package dev.catlean.module;

public enum Category {
    COMBAT("Combat"),
    PLAYER("Player"),
    MOVEMENT("Movement"),
    RENDER("Render"),
    MISC("Misc");

    public final String displayName;

    Category(String displayName) { this.displayName = displayName; }
}
