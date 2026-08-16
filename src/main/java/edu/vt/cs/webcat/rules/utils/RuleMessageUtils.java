package edu.vt.cs.webcat.rules.utils;

/** MessageFormat-safe handling for already-formatted PMD violation text. */
public final class RuleMessageUtils {

    private RuleMessageUtils() {
        throw new AssertionError("Utility class");
    }

    public static String escapeLiteral(String message) {
        return message.replace("'", "''")
                .replace("{", "'{'")
                .replace("}", "'}'");
    }
}
